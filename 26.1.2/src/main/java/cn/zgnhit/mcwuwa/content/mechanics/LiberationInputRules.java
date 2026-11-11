package cn.zgnhit.mcwuwa.content.mechanics;

import cn.zgnhit.mcwuwa.core.LiberationGesture.Mode;

/** The server advertises eligibility; the gesture never grants it. */
public final class LiberationInputRules {
    private LiberationInputRules(){ }
    public static Mode mode(MechanicsState state){
        if(state.characterId().equals("augusta")&&(state.resource("majesty")>=2||state.form().equals("realm")))return Mode.HOLD_ALTERNATIVE;
        if(state.characterId().equals("hiyuki")&&state.form().equals("future"))return Mode.CHARGE_RELEASE;
        if(state.characterId().equals("phrolova")&&!state.form().equals("conducting")&&state.timer("final_note")>0)return Mode.HOLD_ALTERNATIVE;
        return Mode.IMMEDIATE;
    }
}
