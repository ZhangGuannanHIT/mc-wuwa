package cn.zgnhit.mcwuwa.client;

import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Observed pink stellar cuts and cyan mech triangulation, fitted to MC clocks. */
final class AemeathPilotVfx {
    private static final int PINK=0xffff95e1,CYAN=0xff8feaff,WHITE=0xfff4ffff;
    private AemeathPilotVfx(){}
    static boolean supports(String a){return a.startsWith("aemeath/basic_")||a.startsWith("aemeath/mech_")
        ||Set.of("aemeath/skill","aemeath/wing_ensemble_stage","aemeath/wing_ensemble_descend","aemeath/heavy","aemeath/heavy_charge_2","aemeath/air","aemeath/dodge_counter","aemeath/skill_dawn","aemeath/liberation","aemeath/liberation_finale","aemeath/intro","aemeath/intro_starlight").contains(a);}
    static Sample sample(String action,double p,double u){
        if(!supports(action))return null;var paths=ribbons(action,p);
        if(paths.isEmpty())return new Sample(0,0,0,WHITE,0,Kind.NONE);
        double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);
        var v=path.points().get(Math.min(path.points().size()-1,(int)((at-(int)at)*path.points().size())));
        return new Sample(v.side(),v.up(),v.forward(),path.color(),.32f,Kind.DUST);
    }
    static List<LightPath> ribbons(String action,double progress){
        if(!supports(action))return null;double p=action.equals("aemeath/liberation_finale")?finalePhase(progress):Math.clamp(progress,0,1);
        if(p<.10||p>.92)return List.of();var paths=new ArrayList<LightPath>();
        if(action.equals("aemeath/liberation")){
            // Full R card0-5.5s: rising star, falling stellar streaks, then a flat star seal.
            // 0.5--1.5s: the human close-up has a head halo and flowing arcs,
            // not the later four converging star rays. Preserve this distinction
            // even though MC keeps the player's freely controlled camera.
            if(p<2.0/5.5){
                double turn=p*5.5;
                curve(paths,WHITE,.024f,t->{double a=t*Math.TAU;return new Vertex(Math.cos(a)*.38,.78+Math.sin(a)*.38,-.18);});
                for(int i=0;i<3;i++){
                    final int band=i;
                    curve(paths,i==1?CYAN:PINK,.024f,t->{double a=-2.5+t*4.7+turn*.9+band*.4;return new Vertex(Math.cos(a)*(1.05+band*.17),.15+band*.2+Math.sin(a)*.16,Math.sin(a)*.72);});
                }
                return List.copyOf(paths);
            }
            double converge=Math.clamp((p-.27)/.28,0,1),radius=2.1*(1-converge)+.2;
            for(int i=0;i<4;i++){
                double a=i*Math.TAU/4;
                curve(paths,i%2==0?PINK:CYAN,.035f,t->new Vertex(Math.cos(a)*radius*(1-t),.2+t*(1.8+converge),1.7+Math.sin(a)*radius*(1-t)));
            }
            if(p>.52&&p<.78){
                // 3--4s: two broad curving stellar sweeps around the mech.
                for(int i=0;i<2;i++){
                    final int side=i==0?-1:1;
                    curve(paths,i==0?CYAN:PINK,.08f,t->{double a=-2.6+t*4.8+(p-.52)*5;return new Vertex(Math.cos(a)*2.2,.5+side*Math.sin(a)*.48,1.2+Math.sin(a)*1.35);});
                }
            }
            if(p<.70)return List.copyOf(paths);
            double expansion=Math.clamp((p-.70)/.20,0,1);
            for(int layer=0;layer<2;layer++){
                double r=.6+expansion*2.0-layer*.13;
                curve(paths,layer==0?WHITE:CYAN,layer==0?.05f:.025f,t->{double a=t*Math.TAU;return new Vertex(Math.cos(a)*r,-.92,1.7+Math.sin(a)*r);});
            }
            for(int i=0;i<4;i++){
                double a=i*Math.TAU/4;
                curve(paths,i%2==0?PINK:WHITE,.035f,t->new Vertex(Math.cos(a)*t*(.6+expansion*2),-.88+Math.sin(t*Math.PI)*.1,1.7+Math.sin(a)*t*(.6+expansion*2)));
            }
        }else if(action.equals("aemeath/liberation_finale")){
            if(p<.25)triangle(paths,0,2.5,3.4,.8,CYAN,.025f);
            else {
                for(int layer=0;layer<3;layer++){
                    double offset=(layer-1)*.12;
                    curve(paths,layer==1?WHITE:CYAN,layer==1?.10f:.025f,t->new Vertex(offset,3.8-t*4.5,3.4));
                }
                double radius=.7+Math.clamp((p-.25)*4,0,2.3);
                for(int layer=0;layer<2;layer++){
                    double r=radius-layer*.12;
                    curve(paths,layer==0?PINK:WHITE,layer==0?.06f:.02f,t->{double a=t*Math.TAU;return new Vertex(Math.cos(a)*r,-.70,3.4+Math.sin(a)*r*.65);});
                }
                for(int spoke=0;spoke<4;spoke++){
                    double a=spoke*Math.TAU/4;
                    curve(paths,CYAN,.035f,t->new Vertex(Math.cos(a)*t*2.2,-.65+Math.sin(t*Math.PI)*.3,3.4+Math.sin(a)*t*2.2));
                }
            }
        }else if(action.equals("aemeath/heavy")||action.equals("aemeath/heavy_charge_2")){
            // Full normal-attack card5--8.4s: a pink fencing streak, crossing
            // warm-white arcs and a stellar flash. The cyan mech seal is separate.
            boolean charged=action.endsWith("charge_2");double tip=1.8+Math.clamp(p/.45,0,1)*(charged?2.6:1.8);
            for(int layer=-1;layer<=1;layer++){
                double offset=layer*.05;
                curve(paths,layer==0?WHITE:PINK,layer==0?.055f:.035f,t->new Vertex(offset,.15+offset,.4+t*(tip-.4)));
            }
            for(int side:new int[]{-1,1})curve(paths,side==1?PINK:0xffffe6bc,.04f,t->{
                double a=-2.4+t*4.8+p*.6;return new Vertex(Math.cos(a)*(charged?1.8:1.2),.15+side*Math.sin(a)*.34,tip*.65+Math.sin(a)*.65);
            });
            if(p>.18&&p<.75)stellarCross(paths,0,.15,tip,charged?.85:.5,PINK);
        }else if(action.contains("heavy")){
            boolean mech=action.contains("/mech_");int color=mech?CYAN:PINK;
            triangle(paths,0,.2,2.1,.65,color,.035f);
            for(int layer=0;layer<3;layer++){
                double offset=(layer-1)*.08;
                curve(paths,layer==1?WHITE:color,layer==1?.09f:.025f,t->new Vertex(offset,.2, .5+t*(1.6+p*1.8)));
            }
            if(p>.3)for(int ray=0;ray<4;ray++){
                double a=ray*Math.PI/2;
                curve(paths,color,.035f,t->new Vertex(Math.cos(a)*t*1.25,.2+Math.sin(a)*t*1.25,2.1+p));
            }
        }else if(action.equals("aemeath/dodge_counter")||action.equals("aemeath/mech_dodge_counter")){
            // Fencing riposte: a straight pink trace with a narrow stellar contact,
            // distinct from a normal slash or the charged triangular seal.
            boolean mech=action.contains("/mech_");int color=mech?CYAN:PINK;
            double tip=.7+Math.clamp((p-.12)/.35,0,1)*3.2;
            for(int layer=-1;layer<=1;layer++){
                double offset=layer*.055;
                curve(paths,layer==0?WHITE:color,layer==0?.032f:.055f,t->new Vertex(.25+offset,.12+offset,.45+t*(tip-.45)));
            }
            if(p>.22&&p<.72){
                double r=.38*Math.sin((p-.22)/.5*Math.PI);
                curve(paths,color,.035f,t->new Vertex(.25+(t*2-1)*r,.12,tip));
                curve(paths,WHITE,.024f,t->new Vertex(.25,.12+(t*2-1)*r,tip));
            }
        }else if(action.equals("aemeath/air")||action.equals("aemeath/mech_air")){
            boolean mech=action.contains("/mech_");int color=mech?CYAN:PINK;
            for(int layer=0;layer<2;layer++){
                double x=layer==0?-.08:.08;
                curve(paths,layer==0?color:WHITE,layer==0?.085f:.025f,t->new Vertex(x,2.8-t*3.5,1.1+t*.3));
            }
            // Normal card9--10.5s lands into a four-point stellar footprint.
            // Mech adapts the same descent plane in its cyan palette; no horizontal jab fallback.
            double radius=.35+Math.clamp(p*2,0,1)*1.45;
            for(int spoke=0;spoke<4;spoke++){
                double a=spoke*Math.TAU/4;
                curve(paths,color,.04f,t->new Vertex(Math.cos(a)*t*radius,-.82,1.4+Math.sin(a)*t*radius));
            }
            curve(paths,color,.02f,t->{double a=t*Math.TAU;return new Vertex(Math.cos(a)*radius*.65,-.85,1.4+Math.sin(a)*radius*.65);});
        }else if(action.equals("aemeath/skill")||action.startsWith("aemeath/wing_ensemble_")||action.equals("aemeath/skill_dawn")||action.equals("aemeath/intro_starlight")){
            boolean humanReturn=action.equals("aemeath/skill_dawn")||action.equals("aemeath/wing_ensemble_stage");
            if(humanReturn){
                // Official skill card11.5-13s: pink stellar floor bloom when returning to human.
                double bloom=Math.clamp((p-.24)/.5,0,1),radius=.65+bloom*1.75;
                for(int layer=0;layer<2;layer++){
                    double r=radius-layer*.14;
                    curve(paths,layer==0?PINK:WHITE,layer==0?.055f:.018f,t->{double a=t*Math.TAU;return new Vertex(Math.cos(a)*r,-.98,1.5+Math.sin(a)*r);});
                }
                for(int ray=0;ray<5;ray++){
                    double a=ray*Math.TAU/5+p*.3;
                    curve(paths,PINK,.035f,t->new Vertex(Math.cos(a)*t*radius,-.94+Math.sin(t*Math.PI)*.12,1.5+Math.sin(a)*t*radius));
                }
            }else for(int i=0;i<3;i++)triangle(paths,(i-1)*.55,.25,1.7+i*.3,.65,CYAN,.025f);
            for(int side:new int[]{-1,1})curve(paths,WHITE,.055f,t->new Vertex(side*t*1.5,.25+(t-.5)*.7,1.1+t*2.0));
        }else{
            int stage=action.endsWith("02")?2:action.endsWith("03")?3:action.endsWith("04")?4:1;
            boolean mech=action.contains("/mech_");int color=mech?CYAN:PINK;
            if(stage==1){
                for(int side:new int[]{-1,1})curve(paths,color,.045f,t->new Vertex(side*(1-t)*.7,.15+side*(1-t)*.35,.5+t*2.7));
            }else for(int layer=0;layer<3;layer++){
                double radius=1.6-layer*.12;
                curve(paths,layer==1?WHITE:color,layer==1?.075f:.025f,t->{double a=-2.1+p*4.5-(1-t)*1.7;return new Vertex(Math.cos(a)*radius,.1+Math.sin(a)*(stage==2?1.0:.25),1.25+Math.sin(a)*radius*(stage==2?.15:.6));});
            }
            if(stage==4)triangle(paths,0,.4,2.2,.75,color,.035f);
            if(mech)triangle(paths,0,.5,2.5,.4,CYAN,.02f);
        }
        return List.copyOf(paths);
    }
    static List<LightSurface> surfaces(String action,double progress){
        if(action.equals("aemeath/liberation_finale"))progress=finalePhase(progress);
        if(!supports(action))return List.of();double p=Math.clamp(progress,0,1);
        if(p<.10||p>.92)return List.of();var out=new ArrayList<LightSurface>();
        boolean mech=action.contains("/mech_");int color=mech?0x508feaff:0x50ff95e1;
        if(action.equals("aemeath/liberation")){
            if(p<.70)return List.of();
            double radius=.5+Math.clamp((p-.70)/.20,0,1)*2.0;
            // Eight narrow alternating points retain the seal silhouette without an opaque screen flash.
            var center=new Vertex(0,-.90,1.7);
            for(int i=0;i<16;i++){
                double a=i*Math.TAU/16,b=(i+1)*Math.TAU/16;
                double ra=i%2==0?radius:radius*.3,rb=i%2==0?radius*.3:radius;
                out.add(new LightSurface(List.of(center,new Vertex(Math.cos(a)*ra,-.90,1.7+Math.sin(a)*ra),new Vertex(Math.cos(b)*rb,-.90,1.7+Math.sin(b)*rb),center),i%2==0?0x48ff95e1:0x488feaff));
            }
        }else if(action.startsWith("aemeath/basic_")||action.startsWith("aemeath/mech_basic_")){
            int stage=action.endsWith("02")?2:action.endsWith("03")?3:action.endsWith("04")?4:1;
            if(stage==1){
                var tip=new Vertex(0,.2,3.1);var left=new Vertex(-.4,.2,.55);var right=new Vertex(.4,.2,.55);
                out.add(new LightSurface(List.of(left,tip,right,right),color));
            }else for(int segment=0;segment<12;segment++){
                double a0=-2.1+p*4.5-1.7+segment*1.7/12,a1=a0+1.7/12;
                out.add(new LightSurface(List.of(sweepPoint(a0,1.1,stage),sweepPoint(a0,1.65,stage),sweepPoint(a1,1.65,stage),sweepPoint(a1,1.1,stage)),color));
            }
        }else if(action.equals("aemeath/heavy")||action.equals("aemeath/heavy_charge_2")){
            double width=action.endsWith("charge_2")?.18:.11;
            out.add(new LightSurface(List.of(new Vertex(-width,.15,.5),new Vertex(0,.15,3.8),new Vertex(width,.15,.5),new Vertex(-width,.15,.5)),0x45ff95e1));
        }else if(action.equals("aemeath/air")||action.equals("aemeath/mech_air")){
            double radius=.35+Math.clamp(p*2,0,1)*1.45;
            Vertex center=new Vertex(0,-.84,1.4);
            for(int i=0;i<8;i++){
                double a=i*Math.TAU/8,b=(i+1)*Math.TAU/8;
                double ra=i%2==0?radius:radius*.2,rb=i%2==0?radius*.2:radius;
                out.add(new LightSurface(List.of(center,new Vertex(Math.cos(a)*ra,-.84,1.4+Math.sin(a)*ra),new Vertex(Math.cos(b)*rb,-.84,1.4+Math.sin(b)*rb),center),color));
            }
        }else if(action.contains("heavy")){
            var tip=new Vertex(0,.2,3.2);var center=new Vertex(0,.2,.6);
            for(int i=0;i<3;i++){
                double a=i*Math.TAU/3,b=(i+1)*Math.TAU/3;
                out.add(new LightSurface(List.of(center,new Vertex(Math.cos(a)*.55,.2+Math.sin(a)*.55,1.6),tip,new Vertex(Math.cos(b)*.55,.2+Math.sin(b)*.55,1.6)),color));
            }
        }else if(action.equals("aemeath/skill")||action.startsWith("aemeath/wing_ensemble_")||action.equals("aemeath/skill_dawn")||action.equals("aemeath/intro_starlight")||action.equals("aemeath/liberation_finale")){
            double z=action.endsWith("liberation_finale")?3.4:2.1,y=action.endsWith("liberation_finale")?1.7:.5;
            Vertex top=new Vertex(0,y+1,z),left=new Vertex(-.87,y-.5,z),right=new Vertex(.87,y-.5,z);
            if(action.equals("aemeath/skill_dawn")||action.equals("aemeath/wing_ensemble_stage")){
                double radius=.65+Math.clamp((p-.24)/.5,0,1)*1.75;
                for(int i=0;i<20;i++){
                    double a=i*Math.TAU/20,b=(i+1)*Math.TAU/20;
                    var center=new Vertex(0,-.97,1.5);
                    out.add(new LightSurface(List.of(center,new Vertex(Math.cos(a)*radius,-.97,1.5+Math.sin(a)*radius),new Vertex(Math.cos(b)*radius,-.97,1.5+Math.sin(b)*radius),center),0x28ff95e1));
                }
            }else out.add(new LightSurface(List.of(top,left,right,right),0x408feaff));
        }
        return List.copyOf(out);
    }
    private static double finalePhase(double progress){
        double p=Math.clamp(progress,0,1);
        double[] time={0,70d/110,.82,1},phase={0,.25,.60,1};
        for(int i=1;i<time.length;i++)if(p<=time[i])return phase[i-1]+(phase[i]-phase[i-1])*(p-time[i-1])/(time[i]-time[i-1]);
        return 1;
    }
    private static Vertex sweepPoint(double a,double radius,int stage){
        return new Vertex(Math.cos(a)*radius,.1+Math.sin(a)*radius*(stage==2?.7:.2),1.25+Math.sin(a)*radius*(stage==2?.1:.6));
    }
    private static void stellarCross(List<LightPath> out,double x,double y,double z,double radius,int color){
        for(int spoke=0;spoke<4;spoke++){
            double a=spoke*Math.TAU/4;
            curve(out,spoke%2==0?color:WHITE,.035f,t->new Vertex(x+Math.cos(a)*t*radius,y+Math.sin(a)*t*radius,z));
        }
    }
    private static void triangle(List<LightPath> out,double x,double y,double z,double radius,int color,float width){
        var points=new ArrayList<Vertex>();for(int i=0;i<=3;i++){
            double a=Math.PI/2+i*Math.TAU/3;points.add(new Vertex(x+Math.cos(a)*radius,y+Math.sin(a)*radius,z));
        }out.add(new LightPath(List.copyOf(points),color,width));
    }
    private static void curve(List<LightPath> out,int color,float width,DoubleFunction<Vertex> fn){
        var points=new ArrayList<Vertex>();for(int i=0;i<=24;i++)points.add(fn.apply(i/24d));out.add(new LightPath(List.copyOf(points),color,width));
    }
}
