package cn.zgnhit.mcwuwa.platform;

import cn.zgnhit.mcwuwa.content.mechanics.MechanicsState;
import cn.zgnhit.mcwuwa.core.CharacterInstance;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/** Accepted role commands only. Distances/speeds are explicit Minecraft adaptations. */
public final class SpecialMovementRuntime {
    private static final Identifier FOX_SPEED=Identifier.fromNamespaceAndPath("mc_wuwa","fox_travel_speed");
    private static final Identifier BLOOM_ROOT=Identifier.fromNamespaceAndPath("mc_wuwa","camellya_bloom_root");
    private static final Identifier CRUISE_SPEED=Identifier.fromNamespaceAndPath("mc_wuwa","lynae_cruise_speed");
    private static final Identifier LUPA_SPEED=Identifier.fromNamespaceAndPath("mc_wuwa","lupa_sprint_speed");
    private boolean ownsGravity,originalNoGravity;
    private boolean hsinSupported,hsinAirWalk;
    private double supportY;
    private Vec3 lastSupport=Vec3.ZERO;
    private String movementMode="";
    private UUID supportOwner;
    private UUID aerialOwner;
    private String aerialAction="";
    private long aerialUntil;
    private UUID driftOwner;
    private long driftStart,driftUntil;
    private Vec3 drift=Vec3.ZERO;
    private boolean iunoExtensionPending;
    private UUID moonPlungeOwner;
    private UUID skillDescentOwner;
    private long skillDescentAt;
    private cn.zgnhit.mcwuwa.platform.entity.ConstructEntity glideVisual;
    private cn.zgnhit.mcwuwa.platform.entity.ConstructEntity swordFlightVisual;
    private cn.zgnhit.mcwuwa.platform.entity.ConstructEntity moonPlatformVisual;
    private cn.zgnhit.mcwuwa.platform.entity.ConstructEntity inkRunVisual;
    private cn.zgnhit.mcwuwa.platform.entity.ConstructEntity observationVisual;
    private String observationVisualAction="";
    private cn.zgnhit.mcwuwa.platform.entity.ConstructEntity formEntryVisual;
    public void pauseOneTick(){
        skillDescentAt=cn.zgnhit.mcwuwa.core.CinematicTimeRules.postpone(skillDescentAt,1);
        aerialUntil=cn.zgnhit.mcwuwa.core.CinematicTimeRules.postpone(aerialUntil,1);
        driftStart=cn.zgnhit.mcwuwa.core.CinematicTimeRules.postpone(driftStart,1);driftUntil=cn.zgnhit.mcwuwa.core.CinematicTimeRules.postpone(driftUntil,1);
    }

    /** Called only for an accepted form transition, including non-attacking transformations. */
    public void formChanged(ServerPlayer player,CharacterInstance role,String form) {
        if(!active(player,role))return;
        if(formEntryVisual!=null){formEntryVisual.discard();formEntryVisual=null;}
        if(role.character()==cn.zgnhit.mcwuwa.core.CharacterId.HSIN&&form.equals("illuminated"))
            formEntryVisual=SkillModelProfiles.light(player,role,"hsin/illuminated_entry",player.position(),20,true);
    }

