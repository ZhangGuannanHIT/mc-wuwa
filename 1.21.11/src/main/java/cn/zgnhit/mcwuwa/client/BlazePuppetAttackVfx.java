package cn.zgnhit.mcwuwa.client;

import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Observed sword/phoenix and puppet/string motifs. Never owns hit or movement rules. */
final class BlazePuppetAttackVfx {
 private static final int FIRE=0xffff6549,GOLD=0xffffc477,PURPLE=0xffd085ff,PINK=0xffff70c8,WHITE=0xfff4e4ff;
 private BlazePuppetAttackVfx(){}
 static boolean supports(String a){return (a.startsWith("changli/")||a.startsWith("yinlin/"))&&!Set.of("liberation","outro","charge").contains(a.substring(a.indexOf('/')+1));}
 static Sample sample(String a,double p,double u){
  var paths=ribbons(a,p);if(paths.isEmpty())return new Sample(0,0,0,WHITE,.2f,Kind.NONE);
  double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);var v=path.points().get(Math.min(24,(int)((at-(int)at)*25)));
  return new Sample(v.side(),v.up(),v.forward(),path.color(),.25f,Kind.DUST);
 }
 static List<LightPath> ribbons(String a,double p){
  if(!supports(a)||p<.1||p>.96)return List.of();var out=new ArrayList<LightPath>();
  if(a.startsWith("changli/")){
   boolean sacrifice=a.contains("sacrifice"),conquest=a.endsWith("conquest"),rise=a.endsWith("true_sight_charge")||a.endsWith("/heavy");
   if(conquest&&p>=.52){
    for(int feather=0;feather<6;feather++){
     final int index=feather;
     curve(out,feather%2==0?FIRE:GOLD,.035f,t->conquestPlume(index,t,0,p));
    }
   }else if(sacrifice||conquest||a.endsWith("/skill")){
    // Two curved wings with separate leading edges and feather tips, not a solid orange disc.
    for(int side:new int[]{-1,1})for(int feather=0;feather<4;feather++){
     double spread=1.1+feather*.42,lift=.4+feather*.25;
     curve(out,feather%2==0?FIRE:GOLD,.035f,t->new Vertex(side*(.2+t*spread),Math.sin(t*Math.PI)*lift-.25*t,.7+t*1.8));
    }
    if(sacrifice)for(int i=0;i<3;i++){double radius=1.3+i*.45;curve(out,i==1?GOLD:FIRE,.035f,t->new Vertex(Math.cos(t*Math.TAU)*radius,-.8,1.5+Math.sin(t*Math.TAU)*radius));}
   }else if(rise){
    for(int i=0;i<3;i++){double s=(i-1)*.22;curve(out,i==1?GOLD:FIRE,.04f,t->new Vertex(s+Math.sin(t*Math.PI)*.5,-.7+t*3.2,.7+t*1.5));}
   }else if(a.equals("changli/air")){
    for(int i=-1;i<=1;i++){double s=i*.18;curve(out,i==0?GOLD:FIRE,.04f,t->new Vertex(s,2.3-t*3.4,.8+t*1.2));}
   }else if(a.startsWith("changli/basic_")){
    int stage=a.charAt(a.length()-1)-'0';
    // Labelled retail A1-A4: diagonal arc, paired arcs, low circular sweep,
    // then a fan of fire feathers. Shape only; combat owns every contact.
    for(int layer=0;layer<3;layer++){
     double offset=layer*.08;
     curve(out,layer==1?GOLD:FIRE,layer==0?.045f:.018f,t->groundArc(stage,t,offset));
    }
    if(stage==2||stage==4)for(int side:new int[]{-1,1})for(int feather=0;feather<(stage==4?4:3);feather++){
     double spread=.65+feather*.22,lift=.25+feather*.13;
     curve(out,feather%2==0?FIRE:GOLD,.022f,t->new Vertex(side*(.45+t*spread),-.25+Math.sin(t*Math.PI)*lift,.9+t*.8));
    }
   }else{
    int stage=a.endsWith("04")?4:a.endsWith("03")?3:a.endsWith("02")?2:1;
    for(int layer=0;layer<(stage==4?3:2);layer++){
     double shift=layer*.12,sign=stage%2==0?-1:1;
     curve(out,layer%2==0?FIRE:GOLD,layer==0?.055f:.022f,t->{double angle=-2.2+t*4.4;return new Vertex(Math.sin(angle)*(1.8+shift),sign*Math.cos(angle)*(.5+shift),1.1+Math.cos(angle)*.8);});
    }
   }
  }else{
   boolean thunder=a.endsWith("judgment_thunder")||a.endsWith("magnetic_blast"),blast=a.endsWith("skill_blast")||a.endsWith("chameleon"),air=a.endsWith("/air");
   if(thunder||blast||air){
    int bolts=blast?5:thunder?3:2;
    for(int i=0;i<bolts;i++){
     double x=(i-(bolts-1)/2d)*.65,z=1.5+(i%2)*.6;
     curve(out,i%2==0?PURPLE:PINK,.035f,t->new Vertex(x+Math.sin(t*35+iPhase(x))*.12,3.2-t*4,z));
    }
    if(blast)for(int i=0;i<3;i++){double r=.7+i*.35;curve(out,PINK,.025f,t->new Vertex(Math.cos(t*Math.TAU)*r,-.8,1.8+Math.sin(t*Math.TAU)*r));}
   }else{
    int stage=a.endsWith("04")?4:a.endsWith("03")?3:a.endsWith("02")?2:1;
    if(a.equals("yinlin/basic_03")){
     // Retail sweep: a wide horizontal puppet trail, not several crossed rays.
     for(int layer=0;layer<2;layer++){
      double radius=1.75+layer*.12,height=.18+layer*.10;
      curve(out,layer==0?PINK:PURPLE,layer==0?.038f:.018f,t->{double angle=-2.4+t*4.8;return new Vertex(Math.sin(angle)*radius,height,1.25+Math.cos(angle)*radius);});
     }
    }else for(int i=0;i<(stage==4?2:stage+1);i++){
     double sign=i%2==0?1:-1,offset=(i-.5)*.2;
     curve(out,i%2==0?PURPLE:PINK,.025f,t->new Vertex(sign*(-1.6+t*3.2),(stage==4?1.2-t*1.4:.5+Math.sin(t*Math.PI)*.3)+offset,.7+t*1.8));
    }
    // Taut control strings join the caster's hands to the forward puppet volume.
    for(int i=-1;i<=1;i++){double hand=i*.25;curve(out,WHITE,.012f,t->new Vertex(hand*(1-t)+.85*t,.45+t*.15,.35+t*1.4));}
   }
  }return List.copyOf(out);
 }
 static List<LightSurface> surfaces(String a,double p){
  if(!supports(a)||!a.startsWith("changli/")||p<.1||p>.96)return List.of();
  if(a.startsWith("changli/basic_")){
   int stage=a.charAt(a.length()-1)-'0';var strips=new ArrayList<LightSurface>();
   for(int segment=0;segment<24;segment++){
    double lo=segment/24d,hi=(segment+1)/24d;
    strips.add(new LightSurface(List.of(groundArc(stage,lo,-.10),groundArc(stage,lo,.10),
     groundArc(stage,hi,.10),groundArc(stage,hi,-.10)),0x28ff503c));
   }
   return List.copyOf(strips);
  }
  if(a.endsWith("true_sight_conquest")&&p>=.52){
   var plume=new ArrayList<LightSurface>();
   for(int feather=0;feather<6;feather++)for(int segment=0;segment<8;segment++){
    double lo=segment/8d,hi=(segment+1)/8d;
    plume.add(new LightSurface(List.of(conquestPlume(feather,lo,-1,p),conquestPlume(feather,lo,1,p),
     conquestPlume(feather,hi,1,p),conquestPlume(feather,hi,-1,p)),0x38ff6549));
   }
   return List.copyOf(plume);
  }
  if(!(a.contains("sacrifice")||a.endsWith("conquest")||a.endsWith("/skill")))return List.of();
  var out=new ArrayList<LightSurface>();
  // Official complete PV 132/148-150s: broad, tapered feathers are luminous
  // sheets, not two flat triangles. Keep the existing eight feather paths.
  for(int sign:new int[]{-1,1})for(int feather=0;feather<4;feather++)for(int segment=0;segment<8;segment++){
   double lo=segment/8d,hi=(segment+1)/8d;
   out.add(new LightSurface(List.of(flameFeather(sign,feather,lo,-1),flameFeather(sign,feather,lo,1),
    flameFeather(sign,feather,hi,1),flameFeather(sign,feather,hi,-1)),feather%2==0?0x30ff6549:0x28ffc477));
  }
  return List.copyOf(out);
 }
 // Retail 429.125-429.875: crossed wings resolve into a rising spiral plume.
 // Shape phases follow the existing 24-tick cast, not the slowed video clock.
 private static Vertex conquestPlume(int feather,double t,int edge,double p){
  double phase=Math.clamp((p-.52)/.4,0,1);
  double angle=feather*Math.TAU/6+t*3.8-phase*.8;
  double radius=(1-t)*1.25+.12;
  double width=edge*Math.sin(t*Math.PI)*.16;
  return new Vertex(Math.cos(angle)*(radius+width),-.8+t*(2.5+phase*.8),
   1.7+Math.sin(angle)*(radius+width));
 }
 private static Vertex flameFeather(int side,int feather,double t,int edge){
  double spread=1.1+feather*.42,lift=.4+feather*.25;
  double width=Math.sin(t*Math.PI)*(.12+feather*.025)*edge;
  return new Vertex(side*(.2+t*spread),Math.sin(t*Math.PI)*lift-.25*t+width,.7+t*1.8-width*.6);
 }
 private static double iPhase(double x){return x*3;}
 private static Vertex groundArc(int stage,double t,double offset){
  double angle=(stage==3?-Math.PI:-2.2)+t*(stage==3?Math.TAU:4.4);
  double up=switch(stage){case 1->1.25-t*1.8;case 2->-.35+Math.sin(t*Math.PI)*.55;case 3->-.55;default->.65-t*.8;};
  return new Vertex(Math.sin(angle)*(1.75+offset),up+offset*.3,1.1+Math.cos(angle)*(1.05+offset));
 }
 private static void curve(List<LightPath> out,int color,float width,DoubleFunction<Vertex> fn){var points=new ArrayList<Vertex>();for(int i=0;i<=24;i++)points.add(fn.apply(i/24d));out.add(new LightPath(List.copyOf(points),color,width));}
}
