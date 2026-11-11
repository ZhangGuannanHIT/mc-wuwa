package cn.zgnhit.mcwuwa.mixin.client;

import cn.zgnhit.mcwuwa.client.presentation.AvatarRenderScope;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/** Scope ends on both normal return and renderer exceptions, without detecting optional mods by name. */
@Mixin(LevelRenderer.class)
public abstract class WorldAvatarRenderScopeMixin {
    @WrapMethod(method="renderEntity")
    private void mcWuwa$worldBodies(Entity entity,double x,double y,double z,float partial,PoseStack poses,MultiBufferSource buffers,Operation<Void> original) {
        AvatarRenderScope.enterWorld();
        try{original.call(entity,x,y,z,partial,poses,buffers);}finally{AvatarRenderScope.leaveWorld();}
    }
}
