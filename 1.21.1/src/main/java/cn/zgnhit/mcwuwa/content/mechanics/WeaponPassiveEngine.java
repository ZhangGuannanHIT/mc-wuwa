package cn.zgnhit.mcwuwa.content.mechanics;

import cn.zgnhit.mcwuwa.content.CharacterDefinition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Signature S1-S5 passive rules. Host owns event provenance, target scope and stat aggregation. */
public final class WeaponPassiveEngine {
    public enum Trigger { INTRO_CAST, SKILL_CAST, BASIC_CAST, BASIC_DAMAGE, SKILL_DAMAGE, ECHO_DAMAGE,
        HEAL, LIBERATION_CAST, HEAVY_DAMAGE, CONCERTO_SPENT, APPLY_HAVOC_BANE, APPLY_ELECTRO_FLARE, RESPOND_UNISON,
        APPLY_GLACIO_CHAFE, APPLY_ANOMALY, TEAM_APPLY_ANOMALY, APPLY_RUPTURE_SHIFTING,
        APPLY_FUSION_BURST, APPLY_STRAIN_SHIFTING, APPLY_AERO_EROSION, ECHO_CAST, ROVER_UNBOUND_WIND_CAST, SWITCH_OUT,
        OUTRO_CAST, DAMAGE_SPECTRO_FRAZZLE,
        /** Positive damage actually dealt by this weapon owner, irrespective of damage category. */
        OWNER_DAMAGE,
        /** This weapon owner actually received a shield, including a successfully renewed shield. */
        OWNER_SHIELD_GAIN,
        /** Actual effect application by any member of the owner team; actor identity stays host-owned. */
        TEAM_APPLY_FUSION_BURST, TEAM_APPLY_STRAIN_SHIFTING,
        /** Qualification actually acquired; distinct from a teammate responding to it. */
        GAIN_UNISON }
    /** Skill identity is independent of its damage tag. Lynae's named
     * enhanced basics do not contain the old basic_ naming convention. */
    public static boolean basicSkillAction(String action){
        return action.contains("basic_")||java.util.Set.of("lynae/visual_impact","lynae/rainbow_splash",
            "lynae/phantom_leap_1","lynae/phantom_leap_2","lynae/phantom_leap_3",
            "lynae/toward_colorful_tomorrow").contains(action);
    }
    public enum Scope { SELF, OWN_TEAM, OWN_ACTIVE, EVENT_ACTOR, ENEMIES_IN_AURA }
    public record Event(Trigger trigger, long serverTick, String actionInstanceId,
                        boolean duringBasicAction, boolean ownerActive, String eventActorId) {
        public Event(Trigger trigger,long serverTick,String actionInstanceId,boolean duringBasicAction,boolean ownerActive) {
            this(trigger,serverTick,actionInstanceId,duringBasicAction,ownerActive,"");
        }
        public Event {
            if (serverTick < 0) throw new IllegalArgumentException("Negative server tick");
            if(eventActorId==null)eventActorId="";
        }
    }
    public record Buff(String id, String attribute, double value, Scope scope,
                       String damageTag, String element, String targetCondition,
                       long expiresAtTick, int stacks, boolean endsOnSwitch, boolean sameNameHighestOnly) { }
    public record State(Map<String, Buff> buffs, Map<String, Long> readyTicks, Map<String, String> lastActions) {
        public State { buffs = Map.copyOf(buffs); readyTicks = Map.copyOf(readyTicks); lastActions = Map.copyOf(lastActions); }
        public static State empty() { return new State(Map.of(), Map.of(), Map.of()); }
    }
    public record Result(State state, List<Buff> changedBuffs, double concertoGranted) {
        public Result { changedBuffs = List.copyOf(changedBuffs); }
    }

