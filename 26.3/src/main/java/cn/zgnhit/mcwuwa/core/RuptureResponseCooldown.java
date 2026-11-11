package cn.zgnhit.mcwuwa.core;
import java.util.*;

/** Source1509/1209/1210: each responder can damage one recipient only once per8s. */
public final class RuptureResponseCooldown {
    private record Key(UUID owner,UUID target){}
    private final Map<Key,Long> ready=new HashMap<>();
    public boolean available(UUID owner,UUID target,long now){
        return ready.getOrDefault(new Key(owner,target),0L)<=Checks.tick(now);
    }
    /** Called only after damage settlement accepted this response. */
    public void accepted(UUID owner,UUID target,long now){
        Checks.tick(now);ready.values().removeIf(expiry->expiry<=now);
        ready.put(new Key(owner,target),Math.addExact(now,160));
    }
    public void pauseOneTick(){ready.replaceAll((key,expiry)->CinematicTimeRules.postpone(expiry,1));}
    public void clear(){ready.clear();}
}
