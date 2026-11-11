package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.List;
import java.util.Map;
import static cn.zgnhit.mcwuwa.content.mechanics.MechanicsEngine.*;

/** Source-described form gates for the remaining ten characters, independent of Minecraft.
 * Native times are seconds ×20. A source saying only "a short time" uses an explicit
 * Minecraft continuation window (60 ticks); it is not a claimed original measurement.
 * Audited native yields and explicitly authorized GameplayResourceRules are separate sources;
 * PreviewResourceYields is optional legacy development data, never the normal-play fallback.
 */
final class ExtendedMechanics {
    private ExtendedMechanics() { }

    static void dispatch(Transition t, Input input) {
        switch (t.original.characterId()) {
            case "suoming" -> SuomingMechanics.input(t,input);
            case "lucilla" -> LucillaMechanics.input(t,input);
            case "zani" -> ZaniMechanics.input(t,input);
            case "augusta" -> AugustaMechanics.input(t,input);
            case "zhezhi" -> NewFiveMechanics.zhezhi(t,input);
            case "changli" -> NewSixMechanics.changli(t,input);
            case "carlotta" -> NewSixMechanics.carlotta(t,input);
            case "iuno" -> NewSixMechanics.iuno(t,input);
            case "lupa" -> NewSixMechanics.lupa(t,input);
            case "denia" -> NewSixMechanics.denia(t,input);
            case "qingxiao" -> NewSixMechanics.qingxiao(t,input);
            case "phoebe" -> NewTideMechanics.phoebe(t,input);
            case "suisui" -> NewTideMechanics.suisui(t,input);
            case "cantarella" -> NewTideMechanics.cantarella(t,input);
            case "shorekeeper" -> NewThreeMechanics.shorekeeper(t,input);
            case "sigrika" -> NewThreeMechanics.sigrika(t,input);
            case "galbrena" -> NewThreeMechanics.galbrena(t,input);
            case "qiuyuan" -> NewAeroMechanics.qiuyuan(t,input);
            case "ciaccona" -> NewAeroMechanics.ciaccona(t,input);
            case "jinhsi" -> jinhsi(t, input);
            case "camellya" -> camellya(t, input);
            case "yinlin" -> yinlin(t, input);
            case "aero_rover" -> rover(t, input);
            case "cartethyia" -> cartethyia(t, input);
            case "yangyang_xuanling" -> CharacterBranches.yangyang(t, input);
            case "hsin" -> CharacterBranches.hsin(t, input);
            case "hiyuki" -> CharacterBranches.hiyuki(t, input);
            case "chisa" -> CharacterBranches.chisa(t, input);
            case "phrolova" -> CharacterBranches.phrolova(t, input);
            default -> throw new Rejected("strategy_not_implemented");
        }
    }
    static void combo(Transition t, String prefix, int maximum) {
        int segment = Math.clamp(t.combo, 1, maximum);
        t.action(prefix + "basic_0" + segment); t.combo = segment % maximum + 1;
    }
    static void form(Transition t, String form, int duration) {
        t.form = form; t.combo = 1; t.effect(Kind.FORM, form, 0, duration);
    }
    static void require(boolean condition, String reason) { if (!condition) throw new Rejected(reason); }
    static void mark(Transition t, String id, int layers, int duration) {
        t.effect(Kind.MARK_TARGET, id, layers, duration);
    }
    static void temporary(Transition t, String key) { t.timer(key, 60); }
    static void healTeam(Transition t, String id, double flat, double attackRatio) {
        t.effect(Kind.HEAL_TEAM, id, flat + attackRatio * t.context.attack(), 0);
    }

