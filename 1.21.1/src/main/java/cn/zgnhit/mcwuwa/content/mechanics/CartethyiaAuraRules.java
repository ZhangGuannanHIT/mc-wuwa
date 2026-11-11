package cn.zgnhit.mcwuwa.content.mechanics;

/** Divine Intent affects nearby targets, not only erosion owned by Cartethyia.
 * Eight blocks is the MC range adaptation; eligibility follows skill3/13-15. */
public final class CartethyiaAuraRules {
    public static final double RADIUS_SQUARED=64;
    private CartethyiaAuraRules() { }
    public static boolean divineIntent(MechanicsState state,boolean deployedAlive,double distanceSquared) {
        return deployedAlive&&state!=null&&state.characterId().equals("cartethyia")
            &&state.form().equals("fleurdelys")&&state.timer("manifestation")>0&&state.timer("divine_intent")>0
            &&Double.isFinite(distanceSquared)&&distanceSquared>=0&&distanceSquared<=RADIUS_SQUARED;
    }
    public static boolean erosionDamage(String cause) {
        return cause!=null&&(cause.equals("effect/aero_erosion")||cause.startsWith("effect/aero_erosion/"));
    }
}
