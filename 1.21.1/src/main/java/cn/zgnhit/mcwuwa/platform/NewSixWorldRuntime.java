package cn.zgnhit.mcwuwa.platform;

import cn.zgnhit.mcwuwa.content.mechanics.MechanicsState;
import cn.zgnhit.mcwuwa.core.CharacterInstance;
import cn.zgnhit.mcwuwa.core.CharacterId;
import cn.zgnhit.mcwuwa.core.EnemyProfile;
import cn.zgnhit.mcwuwa.platform.entity.ConstructEntity;
import cn.zgnhit.mcwuwa.platform.entity.EnemyEvents;
import java.util.*;
import java.util.function.Function;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** One instance belongs to one player's ActionRuntime. No static cross-world
 * target state, no copied base attack and no fake owner-level target gates. */
public final class NewSixWorldRuntime {
    public interface Callbacks {
        void hit(CharacterInstance owner,LivingEntity target,String action);
        void heal(CharacterInstance owner,CharacterInstance recipient,double attackRatio);
        void effect(CharacterInstance owner,LivingEntity target,String effect,int layers);
        void event(CharacterInstance owner,CharacterInstance actor,String id,LivingEntity target,Map<String,Double> values);
        /** Optional full skinned projection supplied by the presentation host. */
        default ConstructEntity projection(ServerPlayer player,CharacterInstance owner,Vec3 position,int ticks){return null;}
    }
    private record Key(UUID owner,UUID target,String id) { }
    private static final class Mark {
        final UUID owner;final LivingEntity target;final String id;final boolean breakOnDamage;long until;int layers;
        Mark(UUID owner,LivingEntity target,String id,long until,int layers,boolean breakOnDamage){this.owner=owner;this.target=target;this.id=id;this.until=until;this.layers=layers;this.breakOnDamage=breakOnDamage;}
    }
    private record Gate(long until,LivingEntity target) { }
    private static final class Field {
        final CharacterInstance caster;final ServerLevel world;
        final UUID owner;final String id;final Vec3 center;long until;final double radius;final int period;long next,energyNext;
        ConstructEntity visual;
        Field(CharacterInstance caster,ServerLevel world,String id,Vec3 center,long now,int ticks,double radius,int period){this.caster=caster;this.world=world;this.owner=caster.instanceId();this.id=id;this.center=center;until=now+ticks;this.radius=radius;this.period=period;next=now+period;energyNext=now+20;}
    }
    private static final class Assist {
        final UUID owner;final ServerLevel world;final Vec3 center;long until;final ConstructEntity projection;
        Assist(UUID owner,ServerLevel world,Vec3 center,long until,ConstructEntity projection){this.owner=owner;this.world=world;this.center=center;this.until=until;this.projection=projection;}
    }
    private final Map<Key,Mark> marks=new HashMap<>();
    private final Map<Key,Gate> gates=new HashMap<>();
    private final Map<String,Field> fields=new HashMap<>();
    private final Map<UUID,Assist> assists=new HashMap<>();
    private boolean resolving;
    public void pauseOneTick(){
        marks.values().forEach(m->m.until=cn.zgnhit.mcwuwa.core.CinematicTimeRules.postpone(m.until,1));
        gates.replaceAll((key,g)->new Gate(cn.zgnhit.mcwuwa.core.CinematicTimeRules.postpone(g.until,1),g.target));
        fields.values().forEach(f->{f.until++;f.next++;f.energyNext++;});assists.values().forEach(a->a.until++);
    }

