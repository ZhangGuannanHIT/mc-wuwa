package cn.zgnhit.mcwuwa.platform.entity;

import cn.zgnhit.mcwuwa.platform.PlatformRuntime;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Zombie navigation/targeting with a server-owned delayed melee strike and explicit parry window. */
public final class AlephEntity extends Zombie {
    private static final EntityDataAccessor<Integer> WINDUP = SynchedEntityData.defineId(AlephEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STAGGER = SynchedEntityData.defineId(AlephEntity.class, EntityDataSerializers.INT);
    private LivingEntity pendingTarget;
    public AlephEntity(EntityType<? extends Zombie> type, Level level) { super(type, level); setCanPickUpLoot(false); }
    public static AttributeSupplier.Builder createAlephAttributes() {
        AttributeSupplier iron = IronGolem.createAttributes().build();
        return Zombie.createAttributes()
            .add(Attributes.MAX_HEALTH, iron.getBaseValue(Attributes.MAX_HEALTH))
            .add(Attributes.ATTACK_DAMAGE, iron.getBaseValue(Attributes.ATTACK_DAMAGE) / 2)
            .add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(WINDUP, 0); builder.define(STAGGER, 0);
    }
    @Override public void setBaby(boolean value) { super.setBaby(false); }
    @Override protected boolean convertsInWater() { return false; }
    // Zombie supplies navigation and numeric baselines only, not this construct's voice.
    @Override protected net.minecraft.sounds.SoundEvent getAmbientSound() { return null; }
    @Override protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) { return SoundEvents.IRON_GOLEM_HURT; }
    @Override protected net.minecraft.sounds.SoundEvent getDeathSound() { return SoundEvents.IRON_GOLEM_DEATH; }
    @Override protected void playStepSound(net.minecraft.core.BlockPos position,net.minecraft.world.level.block.state.BlockState state) {
        playSound(SoundEvents.IRON_GOLEM_STEP,.35f,1.1f);
    }
    @Override protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) { }
    @Override protected void handleAttributes(float difficulty) {
        // Do not inherit zombie leader/reinforcement random stat boosts: the plan fixes iron-golem-derived values.
        var attribute = getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE); if (attribute != null) attribute.setBaseValue(0);
    }
    @Override protected void randomizeReinforcementsChance() {
        var attribute = getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE); if (attribute != null) attribute.setBaseValue(0);
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (!(target instanceof LivingEntity living) || !living.isAlive() || isWindingUp() || PlatformRuntime.aiInterrupted(this)) return false;
        pendingTarget = living; entityData.set(WINDUP, 16); getNavigation().stop();
        return false; // The actual hit occurs once at the server windup deadline.
    }
    @Override public void tick() {
        if (level() instanceof ServerLevel server) {
            // Control cancels the old strike; its deadline must not survive recovery.
            // Entity ticks and gravity continue while the AI is paused.
            if (PlatformRuntime.aiInterrupted(this)) { pendingTarget = null; entityData.set(WINDUP, 0); }
            int stagger = entityData.get(STAGGER);
            if (stagger > 0) entityData.set(STAGGER, stagger - 1);
            int windup = entityData.get(WINDUP);
            if (windup > 0) {
                entityData.set(WINDUP, windup - 1);
                if (windup == 1) {
                    LivingEntity target = pendingTarget; pendingTarget = null;
                    if (target != null && target.level() == level() && target.isAlive() && !isStaggered()
                        && isWithinMeleeAttackRange(target) && hasLineOfSight(target)) super.doHurtTarget(server, target);
                } else if (windup <= 9) sendParryRing(server, windup - 1);
            }
        }
        super.tick();
        if (isWindingUp() || PlatformRuntime.aiInterrupted(this)) {
            getNavigation().stop(); setDeltaMovement(0, getDeltaMovement().y(), 0);
        }
        if (!isAlive()) { pendingTarget = null; entityData.set(WINDUP, 0); }
    }
    @Override public void travel(Vec3 input) {
        super.travel(isWindingUp() || PlatformRuntime.aiInterrupted(this) ? new Vec3(0, input.y(), 0) : input);
    }
    public boolean isWindingUp() { return entityData.get(WINDUP) > 0; }
    public void interruptPendingAttack(){pendingTarget=null;entityData.set(WINDUP,0);getNavigation().stop();}
    public boolean isStaggered() { return entityData.get(STAGGER) > 0; }
    public boolean isParryWindow() { int ticks = entityData.get(WINDUP); return isAlive() && ticks > 0 && ticks <= 8; }
    public float windupProgress(float partialTick) {
        int remaining = entityData.get(WINDUP); if (remaining == 0) return 0;
        return Math.clamp((16 - remaining + partialTick) / 16f, 0, 1);
    }
    /** Caller must establish active direct-hit provenance, friendly-fire permissions and phase immunity first. */
    public boolean tryParry(ServerPlayer player) {
        if (!(level() instanceof ServerLevel server) || !player.isAlive() || player.level() != level() || !isParryWindow()) return false;
        pendingTarget = null; entityData.set(WINDUP, 0); entityData.set(STAGGER, 20); getNavigation().stop();
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, .7f, 1.8f);
        server.sendParticles(new DustParticleOptions(0xffd978, 1.2f), getX(), getY() + 1.1, getZ(), 12, .5, .35, .5, 0);
        return true;
    }
    private void sendParryRing(ServerLevel level, int remaining) {
        double radius = .35 + .9 * remaining / 8d;
        double radians = Math.toRadians(getYRot()), rightX = Math.cos(radians), rightZ = Math.sin(radians);
        DustParticleOptions dust = new DustParticleOptions(0xffd978, .7f);
        for (int i = 0; i < 16; i++) {
            double angle = i * Math.PI * 2 / 16, horizontal = Math.cos(angle) * radius;
            level.sendParticles(dust, getX() + rightX * horizontal, getY() + 1.1 + Math.sin(angle) * radius,
                getZ() + rightZ * horizontal, 1, 0, 0, 0, 0);
        }
    }
}
