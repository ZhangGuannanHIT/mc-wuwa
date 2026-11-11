package cn.zgnhit.mcwuwa.platform;

import cn.zgnhit.mcwuwa.core.CharacterId;
import cn.zgnhit.mcwuwa.core.CharacterInstance;
import cn.zgnhit.mcwuwa.content.mechanics.MechanicsState;
import java.util.*;
import java.util.function.Function;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/** World-owned dream targets and jellyfish schedules. Resource pools remain in
 * MechanicsState. Each delayed hit retains its actual casting role identity. */
final class TideWorldRuntime {
    interface Hit {void apply(CharacterInstance owner,LivingEntity target,String action);}
    interface Heal {void apply(CharacterInstance owner,CharacterInstance recipient,double flat,double healthRatio);}
    private record Dream(UUID owner,LivingEntity target,long expires,long protectedUntil,Identifier slow){}
    private static final class Diffusion {
        final UUID owner;LivingEntity target;long next,burstUntil,nextTrigger;int spawned;
        Diffusion(UUID owner){this.owner=owner;}
    }
    private final Map<UUID,Dream> dreams=new HashMap<>();
    private final Map<UUID,Diffusion> diffusion=new HashMap<>();
    private final Map<UUID,Set<UUID>> moisture=new HashMap<>();
    private static final class Spring {
        final UUID owner,recipient;int charges=10;long next;
        Spring(UUID owner,UUID recipient,long now){this.owner=owner;this.recipient=recipient;next=now+40;}
    }
    private final Map<String,Spring> springs=new HashMap<>();
    private boolean resolving;
    void pauseOneTick(){
        dreams.replaceAll((id,d)->new Dream(d.owner,d.target,d.expires+1,d.protectedUntil+1,d.slow));
        diffusion.values().forEach(d->{if(d.next>0)d.next++;if(d.burstUntil>0)d.burstUntil++;if(d.nextTrigger>0)d.nextTrigger++;});
        springs.values().forEach(s->s.next++);
    }

