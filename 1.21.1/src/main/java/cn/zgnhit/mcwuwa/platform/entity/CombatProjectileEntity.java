package cn.zgnhit.mcwuwa.platform.entity;

import cn.zgnhit.mcwuwa.core.CharacterInstance;
import cn.zgnhit.mcwuwa.core.Element;
import cn.zgnhit.mcwuwa.platform.ModAttachments;
import cn.zgnhit.mcwuwa.platform.PlatformRuntime;
import cn.zgnhit.mcwuwa.platform.NativeAttackTargets;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** Real collision carrier. Only the server callback can settle its authored damage. */
public final class CombatProjectileEntity extends ThrowableItemProjectile {
    // Explicit Minecraft flight adaptation; these are not claimed original-game velocities.
    public static final double SPEED_BLOCKS_PER_TICK = 2;
    public static final int MAX_LIFETIME_TICKS = 80;
    private static final Set<CombatProjectileEntity> ACTIVE = Collections.newSetFromMap(new WeakHashMap<>());
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> VISUAL_MODE=
        net.minecraft.network.syncher.SynchedEntityData.defineId(CombatProjectileEntity.class,net.minecraft.network.syncher.EntityDataSerializers.INT);
    public int visualMode(){return entityData.get(VISUAL_MODE);}
    public void visualAction(String action){
        if(sourceRole!=null&&sourceRole.character()==cn.zgnhit.mcwuwa.core.CharacterId.GALBRENA)
            entityData.set(VISUAL_MODE,action.startsWith("galbrena/demon_")?10:8);
        if(sourceRole!=null&&sourceRole.character()==cn.zgnhit.mcwuwa.core.CharacterId.HSIN)
            entityData.set(VISUAL_MODE,action.contains("illuminated")||action.contains("palaces")?20:15);
    }
    @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder){
        super.defineSynchedData(builder);builder.define(VISUAL_MODE,0);
    }
    private ServerPlayer sourceBody;
    private CharacterInstance sourceRole;
    private Consumer<LivingEntity> impact;
    private double remainingRange;
    private boolean settled;
    private long createdAt;
    public long createdAt(){return createdAt;}
    private LivingEntity guidedTarget;
    private ConstructEntity visual;
    private Vec3 interceptCenter;
    private double interceptRadius;
    private Runnable intercept;

    /** Server-owned Phoebe ring interception. A wall or body in front of the
     * ring still takes priority over the skill volume. */
    public void interceptRing(Vec3 center,double radius,Runnable callback){
        if(level().isClientSide()||radius<=0||radius>8)throw new IllegalArgumentException("ring interception");
        interceptCenter=Objects.requireNonNull(center);interceptRadius=radius;intercept=Objects.requireNonNull(callback);
    }

    public CombatProjectileEntity(EntityType<? extends CombatProjectileEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public static CombatProjectileEntity spawn(ServerPlayer player, CharacterInstance role, Element element,
            Vec3 direction, double range, Consumer<LivingEntity> impact) {
        if (!(player.level() instanceof ServerLevel level) || !Double.isFinite(range) || range <= 0
                || range > 128 || direction.lengthSqr() < .001) throw new IllegalArgumentException("invalid projectile flight");
        var projectile = new CombatProjectileEntity(ModEntities.COMBAT_PROJECTILE.get(), level);
        projectile.sourceBody = player;
        projectile.createdAt=level.getGameTime();
        projectile.sourceRole = Objects.requireNonNull(role);
        projectile.entityData.set(VISUAL_MODE,switch(role.character()){
            case IUNO -> 1;
            case MORNYE -> 2;
            case PHOEBE -> 3;
            case CANTARELLA -> 4;
            case CARLOTTA -> 5;
            case QINGXIAO -> 6;
            case LYNAE -> 7;
            case GALBRENA -> 8;
            case CIACCONA -> 9;
            case ZHEZHI -> 11;
            case YINLIN -> 12;
            case DENIA -> 13;
            case PHROLOVA -> 14;
            case HSIN -> 15;
            case JINHSI -> 16;
            case HIYUKI -> 17;
            case SHOREKEEPER -> 18;
            case LUPA -> 19;
            default -> 0;
        });
        projectile.impact = Objects.requireNonNull(impact);
        projectile.remainingRange = range;
        projectile.setOwner(player);
        projectile.setItem(new ItemStack(switch (element) {
            case GLACIO -> Items.PRISMARINE_CRYSTALS;
            case FUSION -> Items.BLAZE_POWDER;
            case ELECTRO -> Items.GLOWSTONE_DUST;
            case AERO -> Items.PRISMARINE_SHARD;
            case SPECTRO -> Items.GHAST_TEAR;
            case HAVOC -> Items.AMETHYST_SHARD;
            case PHYSICAL -> Items.IRON_NUGGET;
        }));
        // Start at the actual eye, never beyond a nearby wall or inside a client-supplied target.
        projectile.setPos(player.getEyePosition());
        Vec3 velocity = direction.normalize().scale(SPEED_BLOCKS_PER_TICK);
        projectile.shoot(velocity.x, velocity.y, velocity.z, (float) SPEED_BLOCKS_PER_TICK, 0);
        if (!projectile.validSource() || !level.addFreshEntity(projectile)) return null;
        ACTIVE.add(projectile);
        return projectile;
    }

    @Override protected Item getDefaultItem() { return Items.AMETHYST_SHARD; }
    /** Server-selected homing target; no client entity id or impact position is accepted. */
    public static CombatProjectileEntity guidedButterfly(ServerPlayer player,CharacterInstance role,Vec3 origin,
            double range,Consumer<LivingEntity> impact) {
        return guidedButterfly(player,role,origin,range,"shorekeeper_star_butterfly",impact);
    }
    public static CombatProjectileEntity guidedButterfly(ServerPlayer player,CharacterInstance role,Vec3 origin,
            double range,String model,Consumer<LivingEntity> impact) {
        LivingEntity target=player.level().getEntitiesOfClass(LivingEntity.class,
            new AABB(origin,origin).inflate(range),e->PlatformRuntime.canAttack(player,e)&&player.hasLineOfSight(e))
            .stream().filter(e->e.position().distanceToSqr(origin)<=range*range)
            .min(Comparator.comparingDouble(e->e.position().distanceToSqr(origin))).orElse(null);
        Vec3 direction=target==null?player.getLookAngle():target.getBoundingBox().getCenter().subtract(origin);
        if(direction.lengthSqr()<.001)direction=player.getLookAngle();
        var projectile=spawn(player,role,Element.SPECTRO,direction,range,impact);
        if(projectile==null)return null;
        projectile.setPos(origin);projectile.guidedTarget=target;projectile.setInvisible(true);
        projectile.visual=ConstructEntity.spawn(player.serverLevel(),player,role.instanceId(),model,origin,MAX_LIFETIME_TICKS,.22f,false,"idle");
        projectile.visual.useProjectileClock(projectile.createdAt);
        return projectile;
    }
    @Override public boolean canUsePortal(boolean allowPassengers) { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double distance) {
        // Vanilla suppresses the first two near-camera frames; interpolation starts at the eye.
        return distance < 64 * 64 && super.shouldRenderAtSqrDistance(distance);
    }

    private boolean validSource() {
        if (sourceBody == null || sourceRole == null || sourceBody.isRemoved() || !sourceBody.isAlive()
                || sourceBody.level() != level() || !sourceRole.alive()) return false;
        // Body identity plus the original weapon instance prevent respawn/swap identity transfer.
        var team = ModAttachments.team(sourceBody).state();
        for (int slot = 0; slot < 3; slot++) if (team.at(slot).orElse(null) == sourceRole) return true;
        return false;
    }

    @Override protected boolean canHitEntity(Entity target) {
        var living=NativeAttackTargets.livingTarget(target);
        return super.canHitEntity(target) && (level().isClientSide()
            || validSource() && (NativeAttackTargets.accepts(sourceBody,target)
                || living!=null && PlatformRuntime.canAttack(sourceBody, living)));
    }

    @Override public void tick() {
        if (!level().isClientSide() && (!validSource() || settled || tickCount >= MAX_LIFETIME_TICKS || remainingRange <= .001)) {
            disposeVisual();discard(); ACTIVE.remove(this); return;
        }
        if(!level().isClientSide()&&guidedTarget!=null&&guidedTarget.isAlive()&&guidedTarget.level()==level()) {
            Vec3 toward=guidedTarget.getBoundingBox().getCenter().subtract(position());
            if(toward.lengthSqr()>.001)setDeltaMovement(toward.normalize().scale(SPEED_BLOCKS_PER_TICK));
        }
        Vec3 before = position();
        if (!level().isClientSide() && getDeltaMovement().length() > remainingRange)
            setDeltaMovement(getDeltaMovement().normalize().scale(remainingRange));
        if(!level().isClientSide()&&intercept!=null){
            Vec3 velocity=getDeltaMovement(),offset=before.subtract(interceptCenter);
            double a=velocity.lengthSqr(),b=2*offset.dot(velocity),c=offset.lengthSqr()-interceptRadius*interceptRadius;
            double discriminant=b*b-4*a*c;
            if(a>1e-9&&discriminant>=0){
                double t=(-b-Math.sqrt(discriminant))/(2*a);
                if(t>=0&&t<=1){
                    Vec3 point=before.add(velocity.scale(t));
                    var block=level().clip(new net.minecraft.world.level.ClipContext(before,point,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,this));
                    boolean body=level().getEntities(this,new AABB(before,point).inflate(1),this::canHitEntity).stream().anyMatch(e->e.getBoundingBox().inflate(.1).clip(before,point).isPresent());
                    if(block.getType()==HitResult.Type.MISS&&!body){
                        settled=true;setPos(point);try{intercept.run();}finally{intercept=null;disposeVisual();discard();ACTIVE.remove(this);}return;
                    }
                }
            }
        }
        super.tick(); // Vanilla sweeps the whole movement segment against blocks and living bodies.
        if (!level().isClientSide()) {
            if(visual!=null&&!visual.isRemoved()){visual.setPos(position());visual.setYRot((float)(Math.toDegrees(Math.atan2(getDeltaMovement().z,getDeltaMovement().x))-90));}
            remainingRange -= position().distanceTo(before);
            if (isRemoved() || remainingRange <= .001) { disposeVisual();discard(); ACTIVE.remove(this); }
        }
    }

    @Override protected void onHitEntity(EntityHitResult result) {
        if (!level().isClientSide() && !settled && validSource()
                && NativeAttackTargets.accepts(sourceBody,result.getEntity())) {
            settled=true;
            NativeAttackTargets.hit(sourceBody,result.getEntity());
            return;
        }
        var living=NativeAttackTargets.livingTarget(result.getEntity());
        if (!level().isClientSide() && !settled && validSource() && living!=null) {
            settled = true;
            if (PlatformRuntime.canAttack(sourceBody, living)) impact.accept(living);
        }
    }

    @Override protected void onHit(HitResult result) {
        // Elemental combat never invokes vanilla projectile block callbacks (buttons, griefing).
        if (result instanceof EntityHitResult entityHit) onHitEntity(entityHit);
        if (!level().isClientSide()) {
            settled = true;
            if(visual!=null){visual.setPos(result.getLocation());visual.finishAfter(6,"attack");visual=null;}
            discard(); ACTIVE.remove(this); impact = null;
        }
    }
    private void disposeVisual(){if(visual!=null){visual.discard();visual=null;}}

    @Override public void remove(RemovalReason reason) {
        if(!level().isClientSide()){
            ACTIVE.remove(this);disposeVisual();
            impact=null;intercept=null;guidedTarget=null;
        }
        super.remove(reason);
    }

    public static void removeOwned(ServerPlayer body) {
        removeOwned(body,null);
    }
    public static void removeOwned(ServerPlayer body,java.util.UUID weaponId) {
        for (var projectile : List.copyOf(ACTIVE)) if (projectile.sourceBody == body
                &&(weaponId==null||projectile.sourceRole.instanceId().equals(weaponId))) {
            projectile.disposeVisual();projectile.discard(); projectile.impact = null; ACTIVE.remove(projectile);
        }
    }
}
