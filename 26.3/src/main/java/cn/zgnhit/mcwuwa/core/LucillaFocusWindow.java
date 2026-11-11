package cn.zgnhit.mcwuwa.core;

/** Server-tick focus ring shared by basic3 and skill. Timing is an explicit MC
 * authoring profile: the original text specifies the growing region but no
 * numeric pointer speed. Renderers read View and never decide perfect release. */
public final class LucillaFocusWindow {
    public enum Channel { BASIC, SKILL }
    public enum Outcome { ORDINARY, PERFECT }
    public record Profile(int cycleTicks,int fullTicks,double initialHalfWidth) {
        public Profile {
            if(cycleTicks<2||fullTicks<1||!Double.isFinite(initialHalfWidth)
                ||initialHalfWidth<0||initialHalfWidth>=.5)throw new IllegalArgumentException("focus profile");
        }
    }
    public record View(double cursor,double perfectHalfWidth,boolean insidePerfect,boolean open) { }
    private final Profile profile;
    private final boolean firstChain;
    private Channel channel;
    private long opened;
    private boolean open;
    public LucillaFocusWindow(Profile profile,boolean firstChain){this.profile=java.util.Objects.requireNonNull(profile);this.firstChain=firstChain;}
    public void begin(Channel channel,long now){
        Checks.tick(now);if(open)throw new IllegalStateException("focus already open");
        this.channel=java.util.Objects.requireNonNull(channel);opened=now;open=true;
    }
    public View view(long now){
        Checks.tick(now);long elapsed=Math.max(0,now-opened);
        double cursor=(elapsed%profile.cycleTicks())/(double)profile.cycleTicks();
        double width=Math.min(.5,profile.initialHalfWidth()+(.5-profile.initialHalfWidth())*elapsed/profile.fullTicks());
        return new View(cursor,width,Math.abs(cursor-.5)<=width,open);
    }
    /** Empty means keep charging. A returned outcome is consumed exactly once. */
    public java.util.Optional<Outcome> tick(long now){
        if(!open)return java.util.Optional.empty();var view=view(now);
        if(now-opened>=profile.fullTicks()||(firstChain&&channel==Channel.SKILL&&view.insidePerfect())){
            open=false;return java.util.Optional.of(Outcome.PERFECT);
        }
        return java.util.Optional.empty();
    }
    public java.util.Optional<Outcome> release(long now){
        if(!open)return java.util.Optional.empty();var result=tick(now);if(result.isPresent())return result;
        var view=view(now);open=false;return java.util.Optional.of(view.insidePerfect()?Outcome.PERFECT:Outcome.ORDINARY);
    }
    /** Switching/cancellation never manufactures a release attack or reward. */
    public void cancel(){open=false;}
}
