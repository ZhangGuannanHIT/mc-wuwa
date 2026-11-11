package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.HashMap;
import static cn.zgnhit.mcwuwa.content.mechanics.MechanicsEngine.*;

/** Frozen character 1509 node 5: cruise, out of combat, ground backward dodge, 15 seconds.
 * Only a server-accepted normal dodge may call begin; this never grants an evade or movement. */
public final class LynaeCamouflageRules {
    public static final String TIMER="optical_camouflage";
    public static final int DURATION_TICKS=300;
    private LynaeCamouflageRules() { }
    public static MechanicsState begin(MechanicsState state,Context context,boolean backwards) {
        if(!state.characterId().equals("lynae")||!state.form().equals("cruise")||!context.active()
            ||context.airborne()||context.inCombat()||!backwards)return state;
        var timers=new HashMap<>(state.timers());timers.put(TIMER,DURATION_TICKS);
        return new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),state.nextBasic(),state.resources(),timers);
    }
    public static MechanicsState clear(MechanicsState state) {
        if(state.timer(TIMER)==0)return state;
        var timers=new HashMap<>(state.timers());timers.remove(TIMER);
        return new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),state.nextBasic(),state.resources(),timers);
    }
    static void input(Transition t,Input input) {
        if(t.original.characterId().equals("lynae")&&input!=Input.TICK&&input!=Input.DODGE&&input!=Input.BACK_DODGE)
            t.clear(TIMER); // Accepted non-dodge skills and switching; rejected inputs do not commit this.
    }
    static void tick(Transition t) {
        if(t.original.characterId().equals("lynae")&&(!t.context.active()||t.context.inCombat()||!t.form.equals("cruise")))t.clear(TIMER);
    }
}
