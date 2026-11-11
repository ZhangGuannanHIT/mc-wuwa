package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import static cn.zgnhit.mcwuwa.content.mechanics.MechanicsEngine.*;

/** Frozen 1508/node7: five entry conditions, >10 threshold, 50 debit and 100 own-kill grant.
 * Frame windows and movement speed are explicit MC adaptations, not recovered game frames.
 * No natural regeneration rate is invented here. All airborne flags require the host's physical context. */
public final class ChisaTraversalRules {
    public static final int GLIDE_TICKS=40, DASH_TICKS=8, EVADE_TICKS=8, RETRACT_WINDOW=12;
    public static final Set<String> COUNTERS=Set.of("chisa_shattered_ready","chisa_shattered_retry_used","chisa_shattered_active");
    public static final Set<String> BODY_TIMERS=Set.of("chisa_shattered_ready","chisa_shattered_retry_used","chisa_shattered_active","chisa_air_ready","chisa_launch_pending","chisa_knock_pending",
        "chisa_retract_window","chisa_glide","chisa_glide_held","chisa_glide_dash","chisa_glide_evade","chisa_glide_followup");
    private ChisaTraversalRules() { }
    private static MechanicsState with(MechanicsState state,Map<String,Double> resources,Map<String,Integer> timers) {
        return new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),state.nextBasic(),resources,timers);
    }
    public static MechanicsState actionStarted(MechanicsState state,String action,boolean airborne) {
        if(!state.characterId().equals("chisa")||state.form().equals("saw"))return state;
        var timers=new HashMap<>(state.timers());
        // Ground heavy actually launches in SpecialMovementRuntime. Allow a bounded launch handshake.
        if(!action.isEmpty())timers.remove("chisa_shattered_active");
        if(action.equals("chisa/heavy")){timers.put("chisa_air_ready",1);timers.put("chisa_launch_pending",8);timers.put("chisa_shattered_ready",1);timers.remove("chisa_shattered_retry_used");}
        if(action.equals("chisa/shattered_plane")){timers.remove("chisa_shattered_ready");timers.put("chisa_shattered_active",1);}
        if(airborne&&(action.equals("chisa/skill")||action.equals("chisa/pierce")))timers.put("chisa_air_ready",1);
        if(!action.isEmpty()) {
            timers.remove("chisa_glide");timers.remove("chisa_glide_held");timers.remove("chisa_glide_dash");timers.remove("chisa_glide_evade");
        }
        return with(state,state.resources(),timers);
    }
    public static MechanicsState actionCompleted(MechanicsState state,String action) {
        if(!state.characterId().equals("chisa")||state.form().equals("saw"))return state;
        var timers=new HashMap<>(state.timers());
        if(action.equals("chisa/retract"))timers.put("chisa_retract_window",RETRACT_WINDOW);
        if(action.equals("chisa/shattered_plane"))timers.remove("chisa_shattered_active");
        return with(state,state.resources(),timers);
    }
    /** An actual received direct hit may qualify only if subsequent physical motion leaves the ground. */
    public static MechanicsState receivedHit(MechanicsState state) {
        if(!state.characterId().equals("chisa")||state.form().equals("saw"))return state;
        var timers=new HashMap<>(state.timers());timers.put("chisa_knock_pending",8);
        return with(state,state.resources(),timers);
    }
    public static Result begin(MechanicsState state,Context context,boolean forward) {
        if(!state.characterId().equals("chisa")||state.form().equals("saw")||!context.active()
            ||state.resource("lifeline")<=10)return new Result(false,"no_lifeline_glide",state,java.util.List.of());
        boolean air=context.airborne()&&state.timer("chisa_air_ready")>0;
        boolean retract=forward&&state.timer("chisa_retract_window")>0;
        if(!air&&!retract)return new Result(false,"outside_lifeline_entry",state,java.util.List.of());
        var resources=new HashMap<>(state.resources());resources.put("lifeline",Math.max(0,state.resource("lifeline")-(state.chain()>=5?25:50)));
        var timers=new HashMap<>(state.timers());timers.remove("chisa_retract_window");timers.remove("chisa_knock_pending");
        timers.put("chisa_air_ready",1);timers.put("chisa_glide_followup",1);timers.put("chisa_glide",GLIDE_TICKS);
        timers.put("lifeline_recovery_lock",40);
        timers.put("chisa_glide_held",GLIDE_TICKS);timers.put("chisa_glide_dash",DASH_TICKS);timers.put("chisa_glide_evade",EVADE_TICKS);
        if(!context.airborne())timers.put("chisa_launch_pending",8);
        return new Result(true,"ok",with(state,resources,timers),java.util.List.of(
            new Effect(Kind.MOVE_SPECIAL,"chisa_glide",1,GLIDE_TICKS,Map.of()),
            new Effect(Kind.PULL_AREA,"chisa_glide_scar",.18,0,Map.of("radius",6d))));
    }
    public static MechanicsState release(MechanicsState state) {
        if(!state.characterId().equals("chisa"))return state;
        var timers=new HashMap<>(state.timers());timers.remove("chisa_glide_held");
        // The initial short dash and its independently bounded evasion window survive release.
        return with(state,state.resources(),timers);
    }
    public static MechanicsState confirmedEvade(MechanicsState state) {
        var timers=new HashMap<>(state.timers());timers.remove("chisa_glide_evade");return with(state,state.resources(),timers);
    }
    public static boolean evadeWindow(MechanicsState state) {
        return state!=null&&state.characterId().equals("chisa")&&!state.form().equals("saw")&&state.timer("chisa_glide_evade")>0;
    }
    public static MechanicsState ownKill(MechanicsState state) {
        if(!state.characterId().equals("chisa"))return state;
        var resources=new HashMap<>(state.resources());resources.put("lifeline",100d);return with(state,resources,state.timers());
    }
    static void tick(Transition t) {
        if(!t.context.active()||t.form.equals("saw")){BODY_TIMERS.forEach(t::clear);return;}
        if(t.context.airborne())t.clear("chisa_launch_pending");
        if(t.context.airborne()&&t.has("chisa_knock_pending")){t.timer("chisa_air_ready",1);t.clear("chisa_knock_pending");}
        if(!t.context.airborne()&&!t.has("chisa_launch_pending")) {
            for(String id:BODY_TIMERS)if(!id.equals("chisa_retract_window"))t.clear(id);
        }
        if(!t.has("chisa_glide"))t.clear("chisa_glide_held");
        if(t.has("chisa_glide"))t.effect(Kind.STAMINA_REGEN_LOCK,"chisa_glide",1,1);
    }
}
