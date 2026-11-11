package cn.zgnhit.mcwuwa.core;

import java.util.Objects;

/** One server-owned instance per canonical target, including multipart bosses. */
public final class GaugeState {
    public record Snapshot(double toughness, double toughnessMax, long brokenUntil, double offTune,
                           double offTuneMax, boolean mistuned, long accumulationLockedUntil) {}
    public record Change(boolean accepted, boolean toughnessBroken, boolean becameMistuned, Snapshot after) {}
    public record TuneBreak(boolean consumed, boolean toughnessBroken, Snapshot after) {}
    private static final Snapshot DISABLED = new Snapshot(0, 0, 0, 0, 0, false, 0);
    private final EnemyProfile profile;
    private double toughness, offTune;
    private long brokenUntil, accumulationLockedUntil, lastActivity, observedTick;
    private boolean mistuned;
    private boolean tuneBreakReserved;
    public synchronized void pauseOneTick(){brokenUntil=CinematicTimeRules.postpone(brokenUntil,1);accumulationLockedUntil=CinematicTimeRules.postpone(accumulationLockedUntil,1);lastActivity++;}
    public GaugeState(EnemyProfile profile, long now) {
        this.profile = Objects.requireNonNull(profile); Checks.tick(now);
        toughness = enabled() ? profile.tier().toughness : 0; observedTick = lastActivity = now;
    }
    public boolean enabled() { return profile.gaugesEnabled(); }
    private void advance(long now) {
        Checks.tick(now);
        if (now < observedTick) throw new IllegalArgumentException("gauge time moved backwards");
        observedTick = now;
        if (brokenUntil != 0 && now >= brokenUntil) { brokenUntil = 0; toughness = profile.tier().toughness; }
    }
    public synchronized Snapshot snapshot(long now) {
        advance(now);
        if (!enabled()) return DISABLED;
        return new Snapshot(toughness, profile.tier().toughness, brokenUntil, offTune,
            profile.tier().offTune, mistuned, accumulationLockedUntil);
    }
    public synchronized Change onHit(double toughnessUnits, double offTuneUnits, boolean immune, long now) {
        Checks.nonnegative("toughness units", toughnessUnits); Checks.nonnegative("offTune units", offTuneUnits);
        advance(now);
        if (!enabled() || immune) return new Change(false, false, false, snapshot(now));
        lastActivity = now;
        boolean broken = reduceToughness(toughnessUnits, now), newMistune = false;
        if (!mistuned && now >= accumulationLockedUntil) {
            offTune = Math.min(profile.tier().offTune, offTune + offTuneUnits);
            newMistune = offTune >= profile.tier().offTune;
            mistuned = newMistune;
        }
        return new Change(true, broken, newMistune, snapshot(now));
    }
    private boolean reduceToughness(double units, long now) {
        if (brokenUntil > now) return false;
        toughness = Math.max(0, toughness - units);
        if (toughness > 0 || units == 0) return false;
        brokenUntil = Math.addExact(now, profile.tier().brokenTicks); return true;
    }
    /** Verify availability of the damage/response definitions before making this irreversible consumption. */
    public synchronized TuneBreak tryTuneBreak(double squaredPlayerDistance, boolean lineOfSight,
                                              boolean targetAlive, boolean attackAllowed, long now) {
        return tryTuneBreak(squaredPlayerDistance,lineOfSight,targetAlive,attackAllowed,now,()->true);
    }
    /** Reserve before the actual hurt callback. Rejection or exception leaves mistune untouched. */
    public synchronized TuneBreak tryTuneBreak(double squaredPlayerDistance, boolean lineOfSight,
                                              boolean targetAlive, boolean attackAllowed,long now,java.util.function.BooleanSupplier damage) {
        Checks.nonnegative("squared distance", squaredPlayerDistance); advance(now);
        Objects.requireNonNull(damage);
        if (tuneBreakReserved || !enabled() || !mistuned || !lineOfSight || !targetAlive || !attackAllowed || squaredPlayerDistance > 36)
            return new TuneBreak(false, false, snapshot(now));
        tuneBreakReserved=true;
        try {if(!damage.getAsBoolean())return new TuneBreak(false,false,snapshot(now));}
        finally{tuneBreakReserved=false;}
        mistuned = false; offTune = 0; accumulationLockedUntil = Math.addExact(now, 100); lastActivity = now;
        boolean broke = reduceToughness(profile.tier().toughness * 0.25, now);
        return new TuneBreak(true, broke, snapshot(now));
    }
    public synchronized Change parryBonus(long now) {
        advance(now);
        if (!enabled() || !profile.entityId().equals("mc_wuwa:aleph_one")) return new Change(false, false, false, snapshot(now));
        lastActivity = now;
        return new Change(true, reduceToughness(profile.tier().toughness * 0.25, now), false, snapshot(now));
    }
    public synchronized void noteAttack(long now) { advance(now); if (enabled()) lastActivity = now; }
    public synchronized boolean tickCombat(long now, boolean hasValidAttackTarget) {
        advance(now);
        // A player has no enemy encounter lifecycle. Never request clearing legal PvP debuffs.
        if (!enabled()) return false;
        if (!hasValidAttackTarget && now - lastActivity >= 200) { reset(now); return true; }
        return false;
    }
    public synchronized void reset(long now) {
        advance(now); toughness = enabled() ? profile.tier().toughness : 0; offTune = 0; mistuned = false;
        brokenUntil = accumulationLockedUntil = 0;
    }
    public static double vanillaHitUnits(double attackCharge, boolean directHit) {
        Checks.fraction("attack charge", attackCharge); return directHit ? 0.25 * attackCharge : 0;
    }
}