    public void apply(ServerPlayer player,CharacterInstance role,String command) {
        if(!active(player,role))return;
        if(command.equals("qingxiao_flight_end")){if(movementMode.equals("qingxiao_sword_flight")){restoreGravity(player);movementMode="";}return;}
        if(command.equals("qingxiao_sword_flight")) {
            if(TraversalMotion.solidSupport(player)){player.move(MoverType.SELF,new Vec3(0,.15,0));player.setOnGround(false);}
            player.fallDistance=0;player.syncVelocity=true;return;
        }
        var six=cn.zgnhit.mcwuwa.content.mechanics.SixMovementRules.command(command);
        if(six!=null) {
            var input=player.getLastClientInput();
            // Death Knell's optional displacement follows direction keys, not
            // residual walking/knockback velocity after the keys were released.
            if(command.equals("carlotta_death_knell")&&!input.forward()&&!input.backward()&&!input.left()&&!input.right())return;
            Vec3 direction=six.directional()?inputDirection(player,(input.left()?1:0)-(input.right()?1:0),(input.forward()?1:0)-(input.backward()?1:0)):horizontalForward(player);
            if(direction.lengthSqr()<1e-8)direction=horizontalForward(player);else direction=direction.normalize();
            int ticks=six.ticks();double distance=six.forward();
            iunoExtensionPending=command.equals("iuno_beyond");
            if(command.equals("iuno_beyond")&&(input.forward()||input.backward()||input.left()||input.right())){ticks=10;distance=3.4;iunoExtensionPending=false;}
            distance=approachDistance(player,direction,distance);
            driftOwner=role.instanceId();driftStart=PlatformRuntime.time(player);driftUntil=driftStart+ticks;drift=direction.scale(distance/ticks);
            double launch=six.launch();
            if(launch>0) {
                if(TraversalMotion.solidSupport(player))player.move(MoverType.SELF,new Vec3(0,.12,0));
                player.setOnGround(false);var velocity=player.getDeltaMovement();player.setDeltaMovement(velocity.x,Math.max(velocity.y,launch),velocity.z);
            }
            if(six.hover()>0&&!TraversalMotion.solidSupport(player)){
                long previous=role.instanceId().equals(aerialOwner)&&role.actionId().equals(aerialAction)?aerialUntil:0;
                aerialOwner=role.instanceId();aerialAction=role.actionId();aerialUntil=Math.max(previous,PlatformRuntime.time(player)+six.hover());
            }
            player.fallDistance=0;player.syncVelocity=true;return;
        }
        switch(command) {
            case "galbrena_retreat" -> {
                driftOwner=role.instanceId();driftStart=PlatformRuntime.time(player);driftUntil=driftStart+5;drift=horizontalForward(player).scale(-.32);
                var velocity=player.getDeltaMovement();player.setDeltaMovement(drift.x,Math.max(.20,velocity.y),drift.z);player.setOnGround(false);player.syncVelocity=true;
            }
            case "lynae_plunge","tide_plunge" -> {
                if(role.character()==cn.zgnhit.mcwuwa.core.CharacterId.IUNO)moonPlungeOwner=role.instanceId();
                Vec3 velocity=player.getDeltaMovement();
                player.setDeltaMovement(velocity.x*.35,Math.min(-1.1,velocity.y),velocity.z*.35);
                player.fallDistance=0;player.syncVelocity=true;
            }
            case "qiuyuan_run","phantom_leap","enter_observation_air","rover_updraft","hsin_uppercut","chisa_heavy_jump","cartethyia_uppercut" -> {
                Vec3 before=player.position();player.move(MoverType.SELF,new Vec3(0,.12,0));
                if(player.getY()>before.y+.001)player.setOnGround(false);
                Vec3 velocity=player.getDeltaMovement();
                player.setDeltaMovement(velocity.x,Math.max(command.equals("phantom_leap")?.68:.42,velocity.y),velocity.z);
                player.fallDistance=0;player.syncVelocity=true;
            }
            case "hiyuki_iai_blink" -> {blink(player);ModAttachments.team(player).state().stamina().resetAirDodge();}
            case "cartethyia_heavy_retreat" -> {
                player.move(MoverType.SELF,horizontalForward(player).scale(-1.5));
                player.teleportTo(player.getX(),player.getY(),player.getZ());player.syncVelocity=true;
            }
            case "chisa_glide" -> {
                if(TraversalMotion.solidSupport(player)){player.move(MoverType.SELF,new Vec3(0,.06,0));player.setOnGround(false);}
            }
            case "hsin_fox_travel","slow_descent" -> { }
            default -> { }
        }
    }
    /** Action-bound support, never a permanent flight permission. Durations are MC timelines. */
    public void extendCommandSupport(ServerPlayer player,CharacterInstance role,String action,int duration){
        if(role.instanceId().equals(aerialOwner)&&action.equals(aerialAction)
            &&(cn.zgnhit.mcwuwa.content.mechanics.SixMovementRules.holdsAction(action)||cn.zgnhit.mcwuwa.content.mechanics.SixMovementRules.leapsAction(action)))
            aerialUntil=Math.max(aerialUntil,PlatformRuntime.time(player)+duration+6);
    }
    public void actionStarted(ServerPlayer player,CharacterInstance role,MechanicsState state,String action,int duration) {
        if(!action.equals("iuno/air"))moonPlungeOwner=null;
        skillDescentOwner=action.equals("changli/skill")?role.instanceId():null;
        skillDescentAt=skillDescentOwner==null?0:PlatformRuntime.time(player)+17;
        aerialAction="";aerialUntil=0;aerialOwner=role.instanceId();
        driftStart=PlatformRuntime.time(player);driftUntil=0;driftOwner=role.instanceId();drift=Vec3.ZERO;iunoExtensionPending=false;
        // A grounded brush finish is a short ballistic hop; an already airborne
        // brush cast keeps its existing action-bound aerial support.
        boolean groundedBrushHop=action.equals("zhezhi/basic_03")&&TraversalMotion.solidSupport(player);
        var authored=cn.zgnhit.mcwuwa.content.mechanics.ActionMotionRules.forAction(state,action);
        // Normal Phrolova A2 is directional: standing still must not force a dash.
        // Read the accepted server input, never infer motion from decorative bones.
        if(action.equals("phrolova/basic_02")&&state.form().equals("normal")) {
            var input=player.getLastClientInput();
            var direction=TraversalMotion.horizontal(player,(input.left()?1:0)-(input.right()?1:0),(input.forward()?1:0)-(input.backward()?1:0),1);
            if(direction.lengthSqr()>0){drift=direction.scale(.18);driftUntil=PlatformRuntime.time(player)+Math.min(duration,6);}
        }
        if(authored!=null){
            Vec3 facing=horizontalForward(player);double distance=authored.forward();
            boolean inkDash=action.startsWith("qiuyuan/ink_heavy_");
            if(inkDash){
                var target=player.level().getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(16),
                    entity->PlatformRuntime.canAttack(player,entity)&&player.hasLineOfSight(entity)).stream()
                    .filter(entity->entity.position().subtract(player.position()).multiply(1,0,1).dot(horizontalForward(player))>0)
                    .min(java.util.Comparator.comparingDouble(player::distanceToSqr));
                if(target.isPresent())facing=target.get().position().subtract(player.position()).multiply(1,0,1).normalize();
            }
            if(distance>0&&!inkDash){
                // Approach a target, do not pass through it before this action's hit
                // frame and leave every subsequent forward attack facing empty air.
                for(var target:player.level().getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(distance+2),entity->PlatformRuntime.canAttack(player,entity)&&player.hasLineOfSight(entity))){
                    var delta=target.position().subtract(player.position());double projection=delta.x*facing.x+delta.z*facing.z;
                    double clearance=(target.getBbWidth()+player.getBbWidth())*.5+.3;
                    double lateral=delta.x*delta.x+delta.z*delta.z-projection*projection;
                    if(projection>=0&&Math.abs(delta.y)<3&&lateral<Math.max(1,clearance*clearance))distance=Math.min(distance,Math.max(0,projection-clearance));
                }
            }
            drift=facing.scale(distance/authored.ticks());driftStart=PlatformRuntime.time(player)+authored.delayTicks();
            driftUntil=driftStart+Math.min(Math.max(0,duration-authored.delayTicks()),authored.ticks());
            if(authored.launch()>0){
                if(TraversalMotion.solidSupport(player))player.move(MoverType.SELF,new Vec3(0,.12,0));
                player.setOnGround(false);var velocity=player.getDeltaMovement();player.setDeltaMovement(velocity.x,Math.max(velocity.y,authored.launch()),velocity.z);player.syncVelocity=true;
            }
        }
        boolean leap=action.startsWith("lynae/phantom_leap_");
        boolean launch=action.equals("jinhsi/skill_overflowing")||action.equals("cartethyia/skill")||action.equals("cartethyia/fleurdelys_skill")
            ||action.equals("cartethyia/fleurdelys_skill_followup")||action.equals("hsin/illuminated_uppercut")||action.equals("aero_rover/skill")||action.equals("chisa/heavy")||action.startsWith("qiuyuan/ink_heavy_");
        boolean aerial=(!groundedBrushHop&&action.startsWith("zhezhi/")&&!action.equals("zhezhi/air_basic_02")&&!action.equals("zhezhi/outro"))
            ||cn.zgnhit.mcwuwa.content.mechanics.SixMovementRules.holdsAction(action)||action.startsWith("cartethyia/fleurdelys_air_0")&&!action.endsWith("03")
            // E4 clears incarnation immediately in mechanics, but its cast
            // still holds the caster in the air until the discharge finishes.
            ||action.equals("jinhsi/epiphany_rays")||action.equals("jinhsi/epiphany_final")
            ||action.startsWith("aemeath/mech_basic_")||action.startsWith("yangyang_xuanling/listening_basic_")
            ||action.equals("aero_rover/wind_air_01")||action.equals("aero_rover/wind_air_02")
            ||action.equals("hiyuki/future_air_01")||action.equals("hiyuki/future_air_02")||action.equals("hiyuki/skill_falling")
            ||action.startsWith("hsin/illuminated_basic_")||action.startsWith("hsin/thousand_palaces_basic_")||action.equals("chisa/shattered_plane")||action.startsWith("qiuyuan/ink_heavy_")||action.startsWith("ciaccona/air_");
        if(launch&&TraversalMotion.solidSupport(player)){
            player.move(MoverType.SELF,new Vec3(0,.35,0));player.setOnGround(false);
            player.setDeltaMovement(player.getDeltaMovement().add(0,.18,0));player.syncVelocity=true;
        }
        if(leap||launch||aerial&&!TraversalMotion.solidSupport(player)){
            aerialAction=action;aerialUntil=PlatformRuntime.time(player)+duration+(leap?24:action.startsWith("qiuyuan/ink_heavy_")?12:6);
        }
        // Intro support lasts through the dismount/light cradle, then releases
        // gravity in time for the landing/recovery portion of the same action.
        if((action.equals("lynae/intro")||action.equals("phoebe/intro"))&&!TraversalMotion.solidSupport(player)){
            aerialAction=action;aerialUntil=PlatformRuntime.time(player)+Math.round(duration*.72);
        }
        if(action.equals("augusta/skill_descent")||action.equals("augusta/air")||action.equals("augusta/air_counter")){
            // End the previous leap's support before the accepted plunge.
            restoreGravity(player);aerialUntil=0;aerialAction="";
            Vec3 velocity=player.getDeltaMovement();
            player.setDeltaMovement(velocity.x*.35,Math.min(-1.1,velocity.y),velocity.z*.35);
            player.fallDistance=0;player.syncVelocity=true;
        }
    }
    public void tick(ServerPlayer player,CharacterInstance role,MechanicsState state) {
        if(state==null||!active(player,role)){clear(player);return;}
        boolean moonPlatform=state.characterId().equals("iuno")&&state.form().equals("new_moon")&&state.timer("iuno_phase")>0;
        if(moonPlatform&&(moonPlatformVisual==null||moonPlatformVisual.isRemoved()))
            moonPlatformVisual=cn.zgnhit.mcwuwa.platform.entity.ConstructEntity.spawn(player.level(),player,role.instanceId(),
                "iuno_new_moon_platform",player.position().add(0,.04,0),12000,1.1f,true,"idle");
        else if(!moonPlatform&&moonPlatformVisual!=null){moonPlatformVisual.discard();moonPlatformVisual=null;}
        if((EchoRuntime.holdsLoreleiAirCast(player)||EchoRuntime.holdsHeronAirCast(player))&&!TraversalMotion.solidSupport(player)&&!player.isInWater()
            &&!player.isPassenger()&&!player.getAbilities().flying&&!player.isFallFlying()){
            if(!ownsGravity){originalNoGravity=player.isNoGravity();ownsGravity=true;}
            player.setNoGravity(true);movementMode="combat_hover";
            var input=player.getLastClientInput();TraversalMotion.apply(player,movementMode,0,(input.left()?1:0)-(input.right()?1:0),(input.forward()?1:0)-(input.backward()?1:0));
            player.syncVelocity=true;return;
        }
        if(state.characterId().equals("cantarella")&&state.form().equals("mirage")
            &&state.timer("cantarella_surf")<=0&&!player.isPassenger()&&!player.getAbilities().flying&&!player.isFallFlying()){
            var floor=player.level().clip(new net.minecraft.world.level.ClipContext(player.position().add(0,1,0),player.position().add(0,-3,0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.ANY,player));
            if(floor.getType()!=net.minecraft.world.phys.HitResult.Type.MISS){
                if(!ownsGravity){originalNoGravity=player.isNoGravity();ownsGravity=true;}
                player.setNoGravity(true);movementMode="cantarella_mirage";
                var input=player.getLastClientInput();TraversalMotion.apply(player,movementMode,0,(input.left()?1:0)-(input.right()?1:0),(input.forward()?1:0)-(input.backward()?1:0));
                var motion=player.getDeltaMovement();
                player.setDeltaMovement(motion.x,Math.clamp(floor.getLocation().y+.35-player.getY(),-.12,.12),motion.z);
                player.fallDistance=0;player.syncVelocity=true;return;
            }
        }
        boolean staffDive=state.characterId().equals("phoebe")&&state.timer("phoebe_staff_dive")>0&&role.actionId().equals("phoebe/air")&&!TraversalMotion.solidSupport(player)&&!player.isInWater();
        boolean staffGlide=state.characterId().equals("phoebe")&&state.timer("phoebe_staff_glide")>0&&role.actionId().equals("phoebe/air_glide")&&!TraversalMotion.solidSupport(player)&&!player.isInWater();
        boolean seaSurf=state.characterId().equals("cantarella")&&state.timer("cantarella_surf")>0&&role.actionId().equals("cantarella/heavy_mirage");
        boolean seaSwim=state.characterId().equals("cantarella")&&player.isInWater()
            &&(player.getAbilities().instabuild||ModAttachments.team(player).state().stamina().snapshot().stamina()>=TraversalMotion.CANTARELLA_SWIM_STAMINA_COST);
        if((staffDive||staffGlide||seaSurf||seaSwim)&&!player.isPassenger()&&!player.getAbilities().flying&&!player.isFallFlying()){
            movementMode=staffDive?"phoebe_staff_dive":staffGlide?"phoebe_staff_glide":seaSurf?"cantarella_surf":"cantarella_swim";
            if(movementMode.equals("cantarella_swim"))restoreGravity(player);else {if(!ownsGravity){originalNoGravity=player.isNoGravity();ownsGravity=true;}player.setNoGravity(true);}
            var input=player.getLastClientInput();TraversalMotion.apply(player,movementMode,0,(input.left()?1:0)-(input.right()?1:0),(input.forward()?1:0)-(input.backward()?1:0),input.jump(),input.shift(),input.sprint());
            player.syncVelocity=true;return;
        }
        boolean inkRun=state.characterId().equals("qiuyuan")&&state.timer("qiuyuan_dash")>0;
        if(!inkRun&&inkRunVisual!=null){inkRunVisual.discard();inkRunVisual=null;}
        boolean screens=state.characterId().equals("mornye")&&state.form().equals("observation");
        String panelAction=screens?"mornye/observation_aura":state.characterId().equals("mornye")&&state.timer("block")>0?"mornye/block_guard":"";
        if(!panelAction.equals(observationVisualAction)&&observationVisual!=null){observationVisual.discard();observationVisual=null;}
        observationVisualAction=panelAction;
        if(!panelAction.isEmpty()&&(observationVisual==null||observationVisual.isRemoved()))
            observationVisual=SkillModelProfiles.light(player,role,panelAction,player.position(),Math.max(1,state.timer(screens?"observation":"block")),true);
        if(state.characterId().equals("shorekeeper")&&state.timer("shore_unbound")>0){
            restoreGravity(player);movementMode="shorekeeper_butterfly";
            var input=player.getLastClientInput();TraversalMotion.apply(player,movementMode,0,(input.left()?1:0)-(input.right()?1:0),1);
            player.setSprinting(false);player.syncVelocity=true;
            if(player.tickCount%3==0)player.level().sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,player.getX(),player.getY()+.6,player.getZ(),2,.2,.15,.2,.01);
            if(player.tickCount%10==0)ShorekeeperHarvest.collect(player);
            return;
        }
        if(inkRun) {
            if(inkRunVisual==null||inkRunVisual.isRemoved())inkRunVisual=SkillModelProfiles.light(player,role,"qiuyuan/skill_run",player.position(),state.timer("qiuyuan_dash"),true);
            if(!ownsGravity){originalNoGravity=player.isNoGravity();ownsGravity=true;}
            player.setNoGravity(true);movementMode="qiuyuan_dash";
            TraversalMotion.apply(player,movementMode,0,0,1);player.syncVelocity=true;return;
        }
        boolean gliding=state.characterId().equals("chisa")&&state.timer("chisa_glide")>0;
        if(gliding&&(glideVisual==null||glideVisual.isRemoved()))glideVisual=cn.zgnhit.mcwuwa.platform.entity.ConstructEntity.spawn(player.level(),player,role.instanceId(),"chisa_chainsaw",player.position().add(0,-.04,0),state.timer("chisa_glide"),.8f,true,"glide");
        else if(!gliding&&glideVisual!=null){glideVisual.discard();glideVisual=null;}
        if(supportOwner!=null&&!supportOwner.equals(role.instanceId())){hsinSupported=hsinAirWalk=false;supportOwner=null;}
        movementMode="";
        boolean observation=state.characterId().equals("mornye")&&state.form().equals("observation");
        boolean wandering=cn.zgnhit.mcwuwa.content.mechanics.HsinTravelRules.wandering(state);
        boolean swordFlight=state.characterId().equals("qingxiao")&&state.timer("qingxiao_flight")>0&&state.resource("flight_qi")>0
            &&!TraversalMotion.solidSupport(player)&&!player.isInWater()&&!player.isPassenger()&&!player.getAbilities().flying&&!player.isFallFlying();
        if(swordFlight&&(swordFlightVisual==null||swordFlightVisual.isRemoved()))
            swordFlightVisual=cn.zgnhit.mcwuwa.platform.entity.ConstructEntity.spawn(player.level(),player,role.instanceId(),
                "qingxiao_flight_sword",player.position().add(0,-.04,0),Math.min(12000,state.timer("qingxiao_flight")),.9f,true,"idle");
        else if(!swordFlight&&swordFlightVisual!=null){swordFlightVisual.discard();swordFlightVisual=null;}
        if(moonPlungeOwner!=null&&(!moonPlungeOwner.equals(role.instanceId())||TraversalMotion.solidSupport(player)))moonPlungeOwner=null;
        boolean moonCruise=moonPlungeOwner==null&&state.characterId().equals("iuno")&&!state.form().equals("normal")&&state.timer("iuno_phase")>0
            &&!TraversalMotion.solidSupport(player)&&!player.isInWater()&&!player.isPassenger()&&!player.getAbilities().flying&&!player.isFallFlying();
        boolean flight=state.characterId().equals("aemeath")&&state.form().equals("mech")&&state.timer("aemeath_flight")>0;
        boolean conducting=state.characterId().equals("phrolova")&&state.form().equals("conducting");
        if((flight||conducting)&&!ownsGravity&&TraversalMotion.solidSupport(player)){
            // The conductor and circular seat float well above the fighting body.
            // This is an MC scale adaptation, not a claim of an original metre value.
            // Entity.move resolves the headroom; never teleport through a ceiling.
            Vec3 before=player.position();player.move(MoverType.SELF,new Vec3(0,conducting?5.0:.35,0));
            if(player.getY()>before.y+.001){
                // Hover can zero vertical velocity before the first motion packet.
                // Sync the already collision-resolved lift once; retain yaw/pitch.
                player.teleportTo(player.getX(),player.getY(),player.getZ());player.setOnGround(false);
            }
        }
        boolean actionHover=role.instanceId().equals(aerialOwner)&&PlatformRuntime.time(player)<aerialUntil
            &&(role.actionId().equals(aerialAction)||role.actionId().isEmpty());
        boolean incarnation=state.characterId().equals("jinhsi")&&(state.form().equals("incarnation")||state.form().equals("epiphany"));
        boolean vine=state.characterId().equals("camellya")&&state.form().equals("bloom")
            &&ModAttachments.team(player).state().stamina().snapshot().stamina()>0;
        boolean rain=state.characterId().equals("galbrena")&&state.timer("gal_raining")>0;
        boolean combatHover=conducting||rain||(actionHover||incarnation||vine)&&!TraversalMotion.solidSupport(player);
        if(observation||flight||swordFlight||moonCruise||wandering||combatHover) {
            if(!ownsGravity){originalNoGravity=player.isNoGravity();ownsGravity=true;}
            player.setNoGravity(true);player.fallDistance=0;
            var input=player.getLastClientInput();
            movementMode=vine?"camellya_bloom":swordFlight?"qingxiao_sword_flight":moonCruise?"iuno_phase_cruise":flight?"aemeath_flight":wandering?"hsin_wander":combatHover?
                (actionHover&&aerialAction.startsWith("lynae/phantom_leap_")?"lynae_leap":actionHover&&(cn.zgnhit.mcwuwa.content.mechanics.SixMovementRules.leapsAction(aerialAction)||aerialAction.startsWith("qiuyuan/ink_heavy_")||aerialAction.equals("cartethyia/skill")||aerialAction.equals("jinhsi/skill_overflowing")||aerialAction.equals("hsin/illuminated_uppercut")||aerialAction.equals("aero_rover/skill")||aerialAction.equals("lynae/intro")||aerialAction.equals("phoebe/intro")||aerialAction.equals("chisa/heavy")||aerialAction.equals("cartethyia/fleurdelys_skill")||aerialAction.equals("cartethyia/fleurdelys_skill_followup"))?"combat_leap":"combat_hover"):
                TraversalMotion.observationMode(state.timer("slow_descent")>0,state.timer("flight_sprint")>0,ModAttachments.team(player).state().stamina().snapshot().stamina());
            TraversalMotion.apply(player,movementMode,0,(input.left()?1:0)-(input.right()?1:0),(input.forward()?1:0)-(input.backward()?1:0),flight&&input.jump(),flight&&input.shift(),(flight||swordFlight||moonCruise)&&input.sprint());
            if(flight&&player.tickCount%4==0)player.level().sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,player.getX(),player.getY()+.8,player.getZ(),2,.15,.12,.15,.01);
            player.syncVelocity=true;hsinSupported=hsinAirWalk=false;supportOwner=null;
        } else if(!chisaTraversal(player,state)&&!hsinTraversal(player,state))restoreGravity(player);
        boolean recital=state.characterId().equals("ciaccona")&&state.form().equals("performing");
        boolean heart=state.characterId().equals("suoming")&&state.form().equals("calamity");
        boolean bloom=state.characterId().equals("camellya")&&state.form().equals("bloom");
        boolean rooted=recital||heart;
        if(bloom&&!combatHover){
            movementMode="camellya_bloom";var input=player.getLastClientInput();
            TraversalMotion.apply(player,movementMode,0,(input.left()?1:0)-(input.right()?1:0),(input.forward()?1:0)-(input.backward()?1:0),false,false,false);
            player.syncVelocity=true;
        }
        if(rooted){if(!combatHover)movementMode=heart?"suoming_heart":recital?"ciaccona_recital":"camellya_bloom";player.setDeltaMovement(0,heart?Math.min(0,player.getDeltaMovement().y):player.getDeltaMovement().y,0);player.setSprinting(false);player.syncVelocity=true;}
        var speed=player.getAttribute(Attributes.MOVEMENT_SPEED);
        if(speed!=null) {
            boolean accelerated=state.characterId().equals("lupa")&&state.timer("lupa_sprint_clock")>=50&&player.isSprinting();
            // The source specifies acceleration, not a numeric speed; +20% is the MC mapping.
            if(accelerated&&!speed.hasModifier(LUPA_SPEED))speed.addTransientModifier(new AttributeModifier(LUPA_SPEED,.20,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            else if(!accelerated)speed.removeModifier(LUPA_SPEED);
            if(state.characterId().equals("lynae")&&state.form().equals("cruise")){
                double cap=cn.zgnhit.mcwuwa.content.mechanics.MechanicsEngine.resourceCaps("lynae",role.chain()).get("lumiflow");
                double bonus=role.chain()>=6&&state.resource("lumiflow")>=cap?.45:state.resource("lumiflow")>=120?.25:.15;var current=speed.getModifier(CRUISE_SPEED);
                if(current==null||current.amount()!=bonus){speed.removeModifier(CRUISE_SPEED);speed.addTransientModifier(new AttributeModifier(CRUISE_SPEED,bonus,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));}
            }else speed.removeModifier(CRUISE_SPEED);
            double stanceSlow=rooted?-1:bloom?-.65:0;
            var stanceModifier=speed.getModifier(BLOOM_ROOT);
            if(stanceModifier!=null&&stanceModifier.amount()!=stanceSlow)speed.removeModifier(BLOOM_ROOT);
            if(stanceSlow!=0&&!speed.hasModifier(BLOOM_ROOT))speed.addTransientModifier(new AttributeModifier(BLOOM_ROOT,stanceSlow,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            boolean fox=state.characterId().equals("hsin")&&state.form().equals("fox");
            if(fox&&!speed.hasModifier(FOX_SPEED))speed.addTransientModifier(new AttributeModifier(FOX_SPEED,.5,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            else if(!fox)speed.removeModifier(FOX_SPEED);
        }
        // Heart's Eye: Tribulation ends in a downward charge. One impulse is
        // tied to this accepted cast; cancel/switch/new action removes it.
        if(skillDescentOwner!=null){
            if(!role.instanceId().equals(skillDescentOwner)||!role.actionId().equals("changli/skill")){
                skillDescentOwner=null;skillDescentAt=0;
            }else if(PlatformRuntime.time(player)>=skillDescentAt){
                skillDescentOwner=null;skillDescentAt=0;
                if(!TraversalMotion.solidSupport(player)&&!player.isPassenger()&&!player.getAbilities().flying){
                    restoreGravity(player);aerialUntil=0;aerialAction="";
                    var velocity=player.getDeltaMovement();
                    player.setDeltaMovement(velocity.x,Math.min(-.85,velocity.y),velocity.z);
                    player.fallDistance=0;player.syncVelocity=true;
                }
            }
        }
        if(role.instanceId().equals(driftOwner)&&PlatformRuntime.time(player)<driftUntil&&!player.isPassenger()){
            if(iunoExtensionPending){
                var input=player.getLastClientInput();
                if(input.forward()||input.backward()||input.left()||input.right()){
                    // Movement input may arrive after the accepted cast, while the dash is running.
                    driftUntil=driftStart+10;iunoExtensionPending=false;Network.sync(player);
                }
            }
            if(PlatformRuntime.time(player)>=driftStart){var velocity=player.getDeltaMovement();player.setDeltaMovement(drift.x,velocity.y,drift.z);player.syncVelocity=true;}
        }else {driftUntil=0;drift=Vec3.ZERO;iunoExtensionPending=false;}
    }
    public void clear(ServerPlayer player) {
        if(moonPlatformVisual!=null){moonPlatformVisual.discard();moonPlatformVisual=null;}
        if(swordFlightVisual!=null){swordFlightVisual.discard();swordFlightVisual=null;}
        observationVisualAction="";
        if(formEntryVisual!=null){formEntryVisual.discard();formEntryVisual=null;}
        if(observationVisual!=null){observationVisual.discard();observationVisual=null;}
        if(inkRunVisual!=null){inkRunVisual.discard();inkRunVisual=null;}
        if(PlatformRuntime.time(player)>=driftStart&&driftUntil>PlatformRuntime.time(player)){var velocity=player.getDeltaMovement();player.setDeltaMovement(0,velocity.y,0);player.syncVelocity=true;}
        if(glideVisual!=null){glideVisual.discard();glideVisual=null;}
        restoreGravity(player);
        hsinSupported=hsinAirWalk=false;supportOwner=null;movementMode="";
        aerialOwner=null;aerialAction="";aerialUntil=0;moonPlungeOwner=null;
        skillDescentOwner=null;skillDescentAt=0;
        driftOwner=null;driftStart=driftUntil=0;drift=Vec3.ZERO;iunoExtensionPending=false;
        var speed=player.getAttribute(Attributes.MOVEMENT_SPEED);if(speed!=null){speed.removeModifier(FOX_SPEED);speed.removeModifier(BLOOM_ROOT);speed.removeModifier(CRUISE_SPEED);speed.removeModifier(LUPA_SPEED);}
    }
    private boolean chisaTraversal(ServerPlayer player,MechanicsState state) {
        if(!state.characterId().equals("chisa")||state.form().equals("saw")||state.timer("chisa_glide")<=0
            ||state.timer("chisa_glide_dash")<=0&&state.timer("chisa_glide_held")<=0||player.getAbilities().flying
            ||player.isFallFlying()||player.isPassenger()||player.onClimbable()||player.isInWater()
            ||TraversalMotion.solidSupport(player)&&state.timer("chisa_launch_pending")<=0)return false;
        if(!ownsGravity){originalNoGravity=player.isNoGravity();ownsGravity=true;}player.setNoGravity(true);
        movementMode=state.timer("chisa_glide_dash")>0?"chisa_glide_dash":"chisa_glide";
        var input=player.getLastClientInput();TraversalMotion.apply(player,movementMode,0,(input.left()?1:0)-(input.right()?1:0),(input.forward()?1:0)-(input.backward()?1:0));
        player.syncVelocity=true;return true;
    }
    private boolean hsinTraversal(ServerPlayer player,MechanicsState state) {
        boolean illuminated=state.characterId().equals("hsin")&&state.form().equals("illuminated");
        boolean large=state.characterId().equals("cartethyia")&&state.form().equals("fleurdelys");
        boolean realm=state.characterId().equals("augusta")&&state.form().equals("realm");
        String prefix=realm?"augusta":large?"fleurdelys":"hsin";
        if(!illuminated&&!large&&!realm||player.getAbilities().flying
            ||player.isFallFlying()||player.isPassenger()||player.onClimbable()) {hsinSupported=hsinAirWalk=false;return false;}
        supportOwner=ModAttachments.team(player).state().active().orElseThrow().instanceId();
        var input=player.getLastClientInput();var stamina=ModAttachments.team(player).state().stamina();
        if(input.jump()||input.shift()){hsinSupported=hsinAirWalk=false;return false;}
        var water=TraversalMotion.waterSurface(player);
        if(water.isPresent()) {
            boolean deep=TraversalMotion.deepWater(player,water.getAsDouble());
            if(deep&&!realm&&!stamina.spend(TraversalMotion.DEEP_WATER_COST)){hsinSupported=hsinAirWalk=false;return false;}
            supportY=water.getAsDouble();lastSupport=player.position();movementMode=prefix+"_water";hsinSupported=true;hsinAirWalk=false;
        } else if(TraversalMotion.solidSupport(player)) {hsinSupported=true;hsinAirWalk=false;supportY=player.getY();lastSupport=player.position();return false;}
        else {
            if(realm){hsinSupported=hsinAirWalk=false;return false;}
            boolean validEdge=hsinSupported&&Math.abs(player.getY()-supportY)<=.5&&player.position().distanceToSqr(lastSupport)<=16;
            // Failing to enter air-walk is not a stamina spend. Hsin's live
            // aerial form owns its recovery prohibition; actual water/air-walk
            // costs above/below retain their normal spend cooldown themselves.
            if(player.isSprinting()||!hsinAirWalk&&!validEdge){hsinSupported=false;hsinAirWalk=false;return false;}
            if(!stamina.spend(TraversalMotion.AIR_WALK_COST)){hsinSupported=hsinAirWalk=false;return false;}
            hsinSupported=false;hsinAirWalk=true;movementMode=prefix+"_air";
        }
        if(!ownsGravity){originalNoGravity=player.isNoGravity();ownsGravity=true;}player.setNoGravity(true);
        TraversalMotion.apply(player,movementMode,supportY,(input.left()?1:0)-(input.right()?1:0),(input.forward()?1:0)-(input.backward()?1:0));
        player.syncVelocity=true;return true;
    }
    /** Only the server chooses eligibility. The client receives the same bounded motion rule. */
    public void addView(com.google.gson.JsonObject roleView) {
        var movement=new com.google.gson.JsonObject();movement.addProperty("mode",movementMode);
        movement.addProperty("gravityOwned",ownsGravity);movement.addProperty("baseNoGravity",originalNoGravity);
        if(driftUntil>0){movement.addProperty("driftStart",driftStart);movement.addProperty("driftUntil",driftUntil);movement.addProperty("driftX",drift.x);movement.addProperty("driftZ",drift.z);}
        if(movementMode.startsWith("hsin_")||movementMode.startsWith("fleurdelys_")||movementMode.equals("augusta_water"))movement.addProperty("supportY",supportY);
        roleView.add("movement",movement);
    }
    public boolean supportedOnWater(ServerPlayer player) {
        return (movementMode.equals("hsin_water")||movementMode.equals("fleurdelys_water")||movementMode.equals("augusta_water"))&&TraversalMotion.waterSurface(player).isPresent();
    }
    private void restoreGravity(ServerPlayer player) {
        if(ownsGravity){player.setNoGravity(originalNoGravity);ownsGravity=false;}
    }
    private static boolean active(ServerPlayer player,CharacterInstance role) {
        var team=ModAttachments.team(player).state();
        return player.isAlive()&&!player.isSpectator()&&team.wuwaMode()&&team.active().orElse(null)==role&&role.alive();
    }
    private static Vec3 horizontalForward(ServerPlayer player) {
        Vec3 direction=player.getLookAngle().multiply(1,0,1);
        if(direction.lengthSqr()<1e-8){double yaw=Math.toRadians(player.getYRot());return new Vec3(-Math.sin(yaw),0,Math.cos(yaw));}
        return direction.normalize();
    }
    private static double approachDistance(ServerPlayer player,Vec3 direction,double distance) {
        if(distance<=0)return distance;
        for(var target:player.level().getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(distance+2),entity->PlatformRuntime.canAttack(player,entity)&&player.hasLineOfSight(entity))) {
            var delta=target.position().subtract(player.position());double along=delta.x*direction.x+delta.z*direction.z;
            double clearance=(target.getBbWidth()+player.getBbWidth())*.5+.3;
            double lateral=delta.x*delta.x+delta.z*delta.z-along*along;
            if(along>=0&&Math.abs(delta.y)<3&&lateral<Math.max(1,clearance*clearance))distance=Math.min(distance,Math.max(0,along-clearance));
        }
        return distance;
    }
    /** The client sends positive strafe for A/left. Yaw zero faces +Z, so left is +X. */
    static Vec3 inputDirection(ServerPlayer player,double strafe,double forwardInput) {
        Vec3 forward=horizontalForward(player),left=new Vec3(forward.z,0,-forward.x);
        return forward.scale(Math.clamp(forwardInput,-1,1)).add(left.scale(Math.clamp(strafe,-1,1)));
    }
    static Vec3 dodgeDirection(ServerPlayer player,double strafe,double forwardInput) {
        Vec3 direction=inputDirection(player,strafe,forwardInput);
        return direction.lengthSqr()<.001?horizontalForward(player).scale(-1):direction.normalize();
    }
    private static void blink(ServerPlayer player) {
        Vec3 forward=horizontalForward(player),displacement=forward.scale(-3);
        if(player.getLastClientInput().forward()) {
            LivingEntity target=player.level().getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(8),
                entity->PlatformRuntime.canAttack(player,entity)&&player.hasLineOfSight(entity)
                    &&entity.distanceToSqr(player)<=64&&Math.abs(entity.getY()-player.getY())<=2
                    &&entity.position().subtract(player.position()).normalize().dot(forward)>.5)
                .stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
            if(target!=null) {
                Vec3 facing=Vec3.directionFromRotation(0,target.getYRot());
                Vec3 destination=target.position().subtract(facing.scale(target.getBbWidth()/2+.8));
                Vec3 proposed=new Vec3(destination.x-player.getX(),0,destination.z-player.getZ());
                if(proposed.lengthSqr()<=100&&player.level().noCollision(player,player.getBoundingBox().move(proposed)))displacement=proposed;
            }
        }
        player.move(MoverType.SELF,displacement);
        player.teleportTo(player.getX(),player.getY(),player.getZ());
        player.setDeltaMovement(0,player.getDeltaMovement().y,0);player.syncVelocity=true;
    }
}
