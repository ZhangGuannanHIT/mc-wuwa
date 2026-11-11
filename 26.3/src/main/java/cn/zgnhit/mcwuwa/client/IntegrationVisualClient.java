package cn.zgnhit.mcwuwa.client;

import cn.zgnhit.mcwuwa.client.presentation.PresentationClient;
import cn.zgnhit.mcwuwa.platform.ClientVisualConfig;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/** Own only the bundled role's player-render arbitration; never edits another mod's settings.
 * Native rendering still supplies armor/cape/held items outside an active combat pose.
 * The optional YSM replacement Pre event is bypassed only for this mod's selected skins. */
public final class IntegrationVisualClient {
    private static int suppressedIndicators,suppressedPortraits,ownedPlayerSubmissions;
    private static int claimedIrisReloads,allowedIrisReloads;
    private IntegrationVisualClient() { }
    /** IPN2.3.5's Kotlin config singletons cycle if a GUI first initializes Features.
     * Initialize its public screen-settings root on the normal client setup thread;
     * this changes neither option values nor third-party files. */
    public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        if(!ModList.get().isLoaded("inventoryprofilesnext"))return;
        event.enqueueWork(()->{
            try{Class.forName("org.anti_ad.mc.ipnext.config.ConfigScreenSettings",true,IntegrationVisualClient.class.getClassLoader());}
            catch(ClassNotFoundException|LinkageError failure){cn.zgnhit.mcwuwa.McWuwa.LOGGER.warn("Optional IPN configuration startup could not be ordered",failure);}
        });
    }
    public static boolean quietOverlays(){return ClientVisualConfig.QUIET_INTEGRATION_OVERLAYS.get()&&CombatClient.hasTeam()&&CombatClient.wuwaMode();}
    public static boolean ownPlayer(AvatarRenderState state){
        if(!ClientVisualConfig.COMBAT_BODY_MODEL.get()
            ||!ModList.get().isLoaded("yes_steve_model")||state.skin==null)return false;
        if(cn.zgnhit.mcwuwa.client.presentation.PresentationClient.hasTransformFrame(state))return true;
        if(!ClientVisualConfig.AUTOMATIC_ROLE_SKINS.get())return false;
        var texture=state.skin.body().texturePath();
        return texture.getNamespace().equals("mc_wuwa")&&texture.getPath().startsWith("textures/entity/characters/");
    }
    public static void submitOwned(RenderPlayerEvent.Pre<?> event){ownedPlayerSubmissions++;PresentationClient.submitOwnedPlayer(event);}
    public static boolean suppressRefillIndicators(){if(!quietOverlays())return false;suppressedIndicators++;return true;}
    public static void guiLayer(RenderGuiLayerEvent.Pre event){
        var id=event.getName();String path=id.getPath();
        if(quietOverlays()&&id.getNamespace().equals("yes_steve_model")
            &&(path.contains("extra")||path.contains("player"))&&!path.contains("loading")){
            suppressedPortraits++;event.setCanceled(true);
        }
    }
    public static int suppressedIndicators(){return suppressedIndicators;}
    public static int suppressedPortraits(){return suppressedPortraits;}
    public static int ownedPlayerSubmissions(){return ownedPlayerSubmissions;}
    public static void irisReload(boolean claimed){if(claimed)claimedIrisReloads++;else allowedIrisReloads++;}
    public static int claimedIrisReloads(){return claimedIrisReloads;}
    public static int allowedIrisReloads(){return allowedIrisReloads;}
}
