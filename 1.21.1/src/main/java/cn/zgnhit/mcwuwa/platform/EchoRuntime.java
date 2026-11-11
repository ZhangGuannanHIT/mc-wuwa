package cn.zgnhit.mcwuwa.platform;

import cn.zgnhit.mcwuwa.McWuwa;
import cn.zgnhit.mcwuwa.content.CharacterDefinition;
import cn.zgnhit.mcwuwa.content.EchoAbilityCatalog;
import cn.zgnhit.mcwuwa.core.CharacterInstance;
import cn.zgnhit.mcwuwa.platform.entity.ConstructEntity;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Fixed main-slot echo actives. Native rates and cooldowns are frozen; collision/time are authored.
 * Each field retains the actual casting weapon UUID and world, including after switching away.
 */
public final class EchoRuntime {
    private record Buff(UUID owner, UUID recipient, String attribute, double value, long expires) { }
    private static final class Run {
        final UUID owner; final EchoAbilityCatalog.Ability ability; long start; final ServerLevel world;
        final Vec3 origin, direction, horizontalDirection; final boolean castAirborne; final Set<String> ledger = new HashSet<>();
        boolean heronEnergy; boolean heronHeld; boolean fistsHeld; boolean riderHeld;boolean riderFinisherStarted;boolean riderFinisherHit;boolean mothHeld;boolean armamentDive;long riderRelease=-1;long fistsRelease=-1; ConstructEntity visual;
        Run(UUID owner,EchoAbilityCatalog.Ability ability,long start,ServerPlayer player) {
            this.owner=owner;this.ability=ability;this.start=start;world=player.serverLevel();origin=player.position();castAirborne=!TraversalMotion.solidSupport(player);
            // Explorer rushes along the ground. Looking down at a nearby target
            // must not send its foot-height sweep into the floor.
            horizontalDirection=Vec3.directionFromRotation(0,player.getYRot());
            direction=ability.id()==6000192?horizontalDirection:player.getLookAngle();
        }
    }
    private static final class Field {
        final UUID owner; final int echo; long expires; int charges; long ready;
        final List<ConstructEntity> blades=new ArrayList<>();
        Field(UUID owner,int echo,long expires,int charges) {this.owner=owner;this.echo=echo;this.expires=expires;this.charges=charges;}
    }
    private static final class JueMark {
        final UUID owner; final LivingEntity target; long expires,next;
        JueMark(UUID owner,LivingEntity target,long now){this.owner=owner;this.target=target;expires=now+300;next=now+20;}
    }
    private static final class Session {
        final List<Run> runs=new ArrayList<>();final List<Field> fields=new ArrayList<>();final List<Buff> buffs=new ArrayList<>();final List<JueMark> jue=new ArrayList<>();
        final Map<UUID,Long> blessings=new HashMap<>(),outroWindows=new HashMap<>(),crownBonus=new HashMap<>();
        final List<ConstructEntity> visuals=new ArrayList<>();
        final Map<UUID,Integer> outroEchoes=new HashMap<>();
        final Map<UUID,Double> armamentPower=new HashMap<>();
        final Set<UUID> armamentReady=new HashSet<>();
        final Map<UUID,String> armamentLastCast=new HashMap<>();
        long sequence;
    }
    private static final Map<ServerPlayer,Session> SESSIONS=new WeakHashMap<>();
    private static final ThreadLocal<Boolean> PROC=ThreadLocal.withInitial(()->false);
    private EchoRuntime() { }
    private static Session session(ServerPlayer player){return SESSIONS.computeIfAbsent(player,p->new Session());}

