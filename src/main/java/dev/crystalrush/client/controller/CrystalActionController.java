package dev.crystalrush.client.controller;

import dev.crystalrush.CrystalRushMod;
import dev.crystalrush.config.CrystalRushConfig;
import dev.crystalrush.config.CrystalRushConfigManager;
import dev.crystalrush.network.CrystalActionAckS2CPayload;
import dev.crystalrush.network.CrystalActionVerifyC2SPayload;
import dev.crystalrush.network.ServerPolicyS2CPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Vanilla-compliant, nanosecond-paced End Crystal action controller.
 *
 * <p>Guarantees:
 * <ul>
 *   <li>Never fabricates {@link BlockHitResult} or {@link EntityHitResult} instances.</li>
 *   <li>Places crystals only when the vanilla crosshair targets a valid Obsidian/Bedrock base
 *       whose 1x2x1 crystal space above is air and completely free of entities.</li>
 *   <li>Detonates crystals only when the vanilla crosshair directly targets a valid, spawned
 *       {@link EndCrystalEntity} at a legitimate non-occluded position outside solid blocks.</li>
 *   <li>Waits for world-state confirmation (crystal entity spawned after placement, or crystal
 *       entity removed after attack) before initiating dependent actions.</li>
 * </ul>
 */
public class CrystalActionController {

    private static final long NANOS_PER_MS = 1_000_000L;
    private static final long CONFIRMATION_TIMEOUT_NS = 250_000_000L; // 250 ms safety timeout
    private static final double VANILLA_ENTITY_REACH_SQ = 20.25D; // 4.5 blocks squared

    private final CrystalRushConfigManager configManager;
    private final HotbarSlotTracker slotTracker = new HotbarSlotTracker();
    private final AtomicInteger sequenceGenerator = new AtomicInteger(0);

    private CrystalActionState state = CrystalActionState.IDLE;
    private boolean wasKeyHeld = false;

    private long lastPlaceTimeNs = 0L;
    private long lastBreakTimeNs = 0L;
    private long lastStatusTimeNs = 0L;

    // Pending world-state confirmation tracking
    private enum PendingAction {
        NONE,
        WAITING_FOR_SPAWN,
        WAITING_FOR_REMOVAL
    }

    private PendingAction pendingAction = PendingAction.NONE;
    private BlockPos pendingBasePos = null;
    private int pendingEntityId = -1;
    private long pendingSinceNs = 0L;

    private boolean serverCompanionPresent = false;
    private boolean serverAllowsCrystalRush = true;

