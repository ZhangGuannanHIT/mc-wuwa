package cn.zgnhit.mcwuwa.client;

import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Reviewed brush crescents and pistol/claw contacts, separate from damage. */
final class InkInfernalVfx {
    private static final int JADE=0xff89e7db,PINK=0xffffb8dc,WHITE=0xfffff4ed,
        BLUE=0xff85cfff,VIOLET=0xffbb8aff,RED=0xffff9bb7;
    private InkInfernalVfx(){}
    static boolean supports(String action){return InkInfernalContactClocks.ALL.containsKey(action);}
    static double delta(String action,double p){
        var clock=InkInfernalContactClocks.ALL.get(action);double d=Double.POSITIVE_INFINITY;
        if(clock!=null)for(int hit:clock.hits())if(Math.abs(p*clock.duration()-hit)<Math.abs(d))d=p*clock.duration()-hit;
        return d;
    }
    static Sample sample(String a,double p,double u){
        var paths=ribbons(a,p);if(paths.isEmpty())return new Sample(0,0,0,WHITE,.2f,Kind.NONE);
        if(a.endsWith("air_rain"))return new Sample((u-.5)*.72,.3-u*3.4,.4,BLUE,.26f,Kind.DUST);
        double n=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)n);
        var v=path.points().get(Math.min(24,(int)((n-(int)n)*25)));
        return new Sample(v.side(),v.up(),v.forward(),path.color(),.26f,Kind.DUST);
    }
    static List<LightPath> ribbons(String a,double p){
        if(!supports(a)||p<=0||p>=1)return List.of();
        if(a.equals("galbrena/liberation"))return TideFireReleaseVfx.ribbons(a,p);
        var out=new ArrayList<LightPath>();
        if(a.equals("zhezhi/liberation")){
            if(p<.18||p>.94)return List.of();
            for(int side:new int[]{-1,1})curve(out,side<0?JADE:PINK,.065f,t->{
                double angle=-2.3+t*4.6+p;
                return new Vertex(Math.sin(angle)*2.8,.3+Math.sin(t*Math.PI)*2.1,Math.cos(angle)*1.5+.7);
            });
            crane(out,0,1.0,1.1,1.6,JADE);return List.copyOf(out);
        }
        double d=delta(a,p);if(d<=-2||d>=3)return List.of();
        if(a.startsWith("galbrena/demon_basic_"))return FiveNormalAttackVfx.contactTrails(demonCombo(a,p),d);
        if(a.startsWith("zhezhi/")){
            boolean teleport=a.contains("genius")||a.contains("maestro"),air=a.contains("air");
            for(int layer=0;layer<3;layer++){
                double r=1.8+layer*.12;
                curve(out,layer==1?PINK:JADE,.045f,t->{double angle=-2.2+t*4.4+(p-.5)*1.2;
                    return new Vertex(Math.sin(angle)*r,.3+Math.cos(angle)*(air?1.25:.4),1.0+Math.cos(angle)*r*.5);});
            }
            if(teleport)crane(out,0,.7,1.8,1.1,WHITE);
            else for(int side:new int[]{-1,1})curve(out,WHITE,.024f,t->new Vertex(side*(.3+t*.6),.5+Math.sin(t*Math.PI)*.25,.6+t*2.8));
        }else{
            boolean demon=a.contains("demon_"),air=a.contains("air");
            if(air){
                for(int i=0;i<5;i++){
                    double x=(i-2)*.35;
                    curve(out,i%2==0?BLUE:VIOLET,.045f,t->new Vertex(x,.3-t*3.4,.4));
                }
            }else if(demon){
                // Three cyan claw edges cross the front, with a violet impact star.
                for(int i=0;i<3;i++){
                    double height=i*.23;
                    curve(out,i==1?WHITE:BLUE,.055f,t->{double angle=-2+t*4;return new Vertex(Math.sin(angle)*2.1,.1+height+Math.cos(angle)*.6,1.4+Math.cos(angle)*.7);});
                }
            }else for(int side:new int[]{-1,1}){
                curve(out,side<0?BLUE:RED,.045f,t->new Vertex(side*.38,.1,.6+t*4));
            }
            for(int side:new int[]{-1,1}){
                curve(out,VIOLET,.04f,t->new Vertex(side*(-.65+t*1.3),.2,3.5));
                curve(out,WHITE,.025f,t->new Vertex(0,-.45+t*1.3,3.5));
            }
        }
        return FiveNormalAttackVfx.contactTrails(out,d);
    }
    static List<LightSurface> surfaces(String a,double p){
        if(!supports(a)||p<=0||p>=1)return List.of();
        if(a.equals("galbrena/liberation"))return TideFireReleaseVfx.surfaces(a,p);
        var out=new ArrayList<LightSurface>();
        if(a.equals("zhezhi/liberation")){
            if(p<.18||p>.94)return List.of();
            for(int side:new int[]{-1,1})wing(out,side,1.5,1.0,1.1,0x6589e7db);
            return List.copyOf(out);
        }
        double contactDelta=delta(a,p);
        if(contactDelta<=-2||contactDelta>=3)return List.of();
        if(a.startsWith("galbrena/demon_basic_")){
            var paths=ribbons(a,p);
            int alpha=(int)Math.round(48*Math.clamp(1-Math.max(0,contactDelta)/3,0,1));
            for(int i=0;i<paths.size();i+=2)
                out.addAll(SlashSurfaceGeometry.between(paths.get(i),paths.get(i+1),alpha<<24|(i%4==0?0x85cfff:0xff9bb7)));
            return List.copyOf(out);
        }
        if(a.startsWith("zhezhi/")){
            // Air strokes tilt with their actual bright edges; the previous
            // independent ground-plane fill visibly separated from those arcs.
            var paths=ribbons(a,p);if(paths.size()<3)return List.of();
            int color=((int)Math.round(40*Math.clamp(1-Math.max(0,contactDelta)/3,0,1))<<24)|0x89e7db;
            return SlashSurfaceGeometry.between(paths.get(0),paths.get(2),color);
        }
        return List.copyOf(out);
    }
    /** Archived forte card0.52--2.73s: diagonal opening, low returning sweeps,
     * crossed broad cuts and the final spread of flame wings. MC contact clocks
     * remain authoritative; these fitted contours are not original frame times. */
    private static List<LightPath> demonCombo(String action,double p){
        int stage=action.charAt(action.length()-1)-'0';
        var out=new ArrayList<LightPath>();
        if(stage==5){
            double spread=.65+.35*Math.clamp(p/.75,0,1);
            for(int side:new int[]{-1,1})for(int edge=0;edge<2;edge++){
                final int border=edge;
                curve(out,edge==0?VIOLET:RED,edge==0?.045f:.025f,t->{
                    double width=Math.sin(t*Math.PI)*(.55+.18*Math.sin(t*Math.PI*3));
                    return new Vertex(side*(.12+t*2.9)*spread,
                        .15+t*.55+(border==0?1:-.55)*width,1.6+t*.65);
                });
            }
        }else{
            int cuts=stage==4?2:1;
            for(int cut=0;cut<cuts;cut++)for(int edge=0;edge<2;edge++){
                final int stroke=cut,border=edge;
                curve(out,edge==0?BLUE:VIOLET,edge==0?.05f:.025f,t->{
                    double direction=stage==2||stroke==1?-1:1;
                    double angle=-2.3+t*4.6+(p-.5)*1.2;
                    double radius=(stage==4?2.45:2.05)-border*Math.sin(t*Math.PI)*.36;
                    double x=direction*Math.sin(angle)*radius;
                    double y=stage==1?.2+x*.42:stage==4?.15+x*(stroke==0?.26:-.26):-.55+Math.cos(angle)*.13;
                    return new Vertex(x,y,1.2+Math.cos(angle)*radius*.6);
                });
            }
        }
        return List.copyOf(out);
    }
    private static void wing(List<LightSurface> out,int side,double size,double y,double z,int color){
        out.add(new LightSurface(List.of(new Vertex(0,y,z),new Vertex(side*size*.45,y+.65,z-.1),new Vertex(side*size,y+.32,z-.5),new Vertex(side*size*.5,y-.03,z-.25)),color));
    }
    private static void crane(List<LightPath> out,double x,double y,double z,double size,int color){
        for(int side:new int[]{-1,1})curve(out,color,.045f,t->new Vertex(x+side*t*size,y+Math.sin(t*Math.PI)*.65,z-t*.45));
        curve(out,WHITE,.05f,t->new Vertex(x,y+Math.sin(t*Math.PI)*.55,z-.3+t*.8));
        curve(out,PINK,.025f,t->new Vertex(x,y+.3-t*.15,z+.5+t*.5));
    }
    private static void curve(List<LightPath> out,int color,float width,DoubleFunction<Vertex> fn){
        var v=new ArrayList<Vertex>();for(int i=0;i<=24;i++)v.add(fn.apply(i/24d));out.add(new LightPath(v,color,width));
    }
}
