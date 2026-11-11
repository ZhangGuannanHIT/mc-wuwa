package cn.zgnhit.mcwuwa.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Action-specific silhouettes from the archived Normal Attack/Forte demonstrations.
 * Distances and normalized contact timing are MC adaptation, not measured original frames. */
final class CartethyiaPilotVfx {
    private static final int ICE=0xff8feaff, VIOLET=0xffa9a4ff, WHITE=0xfff0fbff;
    private static final Set<String> MOVES=Set.of("basic_01","basic_02","basic_03","basic_04","dodge_counter","fleurdelys_counter","heavy","intro","skill",
        "air","air_one_sword","air_two_swords","air_three_swords","fleurdelys_basic_01",
        "fleurdelys_basic_02","fleurdelys_basic_03","fleurdelys_basic_04","fleurdelys_basic_05",
        "fleurdelys_uppercut","fleurdelys_air_01","upward_cut","fleurdelys_air_02","fleurdelys_air_03",
        "fleurdelys_heavy","fleurdelys_heavy_enhanced","fleurdelys_enhanced_heavy","liberation_finale",
        "fleurdelys_skill","fleurdelys_skill_followup","liberation","fleurdelys_enter","fleurdelys_intro");
    private CartethyiaPilotVfx(){}
    static boolean supports(String action){return action.startsWith("cartethyia/")&&MOVES.contains(action.substring(11));}

    static Sample sample(String action,double progress,double u){
        // Finale ground particles have their own terrain-clipped path in CombatEffects.
        if(action.equals("cartethyia/liberation_finale"))return null;
        if(action.equals("cartethyia/skill")&&progress>.15&&progress<.68&&u<.4){
            double angle=u/.4*Math.TAU;
            return new Sample(Math.cos(angle)*.95,-.7+progress*2,1.3+Math.sin(angle)*.65,WHITE,.35f,Kind.STONE);
        }
        var paths=ribbons(action,progress);
        if(paths==null||paths.isEmpty())return new Sample(0,0,0,WHITE,0,Kind.NONE);
        double at=Math.clamp(u,0,.999999)*paths.size();
        var path=paths.get((int)at);double t=at-(int)at;
        var point=path.points().get(Math.min(path.points().size()-1,(int)(t*path.points().size())));
        return new Sample(point.side(),point.up(),point.forward(),path.color(),.28f,Kind.DUST);
    }

