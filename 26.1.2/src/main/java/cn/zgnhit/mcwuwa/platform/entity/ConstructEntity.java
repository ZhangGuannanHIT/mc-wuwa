package cn.zgnhit.mcwuwa.platform.entity;

import cn.zgnhit.mcwuwa.platform.ModAttachments;
import java.util.*;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/** Tracked visual only. Damage, targeting and summon mechanics stay in server executors. */
public final class ConstructEntity extends Entity implements ConstructVisual {
    private static final EntityDataAccessor<String> MODEL=SynchedEntityData.defineId(ConstructEntity.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> ANIMATION=SynchedEntityData.defineId(ConstructEntity.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> OWNER=SynchedEntityData.defineId(ConstructEntity.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> SCALE=SynchedEntityData.defineId(ConstructEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DURATION=SynchedEntityData.defineId(ConstructEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START=SynchedEntityData.defineId(ConstructEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> FOLLOW=SynchedEntityData.defineId(ConstructEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<org.joml.Vector3fc> OFFSET=SynchedEntityData.defineId(ConstructEntity.class,EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<org.joml.Vector3fc> RAY=SynchedEntityData.defineId(ConstructEntity.class,EntityDataSerializers.VECTOR3);
    private static final Set<ConstructEntity> ACTIVE=Collections.newSetFromMap(new WeakHashMap<>());
    private UUID ownerId, weaponInstanceId;
    private boolean followOwner;
    private long creationTick;
    private Vec3 landscapeOrigin;
    private long projectileCreationTick=-1;
    /** World-space vector from this fixed origin; never a damage or aiming authority. */
    public void visualRayTo(Vec3 endpoint){
        Vec3 delta=endpoint.subtract(position());
        entityData.set(RAY,new org.joml.Vector3f((float)delta.x,(float)delta.y,(float)delta.z));
    }
    public Vec3 visualRayVector(){var v=entityData.get(RAY);return new Vec3(v.x(),v.y(),v.z());}
    public void useProjectileClock(long createdAt){projectileCreationTick=createdAt;}

    public ConstructEntity(EntityType<? extends ConstructEntity> type,Level level) {
        super(type,level);noPhysics=true;setNoGravity(true);addTag(EnemyEvents.SKILL_SUMMON_TAG);
    }
    public static ConstructEntity spawn(ServerLevel level,ServerPlayer owner,UUID weaponInstanceId,String modelId,
            Vec3 position,int lifetimeTicks,float scale,boolean followOwner,String animation) {
        Objects.requireNonNull(owner);Objects.requireNonNull(weaponInstanceId);Objects.requireNonNull(position);
        if(owner.level()!=level || !modelId.matches("[a-z0-9_]+") || !(Set.of("idle","attack","hurt","breath","cleave","plunge","glide").contains(animation) || (modelId.equals("cartethyia_inherited_blade") && Set.of("human_right","divine_intent","outsider_power").contains(animation)) || cn.zgnhit.mcwuwa.core.PuppetAnimationRules.allowed(modelId,animation) || cn.zgnhit.mcwuwa.core.ActionPropAnimationRules.allowed(modelId,animation) || (modelId.equals("hecate")&&animation.equals("illusion")) || (modelId.equals("aemeath_mecha")&&Set.of("skill","wing_ensemble_descend").contains(animation)) || (modelId.equals("aemeath_assist")&&Set.of("cooperative","ensemble").contains(animation)) || (modelId.equals("jue_dragon")&&Set.of("liberation","overflow").contains(animation)))
            || lifetimeTicks<1 || lifetimeTicks>12000 || !Float.isFinite(scale) || scale<=0 || scale>16)
            throw new IllegalArgumentException("invalid construct visual");
        ConstructEntity entity=new ConstructEntity(ModEntities.CONSTRUCT.get(),level);
        entity.ownerId=owner.getUUID();entity.weaponInstanceId=weaponInstanceId;entity.followOwner=followOwner;
        var ownerOffset=position.subtract(owner.position()).yRot((float)Math.toRadians(owner.getYRot()));
        entity.entityData.set(FOLLOW,followOwner);entity.entityData.set(OFFSET,new org.joml.Vector3f((float)ownerOffset.x,(float)ownerOffset.y,(float)ownerOffset.z));
        entity.entityData.set(MODEL,modelId);entity.entityData.set(ANIMATION,animation);
        entity.entityData.set(OWNER,owner.getUUID().toString());entity.entityData.set(SCALE,scale);
        entity.entityData.set(DURATION,lifetimeTicks);entity.entityData.set(START,level.getGameTime());
        entity.creationTick=level.getGameTime();
        entity.setPos(position);entity.setYRot(owner.getYRot());
        if(!level.addFreshEntity(entity))throw new IllegalStateException("construct could not join level");
        ACTIVE.add(entity);return entity;
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(MODEL,"");builder.define(ANIMATION,"idle");builder.define(OWNER,"");
        builder.define(SCALE,1f);builder.define(DURATION,1);builder.define(START,0L);builder.define(FOLLOW,false);builder.define(OFFSET,new org.joml.Vector3f());builder.define(RAY,new org.joml.Vector3f());
    }
    public String modelId(){return entityData.get(MODEL);}
    public String animation(){return entityData.get(ANIMATION);}
    public String ownerUuid(){return entityData.get(OWNER);}
    public float visualScale(){return entityData.get(SCALE);}
    public int durationTicks(){return entityData.get(DURATION);}
    public long startTick(){return entityData.get(START);}
    public boolean followsOwner(){return entityData.get(FOLLOW);}
    /** Stored in the caster yaw frame; render and server use the same rotation. */
    public Vec3 visualOwnerOffset(float ownerYaw){var v=entityData.get(OFFSET);return new Vec3(v.x(),v.y(),v.z()).yRot((float)Math.toRadians(-ownerYaw));}
    /** An independently finishing skill stays at its departure position. */
    public void detachFromOwner(){
        if(followOwner&&level() instanceof ServerLevel level&&ownerId!=null){
            var owner=level.getServer().getPlayerList().getPlayer(ownerId);
            if(owner!=null&&owner.level()==level){setPos(owner.position().add(visualOwnerOffset(owner.getYRot())));setYRot(owner.getYRot());}
        }
        followOwner=false;entityData.set(FOLLOW,false);
    }
    /** The 0.1-block collision proxy must not shrink a full-size visual to a 6.4-block render range. */
    @Override public boolean shouldRenderAtSqrDistance(double distance){return distance<96d*96d;}
    /** End a projectile visual on its contact point without losing same-tick spawn packets. */
    public void finishAfter(int ticks,String animation){
        if(!(level() instanceof ServerLevel)||ticks<1||ticks>12000||!Set.of("idle","attack","breath","cleave").contains(animation))throw new IllegalArgumentException("invalid visual finish");
        entityData.set(START,level().getGameTime());entityData.set(DURATION,ticks);entityData.set(ANIMATION,animation);
    }
    @Override public void tick() {
        super.tick();
        if(!(level() instanceof ServerLevel level))return;
        ServerPlayer owner=ownerId==null?null:level.getServer().getPlayerList().getPlayer(ownerId);
        if(owner!=null&&cn.zgnhit.mcwuwa.platform.CinematicTimeRuntime.paused(owner)
            &&(projectileCreationTick>=0?cn.zgnhit.mcwuwa.platform.CinematicTimeRuntime.projectilePredates(owner,projectileCreationTick)
                :!cn.zgnhit.mcwuwa.platform.CinematicTimeRuntime.sceneVisual(owner,modelId(),creationTick))){
            entityData.set(START,startTick()+1);return;
        }
        boolean sourcePresent=false;
        if(owner!=null && owner.level()==level && owner.isAlive())
            for(int slot=0;slot<3;slot++)if(ModAttachments.team(owner).state().at(slot).map(role->role.instanceId().equals(weaponInstanceId)&&role.alive()).orElse(false))sourcePresent=true;
        if(!sourcePresent || level.getGameTime()>=startTick()+durationTicks()){discard();ACTIVE.remove(this);return;}
        if(modelId().equals("suisui_reserve_dancer")){
            if(landscapeOrigin==null)landscapeOrigin=position();
            double age=level.getGameTime()-startTick();
            double rise=Math.min(1,age/22.0);
            rise=rise*rise*(3-2*rise);
            double angle=age*.035;
            double radius=6.4*rise;
            setPos(landscapeOrigin.add(radius*Math.sin(angle),
                rise*(4.4+.38*Math.sin(angle*2)),radius*Math.cos(angle)));
            setYRot((float)Math.toDegrees(-angle)-90);
        }
        if(followOwner){setPos(owner.position().add(visualOwnerOffset(owner.getYRot())));setYRot(owner.getYRot());}
    }
    @Override public void remove(RemovalReason reason) {
        // Fields and action props also discard directly, outside this entity's
        // expiry tick. Remove those entries immediately rather than waiting for GC.
        if(!level().isClientSide())ACTIVE.remove(this);
        super.remove(reason);
    }
    public static void removeOwned(UUID playerId,UUID weaponId) {
        HecateEntity.removeOwned(playerId,weaponId);
        for(ConstructEntity entity:List.copyOf(ACTIVE))if(playerId.equals(entity.ownerId) && (weaponId==null||weaponId.equals(entity.weaponInstanceId))) {
            entity.discard();ACTIVE.remove(entity);
        }
    }
    @Override public boolean hurtServer(ServerLevel level,DamageSource source,float amount){return false;}
    @Override public boolean isPickable(){return false;}
    @Override public boolean isPushable(){return false;}
    @Override public boolean canBeCollidedWith(Entity other){return false;}
    @Override public boolean shouldBeSaved(){return false;}
    @Override protected void readAdditionalSaveData(ValueInput input) { }
    @Override protected void addAdditionalSaveData(ValueOutput output) { }
}
