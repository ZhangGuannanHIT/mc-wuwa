package cn.zgnhit.mcwuwa.mixin;
import cn.zgnhit.mcwuwa.platform.ModAttachments;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin extends AbstractContainerMenu {
    protected InventoryMenuMixin(MenuType<?> type, int id) { super(type, id); }
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void wuwa$quickUnequip(Player player, int slotId, CallbackInfoReturnable<ItemStack> cir) {
        var team = ModAttachments.team(player);
        if (slotId < 0 || slotId >= slots.size() || slots.get(slotId).container != team.inventory()) return;
        if (team.state().inCombat()) { cir.setReturnValue(ItemStack.EMPTY); return; }
        int destination = player.getInventory().getFreeSlot();
        if (destination < 0) { cir.setReturnValue(ItemStack.EMPTY); return; }
        int source = slots.get(slotId).getContainerSlot(); ItemStack result = team.inventory().removeItemNoUpdate(source);
        player.getInventory().setItem(destination, result); cir.setReturnValue(result.copy());
    }
    @Inject(method = "<init>", at = @At("RETURN"))
    private void wuwa$teamSlots(Inventory inventory, boolean active, Player player, CallbackInfo ci) {
        var team = ModAttachments.team(player);
        for (int i = 0; i < 3; i++) {
            // Slot already has a public `index` field (46..48); never capture a local with that name.
            int teamIndex = i;
            addSlot(new Slot(team.inventory(), i, 182, 17 + i * 21) {
                @Override public boolean mayPlace(ItemStack stack) { return team.canEquip(teamIndex, stack); }
                @Override public boolean mayPickup(Player actor) { return !team.state().inCombat(); }
                @Override public int getMaxStackSize() { return 1; }
            });
        }
    }
}
