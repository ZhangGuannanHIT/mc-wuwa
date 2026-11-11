package cn.zgnhit.mcwuwa.content.mechanics;

/** Explicit source-described coordinated hits. A mixed action is classified per
 * damage row, never by marking its main attack as coordinated as well.
 * Cantarella: frozen skill6/7 descriptions and their bound damage groups. */
public final class CoordinatedHitRules {
    private CoordinatedHitRules() { }
    public static boolean coordinated(String action,String damageId) {
        return switch(action) {
            case "suoming/rain_thunder" -> true;
            case "zhezhi/ink_crane", "zhezhi/ink_crane_c5", "yinlin/judgment_thunder", "cantarella/jellyfish", "hsin/flying_palace" -> true;
            case "cantarella/mirage_basic_03" -> damageId.equals("1607013008");
            case "cantarella/mirage_intro" -> damageId.equals("1607401011")
                ||damageId.equals("1607401012")||damageId.equals("1607401013");
            default -> false;
        };
    }
}
