package cn.zgnhit.mcwuwa.platform;

import cn.zgnhit.mcwuwa.content.mechanics.MechanicsState;
import cn.zgnhit.mcwuwa.core.*;
import cn.zgnhit.mcwuwa.platform.entity.ConstructEntity;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Three stationary, weapon-owned sword shadows.12-block recovery is explicit MC geometry. */
final class SwordShadowRuntime {
    private record Shadow(Vec3 point,long expires,ConstructEntity visual){ }
    private final Map<UUID,Map<String,Shadow>> shadows=new HashMap<>();
    private final Map<String,ConstructEntity> inherited=new HashMap<>();
    private UUID inheritedOwner;
    void pauseOneTick(){shadows.values().forEach(values->values.replaceAll((id,s)->new Shadow(s.point,s.expires+1,s.visual)));}
    void spawn(ServerPlayer player,CharacterInstance owner,String kind,int duration){
        var forward=player.getLookAngle().multiply(1,0,1).normalize();if(forward.lengthSqr()<.001)forward=Vec3.directionFromRotation(0,player.getYRot());
        double lateral=switch(kind){case "human_sword"->-1.1;case "divine_sword"->0;default->1.1;};
        var point=player.position().add(forward.scale(2.2)).add(new Vec3(-forward.z,0,forward.x).scale(lateral));
        var hit=player.level().clip(new ClipContext(point.add(0,2,0),point.add(0,-12,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        if(hit.getType()==HitResult.Type.BLOCK)point=hit.getLocation().add(0,.08,0);
        var values=shadows.computeIfAbsent(owner.instanceId(),id->new HashMap<>());var old=values.remove(kind);if(old!=null&&!old.visual.isRemoved())old.visual.discard();
        String model=switch(kind){case "human_sword"->"cartethyia_shadow_human";case "divine_sword"->"cartethyia_shadow_divine";default->"cartethyia_shadow_outsider";};
        var visual=ConstructEntity.spawn(player.serverLevel(),player,owner.instanceId(),model,point,duration,1.0f,false,"idle");
        values.put(kind,new Shadow(point,PlatformRuntime.time(player)+duration,visual));
    }
    MechanicsState prepare(ServerPlayer player,CharacterInstance owner,MechanicsState state){
        if(owner.character()!=CharacterId.CARTETHYIA)return state;
        var timers=new HashMap<>(state.timers());timers.put("cart_shadow_geometry",1);
        for(String kind:List.of("human_sword","divine_sword","outsider_sword")) {
            String key="range_"+kind;timers.remove(key);var shadow=shadows.getOrDefault(owner.instanceId(),Map.of()).get(kind);
            if(shadow!=null&&shadow.expires>PlatformRuntime.time(player)&&state.timer(kind)>0&&player.position().distanceToSqr(shadow.point)<=144)
                timers.put(key,1);
        }
        return new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),state.nextBasic(),state.resources(),timers);
    }
    void collect(CharacterInstance owner,String kind){var values=shadows.get(owner.instanceId());if(values==null)return;var shadow=values.remove(kind);if(shadow!=null&&!shadow.visual.isRemoved())shadow.visual.discard();}
    void tick(ServerPlayer player,java.util.function.Function<CharacterInstance,MechanicsState> stateOf){
        var team=ModAttachments.team(player).state();var live=new HashSet<UUID>();for(int i=0;i<3;i++)team.at(i).filter(CharacterInstance::alive).ifPresent(role->live.add(role.instanceId()));
        for(var entry:List.copyOf(shadows.entrySet())){
            entry.getValue().entrySet().removeIf(e->{if(live.contains(entry.getKey())&&e.getValue().expires>PlatformRuntime.time(player))return false;if(!e.getValue().visual.isRemoved())e.getValue().visual.discard();return true;});
            if(entry.getValue().isEmpty())shadows.remove(entry.getKey());
        }
        var active=team.wuwaMode()?team.active().filter(CharacterInstance::alive).filter(role->role.character()==CharacterId.CARTETHYIA).orElse(null):null;
        if(active==null){clearInherited();return;}
        if(!active.instanceId().equals(inheritedOwner)){clearInherited();inheritedOwner=active.instanceId();}
        var state=stateOf.apply(active);
        var forward=Vec3.directionFromRotation(0,player.getYRot());
        var left=new Vec3(forward.z,0,-forward.x);
        String[] rights={"human_right","divine_intent","outsider_power"};
        for(int i=0;i<rights.length;i++){
            String right=rights[i];var visual=inherited.get(right);
            if(state.timer(right)<=0){if(visual!=null){visual.discard();inherited.remove(right);}continue;}
            // Small inherited blades sit beside the body. They are visual only;
            // the stationary originals retain the authoritative recovery position.
            double angle=Math.toRadians(45+i*45);
            var point=player.position().add(left.scale(.72+.22*Math.sin(angle))).add(forward.scale(-.22))
                .add(0,1.05+.22*Math.cos(angle),0);
            if(visual==null||visual.isRemoved()||PlatformRuntime.time(player)>=visual.startTick()+visual.durationTicks()-2){
                if(visual!=null)visual.discard();
                visual=ConstructEntity.spawn(player.serverLevel(),player,active.instanceId(),"cartethyia_inherited_blade",point,12000,.20f,false,right);
                inherited.put(right,visual);
            }
            visual.setPos(point);visual.setYRot(player.getYRot());
        }
    }
    private void clearInherited(){inherited.values().forEach(ConstructEntity::discard);inherited.clear();inheritedOwner=null;}
    void remove(UUID owner){var values=shadows.remove(owner);if(values!=null)values.values().forEach(s->{if(!s.visual.isRemoved())s.visual.discard();});if(owner.equals(inheritedOwner))clearInherited();}
    void clear(){for(var owner:List.copyOf(shadows.keySet()))remove(owner);clearInherited();}
}
