package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.*;
import static cn.zgnhit.mcwuwa.content.mechanics.MechanicsEngine.*;
import static cn.zgnhit.mcwuwa.content.mechanics.ExtendedMechanics.*;

/** Weapon-owned strategies; native descriptions determine gates and durations.
 * Galbrena's unpublished gain/consumption amounts use explicit MC action grants.
 * Host owns world geometry, healing modifiers, butterflies and target debuffs. */
final class NewThreeMechanics {
    static final Set<String> COUNTERS=Set.of("shore_stage","shore_calculated","shore_escape_uses","sig_hold_consumed","sig_rune_count","sig_rune_0","sig_rune_1","sig_rune_2","sig_rune_3","sig_talent","sig_origin_spent","sig_blessings","sig_condense","gal_heavy_stage","gal_hellwalk_lock");
    private NewThreeMechanics(){}

    static MechanicsState leaveShorekeeperWorld(MechanicsState state){
        var cancelled=InterruptionRules.cancelled(state);
        var timers=new HashMap<>(cancelled.timers());
        for(String key:Set.of("shore_domain","shore_stage","shore_heal","shore_insight_used"))timers.remove(key);
        for(int i=0;i<5;i++)timers.remove("shore_kernel_"+i);
        return new MechanicsState(state.characterId(),"normal",state.mode(),state.chain(),1,state.resources(),timers);
    }

    static void shorekeeper(Transition t,Input input){
        switch(input){
            case BASIC,BASIC_HOLD -> {if(t.has("shore_unbound")){finishButterfly(t);break;}boolean full=java.util.stream.IntStream.range(0,5).allMatch(i->t.has("shore_kernel_"+i));combo(t,"",4);if(full)ripen(t);}
            case HEAVY_START -> {if(t.get("data")>=5)deduction(t,false);else {t.timer("shore_unbound",Integer.MAX_VALUE);t.timer("shore_calculation",20);t.effect(Kind.MOVE_SPECIAL,"shorekeeper_butterfly",1,0);}}
            case HEAVY_FULL,HEAVY_RELEASE -> {if(t.has("shore_unbound"))finishButterfly(t);else if(t.get("data")>=5)deduction(t,false);else {t.payStamina(25);t.action("heavy");}}
            case AIR -> {if(t.get("data")>=5)deduction(t,true);else {t.payStamina(30);t.action("air");}t.combo=2;}
            case DODGE_COUNTER -> t.action("dodge_counter");
            case SKILL -> {t.requireCooldown("skill");t.timer("skill",320);t.action("skill");t.effect(Kind.HEAL_TEAM,"shorekeeper_skill",1313+t.context.maxHealthNative()*.0597,0,Map.of("sourceHealingBonus",t.original.chain()>=4?.70:0d));t.effect(Kind.GAIN_CONCERTO,"shorekeeper_skill",20,0);t.combo=2;}
            case LIBERATION -> {t.requireCooldown("liberation");t.payEnergy(175);t.timer("liberation",500);t.action("liberation");int life=t.original.chain()>=1?800:600;t.timer("shore_domain",life);t.clear("shore_insight_used");t.timer("shore_stage",1);t.timer("shore_heal",60);t.effect(Kind.SPAWN_FIELD,"shorekeeper_domain",1,life,Map.of("radius",t.original.chain()>=1?30d:12d));t.effect(Kind.GAIN_CONCERTO,"shorekeeper_liberation",20,0);if(t.original.chain()>=3&&!t.has("shore_c3_cd")){t.effect(Kind.GAIN_CONCERTO,"shorekeeper_c3",20,0);t.timer("shore_c3_cd",500);}}
            case INTRO -> {boolean insight=t.has("shore_domain")&&t.time("shore_stage")==3&&!t.has("shore_insight_used");t.action(insight?"intro_insight":"intro");t.effect(Kind.HEAL_TEAM,"shorekeeper_intro",(insight?289:259)+t.context.maxHealthNative()*(insight?.0132:.0120),0);t.effect(Kind.GAIN_CONCERTO,"shorekeeper_intro",insight?20:10,0);if(insight){t.timer("shore_insight_used",t.time("shore_domain"));if(t.original.chain()<1)endDomain(t);}}
            case OUTRO -> {t.timer("shore_escape",600);t.timer("shore_escape_uses",5);t.effect(Kind.BUFF_TEAM,"shorekeeper_outro",.15,600);}
            case BASIC_RELEASE,SKILL_RELEASE -> {}
            default -> throw new Rejected("unsupported_input_in_form");
        }
    }
    private static void deduction(Transition t,boolean air){t.payStamina(air?30:25);t.set("data",0);t.action(air?"air_evolution":"heavy_deduction");t.effect(Kind.GAIN_CONCERTO,"shorekeeper_forte",6,0);if(!air)t.effect(Kind.PULL_AREA,"shorekeeper_deduction",1,1,Map.of("radius",t.original.chain()>=5?7.8:6d,"centerOnOwner",1d));ripen(t);}
    private static void finishButterfly(Transition t){t.clear("shore_unbound");t.clear("shore_calculation");int data=t.time("shore_calculated");t.clear("shore_calculated");t.add("data",data);t.action("heavy");for(int i=0;i<data;i++)core(t,false);t.effect(Kind.MOVE_SPECIAL,"shorekeeper_butterfly",0,0);}
    private static void core(Transition t,boolean nextBasic){for(int i=0;i<5;i++)if(!t.has("shore_kernel_"+i)){t.timer("shore_kernel_"+i,120);t.effect(Kind.MOVE_SPECIAL,"shorekeeper_kernel",i,120);return;}if(nextBasic)ripen(t);}
    private static void ripen(Transition t){for(int i=0;i<5;i++)if(t.has("shore_kernel_"+i)){t.clear("shore_kernel_"+i);t.action("butterfly",Map.of("kernelIndex",(double)i));}}
    private static void endDomain(Transition t){t.clear("shore_domain");t.clear("shore_stage");t.effect(Kind.REMOVE_FIELD,"shorekeeper_domain",0,0);}

