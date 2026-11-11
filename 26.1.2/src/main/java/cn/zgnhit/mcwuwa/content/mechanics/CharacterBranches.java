package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.List;
import java.util.Map;
import static cn.zgnhit.mcwuwa.content.mechanics.MechanicsEngine.*;
import static cn.zgnhit.mcwuwa.content.mechanics.ExtendedMechanics.*;

/** Longer form strategies kept outside the common engine to preserve navigation and test seams. */
final class CharacterBranches {
    private CharacterBranches() { }

    static void yangyang(Transition t, Input input) {
        if(t.form.equals("feather")&&t.has("listening")){
            form(t,"listening",0);t.combo=Math.clamp(t.time("yangyang_listening_cursor"),1,3);
        }
        boolean feather = t.form.equals("feather"), listening = t.form.equals("listening");
        switch (input) {
            case BASIC -> {
                if(listening){yangyangListeningAttack(t,false);t.clear("stance_basic_window");}
                else {combo(t,feather?"feather_":"",4);t.timer("stance_basic_window",60);}
                if (!listening&&t.has("thread_pearl")) {t.effect(Kind.NATIVE_PROC,"xuanling_shadow",3.3798,0);t.clear("thread_pearl");}
            }
            case HEAVY_START -> t.timer("charging", 1);
            case HEAVY_RELEASE, HEAVY_FULL -> {
                require(t.get("feathers") >= 2, "two_feathers_required"); t.payStamina(10);
                require(!listening,"listening_uses_basic_chain");require(!feather||!t.context.airborne(),"feather_heavy_requires_ground");
                t.action(feather ? "feather_heavy" : "heavy");
                if (feather) {temporary(t, "feather_descent");yangyangCritical(t,300);}
                else { t.set("feathers", 0); yangyangCritical(t, 1); }
            }
            case AIR -> {
                if ((feather || t.has("feather_descent")) && t.get("feathers") >= 2) {
                    t.payStamina(20); t.set("feathers", 0); t.action("feather_descent"); form(t, "listening", 240); t.timer("listening", 240);t.timer("yangyang_listening_cursor",1);
                    t.clear("feather_descent");
                } else { t.payStamina(30); t.action(feather ? "feather_air" : "air"); t.combo = 2; }
            }
            case DODGE_COUNTER -> {
                if(t.has("listening"))yangyangListeningAttack(t,true);
                else { t.action(feather ? "feather_dodge_counter" : "dodge_counter"); t.combo = 3; t.timer("stance_basic_window", 60); }
                // SkillEvadeRules pays50 only on a distinct server-confirmed
                // success during the specified skills. Ordinary counter hits
                // keep their own audited damage-row debit.
            }
            case SKILL -> {
                boolean flow = t.get("stream") <= 0;
                String next = feather || listening ? "azure" : "feather";
                if(listening)t.timer("yangyang_listening_cursor",Math.clamp(t.combo,1,3));
                boolean returnsToListening=next.equals("feather")&&t.has("listening");
                boolean castsListening=false;
                int keepCombo = t.combo;
                if (flow) {
                    t.action(next.equals("azure") ? "flow_azure" : "flow_feather"); t.set("stream", 100); t.add("feathers", 1);
                    if (t.has("shadow")) { t.action("shadow"); t.clear("shadow"); }
                } else if (!listening && t.has("stance_basic_window")) {
                    t.action(next.equals("azure") ? "skill" : "skill_feather");
                } else if(returnsToListening){
                    castsListening=true;
                } else {
                    t.action(next.equals("azure") ? "basic_01" : "feather_basic_01");
                    keepCombo = 2; t.timer("stance_basic_window", 60);
                }
                form(t,returnsToListening?"listening":next,0);
                t.combo=returnsToListening?Math.clamp(t.time("yangyang_listening_cursor"),1,3):keepCombo;
                if(castsListening){yangyangListeningAttack(t,false);t.clear("stance_basic_window");}
            }
            case LIBERATION -> {
                t.requireCooldown("liberation"); t.payEnergy(125); t.timer("liberation", 500); t.action("liberation");
                t.set("stream", 0); t.add("feathers", 1); t.timer("shadow", 600); t.effect(Kind.GAIN_CONCERTO, "liberation", 20, 0);
            }
            case INTRO -> { t.action("intro"); t.add("feathers", 1); t.effect(Kind.GAIN_CONCERTO, "intro", 10, 0); }
            case OUTRO -> { t.effect(Kind.BUFF_TEAM, "yangyang_outro", .20, 400, Map.of("afterApplyHavocBane", 1.0, "excludeOwner", 1.0)); OutroRules.damage(t,"havoc",3); }
            default -> throw new Rejected("unsupported_input_in_form");
        }
        if(t.original.chain()>=3&&(input==Input.INTRO||input==Input.SKILL&&t.effects.stream().anyMatch(e->e.kind()==Kind.ACTION&&List.of("yangyang_xuanling/flow_azure","yangyang_xuanling/flow_feather").contains(e.id()))))
            t.effect(Kind.MARK_AREA,"raise_cap_havoc_bane",3,400,Map.of("radius",6.0));
    }
    private static void yangyangListeningAttack(Transition t,boolean counter){
        // Azure's ordinary combo is independent of the retained Feather sequence.
        int stage=Math.clamp(t.form.equals("listening")?t.combo:t.time("yangyang_listening_cursor"),1,3);
        t.action((counter?"listening_counter_0":"listening_basic_0")+stage);
        t.timer("yangyang_listening_cursor",stage%3+1);
        if(t.form.equals("listening"))t.combo=stage%3+1;
        if(stage==3){
            t.clear("listening");t.clear("yangyang_listening_cursor");
            if(t.form.equals("listening"))form(t,"feather",0);
        }
    }
    private static void yangyangCritical(Transition t, int duration) {
        String cooldown=duration>=300?"feather_critical":"heavy_critical";
        if (!t.has(cooldown)) {
            t.timer(cooldown, 500); t.effect(Kind.BUFF_SELF, duration>=300?"yangyang_feather_critical_damage":"yangyang_azure_critical_damage", 1.60, duration);
        }
    }

