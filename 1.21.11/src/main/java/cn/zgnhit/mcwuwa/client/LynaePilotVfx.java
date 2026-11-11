package cn.zgnhit.mcwuwa.client;

import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Cyan/pink paint sprays, skate strokes and rainbow ramp observed in six skill cards. */
final class LynaePilotVfx {
    private static final int CYAN=0xff68f4ee,PINK=0xffff8bdc,GOLD=0xffffe291,WHITE=0xfff0ffff;
    private LynaePilotVfx(){}
    static boolean supports(String a){return a.equals("lynae/intro")||a.startsWith("lynae/basic_")||a.startsWith("lynae/cruise_basic_")||a.startsWith("lynae/phantom_leap")||a.matches("lynae/inspiration_[123]")||Set.of("lynae/cruise_jump_bundle","lynae/heavy","lynae/air","lynae/cruise_air","lynae/cruise_air_heavy","lynae/rainbow_splash","lynae/visual_impact","lynae/skill","lynae/skill_additive","lynae/additive_mixing","lynae/liberation","lynae/toward_colorful_tomorrow","lynae/dodge_counter","lynae/cruise_dodge_counter").contains(a);}
    static Sample sample(String a,double p,double u){
        if(!supports(a))return null;var paths=ribbons(a,p);if(paths.isEmpty())return new Sample(0,0,0,WHITE,0,Kind.NONE);
        double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);var v=path.points().get(Math.min(path.points().size()-1,(int)((at-(int)at)*path.points().size())));
        return new Sample(v.side(),v.up(),v.forward(),path.color(),.32f,Kind.DUST);
    }
    static List<LightPath> ribbons(String a,double progress){
        if(a.equals("lynae/liberation"))progress=liberationPhase(progress);
        if(!supports(a))return null;double p=Math.clamp(progress,0,1);if(p<.1||p>.94)return List.of();var out=new ArrayList<LightPath>();
        boolean plunge=Set.of("lynae/cruise_air_heavy","lynae/rainbow_splash","lynae/visual_impact").contains(a);
        if(a.equals("lynae/intro")){
            double front=LynaeIntroMotion.forward(p);
            for(int lane=0;lane<3;lane++){
                double side=(lane-1)*.32;int color=lane==0?CYAN:lane==1?WHITE:PINK;
                curve(out,color,lane==1?.035f:.055f,t->new Vertex(side,-.58+Math.sin(t*Math.PI)*.12,front-1.8+t*2.4));
            }
            for(int lane=0;lane<2;lane++){
                double radius=.48+lane*.22;
                curve(out,lane==0?CYAN:GOLD,.025f,t->{double angle=t*Math.TAU+p*8;return new Vertex(Math.cos(angle)*radius,-.35+Math.sin(angle)*radius,front+.8);});
            }
        }else if(a.equals("lynae/cruise_jump_bundle")){
            // MC contact staging for the source-defined key-up finisher.
            // Three converging paint streams distinguish it from held gunfire.
            for(int lane=0;lane<3;lane++){
                double side=(lane-1)*.7;int color=lane==0?CYAN:lane==1?GOLD:PINK;
                curve(out,color,.055f,t->new Vertex(side*(1-t),.15+Math.sin(t*Math.PI)*.24,.55+t*2.1));
            }
            star(out,0,.15,2.65,.6,WHITE);
        }else if(a.startsWith("lynae/inspiration_")){
            // The held conversion ends in a forward, star-shaped paint burst.
            // MC density distinguishes the three server-selected flow tiers.
            int stage=a.charAt(a.length()-1)-'0';
            for(int i=0;i<stage*2;i++){
                double angle=i*Math.TAU/(stage*2),radius=.8+stage*.3;
                curve(out,i%2==0?CYAN:PINK,.045f,t->new Vertex(.25+Math.cos(angle)*t*radius,.2+Math.sin(angle)*t*radius,.6+t*2.4));
            }
            star(out,.25,.2,2.8,.4+stage*.15,GOLD);
        }else if(plunge){
            // Sweeping descending rainbow surface, not a vertical ambient cylinder.
            for(int lane=0;lane<4;lane++){
                double side=(lane-1.5)*.17;int color=new int[]{CYAN,WHITE,PINK,GOLD}[lane];
                curve(out,color,lane==1?.035f:.025f,t->ramp(side,t));
            }
            if(p>.48)splash(out,2.6,.5+(p-.48)*2.7);
        }else if(a.startsWith("lynae/phantom_leap")){
            int stage=a.endsWith("_2")?2:a.endsWith("_3")?3:1;
            for(int side:new int[]{-1,1})for(int lane=0;lane<2;lane++){
                int color=lane==0?CYAN:PINK;double trailSide=side*(.22+lane*.06);
                curve(out,color,lane==0?.06f:.025f,t->new Vertex(trailSide,-1+t*.6,-.15-t*(.8+stage*.3)));
            }
            // Three separated contact sparks, leaving the rising body readable.
            star(out,0,-.9,.1,.35+stage*.12,GOLD);
        }else if(a.endsWith("dodge_counter")){
            boolean cruise=a.contains("cruise_");
            for(int layer=0;layer<3;layer++){
                double r=1.5-layer*.12;
                curve(out,layer==0?CYAN:layer==1?WHITE:PINK,layer==1?.035f:.025f,t->{double angle=-2.0+p*4-(1-t)*1.7;return new Vertex(Math.cos(angle)*r,.1+Math.sin(angle)*.16,1.0+Math.sin(angle)*r);});
            }
            if(cruise)for(int side:new int[]{-1,1})curve(out,side<0?PINK:CYAN,.028f,t->new Vertex(side*.25,-.9,-.2-t*1.4));
        }else if(a.equals("lynae/toward_colorful_tomorrow")){
            if(p<.6){
                for(int side:new int[]{-1,1})curve(out,side<0?PINK:CYAN,.035f,t->{double angle=t*Math.TAU+p*4;return new Vertex(Math.cos(angle)*1.05,.25+Math.sin(angle)*.25,.4+Math.sin(angle)*1.05);});
                curve(out,WHITE,.045f,t->new Vertex(.25,.3+t*2.5,.65));
            }else{
                for(int side:new int[]{-1,1})curve(out,side<0?CYAN:PINK,.04f,t->new Vertex(.25+side*t*.25,.3,.65+t*3));
                star(out,.25,.3,3.5,.45,GOLD);
            }
        }else if(a.equals("lynae/liberation")){
            if(p<.35){for(int side:new int[]{-1,1})curve(out,side<0?PINK:CYAN,.045f,t->new Vertex(side*(.35+t*.7),.3+Math.sin(t*Math.PI)*.7,.5));}
            else splash(out,1.7,1.2+(p-.35)*2);
        }else if(a.equals("lynae/air")||a.equals("lynae/cruise_air")){
            for(int side:new int[]{-1,1})curve(out,side<0?PINK:CYAN,.055f,t->new Vertex(side*.12,2.2-3*t,.7+t));
            if(p>.45)splash(out,1.7,.8);
        }else{
            int stage=a.endsWith("02")?2:a.endsWith("03")?3:a.endsWith("04")?4:a.endsWith("05")?5:1;
            boolean skate=a.contains("cruise_");
            if(skate)for(int side:new int[]{-1,1})curve(out,side<0?PINK:CYAN,.05f,t->new Vertex(side*(.25+Math.sin(t*Math.PI)*.45),-.85,-.15-t*1.8));
            // Gunfire grows forward from the hand, with crossing paint fins at the impact.
            for(int layer=0;layer<2;layer++){
                double offset=layer*.065;
                curve(out,layer==0?CYAN:WHITE,layer==0?.065f:.025f,t->new Vertex(.25+offset,.25+Math.sin(t*Math.PI)*.05,.5+t*(1.8+stage*.12)));
            }
            if(stage>=3||a.contains("skill")||a.endsWith("heavy")){
                for(int side:new int[]{-1,1})for(int lane=0;lane<2;lane++){
                    int color=lane==0?PINK:CYAN;double spread=.9+stage*.07;double rise=lane==0?.65:-.45;
                    curve(out,color,lane==0?.05f:.025f,t->new Vertex(.25+side*t*spread,.2+t*rise,2.2+t*.25));
                }
            }
        }
        return List.copyOf(out);
    }
    static List<LightSurface> surfaces(String action,double progress){
        double castProgress=progress;
        if(action.equals("lynae/liberation"))progress=liberationPhase(progress);
        if(!supports(action))return List.of();double p=Math.clamp(progress,0,1);
        if(p<.10||p>.94)return List.of();var faces=new ArrayList<LightSurface>();
        boolean plunge=Set.of("lynae/cruise_air_heavy","lynae/rainbow_splash","lynae/visual_impact").contains(action);
        boolean release=action.equals("lynae/liberation");
        if(release&&castProgress>.55&&castProgress<.80){
            // The cinematic throws small paint canisters, not only light rays.
            // Closed voxel volumes remain visible from either side of the player.
            int[] colors={0xeeffb44c,0xee67eee9,0xeeff8bdc,0xeea0ef45};
            for(int i=0;i<4;i++){
                double a=i*Math.TAU/4+p*2.5,r=.75+p*.7;
                double x=Math.cos(a)*r,y=.65+Math.sin(a)*.65,z=.9;
                box(faces,x,y,z,.085,.22,.085,colors[i]);
                box(faces,x,y+.245,z,.06,.025,.06,0xeef0ffff);
            }
        }
        if(plunge){
            int[] colors={0x9f68f4ee,0x77f0ffff,0x9fff8bdc,0x8fffe291};
            // A connected arched surface. It has finite width and real curvature,
            // unlike four straight light columns; stage growth follows the cast.
            double end=Math.clamp((p-.1)/.5,.05,1);
            for(int lane=0;lane<4;lane++)for(int segment=0;segment<12;segment++){
                double t0=end*segment/12,t1=end*(segment+1)/12;
                double x0=(lane-2)*.20,x1=x0+.20;
                faces.add(new LightSurface(List.of(ramp(x0,t0),ramp(x0,t1),ramp(x1,t1),ramp(x1,t0)),colors[lane]));
            }
        }
        if((plunge&&p>.50)||(release&&p>.35)){
            double forward=plunge?2.6:1.7,radius=plunge?1.2:1.9;
            double burst=Math.clamp((p-(plunge?.50:.35))*5,0,1);radius*=.5+burst*.5;
            Vertex center=new Vertex(0,-.3,forward+.12);
            // Cyan/pink/gold toothed paint silhouette, placed behind the contact.
            // Flat fan triangles use a repeated fourth vertex for the quad stream.
            for(int layer=0;layer<2;layer++)for(int i=0;i<24;i++){
                double a0=i*Math.TAU/24,a1=(i+1)*Math.TAU/24;
                double r0=radius*(i%2==0?1:.62)*(layer==0?1:.72),r1=radius*((i+1)%2==0?1:.62)*(layer==0?1:.72);
                double z=forward+.14+layer*.035;
                var v0=new Vertex(Math.cos(a0)*r0,-.3+Math.sin(a0)*r0*.7,z);
                var v1=new Vertex(Math.cos(a1)*r1,-.3+Math.sin(a1)*r1*.7,z);
                int color=release&&i%4==0?0x8fa0ef45:layer==0?0x8fff80d7:0xa068f4ee;
                faces.add(new LightSurface(List.of(center,v0,v1,v1),color));
            }
        }
        return List.copyOf(faces);
    }
    private static double liberationPhase(double p){
        double[][] anchors={{0,0},{.60,.35},{.75,.65},{.90,.94},{1,1}};
        p=Math.clamp(p,0,1);
        for(int i=1;i<anchors.length;i++)if(p<=anchors[i][0])return anchors[i-1][1]+(anchors[i][1]-anchors[i-1][1])*(p-anchors[i-1][0])/(anchors[i][0]-anchors[i-1][0]);
        return 1;
    }
    private static void box(List<LightSurface> out,double x,double y,double z,double hx,double hy,double hz,int color){
        Vertex[] v={new Vertex(x-hx,y-hy,z-hz),new Vertex(x+hx,y-hy,z-hz),new Vertex(x+hx,y+hy,z-hz),new Vertex(x-hx,y+hy,z-hz),
            new Vertex(x-hx,y-hy,z+hz),new Vertex(x+hx,y-hy,z+hz),new Vertex(x+hx,y+hy,z+hz),new Vertex(x-hx,y+hy,z+hz)};
        for(int[] f:new int[][]{{0,1,2,3},{4,7,6,5},{0,4,5,1},{3,2,6,7},{0,3,7,4},{1,5,6,2}})
            out.add(new LightSurface(List.of(v[f[0]],v[f[1]],v[f[2]],v[f[3]]),color));
    }
    private static Vertex ramp(double side,double t){
        // ActionRibbonRenderer adds1.05 above the feet: the crown must cancel that.
        // Source60.8-61.4: the skates stand ON the bridge, never below it.
        return new Vertex(side,-1.10-1.7*Math.pow(2*t-1,2),-2.1+4.2*t);
    }
    private static void splash(List<LightPath> out,double forward,double radius){
        // Six pointed paint lobes plus two low rings, with open gaps for target visibility.
        for(int i=0;i<6;i++){
            double a=i*Math.TAU/6;int color=i%3==0?GOLD:i%2==0?CYAN:PINK;
            curve(out,color,.055f,t->{double r=radius*t;return new Vertex(Math.cos(a)*r,-.65+Math.sin(t*Math.PI)*(.6+(iEven(a)? .3:0)),forward+Math.sin(a)*r*.6);});
        }
        for(int lane=0;lane<2;lane++){
            double r=radius-lane*.12;int color=lane==0?CYAN:PINK;
            curve(out,color,.03f,t->{double a=t*Math.TAU;return new Vertex(Math.cos(a)*r,-.75,forward+Math.sin(a)*r*.6);});
        }
    }
    private static boolean iEven(double a){return Math.cos(a)>0;}
    private static void star(List<LightPath> out,double x,double y,double z,double r,int color){
        for(int axis=0;axis<2;axis++){boolean horizontal=axis==0;curve(out,color,.03f,t->new Vertex(x+(horizontal?(t-.5)*r*2:0),y+(horizontal?0:(t-.5)*r*2),z));}
    }
    private static void curve(List<LightPath> out,int color,float width,DoubleFunction<Vertex> fn){var points=new ArrayList<Vertex>();for(int i=0;i<=24;i++)points.add(fn.apply(i/24d));out.add(new LightPath(List.copyOf(points),color,width));}
}
