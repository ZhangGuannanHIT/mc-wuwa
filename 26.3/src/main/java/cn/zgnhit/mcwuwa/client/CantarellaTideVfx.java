package cn.zgnhit.mcwuwa.client;
import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;
/** Source-observed umbrella sweeps, water ribbons, domed jellyfish and dream bubbles. */
final class CantarellaTideVfx {
 private static final int BLUE=0xff91eaff,PURPLE=0xffd8a5ff,WHITE=0xffeff7ff;
 private CantarellaTideVfx(){}
 static boolean supports(String a){return a.startsWith("cantarella/")&&Set.of("basic_01","basic_02","basic_03","heavy","heavy_mirage","air","dodge_counter","skill","skill_dream","awaken","intro","mirage_intro","mirage_basic_01","mirage_basic_02","mirage_basic_03","mirage_air","skill_drain","mirage_counter").contains(a.substring(11));}
 static Sample sample(String a,double p,double u){
  var paths=ribbons(a,p);if(paths.isEmpty())return new Sample(0,0,0,WHITE,.2f,Kind.NONE);
  double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);var v=path.points().get(Math.min(24,(int)((at-(int)at)*25)));
  return new Sample(v.side(),v.up(),v.forward(),path.color(),.25f,Kind.DUST);
 }
 static List<LightPath> ribbons(String a,double progress){
  if(!supports(a))return List.of();double p=Math.clamp(progress,0,1);if(p<.1||p>.96)return List.of();var out=new ArrayList<LightPath>();
  boolean dome=dome(a),air=a.endsWith("/air")||a.endsWith("mirage_air"),mirage=a.contains("mirage")||a.endsWith("skill_dream");
  if(dome){
   double r=a.endsWith("skill_drain")?1.6:1.0;
   for(int i=0;i<4;i++){
    double angle=i*Math.PI/4;curve(out,i%2==0?BLUE:PURPLE,.035f,t->{double u=t*Math.PI;return new Vertex(Math.cos(angle)*Math.cos(u)*r,.5+Math.sin(u)*r*.7,2+Math.sin(angle)*Math.cos(u)*r);});
    double x=Math.cos(i*Math.TAU/4)*r*.5,z=2+Math.sin(i*Math.TAU/4)*r*.5;
    curve(out,PURPLE,.026f,t->new Vertex(x+Math.sin(t*5+p*4)*.12,.5-t*1.4,z+Math.sin(t*4)*.12));
   }
   curve(out,BLUE,.03f,t->new Vertex(Math.cos(t*Math.TAU)*r,.5,2+Math.sin(t*Math.TAU)*r));
  }else if(air){
   for(int i=0;i<5;i++){double angle=i*Math.TAU/5;curve(out,i%2==0?BLUE:PURPLE,.035f,t->new Vertex(Math.cos(angle)*t*1.7,2.5-t*3.2,1.6+Math.sin(angle)*t*1.7));}
  }else{
   int stage=a.endsWith("03")?3:a.endsWith("02")?2:1;
   for(int i=0;i<5;i++){
    double r=1.6+stage*.2-i*.055;curve(out,i%2==0?BLUE:PURPLE,.035f,t->{double angle=-2.3+t*4.6+p*1.5;return new Vertex(Math.sin(angle)*r,.2+Math.cos(angle)*(mirage?.65:.25),1.2+Math.cos(angle)*r*.7);});
   }
   for(int i=0;i<(mirage?3:1);i++){
    double x=(i-1)*.6,z=1.9+i*.3;curve(out,WHITE,.02f,t->new Vertex(x+Math.cos(t*Math.TAU)*.22,.5+Math.sin(t*Math.TAU)*.22,z));
   }
  }
  return List.copyOf(out);
 }
 static List<LightSurface> surfaces(String a,double p){
  if(!supports(a)||p<.18||p>.88)return List.of();var out=new ArrayList<LightSurface>();
  if(!dome(a)){
   if(a.endsWith("/air")||a.endsWith("mirage_air"))return List.of();
   // Official full panel33.5s: continuous blue/violet water under the bright
   // edge. Keep the middle transparent so targets remain readable.
   boolean mirage=a.contains("mirage")||a.endsWith("skill_dream");
   int stage=a.endsWith("03")?3:a.endsWith("02")?2:1;
   double outer=1.6+stage*.2,inner=outer-(mirage?.48:.30);
   for(int i=0;i<16;i++){
    double start=-2.3+i*4.6/16+p*1.5,end=-2.3+(i+1)*4.6/16+p*1.5;
    out.add(new LightSurface(List.of(waterEdge(start,outer,mirage),waterEdge(end,outer,mirage),
        waterEdge(end,inner,mirage),waterEdge(start,inner,mirage)),i%2==0?0x3091eaff:0x28d8a5ff));
   }
   if(mirage)for(int i=0;i<3;i++){
    double x=(i-1)*.6,y=.5,z=1.9+i*.3;
    // Small dream highlights from44--46s, with two intersecting planes.
    double r=.11;
    out.add(new LightSurface(List.of(new Vertex(x-r,y,z),new Vertex(x,y+r,z),new Vertex(x+r,y,z),new Vertex(x,y-r,z)),0x80eff7ff));
    out.add(new LightSurface(List.of(new Vertex(x,y,z-r),new Vertex(x,y+r,z),new Vertex(x,y,z+r),new Vertex(x,y-r,z)),0x60d8a5ff));
   }
   return List.copyOf(out);
  }
  double r=a.endsWith("skill_drain")?1.6:1;
  //40--41.5s shows a rounded translucent bell rather than a cone.
  for(int band=0;band<3;band++)for(int i=0;i<12;i++){
   double angle=i*Math.TAU/12,next=(i+1)*Math.TAU/12;
   double upper=band*Math.PI/6,lower=(band+1)*Math.PI/6;
   out.add(new LightSurface(List.of(bellPoint(angle,upper,r),bellPoint(next,upper,r),
       bellPoint(next,lower,r),bellPoint(angle,lower,r)),(i+band)%2==0?0x3491eaff:0x2cd8a5ff));
  }
  return List.copyOf(out);
 }
 private static Vertex bellPoint(double angle,double latitude,double radius){
  double ring=Math.sin(latitude)*radius;
  return new Vertex(Math.cos(angle)*ring,.5+Math.cos(latitude)*radius*.7,2+Math.sin(angle)*ring);
 }
 private static Vertex waterEdge(double angle,double radius,boolean mirage){
  return new Vertex(Math.sin(angle)*radius,.2+Math.cos(angle)*(mirage?.65:.25),1.2+Math.cos(angle)*radius*.7);
 }
 private static boolean dome(String a){return a.endsWith("skill_drain")||a.endsWith("heavy_mirage")||a.equals("cantarella/skill");}
 private static void curve(List<LightPath> out,int c,float w,DoubleFunction<Vertex> fn){var v=new ArrayList<Vertex>();for(int i=0;i<=24;i++)v.add(fn.apply(i/24d));out.add(new LightPath(List.copyOf(v),c,w));}
}