    static void hsin(Transition t, Input input) {
        boolean light = t.form.equals("illuminated"), palaces = t.has("palaces");
        switch (input) {
            case BASIC -> {
                if(t.form.equals("fox")){form(t,"worldly",0);t.clear("fox_heal");t.clear("hsin_fox_window");t.clear("hsin_no_alarm");t.combo=4;}
                if(light&&t.context.airborne())t.payStamina(5);
                combo(t, light ? palaces ? "thousand_palaces_" : "illuminated_" : "", light && !palaces ? 3 : 4);
            }
            case HEAVY_START -> {
                if(t.form.equals("fox")){form(t,"worldly",0);t.clear("fox_heal");t.clear("hsin_fox_window");t.clear("hsin_no_alarm");}
                t.timer("charging", 1);
                if(!light&&t.context.airborne()&&t.get("worldly_heart")<100){t.payStamina(5);t.action("heavy_wander");t.timer("hsin_wander",35);}
            }
            case HEAVY_RELEASE, HEAVY_FULL -> {
                if(HsinTravelRules.release(t))break;
                t.payStamina(20);
                if (!light && t.get("worldly_heart") >= 100) {
                    t.set("worldly_heart", 0); boolean strong = !t.has("suppress_worldly");
                    t.action(strong ? "worldly_suppress" : "worldly_step"); if (strong) t.timer("suppress_worldly", 480);
                    if (!t.has("heart_state")) t.timer("transform_ready", 900);
                } else if (light && palaces && t.get("illuminated_heart") <= 0) {
                    boolean strong = !t.has("suppress_illuminated"); t.action(strong ? "illuminated_suppress" : "illuminated_descend");
                    if (strong) t.timer("suppress_illuminated", 480); t.clear("palaces"); t.timer("palaces_finale", 900);
                } else {t.action(light ? "illuminated_heavy" : "heavy");t.combo=light?3:2;t.timer("combo_window",80);}
            }
            case AIR -> { t.payStamina(30); t.action(light ? "illuminated_air" : "air");t.combo=light?3:2;t.timer("combo_window",80); }
            case DODGE_COUNTER -> {t.action(light ? palaces ? "thousand_palaces_counter" : "illuminated_dodge_counter" : "dodge_counter");t.combo=3;t.timer("combo_window",80);}
            case SKILL -> {
                if(t.form.equals("fox")){form(t,"worldly",0);t.clear("fox_heal");t.clear("hsin_fox_window");t.clear("hsin_no_alarm");}
                else if(light&&t.context.airborne()){t.payStamina(30);t.action("illuminated_air");t.combo=3;t.timer("combo_window",80);}
                else if (light && t.get("illuminated_heart") >= 300) {
                    require(!t.context.airborne(), "requires_ground"); t.requireCooldown("palaces_skill"); t.timer("palaces_skill", 240);
                    t.action("palaces_formation"); t.timer("palaces", 260); t.combo = 1;
                } else if (light) {
                    require(!t.context.airborne(), "requires_ground"); t.requireCooldown("light_skill"); t.timer("light_skill", 400); t.action("illuminated_skill");
                } else { t.requireCooldown("skill"); t.timer("skill", 240); t.action("skill"); t.combo = 4;t.timer("combo_window",80);t.timer("hsin_fox_window",80); }
            }
            case SKILL_HOLD -> {
                require(!light && !t.context.inCombat(), "fox_requires_worldly_out_of_combat"); form(t, "fox", 0); t.timer("fox_heal", 60);t.timer("hsin_no_alarm",1);
                t.effect(Kind.MOVE_SPECIAL, "hsin_fox_travel", 1, 0);
            }
            case LIBERATION -> {
                require(!t.form.equals("fox"), "fox_cannot_liberate");
                if (light && t.has("palaces_finale") && t.context.energy()>=125) {
                    t.payEnergy(125); t.action("liberation_palaces"); t.clear("palaces_finale"); t.clear("heart_state"); t.clear("palaces"); t.clear("hsin_barrier_charges"); t.timer("hsin_night_light",1); form(t, "worldly", 0);
                    t.effect(Kind.GAIN_CONCERTO, "liberation_palaces", 20, 0);
                } else if (t.has("heart_state") || light) {
                    t.requireCooldown("form_switch"); t.timer("form_switch", 30); form(t, light ? "worldly" : "illuminated", t.time("heart_state"));
                    // Borrowing an aspect is not a new transformation: no electro-use or C1 refill.
                    if (light) { t.action(t.context.airborne() ? "air" : "basic_02"); t.combo = 3; t.clear("hsin_barrier_charges"); }
                    else { t.action(t.context.airborne() ? "illuminated_air" : "illuminated_uppercut"); t.combo = 1; }
                } else {
                    require(t.has("transform_ready"), "worldly_forte_required"); t.requireCooldown("liberation"); t.timer("liberation", 500);
                    t.clear("transform_ready"); t.set("edicts", 21); t.timer("edicts", 900); t.timer("heart_state", 900);
                    if(t.original.chain()>=1)t.timer("hsin_barrier_charges",2);
                    form(t, "illuminated", 900); t.effect(Kind.GAIN_CONCERTO, "liberation_transform", 20, 0);
                    if(t.original.mode().equals("unison")&&t.has("hsin_transform_unison")){t.timer("unison",100);t.clear("hsin_transform_unison");}
                    if (t.original.mode().contains("electro")) {
                        t.timer("electro_basics", 5); t.effect(Kind.MARK_AREA, "electro_flare", 5, 0, Map.of("radius", 6.0));
                    }
                    if(t.original.mode().contains("electro"))t.timer("hsin_word_lock_bonus_ready",1);
                }
            }
            case INTRO -> {
                boolean unison=t.original.mode().equals("unison"),enhanced=unison&&t.has("hsin_intro_enhanced");
                t.action((light?"illuminated_intro":"intro")+(enhanced?"_full_unison":unison?"_unison":""));
                t.clear("hsin_intro_enhanced");if(unison&&!light)t.timer("hsin_transform_unison",1);
                if (light && t.original.mode().equals("unison")) { t.set("illuminated_heart", 300); t.timer("palaces", 260); }
                else if (!light) { t.add("worldly_heart", 60);t.combo=t.original.mode().equals("unison")?3:4; }
                t.effect(Kind.GAIN_CONCERTO, "intro", 10, 0);
            }
            case JUMP -> {
                if(t.form.equals("fox"))form(t,"worldly",0);
                else if(light&&!t.context.airborne()){t.action("illuminated_uppercut");t.effect(Kind.MOVE_SPECIAL,"hsin_uppercut",1,0);}
                else throw new Rejected("unsupported_input_in_form");
            }
            case OUTRO -> OutroRules.hsin(t);
            default -> throw new Rejected("unsupported_input_in_form");
        }
    }

