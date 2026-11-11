package cn.zgnhit.mcwuwa.platform;

import cn.zgnhit.mcwuwa.core.CharacterId;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;


public final class WeaponItem extends Item {
    private final CharacterId character;
    public WeaponItem(CharacterId character, Properties properties) { super(properties); this.character = character; }
    public CharacterId character() { return character; }
    // NeoForge's transformed Item implements IItemExtension; the raw vanilla API
    // used by the secondary offline compiler does not contain that injected bridge.
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        if (slotChanged || oldStack.getItem() != newStack.getItem()) return true;
        String oldId=WeaponData.tag(oldStack).getString("instance");
        // A saved health/cooldown or loadout update is still the same physical weapon.
        // Unidentified catalogue items retain native behavior; a different UUID really is a swap.
        return oldId.isEmpty() ? oldStack!=newStack : !oldId.equals(WeaponData.tag(newStack).getString("instance"));
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flag) {
        var data = WeaponData.tag(stack);
        tooltip.add(Component.literal("C" + data.getInt("chain") + " · S" + cn.zgnhit.mcwuwa.platform.NbtValues.getInt(data,"refinement", 1)));
        tooltip.add(Component.literal("放入背包右侧队伍栏以启用角色；未编队时为普通武器。"));
    }
}
