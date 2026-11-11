package cn.zgnhit.mcwuwa.core;

import java.util.EnumMap;
import java.util.List;
import java.util.Optional;

/** Weapon-owned crane positions and liberation trigger budget.
 * The platform must accept the collision-limited movement action before consuming
 * an image; reaching the full endpoint is not a prerequisite for a blocked dash.
 * This module does not invent a teleport range or the undocumented resource gains. */
public final class ZhezhiCraneLedger {
    public enum Slot { LEFT, CENTER, RIGHT }
    public record Anchor(Slot slot,String dimension,double x,double y,double z,boolean airborne,long expiresAt) {
        public Anchor {
            java.util.Objects.requireNonNull(slot);Checks.id(dimension);
            Checks.finite("crane x",x);Checks.finite("crane y",y);Checks.finite("crane z",z);Checks.tick(expiresAt);
        }
    }
    public record InkCrane(int ordinal,boolean extraFifthChain) { }
    public record Teleport(boolean enhanced,boolean resetsAirDodge) { }
    private final EnumMap<Slot,Anchor> images=new EnumMap<>(Slot.class);
    private long liberationEnds,damageWindowEnds,nextInkAt,nextTriggerAt;
    private int remaining,emitted;
    private boolean fifthChain;
    private int insight;
    private long insightEnds;
    public void pauseOneTick(){
        liberationEnds=CinematicTimeRules.postpone(liberationEnds,1);damageWindowEnds=CinematicTimeRules.postpone(damageWindowEnds,1);nextInkAt=CinematicTimeRules.postpone(nextInkAt,1);nextTriggerAt=CinematicTimeRules.postpone(nextTriggerAt,1);insightEnds=CinematicTimeRules.postpone(insightEnds,1);
        images.replaceAll((slot,a)->new Anchor(a.slot,a.dimension,a.x,a.y,a.z,a.airborne,CinematicTimeRules.postpone(a.expiresAt,1)));
    }

    public Anchor place(Slot slot,String dimension,double x,double y,double z,boolean airborne,long now){
        Checks.tick(now);
        var image=new Anchor(slot,dimension,x,y,z,airborne,Math.addExact(now,300));
        images.put(slot,image);return image;
    }
    public List<Anchor> images(String dimension,long now){
        expire(now);return images.values().stream().filter(a->a.dimension().equals(dimension)).toList();
    }
    /** A previous selection cannot consume a replacement image in the same slot. */
    public boolean consume(Anchor selected,String dimension,long now){
        expire(now);
        if(!selected.dimension().equals(dimension)||images.get(selected.slot())!=selected)return false;
        images.remove(selected.slot());return true;
    }
    public Optional<Anchor> image(Slot slot,String dimension,long now){
        expire(now);return Optional.ofNullable(images.get(slot)).filter(a->a.dimension().equals(dimension));
    }
    /** Commit after the server has accepted the movement action and the cast itself.
     * Failed/stale selections spend neither an image nor insight. */
    public Optional<Teleport> teleport(Anchor selected,String dimension,long now){
        if(!consume(selected,dimension,now))return Optional.empty();
        boolean enhanced=insight(now)==2;
        insight=enhanced?0:insight+1;insightEnds=enhanced?0:Math.addExact(now,160);
        return Optional.of(new Teleport(enhanced,selected.airborne()));
    }
    public int insight(long now){Checks.tick(now);if(now>=insightEnds)insight=0;return insight;}
    public void beginLiberation(int chain,long now){
        if(chain<0||chain>6)throw new IllegalArgumentException("chain");Checks.tick(now);
        liberationEnds=Math.addExact(now,600);damageWindowEnds=0;nextInkAt=nextTriggerAt=now;
        remaining=chain>=2?27:21;emitted=0;fifthChain=chain>=5;
    }
    /** Called only for accepted damage by the owner's currently active teammate.
     * The ink crane and C5 bonus events explicitly pass false to prevent recursion. */
    public boolean damagedByActiveTeammate(long now,boolean qualifyingRootDamage){
        Checks.tick(now);
        if(!qualifyingRootDamage||!active(now)||now<nextTriggerAt)return false;
        nextTriggerAt=Math.addExact(now,20);
        damageWindowEnds=Math.min(liberationEnds,Math.addExact(now,60));
        return true;
    }
    /** One opportunity per second; never catches up a backlog after a tick pause. */
    public Optional<InkCrane> pollInkCrane(long now){
        Checks.tick(now);
        if(!active(now)||now>=damageWindowEnds||now<nextInkAt)return Optional.empty();
        nextInkAt=Math.addExact(now,20);remaining--;emitted++;
        return Optional.of(new InkCrane(emitted,fifthChain&&emitted%3==0));
    }
    public boolean active(long now){Checks.tick(now);return now<liberationEnds&&remaining>0;}
    public int remaining(long now){return active(now)?remaining:0;}
    public void clear(){images.clear();liberationEnds=damageWindowEnds=nextInkAt=nextTriggerAt=insightEnds=0;remaining=emitted=insight=0;fifthChain=false;}
    private void expire(long now){Checks.tick(now);images.values().removeIf(a->now>=a.expiresAt());}
}
