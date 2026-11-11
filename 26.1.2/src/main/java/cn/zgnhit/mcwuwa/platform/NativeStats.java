package cn.zgnhit.mcwuwa.platform;
import cn.zgnhit.mcwuwa.McWuwa;
import cn.zgnhit.mcwuwa.core.CharacterInstance;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
public record NativeStats(double health, double attack, double defense, double critRate, double critDamage,
                          double energyRegen, Map<String, Double> bonuses) {
    public static NativeStats of(CharacterInstance role, ItemStack weapon) {
        return of(role, weapon, Map.of());
    }
    public static NativeStats of(CharacterInstance role, ItemStack weapon, Map<String, Double> extra) {
        var definition = McWuwa.catalog().requireByWeapon(role.character().weaponPath());
        Map<String, Double> stats = new HashMap<>();
        definition.skillTreeModifiers().forEach((key, value) -> stats.merge(key, value, Double::sum));
        for (var s : definition.weapon().stats()) if (s.isPercent()) {
            String key = switch (s.name()) { case "攻击" -> "attack_pct"; case "生命" -> "health_pct"; case "防御" -> "defense_pct";
                case "暴击" -> "crit_rate"; case "暴击伤害" -> "crit_damage"; case "共鸣效率" -> "energy_regen"; default -> "unsupported:" + s.name(); };
            stats.merge(key, s.value(), Double::sum);
        }
        CompoundTag saved = WeaponData.tag(weapon);
        for (int i = 0; i < 5; i++) {
            CompoundTag echo = saved.getCompoundOrEmpty("echo_" + i);
            for (String section : List.of("main_stats", "sub_stats")) {
                CompoundTag values = echo.getCompoundOrEmpty(section);
                values.keySet().forEach(key -> stats.merge(key, values.getDoubleOr(key, 0), Double::sum));
            }
        }
        var passive = new cn.zgnhit.mcwuwa.content.mechanics.WeaponPassiveEngine();
        SonataRuntime.permanent(role, weapon).forEach((key, value) -> stats.merge(key, value, Double::sum));
        EchoRuntime.mainModifiers(role, weapon).forEach((key, value) -> stats.merge(key, value, Double::sum));
        RolePassiveRuntime.permanentModifiers(role, weapon).forEach((key, value) -> stats.merge(key, value, Double::sum));
        for (var buff : passive.permanent(definition, role.refinement(), true)) stats.merge(buff.attribute(), buff.value(), Double::sum);
        extra.forEach((key, value) -> stats.merge(key, value, Double::sum));
        var base = definition.baseStats();
        return new NativeStats(new cn.zgnhit.mcwuwa.core.CombatMath.Stat(base.health(),stats.getOrDefault("health_flat",0d),stats.getOrDefault("health_pct",0d)).effective(),
            new cn.zgnhit.mcwuwa.core.CombatMath.Stat(base.attack()+definition.weapon().baseAttack(),stats.getOrDefault("attack_flat",0d),stats.getOrDefault("attack_pct",0d)).effective(),
            new cn.zgnhit.mcwuwa.core.CombatMath.Stat(base.defense(),stats.getOrDefault("defense_flat",0d),stats.getOrDefault("defense_pct",0d)).effective(),
            Math.clamp(.05 + stats.getOrDefault("crit_rate", 0d), 0, 1), 1.5 + stats.getOrDefault("crit_damage", 0d),
            Math.max(0,1+stats.getOrDefault("energy_regen",0d)),Map.copyOf(stats));
    }
    /** Panel/threshold value; the critRate component remains the bounded hit probability. */
    public double uncappedCritRate(){return Math.max(0,.05+bonus("crit_rate"));}
    public double bonus(String key) { return bonuses.getOrDefault(key, 0d); }
}
