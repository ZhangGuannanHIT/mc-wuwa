package cn.zgnhit.mcwuwa.core;

/** Original7-second restricted phase and30-second realm are independent clocks.
 * The host applies source-owned entity stasis, not a global Minecraft tick pause.
 * Production world hooks own its target stasis, shields and realm presentation. */
public final class AugustaRealmLedger {
    public enum Exit { NONE, FINALE, INTERACTION }
    public record Shield(double amount,int ticks,boolean transferable) { }
    private final int chain;
    private int majesty,crowns,sunAttacks;
    private long realmUntil,phaseUntil,phaseCooldownUntil,shieldCooldownUntil,crownCooldownUntil;
    private long peacefulSince=-1,nextPeacefulGrant;
    private boolean phase,finalePending;
    public void pauseOneTick(){realmUntil=CinematicTimeRules.postpone(realmUntil,1);phaseUntil=CinematicTimeRules.postpone(phaseUntil,1);phaseCooldownUntil=CinematicTimeRules.postpone(phaseCooldownUntil,1);shieldCooldownUntil=CinematicTimeRules.postpone(shieldCooldownUntil,1);crownCooldownUntil=CinematicTimeRules.postpone(crownCooldownUntil,1);if(peacefulSince>=0)peacefulSince++;nextPeacefulGrant=CinematicTimeRules.postpone(nextPeacefulGrant,1);}
    public AugustaRealmLedger(int chain){if(chain<0||chain>6)throw new IllegalArgumentException("chain");this.chain=chain;}
    public int majesty(){return majesty;}
    public int crowns(){return crowns;}
    public int crownCap(){return chain>=6?4:chain>=1?2:1;}
    public void gainMajesty(){majesty=Math.min(2,majesty+1);}
    public void allyOutro(){gainMajesty();gainCrowns(1);}
    public void intro(){if(chain>=1)gainCrowns(1);}
    private void gainCrowns(int amount){crowns=Math.min(crownCap(),crowns+amount);}
    public boolean canEnter(long now){Checks.tick(now);return !phase&&!finalePending&&majesty==2&&now>=phaseCooldownUntil;}
    /** Call after accepting long-R. No resonance energy is spent here. */
    public void enter(long now){
        if(!canEnter(now))throw new IllegalStateException("realm unavailable");
        majesty=0;phase=true;sunAttacks=0;realmUntil=Math.addExact(now,600);
        phaseUntil=Math.addExact(now,140);phaseCooldownUntil=Math.addExact(now,500);
    }
    public boolean restricted(){return phase;}
    public boolean realmActive(long now){Checks.tick(now);return now<realmUntil;}
    public boolean canSunAttack(long now){Checks.tick(now);return phase&&now<phaseUntil&&sunAttacks<9;}
    public void sunAttack(long now){if(!canSunAttack(now))throw new IllegalStateException("sun chain unavailable");sunAttacks++;}
    public boolean canManualFinale(long now,boolean held){Checks.tick(now);return phase&&(held||sunAttacks>=9);}
    public Exit manualFinale(long now,boolean held){
        if(!canManualFinale(now,held))return Exit.NONE;
        phase=false;finalePending=true;return Exit.FINALE;
    }
    /** Interaction explicitly suppresses the automatic finisher; it does not
     * remove the separate30-second realm or spend crowns. */
    public Exit interact(){if(!phase)return Exit.NONE;phase=false;return Exit.INTERACTION;}
    public Exit tick(long now,boolean inCombat){
        Checks.tick(now);
        if(inCombat){peacefulSince=-1;nextPeacefulGrant=0;}
        else {
            if(peacefulSince<0)peacefulSince=now;
            if(now-peacefulSince>80&&now>=nextPeacefulGrant){majesty=Math.max(1,majesty);crowns=crownCap();nextPeacefulGrant=Math.addExact(now,80);}
        }
        if(phase&&now>=phaseUntil){phase=false;finalePending=true;return Exit.FINALE;}
        return Exit.NONE;
    }
    /** Clear crowns at actual finisher end, not before its damage calculation. */
    public void finishFinale(){if(finalePending){crowns=0;finalePending=false;}}
    public Shield introShield(double maxHealth,long now){
        Checks.nonnegative("max health",maxHealth);
        return realmActive(now)?new Shield(maxHealth*.05+650,200,false):null;
    }
    public Shield damageShield(double maxHealth,long now){
        Checks.nonnegative("max health",maxHealth);Checks.tick(now);
        if(now<shieldCooldownUntil)return null;shieldCooldownUntil=Math.addExact(now,10);
        return new Shield((maxHealth*.025+350)*(chain>=5?1.5:1),100,false);
    }
    /** Returns whether this C6 event awards its once-per-second two crowns;
     * the two100% thunder hits are separately emitted on every qualifying cast. */
    public boolean sixthChainHeavy(long now){
        Checks.tick(now);if(chain<6||now<crownCooldownUntil)return false;
        crownCooldownUntil=Math.addExact(now,20);gainCrowns(2);return true;
    }
    public double electroBonus(){return crowns*.15;}
    public double critChanceBonus(){return chain>=2?crowns*.20:0;}
    public double critDamageBonus(double totalCritChance){
        Checks.nonnegative("critical chance",totalCritChance);
        double result=chain>=1?crowns*.15:0;
        if(chain>=2)result+=Math.min(1,Math.max(0,totalCritChance-1)*2);
        if(chain>=6)result+=Math.min(.5,Math.max(0,totalCritChance-1.5)*2);
        return result;
    }
    public void clear(){majesty=crowns=sunAttacks=0;realmUntil=phaseUntil=phaseCooldownUntil=shieldCooldownUntil=crownCooldownUntil=nextPeacefulGrant=0;peacefulSince=-1;phase=finalePending=false;}
}
