package cn.zgnhit.mcwuwa.core;

/** Accepted-cast state. Costs, damage and the shared team Unison ledger are
 * committed by the host from these results, never inferred from a client pose. */
public final class SuomingContractLedger {
    public enum Form { ARISING, DEEP, CALAMITY }
    public record Intro(String action,boolean refreshSkill,boolean grantsChainUnison,double extraConcerto) { }
    public record Outro(boolean consumedUnison,double nextElectroBonus,double nextCritDamage,int rainBudget) { }
    private final int chain;
    private Form form=Form.ARISING;
    private double obsession;
    private long unisonUntil,covenantUntil,forbiddenUntil,qualifiedUntil,introBonusReady,chainIntroReady;
    private long rainUntil,rainDamageUntil,rainReady,rainTriggerReady;
    private int rainRemaining;
    public void pauseOneTick(){unisonUntil=CinematicTimeRules.postpone(unisonUntil,1);covenantUntil=CinematicTimeRules.postpone(covenantUntil,1);forbiddenUntil=CinematicTimeRules.postpone(forbiddenUntil,1);qualifiedUntil=CinematicTimeRules.postpone(qualifiedUntil,1);introBonusReady=CinematicTimeRules.postpone(introBonusReady,1);chainIntroReady=CinematicTimeRules.postpone(chainIntroReady,1);rainUntil=CinematicTimeRules.postpone(rainUntil,1);rainDamageUntil=CinematicTimeRules.postpone(rainDamageUntil,1);rainReady=CinematicTimeRules.postpone(rainReady,1);rainTriggerReady=CinematicTimeRules.postpone(rainTriggerReady,1);}

    public SuomingContractLedger(int chain){if(chain<0||chain>6)throw new IllegalArgumentException("chain");this.chain=chain;}
    public Form form(){return form;}
    public double obsession(){return obsession;}
    public boolean unison(long now){Checks.tick(now);return now<unisonUntil;}
    public boolean covenant(long now){Checks.tick(now);return now<covenantUntil;}
    public boolean forbidden(long now){Checks.tick(now);return now<forbiddenUntil;}
    public void gainObsession(double amount,boolean actualActiveOwner){
        Checks.nonnegative("obsession",amount);if(actualActiveOwner&&form!=Form.CALAMITY)obsession=Math.min(800,obsession+amount);
    }
    public Intro intro(boolean respondingToUnison,long now){
        Checks.tick(now);if(form==Form.CALAMITY)throw new IllegalStateException("heart action locks intro");
        boolean enters=form==Form.ARISING;
        String action=enters?"intro":"intro_deep";
        if(respondingToUnison)action+="_unison";
        if(enters){form=Form.DEEP;obsession=0;}else gainObsession(200,true);
        double extra=0;
        if(respondingToUnison&&now>=introBonusReady){extra=10;introBonusReady=now+500;}
        boolean chainGrant=!respondingToUnison&&chain>=3&&now>=chainIntroReady;
        if(chainGrant)chainIntroReady=now+500;
        return new Intro(action,enters,chainGrant,extra);
    }
    public boolean unlockDeep(){
        if(form!=Form.ARISING||obsession<800)return false;
        form=Form.DEEP;obsession=0;return true;
    }
    public void gainUnison(long now){Checks.tick(now);unisonUntil=now+100;forbiddenUntil=0;}
    /** Call after paying the accepted liberation's125 energy and cooldown. */
    public void liberated(long now){
        if(form!=Form.DEEP)throw new IllegalStateException("liberation requires deep form");
        gainUnison(now);gainObsession(200,true);qualifiedUntil=now+160;
    }
    /** Requires an actual Unison and20 concerto; failure must not consume either. */
    public boolean beginForbidden(double concerto,long now){
        Checks.nonnegative("concerto",concerto);
        if(form==Form.CALAMITY||!unison(now)||covenant(now)||concerto<20)return false;
        unisonUntil=0;obsession=0;forbiddenUntil=now+240;return true;
    }
    public boolean beginHeart(long now){
        Checks.tick(now);if(form!=Form.DEEP||obsession<800)return false;
        form=Form.CALAMITY;obsession=0;qualifiedUntil=now+160;return true;
    }
    public void finishHeart(){if(form==Form.CALAMITY)form=Form.ARISING;}
    public Outro outro(int teamUnisonStacks,long now){
        Checks.tick(now);if(form==Form.CALAMITY)throw new IllegalStateException("heart action locks switching");
        if(teamUnisonStacks<0)throw new IllegalArgumentException("stacks");
        boolean used=unison(now);
        if(used){unisonUntil=0;covenantUntil=now+600;rainUntil=now+160;rainRemaining=6;rainDamageUntil=rainReady=rainTriggerReady=0;}
        double next=covenant(now)&&now<qualifiedUntil?.30+.20*Math.min(2,teamUnisonStacks):0;
        double crit=chain>=2?.10+.06*Math.min(4,teamUnisonStacks):0;
        forbiddenUntil=0;
        return new Outro(used,next,crit,used?6:0);
    }
    public boolean activeTeammateDamage(boolean actualActive,boolean isRainDamage,long now){
        Checks.tick(now);
        if(!actualActive||isRainDamage||now>=rainUntil||rainRemaining==0||now<rainTriggerReady)return false;
        rainTriggerReady=now+20;
        rainDamageUntil=now+60;
        return true;
    }
    /** Poll only when a valid victim exists. Missed ticks are not caught up in a burst. */
    public boolean pollRain(long now){
        Checks.tick(now);
        if(now>=rainUntil||now>=rainDamageUntil||rainRemaining==0||now<rainReady)return false;
        rainRemaining--;rainReady=now+20;return true;
    }
    public void switchOut(){forbiddenUntil=0;}
}
