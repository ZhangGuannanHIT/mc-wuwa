package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.Map;
import static cn.zgnhit.mcwuwa.content.mechanics.MechanicsEngine.*;
import static cn.zgnhit.mcwuwa.content.mechanics.ExtendedMechanics.*;

/** Source return-forge accounting; ten-tick points/twenty-tick empty warmup are explicit MC input policies. */
public final class HiyukiReturnChargeRules {
    private HiyukiReturnChargeRules() { }
    public static boolean isCharging(MechanicsState state) {
        return state!=null&&state.characterId().equals("hiyuki")&&state.timer("return_charge")>0;
    }
    static boolean input(Transition t,Input input) {
        if(input!=Input.LIBERATION_HOLD&&input!=Input.LIBERATION_RELEASE)return false;
        require(t.form.equals("future"),"return_requires_future");require(t.context.active(),"resource_owner_inactive");
        if(input==Input.LIBERATION_HOLD) {
            require(!t.has("return_charge"),"already_return_charging");t.requireCooldown("return");require(t.context.energy()>=125,"energy_missing");
            clear(t);t.timer("return_charge",60);t.timer("return_charge_pulse",10);
            if(t.get("forge")==0)t.timer("return_charge_wait",20);
        } else {
            require(t.has("return_charge"),"return_charge_required");
            if(t.time("return_charge_spent")>0)finish(t,t.time("return_charge_spent"));
            else t.timer("return_charge_release",1);
        }
        return true;
    }
    static void tick(Transition t) {
        if(t.original.timer("return_charge")==0)return;
        if(!t.has("return_charge")||!t.context.active()||!t.form.equals("future")||t.context.energy()<125){clear(t);return;}
        if(t.get("forge")>0&&!t.has("return_charge_pulse")) {
            t.add("forge",-1);t.timer("return_charge_spent",t.time("return_charge_spent")+1);t.timer("return_charge_pulse",10);
        }
        int spent=t.time("return_charge_spent");
        if(t.get("forge")==0&&(spent>0||!t.has("return_charge_wait"))||t.has("return_charge_release")&&spent>0)finish(t,spent);
    }
    static void finish(Transition t,int spent) {
        if(spent<0||spent>3)throw new IllegalArgumentException("return forge points");
        t.requireCooldown("return");t.payEnergy(125);t.timer("return",500);t.action("liberation_return");
        if(spent>0)t.effect(Kind.BUFF_SELF,"hiyuki_return_forge_multiplier",7.9524*spent,1,Map.of("additiveMultiplier",1.0,"finalHitOnly",1.0,"actionScoped",1d));
        t.set("mind",0);t.set("cold",0);form(t,"present",0);t.clear("iai_stance");t.clear("iai_dodge_window");t.clear("iai_hold_window");clear(t);
        t.effect(Kind.GAIN_CONCERTO,"liberation",20,0);
    }
    static void clear(Transition t) {
        for(String key:java.util.List.of("return_charge","return_charge_spent","return_charge_pulse","return_charge_release","return_charge_wait"))t.clear(key);
    }
}