    static void hiyuki(Transition t, Input input) {
        if(HiyukiReturnChargeRules.input(t,input))return;
        boolean future = t.form.equals("future");
        // The client's four-tick gesture must not replace Present E with a
        // Future-only blink. Present has one ordinary skill for either gesture.
        if(!future&&input==Input.SKILL_HOLD)input=Input.SKILL;
        switch (input) {
            case BASIC -> {
                if(t.has("hiyuki_basic3_window")){t.combo=3;t.clear("hiyuki_basic3_window");t.clear("iai_stance");}
                if (future && t.has("iai_stance") && t.get("cold") >= 100) {
                    t.clear("hiyuki_heavy_guard");
                    // Frost applications belong to the point spent by THIS cast.
                    // A missed previous draw must not empower an uncharged one.
                    t.clear("iai_apply");
                    t.add("cold", -100); t.action("iai"); temporary(t, "iai_stance"); t.timer("iai_guard", 8);
                    if (t.get("iai") > 0) { t.add("iai", -1); t.add("chill", 1); t.timer("iai_apply", 60); }
                } else {
                    int part = Math.clamp(t.combo, 1, future ? 5 : 3); combo(t, future ? "future_" : "", future ? 5 : 3);
                    if (!future && part == 3) { t.add("mind", 100); if (t.has("mind_skill")) { t.add("mind", 100); t.clear("mind_skill"); } }
                }
            }
            case HEAVY_START -> t.timer("charging", 1);
            case HEAVY_RELEASE, HEAVY_FULL -> {
                if (!future) {
                    t.clear("charging");
                    require(t.get("mind") >= 300, "mind_300_required"); t.payStamina(20); t.action("heavy"); t.timer("hiyuki_last_arrow_pending", 1);
                    t.effect(Kind.BUFF_SELF, "hiyuki_heavy_invulnerable", 1, 26,Map.of("preventsNativeHurt",1d));
                } else if (t.get("chill") >= 3) {
                    t.clear("charging");
                    t.payStamina(20); t.set("chill", 0); t.add("forge", 1); t.action("future_heavy_frost");
                } else if(t.context.airborne()) {
                    t.clear("charging");t.payStamina(30);t.action("future_air");t.timer("air_stage",1);
                } else { t.payStamina(20);hiyukiThrust(t); }
            }
            case AIR -> {
                if (future) {
                    int part = Math.clamp(t.time("air_stage"), 1, 3); t.payStamina(part == 3 ? 30 : 5);
                    t.action(part == 3 ? "future_air" : "future_air_0" + part); t.timer("air_stage", part % 3 + 1);
                } else { t.payStamina(30); t.action("air"); }
            }
            case DODGE_COUNTER -> { t.action(future ? "future_dodge_counter" : "dodge_counter"); t.combo = 3; }
            case SKILL -> {
                if (future) {
                    t.requireCooldown("future_skill"); t.timer("future_skill", 240); t.action(t.context.airborne() ? "skill_falling" : "skill_jade"); temporary(t, "iai_ready");
                    t.effect(Kind.PULL_AREA,"hiyuki_frost_pull",.35,0,Map.of("radius",6d,"centerOnOwner",1d));
                } else { t.requireCooldown("skill"); t.timer("skill", 400); t.action("skill"); t.timer("mind_skill", 1); t.combo = 3; t.timer("hiyuki_basic3_window",60); }
            }
            case SKILL_HOLD -> {
                require(future && t.get("cold") >= 100, "cold_100_required");require(t.has("iai_hold_window"),"outside_iai_skill_window");
                t.payStamina(20);temporary(t,"iai_stance");t.clear("iai_dodge_window");t.clear("iai_hold_window");
                t.clear("iai_guard");t.clear("hiyuki_heavy_guard");
                t.effect(Kind.MOVE_SPECIAL, "hiyuki_iai_blink", 1, 0);
            }
            case DODGE -> {
                require(future&&t.get("cold")>=100,"cold_100_required");require(t.has("iai_dodge_window"),"outside_iai_dodge_window");
                t.payStamina(20);temporary(t,"iai_stance");t.clear("iai_dodge_window");t.clear("iai_hold_window");
                t.clear("iai_guard");t.clear("hiyuki_heavy_guard");
                t.effect(Kind.MOVE_SPECIAL, "hiyuki_iai_blink", 1, 0);
            }
            case BLOCK_HIT -> {
                require(t.has("iai_guard"), "outside_iai_guard"); t.clear("iai_guard");
                if(t.has("hiyuki_heavy_guard"))t.effect(Kind.CONTROL_AREA,"hiyuki_heavy_stasis",1,20,Map.of("radius",6d));
                t.clear("hiyuki_heavy_guard");
                t.effect(Kind.BUFF_SELF, "hiyuki_iai_invulnerable", 1, 12); t.combo = 4;
            }
            case LIBERATION -> {
                if (future) {
                    int spent=t.get("forge")>=3?3:0;if(spent==3)t.set("forge",0);HiyukiReturnChargeRules.finish(t,spent);
                } else {
                    require(t.has("see_mind_ready"), "cold_cluster_heavy_required"); t.requireCooldown("see_mind"); t.timer("see_mind", 500); t.action("liberation");
                    t.set("mind", 0); t.set("cold", 50); t.set("iai", 3); t.clear("see_mind_ready"); form(t, "future", 0);
                    t.effect(Kind.GAIN_CONCERTO, "liberation", 20, 0);
                }
            }
            case INTRO -> { t.action("intro"); if (!future) {t.add("mind", 200);t.timer("hiyuki_basic3_window",60);} t.combo=future?2:3; t.effect(Kind.GAIN_CONCERTO, "intro", 10, 0); }
            case OUTRO -> t.effect(Kind.BUFF_TEAM,"hiyuki_outro",.20,400,Map.of("excludeOwner",1d));
            default -> throw new Rejected("unsupported_input_in_form");
        }
        for(var effect:t.effects)if(effect.kind()==Kind.ACTION)HiyukiIaiRules.acceptedAction(t,effect.id());
    }