    public CrystalActionController(CrystalRushConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Called on render frames and client ticks. Uses nanosecond timers while strictly enforcing
     * vanilla crosshair targets and server world-state confirmation.
     */
    public void updateHighSpeed(MinecraftClient client, boolean keyHeld) {
        CrystalRushConfig config = configManager.getConfig();

        if (!isEnvironmentSafeForAction(client)) {
            if (state != CrystalActionState.IDLE || wasKeyHeld) {
                cancelAndCleanup(client, config, "Environment unsafe or GUI opened");
            }
            wasKeyHeld = false;
            return;
        }

        ClientPlayerEntity player = client.player;
        slotTracker.observeTickSlot(player.getInventory().getSelectedSlot());

        if (!keyHeld || !config.isEnabled()) {
            if (wasKeyHeld || state != CrystalActionState.IDLE) {
                clearPendingConfirmation();
                state = CrystalActionState.RESTORING_SLOT;
                handleRestoringSlot(client, config);
            }
            wasKeyHeld = false;
            return;
        }

        wasKeyHeld = true;

        if (serverCompanionPresent && !serverAllowsCrystalRush) {
            sendStatusMessage(client, config, Text.translatable("status.crystalrush.disabled_by_server").formatted(Formatting.RED));
            return;
        }

        // 1. Check if we are waiting for the world state to confirm a previous placement or detonation
        if (pendingAction != PendingAction.NONE) {
            if (!checkPendingWorldStateConfirmed(client.world, System.nanoTime())) {
                state = CrystalActionState.WAITING_FOR_GAME_STATE;
                return;
            }
            state = CrystalActionState.ACTION_COMPLETED;
        }

        // 2. Ensure End Crystal is selected in hotbar/hand
        state = CrystalActionState.CHECKING_INVENTORY;
        if (!ensureCrystalSelected(client, player, config)) {
            state = CrystalActionState.CANCELLED;
            return;
        }

        // 3. Evaluate the current vanilla crosshair target
        state = CrystalActionState.CHECKING_TARGET;
        executeVanillaValidatedCycle(client, player, client.world, client.interactionManager, config);
    }

    public void tick(MinecraftClient client, boolean keyHeld) {
        updateHighSpeed(client, keyHeld);
    }

    /**
     * Verifies whether the previous interaction has been reflected in the client world state.
     */
    private boolean checkPendingWorldStateConfirmed(ClientWorld world, long nowNs) {
        if (world == null) {
            clearPendingConfirmation();
            return false;
        }

        if (pendingAction == PendingAction.WAITING_FOR_SPAWN && pendingBasePos != null) {
            EndCrystalEntity spawned = findCrystalOnBase(world, pendingBasePos);
            if (spawned != null && isCrystalPositionValid(world, spawned)) {
                clearPendingConfirmation();
                return true;
            }
        } else if (pendingAction == PendingAction.WAITING_FOR_REMOVAL && pendingEntityId != -1) {
            Entity existing = world.getEntityById(pendingEntityId);
            if (existing == null || !existing.isAlive() || existing.isRemoved()) {
                clearPendingConfirmation();
                return true;
            }
        }

        // Release lock if server confirmation timed out (e.g., high latency or cancelled by server)
        if (nowNs - pendingSinceNs >= CONFIRMATION_TIMEOUT_NS) {
            clearPendingConfirmation();
            return true;
        }

        return false;
    }

    private boolean ensureCrystalSelected(MinecraftClient client, ClientPlayerEntity player, CrystalRushConfig config) {
        if (player.getMainHandStack().isOf(Items.END_CRYSTAL) || player.getOffHandStack().isOf(Items.END_CRYSTAL)) {
            return true;
        }

        PlayerInventory inventory = player.getInventory();
        int crystalSlot = findEndCrystalHotbarSlot(inventory);
        if (crystalSlot == HotbarSlotTracker.NO_SLOT) {
            sendStatusMessage(client, config, Text.translatable("status.crystalrush.no_crystals").formatted(Formatting.RED));
            return false;
        }

        if (!config.isAutoCrystalSlotDetection()) {
            return false;
        }

        state = CrystalActionState.SELECTING_CRYSTAL;
        int currentSlot = inventory.getSelectedSlot();
        if (slotTracker.recordModSlotSwitch(currentSlot, crystalSlot)) {
            selectHotbarSlotSynchronized(client, player, crystalSlot);
        }
        return true;
    }

    private void executeVanillaValidatedCycle(
            MinecraftClient client,
            ClientPlayerEntity player,
            ClientWorld world,
            ClientPlayerInteractionManager interactionManager,
            CrystalRushConfig config
    ) {
        HitResult hitResult = client.crosshairTarget;
        if (hitResult == null || hitResult.getType() == HitResult.Type.MISS) {
            return;
        }

        long nowNs = System.nanoTime();
        long placeDelayNs = (long) (config.getPlaceDelayMs() * NANOS_PER_MS);
        long breakDelayNs = (long) (config.getBreakDelayMs() * NANOS_PER_MS);

        // CASE 1: Vanilla crosshair is directly targeting an EndCrystalEntity -> Detonate if valid
        if (hitResult.getType() == HitResult.Type.ENTITY && hitResult instanceof EntityHitResult entityHit) {
            Entity target = entityHit.getEntity();
            if (target instanceof EndCrystalEntity crystal) {
                if (nowNs - lastBreakTimeNs < breakDelayNs) {
                    return;
                }
                if (!canDetonateCrystalLegitimately(player, world, crystal, entityHit.getPos())) {
                    return;
                }

                interactionManager.attackEntity(player, crystal);
                player.swingHand(Hand.MAIN_HAND);

                lastBreakTimeNs = nowNs;
                pendingAction = PendingAction.WAITING_FOR_REMOVAL;
                pendingEntityId = crystal.getId();
                pendingBasePos = crystal.getBlockPos().down();
                pendingSinceNs = nowNs;
                state = CrystalActionState.WAITING_FOR_GAME_STATE;

                sendOptionalVerifyPacket(
                        sequenceGenerator.incrementAndGet(),
                        CrystalActionVerifyC2SPayload.ACTION_INTERACT_CRYSTAL,
                        crystal.getBlockPos(),
                        crystal.getId()
                );
            }
            return;
        }

        // CASE 2: Vanilla crosshair is targeting an Obsidian or Bedrock block -> Place if valid
        // Never detonate a crystal from a BlockHitResult (prevents detonating inside/through Obsidian).
        if (hitResult.getType() == HitResult.Type.BLOCK && hitResult instanceof BlockHitResult blockHit) {
            if (nowNs - lastPlaceTimeNs < placeDelayNs) {
                return;
            }

            BlockPos basePos = blockHit.getBlockPos();
            if (!canPlaceCrystalAt(world, basePos)) {
                return;
            }

            if (player.getEyePos().squaredDistanceTo(blockHit.getPos()) > VANILLA_ENTITY_REACH_SQ) {
                return;
            }

            Hand hand = player.getMainHandStack().isOf(Items.END_CRYSTAL) ? Hand.MAIN_HAND
                    : (player.getOffHandStack().isOf(Items.END_CRYSTAL) ? Hand.OFF_HAND : null);
            if (hand == null) {
                return;
            }

            ActionResult result = interactionManager.interactBlock(player, hand, blockHit);
            if (result.isAccepted()) {
                player.swingHand(hand);
                lastPlaceTimeNs = nowNs;
                pendingAction = PendingAction.WAITING_FOR_SPAWN;
                pendingBasePos = basePos.toImmutable();
                pendingEntityId = -1;
                pendingSinceNs = nowNs;
                state = CrystalActionState.WAITING_FOR_GAME_STATE;

                sendOptionalVerifyPacket(
                        sequenceGenerator.incrementAndGet(),
                        CrystalActionVerifyC2SPayload.ACTION_PLACE_BASE,
                        pendingBasePos,
                        -1
                );
            }
        }
    }

    /**
     * Validates that an End Crystal entity is in a legitimate world position (not embedded inside
     * an Obsidian/solid block) and that the player's line of sight to the hit point is not
     * obstructed by a solid block.
     */
    public static boolean canDetonateCrystalLegitimately(
            ClientPlayerEntity player,
            ClientWorld world,
            EndCrystalEntity crystal,
            Vec3d hitPos
    ) {
        if (crystal == null || !crystal.isAlive() || crystal.isRemoved()) {
            return false;
        }
        if (!isCrystalPositionValid(world, crystal)) {
            return false;
        }
        Vec3d eyePos = player.getEyePos();
        Vec3d targetPoint = hitPos != null ? hitPos : crystal.getBoundingBox().getCenter();
        if (eyePos.squaredDistanceTo(targetPoint) > VANILLA_ENTITY_REACH_SQ) {
            return false;
        }
        // Verify the ray from the player's eyes to the crystal hit point does not pass through a solid block
        BlockHitResult blockCheck = world.raycast(new RaycastContext(
                eyePos,
                targetPoint,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));
        return blockCheck.getType() == HitResult.Type.MISS;
    }

    /**
     * Ensures the crystal entity's block position and bounding box are not inside an Obsidian or Bedrock block.
     */
    public static boolean isCrystalPositionValid(ClientWorld world, EndCrystalEntity crystal) {
        BlockPos crystalBlockPos = crystal.getBlockPos();
        BlockState stateAtCrystal = world.getBlockState(crystalBlockPos);
        // An End Crystal rests in the air/fire block ABOVE Obsidian/Bedrock, never inside a solid block
        if (stateAtCrystal.isOf(Blocks.OBSIDIAN)
                || stateAtCrystal.isOf(Blocks.BEDROCK)
                || stateAtCrystal.isSolidBlock(world, crystalBlockPos)) {
            return false;
        }
        // The crystal's center Y must be above the top face of the base block below it
        double minValidY = crystalBlockPos.getY();
        return crystal.getY() >= minValidY - 0.05D;
    }

    private void handleRestoringSlot(MinecraftClient client, CrystalRushConfig config) {
        ClientPlayerEntity player = client.player;
        if (player != null) {
            int currentSlot = player.getInventory().getSelectedSlot();
            int restoreSlot = slotTracker.consumeSlotToRestore(currentSlot, config.isRestorePreviousHotbarSlot());
            if (restoreSlot != HotbarSlotTracker.NO_SLOT) {
                selectHotbarSlotSynchronized(client, player, restoreSlot);
            }
        } else {
            slotTracker.reset();
        }
        state = CrystalActionState.IDLE;
    }

    public void cancelAndCleanup(MinecraftClient client, CrystalRushConfig config, String reason) {
        clearPendingConfirmation();
        if (client != null && client.player != null && client.getNetworkHandler() != null && playerAlive(client.player)) {
            int currentSlot = client.player.getInventory().getSelectedSlot();
            int restoreSlot = slotTracker.consumeSlotToRestore(currentSlot, config.isRestorePreviousHotbarSlot());
            if (restoreSlot != HotbarSlotTracker.NO_SLOT) {
                selectHotbarSlotSynchronized(client, client.player, restoreSlot);
            }
        } else {
            slotTracker.reset();
        }
        state = CrystalActionState.IDLE;
    }

    public void onDisconnectOrWorldChange(MinecraftClient client) {
        clearPendingConfirmation();
        slotTracker.reset();
        serverCompanionPresent = false;
        serverAllowsCrystalRush = true;
        wasKeyHeld = false;
        state = CrystalActionState.IDLE;
    }

    public void onServerPolicyReceived(ServerPolicyS2CPayload payload) {
        this.serverCompanionPresent = true;
        this.serverAllowsCrystalRush = payload.allowed();
    }

    public void onServerAckReceived(CrystalActionAckS2CPayload payload) {
        if (!payload.confirmed()) {
            clearPendingConfirmation();
        }
        if (configManager.getConfig().isDebugLogging()) {
            CrystalRushMod.LOGGER.info("[CrystalRush] Server Ack seq={} confirmed={} reason={}",
                    payload.sequenceId(), payload.confirmed(), payload.reason());
        }
    }

    public static int findEndCrystalHotbarSlot(PlayerInventory inventory) {
        int current = inventory.getSelectedSlot();
        if (HotbarSlotTracker.isValidHotbarSlot(current)) {
            ItemStack currentStack = inventory.getStack(current);
            if (!currentStack.isEmpty() && currentStack.isOf(Items.END_CRYSTAL)) {
                return current;
            }
        }
        for (int slot = 0; slot < HotbarSlotTracker.HOTBAR_SIZE; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!stack.isEmpty() && stack.isOf(Items.END_CRYSTAL)) {
                return slot;
            }
        }
        return HotbarSlotTracker.NO_SLOT;
    }

