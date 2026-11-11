package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.HashMap;
import static cn.zgnhit.mcwuwa.content.mechanics.MechanicsEngine.*;

/** Frozen 1311 nodes1/2: fox qualification and the interruptible aerial held attack.
 * Four-second entry/combo windows and35-tick maximum hover are explicit MC timing.
 * The25 scheduled hits and5 entry +5/s stamina come from the source, not a loop. */
public final class HsinTravelRules {
    private HsinTravelRules() { }
    /** Fox traversal has already replaced the skill body; its exit attack must not
     * be swallowed by the still-running entry skill's authored recovery frames. */
    public static boolean canReplace(MechanicsState state,String current,MechanicsEngine.Input input) {
        return state!=null&&state.characterId().equals("hsin")&&state.form().equals("fox")
            &&"hsin/skill".equals(current)&&java.util.Set.of(MechanicsEngine.Input.BASIC,MechanicsEngine.Input.SKILL,MechanicsEngine.Input.HEAVY_START).contains(input);
    }
    public static boolean wandering(MechanicsState state) {return state!=null&&state.characterId().equals("hsin")&&state.timer("hsin_wander")>0;}
    public static boolean quiet(MechanicsState state,boolean combat,boolean active) {
        return active&&!combat&&state!=null&&state.characterId().equals("hsin")&&state.form().equals("fox")&&state.timer("hsin_no_alarm")>0;
    }
    public static MechanicsState leaveFox(MechanicsState state) {
        if(state==null||!state.characterId().equals("hsin"))return state;
        var timers=new HashMap<>(state.timers());timers.remove("hsin_fox_window");timers.remove("fox_heal");timers.remove("hsin_no_alarm");
        return new MechanicsState(state.characterId(),state.form().equals("fox")?"worldly":state.form(),state.mode(),state.chain(),state.nextBasic(),state.resources(),timers);
    }
    static boolean release(Transition t) {
        if(!t.has("hsin_wander"))return false;
        t.clear("hsin_wander");t.clear("charging");
        t.payStamina(Math.min(30,t.context.stamina()));t.action("air_wander");t.combo=2;t.timer("combo_window",80);return true;
    }
    static void tick(Transition t) {
        if(t.context.inCombat())t.clear("hsin_no_alarm");
        if(t.has("hsin_fox_window")&&t.form.equals("worldly")&&t.context.active()&&t.context.moving()&&!t.context.airborne()) {
            ExtendedMechanics.form(t,"fox",0);t.clear("hsin_fox_window");t.timer("fox_heal",60);
            if(!t.context.inCombat())t.timer("hsin_no_alarm",1);
        }
        if(!wandering(t.original))return;
        if(!t.context.active()||!t.context.airborne()) {t.clear("hsin_wander");t.clear("charging");return;}
        // Exhaustion ends the held attack; only a release requests its plunge.
        // Keep the authored maximum-duration fallback separate from exhaustion.
        if(t.context.stamina()<.25) {
            t.clear("hsin_wander");t.clear("charging");return;
        }
        if(!t.has("hsin_wander")) {
            t.clear("hsin_wander");t.clear("charging");t.payStamina(Math.min(30,t.context.stamina()));
            t.action("air_wander");t.combo=2;t.timer("combo_window",80);
        } else t.payStamina(.25);
    }
}
