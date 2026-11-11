package cn.zgnhit.mcwuwa.client;
import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Retained scissor cuts plus source-observed bamboo, recall and thrust branches. */
final class ScissorBambooVfx {
 private static final int RED=0xffff667c,MINT=0xff65f3df,WHITE=0xffeffff9;
 private ScissorBambooVfx(){}
 static boolean supports(String a){return ScissorBambooContactClocks.ALL.containsKey(a);}
 static double delta(String a,double p){var c=ScissorBambooContactClocks.ALL.get(a);double d=Double.POSITIVE_INFINITY;
  if(c!=null)for(int h:c.hits())if(Math.abs(p*c.duration()-h)<Math.abs(d))d=p*c.duration()-h;return d;}
 static Sample sample(String a,double p,double u){
  var paths=ribbons(a,p);if(paths.isEmpty())return new Sample(0,0,0,MINT,.2f,Kind.NONE);
  double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);
  var v=path.points().get(Math.min(24,(int)((at-(int)at)*25)));return new Sample(v.side(),v.up(),v.forward(),path.color(),.25f,Kind.DUST);
 }
 static List<LightPath> ribbons(String a,double p){
  if(!supports(a)||p<=0||p>=1)return List.of();
  if(a.contains("/liberation"))return NextFiveReleaseVfx.ribbons(a,p);
  double d=delta(a,p);if(d<=-2||d>=3)return List.of();
  if(ScissorInkMelodyVfx.supports(a)){
   var paths=new ArrayList<>(FiveNormalAttackVfx.contactTrails(ScissorInkMelodyVfx.ribbons(a,p),d));
   paths.addAll(ChisaFinaleFractureVfx.ribbons(a,p));return List.copyOf(paths);
  }
  var out=new ArrayList<LightPath>();boolean scissor=a.startsWith("chisa/"),fall=a.endsWith("/air")||a.endsWith("/falling_finale");
  int color=scissor?RED:MINT;
  if(a.endsWith("/skill_canopy")){
   // Broken segmented stalks and paired leaves occupy volume; no opaque screen plane.
   for(int i=0;i<5;i++){
    double x=(i-2)*.55,z=1.5+Math.abs(i-2)*.35;
    curve(out,MINT,.035f,t->new Vertex(x+Math.sin(t*Math.PI)*.12,-.8+t*3.1,z));
    for(int node=0;node<2;node++){
     double y=.2+node*.9;
     curve(out,WHITE,.018f,t->new Vertex(x-.12+t*.24,y,z));
     for(int side:new int[]{-1,1})curve(out,MINT,.022f,t->new Vertex(x+side*t*.45,y+Math.sin(t*Math.PI)*.2,z+t*.12));
    }
   }
  }else if(a.endsWith("/pierce")||a.endsWith("/skill_dash")||a.endsWith("/intro")){
   for(int i=0;i<3;i++){
    double x=(i-1)*.08;curve(out,i==1?WHITE:color,.03f,t->new Vertex(x,.1+Math.sin(t*Math.PI)*.1,.5+t*3.5));
   }
  }else if(a.endsWith("/retract")){
   for(int side:new int[]{-1,1})curve(out,RED,.03f,t->new Vertex(side*(2.3-t*1.8),.15,3-t*2));
  }else if(a.contains("skill_saw")){
   for(int side:new int[]{-1,1})curve(out,RED,.04f,t->new Vertex(side*.12+Math.sin(t*Math.PI*10)*.07,-.7+t*1.8,1.8));
   curve(out,WHITE,.025f,t->new Vertex(-1.4+t*2.8,.3+Math.sin(t*Math.PI)*.6,1.9));
  }else{
   for(int i=0;i<3;i++){
    double lane=(i-1)*.07;
    curve(out,i==1?WHITE:color,.035f,t->{double q=-2.2+t*4.4;
     return fall?new Vertex(lane,2.4-t*3.4,1.5+t*.45):new Vertex(Math.sin(q)*1.9,.1+lane+Math.cos(q)*.2,1.4+Math.cos(q));});
   }
  }
  return FiveNormalAttackVfx.contactTrails(out,d);
 }
 static List<LightSurface> surfaces(String a,double p){
  if(!supports(a)||p<=0||p>=1)return List.of();double d=delta(a,p);
  if(d<=-2||d>=3)return List.of();
  if(a.startsWith("chisa/")&&ScissorInkMelodyVfx.supports(a)){
   var paths=ribbons(a,p);if(paths.size()<3)return List.of();
   int color=((int)Math.round(40*Math.clamp(1-Math.max(0,d)/3,0,1))<<24)|0xff667c;
   if(a.contains("severing")||a.endsWith("saw_finale")){
    var out=new ArrayList<LightSurface>();
    out.addAll(SlashSurfaceGeometry.between(paths.get(0),paths.get(2),color));
    out.addAll(SlashSurfaceGeometry.between(paths.get(3),paths.get(5),color));
    out.addAll(ChisaFinaleFractureVfx.surfaces(a,p));
    return List.copyOf(out);
   }
   return SlashSurfaceGeometry.between(paths.get(0),paths.get(4),color);
  }
  return ScissorInkMelodyVfx.surfaces(a,p);
 }
 private static void curve(List<LightPath> out,int color,float width,DoubleFunction<Vertex> f){var pts=new ArrayList<Vertex>();for(int i=0;i<=24;i++)pts.add(f.apply(i/24d));out.add(new LightPath(pts,color,width));}
}
