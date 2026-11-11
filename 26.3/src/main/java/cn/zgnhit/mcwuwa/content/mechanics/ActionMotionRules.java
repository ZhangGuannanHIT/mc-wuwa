package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.Map;

/** Gameplay displacement is independent of decorative root-bone translation.
 * Distances and windows below are explicit MC adaptations; provenance and review
 * limits live in docs/implementation/action-motion-audit-20261018.md. */
public final class ActionMotionRules {
    public record Motion(double forward,int ticks,double launch,int delayTicks) {
        public Motion(double forward,int ticks,double launch){this(forward,ticks,launch,0);}
        public Motion {
            if(!Double.isFinite(forward)||Math.abs(forward)>6||ticks<1||ticks>20
                ||!Double.isFinite(launch)||launch<0||launch>.6||delayTicks<0)throw new IllegalArgumentException("motion bounds");
        }
        public double speed(){return forward/ticks;}
    }
    private static final Map<String,Motion> MOTIONS=Map.ofEntries(
        Map.entry("camellya/basic_01",new Motion(0.50,6,0,1)),
        Map.entry("camellya/basic_02",new Motion(0.60,6,0,1)),
        Map.entry("camellya/basic_04",new Motion(0.50,6,0.24,1)),
        Map.entry("chisa/basic_01",new Motion(0.50,6,0,1)),
        Map.entry("chisa/basic_02",new Motion(0.60,6,0,1)),
        Map.entry("hiyuki/basic_01",new Motion(0.50,6,0,1)),
        Map.entry("hiyuki/basic_02",new Motion(0.60,6,0,1)),
        Map.entry("hiyuki/basic_03",new Motion(0.40,6,0,1)),
        Map.entry("yangyang_xuanling/basic_01",new Motion(0.50,6,0,1)),
        Map.entry("yangyang_xuanling/basic_02",new Motion(0.60,6,0,1)),
        Map.entry("yangyang_xuanling/basic_03",new Motion(0.40,6,0,1)),
        Map.entry("yangyang_xuanling/basic_04",new Motion(0.50,6,0.22,1)),
        Map.entry("aero_rover/basic_01",new Motion(0.50,6,0,1)),
        Map.entry("aero_rover/basic_02",new Motion(0.60,6,0,1)),
        Map.entry("aero_rover/basic_03",new Motion(0.40,6,0,1)),
        Map.entry("aero_rover/basic_04",new Motion(0.50,6,0,1)),
        Map.entry("changli/basic_01",new Motion(.4,5,0,1)),
        Map.entry("changli/basic_02",new Motion(.5,6,0,1)),
        Map.entry("changli/basic_03",new Motion(.5,7,0,2)),
        Map.entry("changli/basic_04",new Motion(.65,7,0,2)),
        // Full normal panels show short closing steps, not long skill dashes.
        // Distances and the brush hop below are bounded Minecraft adaptation.
        Map.entry("zani/basic_01",new Motion(.4,5,0,1)),
        Map.entry("zani/basic_02",new Motion(.55,6,0,1)),
        Map.entry("zani/basic_03",new Motion(.4,7,0,2)),
        Map.entry("zani/basic_04",new Motion(.6,7,0,2)),
        Map.entry("augusta/basic_01",new Motion(.45,5,0,1)),
        Map.entry("augusta/basic_02",new Motion(.55,6,0,1)),
        Map.entry("augusta/basic_03",new Motion(.4,7,0,2)),
        Map.entry("augusta/basic_04",new Motion(.65,7,0,2)),
        Map.entry("suoming/basic_01",new Motion(.5,5,0,1)),
        Map.entry("suoming/basic_02",new Motion(.4,6,0,1)),
        Map.entry("suoming/basic_03",new Motion(.45,7,0,2)),
        Map.entry("zhezhi/basic_03",new Motion(0,5,.22,2)),
        // Normal-ring and long-blade steps observed in published panels; MC distances.
        Map.entry("qiuyuan/basic_01",new Motion(.35,5,0,1)),
        Map.entry("qiuyuan/basic_02",new Motion(.45,6,0,1)),
        Map.entry("qiuyuan/basic_03",new Motion(.5,7,0,2)),
        Map.entry("ciaccona/basic_01",new Motion(.8,6,0,1)),
        Map.entry("ciaccona/basic_02",new Motion(.4,6,0,1)),
        Map.entry("ciaccona/basic_03",new Motion(.4,7,0,2)),
        Map.entry("ciaccona/basic_04",new Motion(.5,6,0,2)),
        Map.entry("sigrika/basic_01",new Motion(.4,5,0,1)),
        Map.entry("sigrika/basic_02",new Motion(.45,6,0,1)),
        Map.entry("sigrika/basic_03",new Motion(.5,7,0,2)),
        Map.entry("iuno/basic_01",new Motion(.35,5,0,1)),
        Map.entry("iuno/basic_02",new Motion(.5,7,.35,1)),
        Map.entry("iuno/basic_03",new Motion(.4,7,0,2)),
        Map.entry("lupa/basic_01",new Motion(.4,5,0,1)),
        Map.entry("lupa/basic_02",new Motion(.5,6,0,1)),
        Map.entry("lupa/basic_03",new Motion(.65,8,0,2)),
        Map.entry("lupa/basic_04",new Motion(.55,8,0,2)),
        // Reviewed full combat29--63s: close into the forward cut and low spin.
        // These bounded distances are MC adaptation, not extracted original units.
        // The ordinary held lift and ferocious rush already own MOVE_SPECIAL
        // commands; do not schedule a second delayed displacement over them.
        Map.entry("lupa/skill_wolf_dance",new Motion(1.1,8,0,1)),
        Map.entry("lupa/skill_wolf_dance_extreme",new Motion(1.1,8,0,1)),
        Map.entry("lupa/skill_wolf_dance_extreme_c4",new Motion(1.1,8,0,1)),
        Map.entry("suoming/skill",new Motion(2.4,8,0)),
        Map.entry("suoming/skill_counter",new Motion(2,6,0)),
        // Source1306 names define retreat, uppercut, rush and leap. Travel
        // distances and velocities are MC adaptation, not source frame data.
        Map.entry("augusta/heavy_retreat",new Motion(-1.6,8,0)),
        Map.entry("augusta/counter_retreat",new Motion(-1.6,8,0)),
        Map.entry("augusta/heavy_uppercut",new Motion(.6,8,.52)),
        Map.entry("augusta/skill",new Motion(1.2,8,.40)),
        Map.entry("augusta/skill_rush",new Motion(2.8,10,.42)),
        Map.entry("augusta/counter_rush",new Motion(2.8,10,.42)),
        Map.entry("augusta/skill_leap",new Motion(1.2,8,.52)),
        Map.entry("suisui/skill",new Motion(2.4,8,0)),
        Map.entry("suisui/rain_skill",new Motion(2.4,8,0)),
        Map.entry("suisui/rain_heavy",new Motion(.6,6,.44)),
        Map.entry("shorekeeper/intro",new Motion(1.2,8,0)),
        Map.entry("sigrika/skill",new Motion(1.5,6,0)),
        Map.entry("sigrika/skill_insight",new Motion(1.5,6,0)),
        Map.entry("sigrika/skill_sun",new Motion(1.5,6,0)),
        Map.entry("galbrena/skill",new Motion(2.4,8,.28)),
        Map.entry("galbrena/demon_skill",new Motion(2.4,8,.28)),
        Map.entry("galbrena/skill_ascend",new Motion(-2,8,.45)),
        Map.entry("galbrena/hellwalk",new Motion(3,8,0)),
        Map.entry("qiuyuan/intro",new Motion(2.4,8,0)),
        Map.entry("aero_rover/intro",new Motion(1.8,8,.28)),
        Map.entry("lynae/intro",new Motion(1.2,5,.28)),
        // BV1GALuzTEQ2: the light cradle arrives airborne, then lands.
        // Modest vertical launch is an MC adaptation, without forward lunge.
        Map.entry("phoebe/intro",new Motion(0,1,.20)),
        Map.entry("ciaccona/intro",new Motion(1.8,8,0)),
        Map.entry("qiuyuan/skill",new Motion(2.8,8,0)),
        Map.entry("qiuyuan/skill_dash",new Motion(4,10,.30)),
        Map.entry("qiuyuan/skill_canopy",new Motion(2.4,8,0)),
        Map.entry("qiuyuan/answer_basic_04",new Motion(1.2,6,0)),
        Map.entry("ciaccona/skill",new Motion(2.4,8,0)),
        Map.entry("ciaccona/heavy",new Motion(-.8,6,.32)),
        Map.entry("ciaccona/heavy_quartet",new Motion(-.8,6,.32)),
        Map.entry("jinhsi/skill",new Motion(2.4,8,0)),
        Map.entry("jinhsi/skill_overflowing",new Motion(.8,6,.32)),
        Map.entry("cartethyia/skill",new Motion(.6,6,.42)),
        Map.entry("cartethyia/fleurdelys_heavy",new Motion(2.2,8,0)),
        Map.entry("cartethyia/fleurdelys_counter",new Motion(1.6,6,0)),
        Map.entry("cartethyia/fleurdelys_skill",new Motion(1.2,6,.32)),
        Map.entry("cartethyia/fleurdelys_skill_followup",new Motion(0,6,.24)),
        // BV1VtFsz3EB7 full film128.75--130.25: the human fencing
        // heavy actually closes distance; decorative body translation is not motion.
        Map.entry("aemeath/heavy",new Motion(2.4,8,0,2)),
        // BV1WH6FBzEJP69.25--70.75 explicitly labels normal attack3:
        // a short forward disappearance/reappearance, not a stationary emitter pose.
        Map.entry("mornye/basic_03",new Motion(1.6,8,0,1)),
        Map.entry("aemeath/heavy_charge_2",new Motion(2.8,10,0,3)),
        Map.entry("aemeath/skill",new Motion(1.8,8,0)),
        Map.entry("aemeath/skill_dawn",new Motion(2.4,8,0)),
        Map.entry("aemeath/mech_basic_03",new Motion(.8,6,0)),
        Map.entry("aemeath/mech_basic_04",new Motion(1,6,0)),
        Map.entry("camellya/basic_03",new Motion(.6,6,0)),
        Map.entry("camellya/intro",new Motion(1.8,8,.20)),
        Map.entry("camellya/basic_05",new Motion(1,6,0)),
        Map.entry("hsin/worldly_step",new Motion(2,8,0)),
        Map.entry("hsin/worldly_suppress",new Motion(2,8,0)),
        Map.entry("hsin/skill",new Motion(1.2,6,0)),
        Map.entry("hsin/illuminated_uppercut",new Motion(.3,6,.36)),
        // Complete official present-E panel: a small forward vault; jade is a low advance.
        Map.entry("hiyuki/skill",new Motion(1.1,8,.32,1)),
        Map.entry("hiyuki/skill_jade",new Motion(1.0,8,0,1)),
        Map.entry("hiyuki/future_heavy",new Motion(2.8,8,0)),
        Map.entry("hiyuki/heavy",new Motion(-1.2,4,0,22)),
        Map.entry("yangyang_xuanling/heavy",new Motion(2.2,8,0)),
        Map.entry("yangyang_xuanling/flow_azure",new Motion(1.4,6,0)),
        Map.entry("yangyang_xuanling/skill",new Motion(.8,6,0)),
        Map.entry("yangyang_xuanling/skill_feather",new Motion(.8,6,0)),
        Map.entry("yangyang_xuanling/intro",new Motion(2.4,8,.32)),
        Map.entry("yangyang_xuanling/feather_heavy",new Motion(.6,6,.42)),
        Map.entry("chisa/pierce",new Motion(2,8,0)),
        Map.entry("chisa/retract",new Motion(-1.4,7,0)),
        Map.entry("chisa/severing_pincers",new Motion(1.2,6,0)),
        Map.entry("chisa/saw_basic_01",new Motion(.8,6,0)),
        Map.entry("chisa/saw_basic_02",new Motion(.8,6,0)));
    private ActionMotionRules(){ }
    public static Motion forAction(MechanicsState state,String action) {
        if(state==null)return null;
        Motion six=SixMovementRules.action(action);if(six!=null)return six;
        if(action.startsWith("qiuyuan/ink_heavy_")) {
            if(action.contains("_01"))return new Motion(5.8,10,.42);
            if(action.contains("_02"))return new Motion(5.8,10,.18);
            return new Motion(5.8,10,0);
        }
        // Original vine stance explicitly forbids voluntary translation.
        if(state.characterId().equals("camellya")&&state.form().equals("bloom"))return null;
        if(action.equals("lynae/cruise_basic_01")||action.equals("lynae/cruise_basic_04"))
            return new Motion(state.resource("lumiflow")>=120?1.6:.8,8,0);
        return MOTIONS.get(action);
    }
    public static Map<String,Motion> profiles(){return MOTIONS;}
}
