package cn.zgnhit.mcwuwa.client;
import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Complete official panels1/3/4/9: distinct future cuts, airborne crescents and skill falls.
 * Dimensions use MC units; contact clocks and damage remain in the existing catalogue. */
final class HiyukiFrostFormVfx {
    static final Set<String> ACTIONS=Set.of("hiyuki/future_basic_01","hiyuki/future_basic_02","hiyuki/future_basic_03","hiyuki/future_basic_04","hiyuki/future_basic_05",
        "hiyuki/future_air_01","hiyuki/future_air_02","hiyuki/future_air","hiyuki/skill","hiyuki/skill_jade","hiyuki/skill_falling","hiyuki/intro","hiyuki/future_dodge_counter");
    private static final int WHITE=0xfff4fcff,BLUE=0xffa9e0ff,PINK=0xffffbfe2;
    private HiyukiFrostFormVfx(){}
    static boolean supports(String action){return ACTIONS.contains(action);}
    static double delta(String action,double p){var clock=RemainingCastContactClocks.ALL.get(action);double nearest=Double.POSITIVE_INFINITY;
        for(int hit:clock.hits())if(Math.abs(p*clock.duration()-hit)<Math.abs(nearest))nearest=p*clock.duration()-hit;
        return nearest;
    }
    static List<LightPath> ribbons(String action,double p){
        if(!supports(action)||p<=0||p>=1)return List.of();double d=delta(action,p);if(d<=-2||d>=3)return List.of();
        var paths=new ArrayList<LightPath>();String move=action.substring(action.indexOf('/')+1);
        boolean counter=move.equals("future_dodge_counter");
        int stage=counter?2:move.startsWith("future_basic_")?Integer.parseInt(move.substring(move.length()-1)):0;
        double strokePhase=counter?(p<=.2?p/.2*3/14:p<=.8?3/14d+(p-.2)/.6*7/14:10/14d+(p-.8)/.2*4/14):p;
        if(stage>0){
            double sign=stage==2||stage==4?-1:1;
            for(int layer=0;layer<3;layer++){
                double radius=1.8+layer*.13;
                curve(paths,layer==1?WHITE:layer==0?BLUE:PINK,layer==1?.045f:.018f,t->{
                    double angle=sign*(-2.2+t*4.4+strokePhase*.9);
                    return stage==5?new Vertex(Math.sin(angle)*radius*.6,-.65+t*2.6,1.3+Math.cos(angle)*.7)
                        :new Vertex(Math.sin(angle)*radius,.15+Math.cos(angle)*(stage==2?.9:.22),1.2+Math.cos(angle)*radius*.6);
                });
            }
            if(stage==3||stage==5)for(int i=0;i<7;i++){
                double x=(i-3)*.5,z=1.5+Math.abs(i-3)*.18,height=.8+(3-Math.abs(i-3))*.28;
                paths.add(new LightPath(List.of(new Vertex(x-.18,-.85,z),new Vertex(x+.12,height-.85,z+.18),new Vertex(x+.25,-.85,z)),i%2==0?BLUE:WHITE,.026f));
            }
        }else if(move.equals("future_air_01")||move.equals("future_air_02")){
            int sign=move.endsWith("01")?1:-1;
            for(int i=0;i<3;i++){
                double offset=(i-1)*.12;
                curve(paths,i==1?WHITE:PINK,i==1?.045f:.02f,t->{double a=-2.1+t*4.2;return new Vertex(sign*Math.sin(a)*(1.65+offset),.2+Math.cos(a)*.85,1.35+Math.cos(a)*.5);});
            }
        }else if(move.equals("future_air")||move.equals("skill_falling")){
            for(int i=-1;i<=1;i++){
                double x=i*.13;
                curve(paths,i==0?WHITE:BLUE,i==0?.05f:.02f,t->new Vertex(x,2.5-t*3.3,1.2+t*.5));
            }
            ring(paths,BLUE,.035f,1.7,-.85,1.7);
        }else if(move.equals("skill")){
            // Present-self E vaults through a vertical cut into a low pink sweep.
            curve(paths,WHITE,.055f,t->new Vertex(.15,2.4-t*3.2,1.1+t*.7));
            for(int i=0;i<3;i++){
                double r=1.75+i*.16;curve(paths,i==1?WHITE:PINK,.03f,t->{double a=-2.7+t*5.4;return new Vertex(Math.sin(a)*r,-.7+Math.cos(a)*.15,1.1+Math.cos(a)*r*.65);});
            }
        }else{
            // Jade is a low crossing cut; intro opens with crossed blades and a waist halo.
            for(int sign:new int[]{-1,1})curve(paths,sign==1?WHITE:BLUE,.04f,t->new Vertex(-1.8+t*3.6,sign*(-.65+t*1.35),1.5+Math.sin(t*Math.PI)*.25));
            if(move.equals("intro"))ring(paths,BLUE,.02f,1.05,.15,0);
        }
        return FiveNormalAttackVfx.contactTrails(paths,d);
    }
    static List<LightSurface> surfaces(String action,double p){
        var paths=ribbons(action,p);if(paths.isEmpty())return List.of();
        double d=delta(action,p);int alpha=(int)Math.round(38*Math.clamp(1-Math.max(0,d)/3,0,1));
        int color=(alpha<<24)|(BLUE&0xffffff);var faces=new ArrayList<LightSurface>();
        String move=action.substring(action.indexOf('/')+1);
        boolean crossed=move.equals("skill_jade")||move.equals("intro");
        if(crossed){
            for(int i=0;i<2;i++)faces.addAll(SlashSurfaceGeometry.verticalWidth(paths.get(i),.09,color));
        }else {
            int first=move.equals("skill")?1:0;
            faces.addAll(SlashSurfaceGeometry.between(paths.get(first),paths.get(first+2),color));
            if(move.equals("future_basic_03")||move.equals("future_basic_05")){
                // Preserve the existing seven ice-blade outlines and fill each
                // with a faint faceted core; no extra hit or opaque wall.
                for(int i=3;i<paths.size();i++){
                    var points=paths.get(i).points();
                    faces.add(new LightSurface(List.of(points.getFirst(),points.get(points.size()/2),points.getLast(),points.getLast()),color));
                }
            }
        }
        return List.copyOf(faces);
    }
    static Sample sample(String action,double p,double u){
        var paths=ribbons(action,p);if(paths.isEmpty())return new Sample(0,0,0,WHITE,.25f,Kind.NONE);
        double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);var v=path.points().get(Math.min(path.points().size()-1,(int)((at-(int)at)*path.points().size())));
        return new Sample(v.side(),v.up(),v.forward(),path.color(),.28f,Kind.DUST);
    }
    private static void ring(List<LightPath> out,int color,float width,double r,double y,double z){curve(out,color,width,t->{double a=t*Math.TAU;return new Vertex(Math.cos(a)*r,y,z+Math.sin(a)*r);});}
    private static void curve(List<LightPath> out,int color,float width,DoubleFunction<Vertex> f){var points=new ArrayList<Vertex>();for(int i=0;i<=24;i++)points.add(f.apply(i/24d));out.add(new LightPath(points,color,width));}
}
