package cn.zgnhit.mcwuwa.client;
import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;
/** Full-film solar silhouettes, lightning and blade planes on retained server contacts. */
final class AugustaThunderVfx {
    record Clock(int duration,int... hits){}
    private static final Map<String,Clock> CLOCKS=Map.ofEntries(
        Map.entry("augusta/heavy",new Clock(26,7,14,22)),
        Map.entry("augusta/air",new Clock(24,6,20)),
        Map.entry("augusta/dodge_counter",new Clock(20,4,16)),
        Map.entry("augusta/air_counter",new Clock(24,6,20)),
        Map.entry("augusta/heavy_retreat",new Clock(26,7)),
        Map.entry("augusta/heavy_spin",new Clock(26,7,14,22)),
        Map.entry("augusta/heavy_uppercut",new Clock(26,7,22)),
        Map.entry("augusta/counter_heavy",new Clock(26,7,14,22)),
        Map.entry("augusta/counter_retreat",new Clock(24,6)),
        Map.entry("augusta/skill",new Clock(26,6,14,22)),
        Map.entry("augusta/liberation",new Clock(48,12,17,21,26,30,35,39,44)),
        Map.entry("augusta/realm_sun",new Clock(12,6)),
        Map.entry("augusta/realm_finale",new Clock(24,6,7,9,10,11,12,14,15,16,17,19,20)),
        Map.entry("augusta/intro",new Clock(24,5,20)),
        Map.entry("augusta/skill_rush",new Clock(26,6,22)),
        Map.entry("augusta/skill_leap",new Clock(26,6,14,22)),
        Map.entry("augusta/skill_descent",new Clock(26,6,22)),
        Map.entry("augusta/counter_rush",new Clock(24,6,20)));
    private static final int RED=0xffff7957,GOLD=0xffffce83,WHITE=0xffffefcd;
    private AugustaThunderVfx(){}
    static boolean supports(String a){return CLOCKS.containsKey(a);}
    static double delta(String a,double p){var c=CLOCKS.get(a);if(c==null)return Double.POSITIVE_INFINITY;double d=Double.POSITIVE_INFINITY;for(int h:c.hits())if(Math.abs(p*c.duration()-h)<Math.abs(d))d=p*c.duration()-h;return d;}
    static Sample sample(String a,double p,double u){var paths=ribbons(a,p);if(paths.isEmpty())return new Sample(0,0,0,WHITE,.2f,Kind.NONE);double n=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)n);var v=path.points().get(Math.min(24,(int)((n-(int)n)*25)));return new Sample(v.side(),v.up(),v.forward(),path.color(),.3f,Kind.DUST);}
    static List<LightPath> ribbons(String a,double p){
        if(!supports(a)||p<=0||p>=1)return List.of();var out=new ArrayList<LightPath>();double d=delta(a,p);
        boolean release=a.equals("augusta/liberation")||a.equals("augusta/realm_finale");
        if(release&&p>.1&&p<.86){
            // Solar disk is behind/above the actor, leaving the target and ground visible.
            double radius=1.05+Math.sin(p*Math.PI)*.45;
            for(int layer=0;layer<2;layer++){double r=radius+layer*.09;curve(out,layer==0?GOLD:WHITE,.035f,t->{double q=t*Math.TAU;return new Vertex(Math.cos(q)*r,2.2+Math.sin(q)*r,-.7);});}
            for(int i=0;i<8;i++){double angle=i*Math.TAU/8+p*.3;curve(out,GOLD,.022f,t->new Vertex(Math.cos(angle)*(radius+t*.4),2.2+Math.sin(angle)*(radius+t*.4),-.7));}
        }
        if(d<=-2||d>=3)return List.copyOf(out);
        var impact=new ArrayList<LightPath>();
        boolean descent=a.contains("descent")||a.equals("augusta/air")||a.equals("augusta/air_counter")||a.equals("augusta/realm_finale");
        boolean rise=a.contains("uppercut")||a.contains("leap");
        if(descent){
            for(int i=-1;i<=1;i++){double x=i*.13;curve(impact,i==0?WHITE:RED,.05f,t->new Vertex(x+Math.sin(t*25)*.07,3.4-t*4.2,1.7));}
            for(int i=0;i<4;i++){double angle=i*Math.PI/2;curve(impact,GOLD,.025f,t->new Vertex(Math.cos(angle)*t*2.8,-.7,1.7+Math.sin(angle)*t*1.6));}
        }else if(rise){
            for(int i=0;i<3;i++){double z=1.3+i*.08;curve(impact,i==1?WHITE:RED,.05f,t->new Vertex(Math.sin(t*Math.PI)*.8,-.85+t*3.7,z));}
        }else if(a.contains("rush")){
            for(int sign:new int[]{-1,1})curve(impact,RED,.04f,t->new Vertex(sign*.35+Math.sin(t*28)*.08,-.1,.45+t*3.4));
            curve(impact,WHITE,.045f,t->new Vertex(0,.1,.6+t*3.2));
        }else{
            boolean reverse=a.contains("retreat")||a.equals("augusta/realm_sun");
            for(int layer=0;layer<3;layer++){double r=2.15+layer*.08;curve(impact,layer==1?WHITE:RED,.045f,t->{double q=-2.25+t*4.5+(release?0:(p-.5)*1.2);return new Vertex((reverse?-1:1)*Math.sin(q)*r,.12+Math.cos(q)*.3,1.1+Math.cos(q)*r*.6);});}
        }
        // Jagged branching lightning follows the contact plane instead of filling the screen.
        for(int sign:new int[]{-1,1})curve(impact,GOLD,.018f,t->new Vertex(sign*(.25+t*1.8)+Math.sin(t*35+d)*.1,.25+Math.sin(t*17)*.12,1.9+t*.8));
        out.addAll(FiveNormalAttackVfx.contactTrails(impact,d));return List.copyOf(out);
    }
    static List<LightSurface> surfaces(String a,double p){
        if(!supports(a)||p<=0||p>=1)return List.of();
        boolean release=a.equals("augusta/liberation")||a.equals("augusta/realm_finale");
        double d=delta(a,p);
        if(!release){
            if(d<=-2||d>=3)return List.of();
            var paths=ribbons(a,p);if(paths.size()<3)return List.of();
            int color=((int)Math.round(40*Math.clamp(1-Math.max(0,d)/3,0,1))<<24)|0xffae84;
            return SlashSurfaceGeometry.between(paths.get(0),paths.get(a.contains("rush")?1:2),color);
        }
        if(Math.abs(d)>1.4)return List.of();
        if(a.contains("descent")||a.endsWith("/air")||a.endsWith("air_counter")||a.endsWith("realm_finale"))return List.of(new LightSurface(List.of(new Vertex(-.1,3.2,1.7),new Vertex(.1,3.2,1.7),new Vertex(.35,-.75,1.7),new Vertex(-.35,-.75,1.7)),0x50ffae84));
        if(a.contains("leap")||a.contains("uppercut"))return List.of(new LightSurface(List.of(new Vertex(0,-.8,1.3),new Vertex(.9,.8,1.3),new Vertex(.2,2.7,1.3),new Vertex(-.15,.9,1.3)),0x36ffb49c));
        return List.of(new LightSurface(List.of(new Vertex(0,.1,.6),new Vertex(-2.1,.35,1.6),new Vertex(0,.2,2.6),new Vertex(2.1,-.1,1.6)),0x28ff9a7c));
    }
    private static void curve(List<LightPath> out,int color,float width,DoubleFunction<Vertex> f){var points=new ArrayList<Vertex>();for(int i=0;i<=24;i++)points.add(f.apply(i/24d));out.add(new LightPath(points,color,width));}
}
