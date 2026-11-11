package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.Map;
import java.util.Set;
import static cn.zgnhit.mcwuwa.content.mechanics.MechanicsEngine.*;
import static cn.zgnhit.mcwuwa.content.mechanics.ExtendedMechanics.*;

/** Source1306 action transitions. Unspecified link windows use60 MC ticks.
 * Realm world stasis, shield and crown ownership are wired independently. */
final class AugustaMechanics {
    static final Set<String> COUNTERS=Set.of("augusta_sun_count","augusta_skill_stage");
    private AugustaMechanics(){}
    private static void proc(Transition t,String id,double amount){t.effect(Kind.NATIVE_PROC,"augusta_"+id,amount,0);}
    private static boolean realm(Transition t){return t.form.equals("realm");}
    private static void finishRealm(Transition t){
        t.action("realm_finale");form(t,"normal",0);t.clear("augusta_phase");t.clear("augusta_sun_count");
        t.timer("augusta_finale",60);proc(t,"finale",1);
    }
    private static void sun(Transition t){
        if(t.time("augusta_sun_count")>=9){finishRealm(t);return;}
        t.action("realm_sun");proc(t,"sun",1);t.timer("augusta_sun_count",t.time("augusta_sun_count")+1);
    }
    private static void spin(Transition t){t.payStamina(20);t.action("heavy_spin");t.clear("augusta_spin_window");proc(t,"heavy_c6",1);}
    private static void uppercut(Transition t){
        require(t.get("war")>=100,"war_not_full");t.payStamina(20);t.add("war",-100);t.action("heavy_uppercut");
        t.clear("augusta_upper_window");t.clear("augusta_skill_upper_window");proc(t,"heavy_c6",1);
    }
    private static void retreat(Transition t,boolean counter){
        if(!counter){require(t.get("war")>=100,"war_not_full");t.add("war",-100);}
        t.action(counter?"counter_retreat":"heavy_retreat");t.timer("augusta_spin_window",60);
    }
    private static void skill(Transition t){
        int stage=t.time("augusta_skill_stage");
        if(t.has("augusta_skill_link")&&stage==1){
            require(t.context.airborne(),"airborne_required");t.payStamina(10);t.action("skill_leap");t.timer("augusta_skill_stage",2);t.timer("augusta_skill_link",60);return;
        }
        if(t.has("augusta_skill_link")&&stage==2){
            require(t.context.airborne(),"airborne_required");t.add("power",-t.get("power"));t.action("skill_descent");
            t.clear("augusta_skill_stage");t.clear("augusta_skill_link");t.add("majesty",1);proc(t,"majesty",1);
            t.effect(Kind.GAIN_CONCERTO,"augusta_descent",7,0);return;
        }
        if(t.get("power")>=100){
            boolean counter=t.has("augusta_skill_counter");t.clear("augusta_skill_counter");
            t.payStamina(10);t.action(counter?"counter_rush":"skill_rush");t.timer("augusta_skill_stage",1);t.timer("augusta_skill_link",60);return;
        }
        t.requireCooldown("skill");t.timer("skill",300);t.action("skill");t.add("power",10);t.timer("augusta_skill_upper_window",60);
        t.effect(Kind.GAIN_CONCERTO,"augusta_skill",10,0);
    }
    static void input(Transition t,Input input){
        if(realm(t)){
            switch(input){
                case BASIC,BASIC_HOLD -> {if(t.context.airborne()){t.payStamina(30);t.action("air");}else sun(t);}
                case AIR -> {t.payStamina(30);t.action("air");}
                case LIBERATION -> {require(t.time("augusta_sun_count")>=9,"sun_chain_incomplete");finishRealm(t);}
                case LIBERATION_HOLD -> finishRealm(t);
                case LIBERATION_RELEASE,BASIC_RELEASE,DODGE -> { }
                default -> throw new Rejected("augusta_restricted_phase");
            }
            return;
        }
        switch(input){
            case BASIC,BASIC_HOLD -> {
                if(t.has("augusta_spin_window")){spin(t);break;}
                if(t.has("augusta_skill_link")){skill(t);break;}
                if(t.has("augusta_upper_window")&&t.get("war")>=100){uppercut(t);break;}
                if(t.context.airborne()){t.payStamina(30);t.action("air");break;}
                combo(t,"",4);
            }
            case HEAVY_START -> t.timer("charging",1);
            case BASIC_RELEASE -> {
                if(t.has("augusta_spin_window"))spin(t);
                else if(t.has("augusta_upper_window")&&t.get("war")>=100)uppercut(t);
            }
            case HEAVY_RELEASE,HEAVY_FULL -> {
                t.clear("charging");
                if(t.has("augusta_spin_window")){spin(t);break;}
                if(t.has("augusta_upper_window")&&t.get("war")>=100){uppercut(t);break;}
                if(t.get("war")>=100){retreat(t,false);break;}
                t.payStamina(20);t.action("heavy");t.combo=2;t.timer("augusta_upper_window",60);
            }
            case JUMP -> {if(t.get("war")>=100)uppercut(t);}
            case AIR -> {t.payStamina(30);t.action("air");}
            case DODGE_COUNTER -> {
                if(t.get("power")>=100&&!t.context.airborne())retreat(t,true);
                else if(t.get("power")>=100){t.action("counter_rush");t.timer("augusta_skill_stage",1);t.timer("augusta_skill_link",60);}
                else if(t.context.airborne()){t.payStamina(30);t.action("air_counter");}
                else if(t.get("war")>=100){t.action("counter_heavy");t.timer("augusta_upper_window",60);}
                else {t.action("dodge_counter");t.clear("augusta_upper_window");}
            }
            case SKILL,SKILL_HOLD -> {if(t.has("augusta_skill_upper_window")&&t.get("war")>=100)uppercut(t);else skill(t);}
            case LIBERATION -> {
                t.requireCooldown("liberation");t.payEnergy(125);t.timer("liberation",500);t.action("liberation");t.add("power",40);
                t.effect(Kind.GAIN_CONCERTO,"augusta_liberation",20,0);
            }
            case LIBERATION_HOLD -> {
                require(t.get("majesty")>=2,"majesty_not_full");t.requireCooldown("augusta_realm_cd");
                t.add("majesty",-2);t.timer("augusta_realm_cd",500);t.timer("augusta_phase",140);t.timer("augusta_realm",600);
                form(t,"realm",140);t.timer("augusta_sun_count",0);proc(t,"realm_begin",1);
            }
            case INTRO -> {
                t.action("intro");t.add("war",100);t.add("power",20);proc(t,"intro",1);t.effect(Kind.GAIN_CONCERTO,"augusta_intro",10,0);
                if(t.original.chain()>=4)t.effect(Kind.BUFF_TEAM,"augusta_c4_attack",.20,600);
            }
            case OUTRO -> t.effect(Kind.BUFF_NEXT,"augusta_outro",.15,280,Map.of("endsOnSwitch",1d));
            default -> { }
        }
    }
    static void tick(Transition t){
        if(t.context.airborne())t.clear("augusta_spin_window");
        if(realm(t)&&!t.has("augusta_phase"))finishRealm(t);
    }
    static void switchOut(Transition t){require(!realm(t),"augusta_restricted_phase");t.clear("charging");t.clear("augusta_spin_window");t.clear("augusta_upper_window");t.clear("augusta_skill_upper_window");t.clear("augusta_skill_link");t.clear("augusta_skill_stage");}
}
