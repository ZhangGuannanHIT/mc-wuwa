package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.ArrayList;
import java.util.List;
import static cn.zgnhit.mcwuwa.content.mechanics.MechanicsEngine.*;

/** Production playability rules authorized2026-10-14. Audited NativeResourceYields stays independent.
 * Community totals use once-per-confirmed-action MC dispatch, never once per target.
 * Approximate amounts and timing are documented; they are not promoted to original-data evidence. */
public final class GameplayResourceRules {
    public enum Timing { CAST, HIT }
    public record Grant(String character,String action,String resource,double amount,Timing timing,String form,String evidence) { }
    public static final List<Grant> ENTRIES=definitions();
    private GameplayResourceRules() { }
    private static List<Grant> definitions() {
        var out=new ArrayList<Grant>();
        // Suoming obsession uses the independently checked per-hit native slot.
        // Augusta War/Power use individual native contacts; no duplicate whole-action grants.
        // Source specifies these acquisition events but not their numeric yield.
        // Once-per-action MC values until an independent native mapping is available.
        series(out,"zani","redundant",Timing.HIT,"normal","mc_adaptation","basic_",10,10,15,20);
        add(out,"zani","redundant",Timing.HIT,"normal","mc_adaptation",15,"heavy","air","breakthrough");
        add(out,"zani","redundant",Timing.HIT,"normal","mc_adaptation",20,"dodge_counter","precise_counter");
        add(out,"zani","redundant",Timing.CAST,"normal","mc_adaptation",20,"skill","intro");
        add(out,"lynae","overflow",Timing.CAST,"sampling","mc_adaptation",20,"basic_01","basic_02","basic_03","air","dodge_counter","skill");
        series(out,"mornye","rest_energy",Timing.CAST,"baseline","mc_adaptation","basic_",20,43,37,20);
        add(out,"mornye","rest_energy",Timing.CAST,"baseline","mc_adaptation",20,"heavy","dodge_counter");
        add(out,"mornye","rest_energy",Timing.HIT,"baseline","community_evidence",100,"skill_optimal");
        series(out,"mornye","relative_motion",Timing.HIT,"observation","mc_adaptation","observation_basic_",15,15,10);
        add(out,"mornye","relative_motion",Timing.HIT,"observation","mc_adaptation",15,"observation_dodge_counter");
        add(out,"mornye","relative_motion",Timing.HIT,"observation","community_evidence",60,"skill");
        series(out,"yinlin","judgment",Timing.HIT,"","community_evidence","basic_",2,4,7,8);
        add(out,"yinlin","judgment",Timing.HIT,"","community_evidence",20,"heavy");
        add(out,"yinlin","judgment",Timing.HIT,"","community_evidence",5,"air","magnetic_blast");
        add(out,"yinlin","judgment",Timing.HIT,"","mc_adaptation",8,"dodge_counter");
        add(out,"yinlin","judgment",Timing.CAST,"","community_evidence",30,"skill","intro");
        add(out,"yinlin","judgment",Timing.CAST,"","community_evidence",10,"skill_blast");
        series(out,"cartethyia","resolve",Timing.HIT,"fleurdelys","community_evidence","fleurdelys_basic_",4,14,14,10,20);
        series(out,"cartethyia","resolve",Timing.HIT,"fleurdelys","community_evidence","fleurdelys_air_",6,15,10);
        add(out,"cartethyia","resolve",Timing.HIT,"fleurdelys","community_evidence",14,"fleurdelys_counter");
        add(out,"cartethyia","resolve",Timing.HIT,"fleurdelys","community_evidence",8,"fleurdelys_uppercut","fleurdelys_heavy");
        add(out,"cartethyia","resolve",Timing.HIT,"fleurdelys","community_evidence",24,"fleurdelys_heavy_enhanced");
        add(out,"cartethyia","resolve",Timing.HIT,"fleurdelys","community_evidence",13,"fleurdelys_skill");
        add(out,"cartethyia","resolve",Timing.HIT,"fleurdelys","community_evidence",27,"fleurdelys_skill_followup");
        add(out,"cartethyia","resolve",Timing.HIT,"fleurdelys","community_evidence",10,"fleurdelys_intro");
        series(out,"hsin","worldly_heart",Timing.HIT,"worldly","mc_adaptation","basic_",10,10,15,25);
        add(out,"hsin","worldly_heart",Timing.HIT,"worldly","mc_adaptation",20,"heavy","air","dodge_counter");
        add(out,"hsin","worldly_heart",Timing.HIT,"worldly","mc_adaptation",40,"skill");
        series(out,"hsin","illuminated_heart",Timing.HIT,"illuminated","mc_adaptation","illuminated_basic_",30,40,50);
        add(out,"hsin","illuminated_heart",Timing.HIT,"illuminated","mc_adaptation",40,"illuminated_heavy","illuminated_air","illuminated_dodge_counter");
        add(out,"hsin","illuminated_heart",Timing.HIT,"illuminated","mc_adaptation",30,"illuminated_uppercut");
        add(out,"hsin","illuminated_heart",Timing.HIT,"illuminated","mc_adaptation",120,"illuminated_skill");
        add(out,"hsin","illuminated_heart",Timing.HIT,"illuminated","mc_adaptation",60,"illuminated_intro");
        series(out,"hsin","illuminated_heart",Timing.HIT,"illuminated","mc_adaptation","thousand_palaces_basic_",-50,-60,-80,-110);
        add(out,"hsin","illuminated_heart",Timing.HIT,"illuminated","mc_adaptation",-80,"thousand_palaces_counter");
        add(out,"hiyuki","cold",Timing.CAST,"future","community_evidence",75,"skill_jade","skill_falling");
        return List.copyOf(out);
    }
    private static void add(List<Grant> out,String who,String resource,Timing timing,String form,String evidence,double amount,String... actions) {
        for(String action:actions)out.add(new Grant(who,who+"/"+action,resource,amount,timing,form,evidence));
    }
    private static void series(List<Grant> out,String who,String resource,Timing timing,String form,String evidence,String prefix,double... amounts) {
        for(int i=0;i<amounts.length;i++)add(out,who,resource,timing,form,evidence,amounts[i],prefix+String.format(java.util.Locale.ROOT,"%02d",i+1));
    }
    private static void apply(Transition t,String action,Timing timing) {
        if(!t.context.active())return;
        for(var rule:ENTRIES)if(rule.character().equals(t.original.characterId())&&rule.action().equals(action)&&rule.timing()==timing
            &&(rule.form().isEmpty()||rule.form().equals(t.original.form()))) {
            if(rule.character().equals("mornye")&&rule.resource().equals("relative_motion")&&t.has("mornye_inversion_lock"))continue;
            if(rule.character().equals("hsin")&&rule.amount()>0&&rule.resource().equals("illuminated_heart")&&t.has("palaces"))continue;
            t.add(rule.resource(),rule.amount());
        }
    }
    static void cast(Transition t) {
        ConcertoRules.cast(t);
        for(var effect:List.copyOf(t.effects))if(effect.kind()==Kind.ACTION) {
            apply(t,effect.id(),Timing.CAST);
            if(t.original.characterId().equals("hiyuki")&&List.of("hiyuki/skill_jade","hiyuki/skill_falling").contains(effect.id())) {
                if(t.original.chain()>=2&&t.has("hiyuki_c2_skill_charges")){t.add("cold",50);t.timer("hiyuki_c2_skill_charges",t.time("hiyuki_c2_skill_charges")-1);}
                if(t.original.chain()>=4)t.effect(Kind.HEAL_ACTIVE,"hiyuki_chain4",t.context.maxHealthNative()*.18,0);
            }
        }
        if(t.original.characterId().equals("chisa")) {
            if(t.effects.stream().anyMatch(e->e.kind()==Kind.ACTION&&List.of("chisa/skill_saw_cycle","chisa/skill_saw_hold").contains(e.id())))t.timer("chisa_scar_window",60);
            if(t.form.equals("saw")&&!t.original.form().equals("saw")){t.set("saw_heat_points",100);t.timer("saw_heat",100);}
            if(t.form.equals("saw")&&t.effects.stream().anyMatch(e->e.kind()==Kind.ACTION&&List.of("chisa/liberation","chisa/intro","chisa/saw_resonance_03").contains(e.id()))) {t.set("saw_heat_points",100);t.timer("saw_heat",100);}
            if(t.original.form().equals("saw")&&!t.form.equals("saw")){
                t.set("saw_heat_points",0);t.clear("chisa_held_entry");t.clear("chisa_cycle_followup");t.clear("chisa_scar_window");
            }
        }
    }
    static void hit(Transition t,String action) {
        apply(t,action,Timing.HIT);
        if(!t.context.active())return;
        ConcertoRules.hit(t,action);
        if(t.original.characterId().equals("suoming")&&t.has("suoming_forbidden")
            &&(action.startsWith("suoming/deep_basic_")||action.startsWith("suoming/thunder_spin_")))
            t.effect(Kind.GAIN_CONCERTO,"suoming_forbidden_hit",5,0);
        if(t.original.characterId().equals("yinlin")&&action.equals("yinlin/magnetic_blast")&&t.original.chain()>=2) {
            t.add("judgment",5);t.effect(Kind.GAIN_ENERGY,"yinlin_chain2",5,0);
        }
        if(t.original.characterId().equals("chisa")) {
            if(t.form.equals("saw")){t.set("saw_heat_points",100);t.timer("saw_heat",100);}
            else t.clear("chisa_engine_stall");
        }
    }
    static void tick(Transition t) {
        if(!t.original.characterId().equals("chisa"))return;
        if(!t.has("lifeline_recovery_lock"))t.add("lifeline",.5); // Community estimate10/s;50 consumed ->2s pause.
        if(t.form.equals("saw")&&!t.has("saw_heat")) {
            t.add("saw_heat_points",-1); // Community estimate20%/s after MC5s no-damage grace.
            if(t.get("saw_heat_points")==0) {
                t.form="normal";t.combo=1;t.set("lifeline",100);t.timer("chisa_engine_stall",1);
                t.clear("chisa_saw_stage");t.clear("saw_finale_ready");
                t.clear("chisa_held_entry");t.clear("chisa_cycle_followup");t.clear("chisa_scar_window");
                t.effect(Kind.FORM,"normal",0,0);
            }
        }
        if(t.has("chisa_engine_stall"))t.add("saw_echo",-2); // Community estimate40/s; no damage/skill is fabricated.
    }
    public static MechanicsState actionStarted(MechanicsState state,String action,int duration) {
        if(action.startsWith("aemeath/")&&(action.contains("heavy")||action.equals("aemeath/liberation_finale"))){
            var timers=new java.util.HashMap<>(state.timers());timers.put("aemeath_brilliance_wait",duration+81);
            return new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),state.nextBasic(),state.resources(),timers);
        }
        if(action.equals("lynae/skill_additive")){
            var timers=new java.util.HashMap<>(state.timers());timers.put("lynae_premix_stop",duration);
            return new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),state.nextBasic(),state.resources(),timers);
        }
        if(!action.equals("mornye/heavy_inversion"))return state;
        var timers=new java.util.HashMap<>(state.timers());timers.put("mornye_inversion_lock",duration);
        return new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),state.nextBasic(),state.resources(),timers);
    }
}
