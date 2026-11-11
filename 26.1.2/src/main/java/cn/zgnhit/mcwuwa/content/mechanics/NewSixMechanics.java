package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import static cn.zgnhit.mcwuwa.content.mechanics.MechanicsEngine.*;
import static cn.zgnhit.mcwuwa.content.mechanics.ExtendedMechanics.*;

/** Source-defined Changli/Carlotta transitions. Distances and unspecified continuation
 * windows are MC adaptations; damage remains in the native action table. */
final class NewSixMechanics {
    static final Set<String> COUNTERS=Set.of("changli_spent_charges","carlotta_mirror","lupa_air_stage","lupa_hunt_tier","lupa_c2_stacks","lupa_sprint_clock","denia_degenerate_layers","qingxiao_guard","qingxiao_consciousness","qingxiao_sword_charges","qingxiao_harmony","qingxiao_air_stage");
    private NewSixMechanics() { }

    static void changli(Transition t, Input input) {
        switch(input) {
            case BASIC,BASIC_HOLD -> {
                if(t.has("changli_true_sight")) {trueSight(t,t.context.airborne());break;}
                if(t.has("changli_charge_follow")) {t.clear("changli_charge_follow");changliPlunge(t);break;}
                if(t.has("changli_lift_follow")&&t.context.airborne()) {t.clear("changli_lift_follow");t.combo=3;}
                if(t.has("changli_plunge_follow")&&!t.context.airborne()) {t.clear("changli_plunge_follow");t.combo=3;}
                boolean air=t.context.airborne();if(air)t.payStamina(5);
                int stage=Math.clamp(t.combo,1,4);combo(t,air?"air_":"",4);
                if(stage==4)t.timer("changli_true_sight",240);
            }
            case HEAVY_START -> t.timer("charging",1);
            case HEAVY_FULL,HEAVY_RELEASE -> {
                t.clear("charging");
                if(t.context.airborne()){changliPlunge(t);break;}
                if(t.get("forte")>=4) {
                    boolean feather=t.has("changli_flaming_feather");t.set("forte",0);t.clear("changli_flaming_feather");
                    t.action(t.original.chain()>=5?"heavy_flaming_sacrifice_c5":"heavy_flaming_sacrifice",
                        Map.of("attackBonus",feather?.25:0d,"fusionBonus",.20,"defenseIgnore",t.original.chain()>=6?.55:.15,
                            "damageBonus",t.original.chain()>=1?.10:0d));
                    // Host binds reduction/interruption to the actual accepted action lifetime.
                    t.effect(Kind.BUFF_SELF,"changli_sacrifice_action",.40,0,Map.of("actionScoped",1d,"interruptionResistance",t.original.chain()>=1?1d:0d));
                    
                } else {t.payStamina(25);t.action("heavy");t.timer("changli_lift_follow",60);t.combo=3;t.effect(Kind.MOVE_SPECIAL,"changli_heavy_lift",1,0);}
            }
            case AIR -> changliPlunge(t);
            case JUMP -> {require(t.has("changli_true_sight"),"true_sight_required");trueSight(t,true);}
            case SKILL,SKILL_HOLD -> {
                int spent=t.time("changli_spent_charges");require(spent<2,"skill_charges_empty");
                t.timer("changli_spent_charges",spent+1);if(!t.has("changli_skill_recharge"))t.timer("changli_skill_recharge",240);
                t.timer("changli_true_sight",240);t.action("skill",Map.of("damageBonus",t.original.chain()>=1?.10:0d,"defenseIgnore",t.original.chain()>=6?.40:0d));
                
            }
            case LIBERATION -> {
                t.requireCooldown("liberation");t.payEnergy(125);t.timer("liberation",400);gainForte(t,4);
                t.timer("changli_flaming_feather",200);
                t.action("liberation",Map.of("fusionBonus",.20,"defenseIgnore",t.original.chain()>=6?.55:.15,"damageBonus",t.original.chain()>=3?.80:0d));
                
            }
            case INTRO -> {
                t.action("intro");t.timer("changli_true_sight",240);t.effect(Kind.MOVE_SPECIAL,"changli_intro_air",1,0);
                if(t.original.chain()>=4)t.effect(Kind.BUFF_TEAM,"changli_c4_attack",.20,600);
            }
            case OUTRO -> t.effect(Kind.BUFF_NEXT,"changli_outro",1,200,Map.of("fusionAmplification",.20,"liberationAmplification",.25,"endsOnSwitch",1d));
            case DODGE_COUNTER -> t.action("dodge_counter");
            case BASIC_RELEASE,SKILL_RELEASE,DODGE,PERFECT_DODGE -> { }
            default -> throw new Rejected("unsupported_input_in_form");
        }
    }
    private static void trueSight(Transition t,boolean air) {
        t.clear("changli_true_sight");t.action(air?"true_sight_charge":"true_sight_conquest",
            Map.of("fusionBonus",t.get("forte")*.05,"defenseIgnore",t.original.chain()>=6?.40:0d));
        t.effect(Kind.MOVE_SPECIAL,air?"changli_true_sight_dash":"changli_true_sight_ground_dash",1,0,Map.of("airborne",air?1d:0d));
        if(air)t.timer("changli_charge_follow",60);
        
    }
    private static void changliPlunge(Transition t) {
        // Any accepted plunge consumes the previous aerial continuation, not
        // only the BASIC branch. Otherwise a held attack after Charge leaves
        // a second forced plunge waiting for the next grounded basic input.
        t.clear("changli_charge_follow");t.clear("changli_lift_follow");
        t.payStamina(30);t.action("air");t.timer("changli_plunge_follow",60);t.combo=3;
        t.effect(Kind.MOVE_SPECIAL,"tide_plunge",1,0);
    }
    private static void gainForte(Transition t,int count) {
        double before=t.get("forte");t.add("forte",count);
        if(t.get("forte")>before&&t.original.chain()>=2)t.effect(Kind.BUFF_SELF,"changli_c2_crit",.25,160);
    }

