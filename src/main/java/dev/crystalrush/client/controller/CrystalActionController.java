package dev.crystalrush.client.controller;

import dev.crystalrush.CrystalRushMod;
import dev.crystalrush.config.CrystalRushConfig;
import dev.crystalrush.config.CrystalRushConfigManager;
import dev.crystalrush.network.CrystalActionAckS2CPayload;
import dev.crystalrush.network.ServerPolicyS2CPayload;
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
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * High-speed nanosecond-precision End Crystal controller.
 * Executes slot selection, crystal placement, and crystal detonation with zero multi-tick waiting overhead.
 */
public class CrystalActionController {

    private static final long NANOS_PER_MS = 1_000_000L;
    private static final double CRYSTAL_REACH_SQ = 20.25D; // 4.5 blocks squared

    private final CrystalRushConfigManager configManager;
    private final HotbarSlotTracker slotTracker = new HotbarSlotTracker();

    private CrystalActionState state = CrystalActionState.IDLE;
    private boolean wasKeyHeld = false;

    private long lastPlaceTimeNs = 0L;
    private long lastBreakTimeNs = 0L;
    private long lastStatusTimeNs = 0L;

    private boolean serverCompanionPresent = false;
    private boolean serverAllowsCrystalRush = true;

    public CrystalActionController(CrystalRushConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Called on every client render frame AND client tick for sub-millisecond (0.1 ms) responsiveness.
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

        // 1. Ensure End Crystal is equipped in main hand or offhand immediately in the same pass
        state = CrystalActionState.CHECKING_INVENTORY;
        if (!ensureCrystalSelected(client, player, config)) {
            state = CrystalActionState.CANCELLED;
            return;
        }

        // 2. Check target & execute Place + Blast immediately based on nanosecond timers
        state = CrystalActionState.CHECKING_TARGET;
        executeFastCrystalCycle(client, player, client.world, client.interactionManager, config);
    }

