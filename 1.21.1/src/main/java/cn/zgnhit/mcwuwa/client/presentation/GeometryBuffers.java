package cn.zgnhit.mcwuwa.client.presentation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.BiConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

public record GeometryBuffers(MultiBufferSource source) {
    public void submitCustomGeometry(PoseStack poses, RenderType type, BiConsumer<PoseStack.Pose, VertexConsumer> draw) {
        draw.accept(poses.last(), source.getBuffer(type));
    }
}
