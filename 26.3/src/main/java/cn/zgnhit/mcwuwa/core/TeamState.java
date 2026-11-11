package cn.zgnhit.mcwuwa.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

/** Server-owned team; ItemStack movement / golden apple removal belong in the enclosing platform transaction. */
public final class TeamState {
    public enum Failure { NONE, IN_COMBAT, SLOT_OCCUPIED, SLOT_EMPTY, DUPLICATE_CHARACTER, DUPLICATE_INSTANCE,
        DOWNED, SWITCH_COOLDOWN, ALREADY_ACTIVE, REVIVE_COOLDOWN, NOT_DOWNED, MATERIALS, ALL_DOWN }
    public record Result(Failure failure) { public boolean success() { return failure == Failure.NONE; } }
    public record SwitchResult(Failure failure, int previousSlot, int currentSlot, boolean introOutro, boolean consumedJinhsiUnison) {
        public boolean success() { return failure == Failure.NONE; }
        public boolean consumedUnison(){return consumedJinhsiUnison;}
    }
    public record UnequipResult(Failure failure, Optional<CharacterInstance> removed, boolean restoreVanillaFullHealth) {
        public boolean success() { return failure == Failure.NONE; }
    }
    public record DefeatResult(int previousSlot, int activeSlot, boolean playerDeath) {}
    public record Snapshot(UUID playerId, List<Optional<CharacterInstance.Snapshot>> slots, int activeSlot,
                           int selectedHotbarSlot, long switchCooldown, long reviveCooldown, boolean inCombat,
                           StaminaDodge.Snapshot stamina) {}
    @FunctionalInterface public interface DeathProtection { OptionalDouble tryPreventDeath(CharacterInstance current); }
    private final UUID playerId;
    private final CharacterInstance[] slots = new CharacterInstance[3];
    private final StaminaDodge stamina;
    private int activeSlot = -1, selectedHotbarSlot;
    private long now, lastCombatActivity = -60, switchCooldown, reviveCooldown;
    private boolean nearbyAggro;
    public TeamState(UUID playerId) { this.playerId = Objects.requireNonNull(playerId); stamina = new StaminaDodge(0); }
    private static void slot(int slot) { if (slot < 0 || slot >= 3) throw new IllegalArgumentException("team slot"); }
    public UUID playerId() { return playerId; }
    public long onlineTick() { return now; }
    public int activeSlot() { return activeSlot; }
    public int selectedHotbarSlot() { return selectedHotbarSlot; }
    public boolean hasTeam() { return Arrays.stream(slots).anyMatch(Objects::nonNull); }
    public boolean inCombat() { return nearbyAggro || now - lastCombatActivity < 60; }
    public boolean wuwaMode() { return hasTeam() && selectedHotbarSlot == 0; }
    public boolean canAct() { return wuwaMode() && active().map(CharacterInstance::alive).orElse(false); }
    public StaminaDodge stamina() { return stamina; }
    public Optional<CharacterInstance> at(int index) { slot(index); return Optional.ofNullable(slots[index]); }
    public Optional<CharacterInstance> active() { return activeSlot < 0 ? Optional.empty() : Optional.ofNullable(slots[activeSlot]); }
    public void recordCombatActivity() { lastCombatActivity = now; }
    public void setNearbyAggro(boolean within32AndTargetingPlayer) { nearbyAggro = within32AndTargetingPlayer; }
    public void tickOnline(long ticks, boolean sprinting, boolean vanillaHungerAllows) {
        tickOnline(ticks,sprinting,vanillaHungerAllows,true);
    }
    public void tickOnline(long ticks, boolean sprinting, boolean vanillaHungerAllows, boolean staminaRecoveryAllowed) {
        Checks.tick(ticks);
        for (long i = 0; i < ticks; i++) {
            now = Math.addExact(now, 1); switchCooldown = Math.max(0, switchCooldown - 1); reviveCooldown = Math.max(0, reviveCooldown - 1);
            for (CharacterInstance role : slots) if (role != null) role.tickCooldowns(1);
            stamina.advanceTo(now, wuwaMode() && sprinting, inCombat(), vanillaHungerAllows,staminaRecoveryAllowed);
        }
    }
    public Result equip(int index, CharacterInstance weapon) {
        slot(index); Objects.requireNonNull(weapon);
        if (inCombat()) return new Result(Failure.IN_COMBAT);
        if (slots[index] != null) return new Result(Failure.SLOT_OCCUPIED);
        for (CharacterInstance role : slots) if (role != null) {
            if (role.instanceId().equals(weapon.instanceId())) return new Result(Failure.DUPLICATE_INSTANCE);
            if (role.character() == weapon.character()) return new Result(Failure.DUPLICATE_CHARACTER);
        }
        slots[index] = weapon; if (activeSlot < 0) activeSlot = index; return new Result(Failure.NONE);
    }
    public UnequipResult unequip(int index) {
        slot(index);
        if (inCombat()) return new UnequipResult(Failure.IN_COMBAT, Optional.empty(), false);
        CharacterInstance removed = slots[index];
        if (removed == null) return new UnequipResult(Failure.SLOT_EMPTY, Optional.empty(), false);
        removed.resetOnUnequip(); slots[index] = null;
        if (activeSlot == index) activeSlot = firstAliveSlot();
        if (activeSlot < 0) activeSlot = firstOccupiedSlot();
        if (!hasTeam()) { activeSlot = -1; stamina.clearActionWindows(); }
        return new UnequipResult(Failure.NONE, Optional.of(removed), !hasTeam());
    }
    public String selectHotbar(int index) {
        if (index < 0 || index > 8) throw new IllegalArgumentException("hotbar slot");
        String cancelled = "";
        if (selectedHotbarSlot == 0 && index != 0) {
            cancelled = active().map(CharacterInstance::cancelAction).orElse(""); stamina.clearActionWindows();
        }
        selectedHotbarSlot = index; return cancelled;
    }
    public SwitchResult switchTo(int index) {
        slot(index); int previous = activeSlot;
        Failure failure = slots[index] == null ? Failure.SLOT_EMPTY : !slots[index].alive() ? Failure.DOWNED
            : activeSlot == index ? Failure.ALREADY_ACTIVE : switchCooldown > 0 ? Failure.SWITCH_COOLDOWN : Failure.NONE;
        if (failure != Failure.NONE) return new SwitchResult(failure, previous, activeSlot, false, false);
        boolean introOutro = false, unison = false;
        CharacterInstance source = active().orElse(null);
        if (source != null && source.alive() && inCombat()) {
            if (source.character() == CharacterId.JINHSI && source.hasTemporaryState("jinhsi_unison")
                ||source.character()==CharacterId.HSIN&&source.hasTemporaryState("hsin_unison")
                ||source.character().sourceId()==1312&&source.hasTemporaryState("suoming_unison")) {
                source.removeTemporaryState("jinhsi_unison");source.removeTemporaryState("hsin_unison");
                source.removeTemporaryState("suoming_unison");
                source.putTemporaryState("unison_consumed_pending");introOutro = unison = true;
            } else if (source.definition().resources().containsKey("concerto")
                && source.resourceMax("concerto") > 0 && source.resource("concerto") >= source.resourceMax("concerto")) {
                source.pay(java.util.Map.of("concerto", source.resourceMax("concerto"))); introOutro = true;
            }
        }
        if (source != null) source.cancelAction();
        stamina.clearActionWindows(); activeSlot = index; switchCooldown = 20;
        return new SwitchResult(Failure.NONE, previous, activeSlot, introOutro, unison);
    }
    /** Read-only qualification for both HUD and switching; receiving roles do not need concerto. */
    public boolean introOutroReady() {
        CharacterInstance source=active().orElse(null);
        return switchCooldown==0&&inCombat()&&source!=null&&source.alive()
            &&(source.character()==CharacterId.JINHSI&&source.hasTemporaryState("jinhsi_unison")
                ||source.character()==CharacterId.HSIN&&source.hasTemporaryState("hsin_unison")
                ||source.character().sourceId()==1312&&source.hasTemporaryState("suoming_unison")
                ||source.definition().resources().containsKey("concerto")&&source.resourceMax("concerto")>0
                    &&source.resource("concerto")>=source.resourceMax("concerto"));
    }
    /** The adapter must perform collision/movement cleanup for the returned cancelled source as well. */
    public DefeatResult processCurrentDefeat(DeathProtection roleProtection, DeathProtection totem) {
        Objects.requireNonNull(roleProtection); Objects.requireNonNull(totem);
        int previous = activeSlot; CharacterInstance current = active().orElse(null);
        if (current == null || current.alive()) return new DefeatResult(previous, activeSlot, false);
        OptionalDouble protection = roleProtection.tryPreventDeath(current);
        if (protection.isEmpty()) protection = totem.tryPreventDeath(current);
        if (protection.isPresent()) {
            double restored = protection.getAsDouble(); Checks.positive("death protection health", restored);
            current.commitHealth(Math.min(current.maxHealth(), restored)); return new DefeatResult(previous, activeSlot, false);
        }
        current.cancelAction(); stamina.clearActionWindows(); activeSlot = firstAliveSlot();
        if (activeSlot < 0) for (CharacterInstance role : slots) if (role != null) role.clearOnLogout();
        return new DefeatResult(previous, activeSlot, activeSlot < 0);
    }
    Result validateRevive(int index) {
        slot(index);
        if (slots[index] == null) return new Result(Failure.SLOT_EMPTY);
        if (slots[index].alive()) return new Result(Failure.NOT_DOWNED);
        if (reviveCooldown > 0) return new Result(Failure.REVIVE_COOLDOWN);
        return new Result(Failure.NONE);
    }
    void commitRevive(int index) {
        Result check = validateRevive(index); if (!check.success()) throw new IllegalStateException(check.failure.toString());
        slots[index].reviveHalf(); reviveCooldown = 1200; if (activeSlot < 0) activeSlot = index;
    }
    public void sleepSkippedNight() { for (CharacterInstance role : slots) if (role != null) role.sleepFullHeal(); if (activeSlot < 0) activeSlot = firstAliveSlot(); }
    public void logout() { for (CharacterInstance role : slots) if (role != null) role.clearOnLogout(); stamina.clearActionWindows(); }
    public void rejoin() {
        for (CharacterInstance role : slots) if (role != null) role.resetOnRejoin();
        if (activeSlot < 0 || slots[activeSlot] == null || !slots[activeSlot].alive()) activeSlot = firstAliveSlot();
        stamina.clearActionWindows();
    }
    public void respawn() {
        for (CharacterInstance role : slots) if (role != null) role.resetOnRespawn();
        activeSlot = firstAliveSlot(); stamina.clearActionWindows();
    }
    public void restorePlayerCooldowns(long savedSwitchTicks, long savedReviveTicks) {
        Checks.tick(savedSwitchTicks); Checks.tick(savedReviveTicks); switchCooldown = savedSwitchTicks; reviveCooldown = savedReviveTicks;
    }
    /** Call after restoring the actual three item slots; this does not trigger intro/outro or a fresh switch cooldown. */
    public void restoreActiveSlot(int savedSlot) {
        if (savedSlot == -1) {
            // Empty teams and all-downed teams legitimately persist no active slot.
            // An older checkpoint lacking this field may still contain survivors: recover deterministically.
            activeSlot = firstAliveSlot(); return;
        }
        slot(savedSlot);
        if (slots[savedSlot] == null) throw new IllegalArgumentException("saved active slot is empty");
        activeSlot = slots[savedSlot].alive() ? savedSlot : firstAliveSlot();
    }
    private int firstAliveSlot() { for (int i = 0; i < 3; i++) if (slots[i] != null && slots[i].alive()) return i; return -1; }
    private int firstOccupiedSlot() { for (int i = 0; i < 3; i++) if (slots[i] != null) return i; return -1; }
    public Snapshot snapshot() {
        List<Optional<CharacterInstance.Snapshot>> result = new ArrayList<>();
        for (CharacterInstance role : slots) result.add(role == null ? Optional.empty() : Optional.of(role.snapshot()));
        return new Snapshot(playerId, List.copyOf(result), activeSlot, selectedHotbarSlot, switchCooldown, reviveCooldown, inCombat(), stamina.snapshot());
    }
}