    private static void jinhsi(Transition t, Input input) {
        boolean incarnation = t.form.equals("incarnation");
        switch (input) {
            case BASIC -> {
                if(t.form.equals("epiphany")&&t.context.airborne()){t.payStamina(30);t.action("incarnation_heavy");break;}
                int stage = Math.clamp(t.combo, 1, 4); combo(t, incarnation ? "incarnation_" : "", 4);
                if (incarnation && t.original.chain() >= 1) { t.add("incarnation_stacks", 1); t.timer("incarnation_stacks", 120); }
                if (stage == 4) {
                    if (incarnation) { form(t, "epiphany", 100); t.timer("epiphany", 100); t.clear("incarnation"); }
                    else t.timer("overflowing", 100);
                }
            }
            case HEAVY_START -> t.timer("charging", 1);
            case HEAVY_RELEASE, HEAVY_FULL -> {
                boolean aerialHeavy=(incarnation||t.form.equals("epiphany"))&&t.context.airborne();
                t.payStamina(aerialHeavy?30:25);t.action(aerialHeavy?"incarnation_heavy":"heavy");t.clear("charging");
            }
            case AIR -> { t.payStamina(30); t.action(incarnation||t.form.equals("epiphany") ? "incarnation_heavy" : "air"); }
            case DODGE_COUNTER -> { t.action(incarnation ? "incarnation_counter" : "dodge_counter"); if(!incarnation)t.combo = 3; }
            case SKILL -> {
                if (t.form.equals("epiphany") && t.has("epiphany")) {
                    t.effect(Kind.BUFF_SELF, "jinhsi_epiphany_additive_multiplier", .4454 * t.get("incandescence"), 1,
                            Map.of("action_epiphany_final", 1.0,"actionScoped",1d));
                    if (t.original.chain() >= 1) t.effect(Kind.BUFF_SELF, "jinhsi_epiphany_damage_bonus", .2 * t.get("incarnation_stacks"), 1,Map.of("actionScoped",1d));
                    t.action("epiphany_rays"); t.action("epiphany_final");
                    t.set("incandescence", 0); t.set("incarnation_stacks", 0); t.clear("epiphany");
                    form(t, "normal", 0); t.effect(Kind.GAIN_CONCERTO, "epiphany", 20, 0);
                    if (!t.has("unison_cooldown")) { t.timer("unison", 100); t.timer("unison_cooldown", 500); }
                } else if (incarnation) {
                    t.requireCooldown("crescent"); t.timer("crescent", 200); t.action("skill_crescent");
                    t.effect(Kind.GAIN_CONCERTO, "crescent", 8, 0);
                    if (t.original.chain() >= 1) { t.add("incarnation_stacks", 1); t.timer("incarnation_stacks", 120); }
                } else if (t.has("overflowing")) {
                    t.requireCooldown("overflowing_cooldown"); t.timer("overflowing_cooldown", 240);
                    t.action("skill_overflowing"); t.clear("overflowing"); form(t, "incarnation", 200); t.timer("incarnation", 200);
                } else { t.requireCooldown("skill"); t.timer("skill", 60); t.action("skill"); }
            }
            case LIBERATION -> { t.requireCooldown("liberation"); t.payEnergy(150); t.timer("liberation", 480); t.action("liberation"); }
            case INTRO -> { t.action("intro"); if (!incarnation) t.timer("overflowing", 100); }
            case OUTRO -> { t.timer("outro_incandescence", 400); t.effect(Kind.BUFF_TEAM, "jinhsi_incandescence_icd", 1, 400); }
            default -> throw new Rejected("unsupported_input_in_form");
        }
    }

