package cn.zgnhit.mcwuwa.client;

import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Crimson strokes, rose petals and separate Hecate commands observed in the
 * complete official showcase and skill cards. Geometry is visual only. */
final class PhrolovaPilotVfx {
    private static final int RED=0xffed386a,PINK=0xffff83bc,WHITE=0xffffdfed,PURPLE=0xffb196ff,GOLD=0xffffc78b;
    private PhrolovaPilotVfx(){}
    static List<LightSurface> surfaces(String action,double progress){
        boolean opening=action.equals("phrolova/liberation")||action.equals("phrolova/conduction_enter");
        boolean closing=action.equals("phrolova/liberation_curtain")||action.equals("phrolova/hecate_finale");
        if(action.startsWith("phrolova/basic_"))return normalSlashSurfaces(action,progress);
        if(action.equals("phrolova/composition_finale")||action.equals("phrolova/terminal_composition"))
            return compositionSurfaces(progress);
        if(action.startsWith("phrolova/hecate_")&&!closing)return commandSlashSurfaces(action,progress);
        if(!opening&&!closing)return List.of();
        double p=Math.clamp(progress,0,1);
        if(p<.12||p>.94)return List.of();
        // Full official showcase58-62s: connected six-petal red aperture.
        // Fit to the existing MC cast clock; do not reproduce its edited camera.
        double grow=Math.clamp((p-.12)/.28,0,1),fade=Math.clamp((.94-p)/.20,0,1);
        double radius=(closing?2.4:1.6)*(.2+.8*grow),y=closing?.1:.6,z=closing?2:.5;
        int alpha=(int)(125*fade);
        var out=new ArrayList<LightSurface>();
        for(int petal=0;petal<6;petal++){
            double angle=petal*Math.TAU/6;
            for(int band=0;band<8;band++){
                double a=band/8d,b=(band+1)/8d;
                out.add(new LightSurface(List.of(petalPoint(angle,a,-1,radius,y,z),petalPoint(angle,a,1,radius,y,z),
                    petalPoint(angle,b,1,radius,y,z),petalPoint(angle,b,-1,radius,y,z)),
                    alpha<<24|(petal%2==0?0xed386a:0xb51e53)));
            }
        }
        return List.copyOf(out);
    }
    /** Official liberation card2,0--17s: thrusts, broad cross-cuts and
     * rising blades. These remain anchored to the companion's own renderer. */
    private static List<LightSurface> commandSlashSurfaces(String action,double progress){
        double p=Math.clamp(progress,0,1);if(p<.10||p>.92)return List.of();
        var out=new ArrayList<LightSurface>();
        switch(action.substring(9)){
            case "hecate_basic","hecate_basic_01"->{
                double tip=1+Math.min(1,p/.25)*2.8;
                strip(out,0x65ed386a,.16,t->new Vertex(.2,.1,.3+(tip-.3)*t),t->new Vertex(1,0,0));
                strip(out,0x55ffb2cf,.10,t->new Vertex(.2,.1,.3+(tip-.3)*t),t->new Vertex(0,1,0));
            }
            case "hecate_basic_02"->{slashSurface(out,p,2,0,.55,false);if(p>.5)slashSurface(out,p,1.9,.25,-.5,true);}
            case "hecate_strings","hecate_brass"->{slashSurface(out,p,2.6,-.2,.15,false);slashSurface(out,p,2.25,.15,.15,true);}
            case "hecate_winds","hecate_fortissimo"->strip(out,0x65ed386a,.20,t->{
                double angle=-1.2+t*2.8;return new Vertex(.15,Math.sin(angle)*2,1.2+Math.cos(angle)*1.5);
            },t->{double angle=-1.2+t*2.8;return new Vertex(0,Math.sin(angle),Math.cos(angle));});
            case "hecate_color"->{slashSurface(out,p,2.2,.05,.8,false);slashSurface(out,p,2.2,.05,-.8,true);}
            default->{ }
        }
        return List.copyOf(out);
    }
    private static void strip(List<LightSurface> out,int color,double width,DoubleFunction<Vertex> center,DoubleFunction<Vertex> across){
        for(int i=0;i<16;i++){
            double a=i/16d,b=(i+1)/16d;
            out.add(new LightSurface(List.of(bandPoint(center,across,a,-1,width),bandPoint(center,across,a,1,width),
                bandPoint(center,across,b,1,width),bandPoint(center,across,b,-1,width)),color));
        }
    }
    private static Vertex bandPoint(DoubleFunction<Vertex> center,DoubleFunction<Vertex> across,double t,int edge,double width){
        var c=center.apply(t);var n=across.apply(t);double w=edge*width*Math.sin(t*Math.PI);
        return new Vertex(c.side()+n.side()*w,c.up()+n.up()*w,c.forward()+n.forward()*w);
    }
    /** Official enhanced-heavy card1: converging red cuts, then a blossom.
     * Keep the existing two cut endpoints and the bloom's opening threshold. */
    private static List<LightSurface> compositionSurfaces(double progress){
        double p=Math.clamp(progress,0,1);if(p<.10||p>.92)return List.of();
        var out=new ArrayList<LightSurface>();
        for(int sign:new int[]{-1,1})for(int i=0;i<12;i++){
            double a=i/12d,b=(i+1)/12d;
            out.add(new LightSurface(List.of(compositionCut(sign,a,-1),compositionCut(sign,a,1),
                compositionCut(sign,b,1),compositionCut(sign,b,-1)),0x65ed386a));
        }
        if(p>.35){
            double r=1.2*(.55+.45*Math.min(1,(p-.1)/.35));
            for(int petal=0;petal<5;petal++)for(int band=0;band<8;band++){
                double angle=petal*Math.TAU/5+.15,a=band/8d,b=(band+1)/8d;
                out.add(new LightSurface(List.of(compositionPetal(angle,a,-1,r),compositionPetal(angle,a,1,r),
                    compositionPetal(angle,b,1,r),compositionPetal(angle,b,-1,r)),0x55db225a));
            }
        }
        return List.copyOf(out);
    }
    private static Vertex compositionCut(int sign,double t,int edge){
        double width=Math.sin(t*Math.PI)*.13*edge;
        return new Vertex(sign*(1.6-2.4*t)+width*.5,-.5+1.4*t+width, .5+2.2*t);
    }
    private static Vertex compositionPetal(double angle,double t,int edge,double radius){
        double radial=Math.sin(t*Math.PI)*radius;
        double lateral=Math.sin(t*Math.TAU)*Math.sin(t*Math.PI)*radius*.30;
        double width=edge*Math.sin(t*Math.PI)*radius*.15;
        return new Vertex(Math.cos(angle)*radial-Math.sin(angle)*(lateral+width),
            .15+Math.sin(angle)*radial+Math.cos(angle)*(lateral+width),2.3+.28*Math.sin(t*Math.PI));
    }
    /** Official normal card0,0--4.5s: filled crimson crescents rather than
     * isolated filaments. Reuse the existing arc trajectory and cast clock. */
    private static List<LightSurface> normalSlashSurfaces(String action,double progress){
        double p=Math.clamp(progress,0,1);
        if(p<.10||p>.92)return List.of();
        var out=new ArrayList<LightSurface>();
        switch(action){
            case "phrolova/basic_01"->slashSurface(out,p,1.25,.05,.25,false);
            case "phrolova/basic_02"->{slashSurface(out,p,1.65,0,.10,false);slashSurface(out,p,1.45,.15,.10,true);}
            case "phrolova/basic_03"->slashSurface(out,p,1.5,-.35,.35,false);
            default->{ }
        }
        return List.copyOf(out);
    }
    private static void slashSurface(List<LightSurface> out,double p,double radius,double y,double slope,boolean reverse){
        double head=-2.2+Math.clamp((p-.1)/.7,0,1)*4.4;
        double visibility=Math.min(1,Math.min((p-.10)/.10,(.92-p)/.12));
        for(int layer=0;layer<2;layer++)for(int i=0;i<12;i++){
            double a=i/12d,b=(i+1)/12d;
            int alpha=(int)((layer==0?78:116)*visibility);
            out.add(new LightSurface(List.of(slashPoint(head,a,-1,radius,y,slope,reverse,layer),
                slashPoint(head,a,1,radius,y,slope,reverse,layer),
                slashPoint(head,b,1,radius,y,slope,reverse,layer),
                slashPoint(head,b,-1,radius,y,slope,reverse,layer)),
                alpha<<24|(layer==0?0xd82454:0xff91bc)));
        }
    }
    private static Vertex slashPoint(double head,double t,int edge,double radius,double y,double slope,boolean reverse,int layer){
        double angle=head-.9+t*.9;
        double width=Math.sin(t*Math.PI)*(.16+radius*.025)*(layer==0?1:.24);
        double r=radius+edge*width;
        return new Vertex(Math.cos(angle)*r*(reverse?-1:1),
            y+Math.sin(angle)*slope+layer*.008,1.1+Math.sin(angle)*r*.4);
    }
    private static Vertex petalPoint(double angle,double t,double side,double radius,double y,double z){
        double r=radius*t,w=Math.sin(t*Math.PI)*radius*.25*side;
        return new Vertex(Math.cos(angle)*r-Math.sin(angle)*w,
            y+Math.sin(angle)*r+Math.cos(angle)*w,z+Math.sin(t*Math.PI)*.22);
    }
    static Sample sample(String action,double progress,double u){
        var paths=ribbons(action,progress);if(paths==null)return null;
        if(paths.isEmpty())return new Sample(0,0,0,WHITE,0,Kind.NONE);
        double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);
        var point=path.points().get(Math.min(path.points().size()-1,(int)((at-(int)at)*path.points().size())));
        return new Sample(point.side(),point.up(),point.forward(),path.color(),.26f,Kind.DUST);
    }
    static List<LightPath> ribbons(String action,double progress){
        if(!action.startsWith("phrolova/"))return null;
        String move=action.substring(9);double p=Math.clamp(progress,0,1);
        var out=new ArrayList<LightPath>();
        if(move.equals("conducting_throne")){
            // Seat and raised petal back remain behind the player's own skin.
            curve(out,GOLD,.018f,t->{double a=t*Math.TAU;return new Vertex(Math.cos(a)*.68,-.35,Math.sin(a)*.40-.18);});
            curve(out,PINK,.045f,t->{double a=-2.2+t*4.4;return new Vertex(Math.sin(a)*.95,.2+Math.cos(a)*1.0,-.48);});
            for(int i=0;i<5;i++){
                double angle=-1.05+i*.525,x=Math.sin(angle)*1.15,y=.25+Math.cos(angle)*1.05;
                curve(out,i==2?GOLD:RED,.075f,t->{double swell=Math.sin(t*Math.PI);
                    return new Vertex(x*t+Math.signum(x)*swell*.22,-.32+(y+.32)*t,-.20-.35*swell);});
            }
            return List.copyOf(out);
        }
        if(p<.10||p>.92)return List.of();
        switch(move){
            case "basic_01"->{arc(out,p,1.25,.05,.25,false);rose(out,.2,.12,1.45,.25,p);}
            case "basic_02"->{arc(out,p,1.65,.0,.10,false);arc(out,p,1.45,.15,.1,true);}
            case "basic_03","dodge_counter"->{
                arc(out,p,1.5,-.35,.35,false);ray(out,WHITE,.035f,-.6,-.5,.5,.45,.25,2.7);
            }
            case "heavy"->{arc(out,p,1.65,.05,.7,true);rose(out,0,.05,1.7,.42,p);}
            case "composition_finale","terminal_composition"->{
                // Complete publisher film36--39s: converging cut followed by
                // the flowering impact. Keep the crossing and bloom separate.
                for(int sign:new int[]{-1,1})ray(out,sign<0?RED:PINK,.045f,sign*1.6,-.5,.5,-sign*.8,.9,2.7);
                if(p>.35)bloom(out,0,.15,2.3,1.2,p);
            }
            case "movement_death","death_movement"->{
                // Film53--56s: grounded petals expand beneath the victim.
                for(int i=0;i<6;i++){
                    double angle=i*Math.TAU/6;
                    curve(out,i%2==0?RED:PINK,.035f,t->new Vertex(Math.cos(angle)*Math.sin(t*Math.PI)*2.0,-.82+Math.sin(t*Math.PI)*.25,1.8+Math.sin(angle)*Math.sin(t*Math.PI)*2.0));
                }
                rose(out,0,.1,1.8,.6,p);
            }
            case "intro"->{
                arc(out,p,1.7,.1,.5,false);
                rose(out,0,.2,1.8,.25,p);
                for(int sign:new int[]{-1,1})curve(out,PINK,.025f,t->new Vertex(sign*.65+Math.cos(t*Math.TAU)*.15,.2+Math.sin(t*Math.TAU)*.15,1.8+sign*.2));
            }
            case "intro_eternal"->{
                // Official intro card: vertical crimson impact then opening
                // petals. The conductor does not perform Hecate's strike.
                double opening=Math.clamp((p-5d/24)/.35,0,1);
                if(p<.48){
                    ray(out,RED,.12f,0,3.2,2,0,-.7,2);
                    ray(out,WHITE,.025f,0,2.9,2.02,0,-.7,2.02);
                }
                for(int i=0;i<6;i++){
                    double angle=i*Math.TAU/6,radius=.35+opening*2.0;
                    curve(out,i%2==0?RED:PINK,.055f,t->{
                        double r=Math.sin(t*Math.PI)*radius;
                        return new Vertex(Math.cos(angle)*r,-.65+Math.sin(t*Math.PI)*1.35,2+Math.sin(angle)*r);
                    });
                }
                rose(out,0,-.4,2,.3+opening*.65,p);
            }
            case "skill"->{
                ray(out,WHITE,.055f,-1.5,-.35,.5,1.5,.55,2.6);
                ray(out,RED,.035f,-1.5,-.25,.5,1.5,.65,2.6);rose(out,0,.1,2,.38,p);
            }
            case "undying_reverie","eternal_dream"->{bloom(out,0,.2,2,1.65,p);ray(out,WHITE,.055f,-1.5,-.3,.5,1.5,.6,2.7);}
            case "air"->{ray(out,PINK,.06f,0,2.2,.5,0,-.8,1.4);rose(out,0,-.65,1.4,.75,p);}
            case "liberation","conduction_enter"->{
                rose(out,0,.6,.5,1.6,p);ray(out,WHITE,.05f,-2.5,.6,.5,2.5,.6,.5);
            }
            case "immortal_suite"->{
                bloom(out,0,.1,2,1.9,p);arc(out,p,2.3,-.45,.2,false);
            }
            case "liberation_curtain","hecate_finale"->{bloom(out,0,.1,2,2.4,p);ray(out,WHITE,.065f,0,3.2,2,0,-.8,2);}
            case "hecate_basic","hecate_basic_01"->{
                double tip=1.0+Math.min(1,p/.25)*2.8;
                ray(out,WHITE,.055f,.2,.1,.3,.2,.1,tip);
                ray(out,RED,.035f,-.05,.0,.2,-.05,.0,tip-.2);
            }
            case "hecate_basic_02"->{arc(out,p,2.0,.0,.55,false);if(p>.5)arc(out,p,1.9,.25,-.5,true);}
            case "hecate_strings","hecate_brass"->{arc(out,p,2.6,-.2,.15,false);arc(out,p,2.25,.15,.15,true);}
            case "hecate_winds","hecate_fortissimo"->{
                curve(out,RED,.085f,t->{double a=-1.2+t*2.8;return new Vertex(.15,Math.sin(a)*2.0,1.2+Math.cos(a)*1.5);});
                ray(out,WHITE,.045f,.15,2.1,1.2,.15,-.7,2.6);
            }
            case "hecate_color"->{arc(out,p,2.2,.05,.8,false);arc(out,p,2.2,.05,-.8,true);rose(out,0,.15,2,.7,p);}
            default->{return null;}
        }
        return List.copyOf(out);
    }
    private static void rose(List<LightPath> out,double x,double y,double z,double radius,double p){
        for(int i=0;i<6;i++){
            double angle=i*Math.TAU/6;
            curve(out,i%2==0?RED:PINK,.035f,t->{double a=angle+(t-.5)*.9,r=Math.sin(t*Math.PI)*radius;
                return new Vertex(x+Math.cos(a)*r,y+Math.sin(a)*r,z+Math.sin(t*Math.PI)*.14);});
        }
    }
    private static void bloom(List<LightPath> out,double x,double y,double z,double radius,double p){
        double opening=.55+.45*Math.min(1,(p-.1)/.35);
        for(int petal=0;petal<5;petal++)for(int layer=0;layer<2;layer++){
            double angle=petal*Math.TAU/5+.15,scale=radius*opening*(layer==0?1:.9),depth=layer==0?0:-.025;
            curve(out,layer==0?RED:PINK,layer==0?.105f:.025f,t->{
                double radial=Math.sin(t*Math.PI)*scale,lateral=Math.sin(t*Math.TAU)*Math.sin(t*Math.PI)*scale*.30;
                return new Vertex(x+Math.cos(angle)*radial-Math.sin(angle)*lateral,
                    y+Math.sin(angle)*radial+Math.cos(angle)*lateral,z+.28*Math.sin(t*Math.PI)+depth);
            });
        }
    }
    private static void arc(List<LightPath> out,double p,double radius,double y,double slope,boolean reverse){
        double head=-2.2+Math.clamp((p-.1)/.7,0,1)*4.4;
        for(int layer=0;layer<2;layer++){
            int c=layer==0?RED:WHITE;float width=layer==0?.07f:.018f;
            curve(out,c,width,t->{double a=head-.9+t*.9;
                return new Vertex(Math.cos(a)*radius*(reverse?-1:1),y+Math.sin(a)*slope,1.1+Math.sin(a)*radius*.4);});
        }
    }
    private static void ray(List<LightPath> out,int color,float width,double x,double y,double z,double a,double b,double c){
        curve(out,color,width,t->new Vertex(x+(a-x)*t,y+(b-y)*t,z+(c-z)*t));
    }
    private static void curve(List<LightPath> out,int color,float width,DoubleFunction<Vertex> sample){
        var points=new ArrayList<Vertex>();for(int i=0;i<=24;i++)points.add(sample.apply(i/24d));out.add(new LightPath(points,color,width));
    }
}
