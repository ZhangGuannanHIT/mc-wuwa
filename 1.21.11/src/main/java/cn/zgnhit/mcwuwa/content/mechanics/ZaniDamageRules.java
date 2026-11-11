package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.Set;

/** Original source1507 conditions. Packet timing/allocation is authored separately. */
public final class ZaniDamageRules {
    private static final Set<String> HEAVIES=Set.of("zani/heavy_dawn","zani/heavy_day","zani/heavy_night","zani/heavy_night_last","zani/heavy_flash");
    private ZaniDamageRules(){}
    public static double coefficient(MechanicsState state,String action,String kind){
        if(!state.characterId().equals("zani"))return 0;
        double result=state.form().equals("blazing")&&kind.equals("basic")?.25:0;
        if(state.chain()>=2&&Set.of("zani/suppression","zani/breach_counter").contains(action))result+=.80;
        if(state.chain()>=5&&action.equals("zani/liberation"))result+=1.20;
        if(state.chain()>=6&&HEAVIES.contains(action))result+=.40;
        return result;
    }
    public static double thorns(MechanicsState state,String action,String element){
        return state.characterId().equals("zani")&&state.timer("zani_thorns")>0
            &&DamageQualifications.directSpectroFrazzle(action,element)?.20:0;
    }
    public static double critRate(MechanicsState state){return state.characterId().equals("zani")&&state.chain()>=2?.20:0;}
}
