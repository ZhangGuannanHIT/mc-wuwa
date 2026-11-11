package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.Set;

/** Frozen1210 node7 action identities and events. Entering interference is not a response. */
public final class AemeathTargetRules {
    /** C6's60-layer cap is local to the deployed body's8-block MC aura, while in combat. */
    public static int traceCap(int chain,boolean combat,double distanceSquared){return chain>=6&&combat&&distanceSquared<=64?60:30;}
    private static final Set<String> MARK_ATTACKS=Set.of("aemeath/basic_03","aemeath/basic_04",
        "aemeath/mech_basic_03","aemeath/mech_basic_04","aemeath/skill","aemeath/skill_dawn",
        "aemeath/intro","aemeath/intro_starlight");
    private AemeathTargetRules(){ }
    public static boolean appliesModeMark(String action){return MARK_ATTACKS.contains(action);}
    public static boolean collectsTrace(String mode,String event){
        return mode.equals("fusion")?event.equals("fusion_burst"):mode.equals("rupture")&&event.equals("rupture_response");
    }
}
