package cn.zgnhit.mcwuwa.platform.entity;

import cn.zgnhit.mcwuwa.core.EchoBossCatalog;
import cn.zgnhit.mcwuwa.core.EchoBossCatalog.Move;
import cn.zgnhit.mcwuwa.core.EchoBossCatalog.Pattern;
import cn.zgnhit.mcwuwa.platform.PlatformRuntime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.*;

/** Real persistent monsters, never skill summons. All damage and attack deadlines belong to the server. */
public final class EchoBossEntity extends Monster implements ConstructVisual {
    private static final EntityDataAccessor<Integer> MOVE = SynchedEntityData.defineId(EchoBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START = SynchedEntityData.defineId(EchoBossEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> STAGGER = SynchedEntityData.defineId(EchoBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> REALM_PAUSED_AGE = SynchedEntityData.defineId(EchoBossEntity.class, EntityDataSerializers.LONG);
    private static final Move COUNTER = new Move("反击斩", Pattern.PARRY, 18, 26, 6, 1.4f, true);
    private final ServerBossEvent bar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
    private final Set<UUID> struck = new HashSet<>();
    private int nextMove, recovery = 30, stagger, pulse;
    private Vec3 aim = Vec3.ZERO, forward = new Vec3(0, 0, 1), attackOrigin = Vec3.ZERO;
    private float facing;
    private long lastRealmPauseTick=Long.MIN_VALUE;

    public EchoBossEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        setPersistenceRequired(); // Leaving combat never heals or replaces the encounter.
        nextMove = getRandom().nextInt(profile().moves().size());
        bar.setName(getDisplayName());
    }
    public EchoBossCatalog.Boss profile() {
        return EchoBossCatalog.find(BuiltInRegistries.ENTITY_TYPE.getKey(getType()).toString()).orElseThrow();
    }
    public static AttributeSupplier.Builder attributes(EchoBossCatalog.Boss boss) {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, boss.health())
            .add(Attributes.ATTACK_DAMAGE, boss.attack()).add(Attributes.MOVEMENT_SPEED, .29)
            .add(Attributes.FOLLOW_RANGE, 48).add(Attributes.KNOCKBACK_RESISTANCE, .8);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data); data.define(MOVE, -1); data.define(START, 0L); data.define(STAGGER, 0);data.define(REALM_PAUSED_AGE,-1L);
    }
    public Move currentMove() {
        int index = entityData.get(MOVE);
        return index == 99 ? COUNTER : index >= 0 && index < profile().moves().size() ? profile().moves().get(index) : null;
    }
    public int elapsed() { return (int)(realmTimePaused()?entityData.get(REALM_PAUSED_AGE):Math.max(0, level().getGameTime() - startTick())); }
    public boolean realmTimePaused(){return entityData.get(REALM_PAUSED_AGE)>=0;}
    /** One server-world step per paused tick, keeping both hit and visual clocks.
     * No-AI/hard stagger still cancel attacks through their existing paths. */
    public void realmTimePaused(boolean paused){
        if(!(level() instanceof ServerLevel))return;
        long now=level().getGameTime();
        if(paused){
            if(!realmTimePaused())entityData.set(REALM_PAUSED_AGE,Math.max(0,now-startTick()));
            if(lastRealmPauseTick!=now){entityData.set(START,startTick()+1);lastRealmPauseTick=now;}
            bar.setName(getDisplayName());bar.setProgress(Math.clamp(getHealth()/getMaxHealth(),0,1));
        }else entityData.set(REALM_PAUSED_AGE,-1L);
    }
    public int attackIndex() { return entityData.get(MOVE); }
    public int staggerTicks() { return stagger; }
    public boolean isParryWindow() {
        Move move = currentMove();
        return isAlive() && move != null && move.parryable() && elapsed() >= move.windup() - 10 && elapsed() < move.windup();
    }
    public boolean tryParry(ServerPlayer player) {
        if (!(level() instanceof ServerLevel server) || !isParryWindow() || !valid(player) || distanceToSqr(player) > 100) return false;
        cancelAttack(); entityData.set(START,level().getGameTime()); stagger = 30; entityData.set(STAGGER, stagger); recovery = 35;
        server.playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, .6f, 1.9f);
        particles(server, position().add(0, getBbHeight() * .6, 0), 0xffd978, 22, .8);
        return true;
    }
    /** Only the common confirmed direct-hit pipeline may request this response. */
    public boolean tryCounter(ServerPlayer player) {
        Move move = currentMove();
        if (!(level() instanceof ServerLevel) || move == null || move.pattern() != Pattern.GUARD
            || PlatformRuntime.aiInterrupted(this) || elapsed() < move.windup() || elapsed() >= move.duration() || !valid(player) || distanceToSqr(player) > 144) return false;
        setTarget(player); begin(99, player); return true;
    }
    private boolean valid(LivingEntity target) {
        return target instanceof Player player && target.level() == level() && target.isAlive() && !player.isCreative() && !player.isSpectator();
    }
    private void begin(int index, LivingEntity target) {
        entityData.set(MOVE, index); entityData.set(START, level().getGameTime());
        struck.clear(); pulse = 0; aim = target.position(); attackOrigin = position();
        Vec3 offset = aim.subtract(position());
        forward = new Vec3(offset.x, 0, offset.z).normalize();
        if (forward.lengthSqr() < .5) forward = Vec3.directionFromRotation(0, getYRot());
        facing = (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));
        setYRot(facing); setYHeadRot(facing); yBodyRot = facing; getNavigation().stop();
    }
    public void cancelAttack() {
        entityData.set(MOVE, -1); struck.clear(); pulse = 0; getNavigation().stop(); setNoGravity(false);
        if(realmTimePaused())entityData.set(REALM_PAUSED_AGE,0L);
        setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0);
    }
    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) return;
        bar.setName(getDisplayName()); bar.setProgress(Math.clamp(getHealth() / getMaxHealth(), 0, 1));
        if (!isAlive()) { cancelAttack(); bar.removeAllPlayers(); return; }
        if (isNoAi()) { cancelAttack(); return; }
        if (PlatformRuntime.aiInterrupted(this)) { cancelAttack(); recovery = Math.max(recovery, 25); return; }
        if (stagger > 0) { stagger--; entityData.set(STAGGER, stagger); getNavigation().stop(); return; }
        LivingEntity target = getTarget();
        if (!valid(target) || distanceToSqr(target) > 64 * 64) { cancelAttack(); setTarget(null); return; }
        Move move = currentMove();
        if (move == null) {
            if (recovery > 0) recovery--;
            double distance = distanceToSqr(target);
            Move next = profile().moves().get(nextMove);
            if (distance > next.range() * next.range() * .75 || !hasLineOfSight(target)) getNavigation().moveTo(target, 1.0);
            else if (recovery == 0) { begin(nextMove, target); nextMove = (nextMove + 1) % profile().moves().size(); }
            return;
        }
        int at = elapsed(); getNavigation().stop(); setYRot(facing); setYHeadRot(facing); yBodyRot = facing;
        if (at < move.windup()) {
            telegraph(server, move, at);
            if (move.pattern() == Pattern.DIVE || move.pattern() == Pattern.BREATH && modelId().equals("echo_heron")) {
                setNoGravity(true); setDeltaMovement(0, at < 12 ? .11 : 0, 0);
            }
        } else {
            setNoGravity(false);
            attack(server, move, at - move.windup());
        }
        if (at >= move.duration()) { cancelAttack(); recovery = 20; }
    }
    private void telegraph(ServerLevel server, Move move, int at) {
        if (at % 3 != 0) return;
        int color = isParryWindow() ? 0xffd978 : 0xd361da;
        double radius = isParryWindow() ? .35 + (move.windup() - at) * .07 : .7;
        Vec3 center = move.pattern() == Pattern.LIGHTNING ? aim.add(0, .15, 0) : position().add(0, Math.min(2.2, getBbHeight() * .65), 0);
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8;
            Vec3 point = move.pattern() == Pattern.LIGHTNING ? center.add(Math.cos(a) * 2.3, 0, Math.sin(a) * 2.3)
                : center.add(Math.cos(a) * radius * forward.z, Math.sin(a) * radius, -Math.cos(a) * radius * forward.x);
            particles(server, point, color, 1, 0);
        }
    }
    private void attack(ServerLevel server, Move move, int active) {
        switch (move.pattern()) {
            case COMBO -> { if (active == 0 || active == 12 || active == 24) cone(server, move, .1, true); }
            case DASH, DIVE -> {
                if (active < 12) {
                    double speed = Math.min(.85, move.range() / 14);
                    move(MoverType.SELF, forward.scale(speed).add(0, move.pattern() == Pattern.DIVE ? -.15 : 0, 0));
                    area(server, position(), 2.2, 3, move, false);
                }
            }
            case BREATH -> {
                if (active % 12 == 0 && active <= 24) {
                    // A fixed sweep makes the beam dodgeable; it never tracks the player's current location.
                    double angle = modelId().equals("echo_hecate") ? (active - 12) * .055 : 0;
                    Vec3 start = position().add(0, Math.min(2, getBbHeight() * .55), 0);
                    Vec3 direction = aim.add(0,.9,0).subtract(start).normalize().yRot((float) angle);
                    if(direction.lengthSqr()<.5)direction=forward;
                    beam(server, start, direction, move.range(), 1.1, move, true);
                }
            }
            case WAVE -> {
                if (active % 2 == 0 && active <= 20) ring(server, move, move.range() * (active + 2) / 22d);
            }
            case LIGHTNING -> {
                if (active == 0) { beam(server, aim.add(0, 12, 0), new Vec3(0, -1, 0), 12, 1, move, false); area(server, aim, 2.3, 6, move, false); }
            }
            case SWEEP, PARRY -> { if (active == 0) cone(server, move, -.65, true); }
            case WHEEL -> {
                if (active % 2 == 0 && active <= 24) {
                    double f = active <= 12 ? active / 12d : (24 - active) / 12d;
                    Vec3 point = attackOrigin.add(forward.scale(move.range() * f)).add(0, 1.2, 0);
                    if (active == 14) struck.clear();
                    if (clearLine(attackOrigin.add(0, 1.2, 0), point)) area(server, point, 1.8, 3, move, false);
                }
            }
            case BARRAGE -> {
                if (active == 0 || active == 12 || active == 24) {
                    // Three aimed lanes, not a room-wide unavoidable area hit.
                    double angle = (pulse++ - 1) * .16;
                    beam(server, position().add(0, 1.5, 0), forward.yRot((float) angle), move.range(), .7, move, true);
                }
            }
            case BURST -> { if (active == 0) area(server, attackOrigin, move.range(), 4, move, false); }
            case GUARD -> { if (active % 6 == 0) particles(server, position().add(0, 1.8, 0), 0x8edbff, 8, .6); }
        }
    }
    private void cone(ServerLevel server, Move move, double dot, boolean repeat) {
        if (repeat) struck.clear();
        arc(server, position(), move.range(), dot);
        for (Player player : server.getEntitiesOfClass(Player.class, getBoundingBox().inflate(move.range(), 3, move.range()))) {
            Vec3 offset = player.position().subtract(position());
            if (offset.horizontalDistanceSqr() <= move.range() * move.range()
                && new Vec3(offset.x, 0, offset.z).normalize().dot(forward) >= dot && Math.abs(offset.y) < 4) hit(server, player, move);
        }
    }
    private void ring(ServerLevel server, Move move, double radius) {
        arc(server, attackOrigin, radius, -1);
        for (Player player : server.getEntitiesOfClass(Player.class, new AABB(attackOrigin, attackOrigin).inflate(radius + 1, 3, radius + 1))) {
            Vec3 offset = player.position().subtract(attackOrigin);
            if (Math.abs(offset.horizontalDistance() - radius) <= 1.1 && Math.abs(offset.y) < 3) hit(server, player, move);
        }
    }
    private void area(ServerLevel server, Vec3 point, double radius, double height, Move move, boolean repeat) {
        if (repeat) struck.clear();
        particles(server, point.add(0, .8, 0), 0xb29eff, 12, Math.min(radius, 2));
        for (Player player : server.getEntitiesOfClass(Player.class, new AABB(point, point).inflate(radius, height, radius)))
            if (player.position().subtract(point).horizontalDistanceSqr() <= radius * radius && Math.abs(player.getY() - point.y) <= height) hit(server, player, move);
    }
    private void beam(ServerLevel server, Vec3 start, Vec3 direction, double range, double width, Move move, boolean repeat) {
        if (repeat) struck.clear();
        Vec3 end = start.add(direction.scale(range));
        var clip = level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (clip.getType() == HitResult.Type.BLOCK) end = clip.getLocation();
        Vec3 segment = end.subtract(start); double length = segment.lengthSqr();
        for (double d = 0; d <= Math.sqrt(length); d += .6) particles(server, start.add(direction.scale(d)), 0xb9e7ff, 1, .08);
        if (length < .001) return;
        for (Player player : server.getEntitiesOfClass(Player.class, new AABB(start, end).inflate(width + 1))) {
            Vec3 center = player.position().add(0, player.getBbHeight() * .5, 0);
            double t = Math.clamp(center.subtract(start).dot(segment) / length, 0, 1);
            if (center.distanceToSqr(start.add(segment.scale(t))) <= (width + player.getBbWidth() * .5) * (width + player.getBbWidth() * .5)) hit(server, player, move);
        }
    }
    private boolean clearLine(Vec3 start, Vec3 end) {
        return level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.BLOCK;
    }
    private void hit(ServerLevel server, Player player, Move move) {
        if (!valid(player) || struck.contains(player.getUUID()) || !hasLineOfSight(player)) return;
        struck.add(player.getUUID());
        player.hurtServer(server, damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * move.damageScale());
    }
    private void arc(ServerLevel server, Vec3 center, double radius, double minDot) {
        for (int i = 0; i < 28; i++) {
            double angle = i * Math.PI * 2 / 28; Vec3 direction = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            if (direction.dot(forward) >= minDot) particles(server, center.add(direction.scale(radius)).add(0, .3, 0), 0xbb99ff, 1, 0);
        }
    }
    private void particles(ServerLevel server, Vec3 point, int color, int count, double spread) {
        server.sendParticles(new DustParticleOptions(color, .8f), point.x, point.y, point.z, count, spread, spread * .5, spread, 0);
    }
    public static boolean spawnRules(EntityType<EchoBossEntity> type, ServerLevelAccessor access, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (!Monster.checkAnyLightMonsterSpawnRules(type, access, reason, pos, random)) return false;
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) return true;
        ServerLevel level = access.getLevel(); var boss = EchoBossCatalog.find(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()).orElseThrow();
        String biome = level.getBiome(pos).unwrapKey().map(key -> key.identifier().getPath()).orElse("");
        if (!boss.habitat(level.dimension().identifier().toString(), biome, level.getDayTime(), Math.hypot(pos.getX(), pos.getZ()))) return false;
        if(random.nextDouble()>=cn.zgnhit.mcwuwa.platform.CombatConfig.NATURAL_BOSS_SPAWN_CHANCE.getAsDouble())return false;
        return level.getEntitiesOfClass(EchoBossEntity.class, new AABB(pos).inflate(boss.separation()), other -> other.getType() == type
            && other.isAlive() && other.position().distanceToSqr(Vec3.atCenterOf(pos)) <= (double) boss.separation() * boss.separation()).isEmpty();
    }
    @Override public void startSeenByPlayer(ServerPlayer player) { super.startSeenByPlayer(player); bar.addPlayer(player); }
    @Override public void die(net.minecraft.world.damagesource.DamageSource source) {
        super.die(source);
        if (!isAlive() && level() instanceof ServerLevel server) {
            cancelAttack(); bar.removeAllPlayers(); particles(server, position().add(0, getBbHeight()*.5, 0), 0xc8baff, 28, 1);
        }
    }
    @Override public void stopSeenByPlayer(ServerPlayer player) { super.stopSeenByPlayer(player); bar.removePlayer(player); }
    @Override public void remove(RemovalReason reason) { bar.removeAllPlayers(); super.remove(reason); }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); cancelAttack(); recovery = 30; }
    @Override public boolean causeFallDamage(double distance, float multiplier, net.minecraft.world.damagesource.DamageSource source) { return false; }
    @Override public String modelId() { return profile().model(); }
    @Override public String animation() { return entityData.get(STAGGER) > 0 ? "hurt" : currentMove() == null ? "idle" : "attack"; }
    @Override public float visualScale() { return profile().scale(); }
    @Override public int durationTicks() { return currentMove() == null ? 80 : currentMove().duration(); }
    @Override public float animationProgress(double elapsed) {
        if(realmTimePaused())elapsed=entityData.get(REALM_PAUSED_AGE);
        if(entityData.get(STAGGER)>0)return (float)Math.clamp(elapsed/7,0,1);
        Move move=currentMove();
        return move==null?ConstructVisual.super.animationProgress(elapsed)
            :cn.zgnhit.mcwuwa.core.BossAnimationClock.progress(move,elapsed);
    }
    @Override public long startTick() { return entityData.get(START); }
}
