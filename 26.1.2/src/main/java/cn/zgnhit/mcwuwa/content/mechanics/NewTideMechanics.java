package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import static cn.zgnhit.mcwuwa.content.mechanics.MechanicsEngine.*;
import static cn.zgnhit.mcwuwa.content.mechanics.ExtendedMechanics.*;

/** Phoebe/Suisui/Cantarella source-defined transitions. World geometry and
 * per-target dreams are resolved by the host, not fabricated by input state. */
final class NewTideMechanics {
    static final Set<String> COUNTERS=Set.of("phoebe_glides","suisui_dance_count","suisui_dance_tier","cantarella_jellyfish_count","cantarella_revival_uses","cantarella_water_ticks");
    private NewTideMechanics(){}

    static void phoebe(Transition t,Input input){
        switch(input){
            case BASIC,BASIC_HOLD -> {
                if(t.context.airborne()){phoebeAir(t);break;}
                boolean ring=t.has("phoebe_inside_ring");int stage=Math.clamp(t.combo,1,3);
                combo(t,ring?"ring_":"",3);
                if(stage==3&&t.get("gospel")>0)t.timer("phoebe_starlight",Integer.MAX_VALUE);
            }
            case HEAVY_START -> t.timer("charging",1);
            case HEAVY_FULL,HEAVY_RELEASE -> {
                t.clear("charging");
                if(t.context.airborne()){
                    require(t.time("phoebe_glides")<2,"air_heavy_uses_exhausted");
                    t.payStamina(20);t.timer("phoebe_glides",t.time("phoebe_glides")+1);
                    t.clear("phoebe_staff_dive");t.action("air_glide");t.timer("phoebe_staff_glide",30);t.effect(Kind.MOVE_SPECIAL,"phoebe_staff_glide",1,30);break;
                }
                if(t.get("prayer")>=120&&t.get("gospel")==0){enterPhoebe(t,"absolution");break;}
                t.payStamina(25);
                if(t.has("phoebe_starlight")&&t.get("gospel")>0){
                    t.clear("phoebe_starlight");t.add("gospel",t.form.equals("absolution")?-15:-30);
                    t.action("heavy_starlight");
                    if(t.form.equals("confession"))mark(t,"spectro_frazzle",5,0);
                }else t.action("heavy");
            }
            case AIR -> phoebeAir(t);
            case DODGE -> {t.clear("phoebe_glides");t.clear("phoebe_staff_glide");t.clear("phoebe_staff_dive");}
            case DODGE_COUNTER -> {
                t.action(t.has("phoebe_inside_ring")?"ring_counter":"dodge_counter");
                if(t.get("gospel")>0)t.timer("phoebe_starlight",Integer.MAX_VALUE);
            }
            case SKILL_HOLD -> {
                require(t.get("prayer")>=120&&t.get("gospel")==0,"prayer_not_ready");enterPhoebe(t,"confession");
            }
            case SKILL -> {
                if(t.has("phoebe_teleport")){
                    t.clear("phoebe_teleport");t.action("skill_teleport");
                    t.effect(Kind.MOVE_SPECIAL,"phoebe_ring_teleport",1,0);break;
                }
                t.requireCooldown("skill");t.timer("skill",240);t.timer("phoebe_ring",600);
                t.timer("phoebe_teleport",60); // MC continuation window: source has no numeric value.
                t.action("skill");t.effect(Kind.SPAWN_FIELD,"phoebe_ring",1,600,Map.of("radius",4d));
                t.effect(Kind.CONTROL_AREA,"phoebe_ring_stasis",1,t.original.chain()>=6?80:40,Map.of("maxTargets",12d));
                if(t.original.chain()>=6&&!t.form.equals("normal")){
                    t.effect(Kind.NATIVE_PROC,"phoebe_c6_starlight",1,0);
                }
            }
            case LIBERATION -> {
                t.requireCooldown("liberation");t.payEnergy(125);t.timer("liberation",500);t.action("liberation");
                t.effect(Kind.GAIN_CONCERTO,"phoebe_liberation",20,0);
                if(t.form.equals("confession"))mark(t,t.original.chain()>=1?"fill_cap_spectro_frazzle":"spectro_frazzle",t.original.chain()>=1?1:8,0);
            }
            case INTRO -> {t.action("intro");t.effect(Kind.GAIN_CONCERTO,"phoebe_intro",10,0);}
            case OUTRO -> {
                t.effect(Kind.OUTRO_HIT,"phoebe",5.2841,0);
                if(t.form.equals("confession"))t.effect(Kind.BUFF_TEAM,"phoebe_silent_prayer",1,600,
                    Map.of("spectroResistanceReduction",.10,"frazzleAmplification",t.original.chain()>=2?2.2:1d,"frazzlePeriodMultiplier",1.5));
            }
            case BASIC_RELEASE,SKILL_RELEASE -> {}
            default -> throw new Rejected("unsupported_input_in_form");
        }
    }
    private static void enterPhoebe(Transition t,String state){
        t.set("prayer",0);t.set("gospel",60);t.clear("phoebe_starlight");form(t,state,0);t.action(state);
        mark(t,"spectro_frazzle",1,0);
        t.effect(Kind.GAIN_CONCERTO,"phoebe_"+state,state.equals("absolution")?10:40,0);
    }
    private static void phoebeAir(Transition t){t.payStamina(30);t.clear("phoebe_staff_glide");t.timer("phoebe_staff_dive",24);t.action("air");t.effect(Kind.MOVE_SPECIAL,"phoebe_staff_dive",1,24);}