    static void sigrika(Transition t,Input input){
        switch(input){
            case BASIC,BASIC_HOLD -> {if(t.has("sig_interpret")&&!t.context.airborne()){t.clear("sig_interpret");t.action("basic_insight");}else {if(t.combo==1&&t.get("period")>=50)t.combo=2;int stage=t.combo;combo(t,"",4);if(stage==4)t.timer("sig_interpret",100);}}
            case HEAVY_START -> t.timer("charging",1);
            case HEAVY_FULL,HEAVY_RELEASE -> {if(t.time("sig_rune_count")>=2)runicHeavy(t);else {t.payStamina(20);t.action("heavy");t.combo=2;}}
            case AIR -> {t.payStamina(30);t.action("air");t.combo=2;}
            case DODGE_COUNTER -> {if(t.context.airborne()){t.payStamina(30);t.action("air_counter");t.combo=2;}else if(t.has("sig_interpret")){t.clear("sig_interpret");t.action("counter_insight");}else {t.action("dodge_counter");t.combo=4;}}
            case SKILL,SKILL_RELEASE -> {if(input==Input.SKILL_RELEASE&&t.has("sig_hold_consumed")){t.clear("sig_hold_consumed");break;}if(t.has("sig_interpret")&&!t.context.airborne()){t.clear("sig_interpret");t.action(t.get("period")>=50?"skill_sun":"skill_insight");}else {t.requireCooldown("skill");t.timer("skill",200);t.action("skill");t.combo=3;}}
            case SKILL_HOLD -> {if(t.get("period")<100||t.context.airborne())break;t.requireCooldown("sig_semantics");t.set("period",0);keepNewestRunes(t,2);t.timer("sig_semantics",500);t.timer("sig_semantics_cast",60);t.timer("sig_hold_consumed",60);t.action("skill_semantics");t.effect(Kind.GAIN_CONCERTO,"sigrika_semantics",10,0);}
            case LIBERATION -> {t.requireCooldown("liberation");t.payEnergy(125);t.timer("liberation",500);t.timer("sig_focus",400);t.action("liberation");t.effect(Kind.GAIN_CONCERTO,"sigrika_liberation",20,0);}
            case INTRO -> {t.action("intro");t.timer("sig_echo",400);t.combo=2;t.effect(Kind.GAIN_CONCERTO,"sigrika_intro",10,0);}
            case OUTRO -> {t.effect(Kind.OUTRO_HIT,"sigrika",7.95,0);t.timer("sig_condense",t.original.chain()>=1?3:2);t.timer("sig_condense_time",600);t.timers.keySet().removeIf(k->k.startsWith("sig_energy_seen_"));}
            case BASIC_RELEASE -> {}
            default -> throw new Rejected("unsupported_input_in_form");
        }
    }
    private static void keepNewestRunes(Transition t,int limit){
        int count=t.time("sig_rune_count");
        if(count<=limit)return;
        int removed=count-limit;
        for(int i=0;i<limit;i++)t.timer("sig_rune_"+i,t.time("sig_rune_"+(i+removed)));
        for(int i=limit;i<count;i++)t.clear("sig_rune_"+i);
        t.timer("sig_rune_count",limit);
    }

