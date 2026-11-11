package cn.zgnhit.mcwuwa.platform;

import cn.zgnhit.mcwuwa.core.CombatMath;
import cn.zgnhit.mcwuwa.core.CalibrationCadence;
import cn.zgnhit.mcwuwa.platform.entity.EnemyEvents;
import cn.zgnhit.mcwuwa.platform.entity.ConstructEntity;
import com.google.gson.*;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Real gameplay measurements only. These commands never synthesize a sword hit or guess a DPS. */
public final class CalibrationCommands {
    public static final String TARGET_TAG="mc_wuwa:calibration_target";
    private static final int SAMPLE_TICKS=1200;
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID,Session> ACTIVE=new HashMap<>();
    private static final Map<UUID,Completed> COMPLETE=new HashMap<>();
    private static final Map<UUID,VisualCheck> VISUAL_CHECKS=new HashMap<>();
    private static final List<String> VISUAL_MODELS=List.of("aleph_one","aemeath_mecha","jue_dragon","hecate","echo_hecate",
        "camellya_blossom","chisa_chainsaw","hiyuki_frost_blade","cartethyia_storm_blade","rover_wind_blade","yinlin_puppet",
        "lynae_skates","mornye_observation","hsin_construct","xuanling_feathers","echo_crownless","echo_heron","echo_fleurdelys",
        "echo_furnace","echo_sigillum","echo_hyvatia","echo_forged_god","echo_tianyan","echo_puppet_tower","echo_leviathan");
    private static boolean registered;

    private record Completed(Path file,String sha256,double dps,boolean valid) { }
    private record VisualCheck(ServerPlayer owner,UUID weapon,List<ConstructEntity> entities,long start,long deadline,boolean lifetime,Set<String> failures) { }
    private static final class Session {
        final UUID id=UUID.randomUUID();
        final ServerPlayer player;
        final LivingEntity target;
        final long preparedTick;
        final JsonArray samples=new JsonArray();
        final Set<String> failures=new LinkedHashSet<>();
        final CalibrationCadence cadence;
        long startTick=-1,wallStartNanos,lastValidSword=Long.MIN_VALUE/2,attackTick=-1;
        float charge,criticalMultiplier,incomingOriginal,incomingReduced;
        boolean vanillaCritical,pendingSword,pendingPost;
        boolean fireAspectObserved;
        String pendingKind="";
        double damage,swordDamage,fireDamage;
        int swords,fires;
        String controlMethod="manual_player_input";
        Session(ServerPlayer player,LivingEntity target) {
            this.player=player;this.target=target;preparedTick=player.level().getGameTime();
            cadence=new CalibrationCadence(player.getAttributeValue(Attributes.ATTACK_SPEED));
        }
        long now(){return player.level().getGameTime();}
        boolean sampling(){return startTick>=0 && now()<startTick+SAMPLE_TICKS;}
        void fail(String reason) {
            if(failures.size()<100 && failures.add(reason))player.sendSystemMessage(Component.literal("标定样本无效："+reason+"；使用 /mcwuwa calibrate abort 后重新开始。"));
        }
    }

