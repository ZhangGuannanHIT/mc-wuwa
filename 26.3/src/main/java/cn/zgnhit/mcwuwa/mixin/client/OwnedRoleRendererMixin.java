package cn.zgnhit.mcwuwa.mixin.client;

import cn.zgnhit.mcwuwa.client.IntegrationVisualClient;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Uses the native renderer for a bundled role in inventory/riding/idle, and the connected
 * combat rig in a world action. Only arbitration with the installed YSM model is bypassed;
 * vanilla layers still render and account skins/other players remain untouched. */
@Mixin(AvatarRenderer.class)
public abstract class OwnedRoleRendererMixin {
    @WrapOperation(method="submit(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at=@At(value="INVOKE",target="Lnet/neoforged/bus/api/IEventBus;post(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;"))
    private Event mcWuwa$ownBundledRole(IEventBus bus,Event event,Operation<Event> original){
        if(event instanceof RenderPlayerEvent.Pre<?> pre&&IntegrationVisualClient.ownPlayer(pre.getRenderState())){
            IntegrationVisualClient.submitOwned(pre);return event;
        }
        return original.call(bus,event);
    }
}
