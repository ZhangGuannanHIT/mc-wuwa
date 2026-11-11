package cn.zgnhit.mcwuwa.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

/** Five real item slots are owned by the platform; this validates their immutable domain contents. */
public final class EchoLoadout {
    public record MainPair(String first, String second) {
        public MainPair { Checks.id(first); Checks.id(second); if (first.equals(second)) throw new IllegalArgumentException("duplicate main stat"); }
    }
    public record CostRules(int cost, Set<MainPair> legalMainPairs, Map<String, Double> mainMaxRolls,
                            Map<String, Double> subMaxRolls) {
        public CostRules {
            requireCost(cost); legalMainPairs = Set.copyOf(legalMainPairs); mainMaxRolls = Map.copyOf(mainMaxRolls);
            subMaxRolls = Map.copyOf(subMaxRolls);
            if (legalMainPairs.isEmpty() || subMaxRolls.size() < 5) throw new MissingDataException("echo.cost_" + cost + ".legal_pools");
            mainMaxRolls.forEach((k, v) -> { Checks.id(k); Checks.nonnegative(k, v); });
            subMaxRolls.forEach((k, v) -> { Checks.id(k); Checks.nonnegative(k, v); });
            for (MainPair pair : legalMainPairs) if (!mainMaxRolls.containsKey(pair.first) || !mainMaxRolls.containsKey(pair.second))
                throw new MissingDataException("echo.main_pair." + pair);
        }
    }
    public record Echo(UUID instanceId, String speciesId, int cost, Map<String, Double> mainStats, Map<String, Double> subStats) {
        public Echo {
            Objects.requireNonNull(instanceId); Checks.id(speciesId); requireCost(cost);
            mainStats = Map.copyOf(mainStats); subStats = Map.copyOf(subStats);
            if (mainStats.size() != 2 || subStats.size() != 5) throw new IllegalArgumentException("requires two main and five distinct sub stats");
            mainStats.forEach((k, v) -> { Checks.id(k); Checks.nonnegative(k, v); });
            subStats.forEach((k, v) -> { Checks.id(k); Checks.nonnegative(k, v); });
        }
    }
    public record FixedPlan(String primarySet, String secondarySet, String activeEchoId) {
        public FixedPlan { Checks.id(primarySet); Objects.requireNonNull(secondarySet); Checks.id(activeEchoId); }
        public boolean split() { return !secondarySet.isEmpty(); }
    }
    private static final Map<CharacterId, FixedPlan> PLANS = Map.ofEntries(
        Map.entry(CharacterId.CHISA, new FixedPlan("23", "6", "6000167")),
        Map.entry(CharacterId.JINHSI, new FixedPlan("5", "", "6000059")),
        Map.entry(CharacterId.MORNYE, new FixedPlan("25", "", "6000190")),
        Map.entry(CharacterId.CARTETHYIA, new FixedPlan("17", "", "6000106")),
        Map.entry(CharacterId.CAMELLYA, new FixedPlan("6", "", "6000090")),
        Map.entry(CharacterId.YANGYANG_XUANLING, new FixedPlan("33", "", "6000218")),
        Map.entry(CharacterId.AERO_ROVER, new FixedPlan("16", "", "6000106")),
        Map.entry(CharacterId.AEMEATH, new FixedPlan("27", "", "6000191")),
        Map.entry(CharacterId.HIYUKI, new FixedPlan("30", "", "6000199")),
        Map.entry(CharacterId.LYNAE, new FixedPlan("24", "", "6000189")),
        Map.entry(CharacterId.YINLIN, new FixedPlan("8", "", "6000052")),
        Map.entry(CharacterId.PHROLOVA, new FixedPlan("19", "6", "6000115")),
        Map.entry(CharacterId.HSIN, new FixedPlan("36", "", "6000225")));
    private final Echo[] slots = new Echo[5];
    public static FixedPlan plan(CharacterId character) { return PLANS.get(Objects.requireNonNull(character)); }
    private static void requireCost(int cost) { if (cost != 1 && cost != 3 && cost != 4) throw new IllegalArgumentException("echo cost must be 1,3,4"); }
    private static void slot(int index) { if (index < 0 || index >= 5) throw new IllegalArgumentException("echo slot"); }
    public Optional<Echo> at(int index) { slot(index); return Optional.ofNullable(slots[index]); }
    public List<Optional<Echo>> snapshot() {
        List<Optional<Echo>> result = new ArrayList<>(5); for (Echo echo : slots) result.add(Optional.ofNullable(echo)); return List.copyOf(result);
    }
    public int totalCost() { int result = 0; for (Echo echo : slots) if (echo != null) result += echo.cost; return result; }
    public void validateReplacement(int index, Echo echo, CostRules rules) {
        slot(index); if (echo != null) validate(echo, Objects.requireNonNull(rules));
        Set<String> species = new HashSet<>(); Set<UUID> instances = new HashSet<>(); int cost = 0;
        for (int i = 0; i < 5; i++) {
            Echo item = i == index ? echo : slots[i]; if (item == null) continue;
            if (!species.add(item.speciesId) || !instances.add(item.instanceId)) throw new IllegalArgumentException("duplicate echo species or instance");
            cost += item.cost;
        }
        if (cost > 12) throw new IllegalArgumentException("echo total cost exceeds 12");
    }
    public Optional<Echo> replace(int index, Echo echo, CostRules rules, boolean outOfCombat) {
        if (!outOfCombat) throw new IllegalStateException("echo editing requires out of combat");
        validateReplacement(index, echo, rules); Echo old = slots[index]; slots[index] = echo; return Optional.ofNullable(old);
    }
    public Map<String, Integer> setCounts(CharacterId character) {
        FixedPlan plan = plan(character); Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 5; i++) if (slots[i] != null)
            counts.merge(plan.split() && i >= 3 ? plan.secondarySet : plan.primarySet, 1, Integer::sum);
        return Map.copyOf(counts);
    }
    public Optional<String> activeEcho(CharacterId character) { return slots[0] == null ? Optional.empty() : Optional.of(plan(character).activeEchoId); }
    public static void validate(Echo echo, CostRules rules) {
        if (echo.cost != rules.cost) throw new IllegalArgumentException("cost rule mismatch");
        boolean legalPair = rules.legalMainPairs.stream().anyMatch(p -> echo.mainStats.keySet().equals(Set.of(p.first, p.second)));
        if (!legalPair) throw new IllegalArgumentException("illegal main stat combination");
        exactMaxValues(echo.mainStats, rules.mainMaxRolls); exactMaxValues(echo.subStats, rules.subMaxRolls);
    }
    private static void exactMaxValues(Map<String, Double> actual, Map<String, Double> legal) {
        actual.forEach((key, value) -> {
            Double maximum = legal.get(key);
            if (maximum == null || Double.compare(value, maximum) != 0) throw new IllegalArgumentException("illegal/non-max roll: " + key);
        });
    }
    public static Echo roll(UUID instance, String species, CostRules rules, RandomGenerator random) {
        Objects.requireNonNull(random); Objects.requireNonNull(rules);
        List<MainPair> pairs = new ArrayList<>(rules.legalMainPairs);
        pairs.sort(java.util.Comparator.comparing(MainPair::first).thenComparing(MainPair::second));
        MainPair main = pairs.get(random.nextInt(pairs.size()));
        List<String> subPool = new ArrayList<>(rules.subMaxRolls.keySet()); subPool.sort(String::compareTo);
        Map<String, Double> sub = new LinkedHashMap<>();
        for (int i = 0; i < 5; i++) {
            int j = i + random.nextInt(subPool.size() - i);
            String selected = subPool.get(j); subPool.set(j, subPool.get(i)); subPool.set(i, selected);
            sub.put(selected, rules.subMaxRolls.get(selected));
        }
        return new Echo(instance, species, rules.cost,
            Map.of(main.first, rules.mainMaxRolls.get(main.first), main.second, rules.mainMaxRolls.get(main.second)), sub);
    }
}