    static void carlotta(Transition t,Input input) {
        boolean dance=t.form.equals("gunflower");
        switch(input) {
            case BASIC,BASIC_HOLD -> {
                if(dance){deathKnell(t);break;}
                if(t.has("carlotta_courtesy")&&!t.context.airborne()) {t.clear("carlotta_courtesy");t.action("courtesy");crystals(t,3);t.effect(Kind.MOVE_SPECIAL,"carlotta_courtesy_backflip",1,0);break;}
                if(t.context.airborne()){carlottaPlunge(t);break;}
                if(t.get("crystal")>0){int stage=Math.clamp(t.combo,1,3);t.action("necessary_0"+stage);t.combo=stage%3+1;consumeCrystals(t,1);}
                else {int stage=Math.clamp(t.combo,1,2);combo(t,"",2);if(stage==2)crystals(t,3);}
            }
            case HEAVY_START -> t.timer("charging",1);
            case HEAVY_FULL,HEAVY_RELEASE -> {
                t.clear("charging");require(!dance,"gunflower_heavy_unavailable");
                if(t.context.airborne()){carlottaPlunge(t);break;}
                if(t.get("substance")>=120) {
                    boolean end=!t.has("carlotta_tinted_crystal");t.set("substance",0);t.timer("skill",Math.max(0,t.time("skill")-120));
                    if(end){t.timer("carlotta_tinted_crystal",440);t.action(t.original.chain()>=5?"heavy_end_of_road_c5":"heavy_end_of_road");}
                    else t.action("heavy_restrictive");
                    
                } else {t.payStamina(25);t.action("heavy");crystals(t,3);}
                if(t.original.chain()>=4)t.effect(Kind.BUFF_TEAM,"carlotta_c4_skill_bonus",.25,600);
            }
            case AIR -> carlottaPlunge(t);
            case DODGE_COUNTER -> {require(!dance,"gunflower_counter_unavailable");t.action("dodge_counter");consumeCrystals(t,1);}
            case PERFECT_DODGE -> crystals(t,3);
            case SKILL,SKILL_HOLD -> {
                if(t.has("carlotta_show_brilliance")) {
                    t.clear("carlotta_show_brilliance");t.timer("skill",280);
                    t.action(t.original.chain()>=3?"skill_show_brilliance_c3":"skill_show_brilliance");
                    consumeCrystals(t,(int)t.get("crystal"));t.timer("carlotta_safe_plunge",60);
                    
                } else {
                    t.requireCooldown("skill");t.timer("carlotta_show_brilliance",60);
                    t.action(t.original.chain()>=3?"skill_c3":"skill");crystals(t,3);
                    
                }
            }
            case LIBERATION -> {
                if(dance){deathKnell(t);break;}
                t.requireCooldown("liberation");t.payEnergy(125);t.timer("liberation",500);
                boolean herald=t.get("substance")>=120||t.has("carlotta_herald");
                t.action("liberation",Map.of("rateBonus",herald?.80:0d));
                t.set("substance",0);form(t,"gunflower",200);t.timer("carlotta_gunflower",200);t.clear("carlotta_mirror");
                if(herald)t.timer("carlotta_herald",200);
                
            }
            case INTRO -> {t.action("intro");if(!dance){substance(t,30);crystals(t,3);}}
            case OUTRO -> {t.action("outro");if(t.original.chain()>=3)t.effect(Kind.NATIVE_PROC,"carlotta/outro_c3_extra",1,0);}
            case DODGE,BASIC_RELEASE,SKILL_RELEASE -> { }
            default -> throw new Rejected("unsupported_input_in_form");
        }
    }
    private static void deathKnell(Transition t) {
        int mirror=t.time("carlotta_mirror");boolean herald=t.has("carlotta_herald");
        if(mirror>=4){t.action(t.original.chain()>=2?"fatal_finale_c2":"fatal_finale",Map.of("rateBonus",herald?.80:0d));endDance(t);}
        else {t.action("death_knell_0"+(mirror+1)+(t.original.chain()>=6?"_c6":""),Map.of("rateBonus",herald?.80:0d));t.timer("carlotta_mirror",mirror+1);if(t.context.moving())t.effect(Kind.MOVE_SPECIAL,"carlotta_death_knell",1,0);}
    }
    private static void carlottaPlunge(Transition t) {
        t.payStamina(30);t.action("air",Map.of("immuneUntilHit",t.has("carlotta_safe_plunge")?1d:0d));t.clear("carlotta_safe_plunge");
        t.timer("carlotta_plunging",120);t.effect(Kind.MOVE_SPECIAL,"tide_plunge",1,0);
    }
    private static void crystals(Transition t,int count) {if(!t.form.equals("gunflower")){t.add("crystal",count);t.timer("carlotta_crystal_lifetime",200);}}
    private static void consumeCrystals(Transition t,int count) {int spent=(int)Math.min(count,t.get("crystal"));t.add("crystal",-spent);if(!t.form.equals("gunflower"))substance(t,spent*10);}
    private static void substance(Transition t,double count) {if(!t.form.equals("gunflower")){t.add("substance",count);if(t.get("substance")>=120)t.timer("carlotta_herald",Integer.MAX_VALUE);}}
    private static void endDance(Transition t) {form(t,"normal",0);t.set("substance",0);t.clear("carlotta_gunflower");t.clear("carlotta_mirror");t.clear("carlotta_herald");}

