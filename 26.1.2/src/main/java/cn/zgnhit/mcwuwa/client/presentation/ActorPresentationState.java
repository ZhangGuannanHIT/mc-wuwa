package cn.zgnhit.mcwuwa.client.presentation;

import java.util.Objects;
import java.util.UUID;

/** Immutable server-authorized visual state; never owns damage, HP or cooldowns. */
public record ActorPresentationState(UUID actorUuid, int entityId, UUID instanceId,
                                     String actionId, long startTick, int durationTicks,
                                     long revision, String formModel) {
    public ActorPresentationState {
        Objects.requireNonNull(actorUuid);
        Objects.requireNonNull(instanceId);
        Objects.requireNonNull(actionId);
        if (durationTicks <= 0 || durationTicks > 12000) throw new IllegalArgumentException("Invalid visual duration");
        actionId = actionId.startsWith("mc_wuwa:") ? actionId.substring(8) : actionId;
        if (!actionId.matches("[a-z0-9_]+/[a-z0-9_/]+")) throw new IllegalArgumentException("Invalid action ID");
        formModel = formModel == null ? "" : formModel;
    }

    public boolean expired(long now) { return now >= startTick + durationTicks; }
    public float progress(double now) { return (float) Math.clamp((now - startTick) / durationTicks, 0, 1); }
}
