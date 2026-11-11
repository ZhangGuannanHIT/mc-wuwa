package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.HashMap;
import java.util.Set;

/** Frozen frostburn forte: dodge after eligible attacks; hold skill only after jade/falling skill.
 * The 2-tick cancel delay and 30-tick post-action window are explicit Minecraft adaptations.
 */
public final class HiyukiIaiRules {
    private HiyukiIaiRules() { }
    private static final Set<String> DODGE_ACTIONS=Set.of("future_basic_01","future_basic_02","future_basic_03",
        "future_basic_04","future_basic_05","future_heavy","future_air_01","future_air_02","future_air",
        "future_dodge_counter","skill_jade","skill_falling","iai","intro");
    private static String local(String full) { return full.startsWith("hiyuki/")?full.substring(7):""; }
    public static boolean eligible(String action,MechanicsEngine.Input input) {
        String id=local(action);
        return input==MechanicsEngine.Input.DODGE?DODGE_ACTIONS.contains(id)
            :input==MechanicsEngine.Input.SKILL_HOLD&&(id.equals("skill_jade")||id.equals("skill_falling"));
    }
    public static MechanicsState actionStarted(MechanicsState state,String action,int durationTicks) {
        if(!state.characterId().equals("hiyuki"))return state;
        var timers=new HashMap<>(state.timers());timers.remove("iai_dodge_window");timers.remove("iai_hold_window");
        if(state.form().equals("future")) {
            if(eligible(action,MechanicsEngine.Input.DODGE))timers.put("iai_dodge_window",Math.addExact(durationTicks,30));
            if(eligible(action,MechanicsEngine.Input.SKILL_HOLD))timers.put("iai_hold_window",Math.addExact(durationTicks,30));
        }
        return new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),state.nextBasic(),state.resources(),timers);
    }
    static void acceptedAction(MechanicsEngine.Transition transition,String action) {
        if(!transition.form.equals("future"))return;
        transition.clear("iai_dodge_window");transition.clear("iai_hold_window");
        if(eligible(action,MechanicsEngine.Input.DODGE))transition.timer("iai_dodge_window",60);
        if(eligible(action,MechanicsEngine.Input.SKILL_HOLD))transition.timer("iai_hold_window",60);
    }
    public static boolean canCancel(MechanicsState state,String action,MechanicsEngine.Input input,long elapsed) {
        return state.form().equals("future")&&state.resource("cold")>=100&&elapsed>=2&&eligible(action,input)
            &&state.timer(input==MechanicsEngine.Input.DODGE?"iai_dodge_window":"iai_hold_window")>0;
    }
}
