package cn.zgnhit.mcwuwa.content.mechanics;

/** A form's recovery prohibition is a live gate, not a stamina-spend cooldown.
 * The host evaluates only the current body's owner before advancing stamina. */
public final class StaminaRecoveryRules {
    private StaminaRecoveryRules() { }
    /** Lynae spends the published10/s once Flow reaches120, including C6.
     * Hsin skill2 requires fox sprint spending outside combat too; its rate
     * reuses the existing5/s MC sprint adaptation. Host owns ground/input checks. */
    public static double groundSprintCostPerTick(MechanicsState state) {
        if(state!=null&&state.characterId().equals("hsin")&&state.form().equals("fox"))return .25;
        return state!=null&&state.characterId().equals("lynae")&&state.form().equals("cruise")
            &&state.resource("lumiflow")>=120?.5:0;
    }
    /** These bodies already emit their continuous payment from
     * the mechanics tick (with MC rates where the source gives no rate).
     * Generic combat sprint must not add a second5/s. */
    public static boolean ownsContinuousDrain(MechanicsState state,boolean airborne) {
        if(state==null)return false;
        return switch(state.characterId()) {
            case "shorekeeper" -> state.timer("shore_unbound")>0;
            case "qiuyuan" -> state.timer("qiuyuan_dash")>0;
            case "hsin" -> airborne&&state.timer("hsin_wander")>0;
            case "hiyuki" -> state.form().equals("future")&&state.timer("charging")>0;
            case "galbrena" -> airborne&&state.timer("gal_raining")>0;
            case "iuno" -> airborne&&!state.form().equals("normal")&&state.timer("iuno_phase")>0;
            case "camellya" -> airborne&&state.form().equals("bloom");
            case "mornye" -> state.form().equals("observation")&&state.timer("observation")>0
                &&(airborne||state.timer("observation_entry_grace")>0);
            default -> false;
        };
    }
    public static boolean allowed(MechanicsState state,boolean active,boolean airborne,String runningAction) {
        if(!active||state==null)return true;
        if("camellya/bloom_dark_heart".equals(runningAction))return false;
        return switch(state.characterId()) {
            case "hsin" -> !(state.form().equals("illuminated")&&airborne);
            case "mornye" -> !(state.form().equals("observation")&&state.timer("observation")>0
                &&(airborne||state.timer("observation_entry_grace")>0));
            case "chisa" -> state.form().equals("saw")||!airborne||state.timer("chisa_glide")==0;
            case "qingxiao" -> !(state.timer("qingxiao_flight")>0&&airborne&&state.resource("flight_qi")>0);
            case "iuno" -> !(!state.form().equals("normal")&&state.timer("iuno_phase")>0&&airborne);
            default -> true;
        };
    }
}
