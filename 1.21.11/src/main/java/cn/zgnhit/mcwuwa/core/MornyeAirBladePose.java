package cn.zgnhit.mcwuwa.core;
/** Source-observed remote overhead-to-down cut, fitted to the retained6/24MC contact. */
public final class MornyeAirBladePose {
    public record Pose(double side,double up,double forward,double pitch,double scale){}
    private MornyeAirBladePose(){}
    public static Pose at(double progress){
        double q=Math.clamp((progress-.08)/.35,0,1);
        return new Pose(.15+Math.sin(q*Math.PI)*.2,2.1-1.3*q,.6+1.1*Math.sin(q*Math.PI*.5),-90-90*q,.82);
    }
}