    private static void rune(Transition t,int kind){
        int capacity=t.get("period")>=50?4:2;
        keepNewestRunes(t,capacity-1);
        int next=t.time("sig_rune_count");
        t.timer("sig_rune_"+next,kind);
        t.timer("sig_rune_count",next+1);
    }
    private static void acquireRune(Transition t,int kind){rune(t,kind);if(t.get("period")>=100)return;if(t.has("sig_echo")){t.clear("sig_echo");rune(t,kind);}else if(t.has("sig_focus")){t.clear("sig_focus");rune(t,3-kind);}}
    private static void runicHeavy(Transition t){int first=t.time("sig_rune_0"),second=t.time("sig_rune_1"),n=t.time("sig_rune_count");String variant=first!=second?"burst":first==1?"chain":"sun";for(int i=2;i<n;i++)t.timer("sig_rune_"+(i-2),t.time("sig_rune_"+i));for(int i=Math.max(0,n-2);i<4;i++)t.clear("sig_rune_"+i);t.timer("sig_rune_count",n-2);
        double energy=Math.min(30,t.get("sun_energy"));t.add("sun_energy",-energy);t.timer("sig_origin_spent",(int)energy);if(energy>=30)t.timer("sig_talent",Math.min(t.original.chain()>=3?4:2,t.time("sig_talent")+1));t.timer("sig_origin_boost",60);t.action("heavy_origin_"+variant);t.add("period",50);if(variant.equals("chain"))t.effect(Kind.CONTROL_AREA,"sigrika_stasis",1,40);if(variant.equals("sun"))t.effect(Kind.PULL_AREA,"sigrika_sun",1,1,Map.of("radius",4d,"centerOnOwner",1d));}

