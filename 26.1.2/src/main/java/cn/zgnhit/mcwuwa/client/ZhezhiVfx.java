package cn.zgnhit.mcwuwa.client;

import java.util.ArrayList;
import java.util.List;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Brush strokes and crane silhouettes, independent of damage dispatch. */
final class ZhezhiVfx {
    private static final int JADE=0xffa6dfcf,WHITE=0xfffff4e9,INK=0xff537c81;
    private ZhezhiVfx(){}
    static Sample sample(String action,double progress,double u,long elapsed){
        double p=Math.clamp(progress,0,1),angle=u*Math.TAU;
        if(action.equals("charge"))return NewRoleVfxProfiles.charge(JADE,u,elapsed);
        if(action.equals("ink_crane")||action.equals("ink_crane_c5")||action.equals("white_crane_c6")){
            var paths=ribbons(action,p);double at=Math.clamp(u,0,.999999)*paths.size();
            var path=paths.get((int)at);var point=path.points().get((int)((at-(int)at)*25));
            return new Sample(point.side(),point.up(),point.forward(),path.color(),.28f,Kind.DUST);
        }
        if(action.equals("crane_image"))return new Sample(Math.sin(angle)*.65,.8+Math.cos(angle)*.2,0,WHITE,.35f,Kind.DUST);
        if(action.equals("liberation"))return new Sample((u-.5)*4,1.2+Math.sin(u*Math.PI)*.7,.6+p*.4,JADE,.6f,Kind.DUST);
        return new Sample(Math.sin(angle)*(.1+p*.4),Math.cos(angle)*.25,.5+u*2.5,u<.3?INK:WHITE,.5f,Kind.DUST);
    }
    static List<LightPath> ribbons(String action,double progress){
        var paths=new ArrayList<LightPath>();double p=Math.clamp(progress,0,1);
        if(action.equals("charge"))return List.of();
        boolean crane=action.contains("crane")||action.contains("genius")||action.contains("maestro");
        if(crane){
            double z=action.equals("crane_image")?0:.4+p*1.5;
            // Two tapered wings, arched neck, beak and trailing legs remain a
            // recognizable crane from either side; no billboard-only image.
            for(int side:new int[]{-1,1}){
                stroke(paths,WHITE,.035f,t->new Vertex(side*t*1.15,.8+Math.sin(t*Math.PI)*.45,z-.35*t));
                stroke(paths,JADE,.025f,t->new Vertex(side*t*1.15,.8+Math.sin(t*Math.PI)*.30-.13*t,z-.35*t));
                stroke(paths,INK,.018f,t->new Vertex(side*.08,.65-t*.3,z-t*.55));
            }
            stroke(paths,WHITE,.055f,t->new Vertex(0,.7+Math.sin(t*Math.PI)*.28,z-.35+t*.65));
            stroke(paths,WHITE,.025f,t->new Vertex(0,.85+Math.sin(t*Math.PI)*.32,z+.25+t*.25));
            stroke(paths,JADE,.018f,t->new Vertex(0,.86-t*.12,z+.5+t*.3));
        }else if(action.equals("liberation")){
            for(int edge:new int[]{-1,1})stroke(paths,JADE,.045f,t->new Vertex((t-.5)*4,1.2+edge*.45+Math.sin(t*Math.PI)*.4,.6));
            for(int i=0;i<5;i++){final int n=i;stroke(paths,WHITE,.02f,t->new Vertex(-1.5+n*.75+t*.3,.85+t*.65,.6+Math.sin(t*Math.PI)*.05));}
        }else{
            stroke(paths,JADE,.025f,t->{double a=-1.5+t*3;return new Vertex(Math.sin(a)*1.15,.1+Math.cos(a)*.2,1.4+Math.cos(a)*.6);});
            stroke(paths,WHITE,.02f,t->new Vertex(Math.sin(t*Math.TAU+p*3)*.12,.7+Math.cos(t*Math.TAU)*.12,.7+t*2));
        }
        if(action.equals("ink_crane_c5")){
            // The simultaneous extra crane must remain visible beside the base
            // crane. This presentation offset does not move its damage target.
            return paths.stream().map(path->new LightPath(path.points().stream()
                .map(v->new Vertex(-v.side()+.65,v.up()+.35,v.forward()-.35)).toList(),
                path.color(),path.width())).toList();
        }
        return List.copyOf(paths);
    }
    private static void stroke(List<LightPath> paths,int color,float width,java.util.function.DoubleFunction<Vertex> shape){
        var vertices=new ArrayList<Vertex>();for(int i=0;i<=24;i++)vertices.add(shape.apply(i/24d));
        paths.add(new LightPath(vertices,color,width));
    }
}
