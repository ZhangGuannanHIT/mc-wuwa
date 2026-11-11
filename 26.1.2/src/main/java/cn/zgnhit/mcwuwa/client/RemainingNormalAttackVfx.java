package cn.zgnhit.mcwuwa.client;
import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;
/** Complete normal panels inspected; per-hit MC trails, independent from damage. */
final class RemainingNormalAttackVfx {
 record Clock(int duration,int[] hits){}
 private static final Map<String,Clock> CLOCKS=Map.ofEntries(
Map.entry("camellya/basic_01",new Clock(12,new int[]{3})),
Map.entry("camellya/basic_02",new Clock(16,new int[]{4,12})),
Map.entry("camellya/basic_03",new Clock(20,new int[]{5,10,16})),
Map.entry("camellya/dodge_counter",new Clock(20,new int[]{4,10,16})),
Map.entry("camellya/basic_04",new Clock(44,new int[]{11,13,14,16,17,19,20,22,23,25,26,28,29,31,32,34,35,37,38,40})),
Map.entry("camellya/basic_05",new Clock(22,new int[]{5,9,14,18})),
Map.entry("chisa/basic_01",new Clock(14,new int[]{3,10})),
Map.entry("chisa/basic_02",new Clock(18,new int[]{4,9,14})),
Map.entry("hiyuki/basic_01",new Clock(12,new int[]{3,8})),
Map.entry("hiyuki/basic_02",new Clock(14,new int[]{3})),
Map.entry("hiyuki/basic_03",new Clock(24,new int[]{6,9,12,14,17,20})),
Map.entry("yangyang_xuanling/basic_01",new Clock(12,new int[]{3})),
Map.entry("yangyang_xuanling/basic_02",new Clock(16,new int[]{4,8,12})),
Map.entry("yangyang_xuanling/basic_03",new Clock(18,new int[]{4,14})),
Map.entry("yangyang_xuanling/basic_04",new Clock(24,new int[]{6,13,20})),
Map.entry("aero_rover/basic_01",new Clock(12,new int[]{3})),
Map.entry("aero_rover/basic_02",new Clock(16,new int[]{4,12})),
Map.entry("aero_rover/basic_03",new Clock(37,new int[]{8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33})),
Map.entry("aero_rover/basic_04",new Clock(20,new int[]{5})));
 private RemainingNormalAttackVfx(){}
 static boolean supports(String a){return CLOCKS.containsKey(a);}
 static double delta(String a,double p){var c=CLOCKS.get(a);if(c==null)return Double.POSITIVE_INFINITY;double d=Double.POSITIVE_INFINITY;for(int h:c.hits())if(Math.abs(p*c.duration()-h)<Math.abs(d))d=p*c.duration()-h;return d;}
 static List<LightPath> ribbons(String a,double p){double d=delta(a,p);if(d<=-2||d>=3)return List.of();var out=new ArrayList<LightPath>();int hitIndex=contact(a,p);int stage=a.equals("camellya/dodge_counter")?3:a.charAt(a.length()-1)-'0';String role=a.substring(0,a.indexOf('/'));
  int color=switch(role){case "camellya","chisa"->0xffff577b;case "hiyuki"->0xffffd5ee;case "yangyang_xuanling"->0xff8ccfff;default->0xffb1f7f1;};
  if(role.equals("chisa")){
   for(int sign:new int[]{-1,1})for(int layer=0;layer<2;layer++){double off=layer*.05;curve(out,layer==0?color:0xffffd4da,.045f,t->new Vertex(sign*(-1.4+t*2.8),-.7+t*1.5+off,.9+t*.9));}
  }else{
   for(int layer=0;layer<3;layer++){double off=layer*.06;curve(out,layer==1?0xfffff1cd:color,.045f,t->slash(role,stage,t,off,hitIndex));}
   if(role.equals("hiyuki"))for(int i=0;i<4;i++){double x=(i-1.5)*.55,z=1.2+i*.25;curve(out,color,.022f,t->new Vertex(x+Math.sin(t*Math.TAU)*.1,.5+Math.cos(t*Math.TAU)*.2,z));}
   if(role.equals("camellya")&&stage>=4)for(int sign:new int[]{-1,1})curve(out,color,.025f,t->new Vertex(sign*Math.sin(t*Math.PI)*1.8,.4+Math.sin(t*Math.TAU)*.6,.7+t*1.5));
  }return FiveNormalAttackVfx.contactTrails(out,d);
 }
 static List<LightSurface> surfaces(String a,double p){if(Math.abs(delta(a,p))>1.5)return List.of();
  if(a.startsWith("chisa/")){
   var cuts=new ArrayList<LightSurface>();
   for(int sign:new int[]{-1,1})cuts.add(new LightSurface(List.of(new Vertex(sign*-1.4,-.7,.9),new Vertex(sign*1.4,.8,1.8),new Vertex(sign*1.4,.85,1.8),new Vertex(sign*-1.4,-.65,.9)),0x42ff698a));
   return List.copyOf(cuts);
  }
  if(a.startsWith("camellya/")){var out=new ArrayList<LightSurface>();for(int sign:new int[]{-1,1})out.add(new LightSurface(List.of(new Vertex(0,.1,1.5),new Vertex(sign*.45,.6,1.7),new Vertex(sign*.7,.15,1.8),new Vertex(sign*.2,-.1,1.6)),0x3aff7291));return List.copyOf(out);}
  if(!supports(a))return List.of();
  String role=a.substring(0,a.indexOf('/'));int stage=a.charAt(a.length()-1)-'0';
  int tint=role.equals("hiyuki")?0x28ffd5ee:role.equals("yangyang_xuanling")?0x288ccfff:0x28b1f7f1;
  var bands=new ArrayList<LightSurface>();int hitIndex=contact(a,p);
  for(int i=0;i<24;i++){double from=i/24d,to=(i+1)/24d;
   bands.add(new LightSurface(List.of(slash(role,stage,from,0,hitIndex),slash(role,stage,from,.12,hitIndex),slash(role,stage,to,.12,hitIndex),slash(role,stage,to,0,hitIndex)),tint));
  }
  return List.copyOf(bands);
 }
 private static int contact(String action,double progress){
  var clock=CLOCKS.get(action);int closest=0;
  for(int i=1;i<clock.hits().length;i++)if(Math.abs(progress*clock.duration()-clock.hits()[i])<Math.abs(progress*clock.duration()-clock.hits()[closest]))closest=i;
  return closest;
 }
 private static Vertex slash(String role,int stage,double t,double offset,int contact){
  // The normal third-stage sweep stays near the ground; it is not a rising wind column.
  if(role.equals("aero_rover")&&stage==3){
   double angle=t*Math.TAU;
   return new Vertex(Math.sin(angle)*(1.7+offset),-.55+Math.sin(angle)*.06,1+Math.cos(angle)*(1.1+offset));
  }
  // Frozen present-self panel: crossing cuts precede the final downstroke.
  // Keep the authored contact clock; these are MC trail directions, not frame measurements.
  if(role.equals("hiyuki")&&stage==3&&contact<5){
   double angle=-2.3+t*4.6,side=Math.sin(angle)*(1.7+offset);
   return new Vertex(side,.2+side*(contact%2==0?.65:-.65),1+Math.cos(angle)*(1.1+offset));
  }
  boolean vertical=role.equals("yangyang_xuanling")&&stage==4||role.equals("hiyuki")&&stage==3||role.equals("camellya")&&stage==5;
  double angle=-2.3+t*4.6;
  return new Vertex(Math.sin(angle)*(1.7+offset),vertical?1.7-t*2.6:.15+Math.cos(angle)*.65,1+Math.cos(angle)*(1.1+offset));
 }
 static Sample sample(String a,double p,double u){var paths=ribbons(a,p);if(paths.isEmpty())return new Sample(0,0,0,0xffffffff,.2f,Kind.NONE);double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);var v=path.points().get(Math.min(24,(int)((at-(int)at)*25)));return new Sample(v.side(),v.up(),v.forward(),path.color(),.25f,Kind.DUST);}
 private static void curve(List<LightPath> out,int color,float width,DoubleFunction<Vertex> f){var points=new ArrayList<Vertex>();for(int i=0;i<=24;i++)points.add(f.apply(i/24d));out.add(new LightPath(List.copyOf(points),color,width));}
}
