package cn.zgnhit.mcwuwa.platform;

import cn.zgnhit.mcwuwa.McWuwa;
import cn.zgnhit.mcwuwa.core.CharacterInstance;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Fixed per-character slot sets. Empty slots never count toward a set. Source: frozen sonatas.json. */
public final class SonataRuntime {
    private record Buff(UUID owner, UUID recipient, String id, String attribute, double amount, long expiry, boolean endsOnSwitch) { }
    private final List<Buff> buffs = new ArrayList<>();
    private final Map<UUID, Long> snowReady = new HashMap<>(), snowExpiry = new HashMap<>(), snowExtendReady = new HashMap<>();
    private final Map<UUID, Integer> snowExtensions = new HashMap<>();
    private record AttackStacks(int count,long expires){ }
    private final Map<UUID,AttackStacks> sunStacks=new HashMap<>();
    private final Map<UUID,AttackStacks> crownStacks=new HashMap<>(),glacioSkillStacks=new HashMap<>();
    private final Map<UUID,Long> shieldReady=new HashMap<>();
    private record EchoHarmony(Set<Integer> names,int count,long expires) { }
    private final Map<UUID,EchoHarmony> echoHarmony=new HashMap<>();
    public void pauseOneTick(){
        buffs.replaceAll(b->new Buff(b.owner,b.recipient,b.id,b.attribute,b.amount,cn.zgnhit.mcwuwa.core.CinematicTimeRules.postpone(b.expiry,1),b.endsOnSwitch));
        for(var map:List.of(snowReady,snowExpiry,snowExtendReady,shieldReady))map.replaceAll((id,t)->cn.zgnhit.mcwuwa.core.CinematicTimeRules.postpone(t,1));
        for(var map:List.of(sunStacks,crownStacks,glacioSkillStacks))map.replaceAll((id,s)->new AttackStacks(s.count,cn.zgnhit.mcwuwa.core.CinematicTimeRules.postpone(s.expires,1)));
        echoHarmony.replaceAll((id,s)->new EchoHarmony(s.names,s.count,cn.zgnhit.mcwuwa.core.CinematicTimeRules.postpone(s.expires,1)));
    }
    public void echoCast(ServerPlayer player,CharacterInstance role,int echoId) {
        if(counts(role,PlatformRuntime.weaponFor(player,role)).getOrDefault(21,0)<3)return;
        long now=PlatformRuntime.time(player);UUID owner=role.instanceId();
        put(owner,owner,"echo_harmony_heavy","heavy_bonus",.30,now+80,false);
        EchoHarmony old=echoHarmony.get(owner);
        if(old==null||old.expires<=now)old=new EchoHarmony(Set.of(),0,now);
        var names=new HashSet<>(old.names);int count=old.count;
        if(count<4&&!names.add(echoId))return;
        if(count<4)count++;
        echoHarmony.put(owner,new EchoHarmony(Set.copyOf(names),count,now+600));
        put(owner,null,"echo_harmony_team","echo_bonus",.04*count,now+600,false);
    }
    public static Map<Integer, Integer> counts(CharacterInstance role, ItemStack weapon) {
        Map<Integer, Integer> result = new HashMap<>();var definition=McWuwa.catalog().requireByWeapon(role.character().weaponPath());var slots=definition.sonataSlots();var tag = WeaponData.tag(weapon);
        for (int i = 0; i < 5; i++) if (!tag.getCompoundOrEmpty("echo_" + i).isEmpty()) result.merge(cn.zgnhit.mcwuwa.content.mechanics.SonataPresetRules.sonataId(definition.characterId(),role.selectedMode(),slots.get(i)), 1, Integer::sum);
        return Map.copyOf(result);
    }
    public static Map<String, Double> permanent(CharacterInstance role, ItemStack weapon) {
        Map<String, Double> result = new HashMap<>();
        counts(role, weapon).forEach((id, pieces) -> {
            if (pieces >= 2) {
                String attribute = switch (id) {
                    case 5, 11,24 -> "spectro_bonus"; case 6,12 -> "havoc_bonus"; case 8, 33 -> "energy_regen";
                    case 4,16,17,29,34 -> "aero_bonus"; case 7,25 -> "healing_bonus"; case 2,18,27,28 -> "fusion_bonus";
                    case 31 -> "attack_pct";
                    case 30 -> "glacio_bonus"; case 3,36,37 -> "electro_bonus"; default -> "";
                };
                if (!attribute.isEmpty()) result.merge(attribute, .10, Double::sum);
                if(id==10)result.merge("skill_bonus",.12,Double::sum);
            }
            if (id == 19 && pieces >= 3 && role.resource("energy") == 0) { result.merge("crit_rate", .20, Double::sum); result.merge("echo_bonus", .35, Double::sum); }
        }); return Map.copyOf(result);
    }
    public void event(ServerPlayer player, CharacterInstance role, String trigger, boolean targetAero, double offTuneEfficiency, double tuneAmplification) {
        long now = PlatformRuntime.time(player); UUID owner = role.instanceId(); var counts = counts(role, PlatformRuntime.weaponFor(player, role));
        if(counts.getOrDefault(2,0)>=5&&trigger.equals("SKILL_CAST"))put(owner,owner,"molten_skill","fusion_bonus",.30,now+300,false);
        if(counts.getOrDefault(4,0)>=5&&trigger.equals("INTRO"))put(owner,owner,"gale_intro","aero_bonus",.30,now+300,false);
        if(counts.getOrDefault(7,0)>=5&&trigger.equals("HEAL"))put(owner,null,"rejuvenation_attack","attack_pct",.15,now+600,false);
        if(counts.getOrDefault(10,0)>=5){
            if(trigger.equals("SKILL_CAST"))put(owner,owner,"frost_skill","glacio_bonus",.225,now+300,false);
            if(trigger.equals("LIBERATION_CAST")){
                var old=glacioSkillStacks.get(owner);int n=Math.min(2,old!=null&&old.expires>now?old.count+1:1);
                glacioSkillStacks.put(owner,new AttackStacks(n,now+100));put(owner,owner,"frost_liberation","skill_bonus",.18*n,now+100,false);
            }
        }
        if(counts.getOrDefault(11,0)>=5){
            if(trigger.equals("spectro_frazzle"))put(owner,owner,"radiance_crit","crit_rate",.20,now+300,false);
            if(trigger.equals("SPECTRO_10_HIT"))put(owner,owner,"radiance_target","spectro_bonus",.15,now+300,false);
        }
        if(counts.getOrDefault(18,0)>=5&&trigger.equals("LIBERATION_CAST")){
            put(owner,null,"wolf_fusion","fusion_bonus",.15,now+700,false);put(owner,owner,"wolf_liberation","liberation_bonus",.20,now+700,false);
        }
        if(counts.getOrDefault(20,0)>=3&&trigger.equals("SHIELD_GAIN")&&shieldReady.getOrDefault(owner,0L)<=now){
            shieldReady.put(owner,now+10);var old=crownStacks.get(owner);int n=Math.min(5,old!=null&&old.expires>now?old.count+1:1);
            crownStacks.put(owner,new AttackStacks(n,now+80));put(owner,owner,"crown_attack","attack_pct",.06*n,now+80,false);put(owner,owner,"crown_crit_damage","crit_damage",.04*n,now+80,false);
        }
        if(counts.getOrDefault(22,0)>=3){
            if(trigger.equals("ECHO_HIT"))put(owner,owner,"hunter_heavy_crit","heavy_crit_rate",.20,now+120,false);
            if(trigger.equals("HEAVY_HIT"))put(owner,owner,"hunter_echo_crit","echo_crit_rate",.20,now+120,false);
        }
        if(counts.getOrDefault(28,0)>=5&&trigger.equals("fusion_burst"))put(owner,owner,"foam_fusion","fusion_bonus",.10,now+300,false);
        if(counts.getOrDefault(29,0)>=5&&trigger.equals("ECHO_HIT")){
            put(owner,owner,"semantics_echo_crit","echo_crit_rate",.20,now+100,false);put(owner,owner,"semantics_aero","aero_bonus",.15,now+100,false);
        }
        if(counts.getOrDefault(31,0)>=5&&Set.of("rupture_shifting","strain_shifting","tune_offset").contains(trigger))put(owner,null,"dream_tune_amplification","tune_break_amplification",20,now+600,false);
        if(counts.getOrDefault(34,0)>=5&&Set.of("strain_shifting","tune_offset").contains(trigger)){
            put(owner,owner,"evil_cleansing_crit","crit_damage",.20,now+300,false);put(owner,owner,"evil_cleansing_aero","aero_bonus",.30,now+300,false);
        }
        if(counts.getOrDefault(6,0)>=5&&trigger.equals("ATTACK_CAST")){
            var old=sunStacks.get(owner);int count=Math.min(4,old!=null&&old.expires>now?old.count+1:1);
            sunStacks.put(owner,new AttackStacks(count,now+300));put(owner,owner,"sun_havoc","havoc_bonus",.075*count,now+300,false);
        }
        if (counts.getOrDefault(5, 0) >= 5 && trigger.equals("INTRO")) put(owner, owner, "spectro_intro", "spectro_bonus", .30, now + 300, false);
        if (counts.getOrDefault(16, 0) >= 5 && trigger.equals("aero_erosion")) { put(owner, null, "aero_team", "aero_bonus", .15, now + 400, false); put(owner, owner, "aero_self", "aero_bonus", .15, now + 400, false); }
        if (counts.getOrDefault(17, 0) >= 5 && trigger.equals("HIT") && targetAero) { put(owner, owner, "aero_crit", "crit_rate", .10, now + 200, false); put(owner, owner, "aero_damage", "aero_bonus", .30, now + 200, false); }
        if (counts.getOrDefault(23, 0) >= 3 && trigger.equals("havoc_bane")) { put(owner, owner, "havoc_attack", "attack_pct", .20, now + 100, false); put(owner, owner, "havoc_liberation", "liberation_bonus", .30, now + 100, false); }
        if (counts.getOrDefault(25, 0) >= 5 && trigger.equals("HEAL")) put(owner, null, "heal_attack", "attack_pct", Math.min(.25, Math.max(0, offTuneEfficiency) * .20), now + 80, false);
        if (counts.getOrDefault(27, 0) >= 5 && Set.of("fusion_burst", "rupture_shifting").contains(trigger)) { put(owner, owner, "fusion_crit", "crit_rate", .20, now + 160, false); put(owner, owner, "fusion_damage", "fusion_bonus", .20, now + 160, false); }
        if (counts.getOrDefault(33, 0) >= 5) {
            if (trigger.equals("havoc_bane")) { put(owner, owner, "feather_crit", "crit_rate", .20, now + 300, false); put(owner, owner, "feather_heavy", "heavy_bonus", .35, now + 300, false); }
            if (trigger.equals("glacio_chafe")) put(owner, null, "feather_attack", "attack_pct", Math.min(.25, NativeStats.of(role, PlatformRuntime.weaponFor(player, role)).energyRegen() * .10), now + 200, false);
        }
        if (counts.getOrDefault(36, 0) >= 5 && Set.of("electro_flare", "UNISON").contains(trigger)) { put(owner, owner, "electro_crit", "crit_rate", .15, now + 600, false); put(owner, owner, "electro_damage", "electro_bonus", .225, now + 600, false); }
        if(counts.getOrDefault(37,0)>=5&&trigger.equals("electro_flare"))
            put(owner,owner,"reflection_electro","electro_bonus",.10,now+300,false);
        if (counts.getOrDefault(30, 0) >= 5) {
            if (trigger.equals("glacio_chafe")) {
                put(owner, owner, "snow_glacio", "glacio_bonus", .10, now + 300, false);
                if (role.cooldown("sonata_snow_ready") == 0) { role.setCooldown("sonata_snow_ready", 500); snowExpiry.put(owner, now + 300); }
            }
            if (trigger.equals("LIBERATION_HIT")) {
                if (snowExpiry.getOrDefault(owner, 0L) > now) { snowExpiry.remove(owner); snowExtensions.put(owner, 0); put(owner, owner, "snow_crit", "crit_rate", .25, now + 120, false); }
                else if (snowExtensions.getOrDefault(owner, 6) < 6 && role.cooldown("sonata_snow_extend_ready") == 0) {
                    for (var buff : List.copyOf(buffs)) if (buff.owner.equals(owner) && buff.id.equals("snow_crit") && buff.expiry > now) {
                        put(owner, owner, "snow_crit", "crit_rate", .25, buff.expiry + 80, false); snowExtensions.merge(owner, 1, Integer::sum); role.setCooldown("sonata_snow_extend_ready", 10);
                    }
                }
            }
        }
        // Sonata6 follows the frozen description7.5% x4; conflicting raw6% x5 is preserved
        // in sonatas.json, with the resolution and cast dispatch documented separately.
    }
    public void switched(ServerPlayer player, CharacterInstance outgoing, boolean introOutro, double incomingTuneAmplification) {
        if (outgoing == null) return; UUID old = outgoing.instanceId(); long now = PlatformRuntime.time(player);
        buffs.removeIf(b -> b.endsOnSwitch && Objects.equals(b.recipient, old));
        if (!introOutro) return;
        var incoming = ModAttachments.team(player).state().active(); if (incoming.isEmpty()) return; UUID recipient = incoming.get().instanceId();
        var count = counts(outgoing, PlatformRuntime.weaponFor(player, outgoing));
        if(count.getOrDefault(12,0)>=5){
            put(old,recipient,"nocturne_next","havoc_bonus",.15,now+300,false);
            var hit=cn.zgnhit.mcwuwa.content.RoleProcDefinitions.solo("sonata_nocturne_outro","outro","havoc","attack",4.8,Map.of());
            for(var target:player.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,player.getBoundingBox().inflate(8),
                e->PlatformRuntime.canAttack(player,e)&&player.distanceToSqr(e)<=64&&player.hasLineOfSight(e)))
                PlatformRuntime.nativeHit(player,outgoing,target,hit,0,1,"sonata/nocturne_outro",PlatformRuntime.HitOrigin.AUTONOMOUS);
        }
        if(count.getOrDefault(28,0)>=5&&buffs.stream().anyMatch(b->b.owner.equals(old)&&b.id.equals("foam_fusion")&&b.expiry>now))
            put(old,recipient,"foam_next","fusion_bonus",.25,now+300,false);
        if(count.getOrDefault(37,0)>=5&&buffs.stream().anyMatch(b->b.owner.equals(old)&&b.id.equals("reflection_electro")&&b.expiry>now))
            put(old,recipient,"reflection_next","electro_bonus",.25,now+300,false);
        if (count.getOrDefault(8, 0) >= 5) put(old, recipient, "moonlit_attack", "attack_pct", .225, now + 300, false);
        if (count.getOrDefault(24, 0) >= 5) put(old, recipient, "radiant_attack", "attack_pct", .15 + Math.min(.15, Math.max(0, incomingTuneAmplification) * .003), now + 300, true);
        if (count.getOrDefault(30, 0) >= 5 && snowExpiry.getOrDefault(old, 0L) > now) { snowExpiry.remove(old); put(old, recipient, "snow_next", "glacio_bonus", .25, now + 300, false); }
        event(player, incoming.get(), "INTRO", false, 0, incomingTuneAmplification);
    }
    public Map<String, Double> modifiers(CharacterInstance role, long now) {
        return modifiers(role,now,"all");
    }
    public Map<String, Double> modifiers(CharacterInstance role,long now,String kind) {
        return modifiers(role,now,kind,false);
    }
    public Map<String, Double> modifiers(CharacterInstance role,long now,String kind,boolean radianceTarget) {
        sunStacks.values().removeIf(stack->stack.expires<=now);
        buffs.removeIf(b -> b.expiry <= now); Map<String, Double> highest = new HashMap<>(), result = new HashMap<>();
        for (var b : buffs) if (b.recipient == null || b.recipient.equals(role.instanceId())) highest.merge(b.id + "|" + b.attribute, b.amount, Math::max);
        if(radianceTarget)highest.merge("radiance_target|spectro_bonus",.15,Math::max);
        boolean heavy=highest.containsKey("hunter_heavy_crit|heavy_crit_rate"),echo=highest.containsKey("hunter_echo_crit|echo_crit_rate");
        if(heavy&&echo)result.put("fusion_bonus",.16);
        highest.forEach((k,v)->{String attribute=k.substring(k.indexOf('|')+1);
            if(attribute.equals("heavy_crit_rate")||attribute.equals("echo_crit_rate")){
                if(attribute.startsWith(kind+"_"))result.merge("crit_rate",v,Double::sum);
            }else result.merge(attribute,v,Double::sum);
        });return Map.copyOf(result);
    }
    private void put(UUID owner, UUID recipient, String id, String attribute, double amount, long expiry, boolean endsOnSwitch) { buffs.removeIf(b -> b.owner.equals(owner) && b.id.equals(id)); buffs.add(new Buff(owner, recipient, id, attribute, amount, expiry, endsOnSwitch)); }
    public void modeChanged(CharacterInstance role){
        if(role.character()==cn.zgnhit.mcwuwa.core.CharacterId.DENIA)
            buffs.removeIf(b->b.owner.equals(role.instanceId())&&Set.of("foam_fusion","dream_tune_amplification").contains(b.id));
    }
    public void downed(UUID owner){buffs.removeIf(buff->buff.owner.equals(owner)||owner.equals(buff.recipient));sunStacks.remove(owner);snowExpiry.remove(owner);snowExtensions.remove(owner);echoHarmony.remove(owner);crownStacks.remove(owner);glacioSkillStacks.remove(owner);shieldReady.remove(owner);}
    public void clear() { buffs.clear(); snowReady.clear(); snowExpiry.clear(); snowExtendReady.clear(); snowExtensions.clear();sunStacks.clear();echoHarmony.clear();crownStacks.clear();glacioSkillStacks.clear();shieldReady.clear(); }
}
