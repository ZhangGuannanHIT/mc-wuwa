package cn.zgnhit.mcwuwa.mixin.client;

import com.mojang.blaze3d.platform.Window;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Explicit development option: create a real GPU window without displaying or focusing it. */
@Mixin(Window.class)
public abstract class BackgroundWindowMixin {
    @Inject(method = "syncWindow", at = @At("RETURN"))
    private void mcWuwa$backgroundWindow(org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
        if (Boolean.getBoolean("mc_wuwa.backgroundClient")) {
            org.lwjgl.sdl.SDLVideo.SDL_HideWindow(((Window)(Object)this).handle());
        }
    }
}