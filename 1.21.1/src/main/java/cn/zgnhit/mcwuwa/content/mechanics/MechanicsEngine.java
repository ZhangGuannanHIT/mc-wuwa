package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Server-only pure transition strategies. Commands do not touch MC.
 * The host atomically accepts the state and consumes each command once after action validation.
 * Audited resources remain distinct from explicitly documented production MC/community adaptation.
 */
public final class MechanicsEngine {
    /** Stored counters/input latches do not elapse with cooldowns. Shared across all role ticks. */
    private static final Set<String> NON_DECAYING_TIMERS=Set.of(
        "star_uses", "converting", "leap_count", "bounded_uses", "slow_descent", "charging",
        "magnetic_charges", "wind_air_part", "air_stage", "electro_basics", "hecate_count", "note_position",
        "out_of_combat_ticks", "was_in_combat", "human_right", "divine_intent", "outsider_power", "hsin_barrier_charges",
        "camellya_pistil_remainder","observation_escape_uses","hsin_word_lock_bonus_ready","hecate_echo_uses","hecate_background_segment",
        "return_charge_spent","return_charge_release","rover_strangle_charging","rover_air_attack_pending","next_color_note",
        "lynae_air_heavy_used","chisa_saw_stage","chisa_held_entry","chisa_air_ready","chisa_glide_followup","chisa_engine_stall",
        "hiyuki_c2_skill_charges","cart_shadow_geometry","range_human_sword","range_divine_sword","range_outsider_sword");
    public static Map<String, Double> resourceCaps(String character) {
        Map<String, Double> caps = CAPS.get(character);
        if (caps == null) throw new IllegalArgumentException("Unknown character: " + character);
        return caps;
    }
    public static Map<String, Double> resourceCaps(String character, int chain) {
        if (chain < 0 || chain > 6) throw new IllegalArgumentException("chain must be 0..6");
        if(character.equals("augusta"))return Map.of("war",100d,"power",100d,"majesty",2d,"crowns",chain>=6?4d:chain>=1?2d:1d);
        if(character.equals("denia")&&chain>=3)return Map.of("dark_core",5d,"particles",100d,"conformal",100d);
        if (character.equals("lynae") && chain == 6) return Map.of("overflow", 120.0, "lumiflow", 360.0, "true_color", 3.0);
        return resourceCaps(character);
    }
    /** Source initial pools only; no inferred SpecialEnergy slot becomes a cap. */
    public static Map<String,Double> initialResources(String character) {
        return switch(character) {
            case "phoebe" -> Map.of("prayer",120d);
            case "qingxiao" -> Map.of("flight_qi",100d);
            default -> Map.of();
        };
    }
    /** Remaining-tick cooldowns may be mirrored on the owning weapon; temporary forms must not be. */
    public static java.util.Set<String> persistentCooldownKeys(String character) {
        return switch (character) {
            case "aemeath" -> java.util.Set.of("skill", "liberation", "finale");
            case "lynae" -> java.util.Set.of("skill", "liberation", "visual_impact");
            case "mornye" -> java.util.Set.of("expected_error", "distributed_array", "liberation", "blueprint_basic", "blueprint_intro", "chain3", "bounded_cooldown");
            case "jinhsi" -> java.util.Set.of("skill", "liberation", "crescent", "overflowing_cooldown", "unison_cooldown", "incandescence_self", "rest_incandescence","incandescence_spectro","incandescence_aero","incandescence_fusion","incandescence_glacio","incandescence_electro","incandescence_havoc","incandescence_physical", "incandescence_coordinated_spectro", "incandescence_coordinated_aero", "incandescence_coordinated_fusion", "incandescence_coordinated_glacio", "incandescence_coordinated_electro", "incandescence_coordinated_havoc");
            case "camellya" -> java.util.Set.of("skill", "liberation", "ephemeral", "immortal_blossom");
            case "yinlin" -> java.util.Set.of("skill", "liberation");
            case "aero_rover" -> java.util.Set.of("skill", "sky_skill", "liberation", "rover_emergency");
            case "cartethyia" -> java.util.Set.of("skill", "fleur_skill", "liberation", "finale", "form_switch");
            case "yangyang_xuanling" -> java.util.Set.of("liberation", "heavy_critical", "feather_critical", "drywood_cooldown");
            case "hsin" -> java.util.Set.of("skill", "light_skill", "palaces_skill", "liberation", "form_switch", "suppress_worldly", "suppress_illuminated", "edict_pulse", "hsin_barrier_cooldown", "hsin_c2_enter_cooldown","hsin_c1_realm_cooldown");
            case "hiyuki" -> java.util.Set.of("skill", "future_skill", "see_mind", "return");
            case "chisa" -> java.util.Set.of("skill", "liberation");
            case "phrolova" -> java.util.Set.of("skill", "composition", "combat_entry_lock");
            case "qiuyuan" -> java.util.Set.of("skill","liberation","qiuyuan_canopy","qiuyuan_composure_cooldown");
            case "ciaccona" -> java.util.Set.of("skill","ciaccona_skill_second","liberation");
            case "phoebe" -> java.util.Set.of("skill","liberation");
            case "suisui" -> java.util.Set.of("skill","liberation","suisui_awaken","suisui_moisture_cooldown","suisui_inherent_hit_cd","suisui_kingfisher_cd","suisui_rescue_cd");
            case "cantarella" -> java.util.Set.of("skill","liberation","cantarella_dream","cantarella_drain","cantarella_revival_cd");
            case "changli" -> java.util.Set.of("liberation","changli_skill_recharge");
            case "carlotta" -> java.util.Set.of("skill","liberation","carlotta_tinted_crystal");
            case "iuno" -> java.util.Set.of("skill","liberation","iuno_clamor","iuno_beyond_first","iuno_beyond_second","iuno_full_moon_cd");
            case "lupa" -> java.util.Set.of("skill","liberation","lupa_c6_flame_cd");
            case "denia" -> java.util.Set.of("skill","denia_disillusion_skill","liberation","denia_disillusion_liberation","denia_entry_cd");
            case "qingxiao" -> java.util.Set.of("skill","liberation","qingxiao_escape_cd","qingxiao_guard_cd","qingxiao_vast_sword_cd");
            case "shorekeeper" -> java.util.Set.of("skill","liberation","shore_c3_cd","shore_death_cd");
            case "sigrika" -> java.util.Set.of("skill","liberation","sig_semantics");
            case "galbrena" -> java.util.Set.of("skill","liberation","gal_ascend");
            case "zhezhi" -> java.util.Set.of("skill","liberation");
            case "lucilla", "zani" -> java.util.Set.of("skill","liberation");
            case "augusta" -> java.util.Set.of("skill","liberation","augusta_realm_cd");
            case "suoming" -> java.util.Set.of("skill","liberation","suoming_intro_bonus_cd","suoming_chain_intro_cd");
            default -> throw new IllegalArgumentException("Unknown character: " + character);
        };
    }
    /** Counters must be persisted as integers, never passed through elapsed-time cooldown decay. */
    public static Set<String> persistentCounterKeys(String character) {
        return character.equals("changli")?Set.of("changli_spent_charges"):Set.of();
    }
    public enum Input { BASIC, BASIC_HOLD, BASIC_RELEASE, BACK_DODGE, SAW_CONTINUE, HEAVY_START, HEAVY_RELEASE, HEAVY_FULL, AIR, DODGE_COUNTER,
        SKILL, SKILL_HOLD, SKILL_RELEASE, LIBERATION, LIBERATION_HOLD, LIBERATION_RELEASE, INTRO, OUTRO, JUMP, DODGE, SWITCH_OUT, TICK, BLOCK_HIT,
        PERFECT_DODGE, REEL_COUNTER, PINCERS_CLOSE, NOTE_GREEN, NOTE_YELLOW, AIM_PRESS, AIM_RELEASE, SKILL_RUN }
    public enum Kind { ACTION, PAY_ENERGY, PAY_STAMINA, GAIN_CONCERTO, GAIN_ENERGY, HEAL_TEAM, HEAL_ACTIVE,
        BUFF_TEAM, BUFF_SELF, BUFF_NEXT, FORM, SPAWN_FIELD, REMOVE_FIELD, MARK_TARGET, MARK_AREA, CONTROL_TARGET, CONTROL_AREA, PULL_AREA, PUSH_AREA,
        STAMINA_REGEN_LOCK, MOVE_SPECIAL, MECHANIC_GAP, PAY_CONCERTO, SHIELD_TEAM, SET_HEALTH_FRACTION, ECHO_CAST, NATIVE_PROC, OUTRO_HIT, SWORD_SHADOW, SWORD_COLLECT }
    public record Context(double energy, double stamina, double defense, double energyRegen,
                          boolean airborne, boolean moving, boolean inCombat, boolean active,
                          double attack, double currentHealthNative, double maxHealthNative, double concerto) {
        public Context(double energy, double stamina, double defense, double energyRegen,
                       boolean airborne, boolean moving, boolean inCombat, boolean active) {
            this(energy, stamina, defense, energyRegen, airborne, moving, inCombat, active, 0, 0, 0, 0);
        }
        public Context {
            if (!Double.isFinite(energy) || !Double.isFinite(stamina) || !Double.isFinite(defense)
                    || !Double.isFinite(energyRegen) || energy < 0 || stamina < 0 || defense < 0 || energyRegen < 0)
                throw new IllegalArgumentException("Invalid mechanics context");
            if (!Double.isFinite(attack) || !Double.isFinite(currentHealthNative) || !Double.isFinite(maxHealthNative)
                    || !Double.isFinite(concerto) || attack < 0 || currentHealthNative < 0 || maxHealthNative < 0 || concerto < 0)
                throw new IllegalArgumentException("Invalid native stats or concerto in mechanics context");
        }
    }
    public record Effect(Kind kind, String id, double amount, int durationTicks, Map<String, Double> values) {
        public Effect { values = Map.copyOf(values); }
    }
    public record Result(boolean accepted, String reason, MechanicsState state, List<Effect> effects) {
        public Result { effects = List.copyOf(effects); }
    }
    public record DamageResult(double damage, MechanicsState state, List<Effect> effects) {
        public DamageResult { effects = List.copyOf(effects); }
    }

