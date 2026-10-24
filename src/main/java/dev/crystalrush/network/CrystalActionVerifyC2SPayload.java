package dev.crystalrush.network;

import dev.crystalrush.CrystalRushMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

/**
 * Optional C2S verification request sent when the server companion is installed.
 * The client never claims an action succeeded; instead it queries the server to validate
 * target placement/entity state and rate-limiting.
 */
public record CrystalActionVerifyC2SPayload(
        int sequenceId,
        int actionType,
        BlockPos targetPos,
        int targetEntityId
) implements CustomPayload {

    public static final int ACTION_PLACE_BASE = 1;
    public static final int ACTION_INTERACT_CRYSTAL = 2;

    public static final CustomPayload.Id<CrystalActionVerifyC2SPayload> ID =
            new CustomPayload.Id<>(CrystalRushMod.id("action_verify_c2s"));

    public static final PacketCodec<RegistryByteBuf, CrystalActionVerifyC2SPayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.VAR_INT, CrystalActionVerifyC2SPayload::sequenceId,
                    PacketCodecs.VAR_INT, CrystalActionVerifyC2SPayload::actionType,
                    BlockPos.PACKET_CODEC, CrystalActionVerifyC2SPayload::targetPos,
                    PacketCodecs.VAR_INT, CrystalActionVerifyC2SPayload::targetEntityId,
                    CrystalActionVerifyC2SPayload::new
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
