package cn.zgnhit.mcwuwa.client.presentation;
import com.mojang.blaze3d.vertex.PoseStack;
import cn.zgnhit.mcwuwa.client.presentation.GeometryBuffers;
import org.joml.Vector3f;
import java.util.*;
/** Thin decorative support attaches to the evaluated hooked leg, never an independent body part. */
final class CamellyaVineRenderer {
 private CamellyaVineRenderer(){}
 static void submit(SegmentedMeshRenderer.Prepared mesh,PoseStack poses,GeometryBuffers collector){
  var foot=mesh.bonePoint("left_shin",-2,0,0);if(foot.isEmpty())return;
  for(int side:new int[]{-1,1}){
   var points=new ArrayList<Vector3f>();var start=foot.get();
   for(int i=0;i<=16;i++){
    float t=i/16f;points.add(new Vector3f(start).add(side*.10f*t+(float)Math.sin(t*6)*.025f,2.1f*t,.08f*t));
   }
   SkillLightRenderer.submit(new SkillLightRenderer.Ribbon(points,.012f,0xc8b72c45,true),poses,collector);
  }
 }
}
