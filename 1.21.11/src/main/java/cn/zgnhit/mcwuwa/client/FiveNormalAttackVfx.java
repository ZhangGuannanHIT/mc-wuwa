package cn.zgnhit.mcwuwa.client;

import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Normal panels: shield cuts, broad blade, furled canopy, photo frames and brush strokes.
 * Full panel timelines are archived under docs/reference/base-combat-motion/new-five.
 */
final class FiveNormalAttackVfx {
    private static final Map<String,Integer> STAGES=Map.of("zani",4,"augusta",4,"suoming",3,"lucilla",3,"zhezhi",3);
    // Frozen accepted hit clocks, checked against the production catalog in tests.
    // Each contact draws and sheds its own trail instead of leaving a static ring.
    record Clock(int duration,int[] hits){}
    private static final Map<String,Clock> CLOCKS=Map.ofEntries(
        Map.entry("zani/basic_01",new Clock(14,new int[]{3})),
        Map.entry("zani/basic_02",new Clock(18,new int[]{4})),
        Map.entry("zani/basic_03",new Clock(22,new int[]{5,12,18})),
        Map.entry("zani/basic_04",new Clock(26,new int[]{6,11,17,22})),
        Map.entry("augusta/basic_01",new Clock(14,new int[]{3})),
        Map.entry("augusta/basic_02",new Clock(18,new int[]{4,14})),
        Map.entry("augusta/basic_03",new Clock(22,new int[]{5,12,18})),
        Map.entry("augusta/basic_04",new Clock(26,new int[]{6,14,22})),
        Map.entry("suoming/basic_01",new Clock(14,new int[]{3})),
        Map.entry("suoming/basic_02",new Clock(20,new int[]{5,10,16})),
        Map.entry("suoming/basic_03",new Clock(24,new int[]{6,11,15,20})),
        Map.entry("lucilla/basic_01",new Clock(14,new int[]{3})),
        Map.entry("lucilla/basic_02",new Clock(18,new int[]{4,14})),
        Map.entry("lucilla/basic_03",new Clock(22,new int[]{5})),
        Map.entry("zhezhi/basic_01",new Clock(14,new int[]{3,10})),
        Map.entry("zhezhi/basic_02",new Clock(20,new int[]{5,8,10,13,16})),
        Map.entry("zhezhi/basic_03",new Clock(22,new int[]{5})));
    private static double contactDelta(String action,double progress){
        var clock=CLOCKS.get(action);if(clock==null)return Double.POSITIVE_INFINITY;
        double tick=progress*clock.duration(),closest=Double.POSITIVE_INFINITY;
        for(int hit:clock.hits())if(Math.abs(tick-hit)<Math.abs(closest))closest=tick-hit;
        return closest;
    }
    static List<LightPath> contactTrails(List<LightPath> paths,double delta){
        double head=Math.clamp((delta+2)/2,0,1),tail=Math.clamp(delta/3,0,1);
        if(head<=tail)return List.of();
        var trails=new ArrayList<LightPath>();
        float fade=(float)Math.clamp(1-Math.max(0,delta)/3,.05,1);
        for(var path:paths){
            var points=path.points();var segment=new ArrayList<Vertex>();
            for(int i=0;i<=24;i++){
                double at=(tail+(head-tail)*i/24d)*(points.size()-1);int a=Math.min(points.size()-2,(int)at);double f=at-a;
                var x=points.get(a);var y=points.get(a+1);
                segment.add(new Vertex(x.side()+(y.side()-x.side())*f,x.up()+(y.up()-x.up())*f,x.forward()+(y.forward()-x.forward())*f));
            }
            int alpha=Math.round(((path.color()>>>24)&255)*fade);
            trails.add(new LightPath(List.copyOf(segment),(path.color()&0xffffff)|(alpha<<24),path.width()*fade));
        }
        return List.copyOf(trails);
    }
    private FiveNormalAttackVfx(){}
    static boolean supports(String action){
        int slash=action.indexOf('/');if(slash<0)return false;
        Integer stages=STAGES.get(action.substring(0,slash));String move=action.substring(slash+1);
        return stages!=null&&move.length()==8&&move.startsWith("basic_0")&&move.charAt(7)>='1'&&move.charAt(7)<='0'+stages;
    }
    static Sample sample(String action,double p,double u){
        var paths=ribbons(action,p);if(paths.isEmpty())return new Sample(0,0,0,0xffffffff,.2f,Kind.NONE);
        double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);
        int index=Math.min(path.points().size()-1,(int)((at-(int)at)*path.points().size()));var v=path.points().get(index);
        return new Sample(v.side(),v.up(),v.forward(),path.color(),.24f,Kind.DUST);
    }
    static List<LightPath> ribbons(String action,double p){
        if(!supports(action)||p<.06||p>.96)return List.of();double delta=contactDelta(action,p);if(delta<=-2||delta>=3)return List.of();int slash=action.indexOf('/');String role=action.substring(0,slash);int stage=action.charAt(action.length()-1)-'0';
        var out=new ArrayList<LightPath>();
        if(role.equals("lucilla")){
            int blue=0xffaabfff,white=0xfff3edff;double r=.38+stage*.10,z=1.7+Math.min(1,p*2.4);
            double[][] corners={{-r,-r},{r,-r},{r,r},{-r,r}};
            for(int i=0;i<4;i++){var a=corners[i];var b=corners[(i+1)%4];curve(out,i%2==0?white:blue,.023f,t->new Vertex(a[0]+(b[0]-a[0])*t,.15+a[1]+(b[1]-a[1])*t,z));}
            for(int sign:new int[]{-1,1})curve(out,white,.018f,t->new Vertex(sign*(-.8+t*1.6),.15+sign*(-.7+t*1.4),z+.03));
        }else if(role.equals("zhezhi")){
            for(int sign:new int[]{-1,1})curve(out,sign>0?0xffffb8d9:0xff6de8ff,.04f,t->new Vertex(sign*Math.sin(t*Math.PI)*1.3,(stage==3?-.5+t*2.2:.2+Math.sin(t*Math.PI)*.5),.35+t*3.4));
            for(int i=0;i<3;i++){double x=(i-1)*.55,z=1.2+i*.6;curve(out,i==1?0xffffd5e9:0xffbafff1,.02f,t->{double angle=t*Math.TAU;return new Vertex(x+Math.cos(angle)*.16,.25+Math.sin(angle)*.25,z);});}
        }else{
            int color=role.equals("suoming")?0xffff3d5b:role.equals("augusta")?0xffffb856:0xffffedc4;
            boolean vertical=role.equals("augusta")&&stage==4;boolean spin=role.equals("suoming")&&stage==3;
            for(int layer=0;layer<3;layer++){
                double r=1.65+layer*.08,sign=stage%2==0?-1:1,offset=layer-1;
                curve(out,layer==1?0xfffff5e8:color,layer==1?.055f:.028f,t->{double angle=-2+t*4;
                    if(vertical)return new Vertex(offset*.07,2.4-t*3.4,.8+t*1.5);
                    return new Vertex(Math.sin(angle)*r,(spin?-.65:sign*Math.cos(angle)*.65)+offset*.04,1.1+Math.cos(angle)*(spin?1.4:.9));});
            }
            if(stage==STAGES.get(role))for(int axis=0;axis<2;axis++){
                final int a=axis;curve(out,0xfffff6ed,.025f,t->new Vertex(a==0?-.7+t*1.4:0,a==1?-.55+t*1.4:.15,2.2));
            }
        }
        return contactTrails(out,delta);
    }
    static List<LightSurface> surfaces(String action,double p){
        if(!supports(action)||Math.abs(contactDelta(action,p))>1.5)return List.of();
        if(action.startsWith("lucilla/"))return List.of(new LightSurface(List.of(new Vertex(-.55,-.4,2.7),new Vertex(.55,-.4,2.7),new Vertex(.55,.7,2.7),new Vertex(-.55,.7,2.7)),0x18d9e5ff));
        if(action.equals("augusta/basic_04"))return List.of(new LightSurface(List.of(new Vertex(-.08,2.3,.8),new Vertex(.08,2.3,.8),new Vertex(.12,-.9,2.3),new Vertex(-.12,-.9,2.3)),0x38ffbc62));
        return List.of();
    }
    private static void curve(List<LightPath> out,int color,float width,DoubleFunction<Vertex> fn){var points=new ArrayList<Vertex>();for(int i=0;i<=24;i++)points.add(fn.apply(i/24d));out.add(new LightPath(List.copyOf(points),color,width));}
}
