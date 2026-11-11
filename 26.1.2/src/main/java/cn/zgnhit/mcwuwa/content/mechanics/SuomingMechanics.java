package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.Map;
import static cn.zgnhit.mcwuwa.content.mechanics.MechanicsEngine.*;
import static cn.zgnhit.mcwuwa.content.mechanics.ExtendedMechanics.*;

/** Frozen1312 input qualifications. Unspecified continuation windows use60 MC
 * ticks. Heart completion is an actual host action event, never a guessed timer. */
final class SuomingMechanics {
    static final java.util.Set<String> COUNTERS=java.util.Set.of("suoming_heart_held");
    private SuomingMechanics(){}
    private static boolean deep(Transition t){return t.form.equals("deep");}
    // World-only events: forbidden, rain and chain_intro. Other state/action
    // transitions already have an owner and must not become pending target marks.
    private static void proc(Transition t,String id){t.effect(Kind.NATIVE_PROC,"suoming_"+id,1,0);}
    private static void enterDeep(Transition t){form(t,"deep",0);t.add("obsession",-t.get("obsession"));t.clear("skill");t.combo=1;}
    private static void follow(Transition t,int stage){t.combo=stage;t.timer("combo_window",60);}
    private static void spin(Transition t,int stage){
        t.action("thunder_spin_0"+stage);t.clear("suoming_spin_ready");
        if(stage==1)t.timer("suoming_spin_second",60);else t.clear("suoming_spin_second");
    }
    private static void heart(Transition t,boolean held){
        require(t.has("suoming_heart_ready"),"heart_not_ready");
        t.clear("suoming_heart_ready");t.add("obsession",-t.get("obsession"));
        form(t,"calamity",0);t.action("heart");
        t.effect(Kind.GAIN_CONCERTO,"suoming_heart",40,0);
        t.timer("suoming_outro_qualified",160);
        t.effect(Kind.BUFF_SELF,"suoming_heart_invulnerable",1,20,Map.of("preventsNativeHurt",1d)); // MC opening window; source gives no frame count.
        if(held)t.timer("suoming_heart_held",1);
    }
    static void input(Transition t,Input input){
        if(t.form.equals("calamity")){
            if(input==Input.BASIC_RELEASE||input==Input.SKILL_RELEASE){t.clear("suoming_heart_held");return;}
            if(input==Input.BASIC_HOLD||input==Input.SKILL_HOLD){t.timer("suoming_heart_held",1);return;}
            throw new Rejected("suoming_heart_locked");
        }
        switch(input){
            case BASIC,BASIC_HOLD -> {
                if(t.has("suoming_heart_ready")){heart(t,input==Input.BASIC_HOLD);break;}
                if(t.context.airborne()){t.payStamina(30);t.action("air");break;}
                if(deep(t)&&t.has("suoming_spin_second")){spin(t,2);break;}
                if(deep(t)&&input==Input.BASIC_HOLD&&t.has("suoming_spin_ready")){spin(t,1);break;}
                int stage=Math.clamp(t.combo,1,deep(t)?4:3);combo(t,deep(t)?"deep_":"",deep(t)?4:3);
                if(deep(t)&&stage==2)t.timer("suoming_spin_ready",60);else t.clear("suoming_spin_ready");
                if(deep(t)&&stage==4&&t.has("suoming_forbidden"))follow(t,2);
            }
            case AIR -> {t.payStamina(30);t.action("air");}
            case DODGE_COUNTER -> {
                t.action(deep(t)?"deep_counter":"dodge_counter");follow(t,3);
                if(deep(t))t.timer("suoming_spin_ready",60);
            }
            case SKILL,SKILL_HOLD -> {
                if(t.has("suoming_heart_ready")){heart(t,input==Input.SKILL_HOLD);break;}
                if(t.get("obsession")>=800){
                    if(deep(t)){t.action("skill_release");t.timer("suoming_heart_ready",60);t.effect(Kind.PULL_AREA,"suoming_release",1,10,Map.of("radius",6d));}
                    else{t.action("skill_lock");enterDeep(t);}
                    break;
                }
                t.requireCooldown("skill");t.timer("skill",160);t.action("skill");
                if(t.has("unison")&&!t.has("suoming_covenant")){
                    require(t.context.concerto()>=20,"concerto_insufficient");
                    t.effect(Kind.PAY_CONCERTO,"suoming_forbidden",20,0);t.clear("unison");
                    t.add("obsession",-t.get("obsession"));t.timer("suoming_forbidden",240);
                    t.effect(Kind.BUFF_SELF,"suoming_contract_multiplier",1,240,Map.of("endsOnSwitch",1d));
                    t.effect(Kind.BUFF_SELF,"suoming_contract_crit_damage",t.original.chain()>=6?3:1,240,Map.of("endsOnSwitch",1d));
                    proc(t,"forbidden");
                }
                t.timer("suoming_guard",10);follow(t,deep(t)?3:2);
            }
            case BLOCK_HIT -> {
                require(t.has("suoming_guard"),"guard_window_closed");t.clear("suoming_guard");
                t.action("skill_counter");follow(t,deep(t)?4:3);
                t.timer("suoming_counter_armor",20);
                t.effect(Kind.BUFF_SELF,"suoming_counter_invulnerable",1,20);
                t.effect(Kind.CONTROL_AREA,"suoming_guard_stasis",1,10,Map.of("radius",6d));
            }
            case LIBERATION -> {
                require(deep(t),"deep_required");t.requireCooldown("liberation");t.payEnergy(125);
                t.timer("liberation",500);t.action("liberation");t.timer("unison",100);
                t.add("obsession",200);t.clear("suoming_forbidden");
                t.timer("suoming_outro_qualified",160);
                t.effect(Kind.GAIN_CONCERTO,"suoming_liberation",20,0);
                if(t.original.chain()>=3)t.effect(Kind.BUFF_SELF,"suoming_c3_basic",.30,500);
            }
            case INTRO -> {
                boolean wasDeep=deep(t),response=t.has("suoming_intro_response");
                t.action((wasDeep?"intro_deep":"intro")+(response?"_unison":""));
                if(wasDeep){t.add("obsession",200);t.timer("suoming_spin_second",60);}else enterDeep(t);
                t.effect(Kind.GAIN_CONCERTO,"suoming_intro",10,0);
                if(response&&!t.has("suoming_intro_bonus_cd")){t.effect(Kind.GAIN_CONCERTO,"suoming_intro_bonus",10,0);t.timer("suoming_intro_bonus_cd",500);}
                t.effect(Kind.BUFF_SELF,"suoming_intro_electro",.50,300,Map.of("endsOnSwitch",1d));
                t.clear("suoming_intro_response");
                if(!response&&t.original.chain()>=3&&!t.has("suoming_chain_intro_cd")){
                    t.timer("suoming_chain_intro_cd",500);proc(t,"chain_intro");
                }
            }
            case OUTRO -> {
                int layers=t.time("suoming_unison_layers");
                if(t.has("suoming_unison_spent")){t.clear("suoming_unison_spent");t.timer("suoming_covenant",600);proc(t,"rain");}
                t.clear("suoming_forbidden");
                t.effect(Kind.BUFF_NEXT,"suoming_outro",1,160,Map.of("electroAmplification",.20,"skillAmplification",.25,"endsOnSwitch",1d));
                if(t.has("suoming_covenant")&&t.has("suoming_outro_qualified"))
                    t.effect(Kind.BUFF_NEXT,"suoming_next_electro",.30+.20*Math.min(2,layers),160,Map.of("endsOnSwitch",1d));
                if(t.original.chain()>=2)t.effect(Kind.BUFF_NEXT,"suoming_c2_crit_damage",.10+.06*Math.min(4,layers),600,Map.of("endsOnSwitch",1d));
                
            }
            default -> { }
        }
    }
    static void switchOut(Transition t){
        require(!t.form.equals("calamity"),"suoming_heart_locked");
        for(String key:new String[]{"suoming_heart_ready","suoming_spin_ready","suoming_spin_second","suoming_guard","suoming_forbidden","suoming_heart_held"})t.clear(key);
    }
    static void completed(Transition t,String action){
        if(!t.form.equals("calamity"))return;
        if(action.equals("suoming/heart")&&t.has("suoming_heart_held")){
            t.clear("suoming_heart_held");t.action("heart_hold");return;
        }
        if(action.equals("suoming/heart")||action.equals("suoming/heart_hold")){
            t.clear("suoming_heart_held");form(t,"normal",0);
            
        }
    }
}
