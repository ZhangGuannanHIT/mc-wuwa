package cn.zgnhit.mcwuwa.platform;

import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Uses vanilla persistent criterion progress; equipment slots are real ownership too. */
public final class CombatAdvancements {
    public static final Set<String> MONSTERS = Set.of("aleph_one", "impermanence_heron", "jue",
        "nightmare_crownless", "nightmare_hecate", "hyvatia", "furnace_husk", "sigillum");

    private CombatAdvancements() {}

    public static void inventory(ServerPlayer player) {
        // Also covers weapons already equipped when upgrading an existing save.
        // Run before the no-team early return: collecting does not require equipping.
        if (player.tickCount % 20 != 0) return;
        discoverAddedWeaponRecipes(player);
        var advancement = player.level().getServer().getAdvancements().get(id("star_tuner"));
        if (advancement == null) return;
        var progress = player.getAdvancements().getOrStartProgress(advancement);
        // Native inventory triggers can finish before the first scheduled scan.
        if (progress.getCompletedCriteria().iterator().hasNext()) award(player, "root", "begin");
        if (progress.isDone()) return;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) collected(player, inventory.getItem(slot));
        var team = ModAttachments.team(player).inventory();
        for (int slot = 0; slot < team.getContainerSize(); slot++) collected(player, team.getItem(slot));
        collected(player, player.containerMenu.getCarried());
    }

    private static void collected(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof WeaponItem weapon)) return;
        award(player, "root", "begin");
        award(player, "star_tuner", weapon.character().weaponPath());
    }

    /** A completed old reward is not replayed when a newer version adds weapon recipes. */
    private static void discoverAddedWeaponRecipes(ServerPlayer player) {
        var core=player.level().getServer().getAdvancements().get(Identifier.fromNamespaceAndPath("mc_wuwa","recipes/core_weapons"));
        if(core==null||!player.getAdvancements().getOrStartProgress(core).isDone())return;
        var missing=new java.util.ArrayList<net.minecraft.resources.ResourceKey<net.minecraft.world.item.crafting.Recipe<?>>>();
        var recipes=player.level().getServer().getRecipeManager();
        for(var role:cn.zgnhit.mcwuwa.core.CharacterId.values()){
            var key=net.minecraft.resources.ResourceKey.<net.minecraft.world.item.crafting.Recipe<?>>create(net.minecraft.core.registries.Registries.RECIPE,Identifier.parse(role.weaponId()));
            if(!player.getRecipeBook().contains(key)&&recipes.byKey(key).isPresent())missing.add(key);
        }
        if(!missing.isEmpty())player.awardRecipesByKey(missing);
    }

    /** Called after real death by LivingDropsEvent, including player-owned skill kills. */
    public static void defeated(ServerPlayer player, LivingEntity victim) {
        if (victim.isAlive() || victim.getTags().contains("mc_wuwa:skill_summon")) return;
        var type = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType());
        if (!type.getNamespace().equals("mc_wuwa") || !MONSTERS.contains(type.getPath())) return;
        award(player, "root", "begin");
        award(player, "charioteer", type.getPath());
    }

    private static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath("mc_wuwa", "combat/" + name);
    }

    private static void award(ServerPlayer player, String name, String criterion) {
        var advancement = player.level().getServer().getAdvancements().get(id(name));
        if (advancement == null) return; // Datapacks may intentionally remove an advancement.
        var progress = player.getAdvancements().getOrStartProgress(advancement);
        var entry = progress.getCriterion(criterion);
        if (entry != null && !entry.isDone()) player.getAdvancements().award(advancement, criterion);
    }
}
