package cn.zgnhit.mcwuwa.platform;

import cn.zgnhit.mcwuwa.core.CinematicTimeRules;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/** Server-owned bounded shot clock; the world/render clock continues the shot. */
public final class CinematicTimeRuntime {
    private record Shot(String action,long start,long end,Map<Integer,net.minecraft.world.phys.Vec3> hazards){}
    private static final Map<ServerPlayer,Shot> SHOTS=new WeakHashMap<>();
    private record HeldAir(boolean noGravity,double height){}
    private static final Map<ServerPlayer,HeldAir> AIR=new WeakHashMap<>();
    private static final Map<Entity,Long> PAUSED_STEPS=new WeakHashMap<>();
    private CinematicTimeRuntime(){}
    public static void visual(ServerPlayer player,Network.Visual visual){
        if(visual.entityId()!=player.getId()||visual.actionId().equals("@form"))return;
        if(CinematicTimeRules.cinematic(visual.actionId())){
            var shot=new Shot(visual.actionId(),visual.startTick(),visual.startTick()+visual.durationTicks(),new LinkedHashMap<Integer,net.minecraft.world.phys.Vec3>());SHOTS.put(player,shot);
            for(var entity:player.level().getEntities(player,player.getBoundingBox().inflate(32)))
                if(affects(player,entity)&&shot.hazards.size()<1024)shot.hazards.put(entity.getId(),entity.position());
            var snapshot=hazardSnapshot(player);if(snapshot!=null)Network.hazardPause(player,snapshot);
        }
        // A real replacement visual also ends a visual-only opening (such as
        // transformation). Otherwise an interrupted shot keeps freezing clocks.
        else if(SHOTS.remove(player)!=null)sendHazardClear(player);
    }
    public static boolean paused(ServerPlayer player){
        Shot shot=SHOTS.get(player);if(shot==null)return false;
        if(!player.isAlive()||!ModAttachments.team(player).state().wuwaMode()||player.level().getGameTime()>=shot.end()){
            SHOTS.remove(player);if(player.level().getGameTime()<shot.end())sendHazardClear(player);return false;
        }
        return player.level().getGameTime()>=shot.start();
    }
    public static boolean pausesEnemy(Entity entity){
        if(!(entity.level() instanceof net.minecraft.server.level.ServerLevel level))return false;
        boolean stopped=false;
        for(ServerPlayer player:level.players())if(pausesEnemyFor(player,entity))stopped=true;
        return stopped;
    }
    static boolean pausesEnemyFor(ServerPlayer player,Entity entity){
        if(affects(player,entity))return true;
        var shot=SHOTS.get(player);
        // Skill displacement can carry an enemy out of the bounded shot.
        // Release its client anchor when the server resumes its simulation.
        if(shot!=null&&shot.hazards.remove(entity.getId())!=null)
            Network.hazardPause(player,hazardSnapshot(player));
        return false;
    }
    private static boolean affects(ServerPlayer player,Entity entity){
        if(entity instanceof cn.zgnhit.mcwuwa.platform.entity.CombatProjectileEntity projectile
            &&projectile.getOwner()==player&&paused(player)){
            var shot=SHOTS.get(player);
            return shot!=null&&projectilePredates(player,projectile.createdAt());
        }
        Mob source=entity instanceof Mob mob?mob:entity instanceof net.minecraft.world.entity.TraceableEntity traceable&&traceable.getOwner() instanceof Mob mob?mob:null;
        return source!=null&&paused(player)&&player.distanceToSqr(entity)<=1024&&PlatformRuntime.canAttack(player,source);
    }
    public static boolean projectilePredates(ServerPlayer player,long createdAt){
        var shot=SHOTS.get(player);return shot!=null&&createdAt<shot.start();
    }
    public static void hazardStopped(Entity entity){
        if(!(entity.level() instanceof net.minecraft.server.level.ServerLevel level))return;
        for(var player:level.players())trackStoppedFor(player,entity);
    }
    static void trackStoppedFor(ServerPlayer player,Entity entity){
        if(affects(player,entity)){
            var shot=SHOTS.get(player);
            if(shot==null)return;
            var previous=shot.hazards.get(entity.getId());
            // A skill may explicitly pull/teleport a stopped target. Only that
            // authoritative reposition replaces its visual anchor; idle enemies
            // do not resend an entire snapshot each tick.
            if(previous==null&&shot.hazards.size()<1024||previous!=null&&previous.distanceToSqr(entity.position())>1e-8){
                shot.hazards.put(entity.getId(),entity.position());Network.hazardPause(player,hazardSnapshot(player));
            }
        }
    }
    public static Network.HazardPause hazardSnapshot(ServerPlayer player){
        var shot=SHOTS.get(player);if(shot==null)return null;
        var anchors=new ArrayList<Network.HazardAnchor>();
        for(int id:shot.hazards.keySet()){var entity=player.level().getEntity(id);if(entity!=null&&!entity.isRemoved())anchors.add(new Network.HazardAnchor(id,entity.getX(),entity.getY(),entity.getZ()));}
        return new Network.HazardPause(player.getUUID(),shot.start,shot.end,anchors);
    }
    private static void sendHazardClear(ServerPlayer player){long now=player.level().getGameTime();Network.hazardPause(player,new Network.HazardPause(player.getUUID(),now,now,List.of()));}
    public static boolean sceneVisual(ServerPlayer player,String model,long started){
        Shot shot=SHOTS.get(player);if(shot==null||started<shot.start())return false;
        String action=shot.action().replace("mc_wuwa:","");
        return model.equals("skill_light_"+action.replace("/","__"))
            ||SkillModelProfiles.cinematicModel(action,model)
            ||action.startsWith("jinhsi/")&&model.equals("jue_dragon")
            ||action.startsWith("aemeath/")&&(model.equals("aemeath_mecha")||model.equals("aemeath_assist"));
    }
    public static void enemyStepCancelled(Entity entity){PAUSED_STEPS.merge(entity,1L,Long::sum);}
    public static long pausedEnemySteps(Entity entity){return PAUSED_STEPS.getOrDefault(entity,0L);}
    public static void holdAir(ServerPlayer player,boolean paused){
        if(paused&&!player.onGround()&&!player.isPassenger()&&!player.isInWater()){
            HeldAir held=AIR.computeIfAbsent(player,p->new HeldAir(p.isNoGravity(),p.getY()));
            player.setNoGravity(true);player.setPos(player.getX(),held.height(),player.getZ());
            player.setDeltaMovement(player.getDeltaMovement().multiply(1,0,1));player.resetFallDistance();player.syncVelocity=true;
        }else{
            HeldAir held=AIR.remove(player);if(held!=null)player.setNoGravity(held.noGravity());
        }
    }
    public static void clear(ServerPlayer player){if(SHOTS.remove(player)!=null)sendHazardClear(player);holdAir(player,false);}
}
