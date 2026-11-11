package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.Set;

/** Secondary original damage qualifications do not replace basic/heavy/skill kind. */
public final class DamageQualifications {
    private static final Set<String> ZANI_FRAZZLE = Set.of(
        "zani/suppression", "zani/breach_counter", "zani/heavy_dawn",
        "zani/heavy_day", "zani/heavy_night", "zani/heavy_night_last", "zani/heavy_flash", "zani/outro");
    private DamageQualifications() { }

    public static boolean directSpectroFrazzle(String actionId, String element) {
        return "spectro".equals(element) && actionId != null
            && (actionId.equals("effect/spectro_frazzle")
                || actionId.startsWith("effect/spectro_frazzle/")
                || ZANI_FRAZZLE.contains(actionId));
    }
}