    public List<Buff> permanent(CharacterDefinition definition, int refinement, boolean active) {
        Values values = new Values(definition, refinement);
        List<Buff> buffs = new ArrayList<>();
        String attribute = switch (definition.id()) {
            case "stellar_symphony", "firstlights_herald" -> "health_pct";
            case "luminous_hymn", "whispers_of_sirens", "rime_draped_sprouts" -> "attack_pct";
            case "solsworn_ciphers", "lux_and_umbra", "blazing_brilliance", "the_last_dance",
                 "moongazers_sigil", "wildfire_mark", "forged_dwarf_star", "glint_of_clouds" -> "attack_pct";
            case "defiers_thorn" -> "health_pct";
            case "starfield_calibrator" -> "defense_pct";
            case "red_spring", "frostburn", "kumokiri", "lethean_elegy", "spectrum_blaster", "emerald_sentence", "woodland_aria" -> "attack_pct";
            case "bloodpacts_pledge" -> null;
            default -> "all_damage_bonus";
        };
        if(java.util.Set.of(21010046,21020107,21040036,21050086).contains(definition.weapon().sourceId()))attribute="attack_pct";
        if (attribute != null) buffs.add(new Buff(definition.id()+"/permanent", attribute, values.ratio(0), Scope.SELF, "all", "all", "none", Long.MAX_VALUE, 1, false, false));
        if (definition.id().equals("stringmaster") && !active)
            buffs.add(new Buff("stringmaster/off_field", "attack_pct", values.ratio(4), Scope.SELF, "all", "all", "owner_off_field", Long.MAX_VALUE, 1, false, false));
        return List.copyOf(buffs);
    }

