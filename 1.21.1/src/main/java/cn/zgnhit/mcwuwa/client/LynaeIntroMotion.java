package cn.zgnhit.mcwuwa.client;

/** Complete official intro card: wheelie, forward release and departing bike.
 * Distances are MC staging; the existing ten-contact action remains authoritative. */
public final class LynaeIntroMotion {
    private LynaeIntroMotion(){}
    public static float forward(double phase){return (float)(Math.clamp(phase,0,1)*6);}
    public static float pitch(double phase){return (float)(32*(1-Math.clamp(phase/.55,0,1)));}
}
