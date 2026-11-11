package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import static cn.zgnhit.mcwuwa.content.mechanics.MechanicsEngine.*;

/** Solo lifecycle qualifications. Counters are not remaining-tick cooldowns.
 * Source rules are indexed in character-completion-audit-20261014.md; MC scheduling is explicit. */
public final class CharacterLifecycleRules {
    public static final Set<String> COUNTERS=Set.of("role_idle_ticks","idle_reward_latched",
        "electronic_ghost_pending","hiyuki_c2_reset_charges","hiyuki_c1_basic1","hiyuki_c1_basic2","hiyuki_last_arrow_pending",
        "lynae_premix","lynae_premix_clock_mode","eternal_intro","phrolova_return_curtain","aemeath_brilliance","yangyang_listening_cursor");
    private CharacterLifecycleRules() { }
    /** Ordinary defeat ends temporary forms/qualifications, but revival cannot award resources
     * or refund a cooldown. Full-team respawn/login have their separate initial-resource policy. */
    public static MechanicsState downed(MechanicsState state) {
        var base=MechanicsState.initial(state.characterId(),state.mode(),state.chain());
        var timers=new HashMap<String,Integer>();
        for(String key:MechanicsEngine.persistentCooldownKeys(state.characterId()))if(state.timer(key)>0)timers.put(key,state.timer(key));
        for(String key:MechanicsEngine.persistentCounterKeys(state.characterId()))if(state.timer(key)>0)timers.put(key,state.timer(key));
        // These first skills defer their cooldown until followup/exit. Defeat
        // closes the pending window too; filtering it away first refunds E1.
        switch(state.characterId()){
            case "yinlin" -> {if(state.timer("magnetic")>0)timers.put("skill",240);}
            case "carlotta" -> {if(state.timer("carlotta_show_brilliance")>0)timers.put("skill",280);}
            case "lupa" -> {if(state.timer("lupa_ferocious_ready")>0)timers.put("skill",240);}
            case "cartethyia" -> {if(state.timer("fleur_followup")>0)timers.put("fleur_skill",280);}
            default -> {}
        }
        if(state.characterId().equals("sigrika")){
            state.timers().forEach((key,value)->{
                if(key.equals("sig_blessings")||key.startsWith("sig_bless_seen_"))timers.put(key,value);
            });
        }
        var resources=new HashMap<>(state.resources());
        if(state.characterId().equals("hsin"))resources.put("edicts",0d);
        // Wolf Soul is a timed qualification. Removing its lifetime while
        // retaining its count makes the next revived tick refund Wolf Flame as
        // if the10-second lifetime had naturally completed.
        if(state.characterId().equals("lupa"))resources.put("wolf_soul",0d);
        return new MechanicsState(state.characterId(),base.form(),state.mode(),state.chain(),1,resources,timers);
    }
    static void cast(Transition t) {
        for(var effect:List.copyOf(t.effects))if(effect.kind()==Kind.ACTION) {
            String action=effect.id();
            if(t.original.characterId().equals("lupa")){
                t.clear("lupa_basic2_followup");
                if(java.util.Set.of("lupa/skill","lupa/skill_ferocious","lupa/starfall","lupa/dodge_counter").contains(action)
                    &&!(action.equals("lupa/skill")&&t.has("lupa_basic_air_ready")))t.timer("lupa_basic2_followup",60);
            }
            if(t.original.characterId().equals("lynae")){
                t.clear("lynae_basic2_followup");
                if(t.form.equals("cruise")&&Set.of("lynae/skill_additive","lynae/toward_colorful_tomorrow",
                    "lynae/visual_impact","lynae/rainbow_splash").contains(action))
                    t.timer("lynae_basic2_followup",60); // Existing authored combo window, not an original frame claim.
            }
            if(t.original.characterId().equals("qingxiao")&&!java.util.Set.of("qingxiao/basic_02","qingxiao/basic_03","qingxiao/dodge_counter").contains(action))
                t.clear("qingxiao_ascend_ready");
            if(t.original.characterId().equals("hsin")&&t.original.mode().equals("electro")&&t.get("thunder_realm")>=50
                &&HsinTargetRules.stallsInIlluminatedForm(action))
                t.effect(Kind.CONTROL_AREA,"hsin_illuminated_stasis",1,1,Map.of("radius",6d,"actionBound",1d));
            if(t.original.characterId().equals("aemeath"))t.clear(AemeathTravelRules.TIMER);
            if(t.original.characterId().equals("hiyuki")) {
                if(action.equals("hiyuki/liberation")&&t.original.chain()>=1) {
                    t.timer("hiyuki_c1_basic1",1);t.timer("hiyuki_c1_basic2",1);
                }
                if(action.equals("hiyuki/skill_jade")&&t.has("hiyuki_c2_reset_charges")) {
                    t.clear("future_skill");t.timer("hiyuki_c2_reset_charges",t.time("hiyuki_c2_reset_charges")-1);
                }
                if(action.equals("hiyuki/iai")||action.equals("hiyuki/liberation")||action.equals("hiyuki/liberation_return"))t.clear("air_stage");
            }
            if(t.original.characterId().equals("phrolova")) {
                if(action.equals("phrolova/composition_finale")&&t.original.chain()>=3)
                    t.timer("phrolova_color_conversion",12);
                if(action.equals("phrolova/intro_eternal"))t.clear("eternal_intro");
            }
            if(t.original.characterId().equals("aero_rover")&&action.startsWith("aero_rover/unbound_wind_")&&t.original.chain()>=2) {
                t.timer("rover_unbound_healing",600);t.timer("rover_healing_pulse",60);
            }
            if(t.original.characterId().equals("lynae")&&action.equals("lynae/skill_additive")&&t.original.chain()>=3) {
                t.effect(Kind.BUFF_SELF,"lynae_premix_bonus",t.time("lynae_premix")*.55,1,Map.of("lynaeAdditiveOnly",1d));
                t.timer("lynae_premix_stop",40); // MC attack/recovery lock; stacks stop while casting.
                t.clear("lynae_premix_pulse");t.clear("lynae_premix_clock_mode");
            }
        }
        if(t.original.characterId().equals("phrolova")&&t.form.equals("conducting")&&!t.original.form().equals("conducting")) {
            t.timer("eternal_intro",1);
        }
        if(t.original.characterId().equals("chisa")&&t.effects.stream().anyMatch(e->e.kind()==Kind.ACTION
            &&List.of("chisa/liberation","chisa/intro").contains(e.id())))t.clear("chisa_engine_stall");
    }
    static void hit(Transition t,String action) {
        if(!t.context.active()||!t.original.characterId().equals("hiyuki")||t.original.chain()<1)return;
        int part=action.equals("future_basic_01")?1:action.equals("future_basic_02")?2:0;
        if(part>0&&t.has("hiyuki_c1_basic"+part)) {
            t.clear("hiyuki_c1_basic"+part);t.effect(Kind.MARK_TARGET,"glacio_chafe",1,300);
        }
        if(action.equals("future_basic_03"))t.effect(Kind.PULL_AREA,"hiyuki_chain1",.2,1,Map.of("radius",6d));
    }
    static void tick(Transition t) {
        // Aerial progress belongs to this deployed role's current flight, never a reserve
        // character observing somebody else's landing. Ordinary airborne skills preserve it.
        if(t.context.active()&&!t.context.airborne()&&Set.of("hiyuki","cartethyia").contains(t.original.characterId()))t.clear("air_stage");
        AemeathTravelRules.tick(t);
        OutroRules.tick(t);
        LynaeCamouflageRules.tick(t);
        if(t.context.inCombat()){t.clear("role_idle_ticks");t.clear("idle_reward_latched");}
        else t.timer("role_idle_ticks",Math.min(1_000_000,t.time("role_idle_ticks")+1));
        int idle=t.time("role_idle_ticks");String who=t.original.characterId();int chain=t.original.chain();
        // C3 converts the score in order during Composition, not all at input.
        // Public gameplay BV1yo8nzfE6D,183.0--183.5s shows the advancing change.
        // Two ticks per slot is the MC cadence within the authored24-tick cast;
        // no enemy contact is required, and existing Color notes keep their slot.
        int conversion=t.original.timer("phrolova_color_conversion");
        if(who.equals("phrolova")&&chain>=3&&conversion>0&&conversion<=12&&conversion%2==1){
            int slot=6-(conversion+1)/2;
            if(slot<(int)t.get("notes"))t.resources.put("note_"+slot,2d);
        }
        if(who.equals("yangyang_xuanling")&&!t.has("drywood"))t.clear("drywood_uses");
        if(who.equals("aemeath")&&!t.has("aemeath_rupture_window"))t.clear("aemeath_rupture_stacks");
        if(who.equals("hiyuki")&&t.form.equals("future")&&t.has("charging")) {
            if(t.context.stamina()>=.5)t.effect(Kind.PAY_STAMINA,"hiyuki_heavy_hold",.5,0);
            else CharacterBranches.hiyukiThrust(t);
        }
        if(who.equals("aemeath")&&t.has("electronic_ghost_pending")&&!t.has("electronic_ghost")) {
            t.clear("electronic_ghost_pending");t.effect(Kind.SET_HEALTH_FRACTION,"electronic_ghost_return",1,0);
            t.effect(Kind.GAIN_ENERGY,"electronic_ghost_return",30,0);
        }
        if(who.equals("hiyuki")&&idle>=80&&!t.has("idle_reward_latched")) {
            t.timer("idle_reward_latched",1);
            if(chain>=2){t.set("forge",3);if(t.form.equals("future"))t.set("iai",3);t.clear("future_skill");
                t.timer("hiyuki_c2_reset_charges",2);t.timer("hiyuki_c2_skill_charges",2);}
            else if(t.get("forge")<1)t.set("forge",1);
        }
        if(who.equals("phrolova")&&chain>=1&&idle>=80&&!t.form.equals("conducting")&&!t.has("final_note")) {
            while(t.get("notes")<2){int i=(int)t.get("notes");t.resources.put("note_"+i,2d);t.add("notes",1);}
        }
        if(who.equals("mornye")&&chain>=6&&idle>80&&!t.has("mornye_idle_energy")) {
            t.effect(Kind.GAIN_ENERGY,"mornye_chain6",0,0,Map.of("energyFraction",.10));t.timer("mornye_idle_energy",4);
        }
        if(who.equals("yangyang_xuanling")&&chain>=2&&idle>=80&&!t.has("idle_reward_latched")) {
            t.timer("idle_reward_latched",1);t.timer("thread_pearl",1);t.set("feathers",2);
            t.clear("heavy_critical");t.clear("feather_critical");
        }
        if(who.equals("aemeath")&&t.has("charging"))t.timer("aemeath_brilliance_wait",81);
        // finale is a25-second cooldown, not the finisher's performing state.
        if(who.equals("aemeath")&&chain>=1&&idle>80&&!t.has("charging")&&!t.has("aemeath_brilliance_wait"))t.timer("aemeath_brilliance",1);
        if(who.equals("lynae")&&chain>=3&&!t.has("lynae_premix_stop")) {
            boolean charged=t.get("lumiflow")>=120; // C3 names120, even when C6 raises the pool cap to360.
            int interval=t.context.inCombat()?(charged?20:10):(charged?10:20);
            int mode=(t.context.inCombat()?2:0)+(charged?1:0)+1;
            // Count a complete eligible interval, including this tick. A changed
            // gain/decay condition starts its own interval, not the previous rate.
            if(t.time("lynae_premix_clock_mode")!=mode){
                t.timer("lynae_premix_clock_mode",mode);t.timer("lynae_premix_pulse",interval-1);
            }else if(!t.has("lynae_premix_pulse")){
                t.timer("lynae_premix",Math.clamp(t.time("lynae_premix")+(charged?1:-1),0,25));
                t.timer("lynae_premix_pulse",interval);
            }
        }
        // Include the interval ending exactly at30s. No new interval starts
        // after expiry; an expired save must not award a catch-up heal.
        if(who.equals("aero_rover")&&t.original.timer("rover_unbound_healing")>0
            &&t.original.timer("rover_healing_pulse")==1) {
            t.effect(Kind.HEAL_ACTIVE,"rover_chain2",t.context.attack()*.2,0);
            if(t.has("rover_unbound_healing"))t.timer("rover_healing_pulse",60);
        }
        if(who.equals("aero_rover")&&t.has("rover_unbound_healing")) {
            if(t.context.active()&&t.context.maxHealthNative()>0&&t.context.currentHealthNative()<t.context.maxHealthNative()*.35&&!t.has("rover_emergency")) {
                t.timer("rover_emergency",200);t.effect(Kind.HEAL_ACTIVE,"rover_chain2_emergency",
                    (t.context.maxHealthNative()-t.context.currentHealthNative())*.1,0,Map.of("ignoreHealingBonus",1d));
            }
        }
    }
}
