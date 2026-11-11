package cn.zgnhit.mcwuwa.client;

import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Full original skill-card silhouettes; shared MC opening clocks, no film camera. */
final class NextFiveReleaseVfx {
    private static final int RED=0xffff607d,MINT=0xff86f4de,GOLD=0xffffdc89,BLUE=0xff95ddff,PURPLE=0xffd3a3ff,WHITE=0xffeffaff;
    static boolean supports(String action){return Set.of("chisa/liberation","qiuyuan/liberation","qiuyuan/liberation_c3","ciaccona/liberation","shorekeeper/liberation","sigrika/liberation").contains(action);}
    static Sample sample(String action,double progress,double u){
        var paths=ribbons(action,progress);if(paths.isEmpty())return new Sample(0,0,0,WHITE,.2f,Kind.NONE);
        double at=Math.clamp(u,0,.999999)*paths.size();var line=paths.get((int)at);
        var v=line.points().get(Math.min(line.points().size()-1,(int)((at-(int)at)*line.points().size())));
        return new Sample(v.side(),v.up(),v.forward(),line.color(),.28f,Kind.DUST);
    }
    static List<LightPath> ribbons(String action,double progress){
        double p=ReviewedReleaseVfxClock.presentationPhase(action,progress);
        if(p<.05||p>.98)return List.of();var paths=new ArrayList<LightPath>();
        if(action.startsWith("chisa/")){
            if(p<.25){
                curve(paths,RED,.022f,t->{double a=t*Math.TAU;return new Vertex(.45+Math.cos(a)*.3,.65+Math.sin(a)*.3,.85);});
                curve(paths,PURPLE,.018f,t->new Vertex((t-.5)*1.6,.65,1.0));
            }else{
                for(int i=0;i<8;i++){
                    double a=i*Math.TAU/8+p*.5;
                    curve(paths,i%2==0?RED:PURPLE,.03f,t->new Vertex(Math.cos(a)*t*3.1,.2+Math.sin(a)*t*1.8,1.4+t*.7));
                }
                curve(paths,RED,.04f,t->{double a=t*Math.TAU;return new Vertex(Math.cos(a)*2.5,-.75,1.2+Math.sin(a)*2.5);});
            }
        }else if(action.startsWith("qiuyuan/")){
            if(p<.25){
                for(int i=0;i<3;i++){
                    double offset=i*.08;
                    curve(paths,i==1?WHITE:MINT,.018f,t->new Vertex(.5-t*.8,.5+t*.8,.8+offset));
                }
            }else{
                for(int i=0;i<3;i++){
                    double h=(i-1)*.12;
                    curve(paths,i==1?WHITE:MINT,i==1?.055f:.022f,t->new Vertex((t-.5)*8,.1+h+(t-.5)*.4,1.5));
                }
                curve(paths,MINT,.02f,t->new Vertex((t-.5)*6.5,-.8+Math.sin(t*Math.PI)*.12,1.5));
            }
        }else if(action.startsWith("ciaccona/")){
            for(int staff=0;staff<5;staff++){
                double height=-.35+staff*.18;
                curve(paths,staff%2==0?MINT:GOLD,.015f,t->{double a=t*Math.TAU+p*.6;return new Vertex(Math.cos(a)*2.4,height,Math.sin(a)*2.4);});
            }
            for(int note=0;note<3;note++){
                double a=note*Math.TAU/3+p*1.4,x=Math.cos(a)*2.4,z=Math.sin(a)*2.4;
                curve(paths,GOLD,.025f,t->new Vertex(x+Math.cos(t*Math.TAU)*.1,.15+Math.sin(t*Math.TAU)*.08,z));
                curve(paths,MINT,.018f,t->new Vertex(x+.09,.2+t*.45,z));
            }
        }else if(action.startsWith("shorekeeper/")){
            if(p<.52){
                for(int sign:new int[]{-1,1})for(int layer=0;layer<2;layer++){
                    double lift=layer*.16;
                    curve(paths,layer==0?BLUE:WHITE,.018f,t->new Vertex(.35+sign*Math.sin(t*Math.PI)*.32,.65+lift+t*.25,.85+Math.sin(t*Math.PI)*.15));
                }
            }else{
                curve(paths,BLUE,.025f,t->new Vertex(Math.cos(t*Math.TAU)*2.8,2.7,Math.sin(t*Math.TAU)*2.8));
                for(int i=0;i<6;i++){
                    double a=i*Math.TAU/6;
                    curve(paths,i%2==0?WHITE:BLUE,.016f,t->new Vertex(Math.cos(a)*2.8,2.7-t*2.8,Math.sin(a)*2.8));
                }
            }
        }else{
            if(p<.25){
                for(int i=0;i<3;i++){
                    double tilt=i*Math.PI/3;
                    curve(paths,i==1?GOLD:PURPLE,.021f,t->new Vertex(Math.cos(t*Math.TAU)*1.4,.6+Math.sin(t*Math.TAU)*Math.cos(tilt)*1.4,1.2+Math.sin(t*Math.TAU)*Math.sin(tilt)*1.4));
                }
            }else{
                for(int i=0;i<8;i++){
                    double a=i*Math.TAU/8;
                    curve(paths,i%2==0?GOLD:PURPLE,.025f,t->new Vertex(Math.cos(a)*t*3,-.75+t*.18,1+Math.sin(a)*t*3));
                }
                for(int i=0;i<2;i++){
                    double radius=2.5+i*.24,tilt=i==0?.48:-.48;
                    // The post-impact full panel has crossing raised orbits,
                    // then settles into the floor glyph during recovery.
                    double lift=Math.clamp((.82-p)/.22,0,1);
                    curve(paths,i==0?GOLD:PURPLE,.025f,t->{double angle=t*Math.TAU+p*.6;
                        return new Vertex(Math.cos(angle)*radius,-.8+lift*(1.1+Math.sin(angle)*radius*tilt),1+Math.sin(angle)*radius);});
                }
            }
        }
        return List.copyOf(paths);
    }
    private static void curve(List<LightPath> paths,int color,float width,DoubleFunction<Vertex> shape){
        var points=new ArrayList<Vertex>();for(int i=0;i<=24;i++)points.add(shape.apply(i/24d));paths.add(new LightPath(points,color,width));
    }
}