    /** Values must come from audited buff/action data. A missing cost cannot silently
     * become a free enhanced attack. Main host may inject a verified policy. */
    interface ResourcePolicy {
        double iunoCost(String action);
        double iunoRingHit(String action);
        double lupaHit(String action);
        default double deniaParticles(String action){return -1;}
        default double deniaParticleCost(String action){return -1;}
        default double deniaConformal(String action){return -1;}
        default double qingxiaoStyle(String action){return -1;}
        default double qingxiaoIntent(String action){return -1;}
    }
    private static final ResourcePolicy SOURCE_COSTS=new ResourcePolicy() {
        public double iunoCost(String action){return switch(action){case "bow_basic_01" -> 10;case "bow_counter" -> 20;case "bow_basic_02" -> 15;case "bow_basic_03","skill_beyond" -> 25;default -> -1;};}
        public double iunoRingHit(String action){return 0;}
        public double lupaHit(String action){return 0;}
    };
    static void iuno(Transition t,Input input){iuno(t,input,SOURCE_COSTS);}
    static void iuno(Transition t,Input input,ResourcePolicy rules) {
        boolean phase=!t.form.equals("normal"),bow=t.form.equals("new_moon");boolean cast=false;
        switch(input) {
            case BASIC,BASIC_HOLD -> {
                int stage=Math.clamp(t.combo,1,3);
                if(bow){iunoBow(t,"bow_basic_0"+stage,rules,new int[]{4,6,10}[stage-1],stage==3?.4857:.2591);t.combo=stage%3+1;t.timer("iuno_bow_continuation",60);}
                else if(t.context.airborne()) {t.payStamina(30);t.action("air");t.effect(Kind.MOVE_SPECIAL,"tide_plunge",1,0);}
                else {combo(t,"",3);if(stage==3&&!phase)t.timer("iuno_ending_ready",100);}
                cast=true;
            }
            case HEAVY_START -> t.timer("charging",1);
            case HEAVY_FULL,HEAVY_RELEASE,JUMP -> {
                t.clear("charging");
                if(input!=Input.JUMP&&t.context.concerto()>=100) {
                    boolean spawn=!t.has("iuno_full_moon_cd");
                    t.action(t.original.chain()>=6?"heavy_perfection_c6":"heavy_perfection");
                    t.effect(Kind.HEAL_TEAM,"iuno_perfection",t.context.attack()*1.9426,0);
                    iunoEndPhase(t);
                    if(spawn){t.timer("iuno_full_moon_cd",500);t.timer("iuno_full_moon",600);t.effect(Kind.SPAWN_FIELD,"iuno_full_moon",1,600,Map.of("period",100d,"healAttackRatio",.3238,"stamina",20d));}
                    if(t.original.chain()>=4)t.effect(Kind.SHIELD_TEAM,"iuno_c4",t.context.attack()*1.6,600,Map.of("endsOnSwitch",1d));
                    if(t.original.chain()>=6){iunoPhase(t,"new_moon");t.set("spirituality",100);t.clear("iuno_beyond_first");t.clear("iuno_beyond_second");}
                }else {
                    require(phase,"moon_phase_required");t.payStamina(25);
                    t.action(bow?"heavy_ring":"heavy_bow");
                    // Flux changes the weapon state, retaining the current
                    // Lunar Cycle clock; only entering/re-entering renews it.
                    form(t,bow?"crescent":"new_moon",0);
                    t.effect(Kind.MOVE_SPECIAL,"iuno_phase_flow",1,0);
                }
                cast=true;
            }
            case AIR -> {t.payStamina(30);t.action("air");t.effect(Kind.MOVE_SPECIAL,"tide_plunge",1,0);cast=true;}
            case DODGE_COUNTER -> {
                if(bow)iunoBow(t,"bow_counter",rules,8,.3238);else t.action("dodge_counter");
                t.combo=3;if(bow)t.timer("iuno_bow_continuation",60);cast=true;
            }
            case SKILL,SKILL_HOLD -> {
                if(bow) {
                    String charge=!t.has("iuno_beyond_first")?"iuno_beyond_first":"iuno_beyond_second";
                    t.requireCooldown(charge);t.timer(charge,200);int combo=t.combo;
                    iunoBow(t,"skill_beyond",rules,10,.4857);
                    if(t.original.chain()<3||!t.has("iuno_bow_continuation"))t.combo=1;else t.combo=combo;
                    
                    t.effect(Kind.MOVE_SPECIAL,"iuno_beyond",1,0,Map.of("escapeHitstun",1d,"extendByInput",1d,"immuneInterruption",t.original.chain()>=1?1d:0d));
                }else if(phase||t.has("iuno_ending_ready")) {
                    t.requireCooldown("iuno_clamor");t.timer("iuno_clamor",160);t.clear("iuno_ending_ready");
                    t.action(phase?"skill_unending":"skill_ending");t.add("spirituality",25);
                    if(!phase)iunoPhase(t,"crescent");
                }else {
                    t.requireCooldown("skill");t.timer("skill",120);t.action("skill");t.timer("iuno_ending_ready",100);
                    t.effect(Kind.MOVE_SPECIAL,"iuno_initial_dash",1,0);
                }
                cast=true;
            }
            case LIBERATION -> {
                t.requireCooldown("liberation");t.payEnergy(125);t.timer("liberation",500);t.action("liberation");
                t.add("spirituality",60);iunoPhase(t,bow?"new_moon":"crescent");iunoBlessing(t,5);cast=true;
            }
            case INTRO -> {t.action("intro");t.add("spirituality",40);if(!phase)t.timer("iuno_ending_ready",100);iunoBlessing(t,5);cast=true;}
            case OUTRO -> {t.effect(Kind.OUTRO_HIT,"iuno",1,0);t.effect(Kind.BUFF_NEXT,"iuno_outro",.5,280,Map.of("heavyAmplification",.5,"endsOnSwitch",1d));}
            case BASIC_RELEASE,SKILL_RELEASE,DODGE,PERFECT_DODGE -> { }
            default -> throw new Rejected("unsupported_input_in_form");
        }
        if(cast) {
            t.effect(Kind.SHIELD_TEAM,"iuno_personal_shield",t.context.attack()*.32,300,Map.of("ownerOnly",1d,"endsOnSwitch",1d));
            // Supplemental chain stats are read from metadata; C6's flat rate
            // replacement remains in its distinct source damage action.
            for(int i=0;i<t.effects.size();i++) {
                Effect e=t.effects.get(i);if(e.kind()!=Kind.ACTION)continue;
                var values=new HashMap<>(e.values());
                if(t.original.chain()>=1&&(e.id().contains("heavy_perfection")||e.id().contains("skill_beyond")))values.put("immuneInterruption",1d);
                t.effects.set(i,new Effect(e.kind(),e.id(),e.amount(),e.durationTicks(),values));
            }
        }
    }
    private static void iunoPhase(Transition t,String phase) {form(t,phase,0);t.timer("iuno_phase",300);}
    private static void iunoEndPhase(Transition t) {form(t,"normal",0);t.clear("iuno_phase");}
    static void iunoExitTraversal(Transition t){iunoEndPhase(t);}
    private static void iunoBow(Transition t,String action,ResourcePolicy rules,int concerto,double healRatio) {
        double cost=rules.iunoCost(action);boolean enhanced=cost>0&&t.get("spirituality")>0;
        if(cost<0)t.effect(Kind.MECHANIC_GAP,"iuno_spiritual_cost:"+action,1,0);
        if(enhanced){t.add("spirituality",-cost);t.effect(Kind.HEAL_TEAM,"iuno_spiritual",t.context.attack()*healRatio,0);}
        t.action(action+(enhanced?"_enhanced":""),Map.of("amplification",t.original.chain()>=3?.65:0d,"spiritualExtraConcerto",enhanced?(double)concerto:0d));
    }
    private static void iunoBlessing(Transition t,int layers) {t.effect(Kind.BUFF_SELF,"iuno_pale_blessing",layers,200,Map.of("maxStacks",10d,"perStackAmplification",.04,"fullStackExtra",t.original.chain()>=2?.40:0d,"endsOnSwitch",1d));}
    /** Host calls after actual shield receipt while the recipient is inside full moon. */
    static void iunoShieldInField(Transition t) {if(!t.has("iuno_blessing_icd")){t.timer("iuno_blessing_icd",10);iunoBlessing(t,1);}}

