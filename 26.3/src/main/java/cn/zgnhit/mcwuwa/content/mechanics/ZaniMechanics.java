package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.Map;
import java.util.Set;
import static cn.zgnhit.mcwuwa.content.mechanics.MechanicsEngine.*;
import static cn.zgnhit.mcwuwa.content.mechanics.ExtendedMechanics.*;

/** Staged source1507 transitions. Guard6/12/30-tick windows and15/15/30
 * heavy costs are MC adaptation; form400, finale threshold160 and cooldown500
 * are source seconds. Hit-dependent ember and NIGHT spending belong to host. */
final class ZaniMechanics {
    static final Set<String> COUNTERS=Set.of("zani_guard_mode","zani_guard_age","zani_heavy_stage");
    private ZaniMechanics(){}
    private static boolean blazing(Transition t){return t.form.equals("blazing");}
    private static void proc(Transition t,String id,double amount){t.effect(Kind.NATIVE_PROC,"zani_"+id,amount,0);}
    private static void guard(Transition t,int mode){
        t.timer("zani_guard_mode",mode);t.timer("zani_guard_age",0);t.timer("zani_guard_life",30);
        t.clear("zani_entry");proc(t,"guard_open",mode);
    }
    private static void clearGuard(Transition t){t.clear("zani_guard_mode");t.clear("zani_guard_age");t.clear("zani_guard_life");}
    private static void suppress(Transition t,boolean counter){
        require(t.get("redundant")>=100,"redundant_not_full");t.add("redundant",-100);
        t.action(counter?"breach_counter":"suppression");t.timer("zani_thorns",280);
        if(t.original.chain()>=1)t.effect(Kind.BUFF_SELF,"zani_c1_spectro",.50,280);
        t.effect(Kind.GAIN_CONCERTO,"zani_crisis",10,0);proc(t,"suppression",1);
    }
    private static void heavy(Transition t,String action){
        require(blazing(t),"not_blazing");double cost=switch(action){case "dawn","day"->15;case "flash"->30;default->0;};
        require(t.get("blaze")>=cost,"insufficient_blaze");t.add("blaze",-cost);
        t.action("heavy_"+action);t.timer("zani_heavy_stage",action.equals("dawn")?1:action.equals("day")||action.equals("flash")?2:0);
        proc(t,"heavy_"+action,cost);t.clear("zani_entry");
    }
    private static void guardHeavy(Transition t,String action){
        proc(t,"guard_heavy",1);
        heavy(t,action);
    }
    private static void release(Transition t){
        int mode=t.time("zani_guard_mode"),age=t.time("zani_guard_age");clearGuard(t);
        if(mode==2)suppress(t,false);else if(mode==3)guardHeavy(t,age<12?"dawn":"night");
        proc(t,"guard_close",0);
    }
    static void input(Transition t,Input input){
        switch(input){
            case BASIC,BASIC_HOLD -> {
                if(blazing(t)&&t.has("zani_night_resume")){
                    t.clear("zani_night_resume");t.action("heavy_night_last");proc(t,"night_resume",0);break;
                }
                if(t.time("zani_guard_mode")>=2)break;
                if(t.time("zani_guard_mode")==1){clearGuard(t);proc(t,"guard_close",0);}
                if(t.context.airborne()){t.payStamina(30);t.action("air");t.combo=3;break;}
                if(blazing(t)){
                    if(t.has("zani_entry")&&t.get("blaze")>=30){guard(t,3);break;}
                    int stage=t.time("zani_heavy_stage");
                    if(stage==1){heavy(t,"day");break;}
                    if(stage==2){heavy(t,"night");break;}
                    if(t.get("blaze")>=30){heavy(t,"dawn");break;}
                }
                if(t.has("zani_breakthrough")){t.clear("zani_breakthrough");t.action("breakthrough");t.combo=4;break;}
                int stage=t.combo;combo(t,"",4);if(stage==3)t.timer("zani_breakthrough",30);
            }
            case HEAVY_START -> {if(blazing(t)&&t.get("blaze")>=30)guard(t,3);else t.timer("charging",1);}
            case HEAVY_RELEASE,HEAVY_FULL -> {
                if(t.has("zani_guard_mode")){release(t);break;}
                t.clear("charging");t.payStamina(20);t.action("heavy");t.combo=3;
            }
            case AIR -> {t.payStamina(30);t.action("air");t.combo=3;}
            case DODGE_COUNTER -> {
                if(blazing(t)&&t.time("zani_heavy_stage")!=2&&t.get("blaze")>=30)heavy(t,"flash");
                else {t.action("dodge_counter");t.timer("zani_breakthrough",30);}
            }
            case SKILL,SKILL_HOLD -> {
                if(t.has("zani_guard_mode"))break;
                if(blazing(t)&&t.get("blaze")>=30){guard(t,3);break;}
                if(!blazing(t)&&t.get("redundant")>=100){guard(t,2);break;}
                t.requireCooldown("skill");t.timer("skill",100);t.action("skill");t.combo=3;t.timer("zani_standard_follow",40);
                t.effect(Kind.GAIN_CONCERTO,"zani_standard",5,0);guard(t,1);
            }
            case SKILL_RELEASE,BASIC_RELEASE -> {if(t.has("zani_guard_mode"))release(t);}
            case BLOCK_HIT -> {
                int mode=t.time("zani_guard_mode");
                if(mode==0||mode!=1&&t.time("zani_guard_age")>=6)break;
                clearGuard(t);t.timer("zani_post_guard",40);
                if(mode==1){t.action("precise_counter");t.combo=3;t.effect(Kind.GAIN_CONCERTO,"zani_counter",5,0);}
                else if(mode==2)suppress(t,true);else guardHeavy(t,"flash");
                t.effect(Kind.CONTROL_AREA,"zani_counter_stasis",1,10,Map.of("radius",6d));
                t.effect(Kind.MARK_TARGET,"zani_counter_vibration",mode==3?.10:.05,0);
            }
            case LIBERATION -> {
                if(blazing(t)){
                    require(t.get("blaze")<30||t.time("zani_blazing")<=240,"finale_not_ready");
                    t.action("finale");t.effect(Kind.GAIN_CONCERTO,"zani_finale",10,0);proc(t,"finale",1);
                    form(t,"normal",0);t.clear("zani_blazing");t.clear("zani_entry");t.clear("zani_heavy_stage");
                }else{
                    t.requireCooldown("liberation");t.payEnergy(125);t.timer("liberation",500);
                    t.action("liberation");form(t,"blazing",400);t.timer("zani_blazing",400);t.timer("zani_entry",30);
                    t.add("blaze",50);proc(t,"enter",1);t.effect(Kind.GAIN_CONCERTO,"zani_liberation",20,0);
                }
            }
            case INTRO -> {t.action("intro");t.effect(Kind.GAIN_CONCERTO,"zani_intro",10,0);
                t.effect(Kind.BUFF_SELF,"zani_intro_spectro",.12,280);
                if(t.original.chain()>=4)t.effect(Kind.BUFF_TEAM,"zani_c4_attack",.20,600);}
            case OUTRO -> proc(t,"outro",1);
            default -> { }
        }
    }
    static void hit(Transition t,String action){

        if(action.equals("basic_03")&&t.has("zani_standard_follow")){
            t.clear("zani_standard_follow");
            if(!blazing(t))t.add("redundant",10);
            t.effect(Kind.CONTROL_TARGET,"zani_standard_follow_stasis",1,10);
        }
    }
    static void tick(Transition t){
        if(t.has("zani_guard_mode")){t.timer("zani_guard_age",t.time("zani_guard_age")+1);if(!t.has("zani_guard_life"))release(t);}
        if(blazing(t)&&!t.has("zani_blazing")){form(t,"normal",0);t.clear("zani_heavy_stage");t.clear("zani_entry");clearGuard(t);proc(t,"expire",1);}
    }
    static void switchOut(Transition t){clearGuard(t);t.clear("charging");t.clear("zani_entry");t.clear("zani_breakthrough");t.clear("zani_night_resume");proc(t,"guard_close",0);}
}
