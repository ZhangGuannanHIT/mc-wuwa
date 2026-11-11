package cn.zgnhit.mcwuwa.client;

import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Original full liberation GIF silhouettes, fitted to the shared authored clock. */
final class TideFireReleaseVfx {
    private static final int BLUE=0xff91dfff,VIOLET=0xffc8a0ff,GOLD=0xffffda86,WHITE=0xfff3fbff,MINT=0xff99ecd1,FIRE=0xffff754e;
    static boolean supports(String a){return Set.of("galbrena/liberation","phoebe/liberation","suisui/liberation","cantarella/liberation","changli/liberation").contains(a);}
    static Sample sample(String action,double progress,double u){
        var paths=ribbons(action,progress);if(paths.isEmpty())return new Sample(0,0,0,WHITE,.2f,Kind.NONE);
        double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);
        var v=path.points().get(Math.min(path.points().size()-1,(int)((at-(int)at)*path.points().size())));
        return new Sample(v.side(),v.up(),v.forward(),path.color(),.3f,Kind.DUST);
    }
    static List<LightPath> ribbons(String action,double progress){
        double p=ReviewedReleaseVfxClock.presentationPhase(action,progress);if(p<.04||p>.985)return List.of();
        var paths=new ArrayList<LightPath>();
        if(action.startsWith("galbrena/")){
            if(p<.25){
                for(int side:new int[]{-1,1})curve(paths,side<0?BLUE:VIOLET,.025f,t->new Vertex(side*(.2+Math.sin(t*Math.PI)*.6),.6+t*.7,.7));
            }else{
                for(int i=0;i<3;i++){
                    double x=(i-1)*.25;
                    curve(paths,i==1?WHITE:BLUE,i==1?.06f:.025f,t->new Vertex(x+Math.sin(t*24+p*5)*.12,3.8-t*4.6,1.8));
                }
                for(int i=0;i<6;i++){
                    var blade=infernalBlade(i,p);var start=blade.get(0);var tip=blade.get(2);
                    curve(paths,i%2==0?BLUE:VIOLET,.025f,t->new Vertex(start.side()+(tip.side()-start.side())*t,
                        start.up()+(tip.up()-start.up())*t,start.forward()+(tip.forward()-start.forward())*t));
                }
            }
        }else if(action.startsWith("phoebe/")){
            double up=p<.25?.5+p*2:1.1,rad=p<.25?.25+p:.75;
            for(int i=0;i<4;i++){
                double a=i*Math.TAU/4,b=(i+1)*Math.TAU/4;
                curve(paths,GOLD,.025f,t->t<.5?new Vertex(Math.cos(a)*rad*t*2,up+(1-t*2)*rad,.9+Math.sin(a)*rad*t*2):new Vertex(Math.cos(a)*rad*(2-t*2),up-(t*2-1)*rad,.9+Math.sin(a)*rad*(2-t*2)));
                curve(paths,WHITE,.018f,t->new Vertex(Math.cos(a)*(1-t)*rad+Math.cos(b)*t*rad,up,.9+Math.sin(a)*(1-t)*rad+Math.sin(b)*t*rad));
            }
            if(p>=.25)for(int i=0;i<4;i++){
                double a=i*Math.TAU/4;
                curve(paths,GOLD,.025f,t->new Vertex(Math.cos(a)*t*2.9,up+Math.sin(a)*t*1.8,.9+t*.7));
            }
        }else if(action.startsWith("suisui/")){
            for(int side:new int[]{-1,1})for(int layer=0;layer<3;layer++){
                double lift=layer*.14;
                curve(paths,layer==1?GOLD:MINT,.02f,t->new Vertex(side*(.5+t*2.8),-.1+Math.sin(t*Math.PI+p*2)*.7+lift,.5+t*.9));
            }
            if(p>=.52)for(int i=0;i<3;i++){
                double radius=1.3+i*.6;
                curve(paths,i==1?GOLD:MINT,.025f,t->new Vertex(Math.cos(t*Math.TAU)*radius,-.8,Math.sin(t*Math.TAU)*radius));
            }
        }else if(action.startsWith("cantarella/")){
            double radius=p<.25?.3:.65;
            for(int i=0;i<4;i++){
                double meridian=i*Math.PI/4,tentacle=i*Math.TAU/4;
                curve(paths,i%2==0?BLUE:VIOLET,.022f,t->new Vertex(.25+Math.cos(meridian)*Math.cos(t*Math.PI)*radius,.4+Math.sin(t*Math.PI)*radius,.9+Math.sin(meridian)*Math.cos(t*Math.PI)*radius));
                curve(paths,VIOLET,.014f,t->new Vertex(.25+Math.cos(tentacle)*radius*.6+Math.sin(t*8)*.08,.4-t*.9,.9+Math.sin(tentacle)*radius*.6));
            }
            curve(paths,BLUE,.018f,t->new Vertex(.25+Math.cos(t*Math.TAU)*radius,.4,.9+Math.sin(t*Math.TAU)*radius));
            if(p>=.25)for(int i=0;i<3;i++){
                double r=1.2+i*.65;
                curve(paths,i%2==0?BLUE:VIOLET,.025f,t->new Vertex(Math.cos(t*Math.TAU)*r,-.55,1+Math.sin(t*Math.TAU)*r));
            }
        }else{
            for(int side:new int[]{-1,1})for(int feather=0;feather<5;feather++){
                double spread=.7+feather*.42,height=1.2+feather*.22;
                curve(paths,feather%2==0?FIRE:GOLD,.028f,t->{double s=Math.sin(t*Math.PI);return new Vertex(side*(.4+t*spread),-.35+s*height,.2+Math.sin(t*Math.PI)*.4);});
            }
            if(p>=.25)curve(paths,FIRE,.035f,t->new Vertex(Math.cos(t*Math.TAU)*2.8,-.8,Math.sin(t*Math.TAU)*2.8));
        }
        return List.copyOf(paths);
    }
    static List<LightSurface> surfaces(String action,double progress){
        if(!action.equals("galbrena/liberation"))return List.of();
        double p=ReviewedReleaseVfxClock.presentationPhase(action,progress);
        if(p<.25||p>.985)return List.of();
        var out=new ArrayList<LightSurface>();
        // Full release panel 3.5--5.25s: dark jagged blades surround the
        // retained blue column. These are local geometry, not extra hits.
        for(int i=0;i<6;i++)out.add(new LightSurface(infernalBlade(i,p),i%2==0?0x881e204e:0x782b245b));
        return List.copyOf(out);
    }
    private static List<Vertex> infernalBlade(int index,double p){
        double angle=index*Math.TAU/6,cs=Math.cos(angle),sn=Math.sin(angle);
        double growth=Math.clamp((p-.18)/.07,0,1)*Math.clamp((.985-p)/.18,0,1);
        double reach=.45+2.35*growth,height=-.8+(1.55+(index%2)*.35)*growth;
        return List.of(new Vertex(cs*.45-sn*.13,-.8,1.8+sn*.45+cs*.13),
            new Vertex(cs*reach-sn*.20,-.8,1.8+sn*reach+cs*.20),
            new Vertex(cs*(reach-.25),height,1.8+sn*(reach-.25)),
            new Vertex(cs*.9+sn*.13,-.5,1.8+sn*.9-cs*.13));
    }
    private static void curve(List<LightPath> paths,int color,float width,DoubleFunction<Vertex> shape){
        var points=new ArrayList<Vertex>();for(int i=0;i<=24;i++)points.add(shape.apply(i/24d));paths.add(new LightPath(points,color,width));
    }
}
