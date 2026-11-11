package cn.zgnhit.mcwuwa.client.presentation;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.List;

/** The original liberation demo shows a star chart inside the realm, not just its perimeter.
 * Coordinates share the construct's two-block authored radius; no gameplay area is changed. */
final class ShoreDomainRenderer {
    private static final Identifier WHITE=Identifier.fromNamespaceAndPath("mc_wuwa","textures/entity/skill_light.png");
    private record Quad(float[] vertices,int alpha,boolean white,boolean fadeTop) { }
    private static final List<Quad> CHART=chart();
    private ShoreDomainRenderer(){ }

    static void submit(String model,float intensity,float age,PoseStack poses,SubmitNodeCollector collector){
        if(!model.startsWith("shorekeeper_domain_")||intensity<=0)return;
        int color=model.endsWith("gold")?0xffdf8a:model.endsWith("purple")?0xc1a0ff:0x75bbff;
        // Ease the entire chart in with the cast. A very small shimmer keeps the field alive.
        float opacity=Math.min(1,age/8f)*intensity*(.94f+.06f*(float)Math.sin(age*.045));
        // In MC26.1.2 the eyes pass uses TRANSLUCENT blending and disables depth
        // writes. Supply actual alpha, not the old additive premultiplied RGB:
        // opaque dark vertices would cover the terrain instead of fading out.
        collector.submitCustomGeometry(poses,RenderTypes.eyes(WHITE),(stack,consumer)->{
            for(var quad:CHART){
                int red=quad.white?220:(color>>16)&255;
                int green=quad.white?239:(color>>8)&255;
                int blue=quad.white?255:color&255;
                for(int face=0;face<2;face++)for(int index=0;index<4;index++){
                    int corner=face==0?index:3-index,at=corner*3;
                    int alpha=(int)(quad.alpha*opacity*(quad.fadeTop&&corner>=2?0:1));
                    consumer.addVertex(stack,quad.vertices[at],quad.vertices[at+1],quad.vertices[at+2])
                        .setColor(red,green,blue,alpha).setUv(.5f,.5f)
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(stack,0,1,0);
                }
            }
        });
    }

    private static List<Quad> chart(){
        var quads=new ArrayList<Quad>();
        // Light underlay: terrain remains readable through the floor, even in first person.
        for(int i=0;i<96;i++){
            double a=i*Math.TAU/96,b=(i+1)*Math.TAU/96;
            quads.add(new Quad(new float[]{0,.004f,0,(float)Math.cos(a)*2,.004f,(float)Math.sin(a)*2,
                (float)Math.cos(b)*2,.004f,(float)Math.sin(b)*2,0,.004f,0},22,false,false));
        }
        for(float radius:new float[]{.30f,.73f,1.18f,1.66f,1.97f})circle(quads,radius,.008f,.0035f,80,false);
        // Broken star polygons and nested small rings spread across the full floor.
        for(int sector=0;sector<8;sector++){
            double angle=sector*Math.TAU/8;
            float[] previous=null;
            for(int i=0;i<6;i++){
                double theta=angle+(i%2==0?-.12:.16),radius=.24+i*.29;
                float x=(float)(Math.cos(theta)*radius),z=(float)(Math.sin(theta)*radius);
                if(previous!=null)line(quads,previous[0],previous[1],x,z,.011f,.0035f,135,false);
                star(quads,x,.013f,z,.018f+(i%3)*.006f,200);
                if(i==2||i==4)smallCircle(quads,x,z,.065f,.012f,.0025f,105);
                previous=new float[]{x,z};
            }
        }
        // Fine independent points between the constellation branches, deterministically placed.
        for(int i=0;i<72;i++){
            double angle=i*2.399963229728653,radius=1.94*Math.sqrt((i+.5)/72);
            star(quads,(float)(Math.cos(angle)*radius),.016f,(float)(Math.sin(angle)*radius),.006f+(i%4)*.002f,125+i%3*25);
        }
        // The upper realm is another broad circular star chart, not just isolated
        // overhead lines. Keep its interior transparent when looking up from combat.
        for(var floor:List.copyOf(quads)){
            float[] vertices=floor.vertices.clone();
            for(int i=1;i<vertices.length;i+=3)vertices[i]+=1.05f;
            quads.add(new Quad(vertices,floor.alpha==22?8:floor.alpha*3/4,floor.white,false));
        }
        circle(quads,2,1.06f,.006f,150,false);
        circle(quads,1.92f,1.06f,.003f,120,false);
        for(int i=0;i<48;i++){
            double angle=i*Math.TAU/48;
            float c=(float)Math.cos(angle),s=(float)Math.sin(angle);
            line(quads,c*(i%4==0?1.82f:1.88f),s*(i%4==0?1.82f:1.88f),c*1.96f,s*1.96f,1.06f,.0025f,135,false);
        }
        // Soft perimeter light rises between the two discs without an opaque wall.
        for(int i=0;i<96;i++){
            double a=i*Math.TAU/96,b=(i+1)*Math.TAU/96;
            float ax=(float)Math.cos(a)*1.99f,az=(float)Math.sin(a)*1.99f;
            float bx=(float)Math.cos(b)*1.99f,bz=(float)Math.sin(b)*1.99f;
            quads.add(new Quad(new float[]{ax,.015f,az,bx,.015f,bz,bx,1.05f,bz,ax,1.05f,az},30,false,true));
        }
        for(int i=0;i<12;i++){
            double a=i*Math.TAU/12,b=(i+1)*Math.TAU/12;
            float ax=(float)Math.cos(a)*1.77f,az=(float)Math.sin(a)*1.77f;
            float bx=(float)Math.cos(b)*1.77f,bz=(float)Math.sin(b)*1.77f;
            line(quads,ax,az,bx,bz,1.065f,.002f,65,false);
            star(quads,ax,1.065f,az,.013f,135);
        }
        return List.copyOf(quads);
    }
    private static void circle(List<Quad> list,float radius,float y,float width,int alpha,boolean white){
        for(int i=0;i<96;i++){
            double a=i*Math.TAU/96,b=(i+1)*Math.TAU/96;
            line(list,(float)Math.cos(a)*radius,(float)Math.sin(a)*radius,(float)Math.cos(b)*radius,(float)Math.sin(b)*radius,y,width,alpha,white);
        }
    }
    private static void smallCircle(List<Quad> list,float x,float z,float radius,float y,float width,int alpha){
        for(int i=0;i<20;i++){
            double a=i*Math.TAU/20,b=(i+1)*Math.TAU/20;
            line(list,x+(float)Math.cos(a)*radius,z+(float)Math.sin(a)*radius,x+(float)Math.cos(b)*radius,z+(float)Math.sin(b)*radius,y,width,alpha,false);
        }
    }
    private static void star(List<Quad> list,float x,float y,float z,float size,int alpha){
        line(list,x-size,z,x+size,z,y,size*.12f,alpha,true);
        line(list,x,z-size,x,z+size,y,size*.12f,alpha,true);
    }
    private static void line(List<Quad> list,float ax,float az,float bx,float bz,float y,float width,int alpha,boolean white){
        float dx=bx-ax,dz=bz-az,length=(float)Math.sqrt(dx*dx+dz*dz);
        float sx=-dz/length*width,sz=dx/length*width;
        list.add(new Quad(new float[]{ax-sx,y,az-sz,bx-sx,y,bz-sz,bx+sx,y,bz+sz,ax+sx,y,az+sz},alpha,white,false));
    }
}