    static final Map<String, Map<String, Double>> CAPS = Map.ofEntries(
        Map.entry("aemeath", Map.of("synchronization", 200.0, "resonance", 4.0, "radiance", 600.0)),
        Map.entry("lynae", Map.of("overflow", 120.0, "lumiflow", 120.0, "true_color", 3.0)),
        Map.entry("mornye", Map.of("rest_energy", 100.0, "relative_motion", 100.0)),
        Map.entry("jinhsi", Map.of("incandescence", 50.0, "incarnation_stacks", 4.0)),
        Map.entry("camellya", Map.of("pistils", 100.0, "buds", 10.0)),
        Map.entry("yinlin", Map.of("judgment", 100.0)),
        Map.entry("aero_rover", Map.of("wind", 120.0)),
        Map.entry("cartethyia", Map.of("resolve", 120.0, "swords", 3.0)),
        Map.entry("yangyang_xuanling", Map.of("stream", 100.0, "feathers", 2.0, "oath", 6.0)),
        Map.entry("hsin", Map.of("worldly_heart", 100.0, "illuminated_heart", 300.0, "edicts", 21.0, "thunder_realm", 100.0)),
        Map.entry("hiyuki", Map.of("mind", 300.0, "cold", 300.0, "iai", 3.0, "chill", 3.0, "forge", 3.0)),
        Map.entry("chisa", Map.of("saw_echo", 100.0, "saw_consumed", 100.0, "lifeline", 100.0,"saw_heat_points",100.0)),
        Map.entry("phrolova", Map.of("notes", 6.0, "echoes", 24.0)),
        Map.entry("qiuyuan", Map.of("ink",600.0)),
        Map.entry("ciaccona", Map.of("melody",3.0)),
        Map.entry("phoebe", Map.of("prayer",120.0,"gospel",60.0)),
        Map.entry("suisui", Map.of("water_breath",120.0,"fragrant_message",600.0)),
        Map.entry("cantarella", Map.of("trance",5.0,"shiver",3.0)),
        Map.entry("shorekeeper", Map.of("data",5.0)),
        Map.entry("sigrika", Map.of("period",100.0,"sun_energy",60.0)),
        Map.entry("galbrena", Map.of("sin_fire",100.0,"embers",40.0,"purifying_fire",100.0)),
        Map.entry("changli",Map.of("forte",4d)),
        Map.entry("carlotta",Map.of("substance",120d,"crystal",6d)),
        Map.entry("iuno",Map.of("spirituality",100d)),
        Map.entry("lupa",Map.of("wolf_flame",100d,"wolf_soul",2d)),
        Map.entry("denia",Map.of("dark_core",3d,"particles",100d,"conformal",100d)),
        Map.entry("qingxiao",Map.of("qin_heart",100d,"sword_rhyme",100d,"sword_intent",100d,"flight_qi",100d)),
        Map.entry("zhezhi",Map.of("spirit",90d)),
        Map.entry("zani",Map.of("redundant",100d,"blaze",150d)),
        Map.entry("suoming",Map.of("obsession",800d)),
        Map.entry("lucilla",Map.of("impression",150d)),
        Map.entry("augusta",Map.of("war",100d,"power",100d,"majesty",2d,"crowns",4d)));
    /** Call only from audited server events. This method does not choose missing resource yields. */
    public MechanicsState grantResource(MechanicsState state, String resource, double amount) {
        if (!Double.isFinite(amount)) throw new IllegalArgumentException("Nonfinite grant");
        Double cap = resourceCaps(state.characterId(), state.chain()).get(resource);
        if (cap == null) throw new IllegalArgumentException("Unknown resource " + resource);
        Map<String, Double> next = new HashMap<>(state.resources());
        next.put(resource, Math.clamp(state.resource(resource) + amount, 0, cap));
        if (state.characterId().equals("phrolova") && resource.equals("echoes")) {
            if (next.get(resource) == 0) next.remove("echo_overflow");
            else if (amount > 0) next.put("echo_overflow", Math.min(100, state.resource("echo_overflow") + Math.max(0, state.resource(resource) + amount - cap)));
        }
        Map<String, Integer> timers = new HashMap<>(state.timers());
        if (state.characterId().equals("aemeath") && resource.equals("resonance")
                && next.get(resource) >= 4 && state.timer("release") > 0) timers.put("immediate", state.timer("release"));
        return new MechanicsState(state.characterId(), state.form(), state.mode(), state.chain(), state.nextBasic(), next, timers);
    }