    private static void camellya(Transition t, Input input) {
        boolean bloom = t.form.equals("bloom");
        switch (input) {
            case BASIC -> { if (bloom && t.context.airborne()) t.payStamina(5); combo(t, bloom ? "bloom_" : "", bloom ? 4 : 5); }
            case BASIC_HOLD -> {
                require(t.combo == 4,"held_combo_requires_third_basic_or_pruning");
                if(bloom && t.context.airborne())t.payStamina(5);
                t.action(bloom?"bloom_spinning":"basic_04");t.combo=4;t.timer("combo_window",60);
            }
            case HEAVY_START -> t.timer("charging", 1);
            case HEAVY_RELEASE, HEAVY_FULL -> { if(!bloom || t.context.airborne())t.payStamina(bloom ? 5 : 25); t.action(bloom ? "bloom_spinning" : "heavy"); t.combo = 4; }
            case AIR -> { t.payStamina(30); t.action("air"); }
            case DODGE_COUNTER -> { t.action(bloom ? "bloom_dodge_counter" : "dodge_counter"); t.combo = 3; }
            case SKILL -> {
                if (t.original.chain() >= 6 && t.has("immortal_window") && t.context.concerto() >= 100 && !t.has("immortal_blossom")) {
                    t.effect(Kind.PAY_CONCERTO, "immortal_blossom", 50, 0);
                    t.action("immortal_blossom"); t.timer("immortal_blossom", 500);
                    t.timer("budding", 300); t.add("pistils", 50); t.set("buds", 0); t.clear("buds");
                    // C6 replaces this newly entered state's rate increase with 250%, not +250% on top of C6's previous state.
                    t.effect(Kind.BUFF_SELF, "camellya_budding_multiplier", 2.5, 300);
                    t.effect(Kind.BUFF_SELF, "camellya_energy_gain_disabled", 1, 300);
                } else if (t.context.concerto() >= 100 && !t.has("ephemeral")) {
                    t.effect(Kind.PAY_CONCERTO, "ephemeral", 70, 0); t.action("ephemeral"); t.timer("ephemeral", 500); t.timer("camellya_outro_enhanced",1);
                    if (t.original.chain() >= 6) t.timer("immortal_window", 300);
                    t.timer("budding", 300); t.set("pistils", 100);
                    t.effect(Kind.BUFF_SELF, "camellya_budding_multiplier", .5 + .05 * t.get("buds") + (t.original.chain() >= 6 ? 1.5 : 0), 300);
                    t.set("buds", 0); t.clear("buds"); t.effect(Kind.BUFF_SELF, "camellya_energy_gain_disabled", 1, 300);
                } else {
                    t.requireCooldown("skill"); t.timer("skill", 80);
                    t.action(bloom ? "bloom_dark_heart" : "skill"); form(t, bloom ? "normal" : "bloom", 0);
                }
            }
            case LIBERATION -> { t.requireCooldown("liberation"); t.payEnergy(125); t.timer("liberation", 500); t.action("liberation"); }
            case INTRO -> { t.action("intro"); t.set("pistils", 100); t.effect(Kind.GAIN_CONCERTO, "intro", 10, 0); }
            case JUMP -> { require(bloom, "not_blooming"); t.payStamina(5); t.action("bloom_twirl"); form(t, "normal", 0); }
            case OUTRO -> OutroRules.camellya(t);
            default -> throw new Rejected("unsupported_input_in_form");
        }
    }

    private static void yinlin(Transition t, Input input) {
        switch (input) {
            case BASIC -> combo(t, "", 4);
            case HEAVY_START -> t.timer("charging", 1);
            case HEAVY_RELEASE, HEAVY_FULL -> {
                t.payStamina(25);
                if (t.get("judgment") >= 100) { t.set("judgment", 0); t.action("chameleon"); }
                else t.action("heavy");
            }
            case AIR -> { t.payStamina(30); t.action("air"); }
            case DODGE_COUNTER -> t.action("dodge_counter");
            case SKILL -> {
                if (t.has("magnetic")) {
                    t.action("skill_blast"); t.clear("magnetic"); t.clear("magnetic_charges"); t.timer("skill", 240);
                } else {
                    t.requireCooldown("skill"); t.action("skill"); t.timer("magnetic", 200); t.timer("magnetic_charges", 4);
                }
            }
            case LIBERATION -> { t.requireCooldown("liberation"); t.payEnergy(125); t.timer("liberation", 320); t.action("liberation"); }
            case INTRO -> { t.action("intro"); t.effect(Kind.GAIN_CONCERTO, "intro", 10, 0); }
            case OUTRO -> t.effect(Kind.BUFF_NEXT, "yinlin_outro", .20, 280, Map.of("electroAmplification", .20, "liberationAmplification", .25, "endsOnSwitch", 1.0));
            default -> throw new Rejected("unsupported_input_in_form");
        }
    }