    public Result onEvent(CharacterDefinition definition, int refinement, State previous, Event event) {
        Values value = new Values(definition, refinement);
        Builder b = new Builder(previous, event, definition.id());
        Trigger trigger = event.trigger();
        // Source IDs keep staged new signatures independent of final registry
        // spelling. These branches become active only for imported definitions.
        if(definition.weapon().sourceId()==21010046){
            if(trigger==Trigger.INTRO_CAST||trigger==Trigger.SKILL_CAST)
                b.buff("heavy","heavy_bonus",value.ratio(1),value.ticks(2));
            if(trigger==Trigger.OWNER_SHIELD_GAIN&&b.ready("shield_gain",10)){
                Buff old=b.buffs.get(definition.id()+"/shield_ignore");
                int stacks=Math.min(value.integer(4),old==null?1:old.stacks()+1);
                Buff next=new Buff(definition.id()+"/shield_ignore","defense_ignore",value.ratio(3)*stacks,
                    Scope.SELF,"heavy","all","none",event.serverTick()+value.ticks(5),stacks,false,false);
                b.buffs.put(next.id(),next);b.changed.add(next);
            }
        }
        if(definition.weapon().sourceId()==21050086&&trigger==Trigger.APPLY_GLACIO_CHAFE){
            b.buff("glacio","glacio_bonus",value.ratio(1),value.ticks(2));
            b.buff("team_attack","attack_pct",value.ratio(3),value.ticks(4),Scope.OWN_TEAM,"all","all","none",false,true);
        }
        if(definition.weapon().sourceId()==21020107){
            if(trigger==Trigger.GAIN_UNISON){
                b.buffs.remove(definition.id()+"/lament");
                b.buff("self_electro","electro_bonus",value.ratio(1),value.ticks(3));
                b.buff("team_remembrance","electro_bonus",value.ratio(2),value.ticks(3),Scope.OWN_TEAM,"all","all","none",false,true);
            }
            if(trigger==Trigger.CONCERTO_SPENT){
                b.buffs.remove(definition.id()+"/team_remembrance");
                b.buff("lament","electro_bonus",value.ratio(4),value.ticks(5),Scope.SELF,"all","all","none",true,false);
            }
        }
        if(definition.weapon().sourceId()==21040036&&trigger==Trigger.BASIC_CAST){
            b.buff("ignore","defense_ignore",value.ratio(1),value.ticks(3));
            b.buff("frazzle","damage_amplification",value.ratio(2),value.ticks(3),
                Scope.SELF,"all","spectro","direct_spectro_frazzle",false,false);
        }
        if (java.util.Set.of(21010046,21050086,21020107,21040036).contains(definition.weapon().sourceId())) {
            if (trigger == Trigger.SWITCH_OUT) b.buffs.values().removeIf(Buff::endsOnSwitch);
            return new Result(new State(b.buffs, b.readyTicks, b.actions), b.changed, b.concerto);
        }
        switch (definition.id()) {
            case "rime_draped_sprouts" -> {
                if(trigger==Trigger.SKILL_CAST&&b.oncePerAction("rime_cast",event.actionInstanceId())) {
                    Buff old=b.buffs.get("rime_draped_sprouts/onscreen");int stacks=Math.min(value.integer(2),old==null?1:old.stacks()+1);
                    Buff next=new Buff("rime_draped_sprouts/onscreen","basic_bonus",value.ratio(1)*stacks,Scope.SELF,
                        "basic","all","owner_on_field",event.serverTick()+value.ticks(3),stacks,false,false);
                    b.buffs.put(next.id(),next);b.changed.add(next);
                }
                if(trigger==Trigger.OUTRO_CAST) {
                    Buff full=b.buffs.get("rime_draped_sprouts/onscreen");
                    if(full!=null&&full.stacks()>=value.integer(4)) {
                        b.buffs.remove(full.id());
                        b.buff("offscreen","basic_bonus",value.ratio(5),value.ticks(6),Scope.SELF,"basic","all","owner_off_field",false,false);
                    }
                }
            }
            case "blazing_brilliance" -> {
                int gain=0;
                if(trigger==Trigger.OWNER_DAMAGE&&b.ready("plumage_damage",10))gain=1;
                if(trigger==Trigger.SKILL_CAST&&b.oncePerAction("plumage_cast",event.actionInstanceId()))gain=5;
                if(gain>0) {
                    Buff old=b.buffs.get("blazing_brilliance/plumage");
                    int stacks=Math.min(14,(old==null?0:old.stacks())+gain);
                    // Partial stacks have no source expiry. Once full, neither
                    // further damage nor another skill cast refreshes 12 seconds.
                    long deadline=old!=null&&old.stacks()==14?old.expiresAtTick():stacks==14?event.serverTick()+value.ticks(2):Long.MAX_VALUE;
                    Buff next=new Buff("blazing_brilliance/plumage","skill_bonus",value.ratio(1)*stacks,Scope.SELF,"all","all","none",deadline,stacks,false,false);
                    b.buffs.put(next.id(),next);b.changed.add(next);
                }
            }
            case "the_last_dance" -> {
                if(trigger==Trigger.INTRO_CAST||trigger==Trigger.LIBERATION_CAST)
                    b.buff("skill","skill_bonus",value.ratio(1),value.ticks(2));
            }
            case "moongazers_sigil" -> {
                if(trigger==Trigger.INTRO_CAST||trigger==Trigger.LIBERATION_CAST)
                    b.buff("liberation","liberation_bonus",value.ratio(1),value.ticks(2));
                if(trigger==Trigger.OWNER_SHIELD_GAIN&&b.ready("shield_gain",value.ticks(4))) {
                    Buff old=b.buffs.get("moongazers_sigil/shield_ignore");int stacks=Math.min(value.integer(5),old==null?1:old.stacks()+1);
                    Buff next=new Buff("moongazers_sigil/shield_ignore","defense_ignore",value.ratio(3)*stacks,Scope.SELF,"liberation","all","none",event.serverTick()+value.ticks(6),stacks,false,false);
                    b.buffs.put(next.id(),next);b.changed.add(next);
                }
                // "Counts as full" is a temporary override, not five genuine
                // shield receipts and not destruction of an existing 7s stack.
                if(trigger==Trigger.INTRO_CAST)
                    b.buff("intro_ignore","defense_ignore",value.ratio(3)*value.integer(5),value.ticks(7),Scope.SELF,"liberation","all","none",false,false);
            }
            case "wildfire_mark" -> {
                if((trigger==Trigger.INTRO_CAST||trigger==Trigger.LIBERATION_CAST)&&b.oncePerAction("wildfire_cast",event.actionInstanceId())) {
                    b.buff("liberation","liberation_bonus",value.ratio(1),value.ticks(2));
                    b.actions.put("wildfire_extensions","0");
                }
                if(trigger==Trigger.HEAVY_DAMAGE) {
                    Buff old=b.buffs.get("wildfire_mark/liberation");
                    int extensions=Integer.parseInt(b.actions.getOrDefault("wildfire_extensions","0"));
                    if(old!=null&&extensions<value.integer(4)) {
                        Buff next=new Buff(old.id(),old.attribute(),old.value(),old.scope(),old.damageTag(),old.element(),old.targetCondition(),old.expiresAtTick()+value.ticks(3),old.stacks(),old.endsOnSwitch(),old.sameNameHighestOnly());
                        b.buffs.put(next.id(),next);b.changed.add(next);b.actions.put("wildfire_extensions",Integer.toString(extensions+1));
                        b.buff("team_fusion","fusion_bonus",value.ratio(5),value.ticks(6),Scope.OWN_TEAM,"all","all","none",false,true);
                    }
                }
            }
            case "forged_dwarf_star" -> {
                boolean own=trigger==Trigger.APPLY_FUSION_BURST||trigger==Trigger.APPLY_STRAIN_SHIFTING;
                boolean team=trigger==Trigger.TEAM_APPLY_FUSION_BURST||trigger==Trigger.TEAM_APPLY_STRAIN_SHIFTING;
                if(own)b.buff("liberation","liberation_bonus",value.ratio(1),value.ticks(2));
                if((own||team)&&b.buffs.containsKey("forged_dwarf_star/liberation"))
                    b.buff("team_applicator"+(event.eventActorId().isBlank()?"":"/"+event.eventActorId()),"attack_pct",value.ratio(3),value.ticks(4),Scope.EVENT_ACTOR,"all","all","none",false,true);
            }
            case "glint_of_clouds" -> {
                if(trigger==Trigger.APPLY_STRAIN_SHIFTING&&b.ready("offset",value.ticks(4))) {
                    Buff old=b.buffs.get("glint_of_clouds/aero");int stacks=Math.min(value.integer(3),old==null?1:old.stacks()+1);
                    int duration=stacks>=value.integer(3)?value.ticks(5):value.ticks(2);
                    Buff next=new Buff("glint_of_clouds/aero","aero_bonus",value.ratio(1)*stacks,Scope.SELF,"all","all","none",event.serverTick()+duration,stacks,false,false);
                    b.buffs.put(next.id(),next);b.changed.add(next);
                    if(stacks>=value.integer(3))
                        b.buff("aero_ignore","defense_ignore",value.ratio(6),duration,Scope.SELF,"all","aero","none",false,false);
                }
            }
            case "luminous_hymn" -> {
                if(trigger==Trigger.DAMAGE_SPECTRO_FRAZZLE){
                    Buff old=b.buffs.get("luminous_hymn/radiance_basic");int count=Math.min(value.integer(2),old==null?1:old.stacks()+1);
                    for(String kind:List.of("basic","heavy")){
                        Buff next=new Buff("luminous_hymn/radiance_"+kind,"all_damage_bonus",value.ratio(1)*count,Scope.SELF,kind,"all","none",event.serverTick()+value.ticks(3),count,false,false);
                        b.buffs.put(next.id(),next);b.changed.add(next);
                    }
                }
                if(trigger==Trigger.OUTRO_CAST)b.buff("frazzle_aura","damage_amplification",value.ratio(4),value.ticks(5),Scope.ENEMIES_IN_AURA,"effect","spectro","none",false,true);
            }
            case "firstlights_herald" -> {
                if(trigger==Trigger.LIBERATION_CAST&&b.ready("herald_concerto",400))b.concerto+=value.number(1);
                if(trigger==Trigger.APPLY_GLACIO_CHAFE){b.readyTicks.put("herald_snow",event.serverTick()+120);if(event.ownerActive())b.actions.put("herald_active_frost","yes");}
                if(trigger==Trigger.HEAL){b.readyTicks.put("herald_ripples",event.serverTick()+120);if(event.ownerActive())b.actions.put("herald_active_heal","yes");}
                if(trigger==Trigger.OUTRO_CAST&&b.actions.containsKey("herald_active_frost")&&b.actions.containsKey("herald_active_heal")){
                    b.readyTicks.put("herald_snow",event.serverTick()+120);b.readyTicks.put("herald_ripples",event.serverTick()+120);
                    b.actions.remove("herald_active_frost");b.actions.remove("herald_active_heal");
                }
                long snow=b.readyTicks.getOrDefault("herald_snow",0L),ripples=b.readyTicks.getOrDefault("herald_ripples",0L);
                if(snow>event.serverTick()&&ripples>event.serverTick()){
                    int remaining=(int)(Math.min(snow,ripples)-event.serverTick());
                    if(remaining>0)b.buff("team_attack","attack_pct",value.ratio(2),remaining,Scope.OWN_TEAM,"all","all","none",false,true);
                }
            }
            case "whispers_of_sirens" -> {
                if(trigger==Trigger.INTRO_CAST||trigger==Trigger.BASIC_CAST)b.readyTicks.put("sirens_qualification",event.serverTick()+value.ticks(1));
                if(trigger==Trigger.ECHO_CAST&&b.readyTicks.getOrDefault("sirens_qualification",0L)>event.serverTick()){
                    Buff old=b.buffs.get("whispers_of_sirens/dream_basic");
                    int count=old==null?0:old.stacks();
                    if(count<value.integer(3)&&b.readyTicks.getOrDefault("sirens_lock",0L)<=event.serverTick()){
                        if(count==0)b.actions.keySet().removeIf(k->k.startsWith("sirens_echo:"));
                        String identity="sirens_echo:"+event.actionInstanceId();
                        if(!b.actions.containsKey(identity)){
                            b.actions.put(identity,"used");count++;long deadline=event.serverTick()+value.ticks(4);
                            Buff next=new Buff("whispers_of_sirens/dream_basic","all_damage_bonus",value.ratio(6),Scope.SELF,"basic","all","none",deadline,count,true,false);
                            b.buffs.put(next.id(),next);b.changed.add(next);
                            if(count>=value.integer(3)){
                                b.buff("dream_resistance","resistance_ignore",value.ratio(8),value.ticks(4),Scope.SELF,"all","havoc","none",true,false);
                                b.readyTicks.put("sirens_lock",deadline);
                            }
                        }
                    }
                }
            }
            case "stellar_symphony" -> {
                if(trigger==Trigger.LIBERATION_CAST&&b.ready("stellar_concerto",value.ticks(2)))b.concerto+=value.number(1);
                if(trigger==Trigger.HEAL&&event.actionInstanceId().startsWith("shorekeeper_skill:"))b.buff("healing_team","attack_pct",value.ratio(4),value.ticks(5),Scope.OWN_TEAM,"all","all","none",false,true);
            }
            case "solsworn_ciphers" -> {
                if(trigger==Trigger.INTRO_CAST||trigger==Trigger.ECHO_CAST)b.buff("solsworn_echo","damage_amplification",value.ratio(1),value.ticks(2),Scope.SELF,"echo","all","none",false,false);
                if(trigger==Trigger.ECHO_DAMAGE)b.buff("solsworn_ignore","defense_ignore",value.ratio(3),value.ticks(4),Scope.SELF,"all","aero","none",false,false);
            }
            case "lux_and_umbra" -> {
                if(trigger==Trigger.ECHO_DAMAGE)b.buff("lux_heavy","damage_amplification",value.ratio(1),value.ticks(2),Scope.SELF,"heavy","all","none",false,false);
                if(trigger==Trigger.HEAVY_DAMAGE)b.buff("umbra_echo","damage_amplification",value.ratio(3),value.ticks(4),Scope.SELF,"echo","all","none",false,false);
                Buff light=b.buffs.get("lux_and_umbra/lux_heavy"),dark=b.buffs.get("lux_and_umbra/umbra_echo");
                if(light!=null&&dark!=null){long remaining=Math.min(light.expiresAtTick(),dark.expiresAtTick())-event.serverTick();if(remaining>0)b.buff("duality_ignore","defense_ignore",value.ratio(6),(int)remaining,Scope.SELF,"all","all","none",false,false);}
            }
            case "emerald_sentence" -> {
                if(trigger==Trigger.BASIC_CAST||trigger==Trigger.INTRO_CAST)b.readyTicks.put("emerald_armed_until",event.serverTick()+value.ticks(1));
                if(trigger==Trigger.INTRO_CAST)b.buff("intro_team","echo_bonus",value.ratio(5),value.ticks(6),Scope.OWN_TEAM,"all","all","none",false,true);
                if(trigger==Trigger.ECHO_CAST&&b.readyTicks.getOrDefault("emerald_armed_until",0L)>event.serverTick()) {
                    Buff old=b.buffs.get("emerald_sentence/bamboo");
                    if(old==null){b.actions.keySet().removeIf(key->key.startsWith("emerald_echo/"));}
                    String identity="emerald_echo/"+event.actionInstanceId();
                    if((old==null||old.stacks()<2)&&!b.actions.containsKey(identity)&&b.ready("emerald_trigger",value.ticks(4))) {
                        b.actions.put(identity,event.actionInstanceId());int stacks=old==null?1:old.stacks()+1;
                        Buff next=new Buff("emerald_sentence/bamboo","heavy_bonus",value.number(2)/100*stacks,Scope.SELF,"all","all","none",event.serverTick()+value.ticks(3),stacks,true,false);
                        b.buffs.put(next.id(),next);b.changed.add(next);
                    }
                }
                if(trigger==Trigger.SWITCH_OUT){b.readyTicks.remove("emerald_armed_until");b.actions.keySet().removeIf(key->key.startsWith("emerald_echo/"));}
            }
            case "woodland_aria" -> {
                if(trigger==Trigger.APPLY_AERO_EROSION)b.buff("aero","aero_bonus",value.ratio(1),value.ticks(2));
                // The on-hit resistance reduction belongs to the struck enemy, and is applied by the host.
            }
            case "ages_of_harvest" -> {
                if (trigger == Trigger.INTRO_CAST) b.buff("intro", "skill_bonus", value.ratio(1), value.ticks(2));
                if (trigger == Trigger.SKILL_CAST) b.buff("skill", "skill_bonus", value.ratio(3), value.ticks(4));
            }
            case "azure_oath" -> {
                if (trigger == Trigger.APPLY_HAVOC_BANE) {
                    b.buff("heavy_amp", "damage_amplification", value.ratio(1), value.ticks(3), Scope.SELF, "heavy", "all", "none", false, false);
                    b.buff("heavy_ignore", "defense_ignore", value.ratio(2), value.ticks(3), Scope.SELF, "heavy", "all", "none", false, false);
                }
            }
            case "bloodpacts_pledge" -> {
                if (trigger == Trigger.HEAL) b.buff("skill", "skill_bonus", value.ratio(0), value.ticks(1));
                if (trigger == Trigger.ROVER_UNBOUND_WIND_CAST) b.buff("wind", "damage_amplification", value.ratio(2), value.ticks(3), Scope.OWN_ACTIVE, "all", "aero", "none", false, false);
            }
            case "blooming_jadehaven" -> {
                if ((trigger == Trigger.APPLY_ELECTRO_FLARE || trigger == Trigger.RESPOND_UNISON) && b.ready("trigger", value.ticks(5))) {
                    b.buff("skill_amp", "damage_amplification", value.ratio(1), value.ticks(4), Scope.SELF, "skill", "all", "none", false, false);
                    b.buff("skill_ignore", "resistance_ignore", value.ratio(2), value.ticks(4), Scope.SELF, "skill", "electro", "none", false, false);
                    b.buff("electro_aura", "effect_amplification", value.ratio(3), value.ticks(4), Scope.ENEMIES_IN_AURA, "effect", "electro", "owner_active", false, true);
                }
            }
            case "defiers_thorn" -> {
                if (trigger == Trigger.INTRO_CAST || trigger == Trigger.BASIC_CAST) {
                    b.buff("defense_ignore", "defense_ignore", value.ratio(1), value.ticks(3));
                    b.buff("erosion_target", "damage_amplification", value.ratio(2), value.ticks(3), Scope.SELF, "all", "all", "target_aero_erosion_at_least_1", false, false);
                }
            }
            case "everbright_polestar" -> {
                if (trigger == Trigger.APPLY_RUPTURE_SHIFTING || trigger == Trigger.APPLY_FUSION_BURST) {
                    b.buff("liberation_ignore", "defense_ignore", value.ratio(1), value.ticks(3), Scope.SELF, "liberation", "all", "none", false, false);
                    b.buff("fusion_ignore", "resistance_ignore", value.ratio(2), value.ticks(3), Scope.SELF, "liberation", "fusion", "none", false, false);
                }
            }
            case "frostburn" -> {
                if (trigger == Trigger.APPLY_GLACIO_CHAFE && b.ready("trigger", value.ticks(5))) {
                    b.buff("glacio_amp", "damage_amplification", value.ratio(1), value.ticks(4), Scope.SELF, "all", "glacio", "none", false, false);
                    b.buff("liberation_ignore", "defense_ignore", value.ratio(2), value.ticks(4), Scope.SELF, "liberation", "all", "none", false, false);
                    b.buff("glacio_aura", "effect_amplification", value.ratio(3), value.ticks(4), Scope.ENEMIES_IN_AURA, "effect", "glacio", "owner_active", false, true);
                }
            }
            case "kumokiri" -> {
                if (trigger == Trigger.INTRO_CAST || trigger == Trigger.APPLY_ANOMALY)
                    b.stack("liberation", "liberation_bonus", value.ratio(1), value.integer(2), value.ticks(3), Scope.SELF, false);
                Buff stacks = b.buffs.get("kumokiri/liberation");
                if ((trigger == Trigger.TEAM_APPLY_ANOMALY || trigger == Trigger.APPLY_ANOMALY)
                        && stacks != null && stacks.stacks() >= value.integer(2))
                    b.buff("team_applicator", "all_damage_bonus", value.ratio(4), value.ticks(5), Scope.EVENT_ACTOR, "all", "all", "none", false, true);
            }
            case "lethean_elegy" -> {
                if (trigger == Trigger.ECHO_DAMAGE) {
                    b.buff("skill_bonus", "skill_bonus", value.ratio(2), value.ticks(1));
                    b.buff("echo_amp", "damage_amplification", value.ratio(3), value.ticks(1), Scope.SELF, "echo", "all", "none", false, false);
                    b.buff("defense_ignore", "defense_ignore", value.ratio(4), value.ticks(1));
                }
            }
            case "red_spring" -> {
                if (trigger == Trigger.BASIC_DAMAGE && b.ready("basic",20))
                    b.stack("basic", "basic_bonus", value.ratio(1), value.integer(3), value.ticks(2), Scope.SELF, false);
                if (trigger == Trigger.CONCERTO_SPENT && b.ready("concerto",20))
                    b.buff("concerto", "basic_bonus", value.ratio(4), value.ticks(5), Scope.SELF, "all", "all", "none", true, false);
            }
            case "spectrum_blaster" -> {
                if (trigger == Trigger.INTRO_CAST || trigger == Trigger.BASIC_DAMAGE) b.buff("basic", "basic_bonus", value.ratio(1), value.ticks(2));
                if ((trigger == Trigger.APPLY_RUPTURE_SHIFTING || trigger == Trigger.APPLY_STRAIN_SHIFTING)
                        && event.duringBasicAction() && b.oncePerAction("team", event.actionInstanceId()))
                    b.stack("team", "all_damage_bonus", value.ratio(3), value.integer(4), value.ticks(5), Scope.OWN_TEAM, true);
            }
            case "starfield_calibrator" -> {
                if (trigger == Trigger.SKILL_CAST && b.ready("concerto", value.ticks(2))) b.concerto += value.number(1);
                if (trigger == Trigger.HEAL) b.buff("team_crit", "crit_damage", value.ratio(4), value.ticks(5), Scope.OWN_TEAM, "all", "all", "none", false, true);
            }
            case "stringmaster" -> {
                if (trigger == Trigger.SKILL_DAMAGE) b.stack("attack", "attack_pct", value.ratio(1), value.integer(2), value.ticks(3), Scope.SELF, false);
            }
            default -> throw new IllegalArgumentException("Unknown weapon passive: " + definition.id());
        }
        if (trigger == Trigger.SWITCH_OUT) b.buffs.values().removeIf(Buff::endsOnSwitch);
        return new Result(new State(b.buffs, b.readyTicks, b.actions), b.changed, b.concerto);
    }

