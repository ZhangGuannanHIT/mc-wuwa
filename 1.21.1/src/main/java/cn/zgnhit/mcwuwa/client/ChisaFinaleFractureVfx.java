package cn.zgnhit.mcwuwa.client;

import java.util.ArrayList;
import java.util.List;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Last-contact broken red facets observed in the archived Chisa forte film.
 * Positions and envelope are MC authoring, independent of damage geometry. */
final class ChisaFinaleFractureVfx {
    private ChisaFinaleFractureVfx() {}

    static List<LightSurface> surfaces(String action,double progress) {
        if(!action.equals("chisa/saw_finale"))return List.of();
        var clock=ScissorBambooContactClocks.ALL.get(action);
        double age=progress*clock.duration()-clock.hits()[clock.hits().length-1];
        if(age<0||age>=3)return List.of();
        double phase=age/3,spread=1+phase*.2;
        int alpha=(int)Math.round(64*(1-phase));
        var faces=new ArrayList<LightSurface>();
        for(int i=0;i<6;i++) {
            double angle=i*Math.TAU/6+.2,outer=(i%2==0?1.65:1.35)*spread;
            double lift=(i%2==0?.42:.23)+phase*.3;
            Vertex inner=point(angle,.38,-.86);
            Vertex left=point(angle-.34,outer,-.9);
            Vertex tip=point(angle+.08,outer*.84,-.9+lift);
            Vertex right=point(angle+.38,outer*.78,-.9);
            // The shared renderer submits quads; duplicate the last vertex to
            // represent a triangular facet without changing its render contract.
            faces.add(new LightSurface(List.of(inner,left,tip,tip),alpha<<24|0xff526f));
            faces.add(new LightSurface(List.of(inner,tip,right,right),(alpha/2)<<24|0xd9e9ff));
        }
        return List.copyOf(faces);
    }

    static List<LightPath> ribbons(String action,double progress) {
        var paths=new ArrayList<LightPath>();
        var faces=surfaces(action,progress);
        // Keep all facets, highlighting three separated red fragments. Together
        // with the existing nine cut/saw paths this stays inside the 12-path budget.
        for(int index=0;index<faces.size();index+=4) {
            var face=faces.get(index);
            var points=new ArrayList<Vertex>();
            // All VFX sample consumers expect 25 samples per path.
            for(int i=0;i<=24;i++) {
                double at=i/8d;int edge=Math.min(2,(int)at);double t=at-edge;
                Vertex a=face.vertices().get(edge),b=face.vertices().get((edge+1)%3);
                points.add(new Vertex(a.side()+(b.side()-a.side())*t,
                    a.up()+(b.up()-a.up())*t,a.forward()+(b.forward()-a.forward())*t));
            }
            paths.add(new LightPath(List.copyOf(points),0xffffbbc9,.018f));
        }
        return List.copyOf(paths);
    }

    private static Vertex point(double angle,double radius,double up) {
        return new Vertex(Math.cos(angle)*radius,up,1.45+Math.sin(angle)*radius);
    }
}
