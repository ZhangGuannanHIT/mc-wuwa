package cn.zgnhit.mcwuwa.core;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-target original six-second ember stacks, with separate expiry per layer.
 * The host settles Spectro Frazzle damage first and supplies only accepted stacks.
 * This ledger does not create damage, restore expired stacks, or award energy twice. */
public final class ZaniEmberLedger {
    public record Conversion(int convertedLayers,int storedLayers,double heliacalEmberGain) { }
    public record Outro(int consumedLayers,double attackMultiplier) { }
    private final Map<UUID,ArrayDeque<Long>> targets=new HashMap<>();
    public void pauseOneTick(){targets.replaceAll((id,times)->{var shifted=new ArrayDeque<Long>();times.forEach(t->shifted.add(CinematicTimeRules.postpone(t,1)));return shifted;});}

    public Conversion convert(UUID target,int acceptedFrazzleLayers,long now){
        java.util.Objects.requireNonNull(target);Checks.tick(now);
        if(acceptedFrazzleLayers<0)throw new IllegalArgumentException("negative frazzle layers");
        prune(target,now);
        if(acceptedFrazzleLayers==0)return new Conversion(0,0,0);
        var layers=targets.computeIfAbsent(target,key->new ArrayDeque<>());
        int added=Math.min(acceptedFrazzleLayers,60-layers.size());
        long expires=Math.addExact(now,120);
        for(int i=0;i<added;i++)layers.addLast(expires);
        // Energy is earned by the conversion event, not by the number of spare
        // stack slots. Its 100/150 cap belongs to the character's resource layer.
        return new Conversion(acceptedFrazzleLayers,added,acceptedFrazzleLayers*5d);
    }
    /** Suppression applies one ember directly, without the conversion energy grant. */
    public boolean applySuppression(UUID target,long now){
        java.util.Objects.requireNonNull(target);Checks.tick(now);prune(target,now);
        var layers=targets.computeIfAbsent(target,key->new ArrayDeque<>());
        if(layers.size()>=60)return false;
        layers.addLast(Math.addExact(now,120));return true;
    }
    public int stacks(UUID target,long now){prune(target,now);var layers=targets.get(target);return layers==null?0:layers.size();}
    /** Snapshot before removal, so the departure strike receives the full bonus. */
    public Outro consumeOutro(UUID target,long now){
        int layers=stacks(target,now);targets.remove(target);
        return new Outro(layers,1.5*(1+layers*.1));
    }
    public void tick(long now){Checks.tick(now);for(var target:java.util.List.copyOf(targets.keySet()))prune(target,now);}
    public void remove(UUID target){targets.remove(target);}
    public void clear(){targets.clear();}
    private void prune(UUID target,long now){
        Checks.tick(now);var layers=targets.get(target);if(layers==null)return;
        while(!layers.isEmpty()&&layers.getFirst()<=now)layers.removeFirst();
        if(layers.isEmpty())targets.remove(target);
    }
}
