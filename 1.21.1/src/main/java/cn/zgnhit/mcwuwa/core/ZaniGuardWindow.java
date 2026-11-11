package cn.zgnhit.mcwuwa.core;

/** Cast-local guard qualification. The host opens this only after an accepted
 * action, and routes enemy damage through receive before applying HP loss.
 * Window lengths are explicit MC choreography parameters, not source seconds. */
public final class ZaniGuardWindow {
    public enum Mode { NONE, STANDARD, CRISIS, IGNITE }
    public enum Followup { NONE, PRECISE_COUNTER, BREACH_COUNTER, DAWN, NIGHT, FLASH, SUPPRESSION }
    public record Resolution(Followup followup,double incomingMultiplier,double vibrationFraction,
                             boolean stopNearby,boolean consumeRedundant,boolean postGuardReduction) { }
    private Mode mode=Mode.NONE;
    private long began,perfectUntil,earlyUntil,expires,postGuardUntil;

    public void open(Mode next,long now,int perfectTicks,int earlyTicks,int maximumTicks){
        Checks.tick(now);
        if(next==Mode.NONE||perfectTicks<0||earlyTicks<perfectTicks||maximumTicks<earlyTicks||maximumTicks==0)
            throw new IllegalArgumentException("guard windows");
        mode=next;began=now;perfectUntil=Math.addExact(now,perfectTicks);
        earlyUntil=Math.addExact(now,earlyTicks);expires=Math.addExact(now,maximumTicks);
    }
    public Mode mode(long now){Checks.tick(now);return mode!=Mode.NONE&&now<expires?mode:Mode.NONE;}
    public boolean interruptionImmune(long now){Mode current=mode(now);return current==Mode.CRISIS||current==Mode.IGNITE;}
    public Resolution receive(long now,boolean enemyAttack){
        Checks.tick(now);Mode current=mode(now);
        boolean counter=enemyAttack&&current!=Mode.NONE&&(current==Mode.STANDARD||now<perfectUntil);
        if(counter){
            Followup action=switch(current){case STANDARD->Followup.PRECISE_COUNTER;case CRISIS->Followup.BREACH_COUNTER;case IGNITE->Followup.FLASH;default->throw new AssertionError();};
            mode=Mode.NONE;postGuardUntil=Math.addExact(now,40);
            return new Resolution(action,0,current==Mode.IGNITE?.10:.05,true,current==Mode.CRISIS,true);
        }
        double multiplier=current==Mode.CRISIS||current==Mode.IGNITE?.60:1;
        if(now<postGuardUntil)multiplier*=.70;
        return new Resolution(Followup.NONE,multiplier,0,false,false,false);
    }
    /** Called on release or at expiry. Expiry must resolve rather than silently
     * clearing a preparation and losing its final attack. */
    public Followup release(long now){
        Checks.tick(now);Mode prior=mode;mode=Mode.NONE;
        return switch(prior){
            case NONE,STANDARD->Followup.NONE;
            case CRISIS->Followup.SUPPRESSION;
            case IGNITE->now<earlyUntil?Followup.DAWN:Followup.NIGHT;
        };
    }
    public Followup tick(long now){Checks.tick(now);return mode!=Mode.NONE&&now>=expires?release(now):Followup.NONE;}
    /** Switching cancels the stance, never executes its charged attack. */
    public void cancel(){mode=Mode.NONE;}
    public void clear(){cancel();began=perfectUntil=earlyUntil=expires=postGuardUntil=0;}
}
