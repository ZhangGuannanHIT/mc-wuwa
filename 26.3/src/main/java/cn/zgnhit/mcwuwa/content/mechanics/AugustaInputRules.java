package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.HashMap;

/** The host supplies the real successful-dodge window, never a client claim. */
public final class AugustaInputRules {
    private AugustaInputRules(){}
    public static MechanicsState skillCounter(MechanicsState state,MechanicsEngine.Input input,boolean successfulDodge){
        if(!state.characterId().equals("augusta")||!successfulDodge||input!=MechanicsEngine.Input.SKILL
            ||state.resource("power")<100||state.timer("augusta_skill_link")>0||!state.form().equals("normal"))return state;
        var timers=new HashMap<>(state.timers());timers.put("augusta_skill_counter",1);
        return new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),state.nextBasic(),state.resources(),timers);
    }
}
