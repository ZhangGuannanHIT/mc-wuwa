package cn.zgnhit.mcwuwa.core;

import java.util.Objects;
import java.util.OptionalDouble;

public final class CombatMath {
    public static final double HEALTH_SCALE = 0.002;
    public static final double ENEMY_DEFENSE = 1512;
    public static final double TOUGHNESS_UNIT = 0.5943;
    public static final double OFFTUNE_UNIT = 0.396;
    private CombatMath() {}

    /** Inputs are encoded as 1 + applicable DMG Amplify; they share one additive zone.
     * DMG Bonus, target vulnerability and explicitly final modifiers stay separate. */
    public static double amplificationFactor(double... factors) {
        double total=1;
        for(double factor:factors){Checks.nonnegative("amplification factor",factor);total+=factor-1;}
        return Math.max(0,total);
    }

    public record Stat(double base, double flat, double percent) {
        public Stat {
            Checks.nonnegative("base", base); Checks.finite("flat", flat); Checks.finite("percent", percent);
        }
        public double effective() { return Math.max(0, base * (1 + percent) + flat); }
    }
    public record NativeStats(Stat health, Stat attack, Stat defense, double critChance,
                              double critMultiplier, double energyEfficiency) {
        public NativeStats {
            Objects.requireNonNull(health); Objects.requireNonNull(attack); Objects.requireNonNull(defense);
            Checks.nonnegative("critChance", critChance); Checks.positive("critMultiplier", critMultiplier);
            Checks.nonnegative("energyEfficiency", energyEfficiency);
        }
        public double effectiveCritChance() { return Math.min(1, critChance); }
    }
    public static double defenseFactor(int attackerLevel, double defense, double reduction, double ignore) {
        if (attackerLevel < 1) throw new IllegalArgumentException("attackerLevel must be positive");
        Checks.nonnegative("defense", defense); Checks.fraction("reduction", reduction); Checks.fraction("ignore", ignore);
        double levelTerm = 800d + 8d * attackerLevel;
        return levelTerm / (levelTerm + defense * (1 - reduction) * (1 - ignore));
    }
    public static double resistanceFactor(Element element, double effectiveResistance) {
        Objects.requireNonNull(element); Checks.finite("resistance", effectiveResistance);
        if (element == Element.PHYSICAL) return 1;
        if (effectiveResistance < 0) return 1 - effectiveResistance / 2;
        return effectiveResistance < 0.8 ? 1 - effectiveResistance : 1 / (1 + 5 * effectiveResistance);
    }
    public static double mappedHealth(double nativeValue) { return Checks.nonnegative("native health", nativeValue) * HEALTH_SCALE; }
    public static double nativeHealth(double minecraftValue) { return Checks.nonnegative("minecraft health", minecraftValue) / HEALTH_SCALE; }
    public static double toughnessUnits(double nativeValue) { return Checks.nonnegative("toughness", nativeValue) / TOUGHNESS_UNIT; }
    public static double offTuneUnits(double nativeValue) { return Checks.nonnegative("offTune", nativeValue) / OFFTUNE_UNIT; }

    /** Unmeasured calibration deliberately has no fabricated gameplay fallback. */
    public record DamageScale(OptionalDouble measuredSwordDps, String evidenceId, double playabilityMultiplier) {
        public DamageScale {
            Objects.requireNonNull(measuredSwordDps);
            Checks.id(evidenceId);
            measuredSwordDps.ifPresent(d -> Checks.positive("measuredSwordDps", d));
            Checks.positive("playabilityMultiplier", playabilityMultiplier);
        }
        public DamageScale(OptionalDouble measuredSwordDps, String evidenceId) { this(measuredSwordDps, evidenceId, 1); }
        public static DamageScale unmeasured() { return new DamageScale(OptionalDouble.empty(), "unmeasured"); }
        public static DamageScale calibrated(double vanillaSustainedDps, String evidenceId) {
            return new DamageScale(OptionalDouble.of(vanillaSustainedDps), evidenceId);
        }
        /** Gameplay balance is explicit and never falsifies the measured reference sword DPS. */
        public DamageScale withMultiplier(double multiplier) { return new DamageScale(measuredSwordDps, evidenceId, multiplier); }
        public double value() {
            if (measuredSwordDps.isEmpty()) throw new MissingDataException("calibration.vanilla_sword_dps");
            return measuredSwordDps.getAsDouble() * 1.25 / 100000 * playabilityMultiplier;
        }
    }
}
