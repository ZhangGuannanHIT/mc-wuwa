package cn.zgnhit.mcwuwa.core;

import java.util.*;

/** One vocabulary between frozen stat keys and typed formula zones. No numeric values live here. */
public final class AttributeBindings {
    private AttributeBindings(){}
    public static AttributeModifiers.Zone zone(String key) {
        return switch(key) {
            case "attack_flat"->AttributeModifiers.Zone.ATTACK_FLAT;case "attack_pct"->AttributeModifiers.Zone.ATTACK_PERCENT;
            case "health_flat"->AttributeModifiers.Zone.HEALTH_FLAT;case "health_pct"->AttributeModifiers.Zone.HEALTH_PERCENT;
            case "defense_flat"->AttributeModifiers.Zone.DEFENSE_FLAT;case "defense_pct"->AttributeModifiers.Zone.DEFENSE_PERCENT;
            case "crit_rate"->AttributeModifiers.Zone.CRIT_RATE;case "crit_damage"->AttributeModifiers.Zone.CRIT_MULTIPLIER;
            case "energy_regen"->AttributeModifiers.Zone.ENERGY_EFFICIENCY;case "healing_bonus"->AttributeModifiers.Zone.HEALING_BONUS;
            case "incoming_healing_bonus"->AttributeModifiers.Zone.INCOMING_HEALING_BONUS;case "shield_bonus"->AttributeModifiers.Zone.SHIELD_BONUS;
            case "all_damage_bonus","basic_bonus","heavy_bonus","skill_bonus","liberation_bonus","intro_bonus","outro_bonus","echo_bonus",
                "glacio_bonus","fusion_bonus","electro_bonus","aero_bonus","spectro_bonus","havoc_bonus"->AttributeModifiers.Zone.DAMAGE_BONUS;
            case "damage_amplification"->AttributeModifiers.Zone.AMPLIFICATION;case "damage_vulnerability"->AttributeModifiers.Zone.VULNERABILITY;
            case "damage_reduction"->AttributeModifiers.Zone.DAMAGE_REDUCTION;case "skill_ratio_bonus"->AttributeModifiers.Zone.SKILL_RATIO_BONUS;
            case "flat_damage"->AttributeModifiers.Zone.FLAT_DAMAGE;case "final_damage"->AttributeModifiers.Zone.FINAL_DAMAGE;
            case "resistance_reduction"->AttributeModifiers.Zone.RESISTANCE_REDUCTION;case "resistance_ignore"->AttributeModifiers.Zone.RESISTANCE_IGNORE;
            case "defense_reduction"->AttributeModifiers.Zone.DEFENSE_REDUCTION;case "defense_ignore"->AttributeModifiers.Zone.DEFENSE_IGNORE;
            case "offtune_efficiency"->AttributeModifiers.Zone.OFFTUNE_EFFICIENCY;case "tune_break_amplification"->AttributeModifiers.Zone.TUNE_BREAK_AMPLIFICATION;
            case "effect_damage_bonus"->AttributeModifiers.Zone.EFFECT_DAMAGE_BONUS;case "effect_stack_cap"->AttributeModifiers.Zone.EFFECT_STACK_CAP;
            default->throw new IllegalArgumentException("Unknown numeric attribute: "+key);
        };
    }
    public static String key(AttributeModifiers.Zone zone) {
        return switch(zone) {
            case ATTACK_FLAT->"attack_flat";case ATTACK_PERCENT->"attack_pct";case HEALTH_FLAT->"health_flat";case HEALTH_PERCENT->"health_pct";
            case DEFENSE_FLAT->"defense_flat";case DEFENSE_PERCENT->"defense_pct";case CRIT_RATE->"crit_rate";case CRIT_MULTIPLIER->"crit_damage";
            case ENERGY_EFFICIENCY->"energy_regen";case HEALING_BONUS->"healing_bonus";case INCOMING_HEALING_BONUS->"incoming_healing_bonus";
            case SHIELD_BONUS->"shield_bonus";case DAMAGE_BONUS->"all_damage_bonus";case AMPLIFICATION->"damage_amplification";
            case VULNERABILITY->"damage_vulnerability";case DAMAGE_REDUCTION->"damage_reduction";case SKILL_RATIO_BONUS->"skill_ratio_bonus";
            case FLAT_DAMAGE->"flat_damage";case FINAL_DAMAGE->"final_damage";case RESISTANCE_REDUCTION->"resistance_reduction";
            case RESISTANCE_IGNORE->"resistance_ignore";case DEFENSE_REDUCTION->"defense_reduction";case DEFENSE_IGNORE->"defense_ignore";
            case OFFTUNE_EFFICIENCY->"offtune_efficiency";case TUNE_BREAK_AMPLIFICATION->"tune_break_amplification";
            case EFFECT_DAMAGE_BONUS->"effect_damage_bonus";case EFFECT_STACK_CAP->"effect_stack_cap";
        };
    }
    public static DamageKind kind(String tag) {
        return switch(tag) {
            case "basic"->DamageKind.BASIC;case "heavy"->DamageKind.HEAVY;case "skill"->DamageKind.RESONANCE_SKILL;
            case "liberation"->DamageKind.RESONANCE_LIBERATION;case "intro"->DamageKind.INTRO;case "outro"->DamageKind.OUTRO;
            case "echo"->DamageKind.ECHO;case "effect","elemental_effect"->DamageKind.ELEMENTAL_EFFECT;case "tune_break"->DamageKind.TUNE_BREAK;
            case "coordinated"->DamageKind.COORDINATED;case "incoming","all","vanilla"->DamageKind.VANILLA;
            case "environment"->DamageKind.ENVIRONMENT;case "self_damage"->DamageKind.SELF_DAMAGE;
            case "unverified"->DamageKind.UNCLASSIFIED;
            default->throw new IllegalArgumentException("Unknown damage tag: "+tag);
        };
    }
    public static AttributeModifiers.Context context(String tag,String element,boolean direct,boolean melee) {
        return new AttributeModifiers.Context(element.equals("all")?Element.PHYSICAL:Element.valueOf(element.toUpperCase(Locale.ROOT)),kind(tag),direct,melee);
    }
    public static AttributeModifiers.Selector selector(String attribute,String tag,String element) {
        Set<DamageKind> kinds=tag.equals("all")?Set.of():Set.of(kind(tag));Set<Element> elements=element.equals("all")?Set.of():Set.of(Element.valueOf(element.toUpperCase(Locale.ROOT)));
        if(attribute.endsWith("_bonus")&&!attribute.equals("all_damage_bonus")) {
            String prefix=attribute.substring(0,attribute.length()-6);
            if(Set.of("basic","heavy","skill","liberation","intro","outro","echo").contains(prefix))kinds=Set.of(kind(prefix));
            if(Set.of("glacio","fusion","electro","aero","spectro","havoc").contains(prefix))elements=Set.of(Element.valueOf(prefix.toUpperCase(Locale.ROOT)));
        }
        return new AttributeModifiers.Selector(elements,kinds,false,false);
    }
    public static Map<String,Double> values(AttributeModifiers.View view) {
        Map<String,Double> result=new HashMap<>();for(var entry:view.groups().entrySet())result.put(key(entry.getKey()),view.additive(entry.getKey()));return Map.copyOf(result);
    }
}
