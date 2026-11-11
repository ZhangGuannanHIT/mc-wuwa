package cn.zgnhit.mcwuwa.mixin;
import cn.zgnhit.mcwuwa.platform.ModAttachments;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Slot.class)
public abstract class SlotMixin {
    @Shadow @Final public Container container;
    @Shadow public abstract int getContainerSlot();
    @Unique private boolean wuwa$reference() { return container instanceof Inventory inv && getContainerSlot() == 0 && ModAttachments.team(inv.player).hasTeam(); }
    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void wuwa$noPickup(Player player, CallbackInfoReturnable<Boolean> cir) { if (wuwa$reference()) cir.setReturnValue(false); }
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void wuwa$noPlace(ItemStack stack, CallbackInfoReturnable<Boolean> cir) { if (wuwa$reference()) cir.setReturnValue(false); }
}
