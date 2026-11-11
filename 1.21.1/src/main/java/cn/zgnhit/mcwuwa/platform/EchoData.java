package cn.zgnhit.mcwuwa.platform;

import java.util.*;
import cn.zgnhit.mcwuwa.core.EnemyProfile;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Max-level, max-roll pools. A secondary main stat is fixed by COST, not randomly duplicated. */
public final class EchoData {
    /** Uses the same eligibility/COST rules as real death drops; excludes NPCs and skill-created entities. */
    public static List<ResourceLocation> species() {
        return BuiltInRegistries.ENTITY_TYPE.stream().filter(type->DefaultAttributes.hasSupplier(type))
            .filter(type->{String id=BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();return EnemyProfile.forEntity(id,type.getCategory()==MobCategory.MONSTER,false).echoEligible();})
            .map(BuiltInRegistries.ENTITY_TYPE::getKey).sorted(Comparator.comparing(ResourceLocation::toString)).toList();
    }
    public static OptionalInt speciesCost(ResourceLocation species) {
        var type=BuiltInRegistries.ENTITY_TYPE.getOptional(species).orElse(null);
        if(type==null||!DefaultAttributes.hasSupplier(type))return OptionalInt.empty();
        var profile=EnemyProfile.forEntity(species.toString(),type.getCategory()==MobCategory.MONSTER,false);
        return profile.echoEligible()?OptionalInt.of(profile.tier().cost):OptionalInt.empty();
    }
    public static ItemStack create(ResourceLocation species,RandomSource rng) {
        var cost=speciesCost(species);if(cost.isEmpty())return ItemStack.EMPTY;
        var stack=ModItems.ECHO.get().getDefaultInstance();var data=new CompoundTag();
        data.putInt("schema",1);data.putString("instance",UUID.randomUUID().toString());data.putString("species",species.toString());data.putInt("cost",cost.getAsInt());
        rollInto(data,cost.getAsInt(),rng);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));decorate(stack,species);return stack;
    }
    /** Catalogue entries carry only a species selector. The server rolls the real item on pickup. */
    public static ItemStack creativeTemplate(ResourceLocation species) {
        var cost=speciesCost(species);if(cost.isEmpty())return ItemStack.EMPTY;
        var stack=ModItems.ECHO.get().getDefaultInstance();var data=new CompoundTag();
        data.putInt("schema",1);data.putString("species",species.toString());data.putInt("cost",cost.getAsInt());data.putBoolean("creative_template",true);
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));decorate(stack,species);return stack;
    }
    public static Optional<ResourceLocation> templateSpecies(ItemStack stack) {
        if(!stack.is(ModItems.ECHO.get())||stack.getCount()!=1)return Optional.empty();
        var data=data(stack);if(!data.getBoolean("creative_template"))return Optional.empty();
        var id=ResourceLocation.tryParse(data.getString("species"));
        return id!=null&&speciesCost(id).isPresent()?Optional.of(id):Optional.empty();
    }
    private static final java.util.List<String> FACE_KEYS=java.util.List.of("aleph_one", "allay", "armadillo", "axolotl", "bat", "bee", "blaze", "bogged", "breeze", "camel", "camel_husk", "cat", "cave_spider", "chicken", "cod", "copper_golem", "cow", "creaking", "creeper", "dolphin", "donkey", "drowned", "elder_guardian", "ender_dragon", "enderman", "endermite", "evoker", "fox", "frog", "furnace_husk", "ghast", "giant", "glow_squid", "goat", "guardian", "happy_ghast", "hoglin", "horse", "husk", "hyvatia", "illusioner", "impermanence_heron", "iron_golem", "jue", "llama", "magma_cube", "mooshroom", "mule", "nautilus", "nightmare_crownless", "nightmare_hecate", "ocelot", "panda", "parched", "parrot", "phantom", "pig", "piglin", "piglin_brute", "pillager", "polar_bear", "pufferfish", "rabbit", "ravager", "salmon", "sheep", "shulker", "sigillum", "silverfish", "skeleton", "skeleton_horse", "slime", "sniffer", "snow_golem", "spider", "squid", "stray", "strider", "tadpole", "trader_llama", "tropical_fish", "turtle", "vex", "vindicator", "warden", "witch", "wither", "wither_skeleton", "wolf", "zoglin", "zombie", "zombie_horse", "zombie_nautilus", "zombie_villager", "zombified_piglin");
    public static void decorate(ItemStack stack,ResourceLocation species) {
        stack.set(DataComponents.CUSTOM_MODEL_DATA,new net.minecraft.world.item.component.CustomModelData(FACE_KEYS.indexOf(species.getPath())+1));
        stack.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.translatable("entity."+species.toString().replace(':','.')).append(" · 声骸"));
    }
    public static void restoreAppearance(ItemStack stack) {
        if(!stack.is(ModItems.ECHO.get()))return;
        var id=ResourceLocation.tryParse(data(stack).getString("species"));
        if(id!=null&&speciesCost(id).isPresent()) {
            var model=new net.minecraft.world.item.component.CustomModelData(FACE_KEYS.indexOf(id.getPath())+1);
            if(!model.equals(stack.get(DataComponents.CUSTOM_MODEL_DATA)))decorate(stack,id);
        }
    }
    public static final Map<String, Double> SUBS = Map.ofEntries(
        Map.entry("crit_rate", .105), Map.entry("crit_damage", .21), Map.entry("attack_pct", .116),
        Map.entry("health_pct", .116), Map.entry("defense_pct", .147), Map.entry("attack_flat", 60.0),
        Map.entry("health_flat", 580.0), Map.entry("defense_flat", 70.0), Map.entry("energy_regen", .124),
        Map.entry("basic_bonus", .116), Map.entry("heavy_bonus", .116), Map.entry("skill_bonus", .116), Map.entry("liberation_bonus", .116));
    public static void rollInto(CompoundTag data, int cost, RandomSource rng) {
        Map<String, Double> pool = mainPool(cost);
        List<String> keys = new ArrayList<>(pool.keySet()); Collections.sort(keys); String main = keys.get(rng.nextInt(keys.size()));
        CompoundTag mainStats = new CompoundTag(); mainStats.putDouble(main, pool.get(main));
        mainStats.putDouble(cost == 1 ? "health_flat" : "attack_flat", cost == 1 ? 2280 : cost == 3 ? 100 : 150);
        CompoundTag subStats = new CompoundTag(); keys = new ArrayList<>(SUBS.keySet()); Collections.sort(keys);
        for (int i = 0; i < 5; i++) { int j = i + rng.nextInt(keys.size() - i); Collections.swap(keys, i, j); subStats.putDouble(keys.get(i), SUBS.get(keys.get(i))); }
        data.put("main_stats", mainStats); data.put("sub_stats", subStats);
    }
    private static Map<String, Double> mainPool(int cost) {
        if (cost != 1 && cost != 3 && cost != 4) throw new IllegalArgumentException("echo COST");
        Map<String, Double> pool = new LinkedHashMap<>();
        pool.put("attack_pct", cost == 1 ? .18 : cost == 3 ? .30 : .33); pool.put("health_pct", cost == 1 ? .228 : cost == 3 ? .30 : .33);
        pool.put("defense_pct", cost == 1 ? .18 : cost == 3 ? .38 : .418);
        if (cost == 3) { for (String element : List.of("glacio", "fusion", "electro", "aero", "spectro", "havoc")) pool.put(element + "_bonus", .30); pool.put("energy_regen", .32); }
        if (cost == 4) { pool.put("crit_rate", .22); pool.put("crit_damage", .44); pool.put("healing_bonus", .264); }
        return Map.copyOf(pool);
    }
    public static CompoundTag data(ItemStack stack) { return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(); }
    public static boolean valid(ItemStack stack) {
        if (stack.isEmpty() || stack.getCount() != 1 || !stack.is(ModItems.ECHO.get())) return false;
        CompoundTag data = data(stack); int cost = data.getInt("cost");
        if(data.getBoolean("creative_template"))return false;
        if (data.getInt("schema") != 1 || cost != 1 && cost != 3 && cost != 4) return false;
        String identity = data.getString("instance");
        try { if (!UUID.fromString(identity).toString().equals(identity)) return false; }
        catch (IllegalArgumentException malformed) { return false; }
        String species = data.getString("species"); ResourceLocation speciesId = ResourceLocation.tryParse(species);
        if (speciesId == null || !speciesId.toString().equals(species)) return false;
        var type = BuiltInRegistries.ENTITY_TYPE.getOptional(speciesId).orElse(null);
        if (type == null || !DefaultAttributes.hasSupplier(type) && !species.equals("mc_wuwa:aleph_one")) return false;
        var profile = EnemyProfile.forEntity(species, type.getCategory() == MobCategory.MONSTER, false);
        if (!profile.echoEligible() || profile.tier().cost != cost) return false;
        CompoundTag main = data.getCompound("main_stats"), sub = data.getCompound("sub_stats");
        if (main.size() != 2 || sub.size() != 5) return false;
        String fixed = cost == 1 ? "health_flat" : "attack_flat"; double fixedValue = cost == 1 ? 2280 : cost == 3 ? 100 : 150;
        if (Double.compare(cn.zgnhit.mcwuwa.platform.NbtValues.getDouble(main,fixed, Double.NaN), fixedValue) != 0) return false;
        Map<String, Double> pool = mainPool(cost);
        for (String key : main.getAllKeys()) if (!key.equals(fixed) && (!pool.containsKey(key) || Double.compare(cn.zgnhit.mcwuwa.platform.NbtValues.getDouble(main,key, Double.NaN), pool.get(key)) != 0)) return false;
        for (String key : sub.getAllKeys()) if (!SUBS.containsKey(key) || Double.compare(cn.zgnhit.mcwuwa.platform.NbtValues.getDouble(sub,key, Double.NaN), SUBS.get(key)) != 0) return false;
        return true;
    }
    private EchoData() { }
}
