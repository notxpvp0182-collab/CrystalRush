package dev.crystalrush;

import dev.crystalrush.config.CrystalRushConfig;
import dev.crystalrush.config.CrystalRushConfigManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigValidationTest {

    @TempDir
    Path tempDir;

    @Test
    void testDefaultValuesAndClamping() {
        CrystalRushConfig config = new CrystalRushConfig();
        assertTrue(config.isEnabled());
        assertTrue(config.isAutoCrystalSlotDetection());
        assertTrue(config.isRestorePreviousHotbarSlot());
        assertEquals(CrystalRushConfig.DEFAULT_ACTION_DELAY_TICKS, config.getCrystalActionDelayTicks());
        assertEquals(CrystalRushConfig.DEFAULT_ACTION_INTERVAL_TICKS, config.getActionIntervalTicks());

        // Test clamping below minimum
        config.setCrystalActionDelayTicks(-50);
        assertEquals(CrystalRushConfig.MIN_ACTION_DELAY_TICKS, config.getCrystalActionDelayTicks());

        config.setActionIntervalTicks(0);
        assertEquals(CrystalRushConfig.MIN_ACTION_INTERVAL_TICKS, config.getActionIntervalTicks());

        // Test clamping above maximum
        config.setCrystalActionDelayTicks(999);
        assertEquals(CrystalRushConfig.MAX_ACTION_DELAY_TICKS, config.getCrystalActionDelayTicks());

        config.setActionIntervalTicks(999);
        assertEquals(CrystalRushConfig.MAX_ACTION_INTERVAL_TICKS, config.getActionIntervalTicks());
    }

    @Test
    void testConfigPersistenceAndReload() {
        Path configFile = tempDir.resolve("crystalrush.json");
        CrystalRushConfigManager manager = new CrystalRushConfigManager(configFile);

        CrystalRushConfig loaded = manager.load();
        assertTrue(Files.exists(configFile));
        loaded.setEnabled(false);
        loaded.setCrystalActionDelayTicks(5);
        loaded.setActionIntervalTicks(8);
        loaded.setDebugLogging(true);
        manager.save();

        CrystalRushConfigManager secondManager = new CrystalRushConfigManager(configFile);
        CrystalRushConfig reloaded = secondManager.load();
        assertFalse(reloaded.isEnabled());
        assertEquals(5, reloaded.getCrystalActionDelayTicks());
        assertEquals(8, reloaded.getActionIntervalTicks());
        assertTrue(reloaded.isDebugLogging());
    }

    @Test
    void testCorruptJsonRecovery() throws IOException {
        Path configFile = tempDir.resolve("corrupt_crystalrush.json");
        Files.writeString(configFile, "{ invalid_json: [1, 2, ", StandardCharsets.UTF_8);

        CrystalRushConfigManager manager = new CrystalRushConfigManager(configFile);
        CrystalRushConfig recovered = manager.load();

        assertNotNull(recovered);
        assertTrue(recovered.isEnabled());
        assertEquals(CrystalRushConfig.DEFAULT_ACTION_INTERVAL_TICKS, recovered.getActionIntervalTicks());
        assertTrue(Files.exists(configFile.resolveSibling("corrupt_crystalrush.json.bak")));
    }
}
