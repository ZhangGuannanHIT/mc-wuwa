package cn.zgnhit.mcwuwa.core;

/** Visual time only: telegraphs and hit deadlines remain authoritative on the server. */
public final class BossAnimationClock {
    private BossAnimationClock() {}
    public static float progress(EchoBossCatalog.Move move, double elapsed) {
        double t=Math.max(0,elapsed), w=move.windup();
        if(t>=move.duration())return 1;
        if(move.pattern()==EchoBossCatalog.Pattern.GUARD)return (float)(.2*Math.min(1,t/6));
        // Establish the anticipation quickly, hold it, then strike in four ticks.
        if(t<w-4)return (float)(.2*Math.min(1,t/6));
        if(t<w)return (float)(.2+.35*(t-w+4)/4);
        double active=t-w;
        if(move.pattern()==EchoBossCatalog.Pattern.COMBO || move.pattern()==EchoBossCatalog.Pattern.BARRAGE) {
            if(active<24) {
                double cycle=active%12;
                return cycle<8?(float)(.55+.45*cycle/8):(float)(.55*(cycle-8)/4);
            }
            active-=24;
        } else if(move.pattern()==EchoBossCatalog.Pattern.BREATH) {
            return (float)(.55+.45*Math.clamp((t-(move.duration()-8))/8,0,1));
        }
        return (float)(.55+.45*Math.min(1,active/8));
    }
}
