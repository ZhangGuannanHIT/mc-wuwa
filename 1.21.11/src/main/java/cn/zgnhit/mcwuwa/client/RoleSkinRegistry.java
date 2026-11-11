package cn.zgnhit.mcwuwa.client;

import cn.zgnhit.mcwuwa.McWuwa;
import cn.zgnhit.mcwuwa.core.CharacterId;
import cn.zgnhit.mcwuwa.core.RoleAppearanceState;
import cn.zgnhit.mcwuwa.platform.ClientVisualConfig;
import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;

/** One visual-only skin source for native idle/GUI/hands and the existing combat rig.
 * Local owner view and public remote appearance are server-authoritative; no profile mutation. */
public final class RoleSkinRegistry {
    private record Skin(ClientAsset.ResourceTexture body,PlayerModelType model) { }
    private record Binding(Skin base,Map<String,Skin> forms) { }
    private static final Identifier MANIFEST=Identifier.fromNamespaceAndPath("mc_wuwa","presentation/skins.json");
    private static Map<String,Binding> bindings=Map.of();
    private static Skin cachedChoice;
    private static PlayerSkin cachedOriginal,cachedResolved;
    private static final RoleAppearanceState remote=new RoleAppearanceState();
    private record Pending(cn.zgnhit.mcwuwa.platform.Network.Appearance value,long expires) { }
    private static final Map<UUID,Pending> pending=new HashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel appearanceLevel;
    private RoleSkinRegistry() { }

