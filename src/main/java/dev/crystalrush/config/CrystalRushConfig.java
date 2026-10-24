package dev.crystalrush.config;

import org.lwjgl.glfw.GLFW;

/**
 * Pure Java configuration data model for CrystalRush.
 * Kept independent of Minecraft client classes so it can be unit-tested in isolation.
 */
public class CrystalRushConfig {

    public static final int MIN_ACTION_DELAY_TICKS = 0;
    public static final int MAX_ACTION_DELAY_TICKS = 20;
    public static final int DEFAULT_ACTION_DELAY_TICKS = 1;

    public static final int MIN_ACTION_INTERVAL_TICKS = 1;
    public static final int MAX_ACTION_INTERVAL_TICKS = 40;
    public static final int DEFAULT_ACTION_INTERVAL_TICKS = 2;

    public static final int MIN_CONFIRMATION_TIMEOUT_TICKS = 2;
    public static final int MAX_CONFIRMATION_TIMEOUT_TICKS = 40;
    public static final int DEFAULT_CONFIRMATION_TIMEOUT_TICKS = 10;

    public static final int DEFAULT_ACTIVATION_KEY_CODE = GLFW.GLFW_KEY_R;

    /** Master enable/disable toggle for the CrystalRush state machine. */
    private boolean enabled = true;

    /** GLFW key code (or mouse button code if keyTypeIsMouse == true) for Hold-to-Activate. */
    private int activationKeyCode = DEFAULT_ACTIVATION_KEY_CODE;

    /** True if activationKeyCode represents a GLFW mouse button rather than a keyboard key. */
    private boolean activationKeyIsMouse = false;

    /** Automatically switch to a hotbar slot containing End Crystals when activated. */
    private boolean autoCrystalSlotDetection = true;

    /** Restore the player's previous hotbar slot upon key release if not manually overridden. */
    private boolean restorePreviousHotbarSlot = true;

    /**
     * Initial preparation delay (in client ticks) after selecting a crystal or acquiring a target
     * before dispatching the interaction via the client interaction manager.
     */
    private int crystalActionDelayTicks = DEFAULT_ACTION_DELAY_TICKS;

    /**
     * Minimum interval (in client ticks) between consecutive crystal interactions.
     * Respects server-imposed minimums if connected to a server with CrystalRush installed.
     */
    private int actionIntervalTicks = DEFAULT_ACTION_INTERVAL_TICKS;

    /** Maximum ticks to wait for server world-state confirmation before retrying/re-evaluating. */
    private int stateConfirmationTimeoutTicks = DEFAULT_CONFIRMATION_TIMEOUT_TICKS;

    /** Display subtle action-bar status feedback when crystals are missing or actions are blocked. */
    private boolean displayStatusMessages = true;

    /** Output detailed state-machine transitions to the game log. */
    private boolean debugLogging = false;

    public CrystalRushConfig() {
    }

    /**
     * Validates and clamps all numeric and structural configuration fields to safe bounds.
     */
    public void validate() {
        if (this.crystalActionDelayTicks < MIN_ACTION_DELAY_TICKS) {
            this.crystalActionDelayTicks = MIN_ACTION_DELAY_TICKS;
        } else if (this.crystalActionDelayTicks > MAX_ACTION_DELAY_TICKS) {
            this.crystalActionDelayTicks = MAX_ACTION_DELAY_TICKS;
        }

        if (this.actionIntervalTicks < MIN_ACTION_INTERVAL_TICKS) {
            this.actionIntervalTicks = MIN_ACTION_INTERVAL_TICKS;
        } else if (this.actionIntervalTicks > MAX_ACTION_INTERVAL_TICKS) {
            this.actionIntervalTicks = MAX_ACTION_INTERVAL_TICKS;
        }

        if (this.stateConfirmationTimeoutTicks < MIN_CONFIRMATION_TIMEOUT_TICKS) {
            this.stateConfirmationTimeoutTicks = MIN_CONFIRMATION_TIMEOUT_TICKS;
        } else if (this.stateConfirmationTimeoutTicks > MAX_CONFIRMATION_TIMEOUT_TICKS) {
            this.stateConfirmationTimeoutTicks = MAX_CONFIRMATION_TIMEOUT_TICKS;
        }

        if (this.activationKeyCode < GLFW.GLFW_KEY_UNKNOWN || this.activationKeyCode > GLFW.GLFW_KEY_LAST) {
            this.activationKeyCode = DEFAULT_ACTIVATION_KEY_CODE;
            this.activationKeyIsMouse = false;
        }
    }