    public void mark(ServerPlayer player,CharacterInstance owner,LivingEntity target,String id,int ticks,int layers,boolean breakOnDamage) {
        if(!owner.alive()||target.level()!=player.level()||!target.isAlive()||!PlatformRuntime.canAttack(player,target))return;
        if(id.equals("carlotta_variegation")||id.equals("carlotta_iridescence")) {
            if(!EffectRuntime.applyStasis(player,owner,target,Math.max(1,ticks),breakOnDamage))return;
        }
        long until=ticks>0?PlatformRuntime.time(player)+ticks:Long.MAX_VALUE;Key key=new Key(owner.instanceId(),target.getUUID(),id);
        int cap=id.equals("qingxiao_mind_lock")?(owner.chain()>=2?25:15):Integer.MAX_VALUE;
        boolean stacking=id.equals("qingxiao_mind_lock")||id.equals("qingxiao_vast_sword_exposure");
        Mark old=marks.get(key);int count=stacking?Math.min(cap,Math.max(0,layers)+(old==null||old.until<=PlatformRuntime.time(player)?0:old.layers)):Math.max(1,layers);
        marks.put(key,new Mark(owner.instanceId(),target,id,until,count,breakOnDamage));
    }
    public void field(ServerPlayer player,CharacterInstance owner,String id,int ticks,double radius,int period) {
        if(!owner.alive()||!Set.of("denia_erosion","iuno_full_moon").contains(id))return;
        if(ticks<1||ticks>1200||!Double.isFinite(radius)||radius<=0||radius>32||period<1)throw new IllegalArgumentException("field geometry");
        String key=owner.instanceId()+":"+id;
        Field previous=fields.remove(key);if(previous!=null&&previous.visual!=null)previous.visual.discard();
        var field=new Field(owner,player.serverLevel(),id,player.position(),PlatformRuntime.time(player),ticks,radius,period);
        String action=id.equals("iuno_full_moon")?"iuno/full_moon":"denia/erosion_field";
        field.visual=ConstructEntity.spawn(player.serverLevel(),player,owner.instanceId(),"skill_light_"+action.replace("/","__"),
            field.center,ticks,(float)(radius/3),false,"attack");
        fields.put(key,field);
    }
    /** Precise owner-specific view, consumed by RolePassiveEngine target bridges. */
    public Map<String,Integer> targetTimers(CharacterInstance owner,LivingEntity target){return targetTimers(owner,target,target.level().getGameTime());}
    public Map<String,Integer> targetTimers(CharacterInstance owner,LivingEntity target,long now) {
        var values=new HashMap<String,Integer>();
        for(String id:List.of("carlotta_dissociation","carlotta_variegation","lupa_encircle","qingxiao_mind_lock","qingxiao_vast_sword_exposure")) {
            Mark mark=marks.get(new Key(owner.instanceId(),target.getUUID(),id));
            if(mark!=null&&mark.until>now&&target.isAlive())values.put("target_"+id,id.startsWith("qingxiao_")?mark.layers:1);
        }
        return Map.copyOf(values);
    }
    public boolean variegated(CharacterInstance owner,LivingEntity target,long now){return liveMark(owner,target,"carlotta_variegation",now)!=null;}
    public boolean encircled(CharacterInstance owner,LivingEntity target,long now){return liveMark(owner,target,"lupa_encircle",now)!=null;}
    private Mark liveMark(CharacterInstance owner,LivingEntity target,String id,long now){Mark m=marks.get(new Key(owner.instanceId(),target.getUUID(),id));return m!=null&&m.until>now&&m.target.level()==target.level()?m:null;}
    public int captureLocks(ServerPlayer player,CharacterInstance owner,double radius) {
        long now=PlatformRuntime.time(player);int highest=0;
        for(Mark m:marks.values())if(m.owner.equals(owner.instanceId())&&m.id.equals("qingxiao_mind_lock")&&m.until>now&&m.target.isAlive()
            &&m.target.level()==player.level()&&m.target.position().distanceToSqr(player.position())<=radius*radius)highest=Math.max(highest,m.layers);
        return highest;
    }
    private boolean gate(UUID owner,LivingEntity target,String id,long now,long ticks){Key key=new Key(owner,target.getUUID(),id);Gate old=gates.get(key);if(old!=null&&old.until()>now)return false;gates.put(key,new Gate(ticks==Long.MAX_VALUE?Long.MAX_VALUE:now+ticks,target));return true;}
    private static List<CharacterInstance> team(ServerPlayer player){var result=new ArrayList<CharacterInstance>();for(int i=0;i<3;i++)ModAttachments.team(player).state().at(i).filter(CharacterInstance::alive).ifPresent(result::add);return result;}
    private static CharacterInstance active(ServerPlayer player){return ModAttachments.team(player).state().active().filter(CharacterInstance::alive).orElse(null);}