    public static void initialize(IEventBus bus){
        bus.addListener(RoleSkinRegistry::registerReload);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post event)->{
            var game=Minecraft.getInstance();if(game.level==null){clear();return;}
            if(appearanceLevel!=game.level){clear();appearanceLevel=game.level;}
            var queued=new ArrayList<>(pending.values());for(var entry:queued){
                if(game.level.getGameTime()>entry.expires())pending.remove(entry.value().actor());
                else accept(entry.value());
            }
            if(game.player!=null&&game.player.tickCount%20==0)remote.retain((uuid,id)->{
                var tracked=game.level.getEntity(id);return tracked instanceof AbstractClientPlayer&&tracked.getUUID().equals(uuid);
            });
        });
    }
    private static void registerReload(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath("mc_wuwa","character_skins"),(ResourceManagerReloadListener)RoleSkinRegistry::reload);
    }
    public static void reload(ResourceManager resources) {
        Map<String,Binding> loaded=new HashMap<>();
        try(var reader=resources.getResourceOrThrow(MANIFEST).openAsReader()) {
            var json=JsonParser.parseReader(reader).getAsJsonObject();
            if(json.get("schema").getAsInt()!=1)throw new IllegalArgumentException("Unsupported role skin schema");
            Set<String> weapons=new HashSet<>();for(var role:CharacterId.values())weapons.add(role.weaponPath());
            for(var element:json.getAsJsonArray("characters")) {
                var row=element.getAsJsonObject();String weapon=row.get("weapon").getAsString();
                try {
                    if(!weapons.contains(weapon)||loaded.containsKey(weapon))throw new IllegalArgumentException("Unknown or duplicate role weapon");
                    Skin base=skin(resources,row);Map<String,Skin> forms=new HashMap<>();
                    if(row.has("forms"))for(var form:row.getAsJsonObject("forms").entrySet()) {
                        try{forms.put(form.getKey(),skin(resources,form.getValue().getAsJsonObject()));}
                        catch(Exception error){McWuwa.LOGGER.warn("Cannot load role skin variant {}/{}; using its base",weapon,form.getKey(),error);}
                    }
                    loaded.put(weapon,new Binding(base,Map.copyOf(forms)));
                }catch(Exception error){McWuwa.LOGGER.warn("Cannot load role skin {}; retaining account skin for this role",weapon,error);}
            }
        }catch(Exception error){McWuwa.LOGGER.warn("Cannot load role skins; retaining account skins",error);}
        bindings=Map.copyOf(loaded);clearCache();
    }
    private static Skin skin(ResourceManager resources,JsonObject row)throws Exception {
        var texture=Identifier.parse(row.get("texture").getAsString());
        if(!texture.getNamespace().equals("mc_wuwa")||!texture.getPath().startsWith("textures/entity/characters/")||!texture.getPath().endsWith(".png"))
            throw new IllegalArgumentException("Role skin texture must be a local character PNG");
        PlayerModelType model=switch(row.get("model").getAsString()){case "slim"->PlayerModelType.SLIM;case "wide"->PlayerModelType.WIDE;default->throw new IllegalArgumentException("Unknown arm model");};
        try(var input=resources.getResourceOrThrow(texture).open();var image=NativeImage.read(input)) {
            if(image.getWidth()!=64||image.getHeight()!=64)throw new IllegalArgumentException("Role skins must use the standard64x64 layout");
        }
        String path=texture.getPath();var asset=Identifier.fromNamespaceAndPath(texture.getNamespace(),path.substring(9,path.length()-4));
        return new Skin(new ClientAsset.ResourceTexture(asset,texture),model);
    }
    /** Called with the original getSkin return; never calls getSkin recursively. */
    public static PlayerSkin resolve(AbstractClientPlayer actor,PlayerSkin original) {
        var game=Minecraft.getInstance();
        if(original==null||game.level==null||!actor.isAlive()||actor.isSpectator()
            ||!ClientVisualConfig.AUTOMATIC_ROLE_SKINS.get())return original;
        String weapon,form;
        if(actor==game.player) {
            var role=CombatClient.roleView(CombatClient.activeSlot());
            if(!role.has("weapon")||!role.has("hp")||role.get("hp").getAsDouble()<=0)return original;
            weapon=role.get("weapon").getAsString();form=role.has("form")?role.get("form").getAsString():"";
        } else {
            var appearance=remote.find(actor.getUUID(),actor.getId());if(appearance.isEmpty())return original;
            weapon=appearance.get().weapon();form=appearance.get().form();
        }
        var binding=bindings.get(weapon);if(binding==null)return original;
        Skin selected=binding.forms().getOrDefault(form,binding.base());
        if(selected.equals(cachedChoice)&&original.equals(cachedOriginal))return cachedResolved;
        cachedChoice=selected;cachedOriginal=original;
        cachedResolved=PlayerSkin.insecure(selected.body(),original.cape(),original.elytra(),selected.model());
        return cachedResolved;
    }
    public static void clearCache(){cachedChoice=null;cachedOriginal=null;cachedResolved=null;}
    /** Authoritative public form, independent of whether automatic skins are enabled. */
    public static boolean formMatches(net.minecraft.world.entity.player.Player actor,CharacterId character,String form){
        var game=Minecraft.getInstance();
        if(game.level==null||!actor.isAlive()||actor.isSpectator())return false;
        if(game.player!=null&&actor.getUUID().equals(game.player.getUUID())){
            if(!CombatClient.wuwaMode())return false;
            var role=CombatClient.roleView(CombatClient.activeSlot());
            return role.has("weapon")&&role.get("weapon").getAsString().equals(character.weaponPath())
                &&role.has("form")&&role.get("form").getAsString().equals(form)
                &&role.has("hp")&&role.get("hp").getAsDouble()>0;
        }
        return remote.find(actor.getUUID(),actor.getId())
            .filter(value->value.weapon().equals(character.weaponPath())&&value.form().equals(form)).isPresent();
    }
    public static void accept(cn.zgnhit.mcwuwa.platform.Network.Appearance value) {
        var game=Minecraft.getInstance();if(game.level==null)return;
        if(appearanceLevel!=game.level){clear();appearanceLevel=game.level;}
        var actor=game.level.getEntity(value.entityId());
        if(!(actor instanceof AbstractClientPlayer)){
            var old=pending.get(value.actor());
            if(old==null&&pending.size()<256||old!=null&&value.revision()>old.value().revision())pending.put(value.actor(),new Pending(value,game.level.getGameTime()+200));
            return;
        }
        pending.remove(value.actor());
        remote.accept(new RoleAppearanceState.Snapshot(value.actor(),value.entityId(),value.weapon(),value.form(),value.revision()),actor.getUUID(),actor.getId());
    }
    public static void clear(){remote.clear();pending.clear();appearanceLevel=null;clearCache();}
}
