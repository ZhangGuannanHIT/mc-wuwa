package cn.zgnhit.mcwuwa.core;

/** Source photo/film/zoom lifecycle. Animation determines when an accepted
 * Forget occurs; this ledger does not invent an original pulse interval. */
public final class LucillaPhotoLedger {
    public enum Mode { GLACIO, ECHO }
    private final int chain;
    private Mode mode=Mode.GLACIO;
    private double impression;
    private boolean memory,nostalgia;
    private int film,zoom,remembrance;
    private long sharedExpiry,filmReady,liberationReady,c2Expiry,lastForget=-1;
    public void pauseOneTick(){sharedExpiry=CinematicTimeRules.postpone(sharedExpiry,1);filmReady=CinematicTimeRules.postpone(filmReady,1);liberationReady=CinematicTimeRules.postpone(liberationReady,1);c2Expiry=CinematicTimeRules.postpone(c2Expiry,1);}

    public LucillaPhotoLedger(int chain){
        if(chain<0||chain>6)throw new IllegalArgumentException("chain");this.chain=chain;
    }
    public Mode mode(){return mode;}
    public double impression(){return impression;}
    /** Import the owning weapon's already-authoritative pool before reduction.
     * Never accepts a client number or generates photo-consumption effects. */
    public void synchronizeImpression(double amount){
        Checks.nonnegative("impression",amount);
        if(amount>150)throw new IllegalArgumentException("impression exceeds cap");
        impression=amount;
    }
    public int photos(){return (int)(impression/50);}
    public boolean memory(){return memory;}
    public void gainImpression(double amount){Checks.nonnegative("impression",amount);impression=Math.min(150,impression+amount);}
    public boolean canLiberate(long now){Checks.tick(now);return !memory&&photos()==3&&now>=liberationReady;}
    public boolean beginMemory(long now){
        if(!canLiberate(now))return false;
        memory=true;remembrance=0;lastForget=-1;liberationReady=now+500;
        expire(now);sharedExpiry=now+600;
        if(mode==Mode.GLACIO)film=Math.min(10,film+4);else zoom=Math.min(4,zoom+1);
        if(chain>=2)c2Expiry=Long.MAX_VALUE;
        return true;
    }
    /** One serial per accepted Forget, not per hit/victim/render frame. */
    public boolean consumePhoto(long serial,long now){
        Checks.tick(serial);expire(now);
        if(!memory||photos()==0||serial<=lastForget)return false;
        lastForget=serial;impression-=50;
        if(mode==Mode.GLACIO)film=Math.min(10,film+2);else zoom=Math.min(4,zoom+1);
        sharedExpiry=now+600;
        if(chain>=6)remembrance=Math.min(3,remembrance+1);
        return true;
    }
    /** Caller checks actual active teammate ownership before requesting copies.
     * Self applications and these copied applications must not recurse. */
    public int teammateChafe(boolean otherActiveRole,boolean copiedApplication,long now){
        expire(now);
        if(mode!=Mode.GLACIO||!otherActiveRole||copiedApplication||film==0||now<filmReady)return 0;
        film--;filmReady=now+10;return 2;
    }
    public int film(long now){expire(now);return film;}
    public double echoCritDamage(long now){expire(now);return mode==Mode.ECHO?zoom*.10:0;}
    public double finaleAmplification(){return chain>=6?remembrance*2d:0;}
    public boolean secondChainActive(long now){Checks.tick(now);return chain>=2&&now<c2Expiry;}
    public void defeatedTarget(){if(memory&&chain>=6)nostalgia=true;}
    /** Commit after Finale damage, so its three-photo bonus survives resolution. */
    public void finishMemory(long now){
        Checks.tick(now);if(!memory)return;memory=false;remembrance=0;
        if(chain>=2&&c2Expiry!=0)c2Expiry=now+600;
    }
    public boolean peacefulTick(boolean inCombat){
        if(memory||inCombat||!nostalgia)return false;
        nostalgia=false;gainImpression(150);return true;
    }
    public void changeMode(Mode next){
        java.util.Objects.requireNonNull(next);if(next==mode)return;
        mode=next;film=zoom=0;sharedExpiry=c2Expiry=filmReady=0;
    }
    private void expire(long now){Checks.tick(now);if(now>=sharedExpiry){film=zoom=0;sharedExpiry=0;}}
}
