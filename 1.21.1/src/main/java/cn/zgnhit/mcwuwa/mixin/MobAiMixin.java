package cn.zgnhit.mcwuwa.mixin;

import cn.zgnhit.mcwuwa.platform.PlatformRuntime;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pauses attack and navigation goals only for the server-owned bounded stagger interval. */
@Mixin(Mob.class)
public abstract class MobAiMixin {
    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void mcwuwa$stagger(CallbackInfo ci) {
        if (PlatformRuntime.aiInterrupted((Mob)(Object)this)) ci.cancel();
    }
}
