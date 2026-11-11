package cn.zgnhit.mcwuwa.core;

import java.util.Locale;
import java.util.Optional;

/** C2S operations are intentionally separate from the server's mechanics event enum.
 * A client requests an input; it cannot report an intro, a successful block, a counter or a tick.
 */
public enum ClientIntent {
    SYNC, FOCUS, SWITCH, ECHO_SWAP, MODE_NEXT, REVIVE, CHAIN, REFINE, CREATIVE_RECHARGE, TRAVEL, BIKE_JUMP_PRESS, BIKE_JUMP_RELEASE, BIKE_JUMP_CANCEL,
    SIX_JUMP_PRESS, SIX_JUMP_RELEASE, SIX_JUMP_HOLD, SIX_JUMP_CANCEL, NOTE_GREEN, NOTE_YELLOW,
    DODGE_PRESS, DODGE_RELEASE, CANCEL_HEAVY, CANCEL_LIBERATION, CANCEL_SKILL, SCENE_INTERACTION, ECHO_RELEASE, AIM_RELEASE,
    LOCK, TUNE_BREAK, ECHO, ECHO_HOLD, AIM_PRESS,
    BASIC, BASIC_RELEASE, HEAVY_START, HEAVY_RELEASE, HEAVY_FULL, AIR, SKILL, SKILL_HOLD, SKILL_RELEASE, LIBERATION, LIBERATION_HOLD, LIBERATION_RELEASE, JUMP;

    public static Optional<ClientIntent> parse(String operation) {
        if (operation == null || operation.isEmpty() || operation.length() > 32) return Optional.empty();
        for (int i=0;i<operation.length();i++) {
            char c=operation.charAt(i);
            if (c!='_' && (c<'A'||c>'Z') && (c<'a'||c>'z')) return Optional.empty();
        }
        try { return Optional.of(valueOf(operation.toUpperCase(Locale.ROOT))); }
        catch (IllegalArgumentException unknown) { return Optional.empty(); }
    }
}
