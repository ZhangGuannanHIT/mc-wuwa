package cn.zgnhit.mcwuwa.client;
import java.util.Map;
/** Generated from the retained catalogue. */
final class SigrikaSunContactClocks {
    record Clock(int duration,int... hits){}
    static final Map<String,Clock> ALL=Map.ofEntries(
        Map.entry("sigrika/basic_insight",new Clock(24,6,11,15,20)),
        Map.entry("sigrika/heavy",new Clock(26,7,22)),
        Map.entry("sigrika/air",new Clock(24,6)),
        Map.entry("sigrika/dodge_counter",new Clock(20,4,10,16)),
        Map.entry("sigrika/air_counter",new Clock(24,6)),
        Map.entry("sigrika/counter_insight",new Clock(24,6,11,15,20)),
        Map.entry("sigrika/skill",new Clock(26,6,11,17,22)),
        Map.entry("sigrika/skill_insight",new Clock(26,6,10,14,18,22)),
        Map.entry("sigrika/skill_sun",new Clock(26,6,11,17,22)),
        Map.entry("sigrika/liberation",new Clock(85,55)),
        Map.entry("sigrika/intro",new Clock(24,5)),
        Map.entry("sigrika/heavy_origin",new Clock(26,7)),
        Map.entry("sigrika/rune_burst",new Clock(24,6,13,20)),
        Map.entry("sigrika/rune_chain",new Clock(24,6,8,11,13,15,18,20)),
        Map.entry("sigrika/rune_sun",new Clock(24,6,9,12,14,17,20)),
        Map.entry("sigrika/skill_semantics",new Clock(26,6,22)),
        Map.entry("sigrika/heavy_origin_burst",new Clock(26,7,12,17,22)),
        Map.entry("sigrika/heavy_origin_chain",new Clock(26,7,9,11,13,16,18,20,22)),
        Map.entry("sigrika/heavy_origin_sun",new Clock(26,7,10,12,14,17,20,22)));
    private SigrikaSunContactClocks(){}
}
