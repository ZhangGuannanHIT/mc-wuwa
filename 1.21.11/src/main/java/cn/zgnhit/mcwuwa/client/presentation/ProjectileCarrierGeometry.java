package cn.zgnhit.mcwuwa.client.presentation;

import java.util.*;

/** Small flight silhouettes, scaled for MC; the carrier's actual velocity supplies orientation. */
public final class ProjectileCarrierGeometry {
    private ProjectileCarrierGeometry(){}
    public record Face(float[][] points,int color){}
    // These profiles never animate; build once instead of allocating/trigging for every rendered projectile frame.
    private static final List<Face> GOLD_LIGHT=lathe(new float[][]{{-.62f,0},{-.22f,.045f},{.18f,.10f},{.52f,0}},6,0xb8ffe4a0,0x70fff4cc);
    private static final List<Face> TIDE_DROP=lathe(new float[][]{{-.60f,0},{-.25f,.045f},{.02f,.12f},{.18f,.15f},{.32f,.09f},{.37f,0}},8,0xb8998fea,0x887bd9ed);
    private static final List<Face> CRYSTAL=lathe(new float[][]{{-.55f,0},{-.25f,.08f},{.18f,.10f},{.48f,0}},4,0xd0f6d9ed,0x88ef75ad);
    private static final List<Face> JADE_SWORD=sword();
    private static final List<Face> SPECTRAL_BOLT=spectrum();
    private static final List<Face> TWILIGHT_BOLT=lathe(new float[][]{{-.68f,0},{-.30f,.06f},{.05f,.12f},{.35f,.09f},{.53f,0}},6,0xe885cfff,0xc0ff9bb7);
    private static final List<Face> MUSICAL_BOLT=music();
    private static final List<Face> DEMON_BOLT=demon();
    private static final List<Face> INK_FEATHER=feather();
    private static final List<Face> PUPPET_ARC=lightning();
    private static final List<Face> DREAM_BUBBLE=bubble();
    private static final List<Face> SCARLET_WAVE=crescent(0xc0ed386a,0x98ff83bc);
    private static final List<Face> RED_SEAL=seal(0xc8ff677e,0x90ffbda5);
    private static final List<Face> DRAGON_LIGHT=lathe(new float[][]{{-.90f,0},{-.42f,.06f},{.08f,.13f},{.55f,.055f},{.88f,0}},8,0xd8fff4d2,0xa0c8ffff);
    private static final List<Face> FROST_EDGE=crescent(0xc88fdfff,0xa0f0fbff);
    private static final List<Face> STAR_BUTTERFLY=butterfly();
    private static final List<Face> WOLF_WAVE=crescent(0xd0ff553e,0xa8ffca72);
    private static final List<Face> GOLD_SEAL=seal(0xd8ffd48c,0xa09fffe3);
    public static List<Face> faces(int mode){
        return switch(mode){
            // Golden light with a long needle, rounded purple tide drop, faceted pink crystal.
            case 3 -> GOLD_LIGHT;
            case 4 -> TIDE_DROP;
            case 5 -> CRYSTAL;
            case 6 -> JADE_SWORD;
            case 7 -> SPECTRAL_BOLT;
            case 8 -> TWILIGHT_BOLT;
            case 9 -> MUSICAL_BOLT;
            case 10 -> DEMON_BOLT;
            case 11 -> INK_FEATHER;
            case 12 -> PUPPET_ARC;
            case 13 -> DREAM_BUBBLE;
            case 14 -> SCARLET_WAVE;
            case 15 -> RED_SEAL;
            case 16 -> DRAGON_LIGHT;
            case 17 -> FROST_EDGE;
            case 18 -> STAR_BUTTERFLY;
            case 19 -> WOLF_WAVE;
            case 20 -> GOLD_SEAL;
            default -> List.of();
        };
    }
    private static List<Face> bubble(){
        var result=new ArrayList<>(lathe(new float[][]{{-.65f,0},{-.30f,.10f},{0,.30f},{.30f,.10f},{.48f,0}},10,0x4888dfff,0x38ffa8dc));
        for(int plane=0;plane<3;plane++)ring(result,.31f,.018f,plane,plane==1?0xc0ffa8dc:0xb088dfff);
        return List.copyOf(result);
    }
    private static List<Face> crescent(int edge,int glow){
        var result=new ArrayList<Face>();
        // Swept light is a thin solid crescent, not a weapon or a screen-facing item.
        for(int i=0;i<16;i++){
            double a=-1.8+i*3.6/16,b=-1.8+(i+1)*3.6/16;
            float x0=(float)Math.sin(a)*.62f,z0=(float)Math.cos(a)*.85f-.12f;
            float x1=(float)Math.sin(b)*.62f,z1=(float)Math.cos(b)*.85f-.12f;
            result.add(new Face(new float[][]{{x0,-.07f,z0},{x1,-.07f,z1},{x1,.07f,z1},{x0,.07f,z0}},edge));
            result.add(new Face(new float[][]{{x0,-.07f,z0},{x1,-.07f,z1},{x1*.83f,0,z1-.13f},{x0*.83f,0,z0-.13f}},glow));
            result.add(new Face(new float[][]{{x0,.07f,z0},{x1,.07f,z1},{x1*.83f,0,z1-.13f},{x0*.83f,0,z0-.13f}},glow));
        }
        result.addAll(lathe(new float[][]{{-.66f,0},{-.20f,.04f},{.32f,0}},4,edge,glow));
        return List.copyOf(result);
    }
    private static List<Face> seal(int bright,int shaded){
        var result=new ArrayList<>(lathe(new float[][]{{-.65f,0},{-.22f,.06f},{.32f,.09f},{.55f,0}},4,bright,shaded));
        for(int side:new int[]{-1,1}){
            box(result,side*.23f-.025f,-.25f,-.04f,side*.23f+.025f,.25f,.04f,bright);
            box(result,-.25f,side*.23f-.025f,-.04f,.25f,side*.23f+.025f,.04f,bright);
        }
        box(result,-.10f,-.11f,-.06f,.10f,.11f,.06f,shaded);
        return List.copyOf(result);
    }
    private static List<Face> butterfly(){
        var result=new ArrayList<>(lathe(new float[][]{{-.62f,0},{-.22f,.055f},{.25f,.06f},{.50f,0}},6,0xd8fff1ba,0x98a6dfff));
        for(int side:new int[]{-1,1})for(int wing=0;wing<2;wing++){
            float y=wing==0?.29f:-.22f;
            float[][] a={{side*.025f,0,0},{side*.35f,y,.12f},{side*.27f,y*.35f,-.24f},{side*.055f,-.025f,-.12f}};
            float[][] b=new float[4][3];for(int i=0;i<4;i++)b[i]=new float[]{a[i][0],a[i][1],a[i][2]+.035f};
            result.add(new Face(a,0x8895ddff));result.add(new Face(b,0x98fff1ba));
            for(int i=0;i<4;i++)result.add(new Face(new float[][]{a[i],a[(i+1)%4],b[(i+1)%4],b[i]},0xb8e5f6ff));
        }
        return List.copyOf(result);
    }
    private static void ring(List<Face> out,float radius,float halfWidth,int plane,int color){
        for(int i=0;i<20;i++){
            float[][] points=new float[4][3];
            for(int j=0;j<4;j++){
                double angle=(i+(j==1||j==2?1:0))*Math.TAU/20;float r=radius+(j<2?-halfWidth:halfWidth);
                float x=(float)Math.cos(angle)*r,y=(float)Math.sin(angle)*r;
                points[j]=plane==0?new float[]{x,y,0}:plane==1?new float[]{x,0,y}:new float[]{0,x,y};
            }
            out.add(new Face(points,color));
        }
    }
    private static List<Face> feather(){
        var result=new ArrayList<>(lathe(new float[][]{{-.78f,0},{-.25f,.045f},{.35f,.06f},{.72f,0}},4,0xd8fff4ed,0x8889e7db));
        // Reviewed pink/jade brush feathers sweep back from a white central ridge.
        for(int side:new int[]{-1,1})for(int barb=0;barb<4;barb++){
            float z=.34f-barb*.20f,w=.28f-barb*.035f;
            float[][] front={{0,-.025f,z},{side*w,.075f,z-.26f},{side*w*.72f,.07f,z-.39f},{0,-.025f,z-.10f}};
            float[][] back={{0,.025f,z},{side*w,.12f,z-.26f},{side*w*.72f,.115f,z-.39f},{0,.025f,z-.10f}};
            result.add(new Face(front,side<0?0xb889e7db:0xb8ffb8dc));result.add(new Face(back,0xa0fff4ed));
            for(int i=0;i<4;i++)result.add(new Face(new float[][]{front[i],front[(i+1)%4],back[(i+1)%4],back[i]},0x70fff4ed));
        }
        return List.copyOf(result);
    }
    private static List<Face> lightning(){
        var result=new ArrayList<Face>();
        for(int i=0;i<7;i++){
            float z=-.72f+i*.20f,x=(i%2==0?-1:1)*.10f,next=-x;
            for(int plane=0;plane<2;plane++){
                float w=plane==0?.025f:.05f;
                result.add(new Face(plane==0?new float[][]{{x-w,0,z},{x+w,0,z},{next+w,0,z+.20f},{next-w,0,z+.20f}}
                    :new float[][]{{0,x-w,z},{0,x+w,z},{0,next+w,z+.20f},{0,next-w,z+.20f}},plane==0?0xe8ffd5f4:0xa8bb8aff));
            }
        }
        return List.copyOf(result);
    }
    private static List<Face> demon(){
        var result=new ArrayList<>(lathe(new float[][]{{-.75f,0},{-.32f,.09f},{.18f,.11f},{.65f,0}},6,0xd085cfff,0xa0bb8aff));
        // Three short cyan edges echo the reviewed claw motif around a violet core.
        for(int lane=-1;lane<=1;lane++){
            float x=lane*.14f;
            box(result,x-.018f,-.025f,-.42f,x+.018f,.025f,.40f,0xc0cfffff);
        }
        return List.copyOf(result);
    }
    private static List<Face> sword(){
        var result=new ArrayList<>(lathe(new float[][]{{-.75f,0},{-.40f,.045f},{-.22f,.11f},{.40f,.08f},{.85f,0}},4,0xd07ce8fa,0x88c8ffff));
        // A narrow blade ridge with a short crossguard, not a second item model.
        box(result,-.23f,-.035f,-.25f,.23f,.035f,-.18f,0xc0c8ffff);
        return List.copyOf(result);
    }
    private static List<Face> spectrum(){
        var result=new ArrayList<Face>();int[] colors={0xc07ef6df,0xc0ff9bd3,0xc0a8cfff};
        for(int lane=0;lane<3;lane++){
            float x=(lane-1)*.085f;
            for(var face:lathe(new float[][]{{-.65f,0},{-.28f,.055f},{.20f,.055f},{.48f,0}},4,colors[lane],0xb8ffffff)){
                float[][] points=new float[4][3];
                for(int i=0;i<4;i++)points[i]=new float[]{face.points()[i][0]+x,face.points()[i][1],face.points()[i][2]};
                result.add(new Face(points,face.color()));
            }
        }
        return List.copyOf(result);
    }
    private static List<Face> music(){
        var result=new ArrayList<>(lathe(new float[][]{{-.6f,0},{-.18f,.11f},{.20f,.09f},{.48f,0}},6,0xb886f4de,0x78ffdc89));
        // Raised gold note attached to the mint bolt; all sides retain thickness.
        box(result,.05f,.04f,-.14f,.095f,.34f,-.09f,0xd8ffdc89);
        box(result,.05f,.29f,-.14f,.22f,.34f,-.09f,0xd8ffdc89);
        return List.copyOf(result);
    }
    private static void box(List<Face> out,float x0,float y0,float z0,float x1,float y1,float z1,int color){
        float[][] p={{x0,y0,z0},{x1,y0,z0},{x1,y1,z0},{x0,y1,z0},{x0,y0,z1},{x1,y0,z1},{x1,y1,z1},{x0,y1,z1}};
        for(int[] f:new int[][]{{0,1,2,3},{4,7,6,5},{0,4,5,1},{3,2,6,7},{0,3,7,4},{1,5,6,2}})
            out.add(new Face(new float[][]{p[f[0]],p[f[1]],p[f[2]],p[f[3]]},color));
    }
    private static List<Face> lathe(float[][] rings,int sides,int bright,int shaded){
        List<Face> faces=new ArrayList<>();
        for(int r=0;r<rings.length-1;r++)for(int i=0;i<sides;i++){
            double a=2*Math.PI*i/sides,b=2*Math.PI*(i+1)/sides;
            faces.add(new Face(new float[][]{point(rings[r],a),point(rings[r],b),point(rings[r+1],b),point(rings[r+1],a)},i%2==0?bright:shaded));
        }
        return List.copyOf(faces);
    }
    private static float[] point(float[] ring,double angle){
        return new float[]{ring[1]*(float)Math.cos(angle),ring[1]*(float)Math.sin(angle),ring[0]};
    }
}
