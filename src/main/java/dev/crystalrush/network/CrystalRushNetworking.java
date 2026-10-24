package dev.crystalrush.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Registers custom payload types for Minecraft 1.21.11 Fabric networking.
 */
public final class CrystalRushNetworking {

    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private CrystalRushNetworking() {
    }

    public static void registerCommonPayloads() {
        if (REGISTERED.compareAndSet(false, true)) {
            PayloadTypeRegistry.playS2C().register(ServerPolicyS2CPayload.ID, ServerPolicyS2CPayload.CODEC);
            PayloadTypeRegistry.playC2S().register(CrystalActionVerifyC2SPayload.ID, CrystalActionVerifyC2SPayload.CODEC);
            PayloadTypeRegistry.playS2C().register(CrystalActionAckS2CPayload.ID, CrystalActionAckS2CPayload.CODEC);
        }
    }
}