    /** Both release and exhausted breathing enter the same source-defined thrust. */
    static void hiyukiThrust(Transition t) {
        t.clear("charging");t.action("future_heavy");t.combo=2;
        t.timer("iai_guard",8);t.timer("hiyuki_heavy_guard",8);
    }

    static void chisa(Transition t, Input input) {
        boolean saw = t.form.equals("saw");
        switch (input) {
            case BASIC -> {
                if (saw) {
                    if (t.get("saw_echo") <= 0 || t.has("saw_finale_ready")) chisaFinale(t);
                    else if(t.has("chisa_cycle_followup")){t.clear("chisa_cycle_followup");t.action("saw_basic_02");t.combo=3;t.timer("chisa_saw_stage",2);}
                    else { int part = Math.clamp(t.combo, 1, 3); combo(t, "saw_", 3); if(part>=2)t.timer("chisa_saw_stage",part); }
                } else if (t.context.airborne()&&t.has("chisa_glide_followup")) {chisaFallingFinale(t);}
                else if (t.has("pincers_followup")) { t.action("retract"); t.clear("pincers_followup"); }
                else if (t.has("chisa_falling_ready")) { chisaFallingFinale(t); }
                else if (t.has("pierce_followup")) { t.action("severing_pincers"); t.clear("pierce_followup"); t.clear("pincers_followup"); }
                else if (t.has("pierce_ready")) { t.action("pierce"); t.clear("pierce_ready"); temporary(t,t.context.airborne()?"chisa_falling_ready":"pierce_followup"); }
                else { int stage = Math.clamp(t.combo, 1, 2); combo(t, "", 2); if (stage == 2) temporary(t, "pierce_ready"); }
            }
            case HEAVY_START -> {
                if(saw){t.action(t.has("chisa_counter_hold")?"saw_counter_hold":"saw_basic_02_hold");t.clear("chisa_counter_hold");t.timer("chisa_saw_stage",2);t.combo=3;}
                else t.timer("charging",1);
            }
            case BASIC_RELEASE, BACK_DODGE -> {
                int stage=t.time("chisa_saw_stage");require(saw&&(stage==2||stage==3)&&!(input==Input.BACK_DODGE&&stage!=2),"no_saw_release_window");
                t.action(stage==2?"saw_resonance_02":"saw_resonance_03");t.clear("chisa_saw_stage");t.combo=3;t.timer("combo_window",120);
                if(stage==3)t.timer("saw_finale_ready",120);
            }
            case SAW_CONTINUE -> {
                require(saw&&t.time("chisa_saw_stage")>0,"no_saw_completion");
                if(t.get("saw_echo")<=0){t.clear("chisa_saw_stage");return;}
                if(t.time("chisa_saw_stage")==2){t.action("saw_basic_03_hold");t.timer("chisa_saw_stage",3);t.combo=1;}
                else chisaFinale(t);
            }
            case HEAVY_RELEASE, HEAVY_FULL -> {
                if (saw) { int part = Math.clamp(t.combo, 2, 3); t.action("saw_basic_0" + part + "_hold"); t.combo = part == 2 ? 3 : 1;t.timer("chisa_saw_stage",part); }
                else if(t.context.airborne()&&!t.has("chisa_shattered_ready")){t.payStamina(30);t.action("air");t.combo=2;}
                else { t.payStamina(t.context.airborne() ? 10 : 20); t.action(t.context.airborne() ? "shattered_plane" : "heavy");t.clear("chisa_shattered_ready"); temporary(t, "chisa_falling_ready");
                    if(!t.context.airborne())t.effect(Kind.MOVE_SPECIAL,"chisa_heavy_jump",1,0); }
            }
            case AIR -> { require(!saw, "saw_no_air");
                if(t.has("chisa_glide_followup")){chisaFallingFinale(t);}
                else {t.payStamina(30);t.action("air");t.combo=2;} }
            case DODGE_COUNTER -> { t.action(saw ? "saw_counter" : "dodge_counter"); if (saw){t.combo=3;t.timer("chisa_saw_stage",2);t.timer("chisa_counter_hold",40);}else t.timer("pierce_ready",Math.max(60,t.time("pierce_ready"))); }
            // These two events are emitted only by authenticated server timeline/dodge code.
            // They are deliberately absent from the client command allowlist.
            case REEL_COUNTER -> {require(!t.context.airborne(),"reel_requires_ground");t.action("reel_counter");t.effect(Kind.CONTROL_TARGET,"stasis",1,20);
                if(saw){t.combo=3;t.timer("combo_window",80);}else {t.clear("pierce_followup");t.clear("chisa_falling_ready");t.clear("pincers_followup");t.timer("pierce_ready",Integer.MAX_VALUE);}}
            case PINCERS_CLOSE -> {require(t.has("chisa_pincers_active"),"no_live_pincers");t.clear("chisa_pincers_active");temporary(t,"pincers_followup");healTeam(t,"chisa_pincers",1140,.504);}
            case SKILL, SKILL_HOLD -> {
                if (!saw && !t.context.airborne() && t.get("saw_echo") >= 100) {
                    t.action(input == Input.SKILL_HOLD ? "skill_saw_hold" : "skill_saw_cycle"); form(t, "saw", 0); t.combo = 1; t.timer("saw_heat", 100); t.set("saw_consumed", 0); t.clear("pierce_ready"); t.clear("chisa_falling_ready"); t.clear("pierce_followup"); t.clear("pincers_followup");
                    if(input==Input.SKILL_HOLD)t.timer("chisa_held_entry",1);else t.timer("chisa_cycle_followup",90);
                } else {
                    t.requireCooldown("skill"); t.timer("skill", 240); t.action("skill");
                    if(saw){if(!t.context.airborne())t.combo=2;}
                    else {t.clear("pierce_followup");t.clear("chisa_falling_ready");t.clear("pincers_followup");t.timer("pierce_ready",Integer.MAX_VALUE);}
                }
            }
            case SKILL_RELEASE -> {
                require(saw&&t.has("chisa_held_entry"),"no_held_saw_entry");t.clear("chisa_held_entry");t.clear("chisa_cycle_followup");
                t.action("saw_basic_01");t.combo=2;
            }
            case LIBERATION -> {
                t.requireCooldown("liberation"); t.payEnergy(125); t.timer("liberation", 500); t.action("liberation"); t.add("saw_echo", 40); t.timer("myriad", 300);
                t.effect(Kind.BUFF_SELF, "chisa_myriad_multiplier", 1.2, 300); healTeam(t, "chisa_liberation", 2660, 1.176);
                t.effect(Kind.GAIN_CONCERTO, "liberation", 20, 0); if (saw) t.timer("saw_heat", 100); else t.combo = 2;
            }
            case INTRO -> { t.action("intro"); t.combo=2; t.add("saw_echo", 20); t.effect(Kind.GAIN_CONCERTO, "intro", 10, 0); if (saw) t.timer("saw_heat", 100); }
            case OUTRO -> t.effect(Kind.BUFF_TEAM,"chisa_outro",3,400);
            default -> throw new Rejected("unsupported_input_in_form");
        }
    }
    private static void chisaFallingFinale(Transition t) {
        t.payStamina(10);
        t.action("falling_finale");
        t.clear("chisa_glide_followup");
        t.clear("chisa_falling_ready");
        t.clear("pincers_followup");
        if(!t.context.airborne())temporary(t,"pierce_followup");
    }
    private static void chisaFinale(Transition t) {
        // Base and consumed-resource coefficients receive the same additive C3/myriad modifier later.
        double bonus = .0259 * t.get("saw_consumed");
        // Casting Finale consumes Myriad now; only this accepted Finale keeps its bonus.
        t.effect(Kind.BUFF_SELF,"chisa_myriad_multiplier",0,0);
        if(t.has("myriad"))t.effect(Kind.BUFF_SELF,"chisa_myriad_finale_multiplier",1.2,1,Map.of("actionScoped",1d));
        t.effect(Kind.BUFF_SELF, "chisa_finale_additive_multiplier", bonus, 1,Map.of("actionScoped",1d)); t.action("saw_finale");
        t.effect(Kind.SHIELD_TEAM, "chisa_saw_finale", 3800 + 1.68 * t.context.attack(), 600);
        t.effect(Kind.GAIN_CONCERTO, "saw_finale", 45, 0); t.set("saw_echo", 0); t.set("saw_consumed", 0); t.set("lifeline", 100);
        t.clear("saw_finale_ready"); t.clear("chisa_saw_stage"); t.clear("myriad"); form(t, "normal", 0);
    }

