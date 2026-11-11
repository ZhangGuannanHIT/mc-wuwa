package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.Set;
import java.util.Map;
import static cn.zgnhit.mcwuwa.content.mechanics.MechanicsEngine.*;

/** Owned anomaly events, never client-supplied rewards. MC dispatch: one accepted batch per callback. */
public final class SoloEffectRules {
    public static final Set<String> ANOMALIES=Set.of("havoc_bane","aero_erosion","electro_flare","glacio_chafe","glacio_frost","fusion_burst","spectro_frazzle");
    public static final Set<String> COUNTERS=Set.of("wind_dependency","wind_frequency","drywood_uses");
    private SoloEffectRules() { }
    public static Result applied(MechanicsState state,Context context,String effect,boolean ownerActor) {
        Transition t=new Transition(state,context);String who=state.characterId();
        if(who.equals("yinlin")&&ownerActor&&effect.equals("yinlin_c6_resource")&&state.chain()>=6)t.add("judgment",10);
        if(who.equals("yangyang_xuanling")&&effect.equals("havoc_bane")) {
            if(!t.has("oath_pulse")){t.add("oath",1);t.timer("oath",80);t.timer("oath_pulse",20);}
            if(!t.has("wind_frequency")&&!t.has("wind_dependency_pulse")) {
                t.timer("wind_dependency",t.time("wind_dependency")+1);t.timer("wind_dependency_pulse",20);
                if(t.time("wind_dependency")>=6){t.clear("wind_dependency");t.timer("wind_frequency",1);}
            }
        }
        // C6 accepts all six anomalies from the own team, but only while Xuanling is deployed.
        // Havoc-only oath/frequency above has a different source condition.
        if(who.equals("yangyang_xuanling")&&context.active()&&state.chain()>=6&&ANOMALIES.contains(effect)
            &&t.has("drywood")&&!t.has("drywood_pulse")) {
            t.timer("drywood_pulse",20);t.timer("drywood_uses",t.time("drywood_uses")+1);
            t.effect(Kind.NATIVE_PROC,"xuanling_shadow",3.3798,0,Map.of("fixedCritRate",1d));
            if(t.time("drywood_uses")>=5){t.clear("drywood");t.clear("drywood_uses");}
        }
        // Thunder Realm comes from consumed Electro Rage, not ordinary Flare applications.
        // Aemeath C2 is a target-owned qualification, handled by SoloTargetRuntime.
        return new Result(true,"owned_anomaly",t.build(),t.effects);
    }
    static void cast(Transition t) {
        for(var effect:java.util.List.copyOf(t.effects))if(effect.kind()==Kind.ACTION) {
            String action=effect.id();
            if(t.original.characterId().equals("aemeath")&&t.original.chain()>=3&&(t.original.timer("immediate")>0||t.original.timer("aemeath_brilliance")>0)
                &&Set.of("aemeath/heavy_charge_2","aemeath/mech_heavy_charge_2").contains(action))
                t.effect(Kind.MARK_AREA,t.original.mode().equals("fusion")?"fusion_burst":"rupture_shifting",1,0,Map.of("radius",6d));
            if(t.original.characterId().equals("yangyang_xuanling")&&action.startsWith("yangyang_xuanling/flow_")) {
                if(t.original.chain()>=1){t.effect(Kind.NATIVE_PROC,"xuanling_shadow",3.3798,0);t.effect(Kind.CONTROL_AREA,"xuanling_shadow_stasis",1,20,Map.of("radius",6d,"actionBound",1d));}
                if(t.has("wind_frequency")){t.effect(Kind.MARK_TARGET,"havoc_bane",6,0);t.clear("wind_frequency");}
                if(t.original.chain()>=6&&!t.has("drywood_cooldown")){t.timer("drywood",600);t.timer("drywood_cooldown",500);t.clear("drywood_uses");}
            }
            if(t.original.characterId().equals("cartethyia")&&t.original.chain()>=6&&java.util.List.of("cartethyia/intro","cartethyia/fleurdelys_intro","cartethyia/liberation_finale").contains(action))
                t.timer("cartethyia_cap_settle",600);
        }
        if(t.original.characterId().equals("cartethyia")&&t.form.equals("fleurdelys")&&!t.original.form().equals("fleurdelys")
            &&t.effects.stream().anyMatch(e->e.kind()==Kind.PAY_ENERGY&&e.amount()==125)) {
            if(t.original.chain()>=2){t.effect(Kind.MARK_AREA,"raise_cap_aero_erosion",3,600,Map.of("radius",6d));t.timer("cartethyia_c2_settle",600);}
            if(t.original.chain()>=6)t.timer("cartethyia_cap_settle",600);
        }
    }
}
