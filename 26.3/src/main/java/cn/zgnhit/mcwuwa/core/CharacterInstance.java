package cn.zgnhit.mcwuwa.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** One object per physical weapon instance, never one per (player, character). */
public final class CharacterInstance {
    public record Resource(double initial, double maximum) {
        public Resource { Checks.nonnegative("initial resource", initial); Checks.nonnegative("maximum resource", maximum);
            if (initial > maximum) throw new IllegalArgumentException("initial exceeds max"); }
    }
    public record Definition(CharacterId character, double nativeMaxHealth, Map<String, Resource> resources,
                             String initialMode, Set<String> selectableModes) {
        public Definition {
            Objects.requireNonNull(character); Checks.positive("nativeMaxHealth", nativeMaxHealth);
            resources = Map.copyOf(resources); resources.keySet().forEach(Checks::id);
            Checks.id(initialMode); selectableModes = Set.copyOf(selectableModes);
            if (!selectableModes.contains(initialMode)) throw new IllegalArgumentException("initial mode not selectable");
        }
    }
    public record Snapshot(UUID instanceId, CharacterId character, double health, double maxHealth,
                           Map<String, Double> resources, Map<String, Long> cooldowns, int chain, int refinement,
                           String selectedMode, String actionId, Set<String> temporaryStates) {
        public Snapshot { resources = Map.copyOf(resources); cooldowns = Map.copyOf(cooldowns); temporaryStates = Set.copyOf(temporaryStates); }
    }
    private final UUID instanceId;
    private final Definition definition;
    private final EchoLoadout echoes = new EchoLoadout();
    private final Map<String, Double> resources = new HashMap<>();
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Map<String,Integer> persistentCounters=new HashMap<>();
    private final Set<String> temporaryStates = new HashSet<>();
    private double health, maxHealth;
    private int chain, refinement = 1;
    private String selectedMode, actionId = "";
    public CharacterInstance(UUID instanceId, Definition definition) {
        this.instanceId = Objects.requireNonNull(instanceId); this.definition = Objects.requireNonNull(definition);
        maxHealth = CombatMath.mappedHealth(definition.nativeMaxHealth); health = maxHealth; selectedMode = definition.initialMode;
        resetResources();
    }
    public int persistentCounter(String key) { return persistentCounters.getOrDefault(Checks.id(key),0); }
    public void setPersistentCounter(String key,int value) {
        Checks.id(key);if(value<0)throw new IllegalArgumentException("negative persistent counter");
        if(value==0)persistentCounters.remove(key);else persistentCounters.put(key,value);
    }
    public Map<String,Integer> persistentCounters() { return Map.copyOf(persistentCounters); }
    public UUID instanceId() { return instanceId; }
    public CharacterId character() { return definition.character; }
    public Definition definition() { return definition; }
    public double health() { return health; }
    public double maxHealth() { return maxHealth; }
    public boolean alive() { return health > 0; }
    public int chain() { return chain; }
    public int refinement() { return refinement; }
    public EchoLoadout echoes() { return echoes; }
    public String selectedMode() { return selectedMode; }
    public String actionId() { return actionId; }
    public Snapshot snapshot() {
        return new Snapshot(instanceId, character(), health, maxHealth, resources, cooldowns, chain, refinement, selectedMode, actionId, temporaryStates);
    }
    public void setMaxHealth(double minecraftMaxHealth) {
        Checks.positive("max health", minecraftMaxHealth); maxHealth = minecraftMaxHealth; health = Math.min(health, maxHealth);
    }
    public double resource(String key) {
        Checks.id(key); Double value = resources.get(key); if (value == null) throw new MissingDataException(character() + ".resource." + key); return value;
    }
    public double resourceMax(String key) {
        Resource value = definition.resources.get(key); if (value == null) throw new MissingDataException(character() + ".resource." + key); return value.maximum;
    }
    public double gain(String key, double amount) {
        Checks.nonnegative("resource gain", amount); double old = resource(key), next = Math.min(resourceMax(key), old + amount); resources.put(key, next); return next - old;
    }
    public boolean canPay(Map<String, Double> costs) {
        Objects.requireNonNull(costs);
        for (var cost : costs.entrySet()) { Checks.nonnegative("resource cost", cost.getValue()); if (resource(cost.getKey()) < cost.getValue()) return false; }
        return true;
    }
    /** Costs are validated as a set; failed payment cannot partially deduct an earlier resource. */
    public boolean pay(Map<String, Double> costs) {
        if (!canPay(costs)) return false;
        costs.forEach((key, value) -> resources.put(key, resources.get(key) - value)); return true;
    }
    public void setCooldown(String skill, long remainingTicks) {
        Checks.id(skill); Checks.tick(remainingTicks); if (remainingTicks == 0) cooldowns.remove(skill); else cooldowns.put(skill, remainingTicks);
    }
    public long cooldown(String skill) { return cooldowns.getOrDefault(Checks.id(skill), 0L); }
    public void tickCooldowns(long onlineTicks) {
        Checks.tick(onlineTicks); cooldowns.replaceAll((k, v) -> Math.max(0, v - onlineTicks)); cooldowns.values().removeIf(v -> v == 0);
    }
    public void beginAction(String action) { if (!alive()) throw new IllegalStateException("downed"); actionId = Checks.id(action); }
    public String cancelAction() { String previous = actionId; actionId = ""; return previous; }
    public void putTemporaryState(String id) { temporaryStates.add(Checks.id(id)); }
    public boolean hasTemporaryState(String id) { return temporaryStates.contains(id); }
    public void removeTemporaryState(String id) { temporaryStates.remove(id); }
    public void setSelectedMode(String mode, boolean outOfCombat) {
        if (!outOfCombat) throw new IllegalStateException("mode editing requires out of combat");
        if (!definition.selectableModes.contains(mode)) throw new IllegalArgumentException("invalid mode"); selectedMode = mode;
    }
    /** Only the CombatResolver / lifecycle adapter should commit settled health. */
    public void commitHealth(double value) {
        Checks.nonnegative("health", value); if (value > maxHealth) throw new IllegalArgumentException("health exceeds max");
        health = value; if (health == 0) cancelAction();
    }
    public void heal(double amount) { Checks.nonnegative("heal", amount); if (alive()) health = Math.min(maxHealth, health + amount); }
    void upgradeChain() { if (chain >= 6) throw new IllegalStateException("chain cap"); chain++; }
    void upgradeRefinement() { if (refinement >= 5) throw new IllegalStateException("refinement cap"); refinement++; }
    void reviveHalf() { if (alive()) throw new IllegalStateException("already alive"); health = maxHealth / 2; cancelAction(); }
    public void sleepFullHeal() { health = maxHealth; }
    public void resetOnUnequip() { health = maxHealth; cooldowns.clear(); persistentCounters.clear(); resetTransientAndResources(); }
    public void resetOnRespawn() { health = maxHealth; resetTransientAndResources(); }
    public void resetOnRejoin() { resetTransientAndResources(); }
    public void clearOnLogout() { cancelAction(); temporaryStates.clear(); }
    private void resetTransientAndResources() { cancelAction(); temporaryStates.clear(); resetResources(); }
    private void resetResources() { resources.clear(); definition.resources.forEach((key, value) -> resources.put(key, value.initial)); }
    /** Explicit restore after versioned decoding; no fallback to a new C0 item. */
    public void restorePersistent(double savedHealth, Map<String, Long> savedCooldowns, int savedChain, int savedRefinement, String mode) {
        Checks.nonnegative("saved health", savedHealth);
        if (savedHealth > maxHealth || savedChain < 0 || savedChain > 6 || savedRefinement < 1 || savedRefinement > 5 || !definition.selectableModes.contains(mode))
            throw new IllegalArgumentException("invalid persistent character state");
        savedCooldowns.forEach((key, value) -> { Checks.id(key); Checks.tick(value); });
        health = savedHealth; chain = savedChain; refinement = savedRefinement; selectedMode = mode;
        cooldowns.clear(); cooldowns.putAll(savedCooldowns); resetOnRejoin();
    }
}
