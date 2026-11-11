package cn.zgnhit.mcwuwa.client;

import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Full-panel observations: scissor planes, ink cuts and a five-line musical staff. */
final class ScissorInkMelodyVfx {
    private static final int RED=0xffff667c,WHITE=0xffffe7ed,INK=0xff65f3df,GOLD=0xffffedaf,TEAL=0xff98f6d9;
    private ScissorInkMelodyVfx(){}
    static boolean supports(String a){
        if(a.startsWith("chisa/"))return Set.of("heavy","severing_pincers","severing_extra","saw_basic_01","saw_basic_02","saw_basic_02_hold","saw_resonance_02","saw_basic_03","saw_basic_03_hold","saw_resonance_03","saw_counter","saw_counter_hold","saw_finale").contains(a.substring(6));
        if(a.startsWith("qiuyuan/"))return a.contains("ink_heavy_")||Set.of("heavy","answer_basic_01","answer_basic_02","answer_basic_03","answer_basic_04").contains(a.substring(8));
        return a.startsWith("ciaccona/")&&Set.of("basic_03","basic_04","heavy","heavy_quartet","air_01","air_02").contains(a.substring(9));
    }
    static Sample sample(String a,double p,double u){
        var paths=ribbons(a,p);if(paths.isEmpty())return new Sample(0,0,0,WHITE,.2f,Kind.NONE);
        double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);var v=path.points().get(Math.min(24,(int)((at-(int)at)*25)));
        return new Sample(v.side(),v.up(),v.forward(),path.color(),.28f,Kind.DUST);
    }
    static List<LightPath> ribbons(String a,double progress){
        if(!supports(a))return List.of();double p=Math.clamp(progress,0,1);
        if(a.startsWith("chisa/")?(p<=0||p>=1):(p<.1||p>.96))return List.of();
        var out=new ArrayList<LightPath>();
        if(a.startsWith("chisa/")){
            boolean spin=a.equals("chisa/heavy"),finale=a.endsWith("finale"),pincers=a.contains("severing");
            if(pincers||finale)for(int side:new int[]{-1,1})for(int layer=0;layer<3;layer++){
                double offset=(layer-1)*.06;curve(out,layer==1?WHITE:RED,.045f,t->new Vertex(side*(-2.4+t*4.8),offset+(finale?2.3-t*3.5:-.8+t*1.6),1.6+t*.8));
            }
            else for(int i=0;i<5;i++){
                double r=2.2-i*.07;curve(out,i==2?WHITE:RED,.035f,t->{double angle=t*Math.TAU+p*3;return new Vertex(Math.cos(angle)*r,(spin?-.65:.2)+Math.sin(angle)*(spin?.15:.8),1.1+Math.sin(angle)*r*.65);});
            }
            if(a.contains("saw"))for(int i=0;i<3;i++){
                double side=(i-1)*.1;curve(out,RED,.026f,t->new Vertex(side+Math.sin(t*Math.PI*8+p*10)*.08,-.6+t*1.8,1.8+t*.6));
            }
        }else if(a.startsWith("qiuyuan/")){
            int step=a.contains("ink_heavy_01")?1:a.contains("ink_heavy_02")?2:a.contains("ink_heavy_03")?3:0;
            int answer=a.contains("answer_basic_")?a.charAt(a.length()-1)-'0':0;
            for(int i=0;i<4;i++){
                double offset=(i-1.5)*.065;curve(out,i==1?0xffe5fff9:INK,.045f,t->{
                    double angle=-2.1+t*4.2+p;
                    if(step==1)return new Vertex(Math.cos(angle)*1.6+offset,.2+Math.sin(angle)*1.7,1.7);
                    if(step==3)return new Vertex(offset,2.8-t*4,1.1+t*1.2);
                    if(answer==4)return new Vertex(offset,.15,.5+t*3.5);
                    if(answer>0)return answerArc(answer,p,t,offset);
                    return new Vertex(Math.sin(angle)*(step==2?2.5:1.8),offset,1.3+Math.cos(angle)*1.1);
                });
            }
            if(step==3)out.addAll(QiuyuanInkBurstVfx.ribbons(a,p));
        }else{
            boolean quartet=a.endsWith("heavy_quartet"),air=a.contains("air"),leap=a.equals("ciaccona/heavy");
            // Five parallel stave lines remain continuous; notes are closed paths, not loose dots.
            for(int i=0;i<5;i++){
                double height=(i-2)*.12;curve(out,i==2?GOLD:TEAL,.028f,t->new Vertex(-2.3+t*4.6,height+Math.sin(t*Math.PI)*(leap?1.1:.25),.8+t*(quartet?2.8:1.4)));
            }
            for(int i=0;i<3;i++){
                double x=(i-1)*.85,z=1.3+i*.45;curve(out,GOLD,.035f,t->{double angle=t*Math.TAU;return new Vertex(x+Math.cos(angle)*.19,.2+Math.sin(angle)*.12+(air?.7:0),z);});
                curve(out,GOLD,.025f,t->new Vertex(x+.18,.2+t*.7+(air?.7:0),z));
            }
        }
        return List.copyOf(out);
    }
    static List<LightSurface> surfaces(String a,double p){
        if(!supports(a)||p<.18||p>.85)return List.of();var out=new ArrayList<LightSurface>();
        if(a.startsWith("chisa/")&&(a.contains("severing")||a.endsWith("saw_finale")))for(int side:new int[]{-1,1}){
            out.add(new LightSurface(List.of(new Vertex(side*-2,-.7,1.7),new Vertex(side*-1.8,-.9,1.7),new Vertex(side*2,1.2,2.5),new Vertex(side*1.8,1.4,2.5)),0x48ff667c));
        }
        out.addAll(QiuyuanInkBurstVfx.surfaces(a,p));
        if(a.startsWith("qiuyuan/ink_heavy_01")||a.startsWith("qiuyuan/ink_heavy_02")){
            // The retained full panel shows a dark ink blade inside the mint
            // rim. Use the same moving edges, including the C3 variants.
            var edges=ribbons(a,p);
            out.addAll(SlashSurfaceGeometry.between(edges.get(0),edges.get(3),0x780b2927));
        }
        if(a.contains("qiuyuan/answer_basic_")){
            int stage=a.charAt(a.length()-1)-'0';
            if(stage==4)out.add(new LightSurface(List.of(new Vertex(-.10,.15,.5),new Vertex(.10,.15,.5),new Vertex(.02,.15,4),new Vertex(-.02,.15,4)),0x2865f3df));
            else for(int i=0;i<20;i++){
                double t=i/20d,n=(i+1)/20d;
                out.add(new LightSurface(List.of(answerArc(stage,p,t,-.055),answerArc(stage,p,n,-.055),answerArc(stage,p,n,.055),answerArc(stage,p,t,.055)),0x2865f3df));
            }
        }
        return List.copyOf(out);
    }
    private static Vertex answerArc(int stage,double p,double t,double offset){
        double angle=-2.1+t*4.2+(p-.5)*1.2,sign=stage==2?-1:1;
        return new Vertex(sign*Math.sin(angle)*(stage==3?2.4:1.9),offset+Math.sin(angle)*(stage==1?.65:stage==3?.25:-.35),1.3+Math.cos(angle)*1.15);
    }
    private static void curve(List<LightPath> out,int color,float width,DoubleFunction<Vertex> fn){var points=new ArrayList<Vertex>();for(int i=0;i<=24;i++)points.add(fn.apply(i/24d));out.add(new LightPath(List.copyOf(points),color,width));}
}
