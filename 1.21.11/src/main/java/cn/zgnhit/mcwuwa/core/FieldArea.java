package cn.zgnhit.mcwuwa.core;

/** A stationary field's world boundary. Source may be a living reserve role; active slot is irrelevant. */
public record FieldArea(String dimension, double x, double y, double z, double radius, long expiresAt) {
    public FieldArea {
        Checks.id(dimension); Checks.finite("field x", x); Checks.finite("field y", y); Checks.finite("field z", z);
        Checks.positive("field radius", radius); Checks.tick(expiresAt);
    }
    public boolean active(String currentDimension, long now, boolean sourceEquippedAlive) {
        Checks.tick(now);
        return sourceEquippedAlive && dimension.equals(currentDimension) && now < expiresAt;
    }
    public boolean contains(String currentDimension, double targetX, double targetY, double targetZ, long now, boolean sourceEquippedAlive) {
        if (!active(currentDimension, now, sourceEquippedAlive) || !Double.isFinite(targetX)
            || !Double.isFinite(targetY) || !Double.isFinite(targetZ)) return false;
        // Hypot avoids radius/distance squaring overflow for otherwise finite coordinates.
        return Math.hypot(Math.hypot(targetX-x, targetY-y), targetZ-z) <= radius;
    }
}
