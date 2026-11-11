package cn.zgnhit.mcwuwa.core;

import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;

/** Accepted server-side press only; preview/validation never consumes a legal outstanding charge. */
public final class ChargeState {
    public enum Readiness { NO_START, PARTIAL, FULL }
    private UUID owner;
    private long startedAt;
    public boolean active() { return owner != null; }
    public boolean begin(UUID instance, long now) {
        Objects.requireNonNull(instance); Checks.tick(now);
        if (active()) return false;
        owner = instance; startedAt = now; return true;
    }
    public Readiness readiness(UUID instance, long now, OptionalInt verifiedFullTicks) {
        Objects.requireNonNull(instance); Objects.requireNonNull(verifiedFullTicks); Checks.tick(now);
        if (verifiedFullTicks.isPresent() && verifiedFullTicks.getAsInt() <= 0) throw new IllegalArgumentException("full charge ticks must be positive");
        if (!instance.equals(owner) || now < startedAt) return Readiness.NO_START;
        return verifiedFullTicks.isPresent() && now - startedAt >= verifiedFullTicks.getAsInt() ? Readiness.FULL : Readiness.PARTIAL;
    }
    public boolean consume(UUID instance) { if (!Objects.requireNonNull(instance).equals(owner)) return false; clear(); return true; }
    public void clear() { owner = null; startedAt = 0; }
}
