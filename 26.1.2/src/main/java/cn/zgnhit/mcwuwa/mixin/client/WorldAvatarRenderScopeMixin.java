package cn.zgnhit.mcwuwa.mixin.client;

import cn.zgnhit.mcwuwa.client.presentation.AvatarRenderScope;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;

/** Scope ends on both normal return and renderer exceptions, without detecting optional mods by name. */
@Mixin(LevelRenderer.class)
public abstract class WorldAvatarRenderScopeMixin {
    @WrapMethod(method="submitEntities")
    private void mcWuwa$worldBodies(PoseStack poses,LevelRenderState state,SubmitNodeCollector collector,Operation<Void> original) {
        AvatarRenderScope.enterWorld();
        try{original.call(poses,state,collector);}finally{AvatarRenderScope.leaveWorld();}
    }
}
