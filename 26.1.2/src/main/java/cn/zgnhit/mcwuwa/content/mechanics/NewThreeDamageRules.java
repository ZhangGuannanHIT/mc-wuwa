package cn.zgnhit.mcwuwa.content.mechanics;

/** Runic followups are distinct damage rows: their resource bonuses must never
 * leak into the preceding 132.51% heavy-attack body. */
public final class NewThreeDamageRules {
    public record Factors(double coefficient,double amplification,double defenseIgnore){}
    private static final Factors NONE=new Factors(0,1,0);
    private NewThreeDamageRules(){}
    /** Doom amplifies Galbrena's listed character skills, even when their damage
     * is classified as echo. It does not amplify a separately equipped echo. */
    public static boolean galbrenaDoomApplies(String action){
        return action!=null&&action.startsWith("galbrena/");
    }
    public static Factors sigrika(MechanicsState state,String action,String damageId){
        if(!state.characterId().equals("sigrika")||!action.startsWith("sigrika/heavy_origin_")||damageId.equals("1412202001"))return NONE;
        int spent=state.timer("sig_origin_spent"),layers=state.timer("sig_talent");
        double resource=spent>=30?1:1+Math.floor(spent/10d)*.15;
        double talent=1+layers*.30+(state.chain()>=6?Math.min(.60,layers*.15):0);
        return new Factors(spent>=30?.50:0,cn.zgnhit.mcwuwa.core.CombatMath.amplificationFactor(resource,talent),state.chain()>=6?Math.min(.30,layers*.075):0);
    }
}
