package cn.zgnhit.mcwuwa.core;

/** A release-blocking definition gap, not an ordinary unavailable-action result. */
public final class MissingDataException extends IllegalStateException {
    public MissingDataException(String dataKey) { super("Verified data unavailable: " + Checks.id(dataKey)); }
}
