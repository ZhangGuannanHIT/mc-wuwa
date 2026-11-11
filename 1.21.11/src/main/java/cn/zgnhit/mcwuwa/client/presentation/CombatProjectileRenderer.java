package cn.zgnhit.mcwuwa.client.presentation;

import cn.zgnhit.mcwuwa.platform.entity.CombatProjectileEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.state.ThrownItemRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Cosmetic carrier follows the real server projectile, including its current velocity.
 * No extra entities, targeting, damage, lifetime or particle emitters are introduced. */
public final class CombatProjectileRenderer extends ThrownItemRenderer<CombatProjectileEntity> {
    private static final Identifier LIGHT=Identifier.fromNamespaceAndPath("mc_wuwa","textures/entity/skill_light.png");
    public record Submitted(int mode,float yaw,float pitch,long tick){}
    public static java.util.function.Consumer<Submitted> observer;
    static final class State extends ThrownItemRenderState {int visual;float yaw,pitch;}
    CombatProjectileRenderer(EntityRendererProvider.Context context){super(context,.18f,true);}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(CombatProjectileEntity entity,ThrownItemRenderState state,float partial){
        super.extractRenderState(entity,state,partial);var out=(State)state;out.visual=entity.isInvisible()?0:entity.visualMode();
        var velocity=entity.getDeltaMovement();
        out.yaw=(float)Math.toDegrees(Math.atan2(velocity.x,velocity.z));
        out.pitch=(float)-Math.toDegrees(Math.atan2(velocity.y,Math.hypot(velocity.x,velocity.z)));
    }
    @Override public void submit(ThrownItemRenderState input,PoseStack poses,SubmitNodeCollector collector,CameraRenderState camera){
        var state=(State)input;
        if(state.visual==0){super.submit(input,poses,collector,camera);return;}
        poses.pushPose();poses.mulPose(Axis.YP.rotationDegrees(state.yaw));poses.mulPose(Axis.XP.rotationDegrees(state.pitch));
        collector.submitCustomGeometry(poses,CombatLightRenderTypes.of(LIGHT),(stack,consumer)->{
            if(state.visual>=3){
                for(var face:ProjectileCarrierGeometry.faces(state.visual))emit(stack,consumer,face.points(),0,face.color());
                return;
            }
            // Crossed thin surfaces retain volume from side and rear views.
            for(int axis=0;axis<2;axis++)for(int glow=0;glow<2;glow++){
                float width=glow==0?.075f:.021f;
                float[][] points=state.visual==1
                    ?new float[][]{{-width,0,-.72f},{width,0,-.72f},{width,0,.55f},{-width,0,.55f}}
                    :new float[][]{{0,0,-.42f},{width,0,0},{0,0,.42f},{-width,0,0}};
                emit(stack,consumer,points,axis,glow==0?0x5490dfff:0xf0edffff);
                if(state.visual==1){
                    emit(stack,consumer,new float[][]{{0,0,.82f},{-.14f,0,.40f},{0,0,.51f},{.14f,0,.40f}},axis,glow==0?0x68b7eaff:0xe8e7ffff);
                }
            }
        });
        poses.popPose();
        if(observer!=null)observer.accept(new Submitted(state.visual,state.yaw,state.pitch,PresentationClient.now()));
    }
    private static void emit(com.mojang.blaze3d.vertex.PoseStack.Pose stack,com.mojang.blaze3d.vertex.VertexConsumer consumer,float[][] points,int axis,int color){
        // The unlit translucent pipeline is already double-sided.
        for(int i=0;i<4;i++){
            var point=points[i];float x=axis==0?point[0]:point[1],y=axis==0?point[1]:point[0];
            consumer.addVertex(stack,x,y,point[2]).setColor((color>>16)&255,(color>>8)&255,color&255,color>>>24)
                .setUv(.5f,.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(stack,0,1,0);
        }
    }
}
