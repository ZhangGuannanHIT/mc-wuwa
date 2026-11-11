package cn.zgnhit.mcwuwa.core;

/** Shared player-level stamina and generic dodge windows. All times are online ticks, not wall clock. */
public final class StaminaDodge {
    public static final double MAX_STAMINA = 240;
    public enum Incoming { EVADABLE_DIRECT, UNEVADABLE_DIRECT, PERIODIC, ENVIRONMENT, VOID }
    public enum AttackResult { NOT_PREVENTED, DODGE_SUCCEEDED, DIRECT_INVULNERABILITY }
    public record Snapshot(double stamina, long recoveryReadyAt, long attemptEndsAt, long attemptReadyAt,
                           long invulnerableUntil, long counterUntil, long dashEndsAt) {}
    private double stamina = MAX_STAMINA;
    private long now, recoveryReadyAt, attemptEndsAt, attemptReadyAt, invulnerableUntil, counterUntil, dashEndsAt;
    private boolean keyHeld,airDodgeUsed;
    public StaminaDodge(long now) { this.now = Checks.tick(now); }
    /** Returns false if ongoing sprint must stop due to depleted stamina. */
    public boolean advanceTo(long newTick, boolean sprinting, boolean inCombat, boolean vanillaHungerAllows) {
        return advanceTo(newTick,sprinting,inCombat,vanillaHungerAllows,true);
    }
    /** A live action may suppress recovery without creating a fake cooldown.
     * Existing spend/dodge clocks still advance; cancelling the action releases
     * only this gate, not the real recovery delay of an unrelated stamina spend. */
    public boolean advanceTo(long newTick, boolean sprinting, boolean inCombat, boolean vanillaHungerAllows, boolean recoveryAllowed) {
        Checks.tick(newTick);
        if (newTick < now) throw new IllegalArgumentException("stamina time moved backwards");
        boolean maySprint = !sprinting || vanillaHungerAllows;
        for (long t = now + 1; t <= newTick; t++) {
            if (attemptEndsAt != 0 && t >= attemptEndsAt) {
                attemptReadyAt = attemptEndsAt + 20; attemptEndsAt = 0;
            }
            if (sprinting && inCombat && maySprint) {
                if (stamina < 0.25) maySprint = false;
                else { stamina -= 0.25; recoveryReadyAt = t + 30; }
            }
            if (recoveryAllowed && (!sprinting || !inCombat || !maySprint) && t >= recoveryReadyAt)
                stamina = Math.min(MAX_STAMINA, stamina + 1.25);
        }
        now = newTick; return maySprint;
    }
    public boolean spend(double amount) {
        Checks.nonnegative("stamina cost", amount);
        if (stamina < amount) return false;
        stamina -= amount; if (amount > 0) recoveryReadyAt = now + 30; return true;
    }
    /** Call only when a character-specific dodge branch did not claim the key. */
    public boolean pressSprint() {
        return pressSprint(false,false);
    }
    /** One ordinary aerial attempt until landing/perfect evade. Form exceptions are server-owned. */
    public boolean pressSprint(boolean airborne,boolean unlimitedAir) {
        if (keyHeld) return false;
        keyHeld = true;
        if (now < attemptReadyAt || attemptEndsAt != 0) return false;
        if(airborne&&airDodgeUsed&&!unlimitedAir)return false;
        if(airborne&&!unlimitedAir)airDodgeUsed=true;
        attemptEndsAt = now + 6; return true;
    }
    public void observeGrounded(boolean grounded) {if(grounded)resetAirDodge();}
    public void resetAirDodge(){airDodgeUsed=false;}
    public boolean airDodgeUsed(){return airDodgeUsed;}
    public void releaseSprint() { keyHeld = false; }
    public boolean sprintHeld() { return keyHeld; }
    /** Character movement owns this press. Keep prior protection/counter, remove generic attempt/displacement. */
    public void claimMovementInput() { keyHeld = false; attemptEndsAt = dashEndsAt = 0; }
    public AttackResult incoming(Incoming kind) {
        java.util.Objects.requireNonNull(kind);
        if (kind == Incoming.ENVIRONMENT || kind == Incoming.VOID || kind == Incoming.PERIODIC)
            return AttackResult.NOT_PREVENTED;
        if (invulnerableUntil > now) return AttackResult.DIRECT_INVULNERABILITY;
        if (kind == Incoming.EVADABLE_DIRECT && attemptEndsAt > now) {
            return completeSuccess(true);
        }
        return AttackResult.NOT_PREVENTED;
    }
    /** Server-authorized skill window. Shares success protection/counter but adds no generic sprint displacement. */
    public AttackResult successfulSkillEvade() {
        if (invulnerableUntil > now) return AttackResult.DIRECT_INVULNERABILITY;
        return completeSuccess(false);
    }
    private AttackResult completeSuccess(boolean sprintDash) {
        resetAirDodge();
        attemptEndsAt = 0; attemptReadyAt = now; invulnerableUntil = now + 12;
        counterUntil = now + 20; dashEndsAt = sprintDash ? now + 4 : 0;
        return AttackResult.DODGE_SUCCEEDED;
    }
    public boolean consumeCounter(boolean effectiveBasicAttack) {
        if (!effectiveBasicAttack || counterUntil <= now) return false;
        counterUntil = 0; return true;
    }
    /** Each tick asks the collision adapter for at most 0.5 blocks; never writes positions itself. */
    public double requestedDashDistanceThisTick() { return dashEndsAt > now ? 0.5 : 0; }
    public Snapshot snapshot() {
        return new Snapshot(stamina, recoveryReadyAt, attemptEndsAt, attemptReadyAt, invulnerableUntil, counterUntil, dashEndsAt);
    }
    public void restoreStamina(double value) {
        Checks.nonnegative("stamina", value); if (value > MAX_STAMINA) throw new IllegalArgumentException("stamina cap"); stamina = value;
    }
    public void restoreRecoveryDelay(long remainingTicks) { Checks.tick(remainingTicks); recoveryReadyAt = Math.addExact(now, remainingTicks); }
    public void clearActionWindows() { attemptEndsAt = invulnerableUntil = counterUntil = dashEndsAt = 0; keyHeld = false; }
}
