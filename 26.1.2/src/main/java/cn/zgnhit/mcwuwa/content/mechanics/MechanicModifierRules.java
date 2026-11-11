package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.*;

/** Typed interpretation of strategy effects. Unknown effects never become generic damage bonuses. */
public final class MechanicModifierRules {
    /** Source1306 C3 is permanent and action-qualified, not an expiring buff. */
    public static double augustaCoefficient(int chain,String actionId){
        return chain>=3&&Set.of("augusta/heavy_retreat","augusta/counter_retreat","augusta/heavy_spin","augusta/heavy_uppercut",
            "augusta/skill_descent","augusta/realm_sun","augusta/realm_finale").contains(actionId)?.25:0;
    }
    public record Result(Map<String, Double> attributes, double damageBonus, double amplification,
                         double coefficientIncrease, double multiplierAddition) {
        public Result { attributes = Map.copyOf(attributes); }
    }
    private MechanicModifierRules() { }
    /** These four casts alone can own an Immediate Response snapshot. */
    public static boolean aemeathImmediateAttack(String actionId){
        return actionId.startsWith("aemeath/")&&Set.of("heavy","heavy_charge_2","mech_heavy","mech_heavy_charge_2").contains(local(actionId));
    }
    /** Frozen Cartethyia finale: 20% vulnerability per pre-hit aero layer, at most five. */
    public static double cartethyiaFinaleAmplification(int preHitLayers) {
        if (preHitLayers < 0) throw new IllegalArgumentException("negative aero layers");
        return 1 + .20 * Math.min(5, preHitLayers);
    }
    public static boolean cartethyiaCopiesAeroBefore(MechanicsState state,String action) {
        return hasOutsiderRight(state)&&Set.of("cartethyia/fleurdelys_basic_05","cartethyia/fleurdelys_air_02","cartethyia/fleurdelys_heavy_enhanced").contains(action);
    }
    public static boolean cartethyiaCopiesAeroAfter(MechanicsState state,String action) {
        return hasOutsiderRight(state)&&action.equals("cartethyia/fleurdelys_skill_followup");
    }
    private static boolean hasOutsiderRight(MechanicsState state) {
        return state!=null&&state.characterId().equals("cartethyia")&&state.form().equals("fleurdelys")&&state.timer("manifestation")>0&&state.timer("outsider_power")>0;
    }
    public static boolean camellyaAffected(String actionId) {
        String action = local(actionId);
        return action.startsWith("basic_") || action.startsWith("bloom_")
            || Set.of("heavy", "air", "dodge_counter", "skill").contains(action);
    }
    public static Result evaluate(Collection<MechanicsEngine.Effect> effects, String actionId, String kind,
                                  String element, boolean targetAnomaly, boolean finalHit) {
        String action = local(actionId); Map<String, Double> attrs = new HashMap<>();
        double bonus = 0, amp = 1, coefficient = 0, addition = 0,threadBonus=0,lupaHuntAttack=0;
        for (var effect : effects) {
            double value = effect.amount(); String id = effect.id();
            if (id.equals("lynae_liberation_damage_bonus") || id.equals(element + "_damage_bonus")) bonus += value;
            switch (id) {
                case "lucilla_basic_bonus" -> {if(kind.equals("basic"))bonus+=value;}
                case "lucilla_echo_bonus","lucilla_team_echo_bonus","lucilla_c2_echo_bonus" -> {if(kind.equals("echo"))bonus+=value;}
                case "lucilla_zoom_crit" -> {if(kind.equals("echo"))attrs.merge("crit_damage",value,Double::sum);}
                case "lucilla_c1_crit" -> attrs.merge("crit_rate",value,Double::sum);
                case "lucilla_c4_attack" -> attrs.merge("attack_pct",value,Double::sum);
                case "lucilla_c3_finale" -> {if(Set.of("lucilla/finale","lucilla/finale_echo").contains(actionId))coefficient+=value;}
                case "lucilla_c5_forget" -> {if(Set.of("lucilla/forget","lucilla/forget_echo").contains(actionId))coefficient+=value;}
                case "lucilla_c6_remembrance" -> {if(Set.of("lucilla/finale","lucilla/finale_echo").contains(actionId))bonus+=value;}
                case "lucilla_outro_echo" -> {if(kind.equals("echo"))amp+=value;}
                case "suoming_intro_electro","suoming_next_electro" -> {if(element.equals("electro"))bonus+=value;}
                case "suoming_c2_crit_damage","suoming_contract_crit_damage" -> attrs.merge("crit_damage",value,Double::sum);
                case "suoming_c4_attack" -> attrs.merge("attack_pct",value,Double::sum);
                case "suoming_c1_intro" -> {if(Set.of("suoming/intro","suoming/intro_deep","suoming/intro_unison","suoming/intro_deep_unison").contains(actionId))coefficient+=value;}
                case "suoming_contract_multiplier" -> {
                    if(actionId.startsWith("suoming/deep_basic_")||actionId.startsWith("suoming/thunder_spin_"))coefficient+=value;
                }
                case "suoming_c3_basic" -> {if(kind.equals("basic"))amp+=value;}
                case "suoming_c5_liberation" -> {if(actionId.equals("suoming/liberation"))coefficient+=value;}
                case "suoming_c6_heart" -> {if(Set.of("suoming/heart","suoming/heart_hold").contains(actionId))coefficient+=value;}
                case "suoming_outro" -> {
                    if(element.equals("electro"))amp+=effect.values().getOrDefault("electroAmplification",.20);
                    if(kind.equals("skill")&&effect.values().getOrDefault("recipientHasUnison",0d)>0)amp+=effect.values().getOrDefault("skillAmplification",0d);
                }
                case "zani_intro_spectro","zani_c1_spectro" -> {if(element.equals("spectro"))bonus+=value;}
                case "zani_c2_crit" -> attrs.merge("crit_rate",value,Double::sum);
                case "zani_c4_attack","augusta_c4_attack" -> attrs.merge("attack_pct",value,Double::sum);
                case "zani_c2_suppression" -> {
                    if(Set.of("zani/suppression","zani/breach_counter").contains(actionId))coefficient+=value;
                }
                case "zani_c5_rekindle" -> {if(actionId.equals("zani/liberation"))coefficient+=value;}
                case "zani_c6_heavy" -> {
                    if(Set.of("zani/heavy_dawn","zani/heavy_day","zani/heavy_night","zani/heavy_flash").contains(actionId))coefficient+=value;
                }
                case "zani_c3_finale_addition" -> {if(actionId.equals("zani/finale")&&finalHit)addition+=value;}
                case "augusta_crown_electro" -> {if(element.equals("electro"))bonus+=value;}
                case "augusta_crown_crit_rate" -> attrs.merge("crit_rate",value,Double::sum);
                case "augusta_crown_crit_damage","augusta_overcap_crit_damage" -> attrs.merge("crit_damage",value,Double::sum);
                case "augusta_outro" -> amp+=value;
                case "augusta_c3_multiplier" -> {
                    if(Set.of("augusta/heavy_retreat","augusta/counter_retreat","augusta/heavy_spin","augusta/heavy_uppercut",
                        "augusta/skill_descent","augusta/realm_sun","augusta/realm_finale").contains(actionId))coefficient+=value;
                }
                case "zhezhi_brush_attack","zhezhi_c3_attack","zhezhi_c4_attack" -> attrs.merge("attack_pct",value,Double::sum);
                case "zhezhi_c1_crit" -> attrs.merge("crit_rate",value,Double::sum);
                case "zhezhi_maestro_basic" -> {if(kind.equals("basic"))bonus+=value;}
                case "zhezhi_outro" -> {
                    if(element.equals("glacio"))amp+=effect.values().getOrDefault("glacioAmplification",0d);
                    if(kind.equals("skill"))amp+=effect.values().getOrDefault("skillAmplification",0d);
                }
                // Six-role buffs arrive already scoped to their actual recipient by the host.
                case "changli_c4_attack" -> attrs.merge("attack_pct",value,Double::sum);
                case "changli_c2_crit", "lupa_c1_crit" -> attrs.merge("crit_rate",value,Double::sum);
                case "carlotta_c4_skill_bonus", "lupa_c5_liberation" -> {
                    if(kind.equals(id.startsWith("carlotta")?"skill":"liberation"))bonus+=value;
                }
                case "changli_outro" -> {
                    if(element.equals("fusion"))amp+=effect.values().getOrDefault("fusionAmplification",0d);
                    if(kind.equals("liberation"))amp+=effect.values().getOrDefault("liberationAmplification",0d);
                }
                case "iuno_outro" -> {if(kind.equals("heavy"))amp+=effect.values().getOrDefault("heavyAmplification",value);}
                case "iuno_pale_blessing" -> {
                    double layers=Math.max(0,Math.min(effect.values().getOrDefault("maxStacks",10d),value));
                    amp+=layers*effect.values().getOrDefault("perStackAmplification",.04);
                    if(layers>=effect.values().getOrDefault("maxStacks",10d))amp+=effect.values().getOrDefault("fullStackExtra",0d);
                }
                case "lupa_outro" -> {
                    if(element.equals("fusion"))amp+=effect.values().getOrDefault("fusionAmplification",0d);
                    if(kind.equals("basic"))amp+=effect.values().getOrDefault("basicAmplification",0d);
                }
                case "lupa_burning_flag" -> attrs.merge("attack_pct",value,Double::sum);
                case "lupa_c2_fusion" -> {if(element.equals("fusion"))bonus+=value;}
                case "lupa_hunt_attack" -> lupaHuntAttack=Math.max(lupaHuntAttack,value);
                case "lupa_hunt" -> {
                    // Tier effect is the TOTAL 6/12/18%, not an additive second buff.
                    lupaHuntAttack=Math.max(lupaHuntAttack,effect.values().getOrDefault("baseAttackBonus",0d));
                    if(element.equals("fusion")&&effect.values().getOrDefault("targetBoss",0d)>0){
                        bonus+=effect.values().getOrDefault("bossFusionBonus",0d);
                        if(effect.values().getOrDefault("teamFusionCount",0d)>=3||effect.values().getOrDefault("ignoreFusionCount",0d)>0)
                            bonus+=effect.values().getOrDefault("extraBossFusionBonus",0d);
                    }
                }
                case "lupa_glory" -> {
                    if(element.equals("fusion")){
                        double count=Math.max(0,Math.min(2,effect.values().getOrDefault("otherFusionCount",0d)));
                        double ignore=effect.values().getOrDefault("baseResistanceIgnore",0d)+count*effect.values().getOrDefault("perOtherFusion",0d);
                        if(effect.values().getOrDefault("teamFusionCount",0d)>=3)ignore+=effect.values().getOrDefault("threeFusionExtra",0d);
                        attrs.merge("resistance_ignore",ignore,Double::sum);
                    }
                }
                case "denia_entropy_fusion" -> {if(element.equals("fusion"))bonus+=effect.values().getOrDefault("fusionBonus",0d);}
                case "denia_entropy_tune" -> {
                    // Host supplies recipient's current efficiency as a fractional bonus: .20 = +20%.
                    double extra=Math.max(0,effect.values().getOrDefault("recipientOffTuneEfficiency",0d));
                    double points=effect.values().getOrDefault("tuneBreakAmplification",0d)+Math.min(effect.values().getOrDefault("extraCap",0d),extra/.10*effect.values().getOrDefault("perTenEfficiencyAboveBase",0d));
                    attrs.merge("tune_break_amplification",points,Double::sum);
                }
                case "denia_c2_fusion_applier" -> {if(element.equals("fusion"))bonus+=value;}
                case "denia_c2_tune_applier" -> attrs.merge("tune_break_amplification",value,Double::sum);
                case "qingxiao_c4_applier" -> attrs.merge("attack_pct",value,Double::sum);
                case "denia_tune_outro" -> amp+=effect.values().getOrDefault("recipientAppliedTuneOffset",0d)>0?effect.values().getOrDefault("afterTuneOffset",value):value;
                case "denia_fusion_outro" -> {
                    // Never turn a target-local Fusion Burst modifier into all Fusion damage.
                    if(effect.values().getOrDefault("targetNearActive",0d)>0&&effect.values().getOrDefault("fusionBurstDamage",0d)>0)amp+=value;
                }
                case "suisui_outro_amplification" -> amp+=value;
                case "suisui_havoc_bypass" -> {if(element.equals("havoc")){attrs.merge("defense_ignore",value,Double::sum);attrs.merge("resistance_ignore",effect.values().getOrDefault("resistanceIgnore",0d),Double::sum);}}
                case "suisui_smoke_attack" -> attrs.merge("attack_pct",value,Double::sum);
                case "cantarella_outro" -> {if(element.equals("havoc"))amp+=value;if(kind.equals("skill"))amp+=effect.values().getOrDefault("skillAmplification",0d);}
                case "shorekeeper_outro" -> amp+=value;
                case "sigrika_c4_attack" -> attrs.merge("attack_pct",value,Double::sum);
                case "galbrena_c4_bonus" -> bonus+=value;
                case "sigrika_origin" -> {if(actionId.startsWith("sigrika/heavy_origin_")){coefficient+=value;amp+=effect.values().getOrDefault("amplification",0d);}}
                case "qiuyuan_liberation_crit" -> attrs.merge("crit_damage",value,Double::sum);
                case "qiuyuan_wine_attack" -> attrs.merge("attack_pct",value,Double::sum);
                case "qiuyuan_canopy_crit" -> attrs.merge("crit_damage",value,Double::sum);
                case "qiuyuan_bamboo" -> {if(kind.equals("echo"))bonus+=value;}
                case "qiuyuan_bamboo_amplification", "qiuyuan_outro" -> {if(kind.equals("echo"))amp+=value;}
                case "ciaccona_solo_aero", "ciaccona_performance_aero" -> {if(element.equals("aero"))bonus+=value;}
                case "phrolova_conducting_attack" -> attrs.merge("attack_pct", value, Double::sum);
                case "lynae_premix_bonus" -> { if(action.equals("skill_additive"))bonus+=value; }
                case "yangyang_azure_critical_damage" -> { if(action.equals("heavy"))attrs.merge("crit_damage",value,Double::sum); }
                case "yangyang_feather_critical_damage" -> { if(Set.of("feather_heavy","feather_descent","listening_basic_01","listening_basic_02","listening_basic_03","listening_counter_01","listening_counter_02","listening_counter_03").contains(action))attrs.merge("crit_damage",value,Double::sum); }
                case "aemeath_immediate_critical_damage" -> { if (aemeathImmediateAttack(actionId)) attrs.merge("crit_damage", value, Double::sum); }
                case "critical_protocol_crit" -> {
                    if (action.equals("liberation")) { attrs.merge("crit_rate", value, Double::sum); attrs.merge("crit_damage", effect.values().getOrDefault("critDamage", 0d), Double::sum); }
                }
                case "jinhsi_epiphany_damage_bonus" -> { if (action.startsWith("epiphany_")) bonus += value; }
                case "camellya_budding_multiplier" -> { if (camellyaAffected(action)) coefficient += value; }
                case "chisa_myriad_multiplier" -> { if (ChisaSawRules.empoweredAttack(action)) coefficient += value; }
                case "chisa_myriad_finale_multiplier" -> { if(action.equals("saw_finale"))coefficient+=value; }
                case "chisa_void_thread" -> threadBonus=Math.max(threadBonus,effect.values().getOrDefault("allDamageBonus",0d));
                case "jinhsi_epiphany_additive_multiplier" -> { if (action.equals("epiphany_final")) addition += value; }
                case "chisa_finale_additive_multiplier" -> { if (action.equals("saw_finale")) addition += value; }
                case "hiyuki_return_forge_multiplier" -> { if (action.equals("liberation_return") && finalHit) addition += value; }
                case "phrolova_echoes_additive_multiplier" -> { if (action.equals("composition_finale") && finalHit) addition += value; }
                // All applicable DMG Amplify descriptions share one additive zone.
                // Bonus, vulnerability and explicit final modifiers are settled separately.
                case "mornye_outro_all_amplification", "aemeath_outro_other_roles_amp", "aemeath_outro_matching", "lynae_c2_outro" -> amp += value;
                case "lynae_outro" -> {
                    amp += value;
                    if (kind.equals("liberation")) amp += effect.values().getOrDefault("liberationAmplification", 0d);
                }
                case "yinlin_outro" -> {
                    if (element.equals("electro")) amp += effect.values().getOrDefault("electroAmplification", 0d);
                    if (kind.equals("liberation")) amp += effect.values().getOrDefault("liberationAmplification", 0d);
                }
                case "phrolova_outro" -> {
                    if (element.equals("havoc")) amp += value;
                    if (kind.equals("heavy")) amp += effect.values().getOrDefault("heavyAmplification",0d);
                }
                case "cartethyia_outro" -> { if (element.equals("aero") && targetAnomaly) amp += value; }
                case "yangyang_outro_active" -> { if (element.equals("havoc")) amp += value; }
                case "hsin_outro_electro" -> { if(element.equals("electro"))amp+=value; }
                case "hsin_outro_unison" -> amp+=value; // Host checks the real lamp recipient.
                // The host supplies this effect only after checking the actual target's Chafe/Frost.
                case "hiyuki_outro" -> { if(element.equals("glacio"))amp+=value; }
                case "heavy_damage_amplification" -> { if (aemeathImmediateAttack(actionId)) amp += value; }
                default -> { }
            }
        }
        if(lupaHuntAttack>0)attrs.merge("attack_pct",lupaHuntAttack,Double::sum);
        return new Result(attrs, bonus+threadBonus, amp, coefficient, addition);
    }
    /** Incoming-hit and interruption paths consume these separately from outgoing damage. */
    public static double incomingDamageReduction(Collection<MechanicsEngine.Effect> effects) {
        double reduction=0;
        for(var effect:effects)if(effect.id().equals("changli_sacrifice_action")||effect.id().equals("qingxiao_guard_reduction"))
            reduction=Math.max(reduction,effect.amount());
        return Math.max(0,Math.min(1,reduction));
    }
    public static boolean resistsInterruption(Collection<MechanicsEngine.Effect> effects) {
        return effects.stream().anyMatch(e->e.values().getOrDefault("interruptionResistance",0d)>0||e.values().getOrDefault("immuneInterruption",0d)>0);
    }
    /** Source explicitly makes energy zero while budding and 2.5x when consuming petals otherwise. */
    public static double camellyaEnergyMultiplier(String actionId, boolean budding, boolean hadPetals) {
        if (!camellyaAffected(actionId)) return 1;
        return budding ? 0 : hadPetals ? 2.5 : 1;
    }
    private static String local(String id) { int slash = id.lastIndexOf('/'); return slash < 0 ? id : id.substring(slash + 1); }
}
