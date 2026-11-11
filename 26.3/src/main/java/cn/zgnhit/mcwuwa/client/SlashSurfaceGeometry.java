package cn.zgnhit.mcwuwa.client;

import java.util.ArrayList;
import java.util.List;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Thin translucent spans between the same sampled edges used by visible trails. */
final class SlashSurfaceGeometry {
    private SlashSurfaceGeometry() { }
    static List<LightSurface> between(LightPath left,LightPath right,int color) {
        if(left.points().size()!=right.points().size())throw new IllegalArgumentException("Mismatched slash edges");
        var out=new ArrayList<LightSurface>();
        for(int i=1;i<left.points().size();i++)out.add(new LightSurface(List.of(
            left.points().get(i-1),left.points().get(i),right.points().get(i),right.points().get(i-1)),color));
        return List.copyOf(out);
    }
    static List<LightSurface> verticalWidth(LightPath path,double halfWidth,int color) {
        var low=new ArrayList<Vertex>();var high=new ArrayList<Vertex>();
        for(var v:path.points()){
            low.add(new Vertex(v.side(),v.up()-halfWidth,v.forward()));
            high.add(new Vertex(v.side(),v.up()+halfWidth,v.forward()));
        }
        return between(new LightPath(low,color,path.width()),new LightPath(high,color,path.width()),color);
    }
}
