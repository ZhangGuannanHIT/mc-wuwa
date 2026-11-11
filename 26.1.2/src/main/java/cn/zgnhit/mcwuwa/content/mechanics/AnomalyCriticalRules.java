package cn.zgnhit.mcwuwa.content.mechanics;

/** Qualifications are separate from the host's real random critical roll. Distances are
 * the declared Minecraft eight-block adaptation; this aura belongs to the owning team. */
public final class AnomalyCriticalRules {
    private AnomalyCriticalRules() { }
    public static boolean electroAura(boolean inCombat,double distanceSquared,boolean ownerAlive,
                                      int chain,String mode,String cause) {
        return inCombat&&Double.isFinite(distanceSquared)&&distanceSquared>=0&&distanceSquared<=64
            &&ownerAlive&&chain>=6&&mode.equals("electro")&&cause.startsWith("effect/electro_");
    }
}
