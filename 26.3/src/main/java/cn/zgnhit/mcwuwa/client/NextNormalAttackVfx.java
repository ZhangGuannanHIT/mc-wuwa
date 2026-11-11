package cn.zgnhit.mcwuwa.client;
import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;
/** Full published normal panels reviewed; stylized MC geometry, accepted hit clocks. */
final class NextNormalAttackVfx {
    record Clock(int duration,int[] hits){}
    private static final Map<String,Clock> CLOCKS=Map.ofEntries(
        Map.entry("changli/basic_01",new Clock(12,new int[]{3,8})),
        Map.entry("changli/basic_02",new Clock(16,new int[]{4,12})),
        Map.entry("changli/basic_03",new Clock(20,new int[]{5,10,16})),
        Map.entry("changli/basic_04",new Clock(24,new int[]{6,10,13,16,20})),
        Map.entry("denia/basic_01",new Clock(14,new int[]{3})),
        Map.entry("denia/basic_02",new Clock(18,new int[]{4,14})),
        Map.entry("denia/basic_03",new Clock(22,new int[]{5,12,18})),
        Map.entry("denia/basic_04",new Clock(26,new int[]{6})),
        Map.entry("iuno/basic_01",new Clock(14,new int[]{3})),
        Map.entry("iuno/basic_02",new Clock(18,new int[]{4,9,14})),
        Map.entry("iuno/basic_03",new Clock(24,new int[]{6,13,20})),
        Map.entry("qingxiao/basic_01",new Clock(14,new int[]{3,10})),
        Map.entry("qingxiao/basic_02",new Clock(18,new int[]{4,14})),
        Map.entry("qingxiao/basic_03",new Clock(22,new int[]{5,9,14,18})),
        Map.entry("qingxiao/basic_04",new Clock(26,new int[]{6,10,14,18,22})),
        Map.entry("lupa/basic_01",new Clock(14,new int[]{3,6,10})),
        Map.entry("lupa/basic_02",new Clock(18,new int[]{4})),
        Map.entry("lupa/basic_03",new Clock(22,new int[]{5,7,9,12,14,16,18})),
        Map.entry("lupa/basic_04",new Clock(26,new int[]{6,11,17,22})),
        Map.entry("carlotta/basic_01",new Clock(14,new int[]{3})),
        Map.entry("carlotta/basic_02",new Clock(20,new int[]{5,10,16})));
    private NextNormalAttackVfx(){}
    private static String canonical(String action){return action.equals("denia/basic_04_c3_full_core")?"denia/basic_04":action;}
    static boolean supports(String action){return CLOCKS.containsKey(canonical(action));}
    private static double delta(String action,double p){
        var c=CLOCKS.get(canonical(action));if(c==null)return Double.POSITIVE_INFINITY;
        double d=Double.POSITIVE_INFINITY;for(int h:c.hits())if(Math.abs(p*c.duration()-h)<Math.abs(d))d=p*c.duration()-h;
        return d;
    }
    static Sample sample(String action,double p,double u){
        var paths=ribbons(action,p);if(paths.isEmpty())return new Sample(0,0,0,0xffffffff,.2f,Kind.NONE);
        double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);var v=path.points().get(Math.min(path.points().size()-1,(int)((at-(int)at)*path.points().size())));
        return new Sample(v.side(),v.up(),v.forward(),path.color(),.24f,Kind.DUST);
    }
    static List<LightPath> ribbons(String action,double p){
        action=canonical(action);
        double d=delta(action,p);if(d<=-2||d>=3)return List.of();
        String role=action.substring(0,action.indexOf('/'));int stage=action.charAt(action.length()-1)-'0';
        var out=new ArrayList<LightPath>();
        switch(role){
            case "changli" -> {
                out.addAll(BlazePuppetAttackVfx.ribbons(action,p));
            }
            case "denia" -> {
                if(stage==4){
                    // The final guided bubble has depth: three intersecting great circles.
                    double r=.65+Math.clamp(p,0,1)*.25;
                    for(int plane=0;plane<3;plane++){int axis=plane;
                        curve(out,plane==1?0xffffb8ef:0xff8bdcff,.028f,t->{double a=t*Math.TAU,x=Math.cos(a)*r,y=Math.sin(a)*r;
                            return axis==0?new Vertex(x,.6+y,2.7):axis==1?new Vertex(x,.6,2.7+y):new Vertex(0,.6+x,2.7+y);});
                    }
                }else for(int sign:new int[]{-1,1})curve(out,sign>0?0xff8bdcff:0xffffb8ef,.04f,t->{double a=t*Math.TAU;return new Vertex(Math.sin(a)*1.25,.2+Math.cos(a)*.65,.8+t*2.4);});
                for(int i=0;i<4;i++){double x=(i-1.5)*.65,z=1.2+i*.4,r=stage==4?.38:.18;curve(out,0xffb0e9ff,.025f,t->new Vertex(x+Math.cos(t*Math.TAU)*r,.55+Math.sin(t*Math.TAU)*r,z));}
            }
            case "iuno" -> {
                for(int layer=0;layer<3;layer++){double offset=layer*.07;curve(out,layer==1?0xffffefbb:0xff74dfff,.04f,t->moonArc(stage,t,offset,p));}
            }
            case "qingxiao" -> {
                for(int i=0;i<(stage>=3?4:2);i++){
                    double x=(i-(stage>=3?1.5:.5))*.55;
                    if(stage==4){
                        for(int sign:new int[]{-1,1})curve(out,sign<0?0xffd6ffff:0xff72eaff,.025f,t->qingBladeEdge(x,sign,t));
                        curve(out,0xffa5ffff,.025f,t->new Vertex(x-.22+t*.44,1.45,2.4));
                    }else{
                        curve(out,0xffd6ffff,.04f,t->new Vertex(x,.2+t*.35,.4+t*3.2));
                        curve(out,0xff72eaff,.028f,t->new Vertex(x+(t<.5?t:1-t)*.4,.2,.7+t*2.6));
                    }
                }
            }
            case "lupa" -> {
                for(int i=0;i<3;i++){double off=(i-1)*.07;curve(out,i==1?0xffffddc4:0xffff3a61,.05f,t->{double a=-2+t*4;return new Vertex(Math.sin(a)*1.9,(stage==4?-.7+t*2.5:.1+Math.cos(a)*.65)+off,.9+Math.cos(a)*1.3);});}
            }
            case "carlotta" -> {
                for(int sign:new int[]{-1,1}){
                    curve(out,sign>0?0xffc4eaff:0xffffa5d9,.027f,t->new Vertex(sign*(.22+t*.12),.15,.5+t*4));
                    curve(out,0xffffd5ef,.03f,t->new Vertex(sign*(-.45+t*.9),.15+(-.45+t*.9),3.6));
                }
            }
        }
        return FiveNormalAttackVfx.contactTrails(out,d);
    }
    static List<LightSurface> surfaces(String action,double p){
        if(Math.abs(delta(action,p))>1.5)return List.of();
        if(action.startsWith("changli/"))return BlazePuppetAttackVfx.surfaces(action,p);
        if(action.startsWith("iuno/")){
            int stage=action.charAt(action.length()-1)-'0';var faces=new ArrayList<LightSurface>();
            for(int i=0;i<16;i++){double a=i/16d,b=(i+1)/16d;
                faces.add(new LightSurface(List.of(moonArc(stage,a,-.07,p),moonArc(stage,b,-.07,p),moonArc(stage,b,.07,p),moonArc(stage,a,.07,p)),0x2874dfff));
            }return List.copyOf(faces);
        }
        if(action.startsWith("denia/"))return List.of(new LightSurface(List.of(new Vertex(-.3,.3,1.8),new Vertex(0,.65,1.8),new Vertex(.3,.3,1.8),new Vertex(0,-.05,1.8)),0x28bceaff));
        if(action.equals("qingxiao/basic_04")){
            var faces=new ArrayList<LightSurface>();
            for(int i=0;i<4;i++){double x=(i-1.5)*.55;
                faces.add(new LightSurface(List.of(new Vertex(x-.07,1.45,2.4),new Vertex(x+.07,1.45,2.4),new Vertex(x+.07,-.35,2.4),new Vertex(x-.07,-.35,2.4)),0x3872eaff));
                var tip=new Vertex(x,-.7,2.4);faces.add(new LightSurface(List.of(new Vertex(x-.07,-.35,2.4),new Vertex(x+.07,-.35,2.4),tip,tip),0x48a5ffff));
            }return List.copyOf(faces);
        }
        if(action.startsWith("qingxiao/")){
            var left=new Vertex(-.06,.2,2.6);var right=new Vertex(.06,.2,2.6);var tip=new Vertex(0,.2,3.2);
            // Preserve the original five-point sword silhouette using the
            // renderer's four-vertex primitive: shaft plus tapered tip.
            return List.of(new LightSurface(List.of(new Vertex(-.1,.2,.6),new Vertex(.1,.2,.6),right,left),0x68a5ffff),
                new LightSurface(List.of(left,right,tip,tip),0x68a5ffff));
        }
        return List.of();
    }
    private static Vertex qingBladeEdge(double x,int sign,double t){
        double y=2.1-t*2.8;
        double width=y>1.45?.025:y>=-.35?.07:.07*Math.clamp((y+.7)/.35,0,1);
        return new Vertex(x+sign*width,y,2.4);
    }
    private static Vertex moonArc(int stage,double t,double width,double progress){
        double angle=-2+t*4,r=1.5+width;
        if(stage==3)return new Vertex(width,1.8-t*2.6,1.1+Math.sin(t*Math.PI)*1.1);
        if(stage==2){
            double turn;
            if(progress<.30){double u=Math.clamp((progress-.04)/.26,0,1);turn=Math.PI*u*u*(3-2*u);}
            else if(progress<=.72)turn=Math.PI;
            else {double u=Math.clamp((progress-.72)/.28,0,1);turn=Math.PI*(1+u*u*(3-2*u));}
            // Match the inverted body's foot height; public light origin is1.05.
            double height=.75-.75*Math.cos(turn)+.5*Math.pow(Math.sin(turn/2),2)+.10-1.05;
            return new Vertex(Math.sin(angle)*r,height,Math.cos(angle)*r);
        }
        return new Vertex(Math.sin(angle)*r,.25+Math.cos(angle)*.6,1.1+Math.cos(angle)*(1.1+width));
    }
    private static void curve(List<LightPath> out,int color,float width,DoubleFunction<Vertex> fn){var points=new ArrayList<Vertex>();for(int i=0;i<=24;i++)points.add(fn.apply(i/24d));out.add(new LightPath(List.copyOf(points),color,width));}
}
