package cn.zgnhit.mcwuwa.platform;

import cn.zgnhit.mcwuwa.McWuwa;
import cn.zgnhit.mcwuwa.content.mechanics.*;
import cn.zgnhit.mcwuwa.core.*;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Solo target qualifications, tied to real accepted hits and original weapon instances.
 * Range/dispatch and unbound trace amounts are explicit MC adaptation, not guessed native Buff IDs. */
final class SoloTargetRuntime {
    private final ActionRuntime host;
    private record Trace(int layers,long expires) { }
    private record Sealed(int layers,long expires) { }
    private final Map<LivingEntity,Map<UUID,Trace>> traces=new WeakHashMap<>();
    private final Map<LivingEntity,Map<UUID,Trace>> wingVulnerability=new WeakHashMap<>();
    private final Map<LivingEntity,Map<String,Long>> qualifications=new WeakHashMap<>();
    private final Map<UUID,Sealed> sealedAero=new HashMap<>(),sealedTraces=new HashMap<>();
    private final Map<LivingEntity,Map<UUID,Integer>> stormTargets=new WeakHashMap<>();
    private record Defeat(UUID weapon,LivingEntity enemy) { }
    private final ArrayDeque<Defeat> defeats=new ArrayDeque<>();
    private final Set<LivingEntity> seenDefeats=Collections.newSetFromMap(new WeakHashMap<>());
    SoloTargetRuntime(ActionRuntime host){this.host=host;}
    void pauseOneTick(){
        for(var map:List.of(traces,wingVulnerability))map.values().forEach(values->values.replaceAll((id,t)->new Trace(t.layers,CinematicTimeRules.postpone(t.expires,1))));
        qualifications.values().forEach(values->values.replaceAll((id,t)->CinematicTimeRules.postpone(t,1)));
        for(var map:List.of(sealedAero,sealedTraces))map.replaceAll((id,t)->new Sealed(t.layers,CinematicTimeRules.postpone(t.expires,1)));
    }
    private long now(ServerPlayer p){return PlatformRuntime.time(p);}
    private String key(CharacterInstance role,String suffix){return role.instanceId()+"/"+suffix;}
    private boolean ready(ServerPlayer p,CharacterInstance role,LivingEntity target,String suffix,int ticks) {
        var clocks=qualifications.computeIfAbsent(target,k->new HashMap<>());String key=key(role,suffix);
        if(clocks.getOrDefault(key,0L)>now(p))return false;clocks.put(key,now(p)+ticks);return true;
    }
    private void timer(ServerPlayer p,CharacterInstance role,String key,int ticks) {
        var state=host.ruleState(role);var timers=new HashMap<>(state.timers());if(ticks>0)timers.put(key,ticks);else timers.remove(key);
        host.ruleState(p,role,new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),state.nextBasic(),state.resources(),timers));
    }
    private void resource(ServerPlayer p,CharacterInstance role,String id,double amount){host.ruleState(p,role,new MechanicsEngine().grantResource(host.ruleState(role),id,amount));}
    private void mark(ServerPlayer p,CharacterInstance role,LivingEntity target,String id,int layers,int duration){host.ruleMark(p,role,target,id,layers,duration);}
    private int trace(ServerPlayer p,CharacterInstance role,LivingEntity target) {
        var value=traces.getOrDefault(target,Map.of()).get(role.instanceId());return value==null||value.expires()<=now(p)?0:value.layers();
    }
    private void addTrace(ServerPlayer p,CharacterInstance role,LivingEntity target,int layers) {
        int cap=AemeathTargetRules.traceCap(role.chain(),ModAttachments.team(p).state().inCombat(),p.distanceToSqr(target));
        traces.computeIfAbsent(target,k->new HashMap<>()).put(role.instanceId(),new Trace(Math.clamp(trace(p,role,target)+layers,0,cap),now(p)+600));
    }
    void cast(ServerPlayer p,CharacterInstance role,String action) {
        if(role.character()==CharacterId.AEMEATH&&role.chain()>=6&&action.contains("/wing_ensemble"))
            for(var target:p.level().getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().inflate(6),e->PlatformRuntime.canAttack(p,e)&&p.distanceToSqr(e)<=36&&p.hasLineOfSight(e)))addTrace(p,role,target,10);
    }
    void nearbyDefeat(ServerPlayer p,LivingEntity enemy,boolean ownAttack) {
        var team=ModAttachments.team(p).state();
        if(!team.canAct()||!team.wuwaMode()||(!team.inCombat()&&!ownAttack)||p.distanceToSqr(enemy)>36||seenDefeats.contains(enemy))return;
        for(int slot=0;slot<3;slot++)team.at(slot).filter(role->role.alive()&&role.character()==CharacterId.AEMEATH
            &&role.chain()>=2&&host.ruleState(role).mode().equals("fusion")).ifPresent(role->{
                seenDefeats.add(enemy);if(defeats.size()<128)defeats.addLast(new Defeat(role.instanceId(),enemy));
            });
    }
    void tick(ServerPlayer p,CharacterInstance role) {
        prune(p);
        // Queue prevents synchronous death-event damage recursion. At most64 actual defeats per tick.
        for(int i=0;i<64&&!defeats.isEmpty();i++) {
            var event=defeats.removeFirst();var team=ModAttachments.team(p).state();
            for(int slot=0;slot<3;slot++)team.at(slot).filter(owner->owner.instanceId().equals(event.weapon())&&owner.alive()
                &&host.ruleState(owner).mode().equals("fusion")&&event.enemy().level()==p.level()).ifPresent(owner->EffectRuntime.defeatedFusionBurst(p,owner,event.enemy()));
        }
        var team=ModAttachments.team(p).state();if(!team.inCombat())return;
        // The source follows the currently deployed teammate, including when Aemeath is in reserve.
        for(int slot=0;slot<3;slot++)team.at(slot).filter(owner->owner.alive()&&owner.character()==CharacterId.AEMEATH
            &&host.ruleState(owner).mode().equals("fusion")).ifPresent(owner->fusionAura(p,owner));
    }
    private void fusionAura(ServerPlayer p,CharacterInstance role) {
        for(var target:p.level().getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().inflate(8),e->PlatformRuntime.canAttack(p,e)&&p.distanceToSqr(e)<=64&&p.hasLineOfSight(e))) {
            int layers=EffectRuntime.layers(target,"fusion_burst");
            if(layers==0)mark(p,role,target,"fusion_burst",1,0);
            else if(layers>5)EffectRuntime.fusionAtCap(p,role,target,true,1);
        }
    }
    void applied(ServerPlayer p,CharacterInstance role,LivingEntity target,String effect) {
        if(role.character()==CharacterId.AEMEATH) {
            var state=host.ruleState(role);boolean matching=AemeathTargetRules.collectsTrace(state.mode(),effect);
            if(matching)addTrace(p,role,target,(effect.equals("rupture_response")?10:1)*(role.chain()>=6?2:1));
        }
    }
    void markAttached(ServerPlayer p,CharacterInstance role,LivingEntity target,String id) {
        if(id.equals("mornye_observation")&&role.character()==CharacterId.MORNYE&&role.chain()>=1)
            mark(p,role,target,"mornye_interference",1,400);
        if(id.equals("chisa_void_scar")&&role.character()==CharacterId.CHISA&&role.chain()>=1&&ready(p,role,target,"initial_scar",Integer.MAX_VALUE)) {
            var source=new EffectSource(p.getUUID(),role.instanceId(),role.character(),UUID.randomUUID(),UUID.randomUUID());
            PlatformRuntime.effectHit(p,source,target,Element.HAVOC,61803,"fixed/chisa_initial");
        }
    }
    void killed(ServerPlayer p,CharacterInstance role,LivingEntity target,boolean direct) {
        var team=ModAttachments.team(p).state();
        for(int slot=0;slot<3;slot++)team.at(slot).filter(CharacterInstance::alive).ifPresent(owner->{
            if(owner.character()==CharacterId.CHISA&&EffectRuntime.hasOwnedEffect(target,p.getUUID(),owner.instanceId(),"chisa_void_scar")) {
                if(owner.cooldown("chisa_kill_reset")==0){timer(p,owner,"skill",0);owner.setCooldown("chisa_kill_reset",60);}
                if(host.hasChisaThread(role,owner.instanceId(),now(p)))timer(p,owner,"chisa_vision",60);
            }
            if(owner.character()==CharacterId.MORNYE&&(EffectRuntime.hasOwnedEffect(target,p.getUUID(),owner.instanceId(),"mornye_observation")
                ||EffectRuntime.hasOwnedEffect(target,p.getUUID(),owner.instanceId(),"mornye_interference")))timer(p,owner,"mornye_vision",60);
        });
        if(role.character()==CharacterId.CARTETHYIA&&role.chain()>=1&&direct&&role.cooldown("sealed_aero")==0)store(sealedAero,role,EffectRuntime.layers(target,"aero_erosion"),now(p));
        if(role.character()==CharacterId.AEMEATH) {
            if(role.chain()>=1&&direct&&role.cooldown("sealed_traces")==0)store(sealedTraces,role,trace(p,role,target),now(p));
            if(role.chain()>=5&&direct)resource(p,role,"radiance",600);
        }
    }
    private static void store(Map<UUID,Sealed> values,CharacterInstance role,int layers,long now) {
        var old=values.get(role.instanceId());int kept=old!=null&&old.expires()>now?old.layers():0;
        if(layers>0)values.put(role.instanceId(),new Sealed(Math.max(kept,layers),now+200));
    }
    private int take(Map<UUID,Sealed> values,ServerPlayer p,CharacterInstance role,String cooldown) {
        var saved=values.get(role.instanceId());if(saved==null||saved.expires()<=now(p)||role.cooldown(cooldown)>0)return 0;
        values.remove(role.instanceId());role.setCooldown(cooldown,20);return saved.layers();
    }
    void directHit(ServerPlayer p,CharacterInstance role,LivingEntity target,String action,PlatformRuntime.HitOrigin origin) {
        // Kill credit stores the layers before the confirmed-hit queue drains.
        if(!target.isAlive())return;
        damage(p,role,target);
        if(action.startsWith("echo/")||origin!=PlatformRuntime.HitOrigin.DIRECT_ACTIVE&&origin!=PlatformRuntime.HitOrigin.DIRECT_PROJECTILE)return;
        var state=host.ruleState(role);
        if(role.character()==CharacterId.CARTETHYIA){int layers=take(sealedAero,p,role,"sealed_aero");if(layers>0)mark(p,role,target,"aero_erosion",layers,0);}
        if(role.character()==CharacterId.AEMEATH){int layers=take(sealedTraces,p,role,"sealed_traces");if(layers>0)addTrace(p,role,target,layers);}
    }
    void damage(ServerPlayer p,CharacterInstance actor,LivingEntity target) {
        if(!target.isAlive())return;
        var team=ModAttachments.team(p).state();
        for(int slot=0;slot<3;slot++)team.at(slot).filter(owner->owner.alive()&&owner.character()==CharacterId.CHISA
            &&host.ruleState(owner).timer("chisa_vision")>0&&host.hasChisaThread(actor,owner.instanceId(),now(p)))
            .ifPresent(owner->mark(p,owner,target,"chisa_void_scar",1,600));
        for(int slot=0;slot<3;slot++)team.at(slot).filter(owner->owner.alive()&&owner.character()==CharacterId.MORNYE&&host.ruleState(owner).timer("mornye_vision")>0)
            .ifPresent(owner->mark(p,owner,target,"mornye_observation",1,600));
    }
    void electroAura(ServerPlayer p,CharacterInstance owner,LivingEntity target) {
        var state=host.ruleState(owner);int layers=EffectRuntime.layers(target,"electro_flare");
        var caps=stormTargets.computeIfAbsent(target,k->new HashMap<>());
        int cap=EffectRuntime.effectiveCap(target,"electro_flare",now(p));
        Integer previous=caps.get(owner.instanceId());
        boolean applied=layers==0&&state.timer("hsin_storm_charge")>=10;
        boolean capIncreased=previous!=null&&cap>previous;
        if(applied||previous!=null)caps.put(owner.instanceId(),cap);
        int wanted=applied||capIncreased?Math.min(16,cap):1;
        if(layers<wanted)mark(p,owner,target,"electro_flare",wanted-layers,0);
    }
    private void prune(ServerPlayer p) {
        long time=now(p);var team=ModAttachments.team(p).state();Set<UUID> live=new HashSet<>();
        for(int slot=0;slot<3;slot++)team.at(slot).filter(CharacterInstance::alive).ifPresent(owner->live.add(owner.instanceId()));
        traces.entrySet().removeIf(e->{
            if(e.getKey().isRemoved()||!e.getKey().isAlive()||e.getKey().level()!=p.level())return true;
            e.getValue().entrySet().removeIf(v->v.getValue().expires()<=time||!live.contains(v.getKey()));return e.getValue().isEmpty();
        });
        wingVulnerability.entrySet().removeIf(e->{
            if(e.getKey().isRemoved()||!e.getKey().isAlive()||e.getKey().level()!=p.level())return true;
            e.getValue().entrySet().removeIf(v->v.getValue().expires()<=time||!live.contains(v.getKey()));return e.getValue().isEmpty();
        });
        qualifications.entrySet().removeIf(e->{
            if(e.getKey().isRemoved()||!e.getKey().isAlive()||e.getKey().level()!=p.level())return true;
            e.getValue().entrySet().removeIf(v->v.getValue()<=time||live.stream().noneMatch(id->v.getKey().startsWith(id+"/")));return e.getValue().isEmpty();
        });
        sealedAero.entrySet().removeIf(e->e.getValue().expires()<=time||!live.contains(e.getKey()));
        sealedTraces.entrySet().removeIf(e->e.getValue().expires()<=time||!live.contains(e.getKey()));
        stormTargets.entrySet().removeIf(e->{
            if(e.getKey().isRemoved()||!e.getKey().isAlive()||e.getKey().level()!=p.level()||p.distanceToSqr(e.getKey())>64)return true;
            e.getValue().keySet().removeIf(id->{for(int slot=0;slot<3;slot++) {
                var owner=team.at(slot).orElse(null);
                if(owner!=null&&owner.instanceId().equals(id)&&owner.alive()) {
                    var state=host.ruleState(owner);return !state.mode().equals("electro")||state.timer("heart_state")==0;
                }
            }return true;});return e.getValue().isEmpty();
        });
    }
    void timelineHit(ServerPlayer p,CharacterInstance role,LivingEntity target,String action,boolean first,boolean last,double realm) {
        if(!target.isAlive())return;var state=host.ruleState(role);
        if(first&&role.character()==CharacterId.YANGYANG_XUANLING&&action.equals("yangyang_xuanling/liberation"))
            mark(p,role,target,"havoc_bane",EffectRuntime.effectiveCap(target,"havoc_bane",now(p)),0);
        if(role.character()==CharacterId.CHISA&&state.timer("chisa_scar_window")>0)mark(p,role,target,"chisa_void_scar",1,600);
        if(role.character()==CharacterId.CHISA&&action.equals("chisa/skill_saw_hold"))EffectRuntime.pullArea(p,role,target,6,.18);
        if(role.character()==CharacterId.HSIN&&state.mode().equals("electro")) {
            if(first) {
                int layers=HsinTargetRules.electroLayers(action);
                if(layers>0)mark(p,role,target,"electro_flare",layers,0);
                if(state.timer("heart_state")>0&&EffectRuntime.layers(target,"electro_flare")==0)mark(p,role,target,"electro_flare",1,0);
            }
            if(last&&HsinTargetRules.spendsRealm(action)&&realm>0)EffectRuntime.detonate(p,role,target,"electro_flare",false,false,(role.chain()>=1?.42:.35)*realm);
            if(last&&action.equals("hsin/liberation_palaces")&&role.chain()>=3)EffectRuntime.detonate(p,role,target,"electro_flare",false,false,15);
        }
        if(first&&role.character()==CharacterId.CARTETHYIA) {
            if(state.timer("cartethyia_c2_settle")>0){
                timer(p,role,"cartethyia_c2_settle",0);
                for(var near:p.level().getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().inflate(6),e->PlatformRuntime.canAttack(p,e)&&p.distanceToSqr(e)<=36&&p.hasLineOfSight(e))) {
                    mark(p,role,near,"aero_erosion",3,0);EffectRuntime.detonate(p,role,near,"aero_erosion",false,false,1);
                }
            }
            if(List.of("cartethyia/fleurdelys_basic_05","cartethyia/fleurdelys_air_02","cartethyia/fleurdelys_skill_followup").contains(action))EffectRuntime.detonate(p,role,target,"aero_erosion",false,true,1);
        }
        if(first&&role.character()==CharacterId.AEMEATH) {
            boolean fusion=state.mode().equals("fusion");
            if(AemeathTargetRules.appliesModeMark(action)) {
                if(ready(p,role,target,action,60))mark(p,role,target,fusion?"fusion_burst":"rupture_shifting",1,0);
            }
        }
    }
    /** One invocation per cast; returned count is actual accepted rupture damage, not a visual counter. */
    int wingBurst(ServerPlayer p,CharacterInstance role,List<LivingEntity> candidates,boolean star) {
        return wingBurst(p,role,candidates,star,false);
    }
    int wingBurst(ServerPlayer p,CharacterInstance role,List<LivingEntity> candidates,boolean star,boolean preserveTraces) {
        boolean fusion=host.ruleState(role).mode().equals("fusion");int accepted=0;
        // Snapshot and consume each target's old trace once, before any pulse can produce new events.
        var snapshots=new LinkedHashMap<LivingEntity,Integer>();
        for(var target:candidates) {
            if(!PlatformRuntime.canAttack(p,target))continue;
            snapshots.putIfAbsent(target,trace(p,role,target));
        }
        if(!preserveTraces)snapshots.keySet().forEach(target->traces.getOrDefault(target,new HashMap<>()).remove(role.instanceId()));
        if(fusion)for(var entry:snapshots.entrySet()) {
            var target=entry.getKey();int layers=entry.getValue();
            double amp=1+layers*(role.chain()>=2?.15:.10)+(star?(role.chain()>=2?4:2):0);
            if(layers>0)EffectRuntime.fusionAtCap(p,role,target,false,amp);
        }else {
            var source=new EffectSource(p.getUUID(),role.instanceId(),role.character(),UUID.randomUUID(),UUID.randomUUID());
            for(int pulse=0;pulse<(star?10:5);pulse++) {
                var alive=snapshots.keySet().stream().filter(target->PlatformRuntime.canAttack(p,target)).toList();
                if(alive.isEmpty())break;
                var target=alive.get(p.getRandom().nextInt(alive.size()));
                if(PlatformRuntime.effectHit(p,source,target,Element.FUSION,
                    GlobalCombatRules.tuneDamage(host.tuneBreakAmplification(p,role))*(1+.04*snapshots.get(target)),"tune/aemeath_wing")) {
                    accepted++;wingHit(p,role,target);
                }
            }
        }
        return accepted;
    }
    double anomalyFactor(ServerPlayer p,CharacterInstance role,LivingEntity target,Element element,String cause) {
        double factor=1;var state=host.ruleState(role);
        if(role.character()==CharacterId.AEMEATH&&cause.equals("tune/aemeath_wing"))factor*=wingAmplification(p,role,target);
        if(role.character()==CharacterId.AEMEATH&&role.chain()>=6&&state.mode().equals("rupture")
            &&RuptureDamageRules.isRuptureDamage(cause))factor*=p.getRandom().nextDouble()<.8?2.75:1;
        if(cause.startsWith("effect/fusion_burst")&&ModAttachments.team(p).state().inCombat()&&p.distanceToSqr(target)<=64) {
            var team=ModAttachments.team(p).state();
            for(int slot=0;slot<3;slot++)if(team.at(slot).filter(owner->owner.alive()&&owner.character()==CharacterId.AEMEATH&&owner.chain()>=6&&host.ruleState(owner).mode().equals("fusion")).isPresent()) {
                factor*=p.getRandom().nextDouble()<.8?2.75:1;break;
            }
        }
        if(cause.startsWith("effect/electro_")&&ModAttachments.team(p).state().inCombat()&&p.distanceToSqr(target)<=64) {
            var owners=ModAttachments.team(p).state();
            for(int slot=0;slot<3;slot++)if(owners.at(slot).filter(owner->owner.character()==CharacterId.HSIN&&cn.zgnhit.mcwuwa.content.mechanics.AnomalyCriticalRules.electroAura(owners.inCombat(),p.distanceToSqr(target),owner.alive(),owner.chain(),host.ruleState(owner).mode(),cause)).isPresent()) {
                factor*=p.getRandom().nextDouble()<.8?2.3:1;break;
            }
        }
        if(element==Element.GLACIO&&cause.startsWith("effect/glacio_frost"))factor*=RolePassiveRuntime.frostAmplification(p,target);
        return factor;
    }
    /** C6 explicitly deepens anomaly damage; keep it out of independent final/critical factors. */
    double anomalyAmplification(ServerPlayer p,LivingEntity target,String cause) {
        if(!cause.startsWith("effect/"))return 0;
        var team=ModAttachments.team(p).state();
        for(int slot=0;slot<3;slot++)if(team.at(slot).filter(r->r.alive()&&r.character()==CharacterId.CHISA&&r.chain()>=6).isPresent()) {
            var owner=team.at(slot).orElseThrow();
            if(EffectRuntime.hasOwnedEffect(target,p.getUUID(),owner.instanceId(),"chisa_void_scar"))return .30;
        }
        return 0;
    }
    int traceLayers(ServerPlayer player,CharacterInstance owner,LivingEntity target){return trace(player,owner,target);}
    void wingHit(ServerPlayer p,CharacterInstance role,LivingEntity target){
        if(role.character()!=CharacterId.AEMEATH||role.chain()<2||!host.ruleState(role).mode().equals("rupture"))return;
        var values=wingVulnerability.computeIfAbsent(target,key->new HashMap<>());var old=values.get(role.instanceId());
        values.put(role.instanceId(),new Trace(Math.min(5,old!=null&&old.expires()>now(p)?old.layers()+1:1),now(p)+20));
    }
    double wingAmplification(ServerPlayer p,CharacterInstance role,LivingEntity target){
        var value=wingVulnerability.getOrDefault(target,Map.of()).get(role.instanceId());
        return value==null||value.expires()<=now(p)?1:1+.2*value.layers();
    }
    void modeChanged(CharacterInstance role){
        UUID id=role.instanceId();traces.values().forEach(values->values.remove(id));sealedTraces.remove(id);
        wingVulnerability.values().forEach(values->values.remove(id));
        stormTargets.values().forEach(values->values.remove(id));defeats.removeIf(event->event.weapon().equals(id));
        qualifications.values().forEach(values->values.keySet().removeIf(key->key.startsWith(id+"/")));
    }
    void downed(CharacterInstance role){
        modeChanged(role);UUID id=role.instanceId();sealedAero.remove(id);
        qualifications.values().forEach(values->values.keySet().removeIf(key->key.startsWith(id+"/")));
    }
    void clear(){traces.clear();wingVulnerability.clear();qualifications.clear();sealedAero.clear();sealedTraces.clear();stormTargets.clear();defeats.clear();seenDefeats.clear();}
}
