package cn.zgnhit.mcwuwa.client;

import java.util.ArrayList;
import java.util.List;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;

/** Source jade mechanisms in official hsin-1/2; decorative surfaces, not hit boxes. */
final class HsinAerialConstructVfx {
    private HsinAerialConstructVfx() { }
    static boolean supports(String action) {
        return action.equals("hsin/illuminated_uppercut") || action.equals("hsin/illuminated_air") || enhanced(action);
    }
    static boolean enhanced(String action) {
        return action.startsWith("hsin/thousand_palaces_basic_") && SealPuppetContactClocks.ALL.containsKey(action);
    }
    static List<LightSurface> surfaces(String action,double p) {
        if(!supports(action)||p<=0||p>=1)return List.of();
        if(enhanced(action))return mechanisms(action,p);
        boolean rise=action.endsWith("uppercut");
        double travel=Math.clamp(p/.6,0,1);
        double fade=Math.min(1,Math.min(p/.12,(1-p)/.18));
        var out=new ArrayList<LightSurface>();
        for(int i=0;i<3;i++) {
            double x=(i-1)*.52,z=1.3+(i==1?0:.24),height=i==1?2.6:1.65;
            double bottom=rise?-.85-height*(1-travel):-.85+2.5*(1-travel);
            // Front/back faces both contribute; low per-face alpha keeps FP aim readable.
            prism(out,x,bottom,z,.17,height,((int)(28*fade)<<24)|0x75d9bd);
            // Separate gold collars preserve the mechanical silhouette at a distance.
            prism(out,x,bottom+height*.22,z,.195,.07,((int)(65*fade)<<24)|0xffdc91);
            prism(out,x,bottom+height*.78,z,.195,.07,((int)(65*fade)<<24)|0xffdc91);
        }
        return List.copyOf(out);
    }
    private static List<LightSurface> mechanisms(String action,double p) {
        int stage=action.charAt(action.length()-1)-'0';
        double fade=Math.min(1,Math.min(p/.15,(1-p)/.2));
        int jade=((int)(42*fade)<<24)|0x75d9bd,gold=((int)(68*fade)<<24)|0xffdc91;
        var out=new ArrayList<LightSurface>();
        // Complete hsin-1: a forward beam, lateral slab, low stepped wheel,
        // and rising gate towers. Dimensions follow the existing MC attack space.
        if(stage==1){
            box(out,-.25,-.35,.35,.5,.65,2.75,jade);
            for(double z:new double[]{.85,2.1})box(out,-.3,-.4,z,.6,.75,.12,gold);
        }else if(stage==2){
            box(out,-1.75,-.15,1.45,3.5,.65,.5,jade);
            for(int i=0;i<5;i++)box(out,-1.35+i*.65,-.2,1.42,.075,.75,.56,gold);
        }else if(stage==3){
            for(int i=0;i<8;i++){
                double a=i*Math.TAU/8;
                box(out,Math.cos(a)*1.05-.28,-.8,1.7+Math.sin(a)*1.05-.28,.56,.4+(i%2)*.18,.56,jade);
            }
            box(out,-.45,-.8,1.25,.9,.75,.9,gold);
        }else{
            double rise=Math.clamp(p/.55,0,1);
            for(int i=0;i<3;i++){
                double x=(i-1)*.9,h=i==1?3:2.35,bottom=-.8-h*(1-rise);
                box(out,x-.24,bottom,1.7,.48,h,.48,jade);
                box(out,x-.3,bottom+h-.16,1.64,.6,.16,.6,gold);
            }
            for(int i=0;i<2;i++)box(out,-.9+i*.9,1.35-2.35*(1-rise),1.72,.9,.18,.44,gold);
        }
        double yaw=stage==1?.65-p*1.3:stage==2?-.8+p*1.6:stage==3?p*.8:0;
        double c=Math.cos(yaw),s=Math.sin(yaw);
        return out.stream().map(face->new LightSurface(face.vertices().stream().map(v->{
            double z=v.forward()-1.7;
            return new Vertex(v.side()*c-z*s,v.up(),1.7+v.side()*s+z*c);
        }).toList(),face.color())).toList();
    }
    static List<LightPath> mechanismEdges(String action,double p) {
        var faces=surfaces(action,p);var out=new ArrayList<LightPath>();
        // One closed front outline per box; every path keeps the existing 25 samples.
        for(int f=0;f<faces.size();f+=6){
            var vertices=faces.get(f).vertices();var points=new ArrayList<Vertex>();
            for(int i=0;i<=24;i++){
                double t=i/6d;int edge=Math.min(3,(int)t);double u=t-edge;
                var a=vertices.get(edge);var b=vertices.get((edge+1)%4);
                points.add(new Vertex(a.side()+(b.side()-a.side())*u,a.up()+(b.up()-a.up())*u,a.forward()+(b.forward()-a.forward())*u));
            }
            out.add(new LightPath(points,faces.get(f).color()|0xff000000,.02f));
        }
        return List.copyOf(out);
    }
    private static void box(List<LightSurface> out,double x,double y,double z,double w,double h,double d,int color) {
        Vertex[] v={new Vertex(x,y,z),new Vertex(x+w,y,z),new Vertex(x+w,y+h,z),new Vertex(x,y+h,z),
            new Vertex(x,y,z+d),new Vertex(x+w,y,z+d),new Vertex(x+w,y+h,z+d),new Vertex(x,y+h,z+d)};
        for(int[] face:new int[][]{{0,1,2,3},{5,4,7,6},{4,0,3,7},{1,5,6,2},{3,2,6,7},{4,5,1,0}})
            out.add(new LightSurface(List.of(v[face[0]],v[face[1]],v[face[2]],v[face[3]]),color));
    }
    private static void prism(List<LightSurface> out,double x,double y,double z,double r,double h,int color) {
        Vertex[] low={new Vertex(x-r,y,z-r),new Vertex(x+r,y,z-r),new Vertex(x+r,y,z+r),new Vertex(x-r,y,z+r)};
        Vertex[] high={new Vertex(x-r,y+h,z-r),new Vertex(x+r,y+h,z-r),new Vertex(x+r,y+h,z+r),new Vertex(x-r,y+h,z+r)};
        for(int i=0;i<4;i++){int n=(i+1)%4;out.add(new LightSurface(List.of(low[i],low[n],high[n],high[i]),color));}
        out.add(new LightSurface(List.of(high[0],high[1],high[2],high[3]),color));
    }
}
