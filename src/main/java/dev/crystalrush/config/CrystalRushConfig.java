package dev.crystalrush.config;

import org.lwjgl.glfw.GLFW;

/**
 * Configuration model supporting sub-millisecond (0.1 ms) to multi-second (2.0 s) delays.
 */
public class CrystalRushConfig {

    public static final double MIN_DELAY_MS = 0.1D;
    public static final double MAX_DELAY_MS = 2000.0D; // 2.0 seconds
    public static final double DEFAULT_PLACE_DELAY_MS = 0.1D;
    public static final double DEFAULT_BREAK_DELAY_MS = 0.1D;

    public static final int MIN_ACTION_DELAY_TICKS = 0;
    public static final int MAX_ACTION_DELAY_TICKS = 20;
    public static final int DEFAULT_ACTION_DELAY_TICKS = 0;

    public static final int MIN_ACTION_INTERVAL_TICKS = 1;
    public static final int MAX_ACTION_INTERVAL_TICKS = 40;
    public static final int DEFAULT_ACTION_INTERVAL_TICKS = 1;

    public static final int DEFAULT_ACTIVATION_KEY_CODE = GLFW.GLFW_KEY_X;

    private boolean enabled = true;
    private int activationKeyCode = DEFAULT_ACTIVATION_KEY_CODE;
    private boolean activationKeyIsMouse = false;
    private boolean autoCrystalSlotDetection = true;
    private boolean restorePreviousHotbarSlot = true;

    /** Place delay in milliseconds (0.1 ms to 2000.0 ms). */
    private double placeDelayMs = DEFAULT_PLACE_DELAY_MS;

    /** Break/Blast delay in milliseconds (0.1 ms to 2000.0 ms). */
    private double breakDelayMs = DEFAULT_BREAK_DELAY_MS;

    // Legacy tick fields kept for unit test compatibility
    private int crystalActionDelayTicks = DEFAULT_ACTION_DELAY_TICKS;
    private int actionIntervalTicks = DEFAULT_ACTION_INTERVAL_TICKS;

    private boolean displayStatusMessages = true;
    private boolean debugLogging = false;

    public CrystalRushConfig() {
    }

    public void validate() {
        if (Double.isNaN(this.placeDelayMs) || this.placeDelayMs < MIN_DELAY_MS) {
            this.placeDelayMs = MIN_DELAY_MS;
        } else if (this.placeDelayMs > MAX_DELAY_MS) {
            this.placeDelayMs = MAX_DELAY_MS;
        }

        if (Double.isNaN(this.breakDelayMs) || this.breakDelayMs < MIN_DELAY_MS) {
            this.breakDelayMs = MIN_DELAY_MS;
        } else if (this.breakDelayMs > MAX_DELAY_MS) {
            this.breakDelayMs = MAX_DELAY_MS;
        }

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

        if (this.activationKeyCode < GLFW.GLFW_KEY_UNKNOWN || this.activationKeyCode > GLFW.GLFW_KEY_LAST) {
            this.activationKeyCode = DEFAULT_ACTIVATION_KEY_CODE;
            this.activationKeyIsMouse = false;
        }
    }

    public void resetToDefaults() {
        this.enabled = true;
        this.activationKeyCode = DEFAULT_ACTIVATION_KEY_CODE;
        this.activationKeyIsMouse = false;
        this.autoCrystalSlotDetection = true;
        this.restorePreviousHotbarSlot = true;
        this.placeDelayMs = DEFAULT_PLACE_DELAY_MS;
        this.breakDelayMs = DEFAULT_BREAK_DELAY_MS;
        this.crystalActionDelayTicks = DEFAULT_ACTION_DELAY_TICKS;
        this.actionIntervalTicks = DEFAULT_ACTION_INTERVAL_TICKS;
        this.displayStatusMessages = true;
        this.debugLogging = false;
    }

    public static String formatDelayLabel(double delayMs) {
        if (delayMs >= 1000.0D) {
            return String.format(java.util.Locale.ROOT, "%.2f s (%.0f ms)", delayMs / 1000.0D, delayMs);
        } else if (delayMs < 10.0D) {
            return String.format(java.util.Locale.ROOT, "%.1f ms", delayMs);
        } else {
            return String.format(java.util.Locale.ROOT, "%.0f ms (%.2f s)", delayMs, delayMs / 1000.0D);
        }
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

    public double getPlaceDelayMs() {
        return placeDelayMs;
    }

    public void setPlaceDelayMs(double placeDelayMs) {
        this.placeDelayMs = placeDelayMs;
        validate();
    }

    public double getBreakDelayMs() {
        return breakDelayMs;
    }

    public void setBreakDelayMs(double breakDelayMs) {
        this.breakDelayMs = breakDelayMs;
        validate();
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
