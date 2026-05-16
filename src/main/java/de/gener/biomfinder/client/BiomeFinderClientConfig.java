package de.gener.biomfinder.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import de.gener.biomfinder.FindAnywayPaths;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.util.Mth;
import org.slf4j.Logger;

public final class BiomeFinderClientConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static BiomeFinderClientConfig current;

    private OverlaySettings overlay = new OverlaySettings();
    private JourneyMapSettings journeyMap = new JourneyMapSettings();

    public static synchronized BiomeFinderClientConfig get() {
        if (current == null) {
            current = loadOrCreate();
        }
        return current;
    }

    public static synchronized BiomeFinderClientConfig reload() {
        current = loadOrCreate();
        return current;
    }

    public OverlaySettings overlay() {
        return overlay;
    }

    public JourneyMapSettings journeyMap() {
        return journeyMap;
    }

    public Path getPath() {
        return resolvePath();
    }

    private static BiomeFinderClientConfig loadOrCreate() {
        Path path = resolvePath();
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException ex) {
            LOGGER.warn("Could not create config directory for {}", path, ex);
        }

        if (!Files.exists(path)) {
            BiomeFinderClientConfig defaults = new BiomeFinderClientConfig();
            defaults.sanitize();
            defaults.save();
            return defaults;
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            BiomeFinderClientConfig loaded = GSON.fromJson(reader, BiomeFinderClientConfig.class);
            if (loaded == null) {
                loaded = new BiomeFinderClientConfig();
            }
            loaded.sanitize();
            return loaded;
        } catch (IOException | JsonParseException ex) {
            LOGGER.warn("Could not load client config from {}. Falling back to defaults.", path, ex);
            BiomeFinderClientConfig defaults = new BiomeFinderClientConfig();
            defaults.sanitize();
            defaults.save();
            return defaults;
        }
    }

    private void save() {
        Path path = resolvePath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException ex) {
            LOGGER.warn("Could not save client config to {}", path, ex);
        }
    }

    private void sanitize() {
        if (overlay == null) {
            overlay = new OverlaySettings();
        }
        if (journeyMap == null) {
            journeyMap = new JourneyMapSettings();
        }

        overlay.sanitize();
        journeyMap.sanitize();
    }

    private static Path resolvePath() {
        return FindAnywayPaths.resolveConfigRoot()
            .resolve("client.json");
    }

    public static final class OverlaySettings {
        private boolean enabled = true;
        private String anchor = "TOP_CENTER";
        private int offsetX = 0;
        private int offsetY = 8;
        private float scale = 1.0F;
        private boolean showBackground = true;

        private void sanitize() {
            anchor = OverlayAnchor.normalize(anchor);
            scale = Mth.clamp(scale, 0.5F, 4.0F);
            offsetX = Mth.clamp(offsetX, -4000, 4000);
            offsetY = Mth.clamp(offsetY, -4000, 4000);
        }

        public boolean enabled() {
            return enabled;
        }

        public OverlayAnchor anchor() {
            return OverlayAnchor.valueOf(anchor);
        }

        public int offsetX() {
            return offsetX;
        }

        public int offsetY() {
            return offsetY;
        }

        public float scale() {
            return scale;
        }

        public boolean showBackground() {
            return showBackground;
        }
    }

    public static final class JourneyMapSettings {
        private boolean markerEnabled = true;
        private boolean areaEnabled = true;
        private int areaRadiusBlocks = 64;
        private int markerColor = 0xFFD37A;
        private int areaStrokeColor = 0xFF9F1C;
        private int areaFillColor = 0xFFCC66;

        private void sanitize() {
            areaRadiusBlocks = Mth.clamp(areaRadiusBlocks, 8, 512);
            markerColor &= 0xFFFFFF;
            areaStrokeColor &= 0xFFFFFF;
            areaFillColor &= 0xFFFFFF;
        }

        public boolean markerEnabled() {
            return markerEnabled;
        }

        public boolean areaEnabled() {
            return areaEnabled;
        }

        public int areaRadiusBlocks() {
            return areaRadiusBlocks;
        }

        public int markerColor() {
            return markerColor;
        }

        public int areaStrokeColor() {
            return areaStrokeColor;
        }

        public int areaFillColor() {
            return areaFillColor;
        }
    }

    public enum OverlayAnchor {
        TOP_LEFT,
        TOP_CENTER,
        TOP_RIGHT,
        CENTER_LEFT,
        CENTER,
        CENTER_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_CENTER,
        BOTTOM_RIGHT;

        static String normalize(String value) {
            if (value == null || value.isBlank()) {
                return TOP_CENTER.name();
            }

            String normalized = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
            for (OverlayAnchor anchor : values()) {
                if (anchor.name().equals(normalized)) {
                    return anchor.name();
                }
            }
            return TOP_CENTER.name();
        }

        boolean isLeft() {
            return this == TOP_LEFT || this == CENTER_LEFT || this == BOTTOM_LEFT;
        }

        boolean isRight() {
            return this == TOP_RIGHT || this == CENTER_RIGHT || this == BOTTOM_RIGHT;
        }

        boolean isTop() {
            return this == TOP_LEFT || this == TOP_CENTER || this == TOP_RIGHT;
        }

        boolean isBottom() {
            return this == BOTTOM_LEFT || this == BOTTOM_CENTER || this == BOTTOM_RIGHT;
        }
    }
}