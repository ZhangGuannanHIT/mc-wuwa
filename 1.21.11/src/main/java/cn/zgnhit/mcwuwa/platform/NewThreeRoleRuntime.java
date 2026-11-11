package cn.zgnhit.mcwuwa.platform;

import cn.zgnhit.mcwuwa.core.CharacterInstance;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Actual-target Galbrena stacks. No client snapshot or missed attack creates a stack. */
public final class NewThreeRoleRuntime {
    private record Key(UUID owner,UUID target){}
    private static final class Doom {int layers;long expires;final Map<String,Long> ready=new HashMap<>();}
    private static final Map<ServerPlayer,Map<Key,Doom>> DOOM=new WeakHashMap<>();
    private NewThreeRoleRuntime(){}
    public static void confirmedHit(ServerPlayer player,CharacterInstance owner,LivingEntity target,String action){
        if(!owner.character().weaponPath().equals("lux_and_umbra")||target==null||!action.startsWith("galbrena/"))return;
        String local=action.substring(action.indexOf('/')+1);
        if(!local.matches("(?:demon_)?(?:basic_\\d+|heavy_\\d+|air(?:_rain)?|dodge_counter|skill)|skill_ascend|intro|liberation|hellwalk"))return;
        String kind=local.startsWith("demon_basic_")?"demon_basic":local.startsWith("basic_")?"basic":
            local.startsWith("demon_heavy_")?"demon_heavy":local.startsWith("heavy_")?"heavy":
            Set.of("skill","demon_skill").contains(local)?"approach":
            Set.of("air","air_rain").contains(local)?"air":Set.of("demon_air","demon_air_rain").contains(local)?"demon_air":local;
        long now=PlatformRuntime.time(player);var all=DOOM.computeIfAbsent(player,p->new HashMap<>());
        all.entrySet().removeIf(e->e.getValue().expires<=now);
        var mark=all.computeIfAbsent(new Key(owner.instanceId(),target.getUUID()),key->new Doom());
        if(mark.ready.getOrDefault(kind,0L)>now)return;
        mark.ready.put(kind,now+100);mark.layers=Math.min(4,mark.layers+1);mark.expires=now+110;
    }
    public static double amplification(ServerPlayer player,CharacterInstance owner,LivingEntity target){
        if(target==null)return 1;var all=DOOM.get(player);if(all==null)return 1;
        var mark=all.get(new Key(owner.instanceId(),target.getUUID()));
        return mark==null||mark.expires<=PlatformRuntime.time(player)?1:1+mark.layers*.05;
    }
    /** Read-only target HUD; owners' stacks never sum or modify each other. */
    public static int visibleLayers(ServerPlayer player,LivingEntity target){
        var all=DOOM.get(player);if(all==null||target==null)return 0;
        long now=PlatformRuntime.time(player);
        return all.entrySet().stream().filter(e->e.getKey().target.equals(target.getUUID())&&e.getValue().expires>now)
            .mapToInt(e->e.getValue().layers).max().orElse(0);
    }
    static void pauseOneTick(ServerPlayer player){
        var marks=DOOM.get(player);if(marks==null)return;
        marks.values().forEach(mark->{
            mark.expires=cn.zgnhit.mcwuwa.core.CinematicTimeRules.postpone(mark.expires,1);
            mark.ready.replaceAll((kind,ready)->cn.zgnhit.mcwuwa.core.CinematicTimeRules.postpone(ready,1));
        });
    }
    public static void clear(ServerPlayer player){DOOM.remove(player);}
}
