package cn.zgnhit.mcwuwa.client;
import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;
/** Official BV1hip5zeEvM58-69s: luminous moon-bow, not the later story prop. */
public final class IunoMoonBowVfx {
    record Clock(int duration,int[] hits){}
    private static final Map<String,Clock> CLOCKS=Map.ofEntries(
        Map.entry("iuno/bow_basic_01",new Clock(14,new int[]{3})),
        Map.entry("iuno/bow_basic_02",new Clock(18,new int[]{4,9,14})),
        Map.entry("iuno/bow_basic_03",new Clock(24,new int[]{6,20})),
        Map.entry("iuno/bow_counter",new Clock(24,new int[]{6,13,20})),
        Map.entry("iuno/skill_beyond",new Clock(26,new int[]{6,22})),
        Map.entry("iuno/bow_basic_01_enhanced",new Clock(14,new int[]{3})),
        Map.entry("iuno/bow_basic_02_enhanced",new Clock(18,new int[]{4,9,14})),
        Map.entry("iuno/bow_basic_03_enhanced",new Clock(24,new int[]{6,20})),
        Map.entry("iuno/bow_counter_enhanced",new Clock(24,new int[]{6,13,20})),
        Map.entry("iuno/skill_beyond_enhanced",new Clock(26,new int[]{6,22})));
    private IunoMoonBowVfx(){}
    static boolean supports(String action){return CLOCKS.containsKey(action);}
    /** The two projected bow limbs stay by the hand; the complete arrow follows aim. */
    public static List<LightPath> aimedPaths(String action,List<LightPath> paths,float pitch){
        if(!supports(action)||pitch==0)return paths;
        double angle=Math.toRadians(pitch),c=Math.cos(angle),s=Math.sin(angle);
        var result=new ArrayList<LightPath>();
        for(int i=0;i<paths.size();i++){
            var path=paths.get(i);
            if(i<2){result.add(path);continue;}
            result.add(new LightPath(path.points().stream().map(v->{
                double y=v.up()-.15,z=v.forward()-.65;
                return new Vertex(v.side(),.15+y*c-z*s,.65+y*s+z*c);
            }).toList(),path.color(),path.width()));
        }
        return List.copyOf(result);
    }
    static Sample aimedSample(String action,double p,double u,float pitch,Sample fallback){
        if(!supports(action))return fallback;
        var paths=aimedPaths(action,ribbons(action,p),pitch);
        if(paths.isEmpty())return new Sample(0,0,0,0xffffffff,.24f,Kind.NONE);
        double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);
        var v=path.points().get(Math.min(path.points().size()-1,(int)((at-(int)at)*path.points().size())));
        return new Sample(v.side(),v.up(),v.forward(),path.color(),.24f,Kind.DUST);
    }
    private static double delta(String action,double p){
        var c=CLOCKS.get(action);if(c==null)return Double.POSITIVE_INFINITY;double d=Double.POSITIVE_INFINITY;
        for(int h:c.hits())if(Math.abs(p*c.duration()-h)<Math.abs(d))d=p*c.duration()-h;return d;
    }
    static Sample sample(String action,double p,double u){
        var paths=ribbons(action,p);if(paths.isEmpty())return new Sample(0,0,0,0xffffffff,.2f,Kind.NONE);
        double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);var v=path.points().get(Math.min(24,(int)((at-(int)at)*25)));
        return new Sample(v.side(),v.up(),v.forward(),path.color(),.24f,Kind.DUST);
    }
    static List<LightPath> ribbons(String action,double p){
        double d=delta(action,p);if(d<=-2||d>=3)return List.of();var out=new ArrayList<LightPath>();
        double z=1+Math.clamp((d+1)/3,0,1)*3.5;
        // Two translucent limbs/string form the projected bow; the shot advances.
        for(int sign:new int[]{-1,1})curve(out,sign>0?0xffa7f4ff:0xffffe4ac,.026f,t->new Vertex(.32+sign*Math.sin(t*Math.PI)*.32,-.65+t*1.5,.7));
        for(int layer=-1;layer<=1;layer++){
            double side=layer*.07;curve(out,layer==0?0xffedffff:0xff7bdcff,layer==0?.04f:.023f,t->new Vertex(side,.15,.65+t*(z-.65)));
        }
        for(int sign:new int[]{-1,1})curve(out,0xffbcf3ff,.03f,t->new Vertex(sign*t*.26,.15,z-t*.5));
        for(int axis=0;axis<2;axis++){final int a=axis;curve(out,0xffffebc2,.02f,t->new Vertex(a==0?-.25+t*.5:0,.15+(a==1?-.25+t*.5:0),z));}
        return FiveNormalAttackVfx.contactTrails(out,d);
    }
    private static void curve(List<LightPath> out,int color,float width,DoubleFunction<Vertex> fn){var points=new ArrayList<Vertex>();for(int i=0;i<=24;i++)points.add(fn.apply(i/24d));out.add(new LightPath(List.copyOf(points),color,width));}
}
