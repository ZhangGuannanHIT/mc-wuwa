package cn.zgnhit.mcwuwa.client;

import java.util.ArrayList;
import java.util.List;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Authored MC light feedback for the new rectifier actions. Shapes share the
 * accepted action clock; these strokes never deal damage or move an entity.
 * They are visual adaptations, not evidence of original frame-by-frame parity. */
final class TideRoleVfx {
    private static final int GOLD=0xffffe3a0,WHITE=0xfffffae4,MINT=0xffa5ead6,BLUE=0xffa9deff,VIOLET=0xffb49afa;
    private TideRoleVfx(){}
    static boolean supports(String role){return role.equals("phoebe")||role.equals("suisui")||role.equals("cantarella");}
    static Sample sample(String role,String action,double progress,double u,long elapsed){
        double p=Math.clamp(progress,0,1),angle=u*Math.TAU+p*2;
        int lane=(int)(u*24),color=role.equals("phoebe")?GOLD:role.equals("suisui")?MINT:VIOLET;
        if(action.equals("charge"))return NewRoleVfxProfiles.charge(color,u,elapsed);
        if(role.equals("phoebe")){
            if(action.equals("air_glide"))return new Sample(Math.sin(angle)*.22,-.7,-u*1.6,lane%4==0?WHITE:GOLD,.45f,Kind.DUST);
            if(action.equals("liberation"))return new Sample(Math.cos(angle)*(1+p*3),2.8-u*3.6,Math.sin(angle)*(1+p*3),lane%3==0?WHITE:GOLD,.8f,lane%7==0?Kind.STAR:Kind.DUST);
            if(action.startsWith("skill")||action.equals("absolution")||action.equals("confession"))return new Sample(Math.cos(angle)*1.5,Math.sin(angle)*1.5,.9,WHITE,.65f,Kind.DUST);
            if(action.equals("air"))return new Sample(Math.cos(angle)*(.2+p),1.3-u*3,Math.sin(angle)*(.2+p),GOLD,.75f,Kind.DUST);
            return new Sample(Math.cos(angle)*.15,Math.sin(angle)*.15,.6+u*(action.contains("heavy")?3.8:2.6),lane%5==0?WHITE:GOLD,.6f,lane%8==0?Kind.STAR:Kind.DUST);
        }
        if(role.equals("suisui")){
            if(action.equals("liberation"))return new Sample(Math.cos(angle)*(1+p*4),-.8+u*.25,Math.sin(angle)*(1+p*4),lane%3==0?BLUE:MINT,.8f,Kind.DUST);
            if(action.contains("descent")||action.equals("air"))return new Sample(Math.cos(angle)*(.35+p*1.4),1.4-u*3,Math.sin(angle)*(.35+p*1.4),lane%3==0?BLUE:MINT,.7f,Kind.DUST);
            if(action.equals("cloud_dodge"))return new Sample(Math.sin(angle)*.5,-.5+u*.4,-u*1.8,WHITE,.5f,Kind.DUST);
            double arc=-2+u*4;
            return new Sample(Math.sin(arc)*(action.startsWith("rain_")?1.8:1.2),Math.cos(arc)*.55,.8+Math.cos(arc)*.6,lane%4==0?WHITE:MINT,.7f,Kind.DUST);
        }
        if(action.equals("liberation")||action.equals("skill_drain"))return new Sample(Math.cos(angle)*(1+p*2),-.6+Math.sin(angle*2)*.3,Math.sin(angle)*(1+p*2),lane%3==0?BLUE:VIOLET,.8f,Kind.DUST);
        if(action.equals("awaken"))return new Sample(Math.cos(angle)*(.3+p),Math.sin(angle)*(.3+p),1.2,VIOLET,.8f,Kind.STAR);
        if(action.equals("jellyfish"))return new Sample(Math.cos(angle)*.45,.6+Math.sin(angle)*.3,1.1,BLUE,.5f,Kind.DUST);
        return new Sample(Math.sin(angle)*(.15+u*.2),Math.cos(angle)*(.15+u*.2),.7+u*2.7,lane%4==0?BLUE:VIOLET,.6f,Kind.DUST);
    }
    static List<LightPath> ribbons(String role,String action,double progress){
        if(action.equals("charge"))return List.of();
        double p=Math.clamp(progress,0,1);var paths=new ArrayList<LightPath>();
        int color=role.equals("phoebe")?GOLD:role.equals("suisui")?MINT:VIOLET;
        if(action.equals("liberation")||action.startsWith("skill")||action.equals("absolution")||action.equals("confession")){
            for(int layer=0;layer<2;layer++){
                final int level=layer;
                stroke(paths,color,.035f,t->{double a=t*Math.TAU,r=.8+p*2+level*.16;return new Vertex(Math.cos(a)*r,role.equals("phoebe")?Math.sin(a)*r:-.65+level*.18,role.equals("phoebe")?1.2:Math.sin(a)*r);});
            }
        }else if(action.contains("descent")||action.equals("air")){
            stroke(paths,color,.055f,t->new Vertex(Math.sin(t*Math.TAU)*.3,1.5-t*3.1,1+Math.cos(t*Math.TAU)*.3));
        }else if(action.equals("air_glide")||action.equals("cloud_dodge")){
            for(int side:new int[]{-1,1})stroke(paths,color,.018f,t->new Vertex(side*.25,-.55,-t*1.5));
        }else if(role.equals("suisui")){
            stroke(paths,color,.045f,t->{double a=-2+t*4;return new Vertex(Math.sin(a)*(action.startsWith("rain_")?1.8:1.2),Math.cos(a)*.55,.8+Math.cos(a)*.6);});
        }else{
            for(int side:new int[]{-1,1})stroke(paths,color,.025f,t->new Vertex(Math.sin(t*Math.TAU+p*5)*.12*side,Math.cos(t*Math.TAU+p*5)*.12*side,.7+t*(action.contains("heavy")?3.8:2.6)));
        }
        return List.copyOf(paths);
    }
    private static void stroke(List<LightPath> result,int color,float width,java.util.function.DoubleFunction<Vertex> fn){
        var vertices=new ArrayList<Vertex>();for(int i=0;i<=32;i++)vertices.add(fn.apply(i/32d));result.add(new LightPath(vertices,color,width));
    }
}
