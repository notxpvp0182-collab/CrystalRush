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

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Single-instance, tick-driven client state machine for CrystalRush.
 *
 * <p>Guarantees:
 * <ul>
 *   <li>Runs strictly on the Minecraft client tick thread—no background threads or blocking loops.</li>
 *   <li>Performs at most one interaction per state cycle and waits for actual world state confirmation
 *       (crystal entity spawned or crystal entity removed) before transitioning to {@code ACTION_COMPLETED}.</li>
 *   <li>Tracks hotbar slot selection via {@link HotbarSlotTracker} and safely restores the player's
 *       original slot on release unless the player manually switched slots.</li>
 * </ul>
 */
public class CrystalActionController {

    private static final int STATUS_MESSAGE_COOLDOWN_TICKS = 30;

    private final CrystalRushConfigManager configManager;
    private final HotbarSlotTracker slotTracker = new HotbarSlotTracker();
    private final AtomicInteger sequenceGenerator = new AtomicInteger(0);

    private CrystalActionState state = CrystalActionState.IDLE;
    private boolean wasKeyHeldLastTick = false;

    // Timing counters (in client ticks)
    private int delayTicksRemaining = 0;
    private int intervalTicksRemaining = 0;
    private int waitTimeoutTicksRemaining = 0;
    private int statusCooldownTicks = 0;

    // Pending action context for WAITING_FOR_GAME_STATE confirmation
    private enum PendingKind {
        NONE,
        PLACEMENT,
        BREAK_ENTITY
    }

    private PendingKind pendingKind = PendingKind.NONE;
    private BlockPos pendingBasePos = null;
    private int pendingEntityId = -1;
    private int pendingSequenceId = -1;
    private Boolean serverAckReceived = null;

    // Server policy state (populated if server has CrystalRush installed)
    private boolean serverCompanionPresent = false;
    private boolean serverAllowsCrystalRush = true;
    private int serverMinIntervalTicks = 1;

    public CrystalActionController(CrystalRushConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Called once per client tick from {@code ClientTickEvents.END_CLIENT_TICK}.
     *
     * @param client  the MinecraftClient instance
     * @param keyHeld true if the activation keybind is currently held down
     */
    public void tick(MinecraftClient client, boolean keyHeld) {
        if (statusCooldownTicks > 0) {
            statusCooldownTicks--;
        }
        if (intervalTicksRemaining > 0) {
            intervalTicksRemaining--;
        }

        CrystalRushConfig config = configManager.getConfig();

        // Safety check: if player/world/interactionManager is missing, or player died, or a GUI is open
        if (!isEnvironmentSafeForAction(client, config)) {
            if (state != CrystalActionState.IDLE) {
                cancelAndCleanup(client, config, "Environment unsafe or GUI opened");
            }
            wasKeyHeldLastTick = false;
            return;
        }

        ClientPlayerEntity player = client.player;
        slotTracker.observeTickSlot(player.getInventory().getSelectedSlot());

        // Handle key release transition immediately
        if (!keyHeld) {
            if (wasKeyHeldLastTick || (state != CrystalActionState.IDLE && state != CrystalActionState.RESTORING_SLOT)) {
                transitionTo(CrystalActionState.CANCELLED, config, "Activation key released");
            }
            wasKeyHeldLastTick = false;
        } else {
            wasKeyHeldLastTick = true;
        }

        // Check server policy if companion is present
        if (keyHeld && serverCompanionPresent && !serverAllowsCrystalRush) {
            sendStatusMessage(client, config, Text.translatable("status.crystalrush.disabled_by_server").formatted(Formatting.RED));
            transitionTo(CrystalActionState.CANCELLED, config, "Disabled by server policy");
        }

        // Execute current state step (bounded single pass per tick)
        switch (state) {
            case IDLE -> {
                if (keyHeld && config.isEnabled()) {
                    transitionTo(CrystalActionState.CHECKING_INVENTORY, config, "Key held; starting cycle");
                }
            }

            case CHECKING_INVENTORY -> handleCheckingInventory(client, config);

            case SELECTING_CRYSTAL -> handleSelectingCrystal(client, config);

            case CHECKING_TARGET -> handleCheckingTarget(client, config);

            case WAITING_FOR_GAME_STATE -> handleWaitingForGameState(client, config);

            case ACTION_COMPLETED -> handleActionCompleted(config, keyHeld);

            case CANCELLED -> {
                clearPendingContext();
                transitionTo(CrystalActionState.RESTORING_SLOT, config, "Proceeding to slot restoration");
            }

            case RESTORING_SLOT -> handleRestoringSlot(client, config);
        }
    }

    private void handleCheckingInventory(MinecraftClient client, CrystalRushConfig config) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            transitionTo(CrystalActionState.CANCELLED, config, "Player null");
            return;
        }

