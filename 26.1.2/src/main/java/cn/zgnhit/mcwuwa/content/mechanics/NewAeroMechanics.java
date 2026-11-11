package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.Map;
import java.util.Set;
import static cn.zgnhit.mcwuwa.content.mechanics.MechanicsEngine.*;
import static cn.zgnhit.mcwuwa.content.mechanics.ExtendedMechanics.*;

/** Server-owned Qiuyuan/Ciaccona resources. Presentation never grants these resources. */
public final class NewAeroMechanics {
    static final Set<String> COUNTERS = Set.of("qiuyuan_ink_stage", "ciaccona_shadow_count", "ciaccona_shadow_slot", "ciaccona_last_color", "ciaccona_performance_reserve", "ciaccona_instrument");
    // Rhythm interval/window are explicit MC controls.
    // User-approved MC adaptation: complete original 40.305354s recital stem.
    public static final int PERFORMANCE_TICKS=807;
    static final int BEAT_TICKS=20, JUDGEMENT_TICKS=5;
    private NewAeroMechanics() { }

    static void qiuyuan(Transition t, Input input) {
        switch (input) {
            case BASIC, BASIC_HOLD -> {
                // 1411/forte: after the opening held strike, a tap may continue
                // the second/third ink strike. A fresh drunken stance still
                // needs the opening hold; do not replace all normal taps.
                if (t.form.equals("drunken")&&(input==Input.BASIC_HOLD
                    ||t.time("qiuyuan_ink_stage")>1)) { inkHeavy(t); break; }
                boolean answer = t.get("ink") >= 200 || t.has("qiuyuan_answer");
                t.clear("qiuyuan_answer"); // Heavy/intro grants one continuation, not an under-threshold answer combo.
                int stage = Math.clamp(t.combo, 1, answer ? 4 : 3);
                combo(t, answer ? "answer_" : "", answer ? 4 : 3);
                if (answer || stage == 3) ink(t, 100);
                t.timer("qiuyuan_counter_window", 10);
            }
            case HEAVY_START -> t.timer("charging", 1);
            case HEAVY_FULL, HEAVY_RELEASE -> {
                if (t.form.equals("drunken")) {t.timer("qiuyuan_ink_stage",2);inkHeavy(t);}
                else { t.payStamina(20); t.action("heavy"); t.timer("qiuyuan_answer",60); t.combo=4; t.timer("qiuyuan_counter_window",10); }
            }
            case AIR -> { t.payStamina(30); t.action("air"); }
            case DODGE_COUNTER, BLOCK_HIT -> {
                if(input==Input.BLOCK_HIT)require(t.has("qiuyuan_counter_window"),"counter_window_closed");
                t.clear("qiuyuan_counter_window"); t.action("dodge_counter"); ink(t,100);
            }
            case SKILL, SKILL_HOLD -> {
                if (t.original.chain()>=3 && t.context.concerto()>=100 && !t.form.equals("drunken") && !t.has("qiuyuan_canopy")) {
                    t.effect(Kind.PAY_CONCERTO,"qiuyuan_canopy",60,0);t.action("skill_canopy");
                    t.timer("qiuyuan_canopy",400); t.clear("qiuyuan_composure");t.timer("qiuyuan_canopy_outro",Integer.MAX_VALUE);
                    if(t.original.chain()>=6)t.effect(Kind.BUFF_SELF,"qiuyuan_canopy_crit",1,120,Map.of("endsOnSwitch",1.0));
                    t.timer("qiuyuan_next_drunk_enhanced",Integer.MAX_VALUE);ink(t,400);t.combo=3;t.timer("qiuyuan_answer",60);
                } else {
                    t.requireCooldown("skill");t.timer("skill",280);
                    t.action(input==Input.SKILL_HOLD?"skill_dash":"skill");
                    t.effect(Kind.GAIN_CONCERTO,"qiuyuan_skill",10,0);
                    if(input==Input.SKILL)t.timer("qiuyuan_skill_counter",10);
                }
            }
            case SKILL_RELEASE -> t.clear("qiuyuan_dash");
            case SKILL_RUN -> {
                t.requireCooldown("skill");t.payStamina(.5);t.timer("skill",280);t.timer("qiuyuan_dash",60);
                t.effect(Kind.GAIN_CONCERTO,"qiuyuan_skill",10,0);t.effect(Kind.MOVE_SPECIAL,"qiuyuan_run",1,60);
            }
            case LIBERATION -> {
                t.requireCooldown("liberation");t.payEnergy(125);t.timer("liberation",500);
                t.action(t.original.chain()>=3?"liberation_c3":"liberation");
                t.effect(Kind.GAIN_CONCERTO,"qiuyuan_liberation",20,0);
                // Host resolves the current crit-rate snapshot when applying this source-defined buff.
                t.effect(Kind.BUFF_TEAM,"qiuyuan_liberation_crit",.30,600,Map.of("critRateConversion",2.0,"critRateThreshold",.50,"activeOnly",1.0));
            }
            case INTRO -> { t.action("intro");ink(t,400);t.combo=3;t.timer("qiuyuan_answer",60);t.effect(Kind.GAIN_CONCERTO,"intro",10,0); }
            case OUTRO -> {
                t.effect(Kind.OUTRO_HIT,"qiuyuan",t.has("qiuyuan_canopy_outro")?5:1,0,Map.of("multiplayerAmount",1.0));t.clear("qiuyuan_canopy_outro");
                t.effect(Kind.BUFF_NEXT,"qiuyuan_outro",.50,280,Map.of("echoAmplification",.50,"endsOnSwitch",1.0));
            }
            case BASIC_RELEASE -> { }
            default -> throw new Rejected("unsupported_input_in_form");
        }
    }

