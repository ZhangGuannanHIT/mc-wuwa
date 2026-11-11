package cn.zgnhit.mcwuwa.mixin;
import cn.zgnhit.mcwuwa.platform.ModAttachments;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.core.NonNullList;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
@Mixin(Inventory.class)
public abstract class InventoryMixin {
    @Shadow @Final public Player player;
    @Shadow public abstract int getSelectedSlot();
    @Shadow public abstract NonNullList<ItemStack> getNonEquipmentItems();
    @Shadow public abstract boolean add(int slot, ItemStack stack);
    @Inject(method = "getSuitableHotbarSlot", at = @At("HEAD"), cancellable = true)
    private void wuwa$pickDestination(CallbackInfoReturnable<Integer> cir) {
        if (!ModAttachments.team(player).hasTeam()) return;
        var raw = getNonEquipmentItems(); int selected = getSelectedSlot();
        for (int i = 0; i < 9; i++) { int slot = (selected + i) % 9; if (slot != 0 && raw.get(slot).isEmpty()) { cir.setReturnValue(slot); return; } }
        for (int i = 0; i < 9; i++) { int slot = (selected + i) % 9; if (slot != 0 && !raw.get(slot).isEnchanted()) { cir.setReturnValue(slot); return; } }
        cir.setReturnValue(selected == 0 ? 1 : selected);
    }
    @Inject(method = "pickSlot", at = @At("HEAD"), cancellable = true)
    private void wuwa$noPickReference(int sourceSlot, CallbackInfo ci) {
        if (sourceSlot == 0 && ModAttachments.team(player).hasTeam()) ci.cancel();
    }
    @Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void wuwa$redirectInsertion(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (slot == 0 && ModAttachments.team(player).hasTeam()) cir.setReturnValue(add(-1, stack));
    }
    @WrapMethod(method = "getFreeSlot")
    private int wuwa$freeSlot(Operation<Integer> original) {
        // Wrap the completed selection: another mod's cancellable HEAD callback
        // can bypass our own HEAD/RETURN injections. Preserve any valid selection.
        int selected=original.call();
        if(selected!=0||!ModAttachments.team(player).hasTeam())return selected;
        var raw=getNonEquipmentItems();
        for(int slot=1;slot<raw.size();slot++)if(raw.get(slot).isEmpty())return slot;
        return -1;
    }
    @Inject(method = "removeFromSelected", at = @At("HEAD"), cancellable = true)
    private void wuwa$selectedDrop(boolean wholeStack, CallbackInfoReturnable<ItemStack> cir) {
        if (getSelectedSlot() == 0 && ModAttachments.team(player).hasTeam()) cir.setReturnValue(ItemStack.EMPTY);
    }
    @Inject(method = "setSelectedItem", at = @At("HEAD"), cancellable = true)
    private void wuwa$selectedSet(ItemStack incoming, CallbackInfoReturnable<ItemStack> cir) {
        if (getSelectedSlot() == 0 && ModAttachments.team(player).hasTeam()) cir.setReturnValue(ItemStack.EMPTY);
    }
    @Inject(method = "setItem", at = @At("HEAD"), cancellable = true)
    private void wuwa$protectedWrite(int slot, ItemStack incoming, CallbackInfo ci) {
        if (slot == 0 && ModAttachments.team(player).hasTeam()) ci.cancel();
    }
    @Inject(method = "getItem", at = @At("HEAD"), cancellable = true)
    private void wuwa$view(int slot, CallbackInfoReturnable<ItemStack> cir) {
        if (slot == 0) { var team = ModAttachments.team(player); if (team.hasTeam()) cir.setReturnValue(team.activeStack()); }
    }
    @Inject(method = "getSelectedItem", at = @At("HEAD"), cancellable = true)
    private void wuwa$hand(CallbackInfoReturnable<ItemStack> cir) {
        if (getSelectedSlot() == 0) { var team = ModAttachments.team(player); if (team.hasTeam()) cir.setReturnValue(team.activeStack()); }
    }
    @Inject(method = "removeItem(II)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"), cancellable = true)
    private void wuwa$noDrop(int slot, int count, CallbackInfoReturnable<ItemStack> cir) {
        if (slot == 0 && ModAttachments.team(player).hasTeam()) cir.setReturnValue(ItemStack.EMPTY);
    }
    @Inject(method = "removeItemNoUpdate", at = @At("HEAD"), cancellable = true)
    private void wuwa$noRemove(int slot, CallbackInfoReturnable<ItemStack> cir) {
        if (slot == 0 && ModAttachments.team(player).hasTeam()) cir.setReturnValue(ItemStack.EMPTY);
    }
}
