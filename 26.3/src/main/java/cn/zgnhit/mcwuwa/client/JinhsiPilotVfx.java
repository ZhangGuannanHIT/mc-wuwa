package cn.zgnhit.mcwuwa.client;

import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Gold-white cut planes and coiling light observed in the complete archived
 * Jinhsi demonstration. Distances/phase fit Minecraft; damage remains server-owned. */
public final class JinhsiPilotVfx {
    private static final int WHITE=0xfff5fff8,GOLD=0xffffe9ab,TEAL=0xff90e8e5;
    private JinhsiPilotVfx(){}
    /** Match impact accents to the same view-ray endpoint as the evaluated dragon jaw beam. */
    public static java.util.List<LightPath> aimedPaths(String action,double progress,java.util.List<LightPath> paths,Vertex target){
        if(target==null)return paths;
        boolean release=action.equals("jinhsi/liberation"),epiphany=action.equals("jinhsi/epiphany_rays")||action.equals("jinhsi/epiphany_final");
        if(!release&&!epiphany)return paths;
        boolean impact=release||progress>=.76;
        double oldUp=release?.15:impact?.3:-.4,oldForward=release?9.8:4.9;
        return paths.stream().map(path->new LightPath(path.points().stream().map(v->{
            double weight=impact?1:Math.clamp((v.forward()+.7)/5.6,0,1);
            return new Vertex(v.side()+target.side()*weight,v.up()+(target.up()-oldUp)*weight,v.forward()+(target.forward()-oldForward)*weight);
        }).toList(),path.color(),path.width())).toList();
    }
    static boolean supports(String action){
        return action.startsWith("jinhsi/basic_")||action.startsWith("jinhsi/incarnation_basic_")
            ||Set.of("jinhsi/heavy","jinhsi/air","jinhsi/skill","jinhsi/liberation","jinhsi/skill_overflowing","jinhsi/overflowing_radiance","jinhsi/epiphany_rays","jinhsi/epiphany_final","jinhsi/illuminous_epiphany","jinhsi/incarnation_heavy","jinhsi/incarnation_counter","jinhsi/dodge_counter","jinhsi/skill_crescent","jinhsi/intro").contains(action);
    }
    static Sample sample(String action,double p,double u){
        if(!supports(action))return null;
        var paths=ribbons(action,p);if(paths.isEmpty())return new Sample(0,0,0,WHITE,0,Kind.NONE);
        double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);
        var v=path.points().get(Math.min(path.points().size()-1,(int)((at-(int)at)*path.points().size())));
        return new Sample(v.side(),v.up(),v.forward(),path.color(),.34f,Kind.DUST);
    }
    static List<LightPath> ribbons(String action,double progress){
        if(!supports(action))return null;
        double p=Math.clamp(progress,0,1);if(p<.12||p>(action.equals("jinhsi/liberation")||action.contains("epiphany")?.98:.90))return List.of();
        var out=new ArrayList<LightPath>();
        if(action.equals("jinhsi/liberation")){
            // The mouth-anchored beam is rendered by ConstructRenderer from the
            // complete dragon's evaluated jaw. These are its impact accents only.
            double flare=Math.sin(Math.clamp((p-.16)/.76,0,1)*Math.PI);
            for(int ring=0;ring<2;ring++){
                double radius=(.6+ring*.28)*flare;
                curve(out,ring==0?WHITE:GOLD,.035f,t->{double a=t*Math.TAU;return new Vertex(Math.cos(a)*radius,.15+Math.sin(a)*radius,9.8);});
            }
        }else if(action.equals("jinhsi/heavy")||action.equals("jinhsi/skill")){
            for(int layer=0;layer<3;layer++){
                double radius=(action.endsWith("skill")?2.35:1.8)-layer*.10;
                curve(out,layer==1?WHITE:layer==0?GOLD:TEAL,layer==1?.075f:.025f,t->{double a=-2.5+p*Math.TAU-(1-t)*2.8;return new Vertex(Math.cos(a)*radius,.24+Math.sin(a)*.12,1.0+Math.sin(a)*radius);});
            }
        }else if(action.equals("jinhsi/air")||action.equals("jinhsi/incarnation_heavy")){
            // A travelling cut, not a full-height opaque pole throughout the
            // descent. Leave the centre of the first-person view readable.
            double descent=Math.clamp((p-.18)/.52,0,1);
            for(int layer=-1;layer<=1;layer++){
                double offset=layer*.065;
                curve(out,layer==0?WHITE:GOLD,layer==0?.035f:.014f,t->new Vertex(offset,2.7-descent*3.6+(1-t)*.8,1.35));
            }
            if(p>.40){double r=.3+(p-.4)*2.2;curve(out,GOLD,.025f,t->{double a=t*Math.TAU;return new Vertex(Math.cos(a)*r,-.9,1+Math.sin(a)*r);});}
        }else if(action.contains("epiphany")){
            // E4 is the forward dragon discharge at11.5--14s, not the
            // vertical lightning in the later R shot. Preserve its final burst.
            if(p<.76){
                for(int ray=0;ray<6;ray++){
                    double angle=ray*Math.TAU/6;
                    curve(out,ray%2==0?GOLD:TEAL,.022f,t->{
                        double r=(1-t)*.5,a=angle+t*3.2+p*4;
                        return new Vertex(.7*(1-t)+Math.cos(a)*r,1.8-t*2.2+Math.sin(a)*r,-.7+t*5.6);
                    });
                }
                if(p>.23)curve(out,WHITE,.045f,t->new Vertex(.7*(1-t),1.8-t*2.2,-.7+t*5.6));
            }
            if(p>.82){
                double flare=Math.sin(Math.clamp((p-.82)/.16,0,1)*Math.PI);
                for(int ray=0;ray<8;ray++){
                    double angle=ray*Math.TAU/8;
                    curve(out,ray%2==0?WHITE:GOLD,.025f,t->new Vertex(Math.cos(angle)*t*flare*1.6,.3+Math.sin(angle)*t*flare*1.6,4.9));
                }
                curve(out,TEAL,.025f,t->{double angle=t*Math.TAU;return new Vertex(Math.cos(angle)*flare*1.1,-.86,4.9+Math.sin(angle)*flare*1.1);});
            }
        }else if(action.contains("overflowing")||action.endsWith("radiance")){
            for(int ribbon=0;ribbon<3;ribbon++){
                double offset=ribbon*Math.TAU/3;
                curve(out,ribbon==0?WHITE:GOLD,.065f,t->{double a=t*Math.TAU+offset+p*2;return new Vertex(Math.cos(a)*1.15,-.5+t*2.7,1.2+Math.sin(a)*.7);});
            }
            for(int side:new int[]{-1,1})curve(out,TEAL,.045f,t->new Vertex(side*(.2+t*.7),-.7+t*3.2,1.3));
        }else if(action.equals("jinhsi/skill_crescent")){
            // Separate airborne crescent cuts, not the E4 dragon discharge.
            for(int side:new int[]{-1,1})curve(out,side<0?GOLD:WHITE,.035f,t->{double a=-1.3+t*2.6;return new Vertex(side*Math.cos(a)*1.3,.2+Math.sin(a)*1.1,.6+t*2.5);});
        }else if(action.endsWith("counter")||action.equals("jinhsi/intro")){
            for(int layer=0;layer<3;layer++){
                double radius=1.5+layer*.12,lift=action.contains("incarnation")?.75:.4;
                curve(out,layer==1?WHITE:GOLD,layer==1?.04f:.018f,t->{double a=-2.1+t*4.2;return new Vertex(Math.sin(a)*radius,Math.cos(a)*lift,1.2+Math.cos(a)*.6);});
            }
        }else{
            int stage=Integer.parseInt(action.substring(action.length()-2));
            boolean air=action.contains("incarnation");double radius=air?2.15:1.55;
            for(int layer=0;layer<3;layer++){
                double r=radius-layer*.13;int color=layer==0?GOLD:layer==1?WHITE:TEAL;
                curve(out,color,layer==1?.095f:.025f,t->{
                    double a=-2.4+p*4.5-(1-t)*1.65,x=Math.cos(a)*r,y=Math.sin(a)*r;
                    return switch(stage){
                        case 2->new Vertex(x,.15+y*.65,1.35+y*.24);
                        case 3->new Vertex(x,.18+y*.15,1.2+Math.sin(a)*r*.65);
                        case 4->new Vertex(x,.3+y*.85,1.65);
                        default->new Vertex(x,.12+y*.20,1.2+Math.sin(a)*r*.55);
                    };
                });
            }
            if(air&&stage==4)for(int strand=0;strand<3;strand++){
                double offset=strand*Math.TAU/3;
                curve(out,GOLD,.04f,t->{double a=t*Math.TAU+offset;return new Vertex(Math.cos(a)*(1.1+t*.3),-.5+t*2.1,1.4+Math.sin(a)*.7);});
            }
        }
        return List.copyOf(out);
    }
    private static void curve(List<LightPath> out,int color,float width,DoubleFunction<Vertex> fn){
        var points=new ArrayList<Vertex>();for(int i=0;i<=24;i++)points.add(fn.apply(i/24d));
        out.add(new LightPath(List.copyOf(points),color,width));
    }
}
