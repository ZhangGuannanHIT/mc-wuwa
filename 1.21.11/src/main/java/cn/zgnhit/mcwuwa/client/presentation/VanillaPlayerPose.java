package cn.zgnhit.mcwuwa.client.presentation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Samples a private vanilla humanoid, without PAL's PlayerModelMixin, for TP blending and return.
 * The returned poses use physical left/right. A caller that mirrors its final author-space pose
 * must inverse-mirror this baseline first; vanilla handedness must not be mirrored a second time.
 */
public final class VanillaPlayerPose {
    private static HumanoidModel<AvatarRenderState> wide;
    private static HumanoidModel<AvatarRenderState> slim;
    // Vanilla pixel coordinates -> this rig: X reversed, Y up, ground at Y=0. A is its own inverse.
    private static final Matrix4f VANILLA_TO_RIG = new Matrix4f().translation(0,24,0).scale(-1,-1,1);
    private static final float DEGREES = (float)(180 / Math.PI);

    private VanillaPlayerPose() {}

    /** Client/render-thread only. Invalid/missing player resources leave the caller's vanilla fallback available. */
    public static Map<String,ActionPresentationRegistry.Pose> sample(AvatarRenderState state,boolean slimSkin) {
        var mesh=ActionPresentationRegistry.mesh(slimSkin?"rigs/player_slim":"rigs/player_wide");
        if(mesh.isEmpty())return Map.of();
        HumanoidModel<AvatarRenderState> model=slimSkin?slim:wide;
        if(model==null) {
            // Do not construct PlayerModel: PAL injects into that subclass and would resample our action.
            model=new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(slimSkin?ModelLayers.PLAYER_SLIM:ModelLayers.PLAYER));
            if(slimSkin)slim=model;else wide=model;
        }
        return sample(model,state,mesh.get());
    }

    /** Resource reload, disconnect and world replacement must discard cached baked models. */
    public static void clear(){wide=null;slim=null;}

    /** Same production conversion with an explicitly supplied plain model; usable by local API tests. */
    static Map<String,ActionPresentationRegistry.Pose> sample(HumanoidModel<AvatarRenderState> model,
            AvatarRenderState state,ActionPresentationRegistry.Mesh rig) {
        // HumanoidModel.setupAnim starts with Model.resetPose and applies the current walk/crouch/item state.
        model.setupAnim(state);
        Map<String,ModelPart> vanillaParts=Map.of("torso",model.body,"head",model.head,
            "right_arm",model.rightArm,"left_arm",model.leftArm,"right_leg",model.rightLeg,"left_leg",model.leftLeg);
        Map<String,Matrix4f> desired=new HashMap<>();
        vanillaParts.forEach((name,part)->desired.put(name,toRigDelta(part)));

        // There is no vanilla pelvis part. Both hip roots move +Z while crouching; carry that common
        // displacement on the pelvis, so the shared waist ring follows the hips rather than remaining upright.
        Vector3f hips=bindDisplacement(model.rightLeg).add(bindDisplacement(model.leftLeg)).mul(.5f);
        desired.put("body",new Matrix4f());desired.put("pelvis",new Matrix4f().translation(hips));

        Map<String,Matrix4f> finalMatrices=new HashMap<>();
        Map<String,ActionPresentationRegistry.Pose> poses=new HashMap<>();
        List<ActionPresentationRegistry.Bone> bones=ActionPresentationRegistry.orderedBones(rig.bones());
        for(var bone:bones) {
            Matrix4f parent=bone.parent()==null||bone.parent().isEmpty()?new Matrix4f():finalMatrices.get(bone.parent());
            Matrix4f world=desired.get(bone.name());
            // Forearms, shins and item anchors have no separate vanilla motion: inherit once, with straight joints.
            if(world==null)world=new Matrix4f(parent);
            Matrix4f local=new Matrix4f(parent).invertAffine().mul(world);
            Vector3f scale=local.getScale(new Vector3f());
            if(!local.isFinite()||scale.x<=0||scale.y<=0||scale.z<=0)
                throw new IllegalArgumentException("Invalid vanilla pose transform for "+bone.name());
            Vector3f rotation=new Matrix4f(local).scale(1/scale.x,1/scale.y,1/scale.z)
                .getEulerAnglesZYX(new Vector3f()).mul(DEGREES).sub(bone.rotation());
            // The renderer uses T(offset)*T(pivot)*R*S*T(-pivot), with a waist pivot unlike vanilla's body.
            Vector3f offset=local.transformPosition(new Vector3f(bone.pivot())).sub(bone.pivot());
            poses.put(bone.name(),new ActionPresentationRegistry.Pose(offset,rotation,scale));
            finalMatrices.put(bone.name(),new Matrix4f(world));
        }
        // Independent vectors are newly allocated on each call. The connected skin intentionally blends the
        // vanilla rigid targets inside joint bands; we claim bone-target equivalence, not identical vanilla cubes.
        return Map.copyOf(poses);
    }

    private static Vector3f bindDisplacement(ModelPart part) {
        PartPose bind=part.getInitialPose();return new Vector3f(bind.x()-part.x,bind.y()-part.y,part.z-bind.z());
    }
    private static Matrix4f toRigDelta(ModelPart part) {
        PartPose bind=part.getInitialPose();
        Matrix4f current=new Matrix4f().translation(part.x,part.y,part.z).rotateZYX(part.zRot,part.yRot,part.xRot)
            .scale(part.xScale,part.yScale,part.zScale);
        Matrix4f inverseBind=new Matrix4f().translation(bind.x(),bind.y(),bind.z()).rotateZYX(bind.zRot(),bind.yRot(),bind.xRot())
            .scale(bind.xScale(),bind.yScale(),bind.zScale()).invertAffine();
        return new Matrix4f(VANILLA_TO_RIG).mul(current).mul(inverseBind).mul(VANILLA_TO_RIG);
    }
}