    private static void rover(Transition t, Input input) {
        switch (input) {
            case BASIC -> {
                if(t.has("rover_land_window")&&!t.context.airborne()) {
                    t.clear("rover_land_window");
                    if(t.get("wind")>=120){t.add("wind",-60);t.action("unbound_wind_01");temporary(t,"unbound_followup");t.effect(Kind.GAIN_CONCERTO,"unbound_wind",20,0);}
                    else {t.combo=4;combo(t,"",4);}
                } else if (t.has("wind_air")) {
                    int part = t.time("wind_air_part") == 2 ? 2 : 1; t.action("wind_air_0" + part);
                    if(part==1&&!t.context.airborne())t.effect(Kind.MOVE_SPECIAL,"rover_updraft",1,0);
                    if (part == 1) t.timer("wind_air_part", 2); else { t.clear("wind_air"); t.clear("wind_air_part"); }
                } else {
                    if(t.has("rover_heavy_basic_window")){t.combo=3;t.clear("rover_heavy_basic_window");}
                    int part=t.combo;combo(t,"",4);if(part==3)temporary(t,"rover_strangle_window");
                }
            }
            case HEAVY_START -> {t.timer("charging",1);if(t.has("rover_strangle_window"))t.timer("rover_strangle_charging",1);else t.clear("rover_strangle_charging");}
            case HEAVY_RELEASE, HEAVY_FULL -> {
                if(t.context.airborne())RoverInputRules.plunge(t);
                else if(t.has("rover_strangle_charging")||t.has("rover_strangle_window")){t.payStamina(25);t.action("heavy_strangle");temporary(t,"wind_air");t.clear("rover_strangle_window");t.clear("rover_heavy_basic_window");}
                else {t.payStamina(25);t.action("heavy");t.combo=3;temporary(t,"rover_heavy_basic_window");temporary(t,"rover_strangle_window");t.clear("wind_air");t.clear("wind_air_part");}
                t.clear("charging");t.clear("rover_strangle_charging");
            }
            case AIR -> {
                if (t.has("wind_air")) {
                    int part = t.time("wind_air_part") == 2 ? 2 : 1; t.action("wind_air_0" + part);
                    if (part == 1) t.timer("wind_air_part", 2); else { t.clear("wind_air"); t.clear("wind_air_part"); }
                } else RoverInputRules.plunge(t);
            }
            case DODGE_COUNTER -> { t.action("dodge_counter"); t.combo = 3; temporary(t,"rover_strangle_window"); }
            case SKILL -> {
                if (t.has("unbound_followup")) {
                    require(t.get("wind") >= 60, "wind_missing"); t.add("wind", -60); t.action("unbound_wind_02"); t.clear("unbound_followup");
                    t.effect(Kind.GAIN_CONCERTO, "unbound_wind", 20, 0);
                } else if (t.get("wind") >= 120) {
                    t.add("wind", -60); t.action("unbound_wind_01"); temporary(t, "unbound_followup");
                    t.effect(Kind.GAIN_CONCERTO, "unbound_wind", 20, 0);
                } else if (t.context.airborne()) {
                    t.requireCooldown("sky_skill"); t.timer("sky_skill", 240); t.action("skill_sky");
                } else {
                    t.requireCooldown("skill"); t.timer("skill", 60); t.action("skill"); temporary(t, "wind_air");
                    t.effect(Kind.MOVE_SPECIAL, "rover_updraft", 1, 0);
                }
            }
            case LIBERATION -> { t.requireCooldown("liberation"); t.payEnergy(150); t.timer("liberation", 480); t.action("liberation"); t.effect(Kind.HEAL_TEAM,"rover_liberation",(2090+.77*t.context.attack())*1.20,0); }
            case INTRO -> { t.action("intro"); t.add("wind", 20); temporary(t, "wind_air"); t.effect(Kind.GAIN_CONCERTO, "intro", 10, 0); }
            case OUTRO -> t.effect(Kind.BUFF_TEAM, "rover_aero_erosion_cap", 3, 600, Map.of("onHitDurationTicks", 200.0));
            default -> throw new Rejected("unsupported_input_in_form");
        }
    }

