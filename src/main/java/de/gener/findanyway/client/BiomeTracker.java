package de.gener.findanyway.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;

public final class BiomeTracker {
    private static final int SCAN_RADIUS_CHUNKS = 12;
    private static final int SAMPLE_START = 4;
    private static final int SAMPLE_STEP = 8;

    private final Map<Identifier, DimensionCache> dimensions = new HashMap<>();
    private boolean dirty;

    public void clear() {
        dimensions.clear();
        dirty = false;
    }

    public void scan(ClientLevel level, LocalPlayer player) {
        DimensionCache cache = dimensions.computeIfAbsent(level.dimension().identifier(), key -> new DimensionCache());
        int centerChunkX = player.chunkPosition().x();
        int centerChunkZ = player.chunkPosition().z();

        for (int chunkX = centerChunkX - SCAN_RADIUS_CHUNKS; chunkX <= centerChunkX + SCAN_RADIUS_CHUNKS; chunkX++) {
            for (int chunkZ = centerChunkZ - SCAN_RADIUS_CHUNKS; chunkZ <= centerChunkZ + SCAN_RADIUS_CHUNKS; chunkZ++) {
                scanChunk(level, cache, chunkX, chunkZ);
            }
        }
    }

    public int getKnownBiomeCount(ClientLevel level) {
        DimensionCache cache = dimensions.get(level.dimension().identifier());
        return cache == null ? 0 : cache.discoveries.size();
    }

    public Optional<Identifier> getCurrentSurfaceBiome(ClientLevel level, BlockPos reference) {
        return sampleSurface(level, reference.getX(), reference.getZ()).map(sample -> resolveBiomeId(level, sample));
    }

    public List<BiomeMatch> getVisibleBiomes(ClientLevel level, BlockPos reference, String filter) {
        DimensionCache cache = dimensions.get(level.dimension().identifier());
        if (cache == null) {
            return List.of();
        }

        String normalizedFilter = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        List<BiomeMatch> matches = new ArrayList<>();

        for (BiomeDiscovery discovery : cache.discoveries.values()) {
            String biomeName = discovery.biomeId.toString();
            if (!normalizedFilter.isEmpty() && !biomeName.toLowerCase(Locale.ROOT).contains(normalizedFilter)) {
                continue;
            }

            discovery.nearestTo(reference).ifPresent(nearest -> matches.add(new BiomeMatch(
                discovery.biomeId,
                nearest,
                discovery.sightings.size(),
                horizontalDistance(reference, nearest)
            )));
        }

        matches.sort(Comparator.comparingDouble(BiomeMatch::distanceBlocks).thenComparing(match -> match.biomeId().toString()));
        return matches;
    }

