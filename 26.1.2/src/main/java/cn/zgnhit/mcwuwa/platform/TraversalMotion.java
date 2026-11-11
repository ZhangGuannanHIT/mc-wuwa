package cn.zgnhit.mcwuwa.platform;

import java.util.OptionalDouble;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Shared collision-aware movement math, without authority over form or stamina.
 * Distances/speeds are explicit MC authoring, independent of original combat coefficients. */
public final class TraversalMotion {
    public static final double AIR_WALK_SPEED=.065, WATER_WALK_SPEED=.12, DEEP_WATER_COST=.25, AIR_WALK_COST=.25;
    public static final double CHISA_DASH_SPEED=.38,CHISA_GLIDE_SPEED=.18,CHISA_GLIDE_DESCENT=-.045;
    public static final double OBSERVATION_SPEED=.12,OBSERVATION_SPRINT_SPEED=.22,OBSERVATION_DESCENT=-.12;
    /** Source states Cantarella's reduction but gives no ratio. These are MC
     * swimming adaptation costs: 5 stamina/s, or 2.5 stamina/s for Cantarella. */
    public static final double SWIM_STAMINA_COST=.25,CANTARELLA_SWIM_STAMINA_COST=.125;
    private TraversalMotion() { }
    public static String observationMode(boolean descending,boolean sprinting,double stamina){
        if(stamina<.25)return descending?"mornye_exhausted_descent":"mornye_exhausted";
        return descending?"mornye_descent":sprinting?"mornye_sprint":"mornye_hover";
    }
    public static boolean solidSupport(Player player) {
        return solidSupport((net.minecraft.world.entity.Entity)player);
    }
    public static boolean solidSupport(net.minecraft.world.entity.Entity entity) {
        // Validate actual block collision rather than the client-reported onGround bit.
        return entity.level().getBlockCollisions(entity,entity.getBoundingBox().move(0,-.05,0)).iterator().hasNext();
    }
    public static OptionalDouble waterSurface(Player player) {
        // Inspect only loaded blocks around the feet. Never lift a submerged swimmer
        // to the surface, and never interpret lava/waterlogged solid blocks as a platform.
        for(int dy=0;dy>=-1;dy--) {
            BlockPos position=BlockPos.containing(player.getX(),player.getY()+dy-.05,player.getZ());
            if(!player.level().hasChunkAt(position))continue;
            var fluid=player.level().getFluidState(position);
            if(!fluid.is(FluidTags.WATER)||!player.level().getBlockState(position).getCollisionShape(player.level(),position).isEmpty())continue;
            double surface=position.getY()+fluid.getHeight(player.level(),position);
            if(player.getY()>=surface-.25&&player.getY()<=surface+.25)return OptionalDouble.of(surface);
        }
        return OptionalDouble.empty();
    }
    public static boolean deepWater(Player player,double surface) {
        BlockPos below=BlockPos.containing(player.getX(),surface-1.05,player.getZ());
        return player.level().hasChunkAt(below)&&player.level().getFluidState(below).is(FluidTags.WATER);
    }
    public static Vec3 horizontal(Player player,double strafe,double forward,double speed) {
        double yaw=Math.toRadians(player.getYRot());Vec3 facing=new Vec3(-Math.sin(yaw),0,Math.cos(yaw));
        Vec3 direction=facing.scale(Math.clamp(forward,-1,1)).add(new Vec3(facing.z,0,-facing.x).scale(Math.clamp(strafe,-1,1)));
        return direction.lengthSqr()<1e-8?Vec3.ZERO:direction.normalize().scale(speed);
    }
    public static void apply(Player player,String mode,double supportY,double strafe,double forward) {
        apply(player,mode,supportY,strafe,forward,false,false,false);
    }
    public static void apply(Player player,String mode,double supportY,double strafe,double forward,boolean ascend,boolean descend,boolean boost) {
        if(mode.equals("qingxiao_sword_flight")||mode.equals("iuno_phase_cruise")) {
            boolean sword=mode.equals("qingxiao_sword_flight");
            var direction=horizontal(player,strafe,forward,sword?(boost?.32:.22):(boost?.28:.18));
            // Retain the accepted upward impulse briefly; no automatic camera
            // rotation, altitude steering from pitch or noclip is introduced.
            double vertical=Math.clamp(player.getDeltaMovement().y*.82,0,.52);
            player.setDeltaMovement(direction.x,vertical,direction.z);player.fallDistance=0;return;
        }
        if(mode.equals("phoebe_staff_dive")){
            var motion=horizontal(player,strafe,1,.28);
            player.setDeltaMovement(motion.x,-.34,motion.z);player.fallDistance=0;return;
        }
        if(mode.equals("phoebe_staff_glide")||mode.equals("cantarella_surf")||mode.equals("cantarella_swim")){
            boolean swimming=mode.equals("cantarella_swim");
            var motion=horizontal(player,strafe,swimming?forward:1,swimming?.23:.34);
            double vertical=swimming?(ascend==descend?player.getDeltaMovement().y:ascend?.16:-.16):-0.035;
            player.setDeltaMovement(motion.x,vertical,motion.z);player.fallDistance=0;return;
        }
        if(mode.equals("combat_hover")||mode.equals("combat_leap")||mode.equals("lynae_leap")||mode.equals("cantarella_mirage")) {
            var previous=player.getDeltaMovement();
            var direction=horizontal(player,strafe,forward,.08);
            double vertical=mode.equals("lynae_leap")?Math.clamp(previous.y*.84,0,.68):mode.equals("combat_leap")?Math.clamp(previous.y*.8,0,.42):0;
            player.setDeltaMovement(direction.x,vertical,direction.z);player.fallDistance=0;return;
        }
        if(mode.equals("shorekeeper_butterfly")){
            var motion=horizontal(player,strafe,forward,.30);player.setDeltaMovement(motion.x,player.getDeltaMovement().y,motion.z);return;
        }
        if(mode.equals("qiuyuan_dash")) {
            var motion=horizontal(player,0,1,.36);double vertical=Math.clamp(player.getDeltaMovement().y*.8,0,.42);
            player.setDeltaMovement(motion.x,vertical,motion.z);player.fallDistance=0;return;
        }
        if(mode.equals("aemeath_flight")){
            var motion=horizontal(player,strafe,forward,boost?.58:.32);
            double vertical=ascend==descend?-Math.sin(Math.toRadians(player.getXRot()))*Math.clamp(forward,-1,1)*.24:ascend?.32:-.28;
            player.setDeltaMovement(motion.x,vertical,motion.z);player.fallDistance=0;return;
        }
        if(mode.equals("camellya_bloom")) {
            var direction=horizontal(player,strafe,forward,.07);
            player.setDeltaMovement(direction.x,player.isNoGravity()?0:player.getDeltaMovement().y,direction.z);
            player.setSprinting(false);return;
        }
        if(mode.equals("ciaccona_recital")) {
            player.setDeltaMovement(0,player.getDeltaMovement().y,0);player.setSprinting(false);return;
        }
        if(mode.startsWith("mornye_")||mode.equals("hsin_wander")) {
            var previous=player.getDeltaMovement();
            boolean exhausted=mode.startsWith("mornye_exhausted");
            var direction=exhausted?Vec3.ZERO:horizontal(player,strafe,forward,mode.equals("mornye_sprint")?OBSERVATION_SPRINT_SPEED:OBSERVATION_SPEED);
            if(direction.lengthSqr()<1e-8&&!exhausted)direction=previous.multiply(.8,0,.8);
            double vertical=mode.equals("mornye_descent")||mode.equals("mornye_exhausted_descent")?OBSERVATION_DESCENT:Math.max(0,previous.y*.85);
            player.setDeltaMovement(direction.x,vertical,direction.z);player.fallDistance=0;return;
        }
        if(mode.equals("chisa_glide")||mode.equals("chisa_glide_dash")) {
            double speed=mode.equals("chisa_glide_dash")?CHISA_DASH_SPEED:CHISA_GLIDE_SPEED;
            var direction=horizontal(player,strafe,forward>0?forward:1,speed);
            player.setDeltaMovement(direction.x,mode.equals("chisa_glide_dash")?.06:CHISA_GLIDE_DESCENT,direction.z);
            player.fallDistance=0;return;
        }
        double speed=mode.endsWith("_air")?AIR_WALK_SPEED:WATER_WALK_SPEED;
        var horizontal=horizontal(player,strafe,forward,speed);double vertical=Math.clamp(supportY-player.getY(),-.12,.12);
        player.setDeltaMovement(horizontal.x,vertical,horizontal.z);player.fallDistance=0;
        // Native movement continues through collision boxes; no teleport, block edit or noclip.
    }
}
