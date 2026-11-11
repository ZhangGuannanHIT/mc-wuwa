package cn.zgnhit.mcwuwa.core;

/** User-approved cinematic policy. Ordinary attack animation never stops time. */
public final class CinematicTimeRules {
    private CinematicTimeRules(){}
    public static boolean cinematic(String action){
        String id=action.startsWith("mc_wuwa:")?action.substring(8):action;
        int slash=id.indexOf('/');if(slash<0)return false;
        String move=id.substring(slash+1);
        // Recital note pulses are gameplay. Qiuyuan C3 is the full enhanced
        // liberation, not a passive proc; it uses the same opening clock.
        if(move.equals("liberation_note"))return false;
        return move.equals("liberation")||move.startsWith("liberation_")
            ||id.equals("phrolova/conduction_enter")||id.equals("cartethyia/fleurdelys_enter")
            ||id.equals("augusta/realm_finale");
    }
    public static long postpone(long deadline,long ticks){
        if(deadline==Long.MAX_VALUE||deadline<=0)return deadline;
        return Math.addExact(deadline,ticks);
    }
}
