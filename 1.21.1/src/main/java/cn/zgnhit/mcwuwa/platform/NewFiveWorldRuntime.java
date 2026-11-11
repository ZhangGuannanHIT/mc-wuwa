package cn.zgnhit.mcwuwa.platform;

import cn.zgnhit.mcwuwa.content.mechanics.MechanicsState;
import cn.zgnhit.mcwuwa.core.CharacterInstance;
import cn.zgnhit.mcwuwa.core.ZhezhiCraneLedger;
import cn.zgnhit.mcwuwa.core.LucillaPhotoLedger;
import cn.zgnhit.mcwuwa.core.ZaniEmberLedger;
import cn.zgnhit.mcwuwa.core.ZaniBlazeGauge;
import cn.zgnhit.mcwuwa.core.AugustaRealmLedger;
import cn.zgnhit.mcwuwa.platform.entity.ConstructEntity;
import java.util.*;
import java.util.function.Function;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Per-player, per-weapon world ownership for the new-role crane mechanics.
 * Selection radius (12), attack search (24), image offsets and dash duration
 * are Minecraft geometry. Original image/insight/ink lifetimes live in the ledger. */
public final class NewFiveWorldRuntime {
    /** Action is the local suffix; the production sink adds the owner's role prefix. */
    public interface Hits { void hit(CharacterInstance owner,LivingEntity target,String action); }
    private static final class Cranes {
        final ServerLevel world;final ZhezhiCraneLedger ledger=new ZhezhiCraneLedger();
        ZhezhiCraneLedger.Anchor selected;LivingEntity target;Vec3 lastTarget;
        final Map<ZhezhiCraneLedger.Slot,ConstructEntity> visuals=new EnumMap<>(ZhezhiCraneLedger.Slot.class);
        Cranes(ServerLevel world){this.world=world;}
    }
    private record Dash(UUID owner,ServerLevel world,Vec3 destination,long until) { }
    private record ExtraCrane(UUID owner,ServerLevel world,Vec3 origin,long at) { }
    private final Map<UUID,Cranes> owners=new HashMap<>();
    private record Photos(ServerLevel world,int chain,LucillaPhotoLedger ledger){}
    private final Map<UUID,Photos> photos=new HashMap<>();
    private record PhotoAura(ServerLevel world,String mode,long until,double amount){}
    private final Map<UUID,Map<String,PhotoAura>> photoAuras=new HashMap<>();
    private record Blaze(ServerLevel world,ZaniEmberLedger targets,ZaniBlazeGauge gauge){}
    private final Map<UUID,Blaze> blazes=new HashMap<>();
    private final Map<UUID,ZaniBlazeGauge.HeavyCast> zaniHeavyCasts=new HashMap<>();
    private final Map<UUID,Double> zaniFinaleBonuses=new HashMap<>();
    private final Map<UUID,Long> zaniOutroUntil=new HashMap<>();
    private final Map<UUID,ConstructEntity> zaniGuards=new HashMap<>();
    private record Realm(ServerLevel world,int chain,AugustaRealmLedger ledger){}
    private final Map<UUID,Realm> realms=new HashMap<>();
    private final Map<UUID,ConstructEntity> realmVisuals=new HashMap<>();
    private static final class Rain {
        final ServerLevel world;final cn.zgnhit.mcwuwa.core.SuomingContractLedger ledger;
        long until;LivingEntity target;
        Rain(ServerLevel world,int chain,long now){
            this.world=world;this.until=now+160;this.ledger=new cn.zgnhit.mcwuwa.core.SuomingContractLedger(chain);
            ledger.gainUnison(now);ledger.outro(0,now);
        }
    }
    private final Map<UUID,Rain> rains=new HashMap<>();
    public void pauseOneTick(){
        owners.values().forEach(c->{c.ledger.pauseOneTick();if(c.selected!=null){var a=c.selected;c.selected=new ZhezhiCraneLedger.Anchor(a.slot(),a.dimension(),a.x(),a.y(),a.z(),a.airborne(),a.expiresAt()+1);}});
        photos.values().forEach(p->p.ledger.pauseOneTick());
        photoAuras.values().forEach(map->map.replaceAll((id,a)->new PhotoAura(a.world,a.mode,a.until+1,a.amount)));
        blazes.values().forEach(b->{b.targets.pauseOneTick();b.gauge.pauseOneTick();});
        realms.values().forEach(r->r.ledger.pauseOneTick());
        rains.values().forEach(r->{r.until++;r.ledger.pauseOneTick();});
        zaniOutroUntil.replaceAll((id,t)->cn.zgnhit.mcwuwa.core.CinematicTimeRules.postpone(t,1));
        extraCranes.replaceAll(c->new ExtraCrane(c.owner,c.world,c.origin,c.at+1));
        if(dash!=null)dash=new Dash(dash.owner,dash.world,dash.destination,dash.until+1);
    }
    public void suomingRain(ServerPlayer player,CharacterInstance owner){
        rains.put(owner.instanceId(),new Rain(player.serverLevel(),owner.chain(),PlatformRuntime.time(player)));
    }
    private Realm realm(ServerPlayer player,CharacterInstance owner){
        var value=realms.get(owner.instanceId());
        if(value==null||value.world()!=player.level()||value.chain()!=owner.chain()){
            value=new Realm(player.serverLevel(),owner.chain(),new AugustaRealmLedger(owner.chain()));realms.put(owner.instanceId(),value);
        }
        return value;
    }
    public boolean augustaProc(ServerPlayer player,CharacterInstance owner,String id){
        if(!Set.of("augusta_realm_begin","augusta_finale","augusta_majesty","augusta_intro","augusta_heavy_c6","augusta_sun").contains(id))return false;
        var ledger=realm(player,owner).ledger();long now=PlatformRuntime.time(player);
        switch(id){
            case "augusta_realm_begin" -> {
                ledger.enter(now);
                var previous=realmVisuals.remove(owner.instanceId());if(previous!=null)previous.discard();
                // The30s realm outlives the7s restricted phase and its finisher.
                // Ring geometry/radius are a local MC presentation, not hit bounds.
                realmVisuals.put(owner.instanceId(),ConstructEntity.spawn(player.serverLevel(),player,owner.instanceId(),
                    "skill_light_augusta__realm_domain",player.position(),600,1,false,"idle"));
            }
            case "augusta_finale" -> ledger.manualFinale(now,true);
            case "augusta_majesty" -> ledger.gainMajesty();
            case "augusta_intro" -> ledger.intro();
            case "augusta_sun" -> ledger.sunAttack(now);
            case "augusta_heavy_c6" -> ledger.sixthChainHeavy(now);
        }
        return true;
    }
    public AugustaRealmLedger.Shield augustaDamageShield(ServerPlayer player,CharacterInstance owner,double maxHealth){
        return owner.character().sourceId()==1306?realm(player,owner).ledger().damageShield(maxHealth,PlatformRuntime.time(player)):null;
    }
    public record RealmShield(UUID owner,double amount,int ticks){}
    public List<RealmShield> augustaIntroShields(ServerPlayer player,Function<UUID,Double> maxHealth){
        long now=PlatformRuntime.time(player);var result=new ArrayList<RealmShield>();
        for(var entry:realms.entrySet())if(entry.getValue().world()==player.level()){
            var shield=entry.getValue().ledger().introShield(maxHealth.apply(entry.getKey()),now);
            if(shield!=null)result.add(new RealmShield(entry.getKey(),shield.amount(),shield.ticks()));
        }
        return List.copyOf(result);
    }
    public boolean augustaInteract(ServerPlayer player,CharacterInstance owner){
        var value=realms.get(owner.instanceId());if(value==null||value.world()!=player.level())return false;
        boolean left=value.ledger().interact()==AugustaRealmLedger.Exit.INTERACTION;
        if(left)EffectRuntime.clearStasisGroup(owner.instanceId(),"augusta_realm_stasis");return left;
    }
    public void augustaAllyOutro(ServerPlayer player,UUID owner){
        var value=realms.get(owner);if(value!=null&&value.world()==player.level())value.ledger().allyOutro();
    }
    public Map<String,Double> augustaBonuses(ServerPlayer player,CharacterInstance owner,double totalCritChance){
        if(owner.character().sourceId()!=1306)return Map.of();
        var ledger=realm(player,owner).ledger();
        return Map.of("electro_bonus",ledger.electroBonus(),"crit_rate",ledger.critChanceBonus(),
            "crit_damage",ledger.critDamageBonus(Math.max(0,totalCritChance+ledger.critChanceBonus())));
    }
    private Blaze blaze(ServerPlayer player,CharacterInstance owner){
        var value=blazes.get(owner.instanceId());
        if(value==null||value.world()!=player.level()){
            value=new Blaze(player.serverLevel(),new ZaniEmberLedger(),new ZaniBlazeGauge());blazes.put(owner.instanceId(),value);
        }
        return value;
    }
    /** Creative fill changes the authoritative gauge without erasing cast/spend history. */
    public MechanicsState rechargeForCreative(ServerPlayer player,CharacterInstance owner,MechanicsState state){
        if(!state.characterId().equals("zani"))return state;
        var gauge=blaze(player,owner).gauge();long now=PlatformRuntime.time(player);
        gauge.gain(Math.max(0,gauge.cap(now)-gauge.ember(now)),now);
        return prepare(player,owner,state);
    }
    /** Called after actual Frazzle settlement; returns the capped owning gauge,
     * never awards five energy again for visual refreshes or damage callbacks. */
    public double convertFrazzle(ServerPlayer player,CharacterInstance owner,LivingEntity target,int consumed){
        var value=blaze(player,owner);long now=PlatformRuntime.time(player);
        var conversion=value.targets().convert(target.getUUID(),consumed,now);
        value.gauge().gain(conversion.heliacalEmberGain(),now);return value.gauge().ember(now);
    }
    public int emberStacks(ServerPlayer player,LivingEntity target){
        long now=PlatformRuntime.time(player);
        return blazes.values().stream().filter(b->b.world()==player.level())
            .mapToInt(b->b.targets().stacks(target.getUUID(),now)).max().orElse(0);
    }
    public boolean zaniProc(ServerPlayer player,CharacterInstance owner,String id,double amount){
        if(!id.startsWith("zani_")||id.equals("zani_outro")||id.equals("zani_suppression"))return false;
        var value=blaze(player,owner);long now=PlatformRuntime.time(player);
        switch(id){
            case "zani_enter" -> value.gauge().enter(owner.chain(),now);
            case "zani_finale" -> {
                zaniFinaleBonuses.put(owner.instanceId(),value.gauge().thirdChainFinalHitBonus(now));
                value.gauge().leave(now);zaniHeavyCasts.remove(owner.instanceId());
            }
            case "zani_expire" -> {value.gauge().leave(now);zaniHeavyCasts.remove(owner.instanceId());}
            case "zani_guard_heavy" -> value.gauge().cancelHeavyChain();
            case "zani_night_resume" -> { /* continue the existing cast/spend snapshot */ }
            case "zani_guard_open" -> {
                var old=zaniGuards.remove(owner.instanceId());if(old!=null)old.discard();
                zaniGuards.put(owner.instanceId(),ConstructEntity.spawn(player.serverLevel(),player,owner.instanceId(),
                    "skill_light_zani__guard",player.position(),30,1,true,"idle"));
            }
            case "zani_guard_close" -> {var old=zaniGuards.remove(owner.instanceId());if(old!=null)old.discard();}
            case "zani_heavy_dawn","zani_heavy_day","zani_heavy_flash","zani_heavy_night" -> {
                var prior=zaniHeavyCasts.remove(owner.instanceId());if(prior!=null)value.gauge().finishHeavy(prior);
                var action=ZaniBlazeGauge.Heavy.valueOf(id.substring("zani_heavy_".length()).toUpperCase(java.util.Locale.ROOT));
                var cast=action==ZaniBlazeGauge.Heavy.NIGHT&&value.gauge().nextHeavy(now)!=action
                    ?value.gauge().preparedNight(now):value.gauge().castHeavy(action,amount,now);
                zaniHeavyCasts.put(owner.instanceId(),cast);
            }
            default -> {return false;}
        }
        return true;
    }
    public void zaniOutro(ServerPlayer player,CharacterInstance owner){zaniOutroUntil.put(owner.instanceId(),PlatformRuntime.time(player)+400);}
    public double zaniOutroMultiplier(ServerPlayer player,CharacterInstance owner,LivingEntity target){
        return 1.5*(1+.1*blaze(player,owner).targets().stacks(target.getUUID(),PlatformRuntime.time(player)));
    }
    public void zaniOutroConsumed(ServerPlayer player,CharacterInstance owner,LivingEntity target){
        blaze(player,owner).targets().consumeOutro(target.getUUID(),PlatformRuntime.time(player));
    }
    public double zaniOutroAmplification(ServerPlayer player,CharacterInstance actor,LivingEntity target,String element){
        if(target==null||!element.equals("spectro"))return 0;
        long now=PlatformRuntime.time(player);
        return zaniOutroUntil.entrySet().stream().anyMatch(e->e.getValue()>now&&!e.getKey().equals(actor.instanceId())
            &&blazes.containsKey(e.getKey())&&blazes.get(e.getKey()).world()==player.level()
            &&blazes.get(e.getKey()).targets().stacks(target.getUUID(),now)>0)?.20:0;
    }
    public boolean zaniLethalProtection(ServerPlayer player,CharacterInstance owner){
        var value=blazes.get(owner.instanceId());
        return value!=null&&value.world()==player.level()&&value.gauge().lethalProtection(PlatformRuntime.time(player));
    }
    public double zaniNightBonus(CharacterInstance owner,String action,boolean finalHit,long now){
        if(!finalHit||!Set.of("zani/heavy_night","zani/heavy_night_last").contains(action))return 0;
        var value=blazes.get(owner.instanceId());var cast=zaniHeavyCasts.get(owner.instanceId());
        // MC packet allocation: source per-Ember total is added to the second hit
        // exactly once per victim, not to both portions of the same heavy.
        return value==null||cast==null?0:value.gauge().nightDamageSpend(cast,now)*.0995;
    }
    public double zaniFinaleBonus(CharacterInstance owner,String action,boolean finalHit){
        return finalHit&&action.equals("zani/finale")?zaniFinaleBonuses.getOrDefault(owner.instanceId(),0d):0;
    }
    public void completed(ServerPlayer player,CharacterInstance owner,String action){
        var domain=realms.get(owner.instanceId());
        if(domain!=null&&action.equals("augusta/realm_finale"))domain.ledger().finishFinale();
        var value=blazes.get(owner.instanceId());if(value==null)return;
        if(action.equals("zani/finale"))zaniFinaleBonuses.remove(owner.instanceId());
        if(action.startsWith("zani/heavy_")){
            var cast=zaniHeavyCasts.remove(owner.instanceId());if(cast!=null)value.gauge().finishHeavy(cast);
        }
    }
    /** Source1507 skill: gain after the final attack is cast, even on a miss.
     * Recovery completion is later and may be cancelled; the caller deduplicates
     * this scheduled launch on its actual Running instance. */
    public boolean finalGroupLaunched(ServerPlayer player,CharacterInstance owner,String action){
        if(owner.character().sourceId()!=1507||!Set.of("zani/suppression","zani/breach_counter").contains(action))return false;
        blaze(player,owner).gauge().gain(10,PlatformRuntime.time(player));return true;
    }
    public void suppressionHit(ServerPlayer player,CharacterInstance owner,LivingEntity target){
        blaze(player,owner).targets().applySuppression(target.getUUID(),PlatformRuntime.time(player));
    }
    private final List<ExtraCrane> extraCranes=new ArrayList<>();
    private Dash dash;
    private static boolean zhezhi(CharacterInstance role){return role.character().sourceId()==1105;}
    private static String dimension(ServerPlayer player){return player.level().dimension().location().toString();}
    private Cranes cranes(ServerPlayer player,CharacterInstance role){
        Cranes value=owners.get(role.instanceId());
        if(value==null||value.world!=player.serverLevel()){value=new Cranes(player.serverLevel());owners.put(role.instanceId(),value);}
        return value;
    }
    /** Called immediately before command reduction; no client-provided image identity. */
    public MechanicsState prepare(ServerPlayer player,CharacterInstance role,MechanicsState state){
        if(state.characterId().equals("augusta")){
            var ledger=realm(player,role).ledger();var resources=new HashMap<>(state.resources());
            resources.put("majesty",(double)ledger.majesty());resources.put("crowns",(double)ledger.crowns());
            return new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),state.nextBasic(),resources,state.timers());
        }
        if(state.characterId().equals("zani")){
            if(state.timer("zani_guard_mode")==0){var guard=zaniGuards.remove(role.instanceId());if(guard!=null)guard.discard();}
            var value=blaze(player,role);var resources=new HashMap<>(state.resources());
            resources.put("blaze",value.gauge().ember(PlatformRuntime.time(player)));
            return new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),state.nextBasic(),resources,state.timers());
        }
        if(state.characterId().equals("lucilla")){
            var auras=photoAuras.get(role.instanceId());
            if(auras!=null)auras.values().removeIf(a->a.world()!=player.level()||!a.mode().equals(state.mode())||a.until()<=PlatformRuntime.time(player));
            var owner=photos.get(role.instanceId());
            if(owner==null||owner.world()!=player.level()||owner.chain()!=role.chain()){
                owner=new Photos(player.serverLevel(),role.chain(),new LucillaPhotoLedger(role.chain()));photos.put(role.instanceId(),owner);
            }
            owner.ledger().changeMode(state.mode().equals("echo")?LucillaPhotoLedger.Mode.ECHO:LucillaPhotoLedger.Mode.GLACIO);
            owner.ledger().synchronizeImpression(state.resource("impression"));
            if(owner.ledger().peacefulTick(ModAttachments.team(player).state().inCombat())){
                var resources=new HashMap<>(state.resources());resources.put("impression",150d);
                return new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),state.nextBasic(),resources,state.timers());
            }
            return state;
        }
        if(!zhezhi(role))return state;
        Cranes owner=cranes(player,role);long now=PlatformRuntime.time(player);
        var images=owner.ledger.images(dimension(player),now).stream().filter(a->position(a).distanceToSqr(player.position())<=12*12).toList();
        Vec3 eye=player.getEyePosition(),look=player.getLookAngle();
        owner.selected=images.stream().min(Comparator.comparingDouble(a->{
            Vec3 delta=position(a).subtract(eye);return -delta.normalize().dot(look)*100+delta.length();
        })).orElse(null);
        var timers=new HashMap<>(state.timers());
        timers.put("zhezhi_images",images.size());timers.put("zhezhi_insight",owner.ledger.insight(now));
        timers.put("zhezhi_selected_slot",owner.selected==null?0:owner.selected.slot().ordinal()+1);
        timers.put("zhezhi_selected_air",owner.selected!=null&&owner.selected.airborne()?1:0);
        return new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),state.nextBasic(),state.resources(),timers);
    }
    /** State has already committed; prepare supplied the pre-cast photo pool.
     * Each accepted native effect is applied exactly once, never from HUD. */
    public boolean photoProc(ServerPlayer player,CharacterInstance role,MechanicsState committed,String effect){
        var owner=photos.get(role.instanceId());if(owner==null||owner.world()!=player.level())return false;
        long now=PlatformRuntime.time(player);
        switch(effect){
            case "lucilla_memory_begin" -> {
                if(!owner.ledger().beginMemory(now))throw new IllegalStateException("Accepted memory lacked photos");
            }
            case "lucilla_photo_consumed" -> {
                if(!owner.ledger().consumePhoto(committed.timer("lucilla_forget_serial"),now))
                    throw new IllegalStateException("Accepted Forget lacked photo");
            }
            case "lucilla_memory_end" -> owner.ledger().finishMemory(now);
            case "lucilla_focus_open" -> photoFocus(player,role,committed);
            case "lucilla_focus_close" -> EffectRuntime.clearFocusSlow(role.instanceId());
            default -> {return false;}
        }
        return true;
    }
    /** MC camera volume:12blocks,120degree cone, half-speed ordinary hostile mobs.
     * The source specifies slower target time, not a ratio or Minecraft geometry. */
    public void photoFocus(ServerPlayer player,CharacterInstance owner,MechanicsState state){
        if(!state.characterId().equals("lucilla"))return;
        if(state.timer("lucilla_focus_channel")==0||!owner.alive()
            ||ModAttachments.team(player).state().active().orElse(null)!=owner){EffectRuntime.clearFocusSlow(owner.instanceId());return;}
        Vec3 eye=player.getEyePosition(),look=player.getLookAngle();
        var targets=player.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,player.getBoundingBox().inflate(12),target->{
            Vec3 delta=target.getEyePosition().subtract(eye);
            return player.distanceToSqr(target)<=144&&delta.normalize().dot(look)>=.5&&player.hasLineOfSight(target);
        });
        EffectRuntime.focusSlow(player,owner,targets);
    }
    public double photoEchoCrit(ServerPlayer player,CharacterInstance recipient){
        if(ModAttachments.team(player).state().active().orElse(null)!=recipient)return 0;
        return photos.values().stream().filter(p->p.world()==player.level())
            .mapToDouble(p->p.ledger().echoCritDamage(PlatformRuntime.time(player))).max().orElse(0);
    }
    public record PhotoCopy(CharacterInstance source,int applications){}
    public Map<String,Integer> photoView(ServerPlayer player,CharacterInstance role){
        var owner=photos.get(role.instanceId());if(owner==null||owner.world()!=player.level())return Map.of();
        long now=PlatformRuntime.time(player);
        return Map.of("film",owner.ledger().film(now),"zoom",(int)Math.round(owner.ledger().echoCritDamage(now)*10));
    }
    public void photoModeChanged(CharacterInstance role,String mode){
        photoAuras.remove(role.instanceId());
        var owner=photos.get(role.instanceId());
        if(owner!=null)owner.ledger().changeMode(mode.equals("echo")?LucillaPhotoLedger.Mode.ECHO:LucillaPhotoLedger.Mode.GLACIO);
    }
    /** Following aura: evaluate current active-player distance, not targets present at cast. */
    public boolean photoAura(ServerPlayer player,CharacterInstance owner,MechanicsState state,String id,double amount,int duration){
        if(!Set.of("lucilla_glacio_resistance","lucilla_outro_chafe","lucilla_team_echo_bonus").contains(id))return false;
        photoAuras.computeIfAbsent(owner.instanceId(),ignored->new HashMap<>()).put(id,
            new PhotoAura(player.serverLevel(),state.mode(),PlatformRuntime.time(player)+duration,amount));
        return true;
    }
    private double photoAuraAmount(ServerPlayer player,String id){
        long now=PlatformRuntime.time(player);
        return photoAuras.values().stream().map(m->m.get(id)).filter(Objects::nonNull)
            .filter(a->a.world()==player.level()&&a.until()>now).mapToDouble(PhotoAura::amount).max().orElse(0);
    }
    public double photoResistanceReduction(ServerPlayer player,LivingEntity target,String element){
        return target!=null&&element.equals("glacio")&&player.distanceToSqr(target)<=144
            ?photoAuraAmount(player,"lucilla_glacio_resistance"):0;
    }
    public double photoDamageBonus(ServerPlayer player,CharacterInstance actor,String kind,String action){
        long now=PlatformRuntime.time(player);double bonus=0;
        if(kind.equals("echo"))bonus=photos.values().stream().filter(p->p.world()==player.level()
            &&p.ledger().mode()==LucillaPhotoLedger.Mode.ECHO&&p.ledger().secondChainActive(now)).mapToDouble(p->.40).max().orElse(0);
        var owner=photos.get(actor.instanceId());
        if(owner!=null&&owner.world()==player.level()&&Set.of("lucilla/finale","lucilla/finale_echo").contains(action))
            bonus+=owner.ledger().finaleAmplification();
        if(kind.equals("echo"))bonus+=photoAuraAmount(player,"lucilla_team_echo_bonus");
        return bonus;
    }
    public double photoChafeAmplification(ServerPlayer player,LivingEntity target,String action){
        if(target==null||player.distanceToSqr(target)>144||
            !(action.startsWith("effect/glacio_chafe/")||action.startsWith("effect/glacio_frost/")))return 0;
        return photoAuraAmount(player,"lucilla_outro_chafe")+photos.values().stream().filter(p->p.world()==player.level()&&p.ledger().mode()==LucillaPhotoLedger.Mode.GLACIO
            &&p.ledger().secondChainActive(PlatformRuntime.time(player))).mapToDouble(p->.80).max().orElse(0);
    }
    public List<PhotoCopy> copyChafe(ServerPlayer player,CharacterInstance actor,Function<UUID,CharacterInstance> roles){
        if(ModAttachments.team(player).state().active().orElse(null)!=actor)return List.of();
        var copies=new ArrayList<PhotoCopy>();long now=PlatformRuntime.time(player);
        for(var entry:photos.entrySet()){
            var source=roles.apply(entry.getKey());
            if(source==null||!source.alive()||entry.getValue().world()!=player.level())continue;
            int count=entry.getValue().ledger().teammateChafe(source!=actor,false,now);
            if(count>0)copies.add(new PhotoCopy(source,count));
        }
        return List.copyOf(copies);
    }
    public boolean selectedValid(ServerPlayer player,CharacterInstance role,int slot){
        Cranes owner=owners.get(role.instanceId());
        return zhezhi(role)&&owner!=null&&owner.world==player.level()&&owner.selected!=null
            &&owner.selected.slot().ordinal()+1==slot&&position(owner.selected).distanceToSqr(player.position())<=12*12
            &&owner.ledger.image(owner.selected.slot(),dimension(player),PlatformRuntime.time(player)).orElse(null)==owner.selected;
    }
    public void place(ServerPlayer player,CharacterInstance role,boolean pair,boolean airborne){
        if(!zhezhi(role)||!role.alive())return;
        Cranes owner=cranes(player,role);long now=PlatformRuntime.time(player);
        Vec3 forward=Vec3.directionFromRotation(0,player.getYRot());
        Vec3 side=new Vec3(-forward.z,0,forward.x);
        Vec3 center=player.position().add(forward.scale(3)).add(0,airborne?2.0:0,0);
        for(var slot:pair?List.of(ZhezhiCraneLedger.Slot.LEFT,ZhezhiCraneLedger.Slot.RIGHT):List.of(ZhezhiCraneLedger.Slot.CENTER)){
            double offset=slot==ZhezhiCraneLedger.Slot.LEFT?-2:slot==ZhezhiCraneLedger.Slot.RIGHT?2:0;
            Vec3 point=center.add(side.scale(offset));
            owner.ledger.place(slot,dimension(player),point.x,point.y,point.z,airborne,now);
            var previous=owner.visuals.remove(slot);if(previous!=null)previous.discard();
            owner.visuals.put(slot,ConstructEntity.spawn(player.serverLevel(),player,role.instanceId(),
                "skill_light_zhezhi__crane_image",point,300,.8f,false,"idle"));
        }
    }
    public void beginLiberation(ServerPlayer player,CharacterInstance role){
        if(zhezhi(role)&&role.alive())cranes(player,role).ledger.beginLiberation(role.chain(),PlatformRuntime.time(player));
    }
    /** Commit only after the complete cast passed action, energy and stamina checks. */
    public void moveToSelected(ServerPlayer player,CharacterInstance role,int slot,int duration){
        if(!selectedValid(player,role,slot))throw new IllegalStateException("Accepted crane selection became invalid");
        Cranes owner=owners.get(role.instanceId());var anchor=owner.selected;
        var accepted=owner.ledger.teleport(anchor,dimension(player),PlatformRuntime.time(player)).orElseThrow();
        var image=owner.visuals.remove(anchor.slot());if(image!=null)image.discard();
        owner.selected=null;dash=new Dash(role.instanceId(),player.serverLevel(),position(anchor),PlatformRuntime.time(player)+Math.max(1,duration));
        if(accepted.resetsAirDodge())ModAttachments.team(player).state().stamina().resetAirDodge();
    }
    public void extraWhiteCrane(ServerPlayer player,CharacterInstance role){
        if(!zhezhi(role)||role.chain()<6)return;
        // Native proc effects commit before deferred cast movement. Freeze the
        // accepted crane destination here; the old player position searches the
        // wrong end of the teleport and can strike an unrelated rear target.
        Cranes owner=owners.get(role.instanceId());
        Vec3 origin=owner!=null&&owner.selected!=null?position(owner.selected):player.position();
        extraCranes.add(new ExtraCrane(role.instanceId(),player.serverLevel(),origin,PlatformRuntime.time(player)+12));
    }
    public void damage(ServerPlayer player,CharacterInstance attacker,LivingEntity target,String action){
        if(ModAttachments.team(player).state().active().orElse(null)==attacker&&!action.equals("suoming/rain_thunder"))
            for(var rain:rains.values())if(rain.world==player.level()){
                if(rain.ledger.activeTeammateDamage(true,false,PlatformRuntime.time(player)))rain.target=target;
            }
        var blade=blazes.get(attacker.instanceId());var heavy=zaniHeavyCasts.get(attacker.instanceId());
        if(blade!=null&&blade.world()==player.level()&&heavy!=null&&Set.of("zani/heavy_night","zani/heavy_night_last").contains(action))
            blade.gauge().nightHit(heavy,PlatformRuntime.time(player));
        if(ModAttachments.team(player).state().active().orElse(null)!=attacker
            ||action.equals("zhezhi/ink_crane")||action.equals("zhezhi/ink_crane_c5"))return;
        long now=PlatformRuntime.time(player);
        for(var entry:owners.values())if(entry.world==player.level()&&entry.ledger.active(now)){
            if(entry.ledger.damagedByActiveTeammate(now,true)){entry.target=target;entry.lastTarget=target.position();}
        }
    }
    /** All confirmed kill paths, including elemental-effect damage, use this receipt. */
    public void defeated(ServerPlayer player,CharacterInstance owner){
        var photo=photos.get(owner.instanceId());
        if(photo!=null&&photo.world()==player.level())photo.ledger().defeatedTarget();
    }
    public void tick(ServerPlayer player,Function<UUID,CharacterInstance> roles,Hits hits){
        long now=PlatformRuntime.time(player);
        rains.entrySet().removeIf(e->{var role=roles.apply(e.getKey());return role==null||!role.alive()||e.getValue().world!=player.level()||now>=e.getValue().until;});
        for(var entry:rains.entrySet()){
            var rain=entry.getValue();var target=rain.target;
            if(target!=null&&target.isAlive()&&target.level()==player.level()&&PlatformRuntime.canAttack(player,target)
                &&player.distanceToSqr(target)<=576&&player.hasLineOfSight(target)&&rain.ledger.pollRain(now)){
                var owner=roles.apply(entry.getKey());
                SkillModelProfiles.light(player,owner,"suoming/rain_thunder",target.getBoundingBox().getCenter().add(0,-1.05,0),12,false);
                hits.hit(owner,target,"rain_thunder");
            }
        }
        realms.entrySet().removeIf(e->{var role=roles.apply(e.getKey());boolean remove=role==null||!role.alive()||e.getValue().world()!=player.level();
            if(remove){EffectRuntime.clearStasisGroup(e.getKey(),"augusta_realm_stasis");var visual=realmVisuals.remove(e.getKey());if(visual!=null)visual.discard();}return remove;});
        for(var entry:realms.entrySet()){
            var owner=roles.apply(entry.getKey());var ledger=entry.getValue().ledger();ledger.tick(now,ModAttachments.team(player).state().inCombat());
            if(!ledger.restricted()){EffectRuntime.clearStasisGroup(entry.getKey(),"augusta_realm_stasis");continue;}
            // MC24-block range, source-owned target stasis; the world clock is untouched.
            for(var target:player.level().getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(24),
                t->PlatformRuntime.canAttack(player,t)&&player.distanceToSqr(t)<=576&&player.hasLineOfSight(t)))
                EffectRuntime.applyStasis(player,owner,target,2,"augusta_realm_stasis");
        }
        photoAuras.entrySet().removeIf(e->{var role=roles.apply(e.getKey());return role==null||!role.alive();});
        photoAuras.values().forEach(m->m.values().removeIf(a->a.world()!=player.level()||a.until()<=now));
        photos.entrySet().removeIf(e->{var role=roles.apply(e.getKey());return role==null||!role.alive()||e.getValue().world()!=player.level();});
        blazes.entrySet().removeIf(e->{var role=roles.apply(e.getKey());return role==null||!role.alive()||e.getValue().world()!=player.level();});
        blazes.values().forEach(b->b.targets().tick(now));
        zaniHeavyCasts.keySet().removeIf(id->!blazes.containsKey(id));
        zaniFinaleBonuses.keySet().removeIf(id->!blazes.containsKey(id));
        zaniOutroUntil.entrySet().removeIf(e->e.getValue()<=now||!blazes.containsKey(e.getKey()));
        owners.entrySet().removeIf(e->{var role=roles.apply(e.getKey());boolean remove=role==null||!role.alive()||e.getValue().world!=player.level();
            if(remove)e.getValue().visuals.values().forEach(ConstructEntity::discard);return remove;});
        for(var entry:owners.entrySet()){
            var owner=entry.getValue();var role=roles.apply(entry.getKey());
            if(now%6==0)for(var image:owner.ledger.images(dimension(player),now)){
                Vec3 point=position(image).add(0,.7,0);
                player.serverLevel().sendParticles(ParticleTypes.ENCHANT,point.x,point.y,point.z,3,.35,.45,.1,0);
                player.serverLevel().sendParticles(ParticleTypes.END_ROD,point.x,point.y,point.z,1,.1,.2,.1,0);
            }
            LivingEntity target=validTarget(player,owner.target)?owner.target:nearest(player,owner.lastTarget==null?player.position():owner.lastTarget);
            if(target==null)continue; // Do not spend the finite crane budget on no target.
            var emitted=owner.ledger.pollInkCrane(now);
            if(emitted.isPresent()){
                craneFlash(player,role,target,"ink_crane");
                owner.target=target;hits.hit(role,target,"ink_crane");
                if(emitted.get().extraFifthChain()&&validTarget(player,target)){
                    craneFlash(player,role,target,"ink_crane_c5");
                    hits.hit(role,target,"ink_crane_c5");
                }
            }
        }
        var due=extraCranes.stream().filter(e->now>=e.at()).toList();extraCranes.removeAll(due);
        for(var extra:due){var owner=roles.apply(extra.owner());
            if(owner==null||!owner.alive()||extra.world()!=player.level())continue;
            LivingEntity target=nearest(player,extra.origin());if(target!=null){craneFlash(player,owner,target,"white_crane_c6");hits.hit(owner,target,"white_crane_c6");}
        }
    }
    /** Run after ordinary role movement. Native collision clips each step; no wall teleport. */
    public void tickMovement(ServerPlayer player,CharacterInstance role){
        if(dash==null)return;
        if(!role.alive()||!dash.owner().equals(role.instanceId())||dash.world()!=player.level()||player.isPassenger()){
            cancelMovement(player);return;
        }
        long now=PlatformRuntime.time(player);Vec3 delta=dash.destination().subtract(player.position());
        if(now>=dash.until()||delta.lengthSqr()<.01){cancelMovement(player);return;}
        Vec3 step=delta.scale(1.0/Math.max(1,dash.until()-now));if(step.length()>1.25)step=step.normalize().scale(1.25);
        Vec3 before=player.position();player.move(MoverType.SELF,step);
        player.setDeltaMovement(Vec3.ZERO);player.fallDistance=0;player.hurtMarked=true;
        player.teleportTo(player.getX(),player.getY(),player.getZ());
        if(player.position().subtract(before).distanceToSqr(step)>.0001)cancelMovement(player);
    }
    public void cancelMovement(ServerPlayer player){if(dash!=null){dash=null;player.setDeltaMovement(Vec3.ZERO);player.hurtMarked=true;}}
    public void remove(UUID owner){EffectRuntime.clearFocusSlow(owner);var guard=zaniGuards.remove(owner);if(guard!=null)guard.discard();rains.remove(owner);realms.remove(owner);var realmVisual=realmVisuals.remove(owner);if(realmVisual!=null)realmVisual.discard();EffectRuntime.clearStasisGroup(owner,"augusta_realm_stasis");zaniOutroUntil.remove(owner);zaniFinaleBonuses.remove(owner);zaniHeavyCasts.remove(owner);blazes.remove(owner);photoAuras.remove(owner);photos.remove(owner);var removed=owners.remove(owner);if(removed!=null)removed.visuals.values().forEach(ConstructEntity::discard);extraCranes.removeIf(e->e.owner().equals(owner));if(dash!=null&&dash.owner().equals(owner))dash=null;}
    public void clear(){photos.keySet().forEach(EffectRuntime::clearFocusSlow);zaniGuards.values().forEach(ConstructEntity::discard);zaniGuards.clear();rains.clear();realmVisuals.values().forEach(ConstructEntity::discard);realmVisuals.clear();realms.keySet().forEach(id->EffectRuntime.clearStasisGroup(id,"augusta_realm_stasis"));realms.clear();zaniOutroUntil.clear();zaniFinaleBonuses.clear();zaniHeavyCasts.clear();blazes.clear();photoAuras.clear();photos.clear();owners.values().forEach(owner->owner.visuals.values().forEach(ConstructEntity::discard));owners.clear();extraCranes.clear();dash=null;}
    private static void craneFlash(ServerPlayer player,CharacterInstance owner,LivingEntity target,String action){
        ConstructEntity.spawn(player.serverLevel(),player,owner.instanceId(),"skill_light_zhezhi__"+action,
            target.position().add(0,.4,0),12,.85f,false,"attack");
    }
    private static Vec3 position(ZhezhiCraneLedger.Anchor image){return new Vec3(image.x(),image.y(),image.z());}
    private static boolean validTarget(ServerPlayer player,LivingEntity target){return target!=null&&target.level()==player.level()&&target.isAlive()&&PlatformRuntime.canAttack(player,target)&&player.distanceToSqr(target)<=24*24&&player.hasLineOfSight(target);}
    private static LivingEntity nearest(ServerPlayer player,Vec3 center){return player.level().getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(24),t->validTarget(player,t)).stream().min(Comparator.comparingDouble(t->t.position().distanceToSqr(center))).orElse(null);}
}