    private static void ink(Transition t,int amount) {
        if(t.form.equals("drunken"))return;
        double before=t.get("ink");t.add("ink",amount);
        if(t.get("ink")>before&&t.has("qiuyuan_wine")) {
            t.clear("qiuyuan_wine");t.effect(Kind.BUFF_SELF,"qiuyuan_wine_attack",.10,400);
        }
        if(before<400&&t.get("ink")>=400) {
            t.effect(Kind.BUFF_TEAM,"qiuyuan_bamboo",.30,600,Map.of("activeOnly",1.0));
            if(t.original.chain()>=2)t.effect(Kind.BUFF_TEAM,"qiuyuan_bamboo_amplification",.30,600);
        }
        if(t.get("ink")>=600) {
            form(t,"drunken",160);t.timer("qiuyuan_drunk",160);t.timer("qiuyuan_ink_stage",1);
            if(t.has("qiuyuan_next_drunk_enhanced")) {
                t.clear("qiuyuan_next_drunk_enhanced");t.timer("qiuyuan_drunk_enhanced",160);
            } else if(!t.has("qiuyuan_composure_cooldown")) {
                t.timer("qiuyuan_composure",200);t.timer("qiuyuan_composure_cooldown",440);
            }
        }
    }

    private static void inkHeavy(Transition t) {
        require(t.get("ink")>0&&t.has("qiuyuan_drunk"),"drunken_ink_missing");
        int stage=Math.clamp(t.time("qiuyuan_ink_stage"),1,3);
        String action="ink_heavy_0"+stage;
        if(t.has("qiuyuan_drunk_enhanced"))action+="_c3";
        t.action(action);t.effect(Kind.ECHO_CAST,"qiuyuan_ink_0"+stage,141110+stage,0);
        t.add("ink",-200);t.timer("qiuyuan_ink_stage",stage%3+1);
        if(stage==3&&(t.has("qiuyuan_composure")||t.has("qiuyuan_drunk_enhanced")))t.timer("qiuyuan_final_reward",60);
        if(stage==3&&t.original.chain()>=6)t.effect(Kind.CONTROL_AREA,"qiuyuan_ink_stasis",1,100,Map.of("singleplayerOnly",1.0));
        if(t.get("ink")==0)exitDrunk(t);
    }