    public static int effectiveEchoId(CharacterInstance role) {
        var definition=McWuwa.catalog().requireByWeapon(role.character().weaponPath());
        return cn.zgnhit.mcwuwa.content.mechanics.SonataPresetRules.echoId(definition.characterId(),role.selectedMode(),definition.echoId());
    }
    public static boolean cast(ServerPlayer player) { return cast(player, false); }
    /** Held variants keep their source-specific release and resource conditions. */
    public static boolean cast(ServerPlayer player, boolean held) {
        var team=ModAttachments.team(player);if(!team.state().canAct()||PlatformRuntime.returnCharging(player)||PlatformRuntime.ghostActive(player))return false;
        var active=team.state().active();if(active.isEmpty())return false;var role=active.get();
        ItemStack weapon=stack(player,role);if(!mainEnabled(weapon)){message(player,"第一声骸槽需要装备合法声骸。");return false;}
        Session s=session(player);if(s.runs.stream().anyMatch(r->r.owner.equals(role.instanceId()) && r.ability.transformation()))return false;
        int id=effectiveEchoId(role);var ability=EchoAbilityCatalog.require(id);
        boolean armamentDive=id==6000083&&s.armamentReady.contains(role.instanceId());
        if(held&&id==6000198)ability=new EchoAbilityCatalog.Ability(id,500,1,true,80,8,-1,
            java.util.stream.IntStream.range(0,12).mapToObj(i->new EchoAbilityCatalog.Hit("20013402",6+i*6)).toList(),true,"MC held pulse cadence6ticks, exactly12 source hit cap");
        if(held&&id==6000091)ability=new EchoAbilityCatalog.Ability(id,500,1,true,200,7,0,List.of(),true,"MC held riding envelope200ticks; release emits source finishing attack");
        if(armamentDive)ability=new EchoAbilityCatalog.Ability(id,500,1,true,40,8,0,List.of(new EchoAbilityCatalog.Hit("28001402",26)),true,"MC enhanced ascent/dive timing; source row unchanged");
        if(held&&id==6000052) {
            List<EchoAbilityCatalog.Hit> fire=new ArrayList<>();for(int tick=10;tick<196;tick+=5)fire.add(new EchoAbilityCatalog.Hit("28001001",tick));
            ability=new EchoAbilityCatalog.Ability(id,400,1,true,200,8,.0,fire,true,"minecraft_authored_approximation: held max200ticks, pulse5ticks; original cadence unverified");
        }
        if(held&&id==6000060) {
            ability=new EchoAbilityCatalog.Ability(id,400,1,false,200,5,-1,List.of(new EchoAbilityCatalog.Hit("280013010",6)),true,"MC initial presentation200ticks, flurry5ticks, stamina2/pulse; hold ends on release or exhaustion; source HP coefficients");
        }
        if(ability.charges()>1) {
            int charges=charges(weapon,ability.charges());if(charges<=0){message(player,"声骸使用次数正在恢复。");return false;}
            setCharges(weapon,charges-1);if(role.cooldown("echo_recharge")==0)role.setCooldown("echo_recharge",ability.cooldownTicks());
        } else {
            if(role.cooldown("echo_active")>0){message(player,"声骸技能冷却中。");return false;}
            role.setCooldown("echo_active",ability.cooldownTicks());
        }
        if(player.getVehicle() instanceof cn.zgnhit.mcwuwa.platform.entity.SurveyMotorcycleEntity)player.stopRiding();
        long now=PlatformRuntime.time(player);var run=new Run(role.instanceId(),ability,now,player);run.fistsHeld=held&&id==6000060;
        run.riderHeld=held&&id==6000091;run.mothHeld=held&&id==6000198;run.heronHeld=held&&id==6000052;run.armamentDive=armamentDive;
        if(armamentDive){s.armamentReady.remove(role.instanceId());s.armamentPower.remove(role.instanceId());}
        s.runs.add(run);
        if(run.heronHeld&&!player.isInWater()&&!player.isPassenger()&&!player.getAbilities().flying&&!player.isFallFlying()){
            // Authored MC takeoff; the sustained hold uses the shared aerial controller.
            if(TraversalMotion.solidSupport(player))player.move(net.minecraft.world.entity.MoverType.SELF,new Vec3(0,.12,0));
            player.setOnGround(false);var velocity=player.getDeltaMovement();
            player.setDeltaMovement(velocity.x,Math.max(.45,velocity.y),velocity.z);player.hurtMarked=true;
        }
        PlatformRuntime.acceptedEchoCast(player,role,id);
        if(ability.transformation())role.beginAction("echo_transform");
        String slug=McWuwa.catalog().requireByWeapon(role.character().weaponPath()).characterId();
        String transform=switch(id){case 6000082->"echo_lorelei_pixel";case 6000052->"echo_heron";case 6000090->"echo_crownless";case 6000115->"echo_hecate";case 6000190->"echo_furnace";
            case 6000221->"echo_calamity_effigy";case 6000083->"echo_sentry_construct";case 6000198->"echo_voidwing_moth";case 6000091->"echo_nightmare_rider_pixel";case 6000121->"echo_false_sovereign";default->"";};
        String presentation=run.riderHeld?"/echo_riding":run.heronHeld?"/echo_heron_breath":"/echo";
        Network.visual(player,new Network.Visual(player.getId(),role.instanceId(),"mc_wuwa:"+slug+presentation,now,ability.durationTicks(),++s.sequence,transform));
        if(!ability.transformation()) {
            String model=switch(id){case 6000092->"echo_nightmare_aix";case 6000082->"echo_lorelei_pixel";case 6000060->"echo_fallacy_fists";case 6000192->"echo_nameless_explorer";case 6000059->"jue_dragon";case 6000106->"echo_fleurdelys";case 6000167->"echo_leviathan";
                case 6000116->"fenrico_claw";case 6000189->"echo_hyvatia";case 6000191->"echo_sigillum";case 6000199->"echo_forged_god";case 6000218->"";case 6000225->"echo_tianyan";
                case 6000104->"echo_capitaneus";case 6000195->"echo_gromos";case 6000224->"echo_soulfrayer";case 6000160->"echo_sea_maiden_twin_vortex";case 6000114->"echo_lion_halberd_field";case 6000200->"echo_denia_trickster";default->"";};
            if(!model.isEmpty()){run.visual=ConstructEntity.spawn(player.serverLevel(),player,role.instanceId(),model,
                    id==6000060?player.position():id==6000199||id==6000104||id==6000195?groundedSummonPosition(player):
                        id==6000160||id==6000114?player.position():id==6000200?player.position().add(Vec3.directionFromRotation(0,player.getYRot()).scale(1.5)):
                        player.position().add(player.getLookAngle().scale(2)).add(0,1,0),
                    id==6000167||id==6000218?300:ability.durationTicks(),id==6000160?1.8f:id==6000114?2f:id==6000200?.7f:1f,id==6000060,"attack");s.visuals.add(run.visual);}
        }
        switch(id) {
            case 6000200,6000198,6000195,6000224 -> {s.outroWindows.put(role.instanceId(),now+300);s.outroEchoes.put(role.instanceId(),id);}
            case 6000091 -> {if(!held){if(TraversalMotion.solidSupport(player))player.move(net.minecraft.world.entity.MoverType.SELF,new Vec3(0,.12,0));player.setOnGround(false);var velocity=player.getDeltaMovement();player.setDeltaMovement(velocity.x,Math.max(.44,velocity.y),velocity.z);player.hurtMarked=true;}}
            case 6000083 -> {if(armamentDive){if(TraversalMotion.solidSupport(player))player.move(net.minecraft.world.entity.MoverType.SELF,new Vec3(0,.12,0));player.setOnGround(false);var velocity=player.getDeltaMovement();player.setDeltaMovement(velocity.x,.56,velocity.z);player.hurtMarked=true;}}
            case 6000060 -> {
                s.buffs.removeIf(b->b.recipient.equals(role.instanceId())&&b.attribute.equals("energy_regen"));
                s.buffs.add(new Buff(role.instanceId(),role.instanceId(),"energy_regen",.10,now+400));
                for(int slot=0;slot<3;slot++){var member=team.state().at(slot).orElse(null);if(member==null)continue;
                    s.buffs.removeIf(b->b.recipient.equals(member.instanceId())&&b.attribute.equals("attack_pct"));
                    s.buffs.add(new Buff(role.instanceId(),member.instanceId(),"attack_pct",.10,now+400));}
            }
            case 6000059 -> s.blessings.put(role.instanceId(),now+300);
            case 6000189 -> s.outroWindows.put(role.instanceId(),now+300);
            case 6000167 -> {s.fields.removeIf(f->f.owner.equals(role.instanceId())&&f.echo==id);s.fields.add(new Field(role.instanceId(),id,now+300,8));}
            case 6000218 -> {
                s.fields.removeIf(f->{if(!f.owner.equals(role.instanceId())||f.echo!=id)return false;discardBlades(f);return true;});
                var field=new Field(role.instanceId(),id,now+300,4);s.fields.add(field);
                for(int i=0;i<4;i++){
                    var blade=ConstructEntity.spawn(player.serverLevel(),player,role.instanceId(),"echo_puppet_memory_sword",memoryBladePosition(player,i),300,.8f,false,"idle");
                    field.blades.add(blade);s.visuals.add(blade);
                }
            }
            default -> { }
        }
        return true;
    }

