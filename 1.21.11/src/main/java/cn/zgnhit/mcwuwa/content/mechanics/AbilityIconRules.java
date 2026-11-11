package cn.zgnhit.mcwuwa.content.mechanics;

/** Authored binding of archived original B/C variants to executable MC actions.
 * Availability/brightness is separate: a cooldown must not turn the current form's
 * icon into another form. No server-controlled arbitrary resource path is accepted. */
public final class AbilityIconRules {
    private AbilityIconRules() { }
    /** Read-only authoritative focus projection, never a client qualification. */
    public static cn.zgnhit.mcwuwa.core.LucillaFocusWindow.View focusView(MechanicsState state){
        return state.characterId().equals("lucilla")?LucillaMechanics.focusView(state):null;
    }
    public static boolean enhancedBasic(String action){
        // These forte replacements are still named basic_0N. Treating every
        // such name as ordinary hid their earned basic-button feedback.
        return !action.isEmpty()&&(action.startsWith("qiuyuan/answer_basic_")
            ||action.startsWith("yangyang_xuanling/listening_basic_")
            ||action.startsWith("jinhsi/incarnation_basic_")
            ||!action.matches(".*/(?:.*_)?basic_0[1-5]|.*/(?:mech_|cruise_)?air"));
    }
    /** Conditional long-press replacements, after the real reducer accepts them. */
    public static boolean qualifiedHold(MechanicsState state,String action,HeldAttackRules.Mode mode) {
        return switch(state.characterId()) {
            case "suoming" -> state.timer("suoming_heart_ready")>0||state.timer("suoming_spin_ready")>0||state.timer("suoming_spin_second")>0;
            case "augusta" -> state.resource("war")>=100||state.timer("augusta_spin_window")>0||state.form().equals("realm");
            case "zani" -> state.form().equals("blazing")&&state.resource("blaze")>=30;
            case "lucilla" -> state.form().equals("memory")||state.timer("lucilla_focus_channel")==1;
            case "zhezhi" -> action.equals("zhezhi/heavy_conjuration");
            case "changli" -> action.startsWith("changli/heavy_flaming_sacrifice");
            case "carlotta" -> !state.form().equals("gunflower")&&state.resource("substance")>=120;
            case "iuno" -> action.startsWith("iuno/heavy_");
            case "lupa" -> action.equals("lupa/heavy_wolf_bite")||action.equals("lupa/heavy_sharp_claw")||action.equals("lupa/air_flame_strike");
            case "denia" -> action.endsWith("_enhanced");
            case "qingxiao" -> state.form().equals("immortal")?state.resource("sword_intent")>=100:state.resource("qin_heart")>=100&&state.resource("sword_rhyme")>=100;
            case "phoebe" -> state.resource("prayer")>=120&&state.resource("gospel")==0||state.timer("phoebe_starlight")>0&&state.resource("gospel")>0;
            case "cantarella" -> state.resource("trance")>0;
            case "suisui" -> state.form().equals("rain");
            case "shorekeeper" -> state.resource("data")>=5;
            case "sigrika" -> state.timer("sig_rune_count")>=2;
            case "galbrena" -> state.form().equals("demon")&&mode==HeldAttackRules.Mode.GALBRENA_HEAVY_LOOP;
            case "qiuyuan" -> action.contains("/ink_heavy_");
            case "ciaccona" -> action.equals("ciaccona/heavy_quartet");
            case "yinlin" -> action.equals("yinlin/chameleon");
            case "mornye" -> action.equals("mornye/heavy_potential")||action.equals("mornye/heavy_inversion");
            case "aero_rover" -> action.equals("aero_rover/heavy_strangle");
            case "hsin" -> java.util.Set.of("hsin/worldly_step","hsin/worldly_suppress","hsin/illuminated_suppress","hsin/illuminated_descend").contains(action);
            case "hiyuki" -> action.equals("hiyuki/future_heavy_frost")||action.equals("hiyuki/heavy")&&state.resource("mind")>=300;
            case "chisa" -> action.equals("chisa/shattered_plane");
            case "yangyang_xuanling" -> action.equals("yangyang_xuanling/heavy")||action.equals("yangyang_xuanling/feather_heavy");
            case "aemeath" -> state.timer("immediate")>0||state.timer("aemeath_brilliance")>0;
            case "lynae" -> state.resource("overflow")>=120;
            case "camellya" -> state.form().equals("bloom")&&mode==HeldAttackRules.Mode.CAMELLYA_LOOP;
            default -> false;
        };
    }
    /** Reducer acceptance and ready state remain mandatory at the host. */
    public static boolean qualifiedSkill(MechanicsState state,String action) {
        return switch(state.characterId()) {
            case "suoming" -> state.resource("obsession")>=800||state.timer("suoming_heart_ready")>0;
            case "augusta" -> state.resource("power")>=100||state.timer("augusta_skill_link")>0||state.timer("augusta_skill_upper_window")>0&&state.resource("war")>=100;
            case "zani" -> state.form().equals("blazing")?state.resource("blaze")>=30:state.resource("redundant")>=100;
            case "lucilla" -> state.timer("lucilla_focus_channel")==2;
            case "zhezhi" -> state.timer("zhezhi_images")>0;
            case "carlotta" -> state.timer("carlotta_show_brilliance")>0;
            case "iuno" -> state.timer("iuno_ending_ready")>0||state.form().equals("new_moon")&&state.resource("spirituality")>0;
            case "lupa" -> state.resource("wolf_soul")>=2||state.timer("lupa_break_enemy_ready")>0||state.timer("lupa_ferocious_ready")>0;
            case "denia" -> state.form().equals("disillusion")&&state.resource("dark_core")>0||action.equals("denia/skill_c3_full_core");
            case "qingxiao" -> state.timer("qingxiao_ascend_ready")>0||action.equals("qingxiao/air_basic_03");
            default -> false;
        };
    }
    public static boolean qualifiedLiberation(MechanicsState state,String action) {
        return switch(state.characterId()) {
            case "augusta" -> state.resource("majesty")>=2||state.form().equals("realm");
            case "zani" -> state.form().equals("blazing")&&(state.resource("blaze")<30||state.timer("zani_blazing")<=240);
            case "lucilla" -> !state.form().equals("memory")&&state.resource("impression")>=150;
            case "carlotta" -> state.form().equals("gunflower");
            case "denia" -> state.form().equals("disillusion")&&state.resource("conformal")>=100;
            default -> false;
        };
    }
    public static String variant(MechanicsState s,String button,String fullAction,boolean enhanced) {
        String a=fullAction.substring(fullAction.lastIndexOf('/')+1);
        // Original sibling images; contextual action bindings are authored.
        // Keep form-specific art even while its condition/cooldown disables it.
        if(java.util.Set.of("changli","carlotta","iuno","lupa","denia","qingxiao").contains(s.characterId())) {
            if(button.equals("basic"))return enhanced?"forte":"basic";
            if(button.equals("liberation"))return switch(s.characterId()) {
                case "denia" -> s.form().equals("disillusion")?"c2":"liberation";
                case "carlotta" -> s.form().equals("gunflower")?(s.timer("carlotta_mirror")>=4?"c3":"c2"):"liberation";
                default -> "liberation";
            };
            if(!button.equals("skill"))return button;
            return switch(s.characterId()) {
                case "denia" -> s.form().equals("disillusion")?(s.timer("denia_exile_follow")>0?"b4":s.resource("dark_core")>0?"b3":"b2"):"skill";
                case "iuno" -> s.form().equals("new_moon")?"b4":s.form().equals("crescent")?"b3":s.timer("iuno_ending_ready")>0?"b2":"skill";
                case "lupa" -> s.resource("wolf_soul")>=2?(s.timer("lupa_match_point")>0||s.chain()>=6?"b4":"b3"):s.timer("lupa_ferocious_ready")>0?"b2":"skill";
                case "qingxiao" -> a.equals("air_basic_03")?"b3":s.timer("qingxiao_ascend_ready")>0?"b2":"skill";
                case "carlotta" -> s.timer("carlotta_show_brilliance")>0?"b2":"skill";
                default -> "skill";
            };
        }
        if(button.equals("basic"))return enhanced?"forte":"basic";
        if(button.equals("liberation"))return switch(s.characterId()) {
            case "aemeath"->s.timer("release")>0?"c2":"liberation";
            case "cartethyia"->s.form().equals("fleurdelys")?(s.resource("resolve")>=120?"c3":"c2"):"liberation";
            case "hiyuki"->s.form().equals("future")?"c2":"liberation";
            case "hsin"->a.equals("liberation_palaces")?"c3":s.form().equals("illuminated")||s.timer("heart_state")>0?"c2":"liberation";
            case "phrolova"->s.form().equals("conducting")?"c2":"liberation";
            default->"liberation";
        };
        if(!button.equals("skill"))return button;
        return switch(s.characterId()) {
            case "zhezhi" -> s.timer("zhezhi_images")>0?"forte":"skill";
            case "phoebe" -> s.timer("phoebe_teleport")>0?"b2":"skill";
            case "suisui" -> !s.form().equals("rain")&&s.resource("water_breath")>=120?"b2":"skill";
            case "cantarella" -> s.form().equals("mirage")?(s.resource("shiver")>=3?"b3":"b2"):"skill";
            case "sigrika" -> a.equals("skill_semantics")?"b4":a.equals("skill_sun")?"b3":a.equals("skill_insight")?"b2":"skill";
            case "galbrena" -> a.equals("skill_ascend")||s.form().equals("demon")?"b2":"skill";
            case "aemeath" -> a.equals("wing_ensemble_descend")?"b4":a.equals("wing_ensemble_stage")?"b3":s.timer("intro_followup")==0&&s.timer("wing_window")>0&&s.resource("synchronization")>=100?(s.form().equals("mech")?"b3":"b4"):s.form().equals("mech")?"b2":"skill";
            case "jinhsi" -> a.startsWith("epiphany")||s.timer("epiphany")>0?"b4":a.equals("skill_crescent")||s.form().equals("incarnation")?"b3":a.equals("skill_overflowing")||s.timer("overflowing")>0?"b2":"skill";
            case "mornye" -> s.form().equals("observation")?"b2":"skill";
            case "cartethyia" -> s.form().equals("fleurdelys")?"b2":"skill";
            case "camellya" -> a.equals("immortal_blossom")?"b4":a.equals("ephemeral")?"b3":s.form().equals("bloom")?"b2":"skill";
            case "yinlin" -> a.equals("skill_blast")||s.timer("magnetic")>0?"forte":"skill";
            case "aero_rover" -> a.equals("unbound_wind_02")?"b3":a.equals("unbound_wind_01")?"b2":"skill";
            case "yangyang_xuanling" -> a.equals("flow_azure")?"b3":a.equals("flow_feather")?"b4":s.form().equals("feather")||s.form().equals("listening")?"b2":"skill";
            case "hsin" -> a.equals("palaces_formation")||s.form().equals("illuminated")&&s.resource("illuminated_heart")>=300?"b3":s.form().equals("illuminated")?"b2":"skill";
            case "hiyuki" -> a.equals("skill_falling")?"b3":s.form().equals("future")?"b2":"skill";
            case "chisa" -> a.equals("skill_saw_hold")?"b3":a.equals("skill_saw_cycle")||s.resource("saw_echo")>=100&&!s.form().equals("saw")?"b2":"skill";
            case "lynae" -> a.equals("visual_impact")||a.equals("rainbow_splash")?"b3":s.form().equals("cruise")?"b2":"skill";
            case "phrolova" -> a.equals("undying_reverie")||s.timer("reincarnation")>0?"b2":s.form().equals("conducting")?"b3":"skill";
            default -> "skill";
        };
    }
}
