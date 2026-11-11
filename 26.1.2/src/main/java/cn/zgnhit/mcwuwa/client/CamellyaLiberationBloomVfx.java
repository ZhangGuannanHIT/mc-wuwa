package cn.zgnhit.mcwuwa.client;

import java.util.*;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Official Chinese combat demo 44–48s: closed bud, layered petals, branching gold veins.
 * Actor-local decoration only; dimensions and expansion are Minecraft adaptations. */
final class CamellyaLiberationBloomVfx {
    private CamellyaLiberationBloomVfx(){}
    static boolean supports(String action){return action.equals("camellya/liberation");}
    private static double opening(double p){return Math.clamp((p-.21)/.12,0,1);}
    private static int color(double p,int rgb,int opacity){
        double fade=Math.clamp((p-.04)/.10,0,1)*Math.clamp((1-p)/.15,0,1);
        return ((int)(opacity*fade)<<24)|rgb;
    }
    private static Vertex petal(double angle,double t,double edge,double open,int layer){
        double radius=.12+t*(.45+open*(layer==0?4.1:2.7));
        double width=Math.sin(Math.PI*t)*(.13+open*(layer==0?.72:.55));
        double height=-.76+Math.sin(Math.PI*t)*(.4+open*.5)+t*(1.7+open*.6);
        if(layer==1)height+=Math.sin(Math.PI*t)*.7;
        return new Vertex(Math.cos(angle)*radius-Math.sin(angle)*edge*width,height,
            Math.sin(angle)*radius+Math.cos(angle)*edge*width);
    }
    static List<LightSurface> surfaces(String action,double rawProgress){
        if(!supports(action))return List.of();
        double p=ReviewedReleaseVfxClock.presentationPhase(action,rawProgress);
        if(p<=.04||p>=1)return List.of();double open=opening(p);
        var faces=new ArrayList<LightSurface>();
        for(int layer=0;layer<2;layer++)for(int petal=0;petal<6;petal++){
            double angle=petal*Math.TAU/6+layer*Math.PI/6;
            for(int segment=0;segment<8;segment++){
                double a=segment/8d,b=(segment+1)/8d;
                faces.add(new LightSurface(List.of(petal(angle,a,-1,open,layer),petal(angle,b,-1,open,layer),
                    petal(angle,b,1,open,layer),petal(angle,a,1,open,layer)),
                    color(p,layer==0?0xef2858:0xff7f95,layer==0?36:26)));
            }
        }
        return List.copyOf(faces);
    }
    /** Input phase is already remapped by SupplementalActionVfx. */
    static List<LightPath> ribbons(double p){
        if(p<=.04||p>=1)return List.of();double open=opening(p);var paths=new ArrayList<LightPath>();
        for(int petal=0;petal<6;petal++){
            double angle=petal*Math.TAU/6;
            var points=new ArrayList<Vertex>();
            // One continuous closed outline per petal keeps the existing
            // twelve-path budget including the six outer veins.
            for(int i=0;i<=24;i++)points.add(petal(angle,i<=12?i/12d:(24-i)/12d,i<=12?-1:1,open,0));
            paths.add(new LightPath(points,color(p,0xff8eae,210),.025f));
            if(open>0){
                var vine=new ArrayList<Vertex>();
                for(int i=0;i<=24;i++){
                    double t=i/24d,r=t*(1+open*3.8),a=angle+Math.sin(t*Math.TAU)*.19;
                    vine.add(new Vertex(Math.cos(a)*r,-.82+Math.sin(t*Math.PI)*.22,Math.sin(a)*r));
                }
                paths.add(new LightPath(vine,color(p,0xffcf88,180),.018f));
            }
        }
        return List.copyOf(paths);
    }
}
