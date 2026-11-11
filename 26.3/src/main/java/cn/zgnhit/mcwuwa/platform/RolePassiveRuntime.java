package cn.zgnhit.mcwuwa.platform;

import cn.zgnhit.mcwuwa.McWuwa;
import cn.zgnhit.mcwuwa.content.mechanics.MechanicsState;
import cn.zgnhit.mcwuwa.content.mechanics.RolePassiveEngine;
import cn.zgnhit.mcwuwa.core.CharacterInstance;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Thin server bridge; observed mechanics are immutable snapshots, never another resource owner. */
public final class RolePassiveRuntime {
    private static final RolePassiveEngine ENGINE=new RolePassiveEngine();
    private static final class Session {
        final Map<UUID,RolePassiveEngine.State> states=new HashMap<>();
        final Map<UUID,MechanicsState> observed=new HashMap<>();
        final Map<UUID,String> modes=new HashMap<>();
        final Map<UUID,Long> actionInstances=new HashMap<>();
        final Map<UUID,YinlinStorm> yinlinStorms=new HashMap<>();
        final Map<UUID,Long> equippedAt=new HashMap<>();
        final Set<UUID> spawnSeeded=new HashSet<>();
        final Set<UUID> downed=new HashSet<>();
        long actionSequence;
        boolean inCombat;
    }
    private record YinlinStorm(long expires,int charges,long lastBasicAction) { }
    private static final Map<ServerPlayer,Session> SESSIONS=new WeakHashMap<>();
    private RolePassiveRuntime() { }
    private static Session session(ServerPlayer player){return SESSIONS.computeIfAbsent(player,p->new Session());}
    public static void updateMechanics(ServerPlayer player,CharacterInstance role,MechanicsState state){
        var previous=session(player).observed.put(role.instanceId(),state);
        // Resource rewards are committed after the triggering hurt. Waiting for another
        // CAST/HIT loses the final120 threshold when R2 consumes resolve before its CAST.
        if(role.alive()&&role.chain()>=1&&role.character()==cn.zgnhit.mcwuwa.core.CharacterId.CARTETHYIA&&state.form().equals("fleurdelys")) {
            int before=previous==null?0:(int)(previous.resource("resolve")/30),after=(int)(state.resource("resolve")/30);
            // A paid new cycle also reports its falling threshold immediately;
            // resetting the cache must not depend on an unrelated later CAST.
            if(after!=before)process(player,role,new RolePassiveEngine.Event(after>before?RolePassiveEngine.Trigger.RESOURCE_GAIN:RolePassiveEngine.Trigger.RESOURCE_RESET,
                PlatformRuntime.time(player),"","","resolve",role.instanceId().toString(),true),null);
        }
    }
    public static void onAction(ServerPlayer player,CharacterInstance role,String actionId){
        Session s=session(player);s.actionInstances.put(role.instanceId(),++s.actionSequence);
        own(player,role,RolePassiveEngine.Trigger.CAST,actionId,"","",null);
        if(role.chain()>=6&&actionId.equals("yinlin/liberation"))s.yinlinStorms.put(role.instanceId(),new YinlinStorm(PlatformRuntime.time(player)+600,4,-1));
    }
    public static void onHit(ServerPlayer player,CharacterInstance role,LivingEntity target,String actionId,String kind){
        NewThreeRoleRuntime.confirmedHit(player,role,target,actionId);
        own(player,role,RolePassiveEngine.Trigger.HIT,actionId,kind,"",target);
        Session s=session(player);YinlinStorm storm=s.yinlinStorms.get(role.instanceId());long action=s.actionInstances.getOrDefault(role.instanceId(),0L);
        if(storm!=null&&storm.expires()>PlatformRuntime.time(player)&&storm.charges()>0&&storm.lastBasicAction()!=action
            &&kind.equals("basic")&&actionId.startsWith("yinlin/basic_")) {
            s.yinlinStorms.put(role.instanceId(),new YinlinStorm(storm.expires(),storm.charges()-1,action));
            var definition=McWuwa.catalog().requireByWeapon(role.character().weaponPath());
            if(PlatformRuntime.nativeHit(player,role,target,cn.zgnhit.mcwuwa.content.RoleProcDefinitions.yinlinSixthChain(definition),0,1,"yinlin/c6_thunder",PlatformRuntime.HitOrigin.AUTONOMOUS)) {
                SkillModelProfiles.light(player,role,"yinlin/judgment_contact",target.position(),12,false);
                PlatformRuntime.soloEffectApplied(player,role,target,"yinlin_c6_resource");
            }
        }
    }
    public static void onEchoCast(ServerPlayer player,CharacterInstance role){own(player,role,RolePassiveEngine.Trigger.ECHO_CAST,"echo","echo","",null);}
    /** Accepted damage only. This is not an anomaly application or a normal action/resource hit. */
    public static void onRuptureDamage(ServerPlayer player,CharacterInstance actor,LivingEntity target,String cause) {
        if(!cn.zgnhit.mcwuwa.content.mechanics.RuptureDamageRules.isRuptureDamage(cause))return;
        var team=ModAttachments.team(player).state();
        for(int slot=0;slot<3;slot++)team.at(slot).filter(CharacterInstance::alive).ifPresent(owner->
            process(player,owner,new RolePassiveEngine.Event(RolePassiveEngine.Trigger.HIT,PlatformRuntime.time(player),
                cause,"tune_break","rupture_damage",actor.instanceId().toString(),owner.instanceId().equals(actor.instanceId())),target));
    }
    public static void modeChanged(ServerPlayer player,CharacterInstance role,MechanicsState mechanics) {
        Session s=session(player);s.states.computeIfPresent(role.instanceId(),(id,state)->ENGINE.modeChanged(state,mechanics.characterId()));
        s.observed.put(role.instanceId(),mechanics);s.modes.put(role.instanceId(),mechanics.mode());
    }
    public static void onEffectApplied(ServerPlayer player,CharacterInstance actor,LivingEntity target,String effectId) {
        onEffectApplied(player,actor,target,effectId,1);
    }
    /** applications is the accepted application count, not the target's resulting stack count.
     * Contributor and batch events remain single; Snow Rust alone fires once per application. */
    public static void onEffectApplied(ServerPlayer player,CharacterInstance actor,LivingEntity target,String effectId,int applications) {
        if(applications<=0)throw new IllegalArgumentException("Accepted application count");
        PlatformRuntime.soloEffectApplied(player,actor,target,effectId);
        var team=ModAttachments.team(player).state();
        boolean anomaly=Set.of("havoc_bane","aero_erosion","electro_flare","glacio_chafe","glacio_frost","fusion_burst","spectro_frazzle","rupture_shifting","strain_shifting","electro_rage").contains(effectId);
        for(int i=0;i<3;i++)team.at(i).filter(CharacterInstance::alive).ifPresent(owner->process(player,owner,new RolePassiveEngine.Event(
                !anomaly?RolePassiveEngine.Trigger.APPLY_MARK:owner.instanceId().equals(actor.instanceId())?RolePassiveEngine.Trigger.APPLY_ANOMALY:RolePassiveEngine.Trigger.TEAM_APPLY_ANOMALY,
                PlatformRuntime.time(player),actor.actionId(),"",effectId,actor.instanceId().toString(),owner.instanceId().equals(actor.instanceId())),target));
        if(effectId.equals("glacio_chafe")||effectId.equals("glacio_frost"))team.active().filter(owner->owner.alive()&&owner.character()==cn.zgnhit.mcwuwa.core.CharacterId.HIYUKI).ifPresent(owner->{
            int rust=session(player).states.getOrDefault(owner.instanceId(),RolePassiveEngine.State.empty()).stacks().getOrDefault("snow_rust",0);
            double ratio=cn.zgnhit.mcwuwa.content.mechanics.HiyukiFrostRules.additionalRatio(rust,owner.chain(),owner.instanceId().equals(actor.instanceId()));
            if(ratio>0)for(int application=0;application<applications&&target.isAlive();application++)
                PlatformRuntime.effectHit(player,new cn.zgnhit.mcwuwa.core.EffectSource(player.getUUID(),owner.instanceId(),owner.character(),UUID.randomUUID(),UUID.randomUUID()),
                    target,cn.zgnhit.mcwuwa.core.Element.GLACIO,cn.zgnhit.mcwuwa.core.GlobalCombatRules.LEVEL_90_BASE*ratio,"effect/glacio_frost/snow_rust");
        });
    }
    public static double frostAmplification(ServerPlayer player,LivingEntity target) {
        if(target==null||target.level()!=player.level()||player.distanceToSqr(target)>64)return 1;
        var team=ModAttachments.team(player).state();double factor=1;
        for(int slot=0;slot<3;slot++){
            var owner=team.at(slot).orElse(null);if(owner==null||!owner.alive()||owner.character()!=cn.zgnhit.mcwuwa.core.CharacterId.HIYUKI)continue;
            int rust=session(player).states.getOrDefault(owner.instanceId(),RolePassiveEngine.State.empty()).stacks().getOrDefault("snow_rust",0);
            double value=team.active().orElse(null)==owner?cn.zgnhit.mcwuwa.content.mechanics.HiyukiFrostRules.amplification(rust,owner.chain()):owner.chain()>=6&&rust>=3?1.25:1;
            factor=Math.max(factor,value);
        }return factor;
    }
    public static void onUnison(ServerPlayer player,CharacterInstance actor) {
        var team=ModAttachments.team(player).state();for(int i=0;i<3;i++)team.at(i).filter(CharacterInstance::alive).ifPresent(owner->process(player,owner,
                new RolePassiveEngine.Event(RolePassiveEngine.Trigger.UNISON,PlatformRuntime.time(player),"","","",actor.instanceId().toString(),owner.instanceId().equals(actor.instanceId())),null));
    }
    public static void onSwitch(ServerPlayer player,CharacterInstance outgoing){own(player,outgoing,RolePassiveEngine.Trigger.SWITCH_OUT,"","","",null);}
    public static void onDowned(ServerPlayer player,CharacterInstance owner){
        var state=session(player);UUID id=owner.instanceId();state.downed.add(id);
        // These contributor rewards reset on equipping the character or changing its mode.
        var retainedBuffs=switch(owner.character()){
            case HIYUKI -> Set.of("hiyuki_rust_crit");
            case HSIN -> Set.of("hsin_inherent4_electro");
            case AEMEATH -> Set.of("aemeath_stars_crit","aemeath_stars_finale");
            default -> Set.<String>of();
        };
        if(retainedBuffs.isEmpty())state.states.remove(id);
        else state.states.computeIfPresent(id,(key,previous)->{
            var buffs=new HashMap<>(previous.buffs());
            buffs.keySet().retainAll(retainedBuffs);
            var stacks=new HashMap<>(previous.stacks());
            stacks.keySet().retainAll(Set.of("snow_rust","hsin_electro_contributors","aemeath_stars"));
            return new RolePassiveEngine.State(buffs,stacks,previous.ready(),previous.contributors());
        });
        state.observed.remove(id);state.yinlinStorms.remove(id);
        state.spawnSeeded.remove(id);
    }
    public static void tick(ServerPlayer player) {
        Session s=session(player);var team=ModAttachments.team(player).state();Set<UUID> equipped=new HashSet<>();
        if(CinematicTimeRuntime.paused(player)){
            NewThreeRoleRuntime.pauseOneTick(player);
            s.states.replaceAll((id,state)->{
                var buffs=new HashMap<String,RolePassiveEngine.Buff>();
                state.buffs().forEach((key,b)->buffs.put(key,new RolePassiveEngine.Buff(b.id(),b.scope(),b.attribute(),b.value(),cn.zgnhit.mcwuwa.core.CinematicTimeRules.postpone(b.expires(),1),b.endsOnSwitch())));
                var ready=new HashMap<String,Long>();state.ready().forEach((key,t)->ready.put(key,cn.zgnhit.mcwuwa.core.CinematicTimeRules.postpone(t,1)));
                return new RolePassiveEngine.State(buffs,state.stacks(),ready,state.contributors());
            });
            s.yinlinStorms.replaceAll((id,b)->new YinlinStorm(b.expires()+1,b.charges(),b.lastBasicAction()));
            s.equippedAt.replaceAll((id,t)->t+1);return;
        }
        for(int slot=0;slot<3;slot++)team.at(slot).ifPresent(role->{
            UUID id=role.instanceId();equipped.add(id);String slug=McWuwa.catalog().requireByWeapon(role.character().weaponPath()).characterId();
            if(!role.alive())s.downed.add(id);
            else if(s.downed.remove(id)){s.equippedAt.put(id,PlatformRuntime.time(player));s.spawnSeeded.remove(id);}
            long equippedTime=s.equippedAt.computeIfAbsent(id,key->PlatformRuntime.time(player));
            if(role.alive()&&slug.equals("hiyuki")&&role.chain()>=3&&PlatformRuntime.time(player)-equippedTime>=40&&s.spawnSeeded.add(id))own(player,role,RolePassiveEngine.Trigger.SPAWN_READY,"","","",null);
            String mode=MechanicsState.canonicalMode(slug,role.selectedMode());String previous=s.modes.put(id,mode);
            if(previous!=null&&!previous.equals(mode))s.states.computeIfPresent(id,(key,state)->ENGINE.modeChanged(state,slug));
            if(s.inCombat!=team.inCombat())own(player,role,team.inCombat()?RolePassiveEngine.Trigger.ENTER_COMBAT:RolePassiveEngine.Trigger.EXIT_COMBAT,"","","",null);
        });
        s.inCombat=team.inCombat();s.states.keySet().retainAll(equipped);s.observed.keySet().retainAll(equipped);s.modes.keySet().retainAll(equipped);
        s.actionInstances.keySet().retainAll(equipped);s.yinlinStorms.entrySet().removeIf(e->!equipped.contains(e.getKey())||e.getValue().expires()<=PlatformRuntime.time(player));
        s.equippedAt.keySet().retainAll(equipped);s.spawnSeeded.retainAll(equipped);
        s.downed.retainAll(equipped);
    }
    public static void clear(ServerPlayer player){SESSIONS.remove(player);NewThreeRoleRuntime.clear(player);}
    /** Phrolova's explicit echo-cast protection shares the same verified 15-second passive lifetime. */
    public static boolean hasEchoInterruptionProtection(ServerPlayer player, CharacterInstance role) {
        var session = SESSIONS.get(player); if (session == null) return false;
        return ENGINE.active(session.states.getOrDefault(role.instanceId(), RolePassiveEngine.State.empty()), PlatformRuntime.time(player))
            .stream().anyMatch(buff -> buff.id().equals("phrolova_inherent4"));
    }

