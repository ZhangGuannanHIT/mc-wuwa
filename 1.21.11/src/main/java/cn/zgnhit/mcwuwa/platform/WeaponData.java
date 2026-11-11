package cn.zgnhit.mcwuwa.platform;

import cn.zgnhit.mcwuwa.McWuwa;
import cn.zgnhit.mcwuwa.content.CharacterDefinition;
import cn.zgnhit.mcwuwa.core.CharacterInstance;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Only real weapon ItemStacks own durable character state; hotbar references never store a copy. */
public final class WeaponData {
    public static CompoundTag tag(ItemStack stack) { return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(); }
    public static CharacterInstance read(ItemStack stack) {
        if (!(stack.getItem() instanceof WeaponItem weapon) || stack.getCount() != 1) throw new IllegalArgumentException("expected one role weapon");
        CharacterDefinition data = McWuwa.catalog().requireByWeapon(weapon.character().weaponPath());
        CompoundTag tag = tag(stack);
        if (tag.getIntOr("schema", 1) != 1) throw new IllegalStateException("Unsupported weapon schema; preserve item data");
        String uuid = tag.getStringOr("instance", "");
        if (uuid.isEmpty()) { uuid = UUID.randomUUID().toString(); tag.putString("instance", uuid); }
        Map<String, CharacterInstance.Resource> resources = new HashMap<>();
        resources.put("concerto", new CharacterInstance.Resource(0, 100));
        double energyMax = data.liberationEnergyCost().orElseGet(()->{
            if(data.characterId().equals("phrolova"))return 0; // Her liberation uses Forte.
            if(data.characterId().equals("lucilla"))return 0; // Clear As Day requires150 Impression, not Resonance Energy.
            throw new IllegalStateException(data.characterId()+": missing native liberation energy capacity");
        });
        resources.put("energy", new CharacterInstance.Resource(0, energyMax));
        Set<String> modes = new HashSet<>(); modes.add("default"); data.modes().forEach(m -> modes.add(Integer.toString(m.sourceId())));
        UUID instanceId = UUID.fromString(uuid); tag.putString("instance", instanceId.toString());
        CharacterInstance result = new CharacterInstance(instanceId, new CharacterInstance.Definition(weapon.character(), data.baseStats().health(), resources, "default", modes));
        Map<String, Long> cooldowns = new HashMap<>(); CompoundTag cd = tag.getCompoundOrEmpty("cooldowns");
        cd.keySet().forEach(key -> cooldowns.put(key, Math.max(0, cd.getLongOr(key, 0))));
        CompoundTag counters=tag.getCompoundOrEmpty("mechanicsCounters");
        cn.zgnhit.mcwuwa.content.mechanics.MechanicsEngine.persistentCounterKeys(data.characterId()).forEach(key->result.setPersistentCounter(key,Math.clamp(counters.getIntOr(key,0),0,2)));
        int chain = tag.getIntOr("chain", 0), refinement = tag.getIntOr("refinement", 1);
        String mode = tag.getStringOr("mode", "default");
        // Restore permanent upgrades before evaluating their gear stats, then apply persisted HP against that maximum.
        // External MC Health Boost is deliberately absent from this persistent equipment calculation.
        result.restorePersistent(0, cooldowns, chain, refinement, mode);
        result.setMaxHealth(NativeStats.of(result, stack).health() * cn.zgnhit.mcwuwa.core.CombatMath.HEALTH_SCALE);
        double savedHealth = tag.getDoubleOr("health", result.maxHealth());
        if (!Double.isFinite(savedHealth) || savedHealth < 0) throw new IllegalArgumentException("invalid saved weapon health");
        result.restorePersistent(Math.min(result.maxHealth(), savedHealth), cooldowns, chain, refinement, mode);
        tag.putInt("schema", 1); stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag)); return result;
    }
    public static void write(ItemStack stack, CharacterInstance role) {
        CompoundTag tag = tag(stack); tag.putInt("schema", 1); tag.putString("instance", role.instanceId().toString());
        tag.putDouble("health", role.health()); tag.putInt("chain", role.chain()); tag.putInt("refinement", role.refinement()); tag.putString("mode", role.selectedMode());
        CompoundTag cd = new CompoundTag(); role.snapshot().cooldowns().forEach(cd::putLong); tag.put("cooldowns", cd);
        CompoundTag counters=new CompoundTag();role.persistentCounters().forEach(counters::putInt);tag.put("mechanicsCounters",counters);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
    /** Equipped instances use the holder's current vanilla HP modifiers; detached items remain gear-only. */
    public static CharacterInstance readEquipped(ItemStack stack, net.minecraft.world.entity.player.Player player) {
        CharacterInstance result = read(stack);
        result.setMaxHealth(PlatformRuntime.projectedMaxHealth(player, NativeStats.of(result, stack).health()));
        result.commitHealth(Math.min(result.maxHealth(), tag(stack).getDoubleOr("health", result.maxHealth())));
        return result;
    }
    private WeaponData() { }
}
