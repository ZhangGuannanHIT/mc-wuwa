package cn.zgnhit.mcwuwa.platform;

import cn.zgnhit.mcwuwa.content.mechanics.MechanicsState;
import cn.zgnhit.mcwuwa.core.CharacterInstance;
import cn.zgnhit.mcwuwa.platform.entity.ConstructEntity;
import java.util.*;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** World positions and visuals of weapon-owned kernels; damage stays in ActionRuntime. */
final class NewThreeWorldEffects {
    private record Kernel(Vec3 position,ConstructEntity visual) { }
    private final Map<UUID,LinkedHashMap<Integer,Kernel>> kernels=new HashMap<>();
    private record Domain(int stage,ConstructEntity visual) { }
    private final Map<UUID,Domain> domains=new HashMap<>();
    private ConstructEntity demonAura;
    private record Orbit(ConstructEntity star,ConstructEntity dark,long start,long expires){}
    private final Map<UUID,Orbit> outroOrbits=new HashMap<>();
    void pauseOneTick(){outroOrbits.replaceAll((id,o)->new Orbit(o.star,o.dark,o.start+1,o.expires+1));}
    void outros(ServerPlayer player,Map<UUID,MechanicsState> states){
        var team=ModAttachments.team(player).state();var live=new HashSet<UUID>();long now=PlatformRuntime.time(player);
        for(int slot=0;slot<3;slot++){
            var owner=team.at(slot).orElse(null);if(owner==null||!owner.alive())continue;
            var state=states.get(owner.instanceId());
            if(state==null||!state.characterId().equals("shorekeeper")||state.timer("shore_escape")<=0)continue;
            live.add(owner.instanceId());var orbit=outroOrbits.get(owner.instanceId());
            if(orbit==null||orbit.star.isRemoved()||orbit.dark.isRemoved()){
                removeOrbit(owner.instanceId());int remaining=state.timer("shore_escape");
                orbit=new Orbit(ConstructEntity.spawn(player.serverLevel(),player,owner.instanceId(),"shorekeeper_outro_star_butterfly",player.position(),remaining,.25f,false,"idle"),
                    ConstructEntity.spawn(player.serverLevel(),player,owner.instanceId(),"shorekeeper_outro_dark_butterfly",player.position(),remaining,.25f,false,"idle"),now,now+remaining);
                outroOrbits.put(owner.instanceId(),orbit);
            }
            if(now+state.timer("shore_escape")>orbit.expires+1){
                orbit.star.finishAfter(state.timer("shore_escape"),"idle");orbit.dark.finishAfter(state.timer("shore_escape"),"idle");
                orbit=new Orbit(orbit.star,orbit.dark,orbit.start,now+state.timer("shore_escape"));outroOrbits.put(owner.instanceId(),orbit);
            }
            double angle=(now-orbit.start)*.12;
            placeOrbit(player,orbit.star,angle);placeOrbit(player,orbit.dark,angle+Math.PI);
        }
        for(var owner:List.copyOf(outroOrbits.keySet()))if(!live.contains(owner))removeOrbit(owner);
    }
    private static void placeOrbit(ServerPlayer player,ConstructEntity butterfly,double angle){
        butterfly.setPos(player.position().add(Math.cos(angle)*.8,1.15+Math.sin(angle*2)*.12,Math.sin(angle)*.8));
        butterfly.setYRot((float)Math.toDegrees(angle));
    }
    private void removeOrbit(UUID owner){var orbit=outroOrbits.remove(owner);if(orbit!=null){orbit.star.discard();orbit.dark.discard();}}
    void prune(ServerPlayer player) {
        var team=ModAttachments.team(player).state();var alive=new HashSet<UUID>();
        for(int i=0;i<3;i++)team.at(i).filter(CharacterInstance::alive).ifPresent(role->alive.add(role.instanceId()));
        for(var owner:List.copyOf(kernels.keySet()))if(!alive.contains(owner))remove(owner);
        for(var owner:List.copyOf(domains.keySet()))if(!alive.contains(owner)||domains.get(owner).visual.isRemoved())removeDomain(owner);
    }
    void active(ServerPlayer player,MechanicsState state) {
        if(state==null||!state.characterId().equals("galbrena")||!state.form().equals("demon")){
            if(demonAura!=null){demonAura.discard();demonAura=null;}return;
        }
        if(demonAura==null||demonAura.isRemoved())ModAttachments.team(player).state().active().filter(CharacterInstance::alive).ifPresent(role->
            demonAura=SkillModelProfiles.light(player,role,"galbrena/demon_aura",player.position(),Math.max(1,state.timer("gal_demon")),true));
    }

