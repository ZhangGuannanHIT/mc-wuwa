package cn.zgnhit.mcwuwa.client;
import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;
/** Floating sword-zither and falling blades observed in the complete official demonstration.
 * Hit clock and dimensions are explicitly Minecraft presentation adaptation. */
final class QingxiaoZitherVfx {
 private static final int[] HITS={12,15,18,22,25,28,31,34,38,41,44};
 private QingxiaoZitherVfx(){}
 static boolean supports(String a){return a.equals("qingxiao/liberation");}
 static List<LightPath> ribbons(String a,double p){
  if(!supports(a)||p>=1)return List.of();double tick=Math.clamp(p,0,1)*48;var out=new ArrayList<LightPath>();
  // Fine luminous vibration stays on the real strings; do not replace the solid prop with poles.
  if(tick<12)for(int i=0;i<5;i++){double lane=(i-2)*.045;curve(out,0xffffe3aa,.013f,t->new Vertex(-.8+t*1.5,-.30+Math.sin(t*Math.PI*4+tick*.8)*.025,.95+lane));}
  for(int i=0;i<HITS.length;i++){
   double d=tick-HITS[i];if(d<=-2||d>=3)continue;var blade=new ArrayList<LightPath>();
   double x=((i*3)%7-3)*.38,z=1.4+(i%3)*.45,y=3.3-Math.clamp((d+2)/5,0,1)*4.4;
   curve(blade,0xffe6ffff,.045f,t->new Vertex(x,y+.9*t,z));
   for(int sign:new int[]{-1,1})curve(blade,0xff74e9f2,.022f,t->new Vertex(x+sign*Math.sin(t*Math.PI)*.1,y+t*.7,z));
   curve(blade,0xffffe5ac,.022f,t->new Vertex(x-.17+t*.34,y+.7,z));
   out.addAll(FiveNormalAttackVfx.contactTrails(blade,d));
  }
  return List.copyOf(out);
 }
 static Sample sample(String a,double p,double u){var paths=ribbons(a,p);if(paths.isEmpty())return new Sample(0,0,0,0xffffffff,.2f,Kind.NONE);double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);var v=path.points().get(Math.min(24,(int)((at-(int)at)*25)));return new Sample(v.side(),v.up(),v.forward(),path.color(),.25f,Kind.DUST);}
 private static void curve(List<LightPath> out,int color,float width,DoubleFunction<Vertex> f){var points=new ArrayList<Vertex>();for(int i=0;i<=24;i++)points.add(f.apply(i/24d));out.add(new LightPath(List.copyOf(points),color,width));}
}
