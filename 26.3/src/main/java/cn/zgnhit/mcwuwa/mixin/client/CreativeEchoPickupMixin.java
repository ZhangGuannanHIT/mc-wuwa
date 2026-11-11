package cn.zgnhit.mcwuwa.mixin.client;

import cn.zgnhit.mcwuwa.client.CombatClient;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native creative clicks own their cursor, including local-only deletion.
 * Echo catalogue previews additionally request a freshly rolled physical item. */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeEchoPickupMixin {
    @Inject(method="slotClicked",at=@At("RETURN"))
    private void wuwa$rollPickedEcho(Slot slot,int index,int button,ContainerInput input,CallbackInfo ci) {
        var screen=(CreativeModeInventoryScreen)(Object)this;
        CombatClient.creativeCursorChanged(screen.getMenu().getCarried());
    }
}
