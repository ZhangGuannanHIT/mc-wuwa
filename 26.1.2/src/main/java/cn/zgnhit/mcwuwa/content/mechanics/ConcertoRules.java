package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.regex.Pattern;
import java.util.List;
import static cn.zgnhit.mcwuwa.content.mechanics.MechanicsEngine.*;

/** Conservative MC solo-play fallback for ordinary confirmed actions. This is deliberately
 * separate from audited resonance Energy and SpecialEnergy: ElementPower is not reinterpreted.
 * The runtime calls onHit once per accepted action, not once per hit index or target.
 * Explicit skill/liberation and consumed-pistil rewards remain separate sourced rules. */
public final class ConcertoRules {
    public record SourceGrant(String action,int node,String parameter,double amount,boolean confirmedHit) { }
    /** Each literal amount can be checked against the frozen source skill parameter. Existing
     * strategy payouts win; they can include additional passive-qualified awards. */
    public static final List<SourceGrant> SOURCE_GRANTS=List.of(
        new SourceGrant("jinhsi/skill_overflowing",2,"神霓飞芒回复协奏能量",4,false),
        new SourceGrant("jinhsi/liberation",3,"回复协奏能量",20,false),
        new SourceGrant("jinhsi/intro",6,"回复协奏能量",10,false),
        new SourceGrant("yinlin/skill",2,"磁殛咆哮回复协奏能量",10,false),
        new SourceGrant("yinlin/skill_blast",2,"召雷磁爆回复协奏能量",15,false),
        new SourceGrant("yinlin/magnetic_blast",2,"磁殛爆发回复协奏能量",5,true),
        new SourceGrant("yinlin/liberation",3,"回复协奏能量",20,false),
        new SourceGrant("aero_rover/skill",2,"苍息破象回复协奏能量",10,false),
        new SourceGrant("aero_rover/skill_sky",2,"碧霄断行回复协奏能量",5,false),
        new SourceGrant("aero_rover/liberation",3,"回复协奏能量",20,false),
        new SourceGrant("cartethyia/skill",2,"共鸣技能回复协奏能量",10,false),
        new SourceGrant("cartethyia/fleurdelys_skill",7,"共鸣技能·此剑为潮浪之意回复协奏能量",10,false),
        new SourceGrant("cartethyia/fleurdelys_skill_followup",7,"共鸣技能·凭风斩浪破敌回复协奏能量",10,false),
        new SourceGrant("cartethyia/intro",6,"此剑，为潮水的过去回复协奏能量",10,false),
        new SourceGrant("cartethyia/fleurdelys_intro",6,"此剑，为自由的未来回复协奏能量",10,false),
        new SourceGrant("camellya/skill",2,"红椿盛绽回复协奏能量",7,false),
        new SourceGrant("camellya/bloom_dark_heart",2,"黯蕊猎心回复协奏能量",7,false),
        new SourceGrant("camellya/liberation",3,"回复协奏能量",20,false),
        new SourceGrant("changli/skill",2,"心眼·劫回复协奏能量",14,false),
        new SourceGrant("changli/true_sight_conquest",2,"心眼·征回复协奏能量",7,false),
        new SourceGrant("changli/true_sight_charge",2,"心眼·冲回复协奏能量",6,false),
        new SourceGrant("changli/heavy_flaming_sacrifice",7,"回复协奏能量",10,false),
        new SourceGrant("changli/heavy_flaming_sacrifice_c5",7,"回复协奏能量",10,false),
        new SourceGrant("carlotta/heavy_restrictive",1,"限制性策略回复协奏能量",15,false),
        new SourceGrant("carlotta/skill",2,"回复协奏能量",5,false),
        new SourceGrant("carlotta/skill_c3",2,"回复协奏能量",5,false),
        new SourceGrant("carlotta/skill_show_brilliance",2,"示我璀璨回复协奏能量",5,false),
        new SourceGrant("carlotta/skill_show_brilliance_c3",2,"示我璀璨回复协奏能量",5,false),
        new SourceGrant("carlotta/death_knell_01",3,"死兆回复协奏能量",7,false),
        new SourceGrant("carlotta/death_knell_01_c6",3,"死兆回复协奏能量",7,false),
        new SourceGrant("carlotta/death_knell_02",3,"死兆回复协奏能量",7,false),
        new SourceGrant("carlotta/death_knell_02_c6",3,"死兆回复协奏能量",7,false),
        new SourceGrant("carlotta/death_knell_03",3,"死兆回复协奏能量",7,false),
        new SourceGrant("carlotta/death_knell_03_c6",3,"死兆回复协奏能量",7,false),
        new SourceGrant("carlotta/death_knell_04",3,"死兆回复协奏能量",7,false),
        new SourceGrant("carlotta/death_knell_04_c6",3,"死兆回复协奏能量",7,false),
        new SourceGrant("carlotta/fatal_finale",3,"致死以终回复协奏能量",10,false),
        new SourceGrant("carlotta/fatal_finale_c2",3,"致死以终回复协奏能量",10,false),
        new SourceGrant("carlotta/heavy_end_of_road",7,"末路见行回复协奏能量",15,false),
        new SourceGrant("carlotta/heavy_end_of_road_c5",7,"末路见行回复协奏能量",15,false),
        new SourceGrant("iuno/skill",2,"原初的律动回复协奏能量",6,false),
        new SourceGrant("iuno/skill_ending",2,"告终/未终的喧响回复协奏能量",8,false),
        new SourceGrant("iuno/skill_unending",2,"告终/未终的喧响回复协奏能量",8,false),
        new SourceGrant("iuno/skill_beyond",2,"越限的弦引回复协奏能量",8,false),
        new SourceGrant("iuno/skill_beyond_enhanced",2,"越限的弦引回复协奏能量",8,false),
        new SourceGrant("lupa/intro_pursuit",6,"你无法逃离！回复协奏能量",10,false),
        new SourceGrant("lupa/intro_pursuit_c3",6,"你无法逃离！回复协奏能量",10,false),
        new SourceGrant("denia/liberation_disillusion",3,"帷幕终景·幻灭之形回复协奏能量",20,false),
        new SourceGrant("denia/liberation_disillusion_c3",3,"帷幕终景·幻灭之形回复协奏能量",20,false),
        new SourceGrant("denia/intro_disillusion",6,"轻叩门扉回复协奏能量",10,false),
        new SourceGrant("qingxiao/heavy_banishing",7,"天钧荡煞·昙体仙身回复协奏能量",25,false),
        new SourceGrant("qingxiao/heavy_banishing_enhanced",7,"天钧荡煞·昙体仙身回复协奏能量",25,false),
        new SourceGrant("changli/liberation",3,"回复协奏能量",20,false),
        new SourceGrant("changli/intro",6,"回复协奏能量",10,false),
        new SourceGrant("carlotta/liberation",3,"回复协奏能量",20,false),
        new SourceGrant("carlotta/intro",6,"回复协奏能量",10,false),
        new SourceGrant("iuno/liberation",3,"回复协奏能量",20,false),
        new SourceGrant("iuno/intro",6,"回复协奏能量",10,false),
        new SourceGrant("lupa/liberation",3,"回复协奏能量",20,false),
        new SourceGrant("lupa/intro",6,"回复协奏能量",10,false),
        new SourceGrant("denia/liberation",3,"回复协奏能量",20,false),
        new SourceGrant("denia/intro",6,"回复协奏能量",10,false),
        new SourceGrant("qingxiao/liberation",3,"回复协奏能量",20,false),
        new SourceGrant("qingxiao/intro",6,"回复协奏能量",10,false));
    private static final Pattern BASIC=Pattern.compile("(?:(?:mech|cruise|observation|incarnation|bloom|fleurdelys|future|illuminated|thousand_palaces|feather|listening|saw|answer)_)?basic_\\d{2}(?:_hold)?");
    private static final Pattern COUNTER=Pattern.compile("(?:[a-z]+_)?dodge_counter|fleurdelys_counter|saw_counter|listening_counter_\\d{2}");
    private static final Pattern AIR=Pattern.compile("(?:(?:mech|cruise|future|incarnation|illuminated|feather|fleurdelys|wind)_)?air(?:_\\d{2})?");
    private ConcertoRules() { }
    static void cast(Transition t) {
        if(!t.context.active()||t.effects.stream().anyMatch(e->e.kind()==Kind.GAIN_CONCERTO))return;
        for(var effect:List.copyOf(t.effects))if(effect.kind()==Kind.ACTION){
            for(var rule:SOURCE_GRANTS)if(!rule.confirmedHit()&&rule.action().equals(effect.id()))
                t.effect(Kind.GAIN_CONCERTO,"source_cast_concerto",rule.amount(),0);
            // Additional source payouts are not ordinary-action MC fallback.
            if(effect.id().startsWith("iuno/")){
                double extra=effect.values().getOrDefault("spiritualExtraConcerto",0d);
                if(extra>0)t.effect(Kind.GAIN_CONCERTO,"iuno_spiritual_extra",extra,0);
            }
            if(effect.id().equals("lupa/liberation")&&t.original.chain()>=1)
                t.effect(Kind.GAIN_CONCERTO,"lupa_c1_liberation_extra",10,0);
            if(effect.id().startsWith("denia/liberation_disillusion")&&t.original.chain()>=3
                &&t.original.timer("denia_entropy_dark")>0)
                t.effect(Kind.GAIN_CONCERTO,"denia_c3_liberation_extra",30,0);
        }
    }
    static void hit(Transition t,String action) {
        if(!t.context.active()||!action.startsWith(t.original.characterId()+"/"))return;
        for(var rule:SOURCE_GRANTS)if(rule.confirmedHit()&&rule.action().equals(action))
            t.effect(Kind.GAIN_CONCERTO,"source_confirmed_concerto",rule.amount(),0);
        // A known source grant replaces the ordinary action fallback even if paid on cast.
        if(SOURCE_GRANTS.stream().anyMatch(r->r.action().equals(action)))return;
        String local=action.substring(action.indexOf('/')+1);
        if(action.equals("ciaccona/heavy_quartet"))return; // Source25 is paid by the accepted cast.
        boolean six=java.util.Set.of("changli","carlotta","iuno","lupa","denia","qingxiao").contains(t.original.characterId());
        // Unsourced ordinary sequences retain the existing MC 3/4/5 fallback, once per action.
        // These names are exact new-character ordinary families, not every skill/damage tag.
        if(six){
            String ordinary=local.replaceFirst("_enhanced$","");
            if(ordinary.matches("(?:(?:bow|ring|immortal|disillusion)_)?(?:air_)?basic_0[1-4]|necessary_0[1-3]")){
                t.effect(Kind.GAIN_CONCERTO,"mc_confirmed_direct_action",ordinary.contains("air_basic")?4:3,0);return;
            }
            if(ordinary.equals("bow_counter")||ordinary.equals("immortal_counter")||ordinary.equals("disillusion_counter")){
                t.effect(Kind.GAIN_CONCERTO,"mc_confirmed_direct_action",5,0);return;
            }
        }
        double amount=BASIC.matcher(local).matches()||local.equals("bloom_spinning")?3:
            COUNTER.matcher(local).matches()?5:local.contains("heavy")?5:AIR.matcher(local).matches()?4:0;
        if(amount>0)t.effect(Kind.GAIN_CONCERTO,"mc_confirmed_direct_action",amount,0);
    }
}
