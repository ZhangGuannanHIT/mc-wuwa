package cn.zgnhit.mcwuwa.client;

import java.util.ArrayList;
import java.util.List;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Amber blade edges and descending sun strokes. Pure presentation, no hit logic. */
final class AugustaVfx {
    private static final int GOLD=0xffffc66a,WHITE=0xfffff3cd,AMBER=0xffed983c;
    private AugustaVfx(){}
    static Sample sample(String action,double progress,double u,long elapsed){
        if(action.equals("charge"))return NewRoleVfxProfiles.charge(GOLD,u,elapsed);
        double p=Math.clamp(progress,0,1),a=-2.15+u*4.3;
        if(action.equals("realm_domain")){
            double angle=u*Math.TAU,r=7+.06*Math.sin(elapsed*.08);
            return new Sample(Math.cos(angle)*r,-.82,Math.sin(angle)*r,GOLD,.45f,Kind.STAR);
        }
        if(action.startsWith("realm")||action.equals("liberation")){
            double r=1.2+p*2.2;
            return new Sample(Math.cos(u*Math.TAU)*r,action.contains("finale")?3.5-u*4:-.65,
                Math.sin(u*Math.TAU)*r,u<.16?WHITE:GOLD,.75f,Kind.STAR);
        }
        if(action.contains("descent")||action.equals("air"))
            return new Sample((u-.5)*.1,2.4-u*3.5,1.0+p,u<.2?WHITE:AMBER,.8f,Kind.DUST);
        if(action.contains("rush"))return new Sample((u<.5?-.3:.3),-.55+u*.3,-.3-u*2.2,GOLD,.5f,Kind.DUST);
        return new Sample(Math.sin(a)*(action.contains("heavy")?2.0:1.3),Math.cos(a)*.4,
            1+Math.cos(a)*.8,u<.15?WHITE:GOLD,.65f,Kind.DUST);
    }
    static List<LightPath> ribbons(String action,double progress){
        if(action.equals("charge"))return List.of();
        double p=Math.clamp(progress,0,1);var out=new ArrayList<LightPath>();
        if(action.equals("realm_domain")){
            double radius=7+.06*Math.sin(p*Math.TAU);
            stroke(out,0x80ffc66a,.035f,t->new Vertex(Math.cos(t*Math.TAU)*radius,-.82,Math.sin(t*Math.TAU)*radius));
            stroke(out,0x50fff3cd,.02f,t->new Vertex(Math.cos(t*Math.TAU)*6.75,-.81,Math.sin(t*Math.TAU)*6.75));
            for(int i=0;i<12;i++){
                double a=i*Math.TAU/12;
                stroke(out,0xa0ffc66a,.035f,t->new Vertex(Math.cos(a)*(6.8+t*.4),-.8,Math.sin(a)*(6.8+t*.4)));
            }
            return out;
        }
        if(action.startsWith("realm")||action.equals("liberation")){
            double r=1.4+p*2.5;
            stroke(out,GOLD,.065f,t->new Vertex(Math.cos(t*Math.TAU)*r,-.7,Math.sin(t*Math.TAU)*r));
            if(action.contains("finale")||action.equals("liberation")){
                for(int i=0;i<6;i++){double a=i*Math.TAU/6,x=Math.cos(a)*2.2,z=Math.sin(a)*2.2;
                    stroke(out,WHITE,.055f,t->new Vertex(x,3.3-t*4,z));}
            }else stroke(out,GOLD,.08f,t->{double a=-2.1+t*4.2;return new Vertex(Math.sin(a)*2.2,.2,1+Math.cos(a));});
        }else if(action.contains("leap")||action.contains("uppercut")){
            stroke(out,GOLD,.08f,t->new Vertex(Math.sin(t*Math.PI)*.7,-1+t*3,1.2));
        }else if(action.contains("descent")||action.equals("air")){
            stroke(out,WHITE,.08f,t->new Vertex(0,2.8-t*4,1.2+p));
        }else if(action.contains("rush")){
            for(int sign:new int[]{-1,1})stroke(out,GOLD,.035f,t->new Vertex(sign*.3,-.55,-.3-t*2.8));
        }else{
            double r=action.contains("heavy")?2.1:1.4;
            stroke(out,GOLD,.07f,t->{double a=-2.2+t*4.4;return new Vertex(Math.sin(a)*r,Math.cos(a)*.4,1+Math.cos(a));});
        }
        return List.copyOf(out);
    }
    private static void stroke(List<LightPath> out,int colour,float width,java.util.function.DoubleFunction<Vertex> shape){
        var points=new ArrayList<Vertex>();for(int i=0;i<=32;i++)points.add(shape.apply(i/32d));out.add(new LightPath(points,colour,width));
    }
}
