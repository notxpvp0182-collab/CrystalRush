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
import java.nio.file.StandardCopyOption;

/**
 * Handles atomic JSON persistence, validation, and fallback recovery for {@link CrystalRushConfig}.
 */
public class CrystalRushConfigManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("CrystalRush/Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path configPath;
    private CrystalRushConfig config;

    public CrystalRushConfigManager(Path configPath) {
        this.configPath = configPath;
        this.config = new CrystalRushConfig();
    }

    /**
     * Loads the configuration from disk. If the file does not exist or contains corrupt JSON,
     * falls back safely to validated defaults and writes a fresh file.
     */
    public synchronized CrystalRushConfig load() {
        if (!Files.exists(configPath)) {
            LOGGER.info("Configuration file not found at {}; creating default configuration.", configPath);
            this.config = new CrystalRushConfig();
            this.config.validate();
            save();
            return this.config;
        }

        try (BufferedReader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            CrystalRushConfig loaded = GSON.fromJson(reader, CrystalRushConfig.class);
            if (loaded == null) {
                LOGGER.warn("Configuration file at {} was empty; restoring defaults.", configPath);
                this.config = new CrystalRushConfig();
            } else {
                loaded.validate();
                this.config = loaded;
            }
        } catch (IOException | RuntimeException ex) {
            LOGGER.error("Failed to read configuration file at {}; backing up corrupt file and restoring defaults.", configPath, ex);
            backupCorruptConfig();
            this.config = new CrystalRushConfig();
            this.config.validate();
            save();
        }
        return this.config;
    }

    /**
     * Validates and writes the current configuration atomically to disk.
     */
    public synchronized void save() {
        if (this.config == null) {
            this.config = new CrystalRushConfig();
        }
        this.config.validate();

        try {
            Path parent = configPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Path tempFile = configPath.resolveSibling(configPath.getFileName().toString() + ".tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                GSON.toJson(this.config, writer);
            }
            Files.move(tempFile, configPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            try (BufferedWriter writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                GSON.toJson(this.config, writer);
            } catch (IOException innerEx) {
                LOGGER.error("Failed to save configuration to {}", configPath, innerEx);
            }
        }
    }

    private void backupCorruptConfig() {
        try {
            Path backupPath = configPath.resolveSibling(configPath.getFileName().toString() + ".bak");
            Files.copy(configPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
    }

    public synchronized CrystalRushConfig getConfig() {
        return config;
    }

    public Path getConfigPath() {
        return configPath;
    }
}
