package cn.zgnhit.mcwuwa.client;

import cn.zgnhit.mcwuwa.McWuwa;
import com.google.gson.JsonObject;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import java.util.Set;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.ModConfigSpec;

/** PC HUD hierarchy above Minecraft's hotbar. Never print HP, damage, shield or resource values. */
public final class CombatHud {
    public static final ModConfigSpec CONFIG;
    private static final ModConfigSpec.BooleanValue ENABLED, TEAM_VISIBLE, PLAYER_VISIBLE, TARGET_VISIBLE, ABILITIES_VISIBLE;
    private static final ModConfigSpec.IntValue TEAM_X, TEAM_Y, HEALTH_X, HEALTH_Y, TARGET_X, TARGET_Y, ABILITY_X, ABILITY_Y;
    private static final int WHITE = 0xfff1f2ed, MUTED = 0xff87959c, BACK = 0x98121920;
    static {
        var builder = new ModConfigSpec.Builder();
        builder.comment("Client display only; these settings never alter server combat rules.").push("hud");
        ENABLED = builder.define("enabled", true); TEAM_VISIBLE = builder.define("teamVisible", true);
        PLAYER_VISIBLE=builder.define("playerVisible",true);
        TARGET_VISIBLE = builder.define("targetVisible", true); ABILITIES_VISIBLE = builder.define("abilitiesVisible", true);
        TEAM_X = builder.defineInRange("teamOffsetX", 0, -4096, 4096); TEAM_Y = builder.defineInRange("teamOffsetY", 0, -4096, 4096);
        HEALTH_X = builder.defineInRange("healthOffsetX", 0, -4096, 4096); HEALTH_Y = builder.defineInRange("healthOffsetY", 0, -4096, 4096);
        TARGET_X = builder.defineInRange("targetOffsetX", 0, -4096, 4096); TARGET_Y = builder.defineInRange("targetOffsetY", 0, -4096, 4096);
        ABILITY_X = builder.defineInRange("abilityOffsetX", 0, -4096, 4096); ABILITY_Y = builder.defineInRange("abilityOffsetY", 0, -4096, 4096);
        builder.pop(); CONFIG = builder.build();
    }
    private CombatHud() { }
    public enum Part {
        TEAM("队伍"),PLAYER("生命与共鸣回路"),TARGET("目标"),ABILITIES("攻击与技能");
        public final String label;Part(String label){this.label=label;}
    }
    private static ModConfigSpec.IntValue offset(Part part,boolean vertical){return switch(part){case TEAM->vertical?TEAM_Y:TEAM_X;case PLAYER->vertical?HEALTH_Y:HEALTH_X;case TARGET->vertical?TARGET_Y:TARGET_X;case ABILITIES->vertical?ABILITY_Y:ABILITY_X;};}
    private static ModConfigSpec.BooleanValue visibility(Part part){return switch(part){case TEAM->TEAM_VISIBLE;case TARGET->TARGET_VISIBLE;case ABILITIES->ABILITIES_VISIBLE;case PLAYER->PLAYER_VISIBLE;};}
    public static int offsetValue(Part part,boolean vertical){return offset(part,vertical).get();}
    public static void move(Part part,boolean vertical,int delta){var value=offset(part,vertical);value.set(Math.clamp(value.get()+delta,-4096,4096));}
    public static boolean visible(Part part){return visibility(part).get();}
    public static void toggle(Part part){var value=visibility(part);value.set(!value.get());}
    public static void reset(Part part){offset(part,false).set(0);offset(part,true).set(0);visibility(part).set(true);}
    public static void resetLayout(){ENABLED.set(true);for(var part:Part.values())reset(part);}
    public static void saveLayout(){CONFIG.save();}
    static void initialize() { NeoForge.EVENT_BUS.addListener(CombatHud::draw);NeoForge.EVENT_BUS.addListener(CombatHud::vanillaLayer); }
    private static void vanillaLayer(RenderGuiLayerEvent.Pre event) {
        if(ENABLED.get()&&CombatClient.wuwaMode()&&Set.of(VanillaGuiLayers.PLAYER_HEALTH,VanillaGuiLayers.ARMOR_LEVEL,
            VanillaGuiLayers.FOOD_LEVEL,VanillaGuiLayers.EXPERIENCE_LEVEL,VanillaGuiLayers.CONTEXTUAL_INFO_BAR,
            VanillaGuiLayers.CONTEXTUAL_INFO_BAR_BACKGROUND,VanillaGuiLayers.SELECTED_ITEM_NAME).contains(event.getName()))event.setCanceled(true);
    }
    private static void draw(RenderGuiEvent.Post event) {
        var game = Minecraft.getInstance();
        if (game.player == null || game.options.hideGui || !ENABLED.get()) return;
        var g = event.getGuiGraphics();
        g.pose().pushMatrix(); g.pose().scale(hudScale(g), hudScale(g));
        if(game.player.getVehicle() instanceof cn.zgnhit.mcwuwa.platform.entity.SurveyMotorcycleEntity bike){
            smallText(g,"科考摩托 · 动能 "+Math.round(bike.kinetic())+"/"+Math.round(bike.kineticCapacity())+(bike.boosting()?" · 加速中":" · "+CombatClient.travelKey()+" 加速"),width(g)/2,height(g)-82,.72f,0xffe5dba0);
            smallText(g,"飞跃 "+bike.leapCharges()+"/"+cn.zgnhit.mcwuwa.core.SurveyMotorcycleRules.LEAP_CAPACITY+" · 滞空 "+Math.round(bike.glideEnergy()*100f/cn.zgnhit.mcwuwa.core.SurveyMotorcycleRules.GLIDE_CAPACITY)+"% · "+(bike.gliding()?"滑翔中":"滑翔翼收起"),width(g)/2,height(g)-94,.68f,0xffa5e7ec);
            smallText(g,CombatClient.abilityKey("skill")+" 海豚跳／长按飞跃 · 空格开收翼 · 水面自动结冰",width(g)/2,height(g)-105,.6f,0xffdddddd);
        }
        if(!CombatClient.hasTeam()){g.pose().popMatrix();return;}
        if (TEAM_VISIBLE.get()) team(g);
        var active = CombatClient.roleView(CombatClient.activeSlot());
        if (active.has("weapon")) {
            if(CombatClient.wuwaMode()&&PLAYER_VISIBLE.get())gauges(g, active);
            if (ABILITIES_VISIBLE.get()&&CombatClient.wuwaMode()) abilities(g, active);
            if(CombatClient.wuwaMode()&&number(active,"performanceTicks")>0) {
                int cx=width(g)/2,cy=height(g)-100;boolean ready=flag(active,"rhythmReady");
                for(int side:new int[]{-1,1}) {
                    int color=side<0?0xff8ce0ac:0xffecd58a,x=cx+side*34;
                    ring(g,x,cy,13,2,1,ready?WHITE:color);
                    ring(g,x,cy,14+(int)(number(active,"rhythmTicks")/2),1,1,color);
                    key(g,CombatClient.noteKey(side>0),x,cy-3,color);
                }
                smallText(g,"风蚀       光噪",cx,cy+19,.65f,WHITE);
            }
            if(CombatClient.wuwaMode()&&number(active,"camouflageTicks")>0)smallText(g,"光学迷彩 "+String.format(java.util.Locale.ROOT,"%.1fs",number(active,"camouflageTicks")/20),width(g)/2,height(g)-73,.7f,0xffa4eafa);
        }
        if (TARGET_VISIBLE.get() && CombatClient.view().has("target")) target(g, CombatClient.view().getAsJsonObject("target"));
        if(TARGET_VISIBLE.get()&&CombatClient.wuwaMode()&&CombatClient.view().has("nearbyTuneReady")&&!CombatClient.view().getAsJsonArray("nearbyTuneReady").isEmpty()) {
            var selected=object(CombatClient.view(),"target");
            if(!flag(selected,"mistuned"))smallText(g,CombatClient.abilityKey("tune")+" 谐度破坏",width(g)/2,height(g)/2+29,.8f,0xffe2bfff);
        }
        g.pose().popMatrix();
    }
    /** Only reserve characters occupy the right edge, matching the reference. */
    private static void team(GuiGraphicsExtractor g) {
        int x = Math.clamp(width(g) - 27 + TEAM_X.get(), 44, Math.max(44, width(g) - 19));
        int y = Math.clamp((int)(height(g) * .23) + TEAM_Y.get(), 22, Math.max(22, height(g) - 124));
        int row = 0;
        for (int slot = 0; slot < 3; slot++) {
            var role = CombatClient.roleView(slot);
            if (slot == CombatClient.activeSlot() || !role.has("weapon")) continue;
            int cy = y + row++ * 46;
            boolean alive = number(role, "hp") > 0;
            disk(g, x, cy, 17, BACK); ring(g, x, cy, 17, 1, 1, alive ? 0xaae5e5df : 0xffa56767);
            var data = McWuwa.catalog().requireByWeapon(role.get("weapon").getAsString());
            var portrait = Identifier.fromNamespaceAndPath("mc_wuwa", "textures/gui/portraits/" + data.characterId() + ".png");
            portrait(g, portrait, x, cy);
            if (!alive) { disk(g, x, cy, 15, 0x97131820); smallText(g, "×", x, cy - 4, .9f, 0xffffbab4); }
            bar(g, x - 15, cy + 20, 30, 2, number(role, "hp"), number(role, "maxHp"), alive ? 0xffedf1e3 : 0xffab6a6a);
            key(g, CombatClient.switchKey(slot), x - 39, cy - 3,
                alive && remaining(number(CombatClient.view(), "switchCd")) == 0 ? WHITE : MUTED);
            if (alive && flag(CombatClient.view(),"introOutroReady")) {
                ring(g,x,cy,19,1,1,0xffeee2b2);disk(g,x+17,cy-10,2,0xffeee2b2);
            }
        }
    }
    private static void gauges(GuiGraphicsExtractor g, JsonObject role) {
        int extent=Math.min(160,width(g)/2);
        int center = Math.clamp(width(g) / 2 + HEALTH_X.get(), extent, Math.max(extent,width(g)-extent));
        int bottom = Math.clamp(height(g) - 42 + HEALTH_Y.get(), 64, Math.max(64, height(g) - 20));
        bar(g, center - 78, bottom, 156, 3, number(role, "hp"), number(role, "maxHp"), 0xffe8eee1);
        if (number(role, "shield") > 0) bar(g, center - 78, bottom - 2, 156, 1, number(role, "shield"), number(role, "maxHp"), 0xffa9dde0);
        String slug = McWuwa.catalog().requireByWeapon(role.get("weapon").getAsString()).characterId();
        String form = role.has("form") ? role.get("form").getAsString() : "";
        ForteHud.draw(g,slug,form,object(role,"forte"),object(role,"forteMax"),object(role,"abilities"),center,bottom-14);
        if(role.has("focusRing"))ForteHud.focusRing(g,object(role,"focusRing"),center,bottom-76);
        if(role.has("photoStatus")){
            var photo=object(role,"photoStatus");int film=(int)number(photo,"film"),zoom=(int)number(photo,"zoom");
            if(film>0||zoom>0)smallText(g,film>0?"胶卷 "+film+" / 10":"变焦 "+zoom+" / 4",center,bottom-28,.62f,0xffbce9ff);
        }
        if(flag(role,"cameraActive")){
            int cx=width(g)/2,cy=height(g)/2;
            for(int sx:new int[]{-1,1})for(int sy:new int[]{-1,1}){
                int x=cx+sx*70,y=cy+sy*48;
                g.fill(Math.min(x,x-sx*18),y,Math.max(x,x-sx*18)+1,y+1,0xffbce9ff);
                g.fill(x,Math.min(y,y-sy*14),x+1,Math.max(y,y-sy*14)+1,0xffbce9ff);
            }
            smallText(g,"左键拍摄 · 右键退出",cx,cy+55,.7f,WHITE);
        }
        if(slug.equals("camellya")) {
            // The source ornament itself is the concerto dial (source centre 100,77).
            ring(g,center-114,bottom-17,15,2,ratio(number(role,"concerto"),100),elementColor(role));
        } else {
            disk(g,center-94,bottom-7,9,BACK);ring(g,center-94,bottom-7,9,2,ratio(number(role,"concerto"),100),elementColor(role));
            smallText(g,flag(CombatClient.view(),"introOutroReady")?"奏":"协",center-94,bottom-11,.6f,WHITE);
        }
        if(number(role,"unisonStacks")>0){pips(g,center+94,bottom-7,Math.clamp((int)number(role,"unisonStackCap"),2,4),number(role,"unisonStacks"),0xfff4df8d);smallText(g,"同奏",center+94,bottom-18,.57f,0xfff4df8d);}
        if(role.has("supportBuffs")) {
            var list=role.getAsJsonArray("supportBuffs");int count=Math.min(6,list.size());
            for(int index=0;index<count;index++) {
                var buff=list.get(index).getAsJsonObject();int x=center+(index*46)-(count-1)*23;
                smallText(g,buff.get("label").getAsString(),x,bottom-44,.58f,0xffe5d9ac);
                bar(g,x-19,bottom-36,38,1,remaining(number(buff,"ticks")),number(buff,"duration"),0xffd8ca91);
            }
        }
        staminaArc(g);
    }
    private static void staminaArc(GuiGraphicsExtractor g) {
        double stamina=number(CombatClient.view(),"stamina"),fraction=ratio(stamina,240);
        var player=Minecraft.getInstance().player;
        if(fraction>=.999&&(player==null||!player.isSprinting()))return;
        int cx=width(g)/2+24,cy=height(g)/2+8;
        int color=fraction<=.2?0xfff19d92:0xffdfce91;
        // Short arc next to the crosshair: readable in both first- and third-person.
        for(int i=0;i<48;i++) {
            double angle=-Math.PI*.36+i*(Math.PI*.72/47);
            int x=cx+(int)Math.round(Math.cos(angle)*13),y=cy+(int)Math.round(Math.sin(angle)*13);
            g.fill(x,y,x+2,y+2,i/47.0<=fraction?color:0x68455258);
        }
    }
    private static void abilities(GuiGraphicsExtractor g, JsonObject role) {
        int right = Math.clamp(width(g) - 28 + ABILITY_X.get(), 126, Math.max(126, width(g) - 23));
        int cy = Math.clamp(height(g) - 37 + ABILITY_Y.get(), 27, Math.max(27, height(g) - 27));
        var statuses = object(role, "abilities");
        var basic=object(statuses,"basic");
        button(g,"basic",right-35,cy-55,20,basic,1,elementColor(role));
        if(flag(basic,"enhanced")) {
            String name=basic.has("displayName")?basic.get("displayName").getAsString():"";
            if((!flag(basic,"tapEnhanced")||!flag(basic,"tapReady"))&&flag(basic,"holdEnhanced")&&basic.has("holdName")&&!basic.get("holdName").getAsString().isEmpty())name="长按 · "+basic.get("holdName").getAsString();
            if(name.length()>14)name=name.substring(0,14);
            smallText(g,name,right-35,cy-83,.57f,0xffffe9b6);
        }
        button(g, "skill", right - 82, cy, 14, object(statuses, "skill"), 1, WHITE);
        var skill=object(statuses,"skill");
        if((flag(skill,"enhanced")||flag(skill,"holdAvailable"))&&flag(skill,"ready")&&skill.has("displayName")){
            String label=skill.get("displayName").getAsString();
            if(flag(skill,"holdAvailable")&&skill.has("holdDisplayName"))label="长按 · "+skill.get("holdDisplayName").getAsString();
            else if(skill.has("selectedInput")&&"SKILL_HOLD".equals(skill.get("selectedInput").getAsString()))label="长按 · "+label;
            if(label.length()>12)label=label.substring(0,12);
            smallText(g,label,right-82,cy-28,.55f,0xffffe9b6);
        }
        button(g, "echo", right - 42, cy, 14, object(statuses, "echo"), 1, WHITE);
        double energy = ratio(number(role, "energy"), number(role, "energyMax"));
        if(object(statuses,"liberation").has("progress"))energy=number(object(statuses,"liberation"),"progress");
        if(flag(object(statuses,"liberation"),"available")&&!flag(object(statuses,"liberation"),"usesEnergy")
            &&"energy".equals(object(statuses,"liberation").has("progressMode")?object(statuses,"liberation").get("progressMode").getAsString():"energy"))energy=1;
        button(g, "liberation", right, cy, 19, object(statuses, "liberation"), energy, elementColor(role));
        var liberation=object(statuses,"liberation");
        if(flag(role,"liberationCharging"))smallText(g,"归刃蓄力 "+(int)number(role,"liberationChargeSpent")+" / 3",right,cy-31,.50f,0xffbfefff);
        else if(flag(liberation,"ready")&&flag(liberation,"holdAvailable")&&liberation.has("holdName"))
            smallText(g,"长按 · "+liberation.get("holdName").getAsString(),right,cy-31,.50f,0xffffe9b6);
    }
    private static void button(GuiGraphicsExtractor g, String id, int x, int y, int radius, JsonObject status, double progress, int color) {
        // Cast locks control server input, not the appearance of unrelated buttons.
        boolean available = flag(status, "available"), ready = available;
        double cooldown = remaining(number(status, "cooldownTicks"));
        disk(g, x, y, radius - 2, BACK); ring(g, x, y, radius, id.equals("liberation") ? 2 : 1, 1, 0xa18c979d);
        if (id.equals("liberation")) ring(g, x, y, radius, 2, progress, color);
        if (ready) ring(g, x, y, radius - 3, 1, 1, id.equals("liberation") ? color : 0xffe4e7db);
        if(ready&&flag(status,"enhanced")) {
            double pulse=(Math.sin(number(CombatClient.view(),"tick")*.3)+1)*.5;
            ring(g,x,y,radius+2,1,1,(int)(150+90*pulse)<<24|0x00ffe4a2);
            ring(g,x,y,radius-2,2,1,0xffffe9b6);
        }
        double cue=AbilityFeedbackClient.strength(id,(long)number(CombatClient.view(),"tick"));
        if(cue>0)ring(g,x,y,radius+3+(int)((1-cue)*3),1,1,((int)(210*cue)<<24)|0x00fff0b8);
        if (id.equals("echo")) glyph(g, id, x, y - 1, available ? WHITE : MUTED);
        else {
            var role = CombatClient.roleView(CombatClient.activeSlot());
            String slug = McWuwa.catalog().requireByWeapon(role.get("weapon").getAsString()).characterId();
            String variant=status.has("iconVariant")?status.get("iconVariant").getAsString():id;
            if(!variant.matches("basic|skill|liberation|forte|[bc][1-4]"))variant=id;
            var icon = Identifier.fromNamespaceAndPath("mc_wuwa", "textures/gui/abilities/" + slug + "_" + variant + ".png");
            if(Minecraft.getInstance().getResourceManager().getResource(icon).isEmpty())icon=Identifier.fromNamespaceAndPath("mc_wuwa","textures/gui/abilities/"+slug+"_"+id+".png");
            int size = id.equals("liberation")||id.equals("basic") ? 26 : 22;
            g.blit(RenderPipelines.GUI_TEXTURED, icon, x - size / 2, y - size / 2, 0, 0, size, size, size, size);
            if (!available) disk(g, x, y, radius - 2, 0x89101821);
            if(number(status,"iconStage")>0)smallText(g,number(status,"iconStage")>=2?"Ⅱ":"◆",x+radius-3,y-radius+2,.6f,0xffffe9b6);
        }
        if (cooldown > 0 && (number(status, "maxCharges") <= 1 || number(status, "charges") <= 0)) {
            disk(g, x, y, radius - 2, 0x95101821);
            smallText(g, String.format(Locale.ROOT, "%.1f", cooldown / 20), x, y - 3, .85f, WHITE);
        }
        if (number(status, "maxCharges") > 1) {
            pips(g, x, y - radius - 5, (int)number(status, "maxCharges"), number(status, "charges"), WHITE);
            if (cooldown > 0 && number(status, "charges") > 0)
                smallText(g, String.format(Locale.ROOT, "%.1f", cooldown / 20), x, y + radius + 13, .6f, MUTED);
        }
        // Small charge stocks use pips; continuous gauges (e.g. 150 impression)
        // already use the ring and must not grow a screen-wide row of 150 marks.
        if(id.equals("liberation")&&number(status,"maxPoints")>0&&number(status,"maxPoints")<=8) {
            pips(g,x,y-radius-6,(int)number(status,"maxPoints"),number(status,"points"),color);
            if(status.has("progressMode")&&status.get("progressMode").getAsString().equals("resonance")
                &&number(status,"points")>=number(status,"maxPoints")&&!flag(status,"synchronizationReady"))smallText(g,"同步率未满",x,y-radius-16,.57f,MUTED);
        }
        key(g, CombatClient.abilityKey(id), x, y + radius + 4, ready ? WHITE : MUTED);
    }
    /** Code-native fallbacks, separate from authoritative ability selection. */
    private static void glyph(GuiGraphicsExtractor g, String id, int x, int y, int color) {
        if (id.equals("echo")) {
            ring(g, x, y, 7, 1, .83, color); ring(g, x + 1, y, 3, 1, .75, color);
            g.fill(x - 1, y - 1, x + 2, y + 2, color);
        } else if (id.equals("skill")) {
            for (int dy = -7; dy <= 7; dy++) {
                int dx = 7 - Math.abs(dy);
                g.fill(x - dx, y + dy, x - dx + 1, y + dy + 1, color);
                g.fill(x + dx, y + dy, x + dx + 1, y + dy + 1, color);
            }
            for (int i = -4; i <= 4; i++) g.fill(x + i, y - i - 1, x + i + 2, y - i + 1, color);
        } else {
            for (int i = -8; i <= 8; i++) {
                int width = Math.max(1, 3 - Math.abs(i) / 3);
                g.fill(x - width, y + i, x + width + 1, y + i + 1, color);
                g.fill(x + i, y - width, x + i + 1, y + width + 1, color);
            }
            ring(g, x, y, 9, 1, 1, color);
        }
    }
    private static void target(GuiGraphicsExtractor g, JsonObject target) {
        var game = Minecraft.getInstance();
        int left = Math.clamp(width(g) / 2 - 85 + TARGET_X.get(), 0, Math.max(0, width(g) - 170));
        int top = Math.clamp(13 + TARGET_Y.get(), 0, Math.max(0, height(g) - 54));
        g.centeredText(game.font, target.get("name").getAsString(), left + 85, top, WHITE);
        bar(g, left, top + 14, 170, 5, number(target, "hp"), number(target, "maxHp"), 0xffd8757f);
        boolean gauges = flag(target, "gaugesEnabled");
        if (gauges) {
            bar(g, left, top + 23, 170, 3, number(target, "toughness"), number(target, "toughnessMax"), 0xffd6b974);
            bar(g, left, top + 29, 170, 3, number(target, "offTune"), number(target, "offTuneMax"), 0xffa88bd9);
            String state=flag(target,"mistuned")?"谐度破坏就绪 · "+CombatClient.abilityKey("tune")
                :flag(target,"toughnessBroken")?"破韧":flag(target,"parryWindow")?"弹刀时机"
                :flag(target,"parryStagger")?"弹刀硬直":flag(target,"accumulationLocked")?"偏谐恢复中":"";
            if(!state.isEmpty())g.centeredText(game.font,state,left+85,top+37,flag(target,"parryWindow")?0xffffd978:0xffe2bfff);
        }
        if (!target.has("effects")) return;
        int i = 0;
        for (var entry : target.getAsJsonArray("effects")) {
            var effect = entry.getAsJsonObject();
            String name = switch (effect.get("id").getAsString()) {
                case "spectro_frazzle" -> "光噪"; case "zani_heliacal_ember" -> "烈阳余烬"; case "aero_erosion" -> "风蚀"; case "havoc_bane" -> "虚湮";
                case "fusion_burst" -> "聚爆"; case "glacio_chafe" -> "霜渐";case "glacio_frost"->"霜冻";case "frostbind"->"霜结";case "electro_flare" -> "电磁";
                case "rupture_shifting"->"震谐偏移";case "strain_shifting"->"集谐偏移";case "rupture_interference"->"震谐干涉";case "strain_interference"->"集谐干涉";
                case "yinlin_sin"->"罪之标记";case "yinlin_punishment"->"惩罚印记";case "chisa_void_scar"->"虚痕";case "mornye_observation"->"观测标记";case "mornye_interference"->"干涉标记";
                case "phrolova_color_weakness"->"攻击削弱";case "galbrena_doom"->"死运既定";default->"技能标记";
            };
            g.text(game.font, name + "×" + (int)number(effect, "layers"), left + (i % 2) * 91, top + (gauges ? 49 : 26) + (i / 2) * 10, 0xffb7c7d7, false); i++;
        }
    }
    static double number(JsonObject object, String key) { return object.has(key) ? object.get(key).getAsDouble() : 0; }
    private static JsonObject object(JsonObject parent, String key) { return parent.has(key) ? parent.getAsJsonObject(key) : new JsonObject(); }
    private static boolean flag(JsonObject object, String key) { return object.has(key) && object.get(key).getAsBoolean(); }
    private static double ratio(double value, double maximum) { return maximum > 0 ? Math.clamp(value / maximum, 0, 1) : 0; }
    static double remaining(double ticks) {
        if (flag(CombatClient.view(), "cinematicPaused")) return Math.max(0, ticks);
        var world = Minecraft.getInstance().level;
        long elapsed = world == null ? 0 : Math.max(0, world.getGameTime() - (long)number(CombatClient.view(), "tick"));
        return Math.max(0, ticks - elapsed);
    }
    private static int elementColor(JsonObject role) {
        String element = McWuwa.catalog().requireByWeapon(role.get("weapon").getAsString()).element();
        return switch (element) {
            case "fusion" -> 0xfff5a177; case "spectro" -> 0xffe9de9c; case "havoc" -> 0xffc99adf;
            case "electro" -> 0xffb7a0ea; case "aero" -> 0xff8ce0c3; case "glacio" -> 0xff8bd8ed; default -> WHITE;
        };
    }
    private static void bar(GuiGraphicsExtractor g, int x, int y, int width, int height, double value, double maximum, int color) {
        g.fill(x, y, x + width, y + height, BACK);
        int filled = (int)Math.round(ratio(value, maximum) * width);
        if (filled > 0) g.fill(x, y, x + filled, y + height, color);
    }
    // Reference proportions remain stable when vanilla GUI scale changes. Portraits must not grow into cards again.
    private static float hudScale(GuiGraphicsExtractor g) { return (float)Math.min(g.guiWidth() / 640.0, g.guiHeight() / 360.0); }
    private static int width(GuiGraphicsExtractor g) { return Math.round(g.guiWidth() / hudScale(g)); }
    private static int height(GuiGraphicsExtractor g) { return Math.round(g.guiHeight() / hudScale(g)); }
    private static void forteBar(GuiGraphicsExtractor g, int center, int y, int width, int height, double value, double maximum, int splits, int color) {
        g.outline(center - width / 2 - 2, y - 2, width + 4, height + 4, 0x878f999b);
        bar(g, center - width / 2, y, width, height, value, maximum, color);
        for (int i = 1; i < splits; i++) g.fill(center - width / 2 + width * i / splits, y, center - width / 2 + width * i / splits + 2, y + height, BACK);
    }
    private static void pips(GuiGraphicsExtractor g, int center, int y, int count, double value, int color) {
        int left = center - (count * 6 - 2) / 2;
        for (int i = 0; i < count; i++) g.fill(left + i * 6, y, left + i * 6 + 4, y + 2, value >= i + 1 ? color : 0x9b4f5b64);
    }
    private static void smallText(GuiGraphicsExtractor g, String text, int x, int y, float scale, int color) {
        var font = Minecraft.getInstance().font;
        g.pose().pushMatrix(); g.pose().translate(x, y); g.pose().scale(scale, scale);
        g.text(font, text, -font.width(text) / 2, 0, color, true); g.pose().popMatrix();
    }
    private static void key(GuiGraphicsExtractor g, String text, int x, int y, int color) {
        int width = Math.max(9, (int)Math.ceil(Minecraft.getInstance().font.width(text) * .65) + 5);
        g.fill(x - width / 2, y - 1, x + (width + 1) / 2, y + 7, 0x90232931);
        smallText(g, text, x, y, .65f, color);
    }
    private static void disk(GuiGraphicsExtractor g, int x, int y, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int dx = (int)Math.floor(Math.sqrt(radius * radius - dy * dy));
            g.fill(x - dx, y + dy, x + dx + 1, y + dy + 1, color);
        }
    }
    private static void portrait(GuiGraphicsExtractor g, Identifier texture, int x, int y) {
        float scale = hudScale(g);
        int cx = Math.round(x * scale), cy = Math.round(y * scale), radius = Math.max(1, Math.round(15 * scale));
        // Work in actual GUI pixels: a fractional-height scissor can vanish after rectangle rounding.
        // This overload uses destination bounds, followed by normalized u0/u1/v0/v1 (not width/height).
        g.pose().pushMatrix(); g.pose().scale(1 / scale, 1 / scale);
        for (int dy = -radius; dy < radius; dy++) {
            int extent = (int)Math.floor(Math.sqrt(radius * radius - (dy + .5) * (dy + .5)));
            g.blit(texture, cx - extent, cy + dy, cx + extent + 1, cy + dy + 1,
                (radius - extent) / (2f * radius), (radius + extent + 1) / (2f * radius),
                (dy + radius) / (2f * radius), (dy + radius + 1) / (2f * radius));
        }
        g.pose().popMatrix();
    }
    /** Contiguous scanline spans use only the supported GUI extractor API. */
    private static void ring(GuiGraphicsExtractor g, int x, int y, int radius, int thickness, double fraction, int color) {
        if (fraction <= 0) return;
        double outer = (radius + .35) * (radius + .35), inner = (radius - thickness + .35) * (radius - thickness + .35);
        for (int dy = -radius; dy <= radius; dy++) {
            int start = Integer.MIN_VALUE;
            for (int dx = -radius; dx <= radius + 1; dx++) {
                double distance = dx * dx + dy * dy;
                double angle = (Math.atan2(dx, -dy) + 2 * Math.PI) % (2 * Math.PI);
                boolean ink = dx <= radius && distance <= outer && distance >= inner && angle <= fraction * 2 * Math.PI;
                if (ink && start == Integer.MIN_VALUE) start = dx;
                if (!ink && start != Integer.MIN_VALUE) { g.fill(x + start, y + dy, x + dx, y + dy + 1, color); start = Integer.MIN_VALUE; }
            }
        }
    }
}
