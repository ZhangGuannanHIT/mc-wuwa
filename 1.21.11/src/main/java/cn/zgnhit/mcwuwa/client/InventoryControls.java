package cn.zgnhit.mcwuwa.client;

import cn.zgnhit.mcwuwa.McWuwa;
import cn.zgnhit.mcwuwa.mixin.client.ContainerScreenAccessor;
import cn.zgnhit.mcwuwa.platform.ModAttachments;
import cn.zgnhit.mcwuwa.platform.WeaponData;
import cn.zgnhit.mcwuwa.content.CharacterDefinition;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Inventory container remains authoritative; editor buttons send atomic intents and never move local stacks. */
public final class InventoryControls {
    private static final WeakHashMap<Screen, List<Button>> BUTTONS = new WeakHashMap<>();
    private static final WeakHashMap<Screen, List<Button>> WEAPON_BUTTONS = new WeakHashMap<>();
    private static final java.util.Set<Screen> WEAPON_RELEASE=java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private record Feedback(String text,long expires){}
    private static final Map<Integer,Feedback> FEEDBACK=new java.util.HashMap<>();
    public static void feedback(int slot,String text){var game=Minecraft.getInstance();if(slot<0||slot>2||game.level==null)return;if(text.isEmpty())FEEDBACK.remove(slot);else FEEDBACK.put(slot,new Feedback(text,game.level.getGameTime()+100));}
    static String feedbackText(int slot){var game=Minecraft.getInstance();var feedback=FEEDBACK.get(slot);if(feedback==null||game.level==null||game.level.getGameTime()>=feedback.expires()){FEEDBACK.remove(slot);return "";}return feedback.text();}
    static void clearFeedback(){FEEDBACK.clear();}
    private InventoryControls() {}
    static boolean ownsCreativeCursor(Screen screen){
        return screen instanceof CreativeModeInventoryScreen||screen instanceof DetailsScreen||screen instanceof AttributesScreen;
    }
    static void initialize() {
        NeoForge.EVENT_BUS.addListener(InventoryControls::init);
        NeoForge.EVENT_BUS.addListener(InventoryControls::drawInventory);
    }
    private static void init(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)
            || !(screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen)) return;
        ContainerScreenAccessor origin = (ContainerScreenAccessor) screen; List<Button> buttons = new ArrayList<>(), weapons=new ArrayList<>();
        WEAPON_RELEASE.remove(screen);
        int panel=origin.mcwuwa$left()+panelOffset(screen);
        for (int i = 0; i < 3; i++) {
            int slot = i;
            Button weapon=Button.builder(Component.empty(), ignored -> swapWeapon(screen,slot))
                .bounds(panel,origin.mcwuwa$top()+15+i*21,20,20)
                .tooltip(Tooltip.create(Component.literal("拿起武器后点击放入，空手点击取出；仅脱战可调整"))).build();
            event.addListener(weapon);weapons.add(weapon);
            Button button = Button.builder(Component.literal("配"), ignored -> Minecraft.getInstance().setScreen(new DetailsScreen(screen, slot, false)))
                .bounds(panel + 23, origin.mcwuwa$top() + 16 + i * 21, 20, 18)
                .tooltip(Tooltip.create(Component.literal("查看角色、声骸、养成与复活"))).build();
            event.addListener(button); buttons.add(button);
        }
        BUTTONS.put(screen, buttons);
        WEAPON_BUTTONS.put(screen,weapons);
    }
    private static int panelOffset(Screen screen){return screen instanceof CreativeModeInventoryScreen?199:180;}
    private static void swapWeapon(AbstractContainerScreen<?> screen,int index) {
        var game=Minecraft.getInstance();if(game.player==null||game.gameMode==null)return;
        WEAPON_RELEASE.add(screen);
        if(screen instanceof CreativeModeInventoryScreen) {
            CombatClient.sendCreativeTeamSlot(index,game.player.inventoryMenu.getCarried().copy());
        } else {
            var team=ModAttachments.team(game.player);
            var slot=game.player.inventoryMenu.slots.stream().filter(s->s.container==team.inventory()&&s.getContainerSlot()==index).findFirst().orElseThrow();
            game.gameMode.handleInventoryMouseClick(game.player.inventoryMenu.containerId,slot.index,0,ClickType.PICKUP,game.player);
        }
    }
    /** A managed button has already handled the click; vanilla release must not drop its cursor. */
    public static boolean consumeWeaponRelease(Screen screen,int button){return button==0&&WEAPON_RELEASE.remove(screen);}
    private static void returnTo(Screen parent,boolean buttonPress){
        var game=Minecraft.getInstance();
        // A button changes the screen on press. Its release belongs to that button,
        // not to the restored container's outside-drop handler at the same coordinates.
        game.setScreen(parent);
        if(buttonPress&&parent instanceof AbstractContainerScreen<?>)WEAPON_RELEASE.add(parent);
    }
    private static void drawInventory(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)
            || !(screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen)) return;
        ContainerScreenAccessor origin = (ContainerScreenAccessor) screen; int x = origin.mcwuwa$left(), y = origin.mcwuwa$top();
        var g = event.getGuiGraphics(); var font = Minecraft.getInstance().font;
        int panel=x+panelOffset(screen);
        g.drawString(font, "队伍", panel+1, y + 4, 0xffe4e8e8, true);
        List<Button> buttons = BUTTONS.get(screen);
        List<Button> weapons=WEAPON_BUTTONS.get(screen);
        for (int i = 0; i < 3; i++) {
            g.renderOutline(panel, y + 15 + i * 21, 20, 20, i == CombatClient.activeSlot() ? 0xffd6be75 : 0xff879ba7);
            if(Minecraft.getInstance().player!=null)g.renderItem(ModAttachments.team(Minecraft.getInstance().player).inventory().getItem(i),panel+2,y+17+i*21);
            if(weapons!=null){weapons.get(i).setX(panel);weapons.get(i).setY(y+15+i*21);weapons.get(i).active=!CombatClient.view().has("combat")||!CombatClient.view().get("combat").getAsBoolean();}
            if (buttons != null) {
                buttons.get(i).setX(panel + 23); buttons.get(i).setY(y + 16 + i * 21);
                buttons.get(i).active = CombatClient.roleView(i).has("weapon");
            }
        }
    }
    public static void offerRevive(int slot) { Minecraft.getInstance().setScreen(new DetailsScreen(null, slot, true)); }
    private static CompoundTag weaponTag(int slot) {
        var player = Minecraft.getInstance().player;
        return player == null ? new CompoundTag() : WeaponData.tag(ModAttachments.team(player).inventory().getItem(slot));
    }
    private static String statName(String id) {
        return switch (id) {
            case "crit_rate" -> "暴击"; case "crit_damage" -> "暴击伤害"; case "attack_pct", "attack_flat" -> "攻击";
            case "health_pct", "health_flat" -> "生命"; case "defense_pct", "defense_flat" -> "防御";
            case "energy_regen" -> "共鸣效率"; case "healing_bonus" -> "治疗加成"; case "all_damage_bonus" -> "全伤害加成";
            case "basic_bonus" -> "普攻伤害"; case "heavy_bonus" -> "重击伤害";
            case "skill_bonus" -> "技能伤害"; case "liberation_bonus" -> "解放伤害";
            case "intro_bonus" -> "变奏伤害"; case "outro_bonus" -> "延奏伤害"; case "echo_bonus" -> "声骸伤害";
            case "shield_bonus" -> "护盾加成"; case "incoming_healing_bonus" -> "受治疗加成"; case "damage_reduction" -> "受到伤害降低";
            case "offtune_efficiency" -> "谐度破坏效率"; case "tune_break_amplification" -> "谐度破坏增幅"; case "effect_damage_bonus" -> "效应伤害加成";
            case "glacio_bonus" -> "冷凝伤害"; case "fusion_bonus" -> "热熔伤害"; case "electro_bonus" -> "导电伤害";
            case "aero_bonus" -> "气动伤害"; case "spectro_bonus" -> "衍射伤害"; case "havoc_bonus" -> "湮灭伤害";
            default -> id;
        };
    }
    private static String statValue(String id, double value) {
        return (id.endsWith("_flat")||id.equals("tune_break_amplification")) ? String.format(Locale.ROOT, "%.0f", value) : String.format(Locale.ROOT, "%.1f%%", value * 100);
    }
    private static String echoName(CompoundTag echo) {
        String species = echo.getStringOr("species", "");
        return species.isEmpty() ? "空槽" : Component.translatable("entity." + species.replace(':', '.')).getString();
    }
    private static String selectedMode(CharacterDefinition definition, CompoundTag tag) {
        String selected = tag.getStringOr("mode", "default");
        return definition.modes().stream().filter(mode -> Integer.toString(mode.sourceId()).equals(selected))
            .map(CharacterDefinition.ModeDefinition::name).findFirst().orElse("默认模态");
    }

    private static final class DetailsScreen extends Screen {
        private final Screen parent; private final int slot; private final boolean revivePrompt;
        private Button chain, refine, revive, mode, switchRole;
        private final List<Button> echoButtons = new ArrayList<>();
        private int left, top;
        DetailsScreen(Screen parent, int slot, boolean revivePrompt) {
            super(Component.literal("角色配置")); this.parent = parent; this.slot = slot; this.revivePrompt = revivePrompt;
        }
        @Override protected void init() {
            left = (width - 300) / 2; top = (height - 176) / 2; echoButtons.clear();
            FEEDBACK.remove(slot);
            addRenderableWidget(button("属性", 248, 5, 42, () -> minecraft.setScreen(new AttributesScreen(this, slot)), "查看角色总面板、当前增益与属性来源"));
            chain = addRenderableWidget(button("共鸣链 +1", 10, 48, 86, () -> CombatClient.send("CHAIN", slot), "消耗 6 绿宝石，最多 C6"));
            refine = addRenderableWidget(button("精炼 +1", 107, 48, 86, () -> CombatClient.send("REFINE", slot), "消耗 3 钻石，最多 S5"));
            revive = addRenderableWidget(button("金苹果复活", 204, 48, 86, () -> CombatClient.send("REVIVE", slot), "消耗 1 普通金苹果，恢复半血；全队共用 60 秒冷却"));
            mode = addRenderableWidget(button("切换预选模态", 10, 72, 183, () -> CombatClient.send("MODE_NEXT", slot), "仅脱战可切换，不重置生命和冷却"));
            switchRole=addRenderableWidget(button("切换此角色", 204, 72, 86, () -> { CombatClient.send("SWITCH", slot); returnTo(parent,true); }, "保留当前快捷栏选择；倒下时先复活"));
            for (int i = 0; i < 5; i++) {
                int echo = i;
                echoButtons.add(addRenderableWidget(button("槽 " + (i + 1), 10 + i * 56, 109, 48,
                    () -> CombatClient.swapEcho(slot * 5 + echo), "在背包拿起声骸后点击装备；空手点击取出。仅脱战可编辑")));
            }
            addRenderableWidget(button("返回", 210, 151, 80, ()->returnTo(parent,true), ""));
        }
        private Button button(String label, int x, int y, int w, Runnable action, String tooltip) {
            return Button.builder(Component.literal(label), ignored -> action.run()).bounds(left + x, top + y, w, 18)
                .tooltip(Tooltip.create(Component.literal(tooltip))).build();
        }
        @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.fill(left, top, left + 300, top + 176, 0xf0202a33); g.renderOutline(left, top, 300, 176, 0xff859caa);
            JsonObject role = CombatClient.roleView(slot);
            CompoundTag tag = weaponTag(slot);
            boolean occupied = role.has("weapon"); boolean combat = CombatClient.view().has("combat") && CombatClient.view().get("combat").getAsBoolean();
            boolean downed = occupied && CombatHud.number(role, "hp") <= 0;
            chain.active = occupied && !combat && CombatHud.number(role, "chain") < 6;
            refine.active = occupied && !combat && CombatHud.number(role, "refinement") < 5;
            revive.active = downed && CombatHud.number(CombatClient.view(), "reviveCd") == 0;
            switchRole.active=occupied&&!downed&&CombatHud.number(CombatClient.view(),"switchCd")==0;
            mode.active = occupied && !combat;
            echoButtons.forEach(button -> button.active = occupied && !combat && !revivePrompt);
            if (occupied) {
                var definition = McWuwa.catalog().requireByWeapon(role.get("weapon").getAsString());
                var presetSlots=definition.sonataSlots().stream().map(id->cn.zgnhit.mcwuwa.content.mechanics.SonataPresetRules.sonataId(definition.characterId(),tag.getStringOr("mode","default"),id)).toList();
                int presetEcho=cn.zgnhit.mcwuwa.content.mechanics.SonataPresetRules.echoId(definition.characterId(),tag.getStringOr("mode","default"),definition.echoId());
                mode.setMessage(Component.literal(selectedMode(definition, tag)));
                mode.active &= !definition.modes().isEmpty();
                g.drawString(font, definition.name() + (revivePrompt ? " · 角色已倒下" : " · 角色配置"), left + 10, top + 8, 0xffeef5ef, false);
                g.drawString(font, String.format(Locale.ROOT, "生命 %.1f / %.1f   C%d · S%d", CombatHud.number(role, "hp"), CombatHud.number(role, "maxHp"),
                    (int) CombatHud.number(role, "chain"), (int) CombatHud.number(role, "refinement")), left + 10, top + 22, 0xffbbcdd4, false);
                var total=role.has("stats")?role.getAsJsonObject("stats"):new JsonObject();
                g.drawString(font, String.format(Locale.ROOT, "总攻击 %.1f  总防御 %.1f  暴击 %.1f%%", CombatHud.number(total,"attack"), CombatHud.number(total,"defense"), CombatHud.number(total,"critRate")*100),
                    left + 10, top + 35, 0xffbbcdd4, false);
                int totalCost = 0; Map<Integer, Integer> setCounts = new LinkedHashMap<>();
                for (int i = 0; i < 5; i++) {
                    CompoundTag echo = tag.getCompoundOrEmpty("echo_" + i); int cost = echo.getIntOr("cost", 0); totalCost += cost;
                    if (cost > 0) setCounts.merge(presetSlots.get(i), 1, Integer::sum);
                    String label = cost > 0 ? cost + "C·" + echoName(echo) : "槽 " + (i + 1);
                    echoButtons.get(i).setMessage(Component.literal(font.plainSubstrByWidth(label, 44)));
                    StringBuilder tooltip = new StringBuilder("槽 ").append(i + 1).append(" · ").append(echoName(echo));
                    if (cost > 0) {
                        tooltip.append("\nCOST ").append(cost);
                        for (String category : List.of("main_stats", "sub_stats")) {
                            var stats = echo.getCompoundOrEmpty(category);
                            for (String key : stats.keySet().stream().sorted().toList())
                                tooltip.append('\n').append(category.equals("main_stats") ? "主·" : "副·").append(statName(key)).append(" +").append(statValue(key, stats.getDoubleOr(key, 0)));
                        }
                    }
                    tooltip.append("\n套装：").append(McWuwa.catalog().sonataName(presetSlots.get(i)));
                    if (i == 0) tooltip.append("\n主位主动：").append(McWuwa.catalog().manifest().getAsJsonArray("echoes").asList().stream()
                        .map(element -> element.getAsJsonObject()).filter(e -> e.get("sourceId").getAsInt() == presetEcho)
                        .map(e -> e.get("name").getAsString()).findFirst().orElse("未定义"));
                    tooltip.append("\n拿起声骸后点击交换，空手点击取出");
                    echoButtons.get(i).setTooltip(Tooltip.create(Component.literal(tooltip.toString())));
                }
                g.drawString(font, "声骸 COST " + totalCost + "/12 · 五个不同物种", left + 10, top + 96, 0xffd2ded9, false);
                String progress = setCounts.entrySet().stream().map(entry -> McWuwa.catalog().sonataName(entry.getKey()) + " " + entry.getValue()
                    + "/" + presetSlots.stream().filter(id -> id.equals(entry.getKey())).count()).reduce((a, b) -> a + "  " + b).orElse("套装：尚未装备声骸");
                g.drawString(font, font.plainSubstrByWidth(progress, 280), left + 10, top + 132, 0xffa7bac9, false);
            } else g.drawString(font, "该队伍槽为空", left + 10, top + 8, 0xffe0d7bb, false);
            String feedback=feedbackText(slot);
            g.drawString(font, font.plainSubstrByWidth(!feedback.isEmpty()?feedback:combat ? "战斗中：队伍与养成锁定" : "拿起声骸点击槽位可交换",190), left + 10, top + 155, !feedback.isEmpty()||combat ? 0xffdea28e : 0xffaabfc5, false);
            super.render(g, mouseX, mouseY, partialTick);
            if (minecraft.player != null && !minecraft.player.containerMenu.getCarried().isEmpty()) g.renderItem(minecraft.player.containerMenu.getCarried(), mouseX - 8, mouseY - 8);
        }
        @Override public void onClose() { returnTo(parent,false); }
        @Override public boolean isPauseScreen() { return false; }
    }

    /** Total attributes come from the authoritative combat snapshot; source breakdown stays below. */
    private static final class AttributesScreen extends Screen {
        private final Screen parent; private final int slot; private int page, left, top;
        AttributesScreen(Screen parent, int slot) { super(Component.literal("属性明细")); this.parent = parent; this.slot = slot; }
        @Override protected void init() {
            left = (width - 300) / 2; top = (height - 174) / 2;
            addRenderableWidget(Button.builder(Component.literal("上一页"), b -> page = Math.max(0, page - 1)).bounds(left + 10, top + 148, 70, 18).build());
            addRenderableWidget(Button.builder(Component.literal("下一页"), b -> page++).bounds(left + 90, top + 148, 70, 18).build());
            addRenderableWidget(Button.builder(Component.literal("返回"), b -> returnTo(parent,true)).bounds(left + 220, top + 148, 70, 18).build());
        }
        @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.fill(left, top, left + 300, top + 174, 0xf0202a33); g.renderOutline(left, top, 300, 174, 0xff859caa);
            JsonObject role = CombatClient.roleView(slot); List<String> lines = new ArrayList<>();
            if (role.has("weapon")) {
                var data = McWuwa.catalog().requireByWeapon(role.get("weapon").getAsString());
                lines.add(data.name()+" · 角色90级 / 武器90级");
                lines.add("技能10级 · 小技能节点全部解锁");
                if(role.has("stats")){
                    var total=role.getAsJsonObject("stats");
                    lines.add(String.format(Locale.ROOT,"总生命 %.0f · MC生命 %.2f",CombatHud.number(total,"health"),CombatHud.number(total,"mappedHealth")));
                    lines.add(String.format(Locale.ROOT,"总攻击 %.1f · 总防御 %.1f",CombatHud.number(total,"attack"),CombatHud.number(total,"defense")));
                    lines.add(String.format(Locale.ROOT,"暴击 %.1f%% · 暴伤 %.1f%%",CombatHud.number(total,"critRate")*100,CombatHud.number(total,"critDamage")*100));
                    if(CombatHud.number(total,"critRate")>1)lines.add(String.format(Locale.ROOT,"实际暴击概率 %.1f%%",CombatHud.number(total,"critChance")*100));
                    lines.add(String.format(Locale.ROOT,"共鸣效率 %.1f%%",CombatHud.number(total,"energyRegen")*100));
                    lines.add(String.format(Locale.ROOT,"谐度破坏增幅 %.1f · 效率 %.1f%%",CombatHud.number(total,"tuneBreakAmplification"),CombatHud.number(total,"offTuneEfficiency")*100));
                    lines.add("含装备、技能节点与当前生效的通用增益");
                    lines.add("招式限定增伤、敌方减抗等按命中条件结算");
                    lines.add("专武精炼 S"+(int)CombatHud.number(role,"refinement")+" · 共鸣链 C"+(int)CombatHud.number(role,"chain"));
                    lines.add("当前属性与适用伤害加成：");
                    var bonuses=total.getAsJsonObject("bonuses");
                    for(String key:List.of("health_pct","health_flat","attack_pct","attack_flat","defense_pct","defense_flat","healing_bonus","all_damage_bonus","basic_bonus","heavy_bonus","skill_bonus","liberation_bonus","intro_bonus","outro_bonus","echo_bonus","glacio_bonus","fusion_bonus","electro_bonus","aero_bonus","spectro_bonus","havoc_bonus","effect_damage_bonus","offtune_efficiency","tune_break_amplification","shield_bonus","incoming_healing_bonus","damage_reduction"))
                        lines.add(statName(key)+" +"+statValue(key,CombatHud.number(total.has("applicableBonuses")&&total.getAsJsonObject("applicableBonuses").has(key)?total.getAsJsonObject("applicableBonuses"):bonuses,key)));
                }else lines.add("正在同步服务器总属性……");
                lines.add("以下为基础与装备来源：");
                lines.add("角色基础：" + data.name());
                lines.add(String.format(Locale.ROOT, "原作生命 %.0f · 映射生命 %.2f", data.baseStats().health(), data.baseStats().health() * .002));
                lines.add(String.format(Locale.ROOT, "基础攻击 %.0f · 基础防御 %.0f", data.baseStats().attack(), data.baseStats().defense()));
                lines.add("专武：" + data.weapon().name());
                data.weapon().stats().forEach(stat -> lines.add(stat.name() + " +" + (stat.isPercent() ? String.format(Locale.ROOT, "%.1f%%", stat.value() * 100) : String.format(Locale.ROOT, "%.0f", stat.value()))));
                lines.add("声骸词条汇总（含主、副词条）：");
                Map<String, Double> stats = new java.util.TreeMap<>(); CompoundTag tag = weaponTag(slot);
                for (int i = 0; i < 5; i++) for (String category : List.of("main_stats", "sub_stats")) {
                    var values = tag.getCompoundOrEmpty("echo_" + i).getCompoundOrEmpty(category);
                    values.keySet().forEach(key -> stats.merge(key, values.getDoubleOr(key, 0), Double::sum));
                }
                stats.forEach((key, value) -> lines.add(statName(key) + " +" + statValue(key, value)));
                lines.add("套装和临时战斗增益以实际技能条件为准。");
            } else lines.add("该队伍槽为空。");
            int pages = Math.max(1, (lines.size() + 9) / 10); page = Math.min(page, pages - 1);
            g.drawString(font, "角色总属性 · " + (page + 1) + "/" + pages, left + 10, top + 10, 0xffe5eff4, false);
            for (int i = 0; i < 10 && page * 10 + i < lines.size(); i++)
                g.drawString(font, font.plainSubstrByWidth(lines.get(page * 10 + i), 280), left + 10, top + 29 + i * 11, 0xffbed0d8, false);
            super.render(g, mouseX, mouseY, partialTick);
        }
        @Override public void onClose() { returnTo(parent,false); }
        @Override public boolean isPauseScreen() { return false; }
    }
}
