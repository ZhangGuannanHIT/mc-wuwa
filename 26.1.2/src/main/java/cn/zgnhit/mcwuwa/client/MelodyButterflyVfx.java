package cn.zgnhit.mcwuwa.client;

import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Curved five-line staves and flying butterflies on retained combat contacts. */
final class MelodyButterflyVfx {
    private static final int MINT=0xff86f4de,GOLD=0xffffdc89,BLUE=0xff95ddff,VIOLET=0xffc6a5ff;
    private MelodyButterflyVfx(){}
    static boolean supports(String a){return MelodyButterflyContactClocks.ALL.containsKey(a);}
    static double delta(String a,double p){
        var c=MelodyButterflyContactClocks.ALL.get(a);double d=Double.POSITIVE_INFINITY;
        if(c!=null)for(int h:c.hits())if(Math.abs(p*c.duration()-h)<Math.abs(d))d=p*c.duration()-h;
        return d;
    }
    static Sample sample(String a,double p,double u){
        var paths=ribbons(a,p);if(paths.isEmpty())return new Sample(0,0,0,MINT,.2f,Kind.NONE);
        double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);
        var v=path.points().get(Math.min(24,(int)((at-(int)at)*25)));
        return new Sample(v.side(),v.up(),v.forward(),path.color(),.24f,Kind.DUST);
    }
    static List<LightPath> ribbons(String a,double p){
        if(!supports(a)||p<=0||p>=1)return List.of();
        if(a.endsWith("/liberation")){
            var out=new ArrayList<>(NextFiveReleaseVfx.ribbons(a,p));
            if(a.startsWith("shorekeeper/")&&ReviewedReleaseVfxClock.presentationPhase(a,p)>.52){
                // Matching top/bottom astrolabe discs; persistent domain owns its lifetime.
                for(double y:new double[]{-.85,2.7})for(int i=0;i<3;i++){
                    double r=2.2+i*.3;
                    curve(out,i==1?GOLD:BLUE,.016f,t->new Vertex(Math.cos(t*Math.TAU)*r,y,Math.sin(t*Math.TAU)*r));
                }
            }
            return List.copyOf(out);
        }
        double d=delta(a,p);if(d<=-2||d>=3)return List.of();
        var out=new ArrayList<LightPath>();boolean melody=a.startsWith("ciaccona/");
        if(melody){
            boolean down=a.contains("/air_");
            for(int i=0;i<5;i++){
                double h=(i-2)*.12;
                curve(out,i%2==0?MINT:GOLD,.018f,t->{double q=-1.8+t*3.6;
                    return down?new Vertex(Math.sin(q)*.8,2.2-t*3+h,1.4+Math.cos(q)*.35)
                        :new Vertex(Math.sin(q)*1.8,.15+h+Math.sin(t*Math.PI)*.35,1.4+Math.cos(q)*1.1);});
            }
            if(a.equals("ciaccona/heavy_quartet")){
                // The quadruple downbeat holds one tall gold clef at the
                // target, rather than the three ordinary detached note heads.
                double[][] clef={{0,-.4},{-.15,-.35},{-.2,-.18},{-.05,-.08},{.15,-.18},
                    {.12,-.38},{0,-.42},{.02,.8},{.20,1.12},{.24,1.4},{.08,1.45},
                    {-.1,1.2},{-.02,.75},{.2,.45},{.28,.2},{.14,.02},{-.1,.02},
                    {-.28,.16},{-.28,.4},{-.1,.57},{.12,.56},{.24,.44},{.21,.25},{.07,.21},{0,.3}};
                var points=new ArrayList<Vertex>();
                for(var point:clef)points.add(new Vertex(point[0],.35+point[1],2.2));
                out.add(new LightPath(List.copyOf(points),GOLD,.035f));
            }else for(int i=0;i<3;i++){
                double x=(i-1)*1.05,z=2.1+Math.cos(i)*.2;
                curve(out,GOLD,.024f,t->new Vertex(x+Math.cos(t*Math.TAU)*.12,.45+Math.sin(t*Math.TAU)*.08,z));
                curve(out,MINT,.018f,t->new Vertex(x+.1,.45+t*.45,z));
            }
        }else{
            boolean fall=a.contains("/air");
            for(int i=0;i<6;i++){
                double angle=i*Math.TAU/6,x=Math.cos(angle)*1.6,y=fall?1.8-i*.45:.15+Math.sin(angle)*.5,z=1.6+Math.sin(angle)*.7;
                // Separate upper/lower lobes create butterfly silhouettes rather than radial sparks.
                for(int sign:new int[]{-1,1})for(int lobe=0;lobe<2;lobe++){
                    double size=lobe==0?.3:.19,drop=lobe==0?0:-.22;
                    curve(out,i%2==0?BLUE:VIOLET,.02f,t->new Vertex(x+sign*Math.sin(t*Math.PI)*size,y+drop+Math.sin(t*Math.TAU)*size*.7,z+Math.sin(t*Math.PI)*.12));
                }
            }
            if(a.equals("shorekeeper/heavy_deduction")){
                // Published deduction panel: paired blue/violet spirals gather
                // around the target; the six evolved butterflies stay separate.
                for(int strand=0;strand<2;strand++){
                    double offset=strand*Math.PI;
                    curve(out,strand==0?BLUE:VIOLET,.03f,t->{
                        double q=t*Math.TAU*1.25+p*3+offset;
                        double r=(.35+1.65*(1-p))*(1-t*.55);
                        return new Vertex(Math.cos(q)*r,-.55+t*1.25,1.6+Math.sin(q)*r);
                    });
                }
            }else curve(out,BLUE,.025f,t->{double q=-2+t*4;return new Vertex(Math.sin(q)*1.8,.1+Math.cos(q)*.5,1.4+Math.cos(q));});
        }
        return FiveNormalAttackVfx.contactTrails(out,d);
    }
    static List<LightSurface> surfaces(String a,double p){
        if(!a.startsWith("shorekeeper/")||a.endsWith("/liberation")||!supports(a))return List.of();
        double d=delta(a,p);if(d<=-2||d>=3)return List.of();
        // Fill the actual six butterflies, including their falling positions.
        // The old fallback drew three static triangles unrelated to these wings.
        var paths=ribbons(a,p);var out=new ArrayList<LightSurface>();
        for(int wing=0;wing<Math.min(24,paths.size());wing++){
            var path=paths.get(wing);var points=path.points();var origin=points.getFirst();
            int color=(path.color()&0xffffff)|(Math.round(((path.color()>>>24)&255)*.22f)<<24);
            int previous=1;
            for(int next:new int[]{6,12,18,24}){
                out.add(new LightSurface(List.of(origin,points.get(previous),points.get(next),origin),color));
                previous=next;
            }
        }
        return List.copyOf(out);
    }
    private static void curve(List<LightPath> out,int color,float width,DoubleFunction<Vertex> f){
        var points=new ArrayList<Vertex>();for(int i=0;i<=24;i++)points.add(f.apply(i/24d));out.add(new LightPath(points,color,width));
    }
}
