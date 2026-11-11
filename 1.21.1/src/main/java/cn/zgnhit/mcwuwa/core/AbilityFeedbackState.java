package cn.zgnhit.mcwuwa.core;

import java.util.*;

/** Read-only client cue lifetime. No input eligibility, resources or combat effects live here. */
public final class AbilityFeedbackState {
    public record Signal(String action,String holdAction,boolean enhanced,boolean ready) { }
    private static final Set<String> IDS=Set.of("basic","skill","liberation","echo");
    private final Map<String,Signal> previous=new HashMap<>();
    private final Map<String,Long> pulses=new HashMap<>();
    private String owner="";private long lastTick=Long.MIN_VALUE;
    public void update(String instance,long tick,Map<String,Signal> signals){
        if(!Objects.equals(owner,instance)||tick<lastTick){clear();owner=instance;}
        lastTick=tick;
        for(String id:IDS){
            var next=signals.get(id);var old=previous.get(id);
            if(next!=null&&next.enhanced()&&next.ready()&&(old==null||!old.ready()||!old.enhanced()||!Objects.equals(old.action(),next.action())||!Objects.equals(old.holdAction(),next.holdAction())))pulses.put(id,tick+24);
            if(next==null){previous.remove(id);pulses.remove(id);}else previous.put(id,next);
        }
        pulses.values().removeIf(end->end<=tick);
    }
    public void accepted(String action,long tick){
        previous.forEach((id,signal)->{if(action.equals(signal.action())||action.equals(signal.holdAction()))pulses.put(id,tick+10);});
    }
    public double strength(String id,long tick){return Math.clamp((pulses.getOrDefault(id,tick)-tick)/24d,0,1);}
    public void clear(){previous.clear();pulses.clear();owner="";lastTick=Long.MIN_VALUE;}
}