    static void galbrena(Transition t,Input input){
        boolean demon=t.form.equals("demon");String p=demon?"demon_":"";
        if(Set.of(Input.BASIC,Input.BASIC_HOLD,Input.SKILL,Input.INTRO,Input.LIBERATION,Input.AIR,Input.DODGE_COUNTER,Input.HEAVY_FULL,Input.HEAVY_RELEASE).contains(input))t.clear("gal_hellwalk_lock");
        switch(input){
            case BASIC,BASIC_HOLD -> {if(input==Input.BASIC_HOLD&&!t.context.airborne()){galHeavy(t,demon);break;}int stage=Math.clamp(t.combo,1,demon?5:4);t.action(p+"basic_0"+stage);if(demon&&stage==4)innerBurn(t);t.combo=stage==(demon?5:4)?(demon?3:2):stage+1;}
            case HEAVY_START -> {t.timer("charging",1);if(t.context.airborne()){require(!t.has("gal_air_spent"),"air_rain_interrupted");t.timer("gal_raining",Integer.MAX_VALUE);t.action(p+"air_rain");} }
            case HEAVY_FULL,HEAVY_RELEASE -> {
                if(t.has("gal_raining")){
                    // Key-up must end the channel even if the paid plunge cannot
                    // be afforded. Do not keep firing or award a free attack.
                    t.clear("gal_raining");t.clear("gal_rain_pulse");t.clear("charging");
                    if(t.context.stamina()>=30){t.payStamina(30);t.action(p+"air");t.combo=3;}
                    else t.timer("gal_air_spent",Integer.MAX_VALUE);
                }else if(t.context.airborne()&&t.has("gal_air_spent"))t.clear("charging");
                else galHeavy(t,demon);
            }
            case AIR -> {t.payStamina(30);t.action(p+"air");t.combo=3;}
            case DODGE_COUNTER -> {t.action(p+"dodge_counter");t.combo=4;}
            case DODGE -> {require(!t.context.airborne()&&!t.has("gal_hellwalk_lock"),"hellwalk_not_ready");t.payStamina(20);t.timer("gal_hellwalk_lock",1);t.timer("gal_hellwalk_continuation",60);t.action("hellwalk");innerBurn(t);}
            case SKILL -> {if(!demon&&t.get("sin_fire")>=100){t.requireCooldown("gal_ascend");t.timer("gal_ascend",260);t.set("purifying_fire",t.get("sin_fire"));t.set("sin_fire",0);form(t,"demon",1000);t.timer("gal_demon",1000);t.action("skill_ascend");t.effect(Kind.GAIN_CONCERTO,"galbrena_ascend",10,0);t.combo=2;}else {t.requireCooldown("skill");t.timer("skill",100);t.action(p+"skill");if(demon)t.combo=4;}innerBurn(t);}
            case LIBERATION -> {require(!t.context.airborne()||t.has("gal_low_air"),"low_air_only");t.requireCooldown("liberation");t.payEnergy(125);t.timer("liberation",500);t.timer("gal_liberation_boost",280);t.action("liberation");t.effect(Kind.GAIN_CONCERTO,"galbrena_liberation",20,0);t.combo=2;}
            case INTRO -> {t.action("intro");t.combo=2;t.timer("gal_heavy_stage",2);t.timer("gal_heavy_window",60);innerBurn(t);t.effect(Kind.GAIN_CONCERTO,"galbrena_intro",10,0);}
            case OUTRO -> t.effect(Kind.OUTRO_HIT,"galbrena",7.95,0);
            case BASIC_RELEASE,SKILL_RELEASE -> {}
            default -> throw new Rejected("unsupported_input_in_form");
        }
        if(t.effects.stream().anyMatch(e->e.kind()==Kind.ACTION&&Set.of("galbrena/basic_04","galbrena/demon_basic_05","galbrena/heavy_03","galbrena/demon_heavy_03").contains(e.id())))
            t.effect(Kind.MOVE_SPECIAL,"restore_stamina",10,0);
    }
    private static void innerBurn(Transition t){t.timer("gal_inner",80);}
    private static void galHeavy(Transition t,boolean demon){int stage=t.time("gal_heavy_stage");stage=stage<1?1:Math.min(3,stage);t.payStamina(10);t.action((demon?"demon_":"")+"heavy_0"+stage);t.timer("gal_heavy_stage",stage==3?1:stage+1);t.timer("gal_heavy_window",60);t.combo=stage==1?2:3;}
    private static void exitDemon(Transition t){form(t,"normal",0);t.clear("gal_demon");t.set("purifying_fire",0);t.set("embers",0);t.timers.keySet().removeIf(k->k.startsWith("gal_echo_seen_"));}

    static void hit(Transition t,String action){
        switch(t.original.characterId()){
            case "shorekeeper" -> {if(action.startsWith("basic_")||Set.of("air","dodge_counter").contains(action)){t.add("data",action.equals("basic_03")?2:1);core(t,action.startsWith("basic_"));}if(action.equals("basic_03"))t.effect(Kind.PULL_AREA,"shorekeeper_basic3",1,1,Map.of("radius",t.original.chain()>=5?6d:4d));}
            case "sigrika" -> {if(action.equals("basic_insight")||action.equals("counter_insight"))acquireRune(t,1);if(action.equals("skill_insight")||action.equals("skill_sun"))acquireRune(t,2);}
            case "galbrena" -> {if(t.form.equals("demon")&&action.startsWith("demon_")){double cost=action.contains("air_rain")?2:action.endsWith("05")?16:action.endsWith("04")?12:10;t.add("purifying_fire",-cost);if(t.get("purifying_fire")==0)exitDemon(t);}else if(t.form.equals("normal")&&(action.startsWith("basic_")||action.startsWith("heavy_")||Set.of("air","air_rain","skill","intro").contains(action))){double gain=action.equals("intro")?20:action.equals("skill")?30:action.startsWith("basic_")?switch(action){case "basic_01"->20;case "basic_02","basic_03"->25;default->30;}:20;t.add("sin_fire",gain);}if(Set.of("skill","demon_skill").contains(action))t.effect(Kind.MOVE_SPECIAL,"galbrena_retreat",1,0);}
        }
    }

