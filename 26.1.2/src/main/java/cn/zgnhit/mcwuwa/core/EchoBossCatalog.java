package cn.zgnhit.mcwuwa.core;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** User-approved encounters. All timings, dimensions and damage below are explicit MC adaptations. */
public final class EchoBossCatalog {
    public enum Pattern { COMBO, DASH, BREATH, WAVE, DIVE, LIGHTNING, SWEEP, WHEEL, BARRAGE, PARRY, BURST, GUARD }
    public record Move(String name, Pattern pattern, int windup, int recovery, double range,
                       float damageScale, boolean parryable) {
        public int duration() { return windup + recovery; }
    }
    public record Boss(String id, String name, String model, int echoId, int cores, int weight,
                       int separation, String dimension, Set<String> biomes, boolean nightOnly,
                       float width, float height, float scale, double health, double attack, List<Move> moves) {
        public Boss { biomes = Set.copyOf(biomes); moves = List.copyOf(moves); }
        public boolean habitat(String dimensionId, String biome, long overworldTime, double originDistance) {
            long daytime = Math.floorMod(overworldTime, 24000);
            return dimension.equals(dimensionId) && biomes.contains(biome)
                && (!nightOnly || daytime >= 13000 && daytime < 23000)
                && (!dimension.equals("minecraft:the_end") || originDistance >= 1000);
        }
    }
    private static Move m(String name, Pattern pattern, int windup, int recovery, double range, float damage, boolean parry) {
        return new Move(name, pattern, windup, recovery, range, damage, parry);
    }
    private static Set<String> b(String... names) { return Set.of(names); }
    public static final List<Boss> ALL = List.of(
        new Boss("impermanence_heron", "无常凶鹭", "echo_heron", 6000052, 1, 3, 96, "minecraft:overworld",
            b("badlands", "eroded_badlands", "wooded_badlands", "windswept_hills", "windswept_gravelly_hills", "windswept_forest"), true,
            2.4f, 2.8f, 1.1f, 140, 7, List.of(
                m("喙击", Pattern.COMBO, 20, 24, 4, .75f, true), m("飞羽冲刺", Pattern.DASH, 26, 22, 14, 1, true),
                m("半空吐息", Pattern.BREATH, 32, 32, 16, .7f, false), m("振翅扩散波", Pattern.WAVE, 32, 24, 9, 1.2f, false))),
        new Boss("jue", "角", "jue_dragon", 6000059, 3, 1, 192, "minecraft:overworld",
            b("meadow", "grove", "snowy_slopes", "frozen_peaks", "jagged_peaks", "stony_peaks", "windswept_hills", "windswept_gravelly_hills"), false,
            3.2f, 3.6f, 1.05f, 220, 9, List.of(
                m("盘旋俯冲", Pattern.DIVE, 34, 28, 16, 1.1f, true), m("光束吐息", Pattern.BREATH, 36, 36, 22, .75f, false),
                m("定点落雷", Pattern.LIGHTNING, 36, 24, 24, 1.2f, false), m("横扫", Pattern.SWEEP, 26, 22, 7, 1, true))),
        new Boss("nightmare_crownless", "梦魇·无冠者", "echo_crownless", 6000090, 1, 3, 96, "minecraft:overworld",
            b("dark_forest", "badlands", "eroded_badlands", "wooded_badlands"), true,
            1.8f, 3.6f, 1.05f, 170, 8, List.of(
                m("拳脚连击", Pattern.COMBO, 20, 30, 4.5, .65f, true), m("武器突刺", Pattern.DASH, 24, 22, 12, 1, true),
                m("武器横扫", Pattern.SWEEP, 28, 22, 6, 1.1f, true), m("翼刃冲击", Pattern.WAVE, 30, 24, 10, 1, false))),
        new Boss("nightmare_hecate", "梦魇·赫卡忒", "echo_hecate", 6000115, 1, 3, 96, "minecraft:overworld",
            b("dark_forest", "swamp", "mangrove_swamp"), true,
            1.8f, 4, 1.05f, 160, 7, List.of(
                m("鞭刃连击", Pattern.COMBO, 24, 30, 7, .65f, true), m("回旋轮刃", Pattern.WHEEL, 30, 36, 17, .9f, false),
                m("光束扫射", Pattern.BREATH, 34, 36, 19, .75f, false))),
        new Boss("hyvatia", "海维夏", "echo_hyvatia", 6000189, 1, 3, 96, "minecraft:overworld",
            b("beach", "snowy_beach", "stony_shore"), false,
            1.4f, 2.8f, 1.1f, 160, 7, List.of(
                m("高速近战", Pattern.COMBO, 18, 30, 4.5, .65f, true), m("飞行俯冲", Pattern.DIVE, 28, 24, 15, 1.1f, true),
                m("弹幕", Pattern.BARRAGE, 28, 36, 22, .65f, false), m("拼刀", Pattern.PARRY, 36, 26, 5, 1.5f, true))),
        new Boss("furnace_husk", "炉芯机骸", "echo_furnace", 6000190, 1, 3, 96, "minecraft:the_nether",
            b("nether_wastes", "basalt_deltas"), false,
            2.8f, 2.8f, 1.1f, 200, 9, List.of(
                m("爪击", Pattern.COMBO, 24, 26, 5, .75f, true), m("扑撞", Pattern.DIVE, 30, 26, 13, 1.2f, true),
                m("刀波", Pattern.WHEEL, 28, 26, 16, 1, false), m("蓄力爆发", Pattern.BURST, 44, 30, 9, 1.4f, false))),
        new Boss("sigillum", "辛吉勒姆", "echo_sigillum", 6000191, 3, 1, 192, "minecraft:the_end",
            b("end_highlands", "end_midlands", "end_barrens", "small_end_islands"), false,
            1.6f, 3, 1.15f, 220, 10, List.of(
                m("手刃连击", Pattern.COMBO, 22, 28, 5, .65f, true), m("大剑横扫", Pattern.SWEEP, 30, 24, 7, 1.15f, true),
                m("蓄力炮", Pattern.BREATH, 42, 26, 24, 1.2f, false), m("滑翔突进", Pattern.DASH, 28, 24, 16, 1.1f, true),
                m("反制架势", Pattern.GUARD, 18, 36, 6, 1.4f, false)))
    );
    public static Optional<Boss> find(String registryId) {
        return ALL.stream().filter(boss -> registryId.equals("mc_wuwa:" + boss.id())).findFirst();
    }
    private EchoBossCatalog() {}
}
