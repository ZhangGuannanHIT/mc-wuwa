package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.Map;

/** Immutable weapon-owned state. Host persists this once, not a second player-global copy. */
public record MechanicsState(String characterId, String form, String mode, int chain,
                             int nextBasic, Map<String, Double> resources, Map<String, Integer> timers) {
    public MechanicsState {
        resources = Map.copyOf(resources);
        timers = Map.copyOf(timers);
        if (chain < 0 || chain > 6) throw new IllegalArgumentException("chain must be 0..6");
    }

    public static MechanicsState initial(String character, String mode, int chain) {
        String form = switch (character) {
            case "aemeath" -> "aemeath";
            case "lynae" -> "sampling";
            case "mornye" -> "baseline";
            case "zhezhi", "lucilla", "zani", "augusta", "suoming" -> "normal";
            case "denia", "iuno", "changli", "qingxiao", "lupa", "carlotta", "phoebe", "suisui", "cantarella", "jinhsi", "camellya", "yinlin", "aero_rover", "chisa", "phrolova", "qiuyuan", "ciaccona", "shorekeeper", "sigrika", "galbrena" -> "normal";
            case "cartethyia" -> "cartethyia";
            case "yangyang_xuanling" -> "azure";
            case "hsin" -> "worldly";
            case "hiyuki" -> "present";
            default -> throw new IllegalArgumentException("No mechanics strategy yet: " + character);
        };
        return new MechanicsState(character, form, canonicalMode(character, mode), chain, 1, MechanicsEngine.initialResources(character), Map.of());
    }
    /** Source IDs are the persisted/UI identity; strategies consume explicit semantic names. */
    public static String canonicalMode(String character, String selected) {
        return switch (selected) {
            case "121001", "150901" -> "rupture";
            case "121002" -> "fusion";
            case "121101" -> "fusion";
            case "121102" -> "strain";
            case "150902" -> "strain";
            case "131101" -> "unison";
            case "131102" -> "electro";
            case "110901" -> "glacio";
            case "110902" -> "echo";
            case "default" -> switch (character) {
                case "aemeath", "lynae" -> "rupture";
                case "hsin" -> "unison";
                case "denia" -> "fusion";
                case "lucilla" -> "glacio";
                default -> "default";
            };
            default -> selected;
        };
    }
    public double resource(String key) { return resources.getOrDefault(key, 0.0); }
    public int timer(String key) { return timers.getOrDefault(key, 0); }
}
