package cn.zgnhit.mcwuwa.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Source-aware, tag-filtered modifier view. Formula zones remain distinct rather than one total-damage field. */
public final class AttributeModifiers {
    public enum Zone {
        ATTACK_FLAT, ATTACK_PERCENT, HEALTH_FLAT, HEALTH_PERCENT, DEFENSE_FLAT, DEFENSE_PERCENT,
        CRIT_RATE, CRIT_MULTIPLIER, ENERGY_EFFICIENCY, HEALING_BONUS, INCOMING_HEALING_BONUS,
        SHIELD_BONUS, DAMAGE_BONUS, AMPLIFICATION, VULNERABILITY, DAMAGE_REDUCTION,
        SKILL_RATIO_BONUS, FLAT_DAMAGE, FINAL_DAMAGE, RESISTANCE_REDUCTION, RESISTANCE_IGNORE,
        DEFENSE_REDUCTION, DEFENSE_IGNORE, OFFTUNE_EFFICIENCY, TUNE_BREAK_AMPLIFICATION,
        EFFECT_DAMAGE_BONUS, EFFECT_STACK_CAP
    }
    public enum Stacking { ADD, STRONGEST_MAGNITUDE }
    public record Context(Element element, DamageKind kind, boolean direct, boolean melee) {
        public Context { Objects.requireNonNull(element); Objects.requireNonNull(kind); }
    }
    /** Empty tag sets mean unrestricted. Scalar action restrictions are explicit booleans. */
    public record Selector(Set<Element> elements, Set<DamageKind> damageKinds, boolean directOnly, boolean meleeOnly) {
        public Selector { elements = Set.copyOf(elements); damageKinds = Set.copyOf(damageKinds); }
        public static Selector any() { return new Selector(Set.of(), Set.of(), false, false); }
        public boolean matches(Context context) {
            return (elements.isEmpty() || elements.contains(context.element))
                && (damageKinds.isEmpty() || damageKinds.contains(context.kind))
                && (!directOnly || context.direct) && (!meleeOnly || context.melee);
        }
    }
    public record Modifier(UUID instanceId, EffectSource source, Zone zone, String stackingGroup,
                           Stacking stacking, double value, long expiresAt, Selector selector) {
        public Modifier {
            Objects.requireNonNull(instanceId); Objects.requireNonNull(source); Objects.requireNonNull(zone);
            Checks.id(stackingGroup); Objects.requireNonNull(stacking); Checks.finite("modifier", value);
            Checks.tick(expiresAt); Objects.requireNonNull(selector);
        }
    }
    public record GroupValue(String group, double value, List<UUID> contributors) {
        public GroupValue { contributors = List.copyOf(contributors); }
    }
    public record View(Map<Zone, List<GroupValue>> groups, long revision) {
        public View {
            Map<Zone, List<GroupValue>> copy = new EnumMap<>(Zone.class);
            groups.forEach((key, values) -> copy.put(key, List.copyOf(values))); groups = Map.copyOf(copy);
        }
        public double additive(Zone zone) { return groups.getOrDefault(zone, List.of()).stream().mapToDouble(GroupValue::value).sum(); }
        /** Use only where the verified formula says independently named groups multiply. */
        public List<Double> independentFactors(Zone zone) {
            return groups.getOrDefault(zone, List.of()).stream().map(group -> Math.max(0, 1 + group.value)).toList();
        }
        public CombatMath.Stat attack(double base) { return new CombatMath.Stat(base, additive(Zone.ATTACK_FLAT), additive(Zone.ATTACK_PERCENT)); }
        public CombatMath.Stat health(double base) { return new CombatMath.Stat(base, additive(Zone.HEALTH_FLAT), additive(Zone.HEALTH_PERCENT)); }
        public CombatMath.Stat defense(double base) { return new CombatMath.Stat(base, additive(Zone.DEFENSE_FLAT), additive(Zone.DEFENSE_PERCENT)); }
    }
    private record GroupKey(Zone zone, String group) {}
    private final Map<UUID, Modifier> modifiers = new HashMap<>();
    private long revision;
    public void put(Modifier modifier) {
        Objects.requireNonNull(modifier);
        for (Modifier existing : modifiers.values()) {
            if (!existing.instanceId.equals(modifier.instanceId) && existing.zone == modifier.zone
                && existing.stackingGroup.equals(modifier.stackingGroup) && existing.stacking != modifier.stacking)
                throw new IllegalArgumentException("inconsistent stacking policy in " + modifier.stackingGroup);
        }
        modifiers.put(modifier.instanceId, modifier); revision++;
    }
    public void remove(UUID id) { if (modifiers.remove(Objects.requireNonNull(id)) != null) revision++; }
    public void clearSourceWeapon(UUID weaponId) {
        Objects.requireNonNull(weaponId); if (modifiers.values().removeIf(m -> m.source.weaponInstanceId().equals(weaponId))) revision++;
    }
    public void clearSourceWeaponGroup(UUID weaponId,String group){
        Objects.requireNonNull(weaponId);Checks.id(group);
        if(modifiers.values().removeIf(m->m.source.weaponInstanceId().equals(weaponId)&&m.stackingGroup.equals(group)))revision++;
    }
    public void expire(long now) { Checks.tick(now); if (modifiers.values().removeIf(m -> m.expiresAt <= now)) revision++; }
    public void pauseOneTick(){modifiers.replaceAll((id,m)->new Modifier(m.instanceId,m.source,m.zone,m.stackingGroup,m.stacking,m.value,CinematicTimeRules.postpone(m.expiresAt,1),m.selector));revision++;}
    public View resolve(Context context, long now) {
        Objects.requireNonNull(context); Checks.tick(now);
        Map<GroupKey, List<Modifier>> grouped = new HashMap<>();
        for (Modifier modifier : modifiers.values()) if (modifier.expiresAt > now && modifier.selector.matches(context))
            grouped.computeIfAbsent(new GroupKey(modifier.zone, modifier.stackingGroup), ignored -> new ArrayList<>()).add(modifier);
        Map<Zone, List<GroupValue>> result = new EnumMap<>(Zone.class);
        grouped.forEach((key, values) -> {
            values.sort(Comparator.comparing(m -> m.instanceId.toString()));
            double value; List<UUID> sources;
            if (values.get(0).stacking == Stacking.STRONGEST_MAGNITUDE) {
                Modifier strongest = values.stream().max(Comparator.comparingDouble(m -> Math.abs(m.value))).orElseThrow();
                value = strongest.value; sources = List.of(strongest.instanceId);
            } else { value = values.stream().mapToDouble(Modifier::value).sum(); sources = values.stream().map(Modifier::instanceId).toList(); }
            result.computeIfAbsent(key.zone, ignored -> new ArrayList<>()).add(new GroupValue(key.group, value, sources));
        });
        result.values().forEach(groups -> groups.sort(Comparator.comparing(GroupValue::group)));
        return new View(result, revision);
    }
    public long revision() { return revision; }
    public boolean isEmpty(){return modifiers.isEmpty();}
}
