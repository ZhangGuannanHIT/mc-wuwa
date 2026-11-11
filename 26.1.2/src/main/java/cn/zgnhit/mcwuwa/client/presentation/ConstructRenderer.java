package cn.zgnhit.mcwuwa.client.presentation;

import java.util.Map;

import cn.zgnhit.mcwuwa.platform.entity.ConstructEntity;
import cn.zgnhit.mcwuwa.platform.entity.ConstructVisual;
import cn.zgnhit.mcwuwa.platform.entity.ModEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** All bundled model ids share a real world renderer, with per-model geometry and animation. */
public final class ConstructRenderer<T extends net.minecraft.world.entity.Entity & ConstructVisual> extends EntityRenderer<T,ConstructRenderer.State> {
    private static final Map<net.minecraft.world.entity.Entity,java.util.List<org.joml.Vector3f>> SCAR_PATHS=new java.util.WeakHashMap<>();
    /** Optional read-only evidence of a real render submission; unset outside the opt-in client probe. */
    public record Submitted(String model,String animation,String owner,int vertices,boolean attachedWeapon,long tick) { }
    public record LightSubmitted(String model,String owner,int points,float worldLength,float worldHalfWidth,boolean crossedPlanes,long tick,net.minecraft.world.phys.Vec3 endpoint){ }
    public static java.util.function.Consumer<Submitted> observer;
    public static java.util.function.BiConsumer<String,String> actionLightObserver;
    public static java.util.function.Consumer<LightSubmitted> lightObserver;
    public static final class State extends EntityRenderState {
        SegmentedMeshRenderer.Prepared mesh,weapon;Identifier texture;float scale,yaw,deathRoll;org.joml.Matrix4f weaponTransform;
        net.minecraft.client.renderer.item.ItemStackRenderState heldItem,floatingInstrument;
        String model,animation,owner;
        float visualAge,aimPitch;
        String actionLight="";float actionProgress;
        SkillLightRenderer.Ribbon light;
        cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.Vertex aimTarget;
    }
    public ConstructRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=0;}
    private static String owner(net.minecraft.world.entity.Entity entity){
        if(entity instanceof ConstructEntity construct)return construct.ownerUuid();
        if(entity instanceof cn.zgnhit.mcwuwa.platform.entity.HecateEntity hecate)return hecate.ownerUuid();
        if(entity instanceof cn.zgnhit.mcwuwa.platform.entity.SurveyMotorcycleEntity bike&&bike.getControllingPassenger()!=null)return bike.getControllingPassenger().getUUID().toString();
        return "";
    }
    public static void register(EntityRenderersEvent.RegisterRenderers event){event.registerEntityRenderer(ModEntities.CONSTRUCT.get(),ConstructRenderer::new);event.registerEntityRenderer(ModEntities.HECATE.get(),ConstructRenderer::new);event.registerEntityRenderer(ModEntities.SURVEY_MOTORCYCLE.get(),ConstructRenderer::new);
        ModEntities.ECHO_BOSSES.values().forEach(type -> event.registerEntityRenderer(type.get(), ConstructRenderer::new));
    }
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(T entity,State state,float partial) {
        super.extractRenderState(entity,state,partial);state.mesh=null;state.texture=null;state.weapon=null;state.weaponTransform=null;state.heldItem=null;state.floatingInstrument=null;state.light=null;state.actionLight="";
        state.scale=entity.visualScale();
        // Fixed cast anchors retain their spawn heading; moving creatures and
        // vehicles use the same partial tick as their interpolated position.
        state.yaw=entity instanceof ConstructEntity?entity.getYRot():entity.getYRot(partial);
        state.deathRoll=entity instanceof cn.zgnhit.mcwuwa.platform.entity.EchoBossEntity boss&&!boss.isAlive()?Math.clamp((boss.deathTime+partial)/20f,0,1)*90:0;
        state.model=entity.modelId();state.animation=entity.animation();state.owner=owner(entity);
        var aimOwner=state.owner.isEmpty()?null:entity.level().getPlayerByUUID(java.util.UUID.fromString(state.owner));
        state.aimPitch=aimOwner==null?0:aimOwner.getViewXRot(partial);
        state.aimTarget=null;
        if(entity instanceof ConstructEntity construct&&construct.followsOwner()&&!state.owner.isEmpty()){
            var actor=entity.level().getPlayerByUUID(java.util.UUID.fromString(state.owner));
            if(actor!=null){
                // A server-positioned follower lags behind a rapidly falling
                // local player. Draw on that player's same-frame anchor instead.
                var anchor=actor.getPosition(partial).add(construct.visualOwnerOffset(actor.getYRot(partial)));
                state.x=anchor.x;state.y=anchor.y;state.z=anchor.z;
                state.yaw=actor.getYRot(partial);
            }
        }
        if(aimOwner!=null&&state.model.startsWith("skill_light_jinhsi__")){
            var end=cn.zgnhit.mcwuwa.client.AttackVisualAim.endpoint(aimOwner,partial,12);
            var local=new org.joml.Vector3f((float)(end.x-state.x),(float)(end.y-state.y),(float)(end.z-state.z))
                .rotateY((float)Math.toRadians(state.yaw-180)).div(state.scale);
            state.aimTarget=new cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.Vertex(local.x,local.y-1.05,-local.z);
        }
        state.visualAge=(float)Math.max(0,PresentationClient.now()+partial-entity.startTick());
        double phase=Math.clamp((PresentationClient.now()+partial-entity.startTick())/entity.durationTicks(),0,1);
        if(state.model.equals("phoebe_refraction_ray")&&entity instanceof ConstructEntity ray){
            var delta=ray.visualRayVector();
            // Entity spawn can arrive one frame before tracked contact data.
            // Do not submit a zero-length flash while that packet catches up.
            if(delta.lengthSqr()<1e-8)return;
            var end=FixedLightRay.local((float)delta.x,(float)delta.y,(float)delta.z,state.yaw,state.scale);
            int alpha=(int)(225*(1-phase)*lightIntensity(state.owner));
            state.light=new SkillLightRenderer.Ribbon(java.util.List.of(new org.joml.Vector3f(),end),.085f,(alpha<<24)|0xffecae,true);
            return;
        }
        if(state.model.equals("mornye_observation")){
            // Keep the existing authoritative field lifetime/center; replace only its obsolete solid visual.
            state.actionLight="mornye/observation_field";state.actionProgress=(state.visualAge%80)/80;return;
        }
        if(state.model.startsWith("skill_light_")){
            String encoded=state.model.substring("skill_light_".length());int separator=encoded.indexOf("__");
            if(separator>0)state.actionLight=encoded.substring(0,separator)+"/"+encoded.substring(separator+2);
            state.actionProgress=state.actionLight.endsWith("/demon_aura")||state.actionLight.endsWith("/observation_aura")
                ||state.actionLight.equals("augusta/realm_domain")||state.actionLight.equals("denia/erosion_field")||state.actionLight.equals("iuno/full_moon")?(state.visualAge%40)/40:(float)phase;
            double instrumentPhase=cn.zgnhit.mcwuwa.client.MornyePilotVfx.liberationPhase(phase);
            if(state.actionLight.equals("mornye/liberation")&&instrumentPhase>.08&&instrumentPhase<.32
                ||state.actionLight.equals("mornye/basic_04")&&phase>.15&&phase<.88
                ||state.actionLight.equals("mornye/air")&&phase>.02&&phase<.85
                ||state.actionLight.equals("mornye/dodge_counter")&&phase>.08&&phase<.90
                ||state.actionLight.equals("mornye/observation_aura")){
                var game=net.minecraft.client.Minecraft.getInstance();
                var player=game.level==null||state.owner.isEmpty()?null:game.level.getPlayerByUUID(java.util.UUID.fromString(state.owner));
                if(player!=null){
                    state.floatingInstrument=new net.minecraft.client.renderer.item.ItemStackRenderState();
                    game.getItemModelResolver().updateForLiving(state.floatingInstrument,
                        cn.zgnhit.mcwuwa.platform.ModItems.WEAPONS.get(cn.zgnhit.mcwuwa.core.CharacterId.MORNYE).get().getDefaultInstance(),
                        net.minecraft.world.item.ItemDisplayContext.NONE,player);
                }
            }
            return;
        }
        // These are mutually exclusive: switch-out replaces the microphone
        // with the singer at the same field anchor, retaining its staff lights.
        if(state.model.equals("ciaccona_microphone")||state.model.equals("ciaccona_singer_ensemble")){
            state.actionLight="ciaccona/recital";state.actionProgress=(state.visualAge%80)/80;
        }
        if(entity.modelId().equals("skill_cart_scar")) {
            if(phase<.25)return; // R2 first contact is 12/48 ticks, matching its held downstroke.
            var points=SCAR_PATHS.computeIfAbsent(entity,key->{var path=new java.util.ArrayList<org.joml.Vector3f>();var forward=net.minecraft.world.phys.Vec3.directionFromRotation(0,entity.getYRot());
            var side=new net.minecraft.world.phys.Vec3(-forward.z,0,forward.x);var base=entity.position().add(side.scale(-.9));
            for(int i=0;i<=52;i++){
                double distance=.6+i*.5;var at=base.add(forward.scale(distance));
                var floor=entity.level().clip(new net.minecraft.world.level.ClipContext(at.add(0,3,0),at.add(0,-16,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,entity));
                if(floor.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK)break;
                // Entity root rotation converts negative localZ into the owner's forward direction.
                path.add(new org.joml.Vector3f(-.9f,(float)(floor.getLocation().y-entity.getY()+.035),-(float)distance));
            }
            return java.util.List.copyOf(path);});
            int alpha=(int)(235*Math.min(1,(1-phase)/.2)*lightIntensity(state.owner));state.light=new SkillLightRenderer.Ribbon(points,.34f,(alpha<<24)|0x81ffdc,true);return;
        }
        String name=entity.modelId();String asset=switch(name){
            case "cartethyia_inherited_blade"->switch(entity.animation()){
                case "human_right"->"cartethyia_authority_human";
                case "divine_intent"->"cartethyia_authority_divine";
                case "outsider_power"->"cartethyia_authority_outsider";
                default->"cartethyia_storm_blade";
            };
            case "shorekeeper_outro_star_butterfly"->"shorekeeper_star_butterfly";
            case "shorekeeper_outro_dark_butterfly"->"shorekeeper_dark_butterfly";
            default->name;
        };
        var mesh=ActionPresentationRegistry.mesh(asset);if(mesh.isEmpty())return;
        state.model=name;state.animation=entity.animation();state.owner=owner(entity);
        double elapsed=Math.max(0,PresentationClient.now()+partial-entity.startTick());
        float progress=entity.animationProgress(elapsed);
        if(entity.animation().equals("idle"))progress=ActionPresentationRegistry.modelLoopProgress(asset,"idle",elapsed);
        if(entity instanceof cn.zgnhit.mcwuwa.platform.entity.HecateEntity hecate&&!hecate.attackAction().isEmpty()){
            state.actionLight=hecate.attackAction();state.actionProgress=progress;
        }
        if(name.startsWith("shorekeeper_")&&name.endsWith("butterfly"))progress=ActionPresentationRegistry.modelLoopProgress(asset,entity.animation(),elapsed);
        if(name.equals("echo_fallacy_fists")&&entity.animation().equals("breath"))progress=ActionPresentationRegistry.modelLoopProgress(asset,"breath",elapsed);
        if(name.equals("echo_fallacy_fists")&&entity.animation().equals("attack"))progress=(float)Math.min(1,elapsed/12);
        if(name.equals("ciaccona_microphone")||name.startsWith("ciaccona_")&&name.endsWith("_ensemble"))
            progress=ActionPresentationRegistry.modelLoopProgress(asset,entity.animation(),elapsed);
        // This dance loops every authored 1.2s; its 30s field lifetime must not
        // stretch one complete body motion into a slow thirty-second pose.
        if(name.equals("suisui_reserve_dancer")){
            progress=ActionPresentationRegistry.modelLoopProgress(asset,entity.animation(),elapsed);
            state.actionLight="suisui/reserve_dance";state.actionProgress=progress;
        }
        if(name.equals("lupa_reserve_assist")&&!entity.animation().equals("idle"))progress=(float)Math.min(1,elapsed/16);
        String clip=entity.animation();
        if(entity instanceof cn.zgnhit.mcwuwa.platform.entity.EchoBossEntity boss&&boss.currentMove()!=null&&clip.equals("attack")) {
            String candidate="attack_"+boss.currentMove().pattern().name().toLowerCase(java.util.Locale.ROOT);
            if(!ActionPresentationRegistry.modelPose(name,candidate,progress).isEmpty())clip=candidate;
        }
        Map<String,ActionPresentationRegistry.Pose> modelPose=ActionPresentationRegistry.modelPose(asset,clip,progress);
        if(name.equals("survey_motorcycle")&&entity instanceof ConstructEntity){
            // Intro prop reuses the approved vehicle without spawning a rideable
            // or collectible item. Its departure is independent of later aiming.
            float forward=cn.zgnhit.mcwuwa.client.LynaeIntroMotion.forward(phase);
            var root=new ActionPresentationRegistry.Pose(new org.joml.Vector3f(0,0,-forward*16),
                new org.joml.Vector3f(cn.zgnhit.mcwuwa.client.LynaeIntroMotion.pitch(phase),0,0),new org.joml.Vector3f(1));
            var wheel=new ActionPresentationRegistry.Pose(new org.joml.Vector3f(),new org.joml.Vector3f(forward*180/(float)(Math.PI*.35),0,0),new org.joml.Vector3f(1));
            var folded=new ActionPresentationRegistry.Pose(new org.joml.Vector3f(),new org.joml.Vector3f(),new org.joml.Vector3f(.001f,.3f,1));
            modelPose=Map.of("root",root,"front_wheel",wheel,"rear_wheel",wheel,"wing_left",folded,"wing_right",folded);
        }
        if(entity instanceof cn.zgnhit.mcwuwa.platform.entity.SurveyMotorcycleEntity bike){
            var rotation=new org.joml.Vector3f(bike.wheelDegrees(partial),0,0);var wheel=new ActionPresentationRegistry.Pose(new org.joml.Vector3f(),rotation,new org.joml.Vector3f(1));
            var steer=new ActionPresentationRegistry.Pose(new org.joml.Vector3f(),new org.joml.Vector3f(0,bike.steeringDegrees(partial),0),new org.joml.Vector3f(1));
            float unfold=Math.max(.001f,bike.wingExpansion(partial));
            var wing=new ActionPresentationRegistry.Pose(new org.joml.Vector3f(),new org.joml.Vector3f(),new org.joml.Vector3f(unfold,.3f+.7f*unfold,1));
            modelPose=Map.of("front_wheel",wheel,"rear_wheel",wheel,"front_steer",steer,"wing_left",wing,"wing_right",wing);
        }
        state.mesh=SegmentedMeshRenderer.prepare(mesh.get(),modelPose,null,false,false);
        if(name.equals("lupa_reserve_assist")||name.equals("aemeath_assist")){
            var game=net.minecraft.client.Minecraft.getInstance();
            var ownerPlayer=game.level==null||state.owner.isEmpty()?null:game.level.getPlayerByUUID(java.util.UUID.fromString(state.owner));
            if(ownerPlayer!=null){
                state.heldItem=new net.minecraft.client.renderer.item.ItemStackRenderState();
                game.getItemModelResolver().updateForLiving(state.heldItem,
                    cn.zgnhit.mcwuwa.platform.ModItems.WEAPONS.get(name.equals("aemeath_assist")?cn.zgnhit.mcwuwa.core.CharacterId.AEMEATH:cn.zgnhit.mcwuwa.core.CharacterId.LUPA).get().getDefaultInstance(),
                    net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,ownerPlayer);
            }
        }
        if(name.equals("jue_dragon")&&entity.animation().equals("breath")&&phase>.08&&phase<.94){
            var point=state.mesh.bonePoint("jaw",0,33,-34);
            if(point.isPresent()){
                var start=point.get();float dragonScale=entity.visualScale();float length=12/dragonScale;
                var end=new org.joml.Vector3f(start.x,.6f/dragonScale,start.z-length);
                if(aimOwner!=null){
                    var target=cn.zgnhit.mcwuwa.client.AttackVisualAim.endpoint(aimOwner,partial,12);
                    end.set((float)(target.x-state.x),(float)(target.y-state.y),(float)(target.z-state.z))
                        .rotateY((float)Math.toRadians(state.yaw-180)).div(dragonScale);
                }
                int alpha=(int)(245*lightIntensity(state.owner));state.light=new SkillLightRenderer.Ribbon(java.util.List.of(start,end),.36f/dragonScale,(alpha<<24)|0xffe69a,true);
            }
        }
        state.texture=Identifier.fromNamespaceAndPath("mc_wuwa","textures/entity/"+asset+".png");
        state.scale=entity.visualScale();
    }
    private static double lightIntensity(String owner){var player=net.minecraft.client.Minecraft.getInstance().player;return player!=null&&player.getUUID().toString().equals(owner)?cn.zgnhit.mcwuwa.platform.ClientVisualConfig.OWN_INTENSITY.get():cn.zgnhit.mcwuwa.platform.ClientVisualConfig.OTHER_INTENSITY.get();}
    @Override protected AABB getBoundingBoxForCulling(T entity){
        if(entity instanceof ConstructEntity ray&&entity.modelId().equals("phoebe_refraction_ray"))
            return entity.getBoundingBox().expandTowards(ray.visualRayVector()).inflate(.2);
        var bounds=entity.getBoundingBox().inflate(entity.modelId().equals("skill_cart_scar")?28:
            entity.modelId().equals("jue_dragon")?14:entity.modelId().equals("skill_suisui_landscape")?13*entity.visualScale():8*entity.visualScale());
        if(entity instanceof ConstructEntity construct&&construct.followsOwner()&&!construct.ownerUuid().isEmpty()){
            var actor=entity.level().getPlayerByUUID(java.util.UUID.fromString(construct.ownerUuid()));
            if(actor!=null){
                // Rendering uses the owner's interpolated position, not the
                // last received proxy position. Include both tick endpoints so
                // a fast glide/fall cannot cull an otherwise visible follower.
                var current=actor.position().add(construct.visualOwnerOffset(actor.getYRot())).subtract(entity.position());
                var previous=actor.getPosition(0).add(construct.visualOwnerOffset(actor.getYRot(0))).subtract(entity.position());
                bounds=bounds.minmax(bounds.move(current)).minmax(bounds.move(previous));
            }
        }
        return bounds;
    }
    @Override public void submit(State state,PoseStack poses,SubmitNodeCollector collector,CameraRenderState camera) {
        if((state.mesh==null||state.texture==null)&&state.light==null&&state.actionLight.isEmpty()||state.isInvisible)return;
        poses.pushPose();poses.mulPose(Axis.YP.rotationDegrees(180-state.yaw));poses.scale(state.scale,state.scale,state.scale);
        if(state.deathRoll>0)poses.mulPose(Axis.ZP.rotationDegrees(state.deathRoll));
        if(state.model.equals("cartethyia_inherited_blade")){
            float angle=switch(state.animation){case "human_right"->45;case "divine_intent"->90;default->135;};
            // The inherited meshes point toward their pommels at the radial angle above.
            // Turn the blade tips toward the outside of the fan.
            poses.mulPose(Axis.ZP.rotationDegrees(angle + 180));
            // Rotate about the halo at model Y=20 pixels, rather than the tip.
            poses.translate(0, -2 * 20.0 / 16.0, 0);
        }
        // The chainsaw is authored upright and must lie beneath Chisa. Phoebe's
        // staff is already authored along local Z; a generic glide rotation
        // would turn it upright and displace it away from the rider's feet.
        if(state.model.equals("chisa_chainsaw")&&"glide".equals(state.animation)){
            poses.mulPose(Axis.XP.rotationDegrees(-90));poses.translate(0,-1.3,0);
        }
        int visualLight=state.model.startsWith("shorekeeper_")||state.model.equals("sigrika_sun_sprite")||state.model.equals("lupa_flame_wolf_heads")||state.model.equals("qingxiao_sword_zither")||state.model.equals("cartethyia_inherited_blade")||state.model.startsWith("cartethyia_shadow_")?0xF000F0:state.lightCoords;
        if(state.mesh!=null&&state.texture!=null){
            var game=net.minecraft.client.Minecraft.getInstance();
            boolean ownFirstPersonPalace=state.model.equals("hsin_construct")&&game.options.getCameraType().isFirstPerson()
                &&game.player!=null&&game.player.getUUID().toString().equals(state.owner);
            // Dense nested voxel shells accumulate opacity. Keep the nearby
            // owner's summoned body faint enough to see the actual target.
            boolean ownFirstPersonSummon=(state.model.equals("echo_soulfrayer")||state.model.equals("echo_gromos"))
                &&game.options.getCameraType().isFirstPerson()&&game.player!=null
                &&game.player.getUUID().toString().equals(state.owner);
            boolean ownFirstPersonCast=(state.model.equals("yinlin_puppet")||state.model.equals("camellya_blossom"))
                &&game.options.getCameraType().isFirstPerson()&&game.player!=null
                &&game.player.getUUID().toString().equals(state.owner)
                &&PresentationClient.state(game.player.getUUID()).map(action->action.actionId().equals("yinlin/liberation")
                    ||action.actionId().equals("camellya/liberation")||action.actionId().equals("camellya/ephemeral")
                    ||action.actionId().equals("camellya/immortal_blossom")).orElse(false);
            // Only the nearby owner's microphone gets an FP composition offset;
            // the world anchor, ensemble, field and positional audio stay unchanged.
            boolean ownNearMicrophone=state.model.equals("ciaccona_microphone")
                &&game.options.getCameraType().isFirstPerson()&&game.player!=null
                &&game.player.getUUID().toString().equals(state.owner)
                &&game.player.distanceToSqr(state.x,state.y,state.z)<4;
            poses.pushPose();
            if(ownNearMicrophone){poses.translate(.40,.40,-.65);poses.scale(.65f,.65f,.65f);}
            // These solid summons intersect the owner's FP sightline during R.
            // Retain their shape without replacing the whole view with an opaque shell.
            if(ownFirstPersonCast)SegmentedMeshRenderer.submit(state.mesh,state.texture,poses,collector,visualLight,
                state.model.equals("camellya_blossom")?0x08ffffff:0x28ffffff);
            else if(ownFirstPersonSummon)SegmentedMeshRenderer.submit(state.mesh,state.texture,poses,collector,visualLight,0x12ffffff);
            else if(state.model.equals("suisui_reserve_dancer"))SegmentedMeshRenderer.submit(state.mesh,state.texture,poses,collector,visualLight,0x90ffffff);
            else if(ownFirstPersonPalace)SegmentedMeshRenderer.submit(state.mesh,state.texture,poses,collector,visualLight,0x60ffffff);
            else SegmentedMeshRenderer.submit(state.mesh,state.texture,poses,collector,visualLight,state.model.startsWith("ciaccona_")&&state.model.endsWith("_ensemble"));
            poses.popPose();
        }
        if(state.mesh!=null&&state.heldItem!=null)SegmentedMeshRenderer.submitHeldItem(state.mesh,state.heldItem,net.minecraft.world.entity.HumanoidArm.RIGHT,poses,collector,state.lightCoords);
        if(state.floatingInstrument!=null){
            // The approved weapon's blade runs along local +Y. Rotate that axis
            // toward local -Z, which is forward under this renderer's root yaw.
            boolean normalFinish=state.actionLight.equals("mornye/basic_04");
            boolean counter=state.actionLight.equals("mornye/dodge_counter");
            boolean observation=state.actionLight.equals("mornye/observation_aura");
            boolean air=state.actionLight.equals("mornye/air");
            double phase=normalFinish?Math.clamp((state.actionProgress-.15)/.6,0,1):Math.clamp((cn.zgnhit.mcwuwa.client.MornyePilotVfx.liberationPhase(state.actionProgress)-.08)/.17,0,1),radius=(1-phase)*1.75+.12;
            for(int i=0;i<(air?1:counter||observation?2:3);i++){
                double angle=i*Math.TAU/3;
                poses.pushPose();
                if(air){
                    var cut=cn.zgnhit.mcwuwa.core.MornyeAirBladePose.at(state.actionProgress);
                    poses.translate(cut.side(),cut.up(),-cut.forward());
                    poses.mulPose(Axis.XP.rotationDegrees((float)cut.pitch()));
                    poses.scale((float)cut.scale(),(float)cut.scale(),(float)cut.scale());
                }
                else if(observation){
                    poses.translate(i==0?-1.15:1.15,1.4+Math.sin(state.actionProgress*Math.TAU)*.025,-.12);
                    poses.mulPose(Axis.ZP.rotationDegrees(i==0?32:-32));
                }
                else if(counter){
                    poses.translate(i==0?-.85:.85,1.45,-.6);
                    poses.mulPose(Axis.YP.rotation((float)(-(state.actionProgress-.5)*2.4)));
                }
                else if(normalFinish)poses.translate((i-1)*1.2*(1-phase),1+(i==1?1.5:.7)*(1-phase)-.25*phase,-(1.6+phase*1.6));
                else poses.translate(Math.cos(angle)*radius,1.7+Math.sin(angle)*radius,-(.5+phase*2));
                if(!air){poses.mulPose(Axis.XP.rotationDegrees(observation?0:normalFinish?-72:-90));
                float size=observation?.60f:counter?.38f:normalFinish?.48f:.75f;poses.scale(size,size,size);}
                state.floatingInstrument.submit(poses,collector,0xF000F0,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,0);
                poses.popPose();
            }
        }
        SkillLightRenderer.submit(state.light,poses,collector);
        // Both first-person and front-view cameras can intersect the owner's
        // light surfaces. Fade nearby local geometry, keeping distant effects.
        org.joml.Vector3f frontCamera=null;
        var viewGame=net.minecraft.client.Minecraft.getInstance();
        if(viewGame.player!=null&&viewGame.options.getCameraType()!=net.minecraft.client.CameraType.THIRD_PERSON_BACK
                &&viewGame.player.getUUID().toString().equals(state.owner)){
            var eye=viewGame.gameRenderer.getMainCamera().position();
            frontCamera=new org.joml.Vector3f((float)(eye.x-state.x),(float)(eye.y-state.y),(float)(eye.z-state.z))
                .rotateY((float)Math.toRadians(state.yaw-180)).div(state.scale);
        }
        float actionLightIntensity=(float)lightIntensity(state.owner);
        if(viewGame.player!=null&&viewGame.options.getCameraType()==net.minecraft.client.CameraType.FIRST_PERSON
                &&viewGame.player.getUUID().toString().equals(state.owner)
                &&state.actionLight!=null&&(state.actionLight.startsWith("qiuyuan/ink_heavy_03")
                    ||state.actionLight.startsWith("qingxiao/air_basic_")))
            actionLightIntensity*=.35f; // These near-camera arcs cross the owner's local sightline.
        boolean actionSubmitted=ActionRibbonRenderer.submit(state.actionLight,state.actionProgress,actionLightIntensity,state.aimPitch,state.aimTarget,frontCamera,poses,collector);
        if(actionSubmitted&&actionLightObserver!=null)actionLightObserver.accept(state.model,state.owner);
        ShoreDomainRenderer.submit(state.model,(float)lightIntensity(state.owner),state.visualAge,poses,collector);
        if(lightObserver!=null&&state.light!=null&&state.light.points().size()>1){
            float length=0;for(int i=1;i<state.light.points().size();i++)length+=state.light.points().get(i-1).distance(state.light.points().get(i))*state.scale;
            var end=new org.joml.Vector3f(state.light.points().getLast()).mul(state.scale).rotateY((float)Math.toRadians(180-state.yaw));
            var worldEnd=new net.minecraft.world.phys.Vec3(state.x+end.x,state.y+end.y,state.z+end.z);
            lightObserver.accept(new LightSubmitted(state.model,state.owner,state.light.points().size(),length,state.light.width()*state.scale,state.light.upright(),(long)PresentationClient.now(),worldEnd));
        }
        if(state.weapon!=null&&state.weaponTransform!=null) {
            poses.pushPose();poses.mulPose(state.weaponTransform);
            SegmentedMeshRenderer.submit(state.weapon,Identifier.fromNamespaceAndPath("mc_wuwa","textures/entity/cartethyia_storm_blade.png"),poses,collector,state.lightCoords);poses.popPose();
        }
        poses.popPose();
        if(observer!=null&&state.mesh!=null)observer.accept(new Submitted(state.model,state.animation,state.owner,state.mesh.vertexCount(),state.weapon!=null||state.heldItem!=null||state.model.equals("aemeath_mecha")&&state.mesh.boneTransform("mech_poleblade").isPresent(),(long)PresentationClient.now()));
    }
}