        // Check if End Crystals are already in main hand or offhand
        if (player.getMainHandStack().isOf(Items.END_CRYSTAL) || player.getOffHandStack().isOf(Items.END_CRYSTAL)) {
            delayTicksRemaining = config.getCrystalActionDelayTicks();
            transitionTo(CrystalActionState.CHECKING_TARGET, config, "End Crystal already held in hand");
            return;
        }

        // Search hotbar (slots 0..8) for Items.END_CRYSTAL
        int crystalSlot = findEndCrystalHotbarSlot(player.getInventory());
        if (crystalSlot == HotbarSlotTracker.NO_SLOT) {
            sendStatusMessage(client, config, Text.translatable("status.crystalrush.no_crystals").formatted(Formatting.AQUA));
            transitionTo(CrystalActionState.CANCELLED, config, "No End Crystals in hotbar");
            return;
        }

        if (!config.isAutoCrystalSlotDetection()) {
            sendStatusMessage(client, config, Text.translatable("status.crystalrush.no_crystals").formatted(Formatting.AQUA));
            transitionTo(CrystalActionState.CANCELLED, config, "Auto slot detection disabled and crystal not held");
            return;
        }

        transitionTo(CrystalActionState.SELECTING_CRYSTAL, config, "Found End Crystal in hotbar slot " + crystalSlot);
    }

    private void handleSelectingCrystal(MinecraftClient client, CrystalRushConfig config) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            transitionTo(CrystalActionState.CANCELLED, config, "Player null");
            return;
        }

        PlayerInventory inventory = player.getInventory();
        int currentSlot = inventory.getSelectedSlot();
        int targetSlot = findEndCrystalHotbarSlot(inventory);

        if (targetSlot == HotbarSlotTracker.NO_SLOT) {
            transitionTo(CrystalActionState.CANCELLED, config, "Crystal disappeared before selection");
            return;
        }

        if (slotTracker.recordModSlotSwitch(currentSlot, targetSlot)) {
            selectHotbarSlotSynchronized(client, player, targetSlot);
        }

        delayTicksRemaining = config.getCrystalActionDelayTicks();
        transitionTo(CrystalActionState.CHECKING_TARGET, config, "Selected crystal slot " + targetSlot);
    }

    private void handleCheckingTarget(MinecraftClient client, CrystalRushConfig config) {
        // Respect both inter-action interval and preparation action delay
        if (intervalTicksRemaining > 0) {
            return;
        }
        if (delayTicksRemaining > 0) {
            delayTicksRemaining--;
            return;
        }

        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        ClientPlayerInteractionManager interactionManager = client.interactionManager;
        HitResult hitResult = client.crosshairTarget;

        if (player == null || world == null || interactionManager == null || hitResult == null) {
            return;
        }

        // Verify the player still holds an End Crystal (unless breaking an existing crystal)
        boolean holdingCrystal = player.getMainHandStack().isOf(Items.END_CRYSTAL)
                || player.getOffHandStack().isOf(Items.END_CRYSTAL);

        if (hitResult.getType() == HitResult.Type.ENTITY && hitResult instanceof EntityHitResult entityHit) {
            Entity targetEntity = entityHit.getEntity();
            if (targetEntity instanceof EndCrystalEntity crystal && crystal.isAlive()) {
                performCrystalBreak(client, player, interactionManager, crystal, config);
                return;
            }
        }

        if (hitResult.getType() == HitResult.Type.BLOCK && hitResult instanceof BlockHitResult blockHit) {
            if (!holdingCrystal) {
                transitionTo(CrystalActionState.CHECKING_INVENTORY, config, "Crystal no longer held; re-checking inventory");
                return;
            }

            BlockPos basePos = blockHit.getBlockPos();
            // If an End Crystal already exists on top of this base block, interact/break it instead of double-placing
            EndCrystalEntity existingCrystal = findCrystalOnBase(world, basePos);
            if (existingCrystal != null && existingCrystal.isAlive()) {
                performCrystalBreak(client, player, interactionManager, existingCrystal, config);
                return;
            }

            if (canPlaceCrystalAt(world, basePos)) {
                performCrystalPlacement(client, player, interactionManager, blockHit, basePos, config);
            }
        }
    }

    private void performCrystalPlacement(
            MinecraftClient client,
            ClientPlayerEntity player,
            ClientPlayerInteractionManager interactionManager,
            BlockHitResult blockHit,
            BlockPos basePos,
            CrystalRushConfig config
    ) {
        Hand hand = player.getMainHandStack().isOf(Items.END_CRYSTAL) ? Hand.MAIN_HAND : Hand.OFF_HAND;
        ActionResult result = interactionManager.interactBlock(player, hand, blockHit);
        if (result.isAccepted()) {
            player.swingHand(hand);
            this.pendingKind = PendingKind.PLACEMENT;
            this.pendingBasePos = basePos.toImmutable();
            this.pendingEntityId = -1;
            this.pendingSequenceId = sequenceGenerator.incrementAndGet();
            this.serverAckReceived = null;
            this.waitTimeoutTicksRemaining = config.getStateConfirmationTimeoutTicks();

            sendOptionalVerifyPacket(
                    this.pendingSequenceId,
                    CrystalActionVerifyC2SPayload.ACTION_PLACE_BASE,
                    this.pendingBasePos,
                    -1
            );

            transitionTo(CrystalActionState.WAITING_FOR_GAME_STATE, config,
                    "Dispatched crystal placement at " + basePos.toShortString());
        }
    }

    private void performCrystalBreak(
            MinecraftClient client,
            ClientPlayerEntity player,
            ClientPlayerInteractionManager interactionManager,
            EndCrystalEntity crystal,
            CrystalRushConfig config
    ) {
        interactionManager.attackEntity(player, crystal);
        player.swingHand(Hand.MAIN_HAND);

        this.pendingKind = PendingKind.BREAK_ENTITY;
        this.pendingBasePos = crystal.getBlockPos();
        this.pendingEntityId = crystal.getId();
        this.pendingSequenceId = sequenceGenerator.incrementAndGet();
        this.serverAckReceived = null;
        this.waitTimeoutTicksRemaining = config.getStateConfirmationTimeoutTicks();

        sendOptionalVerifyPacket(
                this.pendingSequenceId,
                CrystalActionVerifyC2SPayload.ACTION_INTERACT_CRYSTAL,
                this.pendingBasePos,
                this.pendingEntityId
        );

        transitionTo(CrystalActionState.WAITING_FOR_GAME_STATE, config,
                "Dispatched crystal attack on entity ID " + crystal.getId());
    }

    private void handleWaitingForGameState(MinecraftClient client, CrystalRushConfig config) {
        ClientWorld world = client.world;
        if (world == null) {
            transitionTo(CrystalActionState.CANCELLED, config, "World became null while waiting");
            return;
        }

        // If the optional server companion explicitly rejected the action, re-evaluate after interval
        if (Boolean.FALSE.equals(serverAckReceived)) {
            clearPendingContext();
            intervalTicksRemaining = getEffectiveIntervalTicks(config);
            transitionTo(CrystalActionState.CHECKING_TARGET, config, "Server companion rejected action; re-checking");
            return;
        }

        boolean worldStateConfirmed = false;
        if (pendingKind == PendingKind.PLACEMENT && pendingBasePos != null) {
            EndCrystalEntity spawned = findCrystalOnBase(world, pendingBasePos);
            if (spawned != null && spawned.isAlive()) {
                worldStateConfirmed = true;
            }
        } else if (pendingKind == PendingKind.BREAK_ENTITY && pendingEntityId != -1) {
            Entity existing = world.getEntityById(pendingEntityId);
            if (existing == null || !existing.isAlive() || existing.isRemoved()) {
                worldStateConfirmed = true;
            }
        }

        if (worldStateConfirmed) {
            clearPendingContext();
            intervalTicksRemaining = getEffectiveIntervalTicks(config);
            transitionTo(CrystalActionState.ACTION_COMPLETED, config, "Server world state confirmed");
            return;
        }

        if (waitTimeoutTicksRemaining > 0) {
            waitTimeoutTicksRemaining--;
        } else {
            // Timed out waiting for server confirmation (e.g., high ping or blocked placement)
            clearPendingContext();
            intervalTicksRemaining = getEffectiveIntervalTicks(config);
            transitionTo(CrystalActionState.CHECKING_TARGET, config, "Confirmation wait timed out; re-evaluating target");
        }
    }

    private void handleActionCompleted(CrystalRushConfig config, boolean keyHeld) {
        if (!keyHeld || !config.isEnabled()) {
            transitionTo(CrystalActionState.CANCELLED, config, "Key no longer held after completion");
            return;
        }
        if (intervalTicksRemaining > 0) {
            return;
        }
        delayTicksRemaining = config.getCrystalActionDelayTicks();
        transitionTo(CrystalActionState.CHECKING_INVENTORY, config, "Action interval elapsed; starting next cycle");
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
        transitionTo(CrystalActionState.IDLE, config, "Returned to IDLE");
    }

    /**
     * Immediately cancels any pending state machine operation and restores or resets hotbar tracking.
     */
    public void cancelAndCleanup(MinecraftClient client, CrystalRushConfig config, String reason) {
        clearPendingContext();
        if (client != null && client.player != null && client.getNetworkHandler() != null && playerAlive(client.player)) {
            int currentSlot = client.player.getInventory().getSelectedSlot();
            int restoreSlot = slotTracker.consumeSlotToRestore(currentSlot, config.isRestorePreviousHotbarSlot());
            if (restoreSlot != HotbarSlotTracker.NO_SLOT) {
                selectHotbarSlotSynchronized(client, client.player, restoreSlot);
            }
        } else {
            slotTracker.reset();
        }
        transitionTo(CrystalActionState.IDLE, config, "Cancelled: " + reason);
    }

    /**
     * Resets server policy state on disconnect or world change.
     */
    public void onDisconnectOrWorldChange(MinecraftClient client) {
        CrystalRushConfig config = configManager.getConfig();
        clearPendingContext();
        slotTracker.reset();
        serverCompanionPresent = false;
        serverAllowsCrystalRush = true;
        serverMinIntervalTicks = 1;
        wasKeyHeldLastTick = false;
        transitionTo(CrystalActionState.IDLE, config, "Disconnected or changed world");
    }

    public void onServerPolicyReceived(ServerPolicyS2CPayload payload) {
        this.serverCompanionPresent = true;
        this.serverAllowsCrystalRush = payload.allowed();
        this.serverMinIntervalTicks = Math.max(1, payload.minActionIntervalTicks());
        if (configManager.getConfig().isDebugLogging()) {
            CrystalRushMod.LOGGER.info("[CrystalRush/Debug] Received server policy: allowed={}, minInterval={}t",
                    serverAllowsCrystalRush, serverMinIntervalTicks);
        }
    }

    public void onServerAckReceived(CrystalActionAckS2CPayload payload) {
        if (payload.sequenceId() == this.pendingSequenceId) {
            this.serverAckReceived = payload.confirmed();
            if (configManager.getConfig().isDebugLogging()) {
                CrystalRushMod.LOGGER.info("[CrystalRush/Debug] Server Ack seq={} confirmed={} reason={}",
                        payload.sequenceId(), payload.confirmed(), payload.reason());
            }
        }
    }

    /**
     * Searches hotbar slots 0..8 for {@link Items#END_CRYSTAL}, preferring the currently selected slot.
     */
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

    /**
     * Checks normal Minecraft End Crystal placement rules on the client world:
     * base block must be Obsidian or Bedrock, space above must be air, and no blocking entities
     * may intersect the 1x2x1 crystal bounding box.
     */
    public static boolean canPlaceCrystalAt(ClientWorld world, BlockPos basePos) {
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
        List<Entity> entities = world.getOtherEntities(null, new Box(x, y, z, x + 1.0D, y + 2.0D, z + 1.0D));
        return entities.isEmpty();
    }

    private static EndCrystalEntity findCrystalOnBase(ClientWorld world, BlockPos basePos) {
        BlockPos crystalSpace = basePos.up();
        double x = crystalSpace.getX();
        double y = crystalSpace.getY();
        double z = crystalSpace.getZ();
        List<Entity> entities = world.getOtherEntities(null, new Box(x, y, z, x + 1.0D, y + 2.0D, z + 1.0D));
        for (Entity entity : entities) {
            if (entity instanceof EndCrystalEntity crystal && crystal.isAlive()) {
                return crystal;
            }
        }
        return null;
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

    private static boolean isEnvironmentSafeForAction(MinecraftClient client, CrystalRushConfig config) {
        if (client == null || client.player == null || client.world == null || client.interactionManager == null) {
            return false;
        }
        if (client.getNetworkHandler() == null) {
            return false;
        }
        // Never activate while typing in chat, viewing an inventory, or paused in any GUI screen
        if (client.currentScreen != null) {
            return false;
        }
        if (!client.isWindowFocused()) {
            return false;
        }
        return playerAlive(client.player) && !client.player.isSpectator();
    }

    private static boolean playerAlive(ClientPlayerEntity player) {
        return player.isAlive() && !player.isDead();
    }

    private int getEffectiveIntervalTicks(CrystalRushConfig config) {
        int configured = config.getActionIntervalTicks();
        if (serverCompanionPresent) {
            return Math.max(configured, serverMinIntervalTicks);
        }
        return configured;
    }

    private void sendOptionalVerifyPacket(int seqId, int actionType, BlockPos pos, int entityId) {
        if (serverCompanionPresent && ClientPlayNetworking.canSend(CrystalActionVerifyC2SPayload.ID)) {
            ClientPlayNetworking.send(new CrystalActionVerifyC2SPayload(seqId, actionType, pos, entityId));
        }
    }

    private void sendStatusMessage(MinecraftClient client, CrystalRushConfig config, Text message) {
        if (!config.isDisplayStatusMessages() || statusCooldownTicks > 0 || client.player == null) {
            return;
        }
        client.player.sendMessage(message, true);
        statusCooldownTicks = STATUS_MESSAGE_COOLDOWN_TICKS;
    }

    private void clearPendingContext() {
        this.pendingKind = PendingKind.NONE;
        this.pendingBasePos = null;
        this.pendingEntityId = -1;
        this.pendingSequenceId = -1;
        this.serverAckReceived = null;
        this.waitTimeoutTicksRemaining = 0;
    }

    private void transitionTo(CrystalActionState nextState, CrystalRushConfig config, String reason) {
        if (this.state != nextState) {
            if (config != null && config.isDebugLogging()) {
                CrystalRushMod.LOGGER.info("[CrystalRush/State] {} -> {} ({})", this.state, nextState, reason);
            }
            this.state = nextState;
        }
    }

    public CrystalActionState getState() {
        return state;
    }
}