    private static void cartethyia(Transition t, Input input) {
        boolean fleur = t.form.equals("fleurdelys");
        if((input==Input.BASIC||input==Input.AIR)&&CartethyiaAttackRules.followup(t))return;
        switch (input) {
            case BASIC -> {
                int stage=t.combo;
                combo(t, fleur ? "fleurdelys_" : "", fleur ? 5 : 4);
                if(!fleur&&stage==4)sword(t,"divine_sword");
                if(fleur&&stage==4&&t.has("human_right")&&t.has("manifestation")) {
                    t.effect(Kind.CONTROL_AREA,"cartethyia_human_stasis",1,20,Map.of("radius",6.0));
                }
            }
            case HEAVY_START -> t.timer("charging", 1);
            case HEAVY_RELEASE, HEAVY_FULL -> {
                t.clear("charging");
                if(fleur&&t.context.airborne()) {t.payStamina(30);t.action("fleurdelys_air_03");t.timer("air_stage",1);t.combo=3;}
                else {t.payStamina(20);t.action(fleur?"fleurdelys_heavy":"heavy");}
                if(!fleur)sword(t,"outsider_sword");
            }
            case AIR -> {
                if (fleur) {
                    int segment = Math.clamp(t.time("air_stage"), 1, 3); t.payStamina(segment == 3 ? 30 : 5);
                    t.action("fleurdelys_air_0" + segment); t.timer("air_stage", segment % 3 + 1);
                    if (segment == 3) t.combo = 3;
                } else {
                    t.payStamina(30); cartethyiaPlunge(t);
                }
            }
            case DODGE_COUNTER -> { t.action(fleur ? "fleurdelys_counter" : "dodge_counter"); t.combo = fleur ? 4 : 3; }
            case SKILL -> {
                if (fleur && t.has("fleur_followup")) {
                    t.action("fleurdelys_skill_followup"); t.clear("fleur_followup"); t.timer("fleur_skill", 280); t.combo = 3;
                    t.effect(Kind.GAIN_CONCERTO, "fleurdelys_skill", 10, 0);
                } else if (fleur) {
                    t.requireCooldown("fleur_skill"); t.action("fleurdelys_skill"); temporary(t, "fleur_followup");
                    t.effect(Kind.GAIN_CONCERTO, "fleurdelys_skill", 10, 0);
                } else { t.requireCooldown("skill"); t.timer("skill", 280); t.action("skill"); sword(t,"human_sword"); }
                if (fleur && t.context.airborne()) t.timer("air_stage", 1);
            }
            case LIBERATION -> {
                if (fleur && t.get("resolve") >= 120) {
                    t.requireCooldown("finale"); t.timer("finale", 500); t.action("liberation_finale"); t.set("resolve", 0); t.clear("manifestation");
                    clearCartethyiaRights(t);
                    t.effect(Kind.HEAL_ACTIVE, "cartethyia_finale", t.context.maxHealthNative() * .5, 0); form(t, "cartethyia", 0);
                    t.effect(Kind.GAIN_CONCERTO, "liberation_finale", 20, 0);
                } else if (t.has("manifestation")) {
                    t.requireCooldown("form_switch"); t.timer("form_switch", 30); form(t, fleur ? "cartethyia" : "fleurdelys", t.time("manifestation"));
                    // Source node 3 explicitly performs an attack on each return,
                    // rather than only selecting the next combo segment. Automatic
                    // attacks retain LIBERATION's existing cost; extra plunge stamina
                    // is an explicit MC adaptation pending original-input measurement.
                    if (fleur) {
                        t.clear("air_stage");
                        if (t.context.airborne()) cartethyiaPlunge(t);
                        else { t.action("basic_02"); t.combo = 3; }
                    } else if (t.context.airborne()) {
                        t.action("fleurdelys_air_01"); t.timer("air_stage", 2);
                    } else { t.action("fleurdelys_basic_02"); t.combo = 3; }
                } else if(fleur) {
                    // User-confirmed rule: expired manifestation ends free round-trips, not the form.
                    // R now returns to small; it must not charge another transformation into the same large form.
                    t.requireCooldown("form_switch");t.timer("form_switch",30);t.clear("air_stage");clearCartethyiaRights(t);
                    form(t,"cartethyia",0);
                    if(t.context.airborne())cartethyiaPlunge(t);else {t.action("basic_02");t.combo=3;}
                } else {
                    t.requireCooldown("liberation"); t.payEnergy(125); t.timer("liberation", 500); t.timer("manifestation", 240); t.set("resolve", 0);
                    double remaining=t.original.chain()>=5?.75:.5;
                    if (t.context.currentHealthNative() > t.context.maxHealthNative() * remaining) t.effect(Kind.SET_HEALTH_FRACTION, "cartethyia_manifestation", remaining, 0);
                    form(t, "fleurdelys", 240);
                    t.effect(Kind.GAIN_CONCERTO, "liberation_transform", 20, 0);
                }
            }
            case INTRO -> { t.action(fleur ? "fleurdelys_intro" : "intro"); t.combo=2; }
            case OUTRO -> t.effect(Kind.BUFF_TEAM, "cartethyia_outro", .175, 400, Map.of("againstAnomaly", 1.0, "excludeOwner", 1.0));
            case JUMP -> {
                require(fleur && !t.context.airborne(), "uppercut_requires_grounded_fleurdelys");
                t.action("fleurdelys_uppercut");
                t.effect(Kind.MOVE_SPECIAL,"cartethyia_uppercut",1,0);
            }
            default -> throw new Rejected("unsupported_input_in_form");
        }
    }

