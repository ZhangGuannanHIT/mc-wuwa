package cn.zgnhit.mcwuwa.platform;

import cn.zgnhit.mcwuwa.core.CharacterInstance;
import com.google.gson.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Versioned intents C2S; authoritative values S2C. No client-provided damage or cooldown. */
public final class Network {
    public static Consumer<State> stateReceiver = value -> { };
    public static Consumer<Visual> visualReceiver = value -> { };
    public static Consumer<UiFeedback> uiFeedbackReceiver=value->{ };
    public static Consumer<Impact> impactReceiver=value->{ };
    public static Consumer<Appearance> appearanceReceiver=value->{ };
    public static Consumer<CreativeCursor> creativeCursorReceiver=value->{ };
    public static Consumer<HazardPause> hazardPauseReceiver=value->{ };
    private static final Map<UUID,Appearance> lastAppearances=new HashMap<>();
    private static long appearanceSequence;
    private static final Map<UUID,cn.zgnhit.mcwuwa.core.TickBudget> impactBudgets=new HashMap<>();
    private static long impactSequence;
    private static final Map<UUID, Long> visualRevisions = new HashMap<>();
    private static final Map<UUID, Visual> lastActions = new HashMap<>(), lastForms = new HashMap<>();
    public record Command(long sequence, String operation, int argument, float strafe, float forward) implements CustomPacketPayload {
        public static final Type<Command> TYPE = new Type<>(Identifier.fromNamespaceAndPath("mc_wuwa", "command"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Command> CODEC = StreamCodec.of((b,p) -> {
            b.writeVarLong(p.sequence); b.writeUtf(p.operation, 32); b.writeVarInt(p.argument); b.writeFloat(p.strafe); b.writeFloat(p.forward);
        }, b -> new Command(b.readVarLong(), b.readUtf(32), b.readVarInt(), b.readFloat(), b.readFloat()));
        @Override public Type<Command> type() { return TYPE; }
    }
    public record State(String json, List<ItemStack> weapons) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(Identifier.fromNamespaceAndPath("mc_wuwa", "state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.of((b,p) -> {
            b.writeUtf(p.json, 32767); for (int i = 0; i < 3; i++) ItemStack.OPTIONAL_STREAM_CODEC.encode(b, p.weapons.get(i));
        }, b -> new State(b.readUtf(32767), List.of(ItemStack.OPTIONAL_STREAM_CODEC.decode(b), ItemStack.OPTIONAL_STREAM_CODEC.decode(b), ItemStack.OPTIONAL_STREAM_CODEC.decode(b))));
        @Override public Type<State> type() { return TYPE; }
    }
    public record UiFeedback(int slot,String text) implements CustomPacketPayload{
        public static final Type<UiFeedback> TYPE=new Type<>(Identifier.fromNamespaceAndPath("mc_wuwa","ui_feedback"));
        public static final StreamCodec<RegistryFriendlyByteBuf,UiFeedback> CODEC=StreamCodec.of((b,p)->{b.writeVarInt(p.slot);b.writeUtf(p.text,256);},b->new UiFeedback(b.readVarInt(),b.readUtf(256)));
        @Override public Type<UiFeedback> type(){return TYPE;}
    }
    public static void feedback(ServerPlayer player,int slot,String text){PacketDistributor.sendToPlayer(player,new UiFeedback(slot,text));}
    public record Impact(UUID owner,String dimension,long tick,long sequence,int element,int style,double x,double y,double z) implements CustomPacketPayload {
        public Impact {
            Objects.requireNonNull(owner);var parsed=Identifier.tryParse(dimension);
            if(parsed==null||!parsed.toString().equals(dimension)||tick<0||sequence<1||element<0||element>=cn.zgnhit.mcwuwa.core.Element.values().length||style<0||style>5||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IllegalArgumentException("visual impact");
        }
        public static final Type<Impact> TYPE=new Type<>(Identifier.fromNamespaceAndPath("mc_wuwa","impact"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Impact> CODEC=StreamCodec.of((b,p)->{
            b.writeUUID(p.owner);b.writeUtf(p.dimension,80);b.writeVarLong(p.tick);b.writeVarLong(p.sequence);b.writeVarInt(p.element);b.writeVarInt(p.style);b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);
        },b->new Impact(b.readUUID(),b.readUtf(80),b.readVarLong(),b.readVarLong(),b.readVarInt(),b.readVarInt(),b.readDouble(),b.readDouble(),b.readDouble()));
        @Override public Type<Impact> type(){return TYPE;}
    }
    public static void impact(ServerPlayer owner,net.minecraft.world.entity.LivingEntity target,cn.zgnhit.mcwuwa.core.Element element,int style) {
        long tick=owner.level().getGameTime();if(impactBudgets.computeIfAbsent(owner.getUUID(),ignored->new cn.zgnhit.mcwuwa.core.TickBudget(20)).grant(tick,1,40)==0)return;
        var packet=new Impact(owner.getUUID(),owner.level().dimension().identifier().toString(),tick,++impactSequence,element.ordinal(),style,target.getX(),target.getY()+target.getBbHeight()*.5,target.getZ());
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(owner,packet);
    }
    /** Creative cursor is client-owned in vanilla; survival players cannot use this packet. */
    public record CreativeTeamSlot(long sequence,int slot,ItemStack carried) implements CustomPacketPayload {
        public static final Type<CreativeTeamSlot> TYPE=new Type<>(Identifier.fromNamespaceAndPath("mc_wuwa","creative_team_slot"));
        public static final StreamCodec<RegistryFriendlyByteBuf,CreativeTeamSlot> CODEC=StreamCodec.of((buffer,value)->{
            buffer.writeVarLong(value.sequence);buffer.writeVarInt(value.slot);ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer,value.carried);
        },buffer->new CreativeTeamSlot(buffer.readVarLong(),buffer.readVarInt(),ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer)));
        @Override public Type<CreativeTeamSlot> type(){return TYPE;}
    }
    public record CreativeEchoSlot(long sequence,int slot,ItemStack carried) implements CustomPacketPayload {
        public static final Type<CreativeEchoSlot> TYPE=new Type<>(Identifier.fromNamespaceAndPath("mc_wuwa","creative_echo_slot"));
        public static final StreamCodec<RegistryFriendlyByteBuf,CreativeEchoSlot> CODEC=StreamCodec.of((b,p)->{
            b.writeVarLong(p.sequence);b.writeVarInt(p.slot);ItemStack.OPTIONAL_STREAM_CODEC.encode(b,p.carried);
        },b->new CreativeEchoSlot(b.readVarLong(),b.readVarInt(),ItemStack.OPTIONAL_STREAM_CODEC.decode(b)));
        @Override public Type<CreativeEchoSlot> type(){return TYPE;}
    }
    public record CreativeEchoCreate(long sequence,String species) implements CustomPacketPayload {
        public static final Type<CreativeEchoCreate> TYPE=new Type<>(Identifier.fromNamespaceAndPath("mc_wuwa","creative_echo_create"));
        public static final StreamCodec<RegistryFriendlyByteBuf,CreativeEchoCreate> CODEC=StreamCodec.of((b,p)->{b.writeVarLong(p.sequence);b.writeUtf(p.species,80);},b->new CreativeEchoCreate(b.readVarLong(),b.readUtf(80)));
        @Override public Type<CreativeEchoCreate> type(){return TYPE;}
    }
    /** Creative's cursor is client-owned in vanilla. Revisioned snapshots and
     * replies prevent an old editor reply overwriting a later native pickup. */
    public record CreativeCursor(long sequence,ItemStack carried) implements CustomPacketPayload {
        public static final Type<CreativeCursor> TYPE=new Type<>(Identifier.fromNamespaceAndPath("mc_wuwa","creative_cursor"));
        public static final StreamCodec<RegistryFriendlyByteBuf,CreativeCursor> CODEC=StreamCodec.of((b,p)->{
            b.writeVarLong(p.sequence);ItemStack.OPTIONAL_STREAM_CODEC.encode(b,p.carried);
        },b->new CreativeCursor(b.readVarLong(),ItemStack.OPTIONAL_STREAM_CODEC.decode(b)));
        @Override public Type<CreativeCursor> type(){return TYPE;}
    }
    public static void creativeCursor(ServerPlayer player,long revision){
        rememberCreativeCursor(player);
        player.inventoryMenu.broadcastChanges();
        PacketDistributor.sendToPlayer(player,new CreativeCursor(revision,player.inventoryMenu.getCarried().copy()));
    }
    public static void rememberCreativeCursor(ServerPlayer player){
        ((cn.zgnhit.mcwuwa.mixin.MenuCursorSyncAccessor)player.inventoryMenu).mcwuwa$remoteCarried().force(player.inventoryMenu.getCarried());
    }
    public static void correctCreativeSlot(ServerPlayer player,int index){
        if(index>=0&&index<player.inventoryMenu.slots.size())player.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(
            player.inventoryMenu.containerId,player.inventoryMenu.incrementStateId(),index,player.inventoryMenu.getSlot(index).getItem().copy()));
    }
    public record Visual(int entityId, UUID instanceId, String actionId, long startTick, int durationTicks, long sequence, String transform) implements CustomPacketPayload {
        public static final Type<Visual> TYPE = new Type<>(Identifier.fromNamespaceAndPath("mc_wuwa", "visual"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Visual> CODEC = StreamCodec.of((b,p) -> {
            b.writeVarInt(p.entityId); b.writeUUID(p.instanceId); b.writeUtf(p.actionId, 160); b.writeVarLong(p.startTick);
            b.writeVarInt(p.durationTicks); b.writeVarLong(p.sequence); b.writeUtf(p.transform, 80);
        }, b -> new Visual(b.readVarInt(), b.readUUID(), b.readUtf(160), b.readVarLong(), b.readVarInt(), b.readVarLong(), b.readUtf(80)));
        @Override public Type<Visual> type() { return TYPE; }
    }
    public record Appearance(UUID actor,int entityId,String weapon,String form,long revision) implements CustomPacketPayload {
        public Appearance { new cn.zgnhit.mcwuwa.core.RoleAppearanceState.Snapshot(actor,entityId,weapon,form,revision); }
        public static final Type<Appearance> TYPE=new Type<>(Identifier.fromNamespaceAndPath("mc_wuwa","appearance"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Appearance> CODEC=StreamCodec.of((b,p)->{
            b.writeUUID(p.actor);b.writeVarInt(p.entityId);b.writeUtf(p.weapon,64);b.writeUtf(p.form,48);b.writeVarLong(p.revision);
        },b->new Appearance(b.readUUID(),b.readVarInt(),b.readUtf(64),b.readUtf(48),b.readVarLong()));
        @Override public Type<Appearance> type(){return TYPE;}
    }
    public record HazardAnchor(int entity,double x,double y,double z){
        public HazardAnchor{if(entity<0||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IllegalArgumentException("invalid hazard anchor");}
    }
    public record HazardPause(UUID owner,long start,long end,List<HazardAnchor> entities) implements CustomPacketPayload {
        public HazardPause {
            entities=List.copyOf(entities);
            if(start<0||end<start||entities.size()>1024)throw new IllegalArgumentException("invalid hazard pause");
        }
        public static final Type<HazardPause> TYPE=new Type<>(Identifier.fromNamespaceAndPath("mc_wuwa","hazard_pause"));
        public static final StreamCodec<RegistryFriendlyByteBuf,HazardPause> CODEC=StreamCodec.of((b,p)->{
            b.writeUUID(p.owner);b.writeVarLong(p.start);b.writeVarLong(p.end);b.writeVarInt(p.entities.size());
            for(var anchor:p.entities){b.writeVarInt(anchor.entity());b.writeDouble(anchor.x());b.writeDouble(anchor.y());b.writeDouble(anchor.z());}
        },b->{
            var owner=b.readUUID();long start=b.readVarLong(),end=b.readVarLong();int size=b.readVarInt();
            if(size<0||size>1024)throw new IllegalArgumentException("invalid hazard pause size");
            var entities=new ArrayList<HazardAnchor>(size);for(int i=0;i<size;i++)entities.add(new HazardAnchor(b.readVarInt(),b.readDouble(),b.readDouble(),b.readDouble()));return new HazardPause(owner,start,end,entities);
        });
        @Override public Type<HazardPause> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("6");
        registrar.playToServer(Command.TYPE, Command.CODEC, (p,context) -> { if (context.player() instanceof ServerPlayer player) PlatformRuntime.command(player, p); });
        registrar.playToServer(CreativeTeamSlot.TYPE,CreativeTeamSlot.CODEC,(p,context)->{if(context.player() instanceof ServerPlayer player)PlatformRuntime.creativeTeamSlot(player,p);});
        registrar.playToServer(CreativeEchoSlot.TYPE,CreativeEchoSlot.CODEC,(p,context)->{if(context.player() instanceof ServerPlayer player)PlatformRuntime.creativeEchoSlot(player,p);});
        registrar.playToServer(CreativeEchoCreate.TYPE,CreativeEchoCreate.CODEC,(p,context)->{if(context.player() instanceof ServerPlayer player)PlatformRuntime.creativeEchoCreate(player,p);});
        registrar.playBidirectional(CreativeCursor.TYPE,CreativeCursor.CODEC,
            (p,context)->{if(context.player() instanceof ServerPlayer player)PlatformRuntime.creativeCursor(player,p);},
            (p,context)->creativeCursorReceiver.accept(p));
        registrar.playToClient(State.TYPE, State.CODEC, (p,context) -> stateReceiver.accept(p));
        registrar.playToClient(Visual.TYPE, Visual.CODEC, (p,context) -> visualReceiver.accept(p));
        registrar.playToClient(HazardPause.TYPE,HazardPause.CODEC,(p,context)->hazardPauseReceiver.accept(p));
        registrar.playToClient(UiFeedback.TYPE,UiFeedback.CODEC,(p,context)->uiFeedbackReceiver.accept(p));
        registrar.playToClient(Impact.TYPE,Impact.CODEC,(p,context)->impactReceiver.accept(p));
        registrar.playToClient(Appearance.TYPE,Appearance.CODEC,(p,context)->appearanceReceiver.accept(p));
    }
    public static void sync(ServerPlayer player) {
        TeamAttachment attachment = ModAttachments.team(player); var state = attachment.state(); var snapshot = state.snapshot();
        JsonObject root = new JsonObject(); root.addProperty("schema", 1); root.addProperty("tick", player.level().getGameTime());
        root.addProperty("active", snapshot.activeSlot()); root.addProperty("combat", state.inCombat()); root.addProperty("stamina", snapshot.stamina().stamina());
        root.addProperty("switchCd", snapshot.switchCooldown()); root.addProperty("reviveCd", snapshot.reviveCooldown());
        root.addProperty("cinematicPaused", CinematicTimeRuntime.paused(player));
        root.addProperty("calibrated", CombatConfig.scale().measuredSwordDps().isPresent());
        root.addProperty("calibrationScope",CombatConfig.calibrationScope());
        root.addProperty("damageMappingMultiplier",CombatConfig.scale().playabilityMultiplier());
        JsonArray roles = new JsonArray();
        for (int i = 0; i < 3; i++) {
            JsonObject role = new JsonObject(); var character = state.at(i);
            if (character.isPresent()) {
                var c = character.get(); role.addProperty("weapon", c.character().weaponPath()); role.addProperty("instance", c.instanceId().toString());
                role.addProperty("hp", c.health()); role.addProperty("maxHp", c.maxHealth()); role.addProperty("energy", c.resource("energy")); role.addProperty("energyMax", c.resourceMax("energy"));
                role.addProperty("concerto", c.resource("concerto")); role.addProperty("chain", c.chain()); role.addProperty("refinement", c.refinement());
                JsonObject cds = new JsonObject(); c.snapshot().cooldowns().forEach(cds::addProperty); role.add("cooldowns", cds);
                PlatformRuntime.addRoleView(player, c, role);
            }
            roles.add(role);
        }
        root.add("roles", roles);root.addProperty("introOutroReady",attachment.state().introOutroReady());PlatformRuntime.addTargetView(player, root);PlatformRuntime.addNearbyTuneView(player,root);
        // HP/cooldown deadlines already travel in roles. Writing them into CUSTOM_DATA
        // every five ticks also changes vanilla menu slot revisions and hand equipment.
        // Checkpoint runtime state only on save/detach, never as a side effect of a HUD packet.
        List<ItemStack> weapons = new ArrayList<>(); for (int i = 0; i < 3; i++) weapons.add(attachment.inventory().getItem(i).copy());
        PacketDistributor.sendToPlayer(player, new State(root.toString(), List.copyOf(weapons)));
        syncAppearance(player);
    }
    private static Appearance currentAppearance(ServerPlayer actor) {
        var role=ModAttachments.team(actor).state().active().filter(CharacterInstance::alive);
        String weapon="",form="";
        if(actor.isAlive()&&!actor.isSpectator()&&role.isPresent()) {
            weapon=role.get().character().weaponPath();form=PlatformRuntime.mechanicsView(actor,role.get()).form();
        }
        var previous=lastAppearances.get(actor.getUUID());
        if(previous!=null&&previous.entityId()==actor.getId()&&previous.weapon().equals(weapon)&&previous.form().equals(form))return previous;
        var packet=new Appearance(actor.getUUID(),actor.getId(),weapon,form,++appearanceSequence);
        lastAppearances.put(actor.getUUID(),packet);return packet;
    }
    private static void syncAppearance(ServerPlayer actor) {
        Appearance old=lastAppearances.get(actor.getUUID()),current=currentAppearance(actor);
        if(old!=current)PacketDistributor.sendToPlayersTrackingEntityAndSelf(actor,current);
    }
    public static void visual(ServerPlayer player, Visual visual) {
        CinematicTimeRuntime.visual(player,visual);
        long revision = visualRevisions.merge(player.getUUID(), 1L, Long::sum);
        var packet = new Visual(visual.entityId(), visual.instanceId(), visual.actionId(), visual.startTick(), visual.durationTicks(), revision, visual.transform());
        if (visual.actionId().equals("@form")) lastForms.put(player.getUUID(), packet); else lastActions.put(player.getUUID(), packet);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, packet);
    }
    static void cancelCinematicVisual(ServerPlayer player){
        var previous=lastActions.get(player.getUUID());
        if(previous!=null&&cn.zgnhit.mcwuwa.core.CinematicTimeRules.cinematic(previous.actionId()))
            visual(player,new Visual(player.getId(),previous.instanceId(),"",player.level().getGameTime(),0,0,""));
    }
    static void hazardPause(ServerPlayer owner,HazardPause packet){
        if(!owner.isRemoved())PacketDistributor.sendToPlayersTrackingEntityAndSelf(owner,packet);
    }
    public static void tracking(ServerPlayer observer, ServerPlayer actor) {
        syncAppearance(actor);PacketDistributor.sendToPlayer(observer,lastAppearances.get(actor.getUUID()));
        var form = lastForms.get(actor.getUUID()); if (form != null) PacketDistributor.sendToPlayer(observer, form);
        var action = lastActions.get(actor.getUUID());
        if (action != null && actor.level().getGameTime() < action.startTick() + action.durationTicks()) PacketDistributor.sendToPlayer(observer, action);
        var pause=CinematicTimeRuntime.hazardSnapshot(actor);if(pause!=null)PacketDistributor.sendToPlayer(observer,pause);
    }
    public static void forget(UUID player) { lastActions.remove(player); lastForms.remove(player); visualRevisions.remove(player);impactBudgets.remove(player);lastAppearances.remove(player); }
    private Network() { }
}
