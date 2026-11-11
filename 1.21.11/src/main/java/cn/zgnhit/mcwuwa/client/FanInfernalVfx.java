package cn.zgnhit.mcwuwa.client;
import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;
/** Completed official fan-film and infernal-pistol panels; no decorative damage. */
final class FanInfernalVfx {
 private static final int GOLD=0xffffe596,JADE=0xff82e6d1,BLUE=0xff93c7ff,VIOLET=0xffd49bff,RED=0xffff94ad;
 private FanInfernalVfx(){}
 static boolean supports(String a){
  if(a.startsWith("galbrena/"))return !Set.of("galbrena/liberation","galbrena/intro","galbrena/hellwalk").contains(a)&&Set.of("basic_01","basic_02","basic_03","basic_04","dodge_counter","air","air_rain","heavy_01","heavy_02","heavy_03","skill","skill_ascend","demon_basic_01","demon_basic_02","demon_basic_03","demon_basic_04","demon_basic_05","demon_heavy_01","demon_heavy_02","demon_heavy_03","demon_dodge_counter","demon_air","demon_air_rain","demon_skill").contains(a.substring(9));
  return a.startsWith("suisui/")&&Set.of("basic_01","basic_02","basic_03","basic_04","air","dodge_counter","skill","skill_awaken","rain_skill","intro","rain_basic_01","rain_basic_02","rain_basic_03","rain_basic_04","rain_heavy","rain_descent_hold","rain_descent","reserve_dance").contains(a.substring(7));
 }
 static Sample sample(String a,double p,double u){
  var paths=ribbons(a,p);if(paths.isEmpty())return new Sample(0,0,0,GOLD,.2f,Kind.NONE);
  if(a.endsWith("air_rain"))return new Sample((u-.5)*.72,.3-3.2*u,.4,BLUE,.27f,Kind.DUST);
  double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);var v=path.points().get(Math.min(24,(int)((at-(int)at)*25)));
  return new Sample(v.side(),v.up(),v.forward(),path.color(),.27f,Kind.DUST);
 }
 static List<LightPath> ribbons(String a,double progress){
  if(!supports(a))return List.of();double p=Math.clamp(progress,0,1);
  if(a.equals("suisui/reserve_dance")){
   var dance=new ArrayList<LightPath>();
   for(int side:new int[]{-1,1})for(int layer=0;layer<2;layer++){
    double phase=p*Math.TAU+side*.7,radius=.65+layer*.1;
    curve(dance,layer==0?GOLD:JADE,.025f,t->{double angle=phase+t*2.4;return new Vertex(side*(.25+Math.sin(angle)*radius),.65+.35*Math.cos(angle),.35+Math.cos(angle)*.45);});
   }
   return List.copyOf(dance);
  }
  if(p<.1||p>.96)return List.of();var out=new ArrayList<LightPath>();
  if(a.startsWith("galbrena/")){
   boolean rain=a.endsWith("air_rain"),demon=a.contains("demon_"),air=a.endsWith("/air")||a.endsWith("demon_air");
   if(rain||air)for(int i=0;i<5;i++){
    double x=(i-2)*.18;curve(out,i%2==0?BLUE:VIOLET,.035f,t->new Vertex(x,.3-t*3.2,rain?.4:.3+t*.12));
   }else{
    for(int side:new int[]{-1,1})for(int layer=0;layer<2;layer++){
     double x=side*(.38+layer*.07);curve(out,side<0?BLUE:RED,.035f,t->new Vertex(x,.05+Math.sin(t*Math.PI)*.04,.6+t*3.8));
    }
    for(int i=0;i<(demon?4:2);i++){
     double angle=i*Math.TAU/(demon?4:2)+p*.5;curve(out,VIOLET,.028f,t->new Vertex(Math.cos(angle)*t*.7,.15+Math.sin(angle)*t*.7,3.4));
    }
   }
   if(demon)for(int side:new int[]{-1,1})curve(out,VIOLET,.03f,t->new Vertex(side*Math.sin(t*Math.PI)*2.2,.3+Math.sin(t*Math.TAU)*.45,1.3+t));
  }else{
   boolean down=a.contains("descent")||a.equals("suisui/air"),rain=a.contains("rain_"),lift=a.endsWith("rain_heavy");
   if(down)for(int i=0;i<5;i++){
    double angle=i*Math.TAU/5;curve(out,i%2==0?GOLD:JADE,.04f,t->new Vertex(Math.cos(angle)*t*1.4,2.5-t*3.5,1.3+Math.sin(angle)*t*1.4));
   }else for(int i=0;i<4;i++){
    double r=1.6+i*.11;curve(out,i%2==0?GOLD:JADE,.04f,t->{double angle=-2.1+t*4.2+p;return new Vertex(Math.sin(angle)*r,.2+Math.cos(angle)*(lift?1.4:.35),1.4+Math.cos(angle)*r*.55);});
   }
   // Long curved fan strokes and four-point bird wings from the source.
   for(int side:new int[]{-1,1})for(int i=0;i<2;i++){
    double height=.5+i*.18;curve(out,i==0?GOLD:JADE,.03f,t->new Vertex(side*t*1.7,height+Math.sin(t*Math.PI)*(rain?.7:.4),1.8+t*.3));
   }
  }
  return List.copyOf(out);
 }
 static List<LightSurface> surfaces(String a,double p){
  if(!supports(a)||a.equals("suisui/reserve_dance")||p<.18||p>.88)return List.of();var out=new ArrayList<LightSurface>();
  if(a.startsWith("suisui/"))for(int side:new int[]{-1,1}){
   var center=new Vertex(0,.5,1.8);out.add(new LightSurface(List.of(center,new Vertex(side*.8,1.1,2),new Vertex(side*1.6,.7,2.1),center),0x48ffe596));
  }
  if(a.startsWith("suisui/")&&!a.contains("descent")&&!a.equals("suisui/air")){
   // Official32--35.5s and52--53.5s: wide golden/jade fan sweeps.
   // Follow the existing stroke plane, with a rising plane for rain-heavy.
   boolean lift=a.endsWith("rain_heavy");
   for(int i=0;i<16;i++){
    double first=-2.1+i*4.2/16+p,next=-2.1+(i+1)*4.2/16+p;
    out.add(new LightSurface(List.of(fanEdge(first,1.93,lift),fanEdge(next,1.93,lift),
        fanEdge(next,1.50,lift),fanEdge(first,1.50,lift)),i%2==0?0x30ffe596:0x2882e6d1));
   }
  }
  if(a.startsWith("galbrena/demon_"))for(int side:new int[]{-1,1}){
   var center=new Vertex(0,.2,1.4);out.add(new LightSurface(List.of(center,new Vertex(side*1.2,.8,1.8),new Vertex(side*2.1,.2,2.4),center),0x42d49bff));
  }return List.copyOf(out);
 }
 private static Vertex fanEdge(double angle,double radius,boolean lift){
  return new Vertex(Math.sin(angle)*radius,.2+Math.cos(angle)*(lift?1.4:.35),1.4+Math.cos(angle)*radius*.55);
 }
 private static void curve(List<LightPath> out,int c,float w,DoubleFunction<Vertex> fn){var v=new ArrayList<Vertex>();for(int i=0;i<=24;i++)v.add(fn.apply(i/24d));out.add(new LightPath(List.copyOf(v),c,w));}
}
