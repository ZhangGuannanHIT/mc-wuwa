package cn.zgnhit.mcwuwa.core;

/** Authoritative form/Heliacal Ember accounting, independent of cast choreography.
 * Costs are supplied by the accepted attack: the source does not grant permission
 * to spend on rejected input, cancelled preparation, or decorative hits. */
public final class ZaniBlazeGauge {
    public enum Heavy { DAWN, DAY, NIGHT, FLASH }
    public record HeavyCast(Heavy action,double spentOnCast,long serial) { }
    private double ember,spent;
    private long entered,expires;
    private int chain;
    private boolean blazing,refillUsed;
    private Heavy followup;
    private long heavySerial,pendingNight;
    private long settledNight;
    private double settledNightSpend;
    public void pauseOneTick(){if(blazing){entered++;expires=CinematicTimeRules.postpone(expires,1);}}

    public double ember(long now){expire(now);return ember;}
    public boolean blazing(long now){expire(now);return blazing;}
    public int cap(long now){return blazing(now)?150:100;}
    public void gain(double points,long now){
        Checks.nonnegative("ember gain",points);expire(now);ember=Math.min(blazing?150:100,ember+points);
    }
    public void enter(int sequence,long now){
        Checks.tick(now);if(sequence<0||sequence>6)throw new IllegalArgumentException("chain");
        expire(now);if(blazing)throw new IllegalStateException("already blazing");
        chain=sequence;entered=now;expires=Math.addExact(now,400);blazing=true;spent=0;refillUsed=false;
        followup=null;pendingNight=settledNight=0;settledNightSpend=0;
        ember=Math.min(150,ember+50);refill();
    }
    public double spend(double acceptedCost,long now){
        Checks.nonnegative("ember cost",acceptedCost);expire(now);
        if(!blazing)throw new IllegalStateException("not blazing");
        double consumed=Math.min(ember,acceptedCost);ember-=consumed;spent=Math.min(150,spent+consumed);refill();return consumed;
    }
    public boolean finaleReady(long now){return blazing(now)&&(ember<30||now-entered>=160);}
    /** Additive multiplier of the final hit only; never multiply the opening hit. */
    public double thirdChainFinalHitBonus(long now){return blazing(now)&&chain>=3?Math.min(12,spent*.08):0;}
    public boolean lethalProtection(long now){return blazing(now)&&chain>=6&&now-entered<160;}
    /** NIGHT survives a first-part interruption; the host continues that accepted
     * action instead of accepting a second cast and spending twice. */
    public Heavy nextHeavy(long now){
        if(!blazing(now))return null;
        return followup!=null?followup:ember>=30?Heavy.DAWN:null;
    }
    public HeavyCast castHeavy(Heavy action,double authoredCost,long now){
        Checks.nonnegative("heavy cost",authoredCost);expire(now);
        if(!blazing||pendingNight!=0)throw new IllegalStateException("heavy unavailable");
        if(action!=Heavy.FLASH&&action!=nextHeavy(now))throw new IllegalStateException("wrong heavy branch");
        if(action==Heavy.FLASH&&(followup==Heavy.NIGHT||ember<30))throw new IllegalStateException("flash unavailable");
        if(action!=Heavy.NIGHT&&ember<authoredCost)throw new IllegalStateException("insufficient ember");
        long serial=++heavySerial;
        double consumed=action==Heavy.NIGHT?0:spend(authoredCost,now);
        followup=switch(action){case DAWN->Heavy.DAY;case DAY,FLASH->Heavy.NIGHT;case NIGHT->null;};
        if(action==Heavy.NIGHT)pendingNight=serial;
        return new HeavyCast(action,consumed,serial);
    }
    /** The long preparation may enter NIGHT without DAWN/DAY; only its
     * accepted release invokes this branch. It still spends on actual hit. */
    public HeavyCast preparedNight(long now){
        if(!blazing(now)||pendingNight!=0)throw new IllegalStateException("prepared night unavailable");
        followup=Heavy.NIGHT;return castHeavy(Heavy.NIGHT,0,now);
    }
    /** Actual first qualifying NIGHT hit consumes at most40 once per cast,
     * including when several victims overlap its collider. */
    public double nightHit(HeavyCast cast,long now){
        expire(now);
        if(!blazing||cast.action()!=Heavy.NIGHT||pendingNight!=cast.serial())return 0;
        pendingNight=0;settledNight=cast.serial();settledNightSpend=spend(40,now);return settledNightSpend;
    }
    /** Every victim and subsequent hit uses the same accepted cast snapshot.
     * Reading it never consumes resources again. */
    public double nightSpentSnapshot(HeavyCast cast){
        return cast.action()==Heavy.NIGHT&&settledNight==cast.serial()?settledNightSpend:0;
    }
    /** Preview for damage calculation; debit remains in nightHit after accepted hurt. */
    public double nightDamageSpend(HeavyCast cast,long now){
        expire(now);
        if(cast.action()!=Heavy.NIGHT)return 0;
        if(settledNight==cast.serial())return settledNightSpend;
        return blazing&&pendingNight==cast.serial()?Math.min(40,ember):0;
    }
    public void finishHeavy(HeavyCast cast){if(pendingNight==cast.serial())pendingNight=0;}
    public void cancelHeavyChain(){followup=null;pendingNight=settledNight=0;settledNightSpend=0;}
    public double leave(long now){expire(now);blazing=false;expires=0;ember=Math.min(100,ember);cancelHeavyChain();return ember;}
    public void clear(){ember=spent=settledNightSpend=0;entered=expires=heavySerial=pendingNight=settledNight=0;chain=0;blazing=refillUsed=false;followup=null;}
    private void refill(){if(chain>=6&&!refillUsed&&ember<70){ember=Math.min(150,ember+70);refillUsed=true;}}
    private void expire(long now){Checks.tick(now);if(blazing&&now>=expires){blazing=false;ember=Math.min(100,ember);cancelHeavyChain();}}
}