    static void lupa(Transition t,Input input) {
        switch(input) {
            case BASIC,BASIC_HOLD -> {
                if(t.has("lupa_break_enemy_ready")){lupaBreak(t);break;}
                // The explicit dodge follow-up replaces this tap even when the
                // preceding Flame Strike also qualified a Sharp Claw.
                if(t.has("lupa_plunge_after_dodge")){t.clear("lupa_plunge_after_dodge");lupaPlunge(t);break;}
                if(t.has("lupa_sharp_claw_ready")&&t.get("wolf_flame")>=50&&t.get("wolf_soul")==1){lupaHeavy(t);break;}
                if(t.has("lupa_starfall")||t.has("lupa_sprint_starfall")){t.clear("lupa_starfall");t.clear("lupa_sprint_starfall");t.clear("lupa_sprint_clock");t.action("starfall");t.combo=2;break;}
                if(t.has("lupa_basic2_followup")){t.clear("lupa_basic2_followup");t.action("basic_02");t.combo=3;break;}
                if(t.context.airborne()||t.has("lupa_intro_air")||t.has("lupa_basic_air_ready")) {
                    if(t.has("lupa_intro_air")){t.timer("lupa_air_stage",3);t.clear("lupa_intro_air");}
                    if(t.has("lupa_basic_air_ready")){t.timer("lupa_air_stage",1);t.clear("lupa_basic_air_ready");}
                    lupaAirCombo(t);
                }else {int stage=Math.clamp(t.combo,1,4);combo(t,"",4);if(stage==3)t.timer("lupa_basic_air_ready",20);}
            }
            case HEAVY_START -> t.timer("charging",1);
            case HEAVY_FULL,HEAVY_RELEASE -> {t.clear("charging");if(t.context.airborne())lupaPlunge(t);else lupaHeavy(t);}
            case AIR -> lupaAirCombo(t);
            case DODGE -> {if(t.has("lupa_air3_cast"))t.timer("lupa_plunge_after_dodge",60);}
            case DODGE_COUNTER -> {t.action("dodge_counter");t.combo=2;}
            case SKILL,SKILL_HOLD -> {
                if(t.has("lupa_break_enemy_ready")){lupaBreak(t);break;}
                if(t.get("wolf_soul")>=2){
                    boolean extreme=t.has("lupa_match_point")||t.original.chain()>=6;t.set("wolf_soul",0);t.clear("lupa_soul_lifetime");
                    if(extreme)t.clear("lupa_match_point");
                    t.action(extreme?(t.original.chain()>=4?"skill_wolf_dance_extreme_c4":"skill_wolf_dance_extreme"):"skill_wolf_dance",Map.of("defenseIgnore",extreme&&t.original.chain()>=6?.30:0d,"immuneInterruption",extreme&&t.original.chain()>=1?1d:0d));
                    t.timer("lupa_assist_ready",160);lupaFlag(t);break;
                }
                require(!t.has("lupa_match_point"),"match_point_blocks_encircle");
                if(t.has("lupa_ferocious_ready")) {
                    t.clear("lupa_basic_air_ready");
                    t.clear("lupa_ferocious_ready");t.timer("skill",240);t.action("skill_ferocious",Map.of("markedTargetDamageId",120760211d));t.add("wolf_flame",15);t.combo=2;
                    lupaFlag(t);t.effect(Kind.MOVE_SPECIAL,"lupa_ferocious_dash",1,0);
                }else {
                    if(input==Input.SKILL)t.clear("lupa_basic_air_ready");
                    t.requireCooldown("skill");t.action("skill");t.add("wolf_flame",15);t.timer("lupa_ferocious_ready",60);t.combo=2;
                    if(input==Input.SKILL_HOLD){t.timer("lupa_basic_air_ready",60);t.effect(Kind.MOVE_SPECIAL,"lupa_skill_lift",1,0);}
                }
            }
            case LIBERATION -> {
                t.requireCooldown("liberation");t.payEnergy(125);t.timer("liberation",400);t.action("liberation",Map.of("defenseIgnore",t.original.chain()>=6?.30:0d));
                t.set("wolf_soul",0);t.clear("lupa_soul_lifetime");t.add("wolf_flame",100);t.timer("lupa_break_enemy_ready",60);
                t.timer("lupa_hunt",700);t.timer("lupa_hunt_tier",1);t.clear("lupa_hunt_once");t.clear("lupa_pursuit_ready");
                t.effect(Kind.BUFF_TEAM,"lupa_hunt",1,700,Map.of("baseAttackBonus",.06,"perIntroAttackBonus",.06,"maxAttackBonus",.18,"bossFusionBonus",.10,"extraBossFusionBonus",.10,"ignoreFusionCount",t.original.chain()>=3?1d:0d));
                t.effect(Kind.BUFF_TEAM,"lupa_glory",1,700,Map.of("baseResistanceIgnore",t.original.chain()>=3?.15:.03,"perOtherFusion",t.original.chain()>=3?0d:.03,"threeFusionExtra",t.original.chain()>=3?0d:.06));
                t.effect(Kind.BUFF_TEAM,"lupa_escape_hitstun",3,700,Map.of("groundPerfectDodge",1d));
                
                if(t.original.chain()>=1)t.effect(Kind.BUFF_SELF,"lupa_c1_crit",.20,200);lupaFlag(t);lupaChain2(t);
            }
            case INTRO -> {
                if(t.has("lupa_pursuit_ready")) {
                    t.clear("lupa_pursuit_ready");t.action(t.original.chain()>=3?"intro_pursuit_c3":"intro_pursuit",Map.of("defenseIgnore",t.original.chain()>=6?.30:0d));
                    if(t.original.chain()<6){t.clear("lupa_hunt");t.clear("lupa_hunt_tier");t.effect(Kind.REMOVE_FIELD,"lupa_hunt_and_glory",1,0);}
                }else {t.action("intro");t.timer("lupa_intro_air",60);}
                
                if(t.original.chain()>=5)t.effect(Kind.BUFF_SELF,"lupa_c5_liberation",.15,200);
            }
            case OUTRO -> t.effect(Kind.BUFF_NEXT,"lupa_outro",1,280,Map.of("fusionAmplification",.20,"basicAmplification",.25,"endsOnSwitch",1d));
            case BASIC_RELEASE,SKILL_RELEASE,PERFECT_DODGE -> { }
            default -> throw new Rejected("unsupported_input_in_form");
        }
    }
    private static void lupaHeavy(Transition t) {
        t.payStamina(25);
        if(t.get("wolf_flame")>=50){boolean claw=t.get("wolf_soul")==1;t.action(claw?"heavy_sharp_claw":"heavy_wolf_bite");lupaSoul(t);if(!claw)t.timer("lupa_sharp_claw_ready",60);}
        else t.action("heavy");
    }
    private static void lupaAirCombo(Transition t) {
        int stage=Math.clamp(t.time("lupa_air_stage"),1,3);boolean strike=stage==3&&t.get("wolf_flame")>=50;
        t.payStamina(strike?30:5);t.action(strike?"air_flame_strike":"air_basic_0"+stage);t.timer("lupa_air_stage",stage%3+1);
        if(stage==3)t.timer("lupa_air3_cast",60);
        if(strike){lupaSoul(t);t.timer("lupa_sharp_claw_ready",60);}
    }
    private static void lupaPlunge(Transition t){t.payStamina(30);t.action("air");t.timer("lupa_starfall",60);t.effect(Kind.MOVE_SPECIAL,"tide_plunge",1,0);}
    private static void lupaSoul(Transition t){t.add("wolf_flame",-50);t.add("wolf_soul",1);t.timer("lupa_soul_lifetime",200);lupaFlag(t);lupaChain2(t);}
    private static void lupaFlag(Transition t){t.effect(Kind.BUFF_SELF,"lupa_burning_flag",.12,160);}
    private static void lupaChain2(Transition t){if(t.original.chain()>=2){int n=Math.min(2,t.time("lupa_c2_stacks")+1);t.timer("lupa_c2_stacks",n);t.timer("lupa_c2_duration",600);t.effect(Kind.BUFF_TEAM,"lupa_c2_fusion",n*.20,600);}}
    private static void lupaBreak(Transition t){t.clear("lupa_break_enemy_ready");t.set("wolf_flame",0);t.action("skill_break_enemy");t.timer("lupa_match_point",240);}
    /** Every teammate intro, including non-Lupa intros, must notify this owner state. */
    static void lupaTeamIntro(Transition t){if(t.has("lupa_hunt")){int n=Math.min(3,t.time("lupa_hunt_tier")+1);t.timer("lupa_hunt_tier",n);t.effect(Kind.BUFF_TEAM,"lupa_hunt_attack",n*.06,t.time("lupa_hunt"));if(n==3&&!t.has("lupa_hunt_once")){t.timer("lupa_hunt_once",t.time("lupa_hunt"));t.timer("lupa_pursuit_ready",t.time("lupa_hunt"));}}}
    static void lupaMarkedKill(Transition t,boolean markedByOwner){if(markedByOwner){t.clear("skill");t.clear("lupa_ferocious_ready");}}
    static void lupaTeammateLiberation(Transition t){if(!t.context.active()&&t.has("lupa_assist_waiting")){t.clear("lupa_assist_waiting");t.effect(Kind.NATIVE_PROC,"lupa/assist",1,0);}}
    static void lupaSprint(Transition t,boolean sprinting){if(sprinting){t.timer("lupa_sprint_clock",Math.min(50,t.time("lupa_sprint_clock")+1));if(t.time("lupa_sprint_clock")>=50)t.timer("lupa_sprint_starfall",2);}else t.clear("lupa_sprint_clock");}

