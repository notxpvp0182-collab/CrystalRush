package dev.crystalrush;

import dev.crystalrush.client.controller.CrystalActionState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StateMachineTransitionTest {

    @Test
    void testAllRequiredStatesPresent() {
        assertNotNull(CrystalActionState.valueOf("IDLE"));
        assertNotNull(CrystalActionState.valueOf("CHECKING_INVENTORY"));
        assertNotNull(CrystalActionState.valueOf("SELECTING_CRYSTAL"));
        assertNotNull(CrystalActionState.valueOf("CHECKING_TARGET"));
        assertNotNull(CrystalActionState.valueOf("WAITING_FOR_GAME_STATE"));
        assertNotNull(CrystalActionState.valueOf("ACTION_COMPLETED"));
        assertNotNull(CrystalActionState.valueOf("CANCELLED"));
        assertNotNull(CrystalActionState.valueOf("RESTORING_SLOT"));
    }
}
