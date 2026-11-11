package cn.zgnhit.mcwuwa.client;

import java.util.ArrayList;
import java.util.List;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Amber fist arcs, shield outline and blazing cleaves. Visuals never award hits. */
final class ZaniVfx {
    private static final int GOLD=0xffffbd66,WHITE=0xffffedc2,EMBER=0xffef743e;
    private ZaniVfx(){}
    static Sample sample(String action,double progress,double u,long elapsed){
        double p=Math.clamp(progress,0,1),a=u*Math.TAU;
        if(action.equals("charge"))return NewRoleVfxProfiles.charge(GOLD,u,elapsed);
        if(action.equals("echo"))return new Sample(Math.cos(a)*2.5,-.6+u*.8,2+Math.sin(a)*2.5,GOLD,.6f,Kind.STAR);
        if(action.equals("liberation")||action.equals("finale"))return new Sample(Math.cos(a)*(1+p*3),-.7+u*1.6,Math.sin(a)*(1+p*3),u<.2?WHITE:EMBER,.75f,Kind.DUST);
        boolean heavy=action.startsWith("heavy_");
        return new Sample(Math.sin(-1.4+u*2.8)*(heavy?2:1),.05+Math.sin(a)*.15,.6+p*1.6+Math.cos(a)*.2,
            heavy?EMBER:GOLD,.45f,Kind.DUST);
    }
    static List<LightPath> ribbons(String action,double progress){
        var out=new ArrayList<LightPath>();double p=Math.clamp(progress,0,1);
        if(action.equals("charge"))return List.of();
        if(action.equals("guard")||action.equals("skill")||action.equals("precise_counter")||action.equals("suppression")){
            stroke(out,GOLD,.04f,t->{double a=t*Math.TAU;return new Vertex(Math.cos(a)*.65,.3+Math.sin(a)*.85,.75);});
            for(int side:new int[]{-1,1})stroke(out,WHITE,.025f,t->new Vertex(side*.3*(1-t),-.4+t*1.3,.78));
        }else if(action.equals("finale")||action.equals("liberation")){
            for(int lane=0;lane<3;lane++){final int n=lane;
                stroke(out,n==1?WHITE:EMBER,.065f,t->{double a=t*Math.TAU;return new Vertex(Math.cos(a)*(1+p*3),-.5+n*.15,Math.sin(a)*(1+p*3));});}
            stroke(out,WHITE,.09f,t->new Vertex(0,2.5-t*3.2,1.2+p));
        }else if(action.startsWith("heavy_")){
            boolean down=action.contains("night");
            for(int lane=0;lane<3;lane++){final int n=lane;
                stroke(out,n==1?WHITE:EMBER,.045f,t->{double a=-1.7+t*3.4;return down?
                    new Vertex((n-1)*.1,2.1-t*2.9,1+Math.sin(t*Math.PI)*1.3):
                    new Vertex(Math.sin(a)*2,.2+(n-1)*.12,1.1+Math.cos(a));});}
        }else{
            double side=action.endsWith("02")||action.endsWith("04")?-.35:.35;
            stroke(out,GOLD,.035f,t->new Vertex(side+Math.sin(t*Math.TAU)*.12,.15+Math.cos(t*Math.TAU)*.12,.45+t*1.7));
            stroke(out,WHITE,.02f,t->{double a=-1.3+t*2.6;return new Vertex(side+Math.sin(a)*.65,.12+Math.cos(a)*.15,1.6);});
        }
        return List.copyOf(out);
    }
    private static void stroke(List<LightPath> out,int color,float width,java.util.function.DoubleFunction<Vertex> shape){
        var points=new ArrayList<Vertex>();for(int i=0;i<=24;i++)points.add(shape.apply(i/24d));out.add(new LightPath(points,color,width));
    }
}
