package cn.zgnhit.mcwuwa.core;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.DoubleUnaryOperator;

/** Numeric settlement only. The server adapter performs target/phase validation before invoking it. */
public final class CombatResolver {
    public enum Stage { NATIVE_DEFENSE, ELEMENT_RESISTANCE, GLOBAL_SCALE, VANILLA_PROTECTION }
    public record Mitigation(int attackerLevel, double defense, double defenseReduction,
                             double defenseIgnore, double effectiveResistance) {
        public Mitigation {
            if (attackerLevel < 1) throw new IllegalArgumentException("attacker level");
            Checks.nonnegative("defense", defense); Checks.fraction("defenseReduction", defenseReduction);
            Checks.fraction("defenseIgnore", defenseIgnore); Checks.finite("resistance", effectiveResistance);
        }
    }
    public record NormalDamage(double scalingAttribute, double skillRatio, double ratioBonus,
                               double flatDamage, double additiveBonus, List<Double> independentMultipliers,
                               boolean critical, double critMultiplier) {
        public NormalDamage {
            Checks.nonnegative("scalingAttribute", scalingAttribute); Checks.nonnegative("skillRatio", skillRatio);
            Checks.finite("ratioBonus", ratioBonus); Checks.finite("flatDamage", flatDamage);
            Checks.finite("additiveBonus", additiveBonus); Checks.positive("critMultiplier", critMultiplier);
            independentMultipliers = List.copyOf(independentMultipliers);
            independentMultipliers.forEach(v -> Checks.nonnegative("independent multiplier", v));
        }
        public double preDefense() {
            double base = Math.max(0, scalingAttribute * skillRatio * Math.max(0, 1 + ratioBonus) + flatDamage);
            double value = base * Math.max(0, 1 + additiveBonus) * (critical ? critMultiplier : 1);
            for (double m : independentMultipliers) value *= m;
            return Checks.nonnegative("calculated native damage", value);
        }
    }
    /** A stage-tagged amount prevents a platform hurt event from applying the same defense twice. */
    public record Amount(double value, Set<Stage> stages, boolean critical) {
        public Amount { Checks.nonnegative("damage", value); stages = Set.copyOf(stages); }
        public boolean applied(Stage stage) { return stages.contains(stage); }
    }
    public Amount nativeDamage(double verifiedPreDefense, boolean critical, Element element,
                               Mitigation mitigation, CombatMath.DamageScale scale) {
        Checks.nonnegative("native damage", verifiedPreDefense);
        Objects.requireNonNull(element); Objects.requireNonNull(mitigation); Objects.requireNonNull(scale);
        double amount = verifiedPreDefense * CombatMath.defenseFactor(mitigation.attackerLevel,
            mitigation.defense, mitigation.defenseReduction, mitigation.defenseIgnore)
            * CombatMath.resistanceFactor(element, mitigation.effectiveResistance) * scale.value();
        return new Amount(amount, EnumSet.of(Stage.NATIVE_DEFENSE, Stage.ELEMENT_RESISTANCE, Stage.GLOBAL_SCALE), critical);
    }
    /** Vanilla physical damage remains in MC units. Void/forced death bypasses this path entirely. */
    public Amount vanillaDefense(Amount input, Mitigation mitigation) {
        Objects.requireNonNull(input); Objects.requireNonNull(mitigation);
        if (input.applied(Stage.NATIVE_DEFENSE)) return input;
        var stages = EnumSet.noneOf(Stage.class); stages.addAll(input.stages); stages.add(Stage.NATIVE_DEFENSE);
        return new Amount(input.value * CombatMath.defenseFactor(mitigation.attackerLevel, mitigation.defense,
            mitigation.defenseReduction, mitigation.defenseIgnore), stages, input.critical);
    }
    /** Strength/Weakness bonus is already an MC-unit modifier; supply zero except direct dedicated-weapon melee. */
    public Amount vanillaProtection(Amount input, double directMeleePotionBonus, DoubleUnaryOperator applicableProtection) {
        Objects.requireNonNull(input); Objects.requireNonNull(applicableProtection);
        Checks.finite("potion bonus", directMeleePotionBonus);
        if (input.applied(Stage.VANILLA_PROTECTION)) throw new IllegalStateException("Vanilla protection already settled");
        double output = applicableProtection.applyAsDouble(Math.max(0, input.value + directMeleePotionBonus));
        var stages = EnumSet.noneOf(Stage.class); stages.addAll(input.stages); stages.add(Stage.VANILLA_PROTECTION);
        return new Amount(Checks.nonnegative("protected damage", output), stages, input.critical);
    }

    public record Shield(String instanceId, double amount) {
        public Shield { Checks.id(instanceId); Checks.nonnegative("shield", amount); }
    }
    /** Ordered shields must already have passed their own stacking/replacement rules. */
    public record Pools(double health, double maxHealth, double absorption, List<Shield> shields) {
        public Pools {
            Checks.positive("maxHealth", maxHealth); Checks.nonnegative("health", health);
            if (health > maxHealth) throw new IllegalArgumentException("health exceeds maximum");
            Checks.nonnegative("absorption", absorption); shields = List.copyOf(shields);
            if (shields.stream().map(Shield::instanceId).distinct().count() != shields.size())
                throw new IllegalArgumentException("duplicate shield instance");
        }
    }
    public record Settlement(boolean hit, boolean immune, boolean critical, double requested,
                             double shieldDamage, double absorptionDamage, double healthDamage,
                             double overkill, boolean lethal, Pools remaining) {
        public boolean dealtDamage() { return shieldDamage + absorptionDamage + healthDamage > 0; }
    }
    public Settlement settle(Amount input, Pools pools, boolean hit, boolean immune) {
        Objects.requireNonNull(input); Objects.requireNonNull(pools);
        if (!hit || immune) return new Settlement(hit, immune, false, input.value, 0, 0, 0, 0, false, pools);
        double left = input.value, shieldDamage = 0;
        List<Shield> remaining = new ArrayList<>();
        for (Shield shield : pools.shields) {
            double used = Math.min(left, shield.amount); left -= used; shieldDamage += used;
            if (shield.amount > used) remaining.add(new Shield(shield.instanceId, shield.amount - used));
        }
        double absorptionDamage = Math.min(left, pools.absorption); left -= absorptionDamage;
        double healthDamage = Math.min(left, pools.health); left -= healthDamage;
        Pools result = new Pools(pools.health - healthDamage, pools.maxHealth,
            pools.absorption - absorptionDamage, remaining);
        return new Settlement(true, false, input.critical, input.value, shieldDamage, absorptionDamage,
            healthDamage, left, pools.health > 0 && result.health == 0, result);
    }
    public record Healing(double requested, double effective, double overheal, Pools remaining) {}
    /** Does not revive. A revive must go through the team transaction. */
    public Healing heal(Pools pools, double minecraftAmount) {
        Objects.requireNonNull(pools); Checks.nonnegative("healing", minecraftAmount);
        double gained = pools.health <= 0 ? 0 : Math.min(minecraftAmount, pools.maxHealth - pools.health);
        return new Healing(minecraftAmount, gained, minecraftAmount - gained,
            new Pools(pools.health + gained, pools.maxHealth, pools.absorption, pools.shields));
    }
    public static double nativeHealing(double base, double outgoingBonus, double incomingBonus) {
        Checks.nonnegative("healing base", base); Checks.finite("outgoingBonus", outgoingBonus);
        Checks.finite("incomingBonus", incomingBonus);
        return CombatMath.mappedHealth(base * Math.max(0, 1 + outgoingBonus) * Math.max(0, 1 + incomingBonus));
    }
}