    /** Add exactly once to NativeStats. No target or action-specific modifier is included here. */
    public static Map<String,Double> permanentModifiers(CharacterInstance role,ItemStack weapon) {
        String who=McWuwa.catalog().requireByWeapon(role.character().weaponPath()).characterId();
        return switch(who) {
            case "cantarella" -> Map.of("healing_bonus",.20);
            case "lynae" -> role.chain()>=4?Map.of("attack_pct",.20):Map.of();
            case "mornye" -> Map.of("energy_regen",.10);
            case "jinhsi" -> Map.of("spectro_bonus",.20);
            case "camellya" -> Map.of("havoc_bonus",.15,"basic_bonus",.15);
            case "aero_rover" -> role.chain()>=3?Map.of("aero_bonus",.15):Map.of();
            default -> Map.of();
        };
    }
    /** NativeStats already contains permanentModifiers, so these outputs explicitly exclude them. */
    public static RolePassiveEngine.Modifiers damage(ServerPlayer player,CharacterInstance role,LivingEntity target,String actionId,String kind,String element) {
        return damage(player,role,target,actionId,kind,element,null);
    }
    static RolePassiveEngine.Modifiers damage(ServerPlayer player,CharacterInstance role,LivingEntity target,String actionId,String kind,String element,Double contactEmbers) {
        Session s=session(player);long now=PlatformRuntime.time(player);var context=context(player,role,target,actionId,contactEmbers);
        var evaluated=ENGINE.modifiers(context,s.states.getOrDefault(role.instanceId(),RolePassiveEngine.State.empty()),now,actionId,kind,element);
        Map<String,Double> attrs=new HashMap<>(evaluated.attributes());permanentModifiers(role,stack(player,role)).forEach((key,value)->attrs.merge(key,-value,Double::sum));
        var team=ModAttachments.team(player).state();
        for(int slot=0;slot<3;slot++)team.at(slot).ifPresent(owner->{
            for(var buff:ENGINE.active(s.states.getOrDefault(owner.instanceId(),RolePassiveEngine.State.empty()),now))if(buff.scope()==RolePassiveEngine.Scope.OWN_TEAM)attrs.merge(buff.attribute(),buff.value(),Double::sum);
            if(owner.alive()&&owner.character().weaponPath().equals("starfield_calibrator")&&target!=null
                &&EffectRuntime.hasOwnedEffect(target,player.getUUID(),owner.instanceId(),"mornye_interference")) {
                double surplus=Math.max(0,energyRegen(player,owner)-1);
                if(owner.chain()>=1||EffectRuntime.hasEffect(target,"rupture_interference")||EffectRuntime.hasEffect(target,"strain_interference"))
                    attrs.merge("all_damage_bonus",Math.min(.40,surplus*.25),Double::sum);
                if(owner.chain()>=2)attrs.merge("crit_damage",Math.min(.32,surplus*.2),Double::sum);
            }
        });
        double doom=cn.zgnhit.mcwuwa.content.mechanics.NewThreeDamageRules.galbrenaDoomApplies(actionId)?NewThreeRoleRuntime.amplification(player,role,target):1;
        return new RolePassiveEngine.Modifiers(attrs,evaluated.coefficientIncrease(),
            cn.zgnhit.mcwuwa.core.CombatMath.amplificationFactor(evaluated.amplification(),doom),evaluated.targetVulnerability());
    }
    public static Map<String,Double> modifiers(ServerPlayer player,CharacterInstance role,LivingEntity target,String actionId,String kind,String element){return damage(player,role,target,actionId,kind,element).attributes();}
    public static double coefficientMultiplier(ServerPlayer player,CharacterInstance role,LivingEntity target,String actionId,String kind,String element){return 1+damage(player,role,target,actionId,kind,element).coefficientIncrease();}
    private static void own(ServerPlayer p,CharacterInstance r,RolePassiveEngine.Trigger trigger,String action,String kind,String effect,LivingEntity target) {
        process(p,r,new RolePassiveEngine.Event(trigger,PlatformRuntime.time(p),action,kind,effect,r.instanceId().toString(),true),target);
    }
    private static void process(ServerPlayer p,CharacterInstance r,RolePassiveEngine.Event event,LivingEntity target) {
        Session s=session(p);
        var previous=s.states.computeIfAbsent(r.instanceId(),id->{
            Map<String,Long> ready=new HashMap<>();
            r.snapshot().cooldowns().forEach((key,value)->{if(key.startsWith("role_passive_"))ready.put(key.substring(13),event.tick()+value);});
            return new RolePassiveEngine.State(Map.of(),Map.of(),ready,Set.of());
        });
        var next=ENGINE.onEvent(previous,context(p,r,target),event);s.states.put(r.instanceId(),next);
        next.ready().forEach((key,value)->r.setCooldown("role_passive_"+key,Math.max(0,value-event.tick())));
    }
    private static RolePassiveEngine.Context context(ServerPlayer player,CharacterInstance role,LivingEntity target) {
        return context(player,role,target,"");
    }
    private static RolePassiveEngine.Context context(ServerPlayer player,CharacterInstance role,LivingEntity target,String action) {
        return context(player,role,target,action,null);
    }
    private static RolePassiveEngine.Context context(ServerPlayer player,CharacterInstance role,LivingEntity target,String action,Double contactEmbers) {
        String who=McWuwa.catalog().requireByWeapon(role.character().weaponPath()).characterId();
        MechanicsState observed=session(player).observed.getOrDefault(role.instanceId(),MechanicsState.initial(who,role.selectedMode(),role.chain()));
        var contextTimers=new HashMap<>(observed.timers());
        if(who.equals("suisui")&&PlatformRuntime.suisuiInherentAttack(player,role,action))contextTimers.remove("suisui_inherent_hit_cd");
        if(target!=null){contextTimers.putAll(PlatformRuntime.sixTargetTimers(player,role,target));contextTimers.put("target_tune_interference",EffectRuntime.strainInterferenceLayers(target));}
        if(target!=null&&EffectRuntime.hasOwnedEffect(target,player.getUUID(),role.instanceId(),"yinlin_sin"))contextTimers.put("target_yinlin_sin",1);
        if(target!=null&&EffectRuntime.hasEffect(target,"spectro_frazzle"))contextTimers.put("target_spectro_frazzle",1);
        boolean active=ModAttachments.team(player).state().active().map(a->a.instanceId().equals(role.instanceId())).orElse(false);
        var contextResources=new HashMap<>(observed.resources());
        // Only this already-resolving contact retains its consumed embers.
        // The live mechanics and subsequent attacks remain in the exited form.
        if(who.equals("galbrena")&&action.startsWith("galbrena/demon_")&&contactEmbers!=null)
            contextResources.put("embers",contactEmbers);
        if(target!=null&&Set.of("denia","qingxiao","lynae","mornye").contains(who))contextResources.put("passive_tune_break_amplification",PlatformRuntime.sixResponseAmplification(player,role));
        return new RolePassiveEngine.Context(who,role.chain(),MechanicsState.canonicalMode(who,role.selectedMode()),observed.form(),contextResources,contextTimers,
                energyRegen(player,role),active,target==null?0:EffectRuntime.layers(target,"havoc_bane"),target==null?0:EffectRuntime.layers(target,"aero_erosion"),target==null?0:EffectRuntime.layers(target,"electro_flare"),
                target!=null&&(EffectRuntime.hasOwnedEffect(target,player.getUUID(),role.instanceId(),"yinlin_sin")||EffectRuntime.hasOwnedEffect(target,player.getUUID(),role.instanceId(),"yinlin_punishment")),target!=null&&EffectRuntime.hasOwnedEffect(target,player.getUUID(),role.instanceId(),"chisa_void_scar"),
                target!=null&&EffectRuntime.hasOwnedEffect(target,player.getUUID(),role.instanceId(),"mornye_interference"),PlatformRuntime.unisonStacks(player,role));
    }
    private static double energyRegen(ServerPlayer player,CharacterInstance role) {
        return PlatformRuntime.currentEnergyRegen(player,role);
    }
    private static ItemStack stack(ServerPlayer player,CharacterInstance role) {
        var team=ModAttachments.team(player);for(int slot=0;slot<3;slot++)if(team.state().at(slot).map(c->c.instanceId().equals(role.instanceId())).orElse(false))return team.inventory().getItem(slot);return ItemStack.EMPTY;
    }
}
