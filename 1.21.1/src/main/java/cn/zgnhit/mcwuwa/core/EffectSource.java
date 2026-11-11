package cn.zgnhit.mcwuwa.core;

import java.util.Objects;
import java.util.UUID;

/** Immutable provenance. Never reconstruct from the player's current held weapon. */
public record EffectSource(UUID playerId, UUID weaponInstanceId, CharacterId character,
                           UUID actionId, UUID triggerChainId) {
    public EffectSource {
        Objects.requireNonNull(playerId); Objects.requireNonNull(weaponInstanceId);
        Objects.requireNonNull(character); Objects.requireNonNull(actionId);
        Objects.requireNonNull(triggerChainId);
    }
}