    /** Both a normal plunge and an automatic small-form return recover the same live shadow types. */
    private static void cartethyiaPlunge(Transition t) {
        int swords = 0;
        var recovered=List.of("human_sword", "divine_sword", "outsider_sword").stream().filter(key->t.has(key)&&(!t.has("cart_shadow_geometry")||t.has("range_"+key))).toList();
        swords=recovered.size();
        t.action(swords == 0 ? "air" : "air_" + List.of("", "one", "two", "three").get(swords) + "_sword" + (swords > 1 ? "s" : ""));
        t.timer(CartethyiaAttackRules.SMALL_PLUNGE_LINK,60);
        if (recovered.contains("human_sword")) t.timer("human_right", 1);
        if (recovered.contains("divine_sword")) t.timer("divine_intent", 1);
        if (recovered.contains("outsider_sword")) t.timer("outsider_power", 1);
        if (t.original.chain() >= 2) t.timer("skill", Math.max(0, t.time("skill") - swords * 20));
        t.set("swords", Math.max(0,t.get("swords")-swords));
        for(String key:recovered){t.clear(key);t.clear("range_"+key);t.effect(Kind.SWORD_COLLECT,key,1,0);}
    }

    static void hit(Transition t, String action) {
        if(t.original.characterId().equals("zani"))ZaniMechanics.hit(t,action);
        CharacterLifecycleRules.hit(t,action);
        NewAeroMechanics.hit(t,action);
        NewThreeMechanics.hit(t,action);
        NewTideMechanics.hit(t,action);
        NewSixMechanics.hit(t,action);
        switch (t.original.characterId()) {
            case "jinhsi" -> {
                if (!t.has("incandescence_spectro")) { t.add("incandescence", 1); t.timer("incandescence_spectro", t.has("outro_incandescence") ? 20 : 60); }
            }
            case "yinlin" -> {
                if (action.startsWith("basic") || List.of("heavy","air","dodge_counter","liberation","intro").contains(action)) mark(t, "yinlin_sin", 1, 0);
                if (action.equals("chameleon")) mark(t, "yinlin_punishment", 1, 360);
                if (t.has("magnetic") && t.time("magnetic_charges") > 0 && (action.startsWith("basic") || action.equals("dodge_counter"))) {
                    t.timer("magnetic_charges", t.time("magnetic_charges") - 1); t.action("magnetic_blast");
                }
            }
            case "aero_rover" -> {
                if (action.equals("wind_air_01") || action.equals("wind_air_02")) { t.add("wind", 25); healTeam(t, "wind_air", 627, .231); }
                if (List.of("basic_03", "basic_04", "dodge_counter").contains(action)) t.add("wind", 10);
                if (action.equals("skill_sky")) mark(t, "convert_anomalies_to_aero", 1, 0);
            }
            case "cartethyia" -> {
                // Source shadows are created by the accepted casts, independent of a damage hit.
                if (action.equals("skill")) mark(t, "aero_erosion", 2, 0);
                if (action.equals("basic_04")) mark(t, "aero_erosion", 1, 0);
                if (action.equals("heavy")) mark(t, "aero_erosion", 2, 0);
                if (t.original.chain() >= 3 && List.of("fleurdelys_basic_05", "fleurdelys_air_02", "fleurdelys_heavy_enhanced", "fleurdelys_skill_followup").contains(action)) {
                    mark(t, "aero_erosion", 2, 0);
                }
                // Finale's per-target damage snapshot and post-hit clear/C6 cap are hosted by ActionRuntime.
            }
            default -> CharacterBranches.hit(t, action);
        }
    }
    private static void sword(Transition t, String sword) {
        if (!t.has(sword)) t.add("swords", 1); t.timer(sword, 400);
        t.effect(Kind.SWORD_SHADOW,sword,1,400);
    }
    private static void clearCartethyiaRights(Transition t) {
        for(String key:List.of("human_right","divine_intent","outsider_power"))t.clear(key);
    }
    static void tick(Transition t) {
        NewFiveMechanics.tick(t);
        NewAeroMechanics.tick(t);
        NewThreeMechanics.tick(t);
        NewTideMechanics.tick(t);
        NewSixMechanics.tick(t);
        String who = t.original.characterId();
        if (who.equals("jinhsi")) {
            if (t.form.equals("incarnation") && !t.has("incarnation")) form(t, "normal", 0);
            if (t.form.equals("epiphany") && !t.has("epiphany")) form(t, "normal", 0);
            if (!t.has("incarnation_stacks")) t.set("incarnation_stacks", 0);
            if (t.context.inCombat()) t.clear("out_of_combat_ticks");
            else if (t.original.chain() >= 2) {
                t.timer("out_of_combat_ticks", Math.min(81, t.time("out_of_combat_ticks") + 1));
                // Source requires MORE than four seconds out of combat, then at most once every four seconds.
                if (t.time("out_of_combat_ticks") > 80 && !t.has("rest_incandescence")) {
                    t.set("incandescence", 50); t.timer("rest_incandescence", 80);
                }
            }
        }
        if (who.equals("camellya")) {
            if(!t.has("buds"))t.set("buds",0);
            if (t.form.equals("bloom") && t.context.active() && t.context.airborne()) { if (t.context.stamina() < .25) form(t, "normal", 0); else t.effect(Kind.PAY_STAMINA, "bloom_air", .25, 0); }
            if (t.get("pistils") == 0 && t.has("budding")) {
                t.clear("budding"); t.effect(Kind.BUFF_SELF, "camellya_budding_multiplier", 0, 0);
                t.effect(Kind.BUFF_SELF, "camellya_energy_gain_disabled", 0, 0);
            }
        }
        if (who.equals("yinlin") && t.original.timer("magnetic") == 1) { t.timer("skill", 240); t.clear("magnetic_charges"); }
        if (who.equals("cartethyia")) {
            // Manifestation countdown pauses while borrowing the small form.
            if (t.form.equals("cartethyia") && t.original.timer("manifestation") > 0) t.timer("manifestation", t.original.timer("manifestation"));
            if (t.form.equals("fleurdelys") && !t.has("manifestation")) clearCartethyiaRights(t);
            if (t.original.timer("fleur_followup") == 1) t.timer("fleur_skill", 280);
            int count = 0; for (String sword : List.of("human_sword", "divine_sword", "outsider_sword")) if (t.has(sword)) count++;
            t.set("swords", count);
        }
        if(who.equals("aero_rover"))RoverInputRules.tick(t);
        CharacterBranches.tick(t);
    }
    static void switchOut(Transition t) {
        NewFiveMechanics.switchOut(t);
        NewAeroMechanics.switchOut(t);
        NewThreeMechanics.switchOut(t);
        NewTideMechanics.switchOut(t);
        if(java.util.Set.of("changli","carlotta","iuno","lupa","denia","qingxiao").contains(t.original.characterId()))NewSixMechanics.switchOut(t);
        String who = t.original.characterId();
        if (who.equals("yinlin") && t.has("magnetic")) { t.clear("magnetic"); t.clear("magnetic_charges"); t.timer("skill", 240); }
        if (who.equals("camellya")) {
            // Switching ends Budding's amplification, not the independent Blossom stance.
            // Its explicit exits are Floral Ravage, Vining Ronde, Levitator and air exhaustion.
            t.clear("budding");
            t.effect(Kind.BUFF_SELF, "camellya_budding_multiplier", 0, 0);
            t.effect(Kind.BUFF_SELF, "camellya_energy_gain_disabled", 0, 0);
            // The C6 eligibility window is measured from Ephemeral, independently of whether budding ended early.
        }
        if (who.equals("aero_rover") && t.has("unbound_followup") && t.get("wind") >= 60) {
            t.add("wind", -60); t.action("unbound_wind_02"); t.clear("unbound_followup"); t.effect(Kind.GAIN_CONCERTO, "unbound_wind", 20, 0);
        }
        if (who.equals("cartethyia") && t.has("fleur_followup")) { t.clear("fleur_followup"); t.timer("fleur_skill", 280); }
        if(who.equals("aero_rover"))RoverInputRules.clear(t);
        CharacterBranches.switchOut(t);
    }
}
