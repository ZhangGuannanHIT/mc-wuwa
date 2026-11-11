package cn.zgnhit.mcwuwa.client;
import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;
/** Full published normal panels reviewed; stylized MC geometry, accepted hit clocks. */
final class SupportNormalAttackVfx {
    record Clock(int duration,int[] hits){}
    private static final Map<String,Clock> CLOCKS=Map.ofEntries(
        Map.entry("qiuyuan/basic_01",new Clock(14,new int[]{3})),
        Map.entry("qiuyuan/basic_02",new Clock(18,new int[]{4,14})),
        Map.entry("qiuyuan/basic_03",new Clock(26,new int[]{6,10,14,18,22})),
        Map.entry("ciaccona/basic_01",new Clock(12,new int[]{3})),
        Map.entry("ciaccona/basic_02",new Clock(18,new int[]{4,7,11,14})),
        Map.entry("ciaccona/basic_03",new Clock(22,new int[]{5,9,14,18})),
        Map.entry("ciaccona/basic_04",new Clock(28,new int[]{7,13,18,24})),
        Map.entry("shorekeeper/basic_01",new Clock(12,new int[]{3})),
        Map.entry("shorekeeper/basic_02",new Clock(16,new int[]{4,12})),
        Map.entry("shorekeeper/basic_03",new Clock(22,new int[]{5,12,18})),
        Map.entry("shorekeeper/basic_04",new Clock(24,new int[]{6})),
        Map.entry("sigrika/basic_01",new Clock(12,new int[]{3})),
        Map.entry("sigrika/basic_02",new Clock(16,new int[]{4,12})),
        Map.entry("sigrika/basic_03",new Clock(22,new int[]{5,12,18})),
        Map.entry("sigrika/basic_04",new Clock(26,new int[]{6,11,17,22})),
        Map.entry("galbrena/basic_01",new Clock(12,new int[]{3})),
        Map.entry("galbrena/basic_02",new Clock(18,new int[]{4,9,14})),
        Map.entry("galbrena/basic_03",new Clock(22,new int[]{5,9,14,18})),
        Map.entry("galbrena/basic_04",new Clock(24,new int[]{6})));
    private SupportNormalAttackVfx(){}
    private static String canonical(String action){return action;}
    static boolean supports(String action){return CLOCKS.containsKey(canonical(action));}
    private static double delta(String action,double p){
        var c=CLOCKS.get(canonical(action));if(c==null)return Double.POSITIVE_INFINITY;
        double d=Double.POSITIVE_INFINITY;for(int h:c.hits())if(Math.abs(p*c.duration()-h)<Math.abs(d))d=p*c.duration()-h;
        return d;
    }
    /** Publisher normal panel shows alternating reports, followed by the final sweep. */
    private static int[] gunSides(String action,double p){
        if(action.endsWith("04"))return new int[]{-1,1};
        var c=CLOCKS.get(action);int closest=0;
        for(int i=1;i<c.hits().length;i++)
            if(Math.abs(p*c.duration()-c.hits()[i])<Math.abs(p*c.duration()-c.hits()[closest]))closest=i;
        return new int[]{closest%2==0?1:-1};
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
            case "qiuyuan" -> {
                for(int i=0;i<3;i++){double off=(i-1)*.045;curve(out,i==1?0xffe7ffff:0xff53e9e0,.035f,t->new Vertex(-1.6+t*3.2,stage==3?-.5+t*1.4:.15+off,1.3+Math.sin(t*Math.PI)*.8));}
                if(stage==3)curve(out,0xffbfffff,.025f,t->new Vertex(-1.2+t*2.4,1.1-t*1.8,1.8));
            }
            case "ciaccona" -> {
                for(int i=0;i<4;i++){double off=i*.09;curve(out,i%2==0?0xff9affe4:0xffffe4a2,.025f,t->{double a=-2+t*4;return new Vertex(Math.sin(a)*1.7,.1+off+(stage==4?t*.7:0),1.2+Math.cos(a));});}
                for(int i=0;i<3;i++){double x=(i-1)*.65,z=1.3+i*.4;curve(out,0xffffe5a6,.027f,t->new Vertex(x+Math.sin(t*Math.TAU)*.12,.35+Math.cos(t*Math.TAU)*.12,z));curve(out,0xffffe5a6,.024f,t->new Vertex(x+.12,.35+t*.5,z));}
            }
            case "shorekeeper" -> {
                for(int sign:new int[]{-1,1})curve(out,sign>0?0xff8feaff:0xffd0beff,.035f,
                    t->new Vertex(sign*Math.sin(t*Math.PI)*1.25,.1+Math.sin(t*Math.TAU)*.5,.4+t*2.8));
                int count=stage==3?3:stage==2?2:1;
                for(int butterfly=0;butterfly<count;butterfly++)for(int side:new int[]{-1,1}){
                    final int index=butterfly;
                    curve(out,0xffddfaff,.024f,t->butterflyWing(stage,index,side,t,p));
                }
            }
            case "sigrika" -> {
                for(int edge=0;edge<2;edge++){final int rim=edge;curve(out,edge==0?0xffffe99a:0xffdbabff,.04f,t->sigrikaSweep(stage,t,rim));}
                if(stage==4)for(int i=0;i<4;i++){double a=i*Math.PI/2,x=Math.cos(a),y=.3+Math.sin(a);curve(out,0xffffe6a2,.025f,t->new Vertex(x+Math.cos(t*Math.TAU)*.18,y+Math.sin(t*Math.TAU)*.18,2.4));}
            }
            case "galbrena" -> {
                for(int sign:gunSides(action,p)){
                    curve(out,sign>0?0xff9fcbff:0xffffa3e8,.035f,t->new Vertex(sign*.3,.15,.4+t*4.4));
                    for(int i=0;i<2;i++){double angle=i*Math.PI/2+sign*.45;curve(out,0xffe3cdff,.03f,t->new Vertex(sign*.3+Math.cos(angle)*(-.45+t*.9),.15+Math.sin(angle)*(-.45+t*.9),1.1));}
                }
                if(stage==4)curve(out,0xffbb9aff,.04f,t->new Vertex(Math.sin(t*Math.PI)*1.4,.15,1.1+Math.cos(t*Math.PI)*1.1));
            }
        }
        return FiveNormalAttackVfx.contactTrails(out,d);
    }
    static List<LightSurface> surfaces(String action,double p){
        if(Math.abs(delta(action,p))>1.5)return List.of();
        if(action.startsWith("qiuyuan/")) {
            int stage=action.charAt(action.length()-1)-'0';
            var cuts=new ArrayList<LightSurface>();
            for(int segment=0;segment<20;segment++){
                double a=segment/20d,b=(segment+1)/20d;
                cuts.add(new LightSurface(List.of(inkBladeBand(stage,a,-1),inkBladeBand(stage,a,1),
                    inkBladeBand(stage,b,1),inkBladeBand(stage,b,-1)),0x2853e9e0));
            }
            return List.copyOf(cuts);
        }
        if(action.startsWith("galbrena/")) {
            var flashes=new ArrayList<LightSurface>();
            double fade=1-Math.abs(delta(action,p))/1.5;
            for(int sign:gunSides(action,p))for(int ray=0;ray<4;ray++){
                double angle=ray*Math.PI/4,reach=(ray%2==0?.34:.22)*fade;
                double x=sign*.3,y=.15,z=1.1;
                double dx=Math.cos(angle),dy=Math.sin(angle),width=.035*fade;
                flashes.add(new LightSurface(List.of(new Vertex(x-dx*reach,y-dy*reach,z),
                    new Vertex(x-dy*width,y+dx*width,z),new Vertex(x+dx*reach,y+dy*reach,z),
                    new Vertex(x+dy*width,y-dx*width,z)),sign>0?0x70b9e9ff:0x70ffc4e7));
            }
            return List.copyOf(flashes);
        }
        if(action.startsWith("ciaccona/")) {
            int stage=action.charAt(action.length()-1)-'0';
            var stave=new ArrayList<LightSurface>();
            // Complete publisher panel 2.4-4.9s: broad translucent stave ribbons.
            // Lines and note heads remain separate; this fills their thin lit band.
            for(int segment=0;segment<24;segment++){
                double a=segment/24d,b=(segment+1)/24d;
                stave.add(new LightSurface(List.of(musicalBand(stage,a,0),musicalBand(stage,a,1),
                    musicalBand(stage,b,1),musicalBand(stage,b,0)),0x2877efce));
            }
            return List.copyOf(stave);
        }
        if(action.startsWith("sigrika/")) {
            int stage=action.charAt(action.length()-1)-'0';
            int alpha=(int)(105*(1-Math.abs(delta(action,p))/1.5));
            var out=new ArrayList<LightSurface>();
            // Publisher normal panel: gold sweeping crescent with a violet rim,
            // rising kick plane, then four small luminous constellation centers.
            if(stage<4)for(int i=0;i<16;i++) {
                double a=i/16d,b=(i+1)/16d;
                out.add(new LightSurface(List.of(sigrikaSweep(stage,a,0),sigrikaSweep(stage,a,1),
                    sigrikaSweep(stage,b,1),sigrikaSweep(stage,b,0)),alpha<<24|0xffe5a0));
            }
            else for(int i=0;i<4;i++) {
                double angle=i*Math.PI/2,x=Math.cos(angle),y=.3+Math.sin(angle);
                out.add(new LightSurface(List.of(new Vertex(x,y+.29,2.4),new Vertex(x+.07,y,2.4),
                    new Vertex(x,y-.29,2.4),new Vertex(x-.07,y,2.4)),alpha<<24|0xfff6d7));
                out.add(new LightSurface(List.of(new Vertex(x-.29,y,2.4),new Vertex(x,y+.07,2.4),
                    new Vertex(x+.29,y,2.4),new Vertex(x,y-.07,2.4)),alpha<<24|0xfff6d7));
            }
            return List.copyOf(out);
        }
        if(action.startsWith("shorekeeper/")) {
            int stage=action.charAt(action.length()-1)-'0';
            int count=stage==3?3:stage==2?2:1;
            var wings=new ArrayList<LightSurface>();
            for(int butterfly=0;butterfly<count;butterfly++)for(int side:new int[]{-1,1})for(int segment=0;segment<8;segment++){
                double a=segment/8d,b=(segment+1)/8d;
                var center=butterflyCenter(stage,butterfly,p);
                wings.add(new LightSurface(List.of(center,butterflyWing(stage,butterfly,side,a,p),
                    butterflyWing(stage,butterfly,side,b,p),center),0x14b8eaff));
            }
            return List.copyOf(wings);
        }
        return List.of();
    }
    private static Vertex inkBladeBand(int stage,double t,int edge){
        double width=Math.sin(t*Math.PI)*.08*edge;
        return new Vertex(-1.6+t*3.2,(stage==3?-.5+t*1.4:.15)+width,1.3+Math.sin(t*Math.PI)*.8);
    }
    private static Vertex butterflyCenter(int stage,int index,double p){
        int count=stage==3?3:stage==2?2:1;
        double closing=stage==4?1-Math.clamp(p,0,1)*.7:1;
        return new Vertex((index-(count-1)/2d)*.85*closing,.35+(stage==3?index*.18:0),1.8+index*.25);
    }
    private static Vertex butterflyWing(int stage,int index,int side,double t,double p){
        var c=butterflyCenter(stage,index,p);
        double fold=.55+.45*Math.abs(Math.cos(p*Math.PI*3+index*.4));
        double taper=stage==4?1-.6*Math.clamp(p,0,1):1;
        double span=(t<.55?Math.sin(t/.55*Math.PI)*.40:Math.sin((t-.55)/.45*Math.PI)*.25)*taper;
        return new Vertex(c.side()+side*span*fold,c.up()+Math.cos(t*Math.PI)*.35*taper,
            c.forward()+span*(1-fold));
    }
    private static Vertex musicalBand(int stage,double t,int edge){
        double a=-2+t*4;
        return new Vertex(Math.sin(a)*1.7,.1+(stage==4?t*.7:0)+edge*.27,1.2+Math.cos(a));
    }
    private static Vertex sigrikaSweep(int stage,double t,int edge) {
        double a=-2+t*4,r=1.35-edge*Math.sin(t*Math.PI)*.25;
        if(stage==3)return new Vertex(Math.sin(a)*r*.35,-.3+Math.cos(a)*r,1.3+Math.sin(a)*r*.65);
        return new Vertex(Math.sin(a)*r,.2+Math.cos(a)*r*.37,1+Math.cos(a)*r*.74);
    }
    private static void curve(List<LightPath> out,int color,float width,DoubleFunction<Vertex> fn){var points=new ArrayList<Vertex>();for(int i=0;i<=24;i++)points.add(fn.apply(i/24d));out.add(new LightPath(List.copyOf(points),color,width));}
}
