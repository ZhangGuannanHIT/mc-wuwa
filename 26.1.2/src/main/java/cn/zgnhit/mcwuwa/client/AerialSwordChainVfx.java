package cn.zgnhit.mcwuwa.client;
import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;
/** Per-contact airborne sword cuts; source-observed silhouettes on accepted MC clocks. */
final class AerialSwordChainVfx {
 record Clock(int duration,int[] hits){}
 private static final Map<String,Clock> CLOCKS=Map.ofEntries(
Map.entry("changli/air_basic_01",new Clock(12,new int[]{3})),
Map.entry("changli/air_basic_02",new Clock(16,new int[]{4,12})),
Map.entry("changli/air_basic_03",new Clock(20,new int[]{5,10,16})),
Map.entry("changli/air_basic_04",new Clock(24,new int[]{6,10,13,16,20})),
Map.entry("lupa/air_basic_01",new Clock(14,new int[]{3})),
Map.entry("lupa/air_basic_02",new Clock(18,new int[]{4,6,9,12,14})),
Map.entry("lupa/air_basic_03",new Clock(22,new int[]{5,18})),
Map.entry("qingxiao/air_basic_01",new Clock(14,new int[]{3,4,6,7,9,10})),
Map.entry("qingxiao/air_basic_02",new Clock(18,new int[]{4,9,14})),
Map.entry("qingxiao/air_basic_03",new Clock(22,new int[]{5,8,10,13,15,18})));
 private AerialSwordChainVfx(){}
 static boolean supports(String a){return CLOCKS.containsKey(a);}
 static List<LightPath> ribbons(String a,double p){
  var c=CLOCKS.get(a);if(c==null)return List.of();double d=Double.POSITIVE_INFINITY;for(int h:c.hits())if(Math.abs(p*c.duration()-h)<Math.abs(d))d=p*c.duration()-h;
  if(d<=-2||d>=3)return List.of();var out=new ArrayList<LightPath>();int stage=a.charAt(a.length()-1)-'0';boolean jade=a.startsWith("qingxiao/");
  for(int layer=0;layer<3;layer++){double off=layer*.06;curve(out,layer==1?0xffffe7b3:jade?0xff7ce8fa:0xffff4c54,.045f,t->{double angle=-2.4+t*4.8+(jade?(p-.5)*1.2:0);
   // Official normal-attack card, 10--11s: lift, then a vertical landing cut.
   // Keep the two authored contacts; do not repeat the lateral sweep at both.
   if(a.equals("lupa/air_basic_03"))return p<.5
    ?new Vertex(off-.06+Math.sin(t*Math.PI)*.45,-.65+t*2.45,.85+Math.sin(t*Math.PI)*1.15)
    :new Vertex(off-.06,1.8-t*2.6,1.6+Math.sin(t*Math.PI)*.15);
   // Lower official Qingxiao card: lateral cuts, rising second stage,
   // then a low whirl resolving into falling swords (not three equal arcs).
   if(jade){
    if(stage==2)return new Vertex(off-.06+Math.sin(t*Math.PI)*.55,-.65+t*2.45,1.15+Math.sin(t*Math.PI)*.6);
    if(stage==3&&p>=.65)return new Vertex(off-.06,1.9-t*2.7,1.6);
    return new Vertex(Math.sin(angle)*(1.6+off),-.2+Math.sin(angle)*.18+off,.9+Math.cos(angle)*(1.1+off));
   }
   if(a.equals("changli/air_basic_03"))return new Vertex(off-.06,-.7+t*2.5,.65+Math.sin(t*Math.PI)*(1.65+off));
   if(a.equals("changli/air_basic_02"))return new Vertex(Math.sin(angle)*(1.6+off),.15+off,.9+Math.cos(angle)*(1.1+off));
   return new Vertex(Math.sin(angle)*(1.6+off),stage%2==0?1.2-t*2.1:Math.cos(angle)*.8,.9+Math.cos(angle)*(1.1+off));});}
  if(a.equals("lupa/air_basic_03")){
   if(p>=.5)for(int rim=0;rim<2;rim++){
    double r=1.4+rim*.18;
    curve(out,rim==0?0xffff4c54:0xffffa76c,.025f,t->new Vertex(Math.cos(t*Math.TAU)*r,-.78,1.6+Math.sin(t*Math.TAU)*r));
   }
  }else if(jade&&stage==3){
   if(p>=.65){
    for(int side:new int[]{-1,1})for(int row=0;row<2;row++){
     double x=side*1.2,z=.6+row*1.9;
     curve(out,0xffc8ffff,.022f,t->new Vertex(x,1.7-t*2.5,z));
    }
    curve(out,0xff7ce8fa,.025f,t->new Vertex(Math.cos(t*Math.TAU)*1.6,-.78,1.6+Math.sin(t*Math.TAU)*1.6));
   }
  }else if(stage>=3)for(int sign:new int[]{-1,1})curve(out,jade?0xffc8ffff:0xffffa76c,.022f,t->new Vertex(sign*Math.sin(t*Math.PI)*.65,1.4-t*2.2,.8+t*1.5));
  return FiveNormalAttackVfx.contactTrails(out,d);
 }
 static List<LightSurface> surfaces(String a,double p){
  if(!a.startsWith("qingxiao/")||p<=0||p>=1)return List.of();
  var paths=ribbons(a,p);if(paths.size()<3)return List.of();
  return SlashSurfaceGeometry.between(paths.getFirst(),paths.get(2),0x247ce8fa);
 }
 static Sample sample(String a,double p,double u){var paths=ribbons(a,p);if(paths.isEmpty())return new Sample(0,0,0,0xffffffff,.2f,Kind.NONE);double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);var v=path.points().get(Math.min(24,(int)((at-(int)at)*25)));return new Sample(v.side(),v.up(),v.forward(),path.color(),.25f,Kind.DUST);}
 private static void curve(List<LightPath> out,int color,float width,DoubleFunction<Vertex> f){var points=new ArrayList<Vertex>();for(int i=0;i<=24;i++)points.add(f.apply(i/24d));out.add(new LightPath(List.copyOf(points),color,width));}
}
