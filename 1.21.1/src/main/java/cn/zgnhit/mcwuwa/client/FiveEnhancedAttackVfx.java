package cn.zgnhit.mcwuwa.client;
import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;
/** Complete reviewed five-role enhanced panels; geometry is visual, damage is server-owned. */
final class FiveEnhancedAttackVfx {
 private static final int GOLD=0xffffdc86,WHITE=0xfff8ffff,RED=0xffff5271,VIOLET=0xffb99bff,BLUE=0xff83dbff,PINK=0xffffafd7;
 private static final Set<String> ACTIONS=Set.of(
  "zani/breakthrough","zani/heavy","zani/heavy_dawn","zani/heavy_day","zani/heavy_night","zani/heavy_flash","zani/heavy_night_last",
  "augusta/heavy","augusta/heavy_retreat","augusta/heavy_spin","augusta/heavy_uppercut","augusta/counter_heavy",
  "suoming/heart","suoming/heart_hold","suoming/deep_basic_01","suoming/deep_basic_02","suoming/deep_basic_03","suoming/deep_basic_04","suoming/thunder_spin_01","suoming/thunder_spin_02",
  "lucilla/basic_03_perfect","lucilla/memory_basic_01","lucilla/memory_basic_02","lucilla/memory_basic_03",
  "zhezhi/heavy_conjuration","zhezhi/skill_genius","zhezhi/skill_genius_air","zhezhi/skill_maestro");
 private FiveEnhancedAttackVfx(){}
 static boolean supports(String a){return ACTIONS.contains(a);}
 static Sample sample(String a,double p,double u){
  var paths=ribbons(a,p);if(paths.isEmpty())return new Sample(0,0,0,WHITE,.2f,Kind.NONE);
  double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);
  var v=path.points().get(Math.min(24,(int)((at-(int)at)*25)));
  return new Sample(v.side(),v.up(),v.forward(),path.color(),.32f,Kind.DUST);
 }
 static List<LightPath> ribbons(String a,double progress){
  double p=Math.clamp(progress,0,1);if(!supports(a)||p<.1||p>.96)return List.of();
  var out=new ArrayList<LightPath>();
  if(a.startsWith("zani/")){
   if(a.endsWith("breakthrough")){
    for(int i=0;i<6;i++){double side=(i-2.5)*.16;curve(out,i%2==0?WHITE:GOLD,.045f,t->new Vertex(side*(1-t),.12+Math.sin(t*Math.PI)*.12,.4+t*3.8));}
   }else if(a.contains("night")){
    for(int i=-1;i<=1;i++){double side=i*.1;curve(out,i==0?WHITE:GOLD,.055f,t->new Vertex(side,3.2-t*4,1.8+Math.sin(t*Math.PI)*.4));}
    curve(out,GOLD,.03f,t->{double x=t*Math.TAU;return new Vertex(Math.cos(x)*1.8,-.85,1.8+Math.sin(x)*.8);});
   }else{
    for(int i=0;i<4;i++){double radius=1.85+i*.1;curve(out,i%2==0?GOLD:WHITE,.04f,t->{double x=-2.5+t*4.8+p*2;return new Vertex(Math.sin(x)*radius,.15+Math.sin(x)*.15,1.2+Math.cos(x)*radius*.65);});}
   }
  }else if(a.startsWith("augusta/")){
   boolean rise=a.endsWith("uppercut"),spin=a.endsWith("spin")||a.contains("counter");
   for(int i=0;i<4;i++){double radius=1.8+i*.10;curve(out,i%2==0?GOLD:WHITE,.04f,t->{double x=-2.3+t*4.6+p*2;
    return rise?new Vertex(Math.cos(x)*radius,.2+Math.sin(x)*radius,1.5):new Vertex(Math.sin(x)*radius,.1+Math.cos(x)*.12,1.3+Math.cos(x)*radius*.7);});}
   for(int i=0;i<(spin?6:3);i++){double side=(i-(spin?2.5:1))*.55;curve(out,GOLD,.023f,t->new Vertex(side+Math.sin(t*15)*.10,-.8+t*3.4,2.2));}
  }else if(a.startsWith("suoming/")){
   boolean heart=a.endsWith("heart")||a.endsWith("heart_hold");
   if(heart){
    for(int i=0;i<8;i++){double angle=i*Math.TAU/8;curve(out,i%2==0?RED:WHITE,.032f,t->{double radius=1.8*(1-t);return new Vertex(Math.cos(angle)*radius,.1+Math.sin(angle)*radius,2.4);});}
    curve(out,RED,.06f,t->new Vertex(Math.sin(t*22+p*18)*.12,.1+Math.cos(t*14)*.08,.4+t*3));
   }else{
    for(int i=0;i<4;i++){double r=1.9+i*.09;curve(out,i%2==0?RED:WHITE,.04f,t->{double x=-2.6+t*5+p*3;return new Vertex(Math.sin(x)*r,.15+Math.sin(x)*.35,1.2+Math.cos(x)*r*.6);});}
    for(int side:new int[]{-1,1})curve(out,RED,.03f,t->new Vertex(side*(.2+t*.7),-.7+t*2.2,2));
   }
  }else if(a.startsWith("lucilla/")){
   // Rectangular film frames travel in crossed planes; these are not solid weapons.
   int frames=a.endsWith("03")?3:2;
   for(int i=0;i<frames;i++){
    double center=(i-(frames-1)*.5)*1.05,depth=1.1+i*.35+p*.5;
    Vertex[] v={new Vertex(center-.55,-.45,depth),new Vertex(center+.55,-.45,depth),new Vertex(center+.55,1.05,depth),new Vertex(center-.55,1.05,depth)};
    for(int edge=0;edge<4;edge++){var x=v[edge];var y=v[(edge+1)%4];curve(out,edge%2==0?VIOLET:BLUE,.027f,t->lerp(x,y,t));}
   }
  }else{
   for(int side:new int[]{-1,1}){
    curve(out,side<0?PINK:BLUE,.055f,t->{double angle=-2+t*4+p*1.8;return new Vertex(Math.cos(angle)*1.7,.12+side*Math.sin(angle)*.75,1.3+Math.sin(angle)*.75);});
    for(int wing:new int[]{-1,1})curve(out,side<0?WHITE:BLUE,.03f,t->new Vertex(side*(.5+t*.7)+wing*Math.sin(t*Math.PI)*.5,.4+Math.sin(t*Math.PI)*.65,1+t*2.3));
   }
   if(a.endsWith("maestro"))for(int i=0;i<3;i++){double side=(i-1)*.75;curve(out,PINK,.04f,t->new Vertex(side,2.4-t*2.7,2.4));}
  }
  return List.copyOf(out);
 }
 static List<LightSurface> surfaces(String a,double p){
  if(!supports(a)||p<.18||p>.9)return List.of();var out=new ArrayList<LightSurface>();
  if(a.startsWith("lucilla/")){
   for(int i=0;i<6;i++){double x=-1.5+i*.5,z=1.5+(i%2)*.3;
    out.add(new LightSurface(List.of(new Vertex(x,-.42,z),new Vertex(x+.38,-.42,z),new Vertex(x+.38,.95,z),new Vertex(x,.95,z)),0x35b6a6ff));}
  }else if(a.startsWith("zani/")&&a.contains("night")){
   out.add(new LightSurface(List.of(new Vertex(-.11,3.1,1.7),new Vertex(.11,3.1,1.7),new Vertex(.38,-.8,2.1),new Vertex(-.38,-.8,2.1)),0x65ffe7a0));
  }else if(a.startsWith("zhezhi/")){
   for(int side:new int[]{-1,1})for(int wing:new int[]{-1,1}){
    double x=side*.95;var root=new Vertex(x,.5,2);
    out.add(new LightSurface(List.of(root,new Vertex(x+wing*.7,.9,2.2),new Vertex(x+wing*.9,.65,2.5),root),side<0?0x65ffbbd9:0x658fddff));
   }
  }
  return List.copyOf(out);
 }
 private static Vertex lerp(Vertex a,Vertex b,double t){return new Vertex(a.side()+(b.side()-a.side())*t,a.up()+(b.up()-a.up())*t,a.forward()+(b.forward()-a.forward())*t);}
 private static void curve(List<LightPath> out,int color,float width,DoubleFunction<Vertex> f){var points=new ArrayList<Vertex>();for(int i=0;i<=24;i++)points.add(f.apply(i/24d));out.add(new LightPath(List.copyOf(points),color,width));}
}
