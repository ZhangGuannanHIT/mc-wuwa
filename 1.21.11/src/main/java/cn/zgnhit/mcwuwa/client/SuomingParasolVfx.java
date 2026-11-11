package cn.zgnhit.mcwuwa.client;
import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;
/** Source-observed red parasol rim, carved heart channel and gold/red cinematic frame. */
final class SuomingParasolVfx {
    record Clock(int duration,int... hits){}
    private static final Map<String,Clock> CLOCKS=Map.ofEntries(
        Map.entry("suoming/deep_basic_01",new Clock(14,3,6,10)),
        Map.entry("suoming/deep_basic_02",new Clock(20,5,9,12,16)),
        Map.entry("suoming/deep_basic_03",new Clock(24,6,11,15,20)),
        Map.entry("suoming/deep_basic_04",new Clock(28,7,11,16,20,24)),
        Map.entry("suoming/dodge_counter",new Clock(20,4,10,16)),
        Map.entry("suoming/deep_counter",new Clock(24,6,11,15,20)),
        Map.entry("suoming/air",new Clock(24,6)),
        Map.entry("suoming/thunder_spin_01",new Clock(28,5,10,14,19,24)),
        Map.entry("suoming/thunder_spin_02",new Clock(28,5,10,14,19,24)),
        Map.entry("suoming/skill",new Clock(26,6)),
        Map.entry("suoming/skill_counter",new Clock(26,6,11,17,22)),
        Map.entry("suoming/liberation",new Clock(48,12,16,20,24,28,32,36,40,44)),
        Map.entry("suoming/rain_thunder",new Clock(12,3)),
        Map.entry("suoming/intro",new Clock(24,5,8,11,14,17,20)),
        Map.entry("suoming/intro_deep",new Clock(24,5,9,12,16,20)),
        Map.entry("suoming/intro_unison",new Clock(24,5,8,11,14,17,20)),
        Map.entry("suoming/intro_deep_unison",new Clock(24,5,9,12,16,20)),
        Map.entry("suoming/skill_lock",new Clock(26,6,9,12,16,19,22)),
        Map.entry("suoming/skill_release",new Clock(24,6)),
        Map.entry("suoming/heart_hold",new Clock(40,3,5,7,9,11,13,15,17,19,20,22,24,26,28,30,32,34,36)),
        Map.entry("suoming/heart",new Clock(60,6,10,14,18,21,25,29,33,37,41,44,48,52,56)));
    private static final int RED=0xffff5570,WHITE=0xffffd8d6,GOLD=0xffd9b07f;
    private SuomingParasolVfx(){}
    static boolean supports(String a){return CLOCKS.containsKey(a);}
    static double delta(String a,double p){var c=CLOCKS.get(a);if(c==null)return Double.POSITIVE_INFINITY;double d=Double.POSITIVE_INFINITY;for(int h:c.hits())if(Math.abs(p*c.duration()-h)<Math.abs(d))d=p*c.duration()-h;return d;}
    static Sample sample(String a,double p,double u){var paths=ribbons(a,p);if(paths.isEmpty())return new Sample(0,0,0,WHITE,.2f,Kind.NONE);double n=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)n);var v=path.points().get(Math.min(24,(int)((n-(int)n)*25)));return new Sample(v.side(),v.up(),v.forward(),path.color(),.3f,Kind.DUST);}
    static List<LightPath> ribbons(String a,double p){
        if(!supports(a)||p<=0||p>=1)return List.of();double d=delta(a,p);var out=new ArrayList<LightPath>();
        if(a.equals("suoming/liberation")&&p>.12&&p<.78){
            for(int side:new int[]{-1,1}){
                curve(out,GOLD,.03f,t->new Vertex(side*2,-.7+t*3.4,-.8));
                curve(out,RED,.035f,t->new Vertex(side*(1.8-Math.sin(t*Math.PI)*.35),-.5+t*3,-.82));
            }
            for(int petal=0;petal<6;petal++){double angle=petal*Math.TAU/6;curve(out,RED,.03f,t->{double r=Math.sin(t*Math.PI)*1.3;return new Vertex(Math.cos(angle+t*.3)*r,.8+Math.sin(angle+t*.3)*r,-.8);});}
        }
        if(d<=-2||d>=3)return List.copyOf(out);var contact=new ArrayList<LightPath>();
        boolean channel=a.contains("heart"),spin=a.contains("spin");
        if(a.equals("suoming/rain_thunder")||a.equals("suoming/air")){
            for(int i=-1;i<=1;i++){double x=i*.06;curve(contact,i==0?WHITE:RED,.04f,t->new Vertex(x+Math.sin(t*25)*.08,3-t*3.7,1.4));}
        }else if(channel){
            // Published engraved-heart panel4.5--5.5s: a red toothed rim
            // stands in front of the crouched actor, rather than lying on the ground.
            for(int layer=0;layer<2;layer++){double r=1.7+layer*.2;curve(contact,layer==0?RED:WHITE,.04f,t->heartRim(t*Math.TAU+p*4,r));}
            curve(contact,RED,.03f,t->heartRim(t*Math.TAU+p*4,1.9+(1-Math.cos(t*Math.TAU*12))*.16));
            for(int i=0;i<6;i++){double q=i*Math.TAU/6;curve(contact,RED,.026f,t->new Vertex(Math.cos(q)*t*2.5,-.6+Math.sin(t*Math.PI)*.1,1.5+Math.sin(q)*t*2.5));}
        }else if(spin||a.startsWith("suoming/deep_")){
            for(int layer=0;layer<3;layer++){double r=1.6+layer*.07;curve(contact,layer==1?WHITE:RED,.045f,t->{double q=-2.5+t*5+p*3;return new Vertex(Math.cos(q)*r,.2+Math.sin(q)*r*.65,1.5+Math.sin(q)*.25);});}
        }else{
            for(int layer=0;layer<3;layer++){double r=2+layer*.06;curve(contact,layer==1?WHITE:RED,.04f,t->{double q=-2+t*4;return new Vertex(Math.sin(q)*r,.05+Math.cos(q)*.3,.8+Math.cos(q)*r*.65);});}
        }
        for(int sign:new int[]{-1,1})curve(contact,RED,.018f,t->new Vertex(sign*t*1.8+Math.sin(t*28)*.1,.15+Math.sin(t*17)*.12,1.8+t*.7));
        out.addAll(FiveNormalAttackVfx.contactTrails(contact,d));
        // Rain is spawned on its victim, unlike the caster-relative plunge.
        // Keep the authored strokes but remove their forward attack offset.
        if(a.equals("suoming/rain_thunder"))return out.stream().map(path->new LightPath(
            path.points().stream().map(v->new Vertex(v.side(),v.up(),v.forward()-1.4)).toList(),path.color(),path.width())).toList();
        return List.copyOf(out);
    }
    static List<LightSurface> surfaces(String a,double p){
        if(!supports(a)||p<=0||p>=1)return List.of();
        double d=delta(a,p);
        if(a.equals("suoming/liberation"))return Math.abs(d)>1.4?List.of():List.of(new LightSurface(List.of(new Vertex(0,.15,.7),new Vertex(-1.8,.6,1.5),new Vertex(0,.1,2.6),new Vertex(1.8,-.3,1.5)),0x28ff6681));
        if(d<=-2||d>=3)return List.of();
        var paths=ribbons(a,p);int last=a.contains("heart")?1:2;
        if(paths.size()<=last)return List.of();
        int alpha=(int)Math.round(40*(1-Math.max(0,d)/3));
        return SlashSurfaceGeometry.between(paths.getFirst(),paths.get(last),(alpha<<24)|0xff6681);
    }
    private static Vertex heartRim(double angle,double radius){return new Vertex(Math.cos(angle)*radius,.25+Math.sin(angle)*radius*.85,1.5+Math.sin(angle)*radius*.4);}
    private static void curve(List<LightPath> out,int color,float width,DoubleFunction<Vertex> f){var points=new ArrayList<Vertex>();for(int i=0;i<=24;i++)points.add(f.apply(i/24d));out.add(new LightPath(points,color,width));}
}
