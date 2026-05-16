package de.gener.findanyway;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

public final class FindAnywayPaths {
    public static final String MOD_ID = "findanyway";
    private static final String LEGACY_MOD_ID = "biomfinder";
    private static final Logger LOGGER = LogUtils.getLogger();

    private FindAnywayPaths() {
    }

    public static Path resolveConfigRoot() {
        Path configDir = FMLPaths.GAMEDIR.get().resolve("config");
        Path currentDir = configDir.resolve(MOD_ID);
        Path legacyDir = configDir.resolve(LEGACY_MOD_ID);

        if (Files.notExists(currentDir) && Files.exists(legacyDir)) {
            try {
                Files.move(legacyDir, currentDir);
            } catch (IOException ex) {
                LOGGER.warn("Could not migrate legacy config directory {} to {}. Continuing with legacy path.", legacyDir, currentDir, ex);
                return legacyDir;
            }
        }

        return currentDir;
    }
}