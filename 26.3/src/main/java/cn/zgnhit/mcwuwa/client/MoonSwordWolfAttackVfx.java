package cn.zgnhit.mcwuwa.client;

import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Complete official forte panels: lunar disk, suspended sword array and flame cleaves. */
final class MoonSwordWolfAttackVfx {
    private static final int BLUE=0xff81dfff,WHITE=0xffefffff,GOLD=0xffffdc9d,RED=0xffff4964;
    static boolean supports(String a){return a.startsWith("qingxiao/immortal_basic_")||a.startsWith("qingxiao/heavy")
        ||a.startsWith("iuno/heavy_")||Set.of("lupa/heavy","lupa/heavy_wolf_bite","lupa/heavy_sharp_claw","lupa/air_flame_strike").contains(a);}
    static Sample sample(String a,double p,double u){var paths=ribbons(a,p);if(paths.isEmpty())return new Sample(0,0,0,WHITE,.2f,Kind.NONE);
        double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);var v=path.points().get(Math.min(24,(int)((at-(int)at)*25)));
        return new Sample(v.side(),v.up(),v.forward(),path.color(),.32f,Kind.DUST);}
    static List<LightPath> ribbons(String a,double progress){
        double p=Math.clamp(progress,0,1);if(p<.1||p>.96)return List.of();var out=new ArrayList<LightPath>();
        if(a.startsWith("qingxiao/")){
            if(a.contains("banishing")){
                // Orbiting sword runes converge onto the tall central sword, not an uppercut arc.
                for(int layer=0;layer<3;layer++){
                    double height=-.7+layer*1.1,r=1.7-layer*.3;
                    curve(out,layer==1?GOLD:BLUE,.03f,t->{double angle=t*Math.TAU+p*2;return new Vertex(Math.cos(angle)*r,height,1.5+Math.sin(angle)*r);});
                }
                // Source heavy: the zither/sword array gathers first; the tall
                // central blade appears at the finishing beat, not at wind-up.
                if(p>=.68)blade(out,0,4.2,1.5,WHITE,.08f);
                for(int i=0;i<4;i++){
                    double angle=i*Math.TAU/4;
                    curve(out,BLUE,.028f,t->new Vertex(Math.cos(angle)*t*1.5,-.75+t*3.5,1.5+Math.sin(angle)*t*1.5));
                }
            }else if(a.contains("immortal_basic_")){
                int stage=a.contains("04")?4:a.contains("03")?3:a.contains("02")?2:1;
                if(stage==1)blade(out,.2,3.4,1.6,WHITE,.045f);
                else if(stage==2){
                    for(int i=0;i<3;i++){double h=(i-1)*.12;curve(out,i==1?WHITE:BLUE,.035f,t->new Vertex((t-.5)*4,.2+h+(t-.5)*1.5,1.6));}
                }else{
                    ring(out,stage==3?BLUE:GOLD,.03f,1.8,stage==3?1.5:2.1,1.6);
                    for(int i=0;i<5;i++){double angle=i*Math.TAU/5;blade(out,Math.cos(angle)*1.5,stage==3?2.2:3.3,1.6+Math.sin(angle)*1.5,i%2==0?BLUE:WHITE,.03f);}
                }
            }else{
                for(int i=0;i<3;i++){double side=(i-1)*.65;blade(out,side,2.5,1.3,BLUE,.03f);}
                ring(out,GOLD,.025f,1.65,-.75,1.3);
            }
        }else if(a.startsWith("iuno/")){
            boolean perfect=a.contains("perfection"),bow=a.endsWith("bow");
            if(perfect){
                for(int plane=0;plane<3;plane++){
                    double tilt=plane*Math.PI/3;
                    curve(out,plane==0?WHITE:BLUE,plane==0?.065f:.03f,t->{double angle=t*Math.TAU;
                        return new Vertex(Math.cos(angle)*1.7,1+Math.sin(angle)*Math.cos(tilt)*1.7,1.7+Math.sin(angle)*Math.sin(tilt)*1.7);});
                }
                ring(out,GOLD,.025f,2,-.8,1.7);
                for(int i=0;i<6;i++){double angle=i*Math.TAU/6;curve(out,WHITE,.02f,t->new Vertex(Math.cos(angle)*(.9+t*1.5),1+Math.sin(angle)*(.9+t*1.5),1.7));}
            }else if(bow){
                curve(out,BLUE,.06f,t->new Vertex(Math.sin(t*Math.PI)*.65,-.7+t*1.8,.8));
                curve(out,GOLD,.02f,t->new Vertex(0,-.7+t*1.8,.8));
                curve(out,WHITE,.065f,t->new Vertex(0,.2,.8+t*4));
                for(int sign:new int[]{-1,1})curve(out,BLUE,.025f,t->new Vertex(sign*(.25-t*.25),.2,4.2+t*.6));
            }else{
                for(int layer=0;layer<3;layer++){double r=1.3+layer*.12;curve(out,layer==1?WHITE:BLUE,.04f,t->{double angle=t*Math.TAU+p*3;return new Vertex(Math.cos(angle)*r,.05+Math.sin(angle)*.2,1+Math.sin(angle)*r);});}
            }
        }else{
            boolean air=a.endsWith("air_flame_strike"),claw=a.endsWith("sharp_claw");
            for(int layer=0;layer<3;layer++){
                double r=1.8+layer*.12;
                curve(out,layer==1?WHITE:RED,layer==1?.025f:.07f,t->{double angle=-2.1+t*4.2;
                    return new Vertex(Math.sin(angle)*r,air?1.7-t*3:-.2+Math.cos(angle)*.2,1+Math.cos(angle)*.8);});
            }
            if(claw)for(int i=0;i<3;i++){double x=(i-1)*.45;curve(out,GOLD,.04f,t->new Vertex(x+(t-.5)*1.5,1.25-t*2.2,1.6));}
            else if(air)ring(out,RED,.055f,1.4+p,-.8,1.4);
            else if(a.endsWith("wolf_bite")){
                for(int sign:new int[]{-1,1})curve(out,RED,.04f,t->new Vertex(sign*(.25+Math.sin(t*Math.PI)*.5),.1+Math.sin(t*Math.PI*2)*.35,1+t*2));
            }
        }
        return List.copyOf(out);
    }
    static List<LightSurface> surfaces(String a,double p){
        if(p<.1||p>.96)return List.of();var faces=new ArrayList<LightSurface>();
        if(a.startsWith("iuno/heavy_perfection")){
            for(int latitude=0;latitude<6;latitude++)for(int longitude=0;longitude<8;longitude++){
                double lo=latitude*Math.PI/6,hi=(latitude+1)*Math.PI/6,left=longitude*Math.TAU/8,right=(longitude+1)*Math.TAU/8;
                faces.add(new LightSurface(List.of(moon(lo,left),moon(lo,right),moon(hi,right),moon(hi,left)),0x4591dfff));
            }
        }else if(a.startsWith("qingxiao/heavy_banishing")&&p>=.68){
            // Two intersecting faces retain a sword-like volume from side views.
            for(int plane=0;plane<2;plane++){
                double x=plane==0?.18:0,z=plane==1?.18:0;
                faces.add(new LightSurface(List.of(new Vertex(-x,-.7,1.5-z),new Vertex(x,-.7,1.5+z),new Vertex(x*.6,3,1.5+z*.6),new Vertex(0,3.4,1.5)),0x78a1eaff));
                faces.add(new LightSurface(List.of(new Vertex(-x,-.7,1.5-z),new Vertex(0,3.4,1.5),new Vertex(-x*.6,3,1.5-z*.6),new Vertex(-x,-.7,1.5-z)),0x78a1eaff));
            }
        }else if(a.startsWith("lupa/")&&supports(a)){
            // Published forte panel: the cleave has a broad red body and a
            // narrow bright edge. Retain the established arc, fill its taper.
            boolean air=a.endsWith("air_flame_strike");
            for(int i=0;i<20;i++){
                double lo=i/20d,hi=(i+1)/20d;
                faces.add(new LightSurface(List.of(wolfCleave(lo,air,0),wolfCleave(lo,air,1),
                    wolfCleave(hi,air,1),wolfCleave(hi,air,0)),0x38ff4964));
            }
            if(a.endsWith("sharp_claw"))for(int i=-1;i<=1;i++){
                double x=i*.45;
                faces.add(new LightSurface(List.of(new Vertex(x-.8,1.25,1.6),new Vertex(x-.7,1.25,1.6),
                    new Vertex(x+.8,-.95,1.6),new Vertex(x+.65,-.95,1.6)),0x48ffdc9d));
            }
        }
        return List.copyOf(faces);
    }
    private static Vertex wolfCleave(double t,boolean air,int inner){
        double angle=-2.1+t*4.2,r=1.8-inner*Math.sin(t*Math.PI)*.38;
        return new Vertex(Math.sin(angle)*r,air?1.7-t*3:-.2+Math.cos(angle)*.2,
            1+Math.cos(angle)*.8+inner*Math.sin(t*Math.PI)*.18);
    }
    private static Vertex moon(double latitude,double longitude){
        return new Vertex(Math.sin(latitude)*Math.cos(longitude)*1.7,1+Math.cos(latitude)*1.7,1.7+Math.sin(latitude)*Math.sin(longitude)*1.7);
    }
    private static void blade(List<LightPath> out,double x,double height,double z,int c,float w){
        curve(out,c,w,t->new Vertex(x,-.8+t*height,z));
        curve(out,BLUE,.018f,t->new Vertex(x+Math.sin(t*Math.PI)*.13,-.8+t*height,z+.03));
    }
    private static void ring(List<LightPath> out,int c,float w,double r,double y,double z){curve(out,c,w,t->new Vertex(Math.cos(t*Math.TAU)*r,y,z+Math.sin(t*Math.TAU)*r));}
    private static void curve(List<LightPath> out,int c,float w,DoubleFunction<Vertex> f){var v=new ArrayList<Vertex>();for(int i=0;i<=24;i++)v.add(f.apply(i/24d));out.add(new LightPath(v,c,w));}
}