    public List<Buff> active(State state, long tick) {
        Buff intro=state.buffs().get("moongazers_sigil/intro_ignore");
        boolean virtualFull=intro!=null&&intro.expiresAtTick()>tick;
        return state.buffs().values().stream().filter(buff -> buff.expiresAtTick() > tick)
            .filter(buff -> !virtualFull||!buff.id().equals("moongazers_sigil/shield_ignore")).toList();
    }
    private record Values(List<String> values) {
        Values(CharacterDefinition definition, int refinement) {
            this(definition.weapon().refinements().get(validateRefinement(refinement)-1).parameters());
        }
        static int validateRefinement(int refinement) { if (refinement<1 || refinement>5) throw new IllegalArgumentException("S1..S5 required"); return refinement; }
        double number(int index) { return Double.parseDouble(values.get(index)); }
        int integer(int index) { return (int)number(index); }
        int ticks(int index) { return (int)Math.round(number(index)*20); }
        double ratio(int index) {
            String raw = values.get(index);
            if (!raw.endsWith("%")) throw new IllegalArgumentException("Expected source percentage: " + raw);
            return Double.parseDouble(raw.substring(0,raw.length()-1))/100;
        }
    }
    private static final class Builder {
        final String weapon;
        final Event event;
        final Map<String,Buff> buffs;
        final Map<String,Long> readyTicks;
        final Map<String,String> actions;
        final List<Buff> changed = new ArrayList<>();
        double concerto;
        Builder(State state, Event event, String weapon) {
            this.weapon=weapon; this.event=event;
            buffs=new HashMap<>(state.buffs()); readyTicks=new HashMap<>(state.readyTicks()); actions=new HashMap<>(state.lastActions());
            buffs.values().removeIf(buff->buff.expiresAtTick()<=event.serverTick());
        }
        boolean ready(String key,int interval) {
            if (readyTicks.getOrDefault(key,0L)>event.serverTick()) return false;
            readyTicks.put(key,event.serverTick()+interval); return true;
        }
        boolean oncePerAction(String key,String action) {
            if (action==null || action.isBlank()) throw new IllegalArgumentException("Trigger requires source action instance");
            if (action.equals(actions.get(key))) return false;
            actions.put(key,action); return true;
        }
        void buff(String id,String attribute,double value,int ticks) { buff(id,attribute,value,ticks,Scope.SELF,"all","all","none",false,false); }
        void buff(String id,String attribute,double value,int ticks,Scope scope,String tag,String element,String condition,boolean endsOnSwitch,boolean strongest) {
            Buff buff=new Buff(weapon+"/"+id,attribute,value,scope,tag,element,condition,event.serverTick()+ticks,1,endsOnSwitch,strongest);
            buffs.put(buff.id(),buff);changed.add(buff);
        }
        void stack(String id,String attribute,double perStack,int cap,int ticks,Scope scope,boolean strongest) {
            Buff old=buffs.get(weapon+"/"+id);
            int count=Math.min(cap,old==null?1:old.stacks()+1);
            Buff buff=new Buff(weapon+"/"+id,attribute,perStack*count,scope,"all","all","none",event.serverTick()+ticks,count,false,strongest);
            buffs.put(buff.id(),buff);changed.add(buff);
        }
    }
}