    void dream(ServerPlayer player,CharacterInstance owner,LivingEntity target,int ticks,int grace){
        Dream old=dreams.remove(target.getUUID());if(old!=null)release(old);
        Identifier id=Identifier.fromNamespaceAndPath("mc_wuwa","cantarella_dream_"+owner.instanceId());
        var speed=target.getAttribute(Attributes.MOVEMENT_SPEED);
        if(speed!=null){speed.removeModifier(id);speed.addTransientModifier(new AttributeModifier(id,-.65,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));}
        long now=PlatformRuntime.time(player);dreams.put(target.getUUID(),new Dream(owner.instanceId(),target,now+ticks,now+grace,id));
    }
    void castDiffusion(CharacterInstance owner){diffusion.put(owner.instanceId(),new Diffusion(owner.instanceId()));}
    void moisture(CharacterInstance owner,Collection<UUID> recipients){moisture.put(owner.instanceId(),new HashSet<>(recipients));}
    void entered(ServerPlayer player,CharacterInstance recipient,Function<UUID,CharacterInstance> roles,Heal heal){
        for(var entry:moisture.entrySet())if(entry.getValue().remove(recipient.instanceId())){
            CharacterInstance owner=roles.apply(entry.getKey());if(owner==null||!owner.alive()||!recipient.alive())continue;
            double factor=owner.chain()>=4?1.5:1;heal.apply(owner,recipient,627*factor,.0342*factor);
            springs.put(owner.instanceId()+":"+recipient.instanceId(),new Spring(owner.instanceId(),recipient.instanceId(),PlatformRuntime.time(player)));
        }
    }
    void damage(ServerPlayer player,CharacterInstance attacker,LivingEntity target,String action,PlatformRuntime.HitOrigin origin,Hit hit){
        damage(player,attacker,target,action,origin,
            cn.zgnhit.mcwuwa.content.mechanics.CoordinatedHitRules.coordinated(action,""),hit);
    }
    void damage(ServerPlayer player,CharacterInstance attacker,LivingEntity target,String action,PlatformRuntime.HitOrigin origin,boolean coordinated,Hit hit){
        if(resolving)return;
        boolean appliesDream=Set.of("cantarella/skill_dream","cantarella/skill_drain").contains(action)
            ||action.equals("cantarella/liberation")&&attacker.chain()>=2;
        // Coordinated damage, Hecate (explicit Phrolova source exception),
        // and exploration tools do not wake or consume Dream.
        // An ordinary allied hit clears it without Cantarella's extra damage.
        if(!coordinated&&!action.startsWith("phrolova/hecate_")&&!action.startsWith("exploration/"))wake(player,target,attacker,appliesDream,hit);
        activeDamage(player,attacker,target,action);
    }
    /** Accepted active-role damage arms Diffusion without waking Dream twice. */
    void activeDamage(ServerPlayer player,CharacterInstance attacker,LivingEntity target,String action){
        if(resolving)return;
        long now=PlatformRuntime.time(player);
        // The jellyfish's own attack cannot restart its three-second burst.
        if(action.equals("cantarella/jellyfish")||ModAttachments.team(player).state().active().orElse(null)!=attacker)return;
        for(Diffusion stream:diffusion.values()){
            if(now<stream.nextTrigger)continue;
            stream.nextTrigger=now+20;
            boolean restarted=stream.next<=0||now>stream.burstUntil;
            stream.target=target;stream.burstUntil=now+60;if(restarted)stream.next=now+20;
        }
    }
    void receivedDamage(ServerPlayer player,LivingEntity target,Hit hit){wake(player,target,null,false,hit);}
    void receivedDamage(ServerPlayer player,LivingEntity target,CharacterInstance attacker,Hit hit){wake(player,target,attacker,false,hit);}
    private void wake(ServerPlayer player,LivingEntity target,CharacterInstance attacker,boolean appliesDream,Hit hit){
        if(resolving)return;
        long now=PlatformRuntime.time(player);Dream dream=dreams.get(target.getUUID());
        if(dream!=null&&(now>=dream.expires()||!target.isAlive())){
            dreams.remove(target.getUUID());release(dream);return;
        }
        if(dream!=null&&(now>=dream.protectedUntil()||appliesDream)){
            dreams.remove(target.getUUID());release(dream);
            CharacterInstance owner=null;var team=ModAttachments.team(player).state();
            for(int slot=0;slot<3;slot++){
                var candidate=team.at(slot).orElse(null);
                if(candidate!=null&&candidate.instanceId().equals(dream.owner())){owner=candidate;break;}
            }
            if(owner!=null&&owner.alive()&&(attacker==null||attacker.instanceId().equals(dream.owner()))){
                resolving=true;try{hit.apply(owner,target,"awaken");}finally{resolving=false;}
            }
        }
    }
    void tick(ServerPlayer player,Function<UUID,CharacterInstance> roles,Function<CharacterInstance,MechanicsState> states,Set<UUID> completedIntervals,Hit hit,Heal heal){
        long now=PlatformRuntime.time(player);
        moisture.entrySet().removeIf(entry->{
            var owner=roles.apply(entry.getKey());if(owner==null||!owner.alive())return true;
            entry.getValue().removeIf(id->{var recipient=roles.apply(id);return recipient==null||!recipient.alive();});
            return entry.getValue().isEmpty();
        });
        for(var iterator=springs.values().iterator();iterator.hasNext();){
            Spring spring=iterator.next();CharacterInstance owner=roles.apply(spring.owner),recipient=roles.apply(spring.recipient);
            if(owner==null||recipient==null||!owner.alive()||!recipient.alive()||spring.charges<=0){iterator.remove();continue;}
            if(now>=spring.next){spring.next=now+40;if(recipient.health()<recipient.maxHealth()){
                spring.charges--;double factor=owner.chain()>=4?1.5:1;heal.apply(owner,recipient,62*factor,.0034*factor);
            }}
        }
        dreams.values().removeIf(d->{var owner=roles.apply(d.owner());boolean expired=d.expires()<=now||!d.target().isAlive()||d.target().level()!=player.level()||owner==null||!owner.alive();if(expired)release(d);return expired;});
        for(var iterator=diffusion.values().iterator();iterator.hasNext();){
            Diffusion stream=iterator.next();CharacterInstance owner=roles.apply(stream.owner);
            boolean ended=owner==null||states.apply(owner).timer("cantarella_diffusion")<=0;
            if(owner==null||!owner.alive()||(ended&&!completedIntervals.contains(stream.owner))||stream.spawned>=(owner.chain()>=5?26:21)){iterator.remove();continue;}
            if(stream.next>0&&now>=stream.next&&now<=stream.burstUntil&&stream.target!=null&&stream.target.isAlive()
                &&PlatformRuntime.canAttack(player,stream.target)){
                stream.next=now+20;stream.spawned++;resolving=true;
                try{hit.apply(owner,stream.target,"jellyfish");}finally{resolving=false;}
            }
            // Complete an already due interval, then remove the expired stream immediately.
            if(ended)iterator.remove();
        }
    }
    private static void release(Dream dream){var speed=dream.target().getAttribute(Attributes.MOVEMENT_SPEED);if(speed!=null)speed.removeModifier(dream.slow());}
    void remove(UUID owner){
        dreams.values().removeIf(d->{if(!d.owner().equals(owner))return false;release(d);return true;});
        diffusion.remove(owner);moisture.remove(owner);moisture.values().forEach(recipients->recipients.remove(owner));
        springs.values().removeIf(s->s.owner.equals(owner)||s.recipient.equals(owner));
    }
    void clear(){dreams.values().forEach(TideWorldRuntime::release);dreams.clear();diffusion.clear();moisture.clear();springs.clear();}
}
