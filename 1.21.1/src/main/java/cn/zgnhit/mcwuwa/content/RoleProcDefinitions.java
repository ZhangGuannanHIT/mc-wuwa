package cn.zgnhit.mcwuwa.content;

import com.google.gson.JsonObject;
import java.util.List;

/** Proc coefficients that are explicit in frozen chain prose but have no published damage-table row. */
public final class RoleProcDefinitions {
    private RoleProcDefinitions() { }
    /** Prose-only solo proc. The caller supplies a server-owned fixed coefficient; no gauge guesses. */
    public static CharacterDefinition.DamageDefinition solo(String id,String kind,String element,String scaling,double ratio,java.util.Map<String,Double> flags) {
        if(!Double.isFinite(ratio)||ratio<0||ratio>10000)throw new IllegalArgumentException("proc coefficient");
        JsonObject raw=new JsonObject();raw.addProperty("missingNativeGaugeValues",true);raw.addProperty("dispatchEvidence","mc_adaptation");
        flags.forEach(raw::addProperty);
        return new CharacterDefinition.DamageDefinition(id,5,kind,element,scaling,10,ratio,0,0,raw,true,
            List.of("Source prose coefficient; explicit MC callback and geometry; no unpublished gauge credit."));
    }
    public static CharacterDefinition.DamageDefinition phrolovaSixthChain(CharacterDefinition source) {
        if (!source.characterId().equals("phrolova")) throw new IllegalArgumentException("Phrolova definition required");
        String evidence=source.chains().get(5).description();
        if(!evidence.contains("216.42%")||!evidence.contains("声骸技能伤害"))throw new IllegalStateException("Frozen Phrolova C6 evidence changed");
        JsonObject raw=new JsonObject();raw.addProperty("sourceCharacter",source.sourceId());raw.addProperty("sourceChain",6);raw.addProperty("sourceDescription",evidence);raw.addProperty("missingNativeGaugeValues",true);
        return new CharacterDefinition.DamageDefinition("phrolova_chain6_illusion",5,"echo","havoc","attack",10,2.1642,0,0,raw,true,
            List.of("Native coefficient is in frozen C6 prose; unpublished toughness/off-tune grant no gauge credit."));
    }
    /** Internal summon timeline, no player clip or second main-echo cast. Timing/geometry are explicit MC policy. */
    public static ActionDamageMapping phrolovaIllusionTimeline(CharacterDefinition source) {
        var damage=phrolovaSixthChain(source);
        return new ActionDamageMapping("phrolova/c6_illusion","重世幻象·赫卡忒","phrolova",source.id(),6,"chain6","216.42%","normal",
            true,true,List.of(new ActionDamageMapping.HitGroup(damage.id(),1)),damage.multiplier(),1,24,List.of(12),true,false,
            new ActionDamageMapping.Geometry("sphere",4,4,"autonomous",true,"minecraft_authored_approximation"),
            "Minecraft summon: accepted cast commits independent attack, contact at12ticks; native timing unavailable.",damage.unresolved());
    }
    public static CharacterDefinition.DamageDefinition yinlinSixthChain(CharacterDefinition source) {
        if (!source.characterId().equals("yinlin")) throw new IllegalArgumentException("Yinlin definition required");
        String evidence = source.chains().get(5).description();
        if (!evidence.contains("419.59%") || !evidence.contains("共鸣技能伤害")) throw new IllegalStateException("Frozen Yinlin C6 evidence changed");
        JsonObject raw = new JsonObject(); raw.addProperty("sourceCharacter", source.sourceId()); raw.addProperty("sourceChain", 6);
        raw.addProperty("sourceDescription", evidence); raw.addProperty("missingNativeGaugeValues", true);
        return new CharacterDefinition.DamageDefinition("yinlin_chain6_thunder", 4, "skill", "electro", "attack", 10,
            4.1959, 0, 0, raw, true, List.of("Native coefficient is in the frozen C6 description; unpublished toughness/off-tune grant no gauge credit."));
    }
}
