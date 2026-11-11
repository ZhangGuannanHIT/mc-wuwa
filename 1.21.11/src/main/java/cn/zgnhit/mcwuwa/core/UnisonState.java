package cn.zgnhit.mcwuwa.core;

import java.util.*;

/** One response contribution per original weapon, plus Hsin's nonstacking extra layer.
 * Online clock only; no offline persistence or reward inferred from selecting a mode. */
public final class UnisonState {
    private record Grant(int layers,long expires,long revision){ }
    private long revision;
    private final Map<UUID,Long> clearedRecipients=new HashMap<>();
    private final Map<UUID,Grant> grants=new HashMap<>();
    private final Set<UUID> observed=new HashSet<>(),lamps=new HashSet<>();
    private final Map<UUID,Grant> hsinBonuses=new HashMap<>();
    public void responded(UUID source,long now,boolean hsinBonus) {
        responded(source,now,hsinBonus?1:0);
    }
    public void responded(UUID source,long now,int extraLayers) {
        if(extraLayers<0||extraLayers>2)throw new IllegalArgumentException("Hsin extra layers");
        Checks.tick(now);grants.put(Objects.requireNonNull(source),new Grant(1,now+600,++revision));
        if(extraLayers>0)grantHsinBonus(source,now,extraLayers);
    }
    /** The supporting Hsin owns the extra layer, even when another role responds. */
    public void grantHsinBonus(UUID owner,long now,int layers) {
        if(layers<1||layers>2)throw new IllegalArgumentException("Hsin extra layers");
        Checks.tick(now);hsinBonuses.put(Objects.requireNonNull(owner),new Grant(layers,now+600,++revision));
    }
    public int stacks(long now,int cap){return stacksAfter(now,cap,-1);}
    public int stacks(long now,int cap,UUID recipient){return stacksAfter(now,cap,clearedRecipients.getOrDefault(recipient,-1L));}
    private int stacksAfter(long now,int cap,long after){Checks.tick(now);return Math.min(cap,grants.values().stream().filter(g->g.expires>now&&g.revision>after).mapToInt(Grant::layers).sum()+hsinBonuses.values().stream().filter(g->g.expires>now&&g.revision>after).mapToInt(Grant::layers).max().orElse(0));}
    /** Suoming's contract removes this recipient's existing buff, not teammates' grants. */
    public void clearRecipient(UUID recipient){clearedRecipients.put(Objects.requireNonNull(recipient),revision);}
    public void pauseOneTick(){
        grants.replaceAll((id,g)->new Grant(g.layers,CinematicTimeRules.postpone(g.expires,1),g.revision));
        hsinBonuses.replaceAll((id,g)->new Grant(g.layers,CinematicTimeRules.postpone(g.expires,1),g.revision));
    }
    public void retainSources(Set<UUID> sources,long now){grants.entrySet().removeIf(e->!sources.contains(e.getKey())||e.getValue().expires<=now);hsinBonuses.entrySet().removeIf(e->!sources.contains(e.getKey())||e.getValue().expires<=now);}
    public void retainEquipped(Set<UUID> sources){observed.retainAll(sources);lamps.retainAll(sources);}
    public void remove(UUID source){grants.remove(source);observed.remove(source);lamps.remove(source);hsinBonuses.remove(source);clearRecipient(source);}
    public void observe(UUID source,boolean holding,boolean eligibleForLamp){
        if(!holding){observed.remove(source);return;}
        if(observed.add(source)&&eligibleForLamp)lamps.add(source);
    }
    public boolean hasLamp(UUID source){return lamps.contains(source);}
    public void clearLamps(){observed.clear();lamps.clear();}
    public void clear(){grants.clear();observed.clear();lamps.clear();hsinBonuses.clear();clearedRecipients.clear();revision=0;}
}
