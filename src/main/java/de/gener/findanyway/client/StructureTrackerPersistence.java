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
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

public final class StructureTrackerPersistence {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final String FORMAT_VERSION = "1";

    public void loadInto(BiomeTrackerPersistence.SessionRef session, StructureTracker tracker) {
        Path file = resolveFile(session);
        tracker.clear();
        if (!Files.exists(file)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            SavedTrackerData data = GSON.fromJson(reader, SavedTrackerData.class);
            tracker.load(toSnapshot(data));
        } catch (IOException | JsonParseException ex) {
            LOGGER.warn("Could not load discovered structures from {}", file, ex);
            tracker.clear();
        }
    }

    public void save(BiomeTrackerPersistence.SessionRef session, StructureTracker tracker) {
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
            LOGGER.warn("Could not save discovered structures to {}", file, ex);
        }
    }

    private Path resolveFile(BiomeTrackerPersistence.SessionRef session) {
        return FindAnywayPaths.resolveConfigRoot()
            .resolve("discoveries")
            .resolve(session.storageId() + "_structures.json");
    }

    private SavedTrackerData fromSnapshot(BiomeTrackerPersistence.SessionRef session, StructureTracker.TrackerSnapshot snapshot) {
        SavedTrackerData data = new SavedTrackerData();
        data.formatVersion = FORMAT_VERSION;
        data.worldType = session.type();
        data.displayName = session.displayName();

        for (Map.Entry<Identifier, List<StructureTracker.StructureSnapshot>> dimensionEntry : snapshot.dimensions().entrySet()) {
            SavedDimension savedDimension = new SavedDimension();
            savedDimension.dimension = dimensionEntry.getKey().toString();

            for (StructureTracker.StructureSnapshot structureSnapshot : dimensionEntry.getValue()) {
                SavedStructure savedStructure = new SavedStructure();
                savedStructure.structure = structureSnapshot.structureId().toString();
                for (BlockPos sample : structureSnapshot.samples()) {
                    SavedBlockPos savedPos = new SavedBlockPos();
                    savedPos.x = sample.getX();
                    savedPos.y = sample.getY();
                    savedPos.z = sample.getZ();
                    savedStructure.samples.add(savedPos);
                }
                savedDimension.structures.add(savedStructure);
            }

            data.dimensions.add(savedDimension);
        }

        return data;
    }

    private StructureTracker.TrackerSnapshot toSnapshot(SavedTrackerData data) {
        Map<Identifier, List<StructureTracker.StructureSnapshot>> dimensions = new LinkedHashMap<>();
        if (data == null || data.dimensions == null) {
            return new StructureTracker.TrackerSnapshot(dimensions);
        }

        for (SavedDimension savedDimension : data.dimensions) {
            Identifier dimensionId = parseId(savedDimension.dimension);
            if (dimensionId == null) {
                continue;
            }

            List<StructureTracker.StructureSnapshot> structures = new ArrayList<>();
            if (savedDimension.structures != null) {
                for (SavedStructure savedStructure : savedDimension.structures) {
                    Identifier structureId = parseId(savedStructure.structure);
                    if (structureId == null) {
                        continue;
                    }

                    List<BlockPos> samples = new ArrayList<>();
                    if (savedStructure.samples != null) {
                        for (SavedBlockPos savedPos : savedStructure.samples) {
                            samples.add(new BlockPos(savedPos.x, savedPos.y, savedPos.z));
                        }
                    }
                    structures.add(new StructureTracker.StructureSnapshot(structureId, samples));
                }
            }

            dimensions.put(dimensionId, structures);
        }

        return new StructureTracker.TrackerSnapshot(dimensions);
    }

    private Identifier parseId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Identifier.tryParse(value);
    }

    private static final class SavedTrackerData {
        private String formatVersion;
        private String worldType;
        private String displayName;
        private List<SavedDimension> dimensions = new ArrayList<>();
    }

    private static final class SavedDimension {
        private String dimension;
        private List<SavedStructure> structures = new ArrayList<>();
    }

    private static final class SavedStructure {
        private String structure;
        private List<SavedBlockPos> samples = new ArrayList<>();
    }

    private static final class SavedBlockPos {
        private int x;
        private int y;
        private int z;
    }
}