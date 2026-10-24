package dev.crystalrush.client.controller;

/**
 * Pure Java helper that tracks hotbar slot selection changes made by CrystalRush
 * and determines whether the previous slot should be restored upon deactivation.
 *
 * <p>Kept independent of Minecraft classes so all edge cases (manual overrides,
 * invalid slot indices, already-equipped crystals) can be thoroughly unit-tested.</p>
 */
public class HotbarSlotTracker {

    public static final int HOTBAR_SIZE = 9;
    public static final int NO_SLOT = -1;

    private int savedOriginalSlot = NO_SLOT;
    private int modSelectedSlot = NO_SLOT;
    private boolean slotChangedByMod = false;
    private boolean manualOverrideDetected = false;

    /**
     * Records the player's current slot when CrystalRush begins an activation session
     * and switches to {@code targetCrystalSlot}.
     *
     * @param currentSlot       the player's currently selected hotbar slot (0..8)
     * @param targetCrystalSlot the hotbar slot containing End Crystals (0..8)
     * @return true if the slot needs to be changed to {@code targetCrystalSlot}
     */
    public boolean recordModSlotSwitch(int currentSlot, int targetCrystalSlot) {
        if (!isValidHotbarSlot(currentSlot) || !isValidHotbarSlot(targetCrystalSlot)) {
            return false;
        }
        if (currentSlot == targetCrystalSlot) {
            return false;
        }
        if (!this.slotChangedByMod && !this.manualOverrideDetected) {
            this.savedOriginalSlot = currentSlot;
        }
        this.modSelectedSlot = targetCrystalSlot;
        this.slotChangedByMod = true;
        return true;
    }

    /**
     * Observes the player's current hotbar slot on every tick while active.
     * If the player manually scrolled or pressed a hotbar number key to switch away
     * from {@code modSelectedSlot}, records a manual override so CrystalRush will not
     * overwrite their intentional selection on release.
     *
     * @param currentSlot the player's current hotbar slot (0..8)
     */
    public void observeTickSlot(int currentSlot) {
        if (this.slotChangedByMod && this.modSelectedSlot != NO_SLOT && currentSlot != this.modSelectedSlot) {
            this.manualOverrideDetected = true;
        }
    }

    /**
     * Computes which hotbar slot to restore when activation ends, and resets internal state.
     *
     * @param currentSlot        the player's current hotbar slot (0..8)
     * @param restoreSlotEnabled whether the "Restore Previous Hotbar Slot" config option is enabled
     * @return the slot index (0..8) to restore, or {@link #NO_SLOT} if no restoration should occur
     */
    public int consumeSlotToRestore(int currentSlot, boolean restoreSlotEnabled) {
        observeTickSlot(currentSlot);

        int slotToRestore = NO_SLOT;
        if (restoreSlotEnabled
                && this.slotChangedByMod
                && !this.manualOverrideDetected
                && isValidHotbarSlot(this.savedOriginalSlot)
                && currentSlot == this.modSelectedSlot
                && this.savedOriginalSlot != currentSlot) {
            slotToRestore = this.savedOriginalSlot;
        }

        reset();
        return slotToRestore;
    }

    /**
     * Clears all tracked slot state (e.g., on disconnect, death, or after restoration).
     */
    public void reset() {
        this.savedOriginalSlot = NO_SLOT;
        this.modSelectedSlot = NO_SLOT;
        this.slotChangedByMod = false;
        this.manualOverrideDetected = false;
    }

    public static boolean isValidHotbarSlot(int slot) {
        return slot >= 0 && slot < HOTBAR_SIZE;
    }

    public int getSavedOriginalSlot() {
        return savedOriginalSlot;
    }

    public int getModSelectedSlot() {
        return modSelectedSlot;
    }

    public boolean isSlotChangedByMod() {
        return slotChangedByMod;
    }

    public boolean isManualOverrideDetected() {
        return manualOverrideDetected;
    }
}