    static void suisui(Transition t,Input input){
        boolean rain=t.form.equals("rain");
        switch(input){
            case BASIC,BASIC_HOLD -> {
                if(rain&&input==Input.BASIC_HOLD&&t.has("suisui_heavy_followup")){suisuiDescent(t,true);break;}
                if(t.context.airborne()){suisuiDescent(t,false);break;}
                combo(t,rain?"rain_":"",4);if(rain&&t.combo==1)consumeKingfisher(t);
            }
            case HEAVY_START -> {if(rain){t.payStamina(20);t.action("rain_heavy");t.timer("suisui_heavy_followup",60);}else combo(t,"",4);}
            case HEAVY_FULL -> {require(rain,"rain_form_required");if(t.has("suisui_heavy_followup"))suisuiDescent(t,true);else{t.payStamina(20);t.action("rain_heavy");t.timer("suisui_heavy_followup",60);}}
            case HEAVY_RELEASE -> {if(rain&&t.has("suisui_heavy_followup"))suisuiDescent(t,false);}
            case AIR -> suisuiDescent(t,false);
            case DODGE_COUNTER -> {if(rain){if(!t.has("suisui_cloud_counter"))t.combo=1;t.clear("suisui_cloud_counter");combo(t,"rain_",4);if(t.combo==1)consumeKingfisher(t);}else {t.clear("suisui_cloud_counter");t.action("dodge_counter");t.combo=4;}}
            case SKILL,SKILL_HOLD -> {
                if(!rain&&t.get("water_breath")>=120){
                    t.requireCooldown("suisui_awaken");t.timer("suisui_awaken",300);t.set("water_breath",0);
                    startRain(t);t.action("skill_awaken");suisuiMoisture(t);
                    t.effect(Kind.PULL_AREA,"suisui_awaken",1,1,Map.of("radius",6d,"centerOnOwner",1d));
                }else{
                    t.requireCooldown("skill");t.timer("skill",120);t.action(rain?"rain_skill":"skill");
                    if(t.context.active())t.add(rain?"fragrant_message":"water_breath",rain?100:40);
                    if(rain&&t.original.chain()>=3){t.timer("suisui_kingfisher",Integer.MAX_VALUE);t.combo=4;}
                }
            }
            case LIBERATION -> {
                t.requireCooldown("liberation");t.payEnergy(175);t.timer("liberation",500);t.timer("suisui_domain",600);
                t.action("liberation");t.effect(Kind.SPAWN_FIELD,"suisui_landscape",1,600,Map.of("radius",12d));
                t.effect(Kind.GAIN_CONCERTO,"suisui_liberation",20,0);
            }
            case INTRO -> {t.set("water_breath",0);startRain(t);t.action("intro");suisuiMoisture(t);t.effect(Kind.PULL_AREA,"suisui_intro",1,1,Map.of("radius",6d,"centerOnOwner",1d));t.effect(Kind.GAIN_CONCERTO,"suisui_intro",10,0);}
            case OUTRO -> {
                double spent=t.get("fragrant_message");t.set("fragrant_message",0);endRain(t);
                t.effect(Kind.BUFF_TEAM,"suisui_outro_amplification",.25,600);
                t.effect(Kind.BUFF_TEAM,"suisui_chongming",1,120);
                if(t.has("suisui_domain")){
                    t.timer("suisui_immortal",600);t.timer("suisui_dance_tier",(int)(spent/200));
                    t.clear("suisui_dance_count");t.timer("suisui_dance_clock",100);
                    t.effect(Kind.MOVE_SPECIAL,"suisui_reserve_dance",spent,Math.min(600,t.time("suisui_domain")));
                }
            }
            case PERFECT_DODGE -> {require(t.has("suisui_cloud_evade"),"cloud_evade_not_active");t.clear("suisui_cloud_evade");t.timer("suisui_cloud_counter",20);t.add(rain?"fragrant_message":"water_breath",rain?102:35);}
            case BASIC_RELEASE,SKILL_RELEASE,DODGE -> {}
            default -> throw new Rejected("unsupported_input_in_form");
        }
    }
    private static void consumeKingfisher(Transition t){
        // C3 explicitly triggers when A4 is cast, including a miss. Only its
        // Frost application belongs to the subsequent successful hit callback.
        if(t.original.chain()>=3&&t.has("suisui_kingfisher")&&!t.has("suisui_kingfisher_cd")){
            t.clear("suisui_kingfisher");t.timer("suisui_kingfisher_cd",500);
            t.add("fragrant_message",350);t.effect(Kind.GAIN_CONCERTO,"suisui_c3",20,0);
        }
    }
    private static void startRain(Transition t){t.set("fragrant_message",0);form(t,"rain",300);t.timer("suisui_rain",300);t.clear("suisui_heavy_followup");}
    private static void endRain(Transition t){t.set("fragrant_message",0);t.clear("suisui_rain");t.clear("suisui_heavy_followup");if(t.form.equals("rain"))form(t,"normal",0);}
    private static void suisuiDescent(Transition t,boolean held){
        t.payStamina(30);t.clear("suisui_heavy_followup");t.action(t.form.equals("rain")?(held?"rain_descent_hold":"rain_descent"):"air");
        t.effect(Kind.MOVE_SPECIAL,"tide_plunge",1,0);
    }
    private static void suisuiMoisture(Transition t){
        if(!t.has("suisui_moisture_cooldown")){t.timer("suisui_moisture_cooldown",500);t.effect(Kind.BUFF_TEAM,"suisui_moisture",1,Integer.MAX_VALUE);}
    }