    static void phrolova(Transition t, Input input) {
        boolean conducting = t.form.equals("conducting");
        switch (input) {
            case BASIC -> {
                if (conducting) {
                    int count = t.time("hecate_count");
                    if (count >= 2) {
                        int note = (int) t.resources.getOrDefault("note_" + t.time("note_position"), 0.0).doubleValue();
                        t.action(note == 2 ? "hecate_color" : note == 1 ? "hecate_winds" : "hecate_strings"); t.clear("hecate_count");
                    } else { t.action("hecate_basic_0" + (count + 1)); t.timer("hecate_count", count + 1); }
                } else if (t.has("reincarnation") && !t.context.airborne()) { t.action("movement_death"); t.clear("reincarnation"); }
                else { int stage = Math.clamp(t.combo, 1, 3); combo(t, "", 3); if (stage == 3) t.timer("reincarnation", 1); }
            }
            case HEAVY_START -> { require(!conducting,"conducting_no_heavy");t.timer("charging",1); }
            case HEAVY_RELEASE, HEAVY_FULL -> {
                require(!conducting, "conducting_no_heavy"); t.payStamina(25);t.clear("charging");
                if (t.get("notes") >= 6 && !t.has("composition") && !t.has("final_note")) {
                    t.action("composition_finale"); t.timer("composition", 500); t.timer("final_note", 1);
                    t.effect(Kind.CONTROL_AREA,"phrolova_composition_stasis",1,1,Map.of("radius",6d,"actionBound",1d));
                    t.effect(Kind.PULL_AREA,"phrolova_composition_pull",.35,1,Map.of("radius",6d,"centerOnOwner",1d));
                    t.effect(Kind.ECHO_CAST,"phrolova_composition",6000115,0);
                    if (t.original.chain() >= 2) t.add("echoes",14);
                    t.effect(Kind.BUFF_SELF, "phrolova_echoes_additive_multiplier", .8255 * t.get("echoes"), 1,
                            Map.of("additiveMultiplier", 1.0, "finalHitOnly", 1.0,"actionScoped",1d));
                    t.effect(Kind.GAIN_CONCERTO, "composition_finale", 40, 0);
                } else { t.action("heavy"); t.combo = 2; }
            }
            case AIR -> { require(!conducting, "conducting_no_air"); t.payStamina(30); t.action("air"); }
            case DODGE_COUNTER -> { require(!conducting,"hecate_no_counter");t.action("dodge_counter");t.combo = 3; }
            case SKILL -> {
                require(!conducting, "conducting_no_skill");
                if (t.has("reincarnation") && !t.context.airborne()) { t.action("undying_reverie"); t.clear("reincarnation"); }
                else { t.requireCooldown("skill"); t.timer("skill", 240); t.action("skill"); t.timer("reincarnation", 1); t.effect(Kind.GAIN_CONCERTO, "skill", 10, 0); }
            }
            case LIBERATION, LIBERATION_HOLD -> {
                if (conducting) phrolovaCurtain(t);
                else {
                    if(input==Input.LIBERATION_HOLD){require(t.has("final_note"),"composition_finale_required");phrolovaCurtain(t);return;}
                    require(t.has("final_note"), "composition_finale_required"); t.clear("final_note"); t.timer("conducting", 480); t.timer("note_pulse", 80); t.clear("note_position"); t.clear("hecate_count");
                    t.clear("hecate_echo_uses");t.clear("hecate_background_pulse");t.clear("hecate_background_segment");
                    t.timers.keySet().removeIf(key -> key.startsWith("echo_heard_"));
                    form(t, "conducting", 480); t.effect(Kind.BUFF_SELF, "phrolova_conducting_attack", 1.2, 480);
                    t.effect(Kind.SPAWN_FIELD, "hecate", 1, 480); t.effect(Kind.GAIN_CONCERTO, "liberation", 20, 0);
                    if(t.original.chain()>=5)t.effect(Kind.SPAWN_FIELD,"phrolova_conducting_stasis",1,80,Map.of("radius",6d));
                }
            }
            case INTRO -> {
                boolean eternal=t.has("eternal_intro");t.action(eternal?"intro_eternal":"intro");t.combo=3;
                if(eternal)t.effect(Kind.CONTROL_TARGET,"stasis",1,40); // MC two-second target stop, applied only on confirmed intro contact.
                t.timer("next_color_note",1);t.effect(Kind.GAIN_CONCERTO,"intro",10,0);
            }
            case JUMP -> { require(conducting, "not_conducting"); t.effect(Kind.MOVE_SPECIAL, "hecate_reset", 1, 0); }
            case OUTRO -> {
                t.effect(Kind.BUFF_NEXT,"phrolova_outro",.20,280,Map.of("heavyAmplification",.25,"endsOnSwitch",1.0));
                if(conducting){t.timer("hecate_outro_enabled",1);t.timer("hecate_outro_pending",2);}
            }
            default -> throw new Rejected("unsupported_input_in_form");
        }
    }
    private static void phrolovaCurtain(Transition t) {
        endConducting(t,true);
    }
    static void endConducting(Transition t,boolean attack) {
        if(attack)t.action("liberation_curtain");
        t.clear("phrolova_return_curtain");
        t.clear("conducting"); t.clear("final_note"); t.set("notes", 0); form(t, "normal", 0);
        t.clear("note_pulse");t.clear("note_position");t.clear("hecate_count");
        for(String key:List.of("hecate_echo_uses","hecate_background_pulse","hecate_background_segment","hecate_outro_pending","hecate_outro_enabled","eternal_intro"))t.clear(key);
        t.timers.keySet().removeIf(key -> key.startsWith("echo_heard_"));
        for (int i = 0; i < 6; i++) t.resources.remove("note_" + i);
        t.effect(Kind.BUFF_SELF, "phrolova_conducting_attack", 0, 0); t.effect(Kind.REMOVE_FIELD, "hecate", 0, 0);
        t.effect(Kind.REMOVE_FIELD,"phrolova_conducting_stasis",0,0);
    }
    static void hecateEnhanced(Transition t) {
        int note=(int)t.resources.getOrDefault("note_" + t.time("note_position"),0.0).doubleValue();
        t.action(note==2?"hecate_color":note==1?"hecate_winds":"hecate_strings");
        if(!t.context.active())t.add("echoes",1);
    }
    private static void addNote(Transition t, int type) {
        if (t.has("final_note") || t.form.equals("conducting")) return;
        int count = (int) t.get("notes");
        if (count == 6) {
            // Frozen text removes the oldest string/wind, never a color note.
            // MC all-color policy: refuse the new note; preserve the next-color qualification.
            int removable = -1;
            for (int i = 0; i < 6; i++) if (t.resources.getOrDefault("note_" + i, 0.0) != 2) { removable = i; break; }
            if (removable < 0) return;
            for (int i = removable; i < 5; i++) t.resources.put("note_" + i, t.resources.getOrDefault("note_" + (i + 1), 0.0));
            count = 5;
        }
        if (t.has("next_color_note")) { type = 2; t.clear("next_color_note"); }
        t.resources.put("note_" + count, (double) type); t.set("notes", count + 1);
    }

