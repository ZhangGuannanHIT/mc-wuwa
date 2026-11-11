package cn.zgnhit.mcwuwa.core;
import java.util.*;

/** Authoritative overlapping shot windows; IDs are cleared when the client changes world. */
public final class CinematicHazardPauseState {
    private record Window(long start,long end,Set<Integer> entities){}
    private final Map<UUID,Window> windows=new HashMap<>();
    public void accept(UUID owner,long start,long end,Collection<Integer> entities){
        if(end<=start||entities.isEmpty())windows.remove(owner);
        else windows.put(owner,new Window(start,end,Set.copyOf(entities)));
    }
    public boolean frozen(int entity,long tick){
        return windows.values().stream().anyMatch(w->tick>=w.start&&tick<w.end&&w.entities.contains(entity));
    }
    public void expire(long tick){windows.values().removeIf(w->tick>=w.end);}
    public void clear(){windows.clear();}
}