    static void cantarella(Transition t,Input input){
        boolean mirage=t.form.equals("mirage");
        switch(input){
            case BASIC,BASIC_HOLD -> {if(mirage&&t.context.airborne())t.payStamina(5);combo(t,mirage?"mirage_":"",3);}
            case HEAVY_START -> t.timer("charging",1);
            case HEAVY_FULL,HEAVY_RELEASE -> {
                t.clear("charging");t.payStamina(20);
                if(t.get("trance")>0){startMirage(t);t.action("heavy_mirage");t.timer("cantarella_surf",30);t.effect(Kind.MOVE_SPECIAL,"cantarella_surf",1,30);}
                else t.action("heavy");
            }
            case AIR -> {t.payStamina(30);t.clear("cantarella_surf");t.action(mirage?"mirage_air":"air");t.effect(Kind.MOVE_SPECIAL,"tide_plunge",1,0);}
            case JUMP -> {require(mirage&&t.context.airborne(),"mirage_air_required");t.payStamina(30);t.clear("cantarella_surf");t.action("mirage_air");t.effect(Kind.MOVE_SPECIAL,"tide_plunge",1,0);}
            case DODGE_COUNTER -> {t.action(mirage?"mirage_counter":"dodge_counter");if(mirage)t.combo=2;}
            case SKILL,SKILL_HOLD -> {
                String cd=mirage?(t.get("shiver")>=3?"cantarella_drain":"cantarella_dream"):"skill";
                t.requireCooldown(cd);t.timer(cd,mirage?(t.get("shiver")>=3?360:240):120);
                if(mirage){
                    boolean drain=t.get("shiver")>=3;if(drain)t.set("shiver",0);
                    t.action(drain?"skill_drain":"skill_dream");cantarellaEchoCast(t,drain?1607014:1607210);
                    if(drain)healTeam(t,"cantarella_drain",712,1.89);
                    t.effect(Kind.GAIN_CONCERTO,"cantarella_skill",drain?12:10,0);
                }else{t.action("skill");t.add("trance",1);t.effect(Kind.GAIN_CONCERTO,"cantarella_skill",10,0);}
                if(t.original.chain()>=1)t.add("trance",1);
            }
            case LIBERATION -> {
                t.requireCooldown("liberation");t.payEnergy(125);t.timer("liberation",500);t.action("liberation");t.add("trance",3);
                t.timer("cantarella_diffusion",600);t.clear("cantarella_jellyfish_count");t.clear("cantarella_jellyfish_clock");
                t.effect(Kind.GAIN_CONCERTO,"cantarella_liberation",20,0);cantarellaEchoCast(t,1607301);
                if(t.original.chain()>=3)startMirage(t);
            }
            case INTRO -> {
                t.add("trance",1);t.action(mirage?"mirage_intro":"intro");t.combo=mirage?1:3;
                t.effect(Kind.GAIN_CONCERTO,"cantarella_intro",10,0);
                if(!t.has("cantarella_revival_cd")){
                    t.timer("cantarella_revival_cd",500);t.timer("cantarella_revival",500);t.timer("cantarella_revival_uses",6);
                }
            }
            case OUTRO -> t.effect(Kind.BUFF_NEXT,"cantarella_outro",.20,280,Map.of("skillAmplification",.25,"endsOnSwitch",1d));
            case BASIC_RELEASE,SKILL_RELEASE,DODGE -> {}
            default -> throw new Rejected("unsupported_input_in_form");
        }
    }
    private static void cantarellaEchoCast(Transition t,int source){t.effect(Kind.ECHO_CAST,"cantarella_virtual_echo",source,0);}
    private static void endMirage(Transition t){t.clear("cantarella_mirage");form(t,"normal",0);t.combo=1;}
    private static void startMirage(Transition t){if(!t.form.equals("mirage")){form(t,"mirage",160);t.timer("cantarella_mirage",160);}}

