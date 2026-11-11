package cn.zgnhit.mcwuwa.core;

import java.util.Map;

/** Action-specific clocks for shared visual props; their meshes remain shared. */
public final class ActionPropAnimationRules {
    public static final Map<String,String> MODELS=Map.of(
        "qingxiao/liberation","qingxiao_sword_zither",
        "qingxiao/heavy_banishing","qingxiao_sword_zither",
        "qingxiao/heavy_banishing_enhanced","qingxiao_sword_zither",
        "phrolova/intro","hecate",
        "phrolova/intro_eternal","hecate",
        "sigrika/skill_sun","sigrika_sun_sprite",
        "sigrika/rune_sun","sigrika_sun_sprite",
        "sigrika/heavy_origin_sun","sigrika_sun_sprite",
        "lupa/liberation","lupa_flame_wolf_heads",
        "lupa/skill_break_enemy","lupa_flame_wolf_heads");
    private ActionPropAnimationRules(){}
    public static String forAction(String action,String fallback){
        return MODELS.containsKey(action)?action.substring(action.indexOf('/')+1):fallback;
    }
    public static boolean allowed(String model,String clip){
        return MODELS.entrySet().stream().anyMatch(e->e.getValue().equals(model)
            &&e.getKey().substring(e.getKey().indexOf('/')+1).equals(clip));
    }
}
