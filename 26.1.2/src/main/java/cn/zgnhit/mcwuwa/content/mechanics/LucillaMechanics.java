package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.Map;
import java.util.Set;
import cn.zgnhit.mcwuwa.core.LucillaFocusWindow;
import static cn.zgnhit.mcwuwa.content.mechanics.MechanicsEngine.*;
import static cn.zgnhit.mcwuwa.content.mechanics.ExtendedMechanics.*;

/** Server transition integration for1109. Focus timings and the Forget pulse
 * interval are MC choreography, not claimed original frame measurements.
 * External photo/film ownership is committed by the host through native procs. */
final class LucillaMechanics {
    static final Set<String> COUNTERS=Set.of("lucilla_memory_held","lucilla_memory_body_done","lucilla_focus_channel","lucilla_focus_elapsed",
        "lucilla_memory_channel","lucilla_forget_serial","lucilla_c4_stacks","lucilla_camera");
    static final LucillaFocusWindow.Profile FOCUS=new LucillaFocusWindow.Profile(40,80,.08);
    static final int FORGET_INTERVAL=20;
    private LucillaMechanics(){}
    private static boolean echo(Transition t){return t.original.mode().equals("echo");}
    private static boolean memory(Transition t){return t.form.equals("memory");}
    private static void proc(Transition t,String id){t.effect(Kind.NATIVE_PROC,"lucilla_"+id,1,0);}
    private static void clearFocus(Transition t){t.clear("lucilla_focus_channel");t.clear("lucilla_focus_elapsed");}
    private static void startFocus(Transition t,boolean skill){
        require(!t.has("lucilla_focus_channel"),"focus_already_open");
        t.timer("lucilla_focus_channel",skill?2:1);t.timer("lucilla_focus_elapsed",0);
        proc(t,"focus_open");
    }
    static LucillaFocusWindow.View focusView(MechanicsState state){
        var focus=focus(state);return focus.view(state.timer("lucilla_focus_elapsed"));
    }
    private static LucillaFocusWindow focus(MechanicsState state){
        var focus=new LucillaFocusWindow(FOCUS,state.chain()>=1);
        if(state.timer("lucilla_focus_channel")>0)focus.begin(state.timer("lucilla_focus_channel")==2
            ?LucillaFocusWindow.Channel.SKILL:LucillaFocusWindow.Channel.BASIC,0);
        return focus;
    }
    private static void release(Transition t){
        require(t.has("lucilla_focus_channel"),"focus_not_open");
        var outcome=focus(t.build()).release(t.time("lucilla_focus_elapsed")).orElseThrow();
        focusedAttack(t,outcome==LucillaFocusWindow.Outcome.PERFECT);
    }
    private static void focusedAttack(Transition t,boolean perfect){
        boolean skill=t.time("lucilla_focus_channel")==2;clearFocus(t);proc(t,"focus_close");
        if(!skill){
            t.action(perfect?"basic_03_perfect":"basic_03");t.add("impression",perfect?50:25);t.combo=1;
            return;
        }
        t.action(perfect?"skill_chase":"skill_fill");t.add("impression",perfect?50:25);t.combo=2;
        if(!perfect){t.timer("skill",Math.max(0,t.time("skill")-160));return;}
        t.effect(Kind.GAIN_CONCERTO,"lucilla_chase",20,0);
        if(echo(t))t.effect(Kind.BUFF_TEAM,"lucilla_team_echo_bonus",.25,600);
        else {proc(t,"chase_chafe");t.effect(Kind.MARK_AREA,"lucilla_glacio_resistance",.08,600);}
        if(t.original.chain()>=1)t.effect(Kind.BUFF_SELF,"lucilla_c1_crit",.20,200);
    }
    static void input(Transition t,Input input){
        require(!t.has("lucilla_finale_lock"),"finale_locked");
        if(t.has("lucilla_camera")){
            if(input==Input.AIM_RELEASE)return;
            if(input==Input.AIM_PRESS){t.clear("lucilla_camera");proc(t,"camera_toggle");return;}
            if(input==Input.BASIC){t.requireCooldown("lucilla_shutter");t.timer("lucilla_shutter",20);proc(t,"camera_shutter");return;}
            if(input==Input.BASIC_HOLD||input==Input.HEAVY_START||input==Input.BASIC_RELEASE||input==Input.HEAVY_RELEASE)return;
            t.clear("lucilla_camera");
        }
        if(t.has("lucilla_focus_channel")){
            if(input==Input.BASIC_RELEASE||input==Input.HEAVY_RELEASE||input==Input.SKILL_RELEASE){
                boolean skill=t.time("lucilla_focus_channel")==2;
                if(skill==(input==Input.SKILL_RELEASE))release(t);
            }
            else if(input==Input.DODGE||input==Input.BACK_DODGE){clearFocus(t);proc(t,"focus_close");}
            else require(input==Input.BASIC_HOLD||input==Input.HEAVY_START||input==Input.SKILL_HOLD,"focus_open");
            return;
        }
        switch(input){
            case BASIC,BASIC_HOLD,HEAVY_START -> {
                boolean held=input!=Input.BASIC;
                if(memory(t)){
                    if(t.has("lucilla_memory_channel")){
                        if(held)t.timer("lucilla_memory_held",1);
                        else t.timer("lucilla_memory_tap_repeat",20); // MC repeat-click grace, not a source frame claim.
                        break;
                    }
                    int stage=Math.clamp(t.combo,1,3);t.action("memory_basic_0"+stage);
                    t.combo=stage==3?3:stage+1;
                    if(stage==3){t.timer("lucilla_memory_channel",1);t.timer("lucilla_forget_pulse",FORGET_INTERVAL);
                        t.clear("lucilla_memory_body_done");t.clear("lucilla_memory_tap_repeat");
                        if(held)t.timer("lucilla_memory_held",1);else t.clear("lucilla_memory_held");}
                }else{
                    int stage=Math.clamp(t.combo,1,3);
                    if(stage==3&&held)startFocus(t,false);
                    else {t.action("basic_0"+stage);t.combo=stage%3+1;if(stage==3)t.add("impression",25);}
                }
            }
            case BASIC_RELEASE,HEAVY_RELEASE -> {if(t.has("lucilla_memory_channel"))finale(t);}
            case AIR -> {t.payStamina(30);t.action(memory(t)?"memory_air":"air");t.combo=2;}
            case DODGE_COUNTER -> {t.action(memory(t)?"memory_counter":"dodge_counter");t.combo=3;}
            case SKILL,SKILL_HOLD -> {
                require(!memory(t),"memory_blocks_skill");t.requireCooldown("skill");t.timer("skill",320);
                t.action("skill");t.effect(Kind.PULL_AREA,"lucilla_focus_pull",1,12);
                // The opening hit and the followup must be separate accepted
                // actions. Even a tap waits for the host's opening completion.
                if(input==Input.SKILL_HOLD)startFocus(t,true);
                else t.timer("lucilla_fill_pending",12);
            }
            case LIBERATION -> {
                require(!memory(t),"memory_blocks_liberation");t.requireCooldown("liberation");
                require(t.get("impression")>=150,"photos_missing");
                t.timer("liberation",500);t.action(echo(t)?"liberation_echo":"liberation");form(t,"memory",0);
                t.effect(Kind.BUFF_SELF,echo(t)?"lucilla_echo_bonus":"lucilla_basic_bonus",.30,200);
                t.effect(Kind.GAIN_CONCERTO,"lucilla_liberation",20,0);proc(t,"memory_begin");
            }
            case INTRO -> {
                t.action(memory(t)?"intro_memory":"intro");t.effect(Kind.GAIN_CONCERTO,"lucilla_intro",10,0);
                if(memory(t))t.combo=3;else t.add("impression",100);
                // Both Editing variants apply Chafe; only Chase and Forget
                // restrict their application to the Glacio modality.
                proc(t,"intro_chafe");
            }
            case OUTRO -> {
                if(echo(t))t.effect(Kind.BUFF_NEXT,"lucilla_outro_echo",.50,280,Map.of("endsOnSwitch",1d));
                else t.effect(Kind.MARK_AREA,"lucilla_outro_chafe",.60,600);
            }
            case AIM_PRESS -> {require(!memory(t),"memory_blocks_camera");t.timer("lucilla_camera",1);t.timer("lucilla_camera_marker",20);proc(t,"camera_toggle");}
            default -> {}
        }
    }
    static void tick(Transition t){
        if(t.context.active()&&t.has("lucilla_camera")&&!t.has("lucilla_camera_marker")){
            t.timer("lucilla_camera_marker",20);proc(t,"camera_mark");
        }
        if(!t.has("lucilla_c4_life"))t.clear("lucilla_c4_stacks");
        if(t.has("lucilla_focus_channel")){
            t.timer("lucilla_focus_elapsed",t.time("lucilla_focus_elapsed")+1);
            var result=focus(t.build()).tick(t.time("lucilla_focus_elapsed"));
            if(result.isPresent())focusedAttack(t,true);
        }
        if(t.original.timer("lucilla_fill_pending")==1){
            if(t.context.active()&&!memory(t)){t.timer("lucilla_focus_channel",2);focusedAttack(t,false);}
        }
        if(t.has("lucilla_memory_channel")&&t.has("lucilla_memory_body_done")&&!t.has("lucilla_memory_held")&&!t.has("lucilla_memory_tap_repeat")){
            finale(t);return;
        }
        if(t.has("lucilla_memory_channel")&&!t.has("lucilla_forget_pulse")&&t.context.active()){
            if(t.get("impression")<50){finale(t);return;}
            t.add("impression",-50);t.timer("lucilla_forget_serial",t.time("lucilla_forget_serial")+1);
            t.action(echo(t)?"forget_echo":"forget");proc(t,"photo_consumed");
            if(!echo(t))proc(t,"forget_chafe");
            if(t.original.chain()>=4){
                int stacks=Math.min(3,t.time("lucilla_c4_stacks")+1);
                t.timer("lucilla_c4_stacks",stacks);t.timer("lucilla_c4_life",120);
                t.effect(Kind.BUFF_SELF,"lucilla_c4_attack",stacks*.10,120);
                proc(t,"forget_pull");
            }
            if(t.original.chain()>=5)t.effect(Kind.BUFF_SELF,"lucilla_c5_forget",.50,FORGET_INTERVAL);
            t.timer("lucilla_forget_pulse",FORGET_INTERVAL);
        }
        if(t.original.timer("lucilla_finale_lock")==1){form(t,"normal",0);proc(t,"memory_end");}
    }
    private static void finale(Transition t){
        t.clear("lucilla_memory_channel");t.clear("lucilla_forget_pulse");
        t.clear("lucilla_memory_held");t.clear("lucilla_memory_body_done");t.clear("lucilla_memory_tap_repeat");
        t.action(echo(t)?"finale_echo":"finale");t.effect(Kind.GAIN_CONCERTO,"lucilla_finale",20,0);
        t.effect(Kind.BUFF_SELF,"lucilla_finale_invulnerable",1,24,Map.of("preventsNativeHurt",1d));
        if(t.original.chain()>=3)t.effect(Kind.BUFF_SELF,"lucilla_c3_finale",1,24);
        t.timer("lucilla_finale_lock",24); // MC action duration, synchronized with authored animation.
    }
    static void completed(Transition t,String action){
        if(!action.equals("lucilla/memory_basic_03")||!t.has("lucilla_memory_channel"))return;
        t.timer("lucilla_memory_body_done",1);
        if(!t.has("lucilla_memory_held")&&!t.has("lucilla_memory_tap_repeat"))finale(t);
    }
    static void switchOut(Transition t){
        require(!t.has("lucilla_finale_lock"),"finale_cannot_switch");
        clearFocus(t);t.clear("lucilla_fill_pending");t.clear("lucilla_memory_channel");
        t.clear("lucilla_memory_held");t.clear("lucilla_memory_body_done");t.clear("lucilla_memory_tap_repeat");
        t.clear("lucilla_camera");
        t.clear("lucilla_forget_pulse");proc(t,"focus_close");
        // Memory and photos survive switching; focus/continuous attacks do not.
    }
}
