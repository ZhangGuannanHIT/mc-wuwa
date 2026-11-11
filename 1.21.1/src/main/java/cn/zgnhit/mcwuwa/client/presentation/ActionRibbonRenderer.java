package cn.zgnhit.mcwuwa.client.presentation;

import cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles;
import com.mojang.blaze3d.vertex.PoseStack;
import cn.zgnhit.mcwuwa.client.presentation.GeometryBuffers;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

/** Continuous unlit silhouettes using the tracked visual's existing lifetime.
 * Halo intensity is alpha, not an opaque darkened RGB strip. */
final class ActionRibbonRenderer {
    private static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath("mc_wuwa","textures/entity/skill_light.png");
    private ActionRibbonRenderer(){}
    static void submit(String action,float progress,float intensity,PoseStack poses,GeometryBuffers collector){submit(action,progress,intensity,0,null,poses,collector);}
    static void submit(String action,float progress,float intensity,float pitch,cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.Vertex target,PoseStack poses,GeometryBuffers collector){
        submit(action,progress,intensity,pitch,target,null,poses,collector);
    }
    static boolean submit(String action,float progress,float intensity,float pitch,cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.Vertex target,Vector3f cameraLocal,PoseStack poses,GeometryBuffers collector){
        if(action==null||action.isEmpty()||intensity<=0)return false;
        var paths=cn.zgnhit.mcwuwa.client.JinhsiPilotVfx.aimedPaths(action,progress,cn.zgnhit.mcwuwa.client.IunoMoonBowVfx.aimedPaths(action,cn.zgnhit.mcwuwa.client.MornyePilotVfx.aimedPaths(action,NewRoleVfxProfiles.ribbons(action,progress),pitch),pitch),target);var surfaces=NewRoleVfxProfiles.surfaces(action,progress);if(paths.isEmpty()&&surfaces.isEmpty())return false;
        float originUp=action.equals("suisui/reserve_dance")||action.equals("cartethyia/gravity_field")||action.equals("cartethyia/gravity_followup_field")||action.equals("hsin/illuminated_word_lock")?0:1.05f;
        float envelope=Math.min(1,progress/.10f)*Math.min(1,(1-progress)/.18f)*intensity;
        if(action.equals("suisui/reserve_dance")||action.equals("ciaccona/recital")||action.equals("galbrena/demon_aura"))envelope=intensity*.65f;
        if(action.equals("phrolova/conducting_throne"))envelope=intensity*.8f;
        if(action.equals("mornye/observation_aura"))envelope=intensity*.45f;
        if(action.equals("mornye/observation_field"))envelope=intensity*.45f;
        if(action.equals("augusta/realm_domain"))envelope=intensity*.7f;
        if(action.equals("denia/erosion_field")||action.equals("iuno/full_moon"))envelope=intensity*(.55f+.15f*(float)Math.sin(progress*Math.TAU));
        final float strength=Math.max(0,envelope);
        // Film photographs use bundled art; ordinary light keeps its existing white-texel path.
        var images=new java.util.LinkedHashMap<String,java.util.List<NewRoleVfxProfiles.LightSurface>>();
        for(var surface:surfaces)if(!surface.texture().isEmpty())images.computeIfAbsent(surface.texture(),key->new java.util.ArrayList<>()).add(surface);
        for(var entry:images.entrySet())collector.submitCustomGeometry(poses,
            CombatLightRenderTypes.of(ResourceLocation.parse(entry.getKey())),(stack,consumer)->{
            for(var surface:entry.getValue()){
                int color=surface.color(),alpha=(int)((color>>>24)*Math.min(1,strength)*surfaceVisibility(surface,cameraLocal,originUp));
                // The light pipeline disables culling. Submitting
                // a reversed copy double-blends the same coplanar light face.
                for(int at=0;at<4;at++){
                    var v=surface.vertices().get(at);
                    consumer.addVertex(stack,(float)v.side(),(float)v.up()+originUp,-(float)v.forward())
                        .setColor((color>>16)&255,(color>>8)&255,color&255,alpha).setUv(at==0||at==3?0:1,at<2?1:0)
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(stack,0,1,0);
                }
            }
        });
        collector.submitCustomGeometry(poses,CombatLightRenderTypes.of(TEXTURE),(stack,consumer)->{
            for(var surface:surfaces){
                if(!surface.texture().isEmpty())continue;
                int color=surface.color(),red=(color>>16)&255,green=(color>>8)&255,blue=color&255;
                int alpha=(int)((color>>>24)*Math.min(1,strength)*surfaceVisibility(surface,cameraLocal,originUp));
                for(int k=0;k<4;k++){
                    var v=surface.vertices().get(k);
                    consumer.addVertex(stack,(float)v.side(),(float)v.up()+originUp,-(float)v.forward()).setColor(red,green,blue,alpha)
                        .setUv(.5f,.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(stack,0,1,0);
                }
            }
            for(var path:paths){
                var points=path.points().stream().map(v->new Vector3f((float)v.side(),(float)v.up()+originUp,-(float)v.forward())).toList();
                var frames=LightRibbonBasis.along(points);
                for(int i=1;i<points.size();i++){
                var first=points.get(i-1);var next=points.get(i);
                if(first.distanceSquared(next)<.000001f)continue;
                for(int glow=0;glow<2;glow++)for(int plane=0;plane<2;plane++){
                    var axis=plane==0?frames[i-1].across():frames[i-1].crossed();
                    var endAxis=plane==0?frames[i].across():frames[i].crossed();
                    var side=new Vector3f(axis).mul(path.width()*(glow==0?3.0f:.65f));
                    var endSide=new Vector3f(endAxis).mul(path.width()*(glow==0?3.0f:.65f));
                    int color=path.color(),red=(color>>16)&255,green=(color>>8)&255,blue=color&255;
                    int alpha=(int)((color>>>24)*Math.min(1,strength)*(glow==0?.30f:.85f)*FrontCameraLightFade.segment(cameraLocal,first,next));
                    if(glow==1){red=(red+255)/2;green=(green+255)/2;blue=(blue+255)/2;}
                    // The light texture is a white texel. Fade the halo in vertex
                    // alpha instead of drawing another hard-edged colored strip.
                    for(int half=0;half<(glow==0?2:1);half++){
                        var edge=new Vector3f(side).mul(half==0?1:-1);
                        var endEdge=new Vector3f(endSide).mul(half==0?1:-1);
                        // Halo begins at the core edge. Drawing it from the
                        // centre overlaps the bright strip at identical depth
                        // and makes sorted transparent segments flicker.
                        var inner=new Vector3f(axis).mul(path.width()*.65f*(half==0?1:-1));
                        var endInner=new Vector3f(endAxis).mul(path.width()*.65f*(half==0?1:-1));
                        var vertices=glow==0
                            ?new Vector3f[]{new Vector3f(first).add(inner),new Vector3f(next).add(endInner),new Vector3f(next).add(endEdge),new Vector3f(first).add(edge)}
                            :new Vector3f[]{new Vector3f(first).sub(side),new Vector3f(next).sub(endSide),new Vector3f(next).add(endSide),new Vector3f(first).add(side)};
                        for(int at=0;at<4;at++){
                            var v=vertices[at];
                            consumer.addVertex(stack,v.x,v.y,v.z).setColor(red,green,blue,glow==0&&at>=2?0:alpha).setUv(.5f,.5f)
                                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(stack,0,1,0);
                        }
                    }
                }
            }
            }
        });
        return true;
    }
    private static float surfaceVisibility(NewRoleVfxProfiles.LightSurface surface,Vector3f camera,float originUp){
        if(camera==null)return 1;
        float visibility=1;
        for(var v:surface.vertices())visibility=Math.min(visibility,FrontCameraLightFade.point(camera,(float)v.side(),(float)v.up()+originUp,-(float)v.forward()));
        return visibility;
    }
}
