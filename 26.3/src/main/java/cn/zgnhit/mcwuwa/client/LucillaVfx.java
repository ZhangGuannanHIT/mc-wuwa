package cn.zgnhit.mcwuwa.client;

import java.util.ArrayList;
import java.util.List;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Lens rings, photographic frames and memory fragments; independent of damage. */
final class LucillaVfx {
    private static final int BLUE=0xffa1ccf6,WHITE=0xfff1f5ff,VIOLET=0xffbcaef5;
    private LucillaVfx(){}
    static Sample sample(String action,double progress,double u,long elapsed){
        if(action.equals("charge"))return NewRoleVfxProfiles.charge(BLUE,u,elapsed);
        double p=Math.clamp(progress,0,1),a=u*Math.TAU;
        boolean memory=action.contains("memory")||action.startsWith("liberation");
        return memory?new Sample(Math.cos(a)*(1.2+p*2),-.5+u*2,Math.sin(a)*(1.2+p*2),u<.2?WHITE:VIOLET,.55f,Kind.STAR)
            :new Sample(Math.cos(a)*(.3+p*.4),Math.sin(a)*(.3+p*.4),.7+u*2.5,u<.2?WHITE:BLUE,.45f,Kind.DUST);
    }
    static List<LightPath> ribbons(String action,double progress){
        if(action.equals("charge"))return List.of();
        double p=Math.clamp(progress,0,1);var out=new ArrayList<LightPath>();
        if(action.startsWith("liberation")||action.contains("memory")){
            for(int frame=0;frame<4;frame++){double a=frame*Math.TAU/4;
                double x=Math.cos(a)*(1.3+p),z=Math.sin(a)*(1.3+p);
                rectangle(out,x,.35,z,.45,.7,VIOLET);}
        }else{
            rectangle(out,0,.2,1.3+p,.75,.6,action.contains("perfect")?WHITE:BLUE);
            for(int ring=0;ring<2;ring++){final double r=.3+ring*.16;
                stroke(out,ring==0?WHITE:BLUE,.025f,t->{double a=t*Math.TAU;return new Vertex(Math.cos(a)*r,.2+Math.sin(a)*r,1.35+p);});}
        }
        return List.copyOf(out);
    }
    private static void rectangle(List<LightPath> out,double x,double y,double z,double w,double h,int colour){
        out.add(new LightPath(List.of(new Vertex(x-w,y-h,z),new Vertex(x+w,y-h,z),new Vertex(x+w,y+h,z),new Vertex(x-w,y+h,z),new Vertex(x-w,y-h,z)),colour,.025f));
    }
    private static void stroke(List<LightPath> out,int colour,float width,java.util.function.DoubleFunction<Vertex> shape){
        var points=new ArrayList<Vertex>();for(int i=0;i<=24;i++)points.add(shape.apply(i/24d));out.add(new LightPath(points,colour,width));
    }
}
