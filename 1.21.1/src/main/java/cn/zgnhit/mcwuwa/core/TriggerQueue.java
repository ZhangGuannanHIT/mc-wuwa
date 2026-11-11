package cn.zgnhit.mcwuwa.core;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Stable FIFO avoids recursive combat callbacks. Recursion is allowed only with explicit next-trigger identities. */
public final class TriggerQueue<E> {
    public record TriggerKey(UUID chainId, String triggerId, UUID targetId, int occurrence) {
        public TriggerKey {
            Objects.requireNonNull(chainId); Checks.id(triggerId); Objects.requireNonNull(targetId);
            if (occurrence < 0) throw new IllegalArgumentException("occurrence");
        }
    }
    private record Pending<E>(TriggerKey key, E event) {}
    private final ArrayDeque<Pending<E>> queue = new ArrayDeque<>();
    private final Set<TriggerKey> scheduled = new HashSet<>();
    private final int safetyLimit;
    private boolean draining;
    public TriggerQueue(int safetyLimit) { if (safetyLimit < 1) throw new IllegalArgumentException("safetyLimit"); this.safetyLimit = safetyLimit; }
    public boolean enqueue(TriggerKey key, E event) {
        Objects.requireNonNull(key); Objects.requireNonNull(event);
        if (scheduled.contains(key)) return false;
        if (scheduled.size() >= safetyLimit) throw new IllegalStateException("trigger safety limit; probable invalid recursive definition");
        scheduled.add(key); queue.addLast(new Pending<>(key, event)); return true;
    }
    public void drain(Consumer<E> resolver) {
        Objects.requireNonNull(resolver); if (draining) throw new IllegalStateException("recursive drain"); draining = true;
        try { while (!queue.isEmpty()) resolver.accept(queue.removeFirst().event); }
        finally { draining = false; }
    }
    /** Only release after a source chain has no remaining live projectile/effect executors. */
    public void releaseChain(UUID chainId) {
        Objects.requireNonNull(chainId);
        if (queue.stream().anyMatch(p -> p.key.chainId.equals(chainId))) throw new IllegalStateException("chain still pending");
        scheduled.removeIf(k -> k.chainId.equals(chainId));
    }
    /** Failed root scopes cannot leave entity references or queued callbacks for the next attack. */
    public void abortChain(UUID chainId) {
        Objects.requireNonNull(chainId);if(draining)throw new IllegalStateException("chain is resolving");
        queue.removeIf(p->p.key.chainId.equals(chainId));scheduled.removeIf(k->k.chainId.equals(chainId));
    }
    public int pending() { return queue.size(); }
}
