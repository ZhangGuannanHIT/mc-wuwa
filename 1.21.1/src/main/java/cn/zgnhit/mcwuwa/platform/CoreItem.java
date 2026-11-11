package cn.zgnhit.mcwuwa.platform;

import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;


/** Survival acquisition guidance; recipe discovery is handled by the vanilla advancement. */
public final class CoreItem extends Item {
    public CoreItem(Properties properties) { super(properties); }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("击败阿列夫一或鸣潮首领获得，用于工作台合成鸣潮武器。"));
        tooltip.add(Component.literal("获得后解锁全部 " + cn.zgnhit.mcwuwa.core.CharacterId.values().length + " 种武器配方，可在配方书查看所需材料。"));
    }
}
