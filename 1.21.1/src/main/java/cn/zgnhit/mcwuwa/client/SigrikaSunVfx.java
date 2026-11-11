package cn.zgnhit.mcwuwa.client;
import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Sun glyphs and differentiated rune combinations, on retained contact clocks. */
final class SigrikaSunVfx {
    private static final int GOLD=0xffffdc9a,VIOLET=0xffc29aff,WHITE=0xfffff4e5;
    private static final String TABLET="mc_wuwa:textures/entity/sigrika_translation_tablet.png";
    private SigrikaSunVfx(){}
    static boolean supports(String a){return SigrikaSunContactClocks.ALL.containsKey(a);}
    static double delta(String a,double p){var c=SigrikaSunContactClocks.ALL.get(a);double d=Double.POSITIVE_INFINITY;
        if(c!=null)for(int h:c.hits())if(Math.abs(p*c.duration()-h)<Math.abs(d))d=p*c.duration()-h;return d;}
    static Sample sample(String a,double p,double u){var paths=ribbons(a,p);if(paths.isEmpty())return new Sample(0,0,0,GOLD,.2f,Kind.NONE);
        double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);var v=path.points().get(Math.min(24,(int)((at-(int)at)*25)));return new Sample(v.side(),v.up(),v.forward(),path.color(),.25f,Kind.DUST);}
    static List<LightPath> ribbons(String a,double p){
        if(!supports(a)||p<=0||p>=1)return List.of();
        if(a.equals("sigrika/liberation"))return NextFiveReleaseVfx.ribbons(a,p);
        double d=delta(a,p);if(d<=-2||d>=3)return List.of();
        if(EnhancedAttackVfx.supports(a))return FiveNormalAttackVfx.contactTrails(EnhancedAttackVfx.ribbons(a,.5),d);
        var out=new ArrayList<LightPath>();boolean down=a.endsWith("air")||a.endsWith("air_counter"),sun=a.contains("sun")||a.contains("insight");
        if(down){
            for(int i=0;i<3;i++){
                double x=(i-1)*.18;curve(out,i==1?WHITE:GOLD,.042f,t->new Vertex(x,2.5-t*3.4,.7+t*.6));
            }
        }else{
            for(int side:new int[]{-1,1})curve(out,side<0?GOLD:VIOLET,.045f,t->{double angle=-2.1+t*4.2;return new Vertex(Math.sin(angle)*1.7,.2+Math.cos(angle)*side*.8,1.1+Math.cos(angle)*.7);});
        }
        int points=sun?8:4;
        for(int i=0;i<points;i++){
            double angle=i*Math.TAU/points;
            curve(out,i%2==0?GOLD:VIOLET,.026f,t->new Vertex(Math.cos(angle)*t*.8,.2+Math.sin(angle)*t*.8,2.3));
        }
        return FiveNormalAttackVfx.contactTrails(out,d);
    }
    static List<LightSurface> surfaces(String a,double p){
        if(!a.equals("sigrika/liberation"))return List.of();
        double phase=ReviewedReleaseVfxClock.presentationPhase(a,p);if(phase<.08||phase>.32)return List.of();
        // Solid six-face tablet turns through the gate; no ornamental light made into a blade.
        double angle=(phase-.08)/.24*.65,cs=Math.cos(angle),sn=Math.sin(angle);
        var v=new ArrayList<Vertex>();
        for(double z:new double[]{-.07,.07})for(double y:new double[]{-.45,1.35})for(double x:new double[]{-.6,.6})v.add(new Vertex(x*cs+z*sn,y,3.2-x*sn+z*cs));
        var out=new ArrayList<LightSurface>();
        for(int[] face:new int[][]{{0,1,3,2},{5,4,6,7},{4,0,2,6},{1,5,7,3},{2,3,7,6},{4,5,1,0}})
            out.add(new LightSurface(List.of(v.get(face[0]),v.get(face[1]),v.get(face[2]),v.get(face[3])),0xeef9dda4,TABLET));
        return List.copyOf(out);
    }
    private static void curve(List<LightPath> out,int color,float width,DoubleFunction<Vertex> f){var v=new ArrayList<Vertex>();for(int i=0;i<=24;i++)v.add(f.apply(i/24d));out.add(new LightPath(v,color,width));}
}