    /**
     * Legacy tick hook delegating to high-speed update.
     */
    public void tick(MinecraftClient client, boolean keyHeld) {
        updateHighSpeed(client, keyHeld);
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

    private void executeFastCrystalCycle(
            MinecraftClient client,
            ClientPlayerEntity player,
            ClientWorld world,
            ClientPlayerInteractionManager interactionManager,
            CrystalRushConfig config
    ) {
        HitResult hitResult = client.crosshairTarget;
        if (hitResult == null) {
            return;
        }

        long nowNs = System.nanoTime();
        long placeDelayNs = (long) (config.getPlaceDelayMs() * NANOS_PER_MS);
        long breakDelayNs = (long) (config.getBreakDelayMs() * NANOS_PER_MS);

        // STEP A: If aiming directly at an End Crystal entity, blast it immediately!
        if (hitResult.getType() == HitResult.Type.ENTITY && hitResult instanceof EntityHitResult entityHit) {
            Entity target = entityHit.getEntity();
            if (target instanceof EndCrystalEntity crystal && crystal.isAlive()) {
                if (nowNs - lastBreakTimeNs >= breakDelayNs) {
                    BlockPos crystalFloorPos = crystal.getBlockPos().down();
                    attackCrystalFast(player, interactionManager, crystal);
                    lastBreakTimeNs = nowNs;
                    state = CrystalActionState.ACTION_COMPLETED;

                    // Instant Re-Place on the base block underneath if place timer is ready
                    if (nowNs - lastPlaceTimeNs >= placeDelayNs && isCrystalBaseBlock(world, crystalFloorPos)) {
                        BlockHitResult syntheticHit = new BlockHitResult(
                                Vec3d.ofCenter(crystalFloorPos).add(0, 0.5, 0),
                                Direction.UP,
                                crystalFloorPos,
                                false
                        );
                        placeCrystalFast(player, interactionManager, syntheticHit);
                        lastPlaceTimeNs = nowNs;
                    }
                }
                return;
            }
        }

        // STEP B: If aiming at Obsidian or Bedrock
        if (hitResult.getType() == HitResult.Type.BLOCK && hitResult instanceof BlockHitResult blockHit) {
            BlockPos basePos = blockHit.getBlockPos();
            if (!isCrystalBaseBlock(world, basePos)) {
                return;
            }

            // Check if there is already an End Crystal on this base block -> Blast it immediately!
            EndCrystalEntity existingCrystal = findCrystalOnBase(world, basePos);
            if (existingCrystal != null && existingCrystal.isAlive()) {
                if (nowNs - lastBreakTimeNs >= breakDelayNs) {
                    attackCrystalFast(player, interactionManager, existingCrystal);
                    lastBreakTimeNs = nowNs;
                    state = CrystalActionState.ACTION_COMPLETED;
                }
            }

            // Place new End Crystal on the base block if place delay has elapsed
            if (nowNs - lastPlaceTimeNs >= placeDelayNs && canPlaceCrystalAt(world, basePos)) {
                if (placeCrystalFast(player, interactionManager, blockHit)) {
                    lastPlaceTimeNs = nowNs;
                    state = CrystalActionState.WAITING_FOR_GAME_STATE;

                    // If a crystal entity is immediately present in client world, blast it in the same cycle if breakDelay allows
                    EndCrystalEntity spawnedCrystal = findCrystalOnBase(world, basePos);
                    if (spawnedCrystal != null && spawnedCrystal.isAlive() && (System.nanoTime() - lastBreakTimeNs >= breakDelayNs)) {
                        attackCrystalFast(player, interactionManager, spawnedCrystal);
                        lastBreakTimeNs = System.nanoTime();
                        state = CrystalActionState.ACTION_COMPLETED;
                    }
                }
            }
        } else {
            // STEP C: Even if crosshair slightly missed, check if there is an EndCrystal directly in front of crosshair ray
            EndCrystalEntity nearbyCrystal = findLookedAtCrystal(player, world);
            if (nearbyCrystal != null && (nowNs - lastBreakTimeNs >= breakDelayNs)) {
                attackCrystalFast(player, interactionManager, nearbyCrystal);
                lastBreakTimeNs = nowNs;
                state = CrystalActionState.ACTION_COMPLETED;
            }
        }
    }

    private boolean placeCrystalFast(
            ClientPlayerEntity player,
            ClientPlayerInteractionManager interactionManager,
            BlockHitResult blockHit
    ) {
        Hand hand = player.getMainHandStack().isOf(Items.END_CRYSTAL) ? Hand.MAIN_HAND
                : (player.getOffHandStack().isOf(Items.END_CRYSTAL) ? Hand.OFF_HAND : null);
        if (hand == null) {
            return false;
        }
        ActionResult result = interactionManager.interactBlock(player, hand, blockHit);
        if (result.isAccepted()) {
            player.swingHand(hand);
            return true;
        }
        return false;
    }

    private void attackCrystalFast(
            ClientPlayerEntity player,
            ClientPlayerInteractionManager interactionManager,
            EndCrystalEntity crystal
    ) {
        interactionManager.attackEntity(player, crystal);
        player.swingHand(Hand.MAIN_HAND);
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
        if (configManager.getConfig().isDebugLogging()) {
            CrystalRushMod.LOGGER.info("[CrystalRush] Server Ack seq={} confirmed={}", payload.sequenceId(), payload.confirmed());
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
        for (Entity entity : entities) {
            if (entity.isAlive() && !(entity instanceof EndCrystalEntity)) {
                return false;
            }
        }
        return true;
    }

    private static EndCrystalEntity findCrystalOnBase(ClientWorld world, BlockPos basePos) {
        BlockPos crystalSpace = basePos.up();
        double x = crystalSpace.getX();
        double y = crystalSpace.getY();
        double z = crystalSpace.getZ();
        List<Entity> entities = world.getOtherEntities(null, new Box(x - 0.5D, y, z - 0.5D, x + 1.5D, y + 2.5D, z + 1.5D));
        for (Entity entity : entities) {
            if (entity instanceof EndCrystalEntity crystal && crystal.isAlive()) {
                return crystal;
            }
        }
        return null;
    }

    private static EndCrystalEntity findLookedAtCrystal(ClientPlayerEntity player, ClientWorld world) {
        Vec3d eyePos = player.getEyePos();
        Vec3d lookVec = player.getRotationVec(1.0F);
        Vec3d endPos = eyePos.add(lookVec.multiply(4.5D));
        Box searchBox = player.getBoundingBox().stretch(lookVec.multiply(4.5D)).expand(1.0D);

        List<Entity> entities = world.getOtherEntities(player, searchBox, e -> e instanceof EndCrystalEntity && e.isAlive());
        EndCrystalEntity closest = null;
        double closestDistSq = CRYSTAL_REACH_SQ;

        for (Entity entity : entities) {
            Box entityBox = entity.getBoundingBox().expand(0.3D);
            if (entityBox.raycast(eyePos, endPos).isPresent()) {
                double distSq = player.squaredDistanceTo(entity);
                if (distSq <= closestDistSq) {
                    closestDistSq = distSq;
                    closest = (EndCrystalEntity) entity;
                }
            }
        }
        return closest;
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
