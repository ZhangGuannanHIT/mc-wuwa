package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.Map;
import java.util.Set;
import static cn.zgnhit.mcwuwa.content.mechanics.MechanicsEngine.*;

/** Frozen 3.7 skill-tree node 8. Target acquisition is an explicit Minecraft host policy. */
public final class OutroRules {
    public static final Set<String> ANOMALIES=Set.of("glacio_chafe","glacio_frost","fusion_burst",
        "electro_flare","aero_erosion","spectro_frazzle","havoc_bane");
    public static final Set<String> CAP_EFFECTS=Set.of("glacio_chafe","fusion_burst","electro_flare",
        "aero_erosion","spectro_frazzle","havoc_bane","electro_rage");
    public static final Set<String> COUNTERS=Set.of("camellya_outro_enhanced","hsin_night_light","hsin_was_in_combat","hecate_outro_enabled","hsin_storm_charge");
    private OutroRules() { }
    public static String label(String id) {
        return switch(id) {
            case "zhezhi_outro" -> "冷凝·共鸣技能";
            case "lynae_outro" -> "全伤·解放";case "lynae_c2_outro" -> "琳奈二链·全伤";case "yinlin_outro" -> "导电·解放";
            case "phrolova_outro" -> "湮灭·重击";case "mornye_outro_all_amplification","aemeath_outro_other_roles_amp" -> "全伤";
            case "cartethyia_outro" -> "气动";case "yangyang_outro" -> "移宫换羽";case "yangyang_outro_active" -> "湮灭";
            case "hiyuki_outro" -> "冷凝";case "hsin_outro_electro" -> "导电";case "hsin_outro_unison" -> "灯同辉";
            case "chisa_outro" -> "异常上限";case "chisa_void_thread" -> "虚湮之线";
            case "phoebe_silent_prayer" -> "默祷·光噪";
            case "suisui_outro_amplification" -> "穗穗·全伤";case "suisui_chongming" -> "重明照影";
            case "suisui_smoke","suisui_smoke_next" -> "烟岚";case "suisui_smoke_attack" -> "烟岚·攻击";
            case "suisui_c2_crit" -> "穗穗二链·暴伤";case "cantarella_outro" -> "湮灭·共鸣技能";
            case "jinhsi_incandescence_icd" -> "韶光";default -> "";
        };
    }
    static void damage(Transition t,String element,double ratio) {
        t.effect(Kind.OUTRO_HIT,element,ratio,0);
    }
    static void camellya(Transition t) {
        damage(t,"havoc",3.2924);
        if(t.has("camellya_outro_enhanced"))damage(t,"havoc",4.5902);
        t.clear("camellya_outro_enhanced");
    }
    static void hsin(Transition t) {
        damage(t,"electro",1);
        if(t.has("hsin_night_light")) {
            if(t.original.mode().equals("electro"))
                t.effect(Kind.BUFF_TEAM,"hsin_outro_electro",.20,400,Map.of("excludeOwner",1d));
            else t.effect(Kind.BUFF_TEAM,"hsin_outro_unison",.20,600,Map.of("excludeOwner",1d));
            t.clear("hsin_night_light");
        }
    }
    static void tick(Transition t) {
        if(!t.original.characterId().equals("hsin"))return;
        if(t.context.inCombat()) {
            if(!t.has("hsin_was_in_combat"))t.timer("hsin_night_light",1);
            t.timer("hsin_was_in_combat",1);
        } else t.clear("hsin_was_in_combat");
    }
}
