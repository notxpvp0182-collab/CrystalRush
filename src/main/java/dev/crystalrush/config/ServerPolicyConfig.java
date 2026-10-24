package dev.crystalrush.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server-side policy configuration for administrators running CrystalRush on a dedicated or LAN server.
 */
public class ServerPolicyConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("CrystalRush/ServerPolicy");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Whether CrystalRush client automation is permitted on this server. */
    private boolean allowCrystalRush = true;

    /** Minimum action interval (in ticks) enforced by the server companion. */
    private int minimumServerActionIntervalTicks = 2;

    /** Maximum block reach distance allowed when validating custom CrystalRush packets. */
    private double maxPlacementReachBlocks = 4.5D;

    public void validate() {
        if (minimumServerActionIntervalTicks < 1) {
            minimumServerActionIntervalTicks = 1;
        } else if (minimumServerActionIntervalTicks > 40) {
            minimumServerActionIntervalTicks = 40;
        }
        if (maxPlacementReachBlocks < 1.0D || maxPlacementReachBlocks > 6.0D) {
            maxPlacementReachBlocks = 4.5D;
        }
    }

    public static ServerPolicyConfig loadOrCreate(Path path) {
        if (Files.exists(path)) {
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                ServerPolicyConfig loaded = GSON.fromJson(reader, ServerPolicyConfig.class);
                if (loaded != null) {
                    loaded.validate();
                    return loaded;
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to read server policy config at {}, using defaults.", path, e);
            }
        }
        ServerPolicyConfig defaults = new ServerPolicyConfig();
        defaults.validate();
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(defaults, writer);
            }
        } catch (IOException e) {
            LOGGER.warn("Could not write default server policy config at {}", path, e);
        }
        return defaults;
    }

    public boolean isAllowCrystalRush() {
        return allowCrystalRush;
    }

    public int getMinimumServerActionIntervalTicks() {
        return minimumServerActionIntervalTicks;
    }

    public double getMaxPlacementReachBlocks() {
        return maxPlacementReachBlocks;
    }
}
