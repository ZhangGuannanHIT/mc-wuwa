package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.Set;
import static cn.zgnhit.mcwuwa.content.mechanics.MechanicsEngine.*;

/** Only descriptions explicitly saying low air are gated. The host supplies a live
 * high_air observation; no-height pure simulations do not invent world geometry. */
public final class LowAirRules {
    private static final Set<String> ROLES=Set.of("suoming","lucilla","suisui","cantarella","yangyang_xuanling","aero_rover","hsin","aemeath","chisa","camellya","galbrena","ciaccona","carlotta","lupa","qingxiao");
    private static final Set<String> ACTIONS=Set.of("suoming/liberation","lucilla/liberation","lucilla/liberation_echo","suisui/skill","suisui/rain_skill","suisui/liberation","cantarella/liberation","yangyang_xuanling/listening_basic_02","yangyang_xuanling/listening_basic_03",
        "aero_rover/liberation","hsin/liberation_palaces","chisa/liberation","camellya/liberation",
        "aemeath/mech_basic_01","aemeath/mech_basic_02","aemeath/mech_basic_03","aemeath/mech_basic_04",
        "aemeath/mech_heavy","aemeath/mech_heavy_charge_2","aemeath/mech_dodge_counter",
        "aemeath/liberation","aemeath/liberation_finale","aemeath/wing_ensemble_stage","aemeath/wing_ensemble_descend",
        "carlotta/liberation","lupa/skill","lupa/skill_ferocious","lupa/skill_ferocious_marked","lupa/liberation",
        "lupa/skill_wolf_dance","lupa/skill_wolf_dance_extreme","lupa/skill_wolf_dance_extreme_c4","qingxiao/liberation");
    private LowAirRules(){}
    public static boolean observesHeight(String role){return ROLES.contains(role);}
    public static boolean requiresLowAir(String action){return ACTIONS.contains(action);}
    static void validate(Transition t,Input input){
        if(!t.context.airborne()||!t.has("high_air"))return;
        boolean limited=t.effects.stream().anyMatch(e->e.kind()==Kind.ACTION&&requiresLowAir(e.id()));
        // Hsin's initial transformation has a FORM effect rather than a damage action.
        limited|=t.original.characterId().equals("hsin")&&input==Input.LIBERATION
            &&!t.original.form().equals("illuminated")&&t.original.timer("heart_state")==0
            &&t.effects.stream().anyMatch(e->e.kind()==Kind.FORM&&e.id().equals("illuminated"));
        limited|=t.original.characterId().equals("aemeath")&&t.original.form().equals("mech")&&input==Input.HEAVY_START;
        if(limited)throw new Rejected("low_air_only");
    }
}
