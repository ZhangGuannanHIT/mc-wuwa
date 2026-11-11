package cn.zgnhit.mcwuwa.mixin.client;

import cn.zgnhit.mcwuwa.client.InventoryControls;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Consume only the matching release of an explicitly handled team-slot button press. */
@Mixin({AbstractContainerScreen.class,CreativeModeInventoryScreen.class})
public abstract class TeamInventoryScreenMixin {
    @Inject(method="mouseReleased",at=@At("HEAD"),cancellable=true)
    private void wuwa$handledWeaponRelease(double mouseX,double mouseY,int button,CallbackInfoReturnable<Boolean> result) {
        if(InventoryControls.consumeWeaponRelease((Screen)(Object)this,button)) {
            ((Screen)(Object)this).setDragging(false);result.setReturnValue(true);
        }
    }
}
