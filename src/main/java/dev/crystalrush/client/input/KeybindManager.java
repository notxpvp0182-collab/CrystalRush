package dev.crystalrush.client.input;

import dev.crystalrush.CrystalRushMod;
import dev.crystalrush.config.CrystalRushConfig;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Registers the CrystalRush activation keybind in Minecraft 1.21.11 and provides
 * reliable Hold-to-Activate detection synchronized with {@link CrystalRushConfig}.
 */
public final class KeybindManager {

    /**
     * Minecraft 1.21.11 uses {@link KeyBinding.Category} records created via {@link net.minecraft.util.Identifier}.
     */
    public static final KeyBinding.Category CRYSTALRUSH_CATEGORY =
            KeyBinding.Category.create(CrystalRushMod.id("general"));

    private static KeyBinding activateKeyBinding;

    private KeybindManager() {
    }

    public static void register(CrystalRushConfig config) {
        InputUtil.Type initialType = config.isActivationKeyIsMouse()
                ? InputUtil.Type.MOUSE
                : InputUtil.Type.KEYSYM;

        activateKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.crystalrush.activate",
                initialType,
                config.getActivationKeyCode(),
                CRYSTALRUSH_CATEGORY
        ));
    }

    /**
     * Synchronizes the bound key between Minecraft's Controls menu and {@link CrystalRushConfig}.
     */
    public static void applyConfigToKeyBinding(CrystalRushConfig config) {
        if (activateKeyBinding == null) {
            return;
        }
        if (config. getActivationKeyCode() == GLFW.GLFW_KEY_UNKNOWN) {
            activateKeyBinding.setBoundKey(InputUtil.UNKNOWN_KEY);
        } else {
            InputUtil.Type type = config.isActivationKeyIsMouse()
                    ? InputUtil.Type.MOUSE
                    : InputUtil.Type.KEYSYM;
            activateKeyBinding.setBoundKey(type.createFromCode(config.getActivationKeyCode()));
        }
        KeyBinding.updateKeysByCode();
    }

    /**
     * Evaluates whether the activation key is actively held down during gameplay.
     * Drains discrete press counters so hold-mode never queues up phantom presses,
     * and returns false immediately if any GUI screen is open or the window is unfocused.
     */
    public static boolean isActivationHeld(MinecraftClient client, CrystalRushConfig config) {
        if (activateKeyBinding == null || client == null) {
            return false;
        }

        // Drain discrete press queue to prevent repeated single-press accumulation
        while (activateKeyBinding.wasPressed()) {
            // Intentionally drained: CrystalRush uses continuous held state
        }

        if (client.currentScreen != null || !client.isWindowFocused()) {
            return false;
        }

        if (activateKeyBinding.isUnbound()) {
            return false;
        }

        return activateKeyBinding.isPressed();
    }

    public static KeyBinding getActivateKeyBinding() {
        return activateKeyBinding;
    }
}
