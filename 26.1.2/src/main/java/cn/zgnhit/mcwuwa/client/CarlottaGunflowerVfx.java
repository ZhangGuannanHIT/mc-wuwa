package cn.zgnhit.mcwuwa.client;
import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;
/** Official gunflower: one rifle report followed by autonomous crystal projectiles. */
final class CarlottaGunflowerVfx {
 private CarlottaGunflowerVfx(){}
 static boolean supports(String a){return a.startsWith("carlotta/death_knell_")||a.startsWith("carlotta/fatal_finale");}
 static int[] hits(String a){return a.contains("fatal_finale")?new int[]{6}:a.contains("c6")?new int[]{6,8,10,11,13,15,16,18,20}:new int[]{6,10,13,16,20};}
 static List<LightPath> ribbons(String a,double progress){
  if(!supports(a))return List.of();double tick=progress*24;var out=new ArrayList<LightPath>();int[] hits=hits(a);
  for(int i=0;i<hits.length;i++){
   double d=tick-hits[i];if(d<=-2||d>=3)continue;var shot=new ArrayList<LightPath>();
   double side=i==0?0:((i-1)%4-1.5)*.42,up=i==0?.15:.55+((i-1)%2)*.4;
   double z=1+Math.clamp((d+1)/3,0,1)*4.5;
   curve(shot,0xffffb9e0,.035f,t->new Vertex(side*(1-t),up*(1-t)+.15*t,.8+t*(z-.8)));
   for(int sign:new int[]{-1,1}){
    curve(shot,0xfff5fcff,.026f,t->new Vertex(sign*Math.sin(t*Math.PI)*.13,.15+(t-.5)*.48,z));
    curve(shot,0xffff69b5,.022f,t->new Vertex(sign*Math.sin(t*Math.PI)*.13,.15,z+(t-.5)*.48));
   }
   out.addAll(FiveNormalAttackVfx.contactTrails(shot,d));
  }return List.copyOf(out);
 }
 static Sample sample(String a,double p,double u){var paths=ribbons(a,p);if(paths.isEmpty())return new Sample(0,0,0,0xffffffff,.2f,Kind.NONE);double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);var v=path.points().get(Math.min(24,(int)((at-(int)at)*25)));return new Sample(v.side(),v.up(),v.forward(),path.color(),.25f,Kind.DUST);}
 private static void curve(List<LightPath> out,int color,float width,DoubleFunction<Vertex> f){var points=new ArrayList<Vertex>();for(int i=0;i<=24;i++)points.add(f.apply(i/24d));out.add(new LightPath(List.copyOf(points),color,width));}
}
