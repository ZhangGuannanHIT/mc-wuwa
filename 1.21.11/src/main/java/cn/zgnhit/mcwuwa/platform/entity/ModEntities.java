package cn.zgnhit.mcwuwa.platform.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Common-side entity bootstrap. Rendering is registered by the isolated client entry point. */
public final class ModEntities {
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities("mc_wuwa");
    public static final java.util.Map<String, DeferredHolder<EntityType<?>, EntityType<EchoBossEntity>>> ECHO_BOSSES = new java.util.LinkedHashMap<>();
    static {
        for (var boss : cn.zgnhit.mcwuwa.core.EchoBossCatalog.ALL)
            ECHO_BOSSES.put(boss.id(), ENTITIES.registerEntityType(boss.id(), EchoBossEntity::new, MobCategory.MONSTER,
                builder -> { builder.sized(boss.width(), boss.height()).clientTrackingRange(12).updateInterval(1).noLootTable();
                    if (boss.id().equals("furnace_husk")) builder.fireImmune(); return builder; }));
    }
    public static final DeferredHolder<EntityType<?>,EntityType<SurveyMotorcycleEntity>> SURVEY_MOTORCYCLE=ENTITIES.registerEntityType(
        "survey_motorcycle",SurveyMotorcycleEntity::new,MobCategory.MISC,builder->builder.sized(1.25f,.8f).clientTrackingRange(10).updateInterval(3));
    public static final DeferredHolder<EntityType<?>, EntityType<AlephEntity>> ALEPH = ENTITIES.registerEntityType(
        "aleph_one", AlephEntity::new, MobCategory.MONSTER,
        builder -> builder.sized(2.4f, 2.0f).eyeHeight(1.1f).clientTrackingRange(10).updateInterval(1).noLootTable());
    public static final DeferredHolder<EntityType<?>, EntityType<ConstructEntity>> CONSTRUCT = ENTITIES.registerEntityType(
        "construct", ConstructEntity::new, MobCategory.MISC,
        builder -> builder.sized(.1f,.1f).clientTrackingRange(12).updateInterval(1).noSave().noLootTable().noSummon().fireImmune());
    public static final DeferredHolder<EntityType<?>, EntityType<CombatProjectileEntity>> COMBAT_PROJECTILE = ENTITIES.registerEntityType(
        "combat_projectile", CombatProjectileEntity::new, MobCategory.MISC,
        builder -> builder.sized(.25f,.25f).clientTrackingRange(8).updateInterval(10).noSave().noLootTable().noSummon().fireImmune());
    private ModEntities() {}
    public static final DeferredHolder<EntityType<?>, EntityType<HecateEntity>> HECATE = ENTITIES.registerEntityType(
        "hecate", HecateEntity::new, MobCategory.MISC,
        builder -> builder.sized(1.2f,2.8f).clientTrackingRange(12).updateInterval(1).noSave().noLootTable().noSummon().fireImmune());
    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
        bus.addListener(ModEntities::attributes);
        bus.addListener(ModEntities::spawnPlacement);
        NeoForge.EVENT_BUS.addListener(EnemyEvents::onJoin);
        NeoForge.EVENT_BUS.addListener(EnemyEvents::onDrops);
        NeoForge.EVENT_BUS.addListener(EnemyEvents::onExperience);
        NeoForge.EVENT_BUS.addListener(EnemyEvents::onPotentialSpawns);
    }
    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(ALEPH.get(), AlephEntity.createAlephAttributes().build());event.put(HECATE.get(),net.minecraft.world.entity.LivingEntity.createLivingAttributes().build());
        for (var boss : cn.zgnhit.mcwuwa.core.EchoBossCatalog.ALL) event.put(ECHO_BOSSES.get(boss.id()).get(), EchoBossEntity.attributes(boss).build());
    }
    private static void spawnPlacement(RegisterSpawnPlacementsEvent event) {
        for (var type : ECHO_BOSSES.values()) event.register(type.get(), SpawnPlacementTypes.ON_GROUND,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EchoBossEntity::spawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ALEPH.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}