    static void hit(Transition t, String action) {
        switch (t.original.characterId()) {
            case "yangyang_xuanling" -> {
                if (action.startsWith("flow_")) mark(t, "consume_havoc_bane", 1, 0);
                if (action.equals("heavy")||action.equals("feather_heavy")) mark(t, "havoc_bane", 2, 0);
                if(t.original.chain()>=3&&List.of("basic_04","feather_basic_04","heavy","feather_heavy").contains(action))mark(t,"havoc_bane",1,0);
                // Itecho uses a pre-hit snapshot and delayed range copy in TargetReactionRuntime, never a post-hit generic mark.
            }
            case "hsin" -> {
                if (t.has("electro_basics") && (action.startsWith("thousand_palaces_basic_") || action.equals("thousand_palaces_counter"))) {
                    mark(t, "electro_flare", 1, 0); t.timer("electro_basics", t.time("electro_basics") - 1);
                }
            }
            case "hiyuki" -> {
                int layers = action.equals("liberation") ? 4 : action.equals("iai") && t.has("iai_apply") ? 3 :
                    List.of("basic_03", "heavy", "future_basic_03", "future_basic_04", "future_basic_05", "future_heavy_frost", "future_air_02", "future_air").contains(action) ? 1 : 0;
                if (layers > 0) mark(t, "glacio_chafe", layers, 0);
                if (action.equals("iai")) t.clear("iai_apply");
                if (action.equals("skill_jade") || action.equals("skill_falling")) mark(t, "remove_frostbind", 1, 0);
                if (action.equals("iai") || action.equals("liberation")) mark(t, "convert_frost_10_to_frostbind", 10, 0);
            }
            case "chisa" -> {
                if (action.startsWith("skill") || action.equals("reel_counter")) mark(t, "chisa_void_scar", 1, 600);
                if(action.startsWith("skill"))t.effect(Kind.CONTROL_TARGET,"chisa_skill_stasis",1,20);
                if(action.equals("skill_saw_cycle"))t.effect(Kind.PULL_AREA,"chisa_cycle_pull",.25,1,Map.of("radius",6d));
                if(action.equals("skill_saw_hold"))t.effect(Kind.PULL_AREA,"chisa_held_pull",.25,26,Map.of("radius",6d));
                if (t.form.equals("saw")) t.timer("saw_heat", 100);
            }
            case "phrolova" -> {
                if(List.of("movement_death","undying_reverie","liberation_curtain").contains(action))t.effect(Kind.CONTROL_TARGET,"phrolova_stasis",1,20);
                if (List.of("basic_03", "movement_death").contains(action)) addNote(t, 0);
                if (List.of("skill", "undying_reverie").contains(action)) addNote(t, 1);
                if(action.equals("hecate_strings")||action.equals("hecate_color"))t.effect(Kind.CONTROL_TARGET,"hecate_stasis",1,20);
                if(action.equals("hecate_winds")||action.equals("hecate_color"))t.effect(Kind.PULL_AREA,"hecate_pull",.35,0,Map.of("radius",4.0));
                if(action.equals("hecate_color")&&t.original.chain()>=3)mark(t,"phrolova_color_weakness",1,300);
                if(action.equals("c6_illusion")&&t.original.chain()>=6)t.add("echoes",8);
            }
            default -> { }
        }
    }

