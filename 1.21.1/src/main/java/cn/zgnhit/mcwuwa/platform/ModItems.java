package cn.zgnhit.mcwuwa.platform;

import cn.zgnhit.mcwuwa.core.CharacterId;
import cn.zgnhit.mcwuwa.core.EnemyProfile;
import cn.zgnhit.mcwuwa.platform.entity.ModEntities;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

/** Registrations contain no client classes, so dedicated servers can load the mod. */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("mc_wuwa");
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "mc_wuwa");
    public static final Map<CharacterId, DeferredItem<WeaponItem>> WEAPONS = new LinkedHashMap<>();
    public static final Map<String, DeferredItem<SpawnEggItem>> BOSS_EGGS = new LinkedHashMap<>();
    public static final DeferredItem<CoreItem> CORE = ITEMS.registerItem("core", p -> new CoreItem(p.rarity(Rarity.EPIC).fireResistant()));
    public static final DeferredItem<SurveyMotorcycleItem> SURVEY_MOTORCYCLE=ITEMS.registerItem("survey_motorcycle",p->new SurveyMotorcycleItem(p.stacksTo(1).rarity(Rarity.RARE)));
    public static final DeferredItem<EchoItem> ECHO = ITEMS.registerItem("echo",p -> new EchoItem(p.stacksTo(1).rarity(Rarity.RARE)));
    public static final DeferredItem<SpawnEggItem> ALEPH_SPAWN_EGG = ITEMS.registerItem("aleph_one_spawn_egg",
        p -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.ALEPH, 0xffffff, 0xffffff, p));
    static {
        ModEntities.ECHO_BOSSES.forEach((id, type) -> BOSS_EGGS.put(id,
            ITEMS.registerItem(id + "_spawn_egg", p -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(type, 0xffffff, 0xffffff, p))));
        for (CharacterId role : CharacterId.values()) {
            WEAPONS.put(role, ITEMS.registerItem(role.weaponPath(), p -> new WeaponItem(role, p.stacksTo(1).fireResistant().rarity(Rarity.EPIC)
                .attributes(ItemAttributeModifiers.builder()
                    .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 7, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                    .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -2.4, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND).build()))));
        }
        TABS.register("equipment", () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0).title(Component.literal("鸣潮 · 战斗"))
            .icon(() -> WEAPONS.get(CharacterId.JINHSI).get().getDefaultInstance()).build());
        TABS.register("echoes", () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 1).title(Component.literal("鸣潮 · 声骸"))
            .icon(() -> EchoData.creativeTemplate(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft","creeper"))).build());
    }
    public static void register(IEventBus bus) {
        ITEMS.register(bus); TABS.register(bus);
        bus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey().location().equals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mc_wuwa", "equipment"))) {
                WEAPONS.values().forEach(item -> event.accept(item.get().getDefaultInstance())); event.accept(CORE.get().getDefaultInstance());
                event.accept(ALEPH_SPAWN_EGG.get());event.accept(SURVEY_MOTORCYCLE.get());
                BOSS_EGGS.values().forEach(egg -> event.accept(egg.get()));
            }
            if(event.getTabKey().location().equals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft","spawn_eggs"))) {
                event.accept(ALEPH_SPAWN_EGG.get());
                BOSS_EGGS.values().forEach(egg -> event.accept(egg.get()));
            }
            if(event.getTabKey().location().equals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mc_wuwa","echoes")))
                EchoData.species().forEach(species -> event.accept(EchoData.creativeTemplate(species)));
        });
    }
    public static ItemStack createEcho(LivingEntity entity, ServerPlayer owner) {
        String species = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        EnemyProfile profile = EnemyProfile.forEntity(species, entity.getType().getCategory() == MobCategory.MONSTER, false);
        if (!profile.echoEligible()) return ItemStack.EMPTY;
        return EchoData.create(net.minecraft.resources.ResourceLocation.parse(species),entity.getRandom());
    }
    private ModItems() { }
}
