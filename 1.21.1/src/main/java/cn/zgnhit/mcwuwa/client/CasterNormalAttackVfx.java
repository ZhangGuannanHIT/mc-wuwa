package cn.zgnhit.mcwuwa.client;
import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;
/** Source-observed golden facets, jellyfish water and paired fan cuts, per accepted hit. */
final class CasterNormalAttackVfx {
 record Clock(int duration,int[] hits){}
 private static final Map<String,Clock> CLOCKS=Map.ofEntries(
Map.entry("phoebe/basic_01",new Clock(12,new int[]{3})),
Map.entry("phoebe/basic_02",new Clock(16,new int[]{4,12})),
Map.entry("phoebe/basic_03",new Clock(24,new int[]{6,8,10,12,14,16,18,20})),
Map.entry("cantarella/basic_01",new Clock(14,new int[]{3})),
Map.entry("cantarella/basic_02",new Clock(18,new int[]{4,7,11,14})),
Map.entry("cantarella/basic_03",new Clock(24,new int[]{6,20})),
Map.entry("suisui/basic_01",new Clock(14,new int[]{3})),
Map.entry("suisui/basic_02",new Clock(18,new int[]{4,14})),
Map.entry("suisui/basic_03",new Clock(24,new int[]{6,13,20})),
Map.entry("suisui/basic_04",new Clock(28,new int[]{7,10,14,17,21,24})));
 private CasterNormalAttackVfx(){}
 static boolean supports(String a){return CLOCKS.containsKey(a);}
 static double delta(String a,double p){var c=CLOCKS.get(a);if(c==null)return Double.POSITIVE_INFINITY;double d=Double.POSITIVE_INFINITY;for(int h:c.hits())if(Math.abs(p*c.duration()-h)<Math.abs(d))d=p*c.duration()-h;return d;}
 static List<LightPath> ribbons(String a,double p){double d=delta(a,p);if(d<=-2||d>=3)return List.of();var out=new ArrayList<LightPath>();int stage=a.charAt(a.length()-1)-'0';double z=1+Math.clamp((d+1)/3,0,1)*2;
  if(a.startsWith("phoebe/")){
   for(int i=0;i<4;i++){int edge=i;curve(out,0xffffe7a2,.032f,t->goldenFacet(stage,edge,t,z));}
   for(int sign:new int[]{-1,1})curve(out,0xfffffff1,.035f,t->new Vertex(sign*(-.6+t*1.2),-.6+t*1.2,z));
   curve(out,0xffffe9aa,.026f,t->new Vertex(0,0,.6+t*(z-.6)));
  }else if(a.startsWith("cantarella/")){
   // The surface below uses the same moving water arc. Separate helix lines
   // previously crossed it instead of outlining the actual umbrella sweep.
   return FiveNormalAttackVfx.contactTrails(CantarellaTideVfx.ribbons(a,p),d);
  }else{
   // The accepted fan film and its luminous edges share the same sweep plane.
   return FiveNormalAttackVfx.contactTrails(FanInfernalVfx.ribbons(a,p),d);
  }
  return FiveNormalAttackVfx.contactTrails(out,d);
 }
 static List<LightSurface> surfaces(String a,double p){if(Math.abs(delta(a,p))>1.5)return List.of();var out=new ArrayList<LightSurface>();
  if(a.startsWith("cantarella/"))return CantarellaTideVfx.surfaces(a,p);
  if(a.startsWith("suisui/"))return FanInfernalVfx.surfaces(a,p);
  if(a.startsWith("phoebe/")){
   int stage=a.charAt(a.length()-1)-'0';double z=1+Math.clamp((delta(a,p)+1)/3,0,1)*2;
   // Shared vertices keep the luminous facet and its outline together as it moves.
   out.add(new LightSurface(List.of(goldenFacet(stage,0,0,z),goldenFacet(stage,1,0,z),goldenFacet(stage,2,0,z),goldenFacet(stage,3,0,z)),0x28fff3be));
  }
  return List.copyOf(out);
 }
 static Sample sample(String a,double p,double u){var paths=ribbons(a,p);if(paths.isEmpty())return new Sample(0,0,0,0xffffffff,.2f,Kind.NONE);double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);var v=path.points().get(Math.min(24,(int)((at-(int)at)*25)));return new Sample(v.side(),v.up(),v.forward(),path.color(),.25f,Kind.DUST);}
 private static Vertex goldenFacet(int stage,int edge,double t,double z){
  double angle=edge*Math.PI/2,next=angle+Math.PI/2,r=stage==3?.95:.75;
  return new Vertex((Math.cos(angle)*(1-t)+Math.cos(next)*t)*r,(Math.sin(angle)*(1-t)+Math.sin(next)*t)*r,z);
 }
 private static void curve(List<LightPath> out,int color,float width,DoubleFunction<Vertex> f){var points=new ArrayList<Vertex>();for(int i=0;i<=24;i++)points.add(f.apply(i/24d));out.add(new LightPath(List.copyOf(points),color,width));}
}
