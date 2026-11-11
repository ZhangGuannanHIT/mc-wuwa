package cn.zgnhit.mcwuwa.client;

import java.util.ArrayList;
import java.util.List;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Volumetric ink shards for the observed third descending cut. */
final class QiuyuanInkBurstVfx {
    private QiuyuanInkBurstVfx() {}
    private record Shard(Vertex a,Vertex b,Vertex c,Vertex tip) {}

    private static List<Shard> shards(String action,double progress) {
        if(!action.equals("qiuyuan/ink_heavy_03")&&!action.equals("qiuyuan/ink_heavy_03_c3"))return List.of();
        var clock=ScissorBambooContactClocks.ALL.get(action);
        double age=progress*clock.duration()-clock.hits()[0];
        if(age<0||age>=3)return List.of();
        double growth=.8+.2*age/3;
        var result=new ArrayList<Shard>();
        for(int i=0;i<8;i++) {
            double angle=i*Math.TAU/8,r=.6+(i%3)*.17,x=Math.cos(angle)*r,z=1.7+Math.sin(angle)*r;
            double width=.15+(i%2)*.04;
            Vertex a=new Vertex(x-width,-.95,z-width),b=new Vertex(x+width,-.95,z-width),c=new Vertex(x,-.95,z+width);
            Vertex tip=new Vertex(x+Math.cos(angle)*.6,-.95+(1.65+(i%3)*.42)*growth,z+Math.sin(angle)*.6);
            result.add(new Shard(a,b,c,tip));
        }
        return List.copyOf(result);
    }

    static List<LightSurface> surfaces(String action,double progress) {
        var result=new ArrayList<LightSurface>();
        for(var shard:shards(action,progress)) {
            result.add(new LightSurface(List.of(shard.a,shard.b,shard.tip,shard.tip),0x680b2927));
            result.add(new LightSurface(List.of(shard.b,shard.c,shard.tip,shard.tip),0x4865f3df));
            result.add(new LightSurface(List.of(shard.c,shard.a,shard.tip,shard.tip),0x38163c37));
        }
        return List.copyOf(result);
    }

    static List<LightPath> ribbons(String action,double progress) {
        var result=new ArrayList<LightPath>();
        for(var shard:shards(action,progress)) {
            var points=new ArrayList<Vertex>();
            for(int i=0;i<=24;i++) {
                double t=i/24d;
                points.add(new Vertex(shard.b.side()+(shard.tip.side()-shard.b.side())*t,
                    shard.b.up()+(shard.tip.up()-shard.b.up())*t,
                    shard.b.forward()+(shard.tip.forward()-shard.b.forward())*t));
            }
            result.add(new LightPath(List.copyOf(points),0xff65f3df,.024f));
        }
        return List.copyOf(result);
    }
}
