package cn.zgnhit.mcwuwa.client;
import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;
/** Phoebe's official combat film: golden arrays, refracting facets and Starflash. */
final class PhoebePrismVfx {
 private static final int GOLD=0xffffe9a5,WHITE=0xfffffff1,BLUE=0xffb0e9ff;
 private PhoebePrismVfx(){}
 static boolean supports(String a){return a.startsWith("phoebe/")&&Set.of("basic_01","basic_02","basic_03","heavy","air","dodge_counter","ring_counter","skill","skill_teleport","refraction","ring_basic_01","ring_basic_02","ring_basic_03","absolution","confession","heavy_starlight","intro").contains(a.substring(7));}
 static Sample sample(String a,double p,double u){
  var paths=ribbons(a,p);if(paths.isEmpty())return new Sample(0,0,0,WHITE,.2f,Kind.NONE);
  double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);var v=path.points().get(Math.min(24,(int)((at-(int)at)*25)));
  return new Sample(v.side(),v.up(),v.forward(),path.color(),.25f,Kind.DUST);
 }
 static List<LightPath> ribbons(String a,double progress){
  if(!supports(a))return List.of();double p=Math.clamp(progress,0,1);if(p<.1||p>.96)return List.of();var out=new ArrayList<LightPath>();
  boolean star=a.endsWith("heavy_starlight"),prism=a.endsWith("refraction")||a.equals("phoebe/heavy"),air=a.endsWith("/air"),ring=a.contains("ring_");
  if(a.equals("phoebe/intro")){
   // The isolated entry review shows the caster inside a faceted light
   // cradle before its outward sweep. Keep it centered on the caster,
   // unlike Starflash's prism projected ahead of the target.
   if(p<.55){
    var v=introVertices(p);
    for(int i=0;i<4;i++){
     line(out,v.get(0),v.get(i+2),GOLD,.026f);
     line(out,v.get(1),v.get(i+2),WHITE,.018f);
     line(out,v.get(i+2),v.get((i+1)%4+2),GOLD,.022f);
    }
    return List.copyOf(out);
   }
   // Community rotation78.25--78.75s: golden outward entry sweep,
   // before the following mirror skill. Keep the horizontal repulsion
   // distinct from that skill's vertical rings; MC envelope, not native frames.
   double radius=.35+Math.clamp((p-.1)/.65,0,1)*3.4;
   for(int i=0;i<2;i++){
    double r=radius-i*.16,y=-.5+i*.16;
    curve(out,i==0?GOLD:WHITE,i==0?.05f:.018f,
        t->new Vertex(Math.cos(t*Math.TAU)*r,y,Math.sin(t*Math.TAU)*r));
   }
   for(int i=0;i<8;i++){
    double angle=i*Math.TAU/8+p*.35,c=Math.cos(angle),s=Math.sin(angle);
    curve(out,WHITE,.018f,t->new Vertex(c*(radius-.35+t*.35),-.42+t*.14,s*(radius-.35+t*.35)));
   }
  }else if(star||prism){
   var vertices=prismVertices(p,star?1.0:.7);
   for(int i=0;i<4;i++){
    Vertex side=vertices.get(i+2);line(out,vertices.getFirst(),side,i%2==0?GOLD:BLUE,.038f);line(out,vertices.get(1),side,i%2==0?BLUE:GOLD,.038f);
   }
   for(int i=0;i<4;i++){double side=(i-1.5)*.48;curve(out,WHITE,.04f,t->new Vertex(side*(1-t),.1+t*.2,.5+t*4.4));}
  }else if(air){
   for(int i=0;i<4;i++){double side=(i-1.5)*.14;curve(out,i%2==0?GOLD:WHITE,.038f,t->new Vertex(side,2.8-t*3.7,1.3+t*.9));}
   curve(out,GOLD,.035f,t->new Vertex(Math.cos(t*Math.TAU)*1.5,-.8,1.6+Math.sin(t*Math.TAU)*1.5));
  }else if(a.equals("phoebe/skill")||a.endsWith("teleport")||a.endsWith("absolution")||a.endsWith("confession")){
   for(int i=0;i<3;i++){double r=.8+i*.35;curve(out,i==1?BLUE:GOLD,.033f,t->new Vertex(Math.cos(t*Math.TAU)*r,.3+Math.sin(t*Math.TAU)*r,2.1));}
   for(int i=0;i<4;i++){double angle=i*Math.TAU/4;curve(out,WHITE,.028f,t->new Vertex(Math.cos(angle)*t*1.8,.3+Math.sin(angle)*t*1.8,2.1));}
  }else{
   int stage=a.endsWith("03")?3:a.endsWith("02")?2:1;
   for(int i=0;i<stage;i++){
    double cx=(i-(stage-1)/2d)*.65,z=1.3+i*.5;double size=ring?.65:.45;
    curve(out,i%2==0?GOLD:BLUE,.035f,t->{double v=t*4;int edge=Math.min(3,(int)v);double q=v-edge;double[] x={0,size,0,-size,0},y={size,0,-size,0,size};return new Vertex(cx+x[edge]*(1-q)+x[edge+1]*q,.3+y[edge]*(1-q)+y[edge+1]*q,z);});
    curve(out,WHITE,.025f,t->new Vertex(cx,.3,Math.max(.4,z-1)+t*3));
   }
   if(ring)for(int i=0;i<4;i++){double angle=i*Math.TAU/4;curve(out,GOLD,.025f,t->new Vertex(Math.cos(angle)*t*1.5,.3+Math.sin(angle)*t*1.1,2.9));}
  }
  return List.copyOf(out);
 }
 static List<LightSurface> surfaces(String a,double p){
  if(a.equals("phoebe/intro")){
   if(p<.1||p>.55)return List.of();
   var v=introVertices(p);var faces=new ArrayList<LightSurface>();
   int alpha=(int)(38*Math.min(1,(.55-p)/.12));
   for(int i=0;i<4;i++)for(int pole=0;pole<2;pole++)
    faces.add(new LightSurface(List.of(v.get(pole),v.get(i+2),v.get((i+1)%4+2),v.get(pole)),
        alpha<<24|(i%2==0?0xffe9a5:0xc7eaff)));
   return List.copyOf(faces);
  }
  if(!supports(a)||p<.18||p>.88||!(a.endsWith("heavy_starlight")||a.endsWith("refraction")||a.equals("phoebe/heavy")))return List.of();
  var v=prismVertices(p,a.endsWith("heavy_starlight")?1:.7);var out=new ArrayList<LightSurface>();
  for(int i=0;i<4;i++)for(int pole=0;pole<2;pole++)out.add(new LightSurface(List.of(v.get(pole),v.get(i+2),v.get((i+1)%4+2),v.get(pole)),(i+pole)%2==0?0x44b0e9ff:0x44ffe9a5));
  if(a.endsWith("heavy_starlight"))for(int i=0;i<8;i++){
   // Official36--38s: a faceted center with long narrow rays, not a
   // screen-filling opaque flash. Quad widths stay below one tenth block.
   double angle=i*Math.TAU/8,c=Math.cos(angle),s=Math.sin(angle);
   double inner=.82,outer=i%2==0?2.35:1.85,width=.035;
   out.add(new LightSurface(List.of(
       new Vertex(c*inner-s*width,.3+s*inner+c*width,2.6),
       new Vertex(c*outer,.3+s*outer,2.6),
       new Vertex(c*inner+s*width,.3+s*inner-c*width,2.6),
       new Vertex(c*.65,.3+s*.65,2.6)),i%2==0?0x70fff6d1:0x50b0e9ff));
  }
  return List.copyOf(out);
 }
 private static List<Vertex> introVertices(double p){
  double radius=1.05,angle=(p-.1)*Math.PI;
  var v=new ArrayList<Vertex>();v.add(new Vertex(0,1.4,0));v.add(new Vertex(0,-1.05,0));
  for(int i=0;i<4;i++)v.add(new Vertex(Math.cos(angle+i*Math.PI/2)*radius,.15,Math.sin(angle+i*Math.PI/2)*radius));
  return v;
 }
 private static List<Vertex> prismVertices(double p,double r){
  var v=new ArrayList<Vertex>();v.add(new Vertex(0,.3+r*1.3,2.6));v.add(new Vertex(0,.3-r*1.3,2.6));
  for(int i=0;i<4;i++){double angle=i*Math.TAU/4+p*.5;v.add(new Vertex(Math.cos(angle)*r,.3,2.6+Math.sin(angle)*r));}return v;
 }
 private static void line(List<LightPath> out,Vertex a,Vertex b,int c,float w){curve(out,c,w,t->new Vertex(a.side()*(1-t)+b.side()*t,a.up()*(1-t)+b.up()*t,a.forward()*(1-t)+b.forward()*t));}
 private static void curve(List<LightPath> out,int c,float w,DoubleFunction<Vertex> fn){var v=new ArrayList<Vertex>();for(int i=0;i<=24;i++)v.add(fn.apply(i/24d));out.add(new LightPath(List.copyOf(v),c,w));}
}
