package cn.zgnhit.mcwuwa.mixin.client;

import cn.zgnhit.mcwuwa.platform.ModAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Creative's SlotWrapper delegates here. Keep its index/data, hide the three duplicated native cells. */
@Mixin(Slot.class)
public abstract class CreativeTeamSlotVisibilityMixin {
    @Shadow @Final public Container container;
    @Inject(method="isActive",at=@At("HEAD"),cancellable=true)
    private void wuwa$managedCreativeSlots(CallbackInfoReturnable<Boolean> result) {
        var game=Minecraft.getInstance();
        if(game.player!=null&&game.gui.screen() instanceof CreativeModeInventoryScreen
            &&container==ModAttachments.team(game.player).inventory())result.setReturnValue(false);
    }
}