    /** Called after successful damage, before dead targets and their marks are pruned. */
    public void damage(ServerPlayer player,CharacterInstance attacker,LivingEntity target,String action,boolean direct,Function<CharacterInstance,MechanicsState> states,Callbacks callbacks) {
        if(resolving)return;long now=PlatformRuntime.time(player);
        marks.values().removeIf(m->m.target==target&&m.breakOnDamage);
        if(attacker.character()==CharacterId.LUPA&&encircled(attacker,target,now)&&!target.isAlive())callbacks.event(attacker,attacker,"lupa_marked_kill",target,Map.of());
        for(var owner:team(player))if(owner.character()==CharacterId.QINGXIAO) {
            MechanicsState state=states.apply(owner);if(state==null)continue;
            Mark lock=liveMark(owner,target,"qingxiao_mind_lock",now);
            if(!target.isAlive()&&direct&&lock!=null)callbacks.event(owner,attacker,"qingxiao_locked_kill",target,Map.of("layers",(double)lock.layers));
            if(target.isAlive()&&state.timer("qingxiao_consciousness")>0&&gate(owner.instanceId(),target,"consciousness_first_damage",now,Long.MAX_VALUE))
                mark(player,owner,target,"qingxiao_mind_lock",0,state.timer("qingxiao_consciousness"),false);
        }
    }
    /** Per skill instance and actual target. Call after native hit identity is known. */
    public boolean deniaSkillGate(ServerPlayer player,CharacterInstance owner,LivingEntity target,String sourceAction){return gate(owner.instanceId(),target,"denia_skill:"+sourceAction,PlatformRuntime.time(player),40);}
    public boolean deniaOffsetGain(ServerPlayer player,CharacterInstance owner,LivingEntity target){return gate(owner.instanceId(),target,"denia_first_offset",PlatformRuntime.time(player),6000);}
    public boolean deniaPreservingDetonation(ServerPlayer player,CharacterInstance owner,LivingEntity target,java.util.function.BooleanSupplier damage) {
        Key key=new Key(owner.instanceId(),target.getUUID(),"denia_c6_detonation");
        if(gates.containsKey(key)||!damage.getAsBoolean())return false;
        gates.put(key,new Gate(Long.MAX_VALUE,target));return true;
    }
    public void resetDeniaDetonation(ServerPlayer player,CharacterInstance owner,LivingEntity target){
        if(gate(owner.instanceId(),target,"denia_reset_detonation",PlatformRuntime.time(player),40))
            gates.remove(new Key(owner.instanceId(),target.getUUID(),"denia_c6_detonation"));
    }
    public boolean qingxiaoSkillGate(ServerPlayer player,CharacterInstance owner,LivingEntity target,String actionInstance){return gate(owner.instanceId(),target,"qingxiao_skill:"+actionInstance,PlatformRuntime.time(player),1200);}

