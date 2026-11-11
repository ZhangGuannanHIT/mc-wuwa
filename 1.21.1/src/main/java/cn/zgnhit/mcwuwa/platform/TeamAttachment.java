package cn.zgnhit.mcwuwa.platform;
import cn.zgnhit.mcwuwa.core.*;
import java.util.*;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/** Durable actual weapon slots. The hotbar exposes a reference and owns no copied stack. */
public final class TeamAttachment implements INBTSerializable<CompoundTag> {
    public final Player player;
    private TeamState state;
    private final TeamContainer inventory;
    public final Progression progression = new Progression();
    private boolean loading;
    private int clientActive = -1;
    private final Map<UUID, Double> loadedHealth = new HashMap<>();
    public long lastInputSequence = -1;
    public TeamAttachment(Player player) { this.player = player; state = new TeamState(player.getUUID()); inventory = new TeamContainer(); }
    public TeamState state() { return state; }
    public TeamContainer inventory() { return inventory; }
    public boolean hasTeam() { return player.level().isClientSide() ? !inventory.isEmpty() : state.hasTeam(); }
    public ItemStack activeStack() {
        int slot = player.level().isClientSide() ? clientActive : state.activeSlot();
        return slot < 0 || slot > 2 ? ItemStack.EMPTY : inventory.getItem(slot);
    }
    public void acceptClientView(List<ItemStack> stacks, int active) {
        if (!player.level().isClientSide()) throw new IllegalStateException("client only");
        // Vanilla slot36 packets mirror the virtual hand into the client's raw list.
        // Once the final team slot disappears that mirror must never become ownership.
        var raw=player.getInventory().items;
        if(stacks.stream().allMatch(ItemStack::isEmpty)&&!inventory.isEmpty()){
            ItemStack mirror=raw.get(0);
            if(mirror.getItem() instanceof WeaponItem){
                String id=WeaponData.tag(mirror).getString("instance");
                for(int i=0;i<3;i++)if(!id.isEmpty()&&id.equals(WeaponData.tag(inventory.getItem(i)).getString("instance")))raw.set(0,ItemStack.EMPTY);
            }
        }
        loading = true; try {
            for (int i = 0; i < 3; i++)
                if (!ItemStack.matches(inventory.getItem(i), stacks.get(i))) inventory.setItem(i, stacks.get(i));
            clientActive = active;
        } finally { loading = false; }
    }
    public boolean canEquip(int slot, ItemStack stack) {
        if (slot < 0 || slot >= 3 || !(stack.getItem() instanceof WeaponItem weapon) || stack.getCount() != 1 || state.inCombat()) return false;
        String instance = WeaponData.tag(stack).getString("instance");
        if (!player.level().isClientSide()) {
            try { instance = WeaponData.read(stack.copy()).instanceId().toString(); }
            catch (IllegalArgumentException | IllegalStateException invalidState) { return false; }
        }
        for (int i = 0; i < 3; i++) if (i != slot && inventory.getItem(i).getItem() instanceof WeaponItem other) {
            if (other.character() == weapon.character()) return false;
            if (!instance.isEmpty() && instance.equals(WeaponData.tag(inventory.getItem(i)).getString("instance"))) return false;
        }
        return true;
    }
    private boolean sameRole(ItemStack stack, CharacterInstance role) {
        return stack.getItem() instanceof WeaponItem weapon && weapon.character() == role.character()
            && WeaponData.tag(stack).getString("instance").equals(role.instanceId().toString());
    }
    private void reconcile() {
        if (loading || player.level().isClientSide()) return;
        boolean before = state.hasTeam(), changed = false;
        boolean frostBefore=player instanceof ServerPlayer server&&EffectRuntime.hasFrostConverter(server);
        for (int i = 0; i < 3; i++) {
            ItemStack item = inventory.getItem(i); var old = state.at(i);
            if (old.isPresent() && !sameRole(item, old.get())) {
                var result = state.unequip(i); if (!result.success()) throw new IllegalStateException("combat menu mutation");
                EffectRuntime.weaponUnequipped(old.get().instanceId());
                changed = true;
            }
            if (item.getItem() instanceof WeaponItem && state.at(i).isEmpty()) {
                var result = state.equip(i, WeaponData.readEquipped(item, player)); if (!result.success()) throw new IllegalStateException("invalid team inventory " + result.failure());
                changed = true;
            }
        }
        if (!before && state.hasTeam()) {
            var raw = player.getInventory().items; ItemStack previous = raw.get(0); raw.set(0, ItemStack.EMPTY);
            if (!previous.isEmpty() && !player.getInventory().add(previous)) player.drop(previous, false);
        }
        if (changed) {
            if(player instanceof ServerPlayer server&&frostBefore!=EffectRuntime.hasFrostConverter(server))EffectRuntime.frostTeamChanged(server,!frostBefore);
            if (player instanceof ServerPlayer serverPlayer) PlatformRuntime.teamChanged(serverPlayer);
            PlatformRuntime.applyHealth(player, this, true);
        }
    }
    public void flushWeapons() {
        for (int i = 0; i < 3; i++) {
            ItemStack stack = inventory.getItem(i);
            state.at(i).ifPresent(role -> {
                WeaponData.write(stack, role);
                // Entity effects can load after attachments. Preserve the raw HP checkpoint until login
                // applies current vanilla modifiers; never save those external modifiers as permanent gear.
                if (loadedHealth.containsKey(role.instanceId())) {
                    var tag = WeaponData.tag(stack); tag.putDouble("health", loadedHealth.get(role.instanceId()));
                    stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
                }
            });
        }
    }
    void restoreLoadedHealth(CharacterInstance role) {
        Double saved = loadedHealth.remove(role.instanceId());
        if (saved != null) role.commitHealth(Math.min(saved, role.maxHealth()));
    }
    /** Death-clone HP belongs to the old body. Discard it before projecting the newly restored team. */
    public void respawn() {
        loadedHealth.clear();
        state.respawn();
    }
    @Override public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        var out = new CompoundTag();
        flushWeapons(); out.putInt("schema", 1);
        for (int i = 0; i < 3; i++) out.put("weapon_" + i, inventory.getItem(i).saveOptional(provider));
        var snapshot = state.snapshot(); out.putInt("active", snapshot.activeSlot());
        out.putLong("switch_cd", snapshot.switchCooldown()); out.putLong("revive_cd", snapshot.reviveCooldown());
        out.putDouble("stamina", snapshot.stamina().stamina());
        out.putLong("stamina_recovery_delay", Math.max(0, snapshot.stamina().recoveryReadyAt() - state.onlineTick()));
        return out;
    }
    @Override public void deserializeNBT(HolderLookup.Provider provider, CompoundTag input) {
        if ((input.contains("schema",99) ? input.getInt("schema") : 1) != 1) throw new IllegalStateException("Unsupported mc_wuwa team schema; preserve save");
        loading = true;
        try { for (int i = 0; i < 3; i++) inventory.setItem(i, ItemStack.parseOptional(provider, input.getCompound("weapon_" + i))); }
        finally { loading = false; }
        state = new TeamState(player.getUUID()); loadedHealth.clear();
        for (int i = 0; i < 3; i++) if (!inventory.getItem(i).isEmpty()) {
            var stack = inventory.getItem(i); var role = WeaponData.readEquipped(stack, player);
            var equip = state.equip(i, role); if (!equip.success()) throw new IllegalStateException("Corrupt team: " + equip.failure());
            loadedHealth.put(role.instanceId(), cn.zgnhit.mcwuwa.platform.NbtValues.getDouble(WeaponData.tag(stack),"health", role.health()));
        }
        state.restoreActiveSlot((input.contains("active",99) ? input.getInt("active") : -1));
        state.restorePlayerCooldowns(input.getLong("switch_cd"), input.getLong("revive_cd"));
        state.stamina().restoreStamina((input.contains("stamina",99) ? input.getDouble("stamina") : StaminaDodge.MAX_STAMINA));
        state.stamina().restoreRecoveryDelay(input.getLong("stamina_recovery_delay")); state.rejoin();
    }
    public final class TeamContainer extends SimpleContainer {
        private TeamContainer() { super(3); }
        @Override public boolean canPlaceItem(int slot, ItemStack stack) { return canEquip(slot, stack); }
        @Override public ItemStack removeItem(int slot, int count) {
            if (count <= 0) return ItemStack.EMPTY;
            if (!loading && !player.level().isClientSide() && state.inCombat()) return ItemStack.EMPTY;
            if (!loading && !player.level().isClientSide() && state.at(slot).isPresent()) {
                PlatformRuntime.saveCurrentHealth(player, TeamAttachment.this);
                var role = state.at(slot).orElseThrow(); role.resetOnUnequip(); WeaponData.write(getItem(slot), role);
            }
            return super.removeItem(slot, count);
        }
        @Override public ItemStack removeItemNoUpdate(int slot) {
            if (!loading && !player.level().isClientSide() && state.inCombat()) return ItemStack.EMPTY;
            if (!loading && !player.level().isClientSide() && state.at(slot).isPresent()) {
                PlatformRuntime.saveCurrentHealth(player, TeamAttachment.this);
                var role = state.at(slot).orElseThrow(); role.resetOnUnequip(); WeaponData.write(getItem(slot), role);
            }
            ItemStack result = super.removeItemNoUpdate(slot); setChanged(); return result;
        }
        @Override public void setItem(int slot, ItemStack stack) {
            if (!loading && !player.level().isClientSide()) {
                if (state.inCombat()) return;
                if (!stack.isEmpty() && !canEquip(slot, stack)) return;
                var old = state.at(slot);
                if (getItem(slot) != stack) {
                    PlatformRuntime.saveCurrentHealth(player, TeamAttachment.this);
                    if (old.isPresent()) {
                        if (sameRole(stack, old.get())) WeaponData.write(stack, old.get());
                        else { old.get().resetOnUnequip(); WeaponData.write(getItem(slot), old.get()); }
                    }
                }
            }
            super.setItem(slot, stack);
        }
        @Override public void setChanged() { super.setChanged(); reconcile(); }
    }
}
