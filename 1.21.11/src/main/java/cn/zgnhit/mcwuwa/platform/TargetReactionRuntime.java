package cn.zgnhit.mcwuwa.platform;

import cn.zgnhit.mcwuwa.content.ActionDamageMapping;
import cn.zgnhit.mcwuwa.core.CharacterInstance;
import cn.zgnhit.mcwuwa.content.mechanics.MechanicsState;
import cn.zgnhit.mcwuwa.content.mechanics.MechanicModifierRules;
import java.util.*;
import java.util.function.BiConsumer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Source-specific target events. Range/delay/lock lifetime are explicit MC adaptations. */
public final class TargetReactionRuntime {
    public record HavocSnapshot(String dimension,Vec3 center,int layers) { }
    public record WordLock(UUID owner,String dimension,Vec3 center,long expiresAt) { }
    public record AeroSnapshot(String dimension,Vec3 center,int layers) { }
    private record Copy(UUID owner,HavocSnapshot snapshot,long due) { }
    private record Lift(UUID owner,LivingEntity target,String dimension,long due) { }
    @FunctionalInterface public interface MarkSink { void apply(CharacterInstance owner,LivingEntity target,int additionalLayers); }
    private static final Set<String> COPY_ACTIONS=Set.of("yangyang_xuanling/basic_04","yangyang_xuanling/feather_basic_04",
        "yangyang_xuanling/heavy","yangyang_xuanling/feather_heavy");
    private static final Set<String> COLLAPSE_ACTIONS=Set.of("hsin/illuminated_basic_02","hsin/illuminated_heavy",
        "hsin/illuminated_dodge_counter","hsin/illuminated_skill");
    private final List<Copy> copies=new ArrayList<>();
    private final List<Lift> lifts=new ArrayList<>();
    private final Map<UUID,WordLock> locks=new HashMap<>();
    public void pauseOneTick(){
        copies.replaceAll(c->new Copy(c.owner,c.snapshot,c.due+1));lifts.replaceAll(l->new Lift(l.owner,l.target,l.dimension,l.due+1));
        locks.replaceAll((id,l)->new WordLock(l.owner,l.dimension,l.center,l.expiresAt+1));
    }
    public static boolean copiesHavoc(String action) { return COPY_ACTIONS.contains(action); }
    public static boolean copiesAeroBefore(MechanicsState state,String action) {
        return MechanicModifierRules.cartethyiaCopiesAeroBefore(state,action);
    }
    public static boolean copiesAeroAfter(MechanicsState state,String action) {
        return MechanicModifierRules.cartethyiaCopiesAeroAfter(state,action);
    }
    public AeroSnapshot captureAero(ServerPlayer player) {
        Vec3 center=player.position();int layers=nearby(player,center,6).stream().mapToInt(e->EffectRuntime.layers(e,"aero_erosion")).max().orElse(0);
        return new AeroSnapshot(dimension(player),center,layers);
    }
    public void copyAero(ServerPlayer player,CharacterInstance owner,AeroSnapshot snapshot,MarkSink sink) {
        if(snapshot==null||snapshot.layers()<=0||!snapshot.dimension().equals(dimension(player))||owner(player,owner.instanceId()).isEmpty())return;
        for(LivingEntity target:nearby(player,snapshot.center(),6)) {
            int missing=snapshot.layers()-EffectRuntime.layers(target,"aero_erosion");if(missing>0)sink.apply(owner,target,missing);
        }
    }
    public HavocSnapshot beforeHit(ServerPlayer player,String action) {
        if(!copiesHavoc(action))return null;
        Vec3 center=player.position();int highest=nearby(player,center,6).stream().mapToInt(e->EffectRuntime.layers(e,"havoc_bane")).max().orElse(0);
        return new HavocSnapshot(dimension(player),center,highest);
    }
    public void confirmedCopy(CharacterInstance owner,HavocSnapshot snapshot,long now) {
        if(snapshot!=null&&snapshot.layers()>0)copies.add(new Copy(owner.instanceId(),snapshot,Math.addExact(now,2)));
    }
    /** Small Cart E's lift then return-to-ground; velocities and eight-tick delay are MC tuning. */
    public void confirmedLift(ServerPlayer player,CharacterInstance role,LivingEntity target){
        if(target instanceof cn.zgnhit.mcwuwa.platform.entity.HecateEntity
            ||cn.zgnhit.mcwuwa.platform.entity.EnemyEvents.profile(target).hardControlImmune())return;
        lifts.removeIf(lift->lift.target()==target);
        target.setDeltaMovement(target.getDeltaMovement().x,.42,target.getDeltaMovement().z);target.hurtMarked=true;
        lifts.add(new Lift(role.instanceId(),target,dimension(player),PlatformRuntime.time(player)+8));
    }
    public void actionStarted(ServerPlayer player,CharacterInstance owner,ActionDamageMapping action,BiConsumer<CharacterInstance,WordLock> collapse) {
        long now=PlatformRuntime.time(player);String id=action.id();
        if(id.equals("hsin/illuminated_basic_01")) {
            Vec3 eye=player.getEyePosition(),aim=player.getLookAngle();
            var target=nearby(player,player.position(),action.geometry().rangeBlocks()).stream().filter(e->{
                Vec3 relative=e.getBoundingBox().getCenter().subtract(eye);double forward=relative.dot(aim);
                return forward>=0&&relative.subtract(aim.scale(forward)).length()<=action.geometry().widthBlocks()+e.getBbWidth()*.5;
            }).min(Comparator.comparingDouble(player::distanceToSqr));
            target.ifPresent(e->locks.put(owner.instanceId(),new WordLock(owner.instanceId(),dimension(player),e.position().add(0,.8,0),now+100)));
        } else if(COLLAPSE_ACTIONS.contains(id)) {
            WordLock lock=locks.remove(owner.instanceId());
            if(lock!=null&&lock.expiresAt()>now&&lock.dimension().equals(dimension(player)))collapse.accept(owner,lock);
        }
    }
    public void tick(ServerPlayer player,MarkSink sink) {
        long now=PlatformRuntime.time(player);
        if(!player.isAlive()){clear();return;}
        for(var lift:List.copyOf(lifts)){
            var target=lift.target();
            if(!target.isAlive()||target.isRemoved()||!lift.dimension().equals(dimension(player))
                ||target.level()!=player.level()||owner(player,lift.owner()).isEmpty()) {lifts.remove(lift);continue;}
            if(now<lift.due())continue;
            lifts.remove(lift);
            if(PlatformRuntime.canAttack(player,target)&&!cn.zgnhit.mcwuwa.platform.entity.EnemyEvents.profile(target).hardControlImmune()){
                target.setDeltaMovement(target.getDeltaMovement().x,-.65,target.getDeltaMovement().z);target.hurtMarked=true;
            }
        }
        locks.values().removeIf(lock->lock.expiresAt()<=now||!lock.dimension().equals(dimension(player))||owner(player,lock.owner()).isEmpty());
        if(now%5==0)for(WordLock lock:locks.values())for(int corner=0;corner<4;corner++) {
            double angle=corner*Math.PI/2;
            player.level().sendParticles(ParticleTypes.END_ROD,lock.center().x+Math.cos(angle)*.35,lock.center().y,
                lock.center().z+Math.sin(angle)*.35,1,0,0,0,0);
        }
        for(Copy copy:List.copyOf(copies)) {
            var role=owner(player,copy.owner());
            if(role.isEmpty()||!copy.snapshot().dimension().equals(dimension(player))){copies.remove(copy);continue;}
            if(now<copy.due())continue;
            copies.remove(copy);
            for(LivingEntity target:nearby(player,copy.snapshot().center(),6)) {
                int missing=copy.snapshot().layers()-EffectRuntime.layers(target,"havoc_bane");
                if(missing>0)sink.apply(role.get(),target,missing);
            }
        }
    }
    /** Defeat removes accepted but not yet delivered reactions synchronously.
     * Waiting for the next alive check lets same-tick revival revive old locks. */
    public void removeOwner(UUID owner) {
        copies.removeIf(copy->copy.owner().equals(owner));
        lifts.removeIf(lift->lift.owner().equals(owner));
        locks.remove(owner);
    }
    public int wordLockCount() { return locks.size(); }
    public void clear() { copies.clear();locks.clear();lifts.clear(); }
    private static List<LivingEntity> nearby(ServerPlayer player,Vec3 center,double radius) {
        return player.level().getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(radius),
            e->PlatformRuntime.canAttack(player,e)&&e.position().distanceToSqr(center)<=radius*radius&&player.hasLineOfSight(e));
    }
    private static Optional<CharacterInstance> owner(ServerPlayer player,UUID id) {
        var team=ModAttachments.team(player).state();
        for(int slot=0;slot<3;slot++)if(team.at(slot).filter(role->role.instanceId().equals(id)&&role.alive()).isPresent())return team.at(slot);
        return Optional.empty();
    }
    private static String dimension(ServerPlayer player){return player.level().dimension().identifier().toString();}
}
