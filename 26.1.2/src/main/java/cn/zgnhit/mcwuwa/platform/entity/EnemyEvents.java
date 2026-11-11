package cn.zgnhit.mcwuwa.platform.entity;

import cn.zgnhit.mcwuwa.core.EnemyProfile;
import cn.zgnhit.mcwuwa.platform.ModItems;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Spawn, fixed boss HP and owned-kill drops; never writes outside the running game's level. */
public final class EnemyEvents {
    public static final String SKILL_SUMMON_TAG = "mc_wuwa:skill_summon";
    private static final Identifier HEALTH_RULE = Identifier.fromNamespaceAndPath("mc_wuwa", "strong_enemy_health");
    private EnemyEvents() {}
    public static EnemyProfile profile(LivingEntity entity) {
        return EnemyProfile.forEntity(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
            entity.getType().getCategory() == MobCategory.MONSTER, entity.entityTags().contains(SKILL_SUMMON_TAG));
    }
    static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel) || !(event.getEntity() instanceof LivingEntity living)) return;
        double multiplier = profile(living).healthMultiplier(); if (multiplier == 1) return;
        var maximum = living.getAttribute(Attributes.MAX_HEALTH); if (maximum == null || maximum.hasModifier(HEALTH_RULE)) return;
        float fraction = living.getMaxHealth() <= 0 ? 1 : living.getHealth() / living.getMaxHealth();
        maximum.addPermanentModifier(new AttributeModifier(HEALTH_RULE, multiplier - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        living.setHealth(living.getMaxHealth() * fraction);
    }
    static void onDrops(LivingDropsEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.entityTags().contains(SKILL_SUMMON_TAG)) { event.getDrops().clear(); return; }
        if (victim.level() instanceof ServerLevel && event.getSource().getEntity() instanceof ServerPlayer killer)
            cn.zgnhit.mcwuwa.platform.CombatAdvancements.defeated(killer, victim);
        if (!(victim.level() instanceof ServerLevel level) || !(event.getSource().getEntity() instanceof ServerPlayer owner)
            || !profile(victim).echoEligible()) return;
        // DamageSource#getEntity is the owner for player-fired projectiles and explicitly player-owned skill sources.
        // Recently-hit credit is deliberately not used: a pure environmental final hit must not produce a core.
        if (victim instanceof AlephEntity) addDrop(event, level, victim, ModItems.CORE.get().getDefaultInstance());
        if (victim instanceof EchoBossEntity boss) addDrop(event, level, victim, new ItemStack(ModItems.CORE.get(), boss.profile().cores()));
        ItemStack echo = ModItems.createEcho(victim, owner);
        if (!echo.isEmpty()) addDrop(event, level, victim, echo);
    }
    static void onExperience(LivingExperienceDropEvent event) {
        if (event.getEntity().entityTags().contains(SKILL_SUMMON_TAG)) event.setDroppedExperience(0);
    }
    private static void addDrop(LivingDropsEvent event, ServerLevel level, LivingEntity victim, ItemStack stack) {
        ItemEntity drop = new ItemEntity(level, victim.getX(), victim.getY(), victim.getZ(), stack); drop.setDefaultPickUpDelay(); event.getDrops().add(drop);
    }
    static void onPotentialSpawns(LevelEvent.PotentialSpawns event) {
        if (event.getMobCategory() != MobCategory.MONSTER) return;
        // Add the approved weight BEFORE the existing Aleph integer scaling, preserving relative frequency.
        if (event.getLevel() instanceof ServerLevel level) {
            var pos = event.getPos();
            String biome = level.getBiome(pos).unwrapKey().map(key -> key.identifier().getPath()).orElse("");
            for (var boss : cn.zgnhit.mcwuwa.core.EchoBossCatalog.ALL) {
                var type = ModEntities.ECHO_BOSSES.get(boss.id()).get();
                if (boss.habitat(level.dimension().identifier().toString(), biome, level.getOverworldClockTime(), Math.hypot(pos.getX(), pos.getZ()))
                    && event.getSpawnerDataList().stream().noneMatch(entry -> entry.value().type() == type))
                    event.addSpawnerData(new Weighted<>(new MobSpawnSettings.SpawnerData(type, 1, 1), boss.weight()));
            }
        }
        List<Weighted<MobSpawnSettings.SpawnerData>> entries = List.copyOf(event.getSpawnerDataList());
        if (entries.stream().anyMatch(entry -> entry.value().type() == ModEntities.ALEPH.get())) return;
        List<Weighted<MobSpawnSettings.SpawnerData>> zombies = entries.stream().filter(entry -> entry.value().type() == EntityType.ZOMBIE).toList();
        if (zombies.isEmpty()) return;
        // Multiply the existing integer weights by ten to preserve the exact 1:10 Aleph:zombie weight ratio,
        // including biomes whose zombie weight is not divisible by ten. No random replacement of spawned zombies.
        for (var entry : entries) {
            event.removeSpawnerData(entry); event.addSpawnerData(new Weighted<>(entry.value(), Math.multiplyExact(entry.weight(), 10)));
        }
        for (var zombie : zombies) event.addSpawnerData(new Weighted<>(
            new MobSpawnSettings.SpawnerData(ModEntities.ALEPH.get(), zombie.value().minCount(), zombie.value().maxCount()), zombie.weight()));
    }
}
