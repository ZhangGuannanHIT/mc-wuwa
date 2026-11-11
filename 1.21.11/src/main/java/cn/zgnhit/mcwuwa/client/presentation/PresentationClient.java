package cn.zgnhit.mcwuwa.client.presentation;

import cn.zgnhit.mcwuwa.McWuwa;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.extensions.IRenderStateExtension;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.*;

/** Server snapshots drive all views. No keyframe here can authorize a hit. */
public final class PresentationClient {
    private record Frame(SegmentedMeshRenderer.Prepared mesh, Identifier texture, boolean transformed,float visualScale,UUID actor,String model,boolean conducting,boolean vine) {}
    public record VineSubmitted(float headY,float hipY,long tick){}
    private static final Map<UUID,VineSubmitted> VINE_SUBMISSIONS=new HashMap<>();
    public static Optional<VineSubmitted> submittedVine(UUID actor){return Optional.ofNullable(VINE_SUBMISSIONS.get(actor));}
    private record Form(UUID actorUuid,int entityId,String modelId,long expiresAt,long revision) {}
    private static final ContextKey<Frame> FRAME = new ContextKey<>(Identifier.fromNamespaceAndPath("mc_wuwa", "action_frame"));
    private static final Map<UUID, ActorPresentationState> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> REVISIONS = new HashMap<>();
    private static final Map<UUID,Long> START_SOUNDS = new HashMap<>();
    private static final Map<UUID,Form> FORMS = new HashMap<>();
    private static final Map<UUID,Long> FORM_REVISIONS = new HashMap<>();
    private static final Map<UUID,Integer> ENTITY_IDS = new HashMap<>();
    private static final PlayerPosePipeline POSES=new PlayerPosePipeline();
    private static final Map<UUID,ActionPresentationRegistry.Binding> LAST_BINDINGS=new HashMap<>();
    private static InteractionHand submittedHand;
    private static final Map<UUID, String> SUBMITTED_FORMS = new HashMap<>();
    private static final Map<UUID,String> EXTRACTION_DIAGNOSTICS=new HashMap<>();
    private static final Map<UUID,Long> CONDUCTOR_SUBMISSIONS=new HashMap<>();
    private static boolean initialized;

    private PresentationClient() {}
    public static Map<String,ActionPresentationRegistry.Pose> sampledPose(UUID actor,boolean firstPerson){return POSES.lastPose(actor,firstPerson);}

