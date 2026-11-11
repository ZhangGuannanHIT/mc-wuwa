package cn.zgnhit.mcwuwa.platform;
import cn.zgnhit.mcwuwa.core.CombatMath;
import net.neoforged.neoforge.common.ModConfigSpec;
public final class CombatConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue REFERENCE_DPS;
    public static final ModConfigSpec.DoubleValue DAMAGE_MAPPING_MULTIPLIER;
    public static final ModConfigSpec.ConfigValue<String> CALIBRATION_EVIDENCE;
    public static final ModConfigSpec.BooleanValue PREVIEW_RESOURCE_YIELDS;
    public static final ModConfigSpec.BooleanValue USE_BUNDLED_CALIBRATION;
    public static final ModConfigSpec.DoubleValue NATURAL_BOSS_SPAWN_CHANCE;
    public static final double BUNDLED_SWORD_DPS=12.532979647318522;
    public static final String BUNDLED_EVIDENCE="bundled_26.1.2_neoforge_26.1.2.78_sword_50e0e55eaf23b9ed548ebc237ef99c652c51e0b7be805356d609d51bbb024f9b";
    static {
        var builder = new ModConfigSpec.Builder();
        REFERENCE_DPS = builder.comment("Server-specific measured sword DPS. 0 uses the bundled verified baseline when enabled; otherwise native damage is disabled.").defineInRange("measuredSwordDps", 0d, 0, 100000);
        DAMAGE_MAPPING_MULTIPLIER = builder.comment("Gameplay damage mapping multiplier. Default2.5 was requested after low-damage playtesting; applies exactly once to all WuWa normal/echo/anomaly/tune damage, not vanilla damage or healing/shields/HP. Does not change the measured sword reference.").defineInRange("damageMappingMultiplier", 2.5d, .1, 100);
        CALIBRATION_EVIDENCE = builder.comment("Benchmark report id/hash from this server environment.").define("calibrationEvidence", "unmeasured");
        USE_BUNDLED_CALIBRATION=builder.comment("Use the repeatedly measured Minecraft 26.1.2 / NeoForge 26.1.2.78 project baseline until a server-specific benchmark exists. This does not claim the current modpack has been measured. Disable for strict local calibration.").define("useBundledCalibration",true);
        PREVIEW_RESOURCE_YIELDS = builder.comment("Development preview only: enable explicitly authored resource yields where source values are unknown. False is the strict source-fidelity mode; see data audit.").define("allowAuthoredResourceYields", false);
        NATURAL_BOSS_SPAWN_CHANCE=builder.comment("Additional acceptance chance for natural WuWa echo Boss spawns. Default0.10 reduces successful natural spawn attempts by90% compared with the old behavior. Spawn eggs and commands are unaffected;0 disables natural echo Boss spawning.").defineInRange("naturalBossSpawnChance",.10d,0,1);
        SPEC = builder.build();
    }
    public static CombatMath.DamageScale scale() {
        var baseline=REFERENCE_DPS.getAsDouble()>0&&!CALIBRATION_EVIDENCE.get().equals("unmeasured")&&!CALIBRATION_EVIDENCE.get().isBlank()
            ?CombatMath.DamageScale.calibrated(REFERENCE_DPS.getAsDouble(),CALIBRATION_EVIDENCE.get())
            :USE_BUNDLED_CALIBRATION.getAsBoolean()?CombatMath.DamageScale.calibrated(BUNDLED_SWORD_DPS,BUNDLED_EVIDENCE):CombatMath.DamageScale.unmeasured();
        return baseline.withMultiplier(DAMAGE_MAPPING_MULTIPLIER.getAsDouble());
    }
    public static String calibrationScope(){return REFERENCE_DPS.getAsDouble()>0&&!CALIBRATION_EVIDENCE.get().equals("unmeasured")&&!CALIBRATION_EVIDENCE.get().isBlank()?"server_measurement":USE_BUNDLED_CALIBRATION.getAsBoolean()?"bundled_baseline":"unmeasured";}
    private CombatConfig() { }
}
