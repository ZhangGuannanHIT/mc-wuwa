package cn.zgnhit.mcwuwa.client;

import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Source-observed instrument dials, projection panels and converging prisms; MC clocks. */
public final class MornyePilotVfx {
    private static final int BLUE=0xffa3bfff,PINK=0xffffb7ef,WHITE=0xffeffbff;
    private MornyePilotVfx(){}
    /** Pitch only directed observation rays; emitter rings and screens stay upright. */
    public static List<LightPath> aimedPaths(String action,List<LightPath> paths,float pitch){
        if(!(action.startsWith("mornye/observation_basic_")||action.equals("mornye/observation_dodge_counter"))||pitch==0)return paths;
        double angle=Math.toRadians(pitch),c=Math.cos(angle),s=Math.sin(angle);
        return paths.stream().map(path->{
            var start=path.points().getFirst();var end=path.points().getLast();
            if(end.forward()-start.forward()<1.5)return path;
            return new LightPath(path.points().stream().map(v->{
                double y=v.up()-start.up(),z=v.forward()-start.forward();
                return new Vertex(v.side(),start.up()+y*c-z*s,start.forward()+y*s+z*c);
            }).toList(),path.color(),path.width());
        }).toList();
    }
    public static Sample aimedSample(String action,double progress,double u,float pitch,Sample fallback){
        if(!(action.startsWith("mornye/observation_basic_")||action.equals("mornye/observation_dodge_counter")))return fallback;
        var paths=aimedPaths(action,NewRoleVfxProfiles.ribbons(action,progress),pitch);
        if(paths.isEmpty())return new Sample(0,0,0,WHITE,.25f,Kind.NONE);
        double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);
        var v=path.points().get(Math.min(path.points().size()-1,(int)((at-(int)at)*path.points().size())));
        return new Sample(v.side(),v.up(),v.forward(),path.color(),.25f,Kind.DUST);
    }
    static boolean supports(String a){return a.startsWith("mornye/basic_")||a.startsWith("mornye/observation_basic_")
        ||Set.of("mornye/heavy","mornye/heavy_potential","mornye/heavy_inversion","mornye/air","mornye/dodge_counter","mornye/observation_dodge_counter","mornye/skill","mornye/skill_optimal","mornye/liberation","mornye/intro","mornye/observation_field","mornye/observation_aura").contains(a);}
    static Sample sample(String action,double p,double u){
        if(!supports(action))return null;var paths=ribbons(action,p);
        if(paths.isEmpty())return new Sample(0,0,0,WHITE,0,Kind.NONE);
        double at=Math.clamp(u,0,.999999)*paths.size();var line=paths.get((int)at);
        var v=line.points().get(Math.min(line.points().size()-1,(int)((at-(int)at)*line.points().size())));
        return new Sample(v.side(),v.up(),v.forward(),line.color(),.3f,Kind.DUST);
    }
    static List<LightPath> ribbons(String action,double progress){
        if(!supports(action))return null;double p=Math.clamp(progress,0,1);
        if(action.equals("mornye/liberation"))return liberationRibbons(liberationPhase(p));
        if(action.equals("mornye/observation_field"))return observationField(p);
        if(action.equals("mornye/observation_aura"))return observationInstruments(p);
        if(p<.08||p>.94)return List.of();var out=new ArrayList<LightPath>();
        if(action.equals("mornye/liberation")||action.equals("mornye/skill_optimal")||action.equals("mornye/heavy_inversion")){
            // The central volume is a connected diamond, with two separate control panels.
            double r=.45+Math.sin(p*Math.PI)*.8;
            for(int i=0;i<4;i++){
                double a=i*Math.TAU/4;var corner=new Vertex(Math.cos(a)*r,.7+Math.sin(a)*r,2.3);
                out.add(new LightPath(List.of(new Vertex(0,.7,2.3-r),corner,new Vertex(0,.7,2.3+r)),i%2==0?BLUE:WHITE,.025f));
            }
            for(int side:new int[]{-1,1}){
                double x=side*1.2;
                curve(out,BLUE,.022f,t->{double a=t*Math.TAU;return new Vertex(x+Math.cos(a)*.48,.35+Math.sin(a)*.48,1.0);});
                out.add(new LightPath(List.of(new Vertex(x-.7,-.25,1),new Vertex(x+.7,-.25,1),new Vertex(x+.7,.95,1),new Vertex(x-.7,.95,1),new Vertex(x-.7,-.25,1)),BLUE,.018f));
            }
            if(p>.28)for(int side:new int[]{-1,1})curve(out,PINK,.035f,t->new Vertex(side*(1-t)*1.4,1.8-t*1.3,1+t*1.5));
        }else if(action.equals("mornye/dodge_counter")){
            // BV1WH6FBzEJP105--107s: remote emitters sweep laterally while
            // Mornye pivots and draws her hand to her chin.
            double sweep=(p-.5)*2.4;
            for(int side:new int[]{-1,1}){
                double x=side*.85;
                curve(out,BLUE,.022f,t->{double a=t*Math.TAU;return new Vertex(x+Math.cos(a)*.28,.45+Math.sin(a)*.28,.6);});
                if(p>.2&&p<.8)curve(out,side==1?WHITE:BLUE,.055f,t->new Vertex(x+t*Math.sin(sweep)*3.2,.45-t*.15,.6+t*Math.cos(sweep)*3.2));
            }
        }else if(action.equals("mornye/observation_dodge_counter")){
            // BV1hArSBfEPy110.1--111.1s: evade cue -> floating volley.
            // Distinct recovery/emitter beats, not the normal A2 panel loop.
            int emitter=Math.clamp((int)Math.floor((p-.16)/.2),0,3);
            for(int i=0;i<4;i++){
                double x=i%2==0?-1.1:1.1,y=i<2?1.05:-.15;
                double radius=i==emitter?.23:.14;
                curve(out,i==emitter?WHITE:BLUE,i==emitter?.022f:.009f,t->{double a=t*Math.TAU;return new Vertex(x+Math.cos(a)*radius,y+Math.sin(a)*radius,.8);});
                if(i==emitter&&p>=.16&&p<.86)
                    curve(out,i%2==0?BLUE:PINK,.045f,t->new Vertex(x*(1-t),y*(1-t)+.35*t,.8+t*3.0));
            }
        }else if(action.startsWith("mornye/observation_basic_")){
            // BV1WH6FBzEJP150--163: crossed emitters, a touched optical
            // panel, then a hand sweep dismissing it. These are three beats.
            int stage=Integer.parseInt(action.substring(action.length()-2));
            if(stage<3||p<.52){
                double x=.72,y=.55,z=.42;
                out.add(new LightPath(List.of(new Vertex(x-.42,y-.3,z),new Vertex(x+.42,y-.3,z+.1),new Vertex(x+.42,y+.3,z+.1),new Vertex(x-.42,y+.3,z),new Vertex(x-.42,y-.3,z)),BLUE,.012f));
                for(int row=0;row<3;row++){
                    double h=y-.15+row*.15;
                    curve(out,WHITE,.006f,t->new Vertex(x-.25+t*.5,h,z+.05));
                }
            }
            if(p>.20&&p<.76){
                for(int i=0;i<4;i++){
                    double x=(i%2==0?-1:1)*(stage==3?1.45:1.15);
                    double y=i<2?1.35:-.25;
                    curve(out,i%2==0?BLUE:PINK,.027f,t->new Vertex(x*(1-t),y*(1-t)-.4*t,.7+t*2.3));
                }
            }
            if(stage==3){
                double radius=.45+p*.6;
                curve(out,BLUE,.018f,t->{double a=t*Math.TAU;return new Vertex(Math.cos(a)*radius,-.7,1.2+Math.sin(a)*radius);});
            }
        }else if(action.equals("mornye/basic_04")){
            for(int i=0;i<3;i++){
                double x=(i-1)*1.2,y=i==1?1.5:.7;
                curve(out,BLUE,.025f,t->{double a=t*Math.TAU;return new Vertex(x+Math.cos(a)*.25,y+Math.sin(a)*.25,1.6);});
                curve(out,i==1?WHITE:PINK,.035f,t->new Vertex(x*(1-t),y*(1-t)-.25*t,1.6+t*1.6));
            }
        }else if(action.equals("mornye/air")){
            // BV16QkVBVE2N79--83.5s: one remote blade sweeps a vertical
            // forward-down crescent, not the paired rising conversion beams.
            for(int layer=0;layer<3;layer++){double r=1.65+layer*.065;
                curve(out,layer==1?WHITE:BLUE,.035f,t->{double a=t*Math.PI;return new Vertex(.15,1.05+Math.cos(a)*r,.5+Math.sin(a)*r);});
            }
            for(int i=0;i<5;i++){double side=(i-2)*.3;
                curve(out,BLUE,.012f,t->new Vertex(side*t,-.65+.15*Math.sin(t*Math.PI),1.2+t*1.4));
            }
        }else if(action.equals("mornye/heavy_potential")||action.equals("mornye/intro")){
            for(int side:new int[]{-1,1})curve(out,side==1?BLUE:PINK,.04f,t->{double a=-1+p*3+t*1.5;return new Vertex(side*Math.cos(a)*.8,.4+Math.sin(a)*1.3,.5+t*2);});
            if(p>.28)curve(out,WHITE,.05f,t->new Vertex(0,2.3-t*3.2,2.1));
        }else{
            boolean hover=action.contains("observation");int stage=action.endsWith("02")?2:action.endsWith("03")?3:1;
            for(int side:new int[]{-1,1}){
                double x=side*(hover?1.05:.65);
                curve(out,side==1?BLUE:PINK,.025f,t->{double a=t*Math.TAU+p*2;return new Vertex(x+Math.cos(a)*.35,.3+Math.sin(a)*.35,1.0);});
                curve(out,WHITE,.035f,t->new Vertex(x*(1-t),.3+(stage-2)*t*.5,1+t*2.1));
            }
            if(action.equals("mornye/heavy")||action.equals("mornye/skill")){
                curve(out,BLUE,.025f,t->{double a=t*Math.TAU;return new Vertex(Math.cos(a)*1.3,.35+Math.sin(a)*1.3,1.8);});
            }
        }
        return List.copyOf(out);
    }
    /** BV1WH6FBzEJP126-140s: a wide optical frame, not a solid gyroscope at the feet. */
    private static List<LightPath> observationField(double p){
        var out=new ArrayList<LightPath>();
        for(double y:new double[]{-.9,1.35}){
            curve(out,BLUE,.014f,t->{double a=t*Math.TAU;return new Vertex(Math.cos(a)*5.5,y,Math.sin(a)*5.5);});
        }
        for(int i=0;i<8;i++){
            double a=i*Math.TAU/8,x=Math.cos(a)*5.5,z=Math.sin(a)*5.5;
            out.add(new LightPath(List.of(new Vertex(x,.1,z),new Vertex(x-.17,.45,z),new Vertex(x,.8,z),new Vertex(x+.17,.45,z),new Vertex(x,.1,z)),WHITE,.018f));
            out.add(new LightPath(List.of(new Vertex(x,-.9,z),new Vertex(x,1.35,z)),BLUE,.008f));
        }
        return List.copyOf(out);
    }
    private static List<LightPath> observationInstruments(double p){
        var out=new ArrayList<LightPath>();
        for(int side:new int[]{-1,1}){
            double x=side*1.15,y=.35+Math.sin(p*Math.TAU)*.025;
            curve(out,BLUE,.018f,t->{double a=t*Math.TAU;return new Vertex(x+Math.cos(a)*.24,y+Math.sin(a)*.24,.12);});
            out.add(new LightPath(List.of(new Vertex(x-.38,y-.38,.12),new Vertex(x+.38,y+.38,.12)),WHITE,.02f));
        }
        return List.copyOf(out);
    }
    static List<LightSurface> surfaces(String action,double progress){
        if(action.equals("mornye/liberation"))return liberationSurfaces(liberationPhase(progress));
        if(!supports(action)||progress<.08||progress>.94)return List.of();var out=new ArrayList<LightSurface>();
        if(action.equals("mornye/liberation")||action.equals("mornye/skill_optimal")||action.equals("mornye/heavy_inversion")){
            double r=.45+Math.sin(progress*Math.PI)*.8;
            var near=new Vertex(0,.7,2.3-r);var far=new Vertex(0,.7,2.3+r);
            for(int i=0;i<4;i++){
                double a=i*Math.TAU/4,b=(i+1)*Math.TAU/4;
                var v=new Vertex(Math.cos(a)*r,.7+Math.sin(a)*r,2.3);var w=new Vertex(Math.cos(b)*r,.7+Math.sin(b)*r,2.3);
                out.add(new LightSurface(List.of(near,v,w,near),0x22a3bfff));out.add(new LightSurface(List.of(far,w,v,far),0x22a3bfff));
            }
            for(int side:new int[]{-1,1}){
                double x=side*1.2;
                out.add(new LightSurface(List.of(new Vertex(x-.7,-.25,1),new Vertex(x+.7,-.25,1),new Vertex(x+.7,.95,1),new Vertex(x-.7,.95,1)),0x18a3bfff));
            }
        }
        return List.copyOf(out);
    }
    /** Shared phase mapping keeps actual weapon projections and light volumes together.
     * Full card sequence: hand gesture, flying instruments, prism, discharge at4.5s. */
    public static double liberationPhase(double progress){
        double p=Math.clamp(progress,0,1);
        double[] time={0,1.5/5.5,3.5/5.5,4.5/5.5,1},phase={0,.08,.15,.25,1};
        for(int i=1;i<time.length;i++)if(p<=time[i])return phase[i-1]+(phase[i]-phase[i-1])*(p-time[i-1])/(time[i]-time[i-1]);
        return 1;
    }
    /** Hand seal -> instrument convergence -> prism discharge -> expanding aftermath. */
    private static List<LightPath> liberationRibbons(double p){
        if(p<.025||p>.96)return List.of();
        var out=new ArrayList<LightPath>();
        if(p<.17){
            double r=.12+.13*Math.sin(Math.PI*p/.17);
            curve(out,BLUE,.02f,t->{double a=t*Math.TAU;return new Vertex(-.37+Math.cos(a)*r,.52+Math.sin(a)*r,.42);});
        }
        if(p>.08&&p<.32){
            double converge=Math.clamp((p-.08)/.17,0,1);
            for(int i=0;i<3;i++){
                double a=i*Math.TAU/3, radius=(1-converge)*1.75+.12;
                curve(out,WHITE,.025f,t->new Vertex(Math.cos(a)*radius*(1-t),.65+Math.sin(a)*radius*(1-t),.5+t*2.0));
            }
        }
        if(p>.15&&p<.65){
            double r=p<.25?(.2+(p-.15)*9):Math.max(.1,1.1-(p-.25)*2.5);
            for(int i=0;i<4;i++){
                double a=i*Math.TAU/4;
                out.add(new LightPath(List.of(new Vertex(0,.65,2.5-r),new Vertex(Math.cos(a)*r,.65+Math.sin(a)*r,2.5),new Vertex(0,.65,2.5+r)),BLUE,.025f));
            }
        }
        if(p>=.25){
            double burst=(p-.25)/.71,r=.35+burst*3.2;
            curve(out,BLUE,(float)(.045*(1-burst)+.01),t->{double a=t*Math.TAU;return new Vertex(Math.cos(a)*r,.3+Math.sin(a)*r*.65,2.5);});
            if(p<.49)for(int i=0;i<3;i++){
                double a=i*Math.TAU/3;
                curve(out,WHITE,.04f,t->new Vertex(Math.cos(a)*.12,.65+Math.sin(a)*.12,2.5+t*2.4));
            }
        }
        return List.copyOf(out);
    }
    private static List<LightSurface> liberationSurfaces(double p){
        if(p<.15||p>.65)return List.of();
        var out=new ArrayList<LightSurface>();
        double r=p<.25?(.2+(p-.15)*9):Math.max(.1,1.1-(p-.25)*2.5);
        var near=new Vertex(0,.65,2.5-r);var far=new Vertex(0,.65,2.5+r);
        int alpha=p<.25?38:(int)(38*(.65-p)/.4),color=(alpha<<24)|0xa3bfff;
        for(int i=0;i<4;i++){
            double a=i*Math.TAU/4,b=(i+1)*Math.TAU/4;
            var v=new Vertex(Math.cos(a)*r,.65+Math.sin(a)*r,2.5);
            var w=new Vertex(Math.cos(b)*r,.65+Math.sin(b)*r,2.5);
            out.add(new LightSurface(List.of(near,v,w,near),color));
            out.add(new LightSurface(List.of(far,w,v,far),color));
        }
        return List.copyOf(out);
    }
    private static void curve(List<LightPath> out,int color,float width,DoubleFunction<Vertex> f){
        var points=new ArrayList<Vertex>();for(int i=0;i<=24;i++)points.add(f.apply(i/24d));out.add(new LightPath(List.copyOf(points),color,width));
    }
}
