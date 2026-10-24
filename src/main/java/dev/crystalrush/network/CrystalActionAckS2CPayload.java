package dev.crystalrush.network;

import dev.crystalrush.CrystalRushMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Authoritative S2C acknowledgement sent by the optional server companion after verifying
 * world state, permissions, and cooldowns for a {@link CrystalActionVerifyC2SPayload}.
 */
public record CrystalActionAckS2CPayload(
        int sequenceId,
        boolean confirmed,
        String reason
) implements CustomPayload {

    public static final CustomPayload.Id<CrystalActionAckS2CPayload> ID =
            new CustomPayload.Id<>(CrystalRushMod.id("action_ack_s2c"));

    public static final PacketCodec<RegistryByteBuf, CrystalActionAckS2CPayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.VAR_INT, CrystalActionAckS2CPayload::sequenceId,
                    PacketCodecs.BOOLEAN, CrystalActionAckS2CPayload::confirmed,
                    PacketCodecs.string(64), CrystalActionAckS2CPayload::reason,
                    CrystalActionAckS2CPayload::new
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