    private static void exitDrunk(Transition t) {
        if(t.form.equals("drunken")&&t.original.chain()>=6&&t.context.active())t.action("drunken_exit");
        t.set("ink",0);t.clear("qiuyuan_drunk");t.clear("qiuyuan_drunk_enhanced");t.clear("qiuyuan_ink_stage");form(t,"normal",0);
    }

    static void ciaccona(Transition t,Input input) {
        if(t.form.equals("performing")&&input!=Input.LIBERATION&&input!=Input.NOTE_GREEN&&input!=Input.NOTE_YELLOW&&input!=Input.INTRO&&input!=Input.OUTRO&&input!=Input.AIM_RELEASE)
            throw new Rejected("performance_active");
        if(t.has("ciaccona_solo")&&input!=Input.BASIC_RELEASE&&input!=Input.AIM_RELEASE) {shadow(t);stopSolo(t);}
        switch(input) {
            case BASIC -> {
                if(t.has("ciaccona_aim")){t.action(t.has("ciaccona_aim_charge")?"aim":"aim_charged");t.timer("ciaccona_aim_charge",20);break;}
                int stage=Math.clamp(t.combo,1,4);combo(t,"",4);
                t.clear("ciaccona_followup");
                if(stage==1)t.timer("ciaccona_instrument",1);
                if(stage==4){t.add("melody",t.original.chain()>=3?2:1);t.timer("ciaccona_solo_pending",Integer.MAX_VALUE);}
            }
            case BASIC_HOLD -> {t.payStamina(25);quartet(t);}
            case HEAVY_START -> t.timer("charging",1);
            case HEAVY_RELEASE,HEAVY_FULL -> {t.payStamina(25);quartet(t);}
            case AIR -> {
                t.payStamina(15);boolean second=t.has("ciaccona_air_second");
                t.action(second?"air_02":"air_01");
                if(second){t.clear("ciaccona_air_second");t.combo=4;t.timer("ciaccona_followup",60);t.timer("ciaccona_instrument",2);}else t.timer("ciaccona_air_second",60);
            }
            case DODGE_COUNTER -> {t.action("dodge_counter");t.combo=3;t.timer("ciaccona_followup",60);t.timer("ciaccona_instrument",3);}
            case SKILL -> {
                if(t.has("skill")) {
                    require(t.original.chain()>=3,"cooldown:skill");t.requireCooldown("ciaccona_skill_second");t.timer("ciaccona_skill_second",200);
                } else t.timer("skill",200);
                t.action("skill");t.combo=2;t.timer("ciaccona_followup",60);t.timer("ciaccona_instrument",3);
                if(t.original.chain()>=1)t.timer("ciaccona_skill_armor",60);
                t.effect(Kind.GAIN_CONCERTO,"ciaccona_skill",15,0);
            }
            case LIBERATION -> {
                if(t.has("ciaccona_performance")){endPerformance(t);break;}
                t.requireCooldown("liberation");t.payEnergy(125);t.timer("liberation",400);t.action("liberation");
                t.clear("ciaccona_aim");t.clear("ciaccona_aim_charge");
                t.effect(Kind.GAIN_CONCERTO,"ciaccona_liberation",20,0);
                t.effect(Kind.SHIELD_TEAM,"ciaccona_self_shield",t.context.maxHealthNative(),80,Map.of("selfOnly",1.0,"endsOnSwitch",1.0));
                form(t,"performing",PERFORMANCE_TICKS);t.timer("ciaccona_performance",PERFORMANCE_TICKS);
                t.timer("ciaccona_next_beat",BEAT_TICKS);t.timer("ciaccona_last_color",1);
                t.effect(Kind.SPAWN_FIELD,"ciaccona_performance",1,PERFORMANCE_TICKS,Map.of("radius",12.0));
                t.effect(Kind.SPAWN_FIELD,"ciaccona_performer_guitar",1,PERFORMANCE_TICKS,Map.of("radius",12.0));
                t.effect(Kind.SPAWN_FIELD,"ciaccona_performer_drum",1,PERFORMANCE_TICKS,Map.of("radius",12.0));
            }
            case NOTE_GREEN,NOTE_YELLOW -> {
                require(t.has("ciaccona_performance"),"not_performing");
                require(t.has("ciaccona_judgement"),"outside_rhythm_window");
                t.timer("ciaccona_last_color",input==Input.NOTE_GREEN?1:2);
                note(t);t.clear("ciaccona_judgement");
            }
            case INTRO -> {t.action("intro");t.add("melody",1);t.combo=3;t.timer("ciaccona_followup",60);t.timer("ciaccona_instrument",1);t.effect(Kind.GAIN_CONCERTO,"intro",10,0);}
            case AIM_PRESS -> {t.timer("ciaccona_aim",Integer.MAX_VALUE);t.timer("ciaccona_aim_charge",20);}
            case AIM_RELEASE -> {t.clear("ciaccona_aim");t.clear("ciaccona_aim_charge");}
            case OUTRO -> t.effect(Kind.BUFF_TEAM,"ciaccona_outro_erosion",1,600);
            case DODGE -> { }
            case BASIC_RELEASE -> { }
            default -> throw new Rejected("unsupported_input_in_form");
        }
    }

