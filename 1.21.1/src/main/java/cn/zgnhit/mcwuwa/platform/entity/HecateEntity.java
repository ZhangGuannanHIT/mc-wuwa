package cn.zgnhit.mcwuwa.platform.entity;

import cn.zgnhit.mcwuwa.core.CharacterId;
import cn.zgnhit.mcwuwa.core.StaminaDodge;
import cn.zgnhit.mcwuwa.platform.ModAttachments;
import cn.zgnhit.mcwuwa.platform.PlatformRuntime;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.nbt.CompoundTag;

import net.minecraft.world.phys.Vec3;

/** Conducting Hecate is an attackable relay, never an additional health pool or loot source. */
public final class HecateEntity extends LivingEntity implements ConstructVisual {
    private static final EntityDataAccessor<Long> START=SynchedEntityData.defineId(HecateEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DURATION=SynchedEntityData.defineId(HecateEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> ATTACK_START=SynchedEntityData.defineId(HecateEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> ATTACK_DURATION=SynchedEntityData.defineId(HecateEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> ATTACK=SynchedEntityData.defineId(HecateEntity.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> ATTACK_ACTION=SynchedEntityData.defineId(HecateEntity.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> OWNER=SynchedEntityData.defineId(HecateEntity.class,EntityDataSerializers.STRING);
    private static final Set<HecateEntity> ACTIVE=Collections.newSetFromMap(new WeakHashMap<>());
    private WeakReference<ServerPlayer> owner=new WeakReference<>(null);
    private UUID ownerId,weaponId;
    private final Set<Mob> redirected=Collections.newSetFromMap(new WeakHashMap<>());
    private StaminaDodge dodge;
    private Vec3 dodgeDirection=Vec3.ZERO;
    private Vec3 attackDirection=Vec3.ZERO;
    private int attackDashRemaining;
    private double attackDashDistance,longestAttackDashDistance;
    public double longestAttackDashDistance(){return longestAttackDashDistance;}
    public double attackDashDistance(){return attackDashDistance;}
    public HecateEntity(EntityType<? extends HecateEntity> type,Level level) {
        super(type,level);setNoGravity(true);addTag(EnemyEvents.SKILL_SUMMON_TAG);
    }
    public static HecateEntity spawn(ServerPlayer player,UUID weapon,Vec3 position,int duration) {
        if(duration<1||duration>12000)throw new IllegalArgumentException("hecate lifetime");
        var entity=new HecateEntity(ModEntities.HECATE.get(),player.level());
        entity.owner=new WeakReference<>(player);entity.ownerId=player.getUUID();entity.weaponId=weapon;
        entity.entityData.set(OWNER,player.getUUID().toString());
        entity.dodge=new StaminaDodge(ModAttachments.team(player).state().onlineTick());
        entity.entityData.set(START,player.level().getGameTime());entity.entityData.set(DURATION,duration);
        entity.setPos(position);entity.setYRot(player.getYRot());
        if(!player.level().addFreshEntity(entity))throw new IllegalStateException("Hecate could not join level");
        ACTIVE.add(entity);entity.updateHostileTargets();return entity;
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);builder.define(START,0L);builder.define(DURATION,1);
        builder.define(ATTACK_START,0L);builder.define(ATTACK_DURATION,1);builder.define(ATTACK,"");
        builder.define(ATTACK_ACTION,"");
        builder.define(OWNER,"");
    }
    /** Presentation follows the accepted attack timeline, including reserve-owner attacks. */
    public void animateAttack(String action,int duration) {
        if(level().isClientSide()||!action.startsWith("phrolova/hecate_"))return;
        entityData.set(ATTACK_START,level().getGameTime());entityData.set(ATTACK_DURATION,Math.max(1,duration));
        entityData.set(ATTACK,switch(action){
            case "phrolova/hecate_basic_01"->"dash_01";
            case "phrolova/hecate_basic_02"->"dash_02";
            case "phrolova/hecate_strings"->"strings";
            case "phrolova/hecate_winds"->"winds";
            case "phrolova/hecate_color"->"color";
            case "phrolova/hecate_brass"->"brass";
            case "phrolova/hecate_fortissimo"->"fortissimo";
            case "phrolova/hecate_finale"->"finale";
            default->action.contains("basic")?"dash":"attack";
        });
        entityData.set(ATTACK_ACTION,action);
        attackDashRemaining=10;attackDashDistance=0;
        attackDirection=Vec3.directionFromRotation(0,getYRot());
        var player=ownerPlayer();if(player!=null) {
            var target=level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(12),
                candidate->PlatformRuntime.canAttack(player,candidate)&&hasLineOfSight(candidate)).stream()
                .min(Comparator.comparingDouble(this::distanceToSqr));
            target.ifPresent(candidate->{
                var direction=candidate.position().subtract(position()).multiply(1,0,1);
                // A summon may already overlap the selected target. Retain its
                // facing then; a zero horizontal vector must not erase the dash.
                if(direction.lengthSqr()>1e-8){direction=direction.normalize();attackDirection=direction;setYRot((float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));}
            });
        }
    }
    public ServerPlayer ownerPlayer() {
        ServerPlayer player=owner.get();
        if(player==null&&level() instanceof ServerLevel server&&ownerId!=null)player=server.getServer().getPlayerList().getPlayer(ownerId);
        if(player==null||!player.isAlive()||player.level()!=level())return null;
        for(int slot=0;slot<3;slot++)if(ModAttachments.team(player).state().at(slot)
            .filter(role->role.alive()&&role.character()==CharacterId.PHROLOVA&&role.instanceId().equals(weaponId)).isPresent())return player;
        return null;
    }
    public String ownerUuid(){return entityData.get(OWNER);}
    public boolean foreground() {
        var player=ownerPlayer();return player!=null&&ModAttachments.team(player).state().wuwaMode()
            &&ModAttachments.team(player).state().active().filter(role->role.instanceId().equals(weaponId)).isPresent()
            &&!isRemoved()&&level().getGameTime()<startTick()+durationTicks();
    }
    public boolean commandDodge(ServerPlayer player,Vec3 direction) {
        if(player!=ownerPlayer()||!foreground())return false;
        advanceDodge();dodge.releaseSprint();boolean accepted=dodge.pressSprint();
        if(accepted)dodgeDirection=direction.lengthSqr()>1e-8?direction.normalize():Vec3.ZERO;
        return accepted;
    }
    /** One shared gate for native hits and vanilla/projectile relay; owner dodge is evaluated afterwards. */
    public boolean avoids(DamageSource source,float amount) {
        return avoids(source,amount,false);
    }
    public boolean avoids(DamageSource source,float amount,boolean periodic) {
        if(!(amount>0)||dodge==null)return false;
        advanceDodge();var incoming=!periodic&&PlatformRuntime.evadableDirect(source)
            ?StaminaDodge.Incoming.EVADABLE_DIRECT:StaminaDodge.Incoming.ENVIRONMENT;
        return dodge.incoming(incoming)!=StaminaDodge.AttackResult.NOT_PREVENTED;
    }
    private void advanceDodge(){
        var player=ownerPlayer();
        // Independent stamina, shared paused gameplay clock. World time would
        // catch up recovery and consume dodge windows after a liberation shot.
        if(dodge!=null&&player!=null)dodge.advanceTo(ModAttachments.team(player).state().onlineTick(),false,false,true);
    }
    @Override public void tick() {
        super.tick();if(!(level() instanceof ServerLevel))return;
        if(ownerPlayer()!=null&&cn.zgnhit.mcwuwa.platform.CinematicTimeRuntime.paused(ownerPlayer())){
            entityData.set(START,startTick()+1);entityData.set(ATTACK_START,attackStartTick()+1);setDeltaMovement(Vec3.ZERO);return;
        }
        if(ownerPlayer()==null||level().getGameTime()>=startTick()+durationTicks()){discard();ACTIVE.remove(this);return;}
        if(tickCount%10==0)updateHostileTargets();
        advanceDodge();double distance=dodge.requestedDashDistanceThisTick();
        if(foreground()&&distance>0)move(MoverType.SELF,dodgeDirection.scale(distance));
        if(attackDashRemaining>0){
            Vec3 before=position();move(MoverType.SELF,attackDirection.scale(.52));
            attackDashDistance+=position().subtract(before).horizontalDistance();
            longestAttackDashDistance=Math.max(longestAttackDashDistance,attackDashDistance);attackDashRemaining--;
        }
        // A reserve command is a ground attack, even when its conductor is aloft.
        move(MoverType.SELF,new Vec3(0,-.35,0));
        setDeltaMovement(Vec3.ZERO);
    }
    /** Native hostile goals still acquire the real player. Redirect only that existing aggro;
     * neutral mobs and unrelated players never acquire a new target from this relay. */
    private void updateHostileTargets() {
        var player=ownerPlayer();boolean deployed=foreground();
        if(deployed&&player!=null)for(var mob:level().getEntitiesOfClass(Mob.class,getBoundingBox().inflate(32),mob->mob.getTarget()==player)) {
            redirected.add(mob);mob.setTarget(this);
        }
        for(var mob:List.copyOf(redirected)) {
            if(mob.isRemoved()||!mob.isAlive()||mob.level()!=level()||mob.getTarget()!=this){redirected.remove(mob);continue;}
            if(!deployed){mob.setTarget(player!=null&&player.isAlive()?player:null);redirected.remove(mob);}
        }
    }
    public static Optional<HecateEntity> activeRelay(ServerPlayer player) {
        return ACTIVE.stream().filter(entity->entity.ownerPlayer()==player&&entity.foreground()).findFirst();
    }
    public static void teamChanged(ServerPlayer player){for(var entity:List.copyOf(ACTIVE))if(player.getUUID().equals(entity.ownerId))entity.updateHostileTargets();}
    public void rememberTarget(Mob mob){redirected.add(mob);}
    @Override public void remove(RemovalReason reason) {
        if(!level().isClientSide()){
            var player=ownerPlayer();
            ACTIVE.remove(this);
            for(var mob:List.copyOf(redirected))if(!mob.isRemoved()&&mob.getTarget()==this)mob.setTarget(player!=null&&player.isAlive()?player:null);
            redirected.clear();
        }
        super.remove(reason);
    }
    @Override public boolean hurt(DamageSource source,float amount) {
        var player=ownerPlayer();if(player==null||!foreground()||!Float.isFinite(amount)||amount<=0)return false;
        if(source.getEntity() instanceof ServerPlayer attacker&&!PlatformRuntime.canAttack(attacker,this))return false;
        if(avoids(source,amount))return false;
        return player.hurt(source,amount);
    }
    public static Optional<HecateEntity> owned(ServerPlayer player,UUID weapon) {
        return ACTIVE.stream().filter(entity->!entity.isRemoved()&&entity.ownerPlayer()==player&&weapon.equals(entity.weaponId)).findFirst();
    }
    public static void removeOwned(UUID player,UUID weapon) {
        for(var entity:List.copyOf(ACTIVE))if(player.equals(entity.ownerId)&&(weapon==null||weapon.equals(entity.weaponId))){entity.discard();ACTIVE.remove(entity);}
    }
    @Override public boolean isPickable(){return level().isClientSide()?super.isPickable():foreground();}
    @Override public boolean canBeHitByProjectile(){return isPickable();}
    @Override public boolean isPushable(){return false;}
    @Override public boolean canBeCollidedWith(){return false;}
    @Override public boolean shouldBeSaved(){return false;}
    @Override public Iterable<net.minecraft.world.item.ItemStack> getArmorSlots(){return java.util.List.of();}
    @Override public net.minecraft.world.item.ItemStack getItemBySlot(net.minecraft.world.entity.EquipmentSlot slot){return net.minecraft.world.item.ItemStack.EMPTY;}
    @Override public void setItemSlot(net.minecraft.world.entity.EquipmentSlot slot,net.minecraft.world.item.ItemStack stack){}
    @Override public HumanoidArm getMainArm(){return HumanoidArm.RIGHT;}
    @Override public void readAdditionalSaveData(CompoundTag input){ }
    @Override public void addAdditionalSaveData(CompoundTag output){ }
    @Override public String modelId(){return "hecate";}
    @Override public String animation(){return level().getGameTime()<entityData.get(ATTACK_START)+entityData.get(ATTACK_DURATION)?entityData.get(ATTACK):"idle";}
    public long attackStartTick(){return entityData.get(ATTACK_START);}
    public int attackDurationTicks(){return entityData.get(ATTACK_DURATION);}
    public String attackAction(){return animation().equals("idle")?"":entityData.get(ATTACK_ACTION);}
    @Override public float animationProgress(double elapsed){
        return animation().equals("idle")?ConstructVisual.super.animationProgress(elapsed):
            (float)Math.clamp((elapsed+startTick()-entityData.get(ATTACK_START))/Math.max(1,entityData.get(ATTACK_DURATION)),0,1);
    }
    // Native mesh is about4.2 blocks high; align gameplay appearance with the2.8-block relay body.
    @Override public float visualScale(){return .66f;}
    @Override public int durationTicks(){return entityData.get(DURATION);}
    @Override public long startTick(){return entityData.get(START);}
}
