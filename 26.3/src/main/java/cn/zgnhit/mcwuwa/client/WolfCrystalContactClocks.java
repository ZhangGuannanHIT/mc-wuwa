package cn.zgnhit.mcwuwa.client;
import java.util.Map;
/** Generated from the unchanged catalogue. */
final class WolfCrystalContactClocks {
    record Clock(int duration,int... hits){}
    static final Map<String,Clock> ALL=Map.ofEntries(
        Map.entry("lupa/air",new Clock(24,6,13,20)),
        Map.entry("lupa/starfall",new Clock(24,6,10,13,16,20)),
        Map.entry("lupa/dodge_counter",new Clock(20,4,7,10,13,16)),
        Map.entry("lupa/skill",new Clock(26,6)),
        Map.entry("lupa/skill_ferocious",new Clock(26,6)),
        Map.entry("lupa/skill_ferocious_marked",new Clock(26,6)),
        Map.entry("lupa/intro",new Clock(24,5,9,12,16,20)),
        Map.entry("lupa/intro_pursuit",new Clock(24,5,9,12,16,20)),
        Map.entry("lupa/intro_pursuit_c3",new Clock(24,5,9,12,16,20)),
        Map.entry("lupa/skill_wolf_dance",new Clock(26,6,9,12,16,19,22)),
        Map.entry("lupa/skill_wolf_dance_extreme",new Clock(26,6,9,12,16,19,22)),
        Map.entry("lupa/skill_wolf_dance_extreme_c4",new Clock(26,6,9,12,16,19,22)),
        Map.entry("carlotta/necessary_01",new Clock(24,6)),
        Map.entry("carlotta/necessary_02",new Clock(24,6,20)),
        Map.entry("carlotta/necessary_03",new Clock(24,6,10,13,16,20)),
        Map.entry("carlotta/air",new Clock(24,6)),
        Map.entry("carlotta/courtesy",new Clock(24,6,20)),
        Map.entry("carlotta/dodge_counter",new Clock(20,4,16)),
        Map.entry("carlotta/skill",new Clock(26,6,22)),
        Map.entry("carlotta/skill_show_brilliance",new Clock(26,6,14,22)),
        Map.entry("carlotta/skill_c3",new Clock(26,6,22)),
        Map.entry("carlotta/skill_show_brilliance_c3",new Clock(26,6,14,22)),
        Map.entry("carlotta/liberation",new Clock(48,12)),
        Map.entry("carlotta/intro",new Clock(24,5,12,20)));
    private WolfCrystalContactClocks(){}
}
