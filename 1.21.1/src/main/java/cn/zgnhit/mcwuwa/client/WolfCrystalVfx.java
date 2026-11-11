package cn.zgnhit.mcwuwa.client;
import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Forward flame cleaves and faceted gun flashes from reviewed official combat. */
final class WolfCrystalVfx {
    private static final int RED=0xffff546c,GOLD=0xffffbe7d,WHITE=0xfffff2ef,
        BLUE=0xff91dfff,PINK=0xffff9bd8;
    private WolfCrystalVfx(){}
    static boolean supports(String a){return WolfCrystalContactClocks.ALL.containsKey(a);}
    static double delta(String a,double p){var c=WolfCrystalContactClocks.ALL.get(a);double d=Double.POSITIVE_INFINITY;
        if(c!=null)for(int h:c.hits())if(Math.abs(p*c.duration()-h)<Math.abs(d))d=p*c.duration()-h;return d;}
    static Sample sample(String a,double p,double u){
        var paths=ribbons(a,p);if(paths.isEmpty())return new Sample(0,0,0,WHITE,.2f,Kind.NONE);
        double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);
        var v=path.points().get(Math.min(24,(int)((at-(int)at)*25)));return new Sample(v.side(),v.up(),v.forward(),path.color(),.26f,Kind.DUST);
    }
    static List<LightPath> ribbons(String a,double p){
        if(!supports(a)||p<=0||p>=1)return List.of();var out=new ArrayList<LightPath>();double d=delta(a,p);
        if(a.equals("lupa/skill")){
            if(p<.08||p>.6)return List.of();
            double z=flagForward(p),y=.3;
            curve(out,GOLD,.035f,t->new Vertex(0,y-.65+t*1.3,z));
            curve(out,RED,.045f,t->new Vertex(t*.85,y+.55-t*.23,z+Math.sin(t*Math.PI)*.08));
            curve(out,RED,.035f,t->new Vertex(t*.85,y+.05+t*.27,z+Math.sin(t*Math.PI)*.08));
            curve(out,RED,.022f,t->new Vertex(0,y,.5+t*(z-.5)));
            return List.copyOf(out);
        }
        if(a.equals("carlotta/liberation")&&p>.1&&p<.85){
            // Crystal perimeter closes in front of the caster, leaving sightline open.
            for(int side:new int[]{-1,1})curve(out,side<0?PINK:BLUE,.027f,t->new Vertex(side*(1.1+Math.sin(t*Math.PI)*1.1),-.6+t*2.5,1.1));
        }
        if(d<=-2||d>=3)return List.copyOf(out);var contact=new ArrayList<LightPath>();
        if(a.startsWith("lupa/")){
            boolean down=a.endsWith("air")||a.endsWith("starfall"),spin=a.contains("wolf_dance");
            for(int layer=0;layer<3;layer++){
                double r=1.9+layer*.12,offset=.05+layer*.06;
                curve(contact,layer==1?WHITE:RED,.045f,t->{double angle=-2.3+t*4.6+(spin?(p-.5)*Math.PI:0);
                    return down?new Vertex(offset,2.6-t*3.4,.7+t*2.2)
                        :new Vertex(Math.sin(angle)*r,.1+Math.cos(angle)*(spin?.25:.8),1.2+Math.cos(angle)*r*.55);});
            }
            // Ground teeth are short flame shards, never a second collision weapon.
            for(int i=0;i<4;i++){
                double x=(i-1.5)*.65;
                curve(contact,GOLD,.028f,t->new Vertex(x,-.9+Math.sin(t*Math.PI)*.65,2.5+(t-.5)*.4));
            }
        }else{
            boolean down=a.endsWith("air");
            curve(contact,WHITE,.045f,t->new Vertex(0,down?.2-t*3.2:.1,down?.5:.7+t*4.4));
            // A faceted six-point pink/cyan crystal impact, with narrow clear faces.
            for(int i=0;i<6;i++){
                double angle=i*Math.TAU/6;
                curve(contact,i%2==0?PINK:BLUE,.03f,t->new Vertex(Math.cos(angle)*t*.8,.15+Math.sin(angle)*t*.8,down?.5:3.5));
            }
        }
        out.addAll(FiveNormalAttackVfx.contactTrails(contact,d));return List.copyOf(out);
    }
    static List<LightSurface> surfaces(String a,double p){
        if(a.equals("lupa/skill")){
            if(p<.08||p>.6)return List.of();double z=flagForward(p);
            return List.of(new LightSurface(List.of(new Vertex(0,.85,z),new Vertex(.85,.62,z),new Vertex(.5,.48,z+.08),new Vertex(0,.35,z)),0x68ff546c));
        }
        if(!supports(a)||p<=0||p>=1||Math.abs(delta(a,p))>1.25||a.startsWith("lupa/"))return List.of();
        var out=new ArrayList<LightSurface>();
        double z=a.endsWith("air")?.5:3.5;
        for(int i=0;i<6;i++){
            double angle=i*Math.TAU/6,x=Math.cos(angle)*.6,y=.15+Math.sin(angle)*.6;
            out.add(new LightSurface(List.of(new Vertex(x,y,z),new Vertex(x+.12,y+.3,z),new Vertex(x,y+.55,z),new Vertex(x-.12,y+.3,z)),i%2==0?0x28ff9bd8:0x2891dfff));
        }
        return List.copyOf(out);
    }
    private static double flagForward(double p){return .7+2.8*Math.clamp((p-.08)/.15,0,1);}
    private static void curve(List<LightPath> out,int color,float width,DoubleFunction<Vertex> f){var v=new ArrayList<Vertex>();for(int i=0;i<=24;i++)v.add(f.apply(i/24d));out.add(new LightPath(v,color,width));}
}
