package cn.zgnhit.mcwuwa.client;
import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Ring rotation, falling crescent and raised moon; the moon-bow stays separate. */
final class IunoMoonRingVfx {
    record Clock(int duration,int... hits){}
    static final Map<String,Clock> CLOCKS=Map.ofEntries(
        Map.entry("iuno/air",new Clock(24,6,20)),
        Map.entry("iuno/dodge_counter",new Clock(20,4,10,16)),
        Map.entry("iuno/skill",new Clock(26,6,8,11,13,15,17,20,22)),
        Map.entry("iuno/skill_ending",new Clock(26,6,14,22)),
        Map.entry("iuno/skill_unending",new Clock(26,6,14,22)),
        Map.entry("iuno/liberation",new Clock(48,12)),
        Map.entry("iuno/intro",new Clock(24,5,7,9,11,14,16,18,20)));
    private static final int BLUE=0xff8edfff,GOLD=0xffffe3a5,WHITE=0xfff1fcff;
    private IunoMoonRingVfx(){}
    static boolean supports(String a){return CLOCKS.containsKey(a);}
    static double delta(String a,double p){var c=CLOCKS.get(a);double d=Double.POSITIVE_INFINITY;
        if(c!=null)for(int h:c.hits())if(Math.abs(p*c.duration()-h)<Math.abs(d))d=p*c.duration()-h;return d;}
    static Sample sample(String a,double p,double u){var paths=ribbons(a,p);if(paths.isEmpty())return new Sample(0,0,0,BLUE,.2f,Kind.NONE);
        double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);var v=path.points().get(Math.min(24,(int)((at-(int)at)*25)));return new Sample(v.side(),v.up(),v.forward(),path.color(),.25f,Kind.DUST);}
    static List<LightPath> ribbons(String a,double p){
        if(!supports(a)||p<=0||p>=1)return List.of();var out=new ArrayList<LightPath>();
        if(a.equals("iuno/liberation")&&p>.1&&p<.93){
            // Moon rises above and behind the performer, out of the aiming path.
            for(int i=0;i<3;i++){
                double angle=i*Math.PI/3;
                curve(out,i==0?WHITE:BLUE,.028f,t->{double q=t*Math.TAU;return new Vertex(Math.cos(q)*1.1*Math.cos(angle),2.4+Math.sin(q)*1.1,-1.2+Math.cos(q)*1.1*Math.sin(angle));});
            }
        }
        double d=delta(a,p);if(d<=-2||d>=3)return List.copyOf(out);var contact=new ArrayList<LightPath>();
        boolean down=a.equals("iuno/air"),rise=a.equals("iuno/intro");
        for(int layer=0;layer<3;layer++){
            double r=1.6+layer*.1;
            curve(contact,layer==1?GOLD:BLUE,.042f,t->{double angle=-2.2+t*4.4;
                return down?new Vertex(Math.sin(angle)*.8*r/1.7,2.5-t*3.3,1+Math.cos(angle)*.5*r/1.7)
                    :new Vertex(Math.sin(angle)*r,.2+Math.cos(angle)*(rise?1.4:.75),1.3+Math.cos(angle)*r*.55);});
        }
        for(int i=0;i<4;i++){
            double angle=i*Math.PI/2;
            curve(contact,WHITE,.024f,t->new Vertex(Math.cos(angle)*t*.55,.25+Math.sin(angle)*t*.55,2.5));
        }
        out.addAll(FiveNormalAttackVfx.contactTrails(contact,d));return List.copyOf(out);
    }
    static List<LightSurface> surfaces(String a,double p){
        if(!a.equals("iuno/liberation")){
            var paths=ribbons(a,p);if(paths.size()<3)return List.of();
            int alpha=(int)Math.round(32*Math.clamp(1-Math.max(0,delta(a,p))/3,0,1));
            return SlashSurfaceGeometry.between(paths.get(0),paths.get(2),(alpha<<24)|(BLUE&0xffffff));
        }
        if(p<=.1||p>=.93)return List.of();
        var out=new ArrayList<LightSurface>();
        for(int y=0;y<6;y++)for(int x=0;x<12;x++){
            out.add(new LightSurface(List.of(moon(x,y),moon(x+1,y),moon(x+1,y+1),moon(x,y+1)),0x188edfff));
        }return List.copyOf(out);
    }
    private static Vertex moon(int x,int y){double lat=-Math.PI/2+y*Math.PI/6,lon=x*Math.TAU/12;return new Vertex(Math.cos(lat)*Math.cos(lon)*1.1,2.4+Math.sin(lat)*1.1,-1.2+Math.cos(lat)*Math.sin(lon)*1.1);}
    private static void curve(List<LightPath> out,int color,float width,DoubleFunction<Vertex> f){var v=new ArrayList<Vertex>();for(int i=0;i<=24;i++)v.add(f.apply(i/24d));out.add(new LightPath(v,color,width));}
}
