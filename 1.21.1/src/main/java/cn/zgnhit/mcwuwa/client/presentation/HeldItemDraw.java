package cn.zgnhit.mcwuwa.client.presentation;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class HeldItemDraw {
    private ItemStack stack = ItemStack.EMPTY;
    private ItemDisplayContext display;
    private LivingEntity owner;

    public void set(ItemStack stack, ItemDisplayContext display, LivingEntity owner) {
        this.stack = stack;
        this.display = display;
        this.owner = owner;
    }

    public boolean isEmpty() { return stack.isEmpty(); }

    public void submit(PoseStack poses, GeometryBuffers buffers, int light, int overlay, int seed) {
        if (!stack.isEmpty()) {
            Minecraft.getInstance().getItemRenderer().renderStatic(owner, stack, display, (display==ItemDisplayContext.FIRST_PERSON_LEFT_HAND || display==ItemDisplayContext.THIRD_PERSON_LEFT_HAND),
                poses, buffers.source(), owner.level(), light, overlay, seed);
        }
    }
}
