package cn.zgnhit.mcwuwa.mixin;
import cn.zgnhit.mcwuwa.platform.ModAttachments;
import cn.zgnhit.mcwuwa.platform.WeaponItem;
import cn.zgnhit.mcwuwa.platform.EchoData;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AbstractContainerMenu.class)
public abstract class MenuClickMixin {
    @Shadow @Final public NonNullList<Slot> slots;
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void wuwa$guard(int slotId, int button, ContainerInput input, Player player, CallbackInfo ci) {
        // Physical equipment keeps its UUID in every vanilla container. Catalogue echo
        // templates remain clonable selectors; an already rolled echo cannot be copied.
        if (input == ContainerInput.CLONE && slotId >= 0 && slotId < slots.size()
            && (slots.get(slotId).getItem().getItem() instanceof WeaponItem
                || EchoData.valid(slots.get(slotId).getItem()))) { ci.cancel(); return; }
        if (!ModAttachments.team(player).hasTeam()) return;
        if (input == ContainerInput.SWAP && button == 0) { ci.cancel(); return; }
        if (slotId >= 0 && slotId < slots.size()) {
            Slot slot = slots.get(slotId);
            if (slot.container == ModAttachments.team(player).inventory() && ModAttachments.team(player).state().inCombat()) { ci.cancel(); return; }
            if (slot.container instanceof Inventory inventory && inventory.player == player && slot.getContainerSlot() == 0) ci.cancel();
        }
    }
}
