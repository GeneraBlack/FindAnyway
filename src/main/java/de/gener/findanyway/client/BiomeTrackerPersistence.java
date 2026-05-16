package de.gener.findanyway.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import de.gener.findanyway.FindAnywayPaths;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public final class BiomeTrackerPersistence {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final String FORMAT_VERSION = "1";

    public Optional<SessionRef> resolveCurrentSession(Minecraft minecraft) {
        if (minecraft.hasSingleplayerServer() && minecraft.getSingleplayerServer() != null) {
            String levelName = minecraft.getSingleplayerServer().getWorldData().getLevelName();
            return Optional.of(new SessionRef("singleplayer", safeDisplayName(levelName), storageId("singleplayer", levelName)));
        }

        ServerData server = minecraft.getCurrentServer();
        if (server != null) {
            String identifier = firstNonBlank(server.ip, server.name, "unknown_server");
            String displayName = safeDisplayName(firstNonBlank(server.name, server.ip, identifier));
            return Optional.of(new SessionRef("multiplayer", displayName, storageId("multiplayer", identifier)));
        }

        return Optional.empty();
    }

    public void loadInto(SessionRef session, BiomeTracker tracker) {
        Path file = resolveFile(session);
        tracker.clear();
        if (!Files.exists(file)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            SavedTrackerData data = GSON.fromJson(reader, SavedTrackerData.class);
            tracker.load(toSnapshot(data));
        } catch (IOException | JsonParseException ex) {
            LOGGER.warn("Could not load biome discoveries from {}", file, ex);
            tracker.clear();
        }
    }

    public void save(SessionRef session, BiomeTracker tracker) {
        Path file = resolveFile(session);
        Path parent = file.getParent();
        try {
            Files.createDirectories(parent);
            Path tempFile = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                GSON.toJson(fromSnapshot(session, tracker.snapshot()), writer);
            }
            Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            tracker.markClean();
        } catch (IOException ex) {
            LOGGER.warn("Could not save biome discoveries to {}", file, ex);
        }
    }

    private Path resolveFile(SessionRef session) {
        return FindAnywayPaths.resolveConfigRoot()
            .resolve("discoveries")
            .resolve(session.storageId() + ".json");
    }

    private SavedTrackerData fromSnapshot(SessionRef session, BiomeTracker.TrackerSnapshot snapshot) {
        SavedTrackerData data = new SavedTrackerData();
        data.formatVersion = FORMAT_VERSION;
        data.worldType = session.type();
        data.displayName = session.displayName();

        for (Map.Entry<ResourceLocation, BiomeTracker.DimensionSnapshot> dimensionEntry : snapshot.dimensions().entrySet()) {
            SavedDimension savedDimension = new SavedDimension();
            savedDimension.dimension = dimensionEntry.getKey().toString();

            for (Map.Entry<ResourceLocation, List<BlockPos>> biomeEntry : dimensionEntry.getValue().discoveries().entrySet()) {
                SavedBiome savedBiome = new SavedBiome();
                savedBiome.biome = biomeEntry.getKey().toString();
                for (BlockPos sample : biomeEntry.getValue()) {
                    SavedBlockPos savedPos = new SavedBlockPos();
                    savedPos.x = sample.getX();
                    savedPos.y = sample.getY();
                    savedPos.z = sample.getZ();
                    savedBiome.samples.add(savedPos);
                }
                savedDimension.biomes.add(savedBiome);
            }

            data.dimensions.add(savedDimension);
        }

        return data;
    }

    private BiomeTracker.TrackerSnapshot toSnapshot(SavedTrackerData data) {
        Map<ResourceLocation, BiomeTracker.DimensionSnapshot> dimensions = new LinkedHashMap<>();
        if (data == null || data.dimensions == null) {
            return new BiomeTracker.TrackerSnapshot(dimensions);
        }

        for (SavedDimension savedDimension : data.dimensions) {
            ResourceLocation dimensionId = parseId(savedDimension.dimension);
            if (dimensionId == null) {
                continue;
            }

            Map<ResourceLocation, List<BlockPos>> discoveries = new LinkedHashMap<>();
            if (savedDimension.biomes != null) {
                for (SavedBiome savedBiome : savedDimension.biomes) {
                    ResourceLocation biomeId = parseId(savedBiome.biome);
                    if (biomeId == null) {
                        continue;
                    }

                    List<BlockPos> samples = new ArrayList<>();
                    if (savedBiome.samples != null) {
                        for (SavedBlockPos savedPos : savedBiome.samples) {
                            samples.add(new BlockPos(savedPos.x, savedPos.y, savedPos.z));
                        }
                    }
                    discoveries.put(biomeId, samples);
                }
            }

            dimensions.put(dimensionId, new BiomeTracker.DimensionSnapshot(discoveries));
        }

        return new BiomeTracker.TrackerSnapshot(dimensions);
    }

    private ResourceLocation parseId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return ResourceLocation.tryParse(value);
    }

    private static String storageId(String type, String rawIdentifier) {
        return type + "_" + sanitize(rawIdentifier);
    }

    private static String sanitize(String rawValue) {
        String normalized = rawValue == null ? "" : rawValue.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return "unknown";
        }

        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            if ((current >= 'a' && current <= 'z') || (current >= '0' && current <= '9')) {
                builder.append(current);
            } else {
                builder.append('_');
            }
        }

        return builder.toString().replaceAll("_+", "_");
    }

    private static String safeDisplayName(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown";
        }
        return value.trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "unknown";
    }

    public record SessionRef(String type, String displayName, String storageId) {
    }

    private static final class SavedTrackerData {
        private String formatVersion;
        private String worldType;
        private String displayName;
        private List<SavedDimension> dimensions = new ArrayList<>();
    }

    private static final class SavedDimension {
        private String dimension;
        private List<SavedBiome> biomes = new ArrayList<>();
    }

    private static final class SavedBiome {
        private String biome;
        private List<SavedBlockPos> samples = new ArrayList<>();
    }

    private static final class SavedBlockPos {
        private int x;
        private int y;
        private int z;
    }
}