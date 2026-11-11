package cn.zgnhit.mcwuwa.mixin;

import cn.zgnhit.mcwuwa.core.CharacterId;
import cn.zgnhit.mcwuwa.platform.ModAttachments;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(net.minecraft.world.item.ElytraItem.class)
public abstract class CarlottaElytraMixin {
    @WrapOperation(method = "elytraFlightTick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"))
    private void mcWuwa$reduceElytraWear(ItemStack stack, int amount, LivingEntity wearer,
                                        EquipmentSlot slot, Operation<Void> original) {
        if (wearer instanceof ServerPlayer player && stack.is(Items.ELYTRA)) {
            var team = ModAttachments.team(player).state();
            for (int index = 0; index < 3; index++) {
                var role = team.at(index).orElse(null);
                if (role != null && role.character() == CharacterId.CARLOTTA) {
                    if (player.getRandom().nextInt(5) == 0) return;
                    break;
                }
            }
        }
        original.call(stack, amount, wearer, slot);
    }
}
