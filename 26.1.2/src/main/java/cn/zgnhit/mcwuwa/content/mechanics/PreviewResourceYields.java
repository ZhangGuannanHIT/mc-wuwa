package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.List;
import static cn.zgnhit.mcwuwa.content.mechanics.MechanicsEngine.*;

/** Opt-in development preview ONLY. These are authoring choices, not game values.
 * Every entry has authoredApproximation=true and evidence="unverified".
 * Production fidelity mode must pass false to MechanicsEngine.onHit.
 * Native coefficients, caps, explicit grants, energy costs and damage types are
 * never changed here. Each grant runs once per confirmed action, not per target.
 */
public final class PreviewResourceYields {
    public record Entry(String character, String resource, String actionFamily, double amount,
                        boolean authoredApproximation, String evidence) { }
    public static final List<Entry> ENTRIES = List.of(
        new Entry("aemeath", "synchronization", "basic", 12, true, "unverified"),
        new Entry("lynae", "overflow", "sampling_basic", 20, true, "unverified"),
        new Entry("mornye", "rest_energy", "normal_basic", 25, true, "unverified"),
        new Entry("mornye", "relative_motion", "observation_basic", 25, true, "unverified"),
        new Entry("yinlin", "judgment", "basic_or_skill", 20, true, "unverified"),
        new Entry("camellya", "pistils", "basic_consumption", -10, true, "unverified"),
        new Entry("cartethyia", "resolve", "fleurdelys_basic_or_skill", 24, true, "unverified"),
        new Entry("yangyang_xuanling", "stream", "basic_consumption", -25, true, "unverified"),
        new Entry("hsin", "worldly_heart", "basic_or_skill", 20, true, "unverified"),
        new Entry("hsin", "illuminated_heart", "basic_or_skill", 75, true, "unverified"),
        new Entry("hsin", "illuminated_heart", "palaces_basic_consumption", -75, true, "unverified"),
        new Entry("hiyuki", "cold", "future_basic_or_skill", 50, true, "unverified"),
        new Entry("chisa", "saw_echo", "normal_basic_or_skill", 20, true, "unverified"),
        new Entry("chisa", "saw_echo", "saw_basic_consumption", -20, true, "unverified")
    );
    private PreviewResourceYields() { }

    static void apply(Transition t, String action) {
        boolean basic = action.contains("basic_"), skill = action.startsWith("skill");
        switch (t.original.characterId()) {
            case "aemeath" -> { if (basic) t.add("synchronization", 12); }
            case "lynae" -> { if (basic && t.form.equals("sampling")) t.add("overflow", 20); }
            case "mornye" -> { if (basic) t.add(action.startsWith("observation") ? "relative_motion" : "rest_energy", 25); }
            case "yinlin" -> { if (basic || skill || action.equals("magnetic_blast") || action.equals("intro")) t.add("judgment", 20); }
            case "camellya" -> {
                if (basic && t.get("pistils") > 0) {
                    double consume = Math.min(10, t.get("pistils")); t.add("pistils", -consume);
                    if (!t.has("budding")) {
                        // 4 concerto and one bud per TEN points are native; only the ten-point hit debit above is authored.
                        t.add("buds", consume / 10); t.timer("buds", 300);
                        t.effect(Kind.GAIN_CONCERTO, "camellya_pistils", consume * .4, 0);
                    }
                }
            }
            case "cartethyia" -> { if (action.startsWith("fleurdelys_") && (basic || action.contains("skill"))) t.add("resolve", 24); }
            case "yangyang_xuanling" -> { if (basic && !action.startsWith("listening")) t.add("stream", -25); }
            case "hsin" -> {
                if (action.startsWith("thousand_palaces_")) t.add("illuminated_heart", -75);
                else if (action.startsWith("illuminated_") && (basic || action.contains("skill"))) t.add("illuminated_heart", 75);
                else if (basic || skill) t.add("worldly_heart", 20);
            }
            case "hiyuki" -> {
                if ((action.startsWith("future_") && !action.equals("future_heavy_frost")) || action.equals("skill_jade") || action.equals("skill_falling")) t.add("cold", 50);
            }
            case "chisa" -> {
                if (action.startsWith("saw_basic_") || action.startsWith("saw_counter")) {
                    double consumed = Math.min(20, t.get("saw_echo")); t.add("saw_echo", -consumed); t.add("saw_consumed", consumed);
                } else if (!t.form.equals("saw") && (basic || skill || action.equals("pierce"))) t.add("saw_echo", 20);
            }
            default -> { }
        }
    }
}