    /** Null leaves actions not yet observed on their existing presentation. */
    static List<LightPath> ribbons(String action,double progress){
        if(!supports(action))return null;
        String move=action.substring("cartethyia/".length());
        double p=Math.clamp(progress,0,1);
        var out=new ArrayList<LightPath>();
        switch(move){
            case "fleurdelys_intro" -> {
                if(p>.12&&p<.45)for(int side:new int[]{-1,1}){
                    int direction=side;
                    curve(out,ICE,.035f,t->{double a=-1.3+t*2.6;return new Vertex(direction*(.5+Math.cos(a)*.65),.25+Math.sin(a)*.9,1.35);});
                    line(out,WHITE,.025f,side*1.1,.4,1.35,0,.25,2.3);
                }
                if(p>.4&&p<.76){
                    double end=2+(p-.4)*7;
                    line(out,ICE,.075f,0,.2,.6,0,.2,end);
                    line(out,WHITE,.025f,-.28,.2,end-.5,.28,.2,end-.5);
                }
            }
            case "basic_01" -> slash(out,p,false,.95,-.65,.7);
            case "basic_02" -> slash(out,p,true,1.25,-.35,.85);
            case "basic_03","dodge_counter" -> {
                // Side apertures emit converging sword rays; no ground halo.
                if(p>.18&&p<.76)for(int side:new int[]{-1,1}){
                    diamond(out,side*1.35,.15,1.15,.42,VIOLET);
                    line(out,ICE,.035f,side*1.35,.15,1.15,0,.15,2.7);
                }
                if(p>.32&&p<.70)diamond(out,0,.15,2.7,.52,WHITE);
            }
            case "fleurdelys_counter" -> {
                if(p>.16&&p<.88){
                    for(int side:new int[]{-1,1}){
                        diamond(out,side*1.25,.22,1.4,.34,VIOLET);
                        line(out,ICE,.045f,side*1.25,.22,1.4,0,.25,3.3);
                    }
                    slash(out,p,false,1.45,-.2,.45);
                }
            }
            case "basic_04" -> {
                if(p>.28&&p<.78){
                    // The continuous comparison shows a volume, not just a flat floor glyph.
                    prism(out,0,1.85,1.05,-.8,1.35);
                    floorDiamond(out,0,2.1,1.15);
                    sword(out,0,2.1,2.7-(p-.28)*5.1,1.9);
                }
            }
            case "skill" -> {
                if(p>.12&&p<.68){
                    prism(out,0,1.65,.95,-.8,.3+p*1.5);
                    diamond(out,0,.45,1.2,.48,WHITE);
                    curve(out,VIOLET,.055f,t->{double a=-1.4+t*2.8;return new Vertex(Math.sin(a)*1.1,-.65+t*2.1,1.1+Math.cos(a)*.35);});
                    line(out,ICE,.035f,0,-.75,1.2,0,1.4,1.2);
                }
            }
            case "heavy","intro" -> {
                // Left-hand summoned sphere (appreciation 168-175s), not a melee sword halo.
                if(p>.12&&p<.78){
                    double r=.55+Math.min(.2,p*.35);
                    for(int axis=0;axis<3;axis++){
                        int plane=axis;
                        curve(out,plane==0?WHITE:ICE,.025f,t->{double a=t*Math.TAU+p*2;
                            double x=Math.cos(a)*r,y=Math.sin(a)*r;
                            return switch(plane){case 0->new Vertex(x,y,1.8);case 1->new Vertex(x,0,1.8+y);default->new Vertex(0,x,1.8+y);};});
                    }
                    diamond(out,0,0,1.8,r*.7,VIOLET);
                    if(p>.42)for(int i=0;i<4;i++){
                        double a=i*Math.TAU/4,spread=(p-.42)*3;
                        line(out,ICE,.025f,Math.cos(a)*r,Math.sin(a)*r,1.8,
                            Math.cos(a)*(r+spread),Math.sin(a)*(r+spread),1.8);
                    }
                }
            }
            case "air", "air_one_sword", "air_two_swords", "air_three_swords" -> {
                int count=move.endsWith("three_swords")?3:move.endsWith("two_swords")?2:1;
                if(p>.18&&p<.76){
                    floorDiamond(out,0,1.1,1.2);
                    if(count==3&&p>=.4){
                        // Three recalled shadows become a single larger descending sword.
                        sword(out,0,1.1,2.8-(p-.4)*5,3.4);
                    }else for(int i=0;i<count;i++){
                        double convergence=count==3?Math.max(0,1-(p-.18)/.22):1;
                        sword(out,(i-(count-1)*.5)*.5*convergence,1.1,2.5-(p-.18)*4.2,1.8);
                    }
                }
            }
            case "fleurdelys_basic_01" -> slash(out,p,false,1.9,-.15,.6);
            case "fleurdelys_basic_02" -> slash(out,p,true,2.0,-.1,1.05);
            case "fleurdelys_basic_03" -> {
                if(p>.18&&p<.85)for(int i=0;i<4;i++){
                    double x=(i-1.5)*.25;
                    line(out,i%2==0?ICE:WHITE,.04f,x,-.1,.6,x,-.1,2.7);
                }
            }
            case "fleurdelys_basic_04" -> {
                // Four round gates and converging fan of blades, appreciation 287-295s.
                if(p>.16&&p<.86)for(int i=0;i<4;i++){
                    double x=(i-1.5)*.85,y=i==0||i==3?.8:1.35;
                    curve(out,VIOLET,.035f,t->{double a=t*Math.TAU;return new Vertex(x+Math.cos(a)*.3,y+Math.sin(a)*.3,.5);});
                    if(p>.28)line(out,i%2==0?ICE:WHITE,.045f,x,y,.5,-x*.45,-.15,3.25);
                }
            }
            case "fleurdelys_basic_05" -> {
                if(p>.26&&p<.8){floorDiamond(out,0,2,1.5);sword(out,0,2,3.4-(p-.26)*5.7,2.8);}
            }
            case "fleurdelys_uppercut", "upward_cut" -> {
                if(p>.15&&p<.75)curve(out,ICE,.065f,t->{double a=-1.4+t*2.8;return new Vertex(.15,Math.sin(a)*1.6,1+Math.cos(a)*1.3);});
            }
            case "fleurdelys_air_01" -> {
                if(p>.15&&p<.75){
                    int side=p<.45?1:-1;
                    curve(out,ICE,.055f,t->new Vertex(side*(t-.5)*2.4,(t-.5)*1.5,1.25+Math.sin(t*Math.PI)*.3));
                }
            }
            case "fleurdelys_air_02" -> {
                if(p>.18&&p<.78)for(int side:new int[]{-1,1})
                    line(out,side<0?VIOLET:ICE,.06f,side*1.8,-1,1.25,-side*1.8,1.25,1.25);
            }
            case "fleurdelys_air_03" -> {
                if(p>.18&&p<.72){
                    line(out,ICE,.07f,.4,1.4,.45,0,-.9,1.3);
                    line(out,WHITE,.035f,.15,1.0,.6,.65,.95,.65);
                    if(p>.5)floorDiamond(out,0,1.3,1.15);
                }
            }
            case "fleurdelys_skill" -> {
                if(p>.16&&p<.82){
                    if(p<.46){
                        sword(out,0,1.3,1.3-(p-.16)*3,1.65);
                        floorDiamond(out,0,1.3,.4+(p-.16)*3);
                    }else{
                        double q=(p-.46)/.36;
                        line(out,VIOLET,.085f,0,.2,.8,0,.2,1.6+q*3);
                        line(out,WHITE,.035f,-.4,.2,1.2+q*3,.4,.2,1.2+q*3);
                    }
                }
            }
            case "fleurdelys_skill_followup" -> {
                if(p>.15&&p<.8){
                    diamond(out,-.55,.15,.7,.3,WHITE);
                    if(p>.3)sword(out,0,1.65,2.8-(p-.3)*5,2.4);
                    // The persistent gravity field is owned by its existing tracked entity.
                }
            }
            case "liberation", "fleurdelys_enter" -> {
                if(p>.16&&p<.82){
                    line(out,WHITE,.06f,0,-.8,0,0,2.5,0);
                    for(int i=0;i<3;i++){
                        double phase=i*Math.TAU/3;
                        curve(out,ICE,.025f,t->{double a=t*Math.PI*2+phase+p*3;return new Vertex(Math.cos(a)*.65,-.75+t*2.8,Math.sin(a)*.65);});
                    }
                }
            }
            case "fleurdelys_heavy" -> {
                slash(out,p,false,2.2,-.15,.6);
                if(p>.18&&p<.68)for(int side:new int[]{-1,1})
                    line(out,VIOLET,.04f,side*.7,.2,-.9,side*.25,.1,1.9);
            }
            case "fleurdelys_heavy_enhanced", "fleurdelys_enhanced_heavy" -> {
                if(p>.14&&p<.8){
                    // Bow limbs, taut string, then a sword-arrow travelling forwards.
                    curve(out,VIOLET,.045f,t->new Vertex(0,-.85+t*1.8,1.25+Math.sin(t*Math.PI)*.65));
                    line(out,WHITE,.02f,0,-.85,1.25,0,.05,.75);
                    line(out,WHITE,.02f,0,.95,1.25,0,.05,.75);
                    double tip=1.5+Math.clamp((p-.3)*12,0,4.5);
                    line(out,ICE,.065f,0,.05,1.3,0,.05,tip);
                    line(out,WHITE,.035f,-.22,.05,tip-.45,0,.05,tip);
                    line(out,WHITE,.035f,.22,.05,tip-.45,0,.05,tip);
                }
            }
            case "liberation_finale" -> {
                // The actual giant sword is held by the hand socket; this only traces its downstroke.
                if(p>=.20&&p<=.30){
                    double q=(p-.20)/.10;
                    line(out,WHITE,.085f,0,2.8-q*3.3,.3+q*1.9,0,1.7-q*3.0,.8+q*2.3);
                    line(out,VIOLET,.045f,.15,2.8-q*3.3,.3+q*1.9,.15,1.7-q*3.0,.8+q*2.3);
                }
            }
            default -> {return null;}
        }
        return List.copyOf(out);
    }
    private static void slash(List<LightPath> out,double p,boolean reverse,double radius,double y,double slope){
        if(p<.15||p>.76)return;
        double travel=Math.clamp((p-.15)/.61,0,1),head=-2.2+travel*4.4;
        for(int band=0;band<2;band++){
            double r=radius+band*.045;
            curve(out,band==0?ICE:WHITE,band==0?.06f:.018f,t->{
                double a=head-.8+t*.8,x=Math.cos(a)*r;
                return new Vertex(reverse?-x:x,y+Math.sin(a)*slope,1.1+Math.sin(a)*r*.5);
            });
        }
    }
    private static void diamond(List<LightPath> out,double x,double y,double z,double radius,int color){
        curve(out,color,.025f,t->{double a=t*4;int k=Math.min(3,(int)a);double f=a-k;
            double[] xs={0,radius,0,-radius,0},ys={radius,0,-radius,0,radius};
            return new Vertex(x+xs[k]*(1-f)+xs[k+1]*f,y+ys[k]*(1-f)+ys[k+1]*f,z);});
    }
    private static void floorDiamond(List<LightPath> out,double x,double z,double radius){
        curve(out,VIOLET,.035f,t->{double a=t*4;int k=Math.min(3,(int)a);double f=a-k;
            double[] xs={0,radius,0,-radius,0},zs={radius,0,-radius,0,radius};
            return new Vertex(x+xs[k]*(1-f)+xs[k+1]*f,-.8,z+zs[k]*(1-f)+zs[k+1]*f);});
    }
    private static void prism(List<LightPath> out,double x,double z,double half,double bottom,double top){
        for(double y:new double[]{bottom,top})curve(out,VIOLET,.018f,t->{
            double a=t*4;int k=Math.min(3,(int)a);double f=a-k;
            double[] xs={-half,half,half,-half,-half},zs={-half,-half,half,half,-half};
            return new Vertex(x+xs[k]*(1-f)+xs[k+1]*f,y,z+zs[k]*(1-f)+zs[k+1]*f);
        });
        for(int side:new int[]{-1,1})for(int depth:new int[]{-1,1})
            line(out,ICE,.02f,x+side*half,bottom,z+depth*half,x+side*half,top,z+depth*half);
    }
    static void sword(List<LightPath> out,double x,double z,double top,double length){
        // The archived airborne close-up has a luminous blade perimeter, not a
        // pair of perpendicular rods. Keep the outline open in its centre so
        // several converging shadows do not become an opaque vertical slab.
        double guard=top-length*.20,tip=top-length,half=length*.075;
        polygon(out,WHITE,.018f,new Vertex[]{
            new Vertex(x,top,z),new Vertex(x+half*.32,top-length*.04,z),
            new Vertex(x+half*.32,guard,z),new Vertex(x+half,guard-length*.09,z),
            new Vertex(x+half*.7,tip+length*.17,z),new Vertex(x,tip,z),
            new Vertex(x-half*.7,tip+length*.17,z),new Vertex(x-half,guard-length*.09,z),
            new Vertex(x-half*.32,guard,z),new Vertex(x-half*.32,top-length*.04,z),
            new Vertex(x,top,z)});
        line(out,ICE,.022f,x,guard,z-.025,x,tip+length*.06,z-.025);
        polygon(out,VIOLET,.022f,new Vertex[]{
            new Vertex(x-half*1.9,guard+length*.035,z),new Vertex(x-half,guard-length*.018,z),
            new Vertex(x,guard,z),new Vertex(x+half,guard-length*.018,z),
            new Vertex(x+half*1.9,guard+length*.035,z)});
    }
    private static void polygon(List<LightPath> out,int color,float width,Vertex[] corners){
        curve(out,color,width,t->{
            double at=t*(corners.length-1);int i=Math.min(corners.length-2,(int)at);double f=at-i;
            var a=corners[i];var b=corners[i+1];
            return new Vertex(a.side()+(b.side()-a.side())*f,a.up()+(b.up()-a.up())*f,a.forward()+(b.forward()-a.forward())*f);
        });
    }
    private static void line(List<LightPath> out,int color,float width,double x,double y,double z,double a,double b,double c){
        curve(out,color,width,t->new Vertex(x+(a-x)*t,y+(b-y)*t,z+(c-z)*t));
    }
    private static void curve(List<LightPath> out,int color,float width,DoubleFunction<Vertex> f){
        var points=new ArrayList<Vertex>();for(int i=0;i<=24;i++)points.add(f.apply(i/24d));
        out.add(new LightPath(points,color,width));
    }
}
