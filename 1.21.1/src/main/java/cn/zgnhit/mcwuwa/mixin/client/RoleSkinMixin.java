package cn.zgnhit.mcwuwa.mixin.client;

import cn.zgnhit.mcwuwa.client.RoleSkinRegistry;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Standard Minecraft skin access covers all native views without replacing player models. */
@Mixin(AbstractClientPlayer.class)
public abstract class RoleSkinMixin {
    @Inject(method="getSkin",at=@At("RETURN"),cancellable=true)
    private void mcWuwa$roleSkin(CallbackInfoReturnable<PlayerSkin> callback) {
        var original=callback.getReturnValue();var selected=RoleSkinRegistry.resolve((AbstractClientPlayer)(Object)this,original);
        if(selected!=original)callback.setReturnValue(selected);
    }
}
