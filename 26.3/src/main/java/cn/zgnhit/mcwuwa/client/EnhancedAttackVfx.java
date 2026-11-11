package cn.zgnhit.mcwuwa.client;

import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Silhouettes reviewed across complete official skill-card panels. Decorations
 * follow accepted action clocks; they never supply damage or change resources. */
final class EnhancedAttackVfx {
    private static final int GOLD=0xffffd98e,VIOLET=0xffc39aff,WHITE=0xfff2fbff,BLUE=0xff78cfff,RED=0xffff4966;
    static boolean supports(String a){
        return a.startsWith("sigrika/rune_")||a.startsWith("sigrika/heavy_origin_")
            ||a.equals("sigrika/skill_semantics")
            ||Set.of("yangyang_xuanling/flow_azure","yangyang_xuanling/flow_feather",
                "camellya/ephemeral","camellya/heavy","camellya/basic_04","camellya/basic_05").contains(a);
    }
    static Sample sample(String action,double p,double u){
        var paths=ribbons(action,p);
        if(paths.isEmpty())return new Sample(0,0,0,WHITE,.2f,Kind.NONE);
        double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);
        var v=path.points().get(Math.min(24,(int)((at-(int)at)*25)));
        return new Sample(v.side(),v.up(),v.forward(),path.color(),.3f,Kind.DUST);
    }
    static List<LightPath> ribbons(String action,double progress){
        double p=Math.clamp(progress,0,1);
        if(p<.10||p>.96)return List.of();var out=new ArrayList<LightPath>();
        if(action.startsWith("sigrika/")){
            if(action.equals("sigrika/skill_semantics")){
                // A suspended luminous sphere with orbital glyphs, then a floor impact.
                double radius=.55+.6*Math.sin(p*Math.PI);
                for(int axis=0;axis<3;axis++){
                    final int plane=axis;
                    curve(out,axis==1?GOLD:VIOLET,.035f,t->{double a=t*Math.TAU;
                        return new Vertex(Math.cos(a)*radius,1.4+Math.sin(a)*radius*(plane==0?0:1),1.25+Math.sin(a)*radius*(plane==2?0:1));});
                }
                if(p>.65)ring(out,VIOLET,.06f,1.2+(p-.65)*4,-.85,1.25);
            }else if(action.endsWith("_chain")){
                // Repeated diagonals form a surrounding lattice, not two generic sweeps.
                for(int i=0;i<6;i++){
                    double x=(i-2.5)*.75;int sign=i%2==0?1:-1;
                    curve(out,i%2==0?GOLD:VIOLET,.028f,t->new Vertex(x+sign*(t-.5)*1.25,-.65+t*2.3,1.35+.25*Math.sin(t*Math.PI)));
                }
                ring(out,VIOLET,.022f,1.9,-.85,1.25);
            }else if(action.endsWith("_sun")){
                // Three petal-like linked disks around a central star.
                for(int i=0;i<3;i++){
                    double a=i*Math.TAU/3,x=Math.cos(a)*.7,y=.4+Math.sin(a)*.7;
                    curve(out,GOLD,.04f,t->new Vertex(x+Math.cos(t*Math.TAU)*.7,y+Math.sin(t*Math.TAU)*.7,1.4));
                    curve(out,VIOLET,.018f,t->new Vertex(x+Math.cos(t*Math.TAU)*.59,y+Math.sin(t*Math.TAU)*.59,1.42));
                }
                star(out,0,.4,1.4,.45,GOLD);
            }else{
                for(int sign:new int[]{-1,1}){
                    curve(out,sign<0?GOLD:VIOLET,.055f,t->new Vertex((t-.5)*3,sign*(t-.5)*2.5,1.25));
                    curve(out,WHITE,.018f,t->new Vertex((t-.5)*2.8,sign*(t-.5)*2.3,1.28));
                }
                star(out,0,0,1.3,.65,GOLD);
            }
        }else if(action.startsWith("yangyang_xuanling/")){
            boolean azure=action.endsWith("azure");
            for(int layer=0;layer<3;layer++){
                double r=1.55+layer*.18,h=layer*.06;
                curve(out,layer==1?GOLD:BLUE,layer==1?.05f:.025f,t->{
                    double a=-2.2+t*4.4+(p-.5)*.8;
                    return new Vertex(Math.sin(a)*r,azure?-.6+t*1.65+h:.05+h+Math.cos(a)*.1,1+Math.cos(a)*r*.65);
                });
            }
            if(!azure)for(int i=0;i<3;i++){
                double side=(i-1)*.7;
                curve(out,GOLD,.025f,t->new Vertex(side+(t-.5)*1.5,.15,1.3+t*1.6));
            }
        }else if(action.equals("camellya/ephemeral")){
            if(p<.25){
                for(int i=0;i<3;i++){
                    double a=i*Math.TAU/3;
                    curve(out,RED,.04f,t->new Vertex(Math.cos(a)*(.5-t*.3),-.7+t*2.7,Math.sin(a)*(.5-t*.3)));
                }
            }else{
                // Six solidly outlined, upward curling petals opening on the floor.
                double spread=1.7+Math.min(1,(p-.25)*4);
                for(int i=0;i<6;i++){
                    double a=i*Math.TAU/6;
                    curve(out,i%2==0?RED:0xffff9eaa,.065f,t->{double r=Math.sin(t*Math.PI)*spread;
                        return new Vertex(Math.cos(a)*r,-.85+Math.sin(t*Math.PI)*1.2,Math.sin(a)*r);});
                }
                ring(out,RED,.045f,spread,-.88,0);
            }
        }else if(action.equals("camellya/basic_04")){
            for(int layer=0;layer<3;layer++){
                double radius=1.5+layer*.12,height=(layer-1)*.16;
                curve(out,layer==1?WHITE:RED,layer==1?.018f:.045f,t->{double a=t*Math.TAU+p*Math.TAU*2;
                    return new Vertex(Math.cos(a)*radius,height+Math.sin(a*2)*.1,Math.sin(a)*radius);});
            }
        }else{
            boolean finisher=action.endsWith("05");
            for(int sign:new int[]{-1,1})curve(out,sign<0?RED:0xffffb1bb,.06f,t->new Vertex(sign*Math.sin(t*Math.PI)*1.6,1.5-t*2.5,1.1));
            if(finisher||p>.5)ring(out,RED,.04f,1.1+p,-.85,1.1);
        }
        return List.copyOf(out);
    }
    static List<LightSurface> surfaces(String action,double progress){
        double p=Math.clamp(progress,0,1);if(p<.1||p>.96)return List.of();
        var faces=new ArrayList<LightSurface>();
        if(action.startsWith("yangyang_xuanling/flow_")){
            boolean azure=action.endsWith("azure");
            // A thin translucent blade fan, not an opaque screen-filling flash.
            // Follow the same moving centre line as the retained outline.
            for(int i=0;i<24;i++){
                double lo=i/24d,hi=(i+1)/24d;
                faces.add(new LightSurface(List.of(flowEdge(azure,p,lo,-1),flowEdge(azure,p,hi,-1),
                    flowEdge(azure,p,hi,1),flowEdge(azure,p,lo,1)),azure?0x6278cfff:0x62ffd98e));
            }
        }else if(action.equals("camellya/ephemeral")&&p>=.25){
            double spread=1.7+Math.min(1,(p-.25)*4);
            for(int petal=0;petal<6;petal++){
                double a=petal*Math.TAU/6;
                for(int segment=0;segment<8;segment++){
                    double lo=segment/8d,hi=(segment+1)/8d;
                    faces.add(new LightSurface(List.of(petal(a,lo,-1,spread),petal(a,hi,-1,spread),petal(a,hi,1,spread),petal(a,lo,1,spread)),0x91e82d50));
                }
            }
        }else if(action.startsWith("sigrika/")&&action.endsWith("_burst")){
            for(int sign:new int[]{-1,1})faces.add(new LightSurface(List.of(
                new Vertex(-1.5,-sign*1.25,1.25),new Vertex(-1.43,-sign*1.32,1.25),
                new Vertex(1.5,sign*1.25,1.25),new Vertex(1.43,sign*1.32,1.25)),sign<0?0x70ffd98e:0x70c39aff));
        }
        return List.copyOf(faces);
    }
    private static Vertex flowEdge(boolean azure,double p,double t,int edge){
        double a=-2.2+t*4.4+(p-.5)*.8,r=1.73+edge*.15*Math.sin(t*Math.PI);
        return new Vertex(Math.sin(a)*r,azure?-.6+t*1.65+.06:.11+Math.cos(a)*.1,1+Math.cos(a)*r*.65);
    }
    private static Vertex petal(double a,double t,int side,double spread){
        double r=Math.sin(t*Math.PI)*spread,width=Math.sin(t*Math.PI)*.48;
        return new Vertex(Math.cos(a)*r-Math.sin(a)*width*side,-.85+Math.sin(t*Math.PI)*1.2,Math.sin(a)*r+Math.cos(a)*width*side);
    }
    private static void curve(List<LightPath> out,int color,float width,DoubleFunction<Vertex> f){
        var vertices=new ArrayList<Vertex>();for(int i=0;i<=24;i++)vertices.add(f.apply(i/24d));out.add(new LightPath(vertices,color,width));
    }
    private static void ring(List<LightPath> out,int c,float w,double r,double y,double z){curve(out,c,w,t->new Vertex(Math.cos(t*Math.TAU)*r,y,z+Math.sin(t*Math.TAU)*r));}
    private static void star(List<LightPath> out,double x,double y,double z,double r,int c){
        for(int sign:new int[]{-1,1})curve(out,c,.035f,t->new Vertex(x+(t-.5)*r*2,y+sign*(t-.5)*r*2,z));
    }
}