    static void denia(Transition t,Input input) {
        boolean dark=t.form.equals("disillusion");
        switch(input) {
            case BASIC,BASIC_HOLD -> {
                if(dark&&t.has("denia_exile_follow")){deniaExile(t);break;}
                boolean air=t.context.airborne();int stage=Math.clamp(t.combo,1,4);
                if(air&&!dark){t.payStamina(30);deniaAction(t,"air");t.effect(Kind.MOVE_SPECIAL,"tide_plunge",1,0);break;}
                if(air)t.payStamina(5);
                String action=(dark?(air?"disillusion_air_":"disillusion_"):"")+"basic_0"+stage;
                deniaAction(t,action);t.combo=stage%4+1;
            }
            case HEAVY_START -> t.timer("charging",1);
            case HEAVY_FULL,HEAVY_RELEASE -> {
                t.clear("charging");boolean air=t.context.airborne();t.payStamina(air?30:20);
                deniaAction(t,dark?(air?"disillusion_air":"disillusion_heavy"):(air?"air":"heavy"));
                if(air)t.effect(Kind.MOVE_SPECIAL,"tide_plunge",1,0);
                else if(dark)t.effect(Kind.PULL_AREA,"denia_heavy",1,0,Map.of("radius",6d));
            }
            case AIR -> {if(dark){t.payStamina(5);int stage=Math.clamp(t.combo,1,4);deniaAction(t,"disillusion_air_basic_0"+stage);t.combo=stage%4+1;}else{t.payStamina(30);deniaAction(t,"air");t.effect(Kind.MOVE_SPECIAL,"tide_plunge",1,0);}}
            case DODGE_COUNTER -> {deniaAction(t,dark?"disillusion_counter":"dodge_counter");t.combo=4;}
            case SKILL,SKILL_HOLD -> {
                if(dark&&t.has("denia_exile_follow")){deniaExile(t);break;}
                String cd=dark?"denia_disillusion_skill":"skill";t.requireCooldown(cd);t.timer(cd,dark?80:400);
                if(!dark){deniaAction(t,"skill");t.add("particles",25);}
                else if(t.get("dark_core")>0){deniaAction(t,"skill_exile_01");t.timer("denia_exile_follow",60);}
                else deniaAction(t,"skill_call");
                t.effect(Kind.PULL_AREA,"denia_skill",1,0,Map.of("radius",6d));
            }
            case LIBERATION -> {
                t.clear("denia_exile_follow");
                String cd=dark?"denia_disillusion_liberation":"liberation";t.requireCooldown(cd);t.timer(cd,500);
                if(dark){
                    require(t.get("conformal")>=100,"conformal_missing");t.set("conformal",0);t.set("particles",0);deniaAction(t,"liberation_disillusion");
                    form(t,"normal",0);deniaEntropy(t,false);t.timer("denia_erosion",600);
                    t.effect(Kind.SPAWN_FIELD,"denia_erosion",1,600,Map.of("period",t.original.chain()>=4?60d:80d,"pull",1d,"replaceOwnerField",1d));
                }else {t.payEnergy(125);deniaAction(t,"liberation");form(t,"disillusion",0);deniaEntropy(t,true);}
            }
            case INTRO -> {deniaAction(t,dark?"intro_disillusion":"intro");t.add("dark_core",1);t.add("particles",25);if(dark)deniaEntropy(t,true);else t.combo=4;}
            case OUTRO -> {
                if(deniaFusion(t))t.effect(Kind.BUFF_TEAM,"denia_fusion_outro",.60,600,Map.of("nearActiveTargets",1d,"endsOnModeChange",1d));
                else t.effect(Kind.BUFF_NEXT,"denia_tune_outro",.15,320,Map.of("afterTuneOffset",.40,"endsOnSwitch",1d,"endsOnModeChange",1d));
            }
            case BASIC_RELEASE,SKILL_RELEASE,DODGE,PERFECT_DODGE -> { }
            default -> throw new Rejected("unsupported_input_in_form");
        }
    }
    private static void deniaExile(Transition t) {
        t.clear("denia_exile_follow");int cores=(int)t.get("dark_core");require(cores>0,"dark_core_missing");t.set("dark_core",0);t.add("conformal",40);
        deniaAction(t,"skill_exile_02"+(cores>1?"_core_"+cores:"")+(t.original.chain()>=2?"_c2":""));
    }
    private static void deniaAction(Transition t,String action){deniaAction(t,action,Map.of());}
    private static void deniaAction(Transition t,String action,Map<String,Double> extra) {
        var values=new HashMap<>(extra);boolean entropy=t.has("denia_entropy_dark")||t.has("denia_entropy_stage");
        if(t.original.chain()>=1&&(action.equals("skill")||action.startsWith("skill_exile")||action.matches("disillusion_(air_)?basic_0[34]")))values.put("immuneInterruption",1d);
        if(t.original.chain()>=2&&action.equals("skill_exile_01"))action="skill_exile_01_c2";
        if(t.original.chain()>=3&&action.equals("liberation_disillusion"))action="liberation_disillusion_c3";
        if(t.original.chain()>=5&&action.equals("liberation"))values.put("damageBonus",1d);
        if(t.original.chain()>=6&&entropy){values.put("attackBonus",.60);values.put("fusionBonus",.60);}
        if(t.has("denia_entropy_dark"))values.merge("attackBonus",.30,Double::sum);
        if(t.has("denia_degenerate_duration"))values.put("fusionResistanceIgnore",t.time("denia_degenerate_layers")*.01);
        if(t.original.chain()>=3&&t.get("dark_core")>=5&&(action.equals("basic_04")||action.equals("skill"))) {t.set("dark_core",0);action+="_c3_full_core";}
        if(t.form.equals("disillusion")&&deniaNormalAttack(action)&&t.get("particles")>0)action+="_enhanced";
        t.action(action,values);
    }
    private static boolean deniaNormalAttack(String action){return action.contains("basic_")||action.contains("heavy")||action.equals("air")||action.equals("disillusion_air")||action.contains("counter");}
    private static boolean deniaFusion(Transition t){return t.original.mode().equals("fusion_burst")||t.original.mode().equals("fusion");}
    private static void deniaEntropy(Transition t,boolean dark) {
        t.clear(dark?"denia_entropy_stage":"denia_entropy_dark");t.timer(dark?"denia_entropy_dark":"denia_entropy_stage",dark?240:600);
        if(!t.has("denia_core_clock"))t.timer("denia_core_clock",t.original.chain()>=3?120:240);
        if(!dark)t.timer("denia_particle_clock",20);
        deniaEntropyAura(t,dark?240:600);
    }
    private static void deniaEntropyAura(Transition t,int duration) {
        t.effect(Kind.BUFF_TEAM,deniaFusion(t)?"denia_entropy_fusion":"denia_entropy_tune",1,duration,
            deniaFusion(t)?Map.of("fusionBonus",.30):Map.of("tuneBreakAmplification",10d,"perTenEfficiencyAboveBase",8d,"extraCap",40d));
    }
    static void deniaModeSelected(Transition t) {
        int remaining=Math.max(t.time("denia_entropy_dark"),t.time("denia_entropy_stage"));
        if(remaining>0)deniaEntropyAura(t,remaining);
    }
    static void deniaEnterCombat(Transition t) {
        if(!t.has("denia_entry_cd")&&(t.form.equals("normal")||t.original.chain()>=3)) {
            t.timer("denia_entry_cd",240);t.set("dark_core",Math.max(t.get("dark_core"),t.original.chain()>=3?5:2));
            t.set("particles",Math.max(t.get("particles"),t.original.chain()>=3?100:20));
        }
        if(t.original.chain()>=1)deniaEntropy(t,t.form.equals("disillusion"));
    }
    /** Called after actual teammate effect application. Target-owned 300s gating is host-side. */
    static void deniaTeamEffect(Transition t,boolean fusionEffect) {
        if(t.original.chain()<2||fusionEffect!=deniaFusion(t))return;
        t.effect(Kind.BUFF_TEAM,fusionEffect?"denia_c2_fusion_applier":"denia_c2_tune_applier",fusionEffect?.50:20,300,Map.of("applierOnly",1d,"endsOnModeChange",1d));
    }
    static void deniaFusionDetonation(Transition t) {if(deniaFusion(t)&&t.original.chain()>=2){t.timer("denia_degenerate_layers",Math.min(10,t.time("denia_degenerate_layers")+1));t.timer("denia_degenerate_duration",300);}}
    static void deniaModeChanged(Transition t){t.clear("denia_degenerate_layers");t.clear("denia_degenerate_duration");t.effect(Kind.REMOVE_FIELD,"denia_mode_bound_buffs",1,0);}