    public static void register() {
        if(registered)return;registered=true;NeoForge.EVENT_BUS.register(new CalibrationCommands());
    }
    /** Exclude only gauges/loot. PlatformRuntime.incoming MUST still apply the default 1512 DEF. */
    public static boolean isTarget(Entity entity){return entity!=null && entity.getTags().contains(TARGET_TAG);}
    /** Call before the Mod's native/periodic damage dispatch; all native damage contaminates this benchmark. */
    public static boolean rejectNativeDamage(Entity target) {
        if(!isTarget(target))return false;
        Session session=find(target);if(session!=null)session.fail("鸣潮原生伤害或效应试图命中标定目标");return true;
    }
    private static Session find(Entity target) {
        for(Session session:ACTIVE.values())if(session.target==target)return session;return null;
    }
    @SubscribeEvent public void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("mcwuwa")
            .requires(source->source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
            .then(Commands.literal("inspect").executes(context->inspect(context.getSource())))
            .then(Commands.literal("status").executes(context->inspect(context.getSource())))
            .then(Commands.literal("visual")
                .then(Commands.literal("test_lifetime").executes(context->visualTest(context.getSource(),true)))
                .then(Commands.literal("test_cleanup").executes(context->visualTest(context.getSource(),false))))
            .then(Commands.literal("calibrate")
                .then(Commands.literal("start").executes(context->start(context.getSource())))
                .then(Commands.literal("status").executes(context->status(context.getSource())))
                .then(Commands.literal("abort").executes(context->abort(context.getSource())))
                .then(Commands.literal("apply").executes(context->apply(context.getSource())))));
    }
    private static int inspect(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player=source.getPlayerOrException();var team=ModAttachments.team(player).state();
        source.sendSuccess(()->Component.literal("鸣潮模式="+team.wuwaMode()+" 队伍="+team.hasTeam()+" 当前槽="+(team.activeSlot()+1)
            +" 伤害基准="+switch(CombatConfig.calibrationScope()){case "server_measurement"->"本服实测";case "bundled_baseline"->"随包实测基准";default->"未启用";}
            +" 有效参考剑DPS="+CombatConfig.scale().measuredSwordDps().orElse(0)+" 伤害映射倍率="+CombatConfig.scale().playabilityMultiplier()),false);
        team.active().ifPresent(role->source.sendSuccess(()->Component.literal("角色="+role.character()+" 武器实例="+role.instanceId()
            +" HP="+role.health()+"/"+role.maxHealth()+" 存活="+role.alive()),false));
        return status(source);
    }
    private static int start(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player=source.getPlayerOrException();
        if(ACTIVE.containsKey(player.getUUID()))return fail(source,"你已有正在进行的标定。");
        String invalid=validatePlayer(player);
        if(invalid!=null)return fail(source,invalid);
        ServerLevel level=player.level();
        Vec3 direction=player.getLookAngle().multiply(1,0,1).normalize();
        if(direction.lengthSqr()<.1)direction=new Vec3(0,0,1);
        LivingEntity target=EntityType.COW.create(level,EntitySpawnReason.COMMAND);
        if(target==null)return fail(source,"无法创建标定目标。");
        target.setPos(player.position().add(direction.scale(2.5)));
        target.addTag(TARGET_TAG);target.addTag(EnemyEvents.SKILL_SUMMON_TAG);
        target.setCustomName(Component.literal("鸣潮标定靶 · 首次满蓄力跳劈开始60秒"));target.setCustomNameVisible(true);
        if(target instanceof Mob mob){mob.setNoAi(true);mob.setPersistenceRequired();}
        Objects.requireNonNull(target.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(2048);
        Objects.requireNonNull(target.getAttribute(Attributes.ARMOR)).setBaseValue(0);
        Objects.requireNonNull(target.getAttribute(Attributes.ARMOR_TOUGHNESS)).setBaseValue(0);
        Objects.requireNonNull(target.getAttribute(Attributes.KNOCKBACK_RESISTANCE)).setBaseValue(1);
        target.setHealth(target.getMaxHealth());
        if(!level.noCollision(target) || target.isInWater() || target.isInLava())return fail(source,"面前没有干燥且无障碍的标定空间；请到平地重试。");
        if(!level.addFreshEntity(target))return fail(source,"目标生成被其他系统拒绝。");
        ACTIVE.put(player.getUUID(),new Session(player,target));COMPLETE.remove(player.getUUID());
        source.sendSuccess(()->Component.literal("已准备真实牛标定靶，保留本Mod 1512防御及原版受伤无敌帧。使用锋利V＋火焰附加II下界合金剑，持续进行满冷却跳跃暴击；第一刀合格命中开始计时。60秒后自动保存实际伤害报告，尚不会修改倍率。"),false);
        return 1;
    }
    /** Explicit GameTest seam using the unchanged measurement/contamination checks. */
    static LivingEntity startDevelopmentActor(ServerPlayer player) throws CommandSyntaxException {
        if(!Boolean.getBoolean("mc_wuwa.smoke") || !(player instanceof net.neoforged.neoforge.common.util.FakePlayer)
            || !(player.level().getServer() instanceof net.minecraft.gametest.framework.GameTestServer))
            throw new IllegalStateException("Automated actor is available only in the opt-in test server");
        if(start(player.createCommandSourceStack())!=1 || !ACTIVE.containsKey(player.getUUID()))
            throw new IllegalStateException("Actual calibration preconditions rejected this actor: "+validatePlayer(player));
        var session=ACTIVE.get(player.getUUID());session.controlMethod="automated_test_actor_with_vanilla_physics";
        return session.target;
    }
    static Optional<Path> developmentReport(ServerPlayer player) {
        var result=COMPLETE.get(player.getUUID());return result==null?Optional.empty():Optional.of(result.file());
    }
    static void abortDevelopmentActor(ServerPlayer player) {
        var session=ACTIVE.get(player.getUUID());if(session!=null)finish(session,false);
    }
    static boolean applyDevelopmentActor(ServerPlayer player) throws CommandSyntaxException {
        if(!Boolean.getBoolean("mc_wuwa.smoke") || !(player instanceof net.neoforged.neoforge.common.util.FakePlayer)
            || !(player.level().getServer() instanceof net.minecraft.gametest.framework.GameTestServer))return false;
        return apply(player.createCommandSourceStack())==1;
    }
    private static String validatePlayer(ServerPlayer player) {
        ItemStack sword=player.getMainHandItem();
        if(!sword.is(Items.NETHERITE_SWORD) || sword.getCount()!=1)return "主手必须为下界合金剑。";
        var enchantments=player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        if(sword.getEnchantments().keySet().size()!=2
            || EnchantmentHelper.getItemEnchantmentLevel(enchantments.getOrThrow(Enchantments.SHARPNESS),sword)!=5
            || EnchantmentHelper.getItemEnchantmentLevel(enchantments.getOrThrow(Enchantments.FIRE_ASPECT),sword)!=2)
            return "基准剑必须且只能带锋利V、火焰附加II两个附魔。";
        if(!Objects.equals(sword.get(DataComponents.ATTRIBUTE_MODIFIERS),Items.NETHERITE_SWORD.getDefaultInstance().get(DataComponents.ATTRIBUTE_MODIFIERS)))
            return "基准剑的属性组件被改写。";
        if(!player.getActiveEffects().isEmpty())return "请移除全部药水、信标等状态效果。";
        if(!player.isAlive() || player.isSpectator() || player.isCreative())return "标定玩家必须存活并处于生存或冒险模式。";
        if(ModAttachments.team(player).state().wuwaMode())return "请切到原版快捷栏位置，退出鸣潮战斗模式。";
        for(var entry:List.of(Map.entry(Attributes.ATTACK_DAMAGE,"minecraft:base_attack_damage"),Map.entry(Attributes.ATTACK_SPEED,"minecraft:base_attack_speed"))) {
            var attribute=player.getAttribute(entry.getKey());
            if(attribute==null)return "玩家缺少攻击属性。";
            if(attribute.getModifiers().stream().anyMatch(modifier->!modifier.id().toString().equals(entry.getValue())))return "检测到额外攻击力或攻速属性增益。";
        }
        if(player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE)!=1 || player.getAttributeBaseValue(Attributes.ATTACK_SPEED)!=4)
            return "玩家基础攻击力或攻速不是原版值。";
        return null;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void attack(AttackEntityEvent event) {
        Session session=find(event.getTarget());if(session==null)return;
        if(event.getEntity()!=session.player){session.fail("其他玩家攻击了标定靶");event.setCanceled(true);return;}
        String invalid=validatePlayer(session.player);
        if(invalid!=null){session.fail(invalid);event.setCanceled(true);return;}
        if(session.startTick>=0 && !session.sampling()){event.setCanceled(true);return;}
        session.attackTick=session.now();session.charge=session.player.getAttackStrengthScale(.5f);
        session.pendingSword=false;session.vanillaCritical=false;
        if(session.charge<.9999f){session.fail("攻击没有完全冷却，充能="+session.charge);event.setCanceled(true);}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void critical(CriticalHitEvent event) {
        Session session=find(event.getTarget());if(session==null || event.getEntity()!=session.player)return;
        session.vanillaCritical=event.isVanillaCritical();session.criticalMultiplier=event.getDamageMultiplier();
        if(session.attackTick!=session.now() || session.charge<.9999f || !event.isVanillaCritical() || !event.isCriticalHit()
            || event.getDamageMultiplier()!=event.getVanillaMultiplier()) {
            session.fail("攻击不是未经修改的原版满冷却跳跃暴击");session.pendingSword=false;return;
        }
        session.pendingSword=true;
        if(session.startTick<0) {
            session.startTick=session.now();session.wallStartNanos=System.nanoTime();
            session.player.sendSystemMessage(Component.literal("标定开始：连续60秒，仅累计实际扣血；保留火焰附加的真实燃烧。"));
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void incoming(LivingIncomingDamageEvent event) {
        Session session=find(event.getEntity());if(session==null)return;
        if(!session.sampling()){event.setCanceled(true);return;}
        DamageSource damage=event.getSource();boolean sword=damage.is(DamageTypes.PLAYER_ATTACK)
            && damage.getEntity()==session.player && damage.getDirectEntity()==session.player
            && session.pendingSword && session.attackTick==session.now();
        boolean fire=damage.is(DamageTypes.ON_FIRE) && session.now()-session.lastValidSword<=160;
        if(!sword && !fire){session.fail("非基准剑或非火焰附加伤害："+damage.type().msgId());event.setCanceled(true);return;}
        if(session.pendingPost){session.fail("嵌套伤害污染采样");event.setCanceled(true);return;}
        double expected=event.getOriginalAmount()*CombatMath.defenseFactor(90,CombatMath.ENEMY_DEFENSE,0,0);
        if(Math.abs(event.getAmount()-expected)>Math.max(.0001,Math.abs(expected)*.00001))
            session.fail("1512防御后的伤害与基准环境不一致：before="+event.getOriginalAmount()+", after="+event.getAmount());
        if(EffectRuntime.defenseReduction(session.target)!=0 || !session.target.getActiveEffects().isEmpty()
            || session.target.getAttributeValue(Attributes.ARMOR)!=0 || session.target.getAttributeValue(Attributes.ARMOR_TOUGHNESS)!=0
            || session.target.getAbsorptionAmount()!=0)
            session.fail("目标受到减防、护甲或状态效果污染");
        session.pendingKind=sword?"sword":"fire";
        session.incomingOriginal=event.getOriginalAmount();session.incomingReduced=event.getAmount();
        if(sword){session.pendingSword=false;session.lastValidSword=session.now();}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void preDamage(LivingDamageEvent.Pre event) {
        Session session=find(event.getEntity());if(session==null || !session.sampling())return;
        // Incoming may still be rejected by vanilla's hurt cooldown. Only an
        // accepted positive pre-health application requires a following Post.
        session.pendingPost=event.getNewDamage()>0;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void damage(LivingDamageEvent.Post event) {
        Session session=find(event.getEntity());if(session==null || !session.pendingPost)return;
        session.pendingPost=false;if(!session.sampling())return;
        float actual=event.getNewDamage();
        if(!Float.isFinite(actual) || actual<0){session.fail("伤害结果不是有效有限数值");return;}
        JsonObject sample=new JsonObject();sample.addProperty("tick",session.now()-session.startTick);
        sample.addProperty("kind",session.pendingKind);sample.addProperty("source",event.getSource().type().msgId());
        sample.addProperty("healthDamage",actual);sample.addProperty("incomingOriginal",session.incomingOriginal);
        sample.addProperty("afterWuwaDefense",session.incomingReduced);sample.addProperty("postAttackInvulnerabilityTicks",event.getPostAttackInvulnerabilityTicks());
        if(session.pendingKind.equals("sword")) {
            sample.addProperty("cooldown",session.charge);sample.addProperty("vanillaCritical",session.vanillaCritical);
            sample.addProperty("criticalMultiplier",session.criticalMultiplier);session.swordDamage+=actual;session.swords++;
            if(actual>0) session.cadence.confirmedSwordHit(session.now());
        } else {session.fireDamage+=actual;session.fires++;}
        session.samples.add(sample);session.damage+=actual;
    }
    @SubscribeEvent public void tick(ServerTickEvent.Post event) {
        tickVisualChecks(event);
        for(Session session:List.copyOf(ACTIVE.values())) {
            if(session.player.level().getServer()!=event.getServer())continue;
            if(!session.player.isAlive() || session.player.level()!=session.target.level() || !session.target.isAlive()
                || event.getServer().getPlayerList().getPlayer(session.player.getUUID())!=session.player) {
                session.fail("玩家离线、死亡、换维度或目标被移除");finish(session,false);continue;
            }
            if(session.startTick>=0 && session.now()>=session.startTick+SAMPLE_TICKS){finish(session,true);continue;}
            if(session.startTick<0 && session.now()-session.preparedTick>=600){session.fail("准备超过30秒未开始");finish(session,false);continue;}
            String invalid=validatePlayer(session.player);if(invalid!=null)session.fail(invalid);
            if(session.startTick>=0 && session.cadence.hasSwordHit() && !session.cadence.sustainedThrough(session.now()))
                session.fail("剑击间隔超过四个完整冷却周期，采样不是持续输出");
            if(session.target.isInWater() || session.target.isInLava())session.fail("目标进入水或岩浆");
            if(session.target.getRemainingFireTicks()>0)session.fireAspectObserved=true;
            // Refill only health; never reset fire, cooldown, invulnerability frames or damage modifiers.
            session.target.setHealth(session.target.getMaxHealth());
            if(session.pendingPost){session.pendingPost=false;session.fail("伤害被其他处理器取消或未产生最终扣血事件");}
        }
    }
    private static void finish(Session session,boolean fullWindow) {
        ACTIVE.remove(session.player.getUUID());session.target.discard();
        if(!fullWindow)session.fail("未完成完整1200 tick采样");
        if(session.swords==0)session.fail("没有实际剑击伤害");
        if(session.startTick>=0 && !session.cadence.sustainedThrough(session.now()))
            session.fail("持续剑击采样中断或没有正数剑击扣血");
        if(!session.fireAspectObserved)session.fail("未观察到真实火焰附加点燃，请确认目标可燃与伤害环境");
        double dps=fullWindow?session.damage/60d:0;
        if(!Double.isFinite(dps) || dps<=0 || dps>100000)session.fail("实测DPS不在配置可接受区间");
        JsonObject report=new JsonObject();report.addProperty("schemaVersion",1);report.addProperty("reportId",session.id.toString());
        report.addProperty("createdAt",Instant.now().toString());report.addProperty("measurement","actual_server_health_damage");
        report.addProperty("player",session.player.getUUID().toString());report.addProperty("dimension",session.target.level().dimension().identifier().toString());
        report.addProperty("target","minecraft:cow");report.addProperty("wwDefense",CombatMath.ENEMY_DEFENSE);
        report.addProperty("targetArmor",0);report.addProperty("preservedVanillaInvulnerability",true);
        report.addProperty("firstAttackTick",session.startTick);report.addProperty("fullWindow",fullWindow);
        report.addProperty("sampleTicks",fullWindow?SAMPLE_TICKS:Math.max(0,session.now()-session.startTick));
        double wall=session.wallStartNanos==0?0:(System.nanoTime()-session.wallStartNanos)/1e9;
        report.addProperty("wallSeconds",wall);report.addProperty("gameSeconds",fullWindow?60:Math.max(0,session.now()-session.startTick)/20d);
        report.addProperty("swordHitCount",session.swords);report.addProperty("fireTickCount",session.fires);
        report.addProperty("cadenceProtocol","maximum idle gap is four complete sword cooldown periods; Minecraft measurement protocol, not native Wuthering Waves data");
        report.addProperty("allowedSwordGapTicks",session.cadence.allowedGapTicks());
        report.addProperty("maximumObservedSwordGapTicks",session.cadence.maximumGapTicks());
        report.addProperty("swordHealthDamage",session.swordDamage);report.addProperty("fireHealthDamage",session.fireDamage);
        report.addProperty("totalHealthDamage",session.damage);report.addProperty("measuredSwordDps",dps);
        report.addProperty("observedFireAspectIgnition",session.fireAspectObserved);
        report.addProperty("fireAccounting","Only actual ON_FIRE Post health loss is counted. Repeated sword hits may keep ignition active while vanilla hurt cooldown prevents fire health loss; zero is retained, never imputed.");
        report.addProperty("observedWallDps",wall>0?session.damage/wall:0);report.addProperty("valid",session.failures.isEmpty());
        report.add("failures",JSON.toJsonTree(session.failures));report.add("samples",session.samples);
        report.addProperty("controlMethod",session.controlMethod);
        report.addProperty("method","Fully cooled vanilla jump criticals, Netherite Sword Sharpness V + Fire Aspect II; actual LivingDamageEvent.Post health loss including fire; no synthetic hits. Control method is recorded separately.");
        try {
            Path folder=session.player.level().getServer().getServerDirectory().resolve("logs/mc_wuwa/calibration");Files.createDirectories(folder);
            Path file=folder.resolve(session.id+".json");byte[] bytes=JSON.toJson(report).getBytes(StandardCharsets.UTF_8);Files.write(file,bytes,StandardOpenOption.CREATE_NEW);
            COMPLETE.put(session.player.getUUID(),new Completed(file,hash(bytes),dps,session.failures.isEmpty()));
            session.player.sendSystemMessage(Component.literal("标定报告已保存："+file+"；DPS="+dps+"，有效="+session.failures.isEmpty()
                +"。只有有效报告可用 /mcwuwa calibrate apply 写入倍率。"));
        } catch(Exception error) {session.player.sendSystemMessage(Component.literal("标定报告写入失败，倍率未改变："+error.getMessage()));}
    }
    private static int status(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player=source.getPlayerOrException();Session active=ACTIVE.get(player.getUUID());Completed last=COMPLETE.get(player.getUUID());
        String message=active!=null?"标定="+(active.startTick<0?"等待第一次合格跳劈":Math.min(60,(active.now()-active.startTick)/20)+"/60秒")
            +" 实际累计伤害="+active.damage+" 无效原因="+active.failures:
            last!=null?"最近报告="+last.file()+" 有效="+last.valid()+" 实测DPS="+last.dps():"当前没有标定会话或本次启动的报告。";
        source.sendSuccess(()->Component.literal(message),false);return 1;
    }
    private static int abort(CommandSourceStack source) throws CommandSyntaxException {
        Session session=ACTIVE.get(source.getPlayerOrException().getUUID());if(session==null)return fail(source,"没有标定会话。");
        session.fail("操作员主动中止");finish(session,false);return 1;
    }
    private static int apply(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player=source.getPlayerOrException();Completed completed=COMPLETE.get(player.getUUID());
        if(ACTIVE.containsKey(player.getUUID()) || completed==null || !completed.valid())return fail(source,"必须先完成本次服务器启动中的有效60秒标定报告。");
        if(!CombatConfig.SPEC.isLoaded())return fail(source,"配置尚未加载，未改变倍率。");
        double oldDps=CombatConfig.REFERENCE_DPS.getAsDouble();String oldEvidence=CombatConfig.CALIBRATION_EVIDENCE.get();
        try {
            if(!hash(Files.readAllBytes(completed.file())).equals(completed.sha256()))return fail(source,"实测报告已被修改，拒绝应用。");
            CombatConfig.REFERENCE_DPS.set(completed.dps());CombatConfig.CALIBRATION_EVIDENCE.set("sha256:"+completed.sha256());CombatConfig.SPEC.save();
            source.sendSuccess(()->Component.literal("已应用真实标定：参考剑DPS="+completed.dps()+"，证据SHA256="+completed.sha256()),true);return 1;
        } catch(Exception error){
            CombatConfig.REFERENCE_DPS.set(oldDps);CombatConfig.CALIBRATION_EVIDENCE.set(oldEvidence);
            return fail(source,"读取报告或保存配置失败，内存倍率已恢复："+error.getMessage());
        }
    }
    /** Two opt-in checks exercise actual tracked server entities, never proxy counters or mocked lifetimes. */
    private static int visualTest(CommandSourceStack source,boolean lifetime) throws CommandSyntaxException {
        ServerPlayer owner=source.getPlayerOrException();
        if(VISUAL_CHECKS.containsKey(owner.getUUID()))return fail(source,"召唤物检查尚未结束。");
        var role=ModAttachments.team(owner).state().active().orElse(null);
        if(role==null || !role.alive())return fail(source,"请先装备一个存活的鸣潮角色，以验证真实武器来源清理。");
        List<ConstructEntity> spawned=new ArrayList<>();long start=owner.level().getGameTime();
        try {
            int modelCount=VISUAL_MODELS.size(),entityCount=modelCount*2,columns=(int)Math.ceil(Math.sqrt(modelCount));
            for(int i=0;i<entityCount;i++) {
                int duration=lifetime?new int[]{1,20,40}[i%3]:100;
                int modelIndex=i%modelCount,copy=i/modelCount;
                Vec3 position=owner.position().add((modelIndex%columns-(columns-1)*.5)*2.5,1+copy*2,(modelIndex/columns+1)*2.5);
                spawned.add(ConstructEntity.spawn(owner.level(),owner,role.instanceId(),VISUAL_MODELS.get(i%VISUAL_MODELS.size()),
                    position,duration,.5f,copy==0,copy==0?"idle":"attack"));
            }
            if(!lifetime)ConstructEntity.removeOwned(owner.getUUID(),role.instanceId());
            VISUAL_CHECKS.put(owner.getUUID(),new VisualCheck(owner,role.instanceId(),List.copyOf(spawned),start,start+(lifetime?42:1),lifetime,new LinkedHashSet<>()));
            source.sendSuccess(()->Component.literal(lifetime?"已生成"+entityCount+"个实体/"+modelCount+"种模型，42 tick后核验寿命清理。请保持来源角色存活、装备不变并观察显示。":
                "已生成"+entityCount+"个实体并调用来源清理；下一tick检查。该调试命令同时清除该武器已有的展示实体。"),false);
            return 1;
        } catch(RuntimeException error){for(var entity:spawned)entity.discard();return fail(source,"生成检查失败，已回收本次对象："+error.getMessage());}
    }
    private static void tickVisualChecks(ServerTickEvent.Post event) {
        for(var entry:List.copyOf(VISUAL_CHECKS.entrySet())) {
            VisualCheck check=entry.getValue();ServerPlayer owner=check.owner();
            if(owner.level().getServer()!=event.getServer())continue;
            long now=owner.level().getGameTime();
            if(check.lifetime())for(var entity:check.entities())
                if(entity.isRemoved() && now<entity.startTick()+entity.durationTicks())check.failures().add("entity removed before its configured lifetime: "+entity.getUUID());
            boolean sourcePresent=event.getServer().getPlayerList().getPlayer(owner.getUUID())==owner && owner.isAlive()
                && check.entities().stream().allMatch(entity->entity.level()==owner.level());
            boolean equipped=false;
            for(int slot=0;slot<3;slot++)if(ModAttachments.team(owner).state().at(slot).map(role->role.instanceId().equals(check.weapon())&&role.alive()).orElse(false))equipped=true;
            if(now<check.deadline() && sourcePresent && equipped)continue;
            long removed=check.entities().stream().filter(Entity::isRemoved).count();
            boolean passed=sourcePresent && equipped && now>=check.deadline() && removed==check.entities().size() && check.failures().isEmpty();
            JsonObject report=new JsonObject();report.addProperty("test",check.lifetime()?"construct_actual_lifetime":"construct_actual_source_cleanup");
            report.addProperty("createdAt",Instant.now().toString());report.addProperty("sourceWeapon",check.weapon().toString());
            report.addProperty("spawned",check.entities().size());report.addProperty("removed",removed);report.addProperty("elapsedTicks",now-check.start());
            report.add("modelIds",JSON.toJsonTree(check.entities().stream().map(ConstructEntity::modelId).distinct().sorted().toList()));
            report.addProperty("sourceRemainedPresent",sourcePresent&&equipped);report.addProperty("passed",passed);
            report.add("failures",JSON.toJsonTree(check.failures()));
            report.addProperty("scope","Server entity lifecycle only; this is not a visual or FPS acceptance result.");
            for(var entity:check.entities())if(!entity.isRemoved())entity.discard();
            VISUAL_CHECKS.remove(entry.getKey());
            try {
                Path directory=event.getServer().getServerDirectory().resolve("logs/mc_wuwa/visual-checks");Files.createDirectories(directory);
                Path path=directory.resolve(UUID.randomUUID()+".json");Files.writeString(path,JSON.toJson(report),StandardOpenOption.CREATE_NEW);
                owner.sendSystemMessage(Component.literal("召唤物清理检查="+passed+"，已移除="+removed+"/"+check.entities().size()+"，报告="+path));
            } catch(Exception error){owner.sendSystemMessage(Component.literal("召唤物清理检查="+passed+"，报告保存失败："+error.getMessage()));}
        }
    }
    private static String hash(byte[] bytes) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static int fail(CommandSourceStack source,String reason){source.sendFailure(Component.literal(reason));return 0;}
    @SubscribeEvent public void stopping(ServerStoppingEvent event) {
        for(Session session:List.copyOf(ACTIVE.values()))if(session.player.level().getServer()==event.getServer()) {
            session.fail("服务器停止");finish(session,false);
        }
        COMPLETE.clear();
        for(var check:VISUAL_CHECKS.values())for(var entity:check.entities())if(!entity.isRemoved())entity.discard();
        VISUAL_CHECKS.clear();
    }
    private CalibrationCommands() { }
}