    public static void tick(ServerPlayer player) {
        var team=ModAttachments.team(player);Session s=session(player);long now=PlatformRuntime.time(player);
        if(CinematicTimeRuntime.paused(player)){
            s.runs.forEach(r->r.start++);s.fields.forEach(f->{f.expires++;if(f.ready>0)f.ready++;});
            s.buffs.replaceAll(b->new Buff(b.owner,b.recipient,b.attribute,b.value,cn.zgnhit.mcwuwa.core.CinematicTimeRules.postpone(b.expires,1)));
            s.jue.forEach(j->{j.expires++;j.next++;});
            for(var map:List.of(s.blessings,s.outroWindows,s.crownBonus))map.replaceAll((id,t)->cn.zgnhit.mcwuwa.core.CinematicTimeRules.postpone(t,1));
            return;
        }
        for(int slot=0;slot<3;slot++) {
            var candidate=team.state().at(slot);if(candidate.isEmpty())continue;var role=candidate.get();ItemStack weapon=team.inventory().getItem(slot);
            var ability=EchoAbilityCatalog.require(effectiveEchoId(role));
            if(ability.charges()>1 && charges(weapon,ability.charges())<ability.charges() && role.cooldown("echo_recharge")==0) {
                int count=charges(weapon,ability.charges())+1;setCharges(weapon,count);
                if(count<ability.charges())role.setCooldown("echo_recharge",ability.cooldownTicks());
            }
        }
        for(var iterator=s.runs.iterator();iterator.hasNext();) {
            Run run=iterator.next();CharacterInstance owner=role(player,run.owner);
            if(owner==null||!owner.alive()||run.world!=player.level()||!mainEnabled(stack(player,owner))) {restoreVisual(player,run);iterator.remove();continue;}
            long age=now-run.start;
            if(java.util.Set.of(6000200,6000198,6000160,6000091,6000221,6000114,6000083).contains(run.ability.id())&&age%3==0)newEchoFeedback(player,run,age);
            if(run.riderHeld) {
                boolean sameActive=team.state().active().map(a->a.instanceId().equals(run.owner)).orElse(false);
                if(!sameActive){restoreVisual(player,run);iterator.remove();continue;}
                if(run.riderRelease<0&&age>=run.ability.durationTicks()-2)run.riderRelease=age;
                if(run.riderRelease>=0) {
                    if(!run.riderFinisherStarted){
                        run.riderFinisherStarted=true;
                        String slug=McWuwa.catalog().requireByWeapon(owner.character().weaponPath()).characterId();
                        Network.visual(player,new Network.Visual(player.getId(),run.owner,"mc_wuwa:"+slug+"/echo_rider_finish",now,16,++s.sequence,"echo_nightmare_rider_pixel"));
                        player.setDeltaMovement(0,player.getDeltaMovement().y,0);player.hurtMarked=true;
                    }
                    long endingAge=age-run.riderRelease;
                    if(!run.riderFinisherHit&&endingAge>=8){
                        run.riderFinisherHit=true;
                        for(var target:targets(player,run))nativeProc(player,owner,target,EchoAbilityCatalog.damage(McWuwa.catalog(),6000091,"28002302"),0,PlatformRuntime.HitOrigin.DIRECT_ACTIVE);
                        echoBurst(player,player.position().add(player.getLookAngle().scale(2)),true);
                    }
                    if(endingAge>=16){restoreVisual(player,run);iterator.remove();}
                    continue;
                }
                var input=cn.zgnhit.mcwuwa.platform.MovementKeys.get(player);var motion=TraversalMotion.horizontal(player,(input.left()?1:0)-(input.right()?1:0),(input.backward()?-1:1),.32);
                player.setDeltaMovement(motion.x,player.getDeltaMovement().y,motion.z);player.hurtMarked=true;
            }
            if(run.armamentDive&&age>=18&&age<27) {
                var motion=TraversalMotion.horizontal(player,0,1,.24);player.setDeltaMovement(motion.x,-.75,motion.z);player.fallDistance=0;player.hurtMarked=true;
            }
            if(run.fistsHeld){
                if(age>=8&&(age==8||run.visual==null||run.visual.isRemoved())){
                    if(run.visual!=null)run.visual.discard();
                    run.visual=ConstructEntity.spawn(player.serverLevel(),player,run.owner,"echo_fallacy_fists",player.position(),200,1f,true,"breath");s.visuals.add(run.visual);
                }
                boolean sameActive=team.state().active().map(a->a.instanceId().equals(run.owner)).orElse(false);
                if(!sameActive){restoreVisual(player,run);iterator.remove();continue;}
                if(run.fistsRelease<0&&age>=10&&age%5==0&&!team.state().stamina().spend(2))run.fistsRelease=Math.max(7,age);
                if(run.fistsRelease>=0&&age>=run.fistsRelease){
                    for(var target:targets(player,run))nativeProc(player,owner,target,EchoAbilityCatalog.damage(McWuwa.catalog(),6000060,"280013030"),0);
                    if(run.visual!=null)run.visual.discard();
                    s.visuals.add(ConstructEntity.spawn(player.serverLevel(),player,run.owner,"echo_fallacy_fists",player.position(),8,1f,true,"cleave"));
                    echoBurst(player,player.position().add(player.getLookAngle().scale(2)),true);iterator.remove();continue;
                }
                if(age>=10&&age%5==0&&run.fistsRelease<0){echoBurst(player,player.position().add(player.getLookAngle().scale(2)),false);
                    for(var target:targets(player,run))nativeProc(player,owner,target,EchoAbilityCatalog.damage(McWuwa.catalog(),6000060,"280013020"),0);}
            }
            if(!run.fistsHeld&&age>=run.ability.durationTicks()) {restoreVisual(player,run);iterator.remove();continue;}
            if(run.ability.transformation()&&team.state().active().map(a->!a.instanceId().equals(run.owner)).orElse(true)) {restoreVisual(player,run);iterator.remove();continue;}
            if(run.ability.id()==6000192&&age>=4){
                if(age>=11){if(run.visual!=null)run.visual.discard();iterator.remove();continue;}
                double progress=Math.min(14,(age-4)*2.4);Vec3 end=run.origin.add(run.direction.scale(progress));
                var wall=run.world.clip(new ClipContext(run.origin.add(0,1,0),end.add(0,1,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
                if(wall.getType()==HitResult.Type.BLOCK)end=wall.getLocation().add(0,-1,0);
                if(run.visual!=null)run.visual.setPos(end);
                echoBurst(player,end,false);
                for(var target:run.world.getEntitiesOfClass(LivingEntity.class,new AABB(end.x-1.5,end.y-1,end.z-1.5,end.x+1.5,end.y+3,end.z+1.5),e->PlatformRuntime.canAttack(player,e)&&player.hasLineOfSight(e)))
                    if(run.ledger.add("explorer:"+PlatformRuntime.combatTargetId(target)))nativeProc(player,owner,target,EchoAbilityCatalog.damage(McWuwa.catalog(),6000192,"28003801"),0);
                continue;
            }
            for(int i=0;i<run.ability.hits().size();i++) {
                var event=run.ability.hits().get(i);if(age<event.tick()||!run.ledger.add("event:"+i))continue;
                var hit=EchoAbilityCatalog.damage(McWuwa.catalog(),run.ability.id(),event.damageId());
                if(run.ability.id()==6000224)arrowFeedback(player,run);
                if(run.ability.id()==6000195)stompFeedback(player,run);
                double bonus=run.ability.id()==6000090&&s.crownBonus.getOrDefault(run.owner,0L)>now?.20:0;
                for(LivingEntity target:targets(player,run)) {
                    if(!run.ledger.add(i+":"+PlatformRuntime.combatTargetId(target)))continue;
                    if(PlatformRuntime.nativeHit(player,owner,target,hit,bonus,run.ability.id()==6000092&&EffectRuntime.hasEffect(target,"spectro_frazzle")?2:1,"echo/"+run.ability.id(),run.ability.transformation()?PlatformRuntime.HitOrigin.DIRECT_ACTIVE:PlatformRuntime.HitOrigin.AUTONOMOUS)) {
                        if(run.ability.id()==6000052&&!run.heronEnergy){owner.gain("energy",10);run.heronEnergy=true;s.outroWindows.put(run.owner,now+300);}
                        if(run.ability.id()==6000090)s.crownBonus.put(run.owner,now+40);
                        if(run.armamentDive)EffectRuntime.applyStasis(player,owner,target,20); // Source buff 280014016: one second.

                    }
                }
            }
        }
        for(var iterator=s.jue.iterator();iterator.hasNext();) {
            JueMark mark=iterator.next();CharacterInstance owner=role(player,mark.owner);
            if(owner==null||!owner.alive()||now>mark.expires||!mark.target.isAlive()||mark.target.level()!=player.level()){iterator.remove();continue;}
            if(now>=mark.next){mark.next=now+20;nativeProc(player,owner,mark.target,EchoAbilityCatalog.damage(McWuwa.catalog(),6000059,"28001202"),0,PlatformRuntime.HitOrigin.PERIODIC);}
            // A complete fifteenth one-second interval is paid before expiry.
            if(now>=mark.expires)iterator.remove();
        }
        s.fields.removeIf(f->{if(f.expires>now&&f.charges>0&&sourceAlive(player,f.owner))return false;discardBlades(f);return true;});
        for(Field field:s.fields)if(field.echo==6000218)for(int i=0;i<field.charges;i++){
            var blade=field.blades.get(i);if(!blade.isRemoved()){blade.setPos(memoryBladePosition(player,i));blade.setYRot(player.getYRot());}
        }
        s.buffs.removeIf(b->b.expires<=now||!sourceAlive(player,b.owner)||!sourceAlive(player,b.recipient));
        for(var timers:List.of(s.blessings,s.outroWindows,s.crownBonus))
            timers.entrySet().removeIf(entry->entry.getValue()<=now||!sourceAlive(player,entry.getKey()));
        s.outroEchoes.keySet().removeIf(owner->!s.outroWindows.containsKey(owner));
        s.armamentPower.keySet().removeIf(owner->!sourceAlive(player,owner));s.armamentReady.removeIf(owner->!sourceAlive(player,owner));s.armamentLastCast.keySet().removeIf(owner->!sourceAlive(player,owner));
        s.visuals.removeIf(ConstructEntity::isRemoved);
    }

    /** Invoke for confirmed non-echo damage. Internal DoT/proc hits cannot recursively retrigger this class. */
    public static void onHit(ServerPlayer player,CharacterInstance attacker,LivingEntity target,String kind) {
        if(PROC.get()||kind.equals("echo")||!PlatformRuntime.canAttack(player,target))return;
        Session s=session(player);long now=PlatformRuntime.time(player);
        if(kind.equals("skill")&&s.blessings.getOrDefault(attacker.instanceId(),0L)>now) {
            var old=s.jue.stream().filter(m->m.owner.equals(attacker.instanceId())&&m.target==target).findFirst();
            if(old.isPresent())old.get().expires=now+300;else s.jue.add(new JueMark(attacker.instanceId(),target,now));
        }
        if(ModAttachments.team(player).state().active().map(a->a.instanceId().equals(attacker.instanceId())).orElse(false)) {
            for(Field field:s.fields)if(field.echo==6000167&&field.expires>now&&field.charges>0&&now>=field.ready) {
                CharacterInstance owner=role(player,field.owner);if(owner==null||!owner.alive())continue;
                var damage=EchoAbilityCatalog.describedProc(McWuwa.catalog(),6000167,2,"havoc","echo");
                field.charges--;field.ready=now+10;
                nativeProc(player,owner,target,damage,EffectRuntime.hasEffect(target,"havoc_bane")?1.0:0);
            }
        }
    }

    /** Required event hook: a hit does not prove that an anomaly was actually applied. */
    public static void onEffectApplied(ServerPlayer player,CharacterInstance owner,LivingEntity target,String effectId) {
        if(PROC.get())return;Session s=session(player);long now=PlatformRuntime.time(player);
        if(java.util.Set.of("tune_offset","strain_shifting").contains(effectId)&&mainEnabled(stack(player,owner))&&effectiveEchoId(owner)==6000221) {
            s.buffs.removeIf(b->b.recipient.equals(owner.instanceId())&&b.attribute.equals("aero_bonus"));s.buffs.add(new Buff(owner.instanceId(),owner.instanceId(),"aero_bonus",.10,now+300));
        }
        if(effectId.equals("havoc_bane"))for(Field field:s.fields) {
            if(field.echo==6000218&&field.owner.equals(owner.instanceId())&&field.expires>now&&field.charges>0&&field.ready<=now) {
                field.charges--;field.ready=now+20;
                if(field.charges<field.blades.size())field.blades.get(field.charges).discard();
                s.visuals.add(ConstructEntity.spawn(player.serverLevel(),player,owner.instanceId(),"echo_puppet_memory_sword",target.position().add(0,.8,0),8,.8f,false,"attack"));
                nativeProc(player,owner,target,EchoAbilityCatalog.damage(McWuwa.catalog(),6000218,"28004301"),0);
            }
        }
        if(effectId.equals("electro_flare")&&mainEnabled(stack(player,owner))&&McWuwa.catalog().requireByWeapon(owner.character().weaponPath()).echoId()==6000225) {
            s.buffs.removeIf(b->b.recipient.equals(owner.instanceId())&&b.attribute.equals("electro_bonus"));s.buffs.add(new Buff(owner.instanceId(),owner.instanceId(),"electro_bonus",.10,now+600));
        }
    }
    public static void onUnison(ServerPlayer player,CharacterInstance owner) {
        if(mainEnabled(stack(player,owner))&&McWuwa.catalog().requireByWeapon(owner.character().weaponPath()).echoId()==6000225) {
            Session s=session(player);s.buffs.removeIf(b->b.recipient.equals(owner.instanceId())&&b.attribute.equals("electro_bonus"));s.buffs.add(new Buff(owner.instanceId(),owner.instanceId(),"electro_bonus",.10,PlatformRuntime.time(player)+600));
        }
    }

    /** Switching ends transformations and held-body input; independent summons retain their lifetime. */
    public static void onSwitch(ServerPlayer player,CharacterInstance outgoing,boolean introOutro) {
        if(outgoing==null)return;
        Session s=session(player);long now=PlatformRuntime.time(player);
        s.runs.removeIf(run->{if(run.owner.equals(outgoing.instanceId())&&(run.ability.transformation()||run.fistsHeld)){restoreVisual(player,run);return true;}return false;});
        if(!introOutro||s.outroWindows.getOrDefault(outgoing.instanceId(),0L)<=now)return;
        int id=s.outroEchoes.getOrDefault(outgoing.instanceId(),effectiveEchoId(outgoing));double bonus=java.util.Set.of(6000052,6000200,6000198,6000195,6000224).contains(id)?.12:id==6000189?.10:0;
        if(bonus==0)return;
        ModAttachments.team(player).state().active().ifPresent(next->{
            s.buffs.removeIf(b->b.recipient.equals(next.instanceId())&&b.attribute.equals("echo_outro_"+id));
            s.buffs.add(new Buff(outgoing.instanceId(),next.instanceId(),"echo_outro_"+id,bonus,now+300));
        });
        s.outroWindows.remove(outgoing.instanceId());s.outroEchoes.remove(outgoing.instanceId());
    }

    public static Map<String,Double> mainModifiers(CharacterInstance owner,ItemStack weapon) {
        if(!mainEnabled(weapon))return Map.of();var data=McWuwa.catalog().requireByWeapon(owner.character().weaponPath());
        return switch(effectiveEchoId(owner)) {
            case 6000121 -> Map.of("electro_bonus",.12,"heavy_bonus",.12);
            case 6000104 -> Map.of("spectro_bonus",.12,"heavy_bonus",.12);
            case 6000091 -> Map.of("fusion_bonus",.12,"skill_bonus",.12);
            case 6000160 -> Map.of("aero_bonus",.12,"liberation_bonus",.12);
            case 6000114 -> Map.of("fusion_bonus",.12,"liberation_bonus",.12);
            case 6000083 -> Map.of("glacio_bonus",.12,"skill_bonus",.12);
            case 6000221 -> Map.of("aero_bonus",.10);
            case 6000192 -> Map.of("aero_bonus",.12,"echo_bonus",.20);
            case 6000090 -> Map.of("havoc_bonus",.12,"basic_bonus",.12);
            case 6000116 -> Map.of("aero_bonus",.12,"heavy_bonus",.12);
            case 6000106 -> Map.of("aero_bonus",data.characterId().equals("aero_rover")||data.characterId().equals("cartethyia")?.20:.10);
            case 6000115 -> Map.of("havoc_bonus",.12,"echo_bonus",.20);
            case 6000167 -> Map.of("havoc_bonus",.12,"liberation_bonus",.12);
            case 6000190 -> Map.of("energy_regen",.10);
            case 6000191 -> data.characterId().equals("aemeath")?Map.of("liberation_bonus",.25):Map.of();
            case 6000199 -> Map.of("glacio_bonus",.12,"liberation_bonus",.12);
            case 6000092 -> Map.of("spectro_bonus",.12);
            case 6000082 -> Map.of("havoc_bonus",.12,"basic_bonus",.12);
            case 6000218 -> Map.of("havoc_bonus",.12,"heavy_bonus",.12);
            case 6000225 -> Map.of("electro_bonus",.10);
            default -> Map.of();
        };
    }
    /** Dynamic source-owned bonuses, to merge once with NativeStats' extra modifier map. */
    public static Map<String,Double> modifiers(ServerPlayer player,CharacterInstance owner) {
        Session s=session(player);long now=PlatformRuntime.time(player);Map<String,Double> result=new HashMap<>();
        if(s.blessings.getOrDefault(owner.instanceId(),0L)>now)result.put("skill_bonus",.16);
        for(Buff buff:s.buffs)if(buff.recipient.equals(owner.instanceId())&&buff.expires>now&&sourceAlive(player,buff.owner))result.merge(switch(buff.attribute){
            case "echo_outro_6000200" -> "fusion_bonus";
            case "echo_outro_6000198" -> "attack_pct";
            case "echo_outro_6000195" -> "glacio_bonus";
            case "echo_outro_6000224" -> "electro_bonus";
            default -> buff.attribute.startsWith("echo_outro_")?"all_damage_bonus":buff.attribute;
        },buff.value,Double::sum);
        return Map.copyOf(result);
    }
    /** Defeat closes temporary echo state synchronously, even if revived this tick.
     * Charges and cooldowns stay on their existing weapon/role owners. */
    public static void onDowned(ServerPlayer player,UUID owner){
        Session s=SESSIONS.get(player);if(s==null)return;
        s.runs.removeIf(run->{if(!run.owner.equals(owner))return false;restoreVisual(player,run);return true;});
        s.fields.removeIf(field->{if(!field.owner.equals(owner))return false;discardBlades(field);return true;});
        s.jue.removeIf(mark->mark.owner.equals(owner));
        s.buffs.removeIf(buff->buff.owner.equals(owner)||buff.recipient.equals(owner));
        s.blessings.remove(owner);s.outroWindows.remove(owner);s.crownBonus.remove(owner);s.outroEchoes.remove(owner);
        s.armamentPower.remove(owner);s.armamentReady.remove(owner);s.armamentLastCast.remove(owner);
    }
    public static void clear(ServerPlayer player) {
        Session s=SESSIONS.get(player);if(s!=null){for(Run run:s.runs)restoreVisual(player,run);for(ConstructEntity visual:s.visuals)visual.discard();}SESSIONS.remove(player);
    }
    public static boolean isTransforming(ServerPlayer player) {
        return session(player).runs.stream().anyMatch(run->run.ability.transformation());
    }
    public static boolean holdsHeronAirCast(ServerPlayer player){
        Session current=SESSIONS.get(player);if(current==null)return false;
        var role=ModAttachments.team(player).state().active().orElse(null);if(role==null||!role.alive())return false;
        long now=PlatformRuntime.time(player);
        return current.runs.stream().anyMatch(run->run.heronHeld&&run.owner.equals(role.instanceId())&&run.world==player.level()
            &&now-run.start>=4&&now-run.start<run.ability.durationTicks());
    }
    /** Official2.2 fix: an airborne Lorelei cast stays aloft during its transformation. */
    public static boolean holdsLoreleiAirCast(ServerPlayer player){
        var current=SESSIONS.get(player);if(current==null)return false;
        var role=ModAttachments.team(player).state().active().orElse(null);if(role==null||!role.alive())return false;
        long now=PlatformRuntime.time(player);
        return current.runs.stream().anyMatch(run->run.owner.equals(role.instanceId())&&run.world==player.level()&&run.ability.id()==6000082
            &&run.castAirborne&&now>=run.start&&now-run.start<run.ability.durationTicks());
    }
    /** Cancel only the active body's echo transformation after a confirmed interrupt.
     * Independent summons, proc fields, buffs, charges and cooldowns retain their own lifetimes.
     */
    public static void interruptTransform(ServerPlayer player) {
        Session s=SESSIONS.get(player);if(s==null)return;
        UUID active=ModAttachments.team(player).state().active().map(CharacterInstance::instanceId).orElse(null);
        if(active==null)return;
        s.runs.removeIf(run->{
            if(run.owner.equals(active)&&run.ability.transformation()){restoreVisual(player,run);return true;}
            return false;
        });
    }
    public static void release(ServerPlayer player) {
        for(var run:session(player).runs)if(run.riderHeld&&run.riderRelease<0)run.riderRelease=PlatformRuntime.time(player)-run.start;
        session(player).runs.removeIf(run->{if(run.mothHeld||run.heronHeld){restoreVisual(player,run);return true;}return false;});
        for(var run:session(player).runs)if(run.fistsHeld&&run.fistsRelease<0)run.fistsRelease=Math.max(7,PlatformRuntime.time(player)-run.start);
    }
    /** Called once for each accepted Resonance Liberation skill cast, including
     * derived Liberation casts triggered through the basic/skill key. Source
     * buffs 280014013/12 add 1000 toward property 68 threshold 6000. */
    public static void onLiberationCast(ServerPlayer player,CharacterInstance owner,String actionInstanceId) {
        if(actionInstanceId==null||actionInstanceId.isBlank()||!owner.alive()
            ||!mainEnabled(stack(player,owner))||effectiveEchoId(owner)!=6000083)return;
        Session s=session(player);
        if(actionInstanceId.equals(s.armamentLastCast.get(owner.instanceId())))return;
        s.armamentLastCast.put(owner.instanceId(),actionInstanceId);
        if(!s.armamentReady.contains(owner.instanceId()))armamentPower(player,owner,1000,6000);
    }
    /** The False Sovereign's Intro summon is independent of its charged active. */
    public static void onIntroCast(ServerPlayer player,CharacterInstance owner) {
        if(!owner.alive()||!mainEnabled(stack(player,owner))||effectiveEchoId(owner)!=6000121)return;
        var ability=new EchoAbilityCatalog.Ability(6000121,0,1,false,24,7,0,
            List.of(new EchoAbilityCatalog.Hit("28003102",12)),true,
            "MC Intro summon contact12ticks; original405% row28003102");
        Session s=session(player);var run=new Run(owner.instanceId(),ability,PlatformRuntime.time(player),player);
        run.visual=ConstructEntity.spawn(player.serverLevel(),player,owner.instanceId(),"echo_false_sovereign",
            groundedSummonPosition(player),ability.durationTicks(),.75f,false,"attack");
        s.runs.add(run);
    }
    /** Exact source-gated meter updates; the host supplies a verified power delta
     * and maximum. A liberation cast is not fabricated as an instant full meter. */
    public static void armamentPower(ServerPlayer player,CharacterInstance owner,double amount,double maximum) {
        if(!Double.isFinite(amount)||amount<0||!Double.isFinite(maximum)||maximum<=0)throw new IllegalArgumentException("armament power");
        if(!owner.alive()||!mainEnabled(stack(player,owner))||effectiveEchoId(owner)!=6000083)return;
        Session s=session(player);double fraction=Math.min(1,s.armamentPower.getOrDefault(owner.instanceId(),0d)+amount/maximum);
        s.armamentPower.put(owner.instanceId(),fraction);
        if(fraction>=1){s.armamentReady.add(owner.instanceId());owner.setCooldown("echo_active",0);}
    }
    public static double armamentProgress(ServerPlayer player,CharacterInstance owner){return session(player).armamentPower.getOrDefault(owner.instanceId(),0d);}
    private static Vec3 heronBreathDirection(ServerPlayer player){
        Vec3 look=player.getLookAngle();
        return new Vec3(look.x,Math.min(-.35,look.y),look.z).normalize();
    }
    private static void newEchoFeedback(ServerPlayer player,Run run,long age) {
        int id=run.ability.id();Vec3 center=run.ability.transformation()?player.position():run.origin;
        int color=switch(id){case 6000160->0x7dcfff;case 6000114->0xf56a48;case 6000091->0xffad61;case 6000200->0xbe8cff;case 6000083->0xaadfff;case 6000221->0x9af3d2;default->0xf3d5ff;};
        if(id==6000160){
            // Publisher3060 Echo GIF5: two rising water vortices. Cosmetic
            // MC spacing only; the server's accepted hit geometry is unchanged.
            Vec3 forward=run.horizontalDirection;
            Vec3 side=new Vec3(-forward.z,0,forward.x);
            double phase=Math.clamp(age/(double)Math.max(1,run.ability.durationTicks()),0,1);
            double envelope=Math.min(1,Math.min(phase*5,(1-phase)*5));
            for(int column:new int[]{-1,1}){
                Vec3 base=center.add(forward.scale(1.2)).add(side.scale(column*1.25));
                for(int i=0;i<18;i++){
                    double height=i/17d*3.2*envelope;
                    double angle=i*.75+age*.24*column;double radius=(.45+.23*Math.sin(i/17d*Math.PI))*envelope;
                    player.serverLevel().sendParticles(new net.minecraft.core.particles.DustParticleOptions(net.minecraft.world.phys.Vec3.fromRGB24(i%3==0?0xe1faff:color).toVector3f(),.65f),
                        base.x+Math.cos(angle)*radius,base.y+.08+height,base.z+Math.sin(angle)*radius,1,0,0,0,0);
                }
            }
        }
        else if(id==6000114){
            for(int i=0;i<10;i++)player.serverLevel().sendParticles(new net.minecraft.core.particles.DustParticleOptions(net.minecraft.world.phys.Vec3.fromRGB24(0xffd4a0).toVector3f(),.7f),center.x,center.y+i*.32,center.z,1,0,0,0,0);
            if(age>=12&&age<=45)for(int lance=0;lance<10;lance++){
                double angle=lance*Math.PI/5;double x=center.x+Math.cos(angle)*2.7,z=center.z+Math.sin(angle)*2.7;
                for(int step=0;step<4;step++)player.serverLevel().sendParticles(new net.minecraft.core.particles.DustParticleOptions(net.minecraft.world.phys.Vec3.fromRGB24(color).toVector3f(),.6f),x,center.y+.1+step*.45,z,1,0,0,0,0);
            }
        }
        else if(id==6000200){
            Vec3 forward=run.horizontalDirection;
            Vec3 side=new Vec3(-forward.z,0,forward.x);Vec3 portal=center.add(forward.scale(1.5)).add(0,1.3,0);
            double phase=age/(double)Math.max(1,run.ability.durationTicks());
            double radius=.85*Math.min(1,Math.max(0,Math.min(phase*6,(1-phase)*5)));
            for(int i=0;i<16;i++){
                double angle=i*Math.PI/8+age*.08;Vec3 at=portal.add(side.scale(Math.cos(angle)*radius)).add(0,Math.sin(angle)*radius,0);
                player.serverLevel().sendParticles(new net.minecraft.core.particles.DustParticleOptions(net.minecraft.world.phys.Vec3.fromRGB24(i%4==0?0xf1d9ff:color).toVector3f(),.65f),at.x,at.y,at.z,1,0,0,0,0);
            }
        }
        else player.serverLevel().sendParticles(new net.minecraft.core.particles.DustParticleOptions(net.minecraft.world.phys.Vec3.fromRGB24(color).toVector3f(),.85f),center.x,center.y+1,center.z,5,.55,.6,.55,0);
    }
    public static boolean mainEnabled(ItemStack weapon) {
        if(weapon.isEmpty())return false;CompoundTag echo=WeaponData.tag(weapon).getCompound("echo_0");int cost=echo.getInt("cost");
        return !echo.getString("species").isEmpty()&&(cost==1||cost==3||cost==4)&&echo.getCompound("main_stats").size()==2&&echo.getCompound("sub_stats").size()==5;
    }
    private static void echoBurst(ServerPlayer player,Vec3 point,boolean finalBlow){
        player.serverLevel().sendParticles(new net.minecraft.core.particles.DustParticleOptions(net.minecraft.world.phys.Vec3.fromRGB24(0x65e6f5).toVector3f(),.9f),point.x,point.y+1,point.z,finalBlow?25:5,.45,.3,.45,0);
        player.serverLevel().sendParticles(new net.minecraft.core.particles.DustParticleOptions(net.minecraft.world.phys.Vec3.fromRGB24(0xe4a4ef).toVector3f(),.7f),point.x,point.y+1,point.z,finalBlow?14:3,.4,.25,.4,0);
        if(finalBlow)player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,point.x,point.y+1,point.z,1,0,0,0,0);
    }
    private static void arrowFeedback(ServerPlayer player,Run run){
        Vec3 start=(run.visual==null?run.origin:run.visual.position()).add(0,1.6,0);
        Vec3 end=targets(player,run).stream().findFirst().map(t->t.position().add(0,t.getBbHeight()*.55,0))
            .orElse(start.add(run.direction.scale(run.ability.rangeBlocks())));
        var wall=run.world.clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        if(wall.getType()==HitResult.Type.BLOCK)end=wall.getLocation();
        Vec3 delta=end.subtract(start);
        for(int i=0;i<=20;i++){
            var p=start.add(delta.scale(i/20d));
            run.world.sendParticles(new net.minecraft.core.particles.DustParticleOptions(net.minecraft.world.phys.Vec3.fromRGB24(i%4==0?0xe9feff:0x74d9e8).toVector3f(),.55f),p.x,p.y,p.z,1,0,0,0,0);
        }
    }
    private static void stompFeedback(ServerPlayer player,Run run){
        Vec3 center=run.visual==null?run.origin:run.visual.position();
        for(int ring=0;ring<3;ring++)for(int i=0;i<40;i++){
            double angle=i*Math.TAU/40,radius=1.1+ring*.8;
            run.world.sendParticles(new net.minecraft.core.particles.DustParticleOptions(net.minecraft.world.phys.Vec3.fromRGB24(ring==1?0xe3f6ff:0x8bbce8).toVector3f(),.7f),
                center.x+Math.cos(angle)*radius,center.y+.1,center.z+Math.sin(angle)*radius,1,0,0,0,0);
        }
    }
    private static List<LivingEntity> targets(ServerPlayer player,Run run) {
        // Transformations replace the moving actor. Independent summons stay
        // at their cast anchor; their target volume must not follow a switch-out.
        boolean followsBody=run.ability.transformation()||run.ability.id()==6000060;
        Vec3 direction=run.heronHeld?heronBreathDirection(player):followsBody?player.getLookAngle():run.direction;
        double range=run.ability.rangeBlocks();Vec3 origin=followsBody?player.position():run.origin;
        AABB box=new AABB(origin.x-range,origin.y-range,origin.z-range,origin.x+range,origin.y+range,origin.z+range);
        return run.world.getEntitiesOfClass(LivingEntity.class,box,target->{
            if(!PlatformRuntime.canAttack(player,target)||!player.hasLineOfSight(target))return false;Vec3 delta=target.position().subtract(origin);
            if(run.ability.id()==6000192){double along=delta.dot(run.direction);return along>=0&&along<=range&&delta.subtract(run.direction.scale(along)).lengthSqr()<=2.25;}
            return delta.lengthSqr()<=range*range&&(delta.lengthSqr()<.01||delta.normalize().dot(direction)>=run.ability.coneDot());
        });
    }
    private static void nativeProc(ServerPlayer player,CharacterInstance owner,LivingEntity target,CharacterDefinition.DamageDefinition damage,double bonus) {
        nativeProc(player,owner,target,damage,bonus,PlatformRuntime.HitOrigin.AUTONOMOUS);
    }
    private static void nativeProc(ServerPlayer player,CharacterInstance owner,LivingEntity target,CharacterDefinition.DamageDefinition damage,double bonus,PlatformRuntime.HitOrigin origin) {
        boolean old=PROC.get();try{PROC.set(true);PlatformRuntime.nativeHit(player,owner,target,damage,bonus,1,"echo/proc",origin);}finally{PROC.set(old);}
    }
    private static CharacterInstance role(ServerPlayer player,UUID id) {
        var team=ModAttachments.team(player).state();for(int slot=0;slot<3;slot++){var c=team.at(slot);if(c.isPresent()&&c.get().instanceId().equals(id))return c.get();}return null;
    }
    private static boolean sourceAlive(ServerPlayer player,UUID id) {
        CharacterInstance owner=role(player,id);
        return player.isAlive()&&owner!=null&&owner.alive();
    }
    private static ItemStack stack(ServerPlayer player,CharacterInstance role) {
        var team=ModAttachments.team(player);for(int slot=0;slot<3;slot++)if(team.state().at(slot).map(c->c.instanceId().equals(role.instanceId())).orElse(false))return team.inventory().getItem(slot);return ItemStack.EMPTY;
    }
    private static int charges(ItemStack weapon,int maximum) {return Math.clamp(cn.zgnhit.mcwuwa.platform.NbtValues.getInt(WeaponData.tag(weapon),"echo_charges",maximum),0,maximum);}
    private static void setCharges(ItemStack weapon,int charges) {var tag=WeaponData.tag(weapon);tag.putInt("echo_charges",charges);weapon.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));}
    /** MC-authored visual placement: yaw-relative, feet on a nearby collision surface.
     * Never changes the frozen damage origin/range and never loads a chunk for a cosmetic entity.
     */
    private static Vec3 groundedSummonPosition(ServerPlayer player) {
        ServerLevel world=player.serverLevel();Vec3 origin=player.position();
        double yaw=Math.toRadians(player.getYRot());Vec3 forward=new Vec3(-Math.sin(yaw),0,Math.cos(yaw));
        Vec3 candidate=origin.add(forward.scale(2));
        if(!visualChunkLoaded(world,candidate))return origin;
        Vec3 rayStart=origin.add(0,1,0),rayEnd=candidate.add(0,1,0);
        var obstruction=world.clip(new ClipContext(rayStart,rayEnd,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        if(obstruction.getType()==HitResult.Type.BLOCK) {
            double distance=Math.max(0,obstruction.getLocation().subtract(rayStart).length()-.45);
            candidate=origin.add(forward.scale(distance));
        }
        Vec3 top=candidate.add(0,.75,0),bottom=candidate.add(0,-3,0);
        if(!visualChunkLoaded(world,top)||!visualChunkLoaded(world,bottom))return candidate;
        var floor=world.clip(new ClipContext(top,bottom,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        return floor.getType()==HitResult.Type.BLOCK?new Vec3(candidate.x,floor.getLocation().y,candidate.z):candidate;
    }
    private static boolean visualChunkLoaded(ServerLevel world,Vec3 position) {
        BlockPos block=BlockPos.containing(position);
        return world.getChunkSource().hasChunk(block.getX()>>4,block.getZ()>>4);
    }
    private static void discardBlades(Field field){for(var blade:field.blades)blade.discard();}
    /** MC fan placement behind the actor; count/lifetime/consumption are source rules. */
    private static Vec3 memoryBladePosition(ServerPlayer player,int index){
        double yaw=Math.toRadians(player.getYRot());var forward=new Vec3(-Math.sin(yaw),0,Math.cos(yaw));
        var right=new Vec3(-forward.z,0,forward.x);double side=(index-1.5)*.6;
        return player.position().add(forward.scale(-.75-Math.abs(side)*.12)).add(right.scale(side)).add(0,.8+Math.abs(side)*.25,0);
    }
    private static void restoreVisual(ServerPlayer player,Run run) {
        if(run.visual!=null&&run.fistsHeld)run.visual.discard();
        if(run.ability.transformation()) {
            CharacterInstance owner=role(player,run.owner);if(owner!=null&&owner.actionId().equals("echo_transform"))owner.cancelAction();
            Network.visual(player,new Network.Visual(player.getId(),run.owner,"",PlatformRuntime.time(player),0,++session(player).sequence,""));
        }
    }
    private static void message(ServerPlayer player,String text){player.displayClientMessage(Component.literal(text), true);}
}
