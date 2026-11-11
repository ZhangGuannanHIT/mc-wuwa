package cn.zgnhit.mcwuwa.content.mechanics;

import cn.zgnhit.mcwuwa.core.CombatControl.Protection;
import java.util.HashMap;
import java.util.Set;

/** Explicit 3.7 source descriptions mapped to the existing action/state IDs; no hidden frame armor is inferred. */
public final class InterruptionRules {
    private InterruptionRules() { }
    private static final Set<String> BODY_INPUT_TIMERS = Set.of("phoebe_staff_dive","phoebe_staff_glide","cantarella_surf","shore_unbound","shore_calculated","shore_calculation","gal_raining","qiuyuan_dash","qiuyuan_dash_ended","qiuyuan_counter_window","qiuyuan_skill_counter","qiuyuan_skill_armor","charging", "converting", "conversion_pulse", "block", "iai_guard", "hiyuki_heavy_guard", "hiyuki_last_arrow_pending", "combo_window", "stance_basic_window","iai_dodge_window","iai_hold_window","return_charge","return_charge_spent","return_charge_pulse","return_charge_release","return_charge_wait","rover_strangle_charging","chisa_saw_stage");
    public static Protection protection(MechanicsState state, String fullAction) {
        String action = fullAction.substring(fullAction.lastIndexOf('/') + 1); int chain = state.chain();
        return switch (state.characterId()) {
            case "suoming" -> state.form().equals("calamity")||state.timer("suoming_counter_armor")>0||chain>=1&&action.equals("skill_release")?Protection.UNINTERRUPTIBLE:Protection.NONE;
            case "augusta" -> chain>=1&&Set.of("skill_rush","skill_leap","skill_descent").contains(action)?Protection.UNINTERRUPTIBLE:Protection.NONE;
            case "zani" -> state.timer("zani_guard_mode")>=2||chain>=1&&Set.of("heavy_night","heavy_night_last").contains(action)?Protection.UNINTERRUPTIBLE:Protection.NONE;
            case "lucilla" -> action.equals("skill_chase")||state.timer("lucilla_finale_lock")>0
                ||chain>=1&&(action.equals("skill")||state.timer("lucilla_focus_channel")==2||action.equals("memory_basic_03"))
                ?Protection.UNINTERRUPTIBLE:state.form().equals("memory")?Protection.INCREASED:Protection.NONE;
            case "changli" -> chain>=1&&(action.equals("skill")||action.startsWith("heavy_flaming_sacrifice"))?Protection.INCREASED:Protection.NONE;
            case "iuno" -> chain>=1&&(action.startsWith("skill_beyond")||action.startsWith("heavy_perfection"))?Protection.UNINTERRUPTIBLE:Protection.NONE;
            case "lupa" -> chain>=1&&action.startsWith("skill_wolf_dance_extreme")?Protection.UNINTERRUPTIBLE:
                Set.of("heavy_wolf_bite","heavy_sharp_claw","air_flame_strike").contains(action)?Protection.INCREASED:Protection.NONE;
            case "denia" -> chain>=1&&(action.equals("skill")||action.equals("skill_c3_full_core")||action.startsWith("skill_exile")||action.matches("disillusion_(air_)?basic_0[34](_enhanced)?"))?Protection.UNINTERRUPTIBLE:Protection.NONE;
            case "qingxiao" -> state.timer("qingxiao_guard_cd")>0?Protection.UNINTERRUPTIBLE:Protection.NONE;
            case "suisui" -> state.form().equals("rain")?(chain>=1&&(action.startsWith("rain_basic_")||action.equals("rain_heavy")||action.startsWith("rain_descent"))?Protection.UNINTERRUPTIBLE:Protection.INCREASED):Protection.NONE;
            case "cantarella" -> chain>=1&&action.equals("skill_drain")?Protection.UNINTERRUPTIBLE:Protection.NONE;
            case "sigrika" -> chain>=1&&Set.of("basic_insight","skill_insight","skill_sun").contains(action)?Protection.UNINTERRUPTIBLE:Protection.NONE;
            case "galbrena" -> chain>=1&&state.form().equals("demon")&&Set.of("demon_basic_05","demon_heavy_03","demon_air","demon_air_rain").contains(action)?Protection.UNINTERRUPTIBLE:state.timer("gal_inner")>0?Protection.INCREASED:Protection.NONE;
            case "qiuyuan" -> chain>=1&&action.startsWith("ink_heavy_")
                ||state.timer("qiuyuan_skill_armor")>0&&action.equals("skill")?Protection.UNINTERRUPTIBLE:Protection.NONE;
            case "ciaccona" -> state.timer("ciaccona_performance")>0||chain>=1&&state.timer("ciaccona_skill_armor")>0?Protection.UNINTERRUPTIBLE:Protection.NONE;
            case "lynae" -> state.timer("converting") > 0
                || chain >= 1 && (action.startsWith("phantom_leap_") || action.equals("visual_impact"))
                || chain >= 6 && action.equals("cruise_air_heavy") ? Protection.UNINTERRUPTIBLE : Protection.NONE;
            case "mornye" -> chain >= 1 && action.startsWith("observation_basic_") ? Protection.UNINTERRUPTIBLE : Protection.NONE;
            case "camellya" -> chain >= 1 && action.equals("ephemeral") || chain >= 6 && action.equals("immortal_blossom")
                ? Protection.UNINTERRUPTIBLE : action.startsWith("basic_") || action.startsWith("bloom_basic_") || action.equals("bloom_spinning")
                    ? Protection.INCREASED : Protection.NONE;
            case "yangyang_xuanling" -> chain >= 1 && (action.equals("heavy") || action.startsWith("listening_basic_") || action.startsWith("listening_counter_"))
                ? Protection.UNINTERRUPTIBLE : Protection.NONE;
            case "hsin" -> chain>=1&&state.timer("hsin_barrier_protection")>0
                || action.equals("worldly_step") || action.equals("worldly_suppress") ? Protection.UNINTERRUPTIBLE
                : state.timer("heart_state") > 0 ? Protection.INCREASED : Protection.NONE;
            case "hiyuki" -> chain >= 1 && (action.equals("future_basic_04") || action.equals("future_basic_05")) ? Protection.UNINTERRUPTIBLE : Protection.NONE;
            case "chisa" -> chain >= 1 && ChisaSawRules.empoweredAttack(fullAction)
                ? Protection.UNINTERRUPTIBLE : Protection.NONE;
            case "aero_rover" -> state.timer("interruption_rover_c1") > 0 ? Protection.INCREASED : Protection.NONE;
            case "cartethyia" -> state.form().equals("fleurdelys") && state.timer("manifestation") > 0
                && state.timer("human_right") > 0 ? Protection.INCREASED : Protection.NONE;
            default -> Protection.NONE;
        };
    }
    public static MechanicsState onAction(MechanicsState state, String action) {
        if (!state.characterId().equals("aero_rover") || state.chain() < 1 || !action.startsWith("aero_rover/wind_air_")) return state;
        var timers = new HashMap<>(state.timers()); timers.put("interruption_rover_c1", 60);
        return new MechanicsState(state.characterId(), state.form(), state.mode(), state.chain(), state.nextBasic(), state.resources(), timers);
    }
    public static boolean hasBodyInput(MechanicsState state) {
        if(state.characterId().equals("zani")&&state.timer("zani_guard_mode")>0)return true;
        if(state.characterId().equals("lucilla")&&(state.timer("lucilla_focus_channel")>0||state.timer("lucilla_memory_channel")>0||state.timer("lucilla_camera")>0))return true;
        if(state.characterId().equals("ciaccona")&&state.timer("ciaccona_solo")>0)return true;
        if(HsinTravelRules.wandering(state)||state.form().equals("fox")&&state.characterId().equals("hsin"))return true;
        return state.timer(AemeathTravelRules.TIMER)>0||BODY_INPUT_TIMERS.stream().anyMatch(key -> state.timer(key) > 0)
            ||ChisaTraversalRules.BODY_TIMERS.stream().anyMatch(key->state.timer(key)>0)
            ||CartethyiaAttackRules.BODY_TIMERS.stream().anyMatch(key->state.timer(key)>0);
    }
    /** Cancel unfinished input/combo only. Paid resources, cooldowns, persistent forms and independent-field timers survive. */
    public static MechanicsState cancelled(MechanicsState state) {
        var timers = new HashMap<>(state.timers()); BODY_INPUT_TIMERS.forEach(timers::remove);timers.remove(AemeathTravelRules.TIMER);
        timers.remove("qingxiao_air_stage");
        timers.remove("qingxiao_ascend_ready");
        if(state.characterId().equals("zani"))for(String key:Set.of("zani_guard_mode","zani_guard_age","zani_guard_life","zani_entry","zani_breakthrough"))timers.remove(key);
        if(state.characterId().equals("lucilla"))for(String key:Set.of("lucilla_camera","lucilla_focus_channel","lucilla_focus_elapsed","lucilla_fill_pending","lucilla_memory_channel","lucilla_forget_pulse","lucilla_memory_held","lucilla_memory_body_done","lucilla_memory_tap_repeat"))timers.remove(key);
        if(state.characterId().equals("suoming")){
            timers.remove("suoming_guard");timers.remove("suoming_heart_held");
            if(state.form().equals("calamity"))return new MechanicsState(state.characterId(),"normal",state.mode(),state.chain(),1,state.resources(),timers);
        }
        if(state.characterId().equals("sigrika")&&state.timer("sig_semantics_cast")>0){timers.remove("sig_semantics_cast");if(state.chain()<3)timers.remove("sig_talent");}
        timers.remove("hiyuki_basic3_window");
        timers.remove("iai_apply");
        timers.remove("qiuyuan_answer");
        timers.remove("ciaccona_followup");
        timers.remove("suisui_heavy_followup");
        timers.remove("phrolova_color_conversion");
        timers.remove("qiuyuan_final_reward");
        // These restrictions belong to the interrupted body action, not to
        // an independent cooldown. A new attack must be able to earn resources.
        timers.remove("mornye_inversion_lock");
        if(timers.remove("lynae_premix_stop")!=null)timers.remove("lynae_premix");
        timers.remove("lynae_basic2_followup");
        timers.remove("lupa_basic2_followup");
        // The source requires more than four seconds AFTER the heavy/finisher
        // stops. An interrupted body must not retain its unplayed duration.
        if(state.characterId().equals("aemeath")&&(state.timer("charging")>0||state.timer("aemeath_brilliance_wait")>81))
            timers.put("aemeath_brilliance_wait",81);
        // Cancel an actual pending landing callback, not persistent flight,
        // a form, or a source-defined next-action qualification.
        timers.remove("carlotta_plunging");
        timers.remove("hsin_wander");timers.remove("hsin_fox_window");
        ChisaTraversalRules.BODY_TIMERS.forEach(timers::remove);timers.remove("chisa_held_entry");timers.remove("chisa_cycle_followup");
        timers.remove("chisa_pincers_active");timers.remove("chisa_counter_hold");
        CartethyiaAttackRules.BODY_TIMERS.forEach(timers::remove);
        return new MechanicsState(state.characterId(), state.form(), state.mode(), state.chain(), ComboContinuationRules.afterCancellation(state), state.resources(), timers);
    }
    public static MechanicsState interrupted(MechanicsState state) {
        var cleared=cancelled(state);var timers=new HashMap<>(cleared.timers());
        // C3 preserves the bow sequence across an accepted Beyond cast, not
        // across an enemy interruption. Voluntary E replacement still uses
        // cancelled() and commits the already computed skill transition.
        boolean brokenBow=state.characterId().equals("iuno")&&state.form().equals("new_moon");
        if(brokenBow)timers.remove("iuno_bow_continuation");
        // Hellwalk preserves a voluntarily cancelled combo, not a combo
        // interrupted by an enemy later in its continuation window.
        boolean brokenHellwalk=state.characterId().equals("galbrena")&&state.timer("gal_hellwalk_continuation")>0;
        if(brokenHellwalk)timers.remove("gal_hellwalk_continuation");
        if(state.characterId().equals("galbrena")&&state.timer("gal_raining")>0)timers.put("gal_air_spent",Integer.MAX_VALUE);
        if(state.characterId().equals("chisa")&&state.timer("chisa_shattered_active")>0&&state.timer("chisa_shattered_retry_used")==0){
            timers.put("chisa_shattered_ready",1);timers.put("chisa_shattered_retry_used",1);
        }
        // A ten-tick MC hurt-recovery input window; the three-use cap is from the frozen source.
        if(state.characterId().equals("mornye")&&state.form().equals("observation"))timers.put("observation_hurt_window",10);
        // Keep form cleanup performed by cancelled(), especially Suoming's
        // action-bound calamity form. Reusing the input form resurrects it.
        return new MechanicsState(cleared.characterId(), cleared.form(), cleared.mode(), cleared.chain(), brokenBow||brokenHellwalk?1:cleared.nextBasic(), cleared.resources(), timers);
    }
    /** Source1306 early interruption repeats the current skill; late interruption
     * retains its next stage. First contact is the explicitly authored MC boundary. */
    public static MechanicsState interrupted(MechanicsState state,String action,long elapsed,int firstHit){
        var next=interrupted(state);
        if(!state.characterId().equals("augusta")||elapsed>Math.max(1,firstHit))return next;
        int retry=action.equals("augusta/skill_rush")?0:action.equals("augusta/skill_leap")?1:-1;
        if(retry<0)return next;
        var timers=new HashMap<>(next.timers());timers.put("augusta_skill_stage",retry);timers.put("augusta_skill_link",60);
        return new MechanicsState(next.characterId(),next.form(),next.mode(),next.chain(),next.nextBasic(),next.resources(),timers);
    }
}
