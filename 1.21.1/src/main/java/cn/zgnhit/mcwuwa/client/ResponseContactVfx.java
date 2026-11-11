package cn.zgnhit.mcwuwa.client;

import java.util.ArrayList;
import java.util.List;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Continuous surfaces supplement the existing successful-hit particles.
 * Colours/shapes follow the archived response observations; this twelve-tick
 * envelope and world dimensions are MC adaptations, not original frame timing. */
final class ResponseContactVfx {
    private ResponseContactVfx() {}
    static boolean supports(String action) {
        return action.equals("aemeath/starburst_contact") || action.equals("lynae/spectrum_contact");
    }
    static List<LightSurface> surfaces(String action,double progress) {
        if(!supports(action)||progress<=0||progress>=1)return List.of();
        var result=new ArrayList<LightSurface>();
        double expansion=Math.sin(progress*Math.PI/2);
        int alpha=(int)(110*(1-progress));
        if(action.startsWith("aemeath/")) {
            // An open centre preserves the victim silhouette. Two differently
            // inclined perimeters give the expanding wave actual depth.
            double radius=.4+1.25*expansion;
            for(int plane=0;plane<2;plane++)for(int i=0;i<24;i++) {
                double a=i*Math.TAU/24,b=(i+1)*Math.TAU/24;
                result.add(new LightSurface(List.of(ring(a,radius,plane),ring(b,radius,plane),
                    ring(b,radius-.065,plane),ring(a,radius-.065,plane)),
                    (alpha<<24)|(plane==0?0xfff3ef:0xffc5e9)));
            }
            for(int side:new int[]{-1,1}) {
                double x=side*.18,height=1.2+expansion*.45;
                result.add(new LightSurface(List.of(new Vertex(x-.018,-height,-.66),
                    new Vertex(x+.018,-height,-.66),new Vertex(x+.018,height,-.66),
                    new Vertex(x-.018,height,-.66)),(alpha<<24)|0xd7b8ff));
            }
        } else {
            // Three coloured slashes, each with a tapered leading tip. These
            // are the contact splash, not Lynae's persistent background rainbow.
            int[] colours={0x39e6f2,0xee68cf,0xffdf59};
            for(int layer=0;layer<3;layer++)for(int sign:new int[]{-1,1}) {
                double angle=(layer==1?-.55:.55)+(layer-1)*.12;
                double reach=(.45+expansion*.95)*sign,width=.055+(1-progress)*.075;
                double x=Math.cos(angle)*reach,y=Math.sin(angle)*reach,z=-.65-layer*.025;
                double nx=-Math.sin(angle)*width,ny=Math.cos(angle)*width;
                result.add(new LightSurface(List.of(new Vertex(nx,ny,z),new Vertex(x+nx,y+ny,z),
                    new Vertex(x*1.18,y*1.18,z),new Vertex(-nx,-ny,z)),
                    (alpha<<24)|colours[layer]));
            }
        }
        return List.copyOf(result);
    }
    private static Vertex ring(double angle,double radius,int plane) {
        double x=Math.cos(angle)*radius,y=Math.sin(angle)*radius;
        return plane==0?new Vertex(x,y,-.65):new Vertex(x,y*.35,-.65+y*.8);
    }
}
