package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.*;

/** Audited inherent/chain numeric modifiers. Summons/revival use their separate platform executors.
 * Coefficient increases, bonus damage, amplification and target vulnerability are separate outputs.
 * Rules already executed by MechanicsEngine (Jinhsi C1, Mornye shields etc.) are not duplicated here.
 */
public final class RolePassiveEngine {
    public enum Trigger { CAST, HIT, ECHO_CAST, APPLY_ANOMALY, TEAM_APPLY_ANOMALY, APPLY_MARK, SWITCH_OUT, ENTER_COMBAT, EXIT_COMBAT, UNISON, SPAWN_READY, RESOURCE_GAIN, RESOURCE_RESET }
    public enum Scope { SELF, OWN_TEAM }
    public record Buff(String id, Scope scope, String attribute, double value, long expires, boolean endsOnSwitch) { }
    public record State(Map<String,Buff> buffs,Map<String,Integer> stacks,Map<String,Long> ready,Set<String> contributors) {
        public State {buffs=Map.copyOf(buffs);stacks=Map.copyOf(stacks);ready=Map.copyOf(ready);contributors=Set.copyOf(contributors);}
        public static State empty(){return new State(Map.of(),Map.of(),Map.of(),Set.of());}
    }
    public record Context(String character,int chain,String mode,String form,Map<String,Double> resources,
                          Map<String,Integer> timers,double energyRegen,boolean active,int havocLayers,int aeroLayers,int electroLayers,
                          boolean yinlinMarked,boolean chisaMarked,boolean mornyeMarked,int unisonStacks) {
        public Context {resources=Map.copyOf(resources);timers=Map.copyOf(timers);if(chain<0||chain>6)throw new IllegalArgumentException("chain");}
        public double resource(String key){return resources.getOrDefault(key,0d);}
        public boolean timer(String key){return timers.getOrDefault(key,0)>0;}
    }
    public record Event(Trigger trigger,long tick,String action,String kind,String effect,String actorIdentity,boolean ownerActor) {
        public Event {if(tick<0)throw new IllegalArgumentException("negative tick");}
    }
    public record Modifiers(Map<String,Double> attributes,double coefficientIncrease,double amplification,double targetVulnerability) {
        public Modifiers {attributes=Map.copyOf(attributes);}
    }
    /** Only mode-scoped inherent rewards end; generic C4 team buffs and cooldowns remain. */
    public State modeChanged(State state,String character) {
        if(!Set.of("aemeath","hsin").contains(character))return state;
        var buffs=new HashMap<>(state.buffs());var stacks=new HashMap<>(state.stacks());var contributors=new HashSet<>(state.contributors());
        String prefix=character.equals("aemeath")?"aemeath_stars_":"hsin_inherent4_";
        buffs.keySet().removeIf(key->key.startsWith(prefix));stacks.remove(character.equals("aemeath")?"aemeath_stars":"hsin_electro_contributors");
        contributors.removeIf(key->key.startsWith(character+":"));return new State(buffs,stacks,state.ready(),contributors);
    }
    public State onEvent(State state,Context context,Event event) {
        Work w=new Work(state,event.tick());String action=shortAction(event.action());int c=context.chain();boolean cast=event.trigger()==Trigger.CAST;
        if(event.trigger()==Trigger.SWITCH_OUT)w.buffs.values().removeIf(Buff::endsOnSwitch);
        if(event.ownerActor())switch(context.character()) {
            case "cantarella" -> {
                if(event.trigger()==Trigger.ECHO_CAST){
                    int stacks=w.buffs.containsKey("cantarella_poison")?w.stack("cantarella_poison",1,2):1;
                    w.stacks.put("cantarella_poison",stacks);w.buff("cantarella_poison",Scope.SELF,"havoc_bonus",.06*stacks,200,false);
                }
                if(c>=6&&cast&&action.equals("liberation"))w.buff("cantarella_c6",Scope.SELF,"defense_ignore",.30,200,false);
            }
            case "phoebe" -> {
                if(c>=5&&cast&&action.equals("intro"))w.buff("phoebe_c5",Scope.SELF,"spectro_bonus",.12,300,false);
                if(c>=6&&cast&&action.equals("skill")&&!context.form().equals("normal"))w.buff("phoebe_c6",Scope.SELF,"attack_pct",.10,400,false);
            }
            case "ciaccona" -> {
                if(c>=1&&cast&&action.startsWith("basic_"))w.buff("ciaccona_c1_attack",Scope.SELF,"attack_pct",.35,200,false);
            }
            case "aemeath" -> {
                if(c>=4&&cast&&(action.startsWith("intro")||action.startsWith("wing_ensemble")||Set.of("skill","skill_dawn").contains(action)))w.buff("aemeath_c4",Scope.OWN_TEAM,"all_damage_bonus",.20,600,false);
            }
            case "lynae" -> {
                if(c>=6&&cast&&(action.equals("cruise_jump_bundle")||action.equals("cruise_air_heavy")))w.stack("lynae_heart_color",1,3);
                if(c>=6&&cast&&(action.equals("visual_impact")||action.equals("rainbow_splash"))) {
                    w.buff("lynae_heart_color_attack",Scope.SELF,"impact_vulnerability",.30*w.stacks.getOrDefault("lynae_heart_color",0),100,false);
                    w.stacks.remove("lynae_heart_color");
                }
            }
            case "jinhsi" -> {
                if(c>=3&&cast&&action.equals("intro")) {int n=w.stack("jinhsi_exiled",1,2);w.buff("jinhsi_c3",Scope.SELF,"attack_pct",.25*n,400,false);}
                if(c>=4&&cast&&(action.equals("liberation")||action.startsWith("epiphany")))w.buff("jinhsi_c4",Scope.OWN_TEAM,"all_damage_bonus",.20,400,false);
            }
            case "camellya" -> {
                if(cast&&action.equals("intro")) {
                    if(c>=1&&w.ready("camellya_c1")){w.cooldown("camellya_c1",500);w.buff("camellya_c1",Scope.SELF,"crit_damage",.28,360,false);}
                    if(c>=4)w.buff("camellya_c4",Scope.OWN_TEAM,"basic_bonus",.25,600,false);
                }
            }
            case "yinlin" -> {
                if(cast&&action.equals("skill"))w.buff("yinlin_inherent4",Scope.SELF,"crit_rate",.15,100,false);
                if(event.trigger()==Trigger.HIT&&action.equals("skill_blast")&&context.timer("target_yinlin_sin"))w.buff("yinlin_inherent5",Scope.SELF,"attack_pct",.10,80,false);
                if(c>=4&&event.trigger()==Trigger.HIT&&action.equals("judgment_thunder"))w.buff("yinlin_c4",Scope.OWN_TEAM,"attack_pct",.20,240,false);
            }
            case "aero_rover" -> {
                if(cast&&action.equals("intro"))w.buff("rover_inherent4",Scope.SELF,"attack_pct",.20,200,false);
                if(c>=4&&cast&&action.startsWith("wind_air"))w.buff("rover_c4",Scope.SELF,"skill_bonus",.15,100,false);
            }
            case "cartethyia" -> {
                if(c>=1&&(context.form().equals("fleurdelys")||action.equals("liberation_finale"))) {
                    int threshold=(int)(context.resource("resolve")/30);int old=w.stacks.getOrDefault("cartethyia_resolve",0);
                    // A fresh paid transformation resets Conviction. Its new
                    // threshold crossings must not inherit the previous cycle.
                    if(threshold<old){old=0;w.stacks.remove("cartethyia_resolve");}
                    // A threshold is a gain event, not a permanent layer slot.
                    // Across paid cycles an older30-point layer may still be
                    // alive; the new crossing must occupy another free slot.
                    for(int crossing=old+1;crossing<=threshold&&crossing<=4;crossing++)
                        for(int slot=1;slot<=4;slot++)if(!w.buffs.containsKey("cartethyia_c1_"+slot)){
                            w.buff("cartethyia_c1_"+slot,Scope.SELF,"crit_damage",.25,300,false);break;
                        }
                    w.stacks.put("cartethyia_resolve",Math.max(old,threshold));
                    if(cast&&action.equals("liberation_finale")) {
                        double current=w.buffs.values().stream().filter(b->b.id().startsWith("cartethyia_c1_")).mapToDouble(Buff::value).sum();
                        w.buff("cartethyia_finale_crit",Scope.SELF,"finale_crit_damage",current,100,false);
                        w.buffs.keySet().removeIf(k->k.startsWith("cartethyia_c1_"));w.stacks.remove("cartethyia_resolve");
                    }
                }
            }
            case "yangyang_xuanling" -> {
                if(c>=4&&cast&&(action.startsWith("intro")||action.startsWith("skill")||action.startsWith("flow_")))w.buff("yangyang_c4",Scope.OWN_TEAM,"attack_pct",.20,400,false);
                if(c>=6&&event.trigger()==Trigger.APPLY_ANOMALY&&event.effect().equals("havoc_bane"))w.buff("yangyang_c6",Scope.SELF,"heavy_vulnerability",.40,600,false);
            }
            case "hsin" -> {
                if(context.mode().equals("unison")&&cast&&action.endsWith("full_unison"))w.buff("hsin_inherent4_unison",Scope.SELF,"attack_pct",.50,160,true);
            }
            case "hiyuki" -> {
                if(c>=4&&cast&&action.startsWith("skill"))w.buff("hiyuki_c4",Scope.OWN_TEAM,"all_damage_bonus",.20,600,false);
                if(c>=3&&event.trigger()==Trigger.SPAWN_READY&&w.contributors.add("hiyuki:spawn")){int count=w.stack("snow_rust",1,3);w.buff("hiyuki_rust_crit",Scope.SELF,"crit_damage",.40+(c>=6&&count>=2?.40:0),0,false);}
            }
            case "chisa" -> {
                if(cast&&(action.equals("intro")||action.equals("liberation"))){w.buff("chisa_inherent5_havoc",Scope.SELF,"havoc_bonus",.20,240,false);w.buff("chisa_inherent5_heal",Scope.SELF,"healing_bonus",.20,240,false);}
                if(c>=1&&event.trigger()==Trigger.APPLY_MARK&&event.effect().equals("chisa_void_scar"))w.buff("chisa_c1",Scope.SELF,"attack_pct",.30,300,false);
            }
            case "phrolova" -> {
                if(event.trigger()==Trigger.ECHO_CAST){w.buff("phrolova_inherent4",Scope.SELF,"damage_reduction",.30,300,false);if(c>=4)w.buff("phrolova_c4",Scope.OWN_TEAM,"all_damage_bonus",.20,600,false);}
            }
            default -> { }
        }
        boolean anomaly=event.trigger()==Trigger.APPLY_ANOMALY||event.trigger()==Trigger.TEAM_APPLY_ANOMALY;
        if(anomaly||event.trigger()==Trigger.UNISON||RuptureDamageRules.isPassiveEvent(event))switch(context.character()) {
            case "aemeath" -> {
                boolean fusion=context.mode().equals("fusion");boolean matches=fusion?event.effect().equals("fusion_burst"):event.effect().equals("rupture_shifting")||event.effect().equals("rupture_damage");
                if(matches&&w.contributors.add("aemeath:"+event.actorIdentity())) {
                    int cap=fusion?2:3;int count=w.stack("aemeath_stars",1,cap);double crit=c>=3?.60:(fusion?.30:.20)*count;
                    w.buff("aemeath_stars_crit",Scope.SELF,"crit_damage",crit,0,false);
                    if(c>=3||count>=cap)w.buff("aemeath_stars_finale",Scope.SELF,"finale_amplification",.25,0,false);
                }
            }
            case "cartethyia" -> {if(c>=4&&anomaly&&SoloEffectRules.ANOMALIES.contains(event.effect()))w.buff("cartethyia_c4",Scope.OWN_TEAM,"all_damage_bonus",.20,400,false);}
            case "hsin" -> {
                if(context.mode().equals("electro")&&event.effect().equals("electro_flare")&&w.contributors.add("hsin:"+event.actorIdentity())) {
                    int count=w.stack("hsin_electro_contributors",1,2);w.buff("hsin_inherent4_electro",Scope.SELF,"electro_bonus",.25*count,0,false);
                }
                if(c>=4&&(event.effect().equals("electro_flare")||event.effect().equals("electro_rage")||event.trigger()==Trigger.UNISON))w.buff("hsin_c4",Scope.OWN_TEAM,"all_damage_bonus",.20,600,false);
            }
            case "hiyuki" -> {
                if((event.effect().equals("glacio_chafe")||event.effect().equals("glacio_frost")||event.effect().equals("havoc_bane"))&&w.contributors.add("hiyuki:"+event.actorIdentity())) {
                    int count=w.stack("snow_rust",1,3);w.buff("hiyuki_rust_crit",Scope.SELF,"crit_damage",.40+(c>=6&&count>=2?.40:0),0,false);
                }
            }
            default -> { }
        }
        return w.finish();
    }

