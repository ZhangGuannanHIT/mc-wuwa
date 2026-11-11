package cn.zgnhit.mcwuwa.platform;

import cn.zgnhit.mcwuwa.McWuwa;
import cn.zgnhit.mcwuwa.content.EffectDefinitions;
import cn.zgnhit.mcwuwa.core.*;
import cn.zgnhit.mcwuwa.platform.entity.EnemyEvents;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Target-owned rounds. First-source attribution never follows the current held weapon. */
public final class EffectRuntime {
    @FunctionalInterface public interface DamageSink {
        boolean hurt(ServerPlayer player,EffectSource source,LivingEntity target,Element element,double nativePreDefenseDamage,String cause);
    }
    public record View(String id, int layers, OptionalInt cap, int electroRage, long expiresAt,
                       EffectSource firstSource, boolean executorStopped, String coverage) {}
    /** Actual accepted applications; conversions report their destination, consumes report none.
     * Applications can be accepted at a cap or cause a threshold pulse without leaving a layer.
     */
    public record AppliedMark(String id, int applications) {}
    private static final class Mark {
        final String id; EffectSource source;
        final EffectDefinitions.VerifiedExecution execution;
        final ElementalEffects engine;
        int layers, rage; OptionalInt cap; long expires; boolean stopped;
        Mark(String id, EffectSource source, OptionalInt cap, long expires, long now) {
            this.id=id;this.source=source;this.cap=cap;this.expires=expires;
            execution=EffectDefinitions.execution(id).orElse(null);
            engine=execution==null?null:new ElementalEffects(now);
        }
    }
    private static final Map<LivingEntity, Map<String, Mark>> TARGETS = new WeakHashMap<>();
    private static final Map<LivingEntity, Long> FROZEN = new WeakHashMap<>();
    private record Stasis(UUID player,long expires,boolean endsOnDamage) { }
    private record StasisKey(UUID weapon,String group) { }
    private static final Map<LivingEntity,Map<StasisKey,Stasis>> STASIS=new WeakHashMap<>();
    private static final Map<Mob,Map<UUID,Long>> FOCUS_SLOW=new WeakHashMap<>();
    private static final Map<LivingEntity,Long> FROSTBOUND=new WeakHashMap<>();
    private static final Map<LivingEntity,Map<String,Integer>> CAP_OVERRIDES=new WeakHashMap<>();
    private static final Map<LivingEntity,EffectCapLedger> CAP_SOURCES=new WeakHashMap<>();
    private static final Map<LivingEntity,TuneInterference> TUNE_STATES=new WeakHashMap<>();
    private static final Map<LivingEntity,AttributeModifiers> STAT_DEBUFFS=new WeakHashMap<>();
    private static final Set<String> WARNED = new HashSet<>();
    private static final ResourceLocation SLOW = ResourceLocation.fromNamespaceAndPath("mc_wuwa","glacio_chafe_slow");
    private static final ResourceLocation COLOR_WEAKNESS = ResourceLocation.fromNamespaceAndPath("mc_wuwa","phrolova_color_weakness");
    private static DamageSink damageSink=(player,source,target,element,damage,cause)->false;
    private EffectRuntime() {}
    public static void setDamageSink(DamageSink sink) { damageSink=Objects.requireNonNull(sink); }

    /** duration=0 means consult the definition, not immediate expiry or an invented timer. */
    public static List<AppliedMark> applyMark(ServerPlayer player, CharacterInstance role, LivingEntity target,
                                 String id, int layers, int duration) {
        Objects.requireNonNull(id);
        if(id.equals("tune_offset")){if(!legalTuneOwner(player,role,target))return List.of();id="strain_shifting";}
        if(id.equals("tune_interference"))return applyStrainInterference(player,role,target,layers,duration);
        // A relay has no independent anomaly/status pool; native damage resolves on its owner.
        if(target instanceof cn.zgnhit.mcwuwa.platform.entity.HecateEntity)return List.of();
        if (layers<1 || layers>1024 || duration<0 || !id.matches("[a-z0-9_]+")) throw new IllegalArgumentException("mark request");
        if (player.level()!=target.level() || !PlatformRuntime.canAttack(player,target)) return List.of();
        // Only the applier's own team selects the variant. Other online teams cannot rewrite this hit.
        if(id.equals("glacio_chafe")&&hasFrostConverter(player))id="glacio_frost";
        long now=player.level().getGameTime();
        refreshCaps(target,now);
        for(var mode:TuneInterference.Mode.values())if(id.equals(mode.shiftId)) {
            if(target instanceof net.minecraft.world.entity.player.Player)return List.of();
            var source=new EffectSource(player.getUUID(),role.instanceId(),role.character(),UUID.randomUUID(),UUID.randomUUID());
            TUNE_STATES.computeIfAbsent(target,key->new TuneInterference()).applyShift(mode,source,now,duration>0?duration:500);
            return List.of(new AppliedMark(id,1));
        }
        if(id.startsWith("raise_cap_")) {
            raiseCap(player,role,target,id.substring(10),id,layers,duration,EffectCapLedger.Lifetime.TIMED);return List.of();
        }
        if (id.startsWith("consume_")) { int used=consumeOwned(target,player.getUUID(),role.instanceId(),id.substring(8),layers);PlatformRuntime.anomalyConsumed(player,role,target,id.substring(8),used);return List.of(); }
        if (id.equals("convert_anomalies_to_aero")) return convertToAero(player,role,target,duration);
        if (id.equals("remove_frostbind")) { consume(target,"frostbind",Integer.MAX_VALUE);FROSTBOUND.remove(target);return List.of(); }
        if(id.equals("convert_frost_10_to_frostbind")) {
            if(layersAtKey(target,"glacio_frost")<10)return List.of();
            consumeKey(target,"glacio_frost",10);
            if(!EnemyEvents.profile(target).hardControlImmune()) {
                applyMark(player,role,target,"frostbind",1,40);FROSTBOUND.merge(target,now+40,Math::max);
            }
            return List.of();
        }
        if (id.startsWith("copy_") || id.startsWith("convert_")) {
            warn(id,"special conversion has no verified target/range rule; not substituted with a generic mark");return List.of();
        }
        var definition=EffectDefinitions.find(id);
        var execution=EffectDefinitions.execution(id);
        if (execution.isPresent() && execution.get().sourceVerified() && duration>0 && duration!=execution.get().rules().durationTicks()) {
            warn(id,"duration override differs from the frozen definition; refused to mutate a live rule");return List.of();
        }
        long lifetime=duration>0?duration:execution.map(e->e.rules().durationTicks()).orElse(0L);
        long expires=lifetime>0?Math.addExact(now,lifetime):Long.MAX_VALUE;
        Map<String,Mark> marks=TARGETS.computeIfAbsent(target,key->new HashMap<>());
        String key=storageKey(id,player.getUUID(),role.instanceId());
        Mark existing=marks.get(key);
        if(existing!=null && existing.expires<=now) { marks.remove(key);existing=null; }
        EffectSource applier=new EffectSource(player.getUUID(),role.instanceId(),role.character(),UUID.randomUUID(),UUID.randomUUID());
        OptionalInt cap=execution.isPresent()?OptionalInt.of(execution.get().rules().baseCap()):definition.flatMap(d->d.baseCap().isPresent()?Optional.of(d.baseCap()):Optional.empty()).orElse(OptionalInt.empty());
        if(id.equals("phrolova_color_weakness")||id.equals("frostbind"))cap=OptionalInt.of(1);
        // Private marks can have a fixed local cap without belonging to the six shared effect definitions.
        if(cap.isPresent()&&definition.isPresent())cap=OptionalInt.of(effectiveCap(target,id,now));
        Mark mark=existing==null?new Mark(id,applier,cap,expires,now):existing;
        boolean aeroWasCapped=id.equals("aero_erosion")&&existing!=null&&existing.layers>=cap.orElse(Integer.MAX_VALUE);
        mark.cap=cap;
        mark.expires=expires;
        if (mark.engine!=null) {
            for(int i=0;i<layers;i++) {
                var applied=mark.engine.apply(mark.execution.rules(),applier,mark.cap.orElseThrow(),false,EnemyEvents.profile(target).hardControlImmune(),now,lifetime,
                    id.equals("electro_flare")?effectiveCap(target,"electro_rage",now):0);
                applied.pulses().forEach(pulse->dispatch(target,mark,pulse));
                if(applied.freezeRequested() && mark.execution.freezeTicks().isPresent()) FROZEN.merge(target,
                    Math.addExact(now,mark.execution.sourceVerified()?mark.execution.freezeTicks().getAsInt():GlobalCombatRules.freezeTicks(mark.cap.orElseThrow())),Math::max);
                mark.layers=applied.resultingLayers();
                if(id.equals("electro_flare")&&hasElectroCollector(player,target)) {
                    int rage=mark.engine.consumeElectroRage(id,now);
                    if(rage>0)PlatformRuntime.electroRageReleased(player,target,rage);
                }
            }
            updateFromEngine(mark);
        } else {
            mark.layers=(int)Math.min(mark.cap.orElse(Integer.MAX_VALUE),Math.min(Integer.MAX_VALUE,(long)mark.layers+layers));
            if (definition.isPresent() && !id.equals("havoc_bane")) warn(id,"numeric source missing: layers recorded, autonomous damage/control disabled");
            if (definition.isPresent() && lifetime==0) warn(id+"_duration","default duration absent in source: no invented expiry; cleared on target unload/death");
        }
        if(mark.layers==0)marks.remove(key);else marks.put(key,mark);
        if(id.equals("phrolova_color_weakness"))applyStatDebuff(player,role,target,AttributeModifiers.Zone.ATTACK_PERCENT,"phrolova_color_weakness",-.20,lifetime,AttributeModifiers.Selector.any());
        applySlow(target,marks);
        if(id.equals("electro_flare"))PlatformRuntime.electroLayersApplied(player,role,target,layers);
        // One settlement per accepted application batch, only when the target was full BEFORE
        // reapplication. Do not fill its layers here or redirect ownership to the C6 window owner.
        if(aeroWasCapped&&PlatformRuntime.cappedAeroSettlementActive(player))detonate(player,role,target,id,false,false,1);
        return List.of(new AppliedMark(id,layers));
    }

