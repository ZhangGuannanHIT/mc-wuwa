package cn.zgnhit.mcwuwa.content.mechanics;

import static cn.zgnhit.mcwuwa.content.mechanics.MechanicsEngine.Input;

/** A short attack gesture is not necessarily a plunge. The server's actual stance
 * chooses the action; HUD preview uses this same decision. Explicit AIR remains
 * available to internal callers requesting a descent, not to infer normal clicks. */
public final class BasicInputRules {
    private BasicInputRules() { }
    /** A player's retained dodge window does not replace a commanded summon,
     * fox movement, or an already prepared Iai attack. Shared by host and HUD. */
    public static boolean counterTap(MechanicsState state, boolean ready) {
        if(!ready)return false;
        if(state==null)return true; // A newly equipped normal role has no cached form yet.
        return switch(state.characterId()) {
            case "phrolova" -> !state.form().equals("conducting");
            case "carlotta" -> !state.form().equals("gunflower");
            case "augusta" -> !state.form().equals("realm");
            case "shorekeeper" -> state.timer("shore_unbound")==0;
            case "lucilla" -> state.timer("lucilla_camera")==0;
            case "ciaccona" -> state.timer("ciaccona_aim")==0;
            case "mornye" -> !(state.form().equals("observation")&&state.timer("slow_descent")>0);
            case "hsin" -> !state.form().equals("fox");
            case "hiyuki" -> !(state.form().equals("future")&&state.timer("iai_stance")>0&&state.resource("cold")>=100);
            default -> true;
        };
    }
    public static Input tap(MechanicsState state, boolean airborne, boolean inWater) {
        if (inWater && state.characterId().equals("cantarella") && !state.form().equals("mirage") && state.resource("trance") > 0) {
            return Input.HEAVY_FULL;
        }
        return tap(state, airborne);
    }

    public static Input tap(MechanicsState state, boolean airborne) {
        if(state.characterId().equals("lucilla")&&state.timer("lucilla_camera")>0)return Input.BASIC;
        if(state.characterId().equals("ciaccona")&&state.timer("ciaccona_aim")>0)return Input.BASIC;
        if (!airborne) return Input.BASIC;
        boolean stanceBasic = switch (state.characterId()) {
            case "augusta" -> state.timer("augusta_skill_link")>0;
            case "changli", "lupa", "qingxiao", "zhezhi" -> true; // Explicit aerial combo, not a tap plunge.
            case "iuno" -> state.form().equals("new_moon");
            case "denia" -> state.form().equals("disillusion");
            case "carlotta" -> state.form().equals("gunflower");
            case "cantarella" -> state.form().equals("mirage");
            case "aemeath" -> state.form().equals("mech")&&state.timer("high_air")==0; // 1210: low-air basics, high-air plunge.
            case "jinhsi" -> state.form().equals("incarnation"); // Epiphany explicitly replaces air click with heavy.
            case "camellya" -> state.form().equals("bloom");
            case "mornye" -> state.form().equals("observation") && state.timer("slow_descent") == 0;
            case "hsin" -> state.form().equals("illuminated")||state.form().equals("fox");
            case "hiyuki" -> state.timer("hiyuki_basic3_window")>0 || state.form().equals("future") && state.timer("iai_stance") > 0 && state.resource("cold") >= 100;
            case "yangyang_xuanling" -> state.form().equals("listening") && state.nextBasic() >= 2;
            case "phrolova" -> state.form().equals("conducting");
            case "ciaccona" -> state.timer("ciaccona_followup")>0;
            case "qiuyuan" -> state.timer("qiuyuan_answer")>0 || state.form().equals("drunken") && state.timer("qiuyuan_ink_stage")>1;
            case "zani" -> state.form().equals("blazing") && state.timer("zani_night_resume")>0;
            case "lynae" -> state.timer("tomorrow")>0 || state.form().equals("cruise")&&state.timer("lynae_basic2_followup")>0;
            case "shorekeeper" -> state.timer("shore_unbound")>0;
            case "chisa" -> state.form().equals("saw") || state.timer("pierce_ready") > 0
                || state.timer("chisa_falling_ready") > 0 || state.timer("pierce_followup") > 0 || state.timer("pincers_followup") > 0;
            default -> false;
        };
        return stanceBasic ? Input.BASIC : Input.AIR;
    }
}
