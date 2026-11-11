package cn.zgnhit.mcwuwa.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Public visual identity only. Contains no health, equipment instance or combat resource data. */
public final class RoleAppearanceState {
    public record Snapshot(UUID actor, int entityId, String weapon, String form, long revision) {
        public Snapshot {
            Objects.requireNonNull(actor); Objects.requireNonNull(weapon); Objects.requireNonNull(form);
            if (entityId < 0 || revision < 1 || form.length() > 48 || !form.matches("[a-z0-9_]*"))
                throw new IllegalArgumentException("Invalid role appearance");
            if (!weapon.isEmpty() && java.util.Arrays.stream(CharacterId.values()).noneMatch(role -> role.weaponPath().equals(weapon)))
                throw new IllegalArgumentException("Unknown role appearance weapon");
            if (weapon.isEmpty() && !form.isEmpty()) throw new IllegalArgumentException("Form without role");
        }
        public boolean sameIdentity(Snapshot other) {
            return other != null && actor.equals(other.actor) && entityId == other.entityId
                && weapon.equals(other.weapon) && form.equals(other.form);
        }
    }
    private final Map<UUID, Snapshot> actors = new HashMap<>();
    /** A packet cannot bind a reused numeric entity ID to a different player. */
    public boolean accept(Snapshot next, UUID trackedActor, int trackedEntityId) {
        if (!next.actor().equals(trackedActor) || next.entityId() != trackedEntityId) return false;
        Snapshot old = actors.get(next.actor());
        if (old != null && old.entityId() == next.entityId() && old.revision() >= next.revision()) return false;
        actors.put(next.actor(), next); return true;
    }
    public Optional<Snapshot> find(UUID actor, int entityId) {
        Snapshot value = actors.get(actor);
        return value != null && value.entityId() == entityId ? Optional.of(value) : Optional.empty();
    }
    public void forget(UUID actor) { actors.remove(actor); }
    public void retain(java.util.function.BiPredicate<UUID,Integer> tracked) {
        actors.values().removeIf(value -> !tracked.test(value.actor(),value.entityId()));
    }
    public void clear() { actors.clear(); }
}