    static void tick(Transition t) {
        switch (t.original.characterId()) {
            case "yangyang_xuanling" -> {
                if(!t.has("listening")){
                    t.clear("yangyang_listening_cursor");
                    if(t.form.equals("listening"))form(t,"feather",0);
                }
                if (!t.has("oath")) t.set("oath", 0);
            }
            case "hsin" -> {
                HsinTravelRules.tick(t);
                if(t.context.active()&&t.form.equals("illuminated")&&t.context.airborne())
                    t.effect(Kind.STAMINA_REGEN_LOCK,"hsin_illuminated_air",1,1);
                // C2 entry means the existing owning-player combat flag's rising
                // edge, including equipped background roles (MC event adaptation).
                // Reset the24s enhanced-heavy acquisition ICDs, never ordinary E CDs.
                if (t.context.inCombat()) {
                    if(t.original.mode().equals("electro")&&!t.has("was_in_combat")&&t.original.chain()>=1&&!t.has("hsin_c1_realm_cooldown")){
                        t.set("thunder_realm",Math.max(50,t.get("thunder_realm")));t.timer("hsin_c1_realm_cooldown",240);
                    }
                    if (!t.has("was_in_combat") && t.original.chain() >= 2 && !t.has("hsin_c2_enter_cooldown")) {
                        t.add("worldly_heart", 100); t.clear("suppress_worldly"); t.clear("suppress_illuminated");
                        t.timer("hsin_c2_enter_cooldown", 240);
                    }
                    t.timer("was_in_combat", 1);
                } else t.clear("was_in_combat");
                // Heart-state expiry removes its protection, not the independent illuminated aspect.
                // Forte: leaving the13-second command state clears its remaining heart,
                // even when it expires without landing enough attacks to spend it.
                if(t.original.timer("palaces")>0&&!t.has("palaces"))t.set("illuminated_heart",0);
                if (!t.has("edicts")) t.set("edicts", 0);
                if (t.has("edict_trigger") && t.get("edicts") > 0 && !t.has("edict_pulse")) {
                    t.add("edicts", -1); t.action("flying_palace"); t.timer("edict_pulse", 20);
                }
                if (t.form.equals("fox") && !t.context.inCombat()&&!t.has("fox_heal")) {
                    t.effect(Kind.HEAL_ACTIVE, "hsin_fox", t.context.maxHealthNative() * .06, 0); t.timer("fox_heal", 60);
                }
            }
            case "chisa" -> {
                ChisaTraversalRules.tick(t);
                GameplayResourceRules.tick(t);
            }
            case "hiyuki" -> HiyukiReturnChargeRules.tick(t);
            case "phrolova" -> {
                if (t.form.equals("conducting")) {
                    if (!t.has("conducting")) {
                        if(t.context.active())phrolovaCurtain(t);
                        else {
                            // Source: after an off-field ending her next entry is
                            // a non-intro Curtain, not a remote body animation now.
                            endConducting(t,false);t.timer("phrolova_return_curtain",1);
                        }
                    }
                    else {
                        if (!t.has("note_pulse")) { t.timer("note_position", Math.min(5, t.time("note_position") + 1)); t.timer("note_pulse", 80); }
                        if(!t.context.active() && t.has("hecate_outro_pending")) {
                            // Two actual casts, not a fabricated doubled coefficient or two echoes on a miss.
                            hecateEnhanced(t);hecateEnhanced(t);t.clear("hecate_outro_pending");
                        } else if(!t.context.active() && t.context.inCombat() && !t.has("hecate_background_pulse")) {
                            int segment=t.time("hecate_background_segment")==1?2:1;
                            t.action("hecate_basic_0"+segment);t.timer("hecate_background_segment",segment==1?1:0);
                            t.timer("hecate_background_pulse",40); // Explicit two-second MC automatic cadence.
                        }
                    }
                }
                if (t.context.inCombat()) {
                    if (!t.has("was_in_combat") && !t.has("combat_entry_lock")) t.add("echoes", 10);
                    t.timer("was_in_combat", 1); t.clear("out_of_combat_ticks");
                } else {
                    if (t.has("was_in_combat")) { t.clear("was_in_combat"); t.timer("combat_entry_lock", 80); }
                    t.timer("out_of_combat_ticks", t.time("out_of_combat_ticks") + 1);
                    if (t.time("out_of_combat_ticks") >= 600) { t.set("echoes", 0); t.clear("out_of_combat_ticks"); }
                }
            }
            default -> { }
        }
    }
    static void switchOut(Transition t) {
        switch (t.original.characterId()) {
            case "chisa" -> {t.clear("chisa_falling_ready");t.clear("pierce_followup");t.clear("pincers_followup");}
            case "phrolova" -> { t.clear("phrolova_color_conversion");if(t.form.equals("conducting")&&t.has("hecate_outro_enabled"))t.timer("hecate_outro_pending",2); }
            case "hiyuki" -> { t.clear("mind_skill"); t.clear("iai_stance");t.clear("iai_dodge_window");t.clear("iai_hold_window");t.clear("hiyuki_last_arrow_pending");HiyukiReturnChargeRules.clear(t); }
            case "hsin" -> {t.clear("hsin_fox_window");t.clear("hsin_wander");t.clear("fox_heal");t.clear("hsin_no_alarm");if (t.form.equals("fox")) form(t, "worldly", 0); }
            default -> { }
        }
    }
}
