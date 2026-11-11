package cn.zgnhit.mcwuwa.core;

/** Explicit control immunity differs from damage immunity. */
public final class CombatControl {
    private CombatControl() {}
    public enum Impact { LIGHT, HEAVY, STRONG }
    public enum Protection { NONE, INCREASED, GREATLY_INCREASED, UNINTERRUPTIBLE }
    /** Plan 8.2: explosions use their explicit heavy tier; ordinary environment/periodic/PvP never adds stagger. */
    public static java.util.Optional<Impact> impact(EnemyProfile attacker, boolean directAttack, boolean explosion, boolean periodic) {
        if (periodic || attacker != null && attacker.player()) return java.util.Optional.empty();
        if (explosion) return java.util.Optional.of(Impact.HEAVY);
        if (!directAttack || attacker == null) return java.util.Optional.empty();
        return java.util.Optional.of(switch (attacker.tier()) {
            case ONE_COST, THREE_COST -> Impact.LIGHT;
            case FOUR_COST -> Impact.HEAVY;
            case STRONG -> Impact.STRONG;
        });
    }
    public static Protection stronger(Protection first, Protection second) {
        java.util.Objects.requireNonNull(first); java.util.Objects.requireNonNull(second);
        return first.ordinal() >= second.ordinal() ? first : second;
    }
    /** Called only after mitigation, including native shields; vanilla absorption is still an actual received hit. */
    public static boolean receivedDamage(double healthLost, double absorptionSpent) {
        Checks.nonnegative("health lost", healthLost); Checks.nonnegative("absorption spent", absorptionSpent);
        return healthLost > 0 || absorptionSpent > 0;
    }
    public static boolean interrupts(Impact impact, Protection protection, boolean direct, boolean pvp) {
        java.util.Objects.requireNonNull(impact); java.util.Objects.requireNonNull(protection);
        return direct && !pvp && impact.ordinal() >= protection.ordinal();
    }
    public static final class AlephParry {
        private long startedAt = -1, staggerUntil;
        public void beginAttack(long tick) { Checks.tick(tick); if (tick < staggerUntil) throw new IllegalStateException("staggered"); startedAt = tick; }
        public boolean isWindow(long tick) { Checks.tick(tick); return startedAt >= 0 && tick >= startedAt + 8 && tick < startedAt + 16; }
        public boolean tryParry(long tick, boolean playerActiveDirectAttack) {
            if (!playerActiveDirectAttack || !isWindow(tick)) return false;
            startedAt = -1; staggerUntil = tick + 20; return true;
        }
        public boolean consumeAttackReady(long tick) {
            Checks.tick(tick);
            if (startedAt < 0 || tick < startedAt + 16) return false;
            startedAt = -1; return true;
        }
        public long staggerUntil() { return staggerUntil; }
    }
}
