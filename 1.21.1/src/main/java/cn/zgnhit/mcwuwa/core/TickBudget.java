package cn.zgnhit.mcwuwa.core;

/** Bounds optional visual work; changing the limit does not replenish the current window. */
public final class TickBudget {
    private final int windowTicks;
    private long started=-1;
    private int used;
    public TickBudget(int windowTicks) {
        if(windowTicks<1)throw new IllegalArgumentException("window ticks");this.windowTicks=windowTicks;
    }
    public int grant(long tick,int requested,int limit) {
        Checks.tick(tick);if(requested<0||limit<0)throw new IllegalArgumentException("visual budget");
        if(started<0||tick<started||tick-started>=windowTicks){started=tick;used=0;}
        int accepted=Math.min(requested,Math.max(0,limit-used));used+=accepted;return accepted;
    }
}
