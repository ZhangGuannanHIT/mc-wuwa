package cn.zgnhit.mcwuwa.content;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/** Frozen evidence from the 3.7 catalog; executable actions are intentionally separate. */
public record CharacterDefinition(
        int schemaVersion, String id, String characterId, int sourceId, String name,
        String element, int weaponType, BaseStats baseStats, double tuneMasteryRaw,
        WeaponDefinition weapon, int echoId, List<Integer> sonataSlots,
        List<SkillDefinition> skills, List<ChainDefinition> chains, List<ModeDefinition> modes,
        String sourceUrl, boolean mechanicsFullyImplemented, List<String> unresolved) {
    public CharacterDefinition {
        sonataSlots = List.copyOf(sonataSlots);
        skills = List.copyOf(skills);
        chains = List.copyOf(chains);
        modes = List.copyOf(modes);
        unresolved = List.copyOf(unresolved);
    }

    public SkillDefinition requireSkill(int sourceNode) {
        return skills.stream().filter(skill -> skill.sourceNode() == sourceNode).findFirst()
                .orElseThrow(() -> new NoSuchElementException(id + ": missing skill node " + sourceNode));
    }

    public DamageDefinition requireDamage(String sourceDamageId) {
        return skills.stream().flatMap(skill -> skill.damages().stream())
                .filter(damage -> damage.id().equals(sourceDamageId)).findFirst()
                .orElseThrow(() -> new NoSuchElementException(id + ": missing damage " + sourceDamageId));
    }

    /** All eight small skill-tree nodes are unlocked. Keep their bonuses separate from naked Lv90 stats.
     * Descriptions are evidence only: parse the exact percentage parameter, never execute source text. */
    public Map<String, Double> skillTreeModifiers() {
        Map<String, Double> result = new HashMap<>();
        for (var skill : skills) {
            if (skill.sourceNode() < 9 || skill.sourceNode() > 16) continue;
            String key = switch (skill.name()) {
                case "攻击提升" -> "attack_pct";
                case "生命提升" -> "health_pct";
                case "防御提升" -> "defense_pct";
                case "暴击提升" -> "crit_rate";
                case "暴击伤害提升" -> "crit_damage";
                case "治疗效果加成提升" -> "healing_bonus";
                case "共鸣效率提升" -> "energy_regen";
                case "冷凝伤害加成提升" -> "glacio_bonus";
                case "热熔伤害加成提升" -> "fusion_bonus";
                case "导电伤害加成提升" -> "electro_bonus";
                case "气动伤害加成提升" -> "aero_bonus";
                case "衍射伤害加成提升" -> "spectro_bonus";
                case "湮灭伤害加成提升" -> "havoc_bonus";
                default -> throw new IllegalStateException(id + ": unsupported stat node " + skill.sourceNode() + ": " + skill.name());
            };
            if (skill.rawParameters().size() != 1 || !skill.rawParameters().getFirst().matches("[0-9]+(?:\\.[0-9]+)?%"))
                throw new IllegalStateException(id + ": malformed stat node " + skill.sourceNode());
            String value = skill.rawParameters().getFirst();
            double ratio = new BigDecimal(value.substring(0, value.length() - 1)).movePointLeft(2).doubleValue();
            if (ratio > 1) throw new IllegalStateException(id + ": unreasonable stat node " + skill.sourceNode());
            result.merge(key, ratio, Double::sum);
        }
        return Map.copyOf(result);
    }

    /** Explicit displayed Liberation payment; empty is not silently interpreted as zero. */
    public OptionalDouble liberationEnergyCost() {
        return skills.stream().filter(skill -> skill.sourceNode() == 3).flatMap(skill -> skill.parameters().stream())
                .filter(parameter -> parameter.name().contains("消耗共鸣能量"))
                .mapToDouble(parameter -> Double.parseDouble(parameter.values().getFirst())).findFirst();
    }
    /** Gameplay threshold: Aemeath's source requires an attainable second stage
     * but omits seconds. One second is an explicit MC input adaptation. */
    public OptionalInt fullChargeTicks() {
        var verified=verifiedFullChargeTicks();
        return verified.isPresent()?verified:characterId.equals("aemeath")?OptionalInt.of(20):OptionalInt.empty();
    }
    /** Only an explicit positive finite seconds parameter proves source timing; damage rows and formulas do not. */
    public OptionalInt verifiedFullChargeTicks() {
        int maximum = 0;
        for (var skill : skills) for (var parameter : skill.parameters()) {
            if (!parameter.name().contains("蓄力时间") || parameter.values().isEmpty()) continue;
            String value = parameter.values().getFirst().trim();
            if (!value.matches("(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)\\s*(?:秒|s)?")) continue;
            value = value.replaceFirst("\\s*(?:秒|s)$", "").trim();
            double seconds; try { seconds = Double.parseDouble(value); } catch (NumberFormatException invalid) { continue; }
            double ticks = Math.ceil(seconds * 20);
            if (Double.isFinite(seconds) && seconds > 0 && Double.isFinite(ticks) && ticks <= Integer.MAX_VALUE) maximum = Math.max(maximum, (int) ticks);
        }
        return maximum == 0 ? OptionalInt.empty() : OptionalInt.of(maximum);
    }

    public record BaseStats(double health, double attack, double defense) { }
    public record WeaponDefinition(int sourceId, String name, List<WeaponStat> stats,
                                   String effectName, List<Refinement> refinements) {
        public WeaponDefinition { stats = List.copyOf(stats); refinements = List.copyOf(refinements); }
        public double baseAttack() {
            return stats.stream().filter(stat -> stat.name().equals("攻击") && !stat.isPercent())
                    .mapToDouble(WeaponStat::value).findFirst().orElseThrow();
        }
    }
    /** Percentage values are fractional (0.243 is 24.3%), flat values remain native units. */
    public record WeaponStat(String name, double value, boolean isRatio, boolean isPercent, double rawValue) { }
    public record Refinement(int tier, String description, List<String> parameters) {
        public Refinement { parameters = List.copyOf(parameters); }
    }
    public record SkillDefinition(String id, int sourceNode, String category, String name,
                                  String description, String rawDescription, List<String> rawParameters,
                                  List<String> headings, List<SkillParameter> parameters,
                                  List<DamageDefinition> damages, String sourceTypeLabel, String iconSource) {
        public SkillDefinition {
            rawParameters = List.copyOf(rawParameters); headings = List.copyOf(headings);
            parameters = List.copyOf(parameters); damages = List.copyOf(damages);
        }
    }
    /** values retains formula strings such as 12.33%+24.66%+86.29%; never evaluate source text. */
    public record SkillParameter(String id, String name, List<String> values, String format) {
        public SkillParameter { values = List.copyOf(values); }
    }
    /** multiplier is native fractional rate; the two gauge quantities use distinct Jinhsi units. */
    public record DamageDefinition(String id, int sourceType, String kind, String element,
                                   String scalingProperty, int skillLevel, double multiplier,
                                   double toughnessUnits, double offTuneUnits, JsonObject raw,
                                   boolean actionMappingVerified, List<String> unresolved) {
        public DamageDefinition {
            raw = raw.deepCopy(); unresolved = List.copyOf(unresolved);
        }
        @Override public JsonObject raw() { return raw.deepCopy(); }
    }
    public record ChainDefinition(int tier, String name, String description,
                                  String rawDescription, List<String> parameters) {
        public ChainDefinition { parameters = List.copyOf(parameters); }
    }
    public record ModeDefinition(int sourceId, String name, String description) { }
}
