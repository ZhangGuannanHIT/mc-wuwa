package cn.zgnhit.mcwuwa.client.presentation;

import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.enums.PlayState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Vector3f;

import java.util.*;

/** All PAL types are confined to this client adapter, never to combat/team code. */
public final class PalPlayerAnimationAdapter {
    private static final Identifier LAYER=Identifier.fromNamespaceAndPath("mc_wuwa","combat_actions");
    private static final String[] BONES={"body","pelvis","torso","head","right_arm","left_arm","right_leg","left_leg","right_forearm","left_forearm","right_shin","left_shin","right_item","left_item"};
    // Client-thread owned. PAL owns the TP layer; only the dedicated FP renderer
    // owns these controllers. Explicit removal avoids a weak-key/strong-value
    // cycle because PlayerAnimationController itself retains its Avatar.
    private static final Map<UUID,BoundController> FIRST_PERSON=new HashMap<>();
    private static int resourceRevision;

    private PalPlayerAnimationAdapter(){}
    public static void initialize(IEventBus modBus){modBus.addListener(PalPlayerAnimationAdapter::setup);}
    private static void setup(FMLClientSetupEvent event){
        event.enqueueWork(()->PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER,1700,avatar->new BoundController(avatar,false)));
    }
    public static void invalidate(){resourceRevision++;clear();}
    /** Drop all standalone FP controllers on disconnect/world replacement. */
    public static void clear(){
        FIRST_PERSON.values().forEach(BoundController::stop);
        FIRST_PERSON.clear();
    }
    /** Drop an actor even if its old entity can no longer be resolved. */
    public static void discard(UUID actorUuid){
        BoundController controller=FIRST_PERSON.remove(actorUuid);
        if(controller!=null)controller.stop();
    }
    public static void stop(Avatar avatar){
        var layer=PlayerAnimationAccess.getPlayerAnimationLayer(avatar,LAYER);
        if(layer instanceof BoundController controller)controller.stop();
        // A late cleanup for the old Avatar must not stop its replacement.
        BoundController firstPerson=FIRST_PERSON.get(avatar.getUUID());
        if(firstPerson!=null && firstPerson.getAvatar()==avatar)discard(avatar.getUUID());
    }
    private static BoundController firstPersonController(Avatar avatar){
        UUID actorUuid=avatar.getUUID();
        BoundController controller=FIRST_PERSON.get(actorUuid);
        if(controller==null || controller.getAvatar()!=avatar){
            discard(actorUuid);
            controller=new BoundController(avatar,true);
            FIRST_PERSON.put(actorUuid,controller);
        }
        return controller;
    }
    public static Optional<Map<String,ActionPresentationRegistry.Pose>> sample(Avatar avatar,ActorPresentationState action,boolean firstPerson,float partialTick){
        if(!ActionPresentationRegistry.available(action.actionId())){stop(avatar);return Optional.empty();}
        BoundController controller;
        if(firstPerson)controller=firstPersonController(avatar);
        else {
            var layer=PlayerAnimationAccess.getPlayerAnimationLayer(avatar,LAYER);
            if(!(layer instanceof BoundController bound))return Optional.empty();
            controller=bound;
        }
        if(!controller.sampleAt(action,PresentationClient.now()+partialTick))return Optional.empty();
        Map<String,ActionPresentationRegistry.Pose> poses=new HashMap<>();
        for(String name:BONES){
            var bone=controller.getBone(name);
            if(bone!=null)poses.put(name,new ActionPresentationRegistry.Pose(new Vector3f(bone.positionX,bone.positionY,bone.positionZ),new Vector3f(bone.rotX,bone.rotY,bone.rotZ).mul((float)(180/Math.PI)),new Vector3f(bone.scaleX,bone.scaleY,bone.scaleZ)));
        }
        return Optional.of(firstPerson?Map.copyOf(poses):toThirdPersonRig(poses));
    }
    /** Boundary between PAL/native model channels and the connected third-person rig. */
    public static Map<String,ActionPresentationRegistry.Pose> toThirdPersonRig(Map<String,ActionPresentationRegistry.Pose> channels){
        // PAL RenderUtil writes rotation directly into native Y-down ModelPart, and negates
        // position.y. Our rig uses (-nativeX, 24-nativeY, nativeZ). Conjugating its
        // ZYX rotation flips X/Y angles. The dedicated FP clips use a separate view basis.
        Map<String,ActionPresentationRegistry.Pose> result=new HashMap<>();
        channels.forEach((name,pose)->result.put(name,new ActionPresentationRegistry.Pose(
            new Vector3f(-pose.position().x,pose.position().y,pose.position().z),
            new Vector3f(-pose.rotation().x,-pose.rotation().y,pose.rotation().z),new Vector3f(pose.scale()))));
        return Map.copyOf(result);
    }

    private static final class BoundController extends PlayerAnimationController {
        private final boolean firstPerson;
        private int loadedRevision=-1;
        private String currentClip="";
        private UUID currentInstance;

        private BoundController(Avatar avatar,boolean firstPerson){
            super(avatar,(controller,data,setter)->PlayState.CONTINUE);
            this.firstPerson=firstPerson;
            for(String name:BONES)registerPlayerAnimBone(name);
            // Our dedicated FP renderer owns the split hand mesh, avoiding a
            // duplicate PAL full-player first-person pass and armor draw.
            setFirstPersonMode(FirstPersonMode.DISABLED);
        }
        @Override public void stop(){
            super.stop();
            for(String name:BONES) {
                var bone=getBone(name);if(bone!=null){bone.setToInitialPose();}
            }
            currentClip="";currentInstance=null;loadedRevision=-1;
        }
        @Override protected void applyCustomPivotPoints(){
            // Preserve local bone channels: SegmentedMeshRenderer applies this
            // hierarchy once. PAL's default would bake parent rotations into
            // child channels, then our model hierarchy would apply them twice.
        }
        @Override public void process(AnimationData data){
            // Standalone FP controllers are only sampled by renderHand. They
            // never enter PAL's avatar stack or its automatic TP process pass.
            if(firstPerson)return;
            if(!(getAvatar() instanceof Player)){stop();return;}
            var action=PresentationClient.state(getAvatar().getUUID());
            if(action.isEmpty()||!ActionPresentationRegistry.available(action.get().actionId())){stop();return;}
            sampleAt(action.get(),PresentationClient.now()+data.getPartialTick());
        }
        private boolean sampleAt(ActorPresentationState action,double serverTime){
            String clip=action.actionId()+ (firstPerson?"/fp":"/tp");
            var animation=PlayerAnimResources.getAnimationOptional(Identifier.fromNamespaceAndPath("mc_wuwa",clip));
            if(animation.isEmpty()) {stop();return false;}
            if(!clip.equals(currentClip)||!action.instanceId().equals(currentInstance)||loadedRevision!=resourceRevision){
                forceAnimationReset();
                setAnimation(RawAnimation.begin().then(animation.get(),animation.get().loopType()),0);
                currentClip=clip;currentInstance=action.instanceId();loadedRevision=resourceRevision;
            }
            float exact=action.progress(serverTime)*animation.get().length();
            exact=Math.min(exact,Math.max(0,animation.get().length()-.001f));
            this.tick=(int)exact;this.startAnimFrom=0;
            // PAL's constructor is (velocity, partialTick, firstPersonPass).
            // Putting the fractional tick in velocity quantizes every pose and
            // can sample the windup instead of a tightly timed contact keyframe.
            super.process(new AnimationData(0,exact-(int)exact,firstPerson));
            return isActive();
        }
    }
}