    /**
     * Resets all fields to their factory defaults.
     */
    public void resetToDefaults() {
        this.enabled = true;
        this.activationKeyCode = DEFAULT_ACTIVATION_KEY_CODE;
        this.activationKeyIsMouse = false;
        this.autoCrystalSlotDetection = true;
        this.restorePreviousHotbarSlot = true;
        this.crystalActionDelayTicks = DEFAULT_ACTION_DELAY_TICKS;
        this.actionIntervalTicks = DEFAULT_ACTION_INTERVAL_TICKS;
        this.stateConfirmationTimeoutTicks = DEFAULT_CONFIRMATION_TIMEOUT_TICKS;
        this.displayStatusMessages = true;
        this.debugLogging = false;
    }

    public CrystalRushConfig copy() {
        CrystalRushConfig copy = new CrystalRushConfig();
        copy.enabled = this.enabled;
        copy.activationKeyCode = this.activationKeyCode;
        copy.activationKeyIsMouse = this.activationKeyIsMouse;
        copy.autoCrystalSlotDetection = this.autoCrystalSlotDetection;
        copy.restorePreviousHotbarSlot = this.restorePreviousHotbarSlot;
        copy.crystalActionDelayTicks = this.crystalActionDelayTicks;
        copy.actionIntervalTicks = this.actionIntervalTicks;
        copy.stateConfirmationTimeoutTicks = this.stateConfirmationTimeoutTicks;
        copy.displayStatusMessages = this.displayStatusMessages;
        copy.debugLogging = this.debugLogging;
        return copy;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getActivationKeyCode() {
        return activationKeyCode;
    }

    public void setActivationKeyCode(int activationKeyCode) {
        this.activationKeyCode = activationKeyCode;
    }

    public boolean isActivationKeyIsMouse() {
        return activationKeyIsMouse;
    }

    public void setActivationKeyIsMouse(boolean activationKeyIsMouse) {
        this.activationKeyIsMouse = activationKeyIsMouse;
    }

    public boolean isAutoCrystalSlotDetection() {
        return autoCrystalSlotDetection;
    }

    public void setAutoCrystalSlotDetection(boolean autoCrystalSlotDetection) {
        this.autoCrystalSlotDetection = autoCrystalSlotDetection;
    }

    public boolean isRestorePreviousHotbarSlot() {
        return restorePreviousHotbarSlot;
    }

    public void setRestorePreviousHotbarSlot(boolean restorePreviousHotbarSlot) {
        this.restorePreviousHotbarSlot = restorePreviousHotbarSlot;
    }

    public int getCrystalActionDelayTicks() {
        return crystalActionDelayTicks;
    }

    public void setCrystalActionDelayTicks(int crystalActionDelayTicks) {
        this.crystalActionDelayTicks = crystalActionDelayTicks;
        validate();
    }

    public int getActionIntervalTicks() {
        return actionIntervalTicks;
    }

    public void setActionIntervalTicks(int actionIntervalTicks) {
        this.actionIntervalTicks = actionIntervalTicks;
        validate();
    }

    public int getStateConfirmationTimeoutTicks() {
        return stateConfirmationTimeoutTicks;
    }

    public void setStateConfirmationTimeoutTicks(int stateConfirmationTimeoutTicks) {
        this.stateConfirmationTimeoutTicks = stateConfirmationTimeoutTicks;
        validate();
    }

    public boolean isDisplayStatusMessages() {
        return displayStatusMessages;
    }

    public void setDisplayStatusMessages(boolean displayStatusMessages) {
        this.displayStatusMessages = displayStatusMessages;
    }

    public boolean isDebugLogging() {
        return debugLogging;
    }

    public void setDebugLogging(boolean debugLogging) {
        this.debugLogging = debugLogging;
    }
}