    public Optional<BiomeMatch> findNearest(ClientLevel level, BlockPos reference, String query) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalizedQuery.isEmpty()) {
            return Optional.empty();
        }

        return getVisibleBiomes(level, reference, normalizedQuery).stream().findFirst();
    }

    private void scanChunk(ClientLevel level, DimensionCache cache, int chunkX, int chunkZ) {
        if (!level.hasChunk(chunkX, chunkZ)) {
            return;
        }

        long chunkKey = ChunkPos.pack(chunkX, chunkZ);
        if (!cache.scannedChunks.add(chunkKey)) {
            return;
        }

        int worldStartX = chunkX << 4;
        int worldStartZ = chunkZ << 4;

        for (int localX = SAMPLE_START; localX < 16; localX += SAMPLE_STEP) {
            for (int localZ = SAMPLE_START; localZ < 16; localZ += SAMPLE_STEP) {
                int worldX = worldStartX + localX;
                int worldZ = worldStartZ + localZ;

                sampleSurface(level, worldX, worldZ).ifPresent(sample -> {
                    Identifier biomeId = resolveBiomeId(level, sample);
                    if (biomeId != null && cache.discover(biomeId, sample)) {
                        dirty = true;
                    }
                });
            }
        }
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markClean() {
        dirty = false;
    }

    public TrackerSnapshot snapshot() {
        Map<Identifier, DimensionSnapshot> savedDimensions = new LinkedHashMap<>();
        for (Map.Entry<Identifier, DimensionCache> entry : dimensions.entrySet()) {
            savedDimensions.put(entry.getKey(), entry.getValue().snapshot());
        }
        return new TrackerSnapshot(savedDimensions);
    }

    public void load(TrackerSnapshot snapshot) {
        clear();
        if (snapshot == null) {
            return;
        }

        for (Map.Entry<Identifier, DimensionSnapshot> dimensionEntry : snapshot.dimensions().entrySet()) {
            DimensionCache cache = dimensions.computeIfAbsent(dimensionEntry.getKey(), key -> new DimensionCache());
            for (Map.Entry<Identifier, List<BlockPos>> biomeEntry : dimensionEntry.getValue().discoveries().entrySet()) {
                cache.restore(biomeEntry.getKey(), biomeEntry.getValue());
            }
        }
        dirty = false;
    }

    private Optional<BlockPos> sampleSurface(ClientLevel level, int x, int z) {
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        int clampedY = Mth.clamp(surfaceY, level.getMinY(), level.getMaxY() - 1);
        return Optional.of(new BlockPos(x, clampedY, z));
    }

    private Identifier resolveBiomeId(ClientLevel level, BlockPos sample) {
        return level.getBiome(sample).unwrapKey().map(key -> key.identifier()).orElse(null);
    }

    private static double horizontalDistance(BlockPos first, BlockPos second) {
        long deltaX = (long) second.getX() - first.getX();
        long deltaZ = (long) second.getZ() - first.getZ();
        return Math.sqrt((double) deltaX * deltaX + (double) deltaZ * deltaZ);
    }

    public record BiomeMatch(Identifier biomeId, BlockPos nearestPos, int sightings, double distanceBlocks) {
        public int roundedDistance() {
            return Mth.floor(distanceBlocks);
        }
    }

    public record TrackerSnapshot(Map<Identifier, DimensionSnapshot> dimensions) {
    }

    public record DimensionSnapshot(Map<Identifier, List<BlockPos>> discoveries) {
    }

    private static final class DimensionCache {
        private final Set<Long> scannedChunks = new HashSet<>();
        private final Map<Identifier, BiomeDiscovery> discoveries = new LinkedHashMap<>();

        private boolean discover(Identifier biomeId, BlockPos sample) {
            return discoveries.computeIfAbsent(biomeId, BiomeDiscovery::new).add(sample);
        }

        private void restore(Identifier biomeId, List<BlockPos> samples) {
            BiomeDiscovery discovery = discoveries.computeIfAbsent(biomeId, BiomeDiscovery::new);
            for (BlockPos sample : samples) {
                discovery.add(sample);
            }
        }

        private DimensionSnapshot snapshot() {
            Map<Identifier, List<BlockPos>> savedDiscoveries = new LinkedHashMap<>();
            for (Map.Entry<Identifier, BiomeDiscovery> entry : discoveries.entrySet()) {
                savedDiscoveries.put(entry.getKey(), entry.getValue().snapshot());
            }
            return new DimensionSnapshot(savedDiscoveries);
        }
    }

    private static final class BiomeDiscovery {
        private final Identifier biomeId;
        private final Map<Long, BlockPos> sightings = new LinkedHashMap<>();

        private BiomeDiscovery(Identifier biomeId) {
            this.biomeId = biomeId;
        }

        private boolean add(BlockPos sample) {
            return sightings.putIfAbsent(sample.asLong(), sample.immutable()) == null;
        }

        private Optional<BlockPos> nearestTo(BlockPos reference) {
            BlockPos nearest = null;
            double bestDistance = Double.MAX_VALUE;

            for (BlockPos sample : sightings.values()) {
                double candidateDistance = horizontalDistance(reference, sample);
                if (candidateDistance < bestDistance) {
                    bestDistance = candidateDistance;
                    nearest = sample;
                }
            }

            return Optional.ofNullable(nearest);
        }

        private List<BlockPos> snapshot() {
            return new ArrayList<>(sightings.values());
        }
    }
}