    public Modifiers modifiers(Context c,State state,long now,String fullAction,String kind,String element) {
        String a=shortAction(fullAction);int chain=c.chain();Map<String,Double> stats=new HashMap<>();double rate=0,amp=1,vulnerable=1;
        for(Buff b:active(state,now))if(b.scope()==Scope.SELF) {
            if(b.id().startsWith("cartethyia_c1_")&&!c.form().equals("fleurdelys"))continue;
            if(b.attribute().equals("finale_amplification")){if(a.equals("liberation_finale"))amp+=b.value();}
            else if(b.attribute().equals("finale_crit_damage")){if(a.equals("liberation_finale"))stats.merge("crit_damage",b.value(),Double::sum);}
            else if(b.attribute().equals("impact_vulnerability")){if(a.equals("visual_impact")||a.equals("rainbow_splash"))vulnerable*=1+b.value();}
            else if(b.attribute().equals("heavy_vulnerability")){if(kind.equals("heavy"))vulnerable*=1+b.value();}
            else stats.merge(b.attribute(),b.value(),Double::sum);
        }
        switch(c.character()) {
            case "changli" -> {
                // The C5 source row includes its coefficient increase only.
                // Its separately worded damage bonus is not another coefficient.
                if(chain>=5&&a.startsWith("heavy_flaming_sacrifice"))add(stats,"all_damage_bonus",.50);
                // Buff1205900102 also lists both True Sight skill families;
                // their action snapshots do not contain the C1 damage bonus.
                if(chain>=1&&a.startsWith("true_sight_"))add(stats,"all_damage_bonus",.10);
                // C1/C3/C6 and inherent action bonuses are action snapshots;
                // C2 gain/C4 team buffs are emitted by NewSixMechanics.
            }
            case "carlotta" -> {
                // The host sets this only for a mark owned by this Carlotta.
                if(c.timer("target_carlotta_dissociation")) {
                    add(stats,"defense_ignore",.18);
                    if(chain>=1)add(stats,"crit_rate",.125);
                }
                // Herald coefficient and C2/C3/C5/C6 replacement damage rows
                // are selected before this evaluator, never multiplied twice.
            }
            case "iuno" -> {
                // C1 is a phase-wide attack stat: it also scales healing,
                // shields and echo hits. Do not add it again to cast snapshots.
                if(chain>=1&&!c.form().equals("normal"))add(stats,"attack_pct",.40);
                if(chain>=5)add(stats,"liberation_bonus",.20);
            }
            case "lupa" -> {
                // Numeric action qualifiers and explicit timed/team buffs live
                // in NewSixMechanics; there is no second generic chain bonus.
            }
            case "denia" -> {
                if(chain>=1)add(stats,"crit_damage",.30);
                if(c.mode().equals("strain")||c.mode().equals("tune")) {
                    int layers=Math.max(0,c.timers().getOrDefault("target_tune_interference",0));
                    double breakAmp=Math.max(0,c.resources().getOrDefault("passive_tune_break_amplification",0d));
                    add(stats,"final_damage",layers*breakAmp*.0012);
                }
                // Entropy, degenerate resistance ignore and C5 damage are
                // action qualifiers. C2/C6 target event effects are host-owned.
            }
            case "qingxiao" -> {
                if(chain>=1)add(stats,"crit_rate",.16);
                int locks=Math.clamp(c.timers().getOrDefault("target_qingxiao_mind_lock",0),0,chain>=2?25:15);
                boolean listed=a.equals("heavy")||a.equals("heavy_c2")||a.startsWith("immortal_basic_")||a.startsWith("immortal_counter")||a.startsWith("heavy_banishing")||a.equals("liberation")
                    ||chain>=6&&a.startsWith("vast_sword");
                double targetIncrease=0;
                if(listed) {
                    double each=locks*.02+Math.min(locks,7)*.05;
                    // Inherent5 is DMG Bonus (ExtraEffect1 / parameter15),
                    // while Forte Mindlock is target DMG taken (ExtraEffect38).
                    // Both apply, but the inherent must share the normal bonus zone.
                    add(stats,"all_damage_bonus",each);targetIncrease+=each;
                }
                if(chain>=6&&(a.equals("heavy")||a.equals("heavy_c2")||a.startsWith("heavy_banishing")||a.equals("liberation")||a.startsWith("vast_sword")))targetIncrease+=.40;
                if(chain>=1&&a.startsWith("vast_sword"))targetIncrease+=.04*Math.max(0,c.timers().getOrDefault("target_qingxiao_vast_sword_exposure",0));
                vulnerable*=1+targetIncrease;
                int layers=Math.max(0,c.timers().getOrDefault("target_tune_interference",0));
                double breakAmp=Math.max(0,c.resources().getOrDefault("passive_tune_break_amplification",0d));
                add(stats,"final_damage",layers*breakAmp*.0012*(chain>=6?1.20:1));
                // C3 R crit damage and dynamic finisher snapshots are action-owned.
            }
            case "phoebe" -> {
                boolean absolution=c.form().equals("absolution"),confession=c.form().equals("confession");
                if(absolution||confession)add(stats,"spectro_bonus",.12);
                if(a.equals("liberation")){if(absolution)rate+=chain>=1?4.8:2.55;else if(confession&&chain>=1)rate+=.90;}
                if(a.equals("heavy_starlight")){
                    if(chain>=3)rate+=absolution?.91:confession?2.49:0;
                    if(absolution&&c.timer("target_spectro_frazzle"))amp+=2.56;
                }
                if(a.equals("outro")&&absolution){rate+=2.55;if(chain>=2&&c.timer("target_spectro_frazzle"))amp+=1.20;}
            }
            case "suisui" -> {
                if(a.equals("intro")||a.equals("skill_awaken")){
                    if(!c.timer("suisui_inherent_hit_cd")){add(stats,"crit_rate",.80);add(stats,"glacio_bonus",2.40);}
                    if(chain>=6)add(stats,"crit_damage",5);
                }
                if(chain>=5&&(a.startsWith("rain_basic_")||a.equals("rain_heavy")))rate+=1;
            }
            case "cantarella" -> {
                add(stats,"healing_bonus",.20);
                if(chain>=4&&c.form().equals("mirage"))add(stats,"healing_bonus",.25);
                if(chain>=1&&Set.of("skill","skill_dream","skill_drain").contains(a))rate+=.50;
                if(chain>=2&&a.equals("awaken"))rate+=2.45;
                if(chain>=3&&a.equals("liberation"))rate+=3.70;
                if(chain>=6&&a.startsWith("mirage_basic_"))rate+=.80;
            }
            case "shorekeeper" -> {
                if(a.equals("intro_insight")){add(stats,"crit_rate",1);if(chain>=6){rate+=.42;add(stats,"crit_damage",5);}}
            }
            case "sigrika" -> {
                if(chain>=1&&Set.of("basic_insight","counter_insight","skill_insight","skill_sun").contains(a))rate+=.70;
                if(chain>=2&&a.equals("skill_semantics"))rate+=1.20;
                if(chain>=5&&a.equals("liberation"))rate+=.30;
                if(kind.equals("echo"))add(stats,"echo_bonus",Math.min(.50,Math.max(0,c.energyRegen()-1.25)*2));
                int blessings=c.timers().getOrDefault("sig_blessings",0);
                double blessing=(c.active()?blessings*.03:0)+(blessings>=6?.30:0);
                add(stats,"aero_bonus",blessing);add(stats,"echo_bonus",blessing);
                if(a.equals("skill_semantics")) {
                    int layers=c.timers().getOrDefault("sig_talent",0);amp+=layers*.30+(chain>=6?Math.min(.60,layers*.15):0);
                    if(chain>=6)add(stats,"defense_ignore",Math.min(.30,layers*.075));
                }
                if(chain>=6)vulnerable*=1.30;
            }
            case "galbrena" -> {
                boolean demon=a.startsWith("demon_");
                boolean boosted=demon&&(a.startsWith("demon_basic_")||a.startsWith("demon_heavy_")||a.startsWith("demon_air")||a.equals("demon_dodge_counter"));
                if(boosted)rate+=(c.timer("gal_liberation_boost")?.85:0)+(chain>=6?.60:0);
                if(demon){double embers=c.resource("embers");vulnerable*=1+Math.min(.60,embers*.015);if(chain>=1)add(stats,"crit_damage",Math.min(.80,embers*.02));if(chain>=6)amp+=Math.min(.35,embers*.00875);}
                if(chain>=3&&a.equals("liberation"))rate+=1.30;
                if(chain>=5&&Set.of("skill","skill_ascend","demon_skill").contains(a))rate+=1.50;
                if(c.timer("gal_inner"))add(stats,"attack_pct",chain>=2?.90:.20);
            }
            case "qiuyuan" -> {
                if(chain>=1)add(stats,"crit_rate",.20);
                if(chain>=4)add(stats,"attack_pct",.20);
                if(chain>=5)add(stats,"defense_ignore",.15);
                if(c.timer("qiuyuan_composure")&&a.startsWith("ink_heavy_"))vulnerable*=1.50;
            }
            case "ciaccona" -> {
                if(a.equals("heavy_quartet"))add(stats,"heavy_bonus",.30);
                if(chain>=4&&(a.equals("heavy_quartet")||kind.equals("liberation")))add(stats,"defense_ignore",.45);
                if(chain>=5&&kind.equals("liberation"))add(stats,"liberation_bonus",.40);
            }
            case "aemeath" -> {
                if(chain>=1&&c.timer("immediate")&&(a.equals("heavy")||a.equals("heavy_charge_2")||a.equals("mech_heavy")||a.equals("mech_heavy_charge_2")))add(stats,"crit_damage",3.0);
                if(chain>=2&&a.startsWith("wing_ensemble"))rate+=1;
                if(chain>=3) {if(a.equals("liberation_finale"))rate+=1;if(a.equals("liberation"))rate+=.4;}
                if(chain>=6&&kind.equals("liberation"))vulnerable*=1.4;
            }
            case "lynae" -> {
                if(chain>=1&&a.startsWith("phantom_leap"))rate+=1.2;
                if(chain>=2)amp+=.25;
                if(chain>=3&&(a.equals("visual_impact")||a.equals("rainbow_splash")))rate+=.9;
                if(chain>=4)add(stats,"attack_pct",.20);
                if(chain>=5&&a.equals("liberation"))rate+=.7;
            }
            case "mornye" -> {
                add(stats,"energy_regen",.10);
                if(chain>=5){if(a.equals("liberation"))rate+=.4;if(a.equals("tune_response_particle_jet"))rate+=1.6;}
                if(chain>=6&&a.equals("liberation"))add(stats,"liberation_bonus",4.0);
            }
            case "jinhsi" -> {
                add(stats,"spectro_bonus",.20);if(a.equals("intro"))rate+=.50;
                if(chain>=5&&a.equals("liberation"))rate+=1.20;
                if(chain>=6&&a.startsWith("epiphany"))rate+=.45;
            }
            case "camellya" -> {
                add(stats,"havoc_bonus",.15);add(stats,"basic_bonus",.15);
                // C6 explicitly deals 100% of Ephemeral's damage; retain C2's Ephemeral rate increase in that referenced damage.
                if(chain>=2&&(a.equals("ephemeral")||chain>=6&&a.equals("immortal_blossom")))rate+=1.2;
                if(chain>=3){if(a.equals("liberation"))rate+=.5;if(c.timer("budding"))add(stats,"attack_pct",.58);}
                if(chain>=5&&a.equals("intro"))rate+=3.03;
                if(chain>=5&&a.equals("outro"))rate+=.68;
            }
            case "yinlin" -> {
                if(chain>=1&&(a.equals("skill")||a.equals("skill_blast")))add(stats,"skill_bonus",.70);
                if(a.equals("skill_blast")&&c.timer("target_yinlin_sin"))add(stats,"skill_bonus",.10);
                if(chain>=3&&a.equals("judgment_thunder"))rate+=.55;
                if(chain>=5&&a.equals("liberation")&&c.yinlinMarked())add(stats,"liberation_bonus",1.0);
            }
            case "aero_rover" -> {
                if(chain>=3)add(stats,"aero_bonus",.15);
                if(chain>=5&&a.equals("liberation"))rate+=.2;
                if(chain>=6&&a.startsWith("unbound_wind"))rate+=.3;
            }
            case "cartethyia" -> {
                if(c.aeroLayers()>0)add(stats,"all_damage_bonus",.3+.1*Math.min(3,Math.max(0,c.aeroLayers()-3)));
                if(chain>=2&&!a.startsWith("fleurdelys")) {
                    if(a.startsWith("air"))rate+=2;
                    else if(a.startsWith("basic_")||a.equals("heavy")||a.equals("dodge_counter")||a.equals("intro"))rate+=.5;
                }
                if(chain>=3&&a.equals("liberation_finale"))rate+=1;
                if(chain>=6&&(a.startsWith("fleurdelys")||a.equals("liberation_finale")))vulnerable*=1.4;
            }
            case "yangyang_xuanling" -> {
                if(kind.equals("heavy")&&List.of("heavy","feather_heavy","feather_descent","listening_basic_01","listening_basic_02","listening_basic_03","listening_counter_01","listening_counter_02","listening_counter_03").contains(a))add(stats,"crit_damage",.25*c.resource("oath"));
                int layers=c.havocLayers();amp+=.1*Math.min(3,layers)+.12*Math.min(3,Math.max(0,layers-3));
                if(chain>=2&&(a.equals("heavy")||a.equals("feather_heavy")||a.equals("feather_descent")||a.startsWith("listening_")))add(stats,"heavy_bonus",1.0);
                if(chain>=3&&a.equals("liberation"))amp+=1.75;
            }
            case "hsin" -> {
                if(chain>=1&&c.mode().equals("unison")&&a.endsWith("full_unison"))rate+=.15+.1*Math.min(4,c.unisonStacks());
                if(chain>=2&&List.of("worldly_step","worldly_suppress","illuminated_descend","illuminated_suppress").contains(a))rate+=.6;
                if(chain>=3&&a.equals("liberation_palaces")){rate+=.7;if(c.mode().equals("unison"))add(stats,"crit_damage",.2+.15*Math.min(4,c.unisonStacks()));}
                if(chain>=6&&kind.equals("skill")){vulnerable*=1.4;add(stats,"defense_ignore",.20);}
                if(chain>=5&&c.form().equals("illuminated"))add(stats,"damage_reduction",.20);
            }
            case "hiyuki" -> {
                if(chain>=1&&a.startsWith("future_")&&!a.equals("future_heavy_frost"))rate+=1.2;
                if(chain>=2&&a.equals("iai"))rate+=1.25;
                if(chain>=3&&(a.equals("heavy")||a.equals("future_heavy_frost")))rate+=1.6;
                if(chain>=5&&a.startsWith("skill"))rate+=.8;
                if(chain>=6&&(a.equals("liberation")||a.equals("liberation_return")))add(stats,"crit_damage",5.0);
            }
            case "chisa" -> {
                // Void Thread requires a live recipient buff; the host checks its owner's scar.
                if(chain>=2&&element.equals("havoc"))add(stats,"resistance_ignore",.10);
                if(chain>=3&&ChisaSawRules.empoweredAttack(a))rate+=1.2;
                if(chain>=5&&a.equals("liberation"))add(stats,"liberation_bonus",1.0);
                if(chain>=6&&c.chisaMarked())vulnerable*=1.4;
            }
            case "phrolova" -> {
                add(stats,"crit_damage",.025*c.resource("echoes")+.01*Math.min(100,c.resource("echo_overflow")));
                if(chain>=1&&(a.equals("movement_death")||a.equals("undying_reverie")))rate+=.8;
                if(chain>=2&&a.equals("composition_finale"))rate+=.75;
                if(chain>=3&&kind.equals("echo"))amp+=.8;
                if(chain>=5&&c.form().equals("conducting"))add(stats,"damage_reduction",.30);
                if(chain>=6){if(List.of("hecate_strings","hecate_winds","hecate_color").contains(a))rate+=.24;if(c.form().equals("conducting")){if(c.active())add(stats,"havoc_bonus",.60);else vulnerable*=1.4;}}
            }
            default -> { }
        }
        // Both responses are always available, including Lynae's rupture mode.
        // Use the existing final-damage zone so this also multiplies team/outro amplification.
        if(c.character().equals("lynae")||c.character().equals("mornye")) {
            int layers=Math.max(0,c.timers().getOrDefault("target_tune_interference",0));
            double breakAmp=Math.max(0,c.resources().getOrDefault("passive_tune_break_amplification",0d));
            add(stats,"final_damage",layers*breakAmp*.0012);
        }
        return new Modifiers(stats,rate,amp,vulnerable);
    }
    /** Own-team effects are returned separately so the platform cannot mistakenly apply them only to the owner. */
    public List<Buff> active(State state,long now){return state.buffs().values().stream().filter(b->b.expires()==Long.MAX_VALUE||b.expires()>now).toList();}
    private static String shortAction(String action){int slash=action.lastIndexOf('/');return slash<0?action:action.substring(slash+1);}
    private static void add(Map<String,Double> map,String key,double value){map.merge(key,value,Double::sum);}
    private static final class Work {
        final Map<String,Buff> buffs;final Map<String,Integer>stacks;final Map<String,Long>ready;final Set<String>contributors;final long now;
        Work(State s,long now){this.now=now;buffs=new HashMap<>(s.buffs());stacks=new HashMap<>(s.stacks());ready=new HashMap<>(s.ready());contributors=new HashSet<>(s.contributors());
            buffs.values().removeIf(b->b.expires()!=Long.MAX_VALUE&&b.expires()<=now);
            if(!buffs.containsKey("jinhsi_c3"))stacks.remove("jinhsi_exiled");}
        void buff(String id,Scope scope,String attribute,double value,int ticks,boolean ends){buffs.put(id,new Buff(id,scope,attribute,value,ticks==0?Long.MAX_VALUE:now+ticks,ends));}
        int stack(String key,int add,int cap){int value=Math.min(cap,stacks.getOrDefault(key,0)+add);stacks.put(key,value);return value;}
        boolean ready(String key){return ready.getOrDefault(key,0L)<=now;}
        void cooldown(String key,int ticks){ready.put(key,now+ticks);}
        State finish(){return new State(buffs,stacks,ready,contributors);}
    }
}
