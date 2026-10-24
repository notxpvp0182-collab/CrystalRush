package dev.crystalrush.client;

import dev.crystalrush.CrystalRushMod;
import dev.crystalrush.client.controller.CrystalActionController;
import dev.crystalrush.client.input.KeybindManager;
import dev.crystalrush.config.CrystalRushConfig;
import dev.crystalrush.config.CrystalRushConfigManager;
import dev.crystalrush.network.CrystalActionAckS2CPayload;
import dev.crystalrush.network.ServerPolicyS2CPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * Client-side initializer for CrystalRush.
 * Wires configuration persistence, Hold-to-Activate keybind registration, S2C networking,
 * and the single-instance {@link CrystalActionController} tick loop.
 */
public class CrystalRushClient implements ClientModInitializer {

    private static CrystalRushConfigManager configManager;
    private static CrystalActionController actionController;

    @Override
    public void onInitializeClient() {
        Path configFile = FabricLoader.getInstance().getConfigDir().resolve("crystalrush.json");
        configManager = new CrystalRushConfigManager(configFile);
        CrystalRushConfig config = configManager.load();

        KeybindManager.register(config);
        actionController = new CrystalActionController(configManager);

        // Register S2C packet receivers for optional server companion
        ClientPlayNetworking.registerGlobalReceiver(
                ServerPolicyS2CPayload.ID,
                (payload, context) -> context.client().execute(() -> actionController.onServerPolicyReceived(payload))
        );

        ClientPlayNetworking.registerGlobalReceiver(
                CrystalActionAckS2CPayload.ID,
                (payload, context) -> context.client().execute(() -> actionController.onServerAckReceived(payload))
        );

        // Clean up state machine on disconnect or server switch
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                client.execute(() -> actionController.onDisconnectOrWorldChange(client))
        );

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                client.execute(() -> actionController.onDisconnectOrWorldChange(client))
        );

        // Main client tick hook
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Keep config in sync if user changed keybind inside vanilla Controls screen
            syncKeybindWithConfigIfChanged(configManager.getConfig());

            boolean held = KeybindManager.isActivationHeld(client, configManager.getConfig());
            actionController.tick(client, held);
        });

        CrystalRushMod.LOGGER.info("CrystalRush client initialized (config: {}).", configFile);
    }

    private static void syncKeybindWithConfigIfChanged(CrystalRushConfig config) {
        var keyBinding = KeybindManager.getActivateKeyBinding();
        if (keyBinding == null) {
            return;
        }
        var boundKey = KeybindManager.getActivateKeyBinding().isUnbound()
                ? null
                : net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(keyBinding);

        if (boundKey != null) {
            boolean isMouse = boundKey.getCategory() == net.minecraft.client.util.InputUtil.Type.MOUSE;
            int code = boundKey.getCode();
            if (config.getActivationKeyCode() != code || config.isActivationKeyIsMouse() != isMouse) {
                config.setActivationKeyCode(code);
                config.setActivationKeyIsMouse(isMouse);
                configManager.save();
            }
        }
    }

    public static CrystalRushConfigManager getConfigManager() {
        return configManager;
    }

    public static CrystalActionController getActionController() {
        return actionController;
    }
}
