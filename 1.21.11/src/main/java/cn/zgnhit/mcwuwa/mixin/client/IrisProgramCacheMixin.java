package cn.zgnhit.mcwuwa.mixin.client;

import com.mojang.blaze3d.opengl.GlProgram;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Iris can unbind a program outside the vanilla encoder's cache. A cached Java
 * object then skips glUseProgram while sampler uniforms are sent to program0.
 * Rebind through the original encoder; no raw GL calls or shader replacement. */
@Mixin(targets="com.mojang.blaze3d.opengl.GlCommandEncoder")
public abstract class IrisProgramCacheMixin {
    @Shadow private GlProgram lastProgram;
    @Unique private static Boolean mcWuwa$irisLoaded;
    @Inject(method="trySetup",at=@At("HEAD"))
    private void mcWuwa$invalidateExternalProgramCache(CallbackInfoReturnable<Boolean> callback) {
        if(mcWuwa$irisLoaded==null) {
            var mods=ModList.get();if(mods==null)return;
            mcWuwa$irisLoaded=mods.isLoaded("iris");
        }
        if(mcWuwa$irisLoaded)lastProgram=null;
    }
}
