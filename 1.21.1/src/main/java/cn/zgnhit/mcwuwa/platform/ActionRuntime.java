package cn.zgnhit.mcwuwa.platform;

import cn.zgnhit.mcwuwa.McWuwa;
import cn.zgnhit.mcwuwa.content.*;
import cn.zgnhit.mcwuwa.content.mechanics.*;
import cn.zgnhit.mcwuwa.core.*;
import cn.zgnhit.mcwuwa.platform.entity.ConstructEntity;
import cn.zgnhit.mcwuwa.platform.entity.HecateEntity;
import cn.zgnhit.mcwuwa.platform.entity.EnemyEvents;
import cn.zgnhit.mcwuwa.platform.entity.CombatProjectileEntity;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Executes one server-owned timeline; view frames are never damage triggers. */
public final class ActionRuntime {
    private static final MechanicsEngine MECHANICS = new MechanicsEngine();
    private static final WeaponPassiveEngine WEAPON_PASSIVES = new WeaponPassiveEngine();
    private final Map<UUID, WeaponPassiveEngine.State> weaponPassives = new HashMap<>();
    private final Map<UUID, MechanicsState> mechanics = new HashMap<>();
    private final List<Buff> buffs = new ArrayList<>();
    private final Map<String, UUID> weaponBuffRecipients = new HashMap<>();
    private final SonataRuntime sonatas = new SonataRuntime();
    private final SpecialMovementRuntime specialMovement=new SpecialMovementRuntime();
    private final TargetReactionRuntime targetReactions=new TargetReactionRuntime();
    private final SoloTargetRuntime soloTargets=new SoloTargetRuntime(this);
    private final UnisonState unison=new UnisonState();
    private final SwordShadowRuntime swordShadows=new SwordShadowRuntime();
    private final Set<UUID> unisonGains=new HashSet<>();
    private final Map<UUID, List<MechanicsEngine.Effect>> pendingMarks = new HashMap<>();
    private final ShieldLedger shields = new ShieldLedger();
    private record AfterDamage(CharacterInstance owner,List<MechanicsEngine.Effect> effects) { }
    private final Deque<List<AfterDamage>> afterDamage = new ArrayDeque<>();
    private final Map<LivingEntity, UUID> punishedTargets = new WeakHashMap<>();
    private final RuptureResponseCooldown responseCooldown=new RuptureResponseCooldown();
    private final Map<LivingEntity, Long> chisaScarReady = new WeakHashMap<>();
    private final Set<LivingEntity> creditedKills=Collections.newSetFromMap(new WeakHashMap<>());
    private boolean relayInProgress;
    private final ChargeState charge = new ChargeState();
    private boolean chargeWaitingForAttack;
    private final List<Field> fields = new ArrayList<>();
    private final NewThreeWorldEffects threeWorld=new NewThreeWorldEffects();
    private final TideWorldRuntime tideWorld=new TideWorldRuntime();
    private final NewSixWorldRuntime sixWorld=new NewSixWorldRuntime();
    private final NewFiveWorldRuntime fiveWorld=new NewFiveWorldRuntime();
    private final Map<UUID,ConstructEntity> suisuiDancers=new HashMap<>();
    private final Map<UUID,Set<UUID>> phoebeStasisTargets=new HashMap<>();
    private final Map<UUID,Long> phoebeRefractionReady=new HashMap<>();
    private final Map<UUID,LivingEntity> hsinEdictTargets=new HashMap<>();
    private record Field(UUID owner, MechanicsEngine.Effect effect, Vec3 center, FieldArea area, net.minecraft.world.entity.Entity visual) { }
    private Running running;
    private UUID recitalPoseOwner;
    private String ciacconaPoseAction="";
    private long ciacconaPoseUntil;
    private UUID qiuyuanRunPoseOwner;
    private UUID hsinOpeningPoseOwner;
    private long hsinOpeningPoseUntil;
    private final ArrayDeque<ActionDamageMapping> queue = new ArrayDeque<>();
    private final List<Running> secondary = new ArrayList<>();
    private long sequence;
    private long currentTick;
    private int committingActionTicks = 1;
    private UUID heldAttackOwner;
    private UUID activeRoleId;
    private UUID sixJumpOwner,sixJumpShortOwner;
    private long sixJumpSince,sixJumpShortUntil;
    private boolean sixJumpHeld,sixJumpHoldQueued,sixJumpReleased;
    private UUID lupaEscapeOwner;
    private long lupaEscapeAt;
    private HeldAttackRules.Mode heldAttackMode;
    private long heldAttackSince;
    private UUID chisaDodgeOwner;
    private long chisaDodgeSince;
    private record DeferredSawRelease(UUID weapon,long start,long sequence) { }
    private DeferredSawRelease deferredSawRelease;
    private record Buff(UUID owner, UUID recipient, MechanicsEngine.Effect effect, long expires) { }
    private static final class Running {
        final UUID weapon; final ActionDamageMapping action; final List<CharacterDefinition.DamageDefinition> hits;
        long start; final long sequence; final Set<String> ledger = new HashSet<>(); boolean resourceHit;
        List<MechanicsEngine.Effect> marks = List.of();
        Map<String,Double> acceptedQualifications=Map.of();
        List<ConstructEntity> visuals=List.of();
        boolean automatic;
        boolean independent;
        boolean fixedPhantom;
        double phantomAttack;
        boolean ruptureResponse;
        long completionTick;
        UUID assistedTarget;
        Vec3 anchor;
        Vec3 fixedAim;
        double anchorRadius = 2;
        TargetReactionRuntime.HavocSnapshot havocSnapshot;
        final Map<UUID, Integer> aeroFinaleSnapshots = new HashMap<>();
        TargetReactionRuntime.AeroSnapshot aeroRangeSnapshot;
        double thunderSnapshot=-1;
        boolean wingStarSnapshot;
        boolean wingPreserveTracesSnapshot;
        JinhsiCastBonus jinhsiCastBonus;
        Double lynaePremixBonus;
        boolean suisuiInherentQualified;
        Running(UUID weapon, ActionDamageMapping action, List<CharacterDefinition.DamageDefinition> hits, long start, long sequence) {
            this.weapon = weapon; this.action = action; this.hits = hits; this.start = start; this.sequence = sequence;
            this.completionTick=start+action.durationTicks();
        }
    }
    private record JinhsiCastBonus(double multiplierAddition,double damageBonus){}
    private NewSixWorldRuntime.Callbacks sixCallbacks(ServerPlayer player) {
        return new NewSixWorldRuntime.Callbacks() {
            public void hit(CharacterInstance owner,LivingEntity target,String action){
                if(tideNativeHit(player,owner,target,action)&&owner.character()==CharacterId.DENIA&&action.equals("erosion_field")) {
                    var result=MECHANICS.onHit(ruleState(owner),"denia/erosion_field",context(player,owner),false);
                    if(result.accepted()){commit(owner,result.state());applyEffects(player,owner,result.effects(),target);}
                }
            }
            public void heal(CharacterInstance owner,CharacterInstance recipient,double ratio){ActionRuntime.this.heal(player,owner,recipient,combatStats(player,owner).attack()*ratio);passiveEvent(player,owner,WeaponPassiveEngine.Trigger.HEAL,"iuno_full_moon:"+PlatformRuntime.time(player));PlatformRuntime.applyHealth(player,ModAttachments.team(player),false);}
            public void effect(CharacterInstance owner,LivingEntity target,String id,int layers){applyMark(player,owner,target,new MechanicsEngine.Effect(MechanicsEngine.Kind.MARK_TARGET,id,layers,0,Map.of()));}
            public void event(CharacterInstance owner,CharacterInstance actor,String id,LivingEntity target,Map<String,Double> values){sixEvent(player,owner,actor,id,target,values);}
            public ConstructEntity projection(ServerPlayer viewer,CharacterInstance owner,Vec3 position,int ticks){return ConstructEntity.spawn(viewer.serverLevel(),viewer,owner.instanceId(),"lupa_reserve_assist",position,ticks,1,false,"idle");}
        };
    }
    private void sixEvent(ServerPlayer player,CharacterInstance owner,CharacterInstance actor,String id,LivingEntity target,Map<String,Double> values) {
        var result=MECHANICS.sixWorldEvent(ruleState(owner),context(player,owner),id,values);if(!result.accepted())return;
        commit(owner,result.state());RolePassiveRuntime.updateMechanics(player,owner,result.state());
        var ordinary=new ArrayList<MechanicsEngine.Effect>();
        for(var effect:result.effects()) {
            if(effect.id().equals("iuno_pale_blessing")){sixBlessing(player,owner,actor,effect);continue;}
            if(effect.values().getOrDefault("applierOnly",0d)>0) {
                buffs.removeIf(b->b.owner.equals(owner.instanceId())&&Objects.equals(b.recipient,actor.instanceId())&&b.effect.id().equals(effect.id()));
                buffs.add(new Buff(owner.instanceId(),actor.instanceId(),effect,PlatformRuntime.time(player)+effect.durationTicks()));
            }else ordinary.add(effect);
        }
        applyEffects(player,owner,ordinary,target);
    }
    private void sixBlessing(ServerPlayer player,CharacterInstance source,CharacterInstance recipient,MechanicsEngine.Effect effect) {
        double old=buffs.stream().filter(b->b.expires>PlatformRuntime.time(player)&&Objects.equals(b.recipient,recipient.instanceId())&&b.effect.id().equals("iuno_pale_blessing")).mapToDouble(b->b.effect.amount()).max().orElse(0);
        var stacked=new MechanicsEngine.Effect(effect.kind(),effect.id(),Math.min(10,old+effect.amount()),effect.durationTicks(),effect.values());
        buffs.removeIf(b->Objects.equals(b.recipient,recipient.instanceId())&&b.effect.id().equals(effect.id()));
        buffs.add(new Buff(source.instanceId(),recipient.instanceId(),stacked,PlatformRuntime.time(player)+effect.durationTicks()));
    }
    public double sixResponseAmplification(ServerPlayer player,CharacterInstance role) {
        var modifiers=new HashMap<>(passiveModifiers(player,role,null,"all","all",role.actionId()));
        EchoRuntime.modifiers(player,role).forEach((key,value)->modifiers.merge(key,value,Double::sum));
        double points=NativeStats.of(role,PlatformRuntime.weaponFor(player,role),modifiers).bonus("tune_break_amplification");
        return Math.max(0,McWuwa.catalog().requireByWeapon(role.character().weaponPath()).tuneMasteryRaw()+points
            +buffs.stream().filter(b->applies(b,role)&&b.effect.id().equals("tune_break_amplification"))
                .mapToDouble(b->b.effect.amount()).max().orElse(0));
    }
    public Map<String,Integer> sixTargetTimers(CharacterInstance owner,LivingEntity target){return sixWorld.targetTimers(owner,target,currentTick);}
    public boolean sixJump(ServerPlayer player,String operation) {
        if(operation.equals("SIX_JUMP_CANCEL")){sixJumpOwner=sixJumpShortOwner=null;sixJumpHeld=sixJumpHoldQueued=sixJumpReleased=false;return true;}
        var team=ModAttachments.team(player).state();var role=team.active().orElse(null);
        if(role==null||!player.isAlive()||!team.canAct()||player.isPassenger())return false;
        var state=ruleState(role);long now=PlatformRuntime.time(player);
        boolean special=role.character()==CharacterId.IUNO&&state.timer("iuno_phase")>0||role.character()==CharacterId.QINGXIAO&&state.timer("qingxiao_flight")>0;
        if(Boolean.getBoolean("mc_wuwa.legacyFeedbackOnly"))McWuwa.LOGGER.info("Native traversal gesture operation={} role={} tick={} since={} held={} phase={} flight={}",operation,role.character(),now,sixJumpSince,sixJumpHeld,state.timer("iuno_phase"),state.timer("qingxiao_flight"));
        if(!special){sixJumpOwner=sixJumpShortOwner=null;sixJumpHeld=sixJumpHoldQueued=sixJumpReleased=false;return false;}
        if(operation.equals("SIX_JUMP_PRESS")){sixJumpOwner=role.instanceId();sixJumpSince=now;sixJumpHeld=sixJumpHoldQueued=sixJumpReleased=false;sixJumpShortOwner=null;return true;}
        if(!role.instanceId().equals(sixJumpOwner))return false;
        if(operation.equals("SIX_JUMP_RELEASE")) {
            if(sixJumpHoldQueued){
                sixJumpReleased=true;
                return now-sixJumpSince>=8?sixJump(player,"SIX_JUMP_HOLD"):true;
            }
            if(!sixJumpHeld){sixJumpShortOwner=role.instanceId();sixJumpShortUntil=now+2;}
            sixJumpOwner=null;return true;
        }
        if(!operation.equals("SIX_JUMP_HOLD")||sixJumpHeld)return false;
        // Keep an early client intent, but execute only after eight server ticks.
        if(now-sixJumpSince<8){sixJumpHoldQueued=true;return true;}
        boolean released=sixJumpReleased;sixJumpHoldQueued=sixJumpReleased=false;
        // Held traversal has the same cinematic boundary as the short jump route.
        if(running!=null&&(!running.weapon.equals(role.instanceId())||CinematicTimeRules.cinematic(running.action.id()))){
            if(released)sixJumpOwner=null;
            return false;
        }
        var result=MECHANICS.sixWorldEvent(state,context(player,role),role.character()==CharacterId.QINGXIAO?"qingxiao_long_jump":"iuno_exit_traversal",Map.of());
        if(Boolean.getBoolean("mc_wuwa.legacyFeedbackOnly"))McWuwa.LOGGER.info("Native traversal held result accepted={} reason={} effects={}",result.accepted(),result.reason(),result.effects());
        if(!result.accepted()){if(released)sixJumpOwner=null;return false;}
        sixJumpHeld=true;cancel(player);commit(role,result.state());RolePassiveRuntime.updateMechanics(player,role,result.state());
        applyEffects(player,role,result.effects());specialMovement.tick(player,role,result.state());Network.sync(player);
        if(released)sixJumpOwner=null;return true;
    }
    public boolean request(ServerPlayer player, String operation) {
        var attachment = ModAttachments.team(player); var team = attachment.state();
        if (!team.canAct() || EchoRuntime.isTransforming(player)) return false;
        var role = team.active().orElseThrow(); long now = PlatformRuntime.time(player);
        currentTick=now;activeRoleId=team.active().map(CharacterInstance::instanceId).orElse(null);
        hsinEdictTargets.keySet().removeIf(id->teamRole(player,id).isEmpty());
        threeWorld.prune(player);
        var data = McWuwa.catalog().requireByWeapon(role.character().weaponPath());
        String slug = data.characterId();
        MechanicsEngine.Input input;
        try { input = MechanicsEngine.Input.valueOf(operation.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException ex) { return false; }
        if(slug.equals("lucilla")&&(input==MechanicsEngine.Input.HEAVY_RELEASE||input==MechanicsEngine.Input.HEAVY_FULL))input=MechanicsEngine.Input.BASIC_RELEASE;
        if(input==MechanicsEngine.Input.JUMP&&(role.character()==CharacterId.IUNO&&ruleState(role).timer("iuno_phase")>0||role.character()==CharacterId.QINGXIAO&&ruleState(role).timer("qingxiao_flight")>0)) {
            if(!role.instanceId().equals(sixJumpShortOwner)||now>sixJumpShortUntil)return false;
            sixJumpShortOwner=null;
        }
        if(role.character()==CharacterId.GALBRENA&&input==MechanicsEngine.Input.DODGE&&(running==null||!running.weapon.equals(role.instanceId())||!player.onGround()||running.action.id().equals("galbrena/hellwalk")))return false;
        if(input==MechanicsEngine.Input.PINCERS_CLOSE)return false; // Internal timeline event, never a public request.
        if(input==MechanicsEngine.Input.NOTE_GREEN||input==MechanicsEngine.Input.NOTE_YELLOW||input==MechanicsEngine.Input.AIM_PRESS||input==MechanicsEngine.Input.AIM_RELEASE) {
            if(role.character()!=CharacterId.CIACCONA&&!(slug.equals("lucilla")&&(input==MechanicsEngine.Input.AIM_PRESS||input==MechanicsEngine.Input.AIM_RELEASE)))return false;
            if(slug.equals("lucilla")&&running!=null)return false;
            var result=MECHANICS.reduce(stateFor(role,slug),input,context(player,role));if(!result.accepted())return false;
            commit(role,result.state());applyEffects(player,role,result.effects());scheduleTickActions(player,role,result.effects());Network.sync(player);return true;
        }
        if(role.character()==CharacterId.QIUYUAN&&input==MechanicsEngine.Input.SKILL_RELEASE) {
            finishQiuyuanRun(player,role,true);return true;
        }
        if(role.character()==CharacterId.QIUYUAN&&ruleState(role).timer("qiuyuan_dash")>0
            &&Set.of(MechanicsEngine.Input.BASIC,MechanicsEngine.Input.HEAVY_START,MechanicsEngine.Input.AIR,
                MechanicsEngine.Input.DODGE,MechanicsEngine.Input.DODGE_COUNTER,MechanicsEngine.Input.LIBERATION,
                MechanicsEngine.Input.SKILL).contains(input))finishQiuyuanRun(player,role,false);
        if(input==MechanicsEngine.Input.SKILL_HOLD)input=heldSkillInput(player,role,stateFor(role,slug));
        if(role.character()==CharacterId.CIACCONA&&input==MechanicsEngine.Input.LIBERATION&&stateFor(role,slug).form().equals("performing")) {
            var result=MECHANICS.reduce(ruleState(role),input,context(player,role));cancel(player);
            commit(role,result.state());applyEffects(player,role,result.effects());Network.sync(player);return true;
        }
        if(role.character()==CharacterId.SIGRIKA&&input==MechanicsEngine.Input.SKILL_RELEASE&&ruleState(role).timer("sig_hold_consumed")>0){
            var result=MECHANICS.reduce(ruleState(role),input,context(player,role));commit(role,result.state());Network.sync(player);return true;
        }
        boolean reelCounter=input==MechanicsEngine.Input.REEL_COUNTER;
        if(reelCounter&&(role.character()!=CharacterId.CHISA||!player.onGround()||!team.stamina().sprintHeld()
            ||team.stamina().snapshot().counterUntil()<=team.onlineTick()||!role.instanceId().equals(chisaDodgeOwner)||now-chisaDodgeSince<4))return false;
        if(input==MechanicsEngine.Input.BASIC&&role.character()==CharacterId.CHISA&&chisaPincersPress(player,role,now))return true;
        var returnState=mechanics.get(role.instanceId());
        if(slug.equals("suoming")&&returnState!=null&&returnState.form().equals("calamity")
            &&Set.of(MechanicsEngine.Input.BASIC_RELEASE,MechanicsEngine.Input.HEAVY_RELEASE,MechanicsEngine.Input.HEAVY_FULL,MechanicsEngine.Input.SKILL_RELEASE).contains(input)){
            heldAttackOwner=null;heldAttackMode=null;
            var release=MECHANICS.reduce(returnState,MechanicsEngine.Input.BASIC_RELEASE,context(player,role));
            if(release.accepted()){commit(role,release.state());applyEffects(player,role,release.effects());Network.sync(player);}
            return release.accepted();
        }
        // A release is a harmless input edge when no held Chisa entry belongs to this body.
        // Other characters also report long-skill key-up; never turn it into an error toast.
        if(input==MechanicsEngine.Input.SKILL_RELEASE&&!Set.of("lucilla","zani").contains(slug)&&role.character()!=CharacterId.QIUYUAN&&role.character()!=CharacterId.SIGRIKA&&(role.character()!=CharacterId.CHISA||returnState==null||returnState.timer("chisa_held_entry")==0))return false;
        if(input==MechanicsEngine.Input.LIBERATION_RELEASE&&!HiyukiReturnChargeRules.isCharging(returnState))return false;
        if(HiyukiReturnChargeRules.isCharging(returnState)&&input!=MechanicsEngine.Input.LIBERATION_RELEASE)return false;
        if (input == MechanicsEngine.Input.SKILL_HOLD && !Set.of("lynae", "hsin", "hiyuki", "chisa", "qiuyuan", "sigrika", "phoebe", "lupa", "zhezhi","lucilla","zani","suoming").contains(slug)) input = MechanicsEngine.Input.SKILL;
        boolean ciacconaSkillCancel=input==MechanicsEngine.Input.SKILL&&canCiacconaSkillCancel(role);
        boolean stanceCancel = running != null && input == MechanicsEngine.Input.SKILL && canSwitchStanceDuringBasic(role);
        var commandState=mechanics.get(role.instanceId());
        if(commandState!=null&&commandState.timer("electronic_ghost")>0)return false;
        if(slug.equals("lucilla")&&commandState!=null&&commandState.timer("lucilla_memory_channel")>0
            &&(input==MechanicsEngine.Input.BASIC||input==MechanicsEngine.Input.HEAVY_START)){
            boolean hold=input==MechanicsEngine.Input.HEAVY_START;
            var repeated=MECHANICS.reduce(commandState,hold?MechanicsEngine.Input.BASIC_HOLD:input,context(player,role));
            if(!repeated.accepted())return false;
            if(hold){heldAttackOwner=role.instanceId();heldAttackMode=HeldAttackRules.Mode.COMBO;heldAttackSince=now;}
            commit(role,repeated.state());RolePassiveRuntime.updateMechanics(player,role,repeated.state());Network.sync(player);return true;
        }
        if(commandState!=null&&running!=null&&role.instanceId().equals(running.weapon)
            &&ChisaSawRules.deferRelease(commandState,running.action.id(),input,now-running.start)) {
            deferredSawRelease=new DeferredSawRelease(running.weapon,running.start,running.sequence);return true;
        }
        if(role.instanceId().equals(heldAttackOwner)&&(input==MechanicsEngine.Input.HEAVY_RELEASE||input==MechanicsEngine.Input.HEAVY_FULL||input==MechanicsEngine.Input.BASIC_RELEASE)) {
            heldAttackOwner=null;heldAttackMode=null;Network.sync(player);
            if(commandState==null||!(slug.equals("lucilla")&&(commandState.timer("lucilla_focus_channel")>0||commandState.timer("lucilla_memory_channel")>0)
                ||slug.equals("zani")&&commandState.timer("zani_guard_mode")>0))return true;
        }
        if(slug.equals("mornye")&&charge.active()
            &&Set.of(MechanicsEngine.Input.HEAVY_RELEASE,MechanicsEngine.Input.HEAVY_FULL).contains(input)){
            // Baseline heavy already fired on key-down. Observation entry is
            // handled by its held-combo owner above, not by this charge latch.
            cancelCharge(player);Network.sync(player);return true;
        }
        if(running!=null&&slug.equals("lynae")&&running.action.id().equals("lynae/cruise_air_heavy")
            &&Set.of(MechanicsEngine.Input.HEAVY_RELEASE,MechanicsEngine.Input.HEAVY_FULL).contains(input)){
            // Air heavy fires at key-down. Key-up releases the physical latch
            // without cancelling the already accepted attack or adding a finisher.
            cancelCharge(player);Network.sync(player);return true;
        }
        if(running!=null&&slug.equals("chisa")&&running.action.id().equals("chisa/shattered_plane")
            &&Set.of(MechanicsEngine.Input.HEAVY_RELEASE,MechanicsEngine.Input.HEAVY_FULL).contains(input)){
            cancelCharge(player);Network.sync(player);return true;
        }
        // Changli's airborne hold is a plunge, not a charge released later.
        // The client already distinguishes a hold after four ticks. Waiting
        // for key-up lets short jumps land and incorrectly become ground lifts.
        if(input==MechanicsEngine.Input.HEAVY_START&&slug.equals("changli")&&context(player,role).airborne())
            input=MechanicsEngine.Input.AIR;
        // Source1406: a hold DURING either Wind Air strike is an immediate
        // stamina-paid plunge, not a charge waiting for key-up/landing.
        boolean immediateAirHeavy=input==MechanicsEngine.Input.HEAVY_START
            &&(running==null||running.weapon.equals(role.instanceId()))
            &&HeldAttackRules.immediateAirHeavy(commandState,running==null?"":running.action.id(),context(player,role).airborne());
        if(immediateAirHeavy)input=MechanicsEngine.Input.HEAVY_FULL;
        if(input==MechanicsEngine.Input.HEAVY_START) {
            var heldState=stateFor(role,slug);var mode=HeldAttackRules.mode(heldState,context(player,role).airborne());
            if(mode!=HeldAttackRules.Mode.HEAVY) {
                var heldInput=HeldAttackRules.next(mode,heldState,"");
                boolean heldLink=running!=null&&Set.of("suoming","lucilla","hiyuki","galbrena").contains(slug)&&canLinkAction(player,role,heldInput,
                    MECHANICS.reduce(heldState,heldInput,context(player,role)));
                if(running!=null&&!heldLink&&!running.action.id().contains("basic_")&&!(mode==HeldAttackRules.Mode.CAMELLYA_LOOP&&running.action.id().equals("camellya/heavy")))return false;
                heldAttackOwner=role.instanceId();heldAttackMode=mode;heldAttackSince=now;
                if((running==null||heldLink)&&!request(player,HeldAttackRules.next(mode,heldState,"").name())){heldAttackOwner=null;heldAttackMode=null;return false;}
                Network.sync(player);return true;
            }
        }
        boolean commandJump=input==MechanicsEngine.Input.JUMP&&commandState!=null
            &&(slug.equals("phrolova")&&commandState.form().equals("conducting")
                ||slug.equals("mornye")&&commandState.form().equals("observation")
                ||slug.equals("lynae")&&commandState.form().equals("cruise")
                ||slug.equals("camellya")&&commandState.form().equals("bloom")
                    &&(running==null||running.weapon.equals(role.instanceId())
                        &&(running.action.id().startsWith("camellya/bloom_basic_")
                            ||Set.of("camellya/bloom_spinning","camellya/bloom_dodge_counter").contains(running.action.id())))
                ||slug.equals("cantarella")&&commandState.form().equals("mirage")
                    &&(running==null||running.weapon.equals(role.instanceId())
                        &&running.action.id().startsWith("cantarella/mirage_basic_"))
                ||slug.equals("changli")&&commandState.timer("changli_true_sight")>0
                    &&(running==null||running.weapon.equals(role.instanceId())
                        &&!CinematicTimeRules.cinematic(running.action.id()))
                ||slug.equals("iuno")&&commandState.timer("iuno_phase")>0
                    &&(running==null||running.weapon.equals(role.instanceId())&&!CinematicTimeRules.cinematic(running.action.id()))
                ||slug.equals("qingxiao")&&commandState.timer("qingxiao_flight")>0
                    &&(running==null||running.weapon.equals(role.instanceId())&&!CinematicTimeRules.cinematic(running.action.id())));
        boolean iaiCancel=running!=null&&role.character()==CharacterId.HIYUKI&&commandState!=null
            &&HiyukiIaiRules.canCancel(commandState,running.action.id(),input,now-running.start);
        boolean sawCancel=running!=null&&commandState!=null&&ChisaSawRules.canCancel(commandState,running.action.id(),input,now-running.start);
        boolean cartethyiaCancel=running!=null&&commandState!=null&&CartethyiaAttackRules.canCancel(commandState,running.action.id(),input,
            now-running.start,running.action.hitTimesTicks().stream().mapToInt(Integer::intValue).max().orElse(0));
        boolean skillLinkCancel=running!=null&&commandState!=null&&(input==MechanicsEngine.Input.SKILL||input==MechanicsEngine.Input.SKILL_HOLD
            ||immediateAirHeavy
            ||Set.of("mornye","phoebe","camellya").contains(slug)&&input==MechanicsEngine.Input.LIBERATION
            ||slug.equals("lynae")&&(input==MechanicsEngine.Input.AIR||input==MechanicsEngine.Input.BASIC)
            ||slug.equals("aemeath")&&input==MechanicsEngine.Input.BASIC
            ||Set.of("lupa","denia").contains(slug)&&input==MechanicsEngine.Input.BASIC
            ||Set.of("changli","carlotta","qiuyuan","hiyuki","mornye","galbrena","qingxiao",
                "shorekeeper","ciaccona","cantarella","iuno","sigrika","cartethyia","aero_rover",
                "hsin","yangyang_xuanling","phrolova","suisui","chisa").contains(slug)&&input==MechanicsEngine.Input.BASIC
            ||slug.equals("suoming")&&(input==MechanicsEngine.Input.BASIC||input==MechanicsEngine.Input.BASIC_HOLD)
            ||slug.equals("galbrena")&&input==MechanicsEngine.Input.BASIC_HOLD
            ||slug.equals("zani")&&(input==MechanicsEngine.Input.BASIC||input==MechanicsEngine.Input.BASIC_HOLD)
            ||Set.of("zhezhi","lucilla").contains(slug)&&(input==MechanicsEngine.Input.BASIC||input==MechanicsEngine.Input.BASIC_HOLD)
            ||slug.equals("zhezhi")&&input==MechanicsEngine.Input.HEAVY_START
            ||slug.equals("aero_rover")&&input==MechanicsEngine.Input.HEAVY_START
            ||slug.equals("augusta")&&Set.of(MechanicsEngine.Input.BASIC,MechanicsEngine.Input.BASIC_HOLD,MechanicsEngine.Input.BASIC_RELEASE,MechanicsEngine.Input.HEAVY_RELEASE,MechanicsEngine.Input.HEAVY_FULL,MechanicsEngine.Input.JUMP,MechanicsEngine.Input.LIBERATION,MechanicsEngine.Input.LIBERATION_HOLD).contains(input))
            &&canLinkAction(player,role,input,MECHANICS.reduce(commandState,input,context(player,role)));
        boolean wanderRelease=running!=null&&HsinTravelRules.wandering(commandState)
            &&(input==MechanicsEngine.Input.HEAVY_RELEASE||input==MechanicsEngine.Input.HEAVY_FULL);
        wanderRelease|=running!=null&&slug.equals("suisui")&&running.action.id().equals("suisui/rain_heavy")&&(input==MechanicsEngine.Input.HEAVY_RELEASE||input==MechanicsEngine.Input.HEAVY_FULL);
        wanderRelease|=running!=null&&slug.equals("lynae")&&running.action.id().equals("lynae/heavy")&&commandState.timer("charging")>0
            &&(input==MechanicsEngine.Input.HEAVY_RELEASE||input==MechanicsEngine.Input.HEAVY_FULL);
        wanderRelease|=running!=null&&slug.equals("galbrena")&&commandState.timer("gal_raining")>0&&(input==MechanicsEngine.Input.HEAVY_RELEASE||input==MechanicsEngine.Input.HEAVY_FULL);
        boolean foxExitCancel=running!=null&&HsinTravelRules.canReplace(commandState,running.action.id(),input);
        boolean zaniRelease=slug.equals("zani")&&commandState!=null&&commandState.timer("zani_guard_mode")>0
            &&Set.of(MechanicsEngine.Input.SKILL_RELEASE,MechanicsEngine.Input.BASIC_RELEASE,MechanicsEngine.Input.HEAVY_RELEASE,MechanicsEngine.Input.HEAVY_FULL).contains(input);
        boolean lucillaRelease=slug.equals("lucilla")&&commandState!=null
            &&(input==MechanicsEngine.Input.SKILL_RELEASE&&commandState.timer("lucilla_focus_channel")==2
                ||input==MechanicsEngine.Input.BASIC_RELEASE&&(commandState.timer("lucilla_focus_channel")==1||commandState.timer("lucilla_memory_channel")>0));
        if (running != null && !zaniRelease&&!lucillaRelease&&!stanceCancel&&!ciacconaSkillCancel&&!commandJump&&!iaiCancel&&!sawCancel&&!skillLinkCancel&&!cartethyiaCancel&&!wanderRelease&&!reelCounter&&!foxExitCancel&&!(slug.equals("galbrena")&&input==MechanicsEngine.Input.DODGE)) return false;
        boolean counter = input == MechanicsEngine.Input.BASIC&&BasicInputRules.counterTap(commandState,
            team.stamina().snapshot().counterUntil()>team.onlineTick());
        if (counter) input = MechanicsEngine.Input.DODGE_COUNTER;
        boolean startsCharge = input == MechanicsEngine.Input.HEAVY_START&&!(slug.equals("chisa")&&commandState!=null&&commandState.form().equals("saw"));
        boolean releasesCharge = !immediateAirHeavy&&(input == MechanicsEngine.Input.HEAVY_RELEASE || input == MechanicsEngine.Input.HEAVY_FULL);
        if (startsCharge && charge.active()) return false;
        if (releasesCharge) {
            var readiness = charge.readiness(role.instanceId(), now, data.fullChargeTicks());
            if (readiness == ChargeState.Readiness.NO_START) return false;
            input = readiness == ChargeState.Readiness.FULL ? MechanicsEngine.Input.HEAVY_FULL : MechanicsEngine.Input.HEAVY_RELEASE;
        }
        MechanicsState state = stateFor(role, slug);
        state=swordShadows.prepare(player,role,state);
        state=fiveWorld.prepare(player,role,state);
        state=heightEligibility(player,state);
        state=prepareTideState(player,role,state,false);
        var counterState=cn.zgnhit.mcwuwa.content.mechanics.AugustaInputRules.skillCounter(state,input,
            team.stamina().snapshot().counterUntil()>team.onlineTick());
        boolean skillCounter=counterState!=state;state=counterState;
        if(input==MechanicsEngine.Input.BASIC)input=BasicInputRules.tap(state,context(player,role).airborne(),player.isInWater());
        MechanicsEngine.Result result = MECHANICS.reduce(state, input, context(player, role));
        if(result.accepted()&&result.effects().stream().anyMatch(e->e.id().equals("phoebe_ring_teleport"))&&!phoebeTeleportable(player,role))return false;
        if (!result.accepted()) { if (input != MechanicsEngine.Input.JUMP&&input!=MechanicsEngine.Input.DODGE) message(player, result.reason()); return false; }
        for(var effect:result.effects())if(effect.id().equals("zhezhi_crane")
            &&!fiveWorld.selectedValid(player,role,effect.values().get("slot").intValue()))return false;
        if(startsCharge&&slug.equals("shorekeeper")&&state.resource("data")>=5)startsCharge=false; // Deduction fires immediately, without leaving a charge latch.
        List<String> actionIds = result.effects().stream()
            .filter(effect -> effect.kind() == MechanicsEngine.Kind.ACTION&&!effect.id().equals("shorekeeper/butterfly")).map(MechanicsEngine.Effect::id).toList();
        List<ActionDamageMapping> actions = new ArrayList<>();
        List<MechanicsEngine.Effect> acceptedMotion=List.of();
        for (String id : actionIds) {
            var mapping = McWuwa.catalog().actionMappings(slug).stream().filter(a -> a.id().equals(id)).findFirst();
            if (mapping.isEmpty() || !mapping.get().executable() || mapping.get().hitTimesTicks() == null) { message(player, "动作数据尚未接通：" + id); return false; }
            if(id.startsWith("phrolova/hecate_")&&fields.stream().noneMatch(field->hecateField(field,player,role)))return false;
            actions.add(mapping.get());
        }
        {
            double energyCost = result.effects().stream().filter(e -> e.kind() == MechanicsEngine.Kind.PAY_ENERGY).mapToDouble(MechanicsEngine.Effect::amount).sum();
            double staminaCost = result.effects().stream().filter(e -> e.kind() == MechanicsEngine.Kind.PAY_STAMINA).mapToDouble(MechanicsEngine.Effect::amount).sum();
            double concertoCost = result.effects().stream().filter(e -> e.kind() == MechanicsEngine.Kind.PAY_CONCERTO).mapToDouble(MechanicsEngine.Effect::amount).sum();
            if (!role.canPay(Map.of("energy", energyCost, "concerto", concertoCost)) || team.stamina().snapshot().stamina() < staminaCost) return false;
            if(actions.stream().anyMatch(action->action.id().equals("hiyuki/future_heavy_frost"))
                &&!GroundImpactGeometry.returnToGround(player)) {
                player.sendSystemMessage(Component.literal("下方没有可落脚的地面"));return false;
            }
            if(player.getVehicle() instanceof cn.zgnhit.mcwuwa.platform.entity.SurveyMotorcycleEntity
                &&input!=MechanicsEngine.Input.JUMP&&input!=MechanicsEngine.Input.DODGE)player.stopRiding();
            // Reject and validate the complete replacement before cancelling the already-running basic.
            if(stanceCancel&&actions.stream().anyMatch(a->a.id().startsWith("yangyang_xuanling/flow_")))preserveXuanlingPhantom(player,role,now);
            if(ciacconaSkillCancel) {
                var shadow=preserveCiaccona(player,role,result.state(),true,result.effects().stream().anyMatch(e->e.kind()==MechanicsEngine.Kind.SPAWN_FIELD&&e.id().startsWith("ciaccona_shadow_")));
                var combined=new ArrayList<>(result.effects());combined.addAll(shadow.effects());
                result=new MechanicsEngine.Result(true,"ok",shadow.state(),combined);
            }
            boolean hsinOpening=role.character()==CharacterId.HSIN&&result.effects().stream()
                .anyMatch(effect->effect.kind()==MechanicsEngine.Kind.GAIN_CONCERTO&&effect.id().equals("liberation_transform"));
            boolean resumeObservationHold=skillLinkCancel&&role.instanceId().equals(heldAttackOwner)
                &&heldAttackMode==HeldAttackRules.Mode.MORNYE_OBSERVATION_LOOP&&result.state().form().equals("observation");
            long observationHeldSince=heldAttackSince;
            if(lucillaRelease&&running!=null&&running.weapon.equals(role.instanceId())
                &&Set.of("lucilla/forget","lucilla/forget_echo").contains(running.action.id())){
                var paidPhoto=running;running=null;paidPhoto.independent=true;secondary.add(paidPhoto);
            }
            boolean hellwalkReplacement=role.character()==CharacterId.GALBRENA&&input==MechanicsEngine.Input.DODGE;
            if (hsinOpening||zaniRelease||lucillaRelease||stanceCancel||ciacconaSkillCancel||iaiCancel||sawCancel||skillLinkCancel||cartethyiaCancel||wanderRelease||reelCounter||foxExitCancel||hellwalkReplacement||commandJump&&Set.of("mornye","lynae","camellya","cantarella","changli","iuno","qingxiao").contains(slug)) cancel(player);
            if(resumeObservationHold){heldAttackOwner=role.instanceId();heldAttackMode=HeldAttackRules.Mode.MORNYE_OBSERVATION_LOOP;heldAttackSince=observationHeldSince;}
            if (counter||reelCounter||skillCounter) team.stamina().consumeCounter(true);
            committingActionTicks = Math.max(1, sharedEpiphany(actions)
                ? actions.stream().mapToInt(ActionDamageMapping::durationTicks).max().orElse(1)
                : actions.stream().mapToInt(ActionDamageMapping::durationTicks).sum());
            // actionStarted clears the previous action's drift/support. Apply
            // accepted command motion after that reset, never before it.
            var motion=actions.isEmpty()?List.<MechanicsEngine.Effect>of():result.effects().stream()
                .filter(ActionRuntime::actionMotionEffect).toList();
            acceptedMotion=motion;
            commit(role, result.state()); RolePassiveRuntime.updateMechanics(player, role, result.state());
            applyEffects(player,role,motion.isEmpty()?result.effects():result.effects().stream().filter(e->!motion.contains(e)).toList());
            committingActionTicks = 1;
        }
        if(input==MechanicsEngine.Input.SKILL_RUN){
            qiuyuanRunPoseOwner=role.instanceId();
            Network.visual(player,new Network.Visual(player.getId(),role.instanceId(),"mc_wuwa:qiuyuan/skill_run",now,result.state().timer("qiuyuan_dash"),++sequence,""));
        }
        if (startsCharge) {
            charge.begin(role.instanceId(), now);
            chargeWaitingForAttack=actions.isEmpty()&&result.state().timer("charging")>0;
            if(actions.isEmpty())Network.visual(player,new Network.Visual(player.getId(),role.instanceId(),"mc_wuwa:"+slug+"/charge",now,1200,++sequence,""));
        }
        else if (releasesCharge) { charge.consume(role.instanceId()); cancelCharge(player); }
        else if (charge.active()) cancelCharge(player);
        // Baseline Mornye E is a real block/heal transaction without a damage timeline.
        // Give it an authenticated visual-only guard; adding a fake attack here would
        // incorrectly award hit resources and prevent the actual BLOCK_HIT counter.
        if(actions.isEmpty()&&role.character()==CharacterId.MORNYE&&input==MechanicsEngine.Input.SKILL&&result.state().timer("block")>0)
            Network.visual(player,new Network.Visual(player.getId(),role.instanceId(),"mc_wuwa:mornye/skill_guard",now,10,++sequence,""));
        if (!actions.isEmpty()) {
            if(Set.of(MechanicsEngine.Input.BASIC,MechanicsEngine.Input.AIR,MechanicsEngine.Input.DODGE_COUNTER,
                MechanicsEngine.Input.HEAVY_START,MechanicsEngine.Input.HEAVY_RELEASE,MechanicsEngine.Input.HEAVY_FULL).contains(input))
                sonatas.event(player,role,"ATTACK_CAST",false,0,0);
            if(heldAttackOwner!=null&&actions.stream().anyMatch(a->!a.id().contains("basic_")&&!a.id().equals("camellya/bloom_spinning")
                  &&!a.id().equals("suoming/thunder_spin_01")
                  &&!a.id().equals("augusta/realm_sun")
                  &&!(heldAttackMode==HeldAttackRules.Mode.SINGLE_BASIC&&Set.of("ciaccona/aim","ciaccona/aim_charged").contains(a.id()))
                  &&!(heldAttackMode==HeldAttackRules.Mode.QIUYUAN_INK_LOOP&&a.id().startsWith("qiuyuan/ink_heavy_"))
                &&!(heldAttackMode==HeldAttackRules.Mode.GALBRENA_HEAVY_LOOP&&a.id().contains("heavy_0"))
                &&!(heldAttackMode==HeldAttackRules.Mode.MORNYE_OBSERVATION_LOOP&&ruleState(role).form().equals("observation")&&a.id().startsWith("mornye/")))) {heldAttackOwner=null;heldAttackMode=null;}
            start(player, role, data, actions.getFirst());
            if(input==MechanicsEngine.Input.HEAVY_START&&running.action.id().equals("mornye/heavy_potential")){
                // The entry heavy and subsequent observation combo share this
                // physical hold; key-up clears the latch without adding a basic.
                charge.consume(role.instanceId());heldAttackOwner=role.instanceId();
                heldAttackMode=HeldAttackRules.Mode.MORNYE_OBSERVATION_LOOP;heldAttackSince=now;
            }
            running.acceptedQualifications=result.effects().stream().filter(effect->Set.of("changli","carlotta","iuno","lupa","denia","qingxiao").contains(slug)&&effect.kind()==MechanicsEngine.Kind.ACTION&&effect.id().equals(running.action.id()))
                .map(MechanicsEngine.Effect::values).findFirst().map(Map::copyOf).orElse(Map.of());
            if(!acceptedMotion.isEmpty()){
                applyEffects(player,role,acceptedMotion);
                specialMovement.extendCommandSupport(player,role,running.action.id(),running.action.durationTicks());
            }
            // The accepted server transaction owns this cast's qualification, even when it consumes the final persistent use.
            running.wingStarSnapshot=result.effects().stream().anyMatch(effect->effect.kind()==MechanicsEngine.Kind.ACTION
                &&effect.id().equals(running.action.id())&&effect.values().getOrDefault("starResonance",0d)>0);
            running.wingPreserveTracesSnapshot=result.effects().stream().anyMatch(effect->effect.kind()==MechanicsEngine.Kind.ACTION
                &&effect.id().equals(running.action.id())&&effect.values().getOrDefault("preserveTraces",0d)>0);
            if(sharedEpiphany(actions)) {
                // Rays and the final dragon burst are two damage definitions
                // inside ONE48-tick E4. Queueing them replays the whole cast and
                // moves the final42-tick contact to90, beyond its paid snapshot.
                var companion=actions.get(1);var hits=new ArrayList<CharacterDefinition.DamageDefinition>();
                for(var group:companion.hitGroups())for(int hit=0;hit<group.count();hit++)hits.add(data.requireDamage(group.damageId()));
                var burst=new Running(role.instanceId(),companion,List.copyOf(hits),running.start,++sequence);
                snapshotEpiphany(player,role,burst);secondary.add(burst);
            } else for (int i = 1; i < actions.size(); i++) queue.add(actions.get(i));
        }
        // Short stance-cancel windows must not wait up to five ticks for their HUD eligibility.
        Network.sync(player);
        return true;
    }
    public boolean busy() { return running != null; }
    private static boolean actionMotionEffect(MechanicsEngine.Effect effect){
        return effect.kind()==MechanicsEngine.Kind.MOVE_SPECIAL
            &&(effect.id().equals("zhezhi_crane")||cn.zgnhit.mcwuwa.content.mechanics.SixMovementRules.command(effect.id())!=null);
    }
    /** Frozen1603/skill2: Dark Heart blocks stamina recovery only while casting. */
    public boolean allowsStaminaRecovery(ServerPlayer player) {
        var role=ModAttachments.team(player).state().active().orElse(null);
        if(role==null||!role.alive())return true;
        return cn.zgnhit.mcwuwa.content.mechanics.StaminaRecoveryRules.allowed(ruleState(role),true,bodyAirborne(player,role),
            running!=null&&running.weapon.equals(role.instanceId())?running.action.id():"");
    }
    public double groundSprintStaminaCost(ServerPlayer player) {
        var role=ModAttachments.team(player).state().active().orElse(null);
        return role==null||!role.alive()?0:cn.zgnhit.mcwuwa.content.mechanics.StaminaRecoveryRules.groundSprintCostPerTick(ruleState(role));
    }
    public boolean ownsContinuousStaminaDrain(ServerPlayer player) {
        var team=ModAttachments.team(player).state();var role=team.active().orElse(null);
        return team.canAct()&&role!=null&&cn.zgnhit.mcwuwa.content.mechanics.StaminaRecoveryRules.ownsContinuousDrain(ruleState(role),bodyAirborne(player,role));
    }
    private boolean chisaPincersPress(ServerPlayer player,CharacterInstance role,long now) {
        if(running==null||!running.weapon.equals(role.instanceId())||!running.action.id().equals("chisa/severing_pincers"))return false;
        Running scissors=running;int last=scissors.action.hitTimesTicks().getLast();
        var press=ChisaScissorsRules.press(scissors.action.id(),now-scissors.start,last,scissors.ledger.contains("pincers-extra"),scissors.ledger.contains("pincers-closed"));
        if(press==ChisaScissorsRules.Press.NONE)return false;
        if(press==ChisaScissorsRules.Press.EXTRA) {
            scissors.ledger.add("pincers-extra");var data=McWuwa.catalog().requireByWeapon(role.character().weaponPath());
            var mapping=McWuwa.catalog().actionMappings("chisa").stream().filter(a->a.id().equals("chisa/severing_extra")).findFirst().orElseThrow();
            List<CharacterDefinition.DamageDefinition> hits=new ArrayList<>();for(var group:mapping.hitGroups())for(int i=0;i<group.count();i++)hits.add(data.requireDamage(group.damageId()));
            // This is an additional contact inside the ongoing scissors animation,
            // not a new24-tick attack windup. Rebase the authored contact to this
            // accepted input, and keep the ordinary hit ledger/damage resolver.
            long contactStart=now-mapping.hitTimesTicks().getFirst();
            var extra=new Running(role.instanceId(),mapping,hits,contactStart,++sequence);extra.automatic=true;secondary.add(extra);
            hit(player,role,extra,0,hits.getFirst());
            Network.visual(player,new Network.Visual(player.getId(),role.instanceId(),"mc_wuwa:"+mapping.id(),contactStart,mapping.durationTicks(),extra.sequence,""));
        } else {
            chisaPincersClosed(player,role,scissors);
            for(int i=0;i<scissors.hits.size();i++)if(scissors.action.hitTimesTicks().get(i)>now-scissors.start)hit(player,role,scissors,i,scissors.hits.get(i));
            scissors.completionTick=now+1;
            Network.visual(player,new Network.Visual(player.getId(),role.instanceId(),"mc_wuwa:"+scissors.action.id(),now-last,scissors.action.durationTicks(),++sequence,""));
        }
        Network.sync(player);return true;
    }
    private void chisaPincersClosed(ServerPlayer player,CharacterInstance role,Running action) {
        if(!action.action.id().equals("chisa/severing_pincers")||!action.ledger.add("pincers-closed"))return;
        var result=MECHANICS.reduce(ruleState(role),MechanicsEngine.Input.PINCERS_CLOSE,context(player,role));
        if(result.accepted()){commit(role,result.state());RolePassiveRuntime.updateMechanics(player,role,result.state());applyEffects(player,role,result.effects());}
    }
    public boolean returnCharging(ServerPlayer player) {
        return ModAttachments.team(player).state().active().map(role->HiyukiReturnChargeRules.isCharging(mechanics.get(role.instanceId()))).orElse(false);
    }
    public boolean ghostActive(ServerPlayer player) {
        return ModAttachments.team(player).state().active().map(role->{var state=mechanics.get(role.instanceId());return state!=null&&state.timer("electronic_ghost")>0;}).orElse(false);
    }
    public void soloEffectApplied(ServerPlayer player,CharacterInstance actor,LivingEntity target,String effectId) {
        if(OutroRules.ANOMALIES.contains(effectId))grantChisaThread(player,actor);
        for(var grant:List.copyOf(buffs))if(grant.effect.id().equals("aemeath_outro_other_roles_amp")&&applies(grant,actor)){
            boolean matches=grant.effect.values().getOrDefault("fusionMode",0d)>0?effectId.equals("fusion_burst")
                :effectId.equals("rupture_shifting");
            if(matches){
                buffs.removeIf(b->b.owner.equals(grant.owner)&&actor.instanceId().equals(b.recipient)&&b.effect.id().equals("aemeath_outro_matching"));
                var effect=new MechanicsEngine.Effect(MechanicsEngine.Kind.BUFF_SELF,"aemeath_outro_matching",
                    grant.effect.values().getOrDefault("afterMatchingApplication",.20)-grant.effect.amount(),400,Map.of());
                buffs.add(new Buff(grant.owner,actor.instanceId(),effect,grant.expires));
            }
        }
        var team=ModAttachments.team(player).state();
        for(int slot=0;slot<3;slot++)if(team.at(slot).isPresent()) {
            var owner=team.at(slot).orElseThrow();if(!owner.alive())continue;
            soloTargets.applied(player,owner,target,effectId);
            var result=SoloEffectRules.applied(stateFor(owner,McWuwa.catalog().requireByWeapon(owner.character().weaponPath()).characterId()),
                context(player,owner),effectId,owner==actor);
            commit(owner,result.state());RolePassiveRuntime.updateMechanics(player,owner,result.state());applyEffects(player,owner,result.effects(),target);
        }
    }
    /** C6 is a team window; it does not turn every Cartethyia damage hit into an anomaly application. */
    public boolean cappedAeroSettlementActive(ServerPlayer player) {
        var team=ModAttachments.team(player).state();if(!team.inCombat())return false;
        for(int slot=0;slot<3;slot++) {
            var owner=team.at(slot).orElse(null);
            if(owner==null||!owner.alive()||owner.character()!=CharacterId.CARTETHYIA||owner.chain()<6)continue;
            var state=mechanics.get(owner.instanceId());
            if(state!=null&&state.timer("cartethyia_cap_settle")>0)return true;
        }
        return false;
    }
    public void typedHit(ServerPlayer player,CharacterInstance actor,String element) { typedHit(player,actor,element,false); }
    public void typedHit(ServerPlayer player,CharacterInstance actor,String element,boolean coordinated) {
        if(!Set.of("glacio","fusion","electro","aero","spectro","havoc").contains(element))return;
        var team=ModAttachments.team(player).state();
        for(int slot=0;slot<3;slot++)team.at(slot).filter(role->role.alive()&&role.character()==CharacterId.JINHSI).ifPresent(role->{
            var state=ruleState(role);var timers=new HashMap<>(state.timers());double gain=0;int interval=state.timer("outro_incandescence")>0?20:60;
            String directKey="incandescence_"+element,coordKey="incandescence_coordinated_"+element;
            if(state.timer(directKey)==0){gain++;timers.put(directKey,interval);}
            if(coordinated&&state.timer(coordKey)==0){gain+=2;timers.put(coordKey,interval);}
            var next=MECHANICS.grantResource(state,"incandescence",gain);
            ruleState(player,role,new MechanicsState(next.characterId(),next.form(),next.mode(),next.chain(),next.nextBasic(),next.resources(),timers));
        });
    }
    /** MC12-block collection range; actual periodic rage is consumed by the target engine once. */
    public void electroRageReleased(ServerPlayer player,LivingEntity target,int rage) {
        if(rage<=0||player.distanceToSqr(target)>144||!player.hasLineOfSight(target))return;
        var team=ModAttachments.team(player).state();if(!team.inCombat())return;
        for(int i=0;i<3;i++)team.at(i).filter(role->role.alive()&&role.character()==CharacterId.HSIN).ifPresent(role->{
            var state=ruleState(role);if(state.mode().equals("electro"))ruleState(player,role,MECHANICS.grantResource(state,"thunder_realm",rage));
        });
    }
    public void electroLayersApplied(ServerPlayer player,CharacterInstance actor,LivingEntity target,int layers) {
        if(layers<=0)return;
        var team=ModAttachments.team(player).state();
        for(int slot=0;slot<3;slot++)team.at(slot).filter(owner->owner.alive()&&owner!=actor&&owner.character()==CharacterId.HSIN).ifPresent(owner->{
            var state=ruleState(owner);
            if(!state.mode().equals("electro")||state.timer("heart_state")>0)return;
            var timers=new HashMap<>(state.timers());timers.put("hsin_storm_charge",Math.min(10,state.timer("hsin_storm_charge")+layers));
            ruleState(player,owner,new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),state.nextBasic(),state.resources(),timers));
        });
    }
    public void cancelLiberationCharge(ServerPlayer player) {
        ModAttachments.team(player).state().active().ifPresent(role->{
            var state=mechanics.get(role.instanceId());if(!HiyukiReturnChargeRules.isCharging(state))return;
            var next=InterruptionRules.cancelled(state);commit(role,next);RolePassiveRuntime.updateMechanics(player,role,next);Network.sync(player);
        });
    }
    public void modeChanged(ServerPlayer player,CharacterInstance role,String previousMode) {
        String slug=McWuwa.catalog().requireByWeapon(role.character().weaponPath()).characterId();
        MechanicsState old=mechanics.getOrDefault(role.instanceId(),MechanicsState.initial(slug,previousMode,role.chain()));
        if(MechanicsState.canonicalMode(slug,previousMode).equals(MechanicsState.canonicalMode(slug,role.selectedMode())))return;
        if(ModAttachments.team(player).state().active().orElse(null)==role)cancel(player);
        old=mechanics.getOrDefault(role.instanceId(),old);var next=MECHANICS.changeMode(InterruptionRules.cancelled(old),role.selectedMode());
        removeSecondary(action->action.weapon.equals(role.instanceId()));pendingMarks.remove(role.instanceId());
        soloTargets.modeChanged(role);
        sonatas.modeChanged(role);
        if(slug.equals("lucilla")){
            fiveWorld.photoModeChanged(role,next.mode());
            buffs.removeIf(buff->buff.owner.equals(role.instanceId())&&buff.effect.id().equals("lucilla_outro_echo"));
        }
        buffs.removeIf(buff->buff.owner.equals(role.instanceId())&&buff.effect.id().startsWith("conversion_"));
        if(role.character()==CharacterId.HSIN)buffs.removeIf(buff->buff.owner.equals(role.instanceId())&&buff.effect.id().startsWith("hsin_outro_"));
        if(role.character()==CharacterId.HSIN){unison.remove(role.instanceId());unison.clearLamps();}
        if(role.character()==CharacterId.DENIA)buffs.removeIf(buff->buff.owner.equals(role.instanceId())
            &&(buff.effect.id().startsWith("denia_entropy_")||buff.effect.values().getOrDefault("endsOnModeChange",0d)>0));
        commit(role,next);RolePassiveRuntime.modeChanged(player,role,next);sendForm(player,role,next.form(),0);
        if(role.character()==CharacterId.DENIA)sixEvent(player,role,role,"denia_mode_selected",null,Map.of());
    }
    public void echoCast(ServerPlayer player, CharacterInstance actor, int echoId) {
        passiveEvent(player,actor,WeaponPassiveEngine.Trigger.ECHO_CAST,Integer.toString(echoId));
        sonatas.echoCast(player,actor,echoId);
        if(actor.character()==CharacterId.QIUYUAN) {
            var result=MECHANICS.onEchoCast(stateFor(actor,"qiuyuan"),context(player,actor),echoId,true);
            commit(actor,result.state());applyEffects(player,actor,result.effects());
        }
        var team=ModAttachments.team(player).state();
        for(int slot=0;slot<3;slot++)team.at(slot).filter(CharacterInstance::alive).ifPresent(owner-> {
            String slug=switch(owner.character()){
                case CANTARELLA->"cantarella";case PHROLOVA->"phrolova";case SIGRIKA->"sigrika";case GALBRENA->"galbrena";default->null;
            };
            if(slug==null)return;
            var state=stateFor(owner,slug);var result=MECHANICS.onEchoCast(state,context(player,owner),echoId,owner==actor);
            commit(owner,result.state());applyEffects(player,owner,result.effects());scheduleTickActions(player,owner,result.effects());
        });
    }
    /** Called only by the validated dodge press; forward is the server-accepted input direction. */
    public boolean hecateDodge(ServerPlayer player,Vec3 direction) {
        var team=ModAttachments.team(player).state();var role=team.active().orElse(null);
        if(role==null||role.character()!=CharacterId.PHROLOVA||!team.canAct())return false;
        var state=mechanics.get(role.instanceId());if(state==null||!state.form().equals("conducting"))return false;
        HecateEntity.owned(player,role.instanceId()).ifPresent(entity->entity.commandDodge(player,direction));
        // The conducting command belongs to Hecate, even while its dodge is cooling down.
        return true;
    }
    public boolean dodgeMovement(ServerPlayer player,boolean forward) {
        var team=ModAttachments.team(player).state();var role=team.active().orElse(null);
        if(role==null||!team.canAct()||EchoRuntime.isTransforming(player))return false;
        if(role.character()==CharacterId.CHISA) {
            var state=stateFor(role,"chisa");var result=ChisaTraversalRules.begin(state,context(player,role),forward);
            if(!result.accepted())return false;
            cancel(player);commit(role,result.state());RolePassiveRuntime.updateMechanics(player,role,result.state());
            applyEffects(player,role,result.effects());specialMovement.tick(player,role,result.state());
            team.stamina().claimMovementInput();Network.sync(player);return true;
        }
        if(role.character()==CharacterId.HIYUKI) {
            boolean claimed=request(player,"DODGE");if(claimed)team.stamina().claimMovementInput();return claimed;
        }
        if(role.character()!=CharacterId.MORNYE)return false;
        var state=mechanics.get(role.instanceId());
        boolean escaping=state!=null&&state.timer("observation_hurt_window")>0&&state.timer("observation_escape_uses")<3;
        if(state==null||!state.form().equals("observation")||!forward&&state.timer("slow_descent")==0&&!escaping)return false;
        // Ordinary flight starts only after the native dodge accepts the attempt.
        if(state.timer("slow_descent")==0&&!escaping)return false;
        var result=MECHANICS.reduce(state,MechanicsEngine.Input.DODGE,context(player,role));if(!result.accepted())return false;
        if(state.timer("slow_descent")>0||escaping)cancel(player);
        commit(role,result.state());RolePassiveRuntime.updateMechanics(player,role,result.state());applyEffects(player,role,result.effects());
        specialMovement.tick(player,role,result.state());Network.sync(player);
        boolean claimed=result.effects().stream().anyMatch(e->e.id().equals("mornye_escape"));
        if(claimed)team.stamina().claimMovementInput();return claimed;
    }
    /** Normal sprint-dodge was accepted first; this qualification grants no movement/protection. */
    /** Source outro: consume one rescue only while the active body is actually hurt. */
    public boolean shorekeeperEscape(ServerPlayer player){
        var team=ModAttachments.team(player).state();if(!team.canAct()||player.hurtTime<=0)return false;
        for(int slot=0;slot<3;slot++){
            var owner=team.at(slot).orElse(null);if(owner==null||!owner.alive()||owner.character()!=CharacterId.SHOREKEEPER)continue;
            var state=ruleState(owner);if(state.timer("shore_escape")<=0||state.timer("shore_escape_uses")<=0)continue;
            var timers=new HashMap<>(state.timers());timers.put("shore_escape_uses",state.timer("shore_escape_uses")-1);
            cancel(player);ruleState(player,owner,new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),state.nextBasic(),state.resources(),timers));
            player.hurtTime=0;player.setDeltaMovement(Vec3.ZERO);player.fallDistance=0;player.hurtMarked=true;
            var floor=player.level().clip(new net.minecraft.world.level.ClipContext(player.position().add(0,.1,0),player.position().add(0,-3,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
            if(floor.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK){var at=floor.getLocation();player.teleportTo(at.x,at.y,at.z);player.setOnGround(true);}
            team.stamina().successfulSkillEvade();Network.sync(player);return true;
        }
        return false;
    }
    boolean canQingxiaoSwordDodge(ServerPlayer player) {
        var role=ModAttachments.team(player).state().active().orElse(null);
        if(role==null||role.character()!=CharacterId.QINGXIAO||player.onGround())return false;
        if(running!=null&&cn.zgnhit.mcwuwa.core.CinematicTimeRules.cinematic(running.action.id()))return false;
        return MECHANICS.reduce(ruleState(role),MechanicsEngine.Input.DODGE,context(player,role)).accepted();
    }
    public void normalDodgeAccepted(ServerPlayer player,float forward) {
        var team=ModAttachments.team(player).state();var role=team.active().orElse(null);
        if(role!=null&&role.character()==CharacterId.MORNYE&&forward>0){
            var state=ruleState(role);
            if(state.form().equals("observation")){
                var result=MECHANICS.reduce(state,MechanicsEngine.Input.DODGE,context(player,role));
                if(result.accepted()){
                    commit(role,result.state());RolePassiveRuntime.updateMechanics(player,role,result.state());
                    applyEffects(player,role,result.effects());specialMovement.tick(player,role,result.state());Network.sync(player);
                }
            }
        }
        if(role!=null&&role.character()==CharacterId.SUISUI){
            var before=ruleState(role);var timers=new HashMap<>(before.timers());timers.remove("suisui_cloud_evade");timers.remove("suisui_cloud_counter");
            commit(role,new MechanicsState(before.characterId(),before.form(),before.mode(),before.chain(),before.nextBasic(),before.resources(),timers));
        }
        if(role!=null&&role.character()==CharacterId.PHOEBE){
            var before=ruleState(role);var timers=new HashMap<>(before.timers());timers.remove("phoebe_glides");timers.remove("phoebe_staff_glide");timers.remove("phoebe_staff_dive");
            commit(role,new MechanicsState(before.characterId(),before.form(),before.mode(),before.chain(),before.nextBasic(),before.resources(),timers));
        }
        if(role!=null&&role.character()==CharacterId.SUISUI&&running!=null&&running.weapon.equals(role.instanceId())&&player.onGround()&&team.stamina().spend(20)){
            var before=ruleState(role);cancel(player);var clean=ruleState(role);var timers=new HashMap<>(clean.timers());timers.put("suisui_cloud_evade",12);timers.put("combo_window",60);
            commit(role,new MechanicsState(clean.characterId(),clean.form(),clean.mode(),clean.chain(),clean.form().equals("rain")?before.nextBasic():1,clean.resources(),timers));
            Network.visual(player,new Network.Visual(player.getId(),role.instanceId(),"mc_wuwa:suisui/cloud_dodge",PlatformRuntime.time(player),12,++sequence,""));
        }
        if(role!=null&&role.character()==CharacterId.CIACCONA) {
            var before=ruleState(role);String oldAction=running==null?"":running.action.id();
            var after=preserveCiaccona(player,role,before,false);cancel(player);
            var next=after.state();int cursor=oldAction.matches("ciaccona/basic_0[123]")?Integer.parseInt(oldAction.substring(oldAction.length()-1))+1:next.nextBasic();
            next=new MechanicsState(next.characterId(),next.form(),next.mode(),next.chain(),cursor,next.resources(),next.timers());
            commit(role,next);applyEffects(player,role,after.effects());Network.sync(player);
        }
        if(role!=null&&role.character()==CharacterId.QINGXIAO&&!player.onGround()) {
            var result=MECHANICS.reduce(ruleState(role),MechanicsEngine.Input.DODGE,context(player,role));
            if(result.accepted()){
                // Preview/payment qualification happened before generic pressSprint;
                // retain that real evade attempt while replacing only its body motion.
                cancel(player);commit(role,result.state());RolePassiveRuntime.updateMechanics(player,role,result.state());
                applyEffects(player,role,result.effects());specialMovement.tick(player,role,result.state());Network.sync(player);
            }
        }
        if(role!=null&&role.character()==CharacterId.LUPA&&running!=null&&running.weapon.equals(role.instanceId())
                &&Set.of("lupa/air_basic_03","lupa/air_flame_strike").contains(running.action.id())) {
            // Source1207: dodging the third aerial body opens the tap-plunge route.
            // Only an accepted generic dodge reaches this callback.
            var result=MECHANICS.reduce(ruleState(role),MechanicsEngine.Input.DODGE,context(player,role));
            if(result.accepted()){
                cancel(player);commit(role,result.state());RolePassiveRuntime.updateMechanics(player,role,result.state());
                applyEffects(player,role,result.effects());Network.sync(player);
            }
        }
        if(role!=null&&role.character()==CharacterId.GALBRENA&&running!=null&&player.onGround())request(player,"DODGE");
        if(role!=null&&role.character()==CharacterId.CHISA){chisaDodgeOwner=role.instanceId();chisaDodgeSince=PlatformRuntime.time(player);}
        if(role!=null&&role.character()==CharacterId.HSIN) {
            var before=ruleState(role);
            var physical=cn.zgnhit.mcwuwa.platform.MovementKeys.get(player);
            if(before.form().equals("fox")&&(!player.onGround()||!(physical.forward()||physical.backward()||physical.left()||physical.right())))sceneInteraction(player);
        }
        if(role==null||role.character()!=CharacterId.LYNAE||!team.canAct()||!team.wuwaMode())return;
        if(!player.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,player.getBoundingBox().inflate(32),m->m.getTarget()==player).isEmpty())return;
        var before=stateFor(role,"lynae");var after=LynaeCamouflageRules.begin(before,context(player,role),forward<0);
        if(!after.equals(before)){commit(role,after);RolePassiveRuntime.updateMechanics(player,role,after);Network.sync(player);}
    }
    public boolean camouflageActive(ServerPlayer player) {
        var team=ModAttachments.team(player).state();var role=team.active().orElse(null);
        if(role==null||role.character()!=CharacterId.LYNAE||!player.isAlive()||player.isSpectator()||!team.canAct()||!team.wuwaMode()||team.inCombat())return false;
        var state=mechanics.get(role.instanceId());return state!=null&&state.form().equals("cruise")&&state.timer(LynaeCamouflageRules.TIMER)>0;
    }
    public boolean quietFox(ServerPlayer player) {
        var team=ModAttachments.team(player).state();var role=team.active().orElse(null);
        return player.isAlive()&&!player.isSpectator()&&team.canAct()&&role!=null
            &&HsinTravelRules.quiet(mechanics.get(role.instanceId()),team.inCombat(),true);
    }
    public boolean travel(ServerPlayer player){
        if(player.getVehicle() instanceof cn.zgnhit.mcwuwa.platform.entity.SurveyMotorcycleEntity bike)return bike.toggleBoost(player);
        var team=ModAttachments.team(player).state();var role=team.active().orElse(null);
        if(role==null||!team.canAct()||busy()||!player.isAlive()||player.isPassenger()||player.isInWater()||player.isFallFlying()||player.getAbilities().flying)return false;
        var result=AemeathTravelRules.toggle(ruleState(role));if(!result.accepted())return false;
        commit(role,result.state());RolePassiveRuntime.updateMechanics(player,role,result.state());specialMovement.tick(player,role,result.state());Network.sync(player);return true;
    }
    /** Scene/UI/tool interactions end camouflage and travel without rolling back paid costs. */
    public void sceneInteraction(ServerPlayer player) {
        var role=ModAttachments.team(player).state().active().orElse(null);if(role==null)return;
        var before=mechanics.get(role.instanceId());if(before==null)return;
        if(role.character()==CharacterId.QIUYUAN&&before.timer("qiuyuan_dash")>0){finishQiuyuanRun(player,role,false);return;}
        var after=HsinTravelRules.leaveFox(AemeathTravelRules.clear(LynaeCamouflageRules.clear(before)));
        // Scene/tool interaction exits these source-defined traversal forms, without
        // resetting paid skill cooldowns or the weapon's inherited resources.
        if(after.characterId().equals("mornye")&&after.form().equals("observation")
            ||after.characterId().equals("hsin")&&after.form().equals("illuminated")
            ||after.characterId().equals("cartethyia")&&after.form().equals("fleurdelys")) {
            // The tool interaction replaces the old body's action as well as
            // its form; otherwise an old E can retain hover or deferred hits.
            cancel(player);
            after=InterruptionRules.cancelled(after);
            var exit=MECHANICS.leaveExploration(after);
            after=exit.state();applyEffects(player,role,exit.effects());
        }
        if(before.characterId().equals("augusta")&&before.form().equals("realm")){
            fiveWorld.augustaInteract(player,role);cancel(player);var timers=new HashMap<>(before.timers());
            timers.remove("augusta_phase");timers.remove("augusta_sun_count");
            after=new MechanicsState(before.characterId(),"normal",before.mode(),before.chain(),before.nextBasic(),before.resources(),timers);
        }
        if(role.character()==CharacterId.IUNO&&!after.form().equals("normal")
            ||role.character()==CharacterId.QINGXIAO&&after.timer("qingxiao_flight")>0) {
            // A tool/scene pose replaces the airborne body as well as its
            // traversal state. Keep accepted costs and cooldowns, not pending hits.
            cancel(player);after=InterruptionRules.cancelled(after);
            var exit=MECHANICS.sixWorldEvent(after,context(player,role),role.character()==CharacterId.IUNO?"iuno_exit_traversal":"qingxiao_exit_traversal",Map.of());
            if(exit.accepted()){after=exit.state();applyEffects(player,role,exit.effects());}
            sixJumpOwner=sixJumpShortOwner=null;sixJumpHeld=sixJumpHoldQueued=sixJumpReleased=false;
        }
        if(!after.equals(before)){commit(role,after);RolePassiveRuntime.updateMechanics(player,role,after);sendForm(player,role,after.form(),0);specialMovement.tick(player,role,after);Network.sync(player);}
    }
    /** The direction is validated from normal C2S input, not a forged successful-dodge event. */
    public boolean sawBackDodge(ServerPlayer player,float forward) {
        if(forward>=0)return false;
        return ModAttachments.team(player).state().active().filter(r->r.character()==CharacterId.CHISA).isPresent()&&request(player,"BACK_DODGE");
    }
    private MechanicsEngine.Result preserveCiaccona(ServerPlayer player,CharacterInstance role,MechanicsState state,boolean skillCancel) {
        return preserveCiaccona(player,role,state,skillCancel,false);
    }
    private MechanicsEngine.Result preserveCiaccona(ServerPlayer player,CharacterInstance role,MechanicsState state,boolean skillCancel,boolean shadowAlreadyCreated) {
        if(running==null)return MECHANICS.ciacconaInterrupted(state,context(player,role),"",0,skillCancel);
        Running previous=running;
        var result=shadowAlreadyCreated?new MechanicsEngine.Result(true,"ok",state,List.of()):MECHANICS.ciacconaInterrupted(state,context(player,role),previous.action.id(),(int)Math.max(1,previous.completionTick-PlatformRuntime.time(player)),skillCancel);
        var before=mechanics.get(role.instanceId());int originalInstrument=before==null?1:Math.max(1,before.timer("ciaccona_instrument"));
        if(!shadowAlreadyCreated)result=new MechanicsEngine.Result(result.accepted(),result.reason(),result.state(),result.effects().stream().map(e->{
            if(e.kind()!=MechanicsEngine.Kind.SPAWN_FIELD||!e.id().startsWith("ciaccona_shadow_"))return e;
            var values=new HashMap<>(e.values());values.put("instrument",(double)originalInstrument);return new MechanicsEngine.Effect(e.kind(),e.id(),e.amount(),e.durationTicks(),values);
        }).toList());
        if(shadowAlreadyCreated||result.effects().stream().anyMatch(e->e.kind()==MechanicsEngine.Kind.SPAWN_FIELD&&e.id().startsWith("ciaccona_shadow_"))) {
            Running copy=new Running(previous.weapon,previous.action,previous.hits,previous.start,++sequence);
            copy.ledger.addAll(previous.ledger);copy.resourceHit=previous.resourceHit;copy.marks=previous.marks;copy.independent=true;copy.automatic=true;
            copy.anchor=player.position();copy.anchorRadius=previous.action.geometry().rangeBlocks();copy.fixedAim=player.getLookAngle();secondary.add(copy);
        }
        return result;
    }
    private boolean canCiacconaSkillCancel(CharacterInstance role) {
        return running!=null&&running.weapon.equals(role.instanceId())&&role.character()==CharacterId.CIACCONA
            &&(running.action.id().startsWith("ciaccona/basic_")||running.action.id().startsWith("ciaccona/air_")||running.action.id().contains("/heavy"));
    }
    public boolean allowsVoluntarySwitch(ServerPlayer player) {
        var role=ModAttachments.team(player).state().active().orElse(null);
        if(role==null||!role.alive())return true;
        var state=mechanics.get(role.instanceId());
        // Preview the same authoritative transition before changing team slots.
        // A later rejected SWITCH_OUT cannot undo an already switched body.
        return state==null||MECHANICS.reduce(state,MechanicsEngine.Input.SWITCH_OUT,context(player,role)).accepted();
    }
    public void cancelForSwitch(ServerPlayer player) {
        var team=ModAttachments.team(player).state();
        // Source Iuno outro explicitly preserves an already accepted Perfection
        // when this switch really qualifies for the concerto outro.
        if(team.introOutroReady()&&running!=null&&team.active().filter(role->role.character()==CharacterId.IUNO&&role.instanceId().equals(running.weapon)).isPresent()
            &&running.action.id().startsWith("iuno/heavy_perfection")) {
            Running previous=running;
            Running copy=new Running(previous.weapon,previous.action,previous.hits,previous.start,++sequence);
            copy.ledger.addAll(previous.ledger);copy.resourceHit=previous.resourceHit;copy.marks=previous.marks;
            copy.acceptedQualifications=previous.acceptedQualifications;
            copy.independent=true;copy.automatic=true;copy.anchor=player.position();
            copy.anchorRadius=previous.action.geometry().rangeBlocks();copy.fixedAim=player.getLookAngle();
            copy.visuals=previous.visuals;copy.visuals.forEach(ConstructEntity::detachFromOwner);
            previous.visuals=List.of();secondary.add(copy);
        }
        ModAttachments.team(player).state().active().filter(r->r.character()==CharacterId.CIACCONA&&r.alive()).ifPresent(role->{
            var result=preserveCiaccona(player,role,stateFor(role,"ciaccona"),false);
            if(result.accepted()){commit(role,result.state());applyEffects(player,role,result.effects());}
        });
        cancel(player);
    }
    private boolean canSwitchStanceDuringBasic(CharacterInstance role) {
        var state = mechanics.get(role.instanceId());
        return running != null && role.character() == CharacterId.YANGYANG_XUANLING && running.weapon.equals(role.instanceId())
            && state != null && state.timer("stance_basic_window") > 0
            && (running.action.id().startsWith("yangyang_xuanling/basic_") || running.action.id().startsWith("yangyang_xuanling/feather_basic_"));
    }
    private boolean canLinkAction(ServerPlayer player,CharacterInstance role,MechanicsEngine.Input input,MechanicsEngine.Result preview) {
        if(running==null||!running.weapon.equals(role.instanceId()))return false;
        var state=mechanics.get(role.instanceId());if(state==null)return false;
        int lastHit=running.action.hitTimesTicks().isEmpty()?0:running.action.hitTimesTicks().getLast();
        return SkillLinkRules.canReplace(state,running.action.id(),input,preview,PlatformRuntime.time(player)-running.start,lastHit);
    }
    public boolean skillEvadeWindow(ServerPlayer player) {
        var team = ModAttachments.team(player).state(); var role = team.active().orElse(null);
        if(role!=null&&role.alive()&&team.canAct()&&role.character()==CharacterId.SUISUI&&ruleState(role).timer("suisui_cloud_evade")>0)return true;
        if(role!=null&&team.canAct()&&ChisaTraversalRules.evadeWindow(mechanics.get(role.instanceId())))return true;
        if (running == null || role == null || !team.canAct() || !role.alive() || !running.weapon.equals(role.instanceId())) return false;
        var state = mechanics.get(role.instanceId());
        return state != null && SkillEvadeRules.activeAt(state, running.action.id(), running.start, PlatformRuntime.time(player), running.action.durationTicks());
    }
    public boolean confirmedSkillEvade(ServerPlayer player) {
        var active=ModAttachments.team(player).state().active().orElse(null);
        if(active!=null&&Set.of(CharacterId.CARLOTTA,CharacterId.ZHEZHI).contains(active.character())) {
            var result=MECHANICS.reduce(ruleState(active),MechanicsEngine.Input.PERFECT_DODGE,context(player,active));
            if(result.accepted()){commit(active,result.state());RolePassiveRuntime.updateMechanics(player,active,result.state());applyEffects(player,active,result.effects());return true;}
        }
        if(active!=null&&active.character()==CharacterId.SUISUI&&ruleState(active).timer("suisui_cloud_evade")>0){
            var result=MECHANICS.reduce(ruleState(active),MechanicsEngine.Input.PERFECT_DODGE,context(player,active));
            if(result.accepted()){commit(active,result.state());RolePassiveRuntime.updateMechanics(player,active,result.state());applyEffects(player,active,result.effects());return true;}
        }
        if (!skillEvadeWindow(player)) return false;
        var role = ModAttachments.team(player).state().active().orElseThrow();
        var state=mechanics.get(role.instanceId());
        var next = ChisaTraversalRules.evadeWindow(state)?ChisaTraversalRules.confirmedEvade(state)
            :SkillEvadeRules.confirmedSuccess(state, running.action.id());
        commit(role, next); RolePassiveRuntime.updateMechanics(player, role, next);
        return true;
    }
    public void releaseDodgeMovement(ServerPlayer player) {
        ModAttachments.team(player).state().active().filter(role->role.character()==CharacterId.CHISA).ifPresent(role->{
            var state=mechanics.get(role.instanceId());if(state==null)return;
            var next=ChisaTraversalRules.release(state);commit(role,next);RolePassiveRuntime.updateMechanics(player,role,next);
            specialMovement.tick(player,role,next);Network.sync(player);
        });
    }
    public void confirmedKill(ServerPlayer player,CharacterInstance role,LivingEntity target) {
        confirmedKill(player,role,target,PlatformRuntime.HitOrigin.AUTONOMOUS);
    }
    public CharacterInstance effectDefeatRecipient(ServerPlayer player,CharacterInstance source,LivingEntity target,String cause){
        return PlatformRuntime.defeatCredit(player,source,target,cause).role();
    }
    boolean hasFrostChase(ServerPlayer player,LivingEntity target){
        return fiveWorld.photoResistanceReduction(player,target,"glacio")>0;
    }
    public void nearbyEnemyDefeated(ServerPlayer player,LivingEntity target,boolean ownAttack){soloTargets.nearbyDefeat(player,target,ownAttack);}
    /** One callback per real explosion, independent of its collateral target count.
     * Nearby means12 blocks in the MC adaptation, measured from the active body. */
    public void fusionBurstObserved(ServerPlayer player,LivingEntity center) {
        if(player.level()!=center.level()||player.distanceToSqr(center)>144||!player.isAlive())return;
        var team=ModAttachments.team(player).state();var actor=team.active().orElse(null);
        if(actor==null||!actor.alive())return;
        for(int i=0;i<3;i++) {
            var owner=team.at(i).orElse(null);
            if(owner!=null&&owner.alive()&&owner.character()==CharacterId.DENIA)
                sixEvent(player,owner,actor,"denia_fusion_detonation",center,Map.of());
        }
    }
    public void confirmedKill(ServerPlayer player,CharacterInstance role,LivingEntity target,PlatformRuntime.HitOrigin origin) {
        confirmedKill(player,role,target,role.actionId(),origin);
    }
    public void confirmedKill(ServerPlayer player,CharacterInstance role,LivingEntity target,String action,PlatformRuntime.HitOrigin origin) {
        if(!role.alive()||!player.isAlive()||target instanceof net.minecraft.world.entity.player.Player
            ||target.isAlive()||target.getHealth()>0||creditedKills.contains(target)||teamRole(player,role.instanceId()).orElse(null)!=role)return;
        creditedKills.add(target);fiveWorld.defeated(player,role);soloTargets.killed(player,role,target,!action.startsWith("echo/")
            &&(origin==PlatformRuntime.HitOrigin.DIRECT_ACTIVE||origin==PlatformRuntime.HitOrigin.DIRECT_PROJECTILE));
        if(role.character()==CharacterId.CHISA){var next=ChisaTraversalRules.ownKill(stateFor(role,"chisa"));commit(role,next);RolePassiveRuntime.updateMechanics(player,role,next);}
        Network.sync(player);
    }
    /** Called after an actual received direct hit; physical airborne motion is checked on the next tick. */
    public void receivedImpact(ServerPlayer player) {
        ModAttachments.team(player).state().active().filter(role->role.character()==CharacterId.CHISA&&role.alive()).ifPresent(role->{
            var next=ChisaTraversalRules.receivedHit(stateFor(role,"chisa"));commit(role,next);RolePassiveRuntime.updateMechanics(player,role,next);
        });
    }
    public boolean isDirectMelee(CharacterInstance role, String actionId) {
        String slug = McWuwa.catalog().requireByWeapon(role.character().weaponPath()).characterId();
        return McWuwa.catalog().actionMappings(slug).stream().filter(a -> a.id().equals(actionId))
            .anyMatch(a -> a.geometry() != null && "melee".equals(a.geometry().delivery()));
    }
    public void confirmedHit(ServerPlayer player, CharacterInstance role, LivingEntity target, String kind) {
        confirmedHit(player, role, target, kind, role.actionId(), PlatformRuntime.HitOrigin.AUTONOMOUS);
    }
    boolean suisuiInherentAttack(CharacterInstance role,String action){
        return running!=null&&running.suisuiInherentQualified&&running.weapon.equals(role.instanceId())&&running.action.id().equals(action);
    }
    private void triggerYinlinJudgment(ServerPlayer player,LivingEntity target) {
        if(relayInProgress||!punishedTargets.containsKey(target))return;
        var team=ModAttachments.team(player).state();
        for(int slot=0;slot<3;slot++) {
            var owner=team.at(slot).orElse(null);
            if(owner==null||!owner.alive())continue;
            if (owner.character() == CharacterId.YINLIN && owner.cooldown("yinlin_judgment") == 0
                && EffectRuntime.hasOwnedEffect(target,player.getUUID(),owner.instanceId(),"yinlin_punishment")) {
                owner.setCooldown("yinlin_judgment", 20);
                var definition = McWuwa.catalog().requireByWeapon(owner.character().weaponPath());
                var damage = definition.requireDamage("1302020041");
                try {
                    relayInProgress = true;
                    for (var entry : List.copyOf(punishedTargets.entrySet())) {
                        LivingEntity marked = entry.getKey();
                        if (entry.getValue().equals(owner.instanceId()) && marked.level() == player.level() && marked.isAlive()
                            && EffectRuntime.hasOwnedEffect(marked,player.getUUID(),owner.instanceId(),"yinlin_punishment")){
                            if(PlatformRuntime.nativeHit(player, owner, marked, damage, 0, 1, "yinlin/judgment_thunder", PlatformRuntime.HitOrigin.AUTONOMOUS))
                                SkillModelProfiles.light(player,owner,"yinlin/judgment_contact",marked.position(),12,false);
                        }
                    }
                } finally { relayInProgress = false; }
            }
        }
    }
    public void observedWorldDamage(ServerPlayer player,LivingEntity target){observedWorldDamage(player,target,null);}
    public void observedWorldDamage(ServerPlayer player,LivingEntity target,CharacterInstance attacker){
        triggerYinlinJudgment(player,target);
        tideWorld.receivedDamage(player,target,attacker,(owner,candidate,action)->tideNativeHit(player,owner,candidate,action));
    }
    public void confirmedHit(ServerPlayer player, CharacterInstance role, LivingEntity target, String kind, String actionId, PlatformRuntime.HitOrigin origin) {
        confirmedHit(player,role,target,kind,actionId,origin,cn.zgnhit.mcwuwa.content.mechanics.CoordinatedHitRules.coordinated(actionId,""));
    }
    /** Accepted damage from every delivery path; does not fabricate a normal hit event. */
    public void activeTeamDamage(ServerPlayer player,CharacterInstance role,LivingEntity target,String actionId){
        if(role==null||!role.alive())return;
        passiveEvent(player,role,WeaponPassiveEngine.Trigger.OWNER_DAMAGE,actionId+":"+sequence);
        if(EffectRuntime.hasEffect(target,"spectro_frazzle"))passiveEvent(player,role,WeaponPassiveEngine.Trigger.DAMAGE_SPECTRO_FRAZZLE,actionId+":"+sequence);
        // Both ordinary hits and accepted elemental-effect damage reach here.
        // Keep crane/rain ownership and their self-recursion exclusions in the
        // world executor; do not simulate another direct hit for an effect.
        fiveWorld.damage(player,role,target,actionId);
        tideWorld.activeDamage(player,role,target,actionId);
        if(role.character().sourceId()==1306){
            var shield=fiveWorld.augustaDamageShield(player,role,PlatformRuntime.projectedMaxHealth(player,combatStats(player,role).health())/CombatMath.HEALTH_SCALE);
            if(shield!=null)grantShield(player,role,role,"augusta_honor",CombatMath.mappedHealth(shield.amount()),PlatformRuntime.time(player)+shield.ticks(),true);
        }
        var activeTeam=ModAttachments.team(player).state();
        if(activeTeam.active().orElse(null)==role&&target.isAlive()&&!(target instanceof net.minecraft.world.entity.decoration.ArmorStand)&&PlatformRuntime.canAttack(player,target))for(int slot=0;slot<3;slot++){
            var owner=activeTeam.at(slot).orElse(null);
            if(owner==null||!owner.alive()||owner.character()!=CharacterId.HSIN)continue;
            var result=MECHANICS.onActiveTeamDamage(ruleState(owner),context(player,owner),actionId);
            if(result.accepted()){
                commit(owner,result.state());RolePassiveRuntime.updateMechanics(player,owner,result.state());
                hsinEdictTargets.put(owner.instanceId(),target);
            }
        }
    }
    public void confirmedHit(ServerPlayer player, CharacterInstance role, LivingEntity target, String kind, String actionId, PlatformRuntime.HitOrigin origin,boolean coordinated) {
        activeTeamDamage(player,role,target,actionId);
        sixWorld.damage(player,role,target,actionId,origin==PlatformRuntime.HitOrigin.DIRECT_ACTIVE||origin==PlatformRuntime.HitOrigin.DIRECT_PROJECTILE,this::ruleState,sixCallbacks(player));
        tideWorld.damage(player,role,target,actionId,origin,coordinated,(owner,candidate,action)->tideNativeHit(player,owner,candidate,action));
        sonatas.event(player, role, "HIT", EffectRuntime.hasEffect(target, "aero_erosion"), offTuneEfficiency(player, role), 0);
        if(role.character()==CharacterId.CIACCONA&&EffectRuntime.hasEffect(target,"aero_erosion"))
            EffectRuntime.applyStatDebuff(player,role,target,cn.zgnhit.mcwuwa.core.AttributeModifiers.Zone.RESISTANCE_REDUCTION,"woodland_aria/aero",.10+.015*(role.refinement()-1),400,
                new cn.zgnhit.mcwuwa.core.AttributeModifiers.Selector(Set.of(cn.zgnhit.mcwuwa.core.Element.AERO),Set.of(),false,false));
        if(Math.max(EffectRuntime.layers(target,"spectro_frazzle"),fiveWorld.emberStacks(player,target))>=10)sonatas.event(player,role,"SPECTRO_10_HIT",false,0,0);
        if (kind.equals("liberation")) sonatas.event(player, role, "LIBERATION_HIT", false, 0, 0);
        if(kind.equals("echo"))sonatas.event(player,role,"ECHO_HIT",false,0,0);
        if(kind.equals("heavy"))sonatas.event(player,role,"HEAVY_HIT",false,0,0);
        if (kind.equals("echo")) passiveEvent(player, role, WeaponPassiveEngine.Trigger.ECHO_DAMAGE, Long.toString(sequence));
        if (kind.equals("heavy")) passiveEvent(player, role, WeaponPassiveEngine.Trigger.HEAVY_DAMAGE, actionId + ":" + sequence);
        if (kind.equals("basic")) passiveEvent(player, role, WeaponPassiveEngine.Trigger.BASIC_DAMAGE, actionId + ":" + sequence);
        if (kind.equals("skill")) passiveEvent(player, role, WeaponPassiveEngine.Trigger.SKILL_DAMAGE, actionId + ":" + sequence);
        if (relayInProgress) {soloTargets.damage(player,role,target);return;}
        long now = PlatformRuntime.time(player); var team = ModAttachments.team(player).state();
        soloTargets.directHit(player,role,target,actionId,origin);
        for(Buff buff:List.copyOf(buffs))if(buff.effect.id().equals("chisa_outro")&&applies(buff,role)) {
            for(int index=0;index<3;index++)team.at(index).filter(owner->owner.alive()&&owner.instanceId().equals(buff.owner)).ifPresent(owner->{
                for(String effect:OutroRules.CAP_EFFECTS)EffectRuntime.raiseCap(player,owner,target,effect,"chisa_outro",3,300,EffectCapLedger.Lifetime.TIMED);
            });
        }
        for(var buff:buffs)if(buff.expires>now&&buff.effect.id().equals("rover_aero_erosion_cap")&&(buff.recipient==null||buff.recipient.equals(role.instanceId()))) {
            for(int index=0;index<3;index++)if(team.at(index).isPresent()) {
                var owner=team.at(index).orElseThrow();
                if(owner.alive()&&owner.instanceId().equals(buff.owner))EffectRuntime.raiseCap(player,owner,target,"aero_erosion","rover_aero_erosion_cap",(int)buff.effect.amount(),
                    buff.effect.values().getOrDefault("onHitDurationTicks",200d).longValue(),EffectCapLedger.Lifetime.TIMED);
            }
        }
        triggerYinlinJudgment(player,target);
        for (int slot = 0; slot < 3; slot++) if (team.at(slot).isPresent()) {
            var owner = team.at(slot).orElseThrow(); if (!owner.alive()) continue;
            boolean direct = origin == PlatformRuntime.HitOrigin.DIRECT_ACTIVE || origin == PlatformRuntime.HitOrigin.DIRECT_PROJECTILE;
            if (owner.character() == CharacterId.CHISA && direct && chisaScarReady.getOrDefault(target, 0L) <= now
                && EffectRuntime.hasOwnedEffect(target,player.getUUID(),owner.instanceId(),"chisa_void_scar")) {
                chisaScarReady.put(target, now + (owner.chain()>=4?20:40));
                applyMark(player, owner, target, new MechanicsEngine.Effect(MechanicsEngine.Kind.MARK_TARGET, "havoc_bane", 1, 0, Map.of()));
            }
        }
    }
    private static boolean sharedEpiphany(List<ActionDamageMapping> actions){
        return actions.size()==2&&actions.get(0).id().equals("jinhsi/epiphany_rays")
            &&actions.get(1).id().equals("jinhsi/epiphany_final");
    }
    private double epiphanyBonus(CharacterInstance role,String effect,long now){
        return buffs.stream().filter(b->b.owner.equals(role.instanceId())&&b.effect.id().equals(effect)&&b.expires>now)
            .mapToDouble(b->b.effect.amount()).sum();
    }
    private void snapshotEpiphany(ServerPlayer player,CharacterInstance role,Running cast){
        if(!Set.of("jinhsi/epiphany_rays","jinhsi/epiphany_final").contains(cast.action.id()))return;
        long now=PlatformRuntime.time(player);
        cast.jinhsiCastBonus=new JinhsiCastBonus(cast.action.id().endsWith("/epiphany_final")
            ?epiphanyBonus(role,"jinhsi_epiphany_additive_multiplier",now):0,
            epiphanyBonus(role,"jinhsi_epiphany_damage_bonus",now));
    }
    private void start(ServerPlayer player, CharacterInstance role, CharacterDefinition data, ActionDamageMapping action) {
        List<CharacterDefinition.DamageDefinition> hits = new ArrayList<>();
        for (var group : action.hitGroups()) for (int i = 0; i < group.count(); i++) hits.add(data.requireDamage(group.damageId()));
        if (hits.size() != action.hitTimesTicks().size()) throw new IllegalStateException("hit timeline count mismatch " + action.id());
        var state = mechanics.get(role.instanceId());
        if (state != null) {
            var next = ChisaSawRules.actionStarted(RoverInputRules.actionStarted(HiyukiIaiRules.actionStarted(InterruptionRules.onAction(state, action.id()),action.id(),action.durationTicks()),action.id(),action.durationTicks()),action.id());
            next=ChisaTraversalRules.actionStarted(next,action.id(),context(player,role).airborne());
            next=ChisaScissorsRules.actionStarted(next,action.id(),action.durationTicks());
            next=CartethyiaAttackRules.actionStarted(next,action.id(),action.durationTicks());
            next=GameplayResourceRules.actionStarted(next,action.id(),action.durationTicks());
            // The continuation window must survive the intro/body itself; duration is an MC timeline.
            if(next.nextBasic()>1) {
                var timers=new HashMap<>(next.timers());timers.put("combo_window",Math.max(next.timer("combo_window"),action.durationTicks()+30));
                if(action.id().equals("lupa/basic_03"))timers.put("lupa_basic_air_ready",action.durationTicks()+20);
                // Source1210/node2 offers Combined Thrust after these paid attacks.
                // Charge2's final contact is tick32, later than its old30-tick gate.
                if(next.characterId().equals("aemeath")&&next.timer("combined_thrust")>0
                    &&Set.of("aemeath/basic_02","aemeath/basic_03","aemeath/basic_04","aemeath/heavy","aemeath/heavy_charge_2","aemeath/dodge_counter",
                        "aemeath/mech_basic_02","aemeath/mech_basic_03","aemeath/mech_basic_04","aemeath/mech_heavy","aemeath/mech_heavy_charge_2","aemeath/mech_dodge_counter").contains(action.id()))
                    timers.put("combined_thrust",Math.max(next.timer("combined_thrust"),action.durationTicks()+30));
                next=new MechanicsState(next.characterId(),next.form(),next.mode(),next.chain(),next.nextBasic(),next.resources(),timers);
            }
            commit(role,next);RolePassiveRuntime.updateMechanics(player,role,next);
        }
        role.beginAction(action.id()); running = new Running(role.instanceId(), action, List.copyOf(hits), PlatformRuntime.time(player), ++sequence);
        snapshotEpiphany(player,role,running);
        if(action.id().equals("lynae/skill_additive"))
            running.lynaePremixBonus=epiphanyBonus(role,"lynae_premix_bonus",PlatformRuntime.time(player));
        if(Set.of("phoebe/skill","phoebe/skill_teleport").contains(action.id()))phoebeRing(player,role).ifPresent(f->{running.anchor=f.center;running.anchorRadius=f.area.radius();});
        specialMovement.actionStarted(player,role,mechanics.get(role.instanceId()),action.id(),action.durationTicks());
        if(GroundImpactGeometry.lynaeImpact(action.id())) {
            running.anchor=GroundImpactGeometry.floor(player);running.anchorRadius=action.geometry().rangeBlocks();
        }
        if(action.id().startsWith("phrolova/hecate_"))fields.stream().filter(field->hecateField(field,player,role)).findFirst().ifPresent(field->{running.anchor=field.center;running.anchorRadius=action.geometry().rangeBlocks();});
        running.visuals=SkillModelProfiles.start(player,role,action.id(),action.durationTicks(),ruleState(role).timer("ciaccona_instrument"));
        HecateEntity.owned(player,role.instanceId()).ifPresent(entity->entity.animateAttack(action.id(),action.durationTicks()));
        running.marks = List.copyOf(pendingMarks.getOrDefault(role.instanceId(), List.of())); pendingMarks.remove(role.instanceId());
        String actionName = action.id().substring(action.id().lastIndexOf('/') + 1);
        boolean skillCast = actionName.startsWith("skill") || actionName.contains("_skill")
            || Set.of("confession","bloom_dark_heart", "undying_reverie", "epiphany_rays").contains(actionName);
        WeaponPassiveEngine.Trigger trigger = actionName.startsWith("intro") || actionName.endsWith("_intro") ? WeaponPassiveEngine.Trigger.INTRO_CAST
            : skillCast ? WeaponPassiveEngine.Trigger.SKILL_CAST : actionName.contains("basic_") ? WeaponPassiveEngine.Trigger.BASIC_CAST : null;
        if (trigger != null) passiveEvent(player, role, trigger, Long.toString(sequence));


        boolean liberationSkill=actionName.startsWith("liberation")||action.id().startsWith("carlotta/death_knell_")||action.id().startsWith("carlotta/fatal_finale");
        if(liberationSkill){EchoRuntime.onLiberationCast(player,role,action.id()+":"+running.sequence);sixWorld.teammateLiberation(player,role,id->teamRole(player,id).orElse(null),sixCallbacks(player));}
        if(trigger==WeaponPassiveEngine.Trigger.INTRO_CAST) {
            EchoRuntime.onIntroCast(player,role);
            var members=ModAttachments.team(player).state();for(int slot=0;slot<3;slot++)members.at(slot).filter(owner->owner.alive()&&owner.character()==CharacterId.LUPA)
                .ifPresent(owner->sixEvent(player,owner,role,"lupa_team_intro",null,Map.of()));
        }
        if (action.id().contains("/unbound_wind_")) passiveEvent(player, role, WeaponPassiveEngine.Trigger.ROVER_UNBOUND_WIND_CAST, Long.toString(sequence));
        if(liberationSkill)passiveEvent(player,role,WeaponPassiveEngine.Trigger.LIBERATION_CAST,action.id()+":"+sequence);
        targetReactions.actionStarted(player,role,action,(owner,lock)->collapseWordLock(player,owner,lock));
        if(role.character()==CharacterId.PHROLOVA&&role.chain()>=6&&Set.of("phrolova/movement_death","phrolova/undying_reverie").contains(action.id()))startPhrolovaIllusion(player,role,data);
        Network.visual(player, new Network.Visual(player.getId(), role.instanceId(), "mc_wuwa:" + action.id(), running.start, action.durationTicks(), running.sequence, ""));
    }
    /** Source1610/node7: switching a depleted stance completes the interrupted normal basic
     * through an independent afterimage. Fixed MC damage uses the old attack's remaining base
     * coefficients and cast ATK; no crit/bonuses, anomaly applications or resource transactions. */
    private void preserveXuanlingPhantom(ServerPlayer player,CharacterInstance role,long now) {
        if(running==null||role.character()!=CharacterId.YANGYANG_XUANLING
            ||!(running.action.id().startsWith("yangyang_xuanling/basic_")||running.action.id().startsWith("yangyang_xuanling/feather_basic_")))return;
        if(running.action.hitTimesTicks().stream().noneMatch(t->running.start+t>=now))return;
        Running ghost=new Running(role.instanceId(),running.action,running.hits,running.start,++sequence);
        ghost.ledger.addAll(running.ledger);ghost.automatic=true;ghost.independent=true;ghost.fixedPhantom=true;
        ghost.phantomAttack=combatStats(player,role).attack();ghost.anchor=player.position();ghost.fixedAim=player.getLookAngle();ghost.anchorRadius=running.action.geometry().rangeBlocks();
        int remaining=(int)Math.max(1,running.start+running.action.durationTicks()-now);
        ghost.visuals=List.of(ConstructEntity.spawn(player.serverLevel(),player,role.instanceId(),"xuanling_feathers",ghost.anchor,remaining,.9f,false,"attack"));
        secondary.add(ghost);
    }
    private void startPhrolovaIllusion(ServerPlayer player,CharacterInstance role,CharacterDefinition definition) {
        var mapping=RoleProcDefinitions.phrolovaIllusionTimeline(definition);
        Running summon=new Running(role.instanceId(),mapping,List.of(RoleProcDefinitions.phrolovaSixthChain(definition)),PlatformRuntime.time(player),++sequence);
        summon.automatic=true;summon.independent=true;
        // C6 follows the accepted normal-form death/reverie cast; it is not a conducting command.
        summon.anchor=player.position().add(Vec3.directionFromRotation(0,player.getYRot()).scale(2));
        summon.anchorRadius=4;
        summon.visuals=List.of(ConstructEntity.spawn(player.serverLevel(),player,role.instanceId(),"hecate",summon.anchor,mapping.durationTicks(),1f,false,"illusion"));
        secondary.add(summon);
    }
    private void pauseCinematicTimers(){
        buffs.replaceAll(b->new Buff(b.owner,b.recipient,b.effect,CinematicTimeRules.postpone(b.expires,1)));
        shields.pauseOneTick();sonatas.pauseOneTick();unison.pauseOneTick();
        tideWorld.pauseOneTick();sixWorld.pauseOneTick();fiveWorld.pauseOneTick();threeWorld.pauseOneTick();
        soloTargets.pauseOneTick();targetReactions.pauseOneTick();swordShadows.pauseOneTick();responseCooldown.pauseOneTick();
        specialMovement.pauseOneTick();
        chisaScarReady.replaceAll((target,t)->CinematicTimeRules.postpone(t,1));phoebeRefractionReady.replaceAll((id,t)->CinematicTimeRules.postpone(t,1));
        fields.replaceAll(f->{
            var effect=f.effect;
            // The shadow's delayed solo starts on the same paused gameplay clock
            // as its lifetime. Do not spend its pending fourth-basic time in a shot.
            if(effect.values().containsKey("soloReadyAt")){
                var values=new HashMap<>(effect.values());
                values.put("soloReadyAt",values.get("soloReadyAt")+1);
                effect=new MechanicsEngine.Effect(effect.kind(),effect.id(),effect.amount(),effect.durationTicks(),values);
            }
            return new Field(f.owner,effect,f.center,new FieldArea(f.area.dimension(),f.area.x(),f.area.y(),f.area.z(),f.area.radius(),CinematicTimeRules.postpone(f.area.expiresAt(),1)),f.visual);
        });
        secondary.forEach(r->{r.start++;r.completionTick++;});
        weaponPassives.replaceAll((id,state)->{
            var active=new HashMap<String,WeaponPassiveEngine.Buff>();
            state.buffs().forEach((key,b)->active.put(key,new WeaponPassiveEngine.Buff(b.id(),b.attribute(),b.value(),b.scope(),b.damageTag(),b.element(),b.targetCondition(),CinematicTimeRules.postpone(b.expiresAtTick(),1),b.stacks(),b.endsOnSwitch(),b.sameNameHighestOnly())));
            var ready=new HashMap<String,Long>();state.readyTicks().forEach((key,t)->ready.put(key,CinematicTimeRules.postpone(t,1)));
            return new WeaponPassiveEngine.State(active,ready,state.lastActions());
        });
    }
    public void tick(ServerPlayer player) {
        var team = ModAttachments.team(player).state(); long now = PlatformRuntime.time(player);
        if(hsinOpeningPoseOwner!=null&&(now>=hsinOpeningPoseUntil||running!=null))hsinOpeningPoseOwner=null;
        boolean cinematic=CinematicTimeRuntime.paused(player);
        if(cinematic)pauseCinematicTimers();
        currentTick=now;activeRoleId=team.active().map(CharacterInstance::instanceId).orElse(null);
        if(sixJumpHoldQueued&&now-sixJumpSince>=8){
            if(!team.wuwaMode()||!sixJump(player,"SIX_JUMP_HOLD")){
                sixJumpOwner=sixJumpShortOwner=null;sixJumpHeld=sixJumpHoldQueued=sixJumpReleased=false;
            }
        }
        if(lupaEscapeOwner!=null&&now>lupaEscapeAt) {
            if(lupaEscapeOwner.equals(activeRoleId)&&player.isAlive()){player.setDeltaMovement(Vec3.ZERO);player.hurtTime=0;player.hurtMarked=true;}
            lupaEscapeOwner=null;
        }
        if(!team.wuwaMode())leaveExploration(player);
        if(!team.wuwaMode())cancelLiberationCharge(player);
        for(int slot=0;slot<3;slot++)team.at(slot).filter(role->!role.alive()).ifPresent(role->{
            EffectRuntime.clearOwnedStasis(role.instanceId());EffectRuntime.clearFocusSlow(role.instanceId());
            var state=mechanics.get(role.instanceId());if(HiyukiReturnChargeRules.isCharging(state))commit(role,InterruptionRules.cancelled(state));
            state=mechanics.get(role.instanceId());
            if(state!=null) {
                // Online cooldowns still advance while downed. Do not overwrite them with the frozen
                // mechanics snapshot on revival, and do not run resource/healing ticks for a dead owner.
                var timers=new HashMap<>(state.timers());
                timers.remove("unison");UnisonRules.COUNTERS.forEach(timers::remove);
                role.removeTemporaryState("jinhsi_unison");role.removeTemporaryState("hsin_unison");role.removeTemporaryState("suoming_unison");role.removeTemporaryState("unison_consumed_pending");
                for(String key:MechanicsEngine.persistentCooldownKeys(state.characterId())) {
                    int remaining=(int)Math.min(Integer.MAX_VALUE,role.cooldown("mechanics_"+key));
                    if(remaining>0)timers.put(key,remaining);else timers.remove(key);
                }
                // Charge recovery is still an online cooldown while downed.
                // Finish each charge and start the next recharge, without
                // executing a dead role's combat/resource/healing TICK.
                if(state.characterId().equals("changli")&&state.timer("changli_spent_charges")>0
                    &&!timers.containsKey("changli_skill_recharge")){
                    int spent=state.timer("changli_spent_charges")-1;
                    if(spent>0){timers.put("changli_spent_charges",spent);timers.put("changli_skill_recharge",240);role.setCooldown("mechanics_changli_skill_recharge",240);}
                    else timers.remove("changli_spent_charges");
                    role.setPersistentCounter("changli_spent_charges",spent);
                }
                mechanics.put(role.instanceId(),new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),state.nextBasic(),state.resources(),timers));
            }
        });
        if (charge.active() && !team.wuwaMode()) cancelCharge(player);
        // Holding a field's lifetime does not stop it accepting enemies that
        // enter its existing area during the liberation presentation.
        if(cinematic)for(var field:fields){
            if(field.effect.id().equals("phrolova_conducting_stasis"))phrolovaConductingStasis(player,field);
            if(field.effect.id().equals("phoebe_ring"))teamRole(player,field.owner)
                .filter(owner->owner.alive()&&owner.chain()>=6).ifPresent(owner->phoebeRingStasis(player,owner,field));
        }
        if(!cinematic){
        buffs.removeIf(b -> {
            if(b.expires>now)return false;
            if(b.effect.id().equals("phoebe_silent_prayer"))EffectRuntime.clearOwnedStatGroup(b.owner,"phoebe/silent_prayer");
            return true;
        });
        shields.prune(now);
        removeFields(f -> !fieldActive(f, player)&&!pendingFinalFieldHeal(f,player));
        removeFields(f -> f.effect.id().startsWith("phrolova_reserve_")
            &&teamRole(player,f.owner).map(owner->ModAttachments.team(player).state().active().orElse(null)==owner
                ||!ruleState(owner).form().equals("conducting")).orElse(true));
        for(int index=0;index<fields.size();index++) {
            var field=fields.get(index);
            if(field.effect.id().equals("phrolova_conducting_stasis"))phrolovaConductingStasis(player,field);
            if(field.effect.id().equals("shorekeeper_domain")){var domainField=field;teamRole(player,field.owner).ifPresent(owner->threeWorld.domain(player,owner,domainField.center,domainField.area.radius(),ruleState(owner)));}
            if(field.effect.id().startsWith("ciaccona_shadow_")&&field.effect.values().getOrDefault("soloC6",0d)>0&&now>=field.effect.values().getOrDefault("soloReadyAt",0d)) {
                var owner=teamRole(player,field.owner).orElse(null);
                if(owner!=null)ciacconaSoloHit(player,owner,field.center);
                var values=new HashMap<>(field.effect.values());values.put("soloC6",0d);
                var effect=new MechanicsEngine.Effect(field.effect.kind(),field.effect.id(),field.effect.amount(),field.effect.durationTicks(),values);
                field=new Field(field.owner,effect,field.center,field.area,field.visual);fields.set(index,field);
            }
            if((field.effect.id().startsWith("ciaccona_shadow_")||field.effect.id().equals("ciaccona_body_solo"))&&field.effect.durationTicks()==0&&(field.visual==null||field.visual.isRemoved())) {
                var owner=teamRole(player,field.owner).orElse(null);
                if(owner!=null)fields.set(index,new Field(field.owner,field.effect,field.center,field.area,ConstructEntity.spawn(player.serverLevel(),player,owner.instanceId(),ciacconaInstrument(field.effect,!field.effect.id().equals("ciaccona_body_solo")),field.center,12000,1f,field.effect.id().equals("ciaccona_body_solo"),"idle")));
            }
            if(field.visual instanceof HecateEntity hecate&&!hecate.isRemoved()) {
                Vec3 center=hecate.position();
                fields.set(index,new Field(field.owner,field.effect,center,new FieldArea(dimension(player),center.x,center.y,center.z,field.area.radius(),field.area.expiresAt()),hecate));
            }
        }
        targetReactions.tick(player,(owner,target,layers)->applyMark(player,owner,target,
            new MechanicsEngine.Effect(MechanicsEngine.Kind.MARK_TARGET,"havoc_bane",layers,0,Map.of())));
        for(Field field:fields)if(field.effect.kind()==MechanicsEngine.Kind.PULL_AREA)teamRole(player,field.owner).ifPresent(owner->EffectRuntime.pullToPoint(player,owner,field.center,field.area.radius(),field.effect.amount()));
        for (Field field : fields) if (field.effect.id().equals("lynae_paint") && now-(field.area.expiresAt()-field.effect.durationTicks())>0
            &&(now-(field.area.expiresAt()-field.effect.durationTicks()))%40==0) {
            var owner = teamRole(player, field.owner).orElseThrow();
            for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(field.center, field.center).inflate(field.area.radius()),
                t -> PlatformRuntime.canAttack(player, t) && field.area.contains(dimension(player), t.getX(), t.getY(), t.getZ(), now, true)))
                applyMark(player, owner, target, new MechanicsEngine.Effect(MechanicsEngine.Kind.MARK_TARGET,
                    field.effect.values().getOrDefault("strainMode",0d)>0 ? "strain_shifting" : "rupture_shifting", 1, 500, Map.of()));
            if(field.effect.values().getOrDefault("chain1Pull",0d)>0&&(now-(field.area.expiresAt()-field.effect.durationTicks()))%120==0)
                EffectRuntime.pullToPoint(player,owner,field.center,field.area.radius(),.2);
        }
        Set<UUID> completedDiffusionIntervals=new HashSet<>();
        for (int i = 0; i < 3; i++) team.at(i).ifPresent(role -> {
            if(!role.alive())return;
            MechanicsState state = stateFor(role, McWuwa.catalog().requireByWeapon(role.character().weaponPath()).characterId());
            if (state != null) {
                if(role.character()==CharacterId.LYNAE&&team.active().orElse(null)==role
                    &&player.getVehicle() instanceof cn.zgnhit.mcwuwa.platform.entity.SurveyMotorcycleEntity bike&&bike.getControllingPassenger()==player)
                    state=MechanicsEngine.vehicleCruise(state);
                if(role.character()==CharacterId.HSIN&&state.timer("edict_trigger")>0){
                    var target=hsinEdictTargets.get(role.instanceId());
                    if(target==null||!target.isAlive()||target.level()!=player.level()||!PlatformRuntime.canAttack(player,target)){
                        var timers=new HashMap<>(state.timers());timers.remove("edict_trigger");
                        state=new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),state.nextBasic(),state.resources(),timers);
                        hsinEdictTargets.remove(role.instanceId());
                    }
                }
                state=prepareTideState(player,role,state,true);
                state=fiveWorld.prepare(player,role,state);
                if(state.timer("cantarella_diffusion")==1)completedDiffusionIntervals.add(role.instanceId());
                var result = MECHANICS.reduce(state, MechanicsEngine.Input.TICK, context(player, role));
                if (result.accepted()) {
                    if(HsinTravelRules.wandering(state)&&!HsinTravelRules.wandering(result.state())&&running!=null&&running.weapon.equals(role.instanceId())&&running.action.id().equals("hsin/heavy_wander"))cancel(player);
                    if(state.characterId().equals("galbrena")&&state.timer("gal_raining")>0&&result.state().timer("gal_raining")==0
                        &&ModAttachments.team(player).state().active().orElse(null)==role
                        &&(running==null||running.weapon.equals(role.instanceId())&&running.action.id().endsWith("air_rain")))cancel(player);
                    commit(role, result.state()); RolePassiveRuntime.updateMechanics(player, role, result.state()); applyEffects(player, role, result.effects()); scheduleTickActions(player, role, result.effects());
                    // The default five-tick HUD cadence skips C3's two-tick
                    // score steps. Publish only actual conversion changes.
                    if(state.timer("phrolova_color_conversion")>0
                        &&(!state.resources().equals(result.state().resources())||result.state().timer("phrolova_color_conversion")==0))Network.sync(player);
                }
            }
        });
        threeWorld.outros(player,mechanics);
        for(int slot=0;slot<3;slot++){
            var dancer=team.at(slot).orElse(null);if(dancer==null||dancer.character()!=CharacterId.SUISUI)continue;
            var domain=fields.stream().filter(f->f.owner.equals(dancer.instanceId())&&f.effect.id().equals("suisui_landscape")&&fieldActive(f,player)).findFirst();
            boolean dance=dancer.alive()&&team.active().orElse(null)!=dancer&&ruleState(dancer).timer("suisui_immortal")>0&&domain.isPresent();
            if(!dance){var old=suisuiDancers.remove(dancer.instanceId());if(old!=null)old.discard();}
            else if(!suisuiDancers.containsKey(dancer.instanceId())||suisuiDancers.get(dancer.instanceId()).isRemoved()){
                var field=domain.orElseThrow();int remaining=(int)Math.max(1,Math.min(ruleState(dancer).timer("suisui_immortal"),field.area.expiresAt()-now));
                suisuiDancers.put(dancer.instanceId(),ConstructEntity.spawn(player.serverLevel(),player,dancer.instanceId(),"suisui_reserve_dancer",field.center.add(0,.08,0),remaining,1f,false,"attack"));
            }
        }
        suisuiDancers.entrySet().removeIf(entry->{if(teamRole(player,entry.getKey()).isEmpty()){entry.getValue().discard();return true;}return false;});
        for(Buff prayer:List.copyOf(buffs))if(prayer.effect.id().equals("phoebe_silent_prayer")&&prayer.expires>now)teamRole(player,prayer.owner).filter(owner->owner.alive()&&ruleState(owner).form().equals("confession")).ifPresent(owner->{
            for(var target:player.level().getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(12),e->PlatformRuntime.canAttack(player,e)&&player.distanceToSqr(e)<=144))
                EffectRuntime.applyStatDebuff(player,owner,target,AttributeModifiers.Zone.RESISTANCE_REDUCTION,"phoebe/silent_prayer",.10,2,new AttributeModifiers.Selector(Set.of(Element.SPECTRO),Set.of(),false,false));
        });
        for(Field ring:List.copyOf(fields))if(ring.effect.id().equals("phoebe_ring"))teamRole(player,ring.owner).filter(owner->owner.alive()&&owner.chain()>=6).ifPresent(owner->phoebeRingStasis(player,owner,ring));
        tideWorld.tick(player,id->teamRole(player,id).orElse(null),this::ruleState,completedDiffusionIntervals,(owner,target,action)->tideNativeHit(player,owner,target,action),(owner,recipient,flat,ratio)->tideHeal(player,owner,recipient,flat,ratio));
        sixWorld.tick(player,id->teamRole(player,id).orElse(null),this::ruleState,sixCallbacks(player));
        fiveWorld.tick(player,id->teamRole(player,id).orElse(null),(owner,target,action)->tideNativeHit(player,owner,target,action));
        team.active().ifPresent(owner->fiveWorld.photoFocus(player,owner,ruleState(owner)));
        }
        if (team.active().isEmpty() || !team.active().orElseThrow().alive()) { cancel(player); return; }
        var role = team.active().orElseThrow();
        var recitalState=mechanics.get(role.instanceId());
        if(role.character()==CharacterId.CIACCONA&&recitalState!=null) {
            var input=cn.zgnhit.mcwuwa.platform.MovementKeys.get(player);boolean moving=input.forward()||input.backward()||input.left()||input.right()||input.jump();
            if(moving&&(recitalState.timer("ciaccona_solo")>0||running!=null&&running.action.id().equals("ciaccona/basic_04"))) {
                var interrupted=preserveCiaccona(player,role,recitalState,false);commit(role,interrupted.state());applyEffects(player,role,interrupted.effects());cancel(player);recitalState=mechanics.get(role.instanceId());
            }
            String pose=recitalState.timer("ciaccona_performance")>0?"ciaccona/recital":recitalState.timer("ciaccona_solo")>0?"ciaccona/solo_"+switch(recitalState.timer("ciaccona_instrument")){case 2->"drum";case 3->"guitar";default->"mic";}:"";
            if(!pose.isEmpty()&&running==null&&(!role.instanceId().equals(recitalPoseOwner)||!pose.equals(ciacconaPoseAction)||now>=ciacconaPoseUntil)) {
                recitalPoseOwner=role.instanceId();ciacconaPoseAction=pose;int duration=pose.endsWith("recital")?recitalState.timer("ciaccona_performance"):40;ciacconaPoseUntil=now+duration;
                Network.visual(player,new Network.Visual(player.getId(),role.instanceId(),"mc_wuwa:"+pose,now,duration,++sequence,""));
            }
        }
        refreshUnison(player);
        if(!cinematic)swordShadows.tick(player,this::ruleState);
        if(!cinematic)for(int i=0;i<3;i++)team.at(i).filter(owner->owner!=role&&owner.alive()&&owner.character()==CharacterId.AERO_ROVER).ifPresent(owner->{
            var state=ruleState(owner);
            if(state.timer("rover_unbound_healing")>0&&state.timer("rover_emergency")==0&&role.health()<role.maxHealth()*.35){
                var timers=new HashMap<>(state.timers());timers.put("rover_emergency",200);
                ruleState(player,owner,new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),state.nextBasic(),state.resources(),timers));
                applyEffects(player,owner,List.of(new MechanicsEngine.Effect(MechanicsEngine.Kind.HEAL_ACTIVE,"rover_chain2_emergency",(role.maxHealth()-role.health())/CombatMath.HEALTH_SCALE*.10,0,Map.of("ignoreHealingBonus",1d))));
            }
        });
        if(heldAttackOwner!=null&&(!role.instanceId().equals(heldAttackOwner)||now-heldAttackSince>1200)) {heldAttackOwner=null;heldAttackMode=null;}
        if(!cinematic)soloTargets.tick(player,role);
        if(!cinematic&&role.character()==CharacterId.LUPA){
            // Source 1207: fifty real sprint ticks, not ordinary movement or a preview.
            boolean sprinting=team.wuwaMode()&&player.isSprinting()&&player.onGround()
                &&!player.isInWater()&&!player.isPassenger()&&!player.getAbilities().flying;
            sixEvent(player,role,role,"lupa_sprint",null,Map.of("sprinting",sprinting?1d:0d));
        }
        var movementState=mechanics.get(role.instanceId());
        threeWorld.active(player,movementState);
        if(role.character()==CharacterId.SUISUI&&role.actionId().equals("suisui/rain_basic_03")&&now%2==0)
            EffectRuntime.pullToPoint(player,role,player.position(),6,.2);
        if(role.character()==CharacterId.AEMEATH&&role.chain()>=1&&charge.active()&&movementState!=null
            &&(movementState.timer("immediate")>0||movementState.timer("aemeath_brilliance")>0))
            EffectRuntime.pullToPoint(player,role,player.position(),6,.2);
        for(int slot=0;slot<3;slot++)team.at(slot).filter(owner->owner.alive()&&owner.character()==CharacterId.HSIN).ifPresent(owner->{
            var hsin=ruleState(owner);if(!hsin.mode().equals("electro")||hsin.timer("heart_state")==0||!team.inCombat())return;
            for(var target:player.level().getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(8),e->PlatformRuntime.canAttack(player,e)&&player.distanceToSqr(e)<=64&&player.hasLineOfSight(e))) {
                soloTargets.electroAura(player,owner,target);
            }
        });
        if(role.character()==CharacterId.CHISA&&movementState!=null&&movementState.timer("chisa_glide")>0
            &&(cn.zgnhit.mcwuwa.platform.MovementKeys.get(player).jump()||cn.zgnhit.mcwuwa.platform.MovementKeys.get(player).shift()||player.isInWater()||player.isPassenger()||player.onClimbable()||player.getAbilities().flying))cancel(player);
        if(role.character()==CharacterId.QIUYUAN&&movementState!=null&&(movementState.timer("qiuyuan_dash_ended")>0
            ||movementState.timer("qiuyuan_dash")>0&&qiuyuanTargetNearby(player,5)))finishQiuyuanRun(player,role,true);
        if(!cinematic){specialMovement.tick(player,role,mechanics.get(role.instanceId()));
        fiveWorld.tickMovement(player,role);}
        if(role.character()==CharacterId.CHISA&&player.onGround()&&team.stamina().sprintHeld()
            &&role.instanceId().equals(chisaDodgeOwner)&&now-chisaDodgeSince>=4&&team.stamina().snapshot().counterUntil()>team.onlineTick())request(player,"REEL_COUNTER");
        if(deferredSawRelease!=null) {
            var release=deferredSawRelease;
            if(running==null||!running.weapon.equals(release.weapon)||running.start!=release.start||running.sequence!=release.sequence
                ||!role.instanceId().equals(release.weapon))deferredSawRelease=null;
            else if(now-running.start>=2) {deferredSawRelease=null;request(player,"BASIC_RELEASE");}
        }
        if (charge.active() && (!team.wuwaMode() || charge.readiness(role.instanceId(), now, OptionalInt.empty()) == ChargeState.Readiness.NO_START)) cancelCharge(player);
        if(charge.active()&&chargeWaitingForAttack&&running==null&&role.character()!=CharacterId.AEMEATH){
            var definition=McWuwa.catalog().requireByWeapon(role.character().weaponPath());
            var threshold=OptionalInt.of(definition.fullChargeTicks().orElse(1));
            if(charge.readiness(role.instanceId(),now,threshold)==ChargeState.Readiness.FULL){
                chargeWaitingForAttack=false;
                if(!request(player,"HEAVY_FULL"))cancelCharge(player);
            }
        }
        if(charge.active()&&role.character()==CharacterId.AEMEATH&&running==null){
            var state=ruleState(role);var definition=McWuwa.catalog().requireByWeapon(role.character().weaponPath());
            // Server elapsed time releases the held second stage. Immediate
            // Response uses the next server tick; neither trusts wire duration.
            var threshold=state.timer("immediate")>0||state.timer("aemeath_brilliance")>0?OptionalInt.of(1):definition.fullChargeTicks();
            if(charge.readiness(role.instanceId(),now,threshold)==ChargeState.Readiness.FULL&&!request(player,"HEAVY_FULL"))cancelCharge(player);
        }
        // Lynae can complete conversion automatically while the input remains held; its later release must not fire a second action.
        if (charge.active() && role.character() == CharacterId.LYNAE && mechanics.get(role.instanceId()).timer("converting") == 0
            && running != null && running.action.id().startsWith("lynae/inspiration")) charge.clear();
        if(charge.active()&&role.character()==CharacterId.HIYUKI&&mechanics.get(role.instanceId()).timer("charging")==0)charge.clear();
        if (running != null && (!role.instanceId().equals(running.weapon) || (!team.wuwaMode() && !running.action.id().contains("/intro")))) { cancel(player); return; }
        if (running != null) {
            long elapsed = now - running.start;
            if(running.action.id().equals("galbrena/hellwalk")&&elapsed==4&&running.ledger.add("hellwalk_fixed")){
                var source=new EffectSource(player.getUUID(),role.instanceId(),role.character(),UUID.randomUUID(),UUID.randomUUID());
                for(var target:player.level().getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(2.5),e->PlatformRuntime.canAttack(player,e)&&player.hasLineOfSight(e)))
                    if(PlatformRuntime.effectHit(player,source,target,Element.FUSION,666,"fixed/galbrena_hellwalk"))NewThreeRoleRuntime.confirmedHit(player,role,target,"galbrena/hellwalk");
            }
            if(!running.action.id().equals("chisa/severing_pincers")||!running.ledger.contains("pincers-closed"))
                for (int i = 0; i < running.hits.size(); i++) if (running.action.hitTimesTicks().get(i) == elapsed) {
                    if(i==running.hits.size()-1)chisaPincersClosed(player,role,running);
                    if(i==running.hits.size()-1&&running.action.id().equals("hiyuki/heavy")) {
                        var released=MECHANICS.hiyukiLastArrowReleased(ruleState(role),context(player,role));
                        commit(role,released.state());RolePassiveRuntime.updateMechanics(player,role,released.state());
                    }
                    hit(player, role, running, i, running.hits.get(i));
                }
        }
        for (Running additional : List.copyOf(secondary)) {
            if(cinematic)continue;
            long extraElapsed = now - additional.start;
            var owner = teamRole(player, additional.weapon);
            if (owner.isEmpty()||!owner.get().alive()) { SkillModelProfiles.end(additional.visuals);secondary.remove(additional); continue; }
            if(additional.action.id().startsWith("phrolova/hecate_")) {
                // A queued command must not outlive the actual summon or its owner form.
                var hecate=HecateEntity.owned(player,additional.weapon).orElse(null);
                if(hecate==null){SkillModelProfiles.end(additional.visuals);secondary.remove(additional);continue;}
                if(extraElapsed>=0&&additional.ledger.add("hecate_visual_started")) {
                    hecate.animateAttack(additional.action.id(),additional.action.durationTicks());
                    additional.anchor=hecate.position();
                }
            }
            for (int i = 0; i < additional.hits.size(); i++) if (additional.action.hitTimesTicks().get(i) == extraElapsed) hit(player, owner.get(), additional, i, additional.hits.get(i));
            if (extraElapsed >= additional.action.durationTicks()) {SkillModelProfiles.end(additional.visuals);secondary.remove(additional);}
        }
        if (running != null && now >= running.completionTick) {
            String completedAction=running.action.id();
            fiveWorld.completed(player,role,completedAction);
            boolean completedHit=running.resourceHit;
            int completedSawStage=ChisaSawRules.stage(running.action.id());
            var finished=mechanics.get(role.instanceId());if(finished!=null) {
                var next=ChisaTraversalRules.actionCompleted(finished,running.action.id());
                // Every role receives the completion event. A host-side role allowlist
                // silently skipped Sigrika's already implemented Talent consumption.
                var completion=MECHANICS.actionCompleted(next,context(player,role),completedAction);
                commit(role,completion.state());RolePassiveRuntime.updateMechanics(player,role,completion.state());
                applyEffects(player,role,completion.effects());
                if(Set.of("suoming","lucilla").contains(next.characterId()))completion.effects().stream().filter(e->e.kind()==MechanicsEngine.Kind.ACTION)
                    .map(e->McWuwa.catalog().actionMappings(next.characterId()).stream().filter(a->a.id().equals(e.id())).findFirst().orElseThrow())
                    .forEach(queue::addLast);
            }
            finishBoundAction(running.weapon,running.action.id());SkillModelProfiles.end(running.visuals);role.cancelAction(); running = null;
            var completionState=ruleState(role);
            if(role.character()==CharacterId.CIACCONA&&completionState.timer("ciaccona_performance")>0&&completedAction.equals("ciaccona/liberation")){recitalPoseOwner=role.instanceId();ciacconaPoseAction="ciaccona/recital";ciacconaPoseUntil=now+completionState.timer("ciaccona_performance");Network.visual(player,new Network.Visual(player.getId(),role.instanceId(),"mc_wuwa:ciaccona/recital",now,completionState.timer("ciaccona_performance"),++sequence,""));}
            if(HeldAttackRules.automaticFinisher(completionState,completedAction))ruleState(player,role,HeldAttackRules.afterCompletion(completionState,completedAction));
            if(completedAction.equals("suisui/rain_heavy")&&completionState.form().equals("rain")
                    &&completionState.timer("suisui_heavy_followup")>0
                    &&charge.readiness(role.instanceId(),now,OptionalInt.empty())!=ChargeState.Readiness.NO_START){
                // The same physical press remains held through heavy recovery.
                // A released/cancelled press has already consumed this charge.
                charge.consume(role.instanceId());request(player,"BASIC_HOLD");
            }
            else if(completedAction.equals("lynae/heavy")&&completionState.form().equals("cruise")&&completionState.timer("charging")>0
                &&charge.readiness(role.instanceId(),now,OptionalInt.empty())!=ChargeState.Readiness.NO_START){
                // One physical hold sustains the authored ground-heavy segments.
                // A release/cancel already clears the latch; airborne heavy never loops.
                charge.consume(role.instanceId());
                if(context(player,role).airborne()||!request(player,"HEAVY_START"))cancelCharge(player);
            }
            else if(completedAction.equals("chisa/skill_saw_hold")&&ruleState(role).timer("chisa_held_entry")>0)request(player,"SKILL_RELEASE");
            else if (!queue.isEmpty()) start(player, role, McWuwa.catalog().requireByWeapon(role.character().weaponPath()), queue.removeFirst());
            else if(role.instanceId().equals(heldAttackOwner)) {
                if(heldAttackMode==HeldAttackRules.Mode.SINGLE_BASIC) { /* Wait for key-up; do not repeat the granted third basic. */ }
                else if(heldAttackMode==HeldAttackRules.Mode.GALBRENA_HEAVY_LOOP&&completedAction.endsWith("heavy_03")){heldAttackMode=HeldAttackRules.Mode.SINGLE_BASIC;}
                else if(heldAttackMode==HeldAttackRules.Mode.QIUYUAN_INK_LOOP&&!ruleState(role).form().equals("drunken")){heldAttackOwner=null;heldAttackMode=null;}
                else if(heldAttackMode==HeldAttackRules.Mode.MORNYE_OBSERVATION_LOOP&&!ruleState(role).form().equals("observation")){heldAttackOwner=null;heldAttackMode=null;}
                else if(role.character()==CharacterId.LYNAE&&heldAttackMode==HeldAttackRules.Mode.COMBO
                    &&ruleState(role).form().equals("sampling")&&ruleState(role).resource("overflow")>=120) {
                    // A sustained sampling input becomes source conversion when it has built
                    // real overflow. Clearing the combo latch prevents a second loop on finish.
                    heldAttackOwner=null;heldAttackMode=null;request(player,"HEAVY_START");
                } else if(!request(player,HeldAttackRules.next(heldAttackMode,ruleState(role),completedAction).name())){heldAttackOwner=null;heldAttackMode=null;}
            }
            else if(HeldAttackRules.automaticFinisher(ruleState(role),completedAction)) {
                request(player,"BASIC");
            }
            else if(completedSawStage>0&&role.character()==CharacterId.CHISA)request(player,"SAW_CONTINUE");
            else if(role.character()==CharacterId.YANGYANG_XUANLING&&completedHit&&finished!=null&&finished.timer("feather_descent")>0&&finished.resource("feathers")>=2)
                startQualifiedDescent(player,role);
            Network.sync(player);
        }
    }
    private void startQualifiedDescent(ServerPlayer player,CharacterInstance role) {
        var result=MECHANICS.reduce(ruleState(role),MechanicsEngine.Input.AIR,context(player,role));if(!result.accepted())return;
        ruleState(player,role,result.state());applyEffects(player,role,result.effects());scheduleTickActions(player,role,result.effects());
    }
    /** Conversion completion and timed form exits can emit real attacks without another key press. */
    boolean ruptureResponseReady(CharacterInstance role,LivingEntity target,long now){
        return responseCooldown.available(role.instanceId(),PlatformRuntime.combatTargetId(target),now);
    }
    void ruptureResponseLanded(ServerPlayer player,CharacterInstance role,LivingEntity target,long now){
        responseCooldown.accepted(role.instanceId(),PlatformRuntime.combatTargetId(target),now);
        var team=ModAttachments.team(player).state();
        for(int slot=0;slot<3;slot++)team.at(slot).filter(owner->owner.alive()&&owner.character()==CharacterId.AEMEATH)
            .ifPresent(owner->soloTargets.applied(player,owner,target,"rupture_response"));
    }
    public void tuneBreakAccepted(ServerPlayer player,LivingEntity target,List<TuneInterference.Activation> activated) {
        var team=ModAttachments.team(player).state();boolean rupture=activated.stream().anyMatch(a->a.mode()==TuneInterference.Mode.RUPTURE);
        team.active().ifPresent(actor->sixWorld.tuneBreak(player,actor,target,activated.stream().anyMatch(a->a.mode()==TuneInterference.Mode.STRAIN),true,this::ruleState,sixCallbacks(player)));
        team.active().filter(role->role.character()==CharacterId.QINGXIAO).ifPresent(role->{
            sixEvent(player,role,role,"qingxiao_actual_tune_break",target,Map.of("grounded",player.onGround()?1d:0d));
            Network.sync(player);
        });
        team.active().filter(role->role.character()==CharacterId.HIYUKI||role.character()==CharacterId.AEMEATH).ifPresent(role->{
            var next=MECHANICS.onTuneBreak(ruleState(role));commit(role,next);
            RolePassiveRuntime.updateMechanics(player,role,next);Network.sync(player);
        });
        for(int slot=0;slot<3;slot++)if(team.at(slot).isPresent()) {
            var owner=team.at(slot).orElseThrow();if(!owner.alive())continue;
            if(owner.character()==CharacterId.MORNYE&&EffectRuntime.hasOwnedEffect(target,player.getUUID(),owner.instanceId(),"mornye_observation"))
                applyMark(player,owner,target,new MechanicsEngine.Effect(MechanicsEngine.Kind.MARK_TARGET,"mornye_interference",1,owner.chain()>=1?400:160,Map.of()));
            if(!rupture)continue;
            var definition=McWuwa.catalog().requireByWeapon(owner.character().weaponPath());
            String response=switch(owner.character()) {
                case LYNAE->"lynae/tune_response_spectrum";
                case MORNYE->"mornye/tune_response_particle_jet";
                case AEMEATH->MechanicsState.canonicalMode("aemeath",owner.selectedMode()).equals("rupture")?"aemeath/tune_response_starburst":"";
                default->"";
            };
            if(response.isEmpty())continue;
            var mapping=McWuwa.catalog().actionMappings(definition.characterId()).stream().filter(a->a.id().equals(response)).findFirst().orElseThrow();
            if(player.distanceToSqr(target)>mapping.geometry().rangeBlocks()*mapping.geometry().rangeBlocks()||!player.hasLineOfSight(target))continue;
            var hits=new ArrayList<CharacterDefinition.DamageDefinition>();for(var group:mapping.hitGroups())for(int i=0;i<group.count();i++)hits.add(definition.requireDamage(group.damageId()));
            var action=new Running(owner.instanceId(),mapping,List.copyOf(hits),PlatformRuntime.time(player),++sequence);
            action.automatic=true;action.independent=true;action.ruptureResponse=true;secondary.add(action);
        }
    }
    private void scheduleTickActions(ServerPlayer player, CharacterInstance role, List<MechanicsEngine.Effect> effects) {
        var definition = McWuwa.catalog().requireByWeapon(role.character().weaponPath());
        for (var effect : effects) if (effect.kind() == MechanicsEngine.Kind.ACTION) {
            if(effect.id().equals("shorekeeper/butterfly"))continue; // Independent projectile is launched by applyEffects.
            var mapping = McWuwa.catalog().actionMappings(definition.characterId()).stream().filter(a -> a.id().equals(effect.id())).findFirst().orElseThrow();
            if(Set.of("lucilla/finale","lucilla/finale_echo").contains(mapping.id())
                &&ModAttachments.team(player).state().active().orElse(null)==role){
                var paidPhoto=running!=null&&running.weapon.equals(role.instanceId())
                    &&Set.of("lucilla/forget","lucilla/forget_echo").contains(running.action.id())?running:null;
                if(paidPhoto!=null){
                    // Its photo was already spent. The finisher takes over the
                    // body, while that accepted autonomous report still lands.
                    running=null;cancel(player);paidPhoto.independent=true;secondary.add(paidPhoto);
                }else if(running!=null)cancel(player);
                start(player,role,definition,mapping);continue;
            }
            if(mapping.id().equals("hsin/flying_palace")){
                var target=hsinEdictTargets.get(role.instanceId());
                if(target==null||!target.isAlive()||target.level()!=player.level())continue;
                List<CharacterDefinition.DamageDefinition> hits=new ArrayList<>();
                for(var group:mapping.hitGroups())for(int i=0;i<group.count();i++)hits.add(definition.requireDamage(group.damageId()));
                var action=new Running(role.instanceId(),mapping,List.copyOf(hits),PlatformRuntime.time(player),++sequence);
                action.automatic=true;action.independent=true;action.assistedTarget=target.getUUID();action.anchor=target.position();
                action.visuals=List.of(SkillModelProfiles.light(player,role,mapping.id(),target.position(),mapping.durationTicks(),false));
                secondary.add(action);continue;
            }
            if(mapping.id().startsWith("phrolova/hecate_")&&fields.stream().noneMatch(field->hecateField(field,player,role)))continue;
            if (running == null && ModAttachments.team(player).state().active().orElse(null) == role && !mapping.id().equals("ciaccona/liberation_note")) start(player, role, definition, mapping);
            else {
                List<CharacterDefinition.DamageDefinition> hits = new ArrayList<>();
                for (var group : mapping.hitGroups()) for (int i = 0; i < group.count(); i++) hits.add(definition.requireDamage(group.damageId()));
                long scheduledStart=PlatformRuntime.time(player);
                if(mapping.id().startsWith("phrolova/hecate_")) {
                    if(running!=null&&running.weapon.equals(role.instanceId())&&running.action.id().startsWith("phrolova/hecate_"))
                        scheduledStart=Math.max(scheduledStart,running.completionTick);
                    for(var queued:secondary)if(queued.weapon.equals(role.instanceId())&&queued.action.id().startsWith("phrolova/hecate_"))
                        scheduledStart=Math.max(scheduledStart,queued.completionTick);
                }
                Running action = new Running(role.instanceId(), mapping, List.copyOf(hits), scheduledStart, ++sequence);
                action.automatic = true; action.independent = role != ModAttachments.team(player).state().active().orElse(null);
                if(mapping.id().equals("ciaccona/liberation_note"))fields.stream().filter(f->f.owner.equals(role.instanceId())&&f.effect.id().equals("ciaccona_performance")).findFirst().ifPresent(f->{action.anchor=f.center;action.anchorRadius=12;action.independent=true;});
                if(action.anchor==null&&action.independent&&!mapping.id().startsWith("phrolova/hecate_")) {
                    action.anchor=player.position();action.anchorRadius=mapping.geometry().rangeBlocks();action.fixedAim=player.getLookAngle();
                }
                if(mapping.id().equals("ciaccona/liberation_note")){
                    String color=ruleState(role).timer("ciaccona_last_color")==2?"yellow":"green";
                    action.visuals=List.of(SkillModelProfiles.light(player,role,"ciaccona/liberation_note_"+color,action.anchor==null?player.position():action.anchor,mapping.durationTicks(),false));
                }else if(Set.of("galbrena/air_rain","galbrena/demon_air_rain").contains(mapping.id()))
                    action.visuals=List.of(SkillModelProfiles.light(player,role,mapping.id(),player.position(),6,false));
                if(mapping.id().startsWith("phrolova/hecate_")) {
                    // The secondary tick starts the visual on its own scheduled boundary.
                    // Scheduling two outro commands together must not overwrite the first clip.
                    fields.stream().filter(field->hecateField(field,player,role)).findFirst().ifPresent(field->{
                        action.anchor=field.center;action.anchorRadius=mapping.geometry().rangeBlocks();
                    });
                }
                action.marks = List.copyOf(pendingMarks.getOrDefault(role.instanceId(), List.of())); pendingMarks.remove(role.instanceId());
                if (mapping.id().contains("/unbound_wind_")) passiveEvent(player, role, WeaponPassiveEngine.Trigger.ROVER_UNBOUND_WIND_CAST, Long.toString(sequence));
                secondary.add(action);
            }
        }
    }
    private static Vec3 attackEye(ServerPlayer player,Running action){
        if(action.action.id().startsWith("phrolova/hecate_"))
            return HecateEntity.owned(player,action.weapon).map(HecateEntity::getEyePosition)
                .orElseGet(()->action.anchor==null?player.getEyePosition():action.anchor.add(0,1,0));
        return action.fixedAim==null?player.getEyePosition():action.anchor.add(0,player.getEyeHeight(),0);
    }
    private static boolean attackSight(ServerPlayer player,Running action,net.minecraft.world.entity.Entity target){
        if(action.action.id().startsWith("phrolova/hecate_"))
            return HecateEntity.owned(player,action.weapon).filter(entity->entity.hasLineOfSight(target)).isPresent();
        return player.hasLineOfSight(target);
    }
    private void hit(ServerPlayer player, CharacterInstance role, Running action, int index, CharacterDefinition.DamageDefinition hit) {
        if(index==action.hits.size()-1&&action.ledger.add("final-group-launched")
            &&fiveWorld.finalGroupLaunched(player,role,action.action.id())){
            var state=mechanics.get(role.instanceId());
            if(state!=null){var next=fiveWorld.prepare(player,role,state);commit(role,next);RolePassiveRuntime.updateMechanics(player,role,next);}
        }
        // Hecate physically dashes after accepting a command. Resolve each hit
        // at the relay's current position, not its stale cast/queue origin.
        if(action.action.id().startsWith("phrolova/hecate_")) {
            var relay=HecateEntity.owned(player,action.weapon).orElse(null);
            if(relay==null)return;
            action.anchor=relay.position();
        }
        var geometry = cn.zgnhit.mcwuwa.content.mechanics.ActionGeometryRules.forChain(action.action,role.chain()); Vec3 eye = attackEye(player,action); Vec3 aim = action.fixedAim==null?player.getLookAngle():action.fixedAim;
        if(action.assistedTarget!=null){
            var entity=player.serverLevel().getEntity(action.assistedTarget);
            var targets=entity instanceof LivingEntity target&&PlatformRuntime.canAttack(player,target)
                &&player.distanceToSqr(target)<=geometry.rangeBlocks()*geometry.rangeBlocks()&&player.hasLineOfSight(target)
                ?List.of(target):List.<LivingEntity>of();
            settleTargets(player,role,action,index,hit,targets,false);return;
        }
        if(action.ruptureResponse) {
            // Source: every interfered target in range, not only the target
            // whose break dispatched this response. Keep the existing authored
            // host range/LOS; reserve responders use the deployed team's body.
            double responseRange=geometry.rangeBlocks();
            var recipients=player.level().getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(responseRange),
                candidate->PlatformRuntime.canAttack(player,candidate)&&player.distanceToSqr(candidate)<=responseRange*responseRange
                    &&player.hasLineOfSight(candidate)&&EffectRuntime.hasEffect(candidate,"rupture_interference"));
            settleTargets(player,role,action,index,hit,recipients,false);return;
        }
        if (geometry.delivery().equals("projectile") && action.anchor == null) {
            if (action.ledger.add("projectile-spawn:" + index)) {
                if(Set.of("shorekeeper/skill","shorekeeper/intro").contains(action.action.id()))CombatProjectileEntity.guidedButterfly(player,role,eye,geometry.rangeBlocks(),"shorekeeper_dark_butterfly",target->settleTargets(player,role,action,index,hit,List.of(target),false));
                else {
                    var projectile=CombatProjectileEntity.spawn(player, role, Element.valueOf(hit.element().toUpperCase(Locale.ROOT)), Set.of("galbrena/air_rain","galbrena/demon_air_rain").contains(action.action.id())?new Vec3(0,-1,0):aim, geometry.rangeBlocks(),target -> settleTargets(player,role,action,index,hit,List.of(target),false));
                    if(projectile!=null)projectile.visualAction(action.action.id());
                    if(projectile!=null&&role.character()==CharacterId.PHOEBE&&(action.action.id().contains("basic_")||action.action.id().contains("counter")))
                        phoebeRing(player,role).filter(f->player.position().distanceToSqr(f.center)>f.area.radius()*f.area.radius()).ifPresent(f->projectile.interceptRing(f.center,f.area.radius(),()->phoebeRefract(player,role,f)));
                }
            }
            return; // A miss grants neither native damage, target marks, nor hit resources.
        }
        Vec3 center=action.anchor==null?player.position():action.anchor;double radius=action.anchor==null?geometry.rangeBlocks():action.anchorRadius;
        // Attackable world entities retain native damage/drop behavior without combat resources.
        {
            for(var crystal:player.level().getEntities(player,
                    new net.minecraft.world.phys.AABB(center,center).inflate(radius),e->NativeAttackTargets.accepts(player,e)&&e.position().distanceToSqr(center)<=radius*radius&&attackSight(player,action,e))) {
                Vec3 relative=crystal.getBoundingBox().getCenter().subtract(eye);double forward=relative.dot(aim);
                if((action.anchor==null||action.fixedAim!=null)&&!geometry.shape().equals("sphere")
                        &&(forward<-.5||relative.subtract(aim.scale(forward)).length()>geometry.widthBlocks()+crystal.getBbWidth()*.5))continue;
                if(action.ledger.add(index+":native_entity:"+crystal.getUUID()))NativeAttackTargets.hit(player,crystal);
            }
        }
        List<LivingEntity> targets = player.level().getEntitiesOfClass(LivingEntity.class,new net.minecraft.world.phys.AABB(center,center).inflate(radius),
            target -> !(target instanceof net.minecraft.world.entity.decoration.ArmorStand) && PlatformRuntime.canAttack(player, target) && target.position().distanceToSqr(center) <= radius*radius
                && (!geometry.requiresLineOfSight() || attackSight(player,action,target)));
        if(action.action.id().equals("yinlin/magnetic_blast")) {
            targets=player.level().getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(18),
                target->PlatformRuntime.canAttack(player,target)&&player.hasLineOfSight(target)
                    &&EffectRuntime.hasOwnedEffect(target,player.getUUID(),role.instanceId(),"yinlin_sin"));
            settleTargets(player,role,action,index,hit,targets,false);return;
        }
        settleTargets(player, role, action, index, hit, targets, true);
    }
    /** Projectile callbacks keep the original action ledger after its body timeline has ended. */
    private void settleTargets(ServerPlayer player, CharacterInstance role, Running action, int index,
            CharacterDefinition.DamageDefinition hit, List<LivingEntity> targets, boolean filterGeometry) {
        var geometry = cn.zgnhit.mcwuwa.content.mechanics.ActionGeometryRules.forChain(action.action,role.chain()); Vec3 eye = attackEye(player,action); Vec3 aim = action.fixedAim==null?player.getLookAngle():action.fixedAim;
        MechanicsState beforeHit = mechanics.get(role.instanceId());
        var contactHit=hit;
        if(role.character()==CharacterId.GALBRENA&&beforeHit!=null&&action.action.id().startsWith("galbrena/demon_")) {
            var raw=hit.raw().deepCopy();raw.addProperty("contactGalbrenaEmbers",beforeHit.resource("embers"));
            contactHit=new CharacterDefinition.DamageDefinition(hit.id(),hit.sourceType(),hit.kind(),hit.element(),hit.scalingProperty(),hit.skillLevel(),hit.multiplier(),hit.toughnessUnits(),hit.offTuneUnits(),raw,hit.actionMappingVerified(),hit.unresolved());
        }
        Vec3 gravityHitPoint=null;
        if(action.thunderSnapshot<0&&beforeHit!=null)action.thunderSnapshot=beforeHit.resource("thunder_realm");
        double energyMultiplier = role.character() == CharacterId.CAMELLYA && beforeHit != null
            ? MechanicModifierRules.camellyaEnergyMultiplier(action.action.id(), beforeHit.timer("budding") > 0, beforeHit.resource("pistils") > 0) : 1;
        boolean buddingHit=role.character()==CharacterId.CAMELLYA&&MechanicModifierRules.camellyaAffected(action.action.id());
        double buddingCoefficient=buddingHit?epiphanyBonus(role,"camellya_budding_multiplier",PlatformRuntime.time(player)):0;
        double buddingAttack=buddingHit&&role.chain()>=3&&beforeHit!=null&&beforeHit.timer("budding")>0?.58:0;
        if(action.action.id().startsWith("aemeath/wing_ensemble")&&action.ledger.add("wing-burst"))
            soloTargets.wingBurst(player,role,targets,action.wingStarSnapshot,action.wingPreserveTracesSnapshot);
        for (LivingEntity target : targets) {
            if(action.action.id().equals("cartethyia/fleurdelys_skill_followup")){
                var recipient=target instanceof HecateEntity proxy&&proxy.ownerPlayer()!=null?proxy.ownerPlayer():target;
                if(!TraversalMotion.solidSupport(recipient))continue;
            }
            Vec3 relative = target.getBoundingBox().getCenter().subtract(eye); double forward = relative.dot(aim);
            if (filterGeometry && (action.anchor==null||action.fixedAim!=null)&&!geometry.shape().equals("sphere")) {
                if (forward < -0.5) continue;
                double lateral = relative.subtract(aim.scale(forward)).length();
                if (lateral > geometry.widthBlocks() + target.getBbWidth() * .5) continue;
            }
            if (!action.ledger.add(index + ":" + PlatformRuntime.combatTargetId(target))) continue;
            if(action.fixedPhantom) {
                var source=new EffectSource(player.getUUID(),role.instanceId(),role.character(),UUID.randomUUID(),UUID.randomUUID());
                PlatformRuntime.effectHit(player,source,target,Element.HAVOC,action.phantomAttack*hit.multiplier(),"fixed/xuanling_phantom");
                continue;
            }
            if(action.ruptureResponse) {
                PlatformRuntime.ruptureResponseHit(player,role,target,hit,action.action.id());continue;
            }
            if(action.havocSnapshot==null&&TargetReactionRuntime.copiesHavoc(action.action.id()))action.havocSnapshot=targetReactions.beforeHit(player,action.action.id());
            if(action.aeroRangeSnapshot==null&&TargetReactionRuntime.copiesAeroBefore(beforeHit,action.action.id()))action.aeroRangeSnapshot=targetReactions.captureAero(player);
            // Ferocious Fang has an independent source row for a target encircled
            // by this Lupa. Resolve per target, before applying cast qualifications;
            // resource gain below still belongs to the original hit index.
            var targetHit=action.action.id().equals("lupa/skill_ferocious")&&sixWorld.encircled(role,target,PlatformRuntime.time(player))
                ?McWuwa.catalog().requireByWeapon(role.character().weaponPath()).requireDamage("120760211"):contactHit;
            double addition = action.jinhsiCastBonus==null?multiplierAddition(role, action.action.id(), index == action.hits.size() - 1)
                :action.jinhsiCastBonus.multiplierAddition();
            var resolved = addition == 0 ? targetHit : new CharacterDefinition.DamageDefinition(targetHit.id(), targetHit.sourceType(), targetHit.kind(), targetHit.element(), targetHit.scalingProperty(), targetHit.skillLevel(), targetHit.multiplier() + addition,
                targetHit.toughnessUnits(), targetHit.offTuneUnits(), targetHit.raw(), targetHit.actionMappingVerified(), targetHit.unresolved());
            resolved=NewSixActionRules.qualify(resolved,action.acceptedQualifications);
            boolean aeroFinale = action.action.id().equals("cartethyia/liberation_finale");
            int aeroLayers = aeroFinale ? action.aeroFinaleSnapshots.getOrDefault(target.getUUID(), EffectRuntime.layers(target, "aero_erosion")) : 0;
            double targetAmplification = aeroFinale ? MechanicModifierRules.cartethyiaFinaleAmplification(aeroLayers) : 1;
            boolean brillianceQualified=action.action.id().startsWith("carlotta/skill_show_brilliance")&&sixWorld.variegated(role,target,PlatformRuntime.time(player));
            // A fired E4 projectile can collide after the body's48-tick end.
            // Keep its consumed Incandescence/C1 snapshot without extending a
            // live buff into the next cast, or adding the same bonus twice.
            double castBonus=action.jinhsiCastBonus==null?0:action.jinhsiCastBonus.damageBonus()
                -epiphanyBonus(role,"jinhsi_epiphany_damage_bonus",PlatformRuntime.time(player));
            // The last additive projectile can arrive after its body ends.
            // Keep this cast's spent premix, never the next cast's live buff.
            if(action.lynaePremixBonus!=null)castBonus+=action.lynaePremixBonus
                -epiphanyBonus(role,"lynae_premix_bonus",PlatformRuntime.time(player));
            // Consuming the final pistils on the first target clears the live buff.
            // All targets of this contact still receive its starting coefficient;
            // the next contact reads the new state, and resources are spent once.
            double coefficientAdjustment=buddingHit?buddingCoefficient
                -epiphanyBonus(role,"camellya_budding_multiplier",PlatformRuntime.time(player)):0;
            var currentMechanics=mechanics.get(role.instanceId());
            double attackAdjustment=buddingHit?buddingAttack
                -(role.chain()>=3&&currentMechanics!=null&&currentMechanics.timer("budding")>0?.58:0):0;
            boolean applied = PlatformRuntime.nativeHit(player, role, target, resolved, castBonus, targetAmplification, action.action.id(),
                action.automatic || geometry.delivery().equals("autonomous") ? PlatformRuntime.HitOrigin.AUTONOMOUS
                    : geometry.delivery().equals("projectile") ? PlatformRuntime.HitOrigin.DIRECT_PROJECTILE : PlatformRuntime.HitOrigin.DIRECT_ACTIVE,coefficientAdjustment,attackAdjustment);
            if (applied) {
                // Death Knell's opening packet is the gunshot. Its crystal hits can break this stasis.
                if(index==0&&role.chain()>=6&&action.action.id().startsWith("carlotta/death_knell_"))
                    sixWorld.mark(player,role,target,"carlotta_iridescence",30,1,true);
                // Secondary magnetic damage has no actor presentation. Show its
                // confirmed contact at each marked enemy, without restarting A.
                if(action.action.id().equals("yinlin/magnetic_blast")
                    &&action.ledger.add("magnetic-contact:"+index+":"+target.getUUID()))
                    SkillModelProfiles.light(player,role,"yinlin/magnetic_contact",target.position(),10,false);
                // Qualification is captured on the first accepted hit, so a cooldown
                // expiring during windup can qualify this whole attack, including later targets.
                if(role.character()==CharacterId.SUISUI&&beforeHit!=null&&beforeHit.timer("suisui_inherent_hit_cd")==0
                    &&Set.of("suisui/skill_awaken","suisui/intro").contains(action.action.id()))action.suisuiInherentQualified=true;
                if(brillianceQualified&&action.ledger.add("carlotta-c1-brilliance"))
                    sixEvent(player,role,role,"carlotta_brilliance_hit",target,Map.of("targetVariegated",1d));
                if(gravityHitPoint==null)gravityHitPoint=target.position();
                if(action.action.id().equals("cartethyia/skill")&&action.ledger.add("cart-lift:"+target.getUUID()))targetReactions.confirmedLift(player,role,target);
                soloTargets.timelineHit(player,role,target,action.action.id(),action.ledger.add("solo-first:"+target.getUUID()),index==action.hits.size()-1,action.thunderSnapshot);
                if (aeroFinale && action.ledger.add("aero-finale:" + target.getUUID())) {
                    // Keep this target's original layers for all seven groups; a miss never snapshots or consumes.
                    action.aeroFinaleSnapshots.put(target.getUUID(), aeroLayers);
                    if (role.chain() < 6) {int consumed=EffectRuntime.consume(target, "aero_erosion", Integer.MAX_VALUE);if(consumed>0)suisuiAnomalyActivity(player,role,target,"aero_erosion",true);}
                    else for (var mark : EffectRuntime.fillKnownCap(player, role, target, "aero_erosion")) {
                        RolePassiveRuntime.onEffectApplied(player, role, target, mark.id());
                    }
                }
                if(action.ledger.add("itecho-copy"))targetReactions.confirmedCopy(role,action.havocSnapshot,PlatformRuntime.time(player));
                if(action.ledger.add("outsider-copy")) {
                    if(TargetReactionRuntime.copiesAeroAfter(beforeHit,action.action.id()))action.aeroRangeSnapshot=targetReactions.captureAero(player);
                    targetReactions.copyAero(player,role,action.aeroRangeSnapshot,(owner,candidate,layers)->applyMark(player,owner,candidate,
                        new MechanicsEngine.Effect(MechanicsEngine.Kind.MARK_TARGET,"aero_erosion",layers,0,Map.of())));
                }
                if (action.ledger.add("marks:" + target.getUUID())) for (var mark : action.marks) applyTargetEffect(player, role, target, mark);
                // Multiple targets share one resource transaction for this actual hit
                // index. A later hit index (including the same damage ID repeated)
                // has its own transaction; missed/canceled hits never earn resources.
                if (action.ledger.add("native-resource:" + index)) {
                    var state = mechanics.get(role.instanceId());
                    if (state != null) {
                        var result = MECHANICS.onDamageHit(state, action.action.id(), hit.id(), context(player, role));
                        if (result.accepted()) {
                            commit(role, result.state()); RolePassiveRuntime.updateMechanics(player, role, result.state());
                            applyEffects(player, role, result.effects(), target);
                        }
                    }
                }
                if (!action.resourceHit) {
                    action.resourceHit = true; var state = mechanics.get(role.instanceId());
                    if (state != null) {
                        var result = MECHANICS.onHit(state, action.action.id(), context(player, role), CombatConfig.PREVIEW_RESOURCE_YIELDS.getAsBoolean());
                        if (result.accepted()) {
                            commit(role, result.state()); RolePassiveRuntime.updateMechanics(player, role, result.state()); applyEffects(player, role, result.effects(), target);
                            // On-hit resource credit happens once; target marks must still reach every target of the attack.
                            var followingMarks = result.effects().stream().filter(e -> e.kind() == MechanicsEngine.Kind.MARK_TARGET||e.kind()==MechanicsEngine.Kind.CONTROL_TARGET||e.kind()==MechanicsEngine.Kind.NATIVE_PROC).toList();
                            if (!followingMarks.isEmpty()) {
                                List<MechanicsEngine.Effect> propagated = new ArrayList<>(action.marks); propagated.addAll(followingMarks); action.marks = List.copyOf(propagated);
                            }
                            var definition = McWuwa.catalog().requireByWeapon(role.character().weaponPath());
                            for (var effect : result.effects()) if (effect.kind() == MechanicsEngine.Kind.ACTION&&!effect.id().equals("shorekeeper/butterfly")) {
                                McWuwa.catalog().actionMappings(definition.characterId()).stream().filter(a -> a.id().equals(effect.id())).findFirst().ifPresent(mapping -> {
                                    List<CharacterDefinition.DamageDefinition> extraHits = new ArrayList<>();
                                    for (var group : mapping.hitGroups()) for (int j = 0; j < group.count(); j++) extraHits.add(definition.requireDamage(group.damageId()));
                                    Running extra = new Running(role.instanceId(), mapping, extraHits, PlatformRuntime.time(player), ++sequence); extra.automatic = true; secondary.add(extra);
                                });
                            }
                        }
                    }
                }
            }
            if (applied && hit.raw().has("energy") && action.ledger.add("energy-resource:" + index)) {
                role.gain("energy", Math.max(0, hit.raw().get("energy").getAsDouble() / 100) * energyMultiplier * combatStats(player, role).energyRegen());
            }
        }
        if((action.action.id().equals("cartethyia/fleurdelys_skill")||action.action.id().equals("cartethyia/fleurdelys_skill_followup"))
            &&action.ledger.add("cart-gravity-field")) {
            // Creation belongs to the accepted skill timeline, not a successful
            // damage callback. A miss supplies no hit resources. Retain the first
            // actual recipient as the target point when one was struck.
            Vec3 at=gravityHitPoint;
            if(at==null){
                Vec3 start=player.position().add(0,.5,0);
                Vec3 groundAim=aim.multiply(1,0,1);
                if(groundAim.lengthSqr()<1e-8)groundAim=Vec3.directionFromRotation(0,player.getYRot());
                Vec3 end=start.add(groundAim.normalize().scale(Math.min(4,geometry.rangeBlocks())));
                var obstacle=player.level().clip(new net.minecraft.world.level.ClipContext(start,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
                at=obstacle.getLocation();
                var floor=player.level().clip(new net.minecraft.world.level.ClipContext(at,at.add(0,-6,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
                if(floor.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK)at=floor.getLocation();
            }
            var effect=new MechanicsEngine.Effect(MechanicsEngine.Kind.PULL_AREA,"cartethyia_gravity",.35,40,Map.of("radius",4d,"fallingSword",action.action.id().endsWith("_followup")?1d:0d));
            addPullField(player,role,effect,at,PlatformRuntime.time(player));
        }
        if(index==action.hits.size()-1&&HsinTargetRules.spendsRealm(action.action.id())) {
            var state=mechanics.get(role.instanceId());if(state!=null)ruleState(player,role,MECHANICS.grantResource(state,"thunder_realm",-100));
        }
    }
    private void addPullField(ServerPlayer player,CharacterInstance role,MechanicsEngine.Effect effect,Vec3 at,long now){
        removeFields(f->f.owner.equals(role.instanceId())&&f.effect.id().equals(effect.id()));
        var visual=effect.id().equals("cartethyia_gravity")?SkillModelProfiles.light(player,role,effect.values().getOrDefault("fallingSword",0d)>0?"cartethyia/gravity_followup_field":"cartethyia/gravity_field",at,effect.durationTicks(),false):null;
        fields.add(new Field(role.instanceId(),effect,at,new FieldArea(dimension(player),at.x,at.y,at.z,effect.values().getOrDefault("radius",6d),now+effect.durationTicks()),visual));
    }
    /** Low air is a live world query in both input and HUD preview. Three blocks
     * is the shared MC height adaptation, not an original published distance. */
    private static MechanicsState heightEligibility(ServerPlayer player,MechanicsState state){
        if(!LowAirRules.observesHeight(state.characterId()))return state;
        String key=switch(state.characterId()){case "galbrena"->"gal_low_air";case "ciaccona"->"ciaccona_low_air";default->"";};
        var timers=new HashMap<>(state.timers());
        var floor=player.level().clip(new net.minecraft.world.level.ClipContext(player.position().add(0,.1,0),player.position().add(0,-3,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
        boolean low=floor.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK;
        if(low)timers.remove("high_air");else timers.put("high_air",1);
        if(!key.isEmpty()){if(low)timers.put(key,3);else timers.remove(key);}
        return new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),state.nextBasic(),state.resources(),timers);
    }
    private boolean bodyAirborne(ServerPlayer player,CharacterInstance role) {
        return (role.character()==CharacterId.CHISA||role.character()==CharacterId.MORNYE?!TraversalMotion.solidSupport(player):!player.onGround())
            &&!(ModAttachments.team(player).state().active().orElse(null)==role&&specialMovement.supportedOnWater(player));
    }
    private MechanicsEngine.Context context(ServerPlayer player, CharacterInstance role) {
        var attachment = ModAttachments.team(player); var stats = combatStats(player, role);
        return new MechanicsEngine.Context(role.resource("energy"), attachment.state().stamina().snapshot().stamina(), stats.defense(), stats.energyRegen(),
            bodyAirborne(player,role),
            // ServerPlayer delta is often a small residual after friction. Native received
            // displacement records actual walking, including a client-controlled integrated player.
            (player.getKnownMovement().horizontalDistanceSqr()>.001||player.getDeltaMovement().horizontalDistanceSqr()>.001)
                &&(role.character()!=CharacterId.HSIN||cn.zgnhit.mcwuwa.platform.MovementKeys.get(player).forward()||cn.zgnhit.mcwuwa.platform.MovementKeys.get(player).backward()
                    ||cn.zgnhit.mcwuwa.platform.MovementKeys.get(player).left()||cn.zgnhit.mcwuwa.platform.MovementKeys.get(player).right()),
            attachment.state().inCombat(), attachment.state().active().map(a -> a == role).orElse(false),
            stats.attack(), role.health() / .002, role.maxHealth() / .002, role.resource("concerto"));
    }
    private int findSlot(TeamAttachment team, CharacterInstance role) { for (int i = 0; i < 3; i++) if (team.state().at(i).orElse(null) == role) return i; throw new IllegalArgumentException("unequipped role"); }
    private void applyEffects(ServerPlayer player, CharacterInstance role, List<MechanicsEngine.Effect> effects) {
        applyEffects(player, role, effects, null);
    }
    private void applyEffects(ServerPlayer player, CharacterInstance role, List<MechanicsEngine.Effect> effects, LivingEntity hitTarget) {
        var team = ModAttachments.team(player).state(); long now = PlatformRuntime.time(player);
        MechanicsState acceptedState = mechanics.get(role.instanceId());
        if (acceptedState != null) RolePassiveRuntime.updateMechanics(player, role, acceptedState);
        if(unisonGains.remove(role.instanceId())&&acceptedState!=null&&acceptedState.timer("unison")>0){
            EchoRuntime.onUnison(player,role);
            RolePassiveRuntime.onUnison(player,role);
            passiveEvent(player,role,WeaponPassiveEngine.Trigger.GAIN_UNISON,"unison_gain:"+now+":"+sequence);
            sonatas.event(player,role,"UNISON",false,0,0);
            boolean lamp=false;for(int i=0;i<3;i++)if(team.at(i).filter(owner->owner.alive()&&UnisonRules.canRespond(ruleState(owner))).isPresent())lamp=true;
            unison.observe(role.instanceId(),true,lamp&&role.character()!=CharacterId.HSIN);
        }
        // Cast passives precede same-cast healing and shield evaluation, and run once per accepted action.
        for (var effect : effects) if (effect.kind() == MechanicsEngine.Kind.ACTION) {
            RolePassiveRuntime.onAction(player, role, effect.id());soloTargets.cast(player,role,effect.id());
            if(effect.id().equals("aero_rover/liberation"))for(int slot=0;slot<3;slot++)
                if(team.at(slot).filter(owner->owner.alive()&&owner.character()==CharacterId.CARTETHYIA).isPresent()){
                    commit(role,MECHANICS.grantResource(ruleState(role),"wind",25));break;
                }
        }
        for (var rawEffect : effects) {
            var normalized=rawEffect.id().equals("qiuyuan_liberation_crit")?new MechanicsEngine.Effect(rawEffect.kind(),rawEffect.id(),Math.clamp(2*(combatStats(player,role).critRate()-.50),0,.30),rawEffect.durationTicks(),rawEffect.values()):rawEffect;
            if(rawEffect.kind()==MechanicsEngine.Kind.SPAWN_FIELD&&rawEffect.id().startsWith("ciaccona_shadow_")) {
                var values=new HashMap<>(rawEffect.values());values.put("soloReadyAt",now+values.getOrDefault("soloDelay",0d));
                normalized=new MechanicsEngine.Effect(rawEffect.kind(),rawEffect.id(),rawEffect.amount(),rawEffect.durationTicks(),values);
            }
            final var effect=normalized;
            if(fiveWorld.photoAura(player,role,ruleState(role),effect.id(),effect.amount(),effect.durationTicks()))continue;
            switch (effect.kind()) {
            case ACTION -> {if(effect.id().equals("shorekeeper/butterfly"))launchShoreButterfly(player,role,effect.values().getOrDefault("kernelIndex",-1d).intValue());}
            case SWORD_SHADOW -> swordShadows.spawn(player,role,effect.id(),effect.durationTicks());
            case SWORD_COLLECT -> swordShadows.collect(role,effect.id());
            case PAY_ENERGY -> role.pay(Map.of("energy", effect.amount()));
            case ECHO_CAST -> {if(effect.id().equals("cantarella_virtual_echo")&&(int)effect.amount()==1607301)tideWorld.castDiffusion(role);PlatformRuntime.acceptedEchoCast(player,role,(int)effect.amount());}
            case PAY_CONCERTO -> { if (role.pay(Map.of("concerto", effect.amount())) && effect.amount() > 0) passiveEvent(player, role, WeaponPassiveEngine.Trigger.CONCERTO_SPENT, "consume:" + sequence); }
            case PAY_STAMINA -> team.stamina().spend(effect.amount());
            case GAIN_CONCERTO -> {
                role.gain("concerto", effect.amount());
                if(role.character()==CharacterId.HSIN&&effect.id().equals("liberation_transform")){
                    // First transformation has no damage action, but owns a real
                    // opening gesture. Borrowing the aspect must not replay it.
                    hsinOpeningPoseOwner=role.instanceId();hsinOpeningPoseUntil=now+80;
                    Network.visual(player,new Network.Visual(player.getId(),role.instanceId(),"mc_wuwa:hsin/liberation",now,80,++sequence,""));
                    ConstructEntity.spawn(player.serverLevel(),player,role.instanceId(),"skill_light_hsin__liberation",player.position(),80,1,true,"attack");
                }
            }
            case GAIN_ENERGY -> role.gain("energy",effect.values().containsKey("energyFraction")?role.resourceMax("energy")*effect.values().get("energyFraction"):effect.amount());
            case SET_HEALTH_FRACTION -> { role.commitHealth(Math.clamp(effect.amount(), 0, 1) * role.maxHealth()); PlatformRuntime.applyHealth(player, ModAttachments.team(player), false); }
            case SHIELD_TEAM -> {
                double amount=effect.amount()*CombatMath.HEALTH_SCALE*Math.max(0,1+combatStats(player,role).bonus("shield_bonus"));
                boolean endsOnSwitch=effect.values().getOrDefault("endsOnSwitch",0d)>0;
                if(effect.values().getOrDefault("selfOnly",0d)>0||effect.values().getOrDefault("ownerOnly",0d)>0)grantShield(player,role,role,effect.id(),amount,now+effect.durationTicks(),endsOnSwitch);
                else for(int i=0;i<3;i++)team.at(i).ifPresent(c->grantShield(player,role,c,effect.id(),amount,now+effect.durationTicks(),endsOnSwitch));
            }
            case MARK_TARGET -> {
                if (hitTarget != null) applyMark(player, role, hitTarget, effect);
                else pendingMarks.computeIfAbsent(role.instanceId(), id -> new ArrayList<>()).add(effect);
            }
            case CONTROL_TARGET -> { if(hitTarget!=null&&Set.of("carlotta_variegation","carlotta_iridescence").contains(effect.id()))applyMark(player,role,hitTarget,effect);
                else if(hitTarget!=null)applyTargetEffect(player,role,hitTarget,effect);
                else pendingMarks.computeIfAbsent(role.instanceId(),id->new ArrayList<>()).add(effect); }
            case NATIVE_PROC -> {
                if(effect.id().equals("suoming_rain")){fiveWorld.suomingRain(player,role);break;}
                if(effect.id().equals("suoming_forbidden")){unison.clearRecipient(role.instanceId());break;}
                if(effect.id().equals("suoming_chain_intro")){
                    if(singleplayerUnison(player))unison.responded(role.instanceId(),now,0);
                    break;
                }
                if(fiveWorld.zaniProc(player,role,effect.id(),effect.amount()))break;
                if(fiveWorld.augustaProc(player,role,effect.id())){
                    if(effect.id().equals("augusta_heavy_c6")&&role.chain()>=6)augustaThunder(player,role);
                    break;
                }
                if(effect.id().equals("zani_outro")){fiveWorld.zaniOutro(player,role);outroHit(player,role,effect);break;}
                if(effect.id().equals("lucilla_camera_shutter")){LucillaPhotography.shutter(player);break;}
                if(effect.id().equals("lucilla_camera_toggle")||effect.id().equals("lucilla_camera_mark")){
                    if(ruleState(role).timer("lucilla_camera")>0)LucillaPhotography.mark(player);break;
                }
                if(Set.of("lucilla_intro_chafe","lucilla_chase_chafe","lucilla_forget_chafe").contains(effect.id())){
                    var mark=new MechanicsEngine.Effect(MechanicsEngine.Kind.MARK_TARGET,"glacio_chafe",1,600,Map.of());
                    if(hitTarget!=null)applyMark(player,role,hitTarget,mark);
                    else pendingMarks.computeIfAbsent(role.instanceId(),id->new ArrayList<>()).add(mark);
                    break;
                }
                if(effect.id().startsWith("lucilla_")&&fiveWorld.photoProc(player,role,ruleState(role),effect.id())){
                    // Forget is a named cast event, not an equipped echo activation:
                    // notify passives/team listeners without spending echo cooldowns.
                    if(effect.id().equals("lucilla_photo_consumed")&&ruleState(role).mode().equals("echo"))
                        echoCast(player,role,1_100_000_000+ruleState(role).timer("lucilla_forget_serial"));
                    break;
                }
                if(effect.id().equals("zhezhi_white_crane_c6")){fiveWorld.extraWhiteCrane(player,role);break;}
                if(effect.id().equals("phoebe_c6_starlight")){
                    phoebeRing(player,role).ifPresent(ring->{
                        for(var target:phoebeRingTargets(player,ring))tideNativeHit(player,role,target,"heavy_starlight");
                    });
                } else if(effect.id().equals("ciaccona_solo_area")) {
                    ciacconaSoloHit(player,role,player.position());
                } else if(hitTarget!=null)applyTargetEffect(player,role,hitTarget,effect);
                else pendingMarks.computeIfAbsent(role.instanceId(),id->new ArrayList<>()).add(effect);
            }
            case OUTRO_HIT -> outroHit(player,role,effect);
            case CONTROL_AREA -> {
                if(effect.values().getOrDefault("singleplayerOnly",0d)>0&&!player.level().getServer().isSingleplayer())break;
                if(effect.id().equals("phoebe_ring_stasis")){
                    phoebeRing(player,role).ifPresent(f->phoebeRingStasis(player,role,f));break;
                }
                double radius=effect.values().getOrDefault("radius",6.0);
                int duration=effect.values().getOrDefault("actionBound",0d)>0?committingActionTicks:effect.durationTicks();
                for(LivingEntity target:player.level().getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(radius),
                    e->PlatformRuntime.canAttack(player,e)&&player.distanceToSqr(e)<=radius*radius&&player.hasLineOfSight(e))){
                    if(Set.of("hsin_illuminated_stasis","phrolova_composition_stasis").contains(effect.id()))EffectRuntime.applyStasis(player,role,target,duration,effect.id());
                    else EffectRuntime.applyStasis(player,role,target,duration,effect.id().equals("qiuyuan_ink_stasis"));
                }
            }
            case PUSH_AREA -> EffectRuntime.pushArea(player,role,effect.values().getOrDefault("radius",4d),effect.amount());
            case PULL_AREA -> { if(effect.id().equals("chisa_glide_scar"))EffectRuntime.pullOwnedScarArea(player,role,effect.values().getOrDefault("radius",6.0),effect.amount());
                else if(effect.values().getOrDefault("centerOnOwner",0d)>0)EffectRuntime.pullArea(player,role,player,effect.values().getOrDefault("radius",4d),effect.amount());
                else if(hitTarget!=null) {
                    if(effect.durationTicks()>1) {
                        addPullField(player,role,effect,hitTarget.position(),now);
                    } else EffectRuntime.pullArea(player,role,hitTarget,effect.values().getOrDefault("radius",4.0),effect.amount());
                } else pendingMarks.computeIfAbsent(role.instanceId(),id->new ArrayList<>()).add(effect); }
            case MARK_AREA -> {
                // Pure engine supplies the authored MC radius; no client target or position is accepted.
                double radius = effect.values().getOrDefault("radius", 6.0);
                var domain=effect.values().getOrDefault("suisuiDomain",0d)>0
                    ?fields.stream().filter(f->f.owner.equals(role.instanceId())&&f.effect.id().equals("suisui_landscape")&&fieldActive(f,player)).findFirst():Optional.<Field>empty();
                if(effect.values().getOrDefault("suisuiDomain",0d)>0&&domain.isEmpty())break;
                Vec3 center=domain.map(Field::center).orElse(player.position());
                for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(center,center).inflate(radius),
                    e -> PlatformRuntime.canAttack(player, e) && e.position().distanceToSqr(center) <= radius * radius
                        &&(domain.isEmpty()?player.hasLineOfSight(e):player.level().clip(new net.minecraft.world.level.ClipContext(center.add(0,1,0),e.getEyePosition(),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,player)).getType()==net.minecraft.world.phys.HitResult.Type.MISS))) {
                    applyMark(player, role, target, effect);
                }
            }
            case SPAWN_FIELD -> {
                if(effect.id().equals("zhezhi_inklit")){fiveWorld.beginLiberation(player,role);break;}
                if(effect.id().equals("zhezhi_pair")||effect.id().equals("zhezhi_middle")){
                    fiveWorld.place(player,role,effect.id().equals("zhezhi_pair"),effect.values().getOrDefault("airborne",0d)>0);break;
                }
                if(Set.of("denia_erosion","iuno_full_moon").contains(effect.id())) {
                    sixWorld.field(player,role,effect.id(),effect.durationTicks(),effect.values().getOrDefault("radius",8d),effect.values().getOrDefault("period",effect.id().equals("iuno_full_moon")?100d:80d).intValue());break;
                }
                if(effect.id().equals("ciaccona_performance"))removeFields(f->f.owner.equals(role.instanceId())&&f.effect.id().startsWith("ciaccona_shadow_"));
                removeFields(f -> f.owner.equals(role.instanceId()) && (f.effect.id().equals(effect.id()) || isMornyeField(f.effect.id()) && isMornyeField(effect.id())));
                String model = switch (effect.id()) { case "phoebe_ring" -> "skill_phoebe_ring";case "suisui_landscape" -> "skill_suisui_landscape";case "ciaccona_performance" -> "ciaccona_microphone"; case "ciaccona_performer_guitar" -> "ciaccona_guitar_ensemble"; case "ciaccona_performer_drum" -> "ciaccona_drum_ensemble"; case "ciaccona_body_solo" -> ciacconaInstrument(effect,false); case "ciaccona_shadow_0", "ciaccona_shadow_1" -> ciacconaInstrument(effect,true); case "hecate" -> "hecate"; case "mornye_resonance", "mornye_strong_resonance" -> "mornye_observation"; default -> ""; };
                if(effect.id().equals("phrolova_conducting_stasis"))model="skill_light_phrolova__conducting_stasis_field";
                Vec3 center=model.equals("hecate")?hecateHome(player):(effect.id().equals("lynae_paint")||effect.id().equals("suisui_landscape")||effect.id().startsWith("ciaccona_shadow_"))?GroundImpactGeometry.floor(player):effect.id().startsWith("ciaccona_performer_")?player.position().add(SpecialMovementRuntime.dodgeDirection(player,effect.id().endsWith("guitar")?-1:1,0).scale(2.2)).add(SpecialMovementRuntime.dodgeDirection(player,0,-1).scale(.6)):player.position();
                if(effect.id().equals("phoebe_ring")){center=tideAim(player);phoebeStasisTargets.put(role.instanceId(),new HashSet<>());}
                net.minecraft.world.entity.Entity visual = model.isEmpty() ? null : model.equals("hecate")
                    ? HecateEntity.spawn(player,role.instanceId(),center,Math.max(1,effect.durationTicks()))
                    : ConstructEntity.spawn(player.serverLevel(), player, role.instanceId(), model, center, effect.durationTicks()==0?12000:Math.max(1, effect.durationTicks()), 1f, effect.id().equals("ciaccona_body_solo"), effect.id().startsWith("ciaccona_performer_")||effect.id().equals("ciaccona_performance")?"attack":"idle");
                var created=new Field(role.instanceId(), effect, center, new FieldArea(dimension(player),center.x,center.y,center.z, effect.values().getOrDefault("radius",6d), effect.durationTicks()==0?Long.MAX_VALUE:Math.addExact(now, effect.durationTicks())), visual);
                fields.add(created);
                if(effect.id().equals("phrolova_conducting_stasis"))phrolovaConductingStasis(player,created);
            }
            case REMOVE_FIELD -> {
                if(effect.id().equals("lupa_hunt_and_glory"))buffs.removeIf(b->b.owner.equals(role.instanceId())
                    &&Set.of("lupa_hunt","lupa_hunt_attack","lupa_glory").contains(b.effect.id()));
                if(effect.id().equals("shorekeeper_domain"))threeWorld.removeDomain(role.instanceId());
                if(role.instanceId().equals(recitalPoseOwner)&&(effect.id().equals("ciaccona_performance")&&ciacconaPoseAction.equals("ciaccona/recital")||effect.id().equals("ciaccona_body_solo")&&ciacconaPoseAction.startsWith("ciaccona/solo_")))clearCiacconaPose(player);
                removeFields(f->f.owner.equals(role.instanceId())&&(f.effect.id().equals(effect.id())||effect.id().equals("ciaccona_performance")&&f.effect.id().startsWith("ciaccona_performer_")));
            }
            case FORM -> {
                sendForm(player, role, effect.id(), effect.durationTicks());specialMovement.formChanged(player,role,effect.id());
                if(role.character()==CharacterId.PHROLOVA&&effect.id().equals("conducting")&&team.active().orElse(null)==role)
                    // Opening is visual, without fabricated R damage or an energy cost.
                    // The 24-second conducting clock remains independent; a real command replaces this clip.
                    Network.visual(player,new Network.Visual(player.getId(),role.instanceId(),"mc_wuwa:phrolova/conduction_enter",PlatformRuntime.time(player),90,++sequence,""));
            }
            case HEAL_TEAM -> { for (int i = 0; i < 3; i++) team.at(i).ifPresent(c -> heal(player, role, c, effect.amount(),effect.values().getOrDefault("sourceHealingBonus",0d))); PlatformRuntime.applyHealth(player, ModAttachments.team(player), false); passiveEvent(player, role, WeaponPassiveEngine.Trigger.HEAL, effect.id()+":"+sequence); }
            case HEAL_ACTIVE -> {
                // Only the periodic field heal is spatial. Matrix/error/cheat-death heals keep their own rules.
                if (effect.id().equals("mornye_field") && fields.stream().noneMatch(f -> f.owner.equals(role.instanceId()) && isMornyeField(f.effect.id()) && insidePeriodicHeal(f, player,effect))) break;
                if(effect.id().equals("shorekeeper_domain")&&fields.stream().noneMatch(f->f.owner.equals(role.instanceId())&&f.effect.id().equals("shorekeeper_domain")&&insidePeriodicHeal(f,player,effect)))break;
                if(effect.id().equals("suisui_dance")&&fields.stream().noneMatch(f->f.owner.equals(role.instanceId())&&f.effect.id().equals("suisui_landscape")&&insideField(f,player)))break;
                team.active().ifPresent(c -> {if(effect.values().getOrDefault("ignoreHealingBonus",0d)>0)c.heal(effect.amount()*CombatMath.HEALTH_SCALE);else heal(player,role,c,effect.amount());}); PlatformRuntime.applyHealth(player, ModAttachments.team(player), false); passiveEvent(player, role, WeaponPassiveEngine.Trigger.HEAL, Long.toString(sequence));
            }
            case BUFF_SELF, BUFF_TEAM, BUFF_NEXT -> {
                if(effect.id().equals("iuno_pale_blessing")){sixBlessing(player,role,role,effect);break;}
                if(effect.values().getOrDefault("suisuiDomain",0d)>0&&fields.stream().noneMatch(f->f.owner.equals(role.instanceId())&&f.effect.id().equals("suisui_landscape")&&insideField(f,player)))break;
                if(effect.id().equals("suisui_moisture")){
                    var recipients=new ArrayList<UUID>();for(int slot=0;slot<3;slot++)team.at(slot).filter(CharacterInstance::alive).ifPresent(c->recipients.add(c.instanceId()));
                    tideWorld.moisture(role,recipients);team.active().ifPresent(current->tideWorld.entered(player,current,id->teamRole(player,id).orElse(null),(owner,recipient,flat,ratio)->tideHeal(player,owner,recipient,flat,ratio)));break;
                }
                UUID recipient = effect.kind() == MechanicsEngine.Kind.BUFF_SELF ? role.instanceId()
                    : effect.kind() == MechanicsEngine.Kind.BUFF_NEXT ? team.active().map(CharacterInstance::instanceId).orElse(null) : null;
                if(effect.kind()==MechanicsEngine.Kind.BUFF_NEXT&&effect.id().equals("zhezhi_outro"))
                    team.active().filter(next->!next.instanceId().equals(role.instanceId())).ifPresent(next->next.gain("energy",effect.values().getOrDefault("energyOnEnter",0d)));
                buffs.removeIf(b -> b.owner.equals(role.instanceId()) && b.effect.id().equals(effect.id()));
                if(effect.id().equals("aemeath_outro_other_roles_amp"))buffs.removeIf(b->b.owner.equals(role.instanceId())&&b.effect.id().equals("aemeath_outro_matching"));
                if (effect.id().equals("yangyang_outro")) buffs.removeIf(b -> b.owner.equals(role.instanceId()) && b.effect.id().equals("yangyang_outro_active"));
                if (effect.amount() != 0) buffs.add(new Buff(role.instanceId(), recipient, effect, effect.values().getOrDefault("actionScoped",0d)>0?now+committingActionTicks:effect.durationTicks() == 0 ? Long.MAX_VALUE : now + (effect.durationTicks() == 1 ? committingActionTicks : effect.durationTicks())));
            }
            // Pure mechanics retains this signal for traceability. The owning
            // body's live state gates recovery before stamina advances; never
            // turn one tick of prohibition into thirty ticks after it ends.
            case STAMINA_REGEN_LOCK -> { }
            case MOVE_SPECIAL -> {
                if(effect.id().equals("zhezhi_crane")){fiveWorld.moveToSelected(player,role,effect.values().get("slot").intValue(),effect.durationTicks());break;}
                if(effect.id().equals("qingxiao_capture_nearby_locks")) {
                    sixEvent(player,role,role,"qingxiao_capture_locks",null,Map.of("layers",(double)sixWorld.captureLocks(player,role,effect.values().getOrDefault("radius",8d))));break;
                }
                if(effect.values().getOrDefault("escapeHitstun",0d)>0) {
                    player.hurtTime=0;
                    player.setDeltaMovement(Vec3.ZERO);
                    player.hurtMarked=true;
                }
                if(effect.id().equals("shorekeeper_kernel"))threeWorld.kernel(player,role,(int)effect.amount(),effect.durationTicks(),hitTarget);
                else if(effect.id().equals("shorekeeper_butterfly"))sendForm(player,role,ruleState(role).form(),0);
                else if(effect.id().equals("restore_stamina"))team.stamina().restoreStamina(Math.min(StaminaDodge.MAX_STAMINA,team.stamina().snapshot().stamina()+effect.amount()));
                else if(effect.id().equals("hecate_reset"))resetHecate(player,role);
                else if(effect.id().equals("mornye_escape"))ModAttachments.team(player).state().stamina().successfulSkillEvade();
                else {
                    if(effect.id().equals("phoebe_ring_teleport")){
                        phoebeRing(player,role).ifPresent(f->player.teleportTo(f.center.x,f.center.y,f.center.z));
                    }else specialMovement.apply(player,role,effect.id());
                    if(effect.id().equals("qiuyuan_run"))Network.visual(player,new Network.Visual(player.getId(),role.instanceId(),"mc_wuwa:qiuyuan/skill_dash",now,60,++sequence,""));
                }
            }
            default -> { } // Typed target/field effects are audited separately; never reinterpret an unknown tag as damage.
            }
        }
    }
    private void launchShoreButterfly(ServerPlayer player,CharacterInstance role,int kernelIndex) {
        var definition=McWuwa.catalog().requireByCharacter("shorekeeper");
        var mapping=McWuwa.catalog().actionMappings("shorekeeper").stream().filter(a->a.id().equals("shorekeeper/butterfly")).findFirst().orElseThrow();
        var hit=definition.requireDamage(mapping.hitGroups().getFirst().damageId());
        var action=new Running(role.instanceId(),mapping,List.of(hit),PlatformRuntime.time(player),++sequence);
        action.automatic=true;action.independent=true;
        Vec3 origin=threeWorld.release(role,kernelIndex,player.position().add(0,.65,0));
        CombatProjectileEntity.guidedButterfly(player,role,origin,mapping.geometry().rangeBlocks(),target->settleTargets(player,role,action,0,hit,List.of(target),false));
    }
    private static Vec3 hecateHome(ServerPlayer player){
        return GroundImpactGeometry.floor(player).add(SpecialMovementRuntime.dodgeDirection(player,0,1).scale(3))
            .add(SpecialMovementRuntime.dodgeDirection(player,-1,0).scale(2));
    }
    private void resetHecate(ServerPlayer player,CharacterInstance role) {
        for(int index=0;index<fields.size();index++) {
            Field field=fields.get(index);
            if(!field.owner.equals(role.instanceId())||!field.effect.id().equals("hecate")||!fieldActive(field,player))continue;
            Vec3 center=hecateHome(player);
            if(field.visual!=null&&!field.visual.isRemoved())field.visual.setPos(center);
            fields.set(index,new Field(field.owner,field.effect,center,
                new FieldArea(dimension(player),center.x,center.y,center.z,field.area.radius(),field.area.expiresAt()),field.visual));
        }
    }
    private void collapseWordLock(ServerPlayer player,CharacterInstance owner,TargetReactionRuntime.WordLock lock) {
        var mapping=McWuwa.catalog().actionMappings("hsin").stream().filter(a->a.id().equals("hsin/illuminated_word_lock")).findFirst().orElseThrow();
        var definition=McWuwa.catalog().requireByCharacter("hsin");List<CharacterDefinition.DamageDefinition> hits=new ArrayList<>();
        for(var group:mapping.hitGroups())for(int i=0;i<group.count();i++)hits.add(definition.requireDamage(group.damageId()));
        var collapse=new Running(owner.instanceId(),mapping,List.copyOf(hits),PlatformRuntime.time(player),++sequence);
        collapse.anchor=lock.center();collapse.automatic=true;collapse.independent=true;secondary.add(collapse);
        collapse.visuals=List.of(SkillModelProfiles.light(player,owner,mapping.id(),lock.center(),mapping.durationTicks(),false));
        var old=mechanics.get(owner.instanceId());
        if(old!=null) {
            var next=MECHANICS.wordLockCollapsed(old,ModAttachments.team(player).state().active().orElse(null)==owner);
            commit(owner,next);RolePassiveRuntime.updateMechanics(player,owner,next);Network.sync(player);
        }
    }
    /** C5's field shares one fixed expiry, including enemies that enter after its cast. */
    private void phrolovaConductingStasis(ServerPlayer player,Field field){
        var owner=teamRole(player,field.owner).orElse(null);
        if(owner==null||ModAttachments.team(player).state().active().orElse(null)!=owner||!ruleState(owner).form().equals("conducting"))return;
        int remaining=(int)Math.max(0,field.area.expiresAt()-PlatformRuntime.time(player));
        if(remaining==0)return;
        for(var target:player.level().getEntitiesOfClass(LivingEntity.class,new net.minecraft.world.phys.AABB(field.center,field.center).inflate(field.area.radius()),
            entity->PlatformRuntime.canAttack(player,entity)&&field.area.contains(dimension(player),entity.getX(),entity.getY(),entity.getZ(),PlatformRuntime.time(player),true)&&player.hasLineOfSight(entity)))
            EffectRuntime.applyStasis(player,owner,target,remaining,"phrolova_conducting");
    }
    private void grantShield(ServerPlayer player,CharacterInstance source,CharacterInstance recipient,String id,double amount,long expires,boolean endsOnSwitch) {
        if(!recipient.alive()||amount<=0||expires<=PlatformRuntime.time(player))return;
        shields.grant(source.instanceId(),recipient.instanceId(),id,amount,expires,endsOnSwitch,PlatformRuntime.time(player));
        passiveEvent(player,recipient,WeaponPassiveEngine.Trigger.OWNER_SHIELD_GAIN,id+":"+sequence);
        sixWorld.shieldGained(player,recipient,sixCallbacks(player));
    }
    public void passiveEvent(ServerPlayer player, CharacterInstance role, WeaponPassiveEngine.Trigger trigger, String actionInstance) {
        passiveEvent(player, role, role, trigger, actionInstance);
    }
    private void passiveEvent(ServerPlayer player, CharacterInstance role, CharacterInstance actor, WeaponPassiveEngine.Trigger trigger, String actionInstance) {
        if (trigger == WeaponPassiveEngine.Trigger.HEAL) sonatas.event(player, role, "HEAL", false, offTuneEfficiency(player, role), 0);
        if(trigger==WeaponPassiveEngine.Trigger.OWNER_SHIELD_GAIN)sonatas.event(player,role,"SHIELD_GAIN",false,0,0);
        if(trigger==WeaponPassiveEngine.Trigger.SKILL_CAST)sonatas.event(player,role,"SKILL_CAST",false,0,0);
        if(trigger==WeaponPassiveEngine.Trigger.LIBERATION_CAST)sonatas.event(player,role,"LIBERATION_CAST",false,0,0);
        var definition = McWuwa.catalog().requireByWeapon(role.character().weaponPath());
        boolean active = ModAttachments.team(player).state().active().orElse(null) == role;
        long now = PlatformRuntime.time(player);
        var previous = weaponPassives.computeIfAbsent(role.instanceId(), id -> {
            Map<String, Long> ready = new HashMap<>();
            role.snapshot().cooldowns().forEach((key,value) -> { if (key.startsWith("weapon_passive_")) ready.put(key.substring(15), now + value); });
            return new WeaponPassiveEngine.State(Map.of(), ready, Map.of());
        });
        boolean duringBasic=running!=null&&running.weapon.equals(role.instanceId())&&WeaponPassiveEngine.basicSkillAction(running.action.id());
        String eventInstance=duringBasic&&role.character()==CharacterId.LYNAE
            &&(trigger==WeaponPassiveEngine.Trigger.APPLY_RUPTURE_SHIFTING||trigger==WeaponPassiveEngine.Trigger.APPLY_STRAIN_SHIFTING)
            ?Long.toString(running.sequence):actionInstance;
        var result = WEAPON_PASSIVES.onEvent(definition, role.refinement(), previous,
            new WeaponPassiveEngine.Event(trigger, PlatformRuntime.time(player), eventInstance, duringBasic, active, actor.instanceId().toString()));
        weaponPassives.put(role.instanceId(), result.state());
        result.state().readyTicks().forEach((key,value) -> role.setCooldown("weapon_passive_" + key, Math.max(0, value - now)));
        for (var buff : result.changedBuffs()) if (buff.scope() == WeaponPassiveEngine.Scope.EVENT_ACTOR)
            weaponBuffRecipients.put(role.instanceId() + ":" + buff.id(), actor.instanceId());
        if (result.concertoGranted() > 0) role.gain("concerto", result.concertoGranted());
    }
    public Map<String, Double> passiveModifiers(ServerPlayer player, CharacterInstance role, LivingEntity target, String kind, String element) {
        return passiveModifiers(player, role, target, kind, element, role.actionId());
    }
    public Map<String, Double> passiveModifiers(ServerPlayer player, CharacterInstance role, LivingEntity target, String kind, String element, String actionId) {
        long now=PlatformRuntime.time(player);
        currentTick=now;
        boolean radianceTarget=target!=null&&Math.max(EffectRuntime.layers(target,"spectro_frazzle"),fiveWorld.emberStacks(player,target))>=10&&SonataRuntime.counts(role,PlatformRuntime.weaponFor(player,role)).getOrDefault(11,0)>=5;
        Map<String, Double> result = new HashMap<>(sonatas.modifiers(role,now,kind,radianceTarget));var typed=new AttributeModifiers();var team=ModAttachments.team(player).state();
        activeRoleId=team.active().map(CharacterInstance::instanceId).orElse(null);
        if(role.character().sourceId()==1507)result.merge("crit_rate",cn.zgnhit.mcwuwa.content.mechanics.ZaniDamageRules.critRate(ruleState(role)),Double::sum);
        if(role.character().sourceId()==1312){
            if(role.chain()>=2)result.merge("crit_damage",.40,Double::sum);
            if(role.chain()>=4)result.merge("attack_pct",.20,Double::sum);
        }
        if(kind.equals("echo"))result.merge("crit_damage",fiveWorld.photoEchoCrit(player,role),Double::sum);
        result.merge("all_damage_bonus",fiveWorld.photoDamageBonus(player,role,kind,actionId),Double::sum);
        result.merge("damage_amplification",fiveWorld.photoChafeAmplification(player,target,actionId)+fiveWorld.zaniOutroAmplification(player,role,target,element),Double::sum);
        result.merge("resistance_reduction",fiveWorld.photoResistanceReduction(player,target,element),Double::sum);
        for (int i = 0; i < 3; i++) if (team.at(i).isPresent()) {
            var owner = team.at(i).orElseThrow(); var state = weaponPassives.getOrDefault(owner.instanceId(), WeaponPassiveEngine.State.empty());
            if(owner.alive()&&owner.character()==CharacterId.CARTETHYIA&&owner!=role)result.merge("incoming_healing_bonus",.20,Double::sum);
            for (var buff : WEAPON_PASSIVES.active(state, PlatformRuntime.time(player))) {
                boolean applies = switch (buff.scope()) {
                    case SELF -> owner == role;
                    case EVENT_ACTOR -> role.instanceId().equals(weaponBuffRecipients.get(owner.instanceId() + ":" + buff.id()));
                    case OWN_TEAM -> true;
                    case OWN_ACTIVE -> team.active().orElse(null) == role;
                    case ENEMIES_IN_AURA -> false;
                };
                if (!applies || (!buff.damageTag().equals("all") && !buff.damageTag().equals(kind)) || (!buff.element().equals("all") && !buff.element().equals(element))) continue;
                if (!buff.targetCondition().equals("none") && !(buff.targetCondition().equals("owner_off_field") && team.active().orElse(null) != owner)
                    && !(buff.targetCondition().equals("owner_on_field") && team.active().orElse(null) == owner)
                    && !(buff.targetCondition().equals("direct_spectro_frazzle") && cn.zgnhit.mcwuwa.content.mechanics.DamageQualifications.directSpectroFrazzle(actionId,element))
                    && !(buff.targetCondition().contains("aero_erosion") && target != null && EffectRuntime.hasEffect(target, "aero_erosion"))) continue;
                String identity=owner.instanceId()+"/"+buff.id()+"/"+buff.attribute();
                UUID contribution=UUID.nameUUIDFromBytes(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                var source=new EffectSource(player.getUUID(),owner.instanceId(),owner.character(),contribution,contribution);
                String sharedId=buff.id().startsWith("forged_dwarf_star/team_applicator/")?"forged_dwarf_star/team_applicator/"+role.instanceId():buff.id();
                String group=(buff.sameNameHighestOnly()?"weapon_shared/":"weapon/"+owner.instanceId()+"/")+sharedId+"/"+buff.attribute();
                typed.put(new AttributeModifiers.Modifier(contribution,source,AttributeBindings.zone(buff.attribute()),group,
                    buff.sameNameHighestOnly()?AttributeModifiers.Stacking.STRONGEST_MAGNITUDE:AttributeModifiers.Stacking.ADD,
                    buff.value(),buff.expiresAtTick(),AttributeBindings.selector(buff.attribute(),buff.damageTag(),buff.element())));
            }
        }
        AttributeBindings.values(typed.resolve(AttributeBindings.context(kind,element,false,false),now)).forEach((key,value)->result.merge(key,value,Double::sum));
        mechanicsModifiers(player,role, actionId, kind, element, target, false).attributes().forEach((key, value) -> result.merge(key, value, Double::sum));
        result.merge("defense_ignore",chisaThreadDefenseIgnore(player,role,target),Double::sum);
        threeRoleTeamModifiers(player,role).forEach((key,value)->result.merge(key,value,Double::sum));
        if (team.active().orElse(null) != role) {
            var definition = McWuwa.catalog().requireByWeapon(role.character().weaponPath());
            WEAPON_PASSIVES.permanent(definition, role.refinement(), false).stream().filter(b -> b.targetCondition().equals("owner_off_field")).forEach(b -> result.merge(b.attribute(), b.value(), Double::sum));
        }
        boolean ciacconaAura=fields.stream().anyMatch(f->insideField(f,player)&&f.effect.id().startsWith("ciaccona_shadow_")&&f.effect.values().getOrDefault("solo",0d)>0&&now>=f.effect.values().getOrDefault("soloReadyAt",0d));
        for(int slot=0;slot<3;slot++) {
            var owner=team.at(slot).orElse(null);if(owner==null||!owner.alive()||owner.character()!=CharacterId.CIACCONA)continue;
            var cs=mechanics.get(owner.instanceId());
            if(cs!=null&&owner.instanceId().equals(activeRoleId)&&cs.timer("ciaccona_solo")>0)ciacconaAura=true;
            boolean performance=fields.stream().anyMatch(f->f.owner.equals(owner.instanceId())&&f.effect.id().equals("ciaccona_performance")&&insideField(f,player));
            if(cs!=null&&cs.timer("ciaccona_performance")>0&&owner.chain()>=2)result.merge("aero_bonus",.40,Double::sum);
            if(performance) {
                ciacconaAura=true;
                if(owner.chain()>=5)result.merge("damage_reduction",.30,Double::sum);
            }
        }
        if(ciacconaAura)result.merge("aero_bonus",.24,Double::sum);
        for(Buff buff:buffs)if(buff.expires>now&&role.instanceId().equals(buff.recipient)&&buff.effect.id().equals("suisui_c2_crit")&&fields.stream().anyMatch(f->f.owner.equals(buff.owner)&&f.effect.id().equals("suisui_landscape")&&insideField(f,player)))result.merge("crit_damage",buff.effect.amount(),Double::sum);
        for(int slot=0;slot<3;slot++){
            var owner=team.at(slot).orElse(null);if(owner==null||owner.character()!=CharacterId.SUISUI||!owner.alive())continue;
            boolean covered=fields.stream().anyMatch(f->f.owner.equals(owner.instanceId())&&f.effect.id().equals("suisui_landscape")&&insideField(f,player));
            if(team.active().orElse(null)==role&&covered&&ruleState(owner).timer("suisui_dance_tier")>=2&&buffs.stream().anyMatch(b->b.owner.equals(owner.instanceId())&&b.effect.id().equals("suisui_chongming")&&b.expires>now))
                result.merge("all_damage_bonus",Math.min(.12,Math.max(0,NativeStats.of(owner,PlatformRuntime.weaponFor(player,owner),EchoRuntime.modifiers(player,owner)).energyRegen()-2)*.20),Double::sum);
        }
        double fieldDefense = fields.stream().filter(f -> insideField(f, player)).mapToDouble(f -> f.effect.values().getOrDefault("defensePercent", 0d)).max().orElse(0);
        result.merge("defense_pct", fieldDefense, Double::sum);
        if(role.character().sourceId()==1306){
            var critExtras=new HashMap<>(result);EchoRuntime.modifiers(player,role).forEach((key,value)->critExtras.merge(key,value,Double::sum));
            double rawCrit=.05+NativeStats.of(role,PlatformRuntime.weaponFor(player,role),critExtras).bonus("crit_rate");
            fiveWorld.augustaBonuses(player,role,rawCrit).forEach((key,value)->result.merge(key,value,Double::sum));
        }
        return Map.copyOf(result);
    }
    public void switched(ServerPlayer player, CharacterInstance outgoing, boolean introOutro) {
        currentTick=PlatformRuntime.time(player);
        boolean usedUnison=introOutro&&outgoing!=null&&outgoing.hasTemporaryState("unison_consumed_pending");
        boolean unisonResponse=usedUnison&&singleplayerUnison(player);
        if(outgoing!=null){outgoing.removeTemporaryState("unison_consumed_pending");if(usedUnison)consumedUnison(outgoing);}
        double incomingTuneAmplification = ModAttachments.team(player).state().active().map(role->tuneBreakAmplification(player,role)).orElse(0d);
        sonatas.switched(player, outgoing, introOutro, incomingTuneAmplification);
        if (outgoing != null) {
            sixJumpOwner=sixJumpShortOwner=null;sixJumpHeld=sixJumpHoldQueued=sixJumpReleased=false;
            buffs.removeIf(b->outgoing.instanceId().equals(b.recipient)&&b.effect.id().equals("iuno_pale_blessing"));
            // A freshly equipped weapon can switch before its first runtime tick (e.g. creative refill).
            // Its legal outro still has to execute; absence of a cache is not absence of a role.
            stateFor(outgoing,McWuwa.catalog().requireByWeapon(outgoing.character().weaponPath()).characterId());
            if(outgoing.character()==CharacterId.PHROLOVA||outgoing.character()==CharacterId.MORNYE||outgoing.character()==CharacterId.QIUYUAN)EffectRuntime.clearOwnedStasis(outgoing.instanceId());
            if(outgoing.character()==CharacterId.PHROLOVA)removeFields(f->f.owner.equals(outgoing.instanceId())&&f.effect.id().equals("phrolova_conducting_stasis"));
            if(outgoing.character()==CharacterId.PHROLOVA&&ruleState(outgoing).form().equals("conducting")){
                removeFields(f->f.owner.equals(outgoing.instanceId())&&f.effect.id().startsWith("phrolova_reserve_"));
                int remaining=Math.max(1,ruleState(outgoing).timer("conducting"));
                Vec3 seat=GroundImpactGeometry.floor(player).add(0,5.2,0);
                for(String model:List.of("phrolova_reserve_conductor","phrolova_conductor_throne")){
                    var effect=new MechanicsEngine.Effect(MechanicsEngine.Kind.SPAWN_FIELD,"phrolova_reserve_"+model,1,remaining,Map.of());
                    var visual=ConstructEntity.spawn(player.serverLevel(),player,outgoing.instanceId(),model,seat,remaining,1f,false,"idle");
                    fields.add(new Field(outgoing.instanceId(),effect,seat,new FieldArea(dimension(player),seat.x,seat.y,seat.z,.1,Math.addExact(currentTick,remaining)),visual));
                }
            }
            if(outgoing.character()==CharacterId.CIACCONA)for(int index=0;index<fields.size();index++) {
                var field=fields.get(index);
                if(field.owner.equals(outgoing.instanceId())&&field.effect.id().equals("ciaccona_performance")) {
                    if(field.visual!=null)field.visual.discard();
                    int remaining=(int)Math.max(1,field.area.expiresAt()-currentTick);
                    fields.set(index,new Field(field.owner,field.effect,field.center,field.area,ConstructEntity.spawn(player.serverLevel(),player,outgoing.instanceId(),"ciaccona_singer_ensemble",field.center,remaining,1f,false,"attack")));
                }
            }
            RolePassiveRuntime.onSwitch(player, outgoing);
            shields.onSwitch(outgoing.instanceId());
            pendingMarks.remove(outgoing.instanceId());
            if (outgoing.character() == CharacterId.YINLIN) EffectRuntime.clearOwnedMark(player.getUUID(),outgoing.instanceId(),"yinlin_sin");
            passiveEvent(player, outgoing, WeaponPassiveEngine.Trigger.SWITCH_OUT, "switch:" + sequence);
            buffs.removeIf(b -> b.recipient != null && b.recipient.equals(outgoing.instanceId()) && b.effect.values().getOrDefault("endsOnSwitch", 0d) > 0&&!b.effect.id().equals("augusta_outro"));
            if (introOutro) {
                SkillModelProfiles.departure(player,outgoing);
                for(Buff grant:List.copyOf(buffs))if(grant.expires>currentTick&&outgoing.instanceId().equals(grant.recipient)&&grant.effect.id().equals("suisui_smoke_next")){
                    buffs.remove(grant);var next=ModAttachments.team(player).state().active().orElse(null);
                    if(next!=null)buffs.add(new Buff(grant.owner,next.instanceId(),new MechanicsEngine.Effect(MechanicsEngine.Kind.BUFF_NEXT,"suisui_smoke",1,280,Map.of("endsOnSwitch",1d)),currentTick+280));
                }
                for(var grant:List.copyOf(buffs))if(grant.expires>currentTick&&outgoing.instanceId().equals(grant.recipient)&&grant.effect.id().equals("augusta_outro"))
                    fiveWorld.augustaAllyOutro(player,grant.owner);
                passiveEvent(player,outgoing,WeaponPassiveEngine.Trigger.OUTRO_CAST,"outro:"+sequence);
                if(!usedUnison)passiveEvent(player, outgoing, WeaponPassiveEngine.Trigger.CONCERTO_SPENT, "switch:" + sequence);
                var state = mechanics.get(outgoing.instanceId());
                if(state!=null&&state.characterId().equals("suoming")){
                    var timers=new HashMap<>(state.timers());timers.put("suoming_unison_layers",unisonStacks(player,outgoing));
                    state=new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),state.nextBasic(),state.resources(),timers);
                }
                if (state != null) { var result = MECHANICS.reduce(state, MechanicsEngine.Input.OUTRO, context(player, outgoing)); if (result.accepted()) { commit(outgoing, result.state()); applyEffects(player, outgoing, result.effects()); scheduleTickActions(player, outgoing, result.effects()); } }
            }
            var state = mechanics.get(outgoing.instanceId());
            if (state != null) { var result = MECHANICS.reduce(state, MechanicsEngine.Input.SWITCH_OUT, context(player, outgoing)); if (result.accepted()) {
                commit(outgoing, result.state()); applyEffects(player, outgoing, result.effects()); scheduleTickActions(player, outgoing, result.effects());
                // Wolf Dance reserves the assist on any switch within eight seconds.
                // SWITCH_OUT creates waiting; OUTRO is earlier and requires concerto.
                if(outgoing.character()==CharacterId.LUPA&&result.state().timer("lupa_assist_waiting")>0)
                    sixWorld.reserveAssist(player,outgoing,result.state().timer("lupa_assist_waiting"),sixCallbacks(player));
            } }
        }
        if(outgoing!=null)buffs.removeIf(b->outgoing.instanceId().equals(b.recipient)&&b.effect.id().equals("augusta_outro"));
        var current=ModAttachments.team(player).state().active().orElse(null);
        HecateEntity.teamChanged(player);
        boolean replacedEntry=false;
        if(current!=null) {
            tideWorld.entered(player,current,id->teamRole(player,id).orElse(null),(owner,recipient,flat,ratio)->tideHeal(player,owner,recipient,flat,ratio));
            var currentState=stateFor(current,McWuwa.catalog().requireByWeapon(current.character().weaponPath()).characterId());
            var entry=MECHANICS.onTeamEntry(currentState,context(player,current));
            if(entry.reason().equals("phrolova_return_curtain")) {
                replacedEntry=true;
                commit(current,entry.state());
                applyEffects(player,current,entry.effects());
                scheduleTickActions(player,current,entry.effects());
                currentState=entry.state();
            }
            sendForm(player,current,currentState.form(),0);
        }
        if (introOutro&&!replacedEntry) {
            var active = ModAttachments.team(player).state().active().orElseThrow(); var definition = McWuwa.catalog().requireByWeapon(active.character().weaponPath());
            try {
                var state = stateFor(active, definition.characterId());
                if(UnisonRules.canRespond(state)) {
                    boolean responding=unisonResponse&&singleplayerUnison(player);
                    state=UnisonRules.prepareIntro(state,responding);
                    if(responding){
                        unison.responded(active.instanceId(),currentTick,0);
                        for(var supporter:unisonSupporters(player))
                            unison.grantHsinBonus(supporter.instanceId(),currentTick,UnisonRules.extraLayers(supporter.chain()));
                        RolePassiveRuntime.onUnison(player,active);EchoRuntime.onUnison(player,active);
                        passiveEvent(player,active,WeaponPassiveEngine.Trigger.RESPOND_UNISON,"unison:"+sequence);
                        sonatas.event(player,active,"UNISON",false,0,0);
                    }
                }
                var result = MECHANICS.reduce(state, MechanicsEngine.Input.INTRO, context(player, active));
                if (result.accepted()) {
                    var introActions=result.effects().stream().filter(e->e.kind()==MechanicsEngine.Kind.ACTION)
                        .map(e->McWuwa.catalog().actionMappings(definition.characterId()).stream().filter(a->a.id().equals(e.id())).findFirst().orElseThrow()).toList();
                    var motion=introActions.isEmpty()?List.<MechanicsEngine.Effect>of():result.effects().stream().filter(ActionRuntime::actionMotionEffect).toList();
                    commit(active, result.state());RolePassiveRuntime.updateMechanics(player,active,result.state());
                    applyEffects(player,active,motion.isEmpty()?result.effects():result.effects().stream().filter(e->!motion.contains(e)).toList());
                    if(!introActions.isEmpty()) {
                        start(player,active,definition,introActions.getFirst());
                        if(!motion.isEmpty()){
                            applyEffects(player,active,motion);
                            specialMovement.extendCommandSupport(player,active,running.action.id(),running.action.durationTicks());
                        }
                        for(int index=1;index<introActions.size();index++)queue.add(introActions.get(index));
                    }
                }
            } catch (IllegalArgumentException unsupported) { McWuwa.catalog().actionMappings(definition.characterId()).stream().filter(a -> a.id().equals(definition.characterId() + "/intro")).findFirst().ifPresent(a -> start(player, active, definition, a)); }
            for(var shield:fiveWorld.augustaIntroShields(player,id->teamRole(player,id).map(owner->PlatformRuntime.projectedMaxHealth(player,combatStats(player,owner).health())/CombatMath.HEALTH_SCALE).orElse(0d)))
                teamRole(player,shield.owner()).ifPresent(owner->grantShield(player,owner,active,"augusta_realm_intro",CombatMath.mappedHealth(shield.amount()),currentTick+shield.ticks(),true));
            // Resolve the incoming intro before upgrading the existing domain.
            var team=ModAttachments.team(player).state();
            for(int slot=0;slot<3;slot++){
                var shore=team.at(slot).orElse(null);
                if(shore==null||!shore.alive()||shore.character()!=CharacterId.SHOREKEEPER)continue;
                var domain=ruleState(shore);
                if(domain.timer("shore_domain")<=0||domain.timer("shore_stage")>=3||fields.stream().noneMatch(f->f.owner.equals(shore.instanceId())&&f.effect.id().equals("shorekeeper_domain")&&insideField(f,player)))continue;
                var timers=new HashMap<>(domain.timers());timers.put("shore_stage",domain.timer("shore_stage")+1);
                ruleState(player,shore,new MechanicsState(domain.characterId(),domain.form(),domain.mode(),domain.chain(),domain.nextBasic(),domain.resources(),timers));
            }
        }
    }
    public double reduceIncoming(ServerPlayer player, double damage) {
        return reduceIncoming(player,damage,false);
    }
    private static MechanicsEngine.Input heldSkillInput(ServerPlayer player,CharacterInstance role,MechanicsState state){
        if(role.character()==CharacterId.QIUYUAN&&!qiuyuanTargetNearby(player,8)
            &&!(role.chain()>=3&&role.resource("concerto")>=100&&!state.form().equals("drunken")&&state.timer("qiuyuan_canopy")==0))
            return MechanicsEngine.Input.SKILL_RUN;
        return MechanicsEngine.Input.SKILL_HOLD;
    }
    private static boolean qiuyuanTargetNearby(ServerPlayer player,double radius) {
        return !player.level().getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(radius),e->PlatformRuntime.canAttack(player,e)&&player.distanceToSqr(e)<=radius*radius&&player.hasLineOfSight(e)).isEmpty();
    }
    private void finishQiuyuanRun(ServerPlayer player,CharacterInstance role,boolean allowHit) {
        var result=MECHANICS.finishQiuyuanRun(ruleState(role),context(player,role),allowHit&&qiuyuanTargetNearby(player,5));
        if(!result.accepted())return;
        cancel(player);commit(role,result.state());applyEffects(player,role,result.effects());scheduleTickActions(player,role,result.effects());
        if(result.effects().isEmpty())Network.visual(player,new Network.Visual(player.getId(),role.instanceId(),"",PlatformRuntime.time(player),0,++sequence,""));
        Network.sync(player);
    }
    /** Runs only for a real, evadable hostile hit at the damage event boundary. */
    public boolean qiuyuanCounter(ServerPlayer player) {
        var role=ModAttachments.team(player).state().active().orElse(null);
        if(role==null||!role.alive()||role.character()!=CharacterId.QIUYUAN)return false;
        var state=stateFor(role,"qiuyuan");
        if(state.timer("qiuyuan_skill_counter")>0) {
            var timers=new HashMap<>(state.timers());timers.remove("qiuyuan_skill_counter");
            if(running!=null&&running.weapon.equals(role.instanceId())&&running.action.id().equals("qiuyuan/skill"))
                timers.put("qiuyuan_skill_armor",(int)Math.max(1,running.completionTick-PlatformRuntime.time(player)));
            ruleState(player,role,new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),state.nextBasic(),state.resources(),timers));
            applyEffects(player,role,List.of(new MechanicsEngine.Effect(MechanicsEngine.Kind.CONTROL_AREA,"qiuyuan_skill_stasis",1,60,Map.of("radius",6.0))));return true;
        }
        var result=MECHANICS.reduce(state,MechanicsEngine.Input.BLOCK_HIT,context(player,role));
        if(!result.accepted())return false;
        cancel(player);commit(role,result.state());applyEffects(player,role,result.effects());scheduleTickActions(player,role,result.effects());Network.sync(player);return true;
    }
    /** Every native Pre/Post pair gets a frame, including hits with no queued heal.
     * A nested damage event therefore cannot consume the outer hit's healing. */
    public void beginIncomingDamage(){afterDamage.push(new ArrayList<>());}
    public void finishIncomingDamage(ServerPlayer player){
        if(afterDamage.isEmpty())return;
        var effects=afterDamage.pop();
        if(player.isAlive())for(var deferred:effects)applyEffects(player,deferred.owner(),deferred.effects());
    }
    public double reduceIncoming(ServerPlayer player, double damage, boolean monsterAttack) {
        currentTick=PlatformRuntime.time(player);
        var team = ModAttachments.team(player).state();
        var active = team.active().orElse(null);
        if(damage>0&&active!=null&&ruleState(active).form().equals("fox"))sceneInteraction(player);
        if (active == null) return damage;
        if(active.chain()>=4&&ruleState(active).characterId().equals("lucilla")&&ruleState(active).timer("lucilla_memory_channel")>0)damage*=.70;
        if(ruleState(active).characterId().equals("zani")){
            var state=ruleState(active);if(state.timer("zani_guard_mode")>=2)damage*=.60;
            if(state.timer("zani_post_guard")>0)damage*=.70;
        }
        if(qualifiedPlungeImmunity(active))return 0;
        if(damage>0&&monsterAttack&&active.character()==CharacterId.QINGXIAO) {
            var guard=MECHANICS.sixWorldEvent(ruleState(active),context(player,active),"qingxiao_guard_hit",Map.of("monsterAttack",1d));
            if(guard.accepted()) {
                commit(active,guard.state());
                applyEffects(player,active,guard.effects());
            }
        }
        damage*=Math.max(0,1-MechanicModifierRules.incomingDamageReduction(buffs.stream().filter(b->applies(b,active)).map(Buff::effect).toList()));
        double passiveReduction=combatStats(player,active,"incoming").bonus("damage_reduction");
        damage *= Math.max(0, 1 - passiveReduction);
        if(active.character()==CharacterId.CIACCONA&&ruleState(active).timer("ciaccona_performance")>0)damage*=.50;
        for (var buff : buffs) if (applies(buff, active)) {
            if (buff.effect.id().endsWith("_invulnerable")) return 0;
            if (buff.effect.id().endsWith("_damage_reduction")) damage *= Math.max(0, 1 - buff.effect.amount());
        }
        var activeMechanics=mechanics.get(active.instanceId());
        if(active.chain()>=6&&active.character()==CharacterId.LYNAE&&active.actionId().equals("lynae/cruise_air_heavy"))damage*=.70;
        boolean existingBarrier=activeMechanics!=null&&active.character()==CharacterId.HSIN&&activeMechanics.timer("hsin_barrier_protection")>0;
        if(existingBarrier)damage=MECHANICS.hsinBarrier(activeMechanics,damage,false).damage();
        // Existing reduction protects the shield. A new reactive barrier still
        // requires positive post-shield damage; an absorbed hit spends no charge.
        damage=shields.absorb(active.instanceId(),damage,PlatformRuntime.time(player));
        if(!existingBarrier&&activeMechanics!=null&&damage>0&&active.character()==CharacterId.HSIN) {
            var result=MECHANICS.hsinBarrier(activeMechanics,damage,monsterAttack);
            damage=result.damage();commit(active,result.state());RolePassiveRuntime.updateMechanics(player,active,result.state());
        }
        for (int i = 0; i < 3; i++) if (team.at(i).isPresent()) {
            var role = team.at(i).orElseThrow(); var state = mechanics.get(role.instanceId()); if (state == null) continue;
            if (state.characterId().equals("mornye")) {
                double yellowHearts = Math.min(player.getAbsorptionAmount(), damage);
                var result = MECHANICS.boundedProof(state, Math.max(0, damage - yellowHearts), player.getHealth(), player.getMaxHealth(), combatStats(player,role).defense());
                damage = result.damage() + yellowHearts; commit(role, result.state());
                if(afterDamage.isEmpty())applyEffects(player,role,result.effects());
                else if(!result.effects().isEmpty())afterDamage.peek().add(new AfterDamage(role,List.copyOf(result.effects())));
            }
        }
        // LivingDamageEvent.Pre precedes vanilla absorption consumption.
        if(fiveWorld.zaniLethalProtection(player,active))
            damage=Math.min(damage,Math.max(0,player.getHealth()-CombatMath.mappedHealth(1))+player.getAbsorptionAmount());
        if(damage>=player.getHealth()+player.getAbsorptionAmount()&&damage>0)for(int slot=0;slot<3;slot++){
            var guardian=team.at(slot).orElse(null);if(guardian==null||guardian==active||!guardian.alive()||!Set.of(CharacterId.SHOREKEEPER,CharacterId.SUISUI).contains(guardian.character())||ruleState(guardian).timer(guardian.character()==CharacterId.SUISUI?"suisui_rescue_cd":"shore_death_cd")>0)continue;
            double transferred=Math.min(guardian.health()*.5,Math.max(0,guardian.health()-CombatMath.HEALTH_SCALE));
            guardian.commitHealth(guardian.health()-transferred);
            var gs=ruleState(guardian);var gt=new HashMap<>(gs.timers());gt.put(guardian.character()==CharacterId.SUISUI?"suisui_rescue_cd":"shore_death_cd",12000);
            ruleState(player,guardian,new MechanicsState(gs.characterId(),gs.form(),gs.mode(),gs.chain(),gs.nextBasic(),gs.resources(),gt));active.heal(transferred);
            PlatformRuntime.applyHealth(player,ModAttachments.team(player),false);Network.sync(player);return 0;
        }
        return preventLethal(player,active,damage);
    }
    private double preventLethal(ServerPlayer player,CharacterInstance role,double damage) {
        if(damage<player.getHealth()+player.getAbsorptionAmount()||damage<=0)return damage;
        var state=stateFor(role,McWuwa.catalog().requireByWeapon(role.character().weaponPath()).characterId());
        var protection=DeathProtectionRules.choose(state,role.actionId(),role.cooldown("death_protection")==0);
        if(!protection.allowed())return damage;
        if(protection.cooldown())role.setCooldown("death_protection",12000);
        double healthFloor=role.character()==CharacterId.CHISA?CombatMath.mappedHealth(1):1;
        double minimum=Math.min(healthFloor,player.getHealth());
        if(protection.immuneTicks()>0) {
            var immune=new MechanicsEngine.Effect(MechanicsEngine.Kind.BUFF_SELF,"chain_death_invulnerable",1,protection.immuneTicks(),
                role.character()==CharacterId.HSIN?Map.of("preventsNativeHurt",1d):Map.of());
            applyEffects(player,role,List.of(immune));
        }
        if(protection.shieldFraction()>0)grantShield(player,role,role,"cartethyia_death",player.getMaxHealth()*protection.shieldFraction(),PlatformRuntime.time(player)+200,false);
        if(protection.ghost()) {
            cancel(player);var cleaned=mechanics.get(role.instanceId());var timers=new HashMap<>(cleaned.timers());timers.put("electronic_ghost",100);
            timers.put("electronic_ghost_pending",1);var next=new MechanicsState(cleaned.characterId(),cleaned.form(),cleaned.mode(),cleaned.chain(),1,cleaned.resources(),timers);
            commit(role,next);RolePassiveRuntime.updateMechanics(player,role,next);
            double attack=combatStats(player,role).attack();
            applyEffects(player,role,List.of(new MechanicsEngine.Effect(MechanicsEngine.Kind.SHIELD_TEAM,"aemeath_ghost",attack*3.6,100,Map.of())));
        }
        if(protection.healFraction()>0) {
            role.commitHealth(Math.max(minimum,role.maxHealth()*protection.healFraction()));
            PlatformRuntime.applyHealth(player,ModAttachments.team(player),false);Network.sync(player);return 0;
        }
        Network.sync(player);return player.getAbsorptionAmount()+Math.max(0,player.getHealth()-minimum);
    }
    public boolean blockHit(ServerPlayer player) {
        var role = ModAttachments.team(player).state().active(); if (role.isEmpty()) return false;
        var state = mechanics.get(role.get().instanceId()); if(state==null)return false;
        boolean zaniGuard=state.characterId().equals("zani")&&(state.timer("zani_guard_mode")==1
            ||state.timer("zani_guard_mode")>1&&state.timer("zani_guard_age")<6);
        if(!zaniGuard&&state.timer("suoming_guard")<=0&&state.timer("block")<=0&&state.timer("iai_guard")<=0)return false;
        var result = MECHANICS.reduce(state, MechanicsEngine.Input.BLOCK_HIT, context(player, role.get())); if (!result.accepted()) return false;
        cancel(player); commit(role.get(), result.state()); applyEffects(player, role.get(), result.effects());
        var definition = McWuwa.catalog().requireByWeapon(role.get().character().weaponPath());
        for (var effect : result.effects()) if (effect.kind() == MechanicsEngine.Kind.ACTION) {
            var action = McWuwa.catalog().actionMappings(definition.characterId()).stream().filter(a -> a.id().equals(effect.id())).findFirst();
            if (action.isPresent()) { if (running == null) start(player, role.get(), definition, action.get()); else queue.add(action.get()); }
        }
        return true;
    }
    private boolean applies(Buff buff, CharacterInstance role) {
        return (buff.effect.values().getOrDefault("activeOnly",0d)==0||role.instanceId().equals(activeRoleId))&&buff.expires>currentTick&&(buff.recipient==null||buff.recipient.equals(role.instanceId()))&&!(buff.effect.values().getOrDefault("excludeOwner",0d)>0&&buff.owner.equals(role.instanceId()));
    }
    private Map<String,Double> threeRoleTeamModifiers(ServerPlayer player,CharacterInstance recipient){
        var out=new HashMap<String,Double>();var team=ModAttachments.team(player).state();
        for(int slot=0;slot<3;slot++){
            var owner=team.at(slot).orElse(null);if(owner==null||!owner.alive())continue;var state=mechanics.get(owner.instanceId());if(state==null)continue;
            if(owner.character()==CharacterId.SHOREKEEPER&&state.timer("shore_domain")>0&&fields.stream().anyMatch(f->f.owner.equals(owner.instanceId())&&f.effect.id().equals("shorekeeper_domain")&&insideField(f,player))){
                var extra=new HashMap<>(EchoRuntime.modifiers(player,owner));extra.merge("energy_regen",.10,Double::sum);
                double efficiency=NativeStats.of(owner,PlatformRuntime.weaponFor(player,owner),extra).energyRegen();int stage=state.timer("shore_stage");
                if(stage>=2)out.merge("crit_rate",Math.min(.125,efficiency*.05),Math::max);
                if(stage>=3)out.merge("crit_damage",Math.min(.25,efficiency*.10),Math::max);
                if(owner.chain()>=2)out.merge("attack_pct",.40,Math::max);
                if(recipient==owner||recipient.character()==CharacterId.AERO_ROVER)out.merge("energy_regen",.10,Math::max);
            }
            if(owner.character()==CharacterId.SIGRIKA&&owner!=recipient&&team.active().orElse(null)==recipient){double bonus=state.timer("sig_blessings")*.03;out.merge("aero_bonus",bonus,Double::sum);out.merge("echo_bonus",bonus,Double::sum);}
        }
        return out;
    }
    /** Source coefficients; MC policy: one visible target in a 12-block, 60-degree forward cone.
     * Resolve immediately on the outgoing weapon so the incoming timeline cannot erase the hit. */
    private void outroHit(ServerPlayer player,CharacterInstance owner,MechanicsEngine.Effect effect) {
        Vec3 eye=player.getEyePosition(),look=player.getLookAngle();
        double multiplier=player.level().getServer().isSingleplayer()?effect.amount():effect.values().getOrDefault("multiplayerAmount",effect.amount());
        player.level().getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(12),
            target->PlatformRuntime.canAttack(player,target)&&player.distanceToSqr(target)<=144&&player.hasLineOfSight(target)
                &&target.getBoundingBox().getCenter().subtract(eye).normalize().dot(look)>=.5)
            .stream().min(Comparator.comparingDouble(player::distanceToSqr)).ifPresent(target->{
                boolean zani=effect.id().equals("zani_outro");
                double rate=zani?fiveWorld.zaniOutroMultiplier(player,owner,target):multiplier;
                boolean hit=PlatformRuntime.nativeHit(player,owner,target,RoleProcDefinitions.solo("outro_"+effect.id(),effect.id().equals("qiuyuan")?"echo":"outro",owner.character().element().name().toLowerCase(Locale.ROOT),"attack",rate,Map.of()),
                    0,1,McWuwa.catalog().requireByWeapon(owner.character().weaponPath()).characterId()+"/outro",PlatformRuntime.HitOrigin.AUTONOMOUS);
                if(zani&&hit)fiveWorld.zaniOutroConsumed(player,owner,target);
            });
    }
    private void grantChisaThread(ServerPlayer player,CharacterInstance actor) {
        currentTick=PlatformRuntime.time(player);
        for(Buff buff:List.copyOf(buffs))if(buff.effect.id().equals("chisa_outro")&&applies(buff,actor)) {
            buffs.removeIf(b->b.owner.equals(buff.owner)&&actor.instanceId().equals(b.recipient)&&b.effect.id().equals("chisa_void_thread"));
            double allBonus=teamRole(player,buff.owner).filter(owner->owner.chain()>=2).isPresent()?.50:0;
            buffs.add(new Buff(buff.owner,actor.instanceId(),new MechanicsEngine.Effect(MechanicsEngine.Kind.BUFF_SELF,"chisa_void_thread",.18,300,Map.of("allDamageBonus",allBonus)),currentTick+300));
        }
    }
    public boolean hasChisaThread(CharacterInstance actor,UUID chisa,long now) {
        return buffs.stream().anyMatch(b->b.expires>now&&b.owner.equals(chisa)&&actor.instanceId().equals(b.recipient)&&b.effect.id().equals("chisa_void_thread"));
    }
    public double chisaThreadDefenseIgnore(ServerPlayer player,CharacterInstance actor,LivingEntity target) {
        if(target==null)return 0;
        long now=PlatformRuntime.time(player);
        return buffs.stream().filter(b->b.expires>now&&actor.instanceId().equals(b.recipient)&&b.effect.id().equals("chisa_void_thread")
            &&EffectRuntime.hasOwnedEffect(target,player.getUUID(),b.owner,"chisa_void_scar")).mapToDouble(b->b.effect.amount()).max().orElse(0);
    }
    public void suisuiAnomalyActivity(ServerPlayer player,CharacterInstance actor,LivingEntity target,String effect,boolean consumed){
        if(effect.equals("glacio_frost"))effect="glacio_chafe";
        if(!Set.of("spectro_frazzle","fusion_burst","glacio_chafe","aero_erosion","electro_flare","electro_rage","havoc_bane").contains(effect))return;
        long now=PlatformRuntime.time(player);var team=ModAttachments.team(player).state();
        for(int slot=0;slot<3;slot++){
            var owner=team.at(slot).orElse(null);if(owner==null||!owner.alive()||owner.character()!=CharacterId.SUISUI)continue;
            boolean inDomain=fields.stream().anyMatch(f->f.owner.equals(owner.instanceId())&&f.effect.id().equals("suisui_landscape")&&insideField(f,player));
            if(inDomain){
                boolean appliedOrDamaged=!consumed&&Set.of("spectro_frazzle","fusion_burst","glacio_chafe","aero_erosion","electro_flare").contains(effect);
                if(appliedOrDamaged){
                    EffectRuntime.raiseCap(player,owner,target,effect,"suisui_domain_"+effect,3,300,EffectCapLedger.Lifetime.TIMED);
                    if(effect.equals("electro_flare"))EffectRuntime.raiseCap(player,owner,target,"electro_rage","suisui_domain_electro_rage",3,300,EffectCapLedger.Lifetime.TIMED);
                }
                if(consumed&&effect.equals("havoc_bane")){
                    buffs.removeIf(b->b.owner.equals(owner.instanceId())&&actor.instanceId().equals(b.recipient)&&b.effect.id().equals("suisui_havoc_bypass"));
                    buffs.add(new Buff(owner.instanceId(),actor.instanceId(),new MechanicsEngine.Effect(MechanicsEngine.Kind.BUFF_SELF,"suisui_havoc_bypass",.06,600,Map.of("resistanceIgnore",.12)),now+600));
                }
                if(owner.chain()>=2&&(appliedOrDamaged||consumed&&effect.equals("havoc_bane"))){
                    buffs.removeIf(b->b.owner.equals(owner.instanceId())&&actor.instanceId().equals(b.recipient)&&b.effect.id().equals("suisui_c2_crit"));
                    buffs.add(new Buff(owner.instanceId(),actor.instanceId(),new MechanicsEngine.Effect(MechanicsEngine.Kind.BUFF_SELF,"suisui_c2_crit",.50,600,Map.of()),now+600));
                }
            }
            if(consumed||owner.chain()>=1&&!effect.equals("electro_rage"))for(Buff smoke:List.copyOf(buffs))if(smoke.owner.equals(owner.instanceId())&&actor.instanceId().equals(smoke.recipient)&&smoke.effect.id().equals("suisui_smoke")&&smoke.expires>now){
                double attack=Math.min(.50,Math.max(0,combatStats(player,owner).energyRegen()-2)/.0012*.001);
                buffs.removeIf(b->b.owner.equals(owner.instanceId())&&actor.instanceId().equals(b.recipient)&&b.effect.id().equals("suisui_smoke_attack"));
                buffs.add(new Buff(owner.instanceId(),actor.instanceId(),new MechanicsEngine.Effect(MechanicsEngine.Kind.BUFF_SELF,"suisui_smoke_attack",attack,120,Map.of("endsOnSwitch",1d)),Math.min(smoke.expires,now+120)));
            }
        }
    }
    public void anomalyDamage(ServerPlayer player,CharacterInstance actor,LivingEntity target,String effect){suisuiAnomalyActivity(player,actor,target,effect,false);anomalyDamage(player,actor,target);}
    public void anomalyDamage(ServerPlayer player,CharacterInstance actor,LivingEntity target) {
        grantChisaThread(player,actor);soloTargets.damage(player,actor,target);
    }
    private MechanicModifierRules.Result mechanicsModifiers(ServerPlayer player,CharacterInstance role,String action,String kind,String element,LivingEntity target,boolean finalHit) {
        int fusionCount=0;var members=ModAttachments.team(player).state();
        for(int slot=0;slot<3;slot++)if(members.at(slot).filter(c->c.character().element()==Element.FUSION).isPresent())fusionCount++;
        final int count=fusionCount;
        double efficiency=NativeStats.of(role,PlatformRuntime.weaponFor(player,role)).bonus("offtune_efficiency")
            +sonatas.modifiers(role,currentTick).getOrDefault("offtune_efficiency",0d)+fieldOffTuneEfficiency(player);
        boolean boss=target!=null&&(EnemyEvents.profile(target).tier()==EnemyProfile.Tier.FOUR_COST||EnemyEvents.profile(target).tier()==EnemyProfile.Tier.STRONG);
        boolean anomaly=target!=null&&List.of("havoc_bane","aero_erosion","electro_flare","glacio_chafe","fusion_burst","spectro_frazzle").stream().anyMatch(id->EffectRuntime.hasEffect(target,id));
        var effects=buffs.stream().filter(b->modifierApplies(b,role,target)).map(b->{
            var effect=recipientEffect(b.effect,role);var values=new HashMap<>(effect.values());
            if(effect.id().equals("lupa_hunt")||effect.id().equals("lupa_glory")) {
                values.put("teamFusionCount",(double)count);values.put("otherFusionCount",(double)Math.max(0,count-1));values.put("targetBoss",boss?1d:0d);
            }
            if(effect.id().equals("denia_entropy_tune"))values.put("recipientOffTuneEfficiency",Math.max(0,efficiency));
            if(effect.id().equals("denia_tune_outro"))values.put("recipientAppliedTuneOffset",effect.values().getOrDefault("recipientAppliedTuneOffset",0d));
            return new MechanicsEngine.Effect(effect.kind(),effect.id(),effect.amount(),effect.durationTicks(),values);
        }).toList();
        return MechanicModifierRules.evaluate(effects,action,kind,element,anomaly,finalHit);
    }
    private MechanicModifierRules.Result mechanicsModifiers(CharacterInstance role, String action, String kind, String element, LivingEntity target, boolean finalHit) {
        boolean anomaly = target != null && List.of("havoc_bane", "aero_erosion", "electro_flare", "glacio_chafe", "fusion_burst", "spectro_frazzle").stream().anyMatch(id -> EffectRuntime.hasEffect(target, id));
        return MechanicModifierRules.evaluate(buffs.stream().filter(b -> modifierApplies(b,role,target))
            .map(b->recipientEffect(b.effect,role)).toList(), action, kind, element, anomaly, finalHit);
    }
    private MechanicsEngine.Effect recipientEffect(MechanicsEngine.Effect effect,CharacterInstance role){
        if(!effect.id().equals("suoming_outro"))return effect;
        // Node8 qualifies the beneficiary, not Suoming's layers at departure.
        boolean responds=role.character()==CharacterId.SUOMING||role.character()==CharacterId.HSIN
            &&MechanicsState.canonicalMode("hsin",role.selectedMode()).equals("unison");
        var values=new HashMap<>(effect.values());
        values.put("recipientHasUnison",responds&&unison.stacks(currentTick,2,role.instanceId())>0?1d:0d);
        return new MechanicsEngine.Effect(effect.kind(),effect.id(),effect.amount(),effect.durationTicks(),values);
    }
    private boolean modifierApplies(Buff buff,CharacterInstance role,LivingEntity target){
        return applies(buff,role)
            &&(!buff.effect.id().equals("hsin_outro_unison")||unison.hasLamp(role.instanceId()))
            &&(!buff.effect.id().equals("hiyuki_outro")||target!=null&&(EffectRuntime.hasEffect(target,"glacio_chafe")||EffectRuntime.hasEffect(target,"glacio_frost")));
    }
    public double coefficientIncrease(CharacterInstance role, String action, String kind, String element) {
        return mechanicsModifiers(role, action, kind, element, null, false).coefficientIncrease()
            +(role.character().sourceId()==1507?cn.zgnhit.mcwuwa.content.mechanics.ZaniDamageRules.coefficient(ruleState(role),action,kind):0)
            +(role.character().sourceId()==1306?MechanicModifierRules.augustaCoefficient(role.chain(),action):0)
            +(role.character().sourceId()==1312?cn.zgnhit.mcwuwa.content.mechanics.SuomingDamageRules.coefficient(role.chain(),action):0);
    }
    public double damageBonus(ServerPlayer player, CharacterInstance role, LivingEntity target, String action, String kind, String element) {
        return mechanicsModifiers(player,role, action, kind, element, target, false).damageBonus();
    }
    public double amplification(ServerPlayer player, CharacterInstance role, LivingEntity target, String action, String kind, String element) {
        return mechanicsModifiers(player,role, action, kind, element, target, false).amplification()+(role.character().sourceId()==1507?cn.zgnhit.mcwuwa.content.mechanics.ZaniDamageRules.thorns(ruleState(role),action,element):0);
    }
    public double damageBonus(CharacterInstance role, String kind, String element) {
        return mechanicsModifiers(role, role.actionId(), kind, element, null, false).damageBonus();
    }
    public double amplification(CharacterInstance role, String kind) {
        return mechanicsModifiers(role, role.actionId(), kind, "all", null, false).amplification();
    }
    /** Only Ciaccona has a source-defined ordinary-pistol aiming branch. */
    public boolean aim(ServerPlayer player, boolean pressed) {
        var role=ModAttachments.team(player).state().active().orElse(null);
        if(role!=null&&role.character()==CharacterId.CIACCONA)return request(player,pressed?"AIM_PRESS":"AIM_RELEASE");
        if(pressed)player.displayClientMessage(Component.literal("当前角色没有瞄准分支"), true);return false;
    }
    public void cancel(ServerPlayer player) {
        // A visual-only transformation can own a shot without a Running action.
        // Explicit cancellation must release its clocks and tracked pose as well.
        if(running==null)Network.cancelCinematicVisual(player);
        CinematicTimeRuntime.clear(player);
        deferredSawRelease=null;
        if(hsinOpeningPoseOwner!=null){
            Network.visual(player,new Network.Visual(player.getId(),hsinOpeningPoseOwner,"",PlatformRuntime.time(player),0,++sequence,""));
            hsinOpeningPoseOwner=null;
        }
        if(qiuyuanRunPoseOwner!=null){
            Network.visual(player,new Network.Visual(player.getId(),qiuyuanRunPoseOwner,"",PlatformRuntime.time(player),0,++sequence,""));
            qiuyuanRunPoseOwner=null;
        }
        clearCiacconaPose(player);
        specialMovement.clear(player);
        fiveWorld.cancelMovement(player);
        cancelCharge(player);
        queue.clear(); removeSecondary(action -> !action.independent);
        ModAttachments.team(player).state().active().ifPresent(role -> {
            pendingMarks.remove(role.instanceId());var formState=mechanics.get(role.instanceId());sendForm(player,role,formState==null?"":formState.form(),0);
            if(running==null&&role.character()==CharacterId.PHROLOVA&&formState!=null&&formState.form().equals("conducting"))
                Network.visual(player,new Network.Visual(player.getId(),role.instanceId(),"",PlatformRuntime.time(player),0,++sequence,""));
        });
        if (running != null) {
            finishBoundAction(running.weapon,running.action.id());
            SkillModelProfiles.end(running.visuals);
            Network.visual(player, new Network.Visual(player.getId(), running.weapon, "", PlatformRuntime.time(player), 0, ++sequence, "")); running = null;
        }
        ModAttachments.team(player).state().active().ifPresent(role -> {
            role.cancelAction();
            EffectRuntime.clearFocusSlow(role.instanceId());
            var state = mechanics.get(role.instanceId());
            if (state != null) {
                var cleaned = InterruptionRules.cancelled(state);
                commit(role, cleaned); RolePassiveRuntime.updateMechanics(player, role, cleaned);
            }
        });
    }
    public void clearMovement(ServerPlayer player){specialMovement.clear(player);fiveWorld.cancelMovement(player);}
    private void finishBoundAction(UUID owner,String action) {
        if(action.equals("lynae/skill_additive"))buffs.removeIf(b->b.owner.equals(owner)&&b.effect.id().equals("lynae_premix_bonus"));
        if(action.equals("mornye/liberation"))buffs.removeIf(b->b.owner.equals(owner)&&b.effect.id().equals("critical_protocol_crit"));
        if(HsinTargetRules.stallsInIlluminatedForm(action))EffectRuntime.clearStasisGroup(owner,"hsin_illuminated_stasis");
        if(action.equals("phrolova/composition_finale")){
            EffectRuntime.clearStasisGroup(owner,"phrolova_composition_stasis");
            buffs.removeIf(b->b.owner.equals(owner)&&b.effect.id().equals("phrolova_echoes_additive_multiplier"));
        }
        if(action.startsWith("changli/heavy_flaming_sacrifice"))buffs.removeIf(b->b.owner.equals(owner)&&b.effect.id().equals("changli_sacrifice_action"));
        if(action.equals("chisa/saw_finale"))buffs.removeIf(b->b.owner.equals(owner)
            &&Set.of("chisa_myriad_finale_multiplier","chisa_finale_additive_multiplier").contains(b.effect.id()));
        if(action.equals("chisa/skill_saw_hold"))removeFields(f->f.owner.equals(owner)&&f.effect.id().equals("chisa_held_pull"));
        if(action.equals("hiyuki/heavy"))buffs.removeIf(b->b.owner.equals(owner)&&b.effect.id().equals("hiyuki_heavy_invulnerable"));
        if(action.equals("hiyuki/liberation_return"))buffs.removeIf(b->b.owner.equals(owner)&&b.effect.id().equals("hiyuki_return_forge_multiplier"));
        if(action.equals("jinhsi/epiphany_rays"))buffs.removeIf(b->b.owner.equals(owner)
            &&Set.of("jinhsi_epiphany_additive_multiplier","jinhsi_epiphany_damage_bonus").contains(b.effect.id()));
        if(MechanicModifierRules.aemeathImmediateAttack(action))buffs.removeIf(b->b.owner.equals(owner)
            &&Set.of("heavy_damage_amplification","aemeath_immediate_critical_damage").contains(b.effect.id()));
        if(!action.startsWith("yangyang_xuanling/"))return;
        if(action.startsWith("yangyang_xuanling/flow_"))EffectRuntime.clearOwnedStasis(owner);
        if(action.equals("yangyang_xuanling/heavy"))buffs.removeIf(b->b.owner.equals(owner)&&b.effect.id().equals("yangyang_azure_critical_damage"));
        if(action.equals("yangyang_xuanling/listening_basic_03")||action.equals("yangyang_xuanling/listening_counter_03"))
            buffs.removeIf(b->b.owner.equals(owner)&&b.effect.id().equals("yangyang_feather_critical_damage"));
    }
    private void removeSecondary(java.util.function.Predicate<Running> predicate){
        secondary.removeIf(action->{if(!predicate.test(action))return false;SkillModelProfiles.end(action.visuals);return true;});
    }
    public void cancelHeldSkill(ServerPlayer player){
        ModAttachments.team(player).state().active().ifPresent(role->{
            var state=mechanics.get(role.instanceId());
            if(state!=null&&state.characterId().equals("sigrika")&&state.timer("sig_hold_consumed")>0){
                var timers=new HashMap<>(state.timers());timers.remove("sig_hold_consumed");
                var next=new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),state.nextBasic(),state.resources(),timers);
                commit(role,next);RolePassiveRuntime.updateMechanics(player,role,next);
            }
        });
        var role=ModAttachments.team(player).state().active().orElse(null);
        if(role!=null&&role.character()==CharacterId.QIUYUAN)finishQiuyuanRun(player,role,false);
        if(role!=null){
            var state=mechanics.get(role.instanceId());
            if(state!=null&&(state.characterId().equals("lucilla")&&state.timer("lucilla_focus_channel")==2
                ||state.characterId().equals("zani")&&state.timer("zani_guard_mode")>0))cancel(player);
        }
        if(running!=null&&running.action.id().equals("chisa/skill_saw_hold"))cancel(player);
    }
    private void leaveExploration(ServerPlayer player) {
        sceneInteraction(player);
        var role=ModAttachments.team(player).state().active().orElse(null);if(role==null)return;
        var state=mechanics.get(role.instanceId());if(state==null)return;
        var result=MECHANICS.leaveExploration(state);if(result.effects().isEmpty()&&result.state().equals(state))return;
        cancel(player);commit(role,result.state());RolePassiveRuntime.updateMechanics(player,role,result.state());applyEffects(player,role,result.effects());Network.sync(player);
    }
    public CombatControl.Protection interruptionProtection(ServerPlayer player) {
        var team = ModAttachments.team(player).state(); var role = team.active().orElse(null);
        if (role == null) return CombatControl.Protection.NONE;
        var state = mechanics.getOrDefault(role.instanceId(), MechanicsState.initial(McWuwa.catalog().requireByWeapon(role.character().weaponPath()).characterId(), role.selectedMode(), role.chain()));
        var protection = InterruptionRules.protection(state, role.actionId()); long now = PlatformRuntime.time(player);
        if(running!=null&&running.weapon.equals(role.instanceId())&&running.acceptedQualifications.getOrDefault("immuneInterruption",0d)>0)
            return CombatControl.Protection.UNINTERRUPTIBLE;
        if (buffs.stream().anyMatch(buff -> applies(buff, role) && buff.expires > now && buff.effect.amount() > 0 && (buff.effect.id().endsWith("_uninterruptible")||buff.effect.values().getOrDefault("immuneInterruption",0d)>0)))
            return CombatControl.Protection.UNINTERRUPTIBLE;
        // Strong fields inherit the normal field's protection even when their numeric map only contains the upgraded values.
        if (fields.stream().anyMatch(field -> insideField(field, player) && (isMornyeField(field.effect.id()) || field.effect.values().getOrDefault("interruptionResistance", 0d) > 0))
            || buffs.stream().anyMatch(buff->applies(buff,role)&&buff.expires>now&&(buff.effect.id().equals("suisui_chongming")||buff.effect.values().getOrDefault("interruptionResistance",0d)>0))
            || RolePassiveRuntime.hasEchoInterruptionProtection(player, role)) protection = CombatControl.stronger(protection, CombatControl.Protection.INCREASED);
        for (int slot = 0; slot < 3; slot++) {
            var owner = team.at(slot).orElse(null);
            if (owner != null && owner.alive() && owner.character() == CharacterId.CARTETHYIA && owner != role)
                protection = CombatControl.stronger(protection, CombatControl.Protection.INCREASED);
        }
        return protection;
    }
    /** Ciaccona explicitly ignores receiving-hit reactions while retaining 50% damage.
     * This is not full damage immunity and must not cancel the incoming damage event. */
    public boolean preventsRecitalKnockback(ServerPlayer player) {
        var role=ModAttachments.team(player).state().active().orElse(null);
        return role!=null&&role.alive()&&role.character()==CharacterId.CIACCONA
            &&ruleState(role).timer("ciaccona_performance")>0;
    }
    /** Explicit source immunity to receiving a hit, distinct from100% reduction
     * or action armor. Cancel before vanilla hurt animation/impulse is applied. */
    public boolean preventsNativeHurt(ServerPlayer player) {
        currentTick=PlatformRuntime.time(player);
        var role=ModAttachments.team(player).state().active().orElse(null);
        return role!=null&&role.alive()&&(qualifiedPlungeImmunity(role)||buffs.stream().anyMatch(buff->applies(buff,role)&&buff.expires>currentTick&&buff.effect.amount()>0
            &&buff.effect.values().getOrDefault("preventsNativeHurt",0d)>0));
    }
    private boolean qualifiedPlungeImmunity(CharacterInstance role) {
        return running!=null&&running.weapon.equals(role.instanceId())&&running.action.id().equals("carlotta/air")
            &&!running.resourceHit&&running.acceptedQualifications.getOrDefault("immuneUntilHit",0d)>0;
    }
    /** Received hit cancels only unfinished body work, never paid resources, cooldowns or independently existing fields. */
    public boolean interrupt(ServerPlayer player, CombatControl.Impact impact) {
        var role = ModAttachments.team(player).state().active().orElse(null);
        if (role == null || !role.alive() || !CombatControl.interrupts(impact, interruptionProtection(player), true, false)) return false;
        if(lupaEscape(player,role))return false;
        var state = mechanics.get(role.instanceId());
        boolean observationHit=state!=null&&state.characterId().equals("mornye")&&state.form().equals("observation");
        if (running == null && role.actionId().isEmpty() && (state == null || !InterruptionRules.hasBodyInput(state)) && !EchoRuntime.isTransforming(player)&&!observationHit) return false;
        if(role.character()==CharacterId.CIACCONA&&state!=null) {
            var after=preserveCiaccona(player,role,state,false);state=after.state();applyEffects(player,role,after.effects());
        }
        boolean zaniNightResume=state!=null&&state.characterId().equals("zani")&&state.form().equals("blazing")
            &&running!=null&&running.action.id().equals("zani/heavy_night")
            &&PlatformRuntime.time(player)-running.start<running.action.hitTimesTicks().getLast();
        String interruptedAction=running==null?"":running.action.id();
        long interruptedElapsed=running==null?0:PlatformRuntime.time(player)-running.start;
        int interruptedFirstHit=running==null||running.action.hitTimesTicks().isEmpty()?0:running.action.hitTimesTicks().getFirst();
        cancel(player); EchoRuntime.interruptTransform(player);
        if (state != null) {
            var next = InterruptionRules.interrupted(state,interruptedAction,interruptedElapsed,interruptedFirstHit);
            if(zaniNightResume){var timers=new HashMap<>(next.timers());timers.put("zani_night_resume",60);
                next=new MechanicsState(next.characterId(),next.form(),next.mode(),next.chain(),next.nextBasic(),next.resources(),timers);}
            commit(role, next); RolePassiveRuntime.updateMechanics(player, role, next);
        }
        buffs.removeIf(buff -> buff.owner.equals(role.instanceId()) && buff.effect.id().startsWith("conversion_"));
        return true;
    }
    private boolean lupaEscape(ServerPlayer player,CharacterInstance role) {
        long now=PlatformRuntime.time(player);
        for(int i=0;i<buffs.size();i++) {
            var grant=buffs.get(i);
            if(!grant.effect.id().equals("lupa_escape_hitstun")||grant.expires<=now||grant.effect.amount()<1||!applies(grant,role))continue;
            double remaining=grant.effect.amount()-1;
            if(remaining==0)buffs.remove(i);
            else buffs.set(i,new Buff(grant.owner,grant.recipient,new MechanicsEngine.Effect(grant.effect.kind(),grant.effect.id(),remaining,grant.effect.durationTicks(),grant.effect.values()),grant.expires));
            player.setDeltaMovement(Vec3.ZERO);player.hurtTime=0;player.hurtMarked=true;
            // Vanilla can finish applying this hit's impulse after the Post hook.
            // Clear that impulse once on the next tick, only for the same body.
            lupaEscapeOwner=role.instanceId();lupaEscapeAt=now;
            if(TraversalMotion.solidSupport(player)&&ModAttachments.team(player).state().stamina().successfulSkillEvade()==StaminaDodge.AttackResult.DODGE_SUCCEEDED)
                confirmedSkillEvade(player);
            Network.sync(player);return true;
        }
        return false;
    }
    public void downed(ServerPlayer player,CharacterInstance role) {
        UUID id=role.instanceId();var old=stateFor(role,McWuwa.catalog().requireByWeapon(role.character().weaponPath()).characterId());
        // Keep pending-window/counter evidence until the lifecycle reducer
        // has closed it. Only the reducer filters temporary qualifications.
        var timers=new HashMap<>(old.timers());for(String key:MechanicsEngine.persistentCooldownKeys(old.characterId())){
            int remaining=(int)Math.min(Integer.MAX_VALUE,role.cooldown("mechanics_"+key));if(remaining>0)timers.put(key,remaining);else timers.remove(key);
        }
        var next=CharacterLifecycleRules.downed(new MechanicsState(old.characterId(),old.form(),old.mode(),old.chain(),old.nextBasic(),old.resources(),timers));
        commit(role,next);RolePassiveRuntime.updateMechanics(player,role,next);
        fiveWorld.remove(id);tideWorld.remove(id);hsinEdictTargets.remove(id);unison.remove(id);unisonGains.remove(id);soloTargets.downed(role);swordShadows.remove(id);sonatas.downed(id);threeWorld.remove(id);
        removeSecondary(run->run.weapon.equals(id));pendingMarks.remove(id);shields.removeRecipient(id);
        // Defeat consumes both this role's grants and temporary qualifications
        // assigned to it by another role. Team-wide grants from living allies
        // have no recipient and remain available to the surviving team.
        buffs.removeIf(buff->buff.owner.equals(id)||id.equals(buff.recipient));removeFields(field->field.owner.equals(id));
        // Passive cooldowns already live on CharacterInstance and are restored
        // by passiveEvent; discarding temporary stacks must not refund them.
        weaponPassives.remove(id);
        sixWorld.downed(id);
        targetReactions.removeOwner(id);
        EchoRuntime.onDowned(player,id);
        CombatProjectileEntity.removeOwned(player,id);
        ConstructEntity.removeOwned(player.getUUID(),id);
        weaponBuffRecipients.entrySet().removeIf(entry->id.equals(entry.getValue())||entry.getKey().startsWith(id+":"));
        EffectRuntime.clearOwnedStasis(id);
    }
    public void clear(ServerPlayer player) { lupaEscapeOwner=null;afterDamage.clear();CombatProjectileEntity.removeOwned(player);cancel(player);targetReactions.clear();soloTargets.clear();hsinEdictTargets.clear();unison.clear();unisonGains.clear();swordShadows.clear();responseCooldown.clear();threeWorld.clear();tideWorld.clear();sixWorld.clear();fiveWorld.clear();suisuiDancers.values().forEach(ConstructEntity::discard);suisuiDancers.clear();phoebeStasisTargets.clear();phoebeRefractionReady.clear();removeSecondary(action->true); mechanics.clear(); buffs.clear(); weaponPassives.clear(); weaponBuffRecipients.clear(); pendingMarks.clear(); removeFields(f -> true); shields.clear(); punishedTargets.clear(); chisaScarReady.clear(); sonatas.clear(); }
    /** World-bound fields cannot follow a player through a portal or revive from stale source timers. */
    public void dimensionChanged(ServerPlayer player) {
        lupaEscapeOwner=null;
        CombatProjectileEntity.removeOwned(player);
        cancel(player);targetReactions.clear();soloTargets.clear();hsinEdictTargets.clear();unison.clear();unisonGains.clear();swordShadows.clear();threeWorld.clear();tideWorld.clear();sixWorld.clear();fiveWorld.clear();suisuiDancers.values().forEach(ConstructEntity::discard);suisuiDancers.clear();phoebeStasisTargets.clear();phoebeRefractionReady.clear();removeSecondary(action->true);removeFields(f -> true);
        for (var entry : List.copyOf(mechanics.entrySet())) {
            var exit=MECHANICS.leaveWorld(entry.getValue());var old=exit.state(); Map<String, Integer> timers = new HashMap<>(old.timers());
            timers.remove("field"); timers.remove("strong_field"); timers.remove("field_heal");
            timers.remove("unison");UnisonRules.COUNTERS.forEach(timers::remove);
            var next = new MechanicsState(old.characterId(), old.form(), old.mode(), old.chain(), old.nextBasic(), old.resources(), timers);
            mechanics.put(entry.getKey(), next); teamRole(player, entry.getKey()).ifPresent(role -> {commit(role,next);RolePassiveRuntime.updateMechanics(player, role, next);applyEffects(player,role,exit.effects());});
        }
    }
    private static String dimension(ServerPlayer player) { return player.level().dimension().location().toString(); }
    private boolean hecateField(Field field,ServerPlayer player,CharacterInstance role) {
        return field.owner.equals(role.instanceId())&&field.effect.id().equals("hecate")&&field.visual instanceof HecateEntity entity&&!entity.isRemoved()&&fieldActive(field,player);
    }
    private boolean fieldActive(Field field, ServerPlayer player) {
        return field.area.active(dimension(player), PlatformRuntime.time(player), teamRole(player, field.owner).filter(CharacterInstance::alive).isPresent());
    }
    private boolean insideField(Field field, ServerPlayer player) {
        return field.area.contains(dimension(player), player.getX(), player.getY(), player.getZ(), PlatformRuntime.time(player), teamRole(player, field.owner).isPresent());
    }
    private boolean pendingFinalFieldHeal(Field field,ServerPlayer player){
        var state=mechanics.get(field.owner);
        if(field.area.expiresAt()!=PlatformRuntime.time(player)||teamRole(player,field.owner).filter(CharacterInstance::alive).isEmpty()||state==null)return false;
        return field.effect.id().equals("shorekeeper_domain")&&state.timer("shore_domain")==1&&state.timer("shore_heal")==1
            ||isMornyeField(field.effect.id())&&state.timer("field")==1&&state.timer("field_heal")==1;
    }
    private boolean insidePeriodicHeal(Field field,ServerPlayer player,MechanicsEngine.Effect heal){
        long now=PlatformRuntime.time(player);
        return insideField(field,player)||heal.values().getOrDefault("completeFieldInterval",0d)>0&&now>0&&field.area.expiresAt()==now
            &&field.area.contains(dimension(player),player.getX(),player.getY(),player.getZ(),now-1,teamRole(player,field.owner).isPresent());
    }
    private static boolean isMornyeField(String id) { return id.equals("mornye_resonance") || id.equals("mornye_strong_resonance"); }
    private void clearCiacconaPose(ServerPlayer player) {
        if(recitalPoseOwner!=null)Network.visual(player,new Network.Visual(player.getId(),recitalPoseOwner,"",PlatformRuntime.time(player),0,++sequence,""));
        recitalPoseOwner=null;ciacconaPoseAction="";ciacconaPoseUntil=0;
    }
    private static String ciacconaInstrument(MechanicsEngine.Effect effect,boolean shadow) {
        String kind=switch(effect.values().getOrDefault("instrument",1d).intValue()){case 2->"drum";case 3->"guitar";default->shadow?"singer":"microphone";};
        return "ciaccona_"+kind+(shadow?"_ensemble":"");
    }
    private static void ciacconaSoloHit(ServerPlayer player,CharacterInstance role,Vec3 center) {
        var bounds=new net.minecraft.world.phys.AABB(center,center).inflate(12);
        for(LivingEntity target:player.level().getEntitiesOfClass(LivingEntity.class,bounds,e->PlatformRuntime.canAttack(player,e)&&e.position().distanceToSqr(center)<=144&&player.level().clip(new net.minecraft.world.level.ClipContext(center.add(0,1,0),e.getBoundingBox().getCenter(),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,player)).getType()==net.minecraft.world.phys.HitResult.Type.MISS))
            if(PlatformRuntime.nativeHit(player,role,target,RoleProcDefinitions.solo("ciaccona_solo","liberation","aero","attack",2.2,Map.of()),0,1,"ciaccona/solo",PlatformRuntime.HitOrigin.AUTONOMOUS))
                autonomousContact(player,role,target,"ciaccona__solo_contact");
    }
    private void removeFields(java.util.function.Predicate<Field> predicate) {
        fields.removeIf(field -> { if (!predicate.test(field)) return false; if (field.visual != null) field.visual.discard();
            if(field.effect.id().equals("shorekeeper_domain"))threeWorld.removeDomain(field.owner);
            if(field.effect.id().equals("phrolova_conducting_stasis"))EffectRuntime.clearStasisGroup(field.owner,"phrolova_conducting");
            return true; });
    }
    /** Screen/focus loss cancels unfinished held bodies as well as the latch. A normal
     * release still uses the authored fourth/fifth finisher through request(). */
    public void cancelHeldInput(ServerPlayer player) {
        var role=ModAttachments.team(player).state().active().orElse(null);
        var state=role==null?null:mechanics.get(role.instanceId());
        boolean channel=state!=null&&(state.timer("shore_unbound")>0||state.timer("gal_raining")>0||HsinTravelRules.wandering(state));
        if(heldAttackOwner!=null||channel){cancel(player);if(channel)Network.sync(player);}else cancelCharge(player);
    }
    public void cancelCharge(ServerPlayer player) {
        chargeWaitingForAttack=false;
        heldAttackOwner=null;heldAttackMode=null;
        boolean wasCharging=charge.active();
        charge.clear();
        var role = ModAttachments.team(player).state().active();
        if(wasCharging&&role.isPresent()&&running==null)Network.visual(player,new Network.Visual(player.getId(),role.get().instanceId(),"",PlatformRuntime.time(player),0,++sequence,""));
        if (role.isEmpty()) return; var state = mechanics.get(role.get().instanceId()); if (state == null) return;
        Map<String, Integer> timers = new HashMap<>(state.timers()); timers.remove("charging");timers.remove("rover_strangle_charging"); timers.remove("converting"); timers.remove("conversion_pulse");
        buffs.removeIf(b -> b.owner.equals(role.get().instanceId()) && b.effect.id().startsWith("conversion_"));
        var next = new MechanicsState(state.characterId(), state.form(), state.mode(), state.chain(), state.nextBasic(), state.resources(), timers);
        commit(role.get(), next); RolePassiveRuntime.updateMechanics(player, role.get(), next);
    }
    private MechanicsState stateFor(CharacterInstance role, String slug) {
        return mechanics.computeIfAbsent(role.instanceId(), id -> initialState(role, slug));
    }
    MechanicsState ruleState(CharacterInstance role){return stateFor(role,McWuwa.catalog().requireByWeapon(role.character().weaponPath()).characterId());}
    int targetTraceLayers(ServerPlayer player,CharacterInstance owner,LivingEntity target){return soloTargets.traceLayers(player,owner,target);}
    int sourceWingBurst(ServerPlayer player,CharacterInstance owner,List<LivingEntity> targets,boolean star){return soloTargets.wingBurst(player,owner,targets,star);}
    int sourceWingBurst(ServerPlayer player,CharacterInstance owner,List<LivingEntity> targets,boolean star,boolean preserve){return soloTargets.wingBurst(player,owner,targets,star,preserve);}
    void ruleState(ServerPlayer player,CharacterInstance role,MechanicsState state){commit(role,state);RolePassiveRuntime.updateMechanics(player,role,state);}
    void ruleMark(ServerPlayer player,CharacterInstance role,LivingEntity target,String id,int layers,int duration){applyMark(player,role,target,new MechanicsEngine.Effect(MechanicsEngine.Kind.MARK_TARGET,id,layers,duration,Map.of()));}
    public double soloAnomalyFactor(ServerPlayer player,CharacterInstance role,LivingEntity target,Element element,String cause){
        return soloAnomalyFactor(player,role,target,element,cause,0);
    }
    public double soloAnomalyFactor(ServerPlayer player,CharacterInstance role,LivingEntity target,Element element,String cause,double effectBonus){
        double factor=soloTargets.anomalyFactor(player,role,target,element,cause);
        double amplification=effectBonus+soloTargets.anomalyAmplification(player,target,cause);
        if(element==Element.FUSION&&(cause.equals("effect/fusion_burst")||cause.startsWith("effect/fusion_burst/"))
                &&player.level()==target.level()&&player.distanceToSqr(target)<=144
                &&ModAttachments.team(player).state().active().filter(CharacterInstance::alive).isPresent()){
            // Source: Denia's fusion outro deepens nearby Fusion Burst only.
            // The effect damage sink bypasses direct-skill modifier evaluation,
            // so apply its shared amplification zone here, once per actual victim.
            amplification+=buffs.stream().filter(b->b.effect.id().equals("denia_fusion_outro")&&applies(b,role))
                .mapToDouble(b->b.effect.amount()).max().orElse(0);
        }
        if(element==Element.AERO&&CartethyiaAuraRules.erosionDamage(cause)) {
            double strongest=player.distanceToSqr(target)<=144?buffs.stream().filter(b->b.expires>PlatformRuntime.time(player)&&b.effect.id().equals("ciaccona_outro_erosion")
                &&teamRole(player,b.owner).filter(CharacterInstance::alive).isPresent()).mapToDouble(b->b.effect.amount()).max().orElse(0):0;
            // Both descriptions deepen erosion damage; they share this amplification zone.
            amplification+=strongest+(EffectRuntime.hasCartethyiaDivineAura(player,target)?.50:0);
        }
        if(element==Element.SPECTRO&&cause.startsWith("effect/")&&player.distanceToSqr(target)<=144){
            double weaponAura=0;
            for(var entry:weaponPassives.entrySet())if(teamRole(player,entry.getKey()).filter(CharacterInstance::alive).isPresent())
                for(var buff:WEAPON_PASSIVES.active(entry.getValue(),PlatformRuntime.time(player)))if(buff.id().equals("luminous_hymn/frazzle_aura"))weaponAura=Math.max(weaponAura,buff.value());
            amplification+=weaponAura+phoebePrayerAmplification(player,target);
        }
        return factor*Math.max(0,1+amplification);
    }
    /** Called only after the server has confirmed actual creative game mode. */
    public void rechargeForCreative(ServerPlayer player) {
        var team=ModAttachments.team(player).state();
        for(int slot=0;slot<3;slot++)team.at(slot).ifPresent(role->{
            role.gain("energy",role.resourceMax("energy")-role.resource("energy"));
            role.gain("concerto",role.resourceMax("concerto")-role.resource("concerto"));
            String slug=McWuwa.catalog().requireByWeapon(role.character().weaponPath()).characterId();
            var recharged=MECHANICS.rechargeForCreative(stateFor(role,slug));
            recharged=fiveWorld.rechargeForCreative(player,role,recharged);commit(role,recharged);
            RolePassiveRuntime.updateMechanics(player,role,recharged);
        });
        team.stamina().restoreStamina(cn.zgnhit.mcwuwa.core.StaminaDodge.MAX_STAMINA);
    }
    private MechanicsState initialState(CharacterInstance role, String slug) {
        var initial = MechanicsState.initial(slug, role.selectedMode(), role.chain());
        Map<String, Integer> timers = new HashMap<>();
        for (String key : MechanicsEngine.persistentCooldownKeys(slug)) if (role.cooldown("mechanics_" + key) > 0)
            timers.put(key, (int)Math.min(Integer.MAX_VALUE, role.cooldown("mechanics_" + key)));
        for(String key:MechanicsEngine.persistentCounterKeys(slug))if(role.persistentCounter(key)>0)timers.put(key,role.persistentCounter(key));
        return new MechanicsState(slug, initial.form(), initial.mode(), role.chain(), 1, initial.resources(), timers);
    }
    private void commit(CharacterInstance role, MechanicsState state) {
        var previous=mechanics.get(role.instanceId());
        if(state.timer("unison")>0&&(previous==null||previous.timer("unison")==0))unisonGains.add(role.instanceId());
        mechanics.put(role.instanceId(), state);
        if(state.characterId().equals("suoming")&&state.timer("suoming_forbidden")==0)
            buffs.removeIf(b->b.owner.equals(role.instanceId())&&Set.of("suoming_contract_multiplier","suoming_contract_crit_damage").contains(b.effect.id()));
        if(state.characterId().equals("phoebe")&&state.form().equals("absolution")&&(previous==null||!previous.form().equals("absolution"))){
            buffs.removeIf(b->b.owner.equals(role.instanceId())&&b.effect.id().equals("phoebe_silent_prayer"));
            EffectRuntime.clearOwnedStatGroup(role.instanceId(),"phoebe/silent_prayer");
        }
        // Expected Error protects only its block state, which has no damage
        // Running timeline. Cancellation, a counter, basic follow-up, switching
        // and natural expiry all leave through this same state commit.
        if(state.characterId().equals("mornye")&&state.timer("block")==0)
            buffs.removeIf(b->b.owner.equals(role.instanceId())&&b.effect.id().equals("mornye_block_damage_reduction"));
        for (String key : MechanicsEngine.persistentCooldownKeys(state.characterId())) role.setCooldown("mechanics_" + key, state.timer(key));
        for(String key:MechanicsEngine.persistentCounterKeys(state.characterId()))role.setPersistentCounter(key,state.timer(key));
        String token=role.character().sourceId()==1312?"suoming_unison":role.character()==CharacterId.HSIN?"hsin_unison":"jinhsi_unison";
        if (state.timer("unison") > 0&&(role.character()!=CharacterId.HSIN||state.mode().equals("unison")))role.putTemporaryState(token);else role.removeTemporaryState(token);
    }
    private Optional<CharacterInstance> teamRole(ServerPlayer player, UUID instance) {
        var team = ModAttachments.team(player).state();
        for (int i = 0; i < 3; i++) if (team.at(i).filter(r -> r.instanceId().equals(instance) && r.alive()).isPresent()) return team.at(i);
        return Optional.empty();
    }
    private void sendForm(ServerPlayer player, CharacterInstance role, String form, int duration) {
        if (ModAttachments.team(player).state().active().orElse(null) != role) return;
        String model = cn.zgnhit.mcwuwa.content.mechanics.FormPresentationRules.model(ruleState(role).characterId(), form);
        if(role.character()==CharacterId.SHOREKEEPER&&ruleState(role).timer("shore_unbound")>0)model="shorekeeper_butterfly_form";
        Network.visual(player, new Network.Visual(player.getId(), role.instanceId(), "@form", duration > 0 ? PlatformRuntime.time(player) + duration : Long.MAX_VALUE, 0, ++sequence, model));
    }
    public void addView(ServerPlayer player,CharacterInstance role,com.google.gson.JsonObject view) {
        addView(role,view);
        view.addProperty("unisonStackCap",unisonStackCap(player));
        view.addProperty("unisonStacks",unisonStacks(player,role));
    }
    public void addView(CharacterInstance role, com.google.gson.JsonObject view) {
        String slug = McWuwa.catalog().requireByWeapon(role.character().weaponPath()).characterId();
        var state = mechanics.get(role.instanceId()); if (state == null) state = initialState(role, slug);
        view.addProperty("form", state.form());
        view.addProperty("action",role.actionId());
        view.addProperty("unisonStackCap",UnisonRules.stackCap(state));
        view.addProperty("unisonStacks",UnisonRules.canRespond(state)?unison.stacks(currentTick,UnisonRules.stackCap(state)):0);
        view.addProperty("unisonHeld",state.timer("unison")>0);
        view.addProperty("liberationHoldMode",LiberationInputRules.mode(state).name());
        view.addProperty("liberationCharging",HiyukiReturnChargeRules.isCharging(state));view.addProperty("liberationChargeSpent",state.timer("return_charge_spent"));
        view.addProperty("heldCombo",role.instanceId().equals(heldAttackOwner));
        view.addProperty("camouflageTicks",state.timer(LynaeCamouflageRules.TIMER));
        if(slug.equals("ciaccona")) {
            view.addProperty("performanceTicks",state.timer("ciaccona_performance"));view.addProperty("rhythmTicks",state.timer("ciaccona_next_beat"));
            view.addProperty("rhythmReady",state.timer("ciaccona_judgement")>0);view.addProperty("rhythmColor",state.timer("ciaccona_last_color"));
        }
        var resources = new com.google.gson.JsonObject(); state.resources().forEach(resources::addProperty); view.add("forte", resources);
        if(slug.equals("phrolova")){
            for(String key:List.of("final_note","phrolova_color_conversion","note_position","conducting"))resources.addProperty(key,state.timer(key));
        }
        if(slug.equals("zhezhi")){
            resources.addProperty("zhezhi_insight",state.timer("zhezhi_insight"));
            resources.addProperty("zhezhi_images",state.timer("zhezhi_images"));
        }
        if(slug.equals("sigrika")){
            for(int i=0;i<4;i++)resources.addProperty("rune_"+i,state.timer("sig_rune_"+i));
            for(String key:List.of("echo","focus","blessings"))resources.addProperty(key,state.timer("sig_"+key));
        }
        if(slug.equals("shorekeeper")){
            resources.addProperty("domain_stage",state.timer("shore_stage"));resources.addProperty("domain_ticks",state.timer("shore_domain"));
            resources.addProperty("domain_max",role.chain()>=1?800:600);
        }
        if(slug.equals("galbrena")){
            resources.addProperty("inner_burn_ticks",state.timer("gal_inner"));
        }
        if(slug.equals("qiuyuan")){
            resources.addProperty("ink_stage",state.timer("qiuyuan_ink_stage"));resources.addProperty("drunk_ticks",state.timer("qiuyuan_drunk"));
        }
        if(slug.equals("cartethyia")){
            for(String key:List.of("human_sword","divine_sword","outsider_sword","human_right","divine_intent","outsider_power"))
                resources.addProperty(key,state.timer(key)>0?1:0);
        }
        for(String key:List.of("changli_true_sight","changli_flaming_feather","carlotta_mirror","carlotta_tinted_crystal","carlotta_gunflower","iuno_phase","iuno_full_moon","lupa_soul_lifetime","lupa_match_point","lupa_assist_ready","denia_entropy_dark","denia_entropy_stage","qingxiao_flight","qingxiao_escape_used","qingxiao_escape_cd"))resources.addProperty(key,state.timer(key));
        var caps = new com.google.gson.JsonObject(); MechanicsEngine.resourceCaps(state.characterId(), role.chain()).forEach(caps::addProperty); view.add("forteMax", caps);
        if(slug.equals("zani"))caps.addProperty("blaze",state.form().equals("blazing")?150:100);
        if(slug.equals("zhezhi"))caps.addProperty("zhezhi_insight",2);
        view.addProperty("shield",shields.effective(role.instanceId(),currentTick));
        var support=new com.google.gson.JsonArray();
        buffs.stream().filter(b->applies(b,role)&&!OutroRules.label(b.effect.id()).isEmpty()&&(!b.effect.id().equals("hsin_outro_unison")||unison.hasLamp(role.instanceId()))).sorted(Comparator.comparing(b->b.effect.id())).limit(12).forEach(b->{
            var item=new com.google.gson.JsonObject();item.addProperty("id",b.effect.id());item.addProperty("label",OutroRules.label(b.effect.id()));
            item.addProperty("ticks",Math.max(0,b.expires-currentTick));item.addProperty("duration",Math.max(1,b.effect.durationTicks()));support.add(item);
        });view.add("supportBuffs",support);
    }
    public int emberStacks(ServerPlayer player,LivingEntity target){return fiveWorld.emberStacks(player,target);}
    /** Read-only preview: never commit the returned state or execute preview effects. */
    public void addHudView(ServerPlayer player, CharacterInstance role, com.google.gson.JsonObject view) {
        var team = ModAttachments.team(player).state();
        if (team.active().orElse(null) != role) return;
        specialMovement.addView(view);
        var definition = McWuwa.catalog().requireByWeapon(role.character().weaponPath());
        var state = mechanics.get(role.instanceId());
        if (state == null) state = initialState(role, definition.characterId());
        state=swordShadows.prepare(player,role,state);
        state=heightEligibility(player,state);
        state=prepareTideState(player,role,state,false);
        var ctx = context(player, role);
        var abilities = new com.google.gson.JsonObject();
        if(state.characterId().equals("augusta"))view.addProperty("liberationHoldMode","HOLD_ALTERNATIVE");
        var focus=AbilityIconRules.focusView(state);
        if(state.characterId().equals("lucilla")){
            view.addProperty("cameraActive",state.timer("lucilla_camera")>0);
            var data=new com.google.gson.JsonObject();fiveWorld.photoView(player,role).forEach(data::addProperty);
            view.add("photoStatus",data);
        }
        if(focus!=null&&focus.open()){
            var data=new com.google.gson.JsonObject();
            data.addProperty("cursor",focus.cursor());data.addProperty("halfWidth",focus.perfectHalfWidth());
            data.addProperty("perfect",focus.insidePerfect());view.add("focusRing",data);
        }
        var basicInput=BasicInputRules.tap(state,ctx.airborne(),player.isInWater());
        // Match request(BASIC): an earned counter is selected before aerial
        // stance dispatch. Observation can counter while hovering as well.
        if(BasicInputRules.counterTap(state,team.stamina().snapshot().counterUntil()>team.onlineTick()))basicInput=MechanicsEngine.Input.DODGE_COUNTER;
        for (var input : List.of(basicInput, MechanicsEngine.Input.SKILL, MechanicsEngine.Input.LIBERATION)) {
            var previewState=AugustaInputRules.skillCounter(state,input,team.stamina().snapshot().counterUntil()>team.onlineTick());
            var result = MECHANICS.reduce(previewState, input, ctx);
            var selectedInput = input;
            if(input==MechanicsEngine.Input.SKILL&&state.characterId().equals("sigrika")&&state.resource("period")>=100&&!ctx.airborne()){
                var held=MECHANICS.reduce(state,MechanicsEngine.Input.SKILL_HOLD,ctx);
                if(held.accepted()&&held.effects().stream().anyMatch(e->e.kind()==MechanicsEngine.Kind.ACTION)){result=held;selectedInput=MechanicsEngine.Input.SKILL_HOLD;}
            }
            var status = new com.google.gson.JsonObject();
            int cooldown = result.reason().startsWith("cooldown:") ? state.timer(result.reason().substring(9)) : 0;
            boolean executable = result.accepted() && result.effects().stream().filter(e -> e.kind() == MechanicsEngine.Kind.ACTION)
                .allMatch(e -> McWuwa.catalog().actionMappings(definition.characterId()).stream()
                    .anyMatch(a -> a.id().equals(e.id()) && a.executable() && a.hitTimesTicks() != null));
            double energy = result.effects().stream().filter(e -> e.kind() == MechanicsEngine.Kind.PAY_ENERGY).mapToDouble(MechanicsEngine.Effect::amount).sum();
            double concerto = result.effects().stream().filter(e -> e.kind() == MechanicsEngine.Kind.PAY_CONCERTO).mapToDouble(MechanicsEngine.Effect::amount).sum();
            double stamina = result.effects().stream().filter(e -> e.kind() == MechanicsEngine.Kind.PAY_STAMINA).mapToDouble(MechanicsEngine.Effect::amount).sum();
            boolean worldTargetAvailable=true;
            for(var effect:result.effects()){
                if(effect.id().equals("phoebe_ring_teleport")&&!phoebeTeleportable(player,role))worldTargetAvailable=false;
                if(effect.kind()==MechanicsEngine.Kind.ACTION&&effect.id().startsWith("phrolova/hecate_")&&fields.stream().noneMatch(field->hecateField(field,player,role)))worldTargetAvailable=false;
                if(effect.id().equals("zhezhi_crane")&&!fiveWorld.selectedValid(player,role,effect.values().get("slot").intValue()))worldTargetAvailable=false;
            }
            boolean available = executable && worldTargetAvailable && role.canPay(Map.of("energy", energy, "concerto", concerto)) && ctx.stamina() >= stamina;
            status.addProperty("cooldownTicks", cooldown);
            status.addProperty("available", available);
            status.addProperty("ready", available && team.canAct()&&!ghostActive(player)
                && (running == null || input == MechanicsEngine.Input.SKILL && (canSwitchStanceDuringBasic(role)||canCiacconaSkillCancel(role))||canLinkAction(player,role,selectedInput,result)
                    ||HsinTravelRules.canReplace(state,running.action.id(),selectedInput)) && !EchoRuntime.isTransforming(player));
            String selected=result.effects().stream().filter(effect->effect.kind()==MechanicsEngine.Kind.ACTION).map(MechanicsEngine.Effect::id).findFirst().orElse("");
            if(input==basicInput&&running!=null&&running.weapon.equals(role.instanceId())&&running.action.id().equals("chisa/severing_pincers")) {
                var press=ChisaScissorsRules.press(running.action.id(),PlatformRuntime.time(player)-running.start,running.action.hitTimesTicks().getLast(),running.ledger.contains("pincers-extra"),running.ledger.contains("pincers-closed"));
                if(press!=ChisaScissorsRules.Press.NONE){selected=press==ChisaScissorsRules.Press.EXTRA?"chisa/severing_extra":"chisa/severing_pincers";
                    status.addProperty("ready",team.canAct()&&!ghostActive(player));status.addProperty("available",true);}
            }
            status.addProperty("enhanced",available&&(SkillLinkRules.enhanced(input,result)||input==basicInput&&AbilityIconRules.enhancedBasic(selected)
                ||input==MechanicsEngine.Input.SKILL&&AbilityIconRules.qualifiedSkill(state,selected)
                ||input==MechanicsEngine.Input.LIBERATION&&AbilityIconRules.qualifiedLiberation(state,selected)));
            if(input==MechanicsEngine.Input.SKILL&&state.characterId().equals("phoebe")){
                var confession=MECHANICS.reduce(state,MechanicsEngine.Input.SKILL_HOLD,ctx);
                if(confession.accepted()){
                    status.addProperty("enhanced",true);status.addProperty("holdAvailable",true);status.addProperty("holdDisplayName","显明告解");
                    boolean holdReady=team.canAct()&&!ghostActive(player)&&!EchoRuntime.isTransforming(player)
                        &&(running==null||canLinkAction(player,role,MechanicsEngine.Input.SKILL_HOLD,confession));
                    status.addProperty("ready",status.get("ready").getAsBoolean()||holdReady);
                    if(!available){
                        selected=confession.effects().stream().filter(e->e.kind()==MechanicsEngine.Kind.ACTION).map(MechanicsEngine.Effect::id).findFirst().orElse("");
                        selectedInput=MechanicsEngine.Input.SKILL_HOLD;
                        result=confession;
                        status.addProperty("available",true);status.addProperty("cooldownTicks",0);
                    }
                }
            }
            if(input==MechanicsEngine.Input.SKILL&&!status.has("holdDisplayName")){
                var heldSkill=MECHANICS.reduce(state,heldSkillInput(player,role,state),ctx);
                String heldAction=heldSkill.effects().stream().filter(e->e.kind()==MechanicsEngine.Kind.ACTION).map(MechanicsEngine.Effect::id).findFirst().orElse("");
                String travelName=heldSkill.effects().stream().filter(e->e.kind()==MechanicsEngine.Kind.MOVE_SPECIAL)
                    .map(e->switch(e.id()){case "qiuyuan_run"->"疾行";case "hsin_fox_travel"->"化狐";default->"";})
                    .filter(name->!name.isEmpty()).findFirst().orElse("");
                if(heldSkill.accepted()&&(!heldAction.isEmpty()&&!heldAction.equals(selected)||!travelName.isEmpty())){
                    double heldEnergy=heldSkill.effects().stream().filter(e->e.kind()==MechanicsEngine.Kind.PAY_ENERGY).mapToDouble(MechanicsEngine.Effect::amount).sum();
                    double heldConcerto=heldSkill.effects().stream().filter(e->e.kind()==MechanicsEngine.Kind.PAY_CONCERTO).mapToDouble(MechanicsEngine.Effect::amount).sum();
                    double heldStamina=heldSkill.effects().stream().filter(e->e.kind()==MechanicsEngine.Kind.PAY_STAMINA).mapToDouble(MechanicsEngine.Effect::amount).sum();
                    if(role.canPay(Map.of("energy",heldEnergy,"concerto",heldConcerto))&&ctx.stamina()>=heldStamina){
                        status.addProperty("holdAvailable",true);
                        status.addProperty("holdDisplayName",travelName.isEmpty()?actionLabel(definition.characterId(),heldAction):travelName);
                        status.addProperty("holdAction",heldAction);
                        var heldInput=heldSkillInput(player,role,state);
                        boolean heldReady=team.canAct()&&!ghostActive(player)&&!EchoRuntime.isTransforming(player)
                            &&(running==null||canLinkAction(player,role,heldInput,heldSkill));
                        status.addProperty("holdReady",heldReady);
                        status.addProperty("ready",status.get("ready").getAsBoolean()||heldReady);
                        if(!available){
                            selected=heldAction;selectedInput=MechanicsEngine.Input.SKILL_HOLD;result=heldSkill;
                            energy=heldEnergy;
                            status.addProperty("available",true);status.addProperty("cooldownTicks",0);
                        }
                    }
                }
            }
            status.addProperty("usesEnergy",energy>0);
            status.addProperty("selectedAction",selected);
            status.addProperty("selectedInput",selectedInput.name());
            status.addProperty("displayName",actionLabel(definition.characterId(),selected));
            status.addProperty("reason", result.reason());
            if(input==MechanicsEngine.Input.LIBERATION) {
                boolean resonance=state.characterId().equals("aemeath")&&state.timer("release")>0;
                status.addProperty("progressMode",resonance?"resonance":"energy");
                status.addProperty("progress",resonance?Math.clamp(state.resource("resonance")/4,0,1):role.resource("energy")/Math.max(1,role.resourceMax("energy")));
                if(resonance){status.addProperty("usesEnergy",false);status.addProperty("points",state.resource("resonance"));status.addProperty("maxPoints",4);status.addProperty("synchronizationReady",state.resource("synchronization")>=200);}
                if(state.characterId().equals("cartethyia")&&state.form().equals("fleurdelys")){
                    status.addProperty("progressMode","resolve");status.addProperty("usesEnergy",false);
                    status.addProperty("progress",Math.clamp(state.resource("resolve")/120,0,1));
                }
                if(state.characterId().equals("phrolova")){
                    boolean conducting=state.form().equals("conducting");
                    status.addProperty("usesEnergy",false);status.addProperty("progressMode",conducting?"duration":"notes");
                    status.addProperty("progress",conducting?Math.clamp(state.timer("conducting")/480d,0,1)
                        :state.timer("final_note")>0?1:Math.clamp(state.resource("notes")/6,0,1));
                    if(conducting)status.addProperty("remainingTicks",state.timer("conducting"));
                }
                if(state.characterId().equals("carlotta")&&state.form().equals("gunflower")) {
                    status.addProperty("progressMode","shots");status.addProperty("usesEnergy",false);
                    status.addProperty("progress",Math.clamp((4d-state.timer("carlotta_mirror"))/4,0,1));
                    status.addProperty("points",Math.max(0,4-state.timer("carlotta_mirror")));status.addProperty("maxPoints",4);
                }
                if(state.characterId().equals("denia")&&state.form().equals("disillusion")) {
                    status.addProperty("progressMode","conformal");status.addProperty("usesEnergy",false);
                    status.addProperty("progress",Math.clamp(state.resource("conformal")/100,0,1));
                }
                if(state.characterId().equals("lucilla")){
                    status.addProperty("progressMode","impression");status.addProperty("usesEnergy",false);
                    status.addProperty("progress",Math.clamp(state.resource("impression")/150,0,1));
                    status.addProperty("points",state.resource("impression"));status.addProperty("maxPoints",150);
                }
                if(state.characterId().equals("ciaccona")&&state.timer("ciaccona_performance")>0){
                    status.addProperty("progressMode","duration");
                    status.addProperty("progress",Math.clamp((double)state.timer("ciaccona_performance")/cn.zgnhit.mcwuwa.content.mechanics.NewAeroMechanics.PERFORMANCE_TICKS,0,1));
                    status.addProperty("usesEnergy",false);status.addProperty("displayName","结束演绎");
                    status.addProperty("remainingTicks",state.timer("ciaccona_performance"));
                }
            }
            if(input==MechanicsEngine.Input.SKILL&&state.characterId().equals("changli")){
                status.addProperty("maxCharges",2);
                status.addProperty("charges",Math.clamp(2-state.timer("changli_spent_charges"),0,2));
                status.addProperty("cooldownTicks",state.timer("changli_skill_recharge"));
            }
            if(input==MechanicsEngine.Input.SKILL&&state.characterId().equals("iuno")&&state.form().equals("new_moon")){
                int first=state.timer("iuno_beyond_first"),second=state.timer("iuno_beyond_second");
                status.addProperty("maxCharges",2);status.addProperty("charges",(first==0?1:0)+(second==0?1:0));
                status.addProperty("cooldownTicks",first==0?second:second==0?first:Math.min(first,second));
            }
            if(input==MechanicsEngine.Input.SKILL&&state.characterId().equals("ciaccona")&&role.chain()>=3){
                int first=state.timer("skill"),second=state.timer("ciaccona_skill_second");
                status.addProperty("maxCharges",2);status.addProperty("charges",(first==0?1:0)+(second==0?1:0));
                status.addProperty("cooldownTicks",first==0?second:second==0?first:Math.min(first,second));
            }
            if(input==basicInput) {
                var mode=HeldAttackRules.mode(state,ctx.airborne());
                var holdInput=mode==HeldAttackRules.Mode.HEAVY?MechanicsEngine.Input.HEAVY_START:HeldAttackRules.next(mode,state,"");
                if(state.characterId().equals("changli")&&ctx.airborne())holdInput=MechanicsEngine.Input.AIR;
                if(HeldAttackRules.immediateAirHeavy(state,running==null?"":running.action.id(),ctx.airborne()))holdInput=MechanicsEngine.Input.HEAVY_FULL;
                var hold=MECHANICS.reduce(state,holdInput,ctx);
                var holdStart=hold;
                if(hold.accepted()&&hold.effects().stream().noneMatch(effect->effect.kind()==MechanicsEngine.Kind.ACTION)&&mode==HeldAttackRules.Mode.HEAVY) {
                    var release=MECHANICS.reduce(hold.state(),MechanicsEngine.Input.HEAVY_FULL,ctx);
                    if(!release.accepted())release=MECHANICS.reduce(hold.state(),MechanicsEngine.Input.HEAVY_RELEASE,ctx);
                    // A free charge-start is not proof that its release is
                    // affordable. Preserve a rejected release for availability.
                    hold=release;
                }
                status.addProperty("holdMode",mode.name());
                status.addProperty("holdAction",hold.effects().stream().filter(effect->effect.kind()==MechanicsEngine.Kind.ACTION).map(MechanicsEngine.Effect::id).findFirst().orElse(""));
                double holdEnergy=hold.effects().stream().filter(e->e.kind()==MechanicsEngine.Kind.PAY_ENERGY).mapToDouble(MechanicsEngine.Effect::amount).sum();
                double holdConcerto=hold.effects().stream().filter(e->e.kind()==MechanicsEngine.Kind.PAY_CONCERTO).mapToDouble(MechanicsEngine.Effect::amount).sum();
                double holdStamina=hold.effects().stream().filter(e->e.kind()==MechanicsEngine.Kind.PAY_STAMINA).mapToDouble(MechanicsEngine.Effect::amount).sum();
                boolean holdAvailable=hold.accepted()&&role.canPay(Map.of("energy",holdEnergy,"concerto",holdConcerto))&&ctx.stamina()>=holdStamina;
                status.addProperty("holdAvailable",holdAvailable);status.addProperty("airborne",ctx.airborne());
                String holdId=status.get("holdAction").getAsString();
                boolean qualifiedHold=holdAvailable&&AbilityIconRules.qualifiedHold(state,holdId,mode);
                status.addProperty("tapEnhanced",status.get("enhanced").getAsBoolean());
                status.addProperty("tapReady",status.get("ready").getAsBoolean());
                status.addProperty("holdEnhanced",qualifiedHold);
                status.addProperty("enhanced",status.get("enhanced").getAsBoolean()||qualifiedHold);
                boolean holdReady=holdAvailable&&team.canAct()&&!ghostActive(player)&&!charge.active()&&!EchoRuntime.isTransforming(player)
                    &&(running==null||canLinkAction(player,role,holdInput,holdStart)
                        ||HsinTravelRules.canReplace(state,running.action.id(),holdInput));
                status.addProperty("holdReady",holdReady);
                if(qualifiedHold&&holdReady)
                    status.addProperty("ready",true);
                status.addProperty("displayName",actionLabel(definition.characterId(),selected));
                status.addProperty("holdName",actionLabel(definition.characterId(),holdId));
            }
            String abilityKey=input == MechanicsEngine.Input.SKILL ? "skill" : input==MechanicsEngine.Input.LIBERATION?"liberation":"basic";
            if(input==MechanicsEngine.Input.LIBERATION&&LiberationInputRules.mode(state)==LiberationGesture.Mode.HOLD_ALTERNATIVE) {
                var hold=MECHANICS.reduce(state,MechanicsEngine.Input.LIBERATION_HOLD,ctx);
                String holdId=hold.effects().stream().filter(effect->effect.kind()==MechanicsEngine.Kind.ACTION).map(MechanicsEngine.Effect::id).findFirst().orElse("");
                status.addProperty("holdAction",holdId);status.addProperty("holdName",actionLabel(definition.characterId(),holdId));
                boolean holdExecutable=hold.accepted()&&hold.effects().stream().filter(e->e.kind()==MechanicsEngine.Kind.ACTION)
                    .allMatch(e->McWuwa.catalog().actionMappings(definition.characterId()).stream().anyMatch(a->a.id().equals(e.id())&&a.executable()&&a.hitTimesTicks()!=null));
                double holdEnergy=hold.effects().stream().filter(e->e.kind()==MechanicsEngine.Kind.PAY_ENERGY).mapToDouble(MechanicsEngine.Effect::amount).sum();
                double holdConcerto=hold.effects().stream().filter(e->e.kind()==MechanicsEngine.Kind.PAY_CONCERTO).mapToDouble(MechanicsEngine.Effect::amount).sum();
                double holdStamina=hold.effects().stream().filter(e->e.kind()==MechanicsEngine.Kind.PAY_STAMINA).mapToDouble(MechanicsEngine.Effect::amount).sum();
                boolean holdAvailable=holdExecutable&&role.canPay(Map.of("energy",holdEnergy,"concerto",holdConcerto))&&ctx.stamina()>=holdStamina;
                status.addProperty("holdAvailable",holdAvailable);
                if(holdAvailable){
                    status.addProperty("enhanced",true);
                    boolean holdReady=team.canAct()&&!ghostActive(player)&&!EchoRuntime.isTransforming(player)
                        &&(running==null||canLinkAction(player,role,MechanicsEngine.Input.LIBERATION_HOLD,hold));
                    status.addProperty("ready",status.get("ready").getAsBoolean()||holdReady);
                    if(!available){
                        status.addProperty("cooldownTicks",0);
                        status.addProperty("available",true);
                        selected=holdId;
                        status.addProperty("selectedAction",holdId);status.addProperty("selectedInput",MechanicsEngine.Input.LIBERATION_HOLD.name());
                        status.addProperty("displayName",actionLabel(definition.characterId(),holdId));
                        status.addProperty("usesEnergy",holdEnergy>0);status.addProperty("reason","");
                    }
                    if(state.characterId().equals("augusta")){
                        boolean realm=state.form().equals("realm");
                        status.addProperty("holdName",realm?"赫日威临·不朽者之肃":"赫日威临");
                        status.addProperty("progressMode",realm?"duration":"majesty");
                        status.addProperty("progress",realm?Math.clamp(state.timer("augusta_phase")/140d,0,1):Math.clamp(state.resource("majesty")/2,0,1));
                        if(realm)status.addProperty("remainingTicks",state.timer("augusta_phase"));
                        else {status.addProperty("points",state.resource("majesty"));status.addProperty("maxPoints",2);}
                    }
                }
            }
            boolean alternate=status.get("enhanced").getAsBoolean();status.addProperty("iconVariant",AbilityIconRules.variant(state,abilityKey,selected,alternate));
            status.addProperty("iconStage",selected.contains("finale")||selected.contains("_return")||selected.contains("skill_blast")||selected.contains("followup")?2:alternate?1:0);
            abilities.add(abilityKey, status);
        }
        var echo = new com.google.gson.JsonObject();
        var weapon = PlatformRuntime.weaponFor(player, role);
        var ability = EchoAbilityCatalog.require(EchoRuntime.effectiveEchoId(role));
        boolean equipped = EchoRuntime.mainEnabled(weapon);
        int charges = ability.charges() > 1 ? Math.clamp(cn.zgnhit.mcwuwa.platform.NbtValues.getInt(WeaponData.tag(weapon),"echo_charges", 3), 0, ability.charges()) : 1;
        long cooldown = role.cooldown(ability.charges() > 1 ? "echo_recharge" : "echo_active");
        echo.addProperty("equipped", equipped); echo.addProperty("charges", charges); echo.addProperty("maxCharges", ability.charges());
        echo.addProperty("cooldownTicks", cooldown);
        echo.addProperty("available", equipped && (ability.charges() > 1 ? charges > 0 : cooldown == 0));
        echo.addProperty("ready", equipped && (ability.charges() > 1 ? charges > 0 : cooldown == 0)
            && team.canAct()&&!ghostActive(player) && running == null && !EchoRuntime.isTransforming(player));
        abilities.add("echo", echo); view.add("abilities", abilities);
    }
    private static String actionLabel(String slug,String id) {
        var mapping=McWuwa.catalog().actionMappings(slug).stream().filter(action->action.id().equals(id)).findFirst().orElse(null);
        if(mapping==null)return "";
        String label=mapping.name().replace("伤害","");
        return label.isBlank()||label.equals("技能")?McWuwa.catalog().requireByCharacter(slug).requireSkill(mapping.sourceNode()).name():label;
    }
    public double offTuneEfficiency(ServerPlayer player, CharacterInstance role) {
        return Math.max(0,combatStats(player,role).bonus("offtune_efficiency")+fieldOffTuneEfficiency(player));
    }
    private double fieldOffTuneEfficiency(ServerPlayer player){return fields.stream().filter(f->insideField(f,player))
        .mapToDouble(f->f.effect.values().getOrDefault("offTuneEfficiency",0d)).max().orElse(0);}
    /** Native point-valued amplification, not a damage multiplier or unverified mastery conversion. */
    public double tuneBreakAmplification(ServerPlayer player,CharacterInstance role) {
        currentTick=PlatformRuntime.time(player);
        double base = McWuwa.catalog().requireByWeapon(role.character().weaponPath()).tuneMasteryRaw();
        return Math.max(0,base+combatStats(player,role).bonus("tune_break_amplification")+buffs.stream().filter(b->applies(b,role)
            &&b.effect.id().equals("tune_break_amplification")).mapToDouble(b->b.effect.amount()).max().orElse(0));
    }
    public double multiplierAddition(CharacterInstance role, String action, boolean finalHit) {
        return mechanicsModifiers(role, action, "all", "all", null, finalHit).multiplierAddition()+fiveWorld.zaniFinaleBonus(role,action,finalHit)+fiveWorld.zaniNightBonus(role,action,finalHit,currentTick);
    }
    public NativeStats combatStats(ServerPlayer player,CharacterInstance role){return combatStats(player,role,"all");}
    /** Server-owned cultivation panel, using the same equipped/dynamic aggregation as combat. */
    public void addStatsView(ServerPlayer player,CharacterInstance role,com.google.gson.JsonObject view){
        var common=combatStatModifiers(player,role,"all","all");
        var stats=NativeStats.of(role,PlatformRuntime.weaponFor(player,role),common);var panel=new com.google.gson.JsonObject();
        panel.addProperty("characterLevel",90);panel.addProperty("weaponLevel",90);panel.addProperty("skillLevel",10);
        double mappedHealth=PlatformRuntime.projectedMaxHealth(player,stats.health());
        panel.addProperty("health",mappedHealth/CombatMath.HEALTH_SCALE);panel.addProperty("mappedHealth",mappedHealth);
        panel.addProperty("attack",stats.attack());panel.addProperty("defense",stats.defense());
        panel.addProperty("critRate",stats.uncappedCritRate());panel.addProperty("critChance",stats.critRate());panel.addProperty("critDamage",stats.critDamage());panel.addProperty("energyRegen",stats.energyRegen());
        panel.addProperty("tuneBreakAmplification",tuneBreakAmplification(player,role));
        panel.addProperty("offTuneEfficiency",1+offTuneEfficiency(player,role));
        // Damage-zone buffs are added by the hit resolver, separately from stats.
        // Show them here too, without feeding them back into the damage calculation.
        double mechanicCommon=mechanicsModifiers(player,role,"","all","all",null,false).damageBonus();
        var bonuses=new com.google.gson.JsonObject();stats.bonuses().forEach(bonuses::addProperty);
        bonuses.addProperty("all_damage_bonus",stats.bonus("all_damage_bonus")+mechanicCommon);panel.add("bonuses",bonuses);
        // Evaluate type/element-gated weapon buffs in their own contexts instead of
        // displaying zero simply because the generic stat request has kind=all.
        var applicable=new com.google.gson.JsonObject();
        for(String kind:List.of("basic","heavy","skill","liberation","intro","outro","echo")){
            var typed=combatStatModifiers(player,role,kind,"all");String key=kind+"_bonus";
            applicable.addProperty(key,stats.bonus(key)-common.getOrDefault(key,0d)+typed.getOrDefault(key,0d)
                -common.getOrDefault("all_damage_bonus",0d)+typed.getOrDefault("all_damage_bonus",0d)
                +mechanicsModifiers(player,role,"",kind,"all",null,false).damageBonus()-mechanicCommon);
        }
        for(String element:List.of("glacio","fusion","electro","aero","spectro","havoc")){
            var typed=combatStatModifiers(player,role,"all",element);String key=element+"_bonus";
            // Typed DAMAGE_BONUS resolves back to the canonical all_damage_bonus
            // key; only the context-specific delta belongs to this element row.
            applicable.addProperty(key,stats.bonus(key)-common.getOrDefault(key,0d)+typed.getOrDefault(key,0d)
                -common.getOrDefault("all_damage_bonus",0d)+typed.getOrDefault("all_damage_bonus",0d)
                +mechanicsModifiers(player,role,"","all",element,null,false).damageBonus()-mechanicCommon);
        }
        panel.add("applicableBonuses",applicable);
        view.add("stats",panel);
    }
    /** Full dynamic ER without calling RolePassiveRuntime.damage, which uses ER to scale Mornye. */
    public double energyRegenWithoutRolePassives(ServerPlayer player,CharacterInstance role) {
        var extra=new HashMap<>(passiveModifiers(player,role,null,"all","all"));
        EchoRuntime.modifiers(player,role).forEach((key,value)->extra.merge(key,value,Double::sum));
        return NativeStats.of(role,PlatformRuntime.weaponFor(player,role),extra).energyRegen();
    }
    public NativeStats combatStats(ServerPlayer player,CharacterInstance role,String kind) {
        return NativeStats.of(role,PlatformRuntime.weaponFor(player,role),combatStatModifiers(player,role,kind,"all"));
    }
    private Map<String,Double> combatStatModifiers(ServerPlayer player,CharacterInstance role,String kind,String element){
        Map<String,Double> extra=new HashMap<>(passiveModifiers(player,role,null,kind,element));
        RolePassiveRuntime.damage(player,role,null,role.actionId(),kind,element).attributes().forEach((key,value)->extra.merge(key,value,Double::sum));
        EchoRuntime.modifiers(player, role).forEach((key,value) -> extra.merge(key,value,Double::sum));
        return extra;
    }
    private void heal(ServerPlayer player, CharacterInstance source, CharacterInstance target, double nativeAmount) {
        heal(player,source,target,nativeAmount,0);
    }
    private void heal(ServerPlayer player, CharacterInstance source, CharacterInstance target, double nativeAmount,double sourceBonus) {
        double outgoing = combatStats(player, source).bonus("healing_bonus")+sourceBonus;
        double incoming = combatStats(player, target).bonus("incoming_healing_bonus");
        target.heal(CombatResolver.nativeHealing(nativeAmount, outgoing, incoming));
    }
    private void tideHeal(ServerPlayer player,CharacterInstance owner,CharacterInstance recipient,double flat,double healthRatio){
        heal(player,owner,recipient,flat+healthRatio*context(player,owner).maxHealthNative());
        passiveEvent(player,owner,WeaponPassiveEngine.Trigger.HEAL,"suisui_spring:"+PlatformRuntime.time(player));
        PlatformRuntime.applyHealth(player,ModAttachments.team(player),false);
    }
    public boolean phoebeSilentPrayer(ServerPlayer player,LivingEntity target){return phoebePrayerAmplification(player,target)>0;}
    private double phoebePrayerAmplification(ServerPlayer player,LivingEntity target){
        if(player.distanceToSqr(target)>144)return 0;
        return buffs.stream().filter(b->b.effect.id().equals("phoebe_silent_prayer")&&b.expires>PlatformRuntime.time(player)&&teamRole(player,b.owner).filter(owner->owner.alive()&&ruleState(owner).form().equals("confession")).isPresent()).mapToDouble(b->b.effect.values().getOrDefault("frazzleAmplification",1d)).max().orElse(0);
    }
    private static List<LivingEntity> phoebeRingTargets(ServerPlayer player,Field ring){
        double radius=ring.area.radius();
        var bounds=new net.minecraft.world.phys.AABB(ring.center,ring.center).inflate(radius);
        return player.level().getEntitiesOfClass(LivingEntity.class,bounds,
            target->PlatformRuntime.canAttack(player,target)&&target.position().distanceToSqr(ring.center)<=radius*radius);
    }
    private void phoebeRingStasis(ServerPlayer player,CharacterInstance owner,Field ring){
        if(!fieldActive(ring,player))return;var affected=phoebeStasisTargets.computeIfAbsent(owner.instanceId(),id->new HashSet<>());
        for(var target:phoebeRingTargets(player,ring)){
            if(affected.size()>=12)break;if(affected.add(PlatformRuntime.combatTargetId(target)))EffectRuntime.applyStasis(player,owner,target,owner.chain()>=6?80:40);
        }
    }
    private void phoebeRefract(ServerPlayer player,CharacterInstance owner,Field ring){
        long now=PlatformRuntime.time(player);if(!owner.alive()||!fieldActive(ring,player)||phoebeRefractionReady.getOrDefault(owner.instanceId(),0L)>now)return;
        phoebeRefractionReady.put(owner.instanceId(),now+10);
        for(var target:phoebeRingTargets(player,ring)){
            if(tideNativeHit(player,owner,target,"refraction")){
                // This autonomous cast has no player Running action. Keep its
                // actual ring origin and confirmed contact, even after camera turns.
                var ray=ConstructEntity.spawn(player.serverLevel(),player,owner.instanceId(),"phoebe_refraction_ray",ring.center,8,1,false,"attack");
                ray.visualRayTo(target.getBoundingBox().getCenter());
            }
        }
        EffectRuntime.pullToPoint(player,owner,ring.center,ring.area.radius(),1);
    }
    private Optional<Field> phoebeRing(ServerPlayer player,CharacterInstance role){return fields.stream().filter(f->f.owner.equals(role.instanceId())&&f.effect.id().equals("phoebe_ring")&&fieldActive(f,player)).findFirst();}
    private boolean phoebeTeleportable(ServerPlayer player,CharacterInstance role){return phoebeRing(player,role).filter(f->player.position().distanceToSqr(f.center)<=400&&player.level().noCollision(player,player.getBoundingBox().move(f.center.subtract(player.position())))).isPresent();}
    private Vec3 tideAim(ServerPlayer player){
        Vec3 eye=player.getEyePosition(),end=eye.add(player.getLookAngle().scale(10));
        var block=player.level().clip(new net.minecraft.world.level.ClipContext(eye,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
        return block.getLocation().subtract(player.getLookAngle().scale(.5));
    }
    private MechanicsState prepareTideState(ServerPlayer player,CharacterInstance role,MechanicsState state,boolean tick){
        if(!Set.of("phoebe","cantarella").contains(state.characterId()))return state;
        var timers=new HashMap<>(state.timers());
        if(state.characterId().equals("phoebe")){
            if(tick&&ModAttachments.team(player).state().active().orElse(null)==role&&TraversalMotion.solidSupport(player)){
                timers.remove("phoebe_glides");timers.remove("phoebe_staff_glide");timers.remove("phoebe_staff_dive");
            }
            if(phoebeRing(player,role).filter(f->player.position().distanceToSqr(f.center)<=f.area.radius()*f.area.radius()).isPresent())timers.put("phoebe_inside_ring",2);else timers.remove("phoebe_inside_ring");
        }
        if(tick&&state.characterId().equals("cantarella")){
            int water=player.isInWater()&&ModAttachments.team(player).state().active().orElse(null)==role?timers.getOrDefault("cantarella_water_ticks",0)+1:0;
            if(water>=100){state=MECHANICS.grantResource(state,"trance",1);water=0;}
            if(water>0)timers.put("cantarella_water_ticks",water);else timers.remove("cantarella_water_ticks");
        }
        return new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),state.nextBasic(),state.resources(),timers);
    }
    private boolean tideNativeHit(ServerPlayer player,CharacterInstance owner,LivingEntity target,String action){
        if(!owner.alive()||!PlatformRuntime.canAttack(player,target))return false;
        if(action.equals("jellyfish"))ConstructEntity.spawn(player.serverLevel(),player,owner.instanceId(),"skill_cantarella_jellyfish",target.position().add(0,1.8,0),14,.75f,false,"attack");
        var definition=McWuwa.catalog().requireByWeapon(owner.character().weaponPath());
        var mapping=McWuwa.catalog().actionMappings(definition.characterId()).stream().filter(a->a.id().equals(definition.characterId()+"/"+action)).findFirst().orElseThrow();
        boolean applied=false;
        for(var group:mapping.hitGroups())for(int i=0;i<group.count();i++)applied|=PlatformRuntime.nativeHit(player,owner,target,definition.requireDamage(group.damageId()),0,1,mapping.id(),PlatformRuntime.HitOrigin.AUTONOMOUS);
        if(applied&&owner.character()==CharacterId.PHOEBE&&action.equals("heavy_starlight")&&ruleState(owner).form().equals("confession"))
            applyMark(player,owner,target,new MechanicsEngine.Effect(MechanicsEngine.Kind.MARK_TARGET,"spectro_frazzle",5,0,Map.of()));
        if(applied){
            if(owner.character()==CharacterId.CANTARELLA&&action.equals("awaken"))autonomousContact(player,owner,target,"cantarella__awaken_contact");
            if(owner.character()==CharacterId.CANTARELLA&&action.equals("jellyfish"))autonomousContact(player,owner,target,"cantarella__jellyfish_contact");
            if(owner.character()==CharacterId.PHOEBE&&action.equals("heavy_starlight"))autonomousContact(player,owner,target,"phoebe__starlight_contact");
            if(owner.character()==CharacterId.QINGXIAO&&action.startsWith("vast_sword"))autonomousContact(player,owner,target,"qingxiao__vast_sword_contact");
            if(owner.character()==CharacterId.CARLOTTA&&action.equals("outro_c3_extra"))autonomousContact(player,owner,target,"carlotta__outro_contact");
            player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,target.getX(),target.getY()+target.getBbHeight()*.6,target.getZ(),8,.3,.3,.3,.025);
        }
        return applied;
    }
    private static void autonomousContact(ServerPlayer player,CharacterInstance owner,LivingEntity target,String light){
        // ActionRibbonRenderer adds 1.05 vertically. Anchor its local zero at
        // the real victim centre, not at the current role's forward offset.
        ConstructEntity.spawn(player.serverLevel(),player,owner.instanceId(),"skill_light_"+light,
            target.getBoundingBox().getCenter().add(0,-1.05,0),12,1,false,"attack");
    }
    private void augustaThunder(ServerPlayer player,CharacterInstance owner){
        // MC four-block local footprint; two original100% heavy hits per victim.
        for(var target:player.level().getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(4),
            e->PlatformRuntime.canAttack(player,e)&&player.distanceToSqr(e)<=16&&player.hasLineOfSight(e)))
        {
            boolean applied=false;
            for(int i=0;i<2;i++)applied|=PlatformRuntime.nativeHit(player,owner,target,
                RoleProcDefinitions.solo("augusta_c6_thunder","heavy","electro","attack",1,Map.of()),0,1,"augusta/c6_thunder",PlatformRuntime.HitOrigin.AUTONOMOUS);
            if(applied)autonomousContact(player,owner,target,"augusta__thunder_contact");
        }
    }
    private void applyTargetEffect(ServerPlayer player, CharacterInstance role, LivingEntity target, MechanicsEngine.Effect effect) {
        if(effect.kind()==MechanicsEngine.Kind.NATIVE_PROC&&effect.id().equals("zani_suppression")){
            fiveWorld.suppressionHit(player,role,target);return;
        }
        if(effect.kind()==MechanicsEngine.Kind.NATIVE_PROC&&effect.id().equals("lucilla_forget_pull")){
            EffectRuntime.pullToPoint(player,role,target.position(),6,.25);return;
        }
        if(effect.kind()==MechanicsEngine.Kind.PULL_AREA)applyEffects(player,role,List.of(effect),target);
        else if(effect.kind()==MechanicsEngine.Kind.CONTROL_TARGET)EffectRuntime.applyStasis(player,role,target,effect.durationTicks());
        else if(effect.kind()==MechanicsEngine.Kind.MARK_TARGET)applyMark(player,role,target,effect);
        else if(effect.kind()==MechanicsEngine.Kind.NATIVE_PROC&&effect.id().startsWith(McWuwa.catalog().requireByWeapon(role.character().weaponPath()).characterId()+"/")
            &&Set.of(CharacterId.DENIA,CharacterId.IUNO,CharacterId.CHANGLI,CharacterId.QINGXIAO,CharacterId.LUPA,CharacterId.CARLOTTA).contains(role.character()))
        {
            String action=effect.id().substring(effect.id().indexOf('/')+1);
            if(role.character()==CharacterId.QINGXIAO&&action.startsWith("vast_sword"))
                sixWorld.mark(player,role,target,"qingxiao_vast_sword_exposure",40,1,false);
            tideNativeHit(player,role,target,action);
        }
        else if(effect.kind()==MechanicsEngine.Kind.NATIVE_PROC&&effect.id().equals("denia_preserving_detonation"))
            sixWorld.deniaPreservingDetonation(player,role,target,()->EffectRuntime.fusionAtCap(player,role,target,false,1+effect.values().getOrDefault("mainTargetRateBonus",0d)));
        else if(effect.kind()==MechanicsEngine.Kind.NATIVE_PROC&&effect.id().equals("xuanling_shadow")) {
            if(PlatformRuntime.nativeHit(player,role,target,RoleProcDefinitions.solo("xuanling_shadow","heavy","havoc","attack",effect.amount(),effect.values()),0,1,"yangyang_xuanling/chain_shadow",PlatformRuntime.HitOrigin.AUTONOMOUS))
                autonomousContact(player,role,target,"yangyang_xuanling__shadow_contact");
        }
        else throw new IllegalArgumentException("not a target effect: "+effect.kind()+"/"+effect.id());
    }
    private void applyMark(ServerPlayer player, CharacterInstance role, LivingEntity target, MechanicsEngine.Effect mark) {
        if (!PlatformRuntime.canAttack(player, target)) return;
        if(mark.id().equals("zani_counter_vibration")){
            if(!(target instanceof net.minecraft.world.entity.player.Player)){
                var gauge=PlatformRuntime.gauge(target);long now=PlatformRuntime.time(player);
                gauge.onHit(gauge.snapshot(now).toughnessMax()*mark.amount(),0,false,now);
            }
            return;
        }
        if(mark.id().equals("denia_reset_erosion_detonation")){sixWorld.resetDeniaDetonation(player,role,target);return;}
        if(role.character()==CharacterId.DENIA&&mark.values().containsKey("sameSkillTargetIcd")
            &&!sixWorld.deniaSkillGate(player,role,target,"sourceSkill:"+mark.values().get("sourceSkill").intValue()))return;
        if(Set.of("carlotta_dissociation","carlotta_variegation","carlotta_iridescence","lupa_encircle","qingxiao_mind_lock").contains(mark.id())) {
            sixWorld.mark(player,role,target,mark.id(),mark.durationTicks(),(int)mark.amount(),mark.values().getOrDefault("endsOnDamage",0d)>0);return;
        }

        if(mark.id().equals("fill_cap_spectro_frazzle")){for(var application:EffectRuntime.fillKnownCap(player,role,target,"spectro_frazzle"))effectApplied(player,role,target,application.id(),application.applications());return;}
        if(mark.id().equals("cantarella_dream")){tideWorld.dream(player,role,target,mark.durationTicks(),mark.values().getOrDefault("graceTicks",0d).intValue());return;}
        if(mark.id().equals("phoebe_c4_spectro_shred")){
            EffectRuntime.applyStatDebuff(player,role,target,AttributeModifiers.Zone.RESISTANCE_REDUCTION,"phoebe/c4",mark.amount(),mark.durationTicks(),new AttributeModifiers.Selector(Set.of(Element.SPECTRO),Set.of(),false,false));return;
        }
        if (mark.id().equals("yinlin_punishment")) {
            if (!EffectRuntime.hasOwnedEffect(target,player.getUUID(),role.instanceId(),"yinlin_sin")) return;
            EffectRuntime.consumeOwned(target,player.getUUID(),role.instanceId(),"yinlin_sin",Integer.MAX_VALUE);
        }
        var applied = EffectRuntime.applyMark(player, role, target, mark.id(), (int)mark.amount(), mark.durationTicks());
        if(!applied.isEmpty()&&mark.values().getOrDefault("refreshInterference",0d)>0)EffectRuntime.refreshStrainInterference(player,target);
        if(!applied.isEmpty())soloTargets.markAttached(player,role,target,mark.id());
        if (mark.id().equals("yinlin_punishment") && EffectRuntime.hasOwnedEffect(target,player.getUUID(),role.instanceId(),mark.id())) punishedTargets.put(target,role.instanceId());
        for (var application : applied) effectApplied(player, role, target, application.id(), application.applications());
    }
    private void effectApplied(ServerPlayer player, CharacterInstance role, LivingEntity target, String effectId, int applications) {
        effectApplied(player,role,target,effectId,applications,false);
    }
    private void effectApplied(ServerPlayer player, CharacterInstance role, LivingEntity target, String effectId, int applications,boolean photoCopy) {
        // Native terminology makes Frost application/damage also count as Chafe, exactly once.
        if(effectId.equals("glacio_frost"))effectId="glacio_chafe";
        if(applications>0&&effectId.equals("spectro_frazzle")){
            var party=ModAttachments.team(player).state();
            for(int slot=0;slot<3;slot++){
                var candidate=party.at(slot);
                if(candidate.isEmpty()||candidate.get().character().sourceId()!=1507||!candidate.get().alive())continue;
                var owner=candidate.get();int layers=EffectRuntime.layers(target,"spectro_frazzle");
                if(layers>0&&EffectRuntime.detonate(player,owner,target,"spectro_frazzle",true,false,1)){
                    double blaze=fiveWorld.convertFrazzle(player,owner,target,layers);
                    var state=ruleState(owner);var resources=new HashMap<>(state.resources());resources.put("blaze",blaze);
                    commit(owner,new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),state.nextBasic(),resources,state.timers()));
                }
                break; // One conversion consumes the shared round, not once per party slot.
            }
        }
        if(!photoCopy&&applications>0&&effectId.equals("glacio_chafe")){
            for(var copy:fiveWorld.copyChafe(player,role,id->teamRole(player,id).orElse(null))){
                // Eight blocks is the authored MC "nearby" radius. Keep the
                // original Lucilla weapon as source, not the active teammate.
                for(var victim:player.level().getEntitiesOfClass(LivingEntity.class,target.getBoundingBox().inflate(8),
                    e->e.isAlive()&&PlatformRuntime.canAttack(player,e)))
                    for(int i=0;i<copy.applications();i++)
                        for(var applied:EffectRuntime.applyMark(player,copy.source(),victim,"glacio_chafe",1,600))
                            effectApplied(player,copy.source(),victim,applied.id(),applied.applications(),true);
            }
        }
        if (effectId.equals("havoc_bane")) {
            for (Buff buff : List.copyOf(buffs)) if (buff.effect.id().equals("yangyang_outro") && applies(buff, role)) {
                MechanicsEngine.Effect enabled = new MechanicsEngine.Effect(MechanicsEngine.Kind.BUFF_SELF, "yangyang_outro_active", buff.effect.amount(), 400, Map.of());
                buffs.removeIf(b -> b.owner.equals(buff.owner) && Objects.equals(b.recipient, role.instanceId()) && b.effect.id().equals(enabled.id()));
                buffs.add(new Buff(buff.owner, role.instanceId(), enabled, buff.expires));
            }
        }
        suisuiAnomalyActivity(player,role,target,effectId,false);
        EchoRuntime.onEffectApplied(player, role, target, effectId);
        sixWorld.effectApplied(player,role,target,effectId.equals("strain_shifting")?"tune_offset":effectId.equals("strain_interference")?"tune_interference":effectId,this::ruleState,sixCallbacks(player));
        if(effectId.equals("strain_shifting"))for(int index=0;index<buffs.size();index++) {
            var buff=buffs.get(index);if(buff.expires<=PlatformRuntime.time(player)||!Objects.equals(buff.recipient,role.instanceId())||!buff.effect.id().equals("denia_tune_outro"))continue;
            var values=new HashMap<>(buff.effect.values());values.put("recipientAppliedTuneOffset",1d);
            buffs.set(index,new Buff(buff.owner,buff.recipient,new MechanicsEngine.Effect(buff.effect.kind(),buff.effect.id(),buff.effect.amount(),buff.effect.durationTicks(),values),buff.expires));
        }
        RolePassiveRuntime.onEffectApplied(player, role, target, effectId, applications);
        sonatas.event(player, role, effectId, false, offTuneEfficiency(player, role), 0);
        WeaponPassiveEngine.Trigger trigger = switch (effectId) {
            case "havoc_bane" -> WeaponPassiveEngine.Trigger.APPLY_HAVOC_BANE;
            case "electro_flare" -> WeaponPassiveEngine.Trigger.APPLY_ELECTRO_FLARE;
            case "glacio_chafe" -> WeaponPassiveEngine.Trigger.APPLY_GLACIO_CHAFE;
            case "aero_erosion" -> WeaponPassiveEngine.Trigger.APPLY_AERO_EROSION;
            case "fusion_burst" -> WeaponPassiveEngine.Trigger.APPLY_FUSION_BURST;
            case "rupture_shifting" -> WeaponPassiveEngine.Trigger.APPLY_RUPTURE_SHIFTING;
            case "strain_shifting" -> WeaponPassiveEngine.Trigger.APPLY_STRAIN_SHIFTING;
            default -> null;
        };
        if (trigger != null) passiveEvent(player, role, trigger, Long.toString(sequence));
        WeaponPassiveEngine.Trigger teamTrigger=switch(effectId) {
            case "fusion_burst" -> WeaponPassiveEngine.Trigger.TEAM_APPLY_FUSION_BURST;
            case "strain_shifting" -> WeaponPassiveEngine.Trigger.TEAM_APPLY_STRAIN_SHIFTING;
            default -> null;
        };
        if(teamTrigger!=null) {
            var members=ModAttachments.team(player).state();
            for(int slot=0;slot<3;slot++)members.at(slot).filter(owner->owner!=role&&owner.alive())
                .ifPresent(owner->passiveEvent(player,owner,role,teamTrigger,Long.toString(sequence)));
        }
        if (Set.of("havoc_bane", "electro_flare", "glacio_chafe", "fusion_burst", "spectro_frazzle", "aero_erosion").contains(effectId)) {
            passiveEvent(player, role, WeaponPassiveEngine.Trigger.APPLY_ANOMALY, Long.toString(sequence));
            var team = ModAttachments.team(player).state();
            for (int i = 0; i < 3; i++) if (team.at(i).isPresent() && team.at(i).orElseThrow() != role) passiveEvent(player, team.at(i).orElseThrow(), role, WeaponPassiveEngine.Trigger.TEAM_APPLY_ANOMALY, Long.toString(sequence));
        }
    }
    /** Lifecycle exit runs source-specific expiry before copying cooldowns to the real weapon. */
    public void prepareLogout(ServerPlayer player) {
        specialMovement.clear(player);
        var team = ModAttachments.team(player).state();
        for (int i = 0; i < 3; i++) if (team.at(i).isPresent()) {
            var role = team.at(i).orElseThrow(); var state = mechanics.get(role.instanceId());
            if (state != null) { var result = MECHANICS.reduce(state, MechanicsEngine.Input.SWITCH_OUT, context(player, role)); if (result.accepted()) commit(role, result.state()); }
        }
    }
    public void consumedUnison(CharacterInstance role) {
        var state = mechanics.get(role.instanceId()); if (state == null) return;
        Map<String, Integer> timers = new HashMap<>(state.timers()); timers.remove("unison");
        if(state.characterId().equals("suoming"))timers.put("suoming_unison_spent",1);
        commit(role, new MechanicsState(state.characterId(), state.form(), state.mode(), state.chain(), state.nextBasic(), state.resources(), timers));
    }
    private static boolean singleplayerUnison(ServerPlayer player){return !player.level().getServer().isDedicatedServer()&&player.level().getServer().getPlayerList().getPlayerCount()<=1;}
    private List<CharacterInstance> unisonSupporters(ServerPlayer player){
        var team=ModAttachments.team(player).state();var supporters=new ArrayList<CharacterInstance>();
        for(int slot=0;slot<3;slot++)team.at(slot).filter(owner->owner.alive()&&owner.character()==CharacterId.HSIN
            &&MechanicsState.canonicalMode("hsin",owner.selectedMode()).equals("unison")).ifPresent(supporters::add);
        return supporters;
    }
    public int unisonStackCap(ServerPlayer player){
        return 2+unisonSupporters(player).stream().mapToInt(owner->UnisonRules.extraLayers(owner.chain())).max().orElse(0);
    }
    public int unisonStacks(ServerPlayer player,CharacterInstance role){
        // Attribute reads also run during equipment projection, before a selected mode is applied.
        // They must not initialize a cached mechanics state with that earlier default mode.
        if(role.character().sourceId()==1312&&singleplayerUnison(player))return unison.stacks(PlatformRuntime.time(player),unisonStackCap(player),role.instanceId());
        return role.character()==CharacterId.HSIN&&singleplayerUnison(player)
            &&MechanicsState.canonicalMode("hsin",role.selectedMode()).equals("unison")?unison.stacks(PlatformRuntime.time(player),unisonStackCap(player),role.instanceId()):0;
    }
    public double unisonMultiplier(ServerPlayer player,CharacterInstance role){
        int layers=unisonStacks(player,role);if(layers==0)return 1;
        var team=ModAttachments.team(player).state();boolean enhanced=false;
        for(int i=0;i<3;i++)if(team.at(i).filter(r->r.alive()&&r.character().sourceId()==1312&&r.chain()>=6).isPresent())enhanced=true;
        return 1+.03*layers+(enhanced?.015*Math.min(4,layers):0);
    }
    private void refreshUnison(ServerPlayer player){
        var team=ModAttachments.team(player).state();var qualified=new HashSet<UUID>();boolean hsin=false;
        var live=new HashSet<UUID>();
        for(int i=0;i<3;i++){var owner=team.at(i).orElse(null);if(owner!=null&&owner.alive()){live.add(owner.instanceId());if(UnisonRules.canRespond(ruleState(owner))){qualified.add(owner.instanceId());hsin=true;}}}
        unison.retainEquipped(live);
        unison.retainSources(qualified,currentTick);if(!hsin)unison.clearLamps();
        for(int i=0;i<3;i++)team.at(i).filter(CharacterInstance::alive).ifPresent(owner->{
            var state=ruleState(owner);unison.observe(owner.instanceId(),state.timer("unison")>0,owner.character()!=CharacterId.HSIN&&!qualified.isEmpty());
        });
    }
    public void progressionChanged(CharacterInstance role) {
        var state = mechanics.get(role.instanceId()); if (state == null) return;
        commit(role, new MechanicsState(state.characterId(), state.form(), state.mode(), role.chain(), state.nextBasic(), state.resources(), state.timers()));
    }
    private static void message(ServerPlayer player, String message) {
        String display = message.startsWith("cooldown:") ? "技能仍在冷却中"
            : message.contains("energy") ? "共鸣能量不足"
            : message.contains("stamina") ? "耐力不足"
            : message.equals("low_air_only") ? "这个动作只能在地面或低空使用"
            : message.equals("unsupported_input_in_form") ? "当前形态无法使用这个动作"
            : message.matches("[a-z0-9_:]+") ? "当前回路或形态条件尚未满足" : message;
        player.displayClientMessage(Component.literal(display), true);
    }
}
