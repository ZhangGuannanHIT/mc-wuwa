package cn.zgnhit.mcwuwa.client;

import cn.zgnhit.mcwuwa.McWuwa;
import cn.zgnhit.mcwuwa.client.presentation.PresentationClient;
import cn.zgnhit.mcwuwa.platform.ModAttachments;
import cn.zgnhit.mcwuwa.platform.Network;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.extensions.IKeyMappingExtension;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;


/** Client input/feedback only; all game effects remain server-authoritative. */
@Mod(value = "mc_wuwa", dist = Dist.CLIENT)
public final class CombatClient {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("mc_wuwa", "combat"));
    private static final KeyMapping SKILL = key("skill", InputConstants.KEY_G);
    private static final KeyMapping LIBERATION = key("liberation", InputConstants.KEY_R);
    private static final KeyMapping ECHO = key("echo", InputConstants.KEY_Q);
    private static final KeyMapping NOTE_GREEN=key("note_green",InputConstants.KEY_Z);
    private static final KeyMapping NOTE_YELLOW=key("note_yellow",InputConstants.KEY_X);
    private static final KeyMapping TUNE = key("tune_break", InputConstants.KEY_B);
    private static final KeyMapping CREATIVE_RECHARGE=key("creative_recharge",InputConstants.KEY_V);
    private static final KeyMapping TRAVEL=key("travel",InputConstants.KEY_J);
    private static final KeyMapping[] SWITCH = { key("switch_1", InputConstants.KEY_1), key("switch_2", InputConstants.KEY_2), key("switch_3", InputConstants.KEY_3) };
    private static JsonObject view = new JsonObject();
    private static long sequence;
    /** Reserve from the same monotonic stream used by ordinary client commands. */
    static long nextInputSequence(){return ++sequence;}
    private static boolean attackDown, sprintDown, aimDown, heavyStarted, quickAttackPending;
    private static boolean skillDown, skillHeld, echoDown, echoHeld, jumpDown;
    private static boolean bikeSpaceDown,bikeSkillDown,wasRidingBike;
    private static final cn.zgnhit.mcwuwa.core.SixJumpGesture SIX_JUMP_GESTURE=new cn.zgnhit.mcwuwa.core.SixJumpGesture();
    private static int skillTicks, echoTicks;
    private static final cn.zgnhit.mcwuwa.core.LiberationGesture LIBERATION_GESTURE=new cn.zgnhit.mcwuwa.core.LiberationGesture();
    private static int attackTicks, previousSelected;
    private static boolean initialized;
    private static boolean screenWasOpen;
    public CombatClient(IEventBus modBus, ModContainer container) {
        if (initialized) return;
        initialized = true;
        container.registerConfig(ModConfig.Type.CLIENT, CombatHud.CONFIG);
        container.registerConfig(ModConfig.Type.CLIENT,cn.zgnhit.mcwuwa.platform.ClientVisualConfig.SPEC,"mc_wuwa-visuals-client.toml");
        container.registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
            (net.neoforged.neoforge.client.gui.IConfigScreenFactory)(mod,parent)->new VisualSettingsScreen(parent));
        modBus.addListener(CombatClient::registerKeys);
        modBus.addListener(IntegrationVisualClient::setup);
        NeoForge.EVENT_BUS.addListener(CombatClient::tick);
        NeoForge.EVENT_BUS.addListener(CombatClient::jumpBeforeMovement);
        NeoForge.EVENT_BUS.addListener(CombatClient::interaction);
        NeoForge.EVENT_BUS.addListener(CombatClient::mouseAttackEdge);
        NeoForge.EVENT_BUS.addListener(CombatClient::keyboardAttackEdge);
        NeoForge.EVENT_BUS.addListener(CombatClient::logout);
        NeoForge.EVENT_BUS.addListener(IntegrationVisualClient::guiLayer);
        CombatHud.initialize();InventoryControls.initialize();RoleSkinRegistry.initialize(modBus);PresentationClient.initialize(modBus);TraversalPrediction.initialize();EnemyHazardPauseClient.initialize();MotorcycleSounds.initialize();CiacconaRecitalSound.initialize();
        Network.stateReceiver = state -> Minecraft.getInstance().execute(() -> receive(state));
        Network.creativeCursorReceiver=reply->Minecraft.getInstance().execute(()->{
            var game=Minecraft.getInstance();
            if(game.player!=null&&game.player.getAbilities().instabuild
                &&(game.player.containerMenu==game.player.inventoryMenu||game.player.containerMenu instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen.ItemPickerMenu)
                &&InventoryControls.ownsCreativeCursor(game.gui.screen())&&reply.sequence()==creativeCursorRevision)
                game.player.inventoryMenu.setCarried(reply.carried().copy());
        });
        Network.impactReceiver=impact->Minecraft.getInstance().execute(()->CombatEffects.accept(impact));
        Network.appearanceReceiver=value->Minecraft.getInstance().execute(()->RoleSkinRegistry.accept(value));
        Network.hazardPauseReceiver=value->Minecraft.getInstance().execute(()->EnemyHazardPauseClient.accept(value));
        Network.uiFeedbackReceiver=feedback->Minecraft.getInstance().execute(()->InventoryControls.feedback(feedback.slot(),feedback.text()));
        Network.visualReceiver = visual -> Minecraft.getInstance().execute(() -> {
            var level = Minecraft.getInstance().level; if (level == null) return;
            Entity actor = level.getEntity(visual.entityId()); if (actor == null) return;
            if(actor==Minecraft.getInstance().player&&!visual.actionId().equals("@form")&&visual.durationTicks()>0)
                AbilityFeedbackClient.accepted(visual.actionId(),level.getGameTime());
            CombatEffects.acceptAction(visual,actor);
            if (visual.actionId().equals("@form")) {
                PresentationClient.acceptForm(actor.getUUID(), visual.entityId(), visual.transform(), visual.startTick(), visual.sequence()); return;
            }
            if (visual.durationTicks() <= 0 || visual.actionId().isBlank())
                PresentationClient.end(actor.getUUID(), visual.instanceId(), visual.sequence());
            else PresentationClient.accept(actor.getUUID(), visual.entityId(), visual.instanceId(), visual.actionId(),
                visual.startTick(), visual.durationTicks(), visual.sequence(), visual.transform());
        });
    }
    private static KeyMapping key(String name, int code) { return new KeyMapping("key.mc_wuwa." + name, InputConstants.Type.KEYBOARD, code, CATEGORY); }
    private static void registerKeys(RegisterKeyMappingsEvent event) {
        for (KeyMapping mapping : List.of(SKILL, LIBERATION, ECHO, TUNE, NOTE_GREEN, NOTE_YELLOW, CREATIVE_RECHARGE, TRAVEL, SWITCH[0], SWITCH[1], SWITCH[2])) {
            ((IKeyMappingExtension) mapping).setKeyConflictContext(KeyConflictContext.IN_GAME); event.register(mapping);
        }
        for (KeyMapping mapping : SWITCH) ((IKeyMappingExtension) mapping).setKeyModifierAndCode(KeyModifier.ALT, mapping.getDefaultKey());
    }
    public static String noteKey(boolean yellow){return (yellow?NOTE_YELLOW:NOTE_GREEN).getTranslatedKeyMessage().getString();}
    public static JsonObject view() { return view; }
    public static JsonObject roleView(int slot) {
        if (slot < 0 || slot > 2 || !view.has("roles") || view.getAsJsonArray("roles").size() <= slot) return new JsonObject();
        return view.getAsJsonArray("roles").get(slot).getAsJsonObject();
    }
    public static int activeSlot() { return view.has("active") ? view.get("active").getAsInt() : -1; }
    static String abilityKey(String ability) {
        if(ability.equals("basic"))return Minecraft.getInstance().options.keyAttack.getTranslatedKeyMessage().getString();
        return (switch (ability) {
            case "skill" -> SKILL; case "liberation" -> LIBERATION; case "echo" -> ECHO; case "tune" -> TUNE;
            default -> throw new IllegalArgumentException("Unknown HUD ability: " + ability);
        }).getTranslatedKeyMessage().getString();
    }
    static String travelKey(){return TRAVEL.getTranslatedKeyMessage().getString();}
    static String switchKey(int slot) { return SWITCH[slot].getTranslatedKeyMessage().getString(); }
    public static boolean hasTeam() {
        Minecraft game = Minecraft.getInstance(); return game.player != null && ModAttachments.team(game.player).hasTeam();
    }
    public static boolean wuwaMode() {
        var player = Minecraft.getInstance().player;
        return player != null && hasTeam() && player.getInventory().getSelectedSlot() == 0;
    }
    /** Optional Iris seam: claim only the same physical key and modifier while combat owns input. */
    public static boolean claimsLiberationKey(KeyMapping other) {
        var game=Minecraft.getInstance();var actor=game.player;
        return actor!=null&&actor.isAlive()&&!actor.isSpectator()&&game.gui.screen()==null&&game.gui.overlay()==null&&wuwaMode()
            &&other.saveString().equals(LIBERATION.saveString())
            &&((IKeyMappingExtension)other).getKeyModifier()==((IKeyMappingExtension)LIBERATION).getKeyModifier();
    }
    private static void receive(Network.State state) {
        var player = Minecraft.getInstance().player; if (player == null) return;
        try {
            JsonObject parsed = JsonParser.parseString(state.json()).getAsJsonObject();
            if (!parsed.has("schema") || parsed.get("schema").getAsInt() != 1 || !parsed.has("roles") || parsed.getAsJsonArray("roles").size() != 3) return;
            int active = parsed.get("active").getAsInt(); if (active < -1 || active > 2 || state.weapons().size() != 3) return;
            view = parsed; ModAttachments.team(player).acceptClientView(state.weapons(), active);
            AbilityFeedbackClient.update(roleView(active),parsed.has("tick")?parsed.get("tick").getAsLong():0);
        } catch (RuntimeException malformed) { McWuwa.LOGGER.warn("Ignoring malformed combat view", malformed); }
    }
    public static void sendCreativeTeamSlot(int slot,net.minecraft.world.item.ItemStack carried) {
        creativeCursorRevision=++sequence;
        ClientPacketDistributor.sendToServer(new Network.CreativeTeamSlot(sequence,slot,carried));
    }
    private static long creativeCursorRevision=-1;
    public static void createCreativeEcho(String species){creativeCursorRevision=++sequence;ClientPacketDistributor.sendToServer(new Network.CreativeEchoCreate(sequence,species));}
    /** Called after every native creative click, including catalogue destruction,
     * which vanilla never communicates to the server cursor. */
    public static void creativeCursorChanged(net.minecraft.world.item.ItemStack carried){
        var game=Minecraft.getInstance();if(game.player==null||!game.player.getAbilities().instabuild)return;
        var species=cn.zgnhit.mcwuwa.platform.EchoData.templateSpecies(carried);
        if(species.isPresent()){createCreativeEcho(species.get().toString());return;}
        creativeCursorRevision=++sequence;
        ClientPacketDistributor.sendToServer(new Network.CreativeCursor(sequence,carried.copy()));
    }
    public static void swapEcho(int slot){
        var game=Minecraft.getInstance();if(game.player==null)return;
        if(game.player.getAbilities().instabuild){creativeCursorRevision=++sequence;ClientPacketDistributor.sendToServer(new Network.CreativeEchoSlot(sequence,slot,game.player.inventoryMenu.getCarried().copy()));}
        else send("ECHO_SWAP",slot);
    }
    public static void send(String operation, int argument) {
        var game = Minecraft.getInstance(); if (game.player == null || game.getConnection() == null) return;
        float strafe = (game.options.keyLeft.isDown() ? 1 : 0) - (game.options.keyRight.isDown() ? 1 : 0);
        float forward = (game.options.keyUp.isDown() ? 1 : 0) - (game.options.keyDown.isDown() ? 1 : 0);
        ClientPacketDistributor.sendToServer(new Network.Command(++sequence, operation, argument, strafe, forward));
    }
    /** Ground-qualified jump skills must precede vanilla's movement packet.
     * Post-tick dispatch observes the already airborne body and loses uppercuts. */
    private static void jumpBeforeMovement(ClientTickEvent.Pre event){
        var game=Minecraft.getInstance();
        if(game.player==null||game.level==null||game.gui.screen()!=null||game.gui.overlay()!=null||!wuwaMode()
            ||game.player.getVehicle() instanceof cn.zgnhit.mcwuwa.platform.entity.SurveyMotorcycleEntity)return;
        boolean jump=game.options.keyJump.isDown();var role=roleView(activeSlot());
        String weapon=role.has("weapon")?role.get("weapon").getAsString():"";
        String form=role.has("form")?role.get("form").getAsString():"normal";
        String movement=role.has("movement")&&role.getAsJsonObject("movement").has("mode")?role.getAsJsonObject("movement").get("mode").getAsString():"";
        if(!usesSixJumpGesture(weapon,form,movement)&&jump&&!jumpDown)send("JUMP",0);
        jumpDown=jump;
    }
    private static boolean usesSixJumpGesture(String weapon,String form,String movement){
        return weapon.equals("moongazers_sigil")&&!form.equals("normal")
            ||weapon.equals("glint_of_clouds")&&movement.equals("qingxiao_sword_flight");
    }
    private static void tick(ClientTickEvent.Post event) {
        Minecraft game = Minecraft.getInstance();
        if (game.player == null || game.level == null) { resetInputs();LIBERATION_GESTURE.reset();SIX_JUMP_GESTURE.reset(); return; }
        CombatEffects.tick();
        if (hasTeam() && game.player.tickCount % 5 == 0) send("FOCUS", game.crosshairPickEntity == null ? -1 : game.crosshairPickEntity.getId());
        boolean screenOpen=game.gui.screen()!=null;
        if(screenOpen&&!screenWasOpen&&hasTeam())send("SCENE_INTERACTION",0);
        if(!screenOpen&&screenWasOpen)creativeCursorRevision=-1;
        screenWasOpen=screenOpen;
        // A resource-loading overlay suspends input but is not a world/tool
        // interaction: it must not consume a persistent traversal form.
        if(screenOpen||game.gui.overlay()!=null){cancelBikeInput();releaseInputs();discardClicks();return;}
        while(CREATIVE_RECHARGE.consumeClick())if(game.player.getAbilities().instabuild)send("CREATIVE_RECHARGE",0);
        while(TRAVEL.consumeClick())if(wuwaMode()||game.player.getVehicle() instanceof cn.zgnhit.mcwuwa.platform.entity.SurveyMotorcycleEntity)send("TRAVEL",0);
        for (int i = 0; i < SWITCH.length; i++) while (SWITCH[i].consumeClick()) {
            if (!hasTeam()) continue;
            while (game.options.keyHotbarSlots[i].consumeClick()) { }
            game.player.getInventory().setSelectedSlot(previousSelected);
            JsonObject target = roleView(i);
            if (target.has("hp") && target.get("hp").getAsDouble() <= 0) InventoryControls.offerRevive(i);
            else {cancelSixJump();send("SWITCH", i);}
        }
        boolean ridingBike=game.player.getVehicle() instanceof cn.zgnhit.mcwuwa.platform.entity.SurveyMotorcycleEntity;
        if(ridingBike){
            if(!wasRidingBike)releaseInputs();
            boolean space=game.options.keyJump.isDown(),bikeSkill=SKILL.isDown();
            boolean spaceClick=false,bikeSkillClick=false;
            while(game.options.keyJump.consumeClick())spaceClick=true;
            while(SKILL.consumeClick())bikeSkillClick=true;
            if(space!=bikeSpaceDown)send(space?"BIKE_JUMP_PRESS":"BIKE_JUMP_RELEASE",0);
            if(bikeSkill!=bikeSkillDown)send(bikeSkill?"BIKE_JUMP_PRESS":"BIKE_JUMP_RELEASE",1);
            if(spaceClick&&!space&&!bikeSpaceDown){send("BIKE_JUMP_PRESS",0);send("BIKE_JUMP_RELEASE",0);}
            if(bikeSkillClick&&!bikeSkill&&!bikeSkillDown){send("BIKE_JUMP_PRESS",1);send("BIKE_JUMP_RELEASE",1);}
            bikeSpaceDown=space;bikeSkillDown=bikeSkill;
        }else{bikeSpaceDown=bikeSkillDown=false;}
        wasRidingBike=ridingBike;
        if (wuwaMode()) {
            while(NOTE_GREEN.consumeClick())send("NOTE_GREEN",0);
            while(NOTE_YELLOW.consumeClick())send("NOTE_YELLOW",0);
            liberationInput();
            boolean skillClick = false, echoClick = false;
            while (SKILL.consumeClick()) skillClick = true;
            while (ECHO.consumeClick()) echoClick = true;
            boolean skill = !ridingBike&&SKILL.isDown();
            if (skill && !skillDown) { skillTicks = 0; skillHeld = false; }
            if (skill && ++skillTicks == 4) { skillHeld = true; send("SKILL_HOLD", 0); }
            if (!skill && (skillDown || skillClick) && !skillHeld) send("SKILL", 0);
            if(!skill&&skillDown&&skillHeld)send("SKILL_RELEASE",0);
            skillDown = skill;
            if (!skill) { skillHeld = false; skillTicks = 0; }
            boolean echo = ECHO.isDown();
            if (echo && !echoDown) { echoTicks = 0; echoHeld = false; }
            if (echo && ++echoTicks == 4) { echoHeld = true; send("ECHO_HOLD", 0); }
            if (!echo && (echoDown || echoClick)) { if (echoHeld) send("ECHO_RELEASE", 0); else send("ECHO", 0); }
            echoDown = echo;
            if (!echo) { echoHeld = false; echoTicks = 0; }
            boolean jump = !ridingBike&&game.options.keyJump.isDown();
            var jumpRole=roleView(activeSlot());
            String jumpOwner=jumpRole.has("instance")?jumpRole.get("instance").getAsString():"";
            String jumpWeapon=jumpRole.has("weapon")?jumpRole.get("weapon").getAsString():"";
            String jumpForm=jumpRole.has("form")?jumpRole.get("form").getAsString():"normal";
            String movement=jumpRole.has("movement")&&jumpRole.getAsJsonObject("movement").has("mode")?jumpRole.getAsJsonObject("movement").get("mode").getAsString():"";
            boolean specialJump=!ridingBike&&usesSixJumpGesture(jumpWeapon,jumpForm,movement);
            for(String operation:SIX_JUMP_GESTURE.poll(jumpOwner,specialJump,jump))send(operation,0);
            // Ordinary jump edges were sent before vanilla movement in Pre.
            while (TUNE.consumeClick()) send("TUNE_BREAK", 0);
            boolean sprint = game.options.keySprint.isDown();
            if (sprint && !sprintDown) send("DODGE_PRESS", 0);
            if (!sprint && sprintDown) send("DODGE_RELEASE", 0);
            sprintDown = sprint;
            boolean aiming = game.options.keyUse.isDown();
            if (aiming && !aimDown) send("AIM_PRESS", 0);
            if (!aiming && aimDown) send("AIM_RELEASE", 0);
            aimDown = aiming;
            boolean attack = game.options.keyAttack.isDown();
            var attackRole=roleView(activeSlot());
            boolean sawInput=attackRole.has("weapon")&&McWuwa.catalog().requireByWeapon(attackRole.get("weapon").getAsString()).characterId().equals("chisa")
                &&attackRole.has("form")&&attackRole.get("form").getAsString().equals("saw");
            if (attack && !attackDown) { attackTicks = 0; heavyStarted = false;if(sawInput)send("BASIC",0); }
            if (attack) {
                attackTicks++;
                // Four ticks distinguish a click from a held input; it is a UI threshold, not a fabricated skill charge time.
                if (attackTicks == 4) { heavyStarted = true; send("HEAVY_START", 0); }
            }
            if (!attack && (attackDown || quickAttackPending)) {
                if(sawInput){if(!attackDown&&quickAttackPending)send("BASIC",0);send("BASIC_RELEASE",0);}
                else if (!heavyStarted) {
                    // Server resolves aerial stance basics/plunges from authoritative state.
                    // A local ground check used to bypass several complete aerial combos.
                    send("BASIC", 0);
                    // A tap can open post-liberation preparation on its release.
                    // Resolve that same physical release instead of timing into NIGHT.
                    if(attackRole.has("weapon")&&attackRole.has("form")&&attackRole.get("form").getAsString().equals("blazing")
                        &&McWuwa.catalog().requireByWeapon(attackRole.get("weapon").getAsString()).characterId().equals("zani"))send("BASIC_RELEASE",0);
                }
                else {
                    OptionalInt full = verifiedFullChargeTicks();
                    send(full.isPresent() && attackTicks >= full.getAsInt() ? "HEAVY_FULL" : "HEAVY_RELEASE", 0);
                }
                // A complete gesture cannot classify the next between-ticks click.
                // Without clearing this, a quick basic after heavy is sent as a
                // second release and rejected by the server's consumed charge.
                heavyStarted=false;attackTicks=0;
            }
            attackDown = attack;
            quickAttackPending = false;
        } else { releaseInputs(); discardCombatClicks(); }
        previousSelected = game.player.getInventory().getSelectedSlot();
    }
    private static OptionalInt verifiedFullChargeTicks() {
        JsonObject role = roleView(activeSlot()); if (!role.has("weapon")) return OptionalInt.empty();
        var data = McWuwa.catalog().requireByWeapon(role.get("weapon").getAsString());
        return data.fullChargeTicks(); // Hint only: elapsed time and owner are independently checked by the server.
    }
    private static void interaction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft game = Minecraft.getInstance(); if (game.gui.screen() != null || game.gui.overlay()!=null || !wuwaMode()) return;
        // Cancel vanilla mining/attacking and offhand use. The authoritative action starts through the input payload.
        if (event.isAttack() || event.isUseItem() || event.isPickBlock()) { event.setCanceled(true); event.setSwingHand(false); }
        // A press/release can fit between two client ticks; the vanilla click event preserves that short tap.
        if (event.isAttack() && !attackDown) quickAttackPending = true;
        if (event.isPickBlock()) send("LOCK", game.crosshairPickEntity == null ? -1 : game.crosshairPickEntity.getId());
    }
    /** Capture the real press before vanilla startAttack's missTime/held-item gates.
     * A press+release between ticks otherwise vanishes before InteractionKeyMappingTriggered.
     * This only latches intent; the existing gesture and server transaction still decide it. */
    private static boolean mayCaptureAttack() {
        var game=Minecraft.getInstance();return game.gui.screen()==null&&game.gui.overlay()==null&&game.player!=null
            &&game.player.isAlive()&&!game.player.isSpectator()&&wuwaMode()&&!attackDown;
    }
    private static void mouseAttackEdge(InputEvent.MouseButton.Pre event) {
        if(event.getAction()==1&&mayCaptureAttack()
            &&((IKeyMappingExtension)Minecraft.getInstance().options.keyAttack).isActiveAndMatches(InputConstants.Type.MOUSE.getOrCreate(event.getButton())))quickAttackPending=true;
    }
    private static void keyboardAttackEdge(InputEvent.Key event) {
        if(event.getAction()==1&&mayCaptureAttack()&&Minecraft.getInstance().options.keyAttack.matches(event.getKeyEvent())
            &&((IKeyMappingExtension)Minecraft.getInstance().options.keyAttack).isConflictContextAndModifierActive())quickAttackPending=true;
    }
    private static void liberationInput() {
        boolean click=false;while(LIBERATION.consumeClick())click=true;
        var role=roleView(activeSlot());var mode=cn.zgnhit.mcwuwa.core.LiberationGesture.Mode.IMMEDIATE;
        if(role.has("weapon")&&role.get("weapon").getAsString().equals("frostburn")&&role.has("form")&&role.get("form").getAsString().equals("future"))mode=cn.zgnhit.mcwuwa.core.LiberationGesture.Mode.CHARGE_RELEASE;
        if(role.has("liberationHoldMode"))try{mode=cn.zgnhit.mcwuwa.core.LiberationGesture.Mode.valueOf(role.get("liberationHoldMode").getAsString());}catch(IllegalArgumentException ignored){ }
        String operation=LIBERATION_GESTURE.poll(role.has("instance")?role.get("instance").getAsString():"",mode,LIBERATION.isDown(),click);
        if(!operation.isEmpty())send(operation,0);
    }
    private static void discardCombatClicks() { for (KeyMapping mapping : List.of(SKILL, LIBERATION, ECHO, TUNE, TRAVEL)) while (mapping.consumeClick()) { } }
    private static void discardClicks() { discardCombatClicks();while(CREATIVE_RECHARGE.consumeClick()){}for (KeyMapping mapping : SWITCH) while (mapping.consumeClick()) { } }
    private static void cancelSixJump(){
        if(SIX_JUMP_GESTURE.cancel(Minecraft.getInstance().options.keyJump.isDown()))send("SIX_JUMP_CANCEL",0);
    }
    private static void releaseInputs() {
        cancelSixJump();
        if (sprintDown) send("DODGE_RELEASE", 0);
        if (aimDown) send("AIM_RELEASE", 0);
        if (heavyStarted) send("CANCEL_HEAVY", 0);
        if (echoHeld) send("ECHO_RELEASE", 0);
        if(skillHeld)send("CANCEL_SKILL",0);
        if(LIBERATION_GESTURE.cancel())send("CANCEL_LIBERATION",0);
        resetInputs();
    }
    private static void cancelBikeInput(){
        if(bikeSpaceDown||bikeSkillDown)send("BIKE_JUMP_CANCEL",0);
        bikeSpaceDown=bikeSkillDown=false;
    }
    private static void resetInputs() { attackDown = sprintDown = aimDown = heavyStarted = quickAttackPending = skillDown = skillHeld = echoDown = echoHeld = jumpDown = false; attackTicks = skillTicks = echoTicks = 0; }
    private static void logout(ClientPlayerNetworkEvent.LoggingOut event) { TraversalPrediction.clear();RoleSkinRegistry.clear();AbilityFeedbackClient.clear();view = new JsonObject(); sequence = 0; previousSelected = 0; screenWasOpen=false;bikeSpaceDown=bikeSkillDown=wasRidingBike=false;resetInputs();LIBERATION_GESTURE.reset(); SIX_JUMP_GESTURE.reset();InventoryControls.clearFeedback();CombatEffects.clear(); }
}