    private static void quartet(Transition t) {
        if(t.get("melody")>=3){require(!t.context.airborne()||t.has("ciaccona_low_air"),"low_air_only");t.set("melody",0);t.action("heavy_quartet");t.effect(Kind.GAIN_CONCERTO,"ciaccona_quartet",25,0);t.effect(Kind.PULL_AREA,"ciaccona_quartet",.2,40,Map.of("radius",6.0));}
        else t.action("heavy");
    }
    private static void solo(Transition t) {
        t.clear("ciaccona_solo_pending");
        t.timer("ciaccona_solo",Integer.MAX_VALUE);
        t.effect(Kind.SPAWN_FIELD,"ciaccona_body_solo",1,0,Map.of("instrument",(double)Math.max(1,t.time("ciaccona_instrument"))));
        if(t.original.chain()>=6)t.effect(Kind.NATIVE_PROC,"ciaccona_solo_area",2.2,0,Map.of("radius",12.0));
    }
    private static void shadow(Transition t) {
        shadow(t,true,0);
    }
    static void interrupted(Transition t,String action,int remaining,boolean skillCancel) {
        boolean solo=t.has("ciaccona_solo")||action.equals("ciaccona/basic_04");
        if(solo||skillCancel&&(action.startsWith("ciaccona/basic_")||action.startsWith("ciaccona/air_")||action.contains("/heavy")))shadow(t,solo,remaining);
        stopSolo(t);
        t.clear("ciaccona_solo_pending");
    }
    static void completed(Transition t,String action) {
        if(action.equals("ciaccona/basic_04")&&t.has("ciaccona_solo_pending"))solo(t);
    }
    private static void stopSolo(Transition t) {
        t.clear("ciaccona_solo");t.effect(Kind.REMOVE_FIELD,"ciaccona_body_solo",0,0);
    }
    private static void shadow(Transition t,boolean solo,int remaining) {
        int count=Math.min(2,t.time("ciaccona_shadow_count")+1);t.timer("ciaccona_shadow_count",count);
        int slot=t.time("ciaccona_shadow_slot")==2?1:0;t.timer("ciaccona_shadow_slot",slot==0?2:1);
        t.effect(Kind.SPAWN_FIELD,"ciaccona_shadow_"+slot,1,solo?0:Math.max(1,remaining),Map.of("radius",12.0,"solo",solo?1.0:0.0,
            "soloDelay",(double)Math.max(0,remaining),"soloC6",solo&&t.original.chain()>=6&&remaining>0?1.0:0.0,
            "instrument",(double)Math.max(1,t.time("ciaccona_instrument"))));
        if(solo&&t.original.chain()>=6&&remaining==0)t.effect(Kind.NATIVE_PROC,"ciaccona_solo_area",2.2,0,Map.of("radius",12.0));
    }
    private static void endPerformance(Transition t) {
        t.clear("ciaccona_performance");t.clear("ciaccona_judgement");t.clear("ciaccona_next_beat");t.clear("ciaccona_performance_reserve");form(t,"normal",0);
        t.effect(Kind.REMOVE_FIELD,"ciaccona_performance",0,0);
    }
    private static void note(Transition t) {
        t.action("liberation_note");t.effect(Kind.GAIN_CONCERTO,"ciaccona_note",10,0);
        t.effect(Kind.MARK_TARGET,t.time("ciaccona_last_color")==2?"spectro_frazzle":"aero_erosion",1,0);
    }
    static void hit(Transition t,String action) {
        if(t.original.characterId().equals("qiuyuan")&&action.startsWith("ink_heavy_03")&&t.has("qiuyuan_final_reward")) {
            t.effect(Kind.GAIN_CONCERTO,"qiuyuan_ink_final",30,0);t.clear("qiuyuan_final_reward");
        }
        if(t.original.characterId().equals("ciaccona")&&Set.of("basic_04","skill","intro","heavy_quartet").contains(action))mark(t,"aero_erosion",1,0);
    }
    static void tick(Transition t) {
        if(t.original.characterId().equals("ciaccona")&&!t.context.airborne())t.clear("ciaccona_air_second");
        if(t.original.characterId().equals("qiuyuan")&&t.form.equals("drunken")&&!t.has("qiuyuan_drunk"))exitDrunk(t);
        if(t.original.characterId().equals("qiuyuan")) {
            if(t.has("qiuyuan_dash")) {
                if(t.context.stamina()<.5){t.clear("qiuyuan_dash");t.timer("qiuyuan_dash_ended",2);}
                else t.effect(Kind.PAY_STAMINA,"qiuyuan_run",.5,0);
            } else if(t.original.timer("qiuyuan_dash")==1)t.timer("qiuyuan_dash_ended",2);
        }
        if(t.original.characterId().equals("ciaccona")&&t.form.equals("performing")) {
            if(!t.has("ciaccona_performance")||t.context.active()&&t.has("ciaccona_performance_reserve")){endPerformance(t);return;}
            if(!t.has("ciaccona_next_beat")) {
                t.timer("ciaccona_next_beat",BEAT_TICKS);
                if(!t.context.active())note(t);else t.timer("ciaccona_judgement",JUDGEMENT_TICKS);
            }
        }
    }
    static void switchOut(Transition t) {
        if(t.original.characterId().equals("qiuyuan")) {
            t.clear("qiuyuan_dash");t.clear("qiuyuan_composure");t.clear("qiuyuan_counter_window");t.clear("qiuyuan_skill_counter");t.clear("qiuyuan_skill_armor");
        } else if(t.original.characterId().equals("ciaccona")) {
            t.clear("ciaccona_air_second");
            t.clear("ciaccona_followup"); // Departure resets the ordinary basic cursor too.
            t.clear("ciaccona_aim");t.clear("ciaccona_aim_charge");
            if(t.has("ciaccona_solo")){shadow(t);stopSolo(t);}
            if(t.has("ciaccona_performance"))t.timer("ciaccona_performance_reserve",1);
        }
    }
    static MechanicsState leaveWorld(MechanicsState state){
        var cancelled=InterruptionRules.cancelled(state);
        var timers=new java.util.HashMap<>(cancelled.timers());
        // Performers and solo shadows are world-owned, not portable summons.
        for(String key:Set.of("ciaccona_performance","ciaccona_judgement","ciaccona_next_beat",
            "ciaccona_performance_reserve","ciaccona_solo","ciaccona_solo_pending",
            "ciaccona_shadow_count","ciaccona_shadow_slot","ciaccona_air_second",
            "ciaccona_aim","ciaccona_aim_charge"))timers.remove(key);
        return new MechanicsState(state.characterId(),"normal",state.mode(),state.chain(),1,state.resources(),timers);
    }
}
