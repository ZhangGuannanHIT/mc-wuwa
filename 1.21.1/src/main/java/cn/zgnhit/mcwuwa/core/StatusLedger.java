package cn.zgnhit.mcwuwa.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Named generic debuffs share a strongest-active group; personal attack marks are isolated by player. */
public final class StatusLedger {
    public record Contribution(UUID instanceId, String group, EffectSource source, double magnitude, long expiresAt) {
        public Contribution {
            Objects.requireNonNull(instanceId); Checks.id(group); Objects.requireNonNull(source);
            Checks.nonnegative("magnitude", magnitude); Checks.tick(expiresAt);
        }
    }
    public record MarkKey(String definitionId, UUID playerId) {
        public MarkKey { Checks.id(definitionId); Objects.requireNonNull(playerId); }
    }
    public record Mark(MarkKey key, EffectSource source, long expiresAt, long triggerReadyAt) {
        public Mark {
            Objects.requireNonNull(key); Objects.requireNonNull(source); Checks.tick(expiresAt); Checks.tick(triggerReadyAt);
            if (!key.playerId.equals(source.playerId())) throw new IllegalArgumentException("mark player/source mismatch");
        }
    }
    private final Map<UUID, Contribution> contributions = new HashMap<>();
    private final Map<MarkKey, Mark> marks = new HashMap<>();
    public void put(Contribution contribution) { contributions.put(contribution.instanceId, contribution); }
    public Optional<Contribution> strongest(String group, long now) {
        Checks.id(group); Checks.tick(now);
        return contributions.values().stream().filter(c -> c.group.equals(group) && c.expiresAt > now)
            .max(Comparator.comparingDouble(Contribution::magnitude).thenComparing(c -> c.instanceId.toString()));
    }
    public List<Contribution> activeContributions(long now) {
        Checks.tick(now); List<Contribution> result = new ArrayList<>();
        contributions.values().stream().filter(c -> c.expiresAt > now).forEach(result::add);
        result.sort(Comparator.comparing(c -> c.instanceId.toString())); return List.copyOf(result);
    }
    public void remove(UUID instance) { contributions.remove(Objects.requireNonNull(instance)); }
    public void putMark(Mark mark) { marks.put(mark.key, mark); }
    /** otherPlayerMayTrigger is a verified per-mark rule, not a global default. */
    public Optional<EffectSource> consumeMarkTrigger(MarkKey key, UUID hitter, boolean otherPlayerMayTrigger,
                                                    long now, long intervalTicks) {
        Objects.requireNonNull(key); Objects.requireNonNull(hitter); Checks.tick(now); Checks.tick(intervalTicks);
        Mark mark = marks.get(key);
        if (mark == null || mark.expiresAt <= now || mark.triggerReadyAt > now
            || !otherPlayerMayTrigger && !key.playerId.equals(hitter)) return Optional.empty();
        marks.put(key, new Mark(key, mark.source, mark.expiresAt, Math.addExact(now, intervalTicks)));
        return Optional.of(mark.source);
    }
    public void expire(long now) {
        Checks.tick(now); contributions.values().removeIf(c -> c.expiresAt <= now); marks.values().removeIf(m -> m.expiresAt <= now);
    }
    /** Generic enemy debuffs are deliberately retained on source logout. */
    public void stopPersonalMarks(UUID player) { marks.keySet().removeIf(k -> k.playerId.equals(player)); }
    public int markCount() { return marks.size(); }
}
