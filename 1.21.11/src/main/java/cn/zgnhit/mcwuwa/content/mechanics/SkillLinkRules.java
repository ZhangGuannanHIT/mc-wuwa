package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.Set;
import static cn.zgnhit.mcwuwa.content.mechanics.MechanicsEngine.*;

/** Explicit conditional action links; the host must first validate the same pure result.
 * One tick after the last scheduled hit is an MC cancellation boundary, not an original frame claim.
 * No damage/resource conditions are fabricated here. Ordinary busy skills remain rejected. */
public final class SkillLinkRules {
    private SkillLinkRules() { }
    public static boolean canReplace(MechanicsState state,String current,Input input,Result preview,long elapsed,int lastHit) {
        if(!preview.accepted())return false;
        if(state.characterId().equals("aero_rover")&&input==Input.HEAVY_START
            &&elapsed>Math.max(1,lastHit)&&state.timer("rover_strangle_window")>0
            &&state.timer("charging")==0&&preview.state().timer("rover_strangle_charging")>0
            &&Set.of("aero_rover/basic_03","aero_rover/dodge_counter","aero_rover/heavy").contains(current))return true;
        if(state.characterId().equals("zhezhi")&&input==Input.HEAVY_START
            &&elapsed>Math.max(1,lastHit)&&state.timer("charging")==0&&preview.state().timer("charging")>0
            &&((current.equals("zhezhi/basic_03")&&state.timer("zhezhi_after_basic3")>0)
                ||Set.of("zhezhi/skill","zhezhi/skill_hold","zhezhi/skill_air").contains(current)&&state.timer("zhezhi_after_manifestation")>0
                ||Set.of("zhezhi/skill_genius","zhezhi/skill_genius_air","zhezhi/skill_maestro").contains(current)&&state.timer("zhezhi_after_herald")>0
                ||current.equals("zhezhi/dodge_counter")&&state.timer("zhezhi_after_dodge")>0))return true;
        if(state.characterId().equals("lucilla")&&current.equals("lucilla/dodge_counter")
            &&input==Input.BASIC_HOLD&&elapsed>Math.max(1,lastHit)
            &&state.timer("lucilla_focus_channel")==0&&preview.state().timer("lucilla_focus_channel")==1)return true;
        String action=preview.effects().stream().filter(effect->effect.kind()==Kind.ACTION).map(Effect::id).findFirst().orElse("");
        if(!current.startsWith(state.characterId()+"/")||!action.startsWith(state.characterId()+"/"))return false;
        String body=current.substring(current.indexOf('/')+1),next=action.substring(action.indexOf('/')+1);
        // Source1207/node1 and1211/node2 offer these taps in the preceding
        // action's recovery. Keep every scheduled contact before replacing it.
        if(elapsed>Math.max(1,lastHit)){
            if(state.characterId().equals("galbrena")&&body.equals("intro")&&input==Input.BASIC_HOLD
                &&Set.of("heavy_02","demon_heavy_02").contains(next))return true;
            // Frozen source1103/1406/1409/1505/1507/1508/1607 explicitly
            // names these next segments. Preview retains stance, cost and landing checks.
            if(input==Input.BASIC){
                // Frozen node6 explicitly resumes these segments after intro.
                // The engine preview chooses stance and segment; contacts still finish first.
                boolean introFollow=switch(state.characterId()){
                    case "denia" -> body.equals("intro")&&Set.of("basic_04","basic_04_c3_full_core").contains(next);
                    case "ciaccona" -> body.equals("intro")&&next.equals("basic_03");
                    case "cantarella" -> body.equals("intro")&&next.equals("basic_03");
                    case "cartethyia" -> body.equals("intro")&&next.equals("basic_02")
                        ||body.equals("fleurdelys_intro")&&next.equals("fleurdelys_basic_02");
                    case "qiuyuan" -> body.equals("intro")&&next.equals("answer_basic_03");
                    case "sigrika" -> body.equals("intro")&&next.equals("basic_02");
                    case "galbrena" -> body.equals("intro")&&Set.of("basic_02","demon_basic_02").contains(next);
                    case "chisa" -> body.equals("intro")&&Set.of("basic_02","saw_basic_02").contains(next);
                    case "phrolova" -> Set.of("intro","intro_eternal").contains(body)&&next.equals("basic_03");
                    case "hsin" -> body.equals("intro")&&next.equals("basic_04")
                        ||Set.of("intro_unison","intro_full_unison").contains(body)&&next.equals("basic_03");
                    case "hiyuki" -> body.equals("intro")&&Set.of("basic_03","future_basic_02").contains(next);
                    case "lupa" -> body.equals("intro")&&next.equals("air_basic_03");
                    default -> false;
                };
                if(introFollow)return true;
                if(state.characterId().equals("hiyuki")){
                    if(body.equals("future_heavy")&&Set.of("future_basic_02","future_basic_04").contains(next))return true;
                    if(body.equals("future_dodge_counter")&&next.equals("future_basic_03"))return true;
                }
                if(state.characterId().equals("qingxiao")){
                    if(Set.of("air_basic_03","air").contains(body)&&state.timer("qingxiao_ground_a3")>0&&next.equals("basic_03"))return true;
                    if(body.equals("dodge_counter")&&next.equals("basic_04"))return true;
                }
                if(state.characterId().equals("chisa")&&body.equals("liberation")&&next.equals("basic_02"))return true;
                if(state.characterId().equals("hsin")&&body.equals("skill")&&next.equals("basic_04"))return true;
                if(state.characterId().equals("chisa")){
                    if(body.equals("skill")&&Set.of("pierce","saw_basic_02").contains(next))return true;
                    if(body.equals("skill_saw_cycle")&&next.equals("saw_basic_02"))return true;
                }
                if(state.characterId().equals("galbrena")&&body.equals("liberation")
                    &&Set.of("basic_02","demon_basic_02").contains(next))return true;
                if(state.characterId().equals("aemeath")&&body.equals("liberation")&&next.equals("mech_basic_02"))return true;
                if(state.characterId().equals("aemeath")){
                    if(Set.of("heavy","air","skill_dawn","wing_ensemble_stage").contains(body)&&next.equals("basic_02"))return true;
                    if(Set.of("mech_heavy","mech_air","skill","wing_ensemble_descend").contains(body)&&next.equals("mech_basic_02"))return true;
                    if(body.equals("heavy_charge_2")&&next.equals("basic_03"))return true;
                    if(body.equals("mech_heavy_charge_2")&&next.equals("mech_basic_03"))return true;
                    if(body.equals("dodge_counter")&&next.equals("basic_04"))return true;
                    if(body.equals("mech_dodge_counter")&&next.equals("mech_basic_04"))return true;
                }
                if(state.characterId().equals("hsin")){
                    if(Set.of("heavy","air","air_wander").contains(body)&&next.equals("basic_02"))return true;
                    if(body.equals("dodge_counter")&&next.equals("basic_03"))return true;
                    if(Set.of("illuminated_heavy","illuminated_dodge_counter").contains(body)&&next.equals("illuminated_basic_03"))return true;
                    if(body.equals("thousand_palaces_counter")&&next.equals("thousand_palaces_basic_03"))return true;
                }
                if(state.characterId().equals("yangyang_xuanling")){
                    if(body.equals("air")&&next.equals("basic_02"))return true;
                    if(body.equals("feather_air")&&next.equals("feather_basic_02"))return true;
                    if(body.equals("dodge_counter")&&next.equals("basic_03"))return true;
                    if(body.equals("feather_dodge_counter")&&next.equals("feather_basic_03"))return true;
                }
                if(state.characterId().equals("phrolova")){
                    if(body.equals("heavy")&&next.equals("basic_02"))return true;
                    if(body.equals("dodge_counter")&&next.equals("basic_03"))return true;
                    if(Set.of("basic_03","skill").contains(body)&&next.equals("movement_death"))return true;
                }
                if(state.characterId().equals("suisui")&&body.equals("dodge_counter")&&next.equals("basic_04"))return true;
                if(state.characterId().equals("chisa")&&body.equals("air")&&next.equals("basic_02"))return true;
                if(state.characterId().equals("shorekeeper")&&Set.of("skill","air","air_evolution").contains(body)&&next.equals("basic_02"))return true;
                if(state.characterId().equals("ciaccona")){
                    if(body.equals("skill")&&next.equals("basic_02"))return true;
                    if(body.equals("air_02")&&next.equals("basic_04"))return true;
                }
                if(state.characterId().equals("cantarella")&&body.equals("mirage_counter")&&next.equals("mirage_basic_02"))return true;
                if(state.characterId().equals("iuno")&&Set.of("dodge_counter","bow_counter","bow_counter_enhanced").contains(body)
                    &&Set.of("basic_03","bow_basic_03","bow_basic_03_enhanced").contains(next))return true;
                if(state.characterId().equals("sigrika")){
                    if(Set.of("heavy","air","air_counter").contains(body)&&next.equals("basic_02"))return true;
                    if(body.equals("dodge_counter")&&next.equals("basic_04"))return true;
                    if(body.equals("skill")&&next.equals("basic_03"))return true;
                    if(body.equals("basic_04")&&next.equals("basic_insight"))return true;
                }
                if(state.characterId().equals("zani")){
                    if(Set.of("heavy","air").contains(body)&&next.equals("basic_03"))return true;
                    if(Set.of("basic_03","dodge_counter").contains(body)&&next.equals("breakthrough"))return true;
                    if(body.equals("breakthrough")&&next.equals("basic_04"))return true;
                }
                if(state.characterId().equals("cartethyia")){
                    if(body.equals("fleurdelys_counter")&&next.equals("fleurdelys_basic_04"))return true;
                    if(body.equals("fleurdelys_skill_followup")&&next.equals("fleurdelys_basic_03"))return true;
                }
                if(state.characterId().equals("aero_rover")&&body.equals("heavy")&&next.equals("basic_03"))return true;
            }
            if(state.characterId().equals("iuno")&&Set.of(Input.SKILL,Input.SKILL_HOLD).contains(input)
                &&Set.of("skill","basic_03","intro").contains(body)&&next.equals("skill_ending"))return true;
            if(state.characterId().equals("aemeath")&&body.equals("liberation")&&input==Input.SKILL&&next.equals("basic_02"))return true;
            if(state.characterId().equals("phoebe")&&body.equals("skill")&&input==Input.SKILL&&next.equals("skill_teleport"))return true;
            if(state.characterId().equals("qingxiao")&&Set.of("skill_punishment","skill_punishment_c5").contains(body)&&input==Input.BASIC&&next.equals("basic_04"))return true;
            if(state.characterId().equals("carlotta")){
                if(Set.of("skill","skill_c3").contains(body)&&Set.of(Input.SKILL,Input.SKILL_HOLD).contains(input)
                    &&Set.of("skill_show_brilliance","skill_show_brilliance_c3").contains(next))return true;
                if(body.equals("air")&&input==Input.BASIC&&next.equals("courtesy"))return true;
            }
            if(state.characterId().equals("changli")&&input==Input.BASIC){
                if(Set.of("skill","basic_04","air_basic_04","intro").contains(body)
                    &&Set.of("true_sight_conquest","true_sight_charge").contains(next))return true;
                if(body.equals("heavy")&&next.equals("air_basic_03"))return true;
                if(body.equals("true_sight_charge")&&next.equals("air"))return true;
                if(body.equals("air")&&next.equals("basic_03"))return true;
            }
            if(state.characterId().equals("qiuyuan")&&body.equals("heavy")&&input==Input.BASIC&&next.equals("answer_basic_04"))return true;
            if(state.characterId().equals("hiyuki")&&Set.of(Input.BASIC,Input.HEAVY_START).contains(input)
                &&Set.of("skill","dodge_counter").contains(body)&&next.equals("basic_03"))return true;
            if(state.characterId().equals("mornye")&&input==Input.BASIC){
                if(body.equals("air")&&next.equals("basic_03"))return true;
                if(body.equals("dodge_counter")&&next.equals("basic_02"))return true;
                if(body.equals("observation_dodge_counter")&&next.equals("observation_basic_03"))return true;
            }
            if(state.characterId().equals("galbrena")&&input==Input.BASIC){
                if(Set.of("basic_04","heavy_01").contains(body)&&next.equals("basic_02"))return true;
                if(Set.of("heavy_02","heavy_03","air").contains(body)&&next.equals("basic_03"))return true;
                if(body.equals("dodge_counter")&&next.equals("basic_04"))return true;
                if(body.equals("skill_ascend")&&next.equals("demon_basic_02"))return true;
                if(body.equals("demon_heavy_01")&&next.equals("demon_basic_02"))return true;
                if(Set.of("demon_heavy_02","demon_heavy_03","demon_air","demon_basic_05").contains(body)&&next.equals("demon_basic_03"))return true;
                if(body.equals("demon_dodge_counter")&&next.equals("demon_basic_04"))return true;
            }
            if(state.characterId().equals("denia")){
                if(Set.of("skill_exile_01","skill_exile_01_c2").contains(body)&&Set.of(Input.BASIC,Input.SKILL).contains(input)
                    &&next.matches("skill_exile_02(?:_core_[2-5])?(?:_c2)?"))return true;
                if(input==Input.BASIC&&Set.of("dodge_counter","disillusion_counter","disillusion_counter_enhanced").contains(body)
                    &&Set.of("basic_04","basic_04_c3_full_core","disillusion_basic_04","disillusion_air_basic_04",
                        "disillusion_basic_04_enhanced","disillusion_air_basic_04_enhanced").contains(next))return true;
            }
            if(state.characterId().equals("lupa")&&input==Input.BASIC){
                if(body.equals("basic_03")&&state.timer("lupa_basic_air_ready")>0&&next.equals("air_basic_01"))return true;
                if(Set.of("dodge_counter","starfall","skill","skill_ferocious").contains(body)&&next.equals("basic_02"))return true;
                if(body.equals("air")&&state.timer("lupa_starfall")>0&&next.equals("starfall"))return true;
                if(Set.of("heavy_wolf_bite","air_flame_strike").contains(body)
                    &&state.timer("lupa_sharp_claw_ready")>0&&next.equals("heavy_sharp_claw"))return true;
            }
        }
        if(state.characterId().equals("aero_rover")&&Set.of("wind_air_01","wind_air_02").contains(body)
            &&input==Input.HEAVY_FULL&&next.equals("air")&&elapsed>=1)return true;
        if(state.characterId().equals("chisa")&&!state.form().equals("saw")&&state.timer("chisa_shattered_ready")>0
            &&body.equals("heavy")&&input==Input.HEAVY_FULL&&next.equals("shattered_plane")&&elapsed>=1)return true;
        // Qingxiao's source explicitly offers Ascend DURING A2/A3/counter,
        // rather than keeping it as a post-action recovery qualification.
        // The one-tick input boundary is MC adaptation; cancelling forfeits
        // every unplayed contact of the replaced attack.
        if(state.characterId().equals("qingxiao")&&state.timer("qingxiao_ascend_ready")>0
            &&Set.of("basic_02","basic_03","dodge_counter").contains(body)
            &&Set.of(Input.SKILL,Input.SKILL_HOLD).contains(input)&&next.equals("skill_ascend")&&elapsed>=1)return true;
        // Community BV1GALuzTEQ2/BV1ZfLGzsEtE distinguish recovery cancels
        // (intro, E1, mode entry, ring A3) from E2: early E2 cancellation
        // really forfeits its remaining hit. Do not grant those missing hits.
        if(state.characterId().equals("phoebe")&&input==Input.LIBERATION&&next.equals("liberation")){
            if(body.equals("skill_teleport")&&elapsed>=1)return true;
            if(Set.of("intro","skill","absolution","confession","ring_basic_03").contains(body)
                &&elapsed>Math.max(1,lastHit))return true;
        }
        // Ephemeral recovery -> paid liberation (community cycle BV1oKuM6CEqh,
        // WuwaBeacon Ephemeral Skill Cancel). Preserve the scheduled contact;
        // this boundary is an MC adaptation, not a native frame measurement.
        if(state.characterId().equals("camellya")&&body.equals("ephemeral")
            &&input==Input.LIBERATION&&next.equals("liberation")
            &&elapsed>Math.max(1,lastHit))return true;
        if(elapsed>Math.max(1,lastHit)){
            // Source1509: these casts explicitly continue at basic2. Preserve
            // every scheduled contact before replacing recovery; airborne
            // plunge selection still belongs to BasicInputRules/the real host.
            if(state.characterId().equals("lynae")&&input==Input.BASIC){
                if(body.equals("skill")&&next.equals("basic_02"))return true;
                if(state.form().equals("cruise")&&next.equals("cruise_basic_02")
                    &&Set.of("skill_additive","toward_colorful_tomorrow","visual_impact","rainbow_splash",
                        "phantom_leap_1","cruise_dodge_counter").contains(body))return true;
            }
            // Explicit source1105/1109 continuations share the host/HUD rule.
            // Scheduled contacts finish first; only recovery is replaced.
            if(state.characterId().equals("zhezhi")){
                if(Set.of("skill","skill_hold","skill_air").contains(body)&&next.equals("heavy_conjuration")&&input==Input.BASIC)return true;
                if(Set.of(Input.SKILL,Input.SKILL_HOLD).contains(input)
                    &&Set.of("skill_genius","skill_genius_air","skill_maestro").contains(next)
                    &&(body.contains("basic_")||Set.of("skill","skill_hold","skill_air","heavy_conjuration","skill_genius","skill_genius_air","skill_maestro").contains(body)))return true;
            }
            if(state.characterId().equals("lucilla")&&Set.of(Input.BASIC,Input.BASIC_HOLD).contains(input)){
                if(Set.of("skill_fill","skill_chase","air").contains(body)&&next.equals("basic_02"))return true;
                if(body.equals("dodge_counter")&&next.equals("basic_03"))return true;
                if(body.equals("memory_air")&&next.equals("memory_basic_02"))return true;
                if(Set.of("memory_counter","intro_memory").contains(body)&&next.equals("memory_basic_03"))return true;
            }
        }
        // The source explicitly permits held R to end the restricted phase early.
        // Cancel the remaining Sun contacts rather than queuing a second action.
        if(state.characterId().equals("augusta")&&body.equals("realm_sun")&&next.equals("realm_finale")
            &&input==Input.LIBERATION_HOLD)return true;
        if(state.characterId().equals("zani")&&elapsed>Math.max(1,lastHit)
            &&Set.of("skill","precise_counter").contains(body)&&next.equals("basic_03"))return true;
        if(state.characterId().equals("suoming")&&elapsed>Math.max(1,lastHit)){
            if(body.equals("deep_basic_02")&&input==Input.BASIC_HOLD&&next.equals("thunder_spin_01"))return true;
            if(body.equals("thunder_spin_01")&&Set.of(Input.BASIC,Input.BASIC_HOLD).contains(input)&&next.equals("thunder_spin_02"))return true;
            if(body.equals("deep_basic_03")&&input==Input.BASIC_HOLD&&next.equals("deep_basic_04"))return true;
            if(body.equals("deep_counter")&&(next.equals("deep_basic_03")||input==Input.BASIC_HOLD&&next.equals("thunder_spin_01")))return true;
            if(body.equals("dodge_counter")&&next.equals("basic_03"))return true;
            if(body.equals("deep_basic_04")&&state.timer("suoming_forbidden")>0&&next.equals("deep_basic_02"))return true;
            if(body.equals("skill_release")&&next.equals("heart"))return true;
            if(body.equals("skill")&&Set.of("basic_02","deep_basic_03").contains(next))return true;
            if(body.equals("skill_counter")&&Set.of("basic_03","deep_basic_04").contains(next))return true;
            if(body.startsWith("intro_deep")&&next.equals("thunder_spin_02"))return true;
        }
        // Source1306 permits basic/skill continuations. Preserve scheduled hits;
        // the cancellation boundary is an explicit MC timing adaptation.
        if(state.characterId().equals("augusta")&&elapsed>Math.max(1,lastHit)){
            if(body.equals("realm_sun")&&Set.of("realm_sun","realm_finale").contains(next))return true;
            if(body.equals("heavy")&&next.equals("basic_02"))return true;
            if(Set.of("skill_rush","counter_rush").contains(body)&&next.equals("skill_leap"))return true;
            if(body.equals("skill_leap")&&next.equals("skill_descent"))return true;
            if(Set.of("heavy_retreat","counter_retreat").contains(body)&&next.equals("heavy_spin"))return true;
            if(Set.of("skill","heavy","counter_heavy").contains(body)&&next.equals("heavy_uppercut"))return true;
        }
        // Source1509/node7 permits either short basic or skill at three airborne pips.
        // A leap can be replaced after2 MC ticks; its remaining hits are cancelled,
        // rather than keeping the player busy until the native jump already lands.
        if(state.characterId().equals("lynae")&&state.form().equals("cruise")&&state.resource("true_color")>=3
            &&Set.of(Input.BASIC,Input.AIR,Input.SKILL).contains(input)&&elapsed>=2
            &&Set.of("visual_impact","rainbow_splash").contains(next)
            &&(body.startsWith("phantom_leap_")||body.startsWith("cruise_basic_")||body.equals("cruise_air")||body.equals("cruise_air_heavy")))return true;
        // Source1210 intro offers both A3 and E -> the other form's A3.
        // Keep all intro contacts; either continuation may replace its recovery.
        if(state.characterId().equals("aemeath")&&(input==Input.BASIC||input==Input.SKILL)&&body.startsWith("intro")
            &&Set.of("basic_03","mech_basic_03").contains(next)&&elapsed>Math.max(1,lastHit))return true;
        // Observation's held combo must still allow a spell in recovery.
        // Use the existing MC final-contact boundary, never erase scheduled shots.
        if(state.characterId().equals("mornye")&&state.form().equals("observation")
                &&body.startsWith("observation_basic_")&&elapsed>Math.max(1,lastHit)
                &&(input==Input.SKILL&&next.equals("skill")||input==Input.LIBERATION&&next.equals("liberation")))return true;
        if((input!=Input.SKILL&&input!=Input.SKILL_HOLD)||elapsed<=Math.max(1,lastHit))return false;
        boolean basic=body.contains("basic_")||body.contains("counter")||body.contains("heavy");
        return switch(state.characterId()) {
            case "aemeath" -> basic&&Set.of("skill","skill_dawn","wing_ensemble_stage","wing_ensemble_descend","liberation_finale").contains(next);
            case "jinhsi" -> body.equals("basic_04")&&next.equals("skill_overflowing")
                ||body.equals("incarnation_basic_04")&&next.equals("epiphany_rays");
            case "camellya" -> (basic||body.equals("bloom_spinning"))&&Set.of("ephemeral","immortal_blossom","bloom_dark_heart").contains(next);
            case "yinlin" -> body.equals("skill")&&next.equals("skill_blast");
            case "aero_rover" -> body.equals("unbound_wind_01")&&next.equals("unbound_wind_02");
            case "cartethyia" -> body.equals("fleurdelys_skill")&&next.equals("fleurdelys_skill_followup");
            case "chisa" -> basic&&!body.startsWith("saw_")&&Set.of("skill_saw_cycle","skill_saw_hold").contains(next);
            case "hsin" -> body.startsWith("illuminated_basic_")&&next.equals("palaces_formation");
            case "phrolova" -> basic&&next.equals("undying_reverie");
            case "sigrika" -> body.equals("basic_04")&&Set.of("skill_insight","skill_sun").contains(next);
            default -> false;
        };
    }
    public static boolean enhanced(Input input,Result preview) {
        if(!preview.accepted())return false;
        if(input==Input.LIBERATION)return preview.effects().stream().noneMatch(e->e.kind()==Kind.PAY_ENERGY)
            ||preview.effects().stream().anyMatch(e->e.kind()==Kind.ACTION&&e.id().endsWith("liberation_finale"));
        if(input!=Input.SKILL&&input!=Input.SKILL_HOLD)return false;
        return preview.effects().stream().filter(e->e.kind()==Kind.ACTION).map(e->e.id().substring(e.id().indexOf('/')+1)).anyMatch(Set.of(
            "wing_ensemble_stage","wing_ensemble_descend","liberation_finale","visual_impact","rainbow_splash","skill_overflowing","skill_crescent",
            "epiphany_rays","epiphany_final","ephemeral","immortal_blossom","skill_blast","unbound_wind_01","unbound_wind_02",
            "fleurdelys_skill_followup","palaces_formation","skill_saw_cycle","skill_saw_hold","undying_reverie","flow_azure","flow_feather",
            "skill_awaken","rain_skill","skill_drain","skill_dream","skill_teleport","confession","skill_jade","skill_falling","skill_insight","skill_sun","skill_semantics","skill_ascend","demon_skill","skill_canopy")::contains);
    }
}
