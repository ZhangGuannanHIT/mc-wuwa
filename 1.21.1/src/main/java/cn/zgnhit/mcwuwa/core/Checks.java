package cn.zgnhit.mcwuwa.core;

import java.util.Objects;

/** Shared strict validation; missing source data is never silently replaced by zero. */
final class Checks {
    private Checks() {}
    static double finite(String name, double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
        return value;
    }
    static double nonnegative(String name, double value) {
        finite(name, value);
        if (value < 0) throw new IllegalArgumentException(name + " must be >= 0");
        return value;
    }
    static double positive(String name, double value) {
        finite(name, value);
        if (value <= 0) throw new IllegalArgumentException(name + " must be > 0");
        return value;
    }
    static double fraction(String name, double value) {
        nonnegative(name, value);
        if (value > 1) throw new IllegalArgumentException(name + " must be <= 1");
        return value;
    }
    static long tick(long value) {
        if (value < 0) throw new IllegalArgumentException("tick must be >= 0");
        return value;
    }
    static String id(String value) {
        Objects.requireNonNull(value, "id");
        if (value.isBlank()) throw new IllegalArgumentException("id must not be blank");
        return value;
    }
}
