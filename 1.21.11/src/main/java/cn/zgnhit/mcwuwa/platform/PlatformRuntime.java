package cn.zgnhit.mcwuwa.platform;

import cn.zgnhit.mcwuwa.content.mechanics.MechanicsState;

import cn.zgnhit.mcwuwa.McWuwa;
import cn.zgnhit.mcwuwa.content.CharacterDefinition;
import cn.zgnhit.mcwuwa.core.*;
import cn.zgnhit.mcwuwa.platform.entity.*;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Minecraft adapter: authoritative ticks, transactions, health and damage hooks. */
public final class PlatformRuntime {
    public enum HitOrigin { DIRECT_ACTIVE, DIRECT_PROJECTILE, AUTONOMOUS, PERIODIC }
    private static final Identifier HEALTH = Identifier.fromNamespaceAndPath("mc_wuwa", "role_health");
    private static final Map<ServerPlayer, Session> SESSIONS = new WeakHashMap<>();
    private static final Map<LivingEntity, GaugeState> GAUGES = new WeakHashMap<>();
    private record SleepRecovery(SleepFinishedTimeEvent event,List<ServerPlayer> sleepers){}
    private static final Map<ServerLevel,List<SleepRecovery>> SLEEP_RECOVERIES = new HashMap<>();
    private static final ThreadLocal<Boolean> NATIVE_DAMAGE = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> PERIODIC_DAMAGE = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<HitRecursionGuard> HIT_GUARD = ThreadLocal.withInitial(() -> new HitRecursionGuard(16));
    private record ConfirmedHit(ServerPlayer player,CharacterInstance role,LivingEntity target,CharacterDefinition.DamageDefinition hit,String action,HitOrigin origin){}
    private static final class PostHits {
        final TriggerQueue<ConfirmedHit> queue=new TriggerQueue<>(4096);UUID chain;int occurrence;
        void begin(){if(chain!=null)throw new IllegalStateException("combat chain already active");chain=UUID.randomUUID();occurrence=0;}
        void put(ConfirmedHit hit){queue.enqueue(new TriggerQueue.TriggerKey(Objects.requireNonNull(chain),"confirmed/"+hit.role.instanceId()+"/"+hit.action,combatTargetId(hit.target),occurrence++),hit);}
        void finish(){if(queue.pending()==0)queue.releaseChain(chain);else queue.abortChain(chain);chain=null;}
    }
    private static final ThreadLocal<PostHits> POST_HITS=ThreadLocal.withInitial(PostHits::new);
    public PlatformRuntime() { EffectRuntime.setDamageSink(PlatformRuntime::effectHit); CalibrationCommands.register(); }
    private static final class Session {
        final ActionRuntime actions = new ActionRuntime(); int lockId = -1, focusId = -1; int inputsThisTick; long inputTick = -1;
        Vec3 dodgeDirection = Vec3.ZERO; long warnedUncalibrated = Long.MIN_VALUE / 2;
        double vanillaCharge = 1;
        String creativeEchoCursor="";
        Set<AttributeModifier> healthModifiers = Set.of();
        Map<UUID,Double> nativeHealth=Map.of();
    }
    private static Session session(ServerPlayer player) { return SESSIONS.computeIfAbsent(player, p -> new Session()); }
    @SubscribeEvent
    public void onKnockback(LivingKnockBackEvent event) {
        if(event.getEntity() instanceof ServerPlayer player&&session(player).actions.preventsRecitalKnockback(player))
            event.setCanceled(true);
    }
    public static long time(ServerPlayer player) { return player.level().getGameTime(); }
    public static ItemStack weaponFor(ServerPlayer player, CharacterInstance role) {
        var team = ModAttachments.team(player);
        for (int i = 0; i < 3; i++) if (team.state().at(i).orElse(null) == role) return team.inventory().getItem(i);
        return ItemStack.EMPTY;
    }
    public static void acceptedEchoCast(ServerPlayer player, CharacterInstance role, int echoId) {
        RolePassiveRuntime.onEchoCast(player,role);session(player).actions.echoCast(player,role,echoId);
    }
    public static boolean phoebeSilentPrayer(ServerPlayer player,LivingEntity target){return session(player).actions.phoebeSilentPrayer(player,target);}
    public static void anomalyConsumed(ServerPlayer player,CharacterInstance actor,LivingEntity target,String effect,int layers){if(layers>0)session(player).actions.suisuiAnomalyActivity(player,actor,target,effect,true);}
    public static void fusionBurstObserved(ServerPlayer player,LivingEntity center){session(player).actions.fusionBurstObserved(player,center);}
    public static boolean returnCharging(ServerPlayer player) { return session(player).actions.returnCharging(player); }
    public static boolean ghostActive(ServerPlayer player) { return session(player).actions.ghostActive(player); }
    public static void electroRageReleased(ServerPlayer player,LivingEntity target,int rage) { session(player).actions.electroRageReleased(player,target,rage); }
    public static void electroLayersApplied(ServerPlayer player,CharacterInstance actor,LivingEntity target,int layers) { session(player).actions.electroLayersApplied(player,actor,target,layers); }
    public static MechanicsState mechanicsView(ServerPlayer player,CharacterInstance role){return session(player).actions.ruleState(role);}
    public static double currentEnergyRegen(ServerPlayer player,CharacterInstance role){return session(player).actions.energyRegenWithoutRolePassives(player,role);}
    public static boolean cappedAeroSettlementActive(ServerPlayer player){return session(player).actions.cappedAeroSettlementActive(player);}
    public static int unisonStacks(ServerPlayer player,CharacterInstance role){return session(player).actions.unisonStacks(player,role);}
    public static void soloEffectApplied(ServerPlayer player,CharacterInstance actor,LivingEntity target,String id){session(player).actions.soloEffectApplied(player,actor,target,id);}
    public static void teamChanged(ServerPlayer player) {
        session(player).actions.clear(player); EchoRuntime.clear(player); RolePassiveRuntime.clear(player);
        ConstructEntity.removeOwned(player.getUUID(), null);
    }
    public static GaugeState gauge(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel level)) throw new IllegalArgumentException("server gauge only");
        // Safe disabled view for external callers; Player never enters the enemy reset/cleanup map.
        if (target instanceof HecateEntity) return new GaugeState(EnemyProfile.forEntity("minecraft:player",false,true),level.getGameTime());
        if (target instanceof Player) return new GaugeState(EnemyEvents.profile(target), level.getGameTime());
        return GAUGES.computeIfAbsent(target, e -> new GaugeState(EnemyEvents.profile(e), level.getGameTime()));
    }
    public static boolean canAttack(ServerPlayer player, LivingEntity target) {
        if(target instanceof HecateEntity hecate) {
            var owner=hecate.ownerPlayer();return hecate.foreground()&&owner!=null&&canAttack(player,owner);
        }
        if (target == player || player.level() != target.level() || !player.isAlive() || !target.isAlive() || target.isSpectator() || target.isInvulnerable()) return false;
        // ServerPlayer#canHarmPlayer already applies server PvP and scoreboard friendly fire.
        // LivingEntity#canAttack is an AI filter that incorrectly rejects Player targets in Peaceful.
        if (target instanceof Player other) return player.canHarmPlayer(other) && other.canBeSeenAsEnemy();
        return player.canAttack(target) && !player.isAlliedTo(target);
    }
    /** A proxy and its owner share one recipient in an action's per-hit ledger. */
    public static UUID combatTargetId(LivingEntity target) {
        if(target instanceof HecateEntity hecate&&hecate.ownerPlayer()!=null)return hecate.ownerPlayer().getUUID();
        return target.getUUID();
    }
    public static boolean evadableDirect(DamageSource source) {
        return !PERIODIC_DAMAGE.get()&&source.getEntity()!=null&&!source.is(DamageTypeTags.IS_FIRE);
    }
    public static void creativeCursor(ServerPlayer player,Network.CreativeCursor packet) {
        var attachment=ModAttachments.team(player);
        if(!player.getAbilities().instabuild||!player.isAlive()||player.isSpectator()||player.containerMenu!=player.inventoryMenu
            ||packet.sequence()<0||packet.sequence()<=attachment.lastInputSequence)return;
        attachment.lastInputSequence=packet.sequence();
        var incoming=packet.carried().copy();
        if(EchoData.templateSpecies(incoming).isPresent())return;
        String identity=incoming.getItem() instanceof WeaponItem?WeaponData.tag(incoming).getStringOr("instance",""):
            EchoData.valid(incoming)?EchoData.data(incoming).getStringOr("instance",""):"";
        if(!identity.isEmpty()) {
            for(var slot:player.inventoryMenu.slots){
                // Slot0 is a view of the same equipped object, never another physical item.
                if(slot.container==player.getInventory()&&slot.getContainerSlot()==0&&attachment.hasTeam())continue;
                var item=slot.getItem();
                String other=item.getItem() instanceof WeaponItem?WeaponData.tag(item).getStringOr("instance",""):
                    EchoData.valid(item)?EchoData.data(item).getStringOr("instance",""):"";
                if(identity.equals(other)){Network.creativeCursor(player,packet.sequence());return;}
            }
            for(int role=0;role<3;role++)for(int echo=0;echo<5;echo++)
                if(identity.equals(WeaponData.tag(attachment.inventory().getItem(role)).getCompoundOrEmpty("echo_"+echo).getStringOr("instance","")))
                    {Network.creativeCursor(player,packet.sequence());return;}
        }
        player.inventoryMenu.setCarried(incoming);
        Network.rememberCreativeCursor(player);
        // This is an observation of the native creative cursor, not an editor
        // result. Do not echo it back and overwrite a newer local click.
    }
    public static void creativeTeamSlot(ServerPlayer player,Network.CreativeTeamSlot packet) {
        var attachment=ModAttachments.team(player);var team=attachment.state();
        long now=time(player);var session=session(player);
        if(!player.getAbilities().instabuild||player.isSpectator()||!player.isAlive()||player.containerMenu!=player.inventoryMenu
            ||team.inCombat()||packet.slot()<0||packet.slot()>2||packet.sequence()<0||packet.sequence()<=attachment.lastInputSequence)return;
        if(session.inputTick!=now){session.inputTick=now;session.inputsThisTick=0;}
        if(++session.inputsThisTick>12)return;
        attachment.lastInputSequence=packet.sequence();
        ItemStack incoming=packet.carried().copy();
        if(!incoming.isEmpty()) {
            if(!attachment.canEquip(packet.slot(),incoming))return;
            try{WeaponData.read(incoming);}catch(IllegalArgumentException|IllegalStateException invalid){return;}
            UUID instance=UUID.fromString(WeaponData.tag(incoming).getStringOr("instance",""));
            for(var slot:player.inventoryMenu.slots) {
                if(!(slot.getItem().getItem() instanceof WeaponItem))continue;
                String identity=WeaponData.tag(slot.getItem()).getStringOr("instance","");
                try{if(!identity.isEmpty()&&UUID.fromString(identity).equals(instance)){Network.creativeCursor(player,packet.sequence());return;}}
                catch(IllegalArgumentException invalidExisting){ }
            }
        }
        // The source cursor is permitted creative content, not a duplicated physical backpack stack.
        ItemStack outgoing=attachment.inventory().removeItemNoUpdate(packet.slot());
        attachment.inventory().setItem(packet.slot(),incoming);
        player.inventoryMenu.setCarried(outgoing);
        Network.creativeCursor(player,packet.sequence());Network.sync(player);
        // After the last unequip getItem(0) no longer resolves the team reference.
        // Explicitly invalidate vanilla's cached projected hotbar, including creative tabs.
        if(!attachment.hasTeam())Network.correctCreativeSlot(player,36);
    }
    public static void creativeEchoSlot(ServerPlayer player,Network.CreativeEchoSlot packet){
        var attachment=ModAttachments.team(player);long now=time(player);var session=session(player);
        if(!player.getAbilities().instabuild||!player.isAlive()||player.isSpectator()||player.containerMenu!=player.inventoryMenu||attachment.state().inCombat()
            ||packet.slot()<0||packet.slot()>=15||packet.sequence()<0||packet.sequence()<=attachment.lastInputSequence)return;
        if(session.inputTick!=now){session.inputTick=now;session.inputsThisTick=0;}if(++session.inputsThisTick>12)return;
        attachment.lastInputSequence=packet.sequence();var incoming=packet.carried().copy();
        if(!incoming.isEmpty()){
            if(!EchoData.valid(incoming))return;
            String identity=EchoData.data(incoming).getStringOr("instance","");
            for(var slot:player.inventoryMenu.slots)if(slot.getItem().is(ModItems.ECHO.get())&&EchoData.data(slot.getItem()).getStringOr("instance","").equals(identity)){Network.creativeCursor(player,packet.sequence());return;}
            for(int role=0;role<3;role++)for(int echo=0;echo<5;echo++)if(WeaponData.tag(attachment.inventory().getItem(role)).getCompoundOrEmpty("echo_"+echo).getStringOr("instance","").equals(identity)){Network.creativeCursor(player,packet.sequence());return;}
        }
        // Vanilla creative pickup already removed the physical source via its normal slot packet.
        // Keep a rejected valid candidate on the authoritative cursor instead of losing it.
        player.inventoryMenu.setCarried(incoming);swapEcho(player,packet.slot());Network.creativeCursor(player,packet.sequence());
    }
    public static void creativeEchoCreate(ServerPlayer player,Network.CreativeEchoCreate packet) {
        var attachment=ModAttachments.team(player);var session=session(player);long now=time(player);
        if(!player.getAbilities().instabuild||!player.isAlive()||player.isSpectator()||player.containerMenu!=player.inventoryMenu
            ||packet.sequence()<0||packet.sequence()<=attachment.lastInputSequence)return;
        if(session.inputTick!=now){session.inputTick=now;session.inputsThisTick=0;}if(++session.inputsThisTick>12)return;
        attachment.lastInputSequence=packet.sequence();var id=Identifier.tryParse(packet.species());
        var cursor=player.inventoryMenu.getCarried();
        boolean unusedCatalogueItem=EchoData.valid(cursor)&&EchoData.data(cursor).getStringOr("instance","").equals(session.creativeEchoCursor);
        if(id==null||EchoData.speciesCost(id).isEmpty()||!cursor.isEmpty()&&!unusedCatalogueItem){Network.creativeCursor(player,packet.sequence());return;}
        var result=EchoData.create(id,player.getRandom());session.creativeEchoCursor=EchoData.data(result).getStringOr("instance","");
        player.inventoryMenu.setCarried(result);Network.creativeCursor(player,packet.sequence());
    }
    /** Handles shift-pick/drop packets and a very fast placement before the pickup response arrives. */
    public static boolean resolveCreativeEchoTemplate(ServerPlayer player,ItemStack incoming) {
        var species=EchoData.templateSpecies(incoming);if(species.isEmpty())return false;
        if(!player.getAbilities().instabuild||!player.isAlive()||player.isSpectator())return false;
        var cursor=player.inventoryMenu.getCarried();var session=session(player);
        var cursorData=EchoData.data(cursor);
        boolean pending=EchoData.valid(cursor)&&cursorData.getStringOr("instance","").equals(session.creativeEchoCursor)
            &&cursorData.getStringOr("species","").equals(species.get().toString());
        var result=pending?cursor:EchoData.create(species.get(),player.getRandom());
        incoming.applyComponentsAndValidate(result.getComponentsPatch());return true;
    }
    private static boolean swapEcho(ServerPlayer player,int index){
        var attachment=ModAttachments.team(player);var team=attachment.state();
        if(player.containerMenu!=player.inventoryMenu||team.inCombat()||index<0||index>=15||!player.isAlive()||player.isSpectator())return false;
        int teamSlot=index/5,echoSlot=index%5;if(team.at(teamSlot).isEmpty())return false;
        ItemStack weapon=attachment.inventory().getItem(teamSlot),incoming=player.inventoryMenu.getCarried();
        if(!incoming.isEmpty()&&!EchoData.valid(incoming)){Network.feedback(player,teamSlot,"请先在背包拿起一个有效声骸");return false;}
        var tag=WeaponData.tag(weapon);var ops=player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        ItemStack outgoing=tag.read("echo_item_"+echoSlot,ItemStack.CODEC,ops).orElse(ItemStack.EMPTY);
        var next=incoming.isEmpty()?new net.minecraft.nbt.CompoundTag():EchoData.data(incoming);
        int cost=next.getIntOr("cost",0);String species=next.getStringOr("species","");
        for(int i=0;i<5;i++)if(i!=echoSlot){var other=tag.getCompoundOrEmpty("echo_"+i);cost+=other.getIntOr("cost",0);
            if(!species.isEmpty()&&species.equals(other.getStringOr("species",""))){Network.feedback(player,teamSlot,"不能重复装备同物种声骸");return false;}
            if(!species.isEmpty()&&next.getStringOr("instance","").equals(other.getStringOr("instance","")))return false;
        }
        if(cost>12){Network.feedback(player,teamSlot,"声骸 COST 总和不能超过 12");return false;}
        saveCurrentHealth(player,attachment);
        if(incoming.isEmpty()){tag.remove("echo_item_"+echoSlot);tag.remove("echo_"+echoSlot);}else{tag.store("echo_item_"+echoSlot,ItemStack.CODEC,ops,incoming.copy());tag.put("echo_"+echoSlot,next);}
        weapon.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));player.inventoryMenu.setCarried(outgoing);
        applyHealth(player,attachment,false);player.inventoryMenu.broadcastChanges();Network.sync(player);Network.feedback(player,teamSlot,"");return true;
    }
    public static void command(ServerPlayer player, Network.Command packet) {
        var intent = ClientIntent.parse(packet.operation());
        if (intent.isEmpty()) return;
        var attachment = ModAttachments.team(player); var team = attachment.state(); Session session = session(player);
        long now = time(player);
        if (packet.sequence() < 0 || packet.sequence() <= attachment.lastInputSequence || !Float.isFinite(packet.strafe()) || !Float.isFinite(packet.forward())) return;
        if (session.inputTick != now) { session.inputTick = now; session.inputsThisTick = 0; }
        if (++session.inputsThisTick > 12) return;
        attachment.lastInputSequence = packet.sequence();
        String cancelledBySlot=team.selectHotbar(player.getInventory().getSelectedSlot());
        if(!cancelledBySlot.isEmpty())session.actions.cancel(player);
        String operation = intent.orElseThrow().name(); int index = packet.argument();
        if (operation.equals("SYNC")) { Network.sync(player); return; }
        if(operation.startsWith("BIKE_JUMP_")){
            if(player.getVehicle() instanceof cn.zgnhit.mcwuwa.platform.entity.SurveyMotorcycleEntity bike)
                bike.jumpInput(player,operation.equals("BIKE_JUMP_PRESS"),operation.equals("BIKE_JUMP_CANCEL"),index);
            return;
        }
        if(operation.equals("SCENE_INTERACTION")){session.actions.sceneInteraction(player);return;}
        if(operation.equals("TRAVEL")){
            if(player.containerMenu==player.inventoryMenu){
                if(player.getVehicle() instanceof cn.zgnhit.mcwuwa.platform.entity.SurveyMotorcycleEntity bike){
                    session.actions.travel(player); // The live vehicle HUD already reports state; do not cover the forte bar with a duplicate toast.
                    return;
                }
                if(!session.actions.travel(player))player.displayClientMessage(Component.literal("流光突进：需要机兵形态、流溢辉光大于200，且当前没有施放动作"),true);
                else player.displayClientMessage(Component.literal(session.actions.ruleState(team.active().orElseThrow()).timer("aemeath_flight")>0?"流光突进：空格升空，潜行下降，疾跑加速，J 结束":"流光突进已结束"),true);
            }
            return;
        }
        if(Set.of("ECHO","ECHO_HOLD","MODE_NEXT","CHAIN","REFINE","ECHO_SWAP").contains(operation))session.actions.sceneInteraction(player);
        if(operation.equals("CREATIVE_RECHARGE")) {
            if(player.gameMode.getGameModeForPlayer()!=net.minecraft.world.level.GameType.CREATIVE||!player.isAlive()
                ||player.isSpectator()||player.containerMenu!=player.inventoryMenu)return;
            session.actions.rechargeForCreative(player);attachment.flushWeapons();Network.sync(player);
            player.displayClientMessage(Component.literal("已补满队伍共鸣能量、协奏能量、回路资源和耐力"),true);return;
        }
        if (operation.equals("DODGE_RELEASE")) { team.stamina().releaseSprint();session.actions.releaseDodgeMovement(player);return; }
        if (operation.equals("CANCEL_HEAVY")) { session.actions.cancelHeldInput(player); return; }
        if (operation.equals("CANCEL_LIBERATION")) { session.actions.cancelLiberationCharge(player); return; }
        if (operation.equals("ECHO_RELEASE")) { EchoRuntime.release(player); return; }
        if(operation.equals("CANCEL_SKILL")){session.actions.cancelHeldSkill(player);return;}
        if (operation.equals("AIM_RELEASE")) { session.actions.aim(player, false); return; }
        if (operation.equals("FOCUS")) {
            var entity = player.level().getEntity(index);
            session.focusId = entity instanceof LivingEntity target && canAttack(player, target) && target.distanceToSqr(player) <= 1024 && player.hasLineOfSight(target) ? index : -1;
            return;
        }
        boolean inventory = player.containerMenu == player.inventoryMenu;
        if (operation.equals("ECHO_SWAP")) {
            swapEcho(player,index);return;
        }
        if (operation.equals("MODE_NEXT")) {
            if (!inventory || team.inCombat() || index < 0 || index >= 3 || team.at(index).isEmpty()) return;
            var role = team.at(index).orElseThrow(); var choices = new ArrayList<>(role.definition().selectableModes()); Collections.sort(choices);
            String previous=role.selectedMode(),next=choices.get((choices.indexOf(previous)+1)%choices.size());
            if(!next.equals(previous)){role.setSelectedMode(next,true);session.actions.modeChanged(player,role,previous);}
            attachment.flushWeapons(); Network.sync(player); return;
        }
        if (operation.equals("REVIVE") || operation.equals("CHAIN") || operation.equals("REFINE")) {
            if (index < 0 || index > 2 || team.at(index).isEmpty() || (!operation.equals("REVIVE") && (!inventory || team.inCombat()))) return;
            UUID request = UUID.nameUUIDFromBytes((player.getUUID() + ":" + packet.sequence()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Progression.Materials materials = (material, count) -> consume(player, switch (material) { case EMERALD -> Items.EMERALD; case DIAMOND -> Items.DIAMOND; case GOLDEN_APPLE -> Items.GOLDEN_APPLE; }, count);
            var result = switch (operation) {
                case "CHAIN" -> attachment.progression.upgradeChain(request, team.at(index).orElseThrow(), materials);
                case "REFINE" -> attachment.progression.upgradeRefinement(request, team.at(index).orElseThrow(), materials);
                default -> attachment.progression.revive(request, team, index, materials);
            };
            if (result.success() && operation.equals("CHAIN")) session.actions.progressionChanged(team.at(index).orElseThrow());
            if (result.success() && operation.equals("REVIVE")) applyHealth(player, attachment, false);
            attachment.flushWeapons(); Network.sync(player);
            if (!result.success()) {
                String reason=switch(result.outcome()){
                    case MATERIALS -> switch(operation){case "CHAIN"->"需要 6 个绿宝石";case "REFINE"->"需要 3 个钻石";default->"需要 1 个普通金苹果";};
                    case MAXIMUM -> operation.equals("CHAIN")?"共鸣链已达到 C6":"精炼已达到 S5";
                    case INVALID_REVIVE -> "角色不可复活或公共冷却尚未结束";
                    default -> "重复或无效请求已拒绝";
                };Network.feedback(player,index,reason);
            }else Network.feedback(player,index,"");return;
        }
        if (operation.equals("SWITCH")) {
            if (index < 0 || index > 2 || !player.isAlive()) return;
            if (!session.actions.allowsVoluntarySwitch(player)) return;
            // A valid switch cancels unfinished body work, including echo transformations.
            // Reject invalid targets/cooldowns before cancelling the outgoing action.
            if (index == team.activeSlot() || team.at(index).isEmpty() || team.snapshot().switchCooldown() > 0) return;
            if (!team.at(index).orElseThrow().alive()) { player.displayClientMessage(Component.literal("角色已倒下：在背包点击复活，消耗 1 金苹果"),true); return; }
            saveCurrentHealth(player, attachment); var outgoing = team.active().orElse(null); session.actions.cancelForSwitch(player);
            var result = team.switchTo(index);
            if (result.success()) { if (result.consumedJinhsiUnison() && outgoing != null) session.actions.consumedUnison(outgoing); applyHealth(player, attachment, false); session.actions.switched(player, outgoing, result.introOutro()); EchoRuntime.onSwitch(player, outgoing, result.introOutro()); Network.sync(player); }
            else if (result.failure() == TeamState.Failure.DOWNED) player.displayClientMessage(Component.literal("角色已倒下：在背包点击复活，消耗 1 金苹果"),true);
            return;
        }
        if (!team.canAct() || !player.isAlive() || player.isSpectator() || !inventory) return;
        switch (operation) {
            case "DODGE_PRESS" -> {
                var travelRole=team.active().orElseThrow();
                var travelState=session.actions.ruleState(travelRole);
                if(travelState.characterId().equals("suoming")&&travelState.form().equals("calamity"))return;
                // Recital owns ordinary inputs; generic sprint must not bypass its reducer lock.
                if(travelState.characterId().equals("ciaccona")&&travelState.form().equals("performing"))return;
                if(session.actions.ruleState(travelRole).timer("aemeath_flight")>0)return;
                if(session.actions.shorekeeperEscape(player))return;
                if(session.actions.sawBackDodge(player,packet.forward())){team.stamina().claimMovementInput();return;}
                if(session.actions.hecateDodge(player,SpecialMovementRuntime.dodgeDirection(player,packet.strafe(),packet.forward())))return;
                if(session.actions.dodgeMovement(player,packet.forward()>0))return;
                session.dodgeDirection = SpecialMovementRuntime.dodgeDirection(player,packet.strafe(),packet.forward());
                boolean airborne=!player.onGround();
                team.stamina().observeGrounded(!airborne);
                var role=team.active().orElseThrow();var mechanics=session.actions.ruleState(role);
                boolean unlimitedAir=airborne&&role.character()==CharacterId.JINHSI&&mechanics.form().equals("incarnation");
                if(airborne&&role.character()==CharacterId.QINGXIAO&&!session.actions.canQingxiaoSwordDodge(player))return;
                // The original specifies a stamina cost but no MC distance/cost: twenty is authored.
                if(unlimitedAir&&team.stamina().snapshot().stamina()<20)return;
                if(team.stamina().pressSprint(airborne,unlimitedAir)) {
                    if(unlimitedAir)team.stamina().spend(20);
                    session.actions.normalDodgeAccepted(player,packet.forward());
                }
            }
            case "DODGE_RELEASE" -> team.stamina().releaseSprint();
            case "LOCK" -> {
                var entity = player.level().getEntity(index);
                session.lockId = session.lockId == index ? -1 : entity instanceof LivingEntity target && canAttack(player, target)
                    && target.distanceToSqr(player) <= 1024 && player.hasLineOfSight(target) ? index : -1;
                if(session.lockId>=0&&entity instanceof LivingEntity target)team.active().filter(role->role.character()==CharacterId.CHISA)
                    .ifPresent(role->session.actions.ruleMark(player,role,target,"chisa_void_scar",1,600));
            }
            case "TUNE_BREAK" -> {
                // Each target owns an atomic consume, so competing players cannot double-consume mistune.
                var role=team.active().orElseThrow();
                if(CombatConfig.scale().measuredSwordDps().isEmpty())return;
                Element element=Element.valueOf(McWuwa.catalog().requireByWeapon(role.character().weaponPath()).element().toUpperCase(Locale.ROOT));
                double tuneDamage=GlobalCombatRules.tuneDamage(session.actions.tuneBreakAmplification(player,role));
                for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(6), t -> !(t instanceof Player) && canAttack(player, t))) {
                    var result = gauge(target).tryTuneBreak(target.distanceToSqr(player), player.hasLineOfSight(target), target.isAlive(), true, now,
                        ()->effectHit(player,new EffectSource(player.getUUID(),role.instanceId(),role.character(),UUID.randomUUID(),UUID.randomUUID()),target,element,tuneDamage,"tune/break"));
                    if (result.consumed()) {
                        team.recordCombatActivity();if(result.toughnessBroken())interruptMob(target);
                        var activated=EffectRuntime.tuneBreakAccepted(player,role,target);
                        session.actions.tuneBreakAccepted(player,target,activated);
                    }
                }
            }
            case "SIX_JUMP_PRESS", "SIX_JUMP_RELEASE", "SIX_JUMP_HOLD", "SIX_JUMP_CANCEL" -> session.actions.sixJump(player,operation);
            case "ECHO" -> { if (!session.actions.busy()) EchoRuntime.cast(player); }
            case "ECHO_HOLD" -> { if (!session.actions.busy()) EchoRuntime.cast(player, true); }
            case "AIM_PRESS" -> session.actions.aim(player, true);
            case "NOTE_GREEN", "NOTE_YELLOW", "BASIC", "BASIC_RELEASE", "HEAVY_START", "HEAVY_RELEASE", "HEAVY_FULL", "AIR", "SKILL", "SKILL_HOLD", "SKILL_RELEASE", "LIBERATION", "LIBERATION_HOLD", "LIBERATION_RELEASE", "JUMP"
                -> session.actions.request(player, operation);
            default -> { }
        }
    }
    private static boolean consume(ServerPlayer player, Item item, int count) {
        var inventory = player.getInventory(); int available = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) if (inventory.getItem(slot).is(item)) available += inventory.getItem(slot).getCount();
        if (available < count) return false;
        int left = count;
        for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) { var stack = inventory.getItem(slot); if (stack.is(item)) { int take = Math.min(left, stack.getCount()); stack.shrink(take); left -= take; } }
        inventory.setChanged(); return true;
    }
    @SubscribeEvent public void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CombatAdvancements.inventory(player);
        var attachment = ModAttachments.team(player); var team = attachment.state(); Session session = session(player);
        team.stamina().observeGrounded(player.onGround());
        if (!team.hasTeam()) {
            CinematicTimeRuntime.clear(player);
            session.actions.clearMovement(player);
            // Combat/respawn cooldowns belong to the online player even before the first weapon is equipped.
            team.tickOnline(1, false, player.getFoodData().getFoodLevel() > 6 || player.getAbilities().mayfly);
            return;
        }
        saveCurrentHealth(player, attachment);
        if (!session.healthModifiers.equals(externalHealthModifiers(player))) applyHealth(player, attachment, false);
        String cancelled = team.selectHotbar(player.getInventory().getSelectedSlot()); if (!cancelled.isEmpty()) session.actions.cancel(player);
        if (player.tickCount % 10 == 0) team.setNearbyAggro(!player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32), mob -> (mob.getTarget() == player||mob.getTarget() instanceof HecateEntity relay&&relay.ownerPlayer()==player) && mob.distanceToSqr(player) <= 1024).isEmpty());
        boolean movement = player.isSprinting() && !player.isSwimming() && !player.onClimbable() && !player.isFallFlying();
        var travelInput=player.getLastClientInput();
        boolean swimming=team.wuwaMode()&&player.isInWater()&&!player.isPassenger()&&!player.getAbilities().instabuild
            &&(travelInput.forward()||travelInput.backward()||travelInput.left()||travelInput.right()||travelInput.jump()||travelInput.shift());
        boolean cinematic=CinematicTimeRuntime.paused(player);
        CinematicTimeRuntime.holdAir(player,cinematic);
        double roleSprintCost=team.canAct()&&movement&&!swimming&&player.onGround()&&!player.isPassenger()
            ?session.actions.groundSprintStaminaCost(player):0;
        boolean roleDrainsStamina=roleSprintCost>0||session.actions.ownsContinuousStaminaDrain(player);
        // Character sprint replaces the generic combat fee: Lynae10/s and
        // Hsin fox5/s (MC rate), including outside combat; never charge twice.
        if(!cinematic)team.tickOnline(1, movement&&!swimming&&!roleDrainsStamina, player.getFoodData().getFoodLevel() > 6 || player.getAbilities().mayfly,
            session.actions.allowsStaminaRecovery(player)&&!swimming&&!roleDrainsStamina);
        if(roleSprintCost>0&&!cinematic&&!team.stamina().spend(roleSprintCost))player.setSprinting(false);
        if(swimming&&!cinematic&&!roleDrainsStamina){
            boolean cantarella=team.active().map(role->role.character()==CharacterId.CANTARELLA).orElse(false);
            double cost=cantarella?TraversalMotion.CANTARELLA_SWIM_STAMINA_COST:TraversalMotion.SWIM_STAMINA_COST;
            if(!team.stamina().spend(cost))player.setSprinting(false);
        }
        if (team.stamina().snapshot().stamina() < .25 && movement && team.inCombat()) player.setSprinting(false);
        double dash = team.stamina().requestedDashDistanceThisTick();
        if (!cinematic && dash > 0) { player.move(MoverType.SELF, session.dodgeDirection.scale(dash)); player.hurtMarked = true; }
        session.actions.tick(player);
        EchoRuntime.tick(player);
        RolePassiveRuntime.tick(player);
        if(!session.nativeHealth.equals(nativeHealthValues(player,attachment)))applyHealth(player,attachment,false);
        if (player.tickCount % 5 == 0) Network.sync(player);
    }
    @SubscribeEvent public void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) { finishSleepRecovery(level); EffectRuntime.tick(level); }
        GAUGES.entrySet().removeIf(entry -> entry.getKey() instanceof Player || entry.getKey().isRemoved() || !entry.getKey().isAlive());
        for (var entry : GAUGES.entrySet()) {
            LivingEntity target = entry.getKey(); var level = (ServerLevel) target.level();
            if(CinematicTimeRuntime.pausesEnemy(target)){entry.getValue().pauseOneTick();continue;}
            if (entry.getValue().tickCombat(level.getGameTime(), target instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive() && mob.getTarget().level()==level)) EffectRuntime.clearTarget(target);
            var status = entry.getValue().snapshot(level.getGameTime());
            if (status.brokenUntil() > level.getGameTime()) interruptMob(target);
        }
    }
    private static void interruptMob(LivingEntity target) { if (target instanceof Mob mob) {
        mob.getNavigation().stop(); mob.setDeltaMovement(0, mob.getDeltaMovement().y, 0);
        if(mob instanceof EchoBossEntity boss)boss.cancelAttack();
        if(mob instanceof AlephEntity aleph)aleph.interruptPendingAttack();
    } }
    @SubscribeEvent public void focusTimeFlow(EntityTickEvent.Pre event){
        if(!(event.getEntity() instanceof Mob)&&CinematicTimeRuntime.pausesEnemy(event.getEntity())){
            // Level advances age before this cancellable body step. Clouds use
            // that age for expiry, so cancelling tick alone still spends lifetime.
            event.getEntity().tickCount=Math.max(0,event.getEntity().tickCount-1);
            CinematicTimeRuntime.hazardStopped(event.getEntity());
            CinematicTimeRuntime.enemyStepCancelled(event.getEntity());event.setCanceled(true);return;
        }
        if(!(event.getEntity() instanceof Mob mob))return;
        if(mob.level() instanceof ServerLevel){
            boolean stopped=EffectRuntime.augustaTimeStopped(mob)||CinematicTimeRuntime.pausesEnemy(mob);
            if(mob instanceof EchoBossEntity boss)boss.realmTimePaused(stopped);
            if(stopped){
                mob.tickCount=Math.max(0,mob.tickCount-1);
                if(CinematicTimeRuntime.pausesEnemy(mob)){CinematicTimeRuntime.hazardStopped(mob);CinematicTimeRuntime.enemyStepCancelled(mob);}
                // Native weapon users must not acquire seven seconds of hurt immunity
                // merely because the victim's body clock is stopped.
                mob.invulnerableTime=Math.max(0,mob.invulnerableTime-1);
                event.setCanceled(true);
            }else if(EffectRuntime.skipFocusTick(mob))event.setCanceled(true);
        }else if(mob instanceof EchoBossEntity boss&&boss.realmTimePaused())event.setCanceled(true);
    }
    public static boolean aiInterrupted(Mob mob) {
        if (!(mob.level() instanceof ServerLevel level)) return false;
        var state = GAUGES.get(mob);
        return mob instanceof AlephEntity aleph && aleph.isStaggered() || EffectRuntime.isFrozen(mob)
            || state != null && state.snapshot(level.getGameTime()).brokenUntil() > level.getGameTime();
    }
    @SubscribeEvent public void noVanillaAttack(AttackEntityEvent event) {
        if (actualWuwaMode(event.getEntity())) event.setCanceled(true);
        else if (event.getEntity() instanceof ServerPlayer player) session(player).vanillaCharge = Math.clamp(player.getAttackStrengthScale(.5f), 0, 1);
    }
    @SubscribeEvent public void noMining(PlayerInteractEvent.LeftClickBlock event) {
        if (actualWuwaMode(event.getEntity())) event.setCanceled(true);
    }
    @SubscribeEvent public void noOffhandShield(PlayerInteractEvent.RightClickItem event) {
        if (actualWuwaMode(event.getEntity()) && event.getItemStack().is(Items.SHIELD)) event.setCanceled(true);
    }
    private static boolean actualWuwaMode(Player player){return player.getInventory().getSelectedSlot()==0&&ModAttachments.team(player).hasTeam();}
    private static void sceneInteract(PlayerInteractEvent event) {
        if(event.getEntity() instanceof ServerPlayer player)session(player).actions.sceneInteraction(player);
    }
    @SubscribeEvent public void sceneItem(PlayerInteractEvent.RightClickItem event){sceneInteract(event);}
    @SubscribeEvent public void sceneBlock(PlayerInteractEvent.RightClickBlock event){sceneInteract(event);}
    @SubscribeEvent public void sceneLeftBlock(PlayerInteractEvent.LeftClickBlock event){sceneInteract(event);}
    @SubscribeEvent public void sceneEntity(PlayerInteractEvent.EntityInteract event){sceneInteract(event);}
    @SubscribeEvent public void sceneSpecificEntity(PlayerInteractEvent.EntityInteractSpecific event){sceneInteract(event);}
    @SubscribeEvent public void containerOpened(PlayerContainerEvent.Open event) {
        if(event.getEntity() instanceof ServerPlayer player)session(player).actions.sceneInteraction(player);
    }
    @SubscribeEvent public void camouflageTarget(LivingChangeTargetEvent event) {
        if(event.getNewAboutToBeSetTarget() instanceof ServerPlayer player){
            if(session(player).actions.camouflageActive(player)||session(player).actions.quietFox(player))event.setNewAboutToBeSetTarget(null);
            else if(event.getEntity() instanceof Mob mob)HecateEntity.activeRelay(player).ifPresent(relay->{relay.rememberTarget(mob);event.setNewAboutToBeSetTarget(relay);});
        }
    }
    @SubscribeEvent public void noHandSwap(LivingSwapItemsEvent.Hands event) {
        if (event.getEntity() instanceof Player player && player.getInventory().getSelectedSlot() == 0 && ModAttachments.team(player).hasTeam()) event.setCanceled(true);
    }
    static double sixResponseAmplification(ServerPlayer player,CharacterInstance owner){return session(player).actions.sixResponseAmplification(player,owner);}
    static boolean suisuiInherentAttack(ServerPlayer player,CharacterInstance owner,String action){return session(player).actions.suisuiInherentAttack(owner,action);}
    static Map<String,Integer> sixTargetTimers(ServerPlayer player,CharacterInstance owner,LivingEntity target) {
        return session(player).actions.sixTargetTimers(owner,target);
    }
    public static boolean nativeHit(ServerPlayer player, CharacterInstance role, LivingEntity target,
                                    CharacterDefinition.DamageDefinition hit, double bonus, double amplification) {
        return nativeHit(player, role, target, hit, bonus, amplification, role.actionId());
    }
    public static boolean nativeHit(ServerPlayer player, CharacterInstance role, LivingEntity target,
                                    CharacterDefinition.DamageDefinition hit, double bonus, double amplification, String actionId) {
        return nativeHit(player, role, target, hit, bonus, amplification, actionId, HitOrigin.AUTONOMOUS);
    }
    public static boolean nativeHit(ServerPlayer player, CharacterInstance role, LivingEntity target,
                                    CharacterDefinition.DamageDefinition hit, double bonus, double amplification, String actionId, HitOrigin origin) {
        return nativeHit(player,role,target,hit,bonus,amplification,actionId,origin,0,0);
    }
    static boolean nativeHit(ServerPlayer player, CharacterInstance role, LivingEntity target,
                                    CharacterDefinition.DamageDefinition hit, double bonus, double amplification, String actionId, HitOrigin origin,
                                    double coefficientAdjustment,double attackAdjustment) {
        var key=new HitRecursionGuard.Key(role.instanceId(),combatTargetId(target),actionId);
        HitOrigin effectiveOrigin=cn.zgnhit.mcwuwa.content.mechanics.CoordinatedHitRules.coordinated(actionId,hit.id())?HitOrigin.AUTONOMOUS:origin;
        return damageScope(key,()->resolveNativeHit(player,role,target,hit,bonus,amplification,actionId,effectiveOrigin,coefficientAdjustment,attackAdjustment));
    }
    private static boolean damageScope(HitRecursionGuard.Key key,java.util.function.BooleanSupplier resolve) {
        var guard=HIT_GUARD.get();if(!guard.enter(key))return false;boolean root=guard.depth()==1;var post=POST_HITS.get();
        if(root)post.begin();
        try{boolean accepted=resolve.getAsBoolean();if(root)post.queue.drain(PlatformRuntime::confirmed);return accepted;}
        finally{if(root)post.finish();guard.exit(key);}
    }
    private static void confirmed(ConfirmedHit confirmed) {
        var player=confirmed.player;var role=confirmed.role;var target=confirmed.target;var hit=confirmed.hit;
        boolean owns=false;for(int slot=0;slot<3;slot++)if(ModAttachments.team(player).state().at(slot).filter(r->r==role&&r.alive()).isPresent())owns=true;
        if(!owns||!player.isAlive()||player.level()!=target.level()||target.isRemoved())return;
        session(player).actions.confirmedHit(player,role,target,hit.kind(),confirmed.action,confirmed.origin,cn.zgnhit.mcwuwa.content.mechanics.CoordinatedHitRules.coordinated(confirmed.action,hit.id()));
        session(player).actions.typedHit(player,role,hit.element(),cn.zgnhit.mcwuwa.content.mechanics.CoordinatedHitRules.coordinated(confirmed.action,hit.id()));
        RolePassiveRuntime.onHit(player,role,target,confirmed.action,hit.kind());EchoRuntime.onHit(player,role,target,hit.kind());
        if(!(target instanceof Player)&&target.isAlive()) {
            var change=gauge(target).onHit(hit.toughnessUnits()*(role.character()==CharacterId.CHISA?cn.zgnhit.mcwuwa.content.mechanics.ChisaSawRules.toughnessMultiplier(role.chain(),confirmed.action):1),hit.offTuneUnits()*(1+session(player).actions.offTuneEfficiency(player,role)),false,time(player));if(change.toughnessBroken())interruptMob(target);
        }
        if(confirmed.origin==HitOrigin.DIRECT_PROJECTILE||confirmed.origin==HitOrigin.DIRECT_ACTIVE&&ModAttachments.team(player).state().active().orElse(null)==role)
            reactToDirectHit(player,target,time(player));
    }
    private static boolean resolveNativeHit(ServerPlayer player, CharacterInstance role, LivingEntity target,
                                    CharacterDefinition.DamageDefinition hit, double bonus, double amplification, String actionId, HitOrigin origin, double coefficientAdjustment,double attackAdjustment) {
        if (CalibrationCommands.rejectNativeDamage(target)) return false;
        if (!canAttack(player, target)) return false;
        HecateEntity proxy=target instanceof HecateEntity hecate?hecate:null;
        if(proxy!=null)target=proxy.ownerPlayer();
        var scale = CombatConfig.scale();
        if (scale.measuredSwordDps().isEmpty()) {
            var session = session(player); if (time(player) - session.warnedUncalibrated > 100) {
                player.displayClientMessage(Component.literal("尚未完成本环境伤害标定；动作可预览，鸣潮伤害未启用。"),true); session.warnedUncalibrated = time(player);
            }
            return false;
        }
        Map<String, Double> modifiers = new HashMap<>(session(player).actions.passiveModifiers(player, role, target, hit.kind(), hit.element(), actionId));
        EchoRuntime.modifiers(player, role).forEach((key, value) -> modifiers.merge(key, value, Double::sum));
        var hitRaw=hit.raw();if(hitRaw.has("acceptedActionModifiers"))for(var entry:hitRaw.getAsJsonObject("acceptedActionModifiers").entrySet())
            modifiers.merge(entry.getKey(),entry.getValue().getAsDouble(),Double::sum);
        var roleModifiers = RolePassiveRuntime.damage(player, role, target, actionId, hit.kind(), hit.element(),
            hitRaw.has("contactGalbrenaEmbers")?hitRaw.get("contactGalbrenaEmbers").getAsDouble():null);
        roleModifiers.attributes().forEach((key, value) -> modifiers.merge(key, value, Double::sum));
        modifiers.merge("attack_pct",attackAdjustment,Double::sum);
        var threeFactors=cn.zgnhit.mcwuwa.content.mechanics.NewThreeDamageRules.sigrika(session(player).actions.ruleState(role),actionId,hit.id());
        modifiers.merge("defense_ignore",threeFactors.defenseIgnore(),Double::sum);
        var stats = NativeStats.of(role, weaponFor(player, role), modifiers); var profile = EnemyEvents.profile(target);
        Element element = Element.valueOf(hit.element().toUpperCase(Locale.ROOT));
        DamageKind kind=AttributeBindings.kind(hit.kind());
        double source=switch(hit.scalingProperty()){case "defense"->stats.defense();case "health"->projectedMaxHealth(player,stats.health())/CombatMath.HEALTH_SCALE;default->stats.attack()*EffectRuntime.attackStatMultiplier(player,element,kind);};
        double defense = targetDefense(target);
        boolean crit=player.getRandom().nextDouble()<(hit.raw().has("fixedCritRate")?hit.raw().get("fixedCritRate").getAsDouble():stats.critRate());
        var normal=new CombatResolver.NormalDamage(source,hit.multiplier(),stats.bonus("skill_ratio_bonus")+roleModifiers.coefficientIncrease()+threeFactors.coefficient()+session(player).actions.coefficientIncrease(role,actionId,hit.kind(),hit.element())+coefficientAdjustment,
            stats.bonus("flat_damage"),bonus+session(player).actions.damageBonus(player,role,target,actionId,hit.kind(),hit.element())+stats.bonus("all_damage_bonus")+stats.bonus(hit.kind()+"_bonus")+stats.bonus(hit.element()+"_bonus"),
            List.of(CombatMath.amplificationFactor(Math.max(0,amplification),threeFactors.amplification(),Math.max(0,session(player).actions.amplification(player,role,target,actionId,hit.kind(),hit.element())),roleModifiers.amplification(),Math.max(0,1+stats.bonus("damage_amplification"))),roleModifiers.targetVulnerability(),
                Math.max(0,1+stats.bonus("damage_vulnerability")),Math.max(0,1+stats.bonus("final_damage")),session(player).actions.unisonMultiplier(player,role)),crit,hit.raw().has("fixedCritDamage")?hit.raw().get("fixedCritDamage").getAsDouble():stats.critDamage());
        var mitigation=new CombatResolver.Mitigation(90,defense,Math.clamp(EffectRuntime.defenseReduction(target,element,kind)+stats.bonus("defense_reduction"),0,1),Math.clamp(stats.bonus("defense_ignore"),0,1),
            profile.resistance(element)-EffectRuntime.resistanceReduction(target,element,kind)-stats.bonus("resistance_reduction")-stats.bonus("resistance_ignore"));
        double amount=new CombatResolver().nativeDamage(normal.preDefense()*EffectRuntime.vulnerability(target,element,kind),crit,element,mitigation,scale).value();
        // Potion modifiers apply only to a confirmed direct melee event, after native-to-MC scaling.
        boolean melee = origin == HitOrigin.DIRECT_ACTIVE && session(player).actions.isDirectMelee(role, actionId);
        if (melee) {
            if (player.hasEffect(MobEffects.STRENGTH)) amount += 3 * (player.getEffect(MobEffects.STRENGTH).getAmplifier() + 1);
            if (player.hasEffect(MobEffects.WEAKNESS)) amount -= 4 * (player.getEffect(MobEffects.WEAKNESS).getAmplifier() + 1);
        }
        if (amount <= 0) return false;
        if(proxy!=null&&proxy.avoids(player.damageSources().playerAttack(player),(float)amount,origin==HitOrigin.PERIODIC))return false;
        int invulnerability = target.invulnerableTime; boolean previous = NATIVE_DAMAGE.get(), periodicBefore = PERIODIC_DAMAGE.get(); boolean accepted;
        var hurtMemory=(cn.zgnhit.mcwuwa.mixin.HurtMemoryAccessor)target;float previousHurt=hurtMemory.mcwuwa$getLastHurt();
        try { NATIVE_DAMAGE.set(true); PERIODIC_DAMAGE.set(origin == HitOrigin.PERIODIC); target.invulnerableTime = 0; accepted = resolvedHurt(player, target, (float) amount); }
        finally { target.invulnerableTime = invulnerability; hurtMemory.mcwuwa$setLastHurt(previousHurt); NATIVE_DAMAGE.set(previous); PERIODIC_DAMAGE.set(periodicBefore); }
        if (accepted) {
            ModAttachments.team(player).state().recordCombatActivity();
            session(player).actions.confirmedKill(player,role,target,actionId,origin);
            Network.impact(player,target,element,0);
            POST_HITS.get().put(new ConfirmedHit(player,role,target,hit,actionId,origin));
        }
        return accepted;
    }
    private static boolean resolvedHurt(ServerPlayer source, LivingEntity target, float amount) {
        if (target instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon) {
            var part = Arrays.stream(dragon.getSubEntities()).min(Comparator.comparingDouble(p -> p.distanceToSqr(source))).orElse(dragon.head);
            return dragon.hurt(source.level(), part, source.damageSources().playerAttack(source), amount);
        }
        return target.hurtServer(source.level(), source.damageSources().playerAttack(source), amount);
    }
    /** Anomaly pulses intentionally bypass crit, normal bonuses, gauges and direct-hit dodge. */
    record DefeatCredit(ServerPlayer player,CharacterInstance role) {}
    static DefeatCredit defeatCredit(ServerPlayer sourcePlayer,CharacterInstance source,LivingEntity target,String cause){
        var original=new DefeatCredit(sourcePlayer,source);
        if(!cause.startsWith("effect/glacio_chafe/")&&!cause.startsWith("effect/glacio_frost/"))return original;
        boolean covered=false;
        DefeatCredit nearest=null;
        double best=Double.POSITIVE_INFINITY;
        for(var player:sourcePlayer.level().players()){
            if(!player.isAlive()||player.isSpectator()||!ModAttachments.team(player).state().wuwaMode())continue;
            var active=ModAttachments.team(player).state().active().orElse(null);
            if(active==null||!active.alive())continue;
            double distance=player.distanceToSqr(target);
            if(distance>144)continue;
            covered|=session(player).actions.hasFrostChase(player,target);
            if(distance<best||(distance==best&&player==sourcePlayer)){
                best=distance;nearest=new DefeatCredit(player,active);
            }
        }
        return covered&&nearest!=null?nearest:original;
    }
    public static boolean effectHit(ServerPlayer player, LivingEntity target, Element element, double nativePreDefenseDamage) {
        var role=ModAttachments.team(player).state().active();if(role.isEmpty())return false;
        var source=new EffectSource(player.getUUID(),role.get().instanceId(),role.get().character(),UUID.randomUUID(),UUID.randomUUID());
        return effectHit(player,source,target,element,nativePreDefenseDamage,"effect/legacy");
    }
    /** The original weapon travels with the pulse; held-item changes never change its attribution. */
    public static boolean effectHit(ServerPlayer player,EffectSource source,LivingEntity target,Element element,double nativePreDefenseDamage,String cause) {
        if(!source.playerId().equals(player.getUUID()))return false;
        var team=ModAttachments.team(player).state();boolean owned=false;
        for(int slot=0;slot<3;slot++)if(team.at(slot).filter(role->role.instanceId().equals(source.weaponInstanceId())&&role.alive()).isPresent())owned=true;
        if(!owned)return false;
        var key=new HitRecursionGuard.Key(source.weaponInstanceId(),combatTargetId(target),cause);
        return damageScope(key,()->resolveEffectHit(player,source,target,element,nativePreDefenseDamage,cause));
    }
    private static boolean resolveEffectHit(ServerPlayer player,EffectSource source,LivingEntity target,Element element,double nativePreDefenseDamage,String cause) {
        if (CalibrationCommands.rejectNativeDamage(target)) return false;
        if (!canAttack(player, target) || !Double.isFinite(nativePreDefenseDamage) || nativePreDefenseDamage <= 0 || CombatConfig.scale().measuredSwordDps().isEmpty()) return false;
        // A relay and its owner have one health pool and one damage recipient.
        if(target instanceof HecateEntity relay){if(relay.avoids(player.damageSources().playerAttack(player),(float)nativePreDefenseDamage,true))return false;target=relay.ownerPlayer();}
        double defense = targetDefense(target);
        double effectBonus=0,effectDefenseIgnore=0;
        if(cause.startsWith("effect/")) {
            for(int slot=0;slot<3;slot++)if(ModAttachments.team(player).state().at(slot).filter(r->r.instanceId().equals(source.weaponInstanceId())).isPresent()) {
                var role=ModAttachments.team(player).state().at(slot).orElseThrow();
                var extra=new HashMap<>(session(player).actions.passiveModifiers(player,role,target,"elemental_effect",element.name().toLowerCase(Locale.ROOT),cause));
                RolePassiveRuntime.damage(player,role,target,cause,"elemental_effect",element.name().toLowerCase(Locale.ROOT)).attributes().forEach((k,v)->extra.merge(k,v,Double::sum));
                EchoRuntime.modifiers(player,role).forEach((k,v)->extra.merge(k,v,Double::sum));
                var effectStats=NativeStats.of(role,weaponFor(player,role),extra);
                effectBonus=effectStats.bonus("effect_damage_bonus")+effectStats.bonus("damage_amplification");
                // passiveModifiers already includes Chisa's thread contribution.
                effectDefenseIgnore=effectStats.bonus("defense_ignore");
            }
        }
        DamageKind kind=cause.startsWith("fixed/")?DamageKind.BASIC:cause.startsWith("effect/")?DamageKind.ELEMENTAL_EFFECT:DamageKind.TUNE_BREAK;
        var team=ModAttachments.team(player).state();
        double soloFactor=1,threadDefenseIgnore=0;
        for(int slot=0;slot<3;slot++)if(team.at(slot).filter(r->r.instanceId().equals(source.weaponInstanceId())).isPresent())
        {
            var owner=team.at(slot).orElseThrow();
            soloFactor=session(player).actions.soloAnomalyFactor(player,owner,target,element,cause,effectBonus);
            if(!cause.startsWith("fixed/"))soloFactor*=session(player).actions.unisonMultiplier(player,owner);
            threadDefenseIgnore=cause.startsWith("effect/")?Math.clamp(effectDefenseIgnore,0,1)
                :session(player).actions.chisaThreadDefenseIgnore(player,team.at(slot).orElseThrow(),target);
        }
        double vulnerability=cause.startsWith("fixed/")?1:EffectRuntime.vulnerability(target,element,kind);
        double amount=nativePreDefenseDamage*soloFactor*vulnerability*CombatMath.defenseFactor(90,defense,EffectRuntime.defenseReduction(target,element,kind),threadDefenseIgnore)
            *CombatMath.resistanceFactor(element,EnemyEvents.profile(target).resistance(element)-EffectRuntime.resistanceReduction(target,element,kind))*CombatConfig.scale().value();
        if(cause.equals("fixed/chisa_initial"))amount=Math.min(amount,Math.max(0,target.getHealth()-target.getMaxHealth()*.618));
        if(amount<=0)return false;
        boolean nativeBefore = NATIVE_DAMAGE.get(), periodicBefore = PERIODIC_DAMAGE.get(); int oldInvulnerability = target.invulnerableTime;
        var hurtMemory=(cn.zgnhit.mcwuwa.mixin.HurtMemoryAccessor)target;float previousHurt=hurtMemory.mcwuwa$getLastHurt();
        try {
            NATIVE_DAMAGE.set(true); PERIODIC_DAMAGE.set(true); target.invulnerableTime = 0;
            boolean accepted = resolvedHurt(player, target, (float)amount);
            if (accepted) {
                CharacterInstance effectOwner=null;
                for(int slot=0;slot<3;slot++){
                    var candidate=team.at(slot).orElse(null);
                    if(candidate!=null&&candidate.instanceId().equals(source.weaponInstanceId())){effectOwner=candidate;break;}
                }
                session(player).actions.observedWorldDamage(player,target,effectOwner);
                session(player).actions.activeTeamDamage(player,effectOwner,target,cause);
                ModAttachments.team(player).state().recordCombatActivity();Network.impact(player,target,element,
                    cause.equals("response/120999005")?3:cause.equals("response/1509056001")?4:cause.equals("response/121099004")?5:cause.startsWith("effect/")?1:2);
                for(int slot=0;slot<3;slot++) {
                    var owner=ModAttachments.team(player).state().at(slot).orElse(null);
                    if(owner!=null&&owner.instanceId().equals(source.weaponInstanceId())) {
                        RolePassiveRuntime.onRuptureDamage(player,owner,target,cause);
                        if(cause.startsWith("effect/")) {
                            session(player).actions.anomalyDamage(player,owner,target,cause.split("/")[1]);
                            session(player).actions.typedHit(player,owner,element.name().toLowerCase(Locale.ROOT));
                        }
                        if(!target.isAlive()){
                            var credit=defeatCredit(player,owner,target,cause);
                            session(credit.player()).actions.confirmedKill(credit.player(),credit.role(),target);
                        }
                    }
                }
            } return accepted;
        } finally { NATIVE_DAMAGE.set(nativeBefore); PERIODIC_DAMAGE.set(periodicBefore); target.invulnerableTime = oldInvulnerability; hurtMemory.mcwuwa$setLastHurt(previousHurt); }
    }
    /** Source Type 12 responses use known ATK coefficients; unknown extra formula zones use the declared MC adaptation. */
    public static boolean ruptureResponseHit(ServerPlayer player,CharacterInstance role,LivingEntity target,CharacterDefinition.DamageDefinition hit,String actionId) {
        var actions=session(player).actions;long now=time(player);
        if(!actions.ruptureResponseReady(role,target,now))return false;
        var extras=new HashMap<>(session(player).actions.passiveModifiers(player,role,target,"tune_break",hit.element(),actionId));
        var roleModifiers=RolePassiveRuntime.damage(player,role,target,actionId,"tune_break",hit.element());
        roleModifiers.attributes().forEach((k,v)->extras.merge(k,v,Double::sum));
        double attack=NativeStats.of(role,weaponFor(player,role),extras).attack()*EffectRuntime.attackStatMultiplier(player,Element.valueOf(hit.element().toUpperCase(Locale.ROOT)),DamageKind.TUNE_BREAK);
        double amplification=1+session(player).actions.tuneBreakAmplification(player,role)/100;
        var source=new EffectSource(player.getUUID(),role.instanceId(),role.character(),UUID.randomUUID(),UUID.randomUUID());
        boolean accepted=effectHit(player,source,target,Element.valueOf(hit.element().toUpperCase(Locale.ROOT)),attack*hit.multiplier()*Math.max(0,1+roleModifiers.coefficientIncrease())*amplification,"response/"+hit.id());
        if(accepted) {
            actions.ruptureResponseLanded(player,role,target,now);
            String light=switch(hit.id()) {
                case "121099004" -> "aemeath__starburst_contact";
                case "1509056001" -> "lynae__spectrum_contact";
                default -> "";
            };
            if(!light.isEmpty()) {
                var visual=ConstructEntity.spawn(player.level(),player,role.instanceId(),"skill_light_"+light,
                    target.getBoundingBox().getCenter().add(0,-1.05,0),12,1,false,"attack");
                // Face the actual source at contact; later camera turns or
                // switching characters must not rotate the fixed impact plane.
                visual.setYRot((float)Math.toDegrees(Math.atan2(player.getX()-target.getX(),target.getZ()-player.getZ())));
            }
        }
        return accepted;
    }
    /** All incoming damage routes see the same currently active role's defensive modifiers. */
    private static double targetDefense(LivingEntity target) {
        if (!(target instanceof Player player)) return CombatMath.ENEMY_DEFENSE;
        var team = ModAttachments.team(player); var active = team.state().active();
        // A player without a role keeps vanilla defenses; the 1512 baseline belongs to mobs.
        if (active.isEmpty()) return 0;
        var role = active.orElseThrow(); Map<String, Double> modifiers = new HashMap<>();
        if (player instanceof ServerPlayer server) {
            modifiers.putAll(session(server).actions.passiveModifiers(server, role, target, "incoming", "all"));
            EchoRuntime.modifiers(server, role).forEach((key, value) -> modifiers.merge(key, value, Double::sum));
            RolePassiveRuntime.damage(server, role, target, role.actionId(), "incoming", "all").attributes()
                .forEach((key, value) -> modifiers.merge(key, value, Double::sum));
        }
        return NativeStats.of(role, team.activeStack(), modifiers).defense();
    }
    @SubscribeEvent public void incoming(LivingIncomingDamageEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        LivingEntity target = event.getEntity(); DamageSource source = event.getSource();
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD)) return;
        if (source.getEntity() instanceof ServerPlayer attacker) ModAttachments.team(attacker).state().recordCombatActivity();
        if (source.getEntity() instanceof Mob attacker) gauge(attacker).noteAttack(level.getGameTime());
        if (source.getDirectEntity() instanceof Mob attacker && (EffectRuntime.isFrozen(attacker) || gauge(attacker).snapshot(level.getGameTime()).brokenUntil() > level.getGameTime())) { event.setCanceled(true); return; }
        if (target instanceof ServerPlayer player) {
            var attachment = ModAttachments.team(player); var team = attachment.state();
            team.recordCombatActivity();
            if (team.hasTeam()) {
                if(session(player).actions.preventsNativeHurt(player)){event.setCanceled(true);return;}
                if(event.getAmount()>0&&evadableDirect(source)&&source.getEntity() instanceof LivingEntity&&session(player).actions.qiuyuanCounter(player)){event.setCanceled(true);return;}
                var type = evadableDirect(source) ? StaminaDodge.Incoming.EVADABLE_DIRECT : StaminaDodge.Incoming.ENVIRONMENT;
                var avoided = event.getAmount() > 0 ? team.stamina().incoming(type) : StaminaDodge.AttackResult.NOT_PREVENTED;
                if (avoided == StaminaDodge.AttackResult.NOT_PREVENTED && event.getAmount() > 0
                    && type == StaminaDodge.Incoming.EVADABLE_DIRECT && session(player).actions.skillEvadeWindow(player))
                    avoided = team.stamina().successfulSkillEvade();
                if (avoided != StaminaDodge.AttackResult.NOT_PREVENTED) {
                    if (avoided == StaminaDodge.AttackResult.DODGE_SUCCEEDED && session(player).actions.confirmedSkillEvade(player)) Network.sync(player);
                    event.setCanceled(true); return;
                }
                if (type == StaminaDodge.Incoming.EVADABLE_DIRECT && session(player).actions.blockHit(player)) { event.setCanceled(true); return; }
            }
        }
        if (!NATIVE_DAMAGE.get()) {
            if (target instanceof Player player) {
                var team = ModAttachments.team(player);
                if (!team.hasTeam()) return;
            }
            event.setAmount((float) (event.getAmount() * CombatMath.defenseFactor(90, targetDefense(target), EffectRuntime.defenseReduction(target), 0)));
        // NeoForge 26.1.2.78 requires ticks > 0 despite its error saying only
        // "cannot be negative". Native dispatch restores the saved vanilla
        // invulnerability timer in its finally block, so use the legal minimum.
        } else event.setInvulnerabilityTicks(1);
    }
    @SubscribeEvent public void damaged(LivingDamageEvent.Post event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if(event.getNewDamage()>0)EffectRuntime.damagedStasis(event.getEntity());
        if (event.getEntity() instanceof ServerPlayer player) {
            if (event.getNewDamage() > 0) saveCurrentHealth(player, ModAttachments.team(player));
            session(player).actions.finishIncomingDamage(player);
            if (player.isAlive() && CombatControl.receivedDamage(event.getNewDamage(), event.getReduction(net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction.ABSORPTION)))
                interruptionImpact(event.getSource()).ifPresent(impact -> {
                    boolean interrupted=session(player).actions.interrupt(player, impact);
                    session(player).actions.receivedImpact(player);if(interrupted)Network.sync(player);
                });
        }
        if (event.getNewDamage() <= 0) return;
        if (!CalibrationCommands.isTarget(event.getEntity()) && !NATIVE_DAMAGE.get() && event.getSource().getEntity() instanceof ServerPlayer player && !event.getSource().is(DamageTypeTags.IS_FIRE)) {
            var target = event.getEntity(); boolean direct = event.getSource().getDirectEntity() == player || event.getSource().is(DamageTypeTags.IS_PROJECTILE);
            if (direct && !(target instanceof Player)) {
                double charge = event.getSource().is(DamageTypeTags.IS_PROJECTILE) ? 1 : session(player).vanillaCharge;
                var change = gauge(target).onHit(.25 * charge, .25 * charge, false, level.getGameTime()); if (change.toughnessBroken()) interruptMob(target);
                reactToDirectHit(player,target,level.getGameTime());
            }
        }
        if(!NATIVE_DAMAGE.get()&&!CalibrationCommands.isTarget(event.getEntity()))
            for(var entry:List.copyOf(SESSIONS.entrySet())){
                var owner=entry.getKey();
                if(owner.isAlive()&&!owner.isRemoved()&&owner.level()==level){
                    var attacker=event.getSource().getEntity() instanceof ServerPlayer sourcePlayer
                        ?ModAttachments.team(sourcePlayer).state().active().orElse(null):null;
                    entry.getValue().actions.observedWorldDamage(owner,event.getEntity(),attacker);
                    if(event.getSource().getEntity()==owner)
                        entry.getValue().actions.activeTeamDamage(owner,attacker,event.getEntity(),"native/accepted_damage");
                }
            }
    }
    private static void reactToDirectHit(ServerPlayer player, LivingEntity target, long now) {
        if (target instanceof AlephEntity aleph && aleph.tryParry(player)) gauge(target).parryBonus(now);
        else if (target instanceof EchoBossEntity boss) {
            if (boss.tryParry(player)) gauge(target).parryBonus(now);
            else boss.tryCounter(player);
        }
    }
    private static Optional<CombatControl.Impact> interruptionImpact(DamageSource source) {
        var attacker = source.getEntity() instanceof LivingEntity living ? EnemyEvents.profile(living) : null;
        boolean periodic = PERIODIC_DAMAGE.get() || source.is(DamageTypes.ON_FIRE) || source.is(DamageTypes.WITHER) || source.is(DamageTypes.DRAGON_BREATH);
        boolean direct = attacker != null && source.getDirectEntity() != null && !(source.getDirectEntity() instanceof AreaEffectCloud)
            && !source.is(DamageTypeTags.NO_IMPACT) && !source.is(DamageTypeTags.IS_FALL) && !source.is(DamageTypeTags.IS_DROWNING)
            && !source.is(DamageTypeTags.IS_FREEZING) && (!source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_PROJECTILE));
        return CombatControl.impact(attacker, direct, source.is(DamageTypeTags.IS_EXPLOSION), periodic);
    }
    @SubscribeEvent public void beforeHealth(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            session(player).actions.beginIncomingDamage();
            if(!event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD))
                event.setNewDamage((float) session(player).actions.reduceIncoming(player, event.getNewDamage(),
                    event.getSource().getEntity() instanceof Mob && interruptionImpact(event.getSource()).isPresent()));
        }
    }
    @SubscribeEvent public void death(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var attachment = ModAttachments.team(player); var team = attachment.state(); if (!team.hasTeam()) return;
        var outgoing = team.active().orElse(null);
        session(player).actions.cancel(player);
        if (event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) { teamChanged(player); EffectRuntime.onOwnedLogout(player.getUUID()); return; }
        team.active().ifPresent(c -> c.commitHealth(0));
        if(outgoing!=null){RolePassiveRuntime.onDowned(player,outgoing);session(player).actions.downed(player,outgoing);}
        // Minecraft has already attempted its current-player totem before LivingDeathEvent.
        var result = team.processCurrentDefeat(c -> OptionalDouble.empty(), c -> OptionalDouble.empty());
        if (!result.playerDeath()) {
            event.setCanceled(true); applyHealth(player, attachment, false);
            session(player).actions.switched(player, outgoing, false); EchoRuntime.onSwitch(player, outgoing, false);
            Network.sync(player);
        } else { teamChanged(player); EffectRuntime.onOwnedLogout(player.getUUID()); }
    }
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST)
    public void nearbyEnemyDefeat(LivingDeathEvent event) {
        if(event.isCanceled()||!(event.getEntity() instanceof Mob enemy)||!(enemy.level() instanceof ServerLevel level)
            ||enemy.getTags().contains(EnemyEvents.SKILL_SUMMON_TAG))return;
        for(var player:level.players())if(ModAttachments.team(player).state().hasTeam())
            session(player).actions.nearbyEnemyDefeated(player,enemy,event.getSource().getEntity()==player);
    }
    public static void saveCurrentHealth(Player player, TeamAttachment attachment) {
        attachment.state().active().ifPresent(c -> { c.setMaxHealth(player.getMaxHealth()); c.commitHealth(Math.max(0, Math.min(player.getHealth(), c.maxHealth()))); });
    }
    public static void applyHealth(Player player, TeamAttachment attachment, boolean teamChanged) {
        var attribute = player.getAttribute(Attributes.MAX_HEALTH); if (attribute == null) return;
        var nativeValues=nativeHealthValues(player,attachment);
        // Clamp directly to each final projected maximum. A temporary clamp to gear-only HP would
        // destroy legitimate Health Boost hearts every time a role switches or equipment is refreshed.
        for (int i = 0; i < 3; i++) if (attachment.state().at(i).isPresent()) {
            var role = attachment.state().at(i).orElseThrow();
            role.setMaxHealth(projectedMaxHealth(player,nativeValues.get(role.instanceId())));
            attachment.restoreLoadedHealth(role);
        }
        var active = attachment.state().active();
        if (active.isPresent()) {
            var role=active.get();double maximum=nativeValues.get(role.instanceId())*CombatMath.HEALTH_SCALE;
            attribute.addOrUpdateTransientModifier(new AttributeModifier(HEALTH, maximum - attribute.getBaseValue(), AttributeModifier.Operation.ADD_VALUE));
            role.setMaxHealth(player.getMaxHealth()); player.setHealth((float) role.health());
        } else { attribute.removeModifier(HEALTH); if (teamChanged) player.setHealth(player.getMaxHealth()); }
        if(player instanceof ServerPlayer server){session(server).healthModifiers=externalHealthModifiers(player);session(server).nativeHealth=nativeValues;}
    }
    private static Map<UUID,Double> nativeHealthValues(Player player,TeamAttachment attachment) {
        Map<UUID,Double> values=new HashMap<>();for(int slot=0;slot<3;slot++)if(attachment.state().at(slot).isPresent()) {
            var role=attachment.state().at(slot).orElseThrow();double health=player instanceof ServerPlayer server?session(server).actions.combatStats(server,role).health():NativeStats.of(role,attachment.inventory().getItem(slot)).health();
            values.put(role.instanceId(),health);
        }
        return Map.copyOf(values);
    }
    private static Set<AttributeModifier> externalHealthModifiers(Player player) {
        var attribute = player.getAttribute(Attributes.MAX_HEALTH); if (attribute == null) return Set.of();
        var result = new HashSet<AttributeModifier>();
        for (var modifier : attribute.getModifiers()) if (!modifier.id().equals(HEALTH)) result.add(modifier);
        return Set.copyOf(result);
    }
    /** Native gear/ability HP enters once; vanilla modifiers are evaluated by Minecraft itself. */
    public static double projectedMaxHealth(Player player, double nativeHealth) {
        if (!Double.isFinite(nativeHealth) || nativeHealth <= 0) throw new IllegalArgumentException("native health must be positive");
        var projected = new AttributeInstance(Attributes.MAX_HEALTH, ignored -> { });
        projected.setBaseValue(nativeHealth * CombatMath.HEALTH_SCALE);
        for (var modifier : externalHealthModifiers(player)) projected.addTransientModifier(modifier);
        return projected.getValue();
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { var team = ModAttachments.team(player); team.state().rejoin(); applyHealth(player, team, false); Network.sync(player); }
    }
    @SubscribeEvent public void tracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer && event.getTarget() instanceof ServerPlayer actor) Network.tracking(observer, actor);
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CinematicTimeRuntime.clear(player);
            var team = ModAttachments.team(player); saveCurrentHealth(player, team); session(player).actions.prepareLogout(player); team.state().logout(); team.flushWeapons();
            EffectRuntime.onOwnedLogout(player.getUUID());
            EchoRuntime.clear(player); Network.forget(player.getUUID());
            RolePassiveRuntime.clear(player);
            Session session = SESSIONS.remove(player); if (session != null) session.actions.clear(player);
        }
    }
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var team = ModAttachments.team(player);
            // Returning through the End credits also fires this event but is not a death/respawn.
            if (!event.isEndConquered()) team.respawn();
            applyHealth(player, team, false); team.flushWeapons(); Network.sync(player);
        }
    }
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { session(player).actions.dimensionChanged(player); EchoRuntime.clear(player); Network.sync(player); }
    }
    @SubscribeEvent public void slept(SleepFinishedTimeEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        // NeoForge fires this before vanilla wakes the sleepers. Keep those bodies only;
        // settle after all listeners can cancel, never heal awake players in the same world.
        var sleepers=level.players().stream().filter(player->player.isSleeping()&&player.isAlive()&&ModAttachments.team(player).hasTeam()).toList();
        if(!sleepers.isEmpty())SLEEP_RECOVERIES.computeIfAbsent(level,ignored->new ArrayList<>()).add(new SleepRecovery(event,sleepers));
    }
    private static void finishSleepRecovery(ServerLevel level) {
        var pending=SLEEP_RECOVERIES.remove(level);if(pending==null)return;
        for(var recovery:pending)for(var player:recovery.sleepers()) {
            if(player.level()!=level||!player.isAlive()||!level.players().contains(player))continue;
            var team=ModAttachments.team(player);if(!team.hasTeam())continue;
            applyHealth(player,team,false);team.state().sleepSkippedNight();applyHealth(player,team,false);Network.sync(player);
        }
    }
    @SubscribeEvent public void serverStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        SLEEP_RECOVERIES.clear();
    }
    public static void addTargetView(ServerPlayer player, JsonObject root) {
        Session session = session(player); var target = player.level().getEntity(session.lockId >= 0 ? session.lockId : session.focusId);
        if (!(target instanceof LivingEntity living) || !canAttack(player, living) || living.distanceToSqr(player) > 1024) { session.lockId = -1; return; }
        JsonObject json = new JsonObject();
        LivingEntity healthTarget=living instanceof HecateEntity hecate?hecate.ownerPlayer():living;
        json.addProperty("id", living.getId()); json.addProperty("name", living.getName().getString()); json.addProperty("hp", healthTarget.getHealth()); json.addProperty("maxHp", healthTarget.getMaxHealth());
        boolean gaugesEnabled = !(living instanceof Player)&&!(living instanceof HecateEntity); json.addProperty("gaugesEnabled", gaugesEnabled);
        if (gaugesEnabled) {
            var gauge = gauge(living).snapshot(time(player));
            json.addProperty("toughness", gauge.toughness()); json.addProperty("toughnessMax", gauge.toughnessMax()); json.addProperty("offTune", gauge.offTune()); json.addProperty("offTuneMax", gauge.offTuneMax()); json.addProperty("mistuned", gauge.mistuned());
            json.addProperty("toughnessBroken",gauge.brokenUntil()>time(player));
            json.addProperty("accumulationLocked",gauge.accumulationLockedUntil()>time(player));
            if(living instanceof AlephEntity aleph){json.addProperty("parryWindow",aleph.isParryWindow());json.addProperty("parryStagger",aleph.isStaggered());}
            if(living instanceof cn.zgnhit.mcwuwa.platform.entity.EchoBossEntity boss){json.addProperty("parryWindow",boss.isParryWindow());json.addProperty("parryStagger",boss.staggerTicks()>0);}
        }
        root.add("target", json);
        com.google.gson.JsonArray marks = new com.google.gson.JsonArray();
        for (var effect : EffectRuntime.snapshots(healthTarget)) { JsonObject mark = new JsonObject(); mark.addProperty("id", effect.id()); mark.addProperty("layers", effect.layers()); mark.addProperty("expires", effect.expiresAt()); mark.addProperty("coverage", effect.coverage()); marks.add(mark); }
        int doom=NewThreeRoleRuntime.visibleLayers(player,living);
        if(doom>0){JsonObject mark=new JsonObject();mark.addProperty("id","galbrena_doom");mark.addProperty("layers",doom);marks.add(mark);}
        int embers=session.actions.emberStacks(player,living);
        if(embers>0){JsonObject mark=new JsonObject();mark.addProperty("id","zani_heliacal_ember");mark.addProperty("layers",embers);marks.add(mark);}
        json.add("effects", marks);
    }
    /** Existing encounter gauges only; querying the HUD never initializes nearby mobs or consumes readiness. */
    public static void addNearbyTuneView(ServerPlayer player,JsonObject root) {
        var ready=new com.google.gson.JsonArray();var team=ModAttachments.team(player).state();long now=time(player);
        if(team.canAct()&&player.isAlive()&&!player.isSpectator())
            for(var entry:GAUGES.entrySet()) {
                LivingEntity target=entry.getKey();
                if(target.level()!=player.level()||target.isRemoved()||!target.isAlive()||target.distanceToSqr(player)>36
                    ||!canAttack(player,target)||!player.hasLineOfSight(target)||!entry.getValue().snapshot(now).mistuned())continue;
                ready.add(target.getId());if(ready.size()==32)break;
            }
        root.add("nearbyTuneReady",ready);
    }
    public static void addRoleView(ServerPlayer player, CharacterInstance role, JsonObject view) {
        var actions = session(player).actions; actions.addView(player,role, view); actions.addHudView(player, role, view);actions.addStatsView(player,role,view);
    }
}
