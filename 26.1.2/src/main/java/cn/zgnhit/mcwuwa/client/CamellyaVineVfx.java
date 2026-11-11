package cn.zgnhit.mcwuwa.client;
import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;
/** Four complete official panels: alternating lashes, full vine spins and petal release. */
final class CamellyaVineVfx {
 static final Set<String> ACTIONS=Set.of("camellya/air","camellya/skill","camellya/bloom_basic_01","camellya/bloom_basic_02","camellya/bloom_basic_03","camellya/bloom_basic_04","camellya/bloom_spinning","camellya/bloom_dark_heart","camellya/bloom_twirl","camellya/ephemeral","camellya/intro");
 private static final int RED=0xfff43d68,CORE=0xffffd3da,DARK=0xffa42243;
 private CamellyaVineVfx(){}
 static boolean supports(String action){return ACTIONS.contains(action);}
 static List<LightPath> ribbons(String action,double p){
  if(!supports(action)||p<=0||p>=1)return List.of();var clock=RemainingCastContactClocks.ALL.get(action);double d=Double.POSITIVE_INFINITY;
  for(int hit:clock.hits())if(Math.abs(p*clock.duration()-hit)<Math.abs(d))d=p*clock.duration()-hit;
  if(d<=-2||d>=3)return List.of();var paths=new ArrayList<LightPath>();
  boolean spin=action.endsWith("spinning")||action.endsWith("04")||action.endsWith("twirl");
  boolean flower=action.equals("camellya/intro")||action.equals("camellya/ephemeral")||action.equals("camellya/bloom_dark_heart");
  if(flower){
   for(int i=0;i<6;i++){
    double a=i*Math.TAU/6;curve(paths,i%2==0?RED:CORE,.03f,t->{double r=Math.sin(t*Math.PI)*1.4;return new Vertex(Math.cos(a)*r,-.7+t*2.3,1.2+Math.sin(a)*r);});
    curve(paths,DARK,.025f,t->{double r=t*2.5;return new Vertex(Math.cos(a+.25*Math.sin(t*9))*r,-.85,1.2+Math.sin(a+.25*Math.sin(t*9))*r);});
   }
  }else if(action.equals("camellya/air")){
   for(int sign:new int[]{-1,1})curve(paths,sign==1?CORE:RED,.045f,t->new Vertex(sign*.12,2.5-t*3.3,1.3));
   ring(paths,RED,.03f,1.5,-.8,1.3);
  }else if(action.equals("camellya/skill")){
   for(int sign:new int[]{-1,1})curve(paths,sign==1?CORE:RED,.04f,t->{double a=-2.5+t*5;return new Vertex(Math.sin(a)*2.1,.2+sign*Math.cos(a)*.45,1.1+Math.cos(a)*1.3);});
  }else{
   int sign=action.endsWith("02")?-1:1;
   for(int layer=0;layer<3;layer++){
    double r=1.7+layer*.16;
    curve(paths,layer==1?CORE:RED,layer==1?.04f:.023f,t->{double a=sign*(-2.5+t*(spin?Math.TAU:5)+p*(spin?Math.TAU:1));return new Vertex(Math.sin(a)*r,.15+Math.sin(a*2)*(spin?.12:.5),1+Math.cos(a)*r*.7);});
   }
   if(action.endsWith("03")||spin)for(int i=0;i<4;i++){
    double a=i*Math.PI/2+p*Math.TAU;
    curve(paths,DARK,.018f,t->{double r=t*2.4;return new Vertex(Math.cos(a)*r,-.3+t*.9,1+Math.sin(a)*r);});
   }
  }
  return FiveNormalAttackVfx.contactTrails(paths,d);
 }
 static List<LightSurface> surfaces(String action,double p){
  if(action.equals("camellya/ephemeral"))return RemainingCastContactVfx.surfaces(action,p);
  var paths=ribbons(action,p);if(paths.isEmpty())return List.of();
  double d=RemainingCastContactVfx.delta(action,p);
  int alpha=(int)Math.round(36*Math.clamp(1-Math.max(0,d)/3,0,1));
  if(action.equals("camellya/air")||action.equals("camellya/skill"))
   return SlashSurfaceGeometry.between(paths.get(0),paths.get(1),alpha<<24|0xf43d68);
  if(action.equals("camellya/intro")||action.equals("camellya/bloom_dark_heart")){
   // Fill the flower's bright petals only; alternating dark root tendrils stay thin.
   var faces=new ArrayList<LightSurface>();
   for(int i=0;i<paths.size();i+=2)faces.addAll(SlashSurfaceGeometry.verticalWidth(paths.get(i),.045,alpha<<24|0xf43d68));
   return List.copyOf(faces);
  }
  // The outer and inner lashes already share the authored spin/alternating-sweep
  // phase. Bridge those edges instead of adding an unrelated solid red disc.
  return SlashSurfaceGeometry.between(paths.get(0),paths.get(2),alpha<<24|0xf43d68);
 }
 static Sample sample(String action,double p,double u){
  var paths=ribbons(action,p);if(paths.isEmpty())return new Sample(0,0,0,RED,.25f,Kind.NONE);
  double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);var v=path.points().get(Math.min(path.points().size()-1,(int)((at-(int)at)*path.points().size())));
  return new Sample(v.side(),v.up(),v.forward(),path.color(),.3f,Kind.DUST);
 }
 private static void ring(List<LightPath> out,int color,float width,double r,double y,double z){curve(out,color,width,t->{double a=t*Math.TAU;return new Vertex(Math.cos(a)*r,y,z+Math.sin(a)*r);});}
 private static void curve(List<LightPath> out,int color,float width,DoubleFunction<Vertex> fn){var points=new ArrayList<Vertex>();for(int i=0;i<=24;i++)points.add(fn.apply(i/24d));out.add(new LightPath(points,color,width));}
}
