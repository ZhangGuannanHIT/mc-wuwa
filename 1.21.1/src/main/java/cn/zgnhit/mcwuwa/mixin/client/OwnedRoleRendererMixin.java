package cn.zgnhit.mcwuwa.mixin.client;

import cn.zgnhit.mcwuwa.client.IntegrationVisualClient;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Uses the native renderer for a bundled role in inventory/riding/idle, and the connected
 * combat rig in a world action. Only arbitration with the installed YSM model is bypassed;
 * vanilla layers still render and account skins/other players remain untouched. */
@Mixin(PlayerRenderer.class)
public abstract class OwnedRoleRendererMixin {
    @WrapOperation(method="render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at=@At(value="INVOKE",target="Lnet/neoforged/bus/api/IEventBus;post(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;"))
    private Event mcWuwa$ownBundledRole(IEventBus bus,Event event,Operation<Event> original){
        if(event instanceof RenderPlayerEvent.Pre pre&&IntegrationVisualClient.ownPlayer(new cn.zgnhit.mcwuwa.client.presentation.AvatarPoseState((net.minecraft.client.player.AbstractClientPlayer)pre.getEntity(),pre.getPartialTick(),pre.getPackedLight()))){
            IntegrationVisualClient.submitOwned(pre);return event;
        }
        return original.call(bus,event);
    }
}
