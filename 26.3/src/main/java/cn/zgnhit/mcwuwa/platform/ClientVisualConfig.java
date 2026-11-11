package cn.zgnhit.mcwuwa.platform;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Pure configuration values: safe to register from the common entry point. */
public final class ClientVisualConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue OWN_INTENSITY,OTHER_INTENSITY;
    public static final ModConfigSpec.IntValue PARTICLE_BUDGET;
    public static final ModConfigSpec.BooleanValue AUTOMATIC_ROLE_SKINS,COMBAT_BODY_MODEL,QUIET_INTEGRATION_OVERLAYS;
    static {
        var builder=new ModConfigSpec.Builder();builder.push("visuals");
        OWN_INTENSITY=builder.comment("Local decorative combat effects; damage and collision are unaffected.").defineInRange("ownEffectIntensity",1.0,0.0,1.0);
        OTHER_INTENSITY=builder.comment("Remote decorative effects; summon models and necessary indicators remain visible.").defineInRange("otherEffectIntensity",.45,0.0,1.0);
        PARTICLE_BUDGET=builder.comment("Maximum mc_wuwa decorative particles per 20 game ticks; zero disables these particles.").defineInRange("particlesPerSecond",128,0,512);
        AUTOMATIC_ROLE_SKINS=builder.comment("Use bundled standard skins for local and server-announced remote roles; no account skin changes.").define("automaticRoleSkins",true);
        COMBAT_BODY_MODEL=builder.comment("Use the connected combat body in world rendering only. Disable to retain another mod's player body; gameplay and first-person feedback are unchanged.").define("combatBodyModel",true);
        QUIET_INTEGRATION_OVERLAYS=builder.comment("While using the role combat UI, hide optional IPN refill arrows and YSM extra screen portrait. Never changes those mods' configuration or behavior.").define("quietIntegrationOverlays",true);
        builder.pop();SPEC=builder.build();
    }
    public static void defaults(){OWN_INTENSITY.set(1.0);OTHER_INTENSITY.set(.45);PARTICLE_BUDGET.set(128);AUTOMATIC_ROLE_SKINS.set(true);COMBAT_BODY_MODEL.set(true);QUIET_INTEGRATION_OVERLAYS.set(true);}
    private ClientVisualConfig(){}
}