    public void effectApplied(ServerPlayer player,CharacterInstance actor,LivingEntity target,String effect,Function<CharacterInstance,MechanicsState> states,Callbacks callbacks) {
        long now=PlatformRuntime.time(player);
        for(var owner:team(player)) {
            if(owner.character()==CharacterId.QINGXIAO&&effect.equals("tune_interference")) {
                var tier=EnemyEvents.profile(target).tier();boolean boss=tier==EnemyProfile.Tier.FOUR_COST||tier==EnemyProfile.Tier.STRONG;
                mark(player,owner,target,"qingxiao_mind_lock",0,boss?2:1,false);
            }
            if(owner.character()==CharacterId.QINGXIAO&&effect.equals("tune_offset")&&owner.chain()>=4)
                callbacks.event(owner,actor,"qingxiao_c4_applier",target,Map.of());
            if(owner.character()==CharacterId.DENIA&&(effect.equals("fusion_burst")||effect.equals("tune_offset"))) {
                callbacks.event(owner,actor,"denia_team_effect",target,Map.of("fusion",effect.equals("fusion_burst")?1d:0d));
                MechanicsState state=states.apply(owner);
                if(effect.equals("tune_offset")&&state!=null&&state.mode().equals("strain")&&deniaOffsetGain(player,owner,target)) {
                    var gauge=PlatformRuntime.gauge(target);
                    double maximum=gauge.snapshot(now).offTuneMax();
                    // This is gauge accumulation, never an automatic tune break.
                    // GaugeState retains its post-break accumulation lock.
                    if(maximum>0)gauge.onHit(0,maximum*(owner.chain()>=2?1d:.5),false,now);
                }
            }
        }
    }
    public void tuneBreak(ServerPlayer player,CharacterInstance actor,LivingEntity target,boolean offset,boolean dissonant,Function<CharacterInstance,MechanicsState> states,Callbacks callbacks) {
        if(!offset)return;long now=PlatformRuntime.time(player);
        for(var owner:team(player)) {
            MechanicsState state=states.apply(owner);
            if(owner.character()==CharacterId.QINGXIAO&&state!=null&&state.timer("qingxiao_consciousness")>0&&gate(owner.instanceId(),target,"consciousness_first_break",now,Long.MAX_VALUE))
                callbacks.effect(owner,target,"tune_interference",owner.chain()>=3?2:1);
            if(owner.character()==CharacterId.DENIA&&owner.chain()>=6&&state!=null&&state.mode().equals("strain")
                &&dissonant&&gate(owner.instanceId(),target,"denia_c6_break",now,60))
                callbacks.event(owner,actor,"denia_c6_tune_break",target,Map.of());
        }
    }
    public void shieldGained(ServerPlayer player,CharacterInstance recipient,Callbacks callbacks) {
        long now=PlatformRuntime.time(player);
        for(Field f:fields.values())if(f.id.equals("iuno_full_moon")&&f.until>now&&inside(player,f)) {
            // This player's physical body belongs only to the current role.
            if(recipient!=active(player))continue;
            if(f.caster.alive()&&gate(f.owner,player,"iuno_shield:"+recipient.instanceId(),now,10))callbacks.event(f.caster,recipient,"iuno_field_shield",null,Map.of());
        }
    }
    public void reserveAssist(ServerPlayer player,CharacterInstance owner,int ticks,Callbacks callbacks) {
        Assist old=assists.remove(owner.instanceId());if(old!=null&&old.projection!=null)old.projection.discard();
        var visual=callbacks.projection(player,owner,player.position(),ticks);
        if(visual==null)visual=ConstructEntity.spawn(player.serverLevel(),player,owner.instanceId(),"skill_light_lupa__assist",player.position(),ticks,1,false,"idle");
        assists.put(owner.instanceId(),new Assist(owner.instanceId(),player.serverLevel(),player.position(),PlatformRuntime.time(player)+ticks,visual));
    }
    public void teammateLiberation(ServerPlayer player,CharacterInstance actor,Function<UUID,CharacterInstance> roles,Callbacks callbacks) {
        long now=PlatformRuntime.time(player);
        for(var iterator=assists.values().iterator();iterator.hasNext();) {
            Assist assist=iterator.next();CharacterInstance owner=roles.apply(assist.owner);
            if(owner==null||!owner.alive()||assist.world!=player.level()||assist.until<=now){if(assist.projection!=null)assist.projection.discard();iterator.remove();continue;}
            if(owner==actor)continue;
            LivingEntity nearest=targets(player,assist.center,12).stream().min(Comparator.comparingDouble(t->t.position().distanceToSqr(assist.center))).orElse(null);
            if(nearest!=null){resolving=true;try{callbacks.hit(owner,nearest,"assist");}finally{resolving=false;}}
            callbacks.event(owner,actor,"lupa_assist_consumed",nearest,Map.of());
            if(assist.projection!=null){
                if(nearest!=null){var aim=nearest.position().subtract(assist.projection.position());
                    assist.projection.setYRot((float)(Math.toDegrees(Math.atan2(aim.z,aim.x))-90));}
                assist.projection.finishAfter(16,"attack");
                // Reserve casts have no active-player Running action to own
                // their stroke. Anchor it to the actual assisting body.
                var stroke=SkillModelProfiles.light(player,owner,"lupa/assist",assist.projection.position(),16,false);
                stroke.setYRot(assist.projection.getYRot());
            }
            iterator.remove();
        }
    }
    public void tick(ServerPlayer player,Function<UUID,CharacterInstance> roles,Function<CharacterInstance,MechanicsState> states,Callbacks callbacks) {
        long now=PlatformRuntime.time(player);
        marks.values().removeIf(m->m.until<=now||!m.target.isAlive()||m.target.level()!=player.level()||roles.apply(m.owner)==null||!roles.apply(m.owner).alive());
        gates.entrySet().removeIf(e->e.getValue().until()<=now||!e.getValue().target().isAlive()||e.getValue().target().level()!=player.level()||roles.apply(e.getKey().owner())==null);
        for(var iterator=fields.values().iterator();iterator.hasNext();) {
            Field f=iterator.next();CharacterInstance owner=roles.apply(f.owner);
            if(owner==null||!owner.alive()||f.world!=player.level()||f.until<now){if(f.visual!=null)f.visual.discard();iterator.remove();continue;}
            if(now%10==0)ring(player,f);
            if(f.id.equals("denia_erosion")&&now>=f.next) {
                f.next=now+f.period;EffectRuntime.pullToPoint(player,owner,f.center,f.radius,.16);
                for(var target:targets(player,f.center,f.radius)){resolving=true;try{callbacks.hit(owner,target,"erosion_field");}finally{resolving=false;}}
            }
            if(f.id.equals("iuno_full_moon")) {
                CharacterInstance recipient=inside(player,f)?active(player):null;
                if(now>=f.next) {
                    f.next=now+f.period;
                    if(recipient!=null) {
                        callbacks.heal(owner,recipient,.3238);
                        var stamina=ModAttachments.team(player).state().stamina();stamina.restoreStamina(Math.min(cn.zgnhit.mcwuwa.core.StaminaDodge.MAX_STAMINA,stamina.snapshot().stamina()+20));
                    }
                }
                if(now>=f.energyNext){f.energyNext=now+20;if(owner.chain()>=1&&recipient==owner)callbacks.event(owner,owner,"iuno_c1_field_energy",null,Map.of("amount",1d));}
            }
            // Pay a complete final interval (e.g. 30s / 5s = six pulses)
            // before removing the field. It must not pulse on the next tick.
            if(now>=f.until){if(f.visual!=null)f.visual.discard();iterator.remove();}
        }
        assists.values().removeIf(a->{boolean expired=a.world!=player.level()||a.until<=now||roles.apply(a.owner)==null||!roles.apply(a.owner).alive();if(expired&&a.projection!=null)a.projection.discard();return expired;});
    }
    private static boolean inside(ServerPlayer player,Field f){return f.world==player.level()&&player.position().distanceToSqr(f.center)<=f.radius*f.radius;}
    private static List<LivingEntity> targets(ServerPlayer player,Vec3 center,double radius){return player.level().getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(radius),t->PlatformRuntime.canAttack(player,t)&&t.position().distanceToSqr(center)<=radius*radius);}
    private static void ring(ServerPlayer player,Field f){for(int i=0;i<24;i++){double angle=i*Math.PI/12;player.serverLevel().sendParticles(f.id.equals("iuno_full_moon")?ParticleTypes.END_ROD:ParticleTypes.ENCHANT,f.center.x+Math.cos(angle)*f.radius,f.center.y+.08,f.center.z+Math.sin(angle)*f.radius,1,0,0,0,0);}}
    /** Same cleanup as a dead owner in tick, committed before revival can occur.
     * Target-specific once/interval gates retain their normal lifetime. */
    public void downed(UUID owner){
        marks.keySet().removeIf(key->key.owner().equals(owner));
        fields.values().removeIf(field->{
            if(!field.owner.equals(owner))return false;
            if(field.visual!=null)field.visual.discard();return true;
        });
        var assist=assists.remove(owner);if(assist!=null&&assist.projection!=null)assist.projection.discard();
    }
    public void clear(){for(UUID owner:new HashSet<>(marks.keySet().stream().map(Key::owner).toList()))EffectRuntime.clearOwnedStasis(owner);marks.clear();gates.clear();fields.values().forEach(f->{if(f.visual!=null)f.visual.discard();});fields.clear();assists.values().forEach(a->{if(a.projection!=null)a.projection.discard();});assists.clear();}
}
