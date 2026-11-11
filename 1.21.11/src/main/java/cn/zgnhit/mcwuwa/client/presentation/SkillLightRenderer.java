package cn.zgnhit.mcwuwa.client.presentation;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;
import java.util.List;

/** Continuous decorative light, submitted with the owned construct rather than particle density.
 * Fullbright surfaces have no damage/collision and respect ordinary depth occlusion. */
final class SkillLightRenderer {
    private static final Identifier TEXTURE=Identifier.fromNamespaceAndPath("mc_wuwa","textures/entity/skill_light.png");
    record Ribbon(List<Vector3f> points,float width,int color,boolean upright) {
        Ribbon{points=points.stream().map(Vector3f::new).toList();}
    }
    private SkillLightRenderer(){ }
    static void submit(Ribbon ribbon,PoseStack poses,SubmitNodeCollector collector){
        if(ribbon==null||ribbon.points.size()<2)return;
        collector.submitCustomGeometry(poses,CombatLightRenderTypes.of(TEXTURE),(stack,consumer)->{
            int color=ribbon.color,a=color>>>24,r=(color>>16)&255,g=(color>>8)&255,b=color&255;
            var frames=LightRibbonBasis.along(ribbon.points);
            for(int i=1;i<ribbon.points.size();i++){
                var first=ribbon.points.get(i-1);var next=ribbon.points.get(i);var direction=new Vector3f(next).sub(first);
                if(direction.lengthSquared()<.00001f)continue;
                for(int plane=0;plane<(ribbon.upright?2:1);plane++){
                Vector3f side=new Vector3f(plane==0?frames[i-1].across():frames[i-1].crossed()).mul(ribbon.width);
                Vector3f endSide=new Vector3f(plane==0?frames[i].across():frames[i].crossed()).mul(ribbon.width);
                var vertices=new Vector3f[]{new Vector3f(first).sub(side),new Vector3f(next).sub(endSide),new Vector3f(next).add(endSide),new Vector3f(first).add(side)};
                // The shared light pipeline renders both sides.
                for(int at=0;at<4;at++){
                    var v=vertices[at];float u=at==0||at==3?0:1,vv=at<2?0:1;
                    consumer.addVertex(stack,v.x,v.y,v.z).setColor(r,g,b,a).setUv(u,vv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(stack,0,1,0);
                }
                }
            }
        });
    }
}
