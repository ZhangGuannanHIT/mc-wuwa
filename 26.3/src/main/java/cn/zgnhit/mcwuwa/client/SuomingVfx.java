package cn.zgnhit.mcwuwa.client;

import java.util.ArrayList;
import java.util.List;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Violet thunder, parasol sweeps and the grounded heart-carving field. Cosmetic only. */
final class SuomingVfx {
    private static final int VIOLET=0xffaf8cf4,WHITE=0xffe4ddff,TEAL=0xff80d4e2;
    private SuomingVfx(){}
    static Sample sample(String action,double progress,double u,long elapsed){
        if(action.equals("charge"))return NewRoleVfxProfiles.charge(VIOLET,u,elapsed);
        if(action.equals("rain_thunder"))return new Sample(Math.sin(u*31)*.10,3.0-u*3.5,Math.cos(u*23)*.08,u<.2?WHITE:VIOLET,.45f,Kind.DUST);
        double p=Math.clamp(progress,0,1),angle=u*Math.TAU;
        boolean heart=action.contains("heart"),spin=action.contains("spin");
        if(heart)return new Sample(Math.cos(angle)*(1+p*3),-.75+u*.35,Math.sin(angle)*(1+p*3),VIOLET,.65f,Kind.DUST);
        if(spin)return new Sample(Math.cos(angle+elapsed*.25)*1.8,.1+Math.sin(angle)*.4,Math.sin(angle+elapsed*.25)*1.8,u<.2?WHITE:VIOLET,.55f,Kind.DUST);
        return new Sample(Math.sin(-1.8+u*3.6)*1.5,.1+Math.cos(angle)*.5,.7+p+Math.cos(-1.8+u*3.6),u<.2?TEAL:VIOLET,.5f,Kind.DUST);
    }
    static List<LightPath> ribbons(String action,double progress){
        if(action.equals("charge"))return List.of();
        var out=new ArrayList<LightPath>();double p=Math.clamp(progress,0,1);
        if(action.equals("rain_thunder")){
            stroke(out,VIOLET,.055f,t->new Vertex(Math.sin(t*31)*.10,3.0-t*3.5,Math.cos(t*23)*.08));
            stroke(out,WHITE,.018f,t->new Vertex(Math.sin(t*31)*.10,3.0-t*3.5,Math.cos(t*23)*.08));
            stroke(out,VIOLET,.028f,t->new Vertex(Math.cos(t*Math.TAU)*(.35+p*.65),-.4,Math.sin(t*Math.TAU)*(.35+p*.65)));
        }else if(action.contains("heart")||action.equals("liberation")){
            for(int ring=0;ring<3;ring++){final int n=ring;
                stroke(out,n==1?WHITE:VIOLET,.04f,t->{double a=t*Math.TAU;double r=.8+n*.65+p*1.4;return new Vertex(Math.cos(a)*r,-.7+n*.08,Math.sin(a)*r);});}
            for(int ray=0;ray<6;ray++){final double a=ray*Math.TAU/6;
                stroke(out,VIOLET,.035f,t->new Vertex(Math.cos(a)*(1+t*2),2.8-t*3.4,Math.sin(a)*(1+t*2)));}
        }else if(action.contains("spin")){
            for(int ring=0;ring<2;ring++){final int n=ring;
                stroke(out,n==0?WHITE:VIOLET,.045f,t->{double a=t*Math.TAU+p*5;return new Vertex(Math.cos(a)*1.9,.1+Math.sin(a)*.35+n*.1,Math.sin(a)*1.9);});}
        }else{
            stroke(out,VIOLET,.04f,t->{double a=-1.8+t*3.6;return new Vertex(Math.sin(a)*1.6,.15+Math.cos(a)*.5,.8+Math.cos(a));});
            stroke(out,WHITE,.02f,t->new Vertex((t-.5)*.1,.1+Math.sin(t*23)*.05,.6+t*2.2));
        }
        return List.copyOf(out);
    }
    private static void stroke(List<LightPath> out,int colour,float width,java.util.function.DoubleFunction<Vertex> shape){
        var points=new ArrayList<Vertex>();for(int i=0;i<=24;i++)points.add(shape.apply(i/24d));out.add(new LightPath(points,colour,width));
    }
}