    static void hit(Transition t,String action){
        switch(t.original.characterId()){
            case "phoebe" -> {
                if(action.equals("intro"))t.effect(Kind.PUSH_AREA,"phoebe_intro",.35,0,Map.of("radius",4d));
                if(t.original.chain()>=4&&(action.contains("basic_")||action.contains("counter")))t.effect(Kind.MARK_TARGET,"phoebe_c4_spectro_shred",.10,600);
            }
            case "suisui" -> {
                if(action.equals("intro")||action.equals("skill_awaken")){
                    mark(t,"glacio_chafe",1,0);
                    if(!t.has("suisui_inherent_hit_cd")){t.timer("suisui_inherent_hit_cd",500);t.effect(Kind.GAIN_CONCERTO,"suisui_inherent",18,0);t.effect(Kind.GAIN_ENERGY,"suisui_inherent",13,0);}
                }
                if(action.equals("rain_basic_04")){
                    mark(t,"glacio_chafe",1,0);
                }
            }
            case "cantarella" -> {
                if(action.equals("basic_03"))t.add("trance",1);
                if(action.startsWith("mirage_basic_")||action.equals("mirage_air")||action.equals("mirage_counter")){
                    if(t.get("trance")>0){t.add("trance",-1);t.add("shiver",1);t.effect(Kind.HEAL_TEAM,"cantarella_trance",171+.4536*t.context.attack(),0,Map.of("sourceHealingBonus",t.get("trance")==0&&t.original.chain()>=4?.25:0d));}
                    if(t.get("trance")==0)endMirage(t);
                }
                if(action.equals("skill_dream")||action.equals("skill_drain")||(action.equals("liberation")&&t.original.chain()>=2))
                    t.effect(Kind.MARK_TARGET,"cantarella_dream",1,130,Map.of("graceTicks",t.original.chain()>=6?24d:0d));
            }
            default -> {}
        }
    }
    static void tick(Transition t){
        switch(t.original.characterId()){
            case "phoebe" -> {
                t.add("prayer",.25);
                if(!t.context.airborne())t.clear("phoebe_glides");
                if(!t.has("phoebe_ring")){t.clear("phoebe_inside_ring");t.clear("phoebe_teleport");}
            }
            case "suisui" -> {
                if(t.form.equals("rain")&&!t.has("suisui_rain"))endRain(t);
                if(!t.has("suisui_domain")){t.clear("suisui_immortal");t.clear("suisui_dance_tier");}
                // The text gives no numeric dance interval: 5 seconds is an
                // explicitly authored MC schedule, not an original frame claim.
                if(t.has("suisui_immortal")&&!t.context.active()){
                    if(!t.has("suisui_dance_clock")&&t.time("suisui_dance_count")<t.time("suisui_dance_tier")){
                        int count=t.time("suisui_dance_count")+1;t.timer("suisui_dance_count",count);t.timer("suisui_dance_clock",100);
                        t.effect(Kind.BUFF_TEAM,"suisui_chongming",1,count==3&&t.original.chain()>=1?240:120);
                        t.effect(Kind.HEAL_ACTIVE,"suisui_dance",1045+.057*t.context.maxHealthNative(),0);
                        t.effect(Kind.MARK_AREA,"glacio_chafe",1,0,Map.of("radius",12d,"suisuiDomain",1d));
                        if(count==1&&t.time("suisui_dance_tier")>=3)t.effect(Kind.BUFF_NEXT,"suisui_smoke_next",1,Math.min(t.time("suisui_immortal"),t.time("suisui_domain")),Map.of("suisuiDomain",1d));
                    }
                }else if(t.has("suisui_immortal"))t.timer("suisui_dance_clock",t.original.timer("suisui_dance_clock"));
            }
            case "cantarella" -> {if(t.form.equals("mirage")&&!t.has("cantarella_mirage"))endMirage(t);}
            default -> {}
        }
    }
    static MechanicsState leaveWorld(MechanicsState state){
        var timers=new HashMap<>(state.timers());
        var fields=switch(state.characterId()){
            case "phoebe" -> Set.of("phoebe_ring","phoebe_inside_ring","phoebe_teleport","phoebe_staff_glide","phoebe_staff_dive","phoebe_glides");
            case "suisui" -> Set.of("suisui_domain","suisui_immortal","suisui_dance_tier","suisui_dance_count","suisui_dance_clock");
            case "cantarella" -> Set.of("cantarella_diffusion","cantarella_jellyfish_count","cantarella_jellyfish_clock","cantarella_surf");
            default -> Set.<String>of();
        };
        fields.forEach(timers::remove);
        return new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),state.nextBasic(),state.resources(),timers);
    }

    static void switchOut(Transition t){
        if(t.original.characterId().equals("suisui")){t.clear("suisui_kingfisher");t.clear("suisui_heavy_followup");t.clear("suisui_cloud_evade");t.clear("suisui_cloud_counter");}
        if(t.original.characterId().equals("phoebe")){t.clear("charging");t.clear("phoebe_glides");}
    }
}
