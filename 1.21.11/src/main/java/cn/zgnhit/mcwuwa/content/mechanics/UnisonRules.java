package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.HashMap;
import java.util.Set;

/**131110—131112 and frozen1311/node7. Qualification is distinct from concerto energy. */
public final class UnisonRules {
    public static final Set<String> COUNTERS=Set.of("hsin_resolve","hsin_intro_enhanced","hsin_transform_unison");
    private UnisonRules(){ }
    public static boolean canRespond(MechanicsState state){return state.characterId().equals("suoming")||state.characterId().equals("hsin")&&state.mode().equals("unison");}
    public static int stackCap(int chain){return chain>=6?4:3;} // hyperlink131111:2, inherent5:+1, C6:+1.
    public static int extraLayers(int chain){return chain>=6?2:1;}
    public static int extraLayers(MechanicsState state){return state.characterId().equals("suoming")?0:extraLayers(state.chain());}
    public static int stackCap(MechanicsState state){return state.characterId().equals("suoming")?2:stackCap(state.chain());}
    public static MechanicsState prepareIntro(MechanicsState state,boolean responded) {
        if(!canRespond(state))return state;
        var timers=new HashMap<>(state.timers());
        if(state.characterId().equals("suoming")){
            if(responded)timers.put("suoming_intro_response",1);else timers.remove("suoming_intro_response");
            return new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),state.nextBasic(),state.resources(),timers);
        }
        if(responded){timers.put("hsin_resolve",1);timers.put("hsin_intro_enhanced",1);}
        else if(timers.getOrDefault("hsin_resolve",0)>0){timers.remove("hsin_resolve");timers.put("hsin_intro_enhanced",1);}
        return new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),state.nextBasic(),state.resources(),timers);
    }
}
