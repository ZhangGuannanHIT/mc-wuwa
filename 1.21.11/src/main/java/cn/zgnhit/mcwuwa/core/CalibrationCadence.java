package cn.zgnhit.mcwuwa.core;

/** Measurement quality protocol, not a source-game combat rule or a theoretical DPS estimator. */
public final class CalibrationCadence {
    private final long allowedGapTicks;
    private long lastHit = -1, observedTick = -1, maximumGap;

    public CalibrationCadence(double attacksPerSecond) {
        Checks.positive("attacks per second", attacksPerSecond);
        double cooldown = Math.max(1, Math.ceil(20 / attacksPerSecond));
        if (!Double.isFinite(cooldown) || cooldown > Integer.MAX_VALUE / 4d)
            throw new IllegalArgumentException("Unrepresentable sword cooldown");
        // Four complete cooldown periods tolerate manual jump timing, but reject idle sampling.
        allowedGapTicks = (long) cooldown * 4;
    }
    public void confirmedSwordHit(long tick) {
        observe(tick);
        lastHit = tick;
    }
    public boolean sustainedThrough(long tick) {
        observe(tick);
        return lastHit >= 0 && maximumGap <= allowedGapTicks;
    }
    private void observe(long tick) {
        Checks.tick(tick);
        if (tick < observedTick) throw new IllegalArgumentException("Calibration clock moved backwards");
        observedTick = tick;
        if (lastHit >= 0) maximumGap = Math.max(maximumGap, tick - lastHit);
    }
    public long allowedGapTicks() { return allowedGapTicks; }
    public long maximumGapTicks() { return maximumGap; }
    public boolean hasSwordHit() { return lastHit >= 0; }
}
