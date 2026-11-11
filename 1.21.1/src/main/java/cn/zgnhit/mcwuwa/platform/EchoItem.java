package cn.zgnhit.mcwuwa.platform;

import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;


/** One registered item; species face and rolled attributes belong to each physical instance. */
public final class EchoItem extends Item {
    private static final Map<String,String> LABELS=Map.ofEntries(Map.entry("crit_rate","暴击率"),Map.entry("crit_damage","暴击伤害"),
        Map.entry("attack_pct","攻击力"),Map.entry("attack_flat","攻击力"),Map.entry("health_pct","生命值"),Map.entry("health_flat","生命值"),
        Map.entry("defense_pct","防御力"),Map.entry("defense_flat","防御力"),Map.entry("energy_regen","共鸣效率"),Map.entry("healing_bonus","治疗加成"),
        Map.entry("basic_bonus","普攻伤害加成"),Map.entry("heavy_bonus","重击伤害加成"),Map.entry("skill_bonus","共鸣技能伤害加成"),Map.entry("liberation_bonus","共鸣解放伤害加成"),
        Map.entry("glacio_bonus","冷凝伤害加成"),Map.entry("fusion_bonus","热熔伤害加成"),Map.entry("electro_bonus","导电伤害加成"),
        Map.entry("aero_bonus","气动伤害加成"),Map.entry("spectro_bonus","衍射伤害加成"),Map.entry("havoc_bonus","湮灭伤害加成"));
    public EchoItem(Properties properties){super(properties);}
    @Override public void inventoryTick(ItemStack stack,net.minecraft.world.level.Level level,Entity owner,int slot,boolean selected){if(!level.isClientSide) EchoData.restoreAppearance(stack);}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,java.util.List<Component> tooltip,TooltipFlag flag){
        var data=EchoData.data(stack);if(!data.contains("species"))return;
        tooltip.add(Component.literal("COST "+data.getInt("cost")));
        if(EchoData.templateSpecies(stack).isPresent()) {tooltip.add(Component.literal("取出后随机生成 2 项主属性与 5 项副属性"));return;}
        for(String section:List.of("main_stats","sub_stats")) {
            tooltip.add(Component.literal(section.equals("main_stats")?"主属性":"副属性"));var stats=data.getCompound(section);
            for(String key:stats.getAllKeys().stream().sorted().toList()) {
                double value=stats.getDouble(key);String number=key.endsWith("_flat")?String.format(Locale.ROOT,"%.0f",value):String.format(Locale.ROOT,"%.1f%%",value*100);
                tooltip.add(Component.literal("  "+LABELS.getOrDefault(key,key)+" +"+number));
            }
        }
    }
}
