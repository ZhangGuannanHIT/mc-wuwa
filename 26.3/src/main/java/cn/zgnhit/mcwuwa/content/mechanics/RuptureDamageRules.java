package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.Set;

/** Server-authored Type12 response rows and the separate wing rupture pulse.
 * Ordinary toughness break is not rupture damage, despite sharing its MC damage zone.
 */
public final class RuptureDamageRules {
    private static final Set<String> CAUSES=Set.of("tune/aemeath_wing", "response/121099004",
        "response/1509056001", "response/120999005");
    private RuptureDamageRules(){ }
    public static boolean isRuptureDamage(String cause){return CAUSES.contains(cause);}
    public static boolean isPassiveEvent(RolePassiveEngine.Event event){
        return event.trigger()==RolePassiveEngine.Trigger.HIT && event.kind().equals("tune_break")
            &&event.effect().equals("rupture_damage");
    }
}
