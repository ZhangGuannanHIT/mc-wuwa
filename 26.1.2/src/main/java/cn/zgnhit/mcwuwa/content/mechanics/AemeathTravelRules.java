package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.HashMap;
import java.util.List;
import static cn.zgnhit.mcwuwa.content.mechanics.MechanicsEngine.*;

/** Source: mech form, radiance strictly greater than200 to start, continuous spending.
 * Missing travel rates are explicit MC authoring:6/s recovery or20/s flight consumption. */
public final class AemeathTravelRules {
    public static final String TIMER="aemeath_flight";
    private AemeathTravelRules() { }
    public static Result toggle(MechanicsState state){
        if(!state.characterId().equals("aemeath")||!state.form().equals("mech"))return new Result(false,"mech_required",state,List.of());
        var timers=new HashMap<>(state.timers());
        if(state.timer(TIMER)>0)timers.remove(TIMER);
        else {if(state.resource("radiance")<=200)return new Result(false,"radiance_above200_required",state,List.of());timers.put(TIMER,2);}
        return new Result(true,"ok",new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),state.nextBasic(),state.resources(),timers),List.of());
    }
    public static MechanicsState clear(MechanicsState state){
        if(state.timer(TIMER)==0)return state;var timers=new HashMap<>(state.timers());timers.remove(TIMER);
        return new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),state.nextBasic(),state.resources(),timers);
    }
    static void tick(Transition t){
        if(!t.original.characterId().equals("aemeath"))return;
        if(t.has(TIMER)){
            if(!t.context.active()||!t.form.equals("mech")){t.clear(TIMER);return;}
            t.add("radiance",-1);if(t.get("radiance")<=0)t.clear(TIMER);else t.timer(TIMER,2);
        }else t.add("radiance",.3);
    }
}
