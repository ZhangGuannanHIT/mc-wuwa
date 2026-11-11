package cn.zgnhit.mcwuwa.content.mechanics;

/** Source chain5/6 protection; evaluated after reductions, before actual native health loss.
 * The ten-minute cooldown is weapon-owned and survives role/form changes and save/rejoin. */
public final class DeathProtectionRules {
    public record Protection(boolean allowed,double healFraction,int immuneTicks,double shieldFraction,boolean ghost,boolean cooldown) { }
    private static final Protection NONE=new Protection(false,0,0,0,false,false);
    private DeathProtectionRules() { }
    public static Protection choose(MechanicsState state,String action,boolean cooldownReady) {
        if(state.characterId().equals("chisa")&&state.chain()>=6
            &&action.startsWith("chisa/")&&ChisaSawRules.empoweredAttack(action))
            return new Protection(true,0,0,0,false,false);
        if(state.chain()<5||!cooldownReady)return NONE;
        return switch(state.characterId()) {
            case "aemeath"->new Protection(true,0,100,0,true,true);
            case "hsin"->new Protection(true,1,20,0,false,true);
            case "cartethyia"->new Protection(true,0,0,.20,false,true);
            case "yangyang_xuanling"->new Protection(true,.50,60,0,false,true);
            default->NONE;
        };
    }
}
