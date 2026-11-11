package cn.zgnhit.mcwuwa.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Target-centred feedback for confirmed autonomous hits. The server anchors
 * these at contact, independently of the active character and camera. */
final class AutonomousContactVfx {
    private AutonomousContactVfx() {}
    static List<LightSurface> surfaces(String action,double progress) {
        // Confirmed victim-centred lightning only. Musical rings, bubbles and
        // strings remain open curves rather than becoming opaque filled discs.
        if(!action.startsWith("yinlin/")&&!action.equals("augusta/thunder_contact"))return List.of();
        var paths=ribbons(action,progress);if(paths.isEmpty())return List.of();
        int alpha=(int)Math.round(32*Math.sin(progress*Math.PI));
        int color=action.startsWith("yinlin/")?0xd085ff:0xb59cff;
        return SlashSurfaceGeometry.between(paths.get(0),paths.get(2),alpha<<24|color);
    }
    static boolean supports(String action) {
        return Set.of("cantarella/jellyfish_contact","cantarella/awaken_contact","phoebe/starlight_contact","augusta/thunder_contact","yinlin/magnetic_contact","yinlin/judgment_contact","yangyang_xuanling/shadow_contact","ciaccona/solo_contact","qingxiao/vast_sword_contact","carlotta/outro_contact").contains(action);
    }
    static List<LightPath> ribbons(String action,double progress) {
        if(!supports(action)||progress<=0||progress>=1)return List.of();
        double p=Math.clamp(progress,0,1);
        var out=new ArrayList<LightPath>();
        if(action.equals("qingxiao/vast_sword_contact")) {
            // The proc has no Running action. Reuse its authored sword rain,
            // translating the caster-relative footprint onto the actual victim.
            for(var path:NewSixRoleVfx.ribbons("qingxiao","vast_sword",p))
                out.add(new LightPath(path.points().stream().map(v->new Vertex(v.side(),v.up()-.4,v.forward()-1.6)).toList(),path.color(),path.width()));
        } else if(action.equals("carlotta/outro_contact")) {
            // Short rose/ice crystal fragments for the independent C3 impact.
            // This does not replay the outgoing body's gun animation.
            for(int i=0;i<6;i++){
                double angle=i*Math.TAU/6,r=.2+p*.65,x=Math.cos(angle)*r,y=Math.sin(angle)*r;
                curve(out,i%2==0?0xffff779f:0xff87d8ff,.03f,t->{
                    double a=t*Math.TAU;
                    return new Vertex(x+Math.cos(a)*.12,y+Math.sin(a)*.28,Math.sin(angle)*.2);
                });
            }
            double radius=.2+p*.8;
            curve(out,0xfff3fcff,.025f,t->new Vertex(Math.cos(t*Math.TAU)*radius,0,Math.sin(t*Math.TAU)*radius));
        } else if(action.equals("ciaccona/solo_contact")) {
            // Authored contact feedback using the existing mint/gold musical
            // vocabulary. The independent solo performer remains at its anchor.
            double radius=.25+p*.9;
            for(int layer=0;layer<3;layer++){
                double height=(layer-1)*.18,r=radius+layer*.06;
                curve(out,layer==1?0xffffedaf:0xff98f6d9,.026f,t->
                    new Vertex(Math.cos(t*Math.TAU)*r,height,Math.sin(t*Math.TAU)*r));
            }
            for(int side:new int[]{-1,1}){
                double x=side*(.3+p*.4),y=.1+p*.45;
                curve(out,0xffffedaf,.03f,t->new Vertex(x+Math.cos(t*Math.TAU)*.16,y+Math.sin(t*Math.TAU)*.09,0));
                curve(out,0xffffedaf,.022f,t->new Vertex(x+.15,y+t*.5,0));
            }
        } else if(action.equals("cantarella/jellyfish_contact")) {
            // Blue/violet water and rising bubbles from the archived release film.
            // This brief MC contact envelope does not replace the hovering jellyfish.
            double radius=.18+.9*Math.sin(p*Math.PI/2);
            for(int layer=0;layer<2;layer++) {
                double height=-.35+layer*.18,r=radius+layer*.12;
                curve(out,layer==0?0xff82e9ff:0xffb79bff,.03f,t->
                    new Vertex(Math.cos(t*Math.TAU)*r,height,Math.sin(t*Math.TAU)*r));
            }
            for(int i=0;i<5;i++) {
                double angle=i*Math.TAU/5,x=Math.cos(angle)*radius*.7,z=Math.sin(angle)*radius*.7;
                double height=-.2+p*.9+i*.06,bubble=.07+.035*Math.sin(p*Math.PI);
                curve(out,i%2==0?0xffb8f6ff:0xffd6b9ff,.018f,t->
                    new Vertex(x+Math.cos(t*Math.TAU)*bubble,height+Math.sin(t*Math.TAU)*bubble,z));
            }
        } else if(action.equals("cantarella/awaken_contact")) {
            // A rupturing dream bubble: three intersecting shells and short
            // outward fragments, not a second swing attached to the caster.
            double r=.25+1.15*Math.sin(Math.min(1,p*1.4)*Math.PI/2);
            for(int axis=0;axis<3;axis++) {
                int plane=axis;
                curve(out,axis==1?0xff92e7ff:0xffd7a0ff,.035f,t->{
                    double a=t*Math.TAU,x=Math.cos(a)*r,y=Math.sin(a)*r;
                    return plane==0?new Vertex(x,y,0):plane==1?new Vertex(x,0,y):new Vertex(0,x,y);
                });
            }
            for(int i=0;i<6;i++) {
                double angle=i*Math.TAU/6;
                curve(out,0xffefe2ff,.028f,t->{double d=r*(.8+t*.45);return new Vertex(Math.cos(angle)*d,Math.sin(angle)*d,(t-.5)*.4);});
            }
        } else if(action.equals("phoebe/starlight_contact")) {
            double r=.65+.25*Math.sin(p*Math.PI);
            for(int i=0;i<4;i++) {
                double angle=i*Math.TAU/4,x=Math.cos(angle)*r,z=Math.sin(angle)*r;
                curve(out,i%2==0?0xffffe9a5:0xffb0e9ff,.04f,t->new Vertex(x*Math.sin(t*Math.PI),r*1.4*Math.cos(t*Math.PI),z*Math.sin(t*Math.PI)));
            }
            for(int i=0;i<4;i++) {
                double angle=i*Math.TAU/4;
                curve(out,0xfffffff1,.03f,t->new Vertex(Math.cos(angle)*t*1.3,Math.sin(angle)*t*1.3,0));
            }
        } else if(action.equals("yangyang_xuanling/shadow_contact")) {
            // Reuse the reviewed blue/gold feather-blade vocabulary. These
            // short contact strokes belong to the struck target, not the
            // stance-switch body or its independent unfinished-basic phantom.
            double sweep=(p-.5)*.65;
            for(int layer=0;layer<3;layer++) {
                double radius=1.1+layer*.12,offset=(layer-1)*.08;
                curve(out,layer==1?0xffffdf98:0xff73cfff,layer==1?.04f:.025f,t->{
                    double angle=-2.35+t*4.7+sweep;
                    return new Vertex(Math.sin(angle)*radius,(t-.5)*1.05+offset,Math.cos(angle)*radius*.45);
                });
            }
            for(int i=0;i<4;i++) {
                double angle=i*Math.TAU/4+p*.25;
                curve(out,i%2==0?0xffd7f4ff:0xffffdf98,.022f,t->{
                    double radius=.35+p*.35+t*.6;
                    return new Vertex(Math.cos(angle)*radius,(t-.5)*.5,Math.sin(angle)*radius*.7);
                });
            }
        } else if(action.startsWith("yinlin/")) {
            // Preserve the reviewed purple lightning motif, centred on the
            // victim rather than the caster's forward puppet position.
            double height=action.equals("yinlin/judgment_contact")?3.1:2.1;
            for(int branch=0;branch<3;branch++) {
                double side=(branch-1)*.34;
                curve(out,branch==1?0xffff70c8:0xffd085ff,.035f,t->
                    new Vertex(side+Math.sin(t*35+side)*.12,height*(1-t),Math.sin(t*24)*.18));
            }
            double radius=.2+.65*p;
            curve(out,0xffd085ff,.025f,t->new Vertex(Math.cos(t*Math.TAU)*radius,.1,Math.sin(t*Math.TAU)*radius));
        } else {
            for(int branch=0;branch<3;branch++) {
                double side=(branch-1)*.35;
                curve(out,branch==1?0xfffff0ba:0xffb59cff,.045f,t->new Vertex(side+Math.sin(t*Math.PI*6)*(1-t)*.24,3.2*(1-t),Math.sin(t*Math.PI*4)*.18));
            }
            double r=.3+p*1.25;
            curve(out,0xffe1c5ff,.035f,t->new Vertex(Math.cos(t*Math.TAU)*r,-.1,Math.sin(t*Math.TAU)*r));
        }
        return List.copyOf(out);
    }
    private static void curve(List<LightPath> out,int color,float width,DoubleFunction<Vertex> fn) {
        var points=new ArrayList<Vertex>();for(int i=0;i<=24;i++)points.add(fn.apply(i/24d));
        out.add(new LightPath(List.copyOf(points),color,width));
    }
}
