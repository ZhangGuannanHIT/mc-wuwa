package cn.zgnhit.mcwuwa.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Invoke on the server game thread while the inventory/menu context is valid. */
public final class Progression {
    public enum Material { EMERALD, DIAMOND, GOLDEN_APPLE }
    @FunctionalInterface public interface Materials {
        /** Atomic check-and-remove: false must leave every stack unchanged. */
        boolean consume(Material material, int quantity);
    }
    public enum Outcome { SUCCESS, MAXIMUM, MATERIALS, INVALID_REVIVE, DUPLICATE_REQUEST_CONFLICT }
    public record Result(Outcome outcome, int spent, boolean repeated, String reason) { public boolean success() { return outcome == Outcome.SUCCESS; } }
    private record Fingerprint(String operation, UUID target) {}
    private record Entry(Fingerprint fingerprint, Result result) {}
    private final Map<UUID, Entry> requests = new HashMap<>();
    private static final int MAX_SESSION_REQUESTS = 100_000;
    private Result cached(UUID request, Fingerprint fingerprint) {
        Objects.requireNonNull(request);
        Entry entry = requests.get(request);
        if (entry == null) { if (requests.size() >= MAX_SESSION_REQUESTS) throw new IllegalStateException("request capacity; create a new authenticated session"); return null; }
        if (!entry.fingerprint.equals(fingerprint)) return new Result(Outcome.DUPLICATE_REQUEST_CONFLICT, 0, true, "request ID reused with a different operation");
        Result result = entry.result; return new Result(result.outcome, result.spent, true, result.reason);
    }
    private Result save(UUID request, Fingerprint fingerprint, Result result) { requests.put(request, new Entry(fingerprint, result)); return result; }
    public Result upgradeChain(UUID request, CharacterInstance weapon, Materials materials) {
        Objects.requireNonNull(weapon); Objects.requireNonNull(materials);
        Fingerprint fingerprint = new Fingerprint("chain", weapon.instanceId()); Result prior = cached(request, fingerprint); if (prior != null) return prior;
        Result result;
        if (weapon.chain() >= 6) result = new Result(Outcome.MAXIMUM, 0, false, "C6");
        else if (!materials.consume(Material.EMERALD, 6)) result = new Result(Outcome.MATERIALS, 0, false, "requires 6 emeralds");
        else { weapon.upgradeChain(); result = new Result(Outcome.SUCCESS, 6, false, ""); }
        return save(request, fingerprint, result);
    }
    public Result upgradeRefinement(UUID request, CharacterInstance weapon, Materials materials) {
        Objects.requireNonNull(weapon); Objects.requireNonNull(materials);
        Fingerprint fingerprint = new Fingerprint("refinement", weapon.instanceId()); Result prior = cached(request, fingerprint); if (prior != null) return prior;
        Result result;
        if (weapon.refinement() >= 5) result = new Result(Outcome.MAXIMUM, 0, false, "S5");
        else if (!materials.consume(Material.DIAMOND, 3)) result = new Result(Outcome.MATERIALS, 0, false, "requires 3 diamonds");
        else { weapon.upgradeRefinement(); result = new Result(Outcome.SUCCESS, 3, false, ""); }
        return save(request, fingerprint, result);
    }
    public Result revive(UUID request, TeamState team, int slot, Materials materials) {
        Objects.requireNonNull(team); Objects.requireNonNull(materials);
        // Resolve the current authoritative slot before accepting the request; client-provided item IDs are never authoritative.
        UUID target = team.at(slot).map(CharacterInstance::instanceId).orElse(team.playerId());
        Fingerprint fingerprint = new Fingerprint("revive:" + slot, target); Result prior = cached(request, fingerprint); if (prior != null) return prior;
        TeamState.Result validation = team.validateRevive(slot); Result result;
        if (!validation.success()) result = new Result(Outcome.INVALID_REVIVE, 0, false, validation.failure().name());
        else if (!materials.consume(Material.GOLDEN_APPLE, 1)) result = new Result(Outcome.MATERIALS, 0, false, "requires 1 normal golden apple");
        else { team.commitRevive(slot); result = new Result(Outcome.SUCCESS, 1, false, ""); }
        return save(request, fingerprint, result);
    }
}
