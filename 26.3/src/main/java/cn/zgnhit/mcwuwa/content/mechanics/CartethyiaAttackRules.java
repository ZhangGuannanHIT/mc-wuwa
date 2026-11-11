package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.HashMap;
import java.util.Set;

/** Node7 attack links. Two-tick heavy cancel and30-tick continuation are MC input windows. */
public final class CartethyiaAttackRules {
    public static final String SMALL_PLUNGE_LINK="cartethyia_small_plunge_link";
    public static final Set<String> BODY_TIMERS=Set.of("cartethyia_heavy_link","cartethyia_uppercut_link","cartethyia_air_basic_link",SMALL_PLUNGE_LINK);
    private CartethyiaAttackRules() { }
    public static MechanicsState actionStarted(MechanicsState state,String action,int duration) {
        if(!state.characterId().equals("cartethyia"))return state;
        var timers=new HashMap<>(state.timers());BODY_TIMERS.forEach(timers::remove);
        if(state.form().equals("fleurdelys")) {
            if(action.equals("cartethyia/fleurdelys_heavy"))timers.put("cartethyia_heavy_link",duration);
            if(action.equals("cartethyia/fleurdelys_heavy_enhanced"))timers.put("cartethyia_uppercut_link",Math.addExact(duration,30));
            if(action.equals("cartethyia/fleurdelys_air_03"))timers.put("cartethyia_air_basic_link",Math.addExact(duration,30));
        }else if(smallPlunge(action))timers.put(SMALL_PLUNGE_LINK,Math.addExact(duration,30));
        return new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),state.nextBasic(),state.resources(),timers);
    }
    public static boolean canCancel(MechanicsState state,String action,MechanicsEngine.Input input,long elapsed,int finalHit) {
        if(!state.characterId().equals("cartethyia")
            ||input!=MechanicsEngine.Input.BASIC&&input!=MechanicsEngine.Input.AIR)return false;
        if(!state.form().equals("fleurdelys"))return input==MechanicsEngine.Input.BASIC&&smallPlunge(action)
            &&state.timer(SMALL_PLUNGE_LINK)>0&&elapsed>Math.max(1,finalHit);
        if(action.equals("cartethyia/fleurdelys_heavy"))return state.timer("cartethyia_heavy_link")>0&&elapsed>=2;
        if(elapsed<=Math.max(1,finalHit))return false;
        return action.equals("cartethyia/fleurdelys_heavy_enhanced")&&state.timer("cartethyia_uppercut_link")>0
            ||action.equals("cartethyia/fleurdelys_air_03")&&state.timer("cartethyia_air_basic_link")>0;
    }
    static boolean followup(MechanicsEngine.Transition t) {
        if(!t.form.equals("fleurdelys")){
            if(t.has(SMALL_PLUNGE_LINK)&&!t.context.airborne()){
                t.clear(SMALL_PLUNGE_LINK);t.action("basic_02");t.combo=3;return true;
            }
            return false;
        }
        if(t.has("cartethyia_heavy_link")) {
            t.clear("cartethyia_heavy_link");t.action("fleurdelys_heavy_enhanced");
            t.effect(MechanicsEngine.Kind.MOVE_SPECIAL,"cartethyia_heavy_retreat",1,0);return true;
        }
        if(t.has("cartethyia_uppercut_link")) {
            t.clear("cartethyia_uppercut_link");t.action("fleurdelys_uppercut");
            t.effect(MechanicsEngine.Kind.MOVE_SPECIAL,"cartethyia_uppercut",1,0);return true;
        }
        if(t.has("cartethyia_air_basic_link")) {
            t.clear("cartethyia_air_basic_link");t.action("fleurdelys_basic_03");t.combo=4;return true;
        }
        return false;
    }
    private static boolean smallPlunge(String action){
        return Set.of("cartethyia/air","cartethyia/air_one_sword","cartethyia/air_two_swords","cartethyia/air_three_swords").contains(action);
    }
}
