package cn.zgnhit.mcwuwa.client;
import java.util.Map;
/** Generated from the unchanged combat catalogue. */
final class InkInfernalContactClocks {
    record Clock(int duration,int... hits){}
    static final Map<String,Clock> ALL=Map.ofEntries(
        Map.entry("zhezhi/heavy",new Clock(26,7)),
        Map.entry("zhezhi/air_basic_01",new Clock(14,3,5,6,8,10)),
        Map.entry("zhezhi/air_basic_02",new Clock(20,5)),
        Map.entry("zhezhi/dodge_counter",new Clock(20,4,7,10,13,16)),
        Map.entry("zhezhi/skill",new Clock(26,6,14,22)),
        Map.entry("zhezhi/skill_hold",new Clock(26,6,14,22)),
        Map.entry("zhezhi/skill_air",new Clock(26,6,14,22)),
        Map.entry("zhezhi/liberation",new Clock(24)),
        Map.entry("zhezhi/intro",new Clock(24,5,12,20)),
        Map.entry("zhezhi/heavy_conjuration",new Clock(26,7,14,22)),
        Map.entry("zhezhi/skill_genius",new Clock(28,14)),
        Map.entry("zhezhi/skill_genius_air",new Clock(28,14)),
        Map.entry("zhezhi/skill_maestro",new Clock(28,14,19,24)),
        Map.entry("galbrena/dodge_counter",new Clock(20,4,8,12,16)),
        Map.entry("galbrena/air",new Clock(24,6)),
        Map.entry("galbrena/air_rain",new Clock(24,6)),
        Map.entry("galbrena/heavy_01",new Clock(26,7,22)),
        Map.entry("galbrena/heavy_02",new Clock(26,7,22)),
        Map.entry("galbrena/heavy_03",new Clock(26,7,12,17,22)),
        Map.entry("galbrena/skill",new Clock(26,6,22)),
        Map.entry("galbrena/skill_ascend",new Clock(26,6,22)),
        Map.entry("galbrena/liberation",new Clock(105,55,58,61,65,68,71,74,77,80,84,87,90)),
        Map.entry("galbrena/intro",new Clock(24,5)),
        Map.entry("galbrena/demon_basic_01",new Clock(12,3)),
        Map.entry("galbrena/demon_basic_02",new Clock(18,4,9,14)),
        Map.entry("galbrena/demon_basic_03",new Clock(22,5,9,14,18)),
        Map.entry("galbrena/demon_basic_04",new Clock(24,6,11,15,20)),
        Map.entry("galbrena/demon_basic_05",new Clock(28,7,24)),
        Map.entry("galbrena/demon_heavy_01",new Clock(26,7,22)),
        Map.entry("galbrena/demon_heavy_02",new Clock(26,7,22)),
        Map.entry("galbrena/demon_heavy_03",new Clock(26,7,12,17,22)),
        Map.entry("galbrena/demon_dodge_counter",new Clock(20,4,8,12,16)),
        Map.entry("galbrena/demon_air",new Clock(24,6)),
        Map.entry("galbrena/demon_air_rain",new Clock(24,6)),
        Map.entry("galbrena/demon_skill",new Clock(24,6,20)));
    private InkInfernalContactClocks(){}
}
