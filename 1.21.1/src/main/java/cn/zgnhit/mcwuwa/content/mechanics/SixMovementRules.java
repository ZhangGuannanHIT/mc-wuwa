package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.Map;

/** Movement direction follows archived skill text/official clips; numeric travel
 * distances, velocities and support tails are explicit Minecraft adaptations. */
public final class SixMovementRules {
    public record Command(double forward,int ticks,double launch,int hover,boolean directional) { }
    private static final Map<String,Command> COMMANDS=Map.ofEntries(
        Map.entry("changli_heavy_lift",new Command(.8,8,.52,14,false)),
        Map.entry("changli_intro_air",new Command(.8,6,.48,16,false)),
        Map.entry("changli_true_sight_dash",new Command(2.6,8,.16,8,false)),
        Map.entry("changli_true_sight_ground_dash",new Command(2.6,8,0,0,false)),
        Map.entry("carlotta_courtesy_backflip",new Command(-1.4,8,.42,0,false)),
        Map.entry("carlotta_death_knell",new Command(1.8,6,0,0,true)),
        Map.entry("iuno_initial_dash",new Command(2.4,8,0,0,false)),
        Map.entry("iuno_phase_flow",new Command(.8,6,.30,10,true)),
        Map.entry("iuno_beyond",new Command(2.4,8,0,10,true)),
        Map.entry("lupa_ferocious_dash",new Command(3,8,.18,6,false)),
        Map.entry("lupa_skill_lift",new Command(.5,6,.48,14,false)),
        Map.entry("qingxiao_clear_escape",new Command(3.6,10,.52,14,false)),
        Map.entry("qingxiao_sword_dash",new Command(2.5,8,0,8,true)));
    private SixMovementRules() { }
    public static Command command(String id){return COMMANDS.get(id);}
    public static boolean holdsAction(String full) {
        return full.equals("augusta/skill_rush")||full.equals("augusta/counter_rush")||full.equals("augusta/skill_leap")||full.equals("augusta/heavy_uppercut")
            ||full.startsWith("changli/air_basic_")||full.equals("changli/true_sight_charge")
            ||full.startsWith("lupa/air_basic_")||full.equals("lupa/air_flame_strike")
            ||full.startsWith("denia/disillusion_air_basic_")
            ||full.equals("iuno/intro")||full.startsWith("iuno/bow_basic_")||full.startsWith("iuno/bow_counter")||full.startsWith("iuno/skill_beyond")
            ||full.startsWith("qingxiao/immortal_basic_")||full.startsWith("qingxiao/immortal_counter");
    }
    public static boolean leapsAction(String full) {
        return full.equals("augusta/skill_rush")||full.equals("augusta/counter_rush")||full.equals("augusta/skill_leap")||full.equals("augusta/heavy_uppercut")
            ||full.equals("changli/heavy")||full.equals("changli/intro")||full.equals("changli/true_sight_charge")||full.startsWith("changli/air_basic_")
            ||full.equals("iuno/intro")||full.equals("lupa/air_basic_01")||full.equals("lupa/intro")||full.equals("lupa/skill");
    }
    public static ActionMotionRules.Motion action(String full) {
        String a=full.replace("_enhanced","");
        if(a.equals("changli/skill"))return new ActionMotionRules.Motion(2.6,8,.20);
        if(a.equals("changli/air_basic_03"))return new ActionMotionRules.Motion(.6,6,0);
        if(a.equals("carlotta/skill_show_brilliance")||a.equals("carlotta/skill_show_brilliance_c3"))return new ActionMotionRules.Motion(-1,8,.32);
        if(a.equals("carlotta/heavy")||a.startsWith("carlotta/heavy_restrictive")||a.startsWith("carlotta/heavy_end_of_road"))return new ActionMotionRules.Motion(-.6,6,0);
        if(a.equals("iuno/intro"))return new ActionMotionRules.Motion(1.2,8,.36);
        if(a.equals("lupa/air_basic_01"))return new ActionMotionRules.Motion(.6,6,.40);
        if(a.equals("lupa/intro"))return new ActionMotionRules.Motion(1.4,8,.42);
        if(a.startsWith("lupa/intro_pursuit"))return new ActionMotionRules.Motion(2.8,10,.16);
        if(a.equals("lupa/heavy_wolf_bite")||a.equals("lupa/heavy_sharp_claw"))return new ActionMotionRules.Motion(1.2,8,.20);
        if(a.equals("denia/heavy"))return new ActionMotionRules.Motion(-.5,6,0);
        if(a.equals("qingxiao/skill_ascend"))return new ActionMotionRules.Motion(1.2,8,.48);
        if(a.equals("qingxiao/heavy")||a.equals("qingxiao/heavy_c2"))return new ActionMotionRules.Motion(.8,8,.34);
        if(a.equals("qingxiao/intro"))return new ActionMotionRules.Motion(1.4,8,.34);
        return null;
    }
}