    public static boolean isCrystalBaseBlock(ClientWorld world, BlockPos basePos) {
        BlockState state = world.getBlockState(basePos);
        return state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK);
    }

    /**
     * Exact vanilla EndCrystalItem placement check:
     * 1. Base block must be Obsidian or Bedrock.
     * 2. Block above (basePos.up()) must be air.
     * 3. Exact 1.0 x 2.0 x 1.0 bounding box above basePos must contain zero entities (including existing crystals).
     */
    public static boolean canPlaceCrystalAt(ClientWorld world, BlockPos basePos) {
        if (!isCrystalBaseBlock(world, basePos)) {
            return false;
        }
        BlockPos crystalSpace = basePos.up();
        if (!world.isAir(crystalSpace)) {
            return false;
        }
        double x = crystalSpace.getX();
        double y = crystalSpace.getY();
        double z = crystalSpace.getZ();
        List<Entity> entities = world.getOtherEntities(null, new Box(x, y, z, x + 1.0D, y + 2.0D, z + 1.0D));
        return entities.isEmpty();
    }

    /**
     * Finds a spawned End Crystal occupying the exact 1x2x1 space above {@code basePos}.
     */
    private static EndCrystalEntity findCrystalOnBase(ClientWorld world, BlockPos basePos) {
        BlockPos crystalSpace = basePos.up();
        double x = crystalSpace.getX();
        double y = crystalSpace.getY();
        double z = crystalSpace.getZ();
        List<Entity> entities = world.getOtherEntities(null, new Box(x, y, z, x + 1.0D, y + 2.0D, z + 1.0D));
        for (Entity entity : entities) {
            if (entity instanceof EndCrystalEntity crystal && crystal.isAlive() && !crystal.isRemoved()) {
                return crystal;
            }
        }
        return null;
    }

    private void clearPendingConfirmation() {
        this.pendingAction = PendingAction.NONE;
        this.pendingBasePos = null;
        this.pendingEntityId = -1;
        this.pendingSinceNs = 0L;
    }

    private void sendOptionalVerifyPacket(int seqId, int actionType, BlockPos pos, int entityId) {
        if (serverCompanionPresent && ClientPlayNetworking.canSend(CrystalActionVerifyC2SPayload.ID)) {
            ClientPlayNetworking.send(new CrystalActionVerifyC2SPayload(seqId, actionType, pos, entityId));
        }
    }

    private static void selectHotbarSlotSynchronized(MinecraftClient client, ClientPlayerEntity player, int slot) {
        if (!HotbarSlotTracker.isValidHotbarSlot(slot)) {
            return;
        }
        player.getInventory().setSelectedSlot(slot);
        if (client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }
    }

    private static boolean isEnvironmentSafeForAction(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null || client.interactionManager == null) {
            return false;
        }
        if (client.getNetworkHandler() == null || client.currentScreen != null || !client.isWindowFocused()) {
            return false;
        }
        return playerAlive(client.player) && !client.player.isSpectator();
    }

    private static boolean playerAlive(ClientPlayerEntity player) {
        return player.isAlive() && !player.isDead();
    }

    private void sendStatusMessage(MinecraftClient client, CrystalRushConfig config, Text message) {
        long now = System.nanoTime();
        if (!config.isDisplayStatusMessages() || (now - lastStatusTimeNs) < 1_000_000_000L || client.player == null) {
            return;
        }
        client.player.sendMessage(message, true);
        lastStatusTimeNs = now;
    }

    public CrystalActionState getState() {
        return state;
    }
}
