package dev.crystalrush.server;

import dev.crystalrush.config.ServerPolicyConfig;
import dev.crystalrush.network.CrystalActionAckS2CPayload;
import dev.crystalrush.network.CrystalActionVerifyC2SPayload;
import dev.crystalrush.network.ServerPolicyS2CPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authoritative server-side handler for optional CrystalRush verification packets.
 * Enforces rate limits, reach checks, inventory checks, and valid End Crystal placement rules.
 */
public final class CrystalRushServerHandler {

    private static final Map<UUID, Long> LAST_ACTION_TICK_BY_PLAYER = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LAST_SEQUENCE_BY_PLAYER = new ConcurrentHashMap<>();

    private CrystalRushServerHandler() {
    }

    public static void register(ServerPolicyConfig policy) {
        // Send server policy when a player joins
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            if (ServerPlayNetworking.canSend(player, ServerPolicyS2CPayload.ID)) {
                ServerPlayNetworking.send(player, new ServerPolicyS2CPayload(
                        policy.isAllowCrystalRush(),
                        policy.getMinimumServerActionIntervalTicks()
                ));
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.getPlayer().getUuid();
            LAST_ACTION_TICK_BY_PLAYER.remove(uuid);
            LAST_SEQUENCE_BY_PLAYER.remove(uuid);
        });

        // Handle optional C2S verification requests
        ServerPlayNetworking.registerGlobalReceiver(
                CrystalActionVerifyC2SPayload.ID,
                (payload, context) -> {
                    ServerPlayerEntity player = context.player();
                    if (player == null) {
                        return;
                    }
                    handleVerifyPayload(player, payload, policy);
                }
        );
    }

    private static void handleVerifyPayload(
            ServerPlayerEntity player,
            CrystalActionVerifyC2SPayload payload,
            ServerPolicyConfig policy
    ) {
        UUID uuid = player.getUuid();

        // 1. Check server policy
        if (!policy.isAllowCrystalRush()) {
            sendAck(player, payload.sequenceId(), false, "disabled_by_server");
            return;
        }

        // 2. Reject duplicate or out-of-order sequence IDs
        Integer lastSeq = LAST_SEQUENCE_BY_PLAYER.get(uuid);
        if (lastSeq != null && payload.sequenceId() <= lastSeq) {
            sendAck(player, payload.sequenceId(), false, "duplicate_sequence");
            return;
        }
        LAST_SEQUENCE_BY_PLAYER.put(uuid, payload.sequenceId());

        // 3. Verify player state
        if (!player.isAlive() || player.isSpectator()) {
            sendAck(player, payload.sequenceId(), false, "invalid_player_state");
            return;
        }

        // 4. Enforce server-authoritative tick interval
        ServerWorld world = player.getServerWorld();
        long currentTick = world.getTime();
        Long lastTick = LAST_ACTION_TICK_BY_PLAYER.get(uuid);
        if (lastTick != null && (currentTick - lastTick) < policy.getMinimumServerActionIntervalTicks()) {
            sendAck(player, payload.sequenceId(), false, "rate_limited");
            return;
        }

        // 5. Validate action type & world state
        if (payload.actionType() == CrystalActionVerifyC2SPayload.ACTION_PLACE_BASE) {
            boolean valid = validateCrystalPlacementOnServer(player, world, payload.targetPos(), policy);
            if (valid) {
                LAST_ACTION_TICK_BY_PLAYER.put(uuid, currentTick);
            }
            sendAck(player, payload.sequenceId(), valid, valid ? "ok" : "invalid_placement");
        } else if (payload.actionType() == CrystalActionVerifyC2SPayload.ACTION_INTERACT_CRYSTAL) {
            boolean valid = validateCrystalEntityOnServer(player, world, payload.targetEntityId(), policy);
            if (valid) {
                LAST_ACTION_TICK_BY_PLAYER.put(uuid, currentTick);
            }
            sendAck(player, payload.sequenceId(), valid, valid ? "ok" : "invalid_entity");
        } else {
            sendAck(player, payload.sequenceId(), false, "malformed_action_type");
        }
    }

    /**
     * Checks normal Minecraft End Crystal placement requirements on the authoritative server world.
     */
    private static boolean validateCrystalPlacementOnServer(
            ServerPlayerEntity player,
            ServerWorld world,
            BlockPos basePos,
            ServerPolicyConfig policy
    ) {
        if (basePos == null || !world.isChunkLoaded(basePos)) {
            return false;
        }
        double maxDistSq = policy.getMaxPlacementReachBlocks() * policy.getMaxPlacementReachBlocks();
        if (player.getEyePos().squaredDistanceTo(basePos.toCenterPos()) > maxDistSq) {
            return false;
        }
        boolean holdingCrystal = player.getMainHandStack().isOf(Items.END_CRYSTAL)
                || player.getOffHandStack().isOf(Items.END_CRYSTAL);
        if (!holdingCrystal) {
            return false;
        }
        BlockState state = world.getBlockState(basePos);
        if (!state.isOf(Blocks.OBSIDIAN) && !state.isOf(Blocks.BEDROCK)) {
            return false;
        }
        BlockPos crystalSpace = basePos.up();
        if (!world.isAir(crystalSpace)) {
            return false;
        }
        double x = crystalSpace.getX();
        double y = crystalSpace.getY();
        double z = crystalSpace.getZ();
        List<Entity> blockingEntities = world.getOtherEntities(null, new Box(x, y, z, x + 1.0D, y + 2.0D, z + 1.0D));
        // If an EndCrystalEntity already spawned here from the vanilla interaction packet, confirm!
        for (Entity entity : blockingEntities) {
            if (entity instanceof EndCrystalEntity && entity.isAlive()) {
                return true;
            }
        }
        return blockingEntities.isEmpty();
    }

    private static boolean validateCrystalEntityOnServer(
            ServerPlayerEntity player,
            ServerWorld world,
            int entityId,
            ServerPolicyConfig policy
    ) {
        Entity entity = world.getEntityById(entityId);
        if (entity == null || !entity.isAlive()) {
            // Entity was already destroyed by the vanilla attack packet -> confirmed!
            return true;
        }
        if (!(entity instanceof EndCrystalEntity)) {
            return false;
        }
        double maxDistSq = policy.getMaxPlacementReachBlocks() * policy.getMaxPlacementReachBlocks();
        return player.getEyePos().squaredDistanceTo(entity.getPos()) <= maxDistSq;
    }

    private static void sendAck(ServerPlayerEntity player, int sequenceId, boolean confirmed, String reason) {
        if (ServerPlayNetworking.canSend(player, CrystalActionAckS2CPayload.ID)) {
            ServerPlayNetworking.send(player, new CrystalActionAckS2CPayload(sequenceId, confirmed, reason));
        }
    }
}
