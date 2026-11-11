package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.HashMap;
import java.util.Set;
import static cn.zgnhit.mcwuwa.content.mechanics.MechanicsEngine.*;
import static cn.zgnhit.mcwuwa.content.mechanics.ExtendedMechanics.*;

/** Native continuation conditions with a thirty-tick MC window after authored actions. */
public final class RoverInputRules {
    private RoverInputRules() { }
    public static MechanicsState actionStarted(MechanicsState state,String fullAction,int durationTicks) {
        if(!state.characterId().equals("aero_rover")||!fullAction.startsWith("aero_rover/"))return state;
        if(durationTicks<1||durationTicks>12000)throw new IllegalArgumentException("rover action duration");
        String action=fullAction.substring(11);var timers=new HashMap<>(state.timers());int window=durationTicks+30;
        if(Set.of("basic_03","dodge_counter","heavy").contains(action))timers.put("rover_strangle_window",window);
        if(action.equals("heavy"))timers.put("rover_heavy_basic_window",window);
        if(Set.of("skill","intro","heavy_strangle","wind_air_01").contains(action))timers.put("wind_air",window);
        return new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),state.nextBasic(),state.resources(),timers);
    }
    static void plunge(Transition t) {
        require(t.context.airborne(),"requires_air");t.payStamina(30);t.action("air");t.clear("wind_air");t.clear("wind_air_part");t.timer("rover_air_attack_pending",1);
    }
    static void tick(Transition t) {
        // The part cursor is a counter, not a countdown. It must not outlive
        // the qualification and skip the opening strike on the next entry.
        if(!t.has("wind_air"))t.clear("wind_air_part");
        if(t.has("rover_air_attack_pending")&&!t.context.airborne()) {t.clear("rover_air_attack_pending");t.timer("rover_land_window",30);t.combo=4;}
    }
    static void clear(Transition t) {
        for(String key:Set.of("rover_strangle_window","rover_strangle_charging","rover_heavy_basic_window","rover_air_attack_pending","rover_land_window","wind_air","wind_air_part"))t.clear(key);
    }
}
