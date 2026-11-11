package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.HashMap;

/** Frozen1508/node1: first in-action basic adds damage, second advances the cut.
 * The two-tick debounce is MC input timing; it is not a recovered original frame. */
public final class ChisaScissorsRules {
    public enum Press { NONE, EXTRA, CLOSE }
    private ChisaScissorsRules() { }
    public static Press press(String action,long elapsed,int finalHit,boolean extraPressed,boolean closed) {
        if(!"chisa/severing_pincers".equals(action)||elapsed<2||elapsed>=finalHit||closed)return Press.NONE;
        return extraPressed?Press.CLOSE:Press.EXTRA;
    }
    public static MechanicsState actionStarted(MechanicsState state,String action,int duration) {
        if(!state.characterId().equals("chisa"))return state;
        var timers=new HashMap<>(state.timers());
        if(action.equals("chisa/severing_pincers"))timers.put("chisa_pincers_active",duration+2);
        else timers.remove("chisa_pincers_active");
        return new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),state.nextBasic(),state.resources(),timers);
    }
}
