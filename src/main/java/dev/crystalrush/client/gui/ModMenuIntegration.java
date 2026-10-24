package dev.crystalrush.client.gui;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu entrypoint for CrystalRush (compatible with Mod Menu 17.0.1 on Minecraft 1.21.11).
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return CrystalRushConfigScreen::new;
    }
}
