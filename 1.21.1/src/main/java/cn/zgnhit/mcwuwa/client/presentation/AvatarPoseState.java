package cn.zgnhit.mcwuwa.client.presentation;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemDisplayContext;

public final class AvatarPoseState {
    public AbstractClientPlayer actor;
    public PlayerSkin skin;
    HumanoidArm mainArm;
    float partial, walkAnimationPos, walkAnimationSpeed, bodyRot, scale;
    int lightCoords;
    boolean isInvisible, isSpectator, showJacket, showHat, showLeftPants, showRightPants, showLeftSleeve, showRightSleeve;
    final HeldItemDraw leftHandItemState = new HeldItemDraw(), rightHandItemState = new HeldItemDraw();

    AvatarPoseState() {}
    public AvatarPoseState(AbstractClientPlayer player, float partial, int light) {
        this.actor=player; this.partial=partial; lightCoords=light;
        skin=player.getSkin(); mainArm=player.getMainArm(); scale=player.getScale();
        walkAnimationPos=player.walkAnimation.position(partial); walkAnimationSpeed=player.walkAnimation.speed(partial);
        bodyRot=Mth.rotLerp(partial,player.yBodyRotO,player.yBodyRot);
        isInvisible=player.isInvisible(); isSpectator=player.isSpectator();
        showHat=player.isModelPartShown(PlayerModelPart.HAT); showJacket=player.isModelPartShown(PlayerModelPart.JACKET);
        showLeftSleeve=player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE); showRightSleeve=player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
        showLeftPants=player.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG); showRightPants=player.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG);
        boolean right=mainArm==HumanoidArm.RIGHT;
        leftHandItemState.set(right?player.getOffhandItem():player.getMainHandItem(),ItemDisplayContext.THIRD_PERSON_LEFT_HAND,player);
        rightHandItemState.set(right?player.getMainHandItem():player.getOffhandItem(),ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,player);
    }
}
