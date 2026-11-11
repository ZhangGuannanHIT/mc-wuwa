package cn.zgnhit.mcwuwa.client;
import java.util.Map;
/** Retained catalogue clocks; no autonomous recital notes. */
final class MelodyButterflyContactClocks {
    record Clock(int duration,int... hits){}
    static final Map<String,Clock> ALL=Map.ofEntries(
        Map.entry("ciaccona/heavy",new Clock(26,7)),
        Map.entry("ciaccona/aim",new Clock(24,6)),
        Map.entry("ciaccona/aim_charged",new Clock(24,6)),
        Map.entry("ciaccona/air_01",new Clock(24,6,20)),
        Map.entry("ciaccona/air_02",new Clock(24,6,11,15,20)),
        Map.entry("ciaccona/dodge_counter",new Clock(20,4,8,12,16)),
        Map.entry("ciaccona/skill",new Clock(26,6,11,17,22)),
        Map.entry("ciaccona/liberation",new Clock(75,65)),
        Map.entry("ciaccona/intro",new Clock(24,5)),
        Map.entry("ciaccona/heavy_quartet",new Clock(26,7,8,10,12,13,14,16,18,19,20,22)),
        Map.entry("shorekeeper/heavy",new Clock(26,7)),
        Map.entry("shorekeeper/air",new Clock(24,6)),
        Map.entry("shorekeeper/dodge_counter",new Clock(20,4,16)),
        Map.entry("shorekeeper/skill",new Clock(26,6,10,14,18,22)),
        Map.entry("shorekeeper/liberation",new Clock(65)),
        Map.entry("shorekeeper/intro",new Clock(24,5,9,12,16,20)),
        Map.entry("shorekeeper/intro_insight",new Clock(24,5,12,20)),
        Map.entry("shorekeeper/heavy_deduction",new Clock(26,7,11,14,18,22)),
        Map.entry("shorekeeper/air_evolution",new Clock(24,6)));
    private MelodyButterflyContactClocks(){}
}