    void kernel(ServerPlayer player,CharacterInstance role,int index,int duration,LivingEntity target) {
        var owned=kernels.computeIfAbsent(role.instanceId(),id->new LinkedHashMap<>());
        var old=owned.remove(index);if(old!=null)old.visual.discard();
        Vec3 center=target==null?player.position():target.position();
        double angle=(index*.4+.1)*Math.PI;
        Vec3 at=center.add(Math.cos(angle)*.6,.25,Math.sin(angle)*.6);
        var visual=ConstructEntity.spawn(player.serverLevel(),player,role.instanceId(),"shorekeeper_kernel",at,duration+5,.22f,false,"idle");
        owned.put(index,new Kernel(at,visual));
    }
    Vec3 release(CharacterInstance role,int index,Vec3 fallback) {
        var owned=kernels.get(role.instanceId());if(owned==null||owned.isEmpty())return fallback;
        var kernel=owned.remove(index);if(kernel==null)return fallback;
        kernel.visual.discard();if(owned.isEmpty())kernels.remove(role.instanceId());return kernel.position;
    }
    void domain(ServerPlayer player,CharacterInstance owner,Vec3 center,double radius,MechanicsState state) {
        int stage=state.timer("shore_stage");var existing=domains.get(owner.instanceId());
        if(existing==null||existing.stage!=stage||existing.visual.isRemoved()){
            removeDomain(owner.instanceId());String model="shorekeeper_domain_"+(stage>=3?"gold":stage==2?"purple":"blue");
            domains.put(owner.instanceId(),new Domain(stage,ConstructEntity.spawn(player.serverLevel(),player,owner.instanceId(),model,center.add(0,.045,0),state.timer("shore_domain"),(float)(radius/2),false,"idle")));
        }
        if(player.tickCount%5!=0||state.timer("shore_domain")<=0)return;
        int color=stage>=3?0xffdf8a:stage==2?0xc1a0ff:0x7dcbff;
        var dust=new net.minecraft.core.particles.DustParticleOptions(net.minecraft.world.phys.Vec3.fromRGB24(color).toVector3f(),.8f);
        if(player.tickCount%20==0)for(int i=0;i<10;i++){
            double angle=i*Math.TAU/10+player.tickCount*.015;double r=radius*(.25+.06*(i%5));
            var at=center.add(Math.cos(angle)*r,.18+(i%3)*.08,Math.sin(angle)*r);
            player.serverLevel().sendParticles(dust,at.x,at.y,at.z,1,.03,.03,.03,0);
        }
    }
    void remove(UUID owner) {
        removeOrbit(owner);
        removeDomain(owner);
        var owned=kernels.remove(owner);if(owned!=null)owned.values().forEach(k->k.visual.discard());
    }
    void removeDomain(UUID owner){var field=domains.remove(owner);if(field!=null)field.visual.discard();}
    void clear(){if(demonAura!=null){demonAura.discard();demonAura=null;}for(var owner:List.copyOf(outroOrbits.keySet()))removeOrbit(owner);for(var owner:List.copyOf(kernels.keySet()))remove(owner);for(var owner:List.copyOf(domains.keySet()))removeDomain(owner);}
}
