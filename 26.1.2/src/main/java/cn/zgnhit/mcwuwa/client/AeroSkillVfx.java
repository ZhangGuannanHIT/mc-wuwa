package cn.zgnhit.mcwuwa.client;

import java.util.ArrayList;
import java.util.List;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Complete official skill card: rising entry, low sweep and crossed aerial wind.
 * Uses retained MC contacts, not the edited film's five-second montage duration. */
final class AeroSkillVfx {
    private AeroSkillVfx() { }
    static boolean supports(String action) {
        return action.equals("aero_rover/skill")||action.equals("aero_rover/skill_sky");
    }
    private static double delta(String action,double p) {
        var c=RemainingCastContactClocks.ALL.get(action);double result=Double.POSITIVE_INFINITY;
        for(int hit:c.hits())if(Math.abs(p*c.duration()-hit)<Math.abs(result))result=p*c.duration()-hit;
        return result;
    }
    static List<LightPath> ribbons(String action,double p) {
        if(!supports(action)||p<=0||p>=1)return List.of();double d=delta(action,p);
        if(d<=-2||d>=3)return List.of();
        boolean rising=action.equals("aero_rover/skill")&&p<.5,sky=action.endsWith("skill_sky");
        var c=RemainingCastContactClocks.ALL.get(action);int contact=0;
        for(int i=1;i<c.hits().length;i++)if(Math.abs(p*c.duration()-c.hits()[i])<Math.abs(p*c.duration()-c.hits()[contact]))contact=i;
        int sign=contact%2==0?1:-1;var out=new ArrayList<LightPath>();
        for(int crossing=0;crossing<(sky?2:1);crossing++)for(int layer=0;layer<3;layer++){
            var points=new ArrayList<Vertex>();double radius=1.75+layer*.07;
            for(int i=0;i<=24;i++){
                double t=i/24d,angle=-2.3+t*4.6+d*.10;
                if(rising)points.add(new Vertex((layer-1)*.065+Math.sin(t*Math.PI)*.20,-.7+t*2.65,.65+Math.sin(t*Math.PI)*1.3));
                else if(sky){
                    int slope=crossing==0?sign:-sign;
                    points.add(new Vertex(Math.sin(angle)*radius,.15+slope*Math.sin(angle)*.62+layer*.025,1.2+Math.cos(angle)*.85+crossing*.08));
                }else points.add(new Vertex(Math.sin(angle)*radius,.1+Math.cos(angle)*.13,1.2+Math.cos(angle)*1.05));
            }
            out.add(new LightPath(List.copyOf(points),layer==1?0xffeffbff:0xff8cdcff,layer==1?.020f:.038f));
        }
        return FiveNormalAttackVfx.contactTrails(out,d);
    }
    static List<LightSurface> surfaces(String action,double p) {
        var paths=ribbons(action,p);if(paths.isEmpty())return List.of();
        var out=new ArrayList<LightSurface>();
        int alpha=(int)Math.round(40*Math.clamp(1-Math.max(0,delta(action,p))/3,0,1));
        for(int i=0;i<paths.size();i+=3)out.addAll(SlashSurfaceGeometry.between(paths.get(i),paths.get(i+2),alpha<<24|0x8cdcff));
        return List.copyOf(out);
    }
    static Sample sample(String action,double p,double u) {
        var paths=ribbons(action,p);if(paths.isEmpty())return new Sample(0,0,0,0xff8cdcff,.2f,Kind.NONE);
        double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);
        var v=path.points().get(Math.min(24,(int)((at-(int)at)*25)));
        return new Sample(v.side(),v.up(),v.forward(),path.color(),.25f,Kind.DUST);
    }
}
