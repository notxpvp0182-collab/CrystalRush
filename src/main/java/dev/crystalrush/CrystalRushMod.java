package dev.crystalrush;

import dev.crystalrush.config.ServerPolicyConfig;
import dev.crystalrush.network.CrystalRushNetworking;
import dev.crystalrush.server.CrystalRushServerHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Common entrypoint for CrystalRush.
 * Safe on both dedicated servers and integrated clients—never loads client GUI or input classes.
 */
public class CrystalRushMod implements ModInitializer {

    public static final String MOD_ID = "crystalrush";
    public static final Logger LOGGER = LoggerFactory.getLogger("CrystalRush");

    private static ServerPolicyConfig serverPolicyConfig;

    @Override
    public void onInitialize() {
        Path policyPath = FabricLoader.getInstance().getConfigDir().resolve("crystalrush-server.json");
        serverPolicyConfig = ServerPolicyConfig.loadOrCreate(policyPath);

        CrystalRushNetworking.registerCommonPayloads();
        CrystalRushServerHandler.register(serverPolicyConfig);

        LOGGER.info("CrystalRush initialized (Server Policy: allowed={}, minInterval={}t).",
                serverPolicyConfig.isAllowCrystalRush(),
                serverPolicyConfig.getMinimumServerActionIntervalTicks());
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    public static ServerPolicyConfig getServerPolicyConfig() {
        return serverPolicyConfig;
    }
}
