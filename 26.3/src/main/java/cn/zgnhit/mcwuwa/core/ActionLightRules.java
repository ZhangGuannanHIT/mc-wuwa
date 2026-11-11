package cn.zgnhit.mcwuwa.core;

import java.util.Set;

/** Shared accepted-action visual selection; contains no rendering or game authority. */
public final class ActionLightRules {
    private static final Set<String> ROLES=Set.of("qiuyuan","ciaccona","shorekeeper","sigrika","galbrena","phoebe","suisui","cantarella","denia","iuno","changli","qingxiao","lupa","carlotta","zhezhi","zani","suoming","lucilla","augusta");
    private static final Set<String> SUPPLEMENTAL=Set.of("jinhsi/liberation","yinlin/liberation",
        "jinhsi/basic_01","jinhsi/basic_02","jinhsi/basic_03","jinhsi/basic_04","jinhsi/heavy","jinhsi/air","jinhsi/dodge_counter","jinhsi/skill","jinhsi/intro",
        "jinhsi/incarnation_basic_01","jinhsi/incarnation_basic_02","jinhsi/incarnation_basic_03","jinhsi/incarnation_basic_04","jinhsi/incarnation_heavy","jinhsi/incarnation_dodge","jinhsi/incarnation_counter",
        "jinhsi/illuminous_epiphany","jinhsi/overflowing_radiance","jinhsi/skill_overflowing","jinhsi/skill_crescent","jinhsi/epiphany_rays","jinhsi/epiphany_final",
        "yinlin/basic_01","yinlin/basic_02","yinlin/basic_03","yinlin/basic_04","yinlin/heavy","yinlin/air","yinlin/dodge_counter","yinlin/skill","yinlin/intro",
        "yinlin/magnetic_contact","yinlin/judgment_contact","yinlin/magnetic_roar","yinlin/magnetic_blast","yinlin/electromagnetic_blast","yinlin/judgment_strike","yinlin/skill_blast","yinlin/chameleon","yinlin/judgment_thunder",
        "chisa/basic_03","chisa/heavy","chisa/air","chisa/dodge_counter","chisa/skill","chisa/intro",
        "chisa/retract","chisa/pierce","chisa/shattered_plane","chisa/falling_finale","chisa/reel_counter",
        "chisa/skill_saw_cycle","chisa/skill_saw_hold","chisa/saw_basic_01","chisa/saw_basic_02","chisa/saw_basic_02_hold","chisa/saw_resonance_02",
        "chisa/saw_basic_03","chisa/saw_basic_03_hold","chisa/saw_resonance_03","chisa/saw_counter","chisa/saw_counter_hold","chisa/saw_finale",
        "lynae/basic_01","lynae/basic_02","lynae/basic_03","lynae/basic_04","lynae/heavy","lynae/air","lynae/dodge_counter","lynae/skill","lynae/liberation","lynae/intro",
        "lynae/cruise_dodge_counter","lynae/cruise_jump_bundle","lynae/cruise_air","lynae/cruise_air_heavy","lynae/skill_additive","lynae/toward_colorful_tomorrow",
        "lynae/inspiration_1","lynae/inspiration_2","lynae/inspiration_3","lynae/rainbow_splash","lynae/visual_impact",
        "chisa/severing_pincers",
        "phrolova/hecate_basic","phrolova/hecate_basic_01","phrolova/hecate_basic_02","phrolova/hecate_strings","phrolova/hecate_winds","phrolova/hecate_brass","phrolova/hecate_color","phrolova/hecate_fortissimo","phrolova/hecate_finale",
        "phrolova/heavy","phrolova/air","phrolova/dodge_counter",
        "mornye/heavy","mornye/air","mornye/dodge_counter","mornye/observation_basic_01","mornye/observation_basic_02","mornye/observation_basic_03","mornye/observation_dodge_counter",
        "camellya/heavy","camellya/air","camellya/dodge_counter","camellya/skill",
        "hiyuki/air","hiyuki/dodge_counter","hiyuki/intro","hiyuki/liberation","hiyuki/liberation_return","hiyuki/iai","hiyuki/future_air","hiyuki/future_air_01","hiyuki/future_air_02","hiyuki/future_dodge_counter",
        "aero_rover/heavy","aero_rover/heavy_strangle","aero_rover/air","aero_rover/dodge_counter","aero_rover/wind_air_01","aero_rover/wind_air_02",
        "yangyang_xuanling/air","yangyang_xuanling/dodge_counter","yangyang_xuanling/skill","yangyang_xuanling/liberation","yangyang_xuanling/intro",
        "yangyang_xuanling/feather_basic_01","yangyang_xuanling/feather_basic_02","yangyang_xuanling/feather_basic_03","yangyang_xuanling/feather_basic_04",
        "yangyang_xuanling/feather_heavy","yangyang_xuanling/feather_air","yangyang_xuanling/feather_dodge_counter","yangyang_xuanling/skill_feather","yangyang_xuanling/feather_descent","yangyang_xuanling/flow_feather",
        "yangyang_xuanling/listening_basic_01","yangyang_xuanling/listening_basic_02","yangyang_xuanling/listening_basic_03","yangyang_xuanling/listening_counter_01","yangyang_xuanling/listening_counter_02","yangyang_xuanling/listening_counter_03",
        "aemeath/heavy","aemeath/heavy_charge_2","aemeath/air","aemeath/dodge_counter","aemeath/mech_air","aemeath/mech_dodge_counter","aemeath/liberation","aemeath/liberation_finale",
        "hsin/heavy","hsin/air","hsin/dodge_counter","hsin/heavy_wander","hsin/air_wander","hsin/illuminated_heavy","hsin/illuminated_uppercut","hsin/illuminated_air","hsin/illuminated_dodge_counter","hsin/thousand_palaces_counter",
        "hsin/intro","hsin/intro_unison","hsin/intro_full_unison","hsin/illuminated_intro","hsin/illuminated_intro_unison","hsin/illuminated_intro_full_unison",
        "cartethyia/dodge_counter","cartethyia/liberation","cartethyia/liberation_finale",
        "cartethyia/fleurdelys_basic_01","cartethyia/fleurdelys_basic_02","cartethyia/fleurdelys_basic_03","cartethyia/fleurdelys_basic_04","cartethyia/fleurdelys_basic_05",
        "cartethyia/fleurdelys_heavy","cartethyia/fleurdelys_heavy_enhanced","cartethyia/fleurdelys_uppercut","cartethyia/fleurdelys_counter",
        "cartethyia/fleurdelys_air_01","cartethyia/fleurdelys_air_02","cartethyia/fleurdelys_air_03","cartethyia/fleurdelys_skill","cartethyia/fleurdelys_skill_followup",
        "aero_rover/intro","aero_rover/skill","aero_rover/skill_sky","aero_rover/liberation","aero_rover/outro",
        "aero_rover/unbound_wind_01","aero_rover/unbound_wind_02","camellya/intro",
        "camellya/ephemeral","camellya/immortal_blossom","camellya/bloom_dark_heart","camellya/liberation",
        "camellya/bloom_basic_01","camellya/bloom_basic_02","camellya/bloom_basic_03","camellya/bloom_basic_04",
        "camellya/bloom_spinning","camellya/bloom_twirl","camellya/bloom_dodge_counter",
        "mornye/skill","mornye/skill_optimal","mornye/heavy_potential","mornye/heavy_inversion","mornye/intro","mornye/liberation","mornye/observation_aura","mornye/block_guard",
        "phrolova/skill","phrolova/movement_death","phrolova/composition_finale","phrolova/intro","phrolova/intro_eternal",
        "phrolova/liberation_curtain","phrolova/undying_reverie","phrolova/conducting_stasis_field",
        "aemeath/skill","aemeath/skill_dawn","aemeath/wing_ensemble_stage","aemeath/wing_ensemble_descend",
        "chisa/liberation","hsin/illuminated_entry","cartethyia/skill","hiyuki/skill",
        "lynae/phantom_leap_1","lynae/phantom_leap_2","lynae/phantom_leap_3",
        "yangyang_xuanling/flow_azure","yangyang_xuanling/heavy","hsin/skill",
        "mornye/basic_01","mornye/basic_02","mornye/basic_03","mornye/basic_04",
        "phrolova/basic_01","phrolova/basic_02","phrolova/basic_03",
        "cartethyia/basic_01","cartethyia/basic_02","cartethyia/basic_03","cartethyia/basic_04","cartethyia/heavy","cartethyia/air",
        "cartethyia/air_one_sword","cartethyia/air_two_swords","cartethyia/air_three_swords",
        "cartethyia/intro","cartethyia/fleurdelys_intro","cartethyia/gravity_field","cartethyia/gravity_followup_field",
        "aemeath/basic_01","aemeath/basic_02","aemeath/basic_03","aemeath/basic_04",
        "aemeath/mech_basic_01","aemeath/mech_basic_02","aemeath/mech_basic_03","aemeath/mech_basic_04","aemeath/mech_heavy","aemeath/mech_heavy_charge_2",
        "aemeath/intro","aemeath/intro_starlight","hiyuki/skill_jade","hiyuki/skill_falling",
        "hiyuki/basic_01","hiyuki/basic_02","hiyuki/basic_03",
        "hiyuki/heavy","hiyuki/future_basic_01","hiyuki/future_basic_02","hiyuki/future_basic_03","hiyuki/future_basic_04","hiyuki/future_basic_05",
        "hiyuki/future_heavy","hiyuki/future_heavy_frost",
        "lynae/cruise_basic_01","lynae/cruise_basic_02","lynae/cruise_basic_03","lynae/cruise_basic_04","lynae/cruise_basic_05",
        "aero_rover/basic_01","aero_rover/basic_02","aero_rover/basic_03","aero_rover/basic_04","chisa/basic_01","chisa/basic_02",
        "camellya/basic_01","camellya/basic_02","camellya/basic_03","camellya/basic_04","camellya/basic_05",
        "hsin/illuminated_skill","hsin/illuminated_descend","hsin/illuminated_suppress","hsin/worldly_step","hsin/worldly_suppress",
        "hsin/liberation","hsin/flying_palace","hsin/illuminated_word_lock","hsin/basic_01","hsin/basic_02","hsin/basic_03","hsin/basic_04",
        "hsin/illuminated_basic_01","hsin/illuminated_basic_02","hsin/illuminated_basic_03",
        "hsin/thousand_palaces_basic_01","hsin/thousand_palaces_basic_02","hsin/thousand_palaces_basic_03","hsin/thousand_palaces_basic_04","hsin/palaces_formation","hsin/liberation_palaces",
        "yangyang_xuanling/basic_01","yangyang_xuanling/basic_02","yangyang_xuanling/basic_03","yangyang_xuanling/basic_04");
    private ActionLightRules(){}
    public static boolean supports(String action){
        int slash=action.indexOf('/');
        return slash>0&&(ROLES.contains(action.substring(0,slash))||SUPPLEMENTAL.contains(action));
    }
    public static boolean supplemental(String action){return SUPPLEMENTAL.contains(action);}
}
