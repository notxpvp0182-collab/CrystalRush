package dev.crystalrush.network;

import dev.crystalrush.CrystalRushMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Sent from the optional server companion to the client on join to synchronize
 * server-side permissions and minimum interval rules.
 */
public record ServerPolicyS2CPayload(
        boolean allowed,
        int minActionIntervalTicks
) implements CustomPayload {

    public static final CustomPayload.Id<ServerPolicyS2CPayload> ID =
            new CustomPayload.Id<>(CrystalRushMod.id("server_policy"));

    public static final PacketCodec<RegistryByteBuf, ServerPolicyS2CPayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.BOOLEAN, ServerPolicyS2CPayload::allowed,
                    PacketCodecs.VAR_INT, ServerPolicyS2CPayload::minActionIntervalTicks,
                    ServerPolicyS2CPayload::new
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