    /** Driving is a server-observed physical vehicle state, never a claimed client resource gain. */
    public static MechanicsState vehicleCruise(MechanicsState state) {
        if(!state.characterId().equals("lynae"))return state;
        var timers=new HashMap<>(state.timers());timers.put("flow_gain",2);
        return new MechanicsState(state.characterId(),"cruise",state.mode(),state.chain(),state.nextBasic(),state.resources(),timers);
    }
    /** Called by the host only after the target atomically accepts a real tune break. */
    public MechanicsState onTuneBreak(MechanicsState state) {
        if (state.characterId().equals("aemeath")) {
            var timers = new HashMap<>(state.timers());
            // Source specifies a short continuation, not a duration: use the existing 1.5s MC combo window.
            timers.put("combo_window",30);
            return new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),3,state.resources(),timers);
        }
        if (!state.characterId().equals("hiyuki")) return state;
        var timers = new HashMap<>(state.timers());
        timers.put("hiyuki_basic3_window",60); timers.put("combo_window",60);
        timers.remove("iai_stance");
        return new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),3,state.resources(),timers);
    }
    public Result reduce(MechanicsState state, Input input, Context context) {
        if (!CAPS.containsKey(state.characterId()))
            return new Result(false, "strategy_not_implemented", state, List.of());
        if(HiyukiReturnChargeRules.isCharging(state)&&input!=Input.TICK&&input!=Input.SWITCH_OUT&&input!=Input.LIBERATION_RELEASE)return new Result(false,"return_charging",state,List.of());
        Transition transaction = new Transition(state, context);
        try {
            if (input == Input.TICK) tick(transaction);
            else if (input == Input.SWITCH_OUT) switchOut(transaction);
            else switch (state.characterId()) {
                case "aemeath" -> aemeath(transaction, input);
                case "lynae" -> lynae(transaction, input);
                case "mornye" -> mornye(transaction, input);
                default -> ExtendedMechanics.dispatch(transaction, input);
            }
            if(input!=Input.TICK&&input!=Input.SWITCH_OUT){LowAirRules.validate(transaction,input);GameplayResourceRules.cast(transaction);CharacterLifecycleRules.cast(transaction);SoloEffectRules.cast(transaction);}
            LynaeCamouflageRules.input(transaction,input);
            return new Result(true, "ok", transaction.build(), transaction.effects);
        } catch (Rejected rejection) {
            return new Result(false, rejection.getMessage(), state, List.of());
        }
    }
    /** Explicit creative utility, never called by normal resource/hit events.
     * Charge pools are filled; historical consumption and attack/shadow/buff counters stay intact. */
    public MechanicsState rechargeForCreative(MechanicsState state) {
        var result=state;
        var bookkeeping=java.util.Set.of("saw_consumed","incarnation_stacks","swords","oath","edicts");
        for(var pool:resourceCaps(state.characterId(),state.chain()).entrySet()) {
            if(bookkeeping.contains(pool.getKey()))continue;
            double cap=state.characterId().equals("zani")&&pool.getKey().equals("blaze")&&!state.form().equals("blazing")?100:pool.getValue();
            double missing=Math.max(0,cap-result.resource(pool.getKey()));
            if(missing>0)result=grantResource(result,pool.getKey(),missing);
        }
        if(state.characterId().equals("phrolova")) {
            var resources=new HashMap<>(result.resources());
            for(int note=0;note<6;note++)resources.putIfAbsent("note_"+note,0d);
            result=new MechanicsState(result.characterId(),result.form(),result.mode(),result.chain(),result.nextBasic(),resources,result.timers());
        }
        return result;
    }
    /** Mode switching preserves shared resources/form/cooldowns; only documented electro-use qualifications end. */
    public MechanicsState changeMode(MechanicsState state,String selectedMode) {
        String mode=MechanicsState.canonicalMode(state.characterId(),selectedMode);if(mode.equals(state.mode()))return state;
        var timers=new HashMap<>(state.timers());
        var resources=new HashMap<>(state.resources());
        if(state.characterId().equals("hsin")){timers.remove("electro_basics");timers.remove("hsin_word_lock_bonus_ready");timers.remove("hsin_storm_charge");timers.remove("unison");UnisonRules.COUNTERS.forEach(timers::remove);resources.remove("thunder_realm");}
        MechanicsState selected=new MechanicsState(state.characterId(),state.form(),mode,state.chain(),state.nextBasic(),resources,timers);
        if(state.characterId().equals("denia")) {
            Transition transition=new Transition(selected,new Context(0,0,0,1,false,false,false,false));
            NewSixMechanics.deniaModeChanged(transition);
            // This state-only API preserves the existing signature. The host must
            // revoke mode-bound external buffs alongside this selection change.
            return transition.build();
        }
        return selected;
    }

    /** One confirmed hit per action instance. The host owns deduplication and target validation.
     * Strict mode never uses the separately audited Minecraft preview resource yields. */
    public Result onHit(MechanicsState state, String actionId, Context context, boolean allowAuthoredResourceYields) {
        if (!actionId.startsWith(state.characterId() + "/")) return new Result(false, "foreign_action", state, List.of());
        Transition t = new Transition(state, context);
        ExtendedMechanics.hit(t, actionId.substring(actionId.indexOf('/') + 1));
        GameplayResourceRules.hit(t,actionId);
        if (allowAuthoredResourceYields && !NativeResourceYields.hasRuleForAction(state.characterId(), actionId)
            &&GameplayResourceRules.ENTRIES.stream().noneMatch(g->g.action().equals(actionId)))
            PreviewResourceYields.apply(t, actionId.substring(actionId.indexOf('/') + 1));
        return new Result(true, "ok", t.build(), t.effects);
    }
    /** Source node3: the active teammate's accepted damage refreshes Hsin's
     * three-second Edict stream at most once per second. Flying Palace cannot self-refresh. */
    public Result onActiveTeamDamage(MechanicsState state,Context context,String action) {
        if(!state.characterId().equals("hsin")||state.resource("edicts")<=0
            ||state.timer("edicts")<=0||state.timer("hsin_edict_refresh")>0||action.equals("hsin/flying_palace"))
            return new Result(false,"no_edict_refresh",state,List.of());
        Transition t=new Transition(state,context);t.timer("edict_trigger",60);t.timer("hsin_edict_refresh",20);
        return new Result(true,"ok",t.build(),List.of());
    }
    /** Accepted echo cast only; echo identity comes from the server's fixed catalog, never a client counter. */
    public Result onEchoCast(MechanicsState state, Context context, int echoId, boolean ownerActor) {
        if (echoId <= 0) throw new IllegalArgumentException("echo identity");
        if(state.characterId().equals("cantarella")){
            Transition t=new Transition(state,context);String name="cantarella_revival_echo_"+echoId;
            if(t.has("cantarella_revival")&&t.time("cantarella_revival_uses")>0&&!t.has(name)){
                t.timer(name,t.time("cantarella_revival"));t.timer("cantarella_revival_uses",t.time("cantarella_revival_uses")-1);t.effect(Kind.GAIN_CONCERTO,"cantarella_revival",6,0);
            }
            return new Result(true,"ok",t.build(),t.effects);
        }
        if(state.characterId().equals("sigrika")||state.characterId().equals("galbrena")) {
            Transition t=new Transition(state,context);NewThreeMechanics.echo(t,echoId);
            return new Result(true,"ok",t.build(),t.effects);
        }
        if(state.characterId().equals("qiuyuan")&&ownerActor) {
            Transition t=new Transition(state,context);t.timer("qiuyuan_wine",Integer.MAX_VALUE);
            return new Result(true,"ok",t.build(),List.of());
        }
        if (!state.characterId().equals("phrolova")) return new Result(true,"no_echo_strategy",state,List.of());
        Transition t = new Transition(state,context);
        if (ownerActor) t.timer("next_color_note",1);
        if (!context.active() && t.form.equals("conducting") && t.has("conducting") && !t.has("echo_heard_" + echoId) && t.time("hecate_echo_uses") < 10) {
            t.timer("echo_heard_" + echoId,t.time("conducting"));t.timer("hecate_echo_uses",t.time("hecate_echo_uses") + 1);
            CharacterBranches.hecateEnhanced(t);
        }
        return new Result(true,"ok",t.build(),t.effects);
    }

    /** Only a host-confirmed cancellation can create an attack-completing Ciaccona afterimage. */
    public Result ciacconaInterrupted(MechanicsState state,Context context,String action,int remaining,boolean skillCancel) {
        if(!state.characterId().equals("ciaccona"))return new Result(false,"wrong_character",state,List.of());
        Transition t=new Transition(state,context);NewAeroMechanics.interrupted(t,action,remaining,skillCancel);
        return new Result(true,"ok",t.build(),t.effects);
    }
    /** Host timeline notification: release of the last arrow, regardless of target hit. */
    public Result hiyukiLastArrowReleased(MechanicsState state,Context context) {
        Transition t=new Transition(state,context);
        if(state.characterId().equals("hiyuki")&&t.has("hiyuki_last_arrow_pending")) {
            t.clear("hiyuki_last_arrow_pending");t.add("mind",-300);t.timer("see_mind_ready",1);
        }
        return new Result(true,"ok",t.build(),t.effects);
    }
    public Result actionCompleted(MechanicsState state,Context context,String action) {
        Transition t=new Transition(state,context);
        if(action.equals("mornye/heavy_inversion"))t.clear("mornye_inversion_lock");
        if(action.equals("lynae/skill_additive")){t.clear("lynae_premix");t.clear("lynae_premix_stop");}
        if(state.characterId().equals("qingxiao")&&java.util.Set.of("qingxiao/basic_02","qingxiao/basic_03","qingxiao/dodge_counter").contains(action))
            t.clear("qingxiao_ascend_ready");
        if(state.characterId().equals("aemeath")&&(action.contains("heavy")||action.equals("aemeath/liberation_finale")))
            t.timer("aemeath_brilliance_wait",81);
        if(state.characterId().equals("suoming"))SuomingMechanics.completed(t,action);
        if(state.characterId().equals("lucilla"))LucillaMechanics.completed(t,action);
        if(state.characterId().equals("ciaccona"))NewAeroMechanics.completed(t,action);
        if(state.characterId().equals("qiuyuan")&&action.startsWith("qiuyuan/ink_heavy_03"))t.clear("qiuyuan_final_reward");
        if(state.characterId().equals("sigrika")&&action.equals("sigrika/skill_semantics")){t.clear("sig_semantics_cast");if(state.chain()<3)t.clear("sig_talent");}
        return new Result(true,"ok",t.build(),t.effects);
    }
    public Result finishQiuyuanRun(MechanicsState state,Context context,boolean nearbyTarget) {
        if(!state.characterId().equals("qiuyuan")||state.timer("qiuyuan_dash")==0&&state.timer("qiuyuan_dash_ended")==0)return new Result(false,"not_running",state,List.of());
        Transition t=new Transition(state,context);t.clear("qiuyuan_dash");t.clear("qiuyuan_dash_ended");
        if(nearbyTarget)t.action("skill_dash");
        return new Result(true,"ok",t.build(),t.effects);
    }

    /** Internal host-confirmed world events; never accepts client-reported target success. */
    public Result sixWorldEvent(MechanicsState state,Context context,String event,Map<String,Double> values) {
        Transition t=new Transition(state,context);
        switch(event) {
            case "lupa_sprint" -> {
                if(!state.characterId().equals("lupa"))return new Result(false,"wrong_character",state,List.of());
                NewSixMechanics.lupaSprint(t,context.active()&&values.getOrDefault("sprinting",0d)>0);
            }
            case "lupa_marked_kill" -> {if(!state.characterId().equals("lupa"))return new Result(false,"wrong_character",state,List.of());NewSixMechanics.lupaMarkedKill(t,true);}
            case "lupa_assist_consumed" -> {t.clear("lupa_assist_waiting");t.clear("lupa_assist_ready");}
            case "lupa_team_intro" -> NewSixMechanics.lupaTeamIntro(t);
            case "qingxiao_locked_kill" -> NewSixMechanics.qingxiaoLockedKill(t,values.getOrDefault("layers",0d).intValue(),true);
            case "qingxiao_c4_applier" -> NewSixMechanics.qingxiaoOffset(t);
            case "qingxiao_capture_locks" -> NewSixMechanics.qingxiaoCaptureLocks(t,values.getOrDefault("layers",0d).intValue());
            case "qingxiao_team_hit" -> NewSixMechanics.qingxiaoTeamHit(t,t.time("qingxiao_consciousness")>0);
            case "qingxiao_guard_hit" -> {
                if(!state.characterId().equals("qingxiao"))return new Result(false,"wrong_character",state,List.of());
                NewSixMechanics.qingxiaoGuardHit(t,values.getOrDefault("monsterAttack",0d)>0);
            }
            case "carlotta_brilliance_hit" -> {
                if(!state.characterId().equals("carlotta"))return new Result(false,"wrong_character",state,List.of());
                NewSixMechanics.carlottaBrillianceHit(t,values.getOrDefault("targetVariegated",0d)>0);
            }
            case "qingxiao_actual_tune_break" -> {
                if(!state.characterId().equals("qingxiao"))return new Result(false,"wrong_character",state,List.of());
                NewSixMechanics.qingxiaoTuneBreak(t,values.getOrDefault("grounded",0d)>0);
            }
            case "qingxiao_long_jump" -> {try{NewSixMechanics.qingxiaoLongJump(t);}catch(Rejected rejection){return new Result(false,rejection.getMessage(),state,List.of());}}
            case "iuno_exit_traversal" -> NewSixMechanics.iunoExitTraversal(t);
            case "qingxiao_exit_traversal" -> NewSixMechanics.qingxiaoExitFlight(t);
            case "iuno_field_shield" -> NewSixMechanics.iunoShieldInField(t);
            case "iuno_c1_field_energy" -> t.effect(Kind.GAIN_ENERGY,"iuno_c1_field_energy",values.getOrDefault("amount",1d),0);
            case "denia_team_effect" -> NewSixMechanics.deniaTeamEffect(t,values.getOrDefault("fusion",0d)>0);
            case "denia_c6_tune_break" -> NewSixMechanics.deniaTeamTuneBreak(t,true);
            case "denia_fusion_detonation" -> {
                if(!state.characterId().equals("denia"))return new Result(false,"wrong_character",state,List.of());
                NewSixMechanics.deniaFusionDetonation(t);
            }
            case "denia_mode_selected" -> {
                if(!state.characterId().equals("denia"))return new Result(false,"wrong_character",state,List.of());
                NewSixMechanics.deniaModeSelected(t);
            }
            default -> {return new Result(false,"unknown_world_event",state,List.of());}
        }
        return new Result(true,"ok",t.build(),t.effects);
    }

    /** Audited slots are per actual damage event, independent of the once-per-action mark/preview callback.
     * The host owns successful-hit and repeated-index deduplication; clients cannot call this seam.
     */
    public Result onDamageHit(MechanicsState state, String actionId, String damageId, Context context) {
        if (!actionId.startsWith(state.characterId() + "/")) return new Result(false, "foreign_action", state, List.of());
        if (!context.active()) return new Result(false, "resource_owner_inactive", state, List.of());
        var yields = NativeResourceYields.findAll(state, actionId, damageId);
        if (yields.isEmpty()) return new Result(true, "no_audited_resource_slot", state, List.of());
        Transition t = new Transition(state, context); for(var yield:yields)NativeResourceYields.apply(t,yield);
        return new Result(true, "ok", t.build(), t.effects);
    }

    private void aemeath(Transition t, Input input) {
        boolean mech = t.form.equals("mech");
        switch (input) {
            case BASIC -> {
                int segment = Math.clamp(t.combo, 1, 4);
                t.clear("intro_followup");
                t.clear("overload_followup");
                t.action((mech ? "mech_" : "") + "basic_0" + segment);
                t.combo = segment % 4 + 1;
                t.timer("combo_window", 30); // Minecraft authored cancel/continuation window, 1.5s.
                if (segment >= 2) t.timer("combined_thrust", 30);
                if (segment == 4) t.timer("wing_window", 100);
            }
            case HEAVY_START -> t.timer("charging", 1);
            case HEAVY_RELEASE, HEAVY_FULL -> {
                t.payStamina(20);
                t.clear("intro_followup");t.clear("overload_followup");
                boolean immediate=t.has("immediate")||t.has("aemeath_brilliance");
                boolean full = input == Input.HEAVY_FULL || immediate;
                t.action((mech ? "mech_" : "") + (full ? "heavy_charge_2" : "heavy"));
                if (immediate) {
                    t.effect(Kind.BUFF_SELF, "heavy_damage_amplification", 2.0, 1);
                    if (t.original.chain() >= 1) t.effect(Kind.BUFF_SELF, "aemeath_immediate_critical_damage", 3.0, 1);
                }
                if (full && immediate && t.has("release")) t.set("synchronization", 200);
                if(full&&t.has("aemeath_brilliance")){if(!t.has("release"))t.add("synchronization",100);t.clear("aemeath_brilliance");}
                if (full) t.clear("immediate");
                t.combo = full ? 3 : 2;
                t.timer("combined_thrust", 30); t.clear("charging");
            }
            case AIR -> { t.payStamina(30); t.clear("intro_followup");t.clear("overload_followup");t.action(mech ? "mech_air" : "air"); t.effect(Kind.MOVE_SPECIAL,"tide_plunge",1,0); t.combo = 2; }
            case DODGE_COUNTER -> { t.clear("intro_followup");t.clear("overload_followup");t.action(mech ? "mech_dodge_counter" : "dodge_counter"); t.combo = 4; t.timer("combined_thrust", 30); }
            case SKILL -> {
                if (aemeathFinaleAvailable(t)) { aemeathFinale(t); return; }
                if(t.has("intro_followup")||t.has("overload_followup")&&!(t.has("wing_window")&&t.get("synchronization")>=100)) {
                    int segment=t.has("intro_followup")?3:2;
                    t.requireCooldown("skill");t.timer("skill",20);t.clear("intro_followup");t.clear("overload_followup");
                    t.form=mech?"aemeath":"mech";t.action((mech?"basic_0":"mech_basic_0")+segment);
                    t.combo=segment+1;t.timer("combo_window",30);t.timer("combined_thrust",30);t.effect(Kind.FORM,t.form,0,0);return;
                }
                t.requireCooldown("skill"); t.timer("skill", 20);
                if (t.has("wing_window") && t.get("synchronization") >= 100) {
                    t.clear("overload_followup");
                    t.add("synchronization", -100);
                    t.action(mech ? "wing_ensemble_stage" : "wing_ensemble_descend",Map.of(
                        "starResonance",t.has("star_resonance")?1d:0d,"preserveTraces",t.has("free_trace")?1d:0d));
                    t.clear("free_trace");
                    t.add("resonance", 1);
                    t.clear("wing_window");
                    if (t.has("release") && t.get("resonance") >= 4) t.timer("immediate", t.time("release"));
                    if (t.has("star_resonance")) {
                        t.timers.merge("star_uses", 1, Integer::sum);
                        if (t.time("star_uses") >= 2) { t.clear("star_resonance"); t.clear("star_uses"); }
                    }
                    // Target-owned traces and one cast-wide pulse budget settle in SoloTargetRuntime.wingBurst.
                } else if (t.has("combined_thrust")) {
                    t.action(mech ? "skill_dawn" : "skill"); t.clear("combined_thrust");
                } else {
                    t.form = mech ? "aemeath" : "mech";
                    t.action(mech && t.context.airborne() ? "air" : (mech ? "basic_01" : "mech_basic_01"));
                    if(mech&&t.context.airborne())t.effect(Kind.MOVE_SPECIAL,"tide_plunge",1,0);
                    t.combo = 2; t.effect(Kind.FORM, t.form, 0, 0); return;
                }
                t.form = mech ? "aemeath" : "mech"; t.combo = 2; t.effect(Kind.FORM, t.form, 0, 0);
            }
            case LIBERATION -> {
                if (aemeathFinaleAvailable(t)) { aemeathFinale(t); return; }
                if (t.has("release")) throw new Rejected("finale_resources_missing");
                t.requireCooldown("liberation"); t.payEnergy(125); t.timer("liberation", 500);
                t.action("liberation"); t.form = "mech"; t.combo = 2;
                t.timer("star_resonance", 600); t.timer("release", 1200); t.timer("free_trace", 600);
                t.clear("star_uses");t.timer("overload_followup",80);
                t.add("synchronization", 30); t.add("resonance", t.has("intro_boost") ? 2 : 1);
                t.clear("intro_boost");t.clear("intro_followup"); t.effect(Kind.GAIN_CONCERTO, "liberation", 20, 0);
                if (t.get("resonance") >= 4) t.timer("immediate", 1200);
                t.effect(Kind.FORM, t.form, 0, 0);
            }
            case INTRO -> {
                t.clear("overload_followup");
                t.action(mech ? "intro_starlight" : "intro"); t.add("synchronization", 40);
                t.timer("intro_boost", 300);t.timer("intro_followup",80); t.combo = 3; t.effect(Kind.GAIN_CONCERTO, "intro", 10, 0);
            }
            case OUTRO -> t.effect(Kind.BUFF_TEAM, "aemeath_outro_other_roles_amp", 0.10, 400,
                    Map.of("afterMatchingApplication", 0.20, "excludeOwner", 1.0,"fusionMode",t.original.mode().equals("fusion")?1d:0d));
            default -> throw new Rejected("unsupported_input_in_form");
        }
    }
    private boolean aemeathFinaleAvailable(Transition t) {
        return t.has("release") && t.get("synchronization") >= 200 && t.get("resonance") >= 4;
    }
    private void aemeathFinale(Transition t) {
        t.requireCooldown("finale"); t.timer("finale", 500); t.action("liberation_finale");
        t.set("synchronization", 0); t.set("resonance", 0);
        t.clear("release"); t.clear("wing_window"); t.clear("immediate");
        t.clear("overload_followup");
        t.form = "aemeath"; t.combo = 1;
        t.effect(Kind.GAIN_CONCERTO, "finale", 20, 0); t.effect(Kind.FORM, t.form, 0, 0);
    }

    private void lynae(Transition t, Input input) {
        boolean cruise = t.form.equals("cruise");
        switch (input) {
            case BASIC -> {
                if (cruise && t.context.airborne() && t.get("true_color") >= 3) { lynaeImpact(t); return; }
                if (t.has("tomorrow")) { t.action("toward_colorful_tomorrow"); t.clear("tomorrow"); t.combo = cruise ? 2 : 1; if(cruise)t.timer("flow_gain",30); return; }
                if(cruise&&t.has("lynae_basic2_followup"))t.combo=2;
                t.combo = Math.clamp(t.combo, 1, cruise ? 5 : 3);
                t.action((cruise ? "cruise_" : "") + "basic_0" + t.combo);
                t.combo = t.combo % (cruise ? 5 : 3) + 1;
                t.timer("combo_window", 30);
                if (cruise) t.timer("flow_gain", 30);
            }
            case HEAVY_START -> {
                if (cruise) {
                    if(t.context.airborne()&&t.has("lynae_air_heavy_used"))throw new Rejected("air_heavy_already_used");
                    t.payStamina(25);t.action(t.context.airborne()?"cruise_air_heavy":"heavy");t.timer("flow_gain",30);
                    if(t.context.airborne())t.clear("charging");else t.timer("charging",1);
                    if(t.context.airborne())t.timer("lynae_air_heavy_used",1);
                }
                else {
                    if (t.get("overflow") < 120) throw new Rejected("overflow_not_full");
                    t.timer("converting", 1); t.timer("conversion_pulse", 4);
                    t.effect(Kind.BUFF_SELF, "conversion_damage_reduction", 0.5, 0);
                    t.effect(Kind.BUFF_SELF, "conversion_uninterruptible", 1, 0);
                }
            }
            case HEAVY_RELEASE, HEAVY_FULL -> {
                if (t.has("converting")) finishConversion(t);
                else if (cruise) {if(t.has("charging")){t.clear("charging");t.action("cruise_jump_bundle");}}
                else throw new Rejected("not_charging");
            }
            case AIR -> {
                if(cruise&&t.context.airborne()&&t.get("true_color")>=3){lynaeImpact(t);return;}
                t.payStamina(30);t.action(cruise?"cruise_air":"air");
            }
            case DODGE_COUNTER -> { t.action(cruise ? "cruise_dodge_counter" : "dodge_counter"); if (cruise) { t.combo = 2; t.timer("flow_gain", 30); } }
            case SKILL, SKILL_HOLD -> {
                if (cruise && t.context.airborne() && t.get("true_color") >= 3 && input == Input.SKILL) { lynaeImpact(t); return; }
                t.requireCooldown("skill"); t.timer("skill", 120);
                if (cruise && !t.context.airborne() && input == Input.SKILL) { t.action("skill_additive"); t.timer("flow_gain", 30); }
                else { t.action("skill"); if (cruise && input == Input.SKILL_HOLD) exitCruise(t); }
                t.combo = 2;
            }
            case LIBERATION -> {
                t.requireCooldown("liberation"); t.payEnergy(125); t.timer("liberation", 500);
                t.action("liberation"); t.timer("tomorrow", 40);
                t.effect(Kind.GAIN_CONCERTO, "liberation", 20, 0);
                t.effect(Kind.BUFF_TEAM, "lynae_liberation_damage_bonus", 0.24, 600);
            }
            case INTRO -> {
                t.action("intro"); if (!cruise) t.add("overflow", 100);
                else if (t.original.chain() >= 6) t.add("lumiflow", 120);
                if(cruise)t.timer("flow_gain",30);
                t.effect(Kind.GAIN_CONCERTO, "intro", 10, 0);
                t.effect(Kind.BUFF_SELF, "spectro_damage_bonus", 0.25, 180);
                t.effect(Kind.MARK_TARGET, t.original.mode().equals("strain") ? "strain_shifting" : "rupture_shifting", 1, 500);
            }
            case OUTRO -> {
                t.effect(Kind.BUFF_NEXT, "lynae_outro", 0.15, 280, Map.of("liberationAmplification", 0.25, "endsOnSwitch", 1.0));
                if(t.original.chain()>=2)t.effect(Kind.BUFF_NEXT,"lynae_c2_outro",.25,280,Map.of("endsOnSwitch",1d));
                // Source outro text says 100% Spectro; host must use its explicit damage policy, not an unrelated damage row.
                OutroRules.damage(t,"spectro",1); if (t.original.chain() < 6) exitCruise(t);
            }
            case JUMP -> {
                double cost=resourceCaps("lynae",t.original.chain()).get("lumiflow")/3;
                if (!cruise || t.get("lumiflow") < cost) throw new Rejected("phantom_leap_unavailable");
                int leap = t.time("leap_count") + 1;
                if (leap > 3) throw new Rejected("phantom_leap_limit");
                t.add("lumiflow", -cost); t.timer("leap_count", leap); t.action("phantom_leap_" + leap);
                if(leap==2)t.effect(Kind.PULL_AREA,"lynae_second_leap",.25,1,Map.of("radius",6d,"centerOnOwner",1d));
                if(t.original.chain()>=6)t.clear("lynae_air_heavy_used");
                if (t.context.inCombat()) t.add("true_color", 1);
                t.effect(Kind.MARK_TARGET, t.original.mode().equals("strain") ? "strain_shifting" : "rupture_shifting", 1, 500);
                t.effect(Kind.MOVE_SPECIAL, "phantom_leap", 0, 0);
                if (leap == 1) t.combo = 2;
            }
            default -> throw new Rejected("unsupported_input_in_form");
        }
    }
    private void lynaeImpact(Transition t) {
        t.add("true_color", -3);
        t.effect(Kind.MOVE_SPECIAL,"lynae_plunge",0,0);
        if (t.has("visual_impact")) t.action("rainbow_splash");
        else {
            t.action("visual_impact"); t.timer("visual_impact", 500);
            t.effect(Kind.BUFF_TEAM, "tune_break_amplification", 40, 600);
            t.effect(Kind.SPAWN_FIELD, "lynae_paint", 0, t.original.chain()>=1?200:100,
                Map.of("markIntervalTicks",40d,"strainMode",t.original.mode().equals("strain")?1d:0d,"chain1Pull",t.original.chain()>=1?1d:0d));
        }
        t.combo = 2;
        t.effect(Kind.MARK_TARGET, t.original.mode().equals("strain") ? "strain_shifting" : "rupture_shifting", 1, 500);
    }
    private void finishConversion(Transition t) {
        double flow = t.get("lumiflow"), maxFlow = resourceCaps("lynae", t.original.chain()).get("lumiflow");
        t.action(flow < maxFlow * .5 ? "inspiration_1" : flow < maxFlow ? "inspiration_2" : "inspiration_3");
        t.clear("converting"); t.clear("conversion_pulse"); t.set("overflow", 0);
        t.form = "cruise"; t.clear("out_of_combat_ticks"); t.combo = 1; t.effect(Kind.FORM, "cruise", 0, 0);
        t.effect(Kind.BUFF_SELF, "conversion_damage_reduction", 0, 0);
        t.effect(Kind.BUFF_SELF, "conversion_uninterruptible", 0, 0);
    }
    private void exitCruise(Transition t) {
        t.clear("lynae_basic2_followup");
        t.form = "sampling"; t.clear("out_of_combat_ticks"); t.set("lumiflow", 0); t.set("true_color", 0);
        t.clear("flow_gain"); t.clear("leap_count"); t.clear("converting"); t.combo = 1;
        t.effect(Kind.FORM, "sampling", 0, 0);
    }

    private void mornye(Transition t, Input input) {
        boolean observation = t.form.equals("observation");
        if(observation&&t.has("slow_descent")&&List.of(Input.BASIC,Input.BASIC_HOLD,Input.HEAVY_START,Input.HEAVY_RELEASE,Input.HEAVY_FULL,Input.SKILL,Input.SKILL_HOLD).contains(input))
            throw new Rejected("slow_descent_blocks_attack");
        switch (input) {
            case BASIC,BASIC_HOLD -> {
                if(input==Input.BASIC_HOLD){
                    if(!observation)throw new Rejected("observation_required");
                    if(t.get("relative_motion")>=100){mornyeInversion(t);break;}
                }
                if(!observation&&t.has("block")){t.clear("block");t.combo=2;t.effect(Kind.BUFF_SELF,"mornye_block_damage_reduction",0,0);}
                mornyeBasic(t, observation);
            }
            case HEAVY_START, HEAVY_RELEASE, HEAVY_FULL -> {
                if (!observation && t.get("rest_energy") >= 100) {
                    t.action("heavy_potential"); t.set("rest_energy", 0); enterObservation(t);
                } else if (observation && t.get("relative_motion") >= 100) {
                    mornyeInversion(t);
                } else if (observation) { mornyeBasic(t, true); }
                else { t.payStamina(25); t.action("heavy"); }
            }
            case AIR -> { t.payStamina(30); t.action("air"); if (observation) exitObservation(t); t.effect(Kind.MOVE_SPECIAL,"tide_plunge",1,0); t.combo = 3; }
            case DODGE_COUNTER -> { t.action(observation ? "observation_dodge_counter" : "dodge_counter"); t.combo = observation ? 3 : 2; }
            case SKILL -> {
                String cooldown = observation ? "distributed_array" : "expected_error";
                t.requireCooldown(cooldown); t.timer(cooldown, observation ? 320 : 100);
                if (observation) {
                    t.action("skill"); t.effect(Kind.HEAL_TEAM, "distributed_array", 427 + 1.134 * t.context.defense(), 0);
                    t.effect(Kind.GAIN_CONCERTO, "distributed_array", 10, 0);
                    if (t.original.chain() >= 3 && !t.has("chain3")) {
                        t.timer("chain3", 500); t.add("relative_motion", 100); t.effect(Kind.GAIN_CONCERTO, "chain3", 25, 0);
                    }
                } else {
                    t.effect(Kind.HEAL_TEAM, "expected_error", 94 + 0.2494 * t.context.defense(), 0);
                    t.timer("block", 10); // Authored MC half-second block window; duration absent from source.
                    t.effect(Kind.BUFF_SELF, "mornye_block_damage_reduction", 1, 10);
                }
                if (!t.has("bounded_cooldown")) {
                    t.timer("bounded_cooldown", 6000); t.timer("bounded", 1200); t.timer("bounded_uses", 3);
                    t.effect(Kind.BUFF_TEAM, "mornye_bounded_proof", 0.30, 1200, Map.of("damageCaps", 3.0, "lethalSaves", 1.0));
                }
            }
            case BLOCK_HIT -> {
                if (!t.has("block")) throw new Rejected("not_blocking");
                t.clear("block"); t.action("skill_optimal"); t.timer("expected_error", Math.max(0, t.time("expected_error") - 40));
                t.effect(Kind.CONTROL_AREA,"mornye_optimal_stasis",1,20,Map.of("radius",6d));
            }
            case LIBERATION -> {
                t.requireCooldown("liberation"); t.payEnergy(175); t.timer("liberation", 500); t.action("liberation");
                t.effect(Kind.GAIN_CONCERTO, "liberation", 20, 0);
                double excess = Math.max(0, t.context.energyRegen() - 1);
                t.effect(Kind.BUFF_SELF, "critical_protocol_crit", Math.min(0.8, excess * 0.5), 1,
                        Map.of("critDamage", Math.min(1.6, excess)));
                if (t.has("field")) {
                    t.effect(Kind.REMOVE_FIELD, "mornye_resonance", 0, 0);
                    t.timer("field", 500); t.timer("strong_field", 500);
                    t.effect(Kind.SPAWN_FIELD, "mornye_strong_resonance", 0, 500,
                            Map.of("defensePercent", .20, "healMultiplier", 1.40, "offTuneEfficiency", t.original.chain() >= 2 ? .70 : .50));
                }
            }
            case INTRO -> {
                t.action("intro"); t.set("rest_energy", 0); enterObservation(t);
                t.effect(Kind.GAIN_CONCERTO, "intro", 10, 0);
                if (!t.has("blueprint_intro")) { t.timer("blueprint_intro", 400); t.effect(Kind.GAIN_CONCERTO, "blueprint_intro", 20, 0); }
            }
            case OUTRO -> t.effect(Kind.BUFF_TEAM, "mornye_outro_all_amplification", .25, 600);
            case JUMP -> {
                if(!observation)throw new Rejected("not_observing");
                if(t.has("slow_descent")||t.context.stamina()<.25)exitObservation(t);
                else {t.effect(Kind.MOVE_SPECIAL,"slow_descent",0,0);t.timer("slow_descent",1);}
            }
            case DODGE -> {
                if(!observation)throw new Rejected("not_observing");
                if(t.has("slow_descent"))exitObservation(t);
                else if(t.has("observation_hurt_window")&&t.time("observation_escape_uses")<3) {
                    t.timer("observation_escape_uses",t.time("observation_escape_uses")+1);t.clear("observation_hurt_window");
                    t.effect(Kind.MOVE_SPECIAL,"mornye_escape",1,0);
                }
                else t.timer("flight_sprint",200);
            }
            default -> throw new Rejected("unsupported_input_in_form");
        }
    }
    private static void mornyeBasic(Transition t, boolean observation) {
        t.combo = Math.clamp(t.combo, 1, observation ? 3 : 4);
        t.action((observation ? "observation_" : "") + "basic_0" + t.combo);
        if (observation && t.combo == 3 && !t.has("blueprint_basic")) {
            t.timer("blueprint_basic", 400); t.effect(Kind.GAIN_CONCERTO, "blueprint_basic", 20, 0);
        }
        t.combo = t.combo % (observation ? 3 : 4) + 1; t.timer("combo_window", 30);
    }

    private static void mornyeInversion(Transition t){
        t.action("heavy_inversion");t.set("relative_motion",0);
        t.effect(Kind.MARK_TARGET,"mornye_observation",1,600);
        if(t.original.chain()>=1)t.effect(Kind.MARK_TARGET,"mornye_interference",1,400);
    }

    private void enterObservation(Transition t) {
        t.form = "observation"; t.combo = 1; t.timer("observation", 600);
        t.clear("observation_escape_uses");t.clear("observation_hurt_window");
        t.timer("field", 500); t.timer("field_heal", 60); t.clear("strong_field");t.timer("observation_entry_grace",3);
        t.effect(Kind.FORM, "observation", 0, 600); t.effect(Kind.MOVE_SPECIAL, "enter_observation_air", 0, 0);
        t.effect(Kind.ACTION, "mornye/resonance_field", 0, 0);
        t.effect(Kind.SPAWN_FIELD, "mornye_resonance", 0, 500,
                Map.of("offTuneEfficiency", t.original.chain() >= 2 ? .70 : .50, "interruptionResistance", 1.0));
    }
    private void exitObservation(Transition t) {
        t.form = "baseline"; t.combo = 1; t.clear("observation"); t.clear("slow_descent");t.clear("flight_sprint");
        t.clear("observation_escape_uses");t.clear("observation_hurt_window");
        t.effect(Kind.FORM, "baseline", 0, 0); t.effect(Kind.STAMINA_REGEN_LOCK, "observation", 0, 0);
    }
    /** Server scene/tool exit. Resource and cooldown ownership stays on the weapon. */
    public Result leaveExploration(MechanicsState state) {
        if(state.characterId().equals("chisa"))return new Result(true,"ok",InterruptionRules.cancelled(state),List.of());
        Transition t=new Transition(state,new Context(0,0,0,1,false,false,false,false));
        if(state.characterId().equals("iuno")&&!state.form().equals("normal"))NewSixMechanics.iunoExitTraversal(t);
        else if(state.characterId().equals("qingxiao")&&state.timer("qingxiao_flight")>0)NewSixMechanics.qingxiaoExitFlight(t);
        else if(state.characterId().equals("mornye")&&state.form().equals("observation"))exitObservation(t);
        else if(state.characterId().equals("aemeath")&&state.form().equals("mech")) {
            t.form="baseline";t.combo=1;t.clear("combined_thrust");t.clear("wing_window");t.effect(Kind.FORM,"baseline",0,0);
        }
        else if(state.characterId().equals("hsin")&&state.form().equals("fox")) {
            t.form="worldly";t.clear("fox_heal");t.effect(Kind.FORM,"worldly",0,0);
        } else if(state.characterId().equals("hsin")&&state.form().equals("illuminated")) {
            t.form="worldly";t.combo=1;t.clear("hsin_barrier_charges");t.effect(Kind.FORM,"worldly",0,0);
        } else if(state.characterId().equals("cartethyia")&&state.form().equals("fleurdelys")) {
            // Temporary small form pauses manifestation; it does not spend resolve or the inherited rights.
            t.form="cartethyia";t.combo=1;t.effect(Kind.FORM,"cartethyia",0,0);
        } else return new Result(true,"no_exploration_state",state,List.of());
        return new Result(true,"ok",t.build(),t.effects);
    }
    /** Frozen Hsin electro mode: one +150 bonus on an actual word-lock collapse per transformation. */
    public MechanicsState wordLockCollapsed(MechanicsState state,boolean ownerActive) {
        if(!state.characterId().equals("hsin")||!state.mode().contains("electro")||!ownerActive
            ||!state.form().equals("illuminated")||state.timer("palaces")>0||state.timer("hsin_word_lock_bonus_ready")==0)return state;
        var next=grantResource(state,"illuminated_heart",150);var timers=new HashMap<>(next.timers());timers.remove("hsin_word_lock_bonus_ready");
        return new MechanicsState(next.characterId(),next.form(),next.mode(),next.chain(),next.nextBasic(),next.resources(),timers);
    }
    /** Source 1608: returning to the conductor ends the remaining performance,
     * even on an ordinary switch without enough concerto for an intro. */
    public Result onTeamEntry(MechanicsState state, Context context) {
        if(!state.characterId().equals("phrolova")||!state.form().equals("conducting")&&state.timer("phrolova_return_curtain")==0)
            return new Result(true,"no_entry_replacement",state,List.of());
        Transition t=new Transition(state,context);
        CharacterBranches.endConducting(t,true);
        return new Result(true,"phrolova_return_curtain",t.build(),t.effects);
    }

    /** MC portal cleanup ends world-bound conducting without attacking the destination world. */
    public Result leaveWorld(MechanicsState state) {
        if(state.characterId().equals("ciaccona")||state.characterId().equals("shorekeeper")){
            var next=state.characterId().equals("ciaccona")?NewAeroMechanics.leaveWorld(state):NewThreeMechanics.leaveShorekeeperWorld(state);
            return new Result(true,"ok",next,List.of(new Effect(Kind.FORM,next.form(),0,0,Map.of())));
        }
        if(state.characterId().equals("lucilla")||state.characterId().equals("augusta")){
            var next=NewFiveMechanics.leaveWorld(state);
            return new Result(true,"ok",next,List.of(new Effect(Kind.FORM,next.form(),0,0,Map.of())));
        }
        if(state.characterId().equals("zhezhi"))return new Result(true,"ok",NewFiveMechanics.leaveWorld(state),List.of());
        if(Set.of("changli","carlotta","iuno","lupa","denia","qingxiao").contains(state.characterId())) {
            Result exploration=leaveExploration(InterruptionRules.cancelled(state));
            return new Result(true,"ok",NewSixMechanics.leaveWorld(exploration.state()),exploration.effects());
        }
        if(Set.of("phoebe","suisui","cantarella").contains(state.characterId()))
            return new Result(true,"ok",NewTideMechanics.leaveWorld(InterruptionRules.cancelled(state)),List.of());
        if(!state.characterId().equals("phrolova")||!state.form().equals("conducting")&&state.timer("phrolova_return_curtain")==0)return leaveExploration(state);
        Transition t=new Transition(state,new Context(0,0,0,1,false,false,false,false));
        CharacterBranches.endConducting(t,false);
        return new Result(true,"ok",t.build(),t.effects);
    }

    private void tick(Transition t) {
        for (String key : List.copyOf(t.timers.keySet())) {
            // Frozen 1608 skills 1/2/7 describe acquired qualifications, without a duration.
            // Preserve positive legacy values too: old saves used an unsupported 60s timer.
            if(t.original.characterId().equals("phrolova")&&(key.equals("reincarnation")||key.equals("final_note")))continue;
            // 1108 skills 2/3: next-basic3 bonus ends on switch/consumption;
            // heavy unlock is not the separate 25-second liberation cooldown.
            if(t.original.characterId().equals("hiyuki")&&(key.equals("mind_skill")||key.equals("see_mind_ready")))continue;
            if(t.original.characterId().equals("yangyang_xuanling")&&key.equals("thread_pearl"))continue;
            if(key.equals("hsin_no_alarm")||CharacterLifecycleRules.COUNTERS.contains(key)||NewAeroMechanics.COUNTERS.contains(key)||NewThreeMechanics.COUNTERS.contains(key)||NewTideMechanics.COUNTERS.contains(key)||NewSixMechanics.COUNTERS.contains(key)||NewFiveMechanics.COUNTERS.contains(key)||LucillaMechanics.COUNTERS.contains(key)||ZaniMechanics.COUNTERS.contains(key)||AugustaMechanics.COUNTERS.contains(key)||ChisaTraversalRules.COUNTERS.contains(key)||SoloEffectRules.COUNTERS.contains(key)||OutroRules.COUNTERS.contains(key)||UnisonRules.COUNTERS.contains(key))continue;
            if (NON_DECAYING_TIMERS.contains(key)) continue;
            if(SuomingMechanics.COUNTERS.contains(key))continue;
            t.timer(key, Math.max(0, t.time(key) - 1));
        }
        if (!t.has("combo_window")&&!ComboContinuationRules.retainsCursor(t.original.characterId(),t.form,t.time("listening"))) t.combo = 1;
        CharacterLifecycleRules.tick(t);
        if(t.original.characterId().equals("denia")||t.original.characterId().equals("qingxiao")) {
            boolean conscious=t.context.maxHealthNative()==0||t.context.currentHealthNative()>0;
            if(t.context.inCombat()&&conscious) {
                if(!t.has("was_in_combat")) {
                    if(t.original.characterId().equals("denia"))NewSixMechanics.deniaEnterCombat(t);
                    else NewSixMechanics.qingxiaoEnterCombat(t);
                }
                t.timer("was_in_combat",1);
            }else t.clear("was_in_combat");
        }
        if (t.original.characterId().equals("lynae")) {
            // Air-heavy eligibility survives form exit; only active landing (or C6 leap) refreshes it.
            if (t.context.active()&&!t.context.airborne()) t.clear("lynae_air_heavy_used");
            // Frozen C1 text: sampling, MORE than2s continuously out of combat,
            // then at most once every2s. Leaving the condition cancels waiting.
            if (t.context.inCombat() || !t.form.equals("sampling")) t.clear("out_of_combat_ticks");
            else if (t.original.chain() >= 1) {
                t.timer("out_of_combat_ticks", Math.min(41, t.time("out_of_combat_ticks") + 1));
                if (t.time("out_of_combat_ticks") > 40 && !t.has("lynae_c1_recovery_cooldown")) {
                    t.add("overflow", 120); t.timer("lynae_c1_recovery_cooldown", 40);
                }
            }
            if (t.has("converting") && !t.has("conversion_pulse")) {
                double consume = Math.min(15, t.get("overflow"));
                double cap = resourceCaps("lynae", t.original.chain()).get("lumiflow");
                t.add("overflow", -consume); t.add("lumiflow", cap * .125 * consume / 15); t.timer("conversion_pulse", 4);
                if (t.get("lumiflow") >= cap || t.get("overflow") <= 0) finishConversion(t);
            } else if (t.form.equals("cruise")) {
                double flowRate=resourceCaps("lynae",t.original.chain()).get("lumiflow")*.01;
                if (!t.context.active() || (!t.context.airborne() && !t.context.moving() && !t.has("flow_gain"))) t.add("lumiflow", -flowRate);
                else if ((!t.context.airborne() && t.context.moving()) || t.has("flow_gain")) t.add("lumiflow", flowRate);
                if (t.context.active()&&!t.context.airborne()) t.clear("leap_count");
            }
        }
        if (t.original.characterId().equals("mornye")) {
            if(t.original.timer("bounded")==1&&!t.has("bounded")) {
                t.clear("bounded_uses");
                t.effect(Kind.HEAL_ACTIVE,"bounded_proof_removed",t.context.defense()*1.5,0);
            }
            if (t.form.equals("observation")) {
                t.effect(Kind.STAMINA_REGEN_LOCK, "observation", 1, 1);
                if (!t.has("observation") || !t.context.active()
                    || !t.context.airborne()&&!t.has("observation_entry_grace")) exitObservation(t);
                else if(t.context.stamina()<.25)t.clear("flight_sprint");
                else if (t.context.moving())t.effect(Kind.PAY_STAMINA, "observation_move", .25, 0);
            }
            boolean finalFieldInterval=t.original.timer("field")>0&&t.original.timer("field_heal")==1;
            if ((t.has("field")||finalFieldInterval) && !t.has("field_heal")) {
                double heal = 76 + .2024 * t.context.defense();
                boolean strong=t.has("strong_field")||finalFieldInterval&&t.original.timer("strong_field")>0;
                if (strong) heal *= 1.4;
                if (t.original.chain() >= 4 && strong) heal *= 1.3;
                t.effect(Kind.HEAL_ACTIVE, "mornye_field", heal, 0,
                    t.original.timer("field")==1?Map.of("completeFieldInterval",1d):Map.of()); t.timer("field_heal", 60);
            }
            if(!t.has("field"))t.clear("field_heal");
        }
        ExtendedMechanics.tick(t);
    }
    private void switchOut(Transition t) {
        // The source exempts Mirage, Incarnation and Listening sequences from
        // resets. Leaving the field does not end these timed forms.
        t.combo = ComboContinuationRules.retainsCursor(t.original.characterId(),t.form,t.time("listening"))
            ? Math.clamp(t.combo,1,t.original.characterId().equals("jinhsi")?4:3) : 1;
        t.clear("combo_window"); t.clear("stance_basic_window"); t.clear("block");
        t.clear("hiyuki_basic3_window"); t.clear("qiuyuan_answer");
        t.clear(LynaeCamouflageRules.TIMER);
        t.clear(AemeathTravelRules.TIMER);
        if (t.original.characterId().equals("mornye") && t.form.equals("observation")) exitObservation(t);
        if (t.original.characterId().equals("lynae")) {
            t.clear("lynae_basic2_followup");
            t.clear("leap_count"); t.clear("converting"); t.clear("conversion_pulse");
            t.effect(Kind.BUFF_SELF, "conversion_damage_reduction", 0, 0);
            t.effect(Kind.BUFF_SELF, "conversion_uninterruptible", 0, 0);
        }
        ExtendedMechanics.switchOut(t);
    }

    /** Hsin C1: two charges from the actual transformation, one charge per second.
     * The host identifies monster attacks and passes positive post-native-shield damage.
     * The granted one-second damage reduction continues for its own lifetime on later damage.
     */
    public DamageResult hsinBarrier(MechanicsState owner,double damage,boolean monsterAttack) {
        if(!Double.isFinite(damage)||damage<0)throw new IllegalArgumentException("Invalid barrier damage");
        if(damage==0||!owner.characterId().equals("hsin")||owner.chain()<1)return new DamageResult(damage,owner,List.of());
        Transition t=new Transition(owner,new Context(0,0,0,1,false,false,true,true));
        if(!t.has("hsin_barrier_protection")&&monsterAttack&&t.form.equals("illuminated")
                &&t.time("hsin_barrier_charges")>0&&!t.has("hsin_barrier_cooldown")) {
            t.timer("hsin_barrier_charges",t.time("hsin_barrier_charges")-1);
            t.timer("hsin_barrier_protection",20);t.timer("hsin_barrier_cooldown",20);
        }
        return new DamageResult(damage*(t.has("hsin_barrier_protection")?.4:1),t.build(),List.of());
    }

    /** Apply to the own-team active target BEFORE totem. No assumptions about Minecraft health scale. */
    public DamageResult boundedProof(MechanicsState owner, double damage, double currentHealth,
                                     double maxHealth, double mornyeDefense) {
        if (damage < 0 || currentHealth < 0 || maxHealth <= 0 || !Double.isFinite(damage)
                || !Double.isFinite(currentHealth) || !Double.isFinite(maxHealth) || !Double.isFinite(mornyeDefense))
            throw new IllegalArgumentException("Invalid bounded-proof damage");
        if (!owner.characterId().equals("mornye") || owner.timer("bounded") <= 0)
            return new DamageResult(damage, owner, List.of());
        Transition t = new Transition(owner, new Context(0, 0, mornyeDefense, 1, false, false, true, true));
        boolean remove = false;
        double result = damage;
        if (result > maxHealth * .30 && t.time("bounded_uses") > 0) {
            result = maxHealth * .30;
            t.timer("bounded_uses", t.time("bounded_uses") - 1);
            remove = t.time("bounded_uses") == 0;
        }
        if (result >= currentHealth && currentHealth > 0) {
            result = Math.max(0, currentHealth - Math.min(1, maxHealth * .0001));
            remove = true;
        }
        if (remove) {
            t.clear("bounded"); t.clear("bounded_uses");
            t.effect(Kind.HEAL_ACTIVE, "bounded_proof_removed", mornyeDefense * 1.5, 0);
        }
        return new DamageResult(result, t.build(), t.effects);
    }

    static final class Rejected extends RuntimeException { Rejected(String reason) { super(reason); } }
    static final class Transition {
        final MechanicsState original;
        final Context context;
        final Map<String, Double> resources;
        final Map<String, Integer> timers;
        final List<Effect> effects = new ArrayList<>();
        String form;
        int combo;
        Transition(MechanicsState state, Context context) {
            original = state; this.context = context; form = state.form(); combo = state.nextBasic();
            resources = new HashMap<>(state.resources()); timers = new HashMap<>(state.timers());
        }
        double get(String key) { return resources.getOrDefault(key, 0.0); }
        void set(String key, double value) {
            resources.put(key, Math.clamp(value, 0, resourceCaps(original.characterId(), original.chain()).get(key)));
            if (original.characterId().equals("phrolova") && key.equals("echoes") && value <= 0) resources.remove("echo_overflow");
        }
        void add(String key, double value) {
            double raw = get(key) + value;
            if (original.characterId().equals("phrolova") && key.equals("echoes") && value > 0) {
                resources.put("echo_overflow", Math.min(100,get("echo_overflow") + Math.max(0,raw - 24)));
            }
            set(key, raw);
        }
        int time(String key) { return timers.getOrDefault(key, 0); }
        boolean has(String key) { return time(key) > 0; }
        void timer(String key, int ticks) { if (ticks <= 0) timers.remove(key); else timers.put(key, ticks); }
        void clear(String key) { timers.remove(key); }
        void requireCooldown(String key) { if (has(key)) throw new Rejected("cooldown:" + key); }
        void action(String id) { action(id,Map.of()); }
        void action(String id,Map<String,Double> qualification) { effect(Kind.ACTION, original.characterId() + "/" + id, 0, 0,qualification); timer("combo_window", 60); }
        void payEnergy(double amount) { if (context.energy() < amount) throw new Rejected("energy_missing"); effect(Kind.PAY_ENERGY, "energy", amount, 0); }
        void payStamina(double amount) { if (context.stamina() < amount) throw new Rejected("stamina_missing"); effect(Kind.PAY_STAMINA, "stamina", amount, 0); }
        void effect(Kind kind, String id, double amount, int duration) { effect(kind, id, amount, duration, Map.of()); }
        void effect(Kind kind, String id, double amount, int duration, Map<String, Double> values) { effects.add(new Effect(kind, id, amount, duration, values)); }
        MechanicsState build() { return new MechanicsState(original.characterId(), form, original.mode(), original.chain(), combo, resources, timers); }
    }
}
