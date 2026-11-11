package cn.zgnhit.mcwuwa.client;
import java.util.*;
import java.util.function.DoubleFunction;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;
/** Continuous film with actual sprocket gaps and bundled photographs; retained contact clocks. */
final class LucillaFilmVfx {
    record Clock(int duration,int... hits){}
    private static final Map<String,Clock> CLOCKS=Map.ofEntries(
        Map.entry("lucilla/basic_03_perfect",new Clock(22,5)),
        Map.entry("lucilla/air",new Clock(24,6)),
        Map.entry("lucilla/dodge_counter",new Clock(20,4,16)),
        Map.entry("lucilla/skill",new Clock(12,3,6,8)),
        Map.entry("lucilla/skill_fill",new Clock(26,6)),
        Map.entry("lucilla/skill_chase",new Clock(26,6,11,17,22)),
        Map.entry("lucilla/liberation",new Clock(48,12)),
        Map.entry("lucilla/liberation_echo",new Clock(48,12)),
        Map.entry("lucilla/memory_basic_01",new Clock(14,3,10)),
        Map.entry("lucilla/memory_basic_02",new Clock(18,4,14)),
        Map.entry("lucilla/memory_basic_03",new Clock(20,4,6,7,9,11,13,14,16)),
        Map.entry("lucilla/finale",new Clock(24,6,11,15,20)),
        Map.entry("lucilla/finale_echo",new Clock(24,6,11,15,20)),
        Map.entry("lucilla/memory_air",new Clock(24,6)),
        Map.entry("lucilla/memory_counter",new Clock(24,6,20)),
        Map.entry("lucilla/intro",new Clock(24,5)),
        Map.entry("lucilla/intro_memory",new Clock(24,5)),
        Map.entry("lucilla/forget",new Clock(16,5)),
        Map.entry("lucilla/forget_echo",new Clock(16,5)));
    private static final int BLUE=0xff9edaff,WHITE=0xffeef7ff,VIOLET=0xffc6b3ff;
    private static final String PHOTO="mc_wuwa:textures/gui/portraits/lucilla.png";
    private LucillaFilmVfx(){}
    static boolean supports(String a){return CLOCKS.containsKey(a);}
    static double delta(String a,double p){var c=CLOCKS.get(a);if(c==null)return Double.POSITIVE_INFINITY;double d=Double.POSITIVE_INFINITY;for(int h:c.hits())if(Math.abs(p*c.duration()-h)<Math.abs(d))d=p*c.duration()-h;return d;}
    static boolean release(String a){return a.startsWith("lucilla/liberation");}
    static Vertex film(double t,double p){double angle=t*Math.TAU*1.6+p*1.4;double r=1.65+.35*Math.cos(t*Math.TAU);return new Vertex(Math.cos(angle)*r,-.4+t*2.2+.2*Math.sin(angle*2),Math.sin(angle)*r+.4);}
    static Sample sample(String a,double p,double u){var paths=ribbons(a,p);if(paths.isEmpty())return new Sample(0,0,0,WHITE,.2f,Kind.NONE);double n=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)n);var v=path.points().get(Math.min(24,(int)((n-(int)n)*25)));return new Sample(v.side(),v.up(),v.forward(),path.color(),.25f,Kind.DUST);}
    static List<LightPath> ribbons(String a,double p){
        if(!supports(a)||p<=0||p>=1)return List.of();double d=delta(a,p);var out=new ArrayList<LightPath>();
        if(release(a)&&p>.10&&p<.94){
            for(int sign:new int[]{-1,1})curve(out,sign<0?BLUE:VIOLET,.022f,t->{var v=film(t,p);return new Vertex(v.side(),v.up()+sign*.34,v.forward());});
            // Fine wave/thread through the continuous band, not disconnected rectangles.
            curve(out,WHITE,.016f,t->{var v=film(t,p);return new Vertex(v.side(),v.up(),v.forward());});
        }
        if(d<=-2||d>=3)return List.copyOf(out);var contact=new ArrayList<LightPath>();
        boolean memory=a.contains("memory")||a.contains("finale");
        if(a.endsWith("air")){
            curve(contact,WHITE,.05f,t->new Vertex(0,2.7-t*3.4,1.5));
            for(int i=0;i<4;i++){double q=i*Math.PI/2;curve(contact,BLUE,.025f,t->new Vertex(Math.cos(q)*t*1.8,-.6,1.5+Math.sin(q)*t*1.2));}
        }else{
            int frames=memory?3:1;
            for(int i=0;i<frames;i++){
                if(a.startsWith("lucilla/memory_basic_")){
                    var corners=memoryFrame(a,p,i);
                    for(int edge=0;edge<4;edge++){
                        var from=corners.get(edge);var to=corners.get((edge+1)%4);
                        curve(contact,i%2==0?BLUE:VIOLET,.025f,t->new Vertex(from.side()+(to.side()-from.side())*t,from.up()+(to.up()-from.up())*t,from.forward()+(to.forward()-from.forward())*t));
                    }
                    continue;
                }
                double x=(i-(frames-1)*.5)*.85,z=1.5+i*.15;
                rectangle(contact,x,.2,z,.4,.55,i%2==0?BLUE:VIOLET);
            }
            for(int sign:new int[]{-1,1})curve(contact,sign<0?WHITE:BLUE,.035f,t->new Vertex(sign*(-1.5+t*3),-.3+t*1.0,1.8+Math.sin(t*Math.PI)*.4));
            curve(contact,WHITE,.025f,t->new Vertex(0,.15,.5+t*2.7));
        }
        out.addAll(FiveNormalAttackVfx.contactTrails(contact,d));return List.copyOf(out);
    }
    static List<LightSurface> surfaces(String a,double p){
        if(!supports(a)||p<=0||p>=1)return List.of();var out=new ArrayList<LightSurface>();
        if(release(a)&&p>.1&&p<.94){
            // Adjacent segments share exact endpoints. Empty upper/lower slots are real holes.
            for(int i=0;i<32;i++){
                var x=film(i/32d,p);var y=film((i+1)/32d,p);
                face(out,x,y,-.34,-.30,0x90c7beff,"");face(out,x,y,.30,.34,0x90c7beff,"");
                if(i%2==0){face(out,x,y,-.30,-.22,0x80d8d2ff,"");face(out,x,y,.22,.30,0x80d8d2ff,"");}
                face(out,x,y,-.22,.22,i%4==0?0x98ffffff:0x25c0b5ff,i%4==0?PHOTO:"");
            }
            return List.copyOf(out);
        }
        if(Math.abs(delta(a,p))>1.4)return List.of();
        // A plunge is a descending light strike, not a floating photograph.
        if(a.endsWith("air"))return List.of();
        int frames=a.contains("memory")||a.contains("finale")?3:1;
        for(int i=0;i<frames;i++){
            if(a.startsWith("lucilla/memory_basic_")){
                out.add(new LightSurface(memoryFrame(a,p,i),0x55e3eaff,PHOTO));
                continue;
            }
            double x=(i-(frames-1)*.5)*.85,z=1.5+i*.15;
            face(out,new Vertex(x-.4,.2,z),new Vertex(x+.4,.2,z),-.55,.55,0x55e3eaff,PHOTO);
        }
        return List.copyOf(out);
    }
    /** Published memory panel2--4s: tilted frames cross in depth instead of a row of upright photos. */
    private static List<Vertex> memoryFrame(String action,double p,int index){
        int stage=action.charAt(action.length()-1)-'0';
        double lane=index-1,roll=(stage==2?-1:1)*(lane*.65+.2)+(p-.5)*.65;
        double yaw=lane*.55,depth=1.7+lane*.25+(p-.5)*1.4;
        double center=lane*.85*(stage==3?.65:1),height=.2+lane*.15;
        var corners=new ArrayList<Vertex>();
        for(double[] xy:new double[][]{{-.4,-.55},{.4,-.55},{.4,.55},{-.4,.55}}){
            double x=xy[0]*Math.cos(roll)-xy[1]*Math.sin(roll),y=xy[0]*Math.sin(roll)+xy[1]*Math.cos(roll);
            corners.add(new Vertex(center+x*Math.cos(yaw),height+y,depth+x*Math.sin(yaw)));
        }
        return List.copyOf(corners);
    }
    private static void face(List<LightSurface> out,Vertex a,Vertex b,double low,double high,int color,String texture){out.add(new LightSurface(List.of(new Vertex(a.side(),a.up()+low,a.forward()),new Vertex(b.side(),b.up()+low,b.forward()),new Vertex(b.side(),b.up()+high,b.forward()),new Vertex(a.side(),a.up()+high,a.forward())),color,texture));}
    private static void rectangle(List<LightPath> out,double x,double y,double z,double w,double h,int color){
        Vertex[] corners={new Vertex(x-w,y-h,z),new Vertex(x+w,y-h,z),new Vertex(x+w,y+h,z),new Vertex(x-w,y+h,z)};
        for(int i=0;i<4;i++){var a=corners[i];var b=corners[(i+1)%4];curve(out,color,.025f,t->new Vertex(a.side()+(b.side()-a.side())*t,a.up()+(b.up()-a.up())*t,z));}
    }
    private static void curve(List<LightPath> out,int color,float width,DoubleFunction<Vertex> f){var points=new ArrayList<Vertex>();for(int i=0;i<=24;i++)points.add(f.apply(i/24d));out.add(new LightPath(points,color,width));}
}
