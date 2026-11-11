package cn.zgnhit.mcwuwa.core;

import java.util.*;

/** Shared target shifts and interference; converting them never consumes the independent toughness/off-tune gauge. */
public final class TuneInterference {
    public enum Mode {
        RUPTURE("rupture_shifting","rupture_interference",160), STRAIN("strain_shifting","strain_interference",600);
        public final String shiftId,interferenceId; public final long interferenceTicks;
        Mode(String shift,String interference,long ticks){shiftId=shift;interferenceId=interference;interferenceTicks=ticks;}
    }
    public record Status(Mode mode,EffectSource applier,long expiresAt) {
        public Status {Objects.requireNonNull(mode);Objects.requireNonNull(applier);Checks.tick(expiresAt);}
    }
    public record Activation(Mode mode,EffectSource shiftSource,EffectSource breaker,long expiresAt) { }
    public record View(String id,EffectSource source,long expiresAt,int layers,int cap) {
        public View(String id,EffectSource source,long expiresAt){this(id,source,expiresAt,1,1);}
    }
    private final Map<Mode,Status> shifts=new EnumMap<>(Mode.class),interference=new EnumMap<>(Mode.class);
    private int strainLayers, strainCap=1;
    public void pauseOneTick(){for(var map:List.of(shifts,interference))map.replaceAll((mode,s)->new Status(s.mode,s.applier,CinematicTimeRules.postpone(s.expiresAt,1)));}
    public void applyShift(Mode mode,EffectSource source,long now,long duration) {
        Checks.tick(now);if(duration<1)throw new IllegalArgumentException("shift duration");
        var next=new Status(mode,source,Math.addExact(now,duration));
        // Shifting variants overwrite one another; already converted
        // interference has its own lifetime and must remain untouched.
        shifts.clear();shifts.put(mode,next);
    }
    public List<Activation> onBreak(EffectSource breaker,boolean damageAccepted,long now) {
        return onBreak(breaker,damageAccepted,now,1);
    }
    public List<Activation> onBreak(EffectSource breaker,boolean damageAccepted,long now,int strainCap) {
        Checks.tick(now);Objects.requireNonNull(breaker);expire(now);if(!damageAccepted)return List.of();
        var activated=new ArrayList<Activation>();
        for(var mode:Mode.values()) {
            var shift=shifts.remove(mode);if(shift==null)continue;
            long expiry=Math.addExact(now,mode.interferenceTicks);
            if(mode==Mode.STRAIN)applyStrain(shift.applier(),1,now,mode.interferenceTicks,strainCap);
            else interference.put(mode,new Status(mode,shift.applier(),expiry));activated.add(new Activation(mode,shift.applier(),breaker,expiry));
        }
        return List.copyOf(activated);
    }
    /** MC refresh policy: adding layers refreshes the whole shared stack lifetime. */
    public int applyStrain(EffectSource source,int layers,long now,long duration,int cap) {
        Objects.requireNonNull(source);Checks.tick(now);
        if(layers<1||duration<1||cap<1)throw new IllegalArgumentException("strain layers/duration/cap");
        expire(now);strainCap=cap;strainLayers=Math.min(cap,strainLayers+layers);
        interference.put(Mode.STRAIN,new Status(Mode.STRAIN,source,Math.addExact(now,duration)));
        return strainLayers;
    }
    public int layers(Mode mode,long now){return interfered(mode,now)?mode==Mode.STRAIN?strainLayers:1:0;}
    /** Refresh an existing interference without adding layers or changing its applier. */
    public boolean refresh(Mode mode,long now,long duration) {
        Checks.tick(now);if(duration<1)throw new IllegalArgumentException("interference duration");
        expire(now);var current=interference.get(mode);if(current==null)return false;
        interference.put(mode,new Status(mode,current.applier(),Math.addExact(now,duration)));return true;
    }
    public boolean shifted(Mode mode,long now){var value=shifts.get(mode);return value!=null&&value.expiresAt()>Checks.tick(now);}
    public boolean interfered(Mode mode,long now){var value=interference.get(mode);return value!=null&&value.expiresAt()>Checks.tick(now);}
    public List<View> snapshots(long now) {
        Checks.tick(now);var result=new ArrayList<View>();
        shifts.values().stream().filter(s->s.expiresAt()>now).forEach(s->result.add(new View(s.mode().shiftId,s.applier(),s.expiresAt())));
        interference.values().stream().filter(s->s.expiresAt()>now).forEach(s->result.add(new View(s.mode().interferenceId,s.applier(),s.expiresAt(),s.mode()==Mode.STRAIN?strainLayers:1,s.mode()==Mode.STRAIN?strainCap:1)));
        result.sort(Comparator.comparing(View::id));return List.copyOf(result);
    }
    public void expire(long now){Checks.tick(now);shifts.values().removeIf(s->s.expiresAt()<=now);interference.values().removeIf(s->s.expiresAt()<=now);if(!interference.containsKey(Mode.STRAIN))strainLayers=0;}
    public void clear(){shifts.clear();interference.clear();strainLayers=0;strainCap=1;}
}