    /** Own-team aura, including a background Hsin; the first Flare applier need not be Hsin. */
    private static boolean hasElectroCollector(ServerPlayer player,LivingEntity target) {
        if(player==null||player.level()!=target.level()||player.distanceToSqr(target)>144||!player.hasLineOfSight(target))return false;
        var team=ModAttachments.team(player).state();if(!team.inCombat())return false;
        for(int slot=0;slot<3;slot++) {
            var owner=team.at(slot).orElse(null);
            if(owner!=null&&owner.alive()&&owner.character()==CharacterId.HSIN&&PlatformRuntime.mechanicsView(player,owner).mode().equals("electro"))return true;
        }
        return false;
    }

    public static boolean hasEffect(LivingEntity target,String id) { return layers(target,id)>0; }
    /** Native role ATK is separate from Minecraft's melee ATTACK_DAMAGE attribute. */
    public static double attackStatMultiplier(LivingEntity target) {
        return attackStatMultiplier(target,Element.PHYSICAL,DamageKind.VANILLA);
    }
    public static double attackStatMultiplier(LivingEntity target,Element element,DamageKind kind){return Math.max(0,1+statDebuffs(target,element,kind).additive(AttributeModifiers.Zone.ATTACK_PERCENT));}
    public static boolean hasOwnedEffect(LivingEntity target,UUID owner,String id) { return ownedLayers(target,owner,id)>0; }
    public static boolean hasOwnedEffect(LivingEntity target,UUID owner,UUID weapon,String id) { return ownedLayers(target,owner,weapon,id)>0; }
    /** Compatibility/diagnostic aggregate. Production attacks use the exact weapon overload. */
    public static int ownedLayers(LivingEntity target,UUID owner,String id) {
        if(shared(id))return layers(target,id);
        return TARGETS.getOrDefault(target,Map.of()).entrySet().stream().filter(e->e.getValue().id.equals(id)&&e.getValue().source.playerId().equals(owner))
            .mapToInt(e->layersAtKey(target,e.getKey())).sum();
    }
    public static int ownedLayers(LivingEntity target,UUID owner,UUID weapon,String id) { return shared(id)?layers(target,id):layersAtKey(target,storageKey(id,owner,weapon)); }
    public static int layers(LivingEntity target,String id) {
        if(id.equals("glacio_chafe"))return layersAtKey(target,id)+layersAtKey(target,"glacio_frost");
        var state=TUNE_STATES.get(target);
        if(state!=null)for(var mode:TuneInterference.Mode.values()) {
            if(id.equals(mode.shiftId))return state.shifted(mode,target.level().getGameTime())?1:0;
            if(id.equals(mode.interferenceId))return state.layers(mode,target.level().getGameTime());
        }
        return layersAtKey(target,id);
    }
    public static List<TuneInterference.Activation> tuneBreakAccepted(ServerPlayer player,CharacterInstance role,LivingEntity target) {
        var state=TUNE_STATES.get(target);if(state==null||target instanceof net.minecraft.world.entity.player.Player)return List.of();
        var source=new EffectSource(player.getUUID(),role.instanceId(),role.character(),UUID.randomUUID(),UUID.randomUUID());
        if(!legalTuneOwner(player,role,target))return List.of();
        return state.onBreak(source,true,player.level().getGameTime(),strainInterferenceCap(player));
    }
    /** Base cap one is retained as an MC adaptation; each formation response adds one. */
    public static int strainInterferenceCap(ServerPlayer player) {
        int cap=1;
        for(int i=0;i<3;i++){
            var member=ModAttachments.team(player).state().at(i).orElse(null);if(member==null)continue;
            if(member.character()==CharacterId.QINGXIAO||member.character()==CharacterId.LYNAE||member.character()==CharacterId.MORNYE)cap++;
            if(member.character()==CharacterId.DENIA){
                String mode=cn.zgnhit.mcwuwa.content.mechanics.MechanicsState.canonicalMode("denia",member.selectedMode());
                if(mode.equals("strain")||mode.equals("tune"))cap++;
            }
        }
        return cap;
    }
    private static boolean legalTuneOwner(ServerPlayer player,CharacterInstance role,LivingEntity target) {
        if(player.level()!=target.level()||!role.alive()||target instanceof net.minecraft.world.entity.player.Player
            ||!PlatformRuntime.canAttack(player,target))return false;
        for(int i=0;i<3;i++)if(ModAttachments.team(player).state().at(i).map(r->r.instanceId().equals(role.instanceId())).orElse(false))return true;
        return false;
    }
    public static boolean refreshStrainInterference(ServerPlayer player,LivingEntity target) {
        if(!PlatformRuntime.canAttack(player,target))return false;
        var state=TUNE_STATES.get(target);
        return state!=null&&state.refresh(TuneInterference.Mode.STRAIN,player.level().getGameTime(),TuneInterference.Mode.STRAIN.interferenceTicks);
    }
    public static List<AppliedMark> applyStrainInterference(ServerPlayer player,CharacterInstance role,LivingEntity target,int layers,int duration) {
        if(layers<1||layers>1024||duration<0)throw new IllegalArgumentException("strain request");
        if(target instanceof cn.zgnhit.mcwuwa.platform.entity.HecateEntity||!legalTuneOwner(player,role,target))return List.of();
        var source=new EffectSource(player.getUUID(),role.instanceId(),role.character(),UUID.randomUUID(),UUID.randomUUID());
        TUNE_STATES.computeIfAbsent(target,key->new TuneInterference()).applyStrain(source,layers,player.level().getGameTime(),duration>0?duration:600,strainInterferenceCap(player));
        return List.of(new AppliedMark("strain_interference",layers));
    }
    public static int strainInterferenceLayers(LivingEntity target){return layers(target,"strain_interference");}
    private static int layersAtKey(LivingEntity target,String key) {
        var mark=TARGETS.getOrDefault(target,Map.of()).get(key);
        return mark==null || !(target.level() instanceof ServerLevel level) || mark.expires<=level.getGameTime()?0:mark.layers;
    }
    public static double defenseReduction(LivingEntity target) {
        return defenseReduction(target,Element.PHYSICAL,DamageKind.VANILLA);
    }
    public static double defenseReduction(LivingEntity target,Element element,DamageKind kind) {
        return Math.clamp(layers(target,"havoc_bane")*EffectDefinitions.HAVOC_DEFENSE_REDUCTION_PER_LAYER+statDebuffs(target,element,kind).additive(AttributeModifiers.Zone.DEFENSE_REDUCTION),0,1);
    }
    public static double resistanceReduction(LivingEntity target,Element element,DamageKind kind) {return statDebuffs(target,element,kind).additive(AttributeModifiers.Zone.RESISTANCE_REDUCTION);}
    public static double vulnerability(LivingEntity target,Element element,DamageKind kind) {return statDebuffs(target,element,kind).independentFactors(AttributeModifiers.Zone.VULNERABILITY).stream().reduce(1d,(a,b)->a*b);}
    public static AttributeModifiers.View statDebuffs(LivingEntity target,Element element,DamageKind kind) {
        var ledger=STAT_DEBUFFS.get(target);return ledger==null?new AttributeModifiers.View(Map.of(),0):ledger.resolve(new AttributeModifiers.Context(element,kind,false,false),target.level().getGameTime());
    }
    /** End one source-owned aura without erasing its other marks or another caster's effects. */
    public static void clearOwnedStatGroup(UUID weaponId,String group){
        for(var entry:List.copyOf(STAT_DEBUFFS.entrySet())){
            var ledger=entry.getValue();long before=ledger.revision();ledger.clearSourceWeaponGroup(weaponId,group);
            if(before!=ledger.revision())applySlow(entry.getKey(),TARGETS.getOrDefault(entry.getKey(),Map.of()));
            if(ledger.isEmpty())STAT_DEBUFFS.remove(entry.getKey());
        }
    }
    /** Trusted server-side content API. Clients cannot submit values, groups, sources or durations. */
    public static boolean applyStatDebuff(ServerPlayer player,CharacterInstance role,LivingEntity target,AttributeModifiers.Zone zone,String group,double value,long duration,AttributeModifiers.Selector selector) {
        if(player.level()!=target.level()||!role.alive()||!PlatformRuntime.canAttack(player,target))return false;
        if(!Set.of(AttributeModifiers.Zone.ATTACK_PERCENT,AttributeModifiers.Zone.DEFENSE_REDUCTION,AttributeModifiers.Zone.RESISTANCE_REDUCTION,AttributeModifiers.Zone.VULNERABILITY).contains(zone))throw new IllegalArgumentException("unsupported target debuff zone");
        if(!Double.isFinite(value)||Math.abs(value)>16||duration<1||duration>72000)throw new IllegalArgumentException("debuff magnitude/duration");
        UUID identity=UUID.nameUUIDFromBytes((player.getUUID()+"/"+role.instanceId()+"/"+zone+"/"+group).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var source=new EffectSource(player.getUUID(),role.instanceId(),role.character(),identity,identity);
        STAT_DEBUFFS.computeIfAbsent(target,key->new AttributeModifiers()).put(new AttributeModifiers.Modifier(identity,source,zone,group,
            AttributeModifiers.Stacking.STRONGEST_MAGNITUDE,value,Math.addExact(player.level().getGameTime(),duration),selector));
        applySlow(target,TARGETS.getOrDefault(target,Map.of()));return true;
    }
    public static boolean isFrozen(LivingEntity target) {
        if(!(target.level() instanceof ServerLevel level))return false;
        return !EnemyEvents.profile(target).hardControlImmune()
                &&(FROZEN.getOrDefault(target,0L)>level.getGameTime()||FROSTBOUND.getOrDefault(target,0L)>level.getGameTime())
            ||STASIS.getOrDefault(target,Map.of()).entrySet().stream()
                .anyMatch(entry->entry.getValue().expires()>level.getGameTime()&&stasisAllowed(target,entry.getKey().group()));
    }
    private static boolean stasisAllowed(LivingEntity target,String group){
        var profile=EnemyEvents.profile(target);
        return group.equals("augusta_realm_stasis")?profile.augustaStasisAllowed():!profile.hardControlImmune();
    }
    public static boolean augustaTimeStopped(Mob target){
        return target.level() instanceof ServerLevel level&&target.isAlive()&&!target.isRemoved()
            &&EnemyEvents.profile(target).augustaStasisAllowed()
            &&STASIS.getOrDefault(target,Map.of()).entrySet().stream()
                .anyMatch(entry->entry.getKey().group().equals("augusta_realm_stasis")&&entry.getValue().expires()>level.getGameTime());
    }
    /** Skill stasis uses the same hard-control immunity and expiry ownership as sourced freezes. */
    public static boolean applyStasis(ServerPlayer player, CharacterInstance role, LivingEntity target, int durationTicks) {
        return applyStasis(player,role,target,durationTicks,false);
    }
    public static boolean applyStasis(ServerPlayer player, CharacterInstance role, LivingEntity target, int durationTicks,boolean endsOnDamage) {
        return applyStasis(player,role,target,durationTicks,endsOnDamage,"");
    }
    public static boolean applyStasis(ServerPlayer player,CharacterInstance role,LivingEntity target,int durationTicks,String group){
        return applyStasis(player,role,target,durationTicks,false,group);
    }
    private static boolean applyStasis(ServerPlayer player,CharacterInstance role,LivingEntity target,int durationTicks,boolean endsOnDamage,String group){
        if(durationTicks<1||durationTicks>1200)throw new IllegalArgumentException("stasis duration");
        if(target instanceof cn.zgnhit.mcwuwa.platform.entity.HecateEntity||!role.alive()||player.level()!=target.level()
            ||!PlatformRuntime.canAttack(player,target)||!stasisAllowed(target,group))return false;
        if(group.equals("augusta_realm_stasis")&&role.character().sourceId()!=1306)return false;
        STASIS.computeIfAbsent(target,key->new HashMap<>()).merge(new StasisKey(role.instanceId(),group),new Stasis(player.getUUID(),Math.addExact(target.level().getGameTime(),durationTicks),endsOnDamage),
            (old,next)->old.expires()>next.expires()?old:next);
        target.setDeltaMovement(0,target.getDeltaMovement().y,0);if(target instanceof Mob mob)mob.getNavigation().stop();return true;
    }
    public static void clearOwnedStasis(UUID weapon) {
        for(var target:List.copyOf(STASIS.keySet())){var sources=STASIS.get(target);sources.keySet().removeIf(key->key.weapon().equals(weapon));if(sources.isEmpty())STASIS.remove(target);}
    }
    public static void clearStasisGroup(UUID weapon,String group){
        var source=new StasisKey(weapon,group);
        for(var target:List.copyOf(STASIS.keySet())){var sources=STASIS.get(target);sources.remove(source);if(sources.isEmpty())STASIS.remove(target);}
    }
    /** Lucilla's unspecified time ratio is adapted to one native mob tick in two.
     * World/player clocks and other casters' independent controls are untouched. */
    public static void focusSlow(ServerPlayer player,CharacterInstance owner,Collection<Mob> targets){
        clearFocusSlow(owner.instanceId());
        if(owner.character().sourceId()!=1109||!owner.alive())return;
        long expires=player.level().getGameTime()+3;
        for(var target:targets)if(target.level()==player.level()&&target.isAlive()&&!target.isRemoved()
            &&PlatformRuntime.canAttack(player,target)&&!EnemyEvents.profile(target).hardControlImmune())
            FOCUS_SLOW.computeIfAbsent(target,key->new HashMap<>()).put(owner.instanceId(),expires);
    }
    public static void clearFocusSlow(UUID weapon){
        for(var target:List.copyOf(FOCUS_SLOW.keySet())){
            var sources=FOCUS_SLOW.get(target);sources.remove(weapon);if(sources.isEmpty())FOCUS_SLOW.remove(target);
        }
    }
    public static boolean focusSlowed(Mob target){
        return target.level() instanceof ServerLevel level&&target.isAlive()&&!target.isRemoved()
            &&!EnemyEvents.profile(target).hardControlImmune()
            &&FOCUS_SLOW.getOrDefault(target,Map.of()).values().stream().anyMatch(expires->expires>level.getGameTime());
    }
    public static boolean skipFocusTick(Mob target){return focusSlowed(target)&&(target.level().getGameTime()&1L)!=0;}
    public static void damagedStasis(LivingEntity target) {
        var sources=STASIS.get(target);if(sources==null)return;
        sources.values().removeIf(Stasis::endsOnDamage);if(sources.isEmpty())STASIS.remove(target);
    }
    /** Fixed-area fields pull towards their own centre, rather than towards every affected mob. */
    public static int pullToPoint(ServerPlayer player,CharacterInstance role,net.minecraft.world.phys.Vec3 center,double radius,double strength) {
        if(radius<=0||radius>16||strength<=0||strength>1||!Double.isFinite(radius)||!Double.isFinite(strength))throw new IllegalArgumentException("pull geometry");
        if(!role.alive())return 0;int count=0;
        for(var target:player.level().getEntitiesOfClass(LivingEntity.class,new net.minecraft.world.phys.AABB(center,center).inflate(radius),
            e->PlatformRuntime.canAttack(player,e)&&player.hasLineOfSight(e)&&e.position().distanceToSqr(center)<=radius*radius&&!EnemyEvents.profile(e).hardControlImmune())) {
            var delta=center.subtract(target.position()).multiply(1,0,1);if(delta.lengthSqr()<.01)continue;
            var velocity=delta.normalize().scale(Math.min(strength,delta.length()));applyPullMotion(target,velocity);count++;
        }
        return count;
    }
    /** A bounded velocity impulse; ordinary collision remains authoritative, no teleport/no-gravity mutation. */
    /** Source-defined outward displacement; native knockback keeps resistance and collisions. */
    public static int pushArea(ServerPlayer player,CharacterInstance role,double radius,double strength){
        if(!Double.isFinite(radius)||radius<=0||radius>16||!Double.isFinite(strength)||strength<=0||strength>1)throw new IllegalArgumentException("push geometry");
        if(!role.alive())return 0;int count=0;
        for(LivingEntity target:player.level().getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(radius),
            e->PlatformRuntime.canAttack(player,e)&&player.distanceToSqr(e)<=radius*radius&&player.hasLineOfSight(e)&&!EnemyEvents.profile(e).hardControlImmune())){
            var toward=player.position().subtract(target.position()).multiply(1,0,1);
            if(toward.lengthSqr()<.01)continue;
            target.knockback(strength,toward.x,toward.z);target.hurtMarked=true;count++;
        }
        return count;
    }
    public static int pullArea(ServerPlayer player, CharacterInstance role, LivingEntity center, double radius, double strength) {
        if(!Double.isFinite(radius)||radius<=0||radius>16||!Double.isFinite(strength)||strength<=0||strength>1)throw new IllegalArgumentException("pull geometry");
        if(!role.alive()||center!=player&&!PlatformRuntime.canAttack(player,center))return 0;
        int count=0;
        for(LivingEntity target:player.level().getEntitiesOfClass(LivingEntity.class,center.getBoundingBox().inflate(radius),
            e->!(e instanceof cn.zgnhit.mcwuwa.platform.entity.HecateEntity)&&PlatformRuntime.canAttack(player,e)&&e.distanceToSqr(center)<=radius*radius&&player.hasLineOfSight(e)&&!EnemyEvents.profile(e).hardControlImmune())) {
            var offset=center.position().subtract(target.position()).multiply(1,0,1);
            if(offset.lengthSqr()<.01)continue;
            var direction=offset.normalize().scale(Math.min(strength,offset.length()));
            applyPullMotion(target,direction);count++;
        }
        return count;
    }
    /** Chisa's pull is centred on herself and accepts only this weapon's private void scars. */
    public static int pullOwnedScarArea(ServerPlayer player,CharacterInstance role,double radius,double strength) {
        if(!Double.isFinite(radius)||radius<=0||radius>16||!Double.isFinite(strength)||strength<=0||strength>1)throw new IllegalArgumentException("scar pull geometry");
        if(!role.alive())return 0;int count=0;
        for(LivingEntity target:player.level().getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(radius),
            e->!(e instanceof cn.zgnhit.mcwuwa.platform.entity.HecateEntity)&&PlatformRuntime.canAttack(player,e)&&player.distanceToSqr(e)<=radius*radius
                &&player.hasLineOfSight(e)&&!EnemyEvents.profile(e).hardControlImmune()&&hasOwnedEffect(e,player.getUUID(),role.instanceId(),"chisa_void_scar"))) {
            var offset=player.position().subtract(target.position()).multiply(1,0,1);if(offset.lengthSqr()<.01)continue;
            var direction=offset.normalize().scale(Math.min(strength,offset.length()));
            applyPullMotion(target,direction);count++;
        }return count;
    }
    /** Stasis cancels ordinary locomotion, but must not cancel a skill's own pull.
     * Consume that bounded displacement through native collisions immediately;
     * leaving another velocity impulse would move twice on the following tick. */
    private static void applyPullMotion(LivingEntity target,net.minecraft.world.phys.Vec3 direction){
        long now=target.level().getGameTime();
        boolean stopped=STASIS.getOrDefault(target,Map.of()).values().stream().anyMatch(value->value.expires()>now);
        if(stopped){
            target.move(net.minecraft.world.entity.MoverType.SELF,direction);
            target.setDeltaMovement(0,target.getDeltaMovement().y,0);
        }else target.setDeltaMovement(direction.x,target.getDeltaMovement().y,direction.z);
        target.hurtMarked=true;
    }
    public static List<View> snapshots(LivingEntity target) {
        if(!(target.level() instanceof ServerLevel level))return List.of();
        var result=new ArrayList<>(TARGETS.getOrDefault(target,Map.of()).values().stream().filter(mark->mark.expires>level.getGameTime() && mark.layers>0)
            .sorted(Comparator.comparing(mark->mark.id)).map(mark->new View(mark.id,mark.layers,mark.cap,mark.rage,mark.expires,mark.source,mark.stopped,
                mark.engine!=null?(mark.execution.sourceVerified()?"verified_execution":"minecraft_adaptation"):mark.id.equals("havoc_bane")?(mark.expires==Long.MAX_VALUE?"verified_defense_duration_pending":"verified_defense_duration_from_caller"):
                    EffectDefinitions.find(mark.id).isPresent()?"layers_only_numeric_pending":"source_owned_skill_mark")).toList());
        var tune=TUNE_STATES.get(target);if(tune!=null)for(var status:tune.snapshots(level.getGameTime()))
            result.add(new View(status.id(),status.layers(),OptionalInt.of(status.cap()),0,status.expiresAt(),status.source(),false,"source_response_with_mc_shared_stack_refresh"));
        result.sort(Comparator.comparing(View::id));return List.copyOf(result);
    }
    public static int consume(LivingEntity target,String id,int count) {
        if(id.equals("glacio_chafe")){int normal=consumeKey(target,id,count);return normal+consumeKey(target,"glacio_frost",count-normal);}
        int used=consumeKey(target,id,count);if(id.equals("frostbind")&&layersAtKey(target,id)==0)FROSTBOUND.remove(target);return used;
    }
    public static int consumeOwned(LivingEntity target,UUID owner,String id,int count) {
        if(shared(id))return consume(target,id,count);
        int remaining=count,consumed=0;
        for(var entry:List.copyOf(TARGETS.getOrDefault(target,Map.of()).entrySet()))if(entry.getValue().id.equals(id)&&entry.getValue().source.playerId().equals(owner)) {
            int used=consumeKey(target,entry.getKey(),remaining);consumed+=used;remaining-=used;if(remaining<=0)break;
        }
        return consumed;
    }
    public static int consumeOwned(LivingEntity target,UUID owner,UUID weapon,String id,int count) {return shared(id)?consume(target,id,count):consumeKey(target,storageKey(id,owner,weapon),count);}
    private static int consumeKey(LivingEntity target,String key,int count) {
        if(count<1)return 0;
        var marks=TARGETS.get(target);if(marks==null)return 0;
        var mark=marks.get(key);if(mark==null)return 0;
        int amount=Math.min(count,mark.layers);
        if(mark.engine!=null && target.level() instanceof ServerLevel level) {
            amount=mark.engine.consumeLayers(mark.id,count,level.getGameTime());updateFromEngine(mark);
        } else mark.layers-=amount;
        if(mark.layers==0)marks.remove(key);applySlow(target,marks);return amount;
    }
    public static void setCap(LivingEntity target,String id,int cap) {
        if(cap<1||cap>1024||EffectDefinitions.find(id).isEmpty())throw new IllegalArgumentException("cap");
        if(id.equals("glacio_frost"))id="glacio_chafe";
        CAP_OVERRIDES.computeIfAbsent(target,key->new HashMap<>()).put(id,cap);
        refreshCaps(target,target.level().getGameTime());
    }
    /** Production cap changes carry a source, named stacking group and explicit lifetime; no client setter. */
    public static boolean raiseCap(ServerPlayer player,CharacterInstance role,LivingEntity target,String effect,String group,
                                   int additionalLayers,long duration,EffectCapLedger.Lifetime lifetime) {
        if(!(target.level() instanceof ServerLevel)||player.level()!=target.level()||!PlatformRuntime.canAttack(player,target))return false;
        if(duration<1||additionalLayers<1||additionalLayers>1024||(!effect.equals("electro_rage")&&EffectDefinitions.find(effect).isEmpty()))throw new IllegalArgumentException("cap contribution");
        if(effect.equals("glacio_frost"))effect="glacio_chafe";
        long now=player.level().getGameTime();
        UUID identity=UUID.nameUUIDFromBytes((player.getUUID()+":"+role.instanceId()+":"+effect+":"+group).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var source=new EffectSource(player.getUUID(),role.instanceId(),role.character(),identity,identity);
        CAP_SOURCES.computeIfAbsent(target,key->new EffectCapLedger()).put(new EffectCapLedger.Contribution(identity,effect,group,source,additionalLayers,Math.addExact(now,duration),lifetime));
        refreshCaps(target,now);return true;
    }
    public static int effectiveCap(LivingEntity target,String effect,long now) {
        if(effect.equals("glacio_frost"))effect="glacio_chafe";
        final String resolved=effect;
        int base=effect.equals("electro_rage")?10:EffectDefinitions.execution(effect).map(e->e.rules().baseCap())
            .orElseGet(()->EffectDefinitions.find(resolved).orElseThrow().baseCap().orElseThrow());
        base=CAP_OVERRIDES.getOrDefault(target,Map.of()).getOrDefault(effect,base);
        var ledger=CAP_SOURCES.get(target);return ledger==null?base:ledger.resolve(effect,base,now);
    }
    public static List<EffectCapLedger.Contribution> capContributions(LivingEntity target) {
        var ledger=CAP_SOURCES.get(target);return ledger==null?List.of():ledger.active(target.level().getGameTime());
    }
    private static void refreshCaps(LivingEntity target,long now) {
        var ledger=CAP_SOURCES.get(target);if(ledger!=null){ledger.expire(now);if(ledger.isEmpty())CAP_SOURCES.remove(target);}
        var marks=TARGETS.getOrDefault(target,Map.of());
        boolean changed=false;
        for(var mark:marks.values())if(EffectDefinitions.find(mark.id).isPresent()) {
            int next=effectiveCap(target,mark.id,now);boolean capChanged=mark.cap.isEmpty()||mark.cap.getAsInt()!=next;
            int rageCap=mark.id.equals("electro_flare")?effectiveCap(target,"electro_rage",now):0;
            boolean rageChanged=mark.engine!=null&&mark.id.equals("electro_flare")&&mark.engine.snapshot(mark.id).map(s->s.electroRageCap()!=rageCap).orElse(false);
            if(!capChanged&&!rageChanged)continue;changed=true;mark.cap=OptionalInt.of(next);
            if(mark.engine!=null){mark.engine.updateCap(mark.id,next);
                if(mark.id.equals("electro_flare"))mark.engine.updateElectroRageCap(mark.id,rageCap);
                updateFromEngine(mark);
            }else mark.layers=Math.min(mark.layers,next);
        }
        if(changed)applySlow(target,marks);
    }
    public static void weaponUnequipped(UUID weapon) {
        clearOwnedStasis(weapon);
        clearFocusSlow(weapon);
        for(var entry:List.copyOf(CAP_SOURCES.entrySet())) {entry.getValue().weaponUnequipped(weapon);refreshCaps(entry.getKey(),entry.getKey().level().getGameTime());}
        for(var entry:List.copyOf(TARGETS.entrySet())) {
            entry.getValue().entrySet().removeIf(e->e.getValue().source.weaponInstanceId().equals(weapon)&&attackMark(e.getValue().id));
            for(var mark:entry.getValue().values())if(mark.source.weaponInstanceId().equals(weapon)){mark.stopped=true;if(mark.engine!=null)mark.engine.stopWeaponExecutors(weapon);}
            applySlow(entry.getKey(),entry.getValue());
        }
    }
    /** Fill an already known live cap without inventing the missing default aero cap. */
    public static List<AppliedMark> fillKnownCap(ServerPlayer player, CharacterInstance role, LivingEntity target, String id) {
        var mark = TARGETS.getOrDefault(target, Map.of()).get(storageKey(id,player.getUUID(),role.instanceId()));
        if (mark == null || mark.expires <= player.level().getGameTime() || mark.cap.isEmpty()) {
            return applyMark(player,role,target,id,effectiveCap(target,id,player.level().getGameTime()),0);
        }
        int missing = mark.cap.getAsInt() - mark.layers;
        if (missing > 1024) { warn(id + "_fill_bound", "cap fill exceeds application bound; current layers retained"); return List.of(); }
        return missing > 0 ? applyMark(player, role, target, id, missing, 0) : List.of();
    }
    /** Active role explicitly settles an existing round. No refresh, ownership rewrite or new stack.
     * Return value means actual accepted damage. Consume-one is performed only after acceptance. */
    public static boolean detonate(ServerPlayer player,CharacterInstance role,LivingEntity target,String id,boolean consumeAll,boolean consumeOne,double amplification) {
        if(!Double.isFinite(amplification)||amplification<0||amplification>1000)throw new IllegalArgumentException("detonation amplification");
        var mark=TARGETS.getOrDefault(target,Map.of()).get(storageKey(id,player.getUUID(),role.instanceId()));
        if(mark==null||mark.engine==null||mark.stopped||!role.alive()||!PlatformRuntime.canAttack(player,target))return false;
        var source=new EffectSource(player.getUUID(),role.instanceId(),role.character(),UUID.randomUUID(),UUID.randomUUID());
        var pulse=mark.engine.detonate(id,source,false,player.level().getGameTime());if(pulse.isEmpty())return false;
        double damage=pulse.get().nativePreDefenseDamage()*amplification;Element element=EffectDefinitions.find(id).orElseThrow().element();
        boolean accepted=damageSink.hurt(player,source,target,element,damage,"effect/"+id+"/explicit_detonation");
        if(accepted&&id.equals("fusion_burst"))PlatformRuntime.fusionBurstObserved(player,target);
        if(accepted&&(consumeOne||consumeAll)){int used=mark.engine.consumeLayers(id,consumeAll?Integer.MAX_VALUE:1,player.level().getGameTime());updateFromEngine(mark);PlatformRuntime.anomalyConsumed(player,role,target,id,used);}
        if(mark.layers==0)TARGETS.getOrDefault(target,new HashMap<>()).remove(storageKey(id,player.getUUID(),role.instanceId()));
        return accepted;
    }
    /** Fusion main-target amplification does not multiply the collateral ring. No invented stacks. */
    static boolean fusionAtCap(ServerPlayer player,CharacterInstance role,LivingEntity target,boolean consume,double mainAmplification) {
        if(!Double.isFinite(mainAmplification)||mainAmplification<0||mainAmplification>1000)throw new IllegalArgumentException("fusion amplification");
        var mark=TARGETS.getOrDefault(target,Map.of()).get("fusion_burst");
        if(mark==null||mark.engine==null||mark.stopped||!role.alive()||!PlatformRuntime.canAttack(player,target))return false;
        var source=new EffectSource(player.getUUID(),role.instanceId(),role.character(),UUID.randomUUID(),UUID.randomUUID());
        var pulse=mark.engine.detonateAtCap("fusion_burst",source,player.level().getGameTime());if(pulse.isEmpty())return false;
        double damage=pulse.get().nativePreDefenseDamage(),radius=mark.execution.burstRadiusBlocks().orElseThrow();
        boolean accepted=damageSink.hurt(player,source,target,Element.FUSION,damage*mainAmplification,"effect/fusion_burst/cap_detonation");
        for(var nearby:player.level().getEntitiesOfClass(LivingEntity.class,target.getBoundingBox().inflate(radius),
            e->e!=target&&e.distanceToSqr(target)<=radius*radius&&PlatformRuntime.canAttack(player,e)&&player.hasLineOfSight(e)))
            accepted|=damageSink.hurt(player,source,nearby,Element.FUSION,damage,"effect/fusion_burst/cap_collateral");
        if(accepted)PlatformRuntime.fusionBurstObserved(player,target);
        if(accepted&&consume){int used=mark.engine.consumeLayers("fusion_burst",Integer.MAX_VALUE,player.level().getGameTime());updateFromEngine(mark);PlatformRuntime.anomalyConsumed(player,role,target,"fusion_burst",used);}
        if(mark.layers==0)TARGETS.getOrDefault(target,new HashMap<>()).remove("fusion_burst");return accepted;
    }
    /** C2 real enemy defeat: one cap-sized burst at that enemy, with no living main target.
     * It never attaches or consumes a surviving enemy's round; radius uses the effect definition. */
    static int defeatedFusionBurst(ServerPlayer player,CharacterInstance role,LivingEntity defeated) {
        if(!role.alive()||role.character()!=CharacterId.AEMEATH||role.chain()<2||player.level()!=defeated.level())return 0;
        var execution=EffectDefinitions.execution("fusion_burst").orElseThrow();int cap=effectiveCap(defeated,"fusion_burst",player.level().getGameTime());
        var source=new EffectSource(player.getUUID(),role.instanceId(),role.character(),UUID.randomUUID(),UUID.randomUUID());
        double damage=execution.rules().damageFormula().evaluate(source,cap,cap,0,ElementalEffects.Trigger.EXPLICIT_DETONATION);
        double radius=execution.burstRadiusBlocks().orElseThrow();int hits=0;
        for(var target:player.level().getEntitiesOfClass(LivingEntity.class,defeated.getBoundingBox().inflate(radius),
            e->e!=defeated&&e.distanceToSqr(defeated)<=radius*radius&&PlatformRuntime.canAttack(player,e)&&player.hasLineOfSight(e)))
            if(damageSink.hurt(player,source,target,Element.FUSION,damage,"effect/fusion_burst/nearby_defeat"))hits++;
        if(hits>0)PlatformRuntime.fusionBurstObserved(player,defeated);
        return hits;
    }
    private static List<AppliedMark> convertToAero(ServerPlayer player,CharacterInstance role,LivingEntity target,int duration) {
        // Frozen 3.7 character 1406 node 2: each removed LAYER grants one Aero Erosion layer.
        List<String> ids=List.of("spectro_frazzle","havoc_bane","fusion_burst","glacio_chafe","electro_flare");
        long total=ids.stream().mapToLong(id->layers(target,id)).sum();
        if(total==0)return List.of();
        if(total>1024) {
            warn("aero_conversion_safety_limit","conversion exceeds the server application bound; source layers retained");
            return List.of();
        }
        // Validate/apply the destination first; a refused definition must not destroy the sources.
        // Aero application itself has no application pulse, so no damage callback observes half a conversion.
        List<AppliedMark> applied=applyMark(player,role,target,"aero_erosion",(int)total,duration);
        if(applied.isEmpty())return applied;
        for(String id:ids)consume(target,id,Integer.MAX_VALUE);
        return applied;
    }
    public static void onOwnedLogout(UUID playerId) {
        for(var target:List.copyOf(STASIS.keySet())){var sources=STASIS.get(target);sources.values().removeIf(value->value.player().equals(playerId));if(sources.isEmpty())STASIS.remove(target);}
        for(var entry:List.copyOf(CAP_SOURCES.entrySet())) {entry.getValue().sourceLoggedOut(playerId);refreshCaps(entry.getKey(),entry.getKey().level().getGameTime());}
        for(var entry:List.copyOf(TARGETS.entrySet())) {
            var marks=entry.getValue();marks.entrySet().removeIf(e->e.getValue().source.playerId().equals(playerId)&&attackMark(e.getValue().id));
            for(var mark:marks.values())if(mark.source.playerId().equals(playerId)){mark.stopped=true;if(mark.engine!=null)mark.engine.stopOwnedExecutors(playerId);}
            applySlow(entry.getKey(),marks);
        }
    }
    /** Call on role switch for marks whose source explicitly says they end on exit. */
    public static void clearOwnedMark(UUID playerId,String id) {
        for(var entry:TARGETS.entrySet()) {
            entry.getValue().entrySet().removeIf(mark->mark.getValue().id.equals(id) && mark.getValue().source.playerId().equals(playerId));
            applySlow(entry.getKey(),entry.getValue());
        }
    }
    public static void clearOwnedMark(UUID playerId,UUID weapon,String id) {
        for(var entry:List.copyOf(TARGETS.entrySet())) {
            entry.getValue().entrySet().removeIf(e->e.getValue().id.equals(id)&&e.getValue().source.playerId().equals(playerId)&&e.getValue().source.weaponInstanceId().equals(weapon));
            applySlow(entry.getKey(),entry.getValue());
        }
    }
    /** Both pulse speed and damage query the deployed aura; pulse ownership stays unchanged. */
    static boolean hasCartethyiaDivineAura(ServerPlayer player,LivingEntity target) {
        if(player==null||!player.isAlive()||player.isRemoved()||player.level()!=target.level())return false;
        var team=ModAttachments.team(player).state();
        var deployed=team.active().orElse(null);
        return team.wuwaMode()&&deployed!=null&&deployed.character()==CharacterId.CARTETHYIA
            &&cn.zgnhit.mcwuwa.content.mechanics.CartethyiaAuraRules.divineIntent(
                PlatformRuntime.mechanicsView(player,deployed),deployed.alive(),player.distanceToSqr(target));
    }
    public static void tick(ServerLevel level) {
        long now=level.getGameTime();
        var paused=new HashSet<LivingEntity>();
        for(var map:List.of(TARGETS,TUNE_STATES,CAP_SOURCES,STAT_DEBUFFS,FROZEN,STASIS,FOCUS_SLOW,FROSTBOUND))
            for(Object key:List.copyOf(map.keySet()))if(key instanceof LivingEntity target&&target.level()==level&&CinematicTimeRuntime.pausesEnemy(target))paused.add(target);
        for(var target:paused){
            TARGETS.getOrDefault(target,Map.of()).values().forEach(m->{m.expires=CinematicTimeRules.postpone(m.expires,1);if(m.engine!=null)m.engine.pauseOneTick();});
            if(TUNE_STATES.containsKey(target))TUNE_STATES.get(target).pauseOneTick();
            if(CAP_SOURCES.containsKey(target))CAP_SOURCES.get(target).pauseOneTick();
            if(STAT_DEBUFFS.containsKey(target))STAT_DEBUFFS.get(target).pauseOneTick();
            FROZEN.computeIfPresent(target,(t,end)->CinematicTimeRules.postpone(end,1));FROSTBOUND.computeIfPresent(target,(t,end)->CinematicTimeRules.postpone(end,1));
            if(STASIS.containsKey(target))STASIS.get(target).replaceAll((id,s)->new Stasis(s.player,CinematicTimeRules.postpone(s.expires,1),s.endsOnDamage));
            if(target instanceof Mob mob&&FOCUS_SLOW.containsKey(mob))FOCUS_SLOW.get(mob).replaceAll((id,end)->CinematicTimeRules.postpone(end,1));
        }
        for(var entry:List.copyOf(TUNE_STATES.entrySet()))if(entry.getKey().level()==level) {
            if(!entry.getKey().isAlive()||entry.getKey().isRemoved())cleanup(entry.getKey());else {
                entry.getValue().expire(now);if(entry.getValue().snapshots(now).isEmpty())TUNE_STATES.remove(entry.getKey());
            }
        }
        for(var target:List.copyOf(CAP_SOURCES.keySet()))if(target.level()==level) {
            if(!target.isAlive()||target.isRemoved())cleanup(target);else refreshCaps(target,now);
        }
        for(var entry:List.copyOf(STAT_DEBUFFS.entrySet()))if(entry.getKey().level()==level) {
            if(!entry.getKey().isAlive()||entry.getKey().isRemoved())cleanup(entry.getKey());else {
                entry.getValue().expire(now);if(entry.getValue().isEmpty())STAT_DEBUFFS.remove(entry.getKey());
                applySlow(entry.getKey(),TARGETS.getOrDefault(entry.getKey(),Map.of()));
            }
        }
        for(var entry:List.copyOf(TARGETS.entrySet())) {
            LivingEntity target=entry.getKey();if(target.level()!=level)continue;
            if(paused.contains(target))continue;
            if(!target.isAlive()||target.isRemoved()){cleanup(target);continue;}
            var marks=entry.getValue();
            for(var mark:List.copyOf(marks.values())) {
                if(mark.engine!=null) {
                    var source=level.getServer().getPlayerList().getPlayer(mark.source.playerId());
                    boolean keepElectro=hasElectroCollector(source,target);
                    int aeroDivisor=mark.id.equals("aero_erosion")&&hasCartethyiaDivineAura(source,target)?2:1;
                    var pulses=mark.engine.tick(now,keepElectro,aeroDivisor);
                    if(mark.id.equals("electro_flare")&&!pulses.isEmpty()&&mark.rage>0&&source!=null)PlatformRuntime.electroRageReleased(source,target,mark.rage);
                    pulses.forEach(pulse->dispatch(target,mark,pulse));
                    if(mark.id.equals("spectro_frazzle")&&!pulses.isEmpty()&&source!=null&&PlatformRuntime.phoebeSilentPrayer(source,target))mark.engine.extendNextFrazzleCycle(mark.id);
                    updateFromEngine(mark);
                    if(mark.layers==0)marks.remove(storageKey(mark.id,mark.source.playerId(),mark.source.weaponInstanceId()));
                }
                if(mark.expires<=now)marks.remove(storageKey(mark.id,mark.source.playerId(),mark.source.weaponInstanceId()));
            }
            applySlow(target,marks);if(marks.isEmpty())TARGETS.remove(target);
        }
        for(var entry:List.copyOf(FROZEN.entrySet())) {
            LivingEntity target=entry.getKey();if(target.level()!=level)continue;
            if(entry.getValue()<=now||!target.isAlive()||target.isRemoved())FROZEN.remove(target);
            else if(!EnemyEvents.profile(target).hardControlImmune()) {
                target.setDeltaMovement(0,target.getDeltaMovement().y,0);
                if(target instanceof Mob mob)mob.getNavigation().stop();
            }
        }
        for(var target:List.copyOf(STASIS.keySet()))if(target.level()==level) {
            STASIS.get(target).values().removeIf(value->value.expires()<=now);
            if(!target.isAlive()||target.isRemoved()||STASIS.get(target).isEmpty())STASIS.remove(target);
            else if(STASIS.get(target).keySet().stream().anyMatch(key->stasisAllowed(target,key.group()))){target.setDeltaMovement(0,target.getDeltaMovement().y,0);if(target instanceof Mob mob)mob.getNavigation().stop();}
        }
        for(var target:List.copyOf(FOCUS_SLOW.keySet()))if(target.level()==level){
            var sources=FOCUS_SLOW.get(target);sources.values().removeIf(expires->expires<=now);
            if(!target.isAlive()||target.isRemoved()||sources.isEmpty())FOCUS_SLOW.remove(target);
        }
        for(var entry:List.copyOf(FROSTBOUND.entrySet())) {
            LivingEntity target=entry.getKey();if(target.level()!=level)continue;
            if(entry.getValue()<=now||!target.isAlive()||target.isRemoved())FROSTBOUND.remove(target);
            else if(!EnemyEvents.profile(target).hardControlImmune()){target.setDeltaMovement(0,target.getDeltaMovement().y,0);if(target instanceof Mob mob)mob.getNavigation().stop();}
        }
    }
    private static void updateFromEngine(Mark mark) {
        var snapshot=mark.engine.snapshot(mark.id);
        mark.layers=snapshot.map(ElementalEffects.Snapshot::layers).orElse(0);
        mark.rage=snapshot.map(ElementalEffects.Snapshot::electroRage).orElse(0);
        snapshot.ifPresent(value->{mark.source=value.firstSource();mark.cap=OptionalInt.of(value.cap());mark.expires=value.expiresAt();mark.stopped=value.executorStopped();});
    }
    private static void dispatch(LivingEntity target,Mark mark,ElementalEffects.Pulse pulse) {
        if(pulse.nativePreDefenseDamage()<=0 || !(target.level() instanceof ServerLevel level))return;
        ServerPlayer source=level.getServer().getPlayerList().getPlayer(pulse.source().playerId());
        if(source==null||source.level()!=level||!source.isAlive())return;
        // A dead/unequipped source cannot be replaced by a newly held weapon.
        boolean owned=false;
        for(int i=0;i<3;i++)if(ModAttachments.team(source).state().at(i).map(role->role.instanceId().equals(pulse.source().weaponInstanceId())&&role.alive()).orElse(false))owned=true;
        if(!owned)return;
        Element element=EffectDefinitions.find(mark.id).orElseThrow().element();
        boolean accepted=false;
        if(pulse.trigger()==ElementalEffects.Trigger.THRESHOLD_BURST && mark.execution.burstRadiusBlocks().isPresent()) {
            double radius=mark.execution.burstRadiusBlocks().getAsDouble();
            Set<UUID> hit=new HashSet<>();
            for(LivingEntity nearby:level.getEntitiesOfClass(LivingEntity.class,target.getBoundingBox().inflate(radius),entity->entity.distanceToSqr(target)<=radius*radius && PlatformRuntime.canAttack(source,entity)))
                if(hit.add(PlatformRuntime.combatTargetId(nearby)))accepted|=damageSink.hurt(source,pulse.source(),nearby,element,pulse.nativePreDefenseDamage(),"effect/"+mark.id+"/"+pulse.trigger().name().toLowerCase(Locale.ROOT));
        } else if(PlatformRuntime.canAttack(source,target))accepted=damageSink.hurt(source,pulse.source(),target,element,pulse.nativePreDefenseDamage(),"effect/"+mark.id+"/"+pulse.trigger().name().toLowerCase(Locale.ROOT));
        if(accepted&&mark.id.equals("fusion_burst"))PlatformRuntime.fusionBurstObserved(source,target);
    }
    private static void applySlow(LivingEntity target,Map<String,Mark> marks) {
        var attack=target.getAttribute(Attributes.ATTACK_DAMAGE);
        if(attack!=null) {
            boolean weak=attackStatMultiplier(target)<1;
            double value=attackStatMultiplier(target)-1;
            if(weak&&(!attack.hasModifier(COLOR_WEAKNESS)||attack.getModifier(COLOR_WEAKNESS).amount()!=value)){attack.removeModifier(COLOR_WEAKNESS);attack.addTransientModifier(new AttributeModifier(COLOR_WEAKNESS,value,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));}
            else if(!weak&&attack.hasModifier(COLOR_WEAKNESS))attack.removeModifier(COLOR_WEAKNESS);
        }
        var attribute=target.getAttribute(Attributes.MOVEMENT_SPEED);if(attribute==null)return;
        attribute.removeModifier(SLOW);
        Mark mark=marks.get("glacio_chafe"),frost=marks.get("glacio_frost");
        if(frost!=null&&(mark==null||frost.layers>mark.layers))mark=frost;
        if(mark!=null&&mark.execution!=null&&mark.execution.slowPerLayer().isPresent()) {
            double slow=mark.execution.sourceVerified()?Math.min(1,mark.layers*mark.execution.slowPerLayer().getAsDouble()):GlobalCombatRules.slow(mark.layers);
            if(slow>0)attribute.addTransientModifier(new AttributeModifier(SLOW,-slow,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
    public static void cleanup(LivingEntity target) {
        TARGETS.remove(target);FROZEN.remove(target);STASIS.remove(target);FOCUS_SLOW.remove(target);FROSTBOUND.remove(target);CAP_OVERRIDES.remove(target);CAP_SOURCES.remove(target);TUNE_STATES.remove(target);STAT_DEBUFFS.remove(target);applySlow(target,Map.of());
    }
    public static void clearTarget(LivingEntity target) { cleanup(target); }
    public static void clear() {
        for(var target:List.copyOf(TARGETS.keySet()))cleanup(target);
        for(var target:List.copyOf(STAT_DEBUFFS.keySet()))cleanup(target);
        FROZEN.clear();STASIS.clear();FOCUS_SLOW.clear();FROSTBOUND.clear();CAP_OVERRIDES.clear();CAP_SOURCES.clear();TUNE_STATES.clear();STAT_DEBUFFS.clear();WARNED.clear();
    }
    private static void warn(String id,String reason) { if(WARNED.add(id))McWuwa.LOGGER.warn("Effect {}: {}",id,reason); }
    private static boolean shared(String id){return id.equals("frostbind")||EffectDefinitions.find(id).isPresent();}
    public static boolean hasFrostConverter(ServerPlayer player) {
        var team=ModAttachments.team(player).state();for(int slot=0;slot<3;slot++)if(team.at(slot).filter(r->r.character()==CharacterId.HIYUKI).isPresent())return true;
        return false;
    }
    /** Team entry/leave cleanup stays scoped to this player's rounds and preserves other players. */
    public static void frostTeamChanged(ServerPlayer player,boolean nowPresent) {
        String clear=nowPresent?"glacio_chafe":"glacio_frost";
        for(var entry:List.copyOf(TARGETS.entrySet())) {
            entry.getValue().entrySet().removeIf(e->e.getValue().id.equals(clear)&&e.getValue().source.playerId().equals(player.getUUID()));
            applySlow(entry.getKey(),entry.getValue());
        }
    }
    private static boolean attackMark(String id){return Set.of("yinlin_sin","yinlin_punishment","chisa_void_scar","mornye_observation","mornye_interference").contains(id);}
    private static String storageKey(String id,UUID owner,UUID weapon) {
        return shared(id)?id:id+"|"+owner+"|"+weapon;
    }
}