    public static void initialize(IEventBus modBus) {
        if (initialized) return;
        initialized = true;
        modBus.addListener(PresentationClient::registerReload);
        modBus.addListener(PresentationClient::registerExtraction);
        modBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) -> AlephRenderer.register(event));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> event.registerEntityRenderer(
            cn.zgnhit.mcwuwa.platform.entity.ModEntities.COMBAT_PROJECTILE.get(),
            CombatProjectileRenderer::new));
        modBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event)->ConstructRenderer.register(event));
        PalPlayerAnimationAdapter.initialize(modBus);
        NeoForge.EVENT_BUS.addListener(PresentationClient::tick);
        NeoForge.EVENT_BUS.addListener(PresentationClient::renderPlayer);
        NeoForge.EVENT_BUS.addListener(PresentationClient::renderHand);
        NeoForge.EVENT_BUS.addListener(PresentationClient::beginFrame);
        NeoForge.EVENT_BUS.addListener(PresentationClient::logout);
    }

    /** Invoke on the client thread from the authenticated server payload handler. */
    public static void accept(UUID actorUuid, int entityId, UUID instanceId, String actionId,
                              long startTick, int durationTicks, long revision, String formModel) {
        Minecraft game = Minecraft.getInstance();
        if (game.level == null) return;
        var entity = game.level.getEntity(entityId);
        if (!(entity instanceof Player) || !entity.getUUID().equals(actorUuid)) return;
        bindEntity(actorUuid,entityId);
        if (revision < REVISIONS.getOrDefault(actorUuid, -1L)) return;
        ActorPresentationState state;
        try { state = new ActorPresentationState(actorUuid, entityId, instanceId, actionId, startTick, durationTicks, revision, formModel); }
        catch (IllegalArgumentException error) { McWuwa.LOGGER.warn("Rejected malformed presentation snapshot for {}", actorUuid); return; }
        REVISIONS.put(actorUuid, revision);
        // Echo transformations animate their own model clips and need not have
        // a matching segmented-player clip (e.g. Augusta's False Sovereign).
        boolean available=ActionPresentationRegistry.available(state.actionId())
            || !state.formModel().isEmpty() && ActionPresentationRegistry.mesh(state.formModel()).isPresent();
        if (state.expired(now()) || !available) {
            ActorPresentationState old=ACTIVE.get(actorUuid);
            if(old!=null)remove(old,false);
            POSES.discard(actorUuid);LAST_BINDINGS.remove(actorUuid);
            PalPlayerAnimationAdapter.stop((Avatar) entity); return;
        }
        ACTIVE.put(actorUuid,state);
        Long lastSound=START_SOUNDS.put(actorUuid,revision);
        if (!Objects.equals(lastSound,revision) && now()-startTick<=3 && !actionId.equals("ciaccona/recital") && !actionId.startsWith("ciaccona/solo_")) {
            String suffix=actionId.contains("liberation")||actionId.contains("skill")||actionId.contains("echo")?"cast":"swing";
            game.level.playLocalSound(entity.getX(),entity.getY()+1,entity.getZ(),SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("mc_wuwa","combat."+suffix)),SoundSource.PLAYERS,.65f,1,false);
        }
    }

    /** Old end packets cannot stop a newer action. */
    public static void end(UUID actorUuid, UUID instanceId, long revision) {
        if(revision<REVISIONS.getOrDefault(actorUuid,-1L))return;
        REVISIONS.put(actorUuid,revision);
        ActorPresentationState state=ACTIVE.get(actorUuid);
        if(state==null||!state.instanceId().equals(instanceId))return;
        remove(state,true);
    }

    /** Independent persistent form; an action end must never implicitly clear it. */
    public static void acceptForm(UUID actorUuid,int entityId,String modelId,long expiresAt,long revision) {
        if(modelId==null || (!modelId.isEmpty() && !modelId.matches("[a-z0-9_]+")))return;
        var level=Minecraft.getInstance().level;if(level==null)return;
        var actor=level.getEntity(entityId);
        if(!(actor instanceof Player)||!actor.getUUID().equals(actorUuid))return;
        bindEntity(actorUuid,entityId);
        if(revision<FORM_REVISIONS.getOrDefault(actorUuid,-1L))return;
        boolean hadForm=!formModel(actorUuid,ACTIVE.get(actorUuid)).isEmpty();
        FORM_REVISIONS.put(actorUuid,revision);
        // Ordinary cancellation also emits an empty form packet. It must not erase
        // the final body pose before the following action-end starts RETURNING.
        if(hadForm||!modelId.isEmpty()){POSES.discard(actorUuid);LAST_BINDINGS.remove(actorUuid);}
        if(modelId.isEmpty() || expiresAt<=now()){FORMS.remove(actorUuid);return;}
        FORMS.put(actorUuid,new Form(actorUuid,entityId,modelId,expiresAt,revision));
    }
    private static void bindEntity(UUID actor,int entityId) {
        Integer previous=ENTITY_IDS.put(actor,entityId);
        if(previous!=null && previous!=entityId) {
            ActorPresentationState old=ACTIVE.get(actor);if(old!=null)remove(old,false);
            POSES.discard(actor);LAST_BINDINGS.remove(actor);
            PalPlayerAnimationAdapter.discard(actor);
            REVISIONS.remove(actor);FORM_REVISIONS.remove(actor);FORMS.remove(actor);START_SOUNDS.remove(actor);
        }
    }

    private static String formModel(UUID actor,ActorPresentationState action) {
        // A newer action-local echo form temporarily overlays a persistent character form.
        // Old explicit clear packets must not suppress every later echo transformation.
        if(action!=null && !action.expired(now()) && !action.formModel().isEmpty()
            && action.revision()>FORM_REVISIONS.getOrDefault(actor,-1L))return action.formModel();
        Form form=FORMS.get(actor);
        if(form!=null && form.expiresAt()>now())return form.modelId();
        if(FORM_REVISIONS.containsKey(actor))return ""; // An explicit form clear overrides legacy action-local form data.
        return action!=null && !action.expired(now())?action.formModel():"";
    }
    private static String visibleForm(UUID actor,ActorPresentationState action) {
        String form=formModel(actor,action);
        // R1 starts with the human's reach/turn. The authoritative mech state is
        // already active; delaying its mesh must not delay abilities or touch inventory.
        if(form.equals("aemeath_mecha")&&action!=null&&!action.expired(now())
            &&action.actionId().equals("aemeath/liberation")&&action.progress(now())<2f/5.5f)return "";
        return form;
    }
    private static boolean humanLiberationOpening(ActorPresentationState action){
        return action!=null&&!action.expired(now())&&action.actionId().equals("aemeath/liberation")
            &&action.progress(now())<2f/5.5f;
    }
    private static Map<String,ActionPresentationRegistry.Pose> formPose(String model,ActorPresentationState action,float partial,UUID actor) {
        return formPose(model,action,partial,false,actor);
    }
    private static Map<String,ActionPresentationRegistry.Pose> formPose(String model,ActorPresentationState action,float partial,boolean firstPerson,UUID actor) {
        boolean playing=action!=null&&!action.expired(now());
        if(model.equals("shorekeeper_butterfly_form"))return ActionPresentationRegistry.modelPose(model,"idle",ActionPresentationRegistry.modelLoopProgress(model,"idle",now()+partial));
        // Sustained moth casts flap at their authored cadence, independently of
        // a short or held cast's total lifetime.
        if(model.equals("echo_voidwing_moth"))return ActionPresentationRegistry.modelPose(model,"idle",ActionPresentationRegistry.modelLoopProgress(model,"idle",now()+partial));
        if(model.equals("echo_heron")&&playing&&action.actionId().endsWith("/echo_heron_breath")){
            double elapsed=now()+partial-action.startTick();
            if(elapsed<10)return ActionPresentationRegistry.modelPose(model,"breath_start",(float)Math.clamp(elapsed/10,0,1));
            return ActionPresentationRegistry.modelPose(model,"breath",
                ActionPresentationRegistry.modelLoopProgress(model,"breath",elapsed-10));
        }
        if(model.equals("echo_nightmare_rider_pixel")&&playing){
            if(action.actionId().endsWith("/echo_riding"))return ActionPresentationRegistry.modelPose(model,"idle",ActionPresentationRegistry.modelLoopProgress(model,"idle",now()+partial-action.startTick()));
            if(action.actionId().endsWith("/echo_rider_finish"))return ActionPresentationRegistry.modelPose(model,"cleave",action.progress(now()+partial));
        }
        float progress=playing?action.progress(now()+partial):ActionPresentationRegistry.modelLoopProgress(model,"idle",now()+partial);
        String animation=playing?"attack":"idle";
        if(playing&&model.equals("aemeath_mecha")) {
            String id=action.actionId();
            String specific=id.replace("mc_wuwa:","").replace("aemeath/","");
            if(specific.equals("liberation"))progress=Math.clamp((progress-2f/5.5f)/(1-2f/5.5f),0,1);
            if(java.util.Set.of("mech_basic_01","mech_basic_02","mech_basic_03","mech_basic_04","mech_heavy","mech_heavy_charge_2","mech_air","mech_dodge_counter","wing_ensemble_stage","wing_ensemble_descend","skill","skill_dawn","liberation").contains(specific))animation=specific.equals("skill_dawn")?"skill":specific;
            else animation=id.contains("heavy")?"attack_heavy":id.contains("skill")||id.contains("counter")?"attack_thrust":
                id.endsWith("_02")?"attack_reverse":id.endsWith("_03")?"attack_thrust":id.endsWith("_04")?"attack_heavy":"attack_sweep";
        }
        if(!playing&&model.equals("aemeath_mecha")&&mechaFlight(actor)){
            animation="flight";progress=ActionPresentationRegistry.modelLoopProgress(model,animation,now()+partial);
        }
        if(firstPerson) {
            var viewPose=ActionPresentationRegistry.modelPose(model,animation+"_first_person",progress);
            if(!viewPose.isEmpty())return viewPose;
        }
        return ActionPresentationRegistry.modelPose(model,animation,progress);
    }

    public static Optional<ActorPresentationState> state(UUID actorUuid) { return Optional.ofNullable(ACTIVE.get(actorUuid)); }
    private static boolean mechaFlight(UUID actor){
        var game=Minecraft.getInstance();if(actor==null||game.player==null||game.level==null)return false;
        if(actor.equals(game.player.getUUID())){
            var role=cn.zgnhit.mcwuwa.client.CombatClient.roleView(cn.zgnhit.mcwuwa.client.CombatClient.activeSlot());
            return role.has("movement")&&role.getAsJsonObject("movement").has("mode")
                &&role.getAsJsonObject("movement").get("mode").getAsString().equals("aemeath_flight");
        }
        var player=game.level.getPlayerByUUID(actor);
        return player!=null&&!player.onGround()&&player.isNoGravity()&&player.getDeltaMovement().lengthSqr()>.04;
    }
    public static String activeForm(UUID actorUuid){return formModel(actorUuid,ACTIVE.get(actorUuid));}
    /** Diagnostic of actual world/hand submissions, not merely a received form packet. */
    public static String submittedForm(UUID actorUuid){return SUBMITTED_FORMS.getOrDefault(actorUuid, "");}
    public static String extractionDiagnostic(UUID actor){return EXTRACTION_DIAGNOSTICS.getOrDefault(actor,"");}
    public static boolean conductorSubmitted(UUID actor){return CONDUCTOR_SUBMISSIONS.getOrDefault(actor,-100L)>=now()-2;}
    public static PlayerPosePipeline.Phase visualPhase(UUID actorUuid,boolean firstPerson){return POSES.phase(actorUuid,firstPerson);}
    public static Optional<PlayerPosePipeline.Diagnostic> visualSnapshot(UUID actorUuid,boolean firstPerson){return POSES.diagnostic(actorUuid,firstPerson);}
    public static Optional<InteractionHand> renderedHandCallback(){return Optional.ofNullable(submittedHand);}
    private static boolean recital(UUID actor){var state=ACTIVE.get(actor);return state!=null&&(state.actionId().equals("ciaccona/recital")||state.actionId().startsWith("ciaccona/solo_")||Set.of("ciaccona/basic_04","ciaccona/skill","ciaccona/heavy_quartet").contains(state.actionId()))&&!state.expired(now());}
    public static long now() { var level=Minecraft.getInstance().level;return level==null?0:level.getLevelData().getGameTime(); }
    public static void clear() {
        for(ActorPresentationState state:List.copyOf(ACTIVE.values()))remove(state,false);
        ACTIVE.clear();REVISIONS.clear();START_SOUNDS.clear();FORMS.clear();FORM_REVISIONS.clear();ENTITY_IDS.clear();
        PalPlayerAnimationAdapter.clear();
        POSES.clear();LAST_BINDINGS.clear();VanillaPlayerPose.clear();
        submittedHand=null;
        SUBMITTED_FORMS.clear();
        EXTRACTION_DIAGNOSTICS.clear();CONDUCTOR_SUBMISSIONS.clear();VINE_SUBMISSIONS.clear();
    }
    private static void remove(ActorPresentationState state,boolean smooth) {
        ACTIVE.remove(state.actorUuid(),state);START_SOUNDS.remove(state.actorUuid());
        if(smooth&&formModel(state.actorUuid(),state).isEmpty())POSES.end(state.actorUuid(),now());
        else {POSES.discard(state.actorUuid());LAST_BINDINGS.remove(state.actorUuid());}
        var level=Minecraft.getInstance().level;
        if(level!=null&&level.getEntity(state.entityId()) instanceof Avatar avatar)PalPlayerAnimationAdapter.stop(avatar);
        PalPlayerAnimationAdapter.discard(state.actorUuid());
    }
    private static void registerReload(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath("mc_wuwa","presentation"),(ResourceManagerReloadListener) resources -> {
            ActionPresentationRegistry.reload(resources);
            // Keep authoritative identities; curves and handles are resolved anew.
            var level=Minecraft.getInstance().level;
            if(level!=null)for(var player:level.players())PalPlayerAnimationAdapter.stop(player);
            PalPlayerAnimationAdapter.invalidate();
            POSES.clear();LAST_BINDINGS.clear();VanillaPlayerPose.clear();
        });
    }
    private static void tick(ClientTickEvent.Post event) {
        var game=Minecraft.getInstance();
        if(game.level==null){clear();return;}
        for(ActorPresentationState state:List.copyOf(ACTIVE.values())) {
            var entity=game.level.getEntity(state.entityId());
            if(!(entity instanceof Player player)||!entity.getUUID().equals(state.actorUuid())||!player.isAlive())remove(state,false);
            else if(state.expired(now()))remove(state,true);
            else if(player.isSpectator()||player.isInvisible()){POSES.discard(state.actorUuid());LAST_BINDINGS.remove(state.actorUuid());}
            else if(state.actionId().endsWith("/echo_heron_breath")&&now()-state.startTick()>=10
                    &&now()-state.startTick()<196&&now()%3==0&&formModel(player.getUUID(),state).equals("echo_heron")){
                ActionPresentationRegistry.mesh("echo_heron").ifPresent(mesh->{
                    var prepared=SegmentedMeshRenderer.prepare(mesh,formPose("echo_heron",state,0,player.getUUID()),null,false,false);
                    prepared.bonePoint("head_1",0,33.3f,-11).ifPresent(point->{
                        point.rotateY((float)Math.toRadians(180-player.yBodyRot)).mul(player.getScale());
                        cn.zgnhit.mcwuwa.client.CombatEffects.heronBreath(player,player.position().add(point.x,point.y,point.z));
                    });
                });
            }
        }
        for(Form form:List.copyOf(FORMS.values())) {
            var actor=game.level.getEntity(form.entityId());
            if(form.expiresAt()<=now() || !(actor instanceof Player player) || !actor.getUUID().equals(form.actorUuid()) || !player.isAlive())FORMS.remove(form.actorUuid(),form);
        }
        // Server revisions restart on reconnect. Drop replay guards only after the actor really
        // leaves client tracking; natural action expiry alone must retain them.
        Set<UUID> tracked=new HashSet<>();for(var player:game.level.players())tracked.add(player.getUUID());
        REVISIONS.keySet().retainAll(tracked);FORM_REVISIONS.keySet().retainAll(tracked);START_SOUNDS.keySet().retainAll(tracked);
        ENTITY_IDS.keySet().retainAll(tracked);
        EXTRACTION_DIAGNOSTICS.keySet().retainAll(tracked);CONDUCTOR_SUBMISSIONS.keySet().retainAll(tracked);
        for(var player:game.level.players())if(!player.isAlive()||player.isSpectator()||player.isInvisible()) {
            POSES.discard(player.getUUID());LAST_BINDINGS.remove(player.getUUID());
        }
        POSES.prune(tracked,now());
        LAST_BINDINGS.keySet().removeIf(actor->!tracked.contains(actor)||!ACTIVE.containsKey(actor)
            &&POSES.phase(actor,false)==PlayerPosePipeline.Phase.VANILLA&&POSES.phase(actor,true)==PlayerPosePipeline.Phase.VANILLA);
    }
    private static void logout(ClientPlayerNetworkEvent.LoggingOut event){clear();}
    private static void beginFrame(RenderFrameEvent.Pre event){submittedHand=null;SUBMITTED_FORMS.clear();}
    public static boolean hasTransformFrame(AvatarRenderState state) {
        Frame frame=((IRenderStateExtension)state).getRenderData(FRAME);
        return frame!=null&&frame.transformed();
    }
    private static boolean claimHand(RenderHandEvent event) {
        event.setCanceled(true);
        if(submittedHand!=null)return false;
        // Minecraft may emit OFF_HAND only while a bow is being used. The first
        // actual callback owns the one submission containing both arms and items.
        submittedHand=event.getHand();return true;
    }

    private static ActorPresentationState renderAction(UUID actor) {
        ActorPresentationState action=ACTIVE.get(actor);
        if(action!=null&&action.expired(now())){remove(action,true);return null;}
        return action;
    }
    private static ActionPresentationRegistry.Binding renderBinding(UUID actor,ActorPresentationState action) {
        if(action==null)return LAST_BINDINGS.get(actor);
        var binding=ActionPresentationRegistry.binding(action.actionId()).orElse(null);
        if(binding!=null)LAST_BINDINGS.put(actor,binding);
        return binding;
    }
    private static double firstContact(ActorPresentationState action,PlayerPosePolicy policy) {
        double relative=policy.contactProgress()*action.durationTicks();
        String[] id=action.actionId().split("/",2);
        if(id.length==2)try {
            var mapping=McWuwa.catalog().actionMappings(id[0]).stream().filter(value->value.id().equals(action.actionId())).findFirst();
            if(mapping.isPresent()&&mapping.get().hitTimesTicks()!=null&&!mapping.get().hitTimesTicks().isEmpty())
                relative=Math.min(relative,Collections.min(mapping.get().hitTimesTicks()));
        } catch(NoSuchElementException ignored) { /* Echo-only clips have no character hit mapping. */ }
        return action.startTick()+relative;
    }
    /** Inverse of prepare's author-to-physical mirroring, so the native pose is not mirrored twice. */
    private static Map<String,ActionPresentationRegistry.Pose> authorBase(Map<String,ActionPresentationRegistry.Pose> physical,boolean mirrored) {
        if(!mirrored)return physical;
        Map<String,ActionPresentationRegistry.Pose> result=new HashMap<>();
        physical.forEach((name,pose)-> {
            String other=name.startsWith("right_")?"left_"+name.substring(6):name.startsWith("left_")?"right_"+name.substring(5):name;
            var p=new org.joml.Vector3f(pose.position());p.x=-p.x;
            var r=new org.joml.Vector3f(pose.rotation());r.y=-r.y;r.z=-r.z;
            result.put(other,new ActionPresentationRegistry.Pose(p,r,new org.joml.Vector3f(pose.scale())));
        });return result;
    }

    private static void registerExtraction(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier((Class) AvatarRenderer.class, (Avatar avatar, AvatarRenderState renderState) -> {
                IRenderStateExtension extension=(IRenderStateExtension)renderState;
                extension.setRenderData(FRAME,null);
                if(!(avatar instanceof Player)||renderState.isSpectator||renderState.isInvisible)return;
                if(dualHunter(avatar)||cartethyiaSplitSword(avatar.getUUID())){
                    var secondary=renderState.mainArm==HumanoidArm.RIGHT?renderState.leftHandItemState:renderState.rightHandItemState;
                    Minecraft.getInstance().getItemModelResolver().updateForLiving(secondary,avatar.getMainHandItem(),renderState.mainArm==HumanoidArm.RIGHT?ItemDisplayContext.THIRD_PERSON_LEFT_HAND:ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,avatar);
                }

                ActorPresentationState action=renderAction(avatar.getUUID());
                boolean conducting=cn.zgnhit.mcwuwa.client.RoleSkinRegistry.formMatches((Player)avatar,
                    cn.zgnhit.mcwuwa.core.CharacterId.PHROLOVA,"conducting");
                if(Boolean.getBoolean("mc_wuwa.skillFlowSmoke"))EXTRACTION_DIAGNOSTICS.put(avatar.getUUID(),
                    "conducting="+conducting+",samePlayer="+(avatar==Minecraft.getInstance().player)+",class="+avatar.getClass().getName()
                    +",clip="+ActionPresentationRegistry.available("phrolova/conducting_idle")+",world="+AvatarRenderScope.worldPass());
                boolean hovering=mornyeHover((Player)avatar);
                boolean moonRiding=moonRiding((Player)avatar);
                boolean vine=camellyaVine((Player)avatar,action);
                String form=visibleForm(avatar.getUUID(),action);
                if(action==null && !conducting&&!hovering&&!moonRiding&&!vine&&form.isEmpty()&&POSES.phase(avatar.getUUID(),false)!=PlayerPosePipeline.Phase.RETURNING)return;
                float partial=Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
                var binding=action==null&&(conducting||hovering||moonRiding||vine)?ActionPresentationRegistry.binding(conducting?"phrolova/conducting_idle":hovering?"mornye/hover_idle":moonRiding?"iuno/new_moon_idle":"camellya/bloom_idle").orElse(null):renderBinding(avatar.getUUID(),action);
                Optional<ActionPresentationRegistry.Clip> clip=binding==null?Optional.empty():ActionPresentationRegistry.clip(binding.actionId(),false);
                if(clip.isEmpty() && form.isEmpty()){POSES.discard(avatar.getUUID());return;}
                String meshId=renderState.skin.model()==PlayerModelType.SLIM?"rigs/player_slim":"rigs/player_wide";
                boolean transformed=!form.isEmpty();
                if(transformed)meshId=form;
                var mesh=ActionPresentationRegistry.mesh(meshId);
                if(mesh.isEmpty()){POSES.discard(avatar.getUUID());return;} // Never hide the vanilla player on missing resources.
                final ActorPresentationState visibleAction=action;
                Map<String,ActionPresentationRegistry.Pose> pose;
                if(transformed){POSES.discard(avatar.getUUID());pose=meshId.equals("hsin_fox")
                    ? foxPose(renderState.walkAnimationPos,renderState.walkAnimationSpeed,partial,false)
                    : formPose(meshId,action,partial,avatar.getUUID());}
                else {
                    boolean mirrored=renderState.mainArm==HumanoidArm.LEFT;
                    var base=authorBase(VanillaPlayerPose.sample(renderState,renderState.skin.model()==PlayerModelType.SLIM),mirrored);
                    if(conducting)base=conductorPose(false,partial);
                    else if(hovering)base=hoverPose(false,partial);
                    else if(moonRiding)base=moonPose(false,partial);
                    else if(vine)base=vinePose(false,partial);
                    var target=action==null?base:PalPlayerAnimationAdapter.sample(avatar,action,false,partial)
                        .orElseGet(()->PalPlayerAnimationAdapter.toThirdPersonRig(clip.get().sample(visibleAction.progress(now()+partial))));
                    if(humanLiberationOpening(action))target=PalPlayerAnimationAdapter.toThirdPersonRig(
                        clip.get().sample(action.progress(now()+partial)));
                    if(action!=null&&Set.of("camellya/ephemeral","camellya/immortal_blossom").contains(action.actionId())
                        &&cn.zgnhit.mcwuwa.client.RoleSkinRegistry.formMatches((Player)avatar,cn.zgnhit.mcwuwa.core.CharacterId.CAMELLYA,"bloom"))
                        target=settleBloomRecovery(action.progress(now()+partial),target,vinePose(false,partial));
                    var mixed=POSES.sample(avatar.getUUID(),false,meshId,ActionPresentationRegistry.revision(),action,binding.posePolicy(),now()+partial,
                        action==null?now():firstContact(action,binding.posePolicy()),base,target);
                    if(mixed.phase()==PlayerPosePipeline.Phase.VANILLA&&!conducting&&!hovering&&!moonRiding&&!vine)return;
                    pose=mixed.phase()==PlayerPosePipeline.Phase.VANILLA?base:mixed.pose();
                }
                // Quadruped left/right anatomy does not change with the player's dominant hand.
                var prepared=transformed?SegmentedMeshRenderer.prepare(mesh.get(),pose,null,false,false)
                    :SegmentedMeshRenderer.prepare(mesh.get(),pose,renderState,false,renderState.mainArm==HumanoidArm.LEFT,binding);
                Identifier texture=transformed?Identifier.fromNamespaceAndPath("mc_wuwa","textures/entity/"+meshId+".png"):renderState.skin.body().texturePath();
                extension.setRenderData(FRAME,new Frame(prepared,texture,transformed,transformed&&meshId.equals("aemeath_mecha")?.58f:1,avatar.getUUID(),transformed?meshId:"",conducting&&!transformed,vine&&!transformed));
        });
    }

    private static void renderPlayer(RenderPlayerEvent.Pre<?> event) {
        if(!AvatarRenderScope.worldPass()||!cn.zgnhit.mcwuwa.platform.ClientVisualConfig.COMBAT_BODY_MODEL.get())return;
        var state=event.getRenderState();
        Frame frame=((IRenderStateExtension)state).getRenderData(FRAME);
        if(frame==null||state.isSpectator||state.isInvisible)return;
        var poses=event.getPoseStack();var collector=event.getSubmitNodeCollector();
        poses.pushPose();
        poses.mulPose(Axis.YP.rotationDegrees(180-state.bodyRot));
        float visualScale=state.scale*frame.visualScale();poses.scale(visualScale,visualScale,visualScale);
        SegmentedMeshRenderer.submit(frame.mesh(),frame.texture(),poses,collector,state.lightCoords);
        boolean baton=!frame.transformed()&&submitPhrolovaBaton(frame.actor(),frame.mesh(),state.mainArm,false,frame.conducting(),poses,collector,state.lightCoords);
        if(frame.conducting()){
            CONDUCTOR_SUBMISSIONS.put(frame.actor(),now());
            ActionPresentationRegistry.mesh("phrolova_conductor_throne").ifPresent(mesh->{
                var seat=SegmentedMeshRenderer.prepare(mesh,Map.of(),null,false,false);
                SegmentedMeshRenderer.submit(seat,Identifier.fromNamespaceAndPath("mc_wuwa","textures/entity/phrolova_conductor_throne.png"),poses,collector,state.lightCoords);
            });
            var local=Minecraft.getInstance().player;
            float intensity=(float)(double)(local!=null&&local.getUUID().equals(frame.actor())
                ?cn.zgnhit.mcwuwa.platform.ClientVisualConfig.OWN_INTENSITY.get():cn.zgnhit.mcwuwa.platform.ClientVisualConfig.OTHER_INTENSITY.get());
            ActionRibbonRenderer.submit("phrolova/conducting_throne",(float)(now()%80)/80,intensity,poses,collector);
            var opening=ACTIVE.get(frame.actor());
            if(opening!=null&&opening.actionId().equals("phrolova/conduction_enter"))
                ActionRibbonRenderer.submit(opening.actionId(),opening.progress(now()),intensity,poses,collector);
        }
        if(frame.vine()){
            CamellyaVineRenderer.submit(frame.mesh(),poses,collector);
            var head=frame.mesh().bonePoint("head",0,28,0);var hip=frame.mesh().bonePoint("body",0,12,0);
            if(head.isPresent()&&hip.isPresent())VINE_SUBMISSIONS.put(frame.actor(),new VineSubmitted(head.get().y,hip.get().y,now()));
        }else VINE_SUBMISSIONS.remove(frame.actor());
        if(frame.transformed())SUBMITTED_FORMS.put(frame.actor(),frame.model());
        if(!frame.transformed()&&!frame.conducting()&&!baton&&!recital(frame.actor())&&!emptyHandsCast(frame.actor())) {
            if(!submitActionWeapons(frame.actor(),frame.mesh(),state.mainArm,false,poses,collector,state.lightCoords)){
            var level=Minecraft.getInstance().level;
            var actor=level==null?null:level.getPlayerByUUID(frame.actor());
            // The connected rig socket includes a Y half-turn absent from vanilla idle hands.
            // Keep the corrected native pistol display, compensating only this custom socket.
            var gunBasis=actor!=null&&actor.getMainHandItem().getItem() instanceof cn.zgnhit.mcwuwa.platform.WeaponItem weapon
                &&weapon.character()==cn.zgnhit.mcwuwa.core.CharacterId.GALBRENA?new org.joml.Matrix4f().rotateY((float)Math.PI):null;
            SegmentedMeshRenderer.submitHeldItem(frame.mesh(),state.rightHandItemState,HumanoidArm.RIGHT,poses,collector,state.lightCoords,gunBasis);
            SegmentedMeshRenderer.submitHeldItem(frame.mesh(),state.leftHandItemState,HumanoidArm.LEFT,poses,collector,state.lightCoords,gunBasis);
            }
        }
        poses.popPose();
        // Rendering only: actual armor inventory and attributes are never touched.
        event.setCanceled(true);
    }
    public static void submitOwnedPlayer(RenderPlayerEvent.Pre<?> event){renderPlayer(event);}

    private static void renderHand(RenderHandEvent event) {
        var game=Minecraft.getInstance();var player=game.player;
        if(player==null||player.isInvisible()||player.isSpectator()||!game.options.getCameraType().isFirstPerson())return;
        ActorPresentationState state=renderAction(player.getUUID());
        String modelId=visibleForm(player.getUUID(),state);
        if(!modelId.isEmpty()) {
            POSES.discard(player.getUUID());
            var form=ActionPresentationRegistry.mesh(modelId);
            if(form.isEmpty())return;
            if(!claimHand(event))return;
            boolean hasArms=form.get().bones().stream().anyMatch(bone->bone.name().contains("arm"));
            var geometry=SegmentedMeshRenderer.prepare(form.get(),modelId.equals("hsin_fox")
                ? foxPose(player.walkAnimation.position(event.getPartialTick()),player.walkAnimation.speed(event.getPartialTick()),event.getPartialTick(),true)
                : formPose(modelId,state,event.getPartialTick(),true,game.player.getUUID()),null,hasArms,false);
            var stack=event.getPoseStack();stack.pushPose();
            // Full transformations use their own arm mesh, or a compact model
            // preview to the lower right for forms without humanoid arms.
            // Never submit the player's old skin or force a camera change.
            // The tall Crownless rig needs its own view anchor; the world pose
            // and the player's actual eye/collision position remain unchanged.
            boolean crownlessArms=hasArms&&modelId.equals("echo_crownless");
            var authoredView=ActionPresentationRegistry.formView(modelId);
            if(authoredView.isPresent())stack.mulPose(authoredView.get().modelToView());
            else {
                stack.translate(hasArms?0:.35,crownlessArms?-2.65:hasArms?-1.15:-.55,crownlessArms?-.95:hasArms?-.8:-1.1);
                float scale=crownlessArms?.55f:hasArms?.65f:.25f;stack.scale(scale,scale,scale);
            }
            SegmentedMeshRenderer.submit(geometry,Identifier.fromNamespaceAndPath("mc_wuwa","textures/entity/"+modelId+".png"),stack,event.getSubmitNodeCollector(),event.getPackedLight());
            SUBMITTED_FORMS.put(player.getUUID(),modelId);
            stack.popPose();return;
        }
        boolean conducting=cn.zgnhit.mcwuwa.client.RoleSkinRegistry.formMatches(player,cn.zgnhit.mcwuwa.core.CharacterId.PHROLOVA,"conducting");
        boolean hovering=mornyeHover(player);
        boolean moonRiding=moonRiding(player);
        boolean vine=camellyaVine(player,state);
        boolean pairedIdle=!moonRiding&&state==null&&dualHunter(player)&&POSES.phase(player.getUUID(),true)!=PlayerPosePipeline.Phase.RETURNING;
        if(state==null&&!pairedIdle&&!conducting&&!hovering&&!moonRiding&&!vine&&POSES.phase(player.getUUID(),true)!=PlayerPosePipeline.Phase.RETURNING)return;
        var binding=state==null&&(conducting||hovering||moonRiding||vine)?ActionPresentationRegistry.binding(conducting?"phrolova/conducting_idle":hovering?"mornye/hover_idle":moonRiding?"iuno/new_moon_idle":"camellya/bloom_idle").orElse(null):pairedIdle?ActionPresentationRegistry.binding(
            switch(((cn.zgnhit.mcwuwa.platform.WeaponItem)player.getMainHandItem().getItem()).character()){
                case ZANI->"zani/basic_01";case SIGRIKA->"sigrika/basic_01";case IUNO->"iuno/basic_01";default->"galbrena/basic_01";
            }).orElse(null):renderBinding(player.getUUID(),state);
        if(binding==null)return;
        var clip=ActionPresentationRegistry.clip(binding.actionId(),true);
        String rig=player.getSkin().model()==PlayerModelType.SLIM?"rigs/player_slim":"rigs/player_wide";
        var mesh=ActionPresentationRegistry.mesh(rig);
        if(clip.isEmpty()||mesh.isEmpty()){POSES.discardView(player.getUUID(),true);return;}
        Map<String,ActionPresentationRegistry.Pose> visiblePose;
        // The hunter's opening keyframe raises both pistols past the camera.
        // Hold the reviewed forward-ready pose while idle; gauntlets keep their own neutral start.
        if(pairedIdle)visiblePose=clip.get().sample(player.getMainHandItem().getItem() instanceof cn.zgnhit.mcwuwa.platform.WeaponItem weapon
            &&weapon.character()==cn.zgnhit.mcwuwa.core.CharacterId.GALBRENA?.47f:0);
        else {
            var base=conducting?conductorPose(true,event.getPartialTick()):hovering?hoverPose(true,event.getPartialTick()):moonRiding?moonPose(true,event.getPartialTick()):vine?vinePose(true,event.getPartialTick()):clip.get().sample(0);
            var target=state==null?base:PalPlayerAnimationAdapter.sample(player,state,true,event.getPartialTick())
                .orElseGet(()->clip.get().sample(state.progress(now()+event.getPartialTick())));
            if(humanLiberationOpening(state))target=clip.get().sample(
                state.progress(now()+event.getPartialTick()));
            var mixed=POSES.sample(player.getUUID(),true,rig,ActionPresentationRegistry.revision(),state,binding.posePolicy(),now()+event.getPartialTick(),
                state==null?now():firstContact(state,binding.posePolicy()),base,target);
            if(mixed.phase()==PlayerPosePipeline.Phase.VANILLA&&!conducting&&!hovering&&!moonRiding&&!vine)return;
            visiblePose=mixed.phase()==PlayerPosePipeline.Phase.VANILLA?base:mixed.pose();
        }
        if(!claimHand(event))return;
        // One main-hand submission includes both limbs. Never draw a whole body.
        var flags=new AvatarRenderState();
        flags.showLeftSleeve=player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
        flags.showRightSleeve=player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
        var prepared=SegmentedMeshRenderer.prepare(mesh.get(),visiblePose,flags,true,player.getMainArm()==HumanoidArm.LEFT,binding);
        // One FP view root keeps shoulders near/below the camera and reaches towards -Z.
        // Skin and both items use it together; actual camera, hit boxes and TP are unchanged.
        var poses=event.getPoseStack();poses.pushPose();poses.mulPose(FirstPersonArmView.modelToView(visiblePose));
        SegmentedMeshRenderer.submit(prepared,player.getSkin().body().texturePath(),poses,event.getSubmitNodeCollector(),event.getPackedLight());
        boolean baton=submitPhrolovaBaton(player.getUUID(),prepared,player.getMainArm(),true,conducting,poses,event.getSubmitNodeCollector(),event.getPackedLight());
        if(!conducting&&!baton&&!recital(player.getUUID())&&!emptyHandsCast(player.getUUID())) {
        if(!submitActionWeapons(player.getUUID(),prepared,player.getMainArm(),true,poses,event.getSubmitNodeCollector(),event.getPackedLight())){
        var item=new ItemStackRenderState();
        game.getItemModelResolver().updateForLiving(item,player.getMainHandItem(),player.getMainArm()==HumanoidArm.RIGHT?ItemDisplayContext.FIRST_PERSON_RIGHT_HAND:ItemDisplayContext.FIRST_PERSON_LEFT_HAND,player);
        SegmentedMeshRenderer.submitHeldItem(prepared,item,player.getMainArm(),poses,event.getSubmitNodeCollector(),event.getPackedLight(),
            firstPersonItemCorrection(player.getMainHandItem(),player.getMainArm(),binding.actionId(),state==null?1:state.progress(now()+event.getPartialTick())));
        var offhand=new ItemStackRenderState();
        HumanoidArm other=player.getMainArm()==HumanoidArm.RIGHT?HumanoidArm.LEFT:HumanoidArm.RIGHT;
        game.getItemModelResolver().updateForLiving(offhand,dualHunter(player)||cartethyiaSplitSword(player.getUUID())?player.getMainHandItem():player.getOffhandItem(),other==HumanoidArm.RIGHT?ItemDisplayContext.FIRST_PERSON_RIGHT_HAND:ItemDisplayContext.FIRST_PERSON_LEFT_HAND,player);
        SegmentedMeshRenderer.submitHeldItem(prepared,offhand,other,poses,event.getSubmitNodeCollector(),event.getPackedLight(),
            firstPersonItemCorrection(dualHunter(player)||cartethyiaSplitSword(player.getUUID())?player.getMainHandItem():player.getOffhandItem(),other,binding.actionId(),state==null?1:state.progress(now()+event.getPartialTick())));
        }
        }
        poses.popPose();
    }
    private static org.joml.Matrix4f firstPersonItemCorrection(net.minecraft.world.item.ItemStack stack,HumanoidArm arm,String action,float progress){
        return stack.getItem() instanceof cn.zgnhit.mcwuwa.platform.WeaponItem weapon
            ?FirstPersonWeaponView.basis(weapon.character().weaponPath(),arm,action,progress).orElse(null):null;
    }
    /** Observed empty-handed liberation gestures; never mutate held inventory. */
    private static boolean mornyeHover(Player player){
        return !player.onGround()&&cn.zgnhit.mcwuwa.client.RoleSkinRegistry.formMatches(player,
            cn.zgnhit.mcwuwa.core.CharacterId.MORNYE,"observation");
    }
    static Map<String,ActionPresentationRegistry.Pose> settleBloomRecovery(float progress,Map<String,ActionPresentationRegistry.Pose> target,Map<String,ActionPresentationRegistry.Pose> idle){
        if(progress<=.65f||!idle.containsKey("body"))return target;
        float t=Math.clamp((progress-.65f)/.35f,0,1);t=t*t*(3-2*t);
        var from=target.getOrDefault("body",ActionPresentationRegistry.Pose.identity());var to=idle.get("body");
        var result=new HashMap<>(target);
        result.put("body",new ActionPresentationRegistry.Pose(new org.joml.Vector3f(from.position()).lerp(to.position(),t),
            new org.joml.Vector3f(from.rotation()).lerp(to.rotation(),t),new org.joml.Vector3f(from.scale()).lerp(to.scale(),t)));
        return Map.copyOf(result);
    }
    private static boolean camellyaVine(Player player,ActorPresentationState action){
        if(!cn.zgnhit.mcwuwa.client.RoleSkinRegistry.formMatches(player,cn.zgnhit.mcwuwa.core.CharacterId.CAMELLYA,"bloom"))return false;
        return action==null||action.actionId().startsWith("camellya/bloom_")&&!action.actionId().equals("camellya/bloom_twirl");
    }
    private static Map<String,ActionPresentationRegistry.Pose> vinePose(boolean firstPerson,float partial){
        var pose=ActionPresentationRegistry.clip("camellya/bloom_idle",firstPerson)
            .map(clip->clip.sample((float)((now()+partial)%32)/32)).orElse(Map.of());
        return firstPerson?pose:PalPlayerAnimationAdapter.toThirdPersonRig(pose);
    }
    private static boolean moonRiding(Player player){
        return cn.zgnhit.mcwuwa.client.RoleSkinRegistry.formMatches(player,cn.zgnhit.mcwuwa.core.CharacterId.IUNO,"new_moon");
    }
    private static Map<String,ActionPresentationRegistry.Pose> moonPose(boolean firstPerson,float partial){
        var pose=ActionPresentationRegistry.clip("iuno/new_moon_idle",firstPerson)
            .map(clip->clip.sample((float)((now()+partial)%40)/40)).orElse(Map.of());
        return firstPerson?pose:PalPlayerAnimationAdapter.toThirdPersonRig(pose);
    }
    private static Map<String,ActionPresentationRegistry.Pose> hoverPose(boolean firstPerson,float partial){
        var pose=ActionPresentationRegistry.clip("mornye/hover_idle",firstPerson)
            .map(clip->clip.sample((float)((now()+partial)%48)/48)).orElse(Map.of());
        return firstPerson?pose:PalPlayerAnimationAdapter.toThirdPersonRig(pose);
    }
    private static boolean emptyHandsCast(UUID actor) {
        var state=ACTIVE.get(actor);
        var level=Minecraft.getInstance().level;
        var player=level==null?null:level.getPlayerByUUID(actor);
        if(player!=null&&cn.zgnhit.mcwuwa.client.RoleSkinRegistry.formMatches(player,cn.zgnhit.mcwuwa.core.CharacterId.CAMELLYA,"bloom"))return true;
        if(state!=null&&state.actionId().startsWith("camellya/bloom_"))return true;
        if(player!=null&&moonRiding(player)&&(state==null||state.expired(now())))return true;
        if(player!=null&&mornyeHover(player)
            &&(state==null||state.expired(now())||state.actionId().startsWith("mornye/observation_basic_")
                ||state.actionId().equals("mornye/skill_optimal")||state.actionId().equals("mornye/heavy_inversion")))return true;
        return state!=null&&Set.of("qingxiao/liberation","galbrena/liberation","phoebe/liberation","cantarella/liberation","changli/liberation","ciaccona/liberation","shorekeeper/liberation","hsin/liberation","hsin/liberation_palaces","yinlin/liberation","camellya/liberation","jinhsi/liberation","jinhsi/epiphany_rays","jinhsi/epiphany_final","mornye/liberation","mornye/basic_04","mornye/air","mornye/dodge_counter","aemeath/liberation").contains(state.actionId())&&!state.expired(now());
    }
    private static Map<String,ActionPresentationRegistry.Pose> conductorPose(boolean firstPerson,float partial){
        var pose=ActionPresentationRegistry.clip("phrolova/conducting_idle",firstPerson)
            .map(clip->clip.sample((float)((now()+partial)%80)/80)).orElse(Map.of());
        return firstPerson?pose:PalPlayerAnimationAdapter.toThirdPersonRig(pose);
    }
    /** The conducting prop follows the real hand socket in either view. */
    private static boolean submitPhrolovaBaton(UUID actor,SegmentedMeshRenderer.Prepared body,HumanoidArm arm,boolean firstPerson,boolean conducting,
        com.mojang.blaze3d.vertex.PoseStack poses,net.minecraft.client.renderer.SubmitNodeCollector collector,int light){
        var action=ACTIVE.get(actor);
        if(!conducting&&(action==null||!action.actionId().startsWith("phrolova/")
            ||action.actionId().equals("phrolova/echo")||action.actionId().equals("phrolova/outro")))return false;
        var mesh=ActionPresentationRegistry.mesh("phrolova_conductor_baton");var socket=body.heldItemSocket(arm);
        if(mesh.isEmpty()||socket.isEmpty())return false;
        var baton=SegmentedMeshRenderer.prepare(mesh.get(),Map.of(),null,false,false);
        poses.pushPose();poses.mulPose(new org.joml.Matrix4f(socket.get()).rotateX((float)Math.toRadians(firstPerson?-90:90)));
        SegmentedMeshRenderer.submit(baton,Identifier.fromNamespaceAndPath("mc_wuwa","textures/entity/phrolova_conductor_baton.png"),poses,collector,light);
        poses.popPose();return true;
    }

    /** Skill weapons belong to the animated hand; ordinary inventory items stay untouched. */
    private static boolean submitActionWeapons(UUID actor,SegmentedMeshRenderer.Prepared body,HumanoidArm arm,boolean firstPerson,
        com.mojang.blaze3d.vertex.PoseStack poses,net.minecraft.client.renderer.SubmitNodeCollector collector,int light){
        var action=ACTIVE.get(actor);
        if(action==null)return false;
        // Steering and dismount use empty hands. This is a visual override,
        // never removal or replacement of the real inventory weapon.
        if(action.actionId().equals("lynae/intro"))return true;
        if(action.actionId().startsWith("zhezhi/")&&!Set.of("zhezhi/ink_crane","zhezhi/ink_crane_c5","zhezhi/white_crane_c6","zhezhi/outro").contains(action.actionId())){
            var mesh=ActionPresentationRegistry.mesh("zhezhi_painting_brush");var socket=body.heldItemSocket(arm);
            if(mesh.isEmpty()||socket.isEmpty())return false;
            var brush=SegmentedMeshRenderer.prepare(mesh.get(),Map.of(),null,false,false);
            poses.pushPose();poses.mulPose(new org.joml.Matrix4f(socket.get()).rotateX((float)Math.toRadians(90)));
            float scale=firstPerson?.52f:.8f;poses.scale(scale,scale,scale);
            SegmentedMeshRenderer.submit(brush,Identifier.fromNamespaceAndPath("mc_wuwa","textures/entity/zhezhi_painting_brush.png"),poses,collector,light);
            if(ConstructRenderer.observer!=null)ConstructRenderer.observer.accept(new ConstructRenderer.Submitted("zhezhi_painting_brush","held_paint",actor.toString(),brush.vertexCount(),true,(long)now()));
            poses.popPose();return true;
        }
        if(action.actionId().startsWith("suoming/deep_")||action.actionId().startsWith("suoming/thunder_spin_")||action.actionId().startsWith("suoming/heart")||action.actionId().equals("suoming/liberation")){
            var mesh=ActionPresentationRegistry.mesh("suoming_open_parasol");var socket=body.heldItemSocket(arm);
            if(mesh.isEmpty()||socket.isEmpty())return false;
            float turn=action.actionId().contains("spin")?action.progress(now())*720:0;
            var parasol=SegmentedMeshRenderer.prepare(mesh.get(),Map.of("root",new ActionPresentationRegistry.Pose(new org.joml.Vector3f(),new org.joml.Vector3f(0,turn,0),new org.joml.Vector3f(1))),null,false,false);
            poses.pushPose();poses.mulPose(new org.joml.Matrix4f(socket.get()).rotateX((float)Math.toRadians(90)));
            float scale=firstPerson?.5f:.85f;poses.scale(scale,scale,scale);
            SegmentedMeshRenderer.submit(parasol,Identifier.fromNamespaceAndPath("mc_wuwa","textures/entity/suoming_open_parasol.png"),poses,collector,light);
            if(ConstructRenderer.observer!=null)ConstructRenderer.observer.accept(new ConstructRenderer.Submitted("suoming_open_parasol","held_open",actor.toString(),parasol.vertexCount(),true,(long)now()));
            poses.popPose();return true;
        }
        if(action.actionId().startsWith("zani/")&&!action.actionId().contains("/basic_")&&!action.actionId().equals("zani/liberation")){
            var mesh=ActionPresentationRegistry.mesh("zani_mechanical_guard_blade");var socket=body.heldItemSocket(arm);
            if(mesh.isEmpty()||socket.isEmpty())return false;
            boolean extended=action.actionId().startsWith("zani/heavy_")||action.actionId().equals("zani/finale");
            var pose=Map.of("blade",new ActionPresentationRegistry.Pose(new org.joml.Vector3f(),new org.joml.Vector3f(),new org.joml.Vector3f(extended?1:.001f)));
            var shield=SegmentedMeshRenderer.prepare(mesh.get(),pose,null,false,false);
            poses.pushPose();poses.mulPose(new org.joml.Matrix4f(socket.get()).rotateX((float)Math.toRadians(90)));
            if(firstPerson)poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
            float scale=firstPerson?.45f:.65f;poses.scale(scale,scale,scale);
            SegmentedMeshRenderer.submit(shield,Identifier.fromNamespaceAndPath("mc_wuwa","textures/entity/zani_mechanical_guard_blade.png"),poses,collector,light);
            if(ConstructRenderer.observer!=null)ConstructRenderer.observer.accept(new ConstructRenderer.Submitted("zani_mechanical_guard_blade",extended?"held_blade":"held_guard",actor.toString(),shield.vertexCount(),true,(long)now()));
            poses.popPose();return true;
        }
        if(action.actionId().matches("phoebe/(ring_)?basic_0[1-3]")||Set.of("phoebe/heavy","phoebe/heavy_starlight","phoebe/refraction").contains(action.actionId())){
            var mesh=ActionPresentationRegistry.mesh("skill_phoebe_staff");var socket=body.heldItemSocket(arm);
            if(mesh.isEmpty()||socket.isEmpty())return false;
            // Existing glide prop is authored horizontally with its crown at -Z.
            // Rotate the prop itself into the same +Y crown basis as a hand-held wand.
            var staff=SegmentedMeshRenderer.prepare(mesh.get(),Map.of("root",new ActionPresentationRegistry.Pose(new org.joml.Vector3f(),new org.joml.Vector3f(90,0,0),new org.joml.Vector3f(1))),null,false,false);
            poses.pushPose();poses.mulPose(new org.joml.Matrix4f(socket.get()).rotateX((float)Math.toRadians(90)));
            float scale=firstPerson?.65f:.9f;poses.scale(scale,scale,scale);
            SegmentedMeshRenderer.submit(staff,Identifier.fromNamespaceAndPath("mc_wuwa","textures/entity/skill_phoebe_staff.png"),poses,collector,light);
            if(ConstructRenderer.observer!=null)ConstructRenderer.observer.accept(new ConstructRenderer.Submitted("skill_phoebe_staff","held_cast",actor.toString(),staff.vertexCount(),true,(long)now()));
            poses.popPose();return true;
        }
        if(action.actionId().startsWith("carlotta/death_knell_")||action.actionId().startsWith("carlotta/fatal_finale")){
            var mesh=ActionPresentationRegistry.mesh("carlotta_crystal_rifle");var socket=body.heldItemSocket(arm);
            if(mesh.isEmpty()||socket.isEmpty())return false;
            var rifle=SegmentedMeshRenderer.prepare(mesh.get(),Map.of(),null,false,false);
            poses.pushPose();poses.mulPose(new org.joml.Matrix4f(socket.get()).rotateX((float)Math.toRadians(90)));
            float scale=firstPerson?.48f:.75f;poses.scale(scale,scale,scale);
            SegmentedMeshRenderer.submit(rifle,Identifier.fromNamespaceAndPath("mc_wuwa","textures/entity/carlotta_crystal_rifle.png"),poses,collector,light);
            if(ConstructRenderer.observer!=null)ConstructRenderer.observer.accept(new ConstructRenderer.Submitted("carlotta_crystal_rifle","held_rifle",actor.toString(),rifle.vertexCount(),true,(long)now()));
            poses.popPose();return true;
        }
        if(action.actionId().matches("iuno/basic_0[1-3]")||Set.of("iuno/heavy_ring","iuno/dodge_counter","iuno/air","iuno/skill","iuno/skill_ending","iuno/skill_unending","iuno/intro").contains(action.actionId())){
            var mesh=ActionPresentationRegistry.mesh("iuno_crescent_ring");var socket=body.heldItemSocket(arm);
            if(mesh.isEmpty()||socket.isEmpty())return false;
            var ring=SegmentedMeshRenderer.prepare(mesh.get(),Map.of(),null,false,false);
            var ringFrame=new org.joml.Matrix4f(socket.get()).rotateX((float)Math.toRadians(firstPerson?-90:90));
            if(!firstPerson&&action.actionId().equals("iuno/basic_02")){
                var rightFoot=body.bonePoint("right_shin",2,0,0);var leftFoot=body.bonePoint("left_shin",-2,0,0);
                if(rightFoot.isPresent()&&leftFoot.isPresent()){
                    float progress=action.progress(now());
                    float blend=Math.clamp(Math.min((progress-.08f)/.22f,(.94f-progress)/.22f),0,1);
                    blend=blend*blend*(3-2*blend);
                    var center=rightFoot.get().add(leftFoot.get()).mul(.5f).add(0,.10f,0);
                    // Approved ring geometry is centered at y12px; flatten it at
                    // the animated feet during the inverted second strike.
                    var orbit=new org.joml.Matrix4f().translation(center).rotateX((float)Math.PI/2)
                        .translate(0,-.75f*.85f,0);
                    var position=ringFrame.getTranslation(new org.joml.Vector3f()).lerp(orbit.getTranslation(new org.joml.Vector3f()),blend);
                    var rotation=ringFrame.getNormalizedRotation(new org.joml.Quaternionf()).slerp(orbit.getNormalizedRotation(new org.joml.Quaternionf()),blend);
                    ringFrame.translationRotate(position,rotation);
                }
            }
            poses.pushPose();poses.mulPose(ringFrame);
            float scale=firstPerson?.62f:.85f;poses.scale(scale,scale,scale);
            SegmentedMeshRenderer.submit(ring,Identifier.fromNamespaceAndPath("mc_wuwa","textures/entity/iuno_crescent_ring.png"),poses,collector,light);
            if(ConstructRenderer.observer!=null)ConstructRenderer.observer.accept(new ConstructRenderer.Submitted("iuno_crescent_ring","held_ring",actor.toString(),ring.vertexCount(),true,(long)now()));
            poses.popPose();return true;
        }
        if(action.actionId().startsWith("cantarella/")&&!Set.of("cantarella/liberation","cantarella/skill_drain","cantarella/jellyfish").contains(action.actionId())){
            var umbrella=ActionPresentationRegistry.mesh("cantarella_held_umbrella");var socket=body.heldItemSocket(arm);
            if(umbrella.isEmpty()||socket.isEmpty())return false;
            var prepared=SegmentedMeshRenderer.prepare(umbrella.get(),Map.of(),null,false,false);
            poses.pushPose();poses.mulPose(new org.joml.Matrix4f(socket.get()).rotateX((float)Math.toRadians(firstPerson?-90:90)));
            float scale=firstPerson?.5f:.8f;poses.scale(scale,scale,scale);
            SegmentedMeshRenderer.submit(prepared,Identifier.fromNamespaceAndPath("mc_wuwa","textures/entity/cantarella_held_umbrella.png"),poses,collector,light);
            if(ConstructRenderer.observer!=null)ConstructRenderer.observer.accept(new ConstructRenderer.Submitted("cantarella_held_umbrella","held_umbrella",actor.toString(),prepared.vertexCount(),true,(long)now()));
            poses.popPose();return true;
        }
        if(action.actionId().equals("hiyuki/heavy")){
            var bow=ActionPresentationRegistry.mesh("hiyuki_frost_bow");
            HumanoidArm bowHand=arm==HumanoidArm.RIGHT?HumanoidArm.LEFT:HumanoidArm.RIGHT;
            var socket=body.heldItemSocket(bowHand);if(bow.isEmpty()||socket.isEmpty())return false;
            var prepared=SegmentedMeshRenderer.prepare(bow.get(),Map.of(),null,false,false);
            poses.pushPose();poses.mulPose(new org.joml.Matrix4f(socket.get()).rotateX((float)Math.toRadians(firstPerson?-90:90)));
            float scale=firstPerson?.62f:.85f;poses.scale(scale,scale,scale);
            SegmentedMeshRenderer.submit(prepared,Identifier.fromNamespaceAndPath("mc_wuwa","textures/entity/hiyuki_frost_bow.png"),poses,collector,light);
            if(ConstructRenderer.observer!=null)ConstructRenderer.observer.accept(new ConstructRenderer.Submitted("hiyuki_frost_bow","held_draw",actor.toString(),prepared.vertexCount(),true,(long)now()));
            poses.popPose();return true;
        }
        if(action.actionId().equals("suisui/liberation")||action.actionId().startsWith("suisui/rain_")||action.actionId().matches("suisui/basic_0[1-4]")){
            var fan=ActionPresentationRegistry.mesh("skill_suisui_hand_fan");if(fan.isEmpty())return false;
            var prepared=SegmentedMeshRenderer.prepare(fan.get(),Map.of(),null,false,false);
            for(var hand:HumanoidArm.values()){
                var socket=body.heldItemSocket(hand);if(socket.isEmpty())continue;
                poses.pushPose();poses.mulPose(new org.joml.Matrix4f(socket.get()).rotateX((float)Math.toRadians(firstPerson?-90:90)));
                float size=firstPerson?.65f:.85f;poses.scale(size,size,size);poses.translate(0,-3f/16,0);
                SegmentedMeshRenderer.submit(prepared,Identifier.fromNamespaceAndPath("mc_wuwa","textures/entity/skill_suisui_hand_fan.png"),poses,collector,light);
                if(ConstructRenderer.observer!=null)ConstructRenderer.observer.accept(new ConstructRenderer.Submitted("skill_suisui_hand_fan","held_fan_"+hand.name().toLowerCase(java.util.Locale.ROOT),actor.toString(),prepared.vertexCount(),true,(long)now()));
                poses.popPose();
            }
            return true;
        }
        if(action.actionId().equals("cartethyia/fleurdelys_heavy_enhanced")||action.actionId().equals("cartethyia/fleurdelys_enhanced_heavy")){
            float progress=action.progress(now());
            // The sword becomes the forward arrow; do not leave another copy in the drawing hand.
            return progress>.35f&&progress<.8f;
        }
        if(Set.of("aero_rover/liberation","aero_rover/heavy_strangle","aero_rover/wind_air_01","aero_rover/wind_air_02","aero_rover/unbound_wind_01","aero_rover/unbound_wind_02").contains(action.actionId())){
            var wind=ActionPresentationRegistry.mesh("rover_wind_blade");var hand=body.heldItemSocket(arm);
            if(wind.isEmpty()||hand.isEmpty())return false;
            // Raw energy blade grows along +Y, just like the approved held finale
            // sword. Use the actual animated hand rather than a stationary world copy.
            var prepared=SegmentedMeshRenderer.prepare(wind.get(),Map.of(),null,false,false);
            poses.pushPose();poses.mulPose(cartethyiaBladeBasis(hand.get(),firstPerson));
            float scale=action.actionId().equals("aero_rover/liberation")?(firstPerson?1.15f:1.65f):(firstPerson?.75f:1.05f);poses.scale(scale,scale,scale);poses.translate(0,-3f/16,0);
            SegmentedMeshRenderer.submit(prepared,Identifier.fromNamespaceAndPath("mc_wuwa","textures/entity/rover_wind_blade.png"),poses,collector,light);
            if(ConstructRenderer.observer!=null)ConstructRenderer.observer.accept(new ConstructRenderer.Submitted("rover_wind_blade","held_sweep",actor.toString(),prepared.vertexCount(),true,(long)now()));
            poses.popPose();return true;
        }
        if(!action.actionId().equals("cartethyia/liberation_finale"))return false;
        var mesh=ActionPresentationRegistry.mesh("cartethyia_storm_blade");var socket=body.heldItemSocket(arm);
        if(mesh.isEmpty()||socket.isEmpty())return false;
        var blade=SegmentedMeshRenderer.prepare(mesh.get(),Map.of(),null,false,false);
        poses.pushPose();poses.mulPose(cartethyiaBladeBasis(socket.get(),firstPerson));
        float scale=firstPerson?1.2f:1.65f;poses.scale(scale,scale,scale);poses.translate(0,-3f/16,0);
        SegmentedMeshRenderer.submit(blade,Identifier.fromNamespaceAndPath("mc_wuwa","textures/entity/cartethyia_storm_blade.png"),poses,collector,light);
        if(ConstructRenderer.observer!=null)ConstructRenderer.observer.accept(new ConstructRenderer.Submitted("cartethyia_storm_blade","held_cleave",actor.toString(),blade.vertexCount(),true,(long)now()));
        poses.popPose();return true;
    }
    /** Raw skill geometry grows along +Y; item sockets include an item-display basis.
     * Dedicated FP clips use the opposite arm/view convention to the native TP rig. */
    static org.joml.Matrix4f cartethyiaBladeBasis(org.joml.Matrix4f socket,boolean firstPerson){
        // Roll about the raw blade's own +Y axis: its broad face must face sideways,
        // so its cutting plane follows the actor's forward/downward swing.
        return new org.joml.Matrix4f(socket).rotateX((float)Math.toRadians(firstPerson?-90:90))
            .rotateY((float)Math.toRadians(90));
    }
    private static boolean dualHunter(net.minecraft.world.entity.LivingEntity actor){
        return actor.getMainHandItem().getItem() instanceof cn.zgnhit.mcwuwa.platform.WeaponItem weapon&&(weapon.character()==cn.zgnhit.mcwuwa.core.CharacterId.ZANI||weapon.character()==cn.zgnhit.mcwuwa.core.CharacterId.GALBRENA||weapon.character()==cn.zgnhit.mcwuwa.core.CharacterId.SIGRIKA||weapon.character()==cn.zgnhit.mcwuwa.core.CharacterId.IUNO);
    }
    /** Visual split only; never inserts a second weapon into either inventory hand. */
    private static boolean cartethyiaSplitSword(UUID actor){
        var action=renderAction(actor);
        return action!=null&&action.actionId().equals("cartethyia/fleurdelys_air_02")
            &&action.progress(now())>.08f&&action.progress(now())<.92f;
    }
    private static Map<String,ActionPresentationRegistry.Pose> foxPose(float distance,float speed,float partial,boolean firstPerson) {
        // Native distance drives diagonal leg pairs; standing never moonwalks on a global clock.
        String clip=speed>.55f?"run":speed>.015f?"walk":"idle";
        float progress=clip.equals("idle")?(float)((now()+partial)%80)/80
            :(distance/(float)(2*Math.PI))%1;
        var pose=ActionPresentationRegistry.modelPose("hsin_fox",clip,progress);
        return pose;
    }
}
