package cn.zgnhit.mcwuwa.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Register server-issued actions first. Unknown/expired actions cannot reopen an old hit. */
public final class HitLedger {
    public record Key(UUID actionId, int hitIndex, UUID canonicalTargetId) {
        public Key {
            Objects.requireNonNull(actionId); Objects.requireNonNull(canonicalTargetId);
            if (hitIndex < 0) throw new IllegalArgumentException("hitIndex");
        }
    }
    private record Action(long lastTick, Set<Key> hits) {}
    private final Map<UUID, Action> actions = new HashMap<>();
    private final int maxConcurrentActions;
    public HitLedger(int maxConcurrentActions) {
        if (maxConcurrentActions < 1) throw new IllegalArgumentException("capacity");
        this.maxConcurrentActions = maxConcurrentActions;
    }
    public synchronized void register(UUID action, long lastAllowedTick) {
        Objects.requireNonNull(action); Checks.tick(lastAllowedTick);
        if (actions.containsKey(action)) throw new IllegalArgumentException("action already registered");
        if (actions.size() >= maxConcurrentActions) throw new IllegalStateException("action capacity reached; expire completed actions");
        actions.put(action, new Action(lastAllowedTick, new HashSet<>()));
    }
    public synchronized boolean accept(Key key, long now) {
        Objects.requireNonNull(key); Checks.tick(now);
        Action action = actions.get(key.actionId);
        return action != null && now <= action.lastTick && action.hits.add(key);
    }
    public synchronized void expire(long now) {
        Checks.tick(now); actions.entrySet().removeIf(e -> e.getValue().lastTick < now);
    }
    public synchronized void cancel(UUID action) { actions.remove(Objects.requireNonNull(action)); }
    public synchronized int activeActions() { return actions.size(); }
}
