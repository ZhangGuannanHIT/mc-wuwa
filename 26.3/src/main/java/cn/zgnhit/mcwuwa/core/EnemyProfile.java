package cn.zgnhit.mcwuwa.core;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Fixed plan-derived creature rules; classification must come from the server registry. */
public record EnemyProfile(String entityId, Tier tier, Element specialResistance,
                           boolean echoEligible, double healthMultiplier, boolean player) {
    public enum Tier {
        ONE_COST(1, 5, 5, 60), THREE_COST(3, 10, 15, 60), FOUR_COST(4, 30, 45, 100),
        STRONG(4, 60, 90, 20);
        public final int cost; public final double toughness, offTune; public final int brokenTicks;
        Tier(int cost, double toughness, double offTune, int brokenTicks) {
            this.cost = cost; this.toughness = toughness; this.offTune = offTune; this.brokenTicks = brokenTicks;
        }
    }
    private static final Set<String> STRONG = Set.of("ender_dragon", "wither", "warden", "elder_guardian");
    private static final Set<String> EXCLUDED = Set.of("villager", "wandering_trader", "armor_stand", "mannequin", "player");
    private static final Map<String, Element> SPECIAL = Map.ofEntries(
        Map.entry("drowned", Element.GLACIO), Map.entry("bogged", Element.GLACIO), Map.entry("parched", Element.GLACIO),
        Map.entry("guardian", Element.GLACIO), Map.entry("elder_guardian", Element.GLACIO),
        Map.entry("zombified_piglin", Element.FUSION), Map.entry("zoglin", Element.FUSION), Map.entry("ravager", Element.FUSION),
        Map.entry("piglin", Element.FUSION), Map.entry("piglin_brute", Element.FUSION), Map.entry("hoglin", Element.FUSION),
        Map.entry("blaze", Element.FUSION), Map.entry("magma_cube", Element.FUSION), Map.entry("ghast", Element.FUSION),
        Map.entry("skeleton", Element.ELECTRO), Map.entry("stray", Element.ELECTRO), Map.entry("witch", Element.ELECTRO),
        Map.entry("creaking", Element.ELECTRO), Map.entry("warden", Element.ELECTRO),
        Map.entry("creeper", Element.AERO), Map.entry("slime", Element.AERO), Map.entry("breeze", Element.AERO),
        Map.entry("silverfish", Element.SPECTRO), Map.entry("vindicator", Element.SPECTRO), Map.entry("evoker", Element.SPECTRO),
        Map.entry("shulker", Element.SPECTRO), Map.entry("phantom", Element.SPECTRO),
        Map.entry("zombie_villager", Element.HAVOC), Map.entry("wither_skeleton", Element.HAVOC), Map.entry("spider", Element.HAVOC),
        Map.entry("cave_spider", Element.HAVOC), Map.entry("pillager", Element.HAVOC), Map.entry("vex", Element.HAVOC),
        Map.entry("enderman", Element.HAVOC), Map.entry("endermite", Element.HAVOC), Map.entry("wither", Element.HAVOC));
    public EnemyProfile {
        Checks.id(entityId); Objects.requireNonNull(tier); Objects.requireNonNull(specialResistance);
        Checks.positive("healthMultiplier", healthMultiplier);
    }
    public double resistance(Element element) {
        Objects.requireNonNull(element);
        return element == Element.PHYSICAL ? 0 : element == specialResistance ? 0.5 : 0.1;
    }
    public boolean hardControlImmune() { return player || tier == Tier.STRONG; }
    /** User-approved Augusta realm exception; never grants control over players. */
    public boolean augustaStasisAllowed() { return !player; }
    public boolean gaugesEnabled() { return !player; }
    public static EnemyProfile forEntity(String registryId, boolean monsterSpecies, boolean skillSummon) {
        Checks.id(registryId);
        boolean vanilla = registryId.startsWith("minecraft:");
        String path = vanilla ? registryId.substring(10) : registryId;
        boolean aleph = registryId.equals("mc_wuwa:aleph_one");
        boolean echoBoss = EchoBossCatalog.find(registryId).isPresent();
        boolean player = path.equals("player");
        Tier tier = echoBoss || STRONG.contains(path) && vanilla ? Tier.STRONG
            : aleph || vanilla && path.equals("iron_golem") ? Tier.FOUR_COST
            : monsterSpecies ? Tier.THREE_COST : Tier.ONE_COST;
        boolean drop = !skillSummon && (vanilla && !EXCLUDED.contains(path) || aleph || echoBoss);
        double multiplier = tier == Tier.STRONG ? path.equals("elder_guardian") ? 6 : 3 : 1;
        return new EnemyProfile(registryId, tier, vanilla ? SPECIAL.getOrDefault(path, Element.PHYSICAL) : Element.PHYSICAL,
            drop, multiplier, player);
    }
}
