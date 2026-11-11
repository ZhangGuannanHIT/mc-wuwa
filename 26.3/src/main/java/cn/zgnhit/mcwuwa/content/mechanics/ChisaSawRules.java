package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.HashMap;
import static cn.zgnhit.mcwuwa.content.mechanics.MechanicsEngine.Input;

/** Frozen1508 node7: release/back dodge folds stage2/counter; release stage3 falls.
 * A two-tick input cancellation boundary is MC timing, not an original game frame claim.
 */
public final class ChisaSawRules {
    private ChisaSawRules(){ }
    /** Node7 saw attacks, not the skill that merely enters saw mode. Shared by C1/C3/C6. */
    public static boolean empoweredAttack(String action) {
        String local=action.substring(action.lastIndexOf('/')+1);
        return local.startsWith("saw_basic_")||local.startsWith("saw_resonance_")
            ||local.equals("saw_counter")||local.equals("saw_counter_hold")||local.equals("saw_finale");
    }
    public static double toughnessMultiplier(int chain,String action) {
        return chain>=3&&empoweredAttack(action)?1.5:1;
    }
    public static int stage(String action) {
        if(action.equals("chisa/saw_counter")||action.equals("chisa/saw_counter_hold")||action.equals("chisa/saw_basic_02")||action.equals("chisa/saw_basic_02_hold"))return 2;
        if(action.equals("chisa/saw_basic_03")||action.equals("chisa/saw_basic_03_hold"))return 3;
        return 0;
    }
    public static MechanicsState actionStarted(MechanicsState state,String action) {
        if(!state.characterId().equals("chisa"))return state;
        var timers=new HashMap<>(state.timers());int stage=stage(action);
        if(!action.equals("chisa/saw_counter"))timers.remove("chisa_counter_hold");
        if(stage==0)timers.remove("chisa_saw_stage");else timers.put("chisa_saw_stage",stage);
        return new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),state.nextBasic(),state.resources(),timers);
    }
    public static boolean canCancel(MechanicsState state,String action,Input input,long elapsed) {
        if(state.characterId().equals("chisa")&&state.form().equals("saw")&&state.timer("chisa_held_entry")>0
            &&action.equals("chisa/skill_saw_hold")&&input==Input.SKILL_RELEASE&&elapsed>=0)return true;
        if(!state.characterId().equals("chisa")||!state.form().equals("saw")||elapsed<2)return false;
        int stage=stage(action);
        return input==Input.BASIC_RELEASE&&(stage==2||stage==3)
            || input==Input.BACK_DODGE&&stage==2
            || input==Input.HEAVY_START&&(action.equals("chisa/saw_basic_01")||action.equals("chisa/saw_counter"));
    }
    /** A real mouse click can deliver press/release in the same server tick. Preserve the
     * release until the existing two-tick boundary; do not loosen that boundary or queue
     * arbitrary inputs for unrelated actions. The host binds it to the current start/weapon. */
    public static boolean deferRelease(MechanicsState state,String action,Input input,long elapsed) {
        return state.characterId().equals("chisa")&&state.form().equals("saw")
            &&input==Input.BASIC_RELEASE&&stage(action)>0&&elapsed>=0&&elapsed<2;
    }
}
