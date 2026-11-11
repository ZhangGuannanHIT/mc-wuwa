package cn.zgnhit.mcwuwa.content.mechanics;

/** Snow Rust modifies elemental frost only, never the similarly named normal heavy attack.
 * Ratios are frozen1108/node4/C3/C6. The underlying anomaly base remains the declared MC rule. */
public final class HiyukiFrostRules {
    private HiyukiFrostRules() { }
    public static double amplification(int rust, int chain) {
        if (rust < 0 || rust > 3 || chain < 0 || chain > 6) throw new IllegalArgumentException("Snow Rust");
        return (1 + (rust >= 1 ? .30 : 0) + (rust >= 3 ? .30 : 0)) * (chain >= 6 && rust >= 3 ? 1.25 : 1);
    }
    public static double additionalRatio(int rust, int chain, boolean ownerApplied) {
        if (rust < 0 || rust > 3 || chain < 0 || chain > 6) throw new IllegalArgumentException("Snow Rust");
        return rust >= 2 && (ownerApplied || chain >= 6) ? 1.02 + (chain >= 3 ? 4.88 : 0) : 0;
    }
}