    static void qingxiao(Transition t,Input input) {
        boolean immortal=t.form.equals("immortal");
        if(!t.context.airborne()||immortal)t.clear("qingxiao_air_stage");
        switch(input) {
            case BASIC,BASIC_HOLD -> {
                if(input==Input.BASIC_HOLD&&((immortal&&t.get("sword_intent")>=100)||(!immortal&&t.get("qin_heart")>=100&&t.get("sword_rhyme")>=100))){qingxiao(t,Input.HEAVY_FULL);break;}
                if(immortal){int stage=Math.clamp(t.combo,1,4);qingxiaoAction(t,"immortal_basic_0"+stage+(t.get("sword_intent")<100?"_enhanced":""));t.combo=stage%4+1;qingxiaoFlight(t);}
                else if(t.context.airborne())qingxiaoAir(t,false);
                else {if(t.has("qingxiao_ground_a3")){t.combo=3;t.clear("qingxiao_ground_a3");}int stage=Math.clamp(t.combo,1,4);qingxiaoAction(t,"basic_0"+stage);t.combo=stage%4+1;if(stage==2||stage==3)t.timer("qingxiao_ascend_ready",60);}
            }
            case HEAVY_START -> t.timer("charging",1);
            case HEAVY_FULL,HEAVY_RELEASE -> {
                t.clear("charging");
                if(immortal) {
                    require(t.get("sword_intent")>=100,"sword_intent_missing");t.set("sword_intent",0);
                    qingxiaoAction(t,t.has("qingxiao_finisher_enhanced")?"heavy_banishing_enhanced":"heavy_banishing",Map.of("rateBonus",t.time("qingxiao_harmony")*.03,"extraTuneBuild",t.has("qingxiao_finisher_enhanced")?1d:0d));
                    t.clear("qingxiao_harmony");t.clear("qingxiao_finisher_enhanced");t.clear("qingxiao_clear_mind");form(t,"normal",0);qingxiaoFlight(t);
                } else {
                    require(t.get("qin_heart")>=100&&t.get("sword_rhyme")>=100,"qin_and_sword_missing");t.payStamina(30);t.set("qin_heart",0);t.set("sword_rhyme",0);
                    qingxiaoAction(t,t.original.chain()>=2?"heavy_c2":"heavy");form(t,"immortal",0);qingxiaoGuard(t,t.original.chain()>=1?2:1);qingxiaoFlight(t);
                    if(t.has("qingxiao_clear_mind")){t.timer("qingxiao_finisher_enhanced",Integer.MAX_VALUE);t.effect(Kind.MARK_AREA,"qingxiao_mind_lock",t.original.chain()>=2?6:3,0,Map.of("radius",8d));}
                    t.effect(Kind.MOVE_SPECIAL,"qingxiao_capture_nearby_locks",1,0,Map.of("storeHarmony",t.original.chain()>=3?1d:0d,"storeSwordCharges",t.original.chain()>=6?1d:0d));
                }
            }
            case AIR -> qingxiaoAir(t,false);
            case JUMP -> {require(t.has("qingxiao_flight"),"sword_flight_required");qingxiaoPlunge(t);}
            case DODGE -> {if(t.context.airborne()){t.payStamina(20);qingxiaoFlight(t);t.effect(Kind.MOVE_SPECIAL,"qingxiao_sword_dash",1,0);}}
            case DODGE_COUNTER -> {qingxiaoAction(t,immortal?"immortal_counter"+(t.get("sword_intent")<100?"_enhanced":""):"dodge_counter");if(immortal)qingxiaoFlight(t);else{t.combo=4;t.timer("qingxiao_ascend_ready",60);}}
            case SKILL,SKILL_HOLD -> {
                if(t.context.airborne()){qingxiaoAir(t,true);break;}
                if(t.has("qingxiao_ascend_ready")){qingxiaoAction(t,"skill_ascend");t.clear("qingxiao_ascend_ready");qingxiaoFlight(t);}
                else {t.requireCooldown("skill");t.timer("skill",400);qingxiaoAction(t,t.original.chain()>=5?"skill_punishment_c5":"skill_punishment");if(!immortal){t.add("qin_heart",45);if(t.has("qingxiao_golden_echo")){t.clear("qingxiao_golden_echo");t.add("qin_heart",30);}t.combo=4;}}
            }
            case LIBERATION -> {t.requireCooldown("liberation");t.payEnergy(125);t.timer("liberation",500);qingxiaoAction(t,"liberation",Map.of("criticalDamage",t.original.chain()>=3?1d:0d));t.timer("qingxiao_clear_mind",Integer.MAX_VALUE);}
            case INTRO -> {qingxiaoAction(t,"intro");if(!immortal)t.add("sword_rhyme",30);t.timer("qingxiao_golden_echo",Integer.MAX_VALUE);qingxiaoGuard(t,1);qingxiaoFlight(t);}
            case OUTRO -> t.effect(Kind.OUTRO_HIT,"qingxiao",8,0);
            case BASIC_RELEASE,SKILL_RELEASE,PERFECT_DODGE -> { }
            default -> throw new Rejected("unsupported_input_in_form");
        }
    }
    private static void qingxiaoAction(Transition t,String action){qingxiaoAction(t,action,Map.of());}
    private static void qingxiaoAction(Transition t,String action,Map<String,Double> extra){var values=new HashMap<>(extra);if(t.original.chain()>=6&&(action.equals("heavy")||action.equals("heavy_c2")||action.startsWith("heavy_banishing")||action.equals("liberation")||action.startsWith("vast_sword")))values.put("targetDamageBonus",.40);t.action(action,values);}
    private static void qingxiaoFlight(Transition t){if(!t.has("qingxiao_flight")){t.timer("qingxiao_flight",Integer.MAX_VALUE);t.clear("qingxiao_escape_used");}t.effect(Kind.MOVE_SPECIAL,"qingxiao_sword_flight",1,0,Map.of("movingCostMultiplier",t.original.chain()>=5?.70:1d));}
    static void qingxiaoExitFlight(Transition t){t.clear("qingxiao_flight");t.clear("qingxiao_escape_used");t.effect(Kind.MOVE_SPECIAL,"qingxiao_flight_end",1,0);}
    static void qingxiaoFlightReserve(Transition t,boolean nonempty){if(!nonempty&&t.has("qingxiao_flight"))qingxiaoExitFlight(t);}
    static void qingxiaoLongJump(Transition t){require(t.has("qingxiao_flight"),"sword_flight_required");require(!t.has("qingxiao_escape_used"),"flight_escape_already_used");t.requireCooldown("qingxiao_escape_cd");t.timer("qingxiao_escape_cd",600);t.timer("qingxiao_escape_used",Integer.MAX_VALUE);t.effect(Kind.MOVE_SPECIAL,"qingxiao_clear_escape",1,0);}
    private static void qingxiaoAir(Transition t,boolean forceThird){int stage=forceThird?3:Math.clamp(t.time("qingxiao_air_stage")+1,1,3);qingxiaoAction(t,"air_basic_0"+stage);t.timer("qingxiao_air_stage",stage%3);t.combo=stage%3+1;if(stage==3){qingxiaoExitFlight(t);t.timer("qingxiao_ground_a3",60);}else qingxiaoFlight(t);}
    private static void qingxiaoPlunge(Transition t){t.payStamina(30);qingxiaoAction(t,"air");qingxiaoExitFlight(t);t.effect(Kind.MOVE_SPECIAL,"tide_plunge",1,0);if(!t.form.equals("immortal"))t.timer("qingxiao_ground_a3",60);}
    private static void qingxiaoGuard(Transition t,int n){t.timer("qingxiao_guard",Math.min(t.original.chain()>=1?2:1,t.time("qingxiao_guard")+n));}
    static void qingxiaoEnterCombat(Transition t){t.timer("qingxiao_consciousness",Math.min(t.original.chain()>=2?25:15,t.time("qingxiao_consciousness")+1));t.timer("qingxiao_consciousness_life",600);t.timer("qingxiao_clear_mind",Integer.MAX_VALUE);if(t.original.chain()>=1)t.timer("qingxiao_sword_charges",25);}
    static void qingxiaoLockedKill(Transition t,int targetLayers,boolean directlyByTeam){if(directlyByTeam&&targetLayers>0&&targetLayers>=t.time("qingxiao_consciousness")){t.timer("qingxiao_consciousness",Math.min(t.original.chain()>=2?25:15,targetLayers));t.timer("qingxiao_consciousness_life",600);}}
    static void qingxiaoGuardHit(Transition t,boolean enemyMob){if(enemyMob&&t.time("qingxiao_guard")>0&&!t.has("qingxiao_guard_cd")){t.timer("qingxiao_guard",t.time("qingxiao_guard")-1);t.timer("qingxiao_guard_cd",20);t.effect(Kind.BUFF_SELF,"qingxiao_guard_reduction",.60,20,Map.of("immuneInterruption",1d));}}
    static void qingxiaoCaptureLocks(Transition t,int highest){if(t.original.chain()>=3)t.timer("qingxiao_harmony",highest);if(t.original.chain()>=6)t.timer("qingxiao_sword_charges",Math.min(25,t.time("qingxiao_sword_charges")+highest));}
    static void qingxiaoTuneBreak(Transition t,boolean grounded){if(grounded&&!t.form.equals("immortal")){t.combo=3;t.timer("qingxiao_ground_a3",60);}}

