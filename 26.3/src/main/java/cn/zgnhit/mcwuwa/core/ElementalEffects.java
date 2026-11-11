package cn.zgnhit.mcwuwa.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Structural six-effect engine. All durations, damage curves and variant rules must be supplied from verified definitions. */
public final class ElementalEffects {
    public enum Kind { SPECTRO_FRAZZLE, AERO_EROSION, GLACIO_CHAFE, GLACIO_FROST, FUSION_BURST, HAVOC_BANE, ELECTRO_FLARE }
    public enum Decay { NONE, MINUS_ONE, FLOOR_HALF }
    public enum CapChange { CLAMP, KEEP_UNTIL_NEXT_APPLICATION }
    public enum Trigger { APPLICATION, PERIODIC, THRESHOLD_BURST, EXPLICIT_DETONATION }
    public record Timing(boolean applicationPulse, boolean pulseAtExpiry, long separateDecayTicks,boolean decayWhenStopped) {
        public Timing {if(separateDecayTicks<0)throw new IllegalArgumentException("decay ticks");}
        public static Timing legacy(){return new Timing(false,false,0,false);}
    }
    @FunctionalInterface public interface DamageFormula {
        /** Return verified native pre-defense damage. The caller owns the required source attribute snapshot. */
        double evaluate(EffectSource source, int layers, int cap, int electroRage, Trigger trigger);
    }
    public record Rules(String definitionId, String evidenceId, Kind kind, int baseCap, long durationTicks,
                        long periodTicks, Decay decay, int electroRageCap, CapChange capChange,
                        DamageFormula damageFormula, Timing timing) {
        public Rules(String id,String evidence,Kind kind,int cap,long duration,long period,Decay decay,int rageCap,CapChange capChange,DamageFormula formula) {
            this(id,evidence,kind,cap,duration,period,decay,rageCap,capChange,formula,Timing.legacy());
        }
        public Rules {
            Checks.id(definitionId); Checks.id(evidenceId); Objects.requireNonNull(kind); Objects.requireNonNull(decay); Objects.requireNonNull(capChange);
            if (baseCap < 1 || durationTicks < 1 || periodTicks < 0 || electroRageCap < 0) throw new IllegalArgumentException("invalid effect rule");
            boolean periodic = kind == Kind.SPECTRO_FRAZZLE || kind == Kind.AERO_EROSION || kind == Kind.ELECTRO_FLARE;
            if (periodic != (periodTicks > 0)) throw new IllegalArgumentException("effect period does not match mechanism");
            if (kind != Kind.HAVOC_BANE && damageFormula == null) throw new MissingDataException(definitionId + ".damage_formula");
            if (kind == Kind.HAVOC_BANE && (damageFormula != null || decay != Decay.NONE)) throw new IllegalArgumentException("Havoc Bane has no autonomous damage/decay");
            Objects.requireNonNull(timing);
            if(timing.applicationPulse && damageFormula==null)throw new IllegalArgumentException("application without damage");
        }
    }
    public record Pulse(String definitionId, Trigger trigger, EffectSource source, int layers,
                        int cap, int electroRage, double nativePreDefenseDamage) {}
    public record Snapshot(String definitionId, Kind kind, int layers, int cap, int electroRage,
                           long expiresAt, long nextTickAt, EffectSource firstSource, boolean executorStopped, int electroRageCap) {}
    public record Applied(int resultingLayers, boolean freezeRequested, int burstCount, List<Pulse> pulses) {}
    private static final class State {
        final Rules rules; final EffectSource source;
        int layers, cap, rage, rageCap; long expires, nextTick, nextDecay, interval; boolean stopped;
        State(Rules rules, EffectSource source, int cap, long now) {
            this.rules = rules; this.source = source; this.cap = cap; rageCap=rules.electroRageCap; expires = Math.addExact(now,rules.durationTicks); nextTick = Math.addExact(now,rules.periodTicks);
            interval=rules.periodTicks;
            nextDecay=rules.timing.separateDecayTicks>0?Math.addExact(now,rules.timing.separateDecayTicks):Long.MAX_VALUE;
        }
        State(State other) {
            rules = other.rules; source = other.source; layers = other.layers; cap = other.cap; rage = other.rage;rageCap=other.rageCap;
            expires = other.expires; nextTick = other.nextTick; nextDecay=other.nextDecay; interval=other.interval; stopped = other.stopped;
        }
    }
    private final Map<String, State> states = new HashMap<>();
    private long observedTick;
    public void pauseOneTick(){
        for(State s:states.values()){s.expires=CinematicTimeRules.postpone(s.expires,1);s.nextTick=CinematicTimeRules.postpone(s.nextTick,1);s.nextDecay=CinematicTimeRules.postpone(s.nextDecay,1);}
        observedTick++;
    }
    public ElementalEffects(long now) { observedTick = Checks.tick(now); }
    private void time(long now) {
        Checks.tick(now); if (now < observedTick) throw new IllegalArgumentException("effect time moved backwards"); observedTick = now;
    }
    /** Each one-stack application is separate; callers must not collapse original multi-hit applications. */
    public Applied apply(Rules rules, EffectSource applier, int effectiveCap, boolean immune, boolean hardControlImmune, long now) {
        return apply(rules,applier,effectiveCap,immune,hardControlImmune,now,rules.durationTicks);
    }
    public Applied apply(Rules rules, EffectSource applier, int effectiveCap, boolean immune, boolean hardControlImmune, long now,long durationTicks) {
        return apply(rules,applier,effectiveCap,immune,hardControlImmune,now,durationTicks,rules.electroRageCap);
    }
    public Applied apply(Rules rules, EffectSource applier, int effectiveCap, boolean immune, boolean hardControlImmune,long now,long durationTicks,int effectiveRageCap) {
        if(durationTicks<1)throw new IllegalArgumentException("duration");
        if(effectiveRageCap<0)throw new IllegalArgumentException("rage cap");
        Objects.requireNonNull(rules); Objects.requireNonNull(applier); if (effectiveCap < 1) throw new IllegalArgumentException("cap"); time(now);
        State existing = states.get(rules.definitionId);
        if (immune) return new Applied(existing == null ? 0 : existing.layers, false, 0, List.of());
        if (existing != null && existing.expires <= now) { states.remove(rules.definitionId); existing = null; }
        if (existing != null && !existing.rules.equals(rules)) throw new IllegalStateException("definition changed inside live effect round");
        // Work on a copy: a missing/invalid damage result must not partially mutate layers.
        State state = existing == null ? new State(rules, applier, effectiveCap, now) : new State(existing);
        state.cap = effectiveCap;state.rageCap=effectiveRageCap;state.rage=Math.min(state.rage,effectiveRageCap);
        if (rules.capChange == CapChange.CLAMP) state.layers = Math.min(state.layers, effectiveCap);
        int rawLayers = Math.addExact(state.layers, 1);
        state.layers = Math.min(effectiveCap, rawLayers); state.expires = Math.addExact(now, durationTicks);
        if(rules.timing.separateDecayTicks>0)state.nextDecay=Math.addExact(now,rules.timing.separateDecayTicks);
        boolean freeze = false; int burst = 0; List<Pulse> pulses = new ArrayList<>();
        switch (rules.kind) {
            case GLACIO_CHAFE -> {
                pulses.add(pulse(state, applier, Trigger.APPLICATION));
                if (state.layers >= effectiveCap) { freeze = !hardControlImmune; state.layers = 0; }
            }
            case GLACIO_FROST -> {
                // The native variant deals cap-based application damage and retains its stacks for
                // Hiyuki's explicit ten-stack consume. Frostbind is a separate sourced control event;
                // do not invent a second automatic freeze that would outlast its native two seconds.
                pulses.add(pulse(state,applier,Trigger.APPLICATION));
            }
            case FUSION_BURST -> {
                if (state.layers >= effectiveCap) { pulses.add(pulse(state, applier, Trigger.THRESHOLD_BURST)); state.layers = 0; burst = 1; }
            }
            case ELECTRO_FLARE -> {
                if (rawLayers > effectiveCap) state.rage = Math.min(state.rageCap, state.rage + rawLayers - effectiveCap);
            }
            default -> { if(rules.timing.applicationPulse)pulses.add(pulse(state,applier,Trigger.APPLICATION)); }
        }
        if (state.layers == 0) states.remove(rules.definitionId); else states.put(rules.definitionId, state);
        return new Applied(state.layers, freeze, burst, List.copyOf(pulses));
    }
    private static Pulse pulse(State state, EffectSource source, Trigger trigger) {
        if (state.rules.damageFormula == null) throw new MissingDataException(state.rules.definitionId + ".damage_formula");
        double amount = state.rules.damageFormula.evaluate(source, state.layers, state.cap, state.rage, trigger);
        Checks.nonnegative("effect damage", amount);
        return new Pulse(state.rules.definitionId, trigger, source, state.layers, state.cap, state.rage, amount);
    }
    /** Process in stable definition order. Attribute changes between ticks should be fed by the server scheduler. */
    public List<Pulse> tick(long now) {
        return tick(now,false,1);
    }
    /** Server content qualification; the ordinary no-argument schedule remains unchanged. */
    public List<Pulse> tick(long now,boolean preserveElectroLayers,int aeroPeriodDivisor) {
        if(aeroPeriodDivisor<1||aeroPeriodDivisor>2)throw new IllegalArgumentException("aero cadence divisor");
        time(now); List<Pulse> result = new ArrayList<>();
        List<String> ids = new ArrayList<>(states.keySet()); ids.sort(String::compareTo);
        for (String id : ids) {
            State original = states.get(id), state = new State(original); List<Pulse> local = new ArrayList<>();
            long interval=state.rules.kind==Kind.AERO_EROSION?Math.max(1,state.rules.periodTicks/aeroPeriodDivisor):state.rules.periodTicks;
            if(interval!=state.interval) {
                // Keep the previous pulse's anchor. A late eligibility change can make only
                // the current pulse due now; it never replays damage for past server ticks.
                state.nextTick=Math.max(now,state.nextTick-state.interval+interval);state.interval=interval;
            }
            while(state.layers>0) {
                long due=Math.min(state.rules.periodTicks>0?state.nextTick:Long.MAX_VALUE,state.nextDecay);
                if(due>now||due>state.expires||due==state.expires&&!state.rules.timing.pulseAtExpiry||due==Long.MAX_VALUE)break;
                if(state.rules.periodTicks>0&&state.nextTick==due) {
                    if(!state.stopped)local.add(pulse(state,state.source,Trigger.PERIODIC));
                    // Source logout stops damage, not the target's normal timed layer decay.
                    if(!state.stopped||state.rules.timing.decayWhenStopped) {
                        if(!(preserveElectroLayers&&state.rules.kind==Kind.ELECTRO_FLARE))state.layers=switch(state.rules.decay){case NONE->state.layers;case MINUS_ONE->Math.max(0,state.layers-1);case FLOOR_HALF->state.layers/2;};state.rage=0;
                    }
                    state.nextTick=Math.addExact(state.nextTick,interval);
                }
                if(state.nextDecay==due) {state.layers=Math.max(0,state.layers-1);state.nextDecay=Math.addExact(state.nextDecay,state.rules.timing.separateDecayTicks);}
            }
            if (state.expires <= now || state.layers == 0) states.remove(id); else states.put(id, state);
            result.addAll(local);
        }
        return List.copyOf(result);
    }
    public Optional<Pulse> detonate(String definitionId, EffectSource triggerer, boolean consume, long now) {
        Checks.id(definitionId); Objects.requireNonNull(triggerer); time(now); State state = states.get(definitionId);
        if (state == null || state.expires <= now || state.layers == 0) return Optional.empty();
        Pulse pulse = pulse(state, triggerer, Trigger.EXPLICIT_DETONATION);
        if (consume) states.remove(definitionId); return Optional.of(pulse);
    }
    /** Phoebe's Silent Prayer affects the next frazzle cycle after a real
     * periodic pulse; applying/refreshing a layer does not postpone it. */
    public void extendNextFrazzleCycle(String definitionId){
        State current=states.get(Checks.id(definitionId));
        if(current==null||current.rules.kind!=Kind.SPECTRO_FRAZZLE||current.rules.periodTicks<=0)return;
        var next=new State(current);next.nextTick=Math.addExact(next.nextTick,(next.rules.periodTicks+1)/2);states.put(definitionId,next);
    }
    /** Settle using the live cap without adding layers, refreshing expiry or rewriting first source. */
    public Optional<Pulse> detonateAtCap(String definitionId,EffectSource triggerer,long now) {
        Checks.id(definitionId);Objects.requireNonNull(triggerer);time(now);var stored=states.get(definitionId);
        if(stored==null||stored.expires<=now||stored.layers==0||stored.stopped)return Optional.empty();
        var calculation=new State(stored);calculation.layers=calculation.cap;
        return Optional.of(pulse(calculation,triggerer,Trigger.EXPLICIT_DETONATION));
    }
    /** Consume existing live stacks without detonating, refreshing, changing ownership or emitting damage. */
    public int consumeLayers(String definitionId, int amount, long now) {
        Checks.id(definitionId); if (amount < 0) throw new IllegalArgumentException("negative layer consumption");
        time(now); State state = states.get(definitionId);
        if (state == null) return 0;
        if (state.expires <= now) { states.remove(definitionId); return 0; }
        int consumed = Math.min(amount, state.layers); state.layers -= consumed;
        if (state.layers == 0) states.remove(definitionId);
        return consumed;
    }
    public void updateCap(String definitionId, int newCap) {
        if (newCap < 1) throw new IllegalArgumentException("cap"); State state = states.get(Checks.id(definitionId)); if (state == null) return;
        state.cap = newCap; if (state.rules.capChange == CapChange.CLAMP) state.layers = Math.min(newCap, state.layers);
    }
    public void updateElectroRageCap(String definitionId,int newCap) {
        if(newCap<0)throw new IllegalArgumentException("rage cap");State state=states.get(Checks.id(definitionId));if(state==null)return;
        if(state.rules.kind!=Kind.ELECTRO_FLARE)throw new IllegalArgumentException("rage cap for non-electro effect");
        state.rageCap=newCap;state.rage=Math.min(state.rage,newCap);
    }
    /** Hsin consumes Rage independently of Flare stacks; preserve the round, clock and first source. */
    public int consumeElectroRage(String definitionId,long now) {
        time(now);State state=states.get(Checks.id(definitionId));
        if(state==null||state.expires<=now||state.stopped||state.rules.kind!=Kind.ELECTRO_FLARE)return 0;
        int amount=state.rage;state.rage=0;return amount;
    }
    public void stopOwnedExecutors(UUID playerId) {
        Objects.requireNonNull(playerId); for (State state : states.values()) if (state.source.playerId().equals(playerId)) state.stopped = true;
    }
    public void stopWeaponExecutors(UUID weaponId) {
        Objects.requireNonNull(weaponId);for(State state:states.values())if(state.source.weaponInstanceId().equals(weaponId))state.stopped=true;
    }
    public Optional<Snapshot> snapshot(String definitionId) {
        State s = states.get(definitionId);
        return s == null ? Optional.empty() : Optional.of(new Snapshot(definitionId, s.rules.kind, s.layers, s.cap, s.rage, s.expires, s.nextTick, s.source, s.stopped,s.rageCap));
    }
    public double havocDefenseReduction() {
        int layers = states.values().stream().filter(s -> s.rules.kind == Kind.HAVOC_BANE && s.expires > observedTick).mapToInt(s -> s.layers).sum();
        return layers * 0.02;
    }
    public void clear() { states.clear(); }
    public void clear(String definitionId) { states.remove(Checks.id(definitionId)); }
}
