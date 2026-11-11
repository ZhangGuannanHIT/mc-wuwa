package cn.zgnhit.mcwuwa.content.mechanics;

import cn.zgnhit.mcwuwa.content.CharacterDefinition;
import java.util.Map;

/** Qualifications captured by the accepted server cast, including delayed projectile hits. */
public final class NewSixActionRules {
    private NewSixActionRules() { }
    public static CharacterDefinition.DamageDefinition qualify(CharacterDefinition.DamageDefinition hit,Map<String,Double> cast) {
        if(cast.isEmpty())return hit;
        var raw=hit.raw();var attributes=new com.google.gson.JsonObject();
        transfer(cast,attributes,"attackBonus","attack_pct");
        transfer(cast,attributes,"fusionBonus","fusion_bonus");
        transfer(cast,attributes,"criticalDamage","crit_damage");
        transfer(cast,attributes,"defenseIgnore","defense_ignore");
        transfer(cast,attributes,"fusionResistanceIgnore","resistance_ignore");
        transfer(cast,attributes,"damageBonus","all_damage_bonus");
        transfer(cast,attributes,"liberationBonus","liberation_bonus");
        transfer(cast,attributes,"amplification","damage_amplification");
        if(!attributes.isEmpty())raw.add("acceptedActionModifiers",attributes);
        String kind=cast.getOrDefault("damageAsLiberation",0d)>0?"liberation":hit.kind();
        double multiplier=hit.multiplier()*(1+cast.getOrDefault("rateBonus",0d));
        return new CharacterDefinition.DamageDefinition(hit.id(),hit.sourceType(),kind,hit.element(),hit.scalingProperty(),
            hit.skillLevel(),multiplier,hit.toughnessUnits(),hit.offTuneUnits(),raw,hit.actionMappingVerified(),hit.unresolved());
    }
    private static void transfer(Map<String,Double> cast,com.google.gson.JsonObject target,String source,String attribute) {
        double amount=cast.getOrDefault(source,0d);if(amount!=0)target.addProperty(attribute,amount);
    }
}