    /** Called once per accepted action's first actual hit, not once per raw damage row. */
    static void hit(Transition t,String action,ResourcePolicy ignored) {hit(t,action);}
    static void hit(Transition t,String action) {
        switch(t.original.characterId()) {
            case "changli" -> {if(action.equals("true_sight_conquest")||action.equals("true_sight_charge"))gainForte(t,1);}
            case "iuno" -> { /* Spirituality is awarded by audited hit/resource data only. */ }
            case "denia" -> deniaHit(t,action);
            case "qingxiao" -> qingxiaoHit(t,action);
            case "lupa" -> {
                if(action.equals("skill"))t.effect(Kind.MARK_TARGET,"lupa_encircle",1,160);
                if((action.equals("skill_ferocious")||action.equals("skill_ferocious_marked"))&&t.original.chain()>=6&&!t.has("lupa_c6_flame_cd")){t.add("wolf_flame",100);t.timer("lupa_c6_flame_cd",400);}
            }
            case "carlotta" -> {
                if(action.equals("skill")||action.equals("skill_c3"))t.effect(Kind.CONTROL_TARGET,"carlotta_variegation",1,30);
                if(action.equals("liberation")||action.startsWith("death_knell_")||action.equals("intro")||action.startsWith("skill_show_brilliance")||action.startsWith("heavy_end_of_road"))
                    t.effect(Kind.MARK_TARGET,"carlotta_dissociation",1,80,Map.of("ownerDefenseIgnore",.18,"ownerCritRate",t.original.chain()>=1?.125:0d));
                    }
            default -> { }
        }
    }
    /** Host must supply actual target's pre-hit variegation, never an owner approximation. */
    static void carlottaBrillianceHit(Transition t,boolean targetVariegated) {
        if(targetVariegated&&t.original.chain()>=1)substance(t,30);
    }
    private static void deniaHit(Transition t,String action) {
        // The native damage table owns per-hit particles/cost/conformal. Never
        // award a whole multi-hit action again in this once-per-action callback.
        action=action.replace("_enhanced","");
        boolean major=action.equals("intro")||action.equals("intro_disillusion")||action.equals("liberation")||action.startsWith("liberation_disillusion")||action.equals("erosion_field");
        boolean eligible=major||action.matches("(disillusion_(air_)?)?basic_0[34]")||action.equals("basic_04_c3_full_core");
        if(eligible)t.effect(Kind.MARK_TARGET,deniaFusion(t)?"fusion_burst":"tune_offset",deniaFusion(t)&&major?2:1,0,
            Map.of("sameSkillTargetIcd",40d,"sourceSkill",(double)deniaSourceSkill(action),"refreshInterference",deniaFusion(t)?0d:1d,"firstOffsetTuneFraction",t.original.chain()>=2?1d:.5,"firstOffsetTargetCooldown",6000d));
        if(t.original.chain()>=6&&action.equals("erosion_field")&&deniaFusion(t))t.effect(Kind.NATIVE_PROC,"denia_preserving_detonation",1,0,Map.of("mainTargetRateBonus",2d,"preserveStacks",1d,"oncePerTarget",1d));
        if(t.original.chain()>=6&&action.startsWith("liberation_disillusion")&&deniaFusion(t))t.effect(Kind.MARK_TARGET,"denia_reset_erosion_detonation",1,40);
    }
    private static int deniaSourceSkill(String action) {
        if(action.startsWith("liberation_disillusion"))return 4;
        return switch(action) {
            case "intro" -> 1;case "intro_disillusion" -> 2;case "liberation" -> 3;case "erosion_field" -> 5;
            case "basic_03" -> 6;case "basic_04","basic_04_c3_full_core" -> 7;
            case "disillusion_basic_03" -> 8;case "disillusion_basic_04" -> 9;
            case "disillusion_air_basic_03" -> 10;case "disillusion_air_basic_04" -> 11;
            default -> throw new IllegalArgumentException("not a Denia effect-applying skill: "+action);
        };
    }
    private static void qingxiaoHit(Transition t,String action) {
        // SpecialEnergy1 routes to qin_heart in normal form and sword_intent
        // in immortal form at native hit evaluation; no second grant here.
        t.effect(Kind.MARK_TARGET,"tune_offset",1,0,Map.of("oncePerSkillTarget",1d));
        if(t.original.chain()>=1&&t.time("qingxiao_sword_charges")>0&&!t.has("qingxiao_vast_sword_cd")&&(action.startsWith("basic_")||action.startsWith("air_basic_")||action.startsWith("immortal_basic_"))) {
            t.timer("qingxiao_sword_charges",t.time("qingxiao_sword_charges")-1);t.timer("qingxiao_vast_sword_cd",20);
            t.effect(Kind.NATIVE_PROC,t.original.chain()>=6?"qingxiao/vast_sword_c6":"qingxiao/vast_sword",1,0,Map.of("sameTargetDamageIncreasePerConsumedStack",.04,"duration",40d));
        }
    }
    /** Target-owned mind locks and one-time gates are intentionally not owner timers. */
    static void qingxiaoTeamHit(Transition t,boolean conscious) {
        if(conscious&&t.time("qingxiao_consciousness")>0)t.effect(Kind.MARK_TARGET,"qingxiao_mind_lock",t.time("qingxiao_consciousness"),0,Map.of("oncePerTarget",1d,"maxLayers",t.original.chain()>=2?25d:15d));
    }
    static void qingxiaoInterference(Transition t,boolean boss) {t.effect(Kind.MARK_TARGET,"qingxiao_mind_lock",boss?2:1,0);}
    static void qingxiaoOffset(Transition t) {if(t.original.chain()>=4)t.effect(Kind.BUFF_TEAM,"qingxiao_c4_applier",.20,160,Map.of("applierOnly",1d));}
    static void qingxiaoDeath(Transition t){t.clear("qingxiao_consciousness");t.clear("qingxiao_consciousness_life");qingxiaoExitFlight(t);}
    /** Applied at actual target damage evaluation, not from a nearest-target guess. */
    static Map<String,Double> qingxiaoTargetStats(MechanicsState state,String action,int layers) {
        int stacks=Math.clamp(layers,0,state.chain()>=2?25:15);
        boolean listed=action.equals("heavy")||action.equals("heavy_c2")||action.startsWith("immortal_basic_")||action.startsWith("immortal_counter")||action.startsWith("heavy_banishing")||action.equals("liberation")||(state.chain()>=6&&action.startsWith("vast_sword"));
        double value=listed?stacks*.02+Math.min(stacks,7)*.05:0;
        return Map.of("targetDamageBonus",value,"targetAmplification",value);
    }
    static void qingxiaoFirstTuneBreak(Transition t,boolean targetHasOffset) {
        if(targetHasOffset&&t.time("qingxiao_consciousness")>0)t.effect(Kind.MARK_TARGET,"tune_interference",t.original.chain()>=3?2:1,0,Map.of("oncePerTarget",1d));
    }
    static void deniaTeamTuneBreak(Transition t,boolean offsetDissonantTarget) {
        if(!deniaFusion(t)&&t.original.chain()>=6&&offsetDissonantTarget)t.effect(Kind.MARK_TARGET,"tune_interference",1,0,Map.of("sameTargetIcd",60d));
    }
    static void tick(Transition t) {
        if(t.original.characterId().equals("changli")) {
            int spent=t.time("changli_spent_charges");
            if(spent>0&&!t.has("changli_skill_recharge")){t.timer("changli_spent_charges",spent-1);if(spent>1)t.timer("changli_skill_recharge",240);}
        } else if(t.original.characterId().equals("denia")) {
            boolean entropy=t.has("denia_entropy_stage")||t.has("denia_entropy_dark");
            // Timers have already advanced. An interval that matures on the
            // effect's last tick still belongs to that elapsed interval.
            boolean coreDue=t.original.timer("denia_core_clock")==1
                &&(t.original.timer("denia_entropy_stage")>0||t.original.timer("denia_entropy_dark")>0);
            if((entropy||coreDue)&&!t.has("denia_core_clock")){t.add("dark_core",1);t.timer("denia_core_clock",t.original.chain()>=3?120:240);}
            if(!entropy)t.clear("denia_core_clock");
            boolean particleDue=t.original.timer("denia_particle_clock")==1&&t.original.timer("denia_entropy_stage")>0;
            if((t.has("denia_entropy_stage")||particleDue)&&!t.has("denia_particle_clock")){t.add("particles",t.original.chain()>=3?4:1);t.timer("denia_particle_clock",20);}
            if(!t.has("denia_entropy_stage"))t.clear("denia_particle_clock");
            if(!t.has("denia_degenerate_duration"))t.clear("denia_degenerate_layers");
        } else if(t.original.characterId().equals("qingxiao")) {
            if(!t.has("combo_window"))t.clear("qingxiao_air_stage");
            if(!t.context.airborne()){t.add("flight_qi",.50);t.clear("qingxiao_air_stage");}
            if(t.has("qingxiao_flight")) {
                if(!t.context.airborne()||t.get("flight_qi")<=0)qingxiaoExitFlight(t);
                else {
                    t.effect(Kind.STAMINA_REGEN_LOCK,"qingxiao_flight",1,1);
                    if(t.context.moving()){t.add("flight_qi",t.original.chain()>=5?-.35:-.50);if(t.get("flight_qi")<=0)qingxiaoExitFlight(t);}
                }
            }
            if(!t.has("qingxiao_consciousness_life"))t.clear("qingxiao_consciousness");
        } else if(t.original.characterId().equals("iuno")) {
            if(!t.form.equals("normal")) {
                if(!t.has("iuno_phase"))iunoEndPhase(t);
                else if(t.context.active()&&t.context.airborne()) {
                    t.effect(Kind.STAMINA_REGEN_LOCK,"iuno_phase",1,1);
                    if(t.context.moving()) {
                        if(t.context.stamina()>=.5)t.effect(Kind.PAY_STAMINA,"iuno_cruise",.5,0);
                        else iunoEndPhase(t);
                    }
                }
            }
        } else if(t.original.characterId().equals("lupa")) {
            if(t.get("wolf_soul")>0&&!t.has("lupa_soul_lifetime")){t.add("wolf_flame",t.get("wolf_soul")*50);t.set("wolf_soul",0);}
            if(t.original.timer("lupa_ferocious_ready")>0&&!t.has("lupa_ferocious_ready"))t.timer("skill",240);
            if(!t.has("lupa_hunt")){t.clear("lupa_hunt_tier");t.clear("lupa_pursuit_ready");t.clear("lupa_hunt_once");}
            if(!t.has("lupa_c2_duration"))t.clear("lupa_c2_stacks");
        } else if(t.original.characterId().equals("carlotta")) {
            if(t.original.timer("carlotta_show_brilliance")>0&&!t.has("carlotta_show_brilliance"))t.timer("skill",280);
            if(t.get("crystal")>0&&!t.has("carlotta_crystal_lifetime"))t.set("crystal",0);
            if(t.form.equals("gunflower")&&!t.has("carlotta_gunflower"))endDance(t);
            if(t.has("carlotta_plunging")&&!t.context.airborne()){t.clear("carlotta_plunging");t.timer("carlotta_courtesy",60);}
        }
    }
    static void switchOut(Transition t) {
        t.clear("charging");
        if(t.original.characterId().equals("carlotta")) {
            if(t.has("carlotta_show_brilliance")){t.clear("carlotta_show_brilliance");t.timer("skill",280);}
            if(t.form.equals("gunflower"))t.clear("carlotta_herald");
            t.clear("carlotta_safe_plunge");t.clear("carlotta_plunging");t.clear("carlotta_courtesy");
        }else if(t.original.characterId().equals("qingxiao")) {
            qingxiaoExitFlight(t);t.clear("qingxiao_air_stage");t.clear("qingxiao_finisher_enhanced");t.clear("qingxiao_ascend_ready");t.clear("qingxiao_ground_a3");
        }else if(t.original.characterId().equals("denia")) {
            t.clear("denia_exile_follow");
        }else if(t.original.characterId().equals("lupa")) {
            t.clear("lupa_sprint_clock");t.clear("lupa_sprint_starfall");
            if(t.has("lupa_ferocious_ready")){t.clear("lupa_ferocious_ready");t.timer("skill",240);}
            if(t.has("lupa_assist_ready")){t.timer("lupa_assist_waiting",t.time("lupa_assist_ready"));t.clear("lupa_assist_ready");}
            t.clear("lupa_break_enemy_ready");t.clear("lupa_sharp_claw_ready");t.clear("lupa_plunge_after_dodge");t.clear("lupa_basic2_followup");
        }else if(t.original.characterId().equals("iuno")) {
            t.effect(Kind.REMOVE_FIELD,"iuno_personal_shield_and_blessing",1,0);
        }else {t.clear("changli_charge_follow");t.clear("changli_lift_follow");t.clear("changli_plunge_follow");}
    }
    static MechanicsState leaveWorld(MechanicsState state) {
        var timers=new HashMap<>(state.timers());
        timers.remove("qingxiao_air_stage");
        Set.of("charging","changli_charge_follow","changli_lift_follow","changli_plunge_follow","carlotta_safe_plunge","carlotta_plunging","carlotta_courtesy","iuno_full_moon","iuno_bow_continuation","lupa_assist_ready","lupa_assist_waiting","lupa_intro_air","lupa_basic_air_ready","lupa_starfall","lupa_plunge_after_dodge","lupa_air3_cast","lupa_sprint_clock","lupa_sprint_starfall","denia_erosion","denia_exile_follow","qingxiao_flight","qingxiao_escape_used","qingxiao_ascend_ready","qingxiao_ground_a3","qingxiao_finisher_enhanced").forEach(timers::remove);
        return new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),state.nextBasic(),state.resources(),timers);
    }
}
