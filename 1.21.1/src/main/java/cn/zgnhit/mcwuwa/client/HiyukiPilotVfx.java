package cn.zgnhit.mcwuwa.client;

import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Reviewed full inward-vision, blade-liberation and Iai GIF silhouettes.
 * Phases/distances fit the existing MC contact clocks; never control damage. */
final class HiyukiPilotVfx {
    private static final int WHITE=0xffeffaff,BLUE=0xff96dfff,PINK=0xffffc9df;
    static boolean supports(String action){return Set.of("hiyuki/liberation","hiyuki/liberation_return","hiyuki/iai").contains(action);}
    static Sample sample(String action,double p,double u){
        if(!supports(action))return null;
        var paths=ribbons(action,p);if(paths.isEmpty())return new Sample(0,0,0,WHITE,0,Kind.NONE);
        double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);
        var point=path.points().get(Math.min(path.points().size()-1,(int)((at-(int)at)*path.points().size())));
        return new Sample(point.side(),point.up(),point.forward(),path.color(),.26f,Kind.DUST);
    }
    static List<LightPath> ribbons(String action,double progress){
        if(!supports(action))return null;
        double p=Math.clamp(progress,0,1);if(p<.08||p>.985)return List.of();
        var paths=new ArrayList<LightPath>();
        if(action.equals("hiyuki/iai")){
            for(int layer=0;layer<4;layer++){
                double height=-.35+layer*.18,phase=layer*.08;
                curve(paths,layer%2==0?WHITE:PINK,.025f,t->{double a=-1.3+t*2.6+p*.6+phase;return new Vertex(Math.sin(a)*2.1,height+Math.sin(t*Math.PI)*.12,1+Math.cos(a)*1.15);});
            }
            curve(paths,BLUE,.018f,t->new Vertex((t-.5)*3.5,-.2+(t-.5)*.6,1.6));
        }else if(action.equals("hiyuki/liberation")&&p<.78){
            // The held-blade invocation gathers frost before the source release.
            for(int ring=0;ring<3;ring++){
                double tilt=ring*Math.PI/3,radius=.18+p*.16;
                curve(paths,ring==1?PINK:BLUE,.018f,t->new Vertex(Math.cos(t*Math.TAU)*radius,.55+Math.sin(t*Math.TAU)*radius*Math.cos(tilt),.9+Math.sin(t*Math.TAU)*radius*Math.sin(tilt)));
            }
        }else if(action.equals("hiyuki/liberation_return")&&p<.82){
            for(int ring=0;ring<3;ring++){
                double tilt=ring*Math.PI/3,radius=.32+p*.28;
                curve(paths,ring==1?BLUE:WHITE,.022f,t->new Vertex(Math.cos(t*Math.TAU)*radius,.45+Math.sin(t*Math.TAU)*radius*Math.cos(tilt),1.2+Math.sin(t*Math.TAU)*radius*Math.sin(tilt)));
            }
            for(int petal=0;petal<6;petal++){
                double a=petal*Math.TAU/6+p*2;
                curve(paths,petal%2==0?PINK:BLUE,.015f,t->{double r=Math.sin(t*Math.PI)*.45;return new Vertex(Math.cos(a)*r,.45+(t-.5)*1.2,1.2+Math.sin(a)*r);});
            }
        }else{
            double burst=action.equals("hiyuki/liberation_return")?Math.clamp((p-.82)/.16,0,1):Math.clamp((p-.78)/.20,0,1);
            for(int ring=0;ring<2;ring++){
                double r=.8+burst*2+ring*.16;
                curve(paths,ring==0?BLUE:WHITE,.028f,t->new Vertex(Math.cos(t*Math.TAU)*r,-.8,1+Math.sin(t*Math.TAU)*r));
            }
            for(int petal=0;petal<6;petal++){
                double a=petal*Math.TAU/6;
                curve(paths,petal%2==0?PINK:WHITE,.025f,t->{double r=Math.sin(t*Math.PI)*(.7+burst*1.9);return new Vertex(Math.cos(a)*r,-.4+Math.sin(a)*r*.48+t*.45,1+t*2.2);});
            }
        }
        return List.copyOf(paths);
    }
    private static void curve(List<LightPath> out,int color,float width,DoubleFunction<Vertex> shape){
        var points=new ArrayList<Vertex>();for(int i=0;i<=28;i++)points.add(shape.apply(i/28d));out.add(new LightPath(points,color,width));
    }
}
