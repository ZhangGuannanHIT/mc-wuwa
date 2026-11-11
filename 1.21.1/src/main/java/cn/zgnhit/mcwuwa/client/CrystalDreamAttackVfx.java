package cn.zgnhit.mcwuwa.client;

import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Reviewed crystal fan and dream-stage/illusion heavy panels; visual authority only. */
final class CrystalDreamAttackVfx {
    private static final int BLUE=0xff8adfff,PINK=0xffff94df,WHITE=0xffefffff,PURPLE=0xffb59aff;
    static boolean supports(String a){return a.startsWith("carlotta/heavy")||a.equals("denia/heavy")||a.startsWith("denia/disillusion_heavy");}
    static Sample sample(String a,double p,double u){var paths=ribbons(a,p);if(paths.isEmpty())return new Sample(0,0,0,WHITE,.2f,Kind.NONE);
        double at=Math.clamp(u,0,.999999)*paths.size();var line=paths.get((int)at);var v=line.points().get(Math.min(24,(int)((at-(int)at)*25)));
        return new Sample(v.side(),v.up(),v.forward(),line.color(),.3f,Kind.DUST);}
    static List<LightPath> ribbons(String a,double progress){
        double p=Math.clamp(progress,0,1);if(p<.1||p>.96)return List.of();var out=new ArrayList<LightPath>();
        if(a.startsWith("carlotta/")){
            boolean finish=a.contains("end_of_road");
            for(int i=0;i<5;i++){
                double side=(i-2)*.28;
                curve(out,i%2==0?PINK:BLUE,i==2?.055f:.025f,t->new Vertex(side*t,.15+Math.sin(t*Math.PI)*.12,.7+t*3.0));
            }
            if(finish && p<.68){
                // Faceted glass half-disk at the target, followed by shards.
                for(int layer=0;layer<2;layer++){
                    double r=(1.45+layer*.15)*fanScale(p);
                    curve(out,layer==0?BLUE:WHITE,.032f,t->{double angle=t*Math.PI;return new Vertex(Math.cos(angle)*r,-.65+Math.sin(angle)*r,2.6);});
                }
                for(int i=0;i<4;i++){
                    double angle=(i+.5)*Math.PI/4;
                    curve(out,PINK,.022f,t->new Vertex(Math.cos(angle)*t*1.6,-.65+Math.sin(angle)*t*1.6,2.6));
                }
            }else{
                for(int sign:new int[]{-1,1})curve(out,sign<0?PINK:BLUE,.04f,t->new Vertex(sign*Math.sin(t*Math.PI)*.65,.15+Math.sin(t*Math.TAU)*.4,2.6));
            }
        }else if(a.equals("denia/heavy")){
            for(int layer=0;layer<4;layer++){
                double h=layer*.12,r=1.5+layer*.15;
                curve(out,layer%2==0?BLUE:WHITE,.045f,t->{double angle=-2+t*4;return new Vertex(Math.sin(angle)*r,.15+h+Math.cos(angle)*.4,.9+Math.cos(angle)*.7);});
            }
            for(int sign:new int[]{-1,1})curve(out,PINK,.022f,t->new Vertex(sign*Math.sin(t*Math.PI)*.7,-.75+t*.8,1.1+t*.6));
        }else{
            if(p<.4){
                for(int sign:new int[]{-1,1})curve(out,sign<0?PURPLE:BLUE,.06f,t->new Vertex(sign*(.65-t*.4),.15+sign*Math.sin(t*Math.PI)*.25,.6+t*2.7));
            }else{
                // A three-dimensional luminous trap frame, not a generic horizontal circle.
                Vertex[] v={new Vertex(-.75,-.7,1.8),new Vertex(.75,-.7,1.8),new Vertex(.75,1.5,1.8),new Vertex(-.75,1.5,1.8),
                    new Vertex(-.75,-.7,2.7),new Vertex(.75,-.7,2.7),new Vertex(.75,1.5,2.7),new Vertex(-.75,1.5,2.7)};
                for(int[] edge:new int[][]{{0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},{0,4},{1,5},{2,6},{3,7}}){
                    Vertex x=v[edge[0]],y=v[edge[1]];
                    curve(out,edge[0]%2==0?PURPLE:BLUE,.03f,t->new Vertex(x.side()+(y.side()-x.side())*t,x.up()+(y.up()-x.up())*t,x.forward()+(y.forward()-x.forward())*t));
                }
            }
        }
        return List.copyOf(out);
    }
    static List<LightSurface> surfaces(String a,double p){
        if(p<.2||p>.9||!a.startsWith("carlotta/heavy_end_of_road"))return List.of();var out=new ArrayList<LightSurface>();
        double breakup=Math.clamp((p-.62)/.28,0,1),scale=fanScale(p);
        for(int i=0;i<8;i++){
            double lo=i*Math.PI/8,hi=(i+1)*Math.PI/8,mid=(lo+hi)*.5,r=1.6*scale;
            // Each facet separates along its own radial direction after the fan opens.
            double dx=Math.cos(mid)*breakup*.85,dy=Math.sin(mid)*breakup*.7-breakup*breakup*.45;
            double z=2.6+breakup*(.4+(i%3)*.16);
            var center=new Vertex(dx, -.65+dy,z);
            int alpha=(int)((i%2==0?0x67:0x4f)*(1-breakup*.85));
            out.add(new LightSurface(List.of(center,new Vertex(dx+Math.cos(lo)*r,-.65+dy+Math.sin(lo)*r,z),new Vertex(dx+Math.cos(hi)*r,-.65+dy+Math.sin(hi)*r,z),center),
                (alpha<<24)|(i%2==0?0x8adfff:0xff94df)));
        }
        return List.copyOf(out);
    }
    private static double fanScale(double p){return .25+.75*Math.clamp((p-.2)/.3,0,1);}
    private static void curve(List<LightPath> out,int c,float w,DoubleFunction<Vertex> f){var v=new ArrayList<Vertex>();for(int i=0;i<=24;i++)v.add(f.apply(i/24d));out.add(new LightPath(v,c,w));}
}
