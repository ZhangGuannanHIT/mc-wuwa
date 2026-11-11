package cn.zgnhit.mcwuwa.client;

import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Observed mechanical dial, guarding sparks, shield-blade trails and downward impacts.
 * Decorative local coordinates only; retained server contacts own all damage. */
final class ZaniGuardBladeVfx {
    record Clock(int duration,int... hits){}
    private static final Map<String,Clock> CLOCKS=Map.ofEntries(
        Map.entry("zani/breakthrough",new Clock(24,6,8,10,12,14,16,18,20)),
        Map.entry("zani/heavy",new Clock(26,7,12,17,22)),Map.entry("zani/air",new Clock(24,6)),
        Map.entry("zani/dodge_counter",new Clock(20,4,10,16)),Map.entry("zani/skill",new Clock(26,6)),
        Map.entry("zani/precise_counter",new Clock(24,6,20)),Map.entry("zani/suppression",new Clock(24,6,13,20)),
        Map.entry("zani/breach_counter",new Clock(24,6,13,20)),Map.entry("zani/liberation",new Clock(48,12)),
        Map.entry("zani/finale",new Clock(24,6,20)),Map.entry("zani/intro",new Clock(24,5,8,11,14,17,20)),
        Map.entry("zani/heavy_dawn",new Clock(26,7)),Map.entry("zani/heavy_day",new Clock(26,7)),
        Map.entry("zani/heavy_night",new Clock(26,7,22)),Map.entry("zani/heavy_flash",new Clock(26,7)),
        Map.entry("zani/heavy_night_last",new Clock(26,7)));
    private static final int GOLD=0xffffd787,WHITE=0xfffff5d7,AMBER=0xffffa345;
    private ZaniGuardBladeVfx(){}
    static boolean supports(String action){return CLOCKS.containsKey(action);}
    static double delta(String action,double p){var c=CLOCKS.get(action);if(c==null)return Double.POSITIVE_INFINITY;double d=Double.POSITIVE_INFINITY;for(int h:c.hits())if(Math.abs(p*c.duration()-h)<Math.abs(d))d=p*c.duration()-h;return d;}
    static Sample sample(String a,double p,double u){var paths=ribbons(a,p);if(paths.isEmpty())return new Sample(0,0,0,WHITE,.2f,Kind.NONE);double n=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)n);var v=path.points().get(Math.min(path.points().size()-1,(int)((n-(int)n)*path.points().size())));return new Sample(v.side(),v.up(),v.forward(),path.color(),.28f,Kind.DUST);}
    static List<LightPath> ribbons(String a,double p){
        if(!supports(a)||p<=0||p>=1)return List.of();var out=new ArrayList<LightPath>();double d=delta(a,p);
        if(a.equals("zani/liberation")){
            // The film's clock-and-gears are a transient backdrop, not extra equipment.
            if(p<.12||p>.84)return List.of();
            double turn=p*Math.PI*.6;
            gear(out,0,.65,-.8,1.8,turn,24);
            gear(out,-1.8,.15,-1.1,.72,-turn*1.5,12);gear(out,1.75,1.25,-1.1,.8,-turn*1.3,12);
            curve(out,WHITE,.025f,t->{double q=t*Math.TAU;return new Vertex(Math.cos(q)*1.56,.65+Math.sin(q)*1.56,-.82);});
            for(int i=0;i<12;i++){double angle=i*Math.TAU/12;curve(out,WHITE,.018f,t->new Vertex(Math.cos(angle)*(1.4+t*.13),.65+Math.sin(angle)*(1.4+t*.13),-.84));}
            for(int i=0;i<2;i++){double angle=turn*(i==0?1:5),length=i==0?1.0:1.3;curve(out,WHITE,.04f,t->new Vertex(Math.sin(angle)*t*length,.65+Math.cos(angle)*t*length,-.88));}
            if(Math.abs(d)<2)curve(out,AMBER,.09f,t->new Vertex((t-.5)*4,-.3+(t-.5)*2,1.2));
            return List.copyOf(out);
        }
        boolean guard=a.equals("zani/skill")||a.contains("counter")||a.equals("zani/suppression");
        if(guard&&p<.28){
            // Broken metallic-looking edge glints frame the actual held guard.
            for(int side:new int[]{-1,1})curve(out,GOLD,.025f,t->new Vertex(side*(.4+t*.12),-.45+t*1.25,.85));
        }
        if(d<=-2||d>=3)return List.copyOf(out);
        var contact=new ArrayList<LightPath>();
        if(a.contains("night")||a.equals("zani/finale")||a.equals("zani/air")){
            for(int i=-1;i<=1;i++){double off=i*.09;curve(contact,i==0?WHITE:GOLD,.06f,t->new Vertex(off,3.2-t*4.0,1.1+Math.sin(t*Math.PI)*1.2));}
            if(d>=0)for(int i=0;i<8;i++){double angle=i*Math.TAU/8;curve(contact,i%2==0?WHITE:GOLD,.028f,t->new Vertex(Math.cos(angle)*t*2.5,-.7+Math.sin(t*Math.PI)*.12,1.6+Math.sin(angle)*t*1.5));}
        }else if(a.equals("zani/breakthrough")||a.equals("zani/heavy_flash")||guard){
            for(int i=-2;i<=2;i++){double off=i*.12;curve(contact,i==0?WHITE:GOLD,.04f,t->new Vertex(off*(1-t),.1+Math.sin(t*Math.PI)*.16,.5+t*3.5));}
            curve(contact,GOLD,.025f,t->{double q=t*Math.TAU;return new Vertex(Math.cos(q)*.7,.1+Math.sin(q)*.7,2.8);});
        }else{
            int sign=a.equals("zani/heavy_day")?-1:1;
            for(int layer=0;layer<3;layer++){double r=2.2+layer*.07;curve(contact,layer==1?WHITE:GOLD,.05f,t->{double q=-2.2+t*4.4+(p-.5)*1.2;return new Vertex(sign*Math.sin(q)*r,.15+Math.cos(q)*.24,1.2+Math.cos(q)*r*.75);});}
        }
        out.addAll(FiveNormalAttackVfx.contactTrails(contact,d));return List.copyOf(out);
    }
    static List<LightSurface> surfaces(String a,double p){
        if(!supports(a)||p<=0||p>=1)return List.of();
        double d=delta(a,p);
        if(a.equals("zani/liberation"))return Math.abs(d)>1.5?List.of():List.of(new LightSurface(List.of(new Vertex(-2,-1,1.2),new Vertex(2,1,1.2),new Vertex(2,.7,1.2),new Vertex(-2,-1.3,1.2)),0x48ffcf78));
        if(d<=-2||d>=3)return List.of();
        boolean guard=a.equals("zani/skill")||a.contains("counter")||a.equals("zani/suppression");
        // Early guard glints precede the contact paths; never fill between a
        // guard glint and a forward slash, which would create a stray sheet.
        int first=guard&&p<.28?2:0;
        int last=first+((a.equals("zani/breakthrough")||a.equals("zani/heavy_flash")||guard)?4:2);
        var paths=ribbons(a,p);
        if(paths.size()<=last)return List.of();
        int alpha=(int)Math.round(40*(1-Math.max(0,d)/3));
        return SlashSurfaceGeometry.between(paths.get(first),paths.get(last),(alpha<<24)|0xffe0a4);
    }
    private static void gear(List<LightPath> out,double x,double y,double z,double radius,double phase,int teeth){
        var rim=new ArrayList<Vertex>();
        // Four samples per tooth retain the notches; 24 samples alias a 24-tooth rim to a circle.
        for(int i=0;i<=teeth*4;i++){double q=i*Math.TAU/(teeth*4)+phase;double r=radius*(.94+.06*Math.cos((q-phase)*teeth));rim.add(new Vertex(x+Math.cos(q)*r,y+Math.sin(q)*r,z));}
        out.add(new LightPath(rim,GOLD,.04f));
        curve(out,AMBER,.025f,t->{double q=t*Math.TAU;return new Vertex(x+Math.cos(q)*radius*.68,y+Math.sin(q)*radius*.68,z);});
        for(int i=0;i<4;i++){double angle=phase+i*Math.PI/2;curve(out,GOLD,.05f,t->new Vertex(x+Math.cos(angle)*radius*t*.8,y+Math.sin(angle)*radius*t*.8,z));}
    }
    private static void curve(List<LightPath> out,int color,float width,DoubleFunction<Vertex> f){var points=new ArrayList<Vertex>();for(int i=0;i<=24;i++)points.add(f.apply(i/24d));out.add(new LightPath(points,color,width));}
}
