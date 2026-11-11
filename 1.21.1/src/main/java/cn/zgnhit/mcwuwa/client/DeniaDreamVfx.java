package cn.zgnhit.mcwuwa.client;
import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Bubbles, folded dream frames and hourglass reveal from the full official film. */
final class DeniaDreamVfx {
    private static final int BLUE=0xff88dfff,PINK=0xffffa8dc,VIOLET=0xffb390ff,WHITE=0xfff1faff;
    private DeniaDreamVfx(){}
    static boolean supports(String a){return DeniaDreamContactClocks.ALL.containsKey(a);}
    static double delta(String a,double p){var c=DeniaDreamContactClocks.ALL.get(a);double d=Double.POSITIVE_INFINITY;
        if(c!=null)for(int h:c.hits())if(Math.abs(p*c.duration()-h)<Math.abs(d))d=p*c.duration()-h;return d;}
    static Sample sample(String a,double p,double u){
        var paths=ribbons(a,p);if(paths.isEmpty())return new Sample(0,0,0,BLUE,.2f,Kind.NONE);
        double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);
        var v=path.points().get(Math.min(24,(int)((at-(int)at)*25)));return new Sample(v.side(),v.up(),v.forward(),path.color(),.25f,Kind.DUST);
    }
    static boolean release(String a){return a.startsWith("denia/liberation");}
    static List<LightPath> ribbons(String a,double p){
        if(!supports(a)||p<=0||p>=1)return List.of();var out=new ArrayList<LightPath>();
        boolean dark=a.contains("disillusion")||a.contains("exile");
        if(release(a)&&p>.10&&p<.94){
            // Three-dimensional hourglass with a narrow waist, not a flat decal.
            for(int i=0;i<4;i++){
                double angle=i*Math.PI/2;
                curve(out,dark?VIOLET:BLUE,.032f,t->{double r=.12+.72*Math.pow(Math.abs(t*2-1),1.25);return new Vertex(Math.cos(angle)*r,-.6+t*2.4,2.4+Math.sin(angle)*r);});
            }
            for(int end:new int[]{-1,1})circle(out,WHITE,.026f,.84,.6+end*1.2,2.4,false);
            if(p>.3)for(int sign:new int[]{-1,1})curve(out,VIOLET,.04f,t->new Vertex(sign*t*2.6,.6+Math.sin(t*Math.PI*5)*.12,2.4));
        }
        double d=delta(a,p);if(d<=-2||d>=3)return List.copyOf(out);
        var hit=new ArrayList<LightPath>();
        if(a.contains("disillusion_heavy")){
            // Original folded rectangular trap retains real depth on its own hit clock.
            hit.addAll(CrystalDreamAttackVfx.ribbons(a,.6));
        }else if(dark){
            for(int side:new int[]{-1,1}){
                curve(hit,side<0?BLUE:VIOLET,.045f,t->new Vertex(side*Math.sin(t*Math.PI)*1.8,.25+Math.sin(t*Math.TAU)*.6,.7+t*2.4));
            }
            for(int i=0;i<3;i++)diamond(hit,(i-1)*.65,.2,2.3+i*.2,.42,i%2==0?BLUE:PINK);
        }else{
            double z=.9+Math.clamp((d+2)/5,0,1)*2.1;
            circle(hit,BLUE,.032f,.65,.25,z,true);
            circle(hit,PINK,.025f,.63,.25,z,false);
            curve(hit,WHITE,.022f,t->new Vertex(Math.sin(t*Math.TAU)*.65,.25+Math.cos(t*Math.TAU)*.65,z+Math.sin(t*Math.TAU)*.3));
            diamond(hit,0,.25,z,.3,PINK);
        }
        out.addAll(FiveNormalAttackVfx.contactTrails(hit,d));return List.copyOf(out);
    }
    static List<LightSurface> surfaces(String a,double p){
        if(!supports(a)||p<=0||p>=1||Math.abs(delta(a,p))>1.25)return List.of();
        if(a.contains("disillusion_heavy"))return List.of();var out=new ArrayList<LightSurface>();
        if(!release(a)){
            var paths=ribbons(a,p);if(paths.size()<2)return List.of();
            int alpha=(int)(36*(1-Math.abs(delta(a,p))/1.5));
            if(a.contains("disillusion")||a.contains("exile")){
                // Independent lobes must not be joined across their empty
                // middle: that produces a rectangular sheet behind the actor.
                out.addAll(SlashSurfaceGeometry.verticalWidth(paths.getFirst(),.055,(alpha<<24)|0x88dfff));
                out.addAll(SlashSurfaceGeometry.verticalWidth(paths.get(1),.055,(alpha<<24)|0xb390ff));
                return List.copyOf(out);
            }
            // The bubble's rim follows the same moving circle as its luminous
            // outline, including trail clipping. Do not leave flat wings behind.
            var edge=paths.getFirst();var inner=new ArrayList<Vertex>();
            for(var v:edge.points())inner.add(new Vertex(v.side()*.84,.25+(v.up()-.25)*.84,v.forward()));
            return SlashSurfaceGeometry.between(edge,new LightPath(inner,edge.color(),edge.width()),(alpha<<24)|0xa8dcff);
        }
        double z=a.contains("disillusion")?2.4:2.0;
        for(int side:new int[]{-1,1})out.add(new LightSurface(List.of(new Vertex(0,.25,z),new Vertex(side*.65,.7,z+.1),new Vertex(side*1.2,.3,z+.25),new Vertex(side*.45,.1,z+.05)),0x2088dfff));
        return List.copyOf(out);
    }
    private static void diamond(List<LightPath> out,double x,double y,double z,double r,int color){
        curve(out,color,.026f,t->{double angle=t*Math.TAU;return new Vertex(x+Math.copySign(Math.pow(Math.abs(Math.cos(angle)),1.4),Math.cos(angle))*r,y+Math.copySign(Math.pow(Math.abs(Math.sin(angle)),1.4),Math.sin(angle))*r,z);});
    }
    private static void circle(List<LightPath> out,int color,float width,double r,double y,double z,boolean vertical){
        curve(out,color,width,t->{double angle=t*Math.TAU;return new Vertex(Math.cos(angle)*r,y+(vertical?Math.sin(angle)*r:0),z+(vertical?0:Math.sin(angle)*r));});
    }
    private static void curve(List<LightPath> out,int color,float width,DoubleFunction<Vertex> f){var v=new ArrayList<Vertex>();for(int i=0;i<=24;i++)v.add(f.apply(i/24d));out.add(new LightPath(v,color,width));}
}
