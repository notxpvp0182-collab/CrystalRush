package dev.crystalrush;

import dev.crystalrush.client.controller.HotbarSlotTracker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HotbarSlotTrackerTest {

    @Test
    void testNormalSwitchAndRestore() {
        HotbarSlotTracker tracker = new HotbarSlotTracker();

        // Player is on slot 0 (Sword), CrystalRush switches to slot 4 (End Crystal)
        assertTrue(tracker.recordModSlotSwitch(0, 4));
        assertTrue(tracker.isSlotChangedByMod());
        assertEquals(0, tracker.getSavedOriginalSlot());
        assertEquals(4, tracker.getModSelectedSlot());

        // Player stays on slot 4 during activation
        tracker.observeTickSlot(4);
        assertFalse(tracker.isManualOverrideDetected());

        // Key released -> should restore slot 0
        int restored = tracker.consumeSlotToRestore(4, true);
        assertEquals(0, restored);
        assertFalse(tracker.isSlotChangedByMod());
    }

    @Test
    void testManualOverridePreventsOverwrite() {
        HotbarSlotTracker tracker = new HotbarSlotTracker();

        // Player starts on slot 1, mod switches to slot 5
        assertTrue(tracker.recordModSlotSwitch(1, 5));
        tracker.observeTickSlot(5);

        // Player manually presses '3' (slot index 2) while holding or releasing
        tracker.observeTickSlot(2);
        assertTrue(tracker.isManualOverrideDetected());

        // Even if player scrolls back or releases key, mod must NOT overwrite manual slot change
        int restored = tracker.consumeSlotToRestore(2, true);
        assertEquals(HotbarSlotTracker.NO_SLOT, restored);
    }

    @Test
    void testNoSwitchWhenAlreadyOnCrystalSlot() {
        HotbarSlotTracker tracker = new HotbarSlotTracker();

        assertFalse(tracker.recordModSlotSwitch(3, 3));
        assertFalse(tracker.isSlotChangedByMod());
        assertEquals(HotbarSlotTracker.NO_SLOT, tracker.consumeSlotToRestore(3, true));
    }
}