    static void tick(Transition t){
        String who=t.original.characterId();
        if(who.equals("shorekeeper")){
            // Expire old kernels before a butterfly exit can reuse their slots.
            // Countdown has already advanced: only the original value1 expires now.
            for(int i=0;i<5;i++)if(t.original.timer("shore_kernel_"+i)==1){t.clear("shore_kernel_"+i);t.action("butterfly",Map.of("kernelIndex",(double)i));}
            if(t.has("shore_unbound")&&t.context.active()){
                if(t.context.stamina()<1.25)finishButterfly(t);
                else {
                    t.payStamina(1.25);
                    if(t.time("shore_calculation")==0){t.timer("shore_calculated",Math.min(5,t.time("shore_calculated")+1));t.timer("shore_calculation",20);}
                }
            }
            if(t.original.timer("shore_domain")>0&&t.time("shore_heal")==0){
                t.timer("shore_heal",60);
                t.effect(Kind.HEAL_ACTIVE,"shorekeeper_domain",438+t.context.maxHealthNative()*.0239,0,
                    t.original.timer("shore_domain")==1?Map.of("completeFieldInterval",1d):Map.of());
            }
            if(!t.has("shore_domain")&&t.time("shore_stage")>0)endDomain(t);
        }
        if(who.equals("sigrika")){
            if(t.original.chain()>=2&&!t.context.inCombat()){
                if(t.time("role_idle_ticks")>80&&!t.has("sig_rest_focus")){t.timer("sig_focus",400);t.timer("sig_rest_focus",80);}
            }
            else t.clear("sig_rest_focus");
            if(t.original.chain()>=3&&!t.context.inCombat()){
                if(t.original.timer("sig_rest_clear")==1){t.clear("sig_talent");t.timer("sig_rest_clear",600);}
                else if(!t.has("sig_rest_clear"))t.timer("sig_rest_clear",600);
            }else t.clear("sig_rest_clear");
            if(!t.has("sig_condense_time"))t.clear("sig_condense");
        }
        if(who.equals("galbrena")){
            if(t.form.equals("demon")&&!t.has("gal_demon"))exitDemon(t);
            if(!t.context.airborne()){t.clear("gal_air_spent");t.clear("gal_raining");t.clear("gal_rain_pulse");}
            if(!t.has("gal_heavy_window"))t.clear("gal_heavy_stage");
            if(t.has("gal_raining")&&t.context.active()){
                if(t.context.stamina()<1){
                    t.clear("gal_raining");t.clear("gal_rain_pulse");t.clear("charging");
                    t.timer("gal_air_spent",Integer.MAX_VALUE);
                }else {t.payStamina(1);if(!t.has("gal_rain_pulse")){t.timer("gal_rain_pulse",5);t.action(t.form.equals("demon")?"demon_air_rain":"air_rain");}}
            }
        }
    }

    static void echo(Transition t,int id){
        if(t.original.characterId().equals("sigrika")){
            if(!t.has("sig_bless_seen_"+id)){t.timer("sig_bless_seen_"+id,Integer.MAX_VALUE);t.timer("sig_blessings",Math.min(6,t.time("sig_blessings")+1));}
            if(!t.has("sig_energy_seen_"+id)){t.timer("sig_energy_seen_"+id,Integer.MAX_VALUE);t.add("sun_energy",10);}
            if(t.time("sig_condense")>0){t.timer("sig_condense",t.time("sig_condense")-1);t.effect(Kind.CONTROL_AREA,"sigrika_condense",1,40);}
            if(t.original.chain()>=4)t.effect(Kind.BUFF_TEAM,"sigrika_c4_attack",.20,400);
        }
        if(t.original.characterId().equals("galbrena")){
            if(!t.form.equals("demon")&&!t.has("gal_echo_seen_"+id)){t.timer("gal_echo_seen_"+id,Integer.MAX_VALUE);t.add("embers",8);}
            if(t.original.chain()>=4)t.effect(Kind.BUFF_TEAM,"galbrena_c4_bonus",.20,400);
        }
    }
    static void switchOut(Transition t){
        if(t.original.characterId().equals("shorekeeper")&&t.has("shore_unbound"))finishButterfly(t);
        if(t.original.characterId().equals("sigrika")){t.clear("sig_interpret");t.clear("sig_hold_consumed");if(t.original.chain()<3)t.clear("sig_talent");}
        if(t.original.characterId().equals("galbrena")){t.clear("gal_raining");t.clear("gal_heavy_stage");}
    }
}
