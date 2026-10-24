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
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.nio.file.Path;

/**
 * Client initializer with Render-Frame (HudRenderCallback) + Dual Client-Tick hooks for 0.1ms response time.
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

        ClientPlayNetworking.registerGlobalReceiver(
                ServerPolicyS2CPayload.ID,
                (payload, context) -> context.client().execute(() -> actionController.onServerPolicyReceived(payload))
        );

        ClientPlayNetworking.registerGlobalReceiver(
                CrystalActionAckS2CPayload.ID,
                (payload, context) -> context.client().execute(() -> actionController.onServerAckReceived(payload))
        );

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                client.execute(() -> actionController.onDisconnectOrWorldChange(client))
        );

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                client.execute(() -> actionController.onDisconnectOrWorldChange(client))
        );

        // High-frequency render frame hook: runs every frame for 0.1 ms precision
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                boolean held = KeybindManager.isActivationHeld(client, configManager.getConfig());
                if (held) {
                    actionController.updateHighSpeed(client, true);
                }
            }
        });

        // Start of client tick hook
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            boolean held = KeybindManager.isActivationHeld(client, configManager.getConfig());
            if (held) {
                actionController.updateHighSpeed(client, true);
            }
        });

        // End of client tick hook: handles keybind sync and cleanup on release
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            syncKeybindWithConfigIfChanged(configManager.getConfig());
            boolean held = KeybindManager.isActivationHeld(client, configManager.getConfig());
            actionController.updateHighSpeed(client, held);
        });

        CrystalRushMod.LOGGER.info("CrystalRush high-speed client initialized (config: {}).", configFile);
    }

    private static void syncKeybindWithConfigIfChanged(CrystalRushConfig config) {
        var keyBinding = KeybindManager.getActivateKeyBinding();
        if (keyBinding == null) {
            return;
        }
        var boundKey = keyBinding.isUnbound()
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
