package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.HashMap;
import java.util.Set;

/** Frozen 1610/1509/1508: these actions open a successful-evasion window.
 * Only Xuanling's skills consume 50 Flowing Echoes on confirmed success.
 * The host supplies the running server timeline, not a client animation or command.
 * Its whole-action window is a labelled MC timing adaptation; original frames are unverified.
 */
public final class SkillEvadeRules {
    private static final Set<String> ACTIONS = Set.of("yangyang_xuanling/skill", "yangyang_xuanling/skill_feather",
        "yangyang_xuanling/flow_azure", "yangyang_xuanling/flow_feather");
    private SkillEvadeRules() { }
    public static boolean supports(MechanicsState state, String action) {
        return state.characterId().equals("yangyang_xuanling") && ACTIONS.contains(action)
            || state.characterId().equals("lynae")&&Set.of("lynae/phantom_leap_1","lynae/phantom_leap_2","lynae/phantom_leap_3").contains(action)
            || state.characterId().equals("chisa")&&Set.of("chisa/saw_resonance_02","chisa/retract").contains(action);
    }
    public static boolean activeAt(MechanicsState state, String action, long start, long now, int duration) {
        return duration > 0 && start >= 0 && now >= start && now - start < duration && supports(state, action);
    }
    /** Only after a distinct server-confirmed success. Invulnerability follow-up hits must not call this. */
    public static MechanicsState confirmedSuccess(MechanicsState state, String action) {
        if (!supports(state, action)) return state;
        if(!state.characterId().equals("yangyang_xuanling"))return state;
        var resources = new HashMap<>(state.resources());
        resources.put("stream", Math.max(0, state.resource("stream") - 50));
        return new MechanicsState(state.characterId(), state.form(), state.mode(), state.chain(), state.nextBasic(), resources, state.timers());
    }
}
