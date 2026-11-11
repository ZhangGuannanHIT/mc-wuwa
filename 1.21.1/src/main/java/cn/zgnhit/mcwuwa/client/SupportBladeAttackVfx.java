package cn.zgnhit.mcwuwa.client;
import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;
/** Source-observed bow, draw-cut, wind-blade and butterfly attack families. */
final class SupportBladeAttackVfx {
 private static final int ICE=0xffb9eaff,PINK=0xffffbedc,WHITE=0xfff7ffff,BLUE=0xff9acfff;
 private SupportBladeAttackVfx(){}
 static boolean supports(String a){return Set.of("hiyuki/heavy","hiyuki/future_heavy","hiyuki/future_heavy_frost","hiyuki/iai",
 "aero_rover/basic_03","aero_rover/basic_04","aero_rover/heavy","aero_rover/heavy_strangle","aero_rover/air","aero_rover/wind_air_01","aero_rover/wind_air_02","aero_rover/unbound_wind_01","aero_rover/unbound_wind_02",
 "shorekeeper/basic_01","shorekeeper/basic_02","shorekeeper/basic_03","shorekeeper/basic_04","shorekeeper/heavy","shorekeeper/heavy_deduction","shorekeeper/air_evolution").contains(a);}
 static Sample sample(String a,double p,double u){var paths=ribbons(a,p);if(paths.isEmpty())return new Sample(0,0,0,WHITE,.2f,Kind.NONE);
 double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);var v=path.points().get(Math.min(24,(int)((at-(int)at)*25)));
 return new Sample(v.side(),v.up(),v.forward(),path.color(),.3f,Kind.DUST);}
 static List<LightPath> ribbons(String a,double progress){
  if(!supports(a))return List.of();double p=Math.clamp(progress,0,1);if(p<.1||p>.96)return List.of();var out=new ArrayList<LightPath>();
  if(a.equals("hiyuki/heavy")){
   for(int i=0;i<3;i++){double side=(i-1)*.10;curve(out,i==1?WHITE:ICE,.035f,t->new Vertex(side,.15+Math.sin(t*Math.PI)*.08,.8+t*3.4));}
   for(int sign:new int[]{-1,1})curve(out,PINK,.018f,t->new Vertex(sign*Math.sin(t*Math.PI)*.35,.12+Math.cos(t*Math.PI)*.35,3.7));
  }else if(a.startsWith("hiyuki/")){
   boolean frost=a.endsWith("frost"),iai=a.endsWith("iai");
   for(int i=0;i<4;i++){double height=(i-1.5)*(iai?.06:.12);curve(out,i%2==0?WHITE:PINK,.035f,t->{double x=-2.4+t*4.8+p*.8;
    return new Vertex(Math.sin(x)*(frost?2.4:1.9),height+Math.cos(x)*.15,1.3+Math.cos(x)*.8);});}
   if(frost)for(int i=0;i<6;i++){double angle=(i-2.5)*.20;curve(out,ICE,.025f,t->new Vertex(Math.sin(angle)*t*2.7,-.7+t*1.6,1+Math.cos(angle)*t*2.7));}
   if(iai)for(int side:new int[]{-1,1})curve(out,ICE,.022f,t->new Vertex(side*(-1.4+t*2.8),-.65+t*1.6,2));
  }else if(a.startsWith("aero_rover/")){
   if(a.equals("aero_rover/unbound_wind_02")){
    // Complete cloudburst panel5.5--6.25s: rising wind coils above
    // a spreading ground ring, rather than a second falling sword cut.
    for(int strand=0;strand<3;strand++){
     double offset=strand*Math.TAU/3;
     curve(out,strand==1?WHITE:ICE,.035f,t->{
      double angle=t*Math.TAU*1.5+offset+p*2,r=1.05-t*.65;
      return new Vertex(Math.cos(angle)*r,-.8+t*3.2,1.7+Math.sin(angle)*r);
     });
    }
    curve(out,WHITE,.04f,t->new Vertex(0,-.8+t*3.2,1.7));
    for(int rim=0;rim<2;rim++){
     double r=1.55+rim*.16;
     curve(out,ICE,.025f,t->new Vertex(Math.cos(t*Math.TAU)*r,-.8,1.7+Math.sin(t*Math.TAU)*r));
    }
    return List.copyOf(out);
   }
   boolean rise=a.endsWith("wind_air_01"),drop=a.equals("aero_rover/air")||a.endsWith("unbound_wind_02"),broad=a.contains("unbound");
   if(a.endsWith("heavy"))for(int i=0;i<4;i++){double side=(i-1.5)*.10;curve(out,ICE,.03f,t->new Vertex(side,.05+Math.sin(t*Math.PI)*.14,.4+t*3.5));}
   else for(int i=0;i<4;i++){double r=(broad?2.3:1.8)-i*.09,column=(i-1.5)*.07;curve(out,i%2==0?ICE:WHITE,.04f,t->{double angle=-2.4+t*4.8+p;
    return rise?new Vertex(Math.cos(angle)*r,.2+Math.sin(angle)*r,1.6):drop?new Vertex(column,2.2-t*3.1,1.5):new Vertex(Math.sin(angle)*r,.15+Math.sin(angle)*.18,1.2+Math.cos(angle)*r*.7);});}
   if(broad)for(int side:new int[]{-1,1})curve(out,WHITE,.026f,t->new Vertex(side*t*.9,-.6+t*2.7,2.1));
  }else{
   boolean drop=a.endsWith("air_evolution"),gather=a.endsWith("heavy_deduction");
   for(int i=0;i<3;i++){
    double center=(i-1)*.7,depth=1.3+i*.4;
    for(int sign:new int[]{-1,1})curve(out,i%2==0?BLUE:PINK,.03f,t->new Vertex(center+sign*Math.sin(t*Math.PI)*.55,.15+Math.sin(t*Math.TAU)*.35,depth+t*.5));
   }
   if(drop)for(int i=0;i<4;i++){double side=(i-1.5)*.3;curve(out,ICE,.028f,t->new Vertex(side,2.3-t*3.1,1.7));}
   else for(int i=0;i<3;i++){double r=gather?2*(1-p)+.3:1.1+i*.12;double h=(i-1)*.22;
    curve(out,i%2==0?BLUE:PINK,.035f,t->{double angle=t*Math.TAU;return new Vertex(Math.cos(angle)*r,h+Math.sin(angle)*.18,1.3+Math.sin(angle)*r*.65);});}
  }
  return List.copyOf(out);
 }
 static List<LightSurface> surfaces(String a,double p){
  if(!supports(a)||p<.18||p>.9)return List.of();var out=new ArrayList<LightSurface>();
  if(a.equals("hiyuki/future_heavy_frost"))for(int i=0;i<6;i++){
   double x=(i-2.5)*.5;out.add(new LightSurface(List.of(new Vertex(x,-.7,1.6),new Vertex(x+.24,-.7,1.6),new Vertex(x+.1,.65,2.8),new Vertex(x-.10,.5,2.7)),0x55b9eaff));
  }else if(a.startsWith("shorekeeper/"))for(int i=0;i<3;i++)for(int side:new int[]{-1,1}){
   double x=(i-1)*.7,z=1.3+i*.4;var center=new Vertex(x,.2,z);
   out.add(new LightSurface(List.of(center,new Vertex(x+side*.45,.65,z+.15),new Vertex(x+side*.58,.25,z+.4),center),i%2==0?0x5c9acfff:0x5cffbedc));
  }
  return List.copyOf(out);
 }
 private static void curve(List<LightPath> out,int color,float width,DoubleFunction<Vertex> fn){var points=new ArrayList<Vertex>();for(int i=0;i<=24;i++)points.add(fn.apply(i/24d));out.add(new LightPath(List.copyOf(points),color,width));}
}
