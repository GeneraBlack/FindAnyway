package de.gener.biomfinder.client;

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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

@SuppressWarnings({"null", "java:S135", "java:S3776"})
public final class StructureTracker {
    private static final int SCAN_RADIUS_CHUNKS = 12;
    private static final String MINECRAFT_NAMESPACE = "minecraft";

    private final Map<ResourceLocation, DimensionCache> dimensions = new HashMap<>();
    private boolean dirty;

    public void clear() {
        dimensions.clear();
        dirty = false;
    }

    public void scan(ClientLevel level, LocalPlayer player) {
        DimensionCache cache = dimensions.computeIfAbsent(level.dimension().location(), key -> new DimensionCache());
        int centerChunkX = player.chunkPosition().x;
        int centerChunkZ = player.chunkPosition().z;

        for (int chunkX = centerChunkX - SCAN_RADIUS_CHUNKS; chunkX <= centerChunkX + SCAN_RADIUS_CHUNKS; chunkX++) {
            for (int chunkZ = centerChunkZ - SCAN_RADIUS_CHUNKS; chunkZ <= centerChunkZ + SCAN_RADIUS_CHUNKS; chunkZ++) {
                scanChunk(level, cache, chunkX, chunkZ);
            }
        }
    }

    public int getKnownStructureCount(ClientLevel level) {
        DimensionCache cache = dimensions.get(level.dimension().location());
        return cache == null ? 0 : cache.sites.size();
    }

    public List<StructureMatch> getVisibleStructures(ClientLevel level, BlockPos reference, String filter) {
        DimensionCache cache = dimensions.get(level.dimension().location());
        if (cache == null) {
            return List.of();
        }

        String normalizedFilter = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        List<StructureMatch> matches = new ArrayList<>();

        for (StructureSite site : cache.sites) {
            BlockPos anchorPos = site.anchorPos();
            if (site.matchesFilter(normalizedFilter) && anchorPos != null) {
                matches.add(new StructureMatch(
                    site.structureId,
                    site.displayName(),
                    anchorPos,
                    site.sightings.size(),
                    horizontalDistance(reference, anchorPos)
                ));
            }
        }

        matches.sort(Comparator.comparingDouble(StructureMatch::distanceBlocks).thenComparing(StructureMatch::displayName));
        return matches;
    }

    public Optional<StructureMatch> findNearest(ClientLevel level, BlockPos reference, String query) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalizedQuery.isEmpty()) {
            return Optional.empty();
        }

        return getVisibleStructures(level, reference, normalizedQuery).stream().findFirst();
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markClean() {
        dirty = false;
    }

    public TrackerSnapshot snapshot() {
        Map<ResourceLocation, List<StructureSnapshot>> savedDimensions = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, DimensionCache> entry : dimensions.entrySet()) {
            savedDimensions.put(entry.getKey(), entry.getValue().snapshot());
        }
        return new TrackerSnapshot(savedDimensions);
    }

    public void load(TrackerSnapshot snapshot) {
        clear();
        if (snapshot == null) {
            return;
        }

        for (Map.Entry<ResourceLocation, List<StructureSnapshot>> dimensionEntry : snapshot.dimensions().entrySet()) {
            DimensionCache cache = dimensions.computeIfAbsent(dimensionEntry.getKey(), key -> new DimensionCache());
            for (StructureSnapshot structureSnapshot : dimensionEntry.getValue()) {
                cache.restore(structureSnapshot.structureId(), structureSnapshot.samples());
            }
        }
        dirty = false;
    }

    private void scanChunk(ClientLevel level, DimensionCache cache, int chunkX, int chunkZ) {
        if (!level.hasChunk(chunkX, chunkZ)) {
            return;
        }

        long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
        if (!cache.scannedChunks.add(chunkKey)) {
            return;
        }

        for (StructureType structureType : StructureType.values()) {
            structureType.detect(level, chunkX, chunkZ).ifPresent(sample -> {
                if (cache.discover(structureType.id(), sample)) {
                    dirty = true;
                }
            });
        }
    }

    private static Optional<BlockPos> findFirstBlock(ClientLevel level, int chunkX, int chunkZ, int minY, int maxY, net.minecraft.world.level.block.Block... blocks) {
        int startX = chunkX << 4;
        int startZ = chunkZ << 4;
        int clampedMinY = Math.max(level.getMinBuildHeight(), minY);
        int clampedMaxY = Math.min(level.getMaxBuildHeight() - 1, maxY);
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int y = clampedMinY; y <= clampedMaxY; y++) {
            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    mutablePos.set(startX + localX, y, startZ + localZ);
                    for (net.minecraft.world.level.block.Block block : blocks) {
                        if (level.getBlockState(mutablePos).is(block)) {
                            return Optional.of(mutablePos.immutable());
                        }
                    }
                }
            }
        }

        return Optional.empty();
    }

    private static Optional<BlockPos> findOceanMonumentSignature(ClientLevel level, int chunkX, int chunkZ) {
        if (!level.dimension().equals(Level.OVERWORLD)) {
            return Optional.empty();
        }

        ResourceLocation biomeId = sampleChunkBiome(level, chunkX, chunkZ, level.getSeaLevel()).orElse(null);
        if (biomeId == null || !biomeId.getPath().contains("ocean")) {
            return Optional.empty();
        }

        int startX = chunkX << 4;
        int startZ = chunkZ << 4;
        int minY = Math.max(level.getMinBuildHeight(), 24);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, 80);
        int prismarineBlocks = 0;
        int seaLanterns = 0;
        int waterBlocks = 0;
        BlockPos anchor = null;
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int y = minY; y <= maxY; y++) {
            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    mutablePos.set(startX + localX, y, startZ + localZ);
                    var state = level.getBlockState(mutablePos);

                    if (state.is(Blocks.PRISMARINE) || state.is(Blocks.PRISMARINE_BRICKS) || state.is(Blocks.DARK_PRISMARINE)) {
                        prismarineBlocks++;
                        if (anchor == null) {
                            anchor = mutablePos.immutable();
                        }
                    } else if (state.is(Blocks.SEA_LANTERN)) {
                        seaLanterns++;
                        if (anchor == null) {
                            anchor = mutablePos.immutable();
                        }
                    } else if (state.is(Blocks.WATER)) {
                        waterBlocks++;
                    }

                    if (prismarineBlocks >= 120 && seaLanterns >= 6 && waterBlocks >= 120 && anchor != null) {
                        return Optional.of(anchor);
                    }
                }
            }
        }

        return Optional.empty();
    }

    private static Optional<BlockPos> findEndCitySignature(ClientLevel level, int chunkX, int chunkZ) {
        if (!level.dimension().equals(Level.END)) {
            return Optional.empty();
        }

        ResourceLocation biomeId = sampleChunkBiome(level, chunkX, chunkZ, 80).orElse(null);
        if (biomeId == null) {
            return Optional.empty();
        }

        String biomePath = biomeId.getPath();
        if (!biomePath.contains("end_highlands") && !biomePath.contains("end_midlands") && !biomePath.contains("small_end_islands")) {
            return Optional.empty();
        }

        int startX = chunkX << 4;
        int startZ = chunkZ << 4;
        int minY = Math.max(level.getMinBuildHeight(), 48);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, 128);
        int purpurBlocks = 0;
        int endStoneBricks = 0;
        int endRods = 0;
        BlockPos anchor = null;
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int y = minY; y <= maxY; y++) {
            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    mutablePos.set(startX + localX, y, startZ + localZ);
                    BlockState state = level.getBlockState(mutablePos);

                    if (isEndCityPurpur(state)) {
                        purpurBlocks++;
                        if (anchor == null) {
                            anchor = mutablePos.immutable();
                        }
                    } else if (state.is(Blocks.END_STONE_BRICKS)) {
                        endStoneBricks++;
                        if (anchor == null) {
                            anchor = mutablePos.immutable();
                        }
                    } else if (state.is(Blocks.END_ROD)) {
                        endRods++;
                        if (anchor == null) {
                            anchor = mutablePos.immutable();
                        }
                    }

                    if (purpurBlocks >= 24 && endStoneBricks >= 8 && endRods >= 1 && anchor != null) {
                        return Optional.of(anchor);
                    }
                }
            }
        }

        return Optional.empty();
    }

    private static Optional<BlockPos> findWoodlandMansionSignature(ClientLevel level, int chunkX, int chunkZ) {
        if (!level.dimension().equals(Level.OVERWORLD)) {
            return Optional.empty();
        }

        ResourceLocation biomeId = sampleChunkBiome(level, chunkX, chunkZ, level.getSeaLevel()).orElse(null);
        if (biomeId == null || !biomeId.getPath().contains("dark_forest")) {
            return Optional.empty();
        }

        int startX = chunkX << 4;
        int startZ = chunkZ << 4;
        int minY = Math.max(level.getMinBuildHeight(), 56);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, 140);
        int darkOakBlocks = 0;
        int cobblestoneBlocks = 0;
        int carpets = 0;
        BlockPos anchor = null;
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int y = minY; y <= maxY; y++) {
            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    mutablePos.set(startX + localX, y, startZ + localZ);
                    BlockState state = level.getBlockState(mutablePos);

                    if (isWoodlandMansionDarkOak(state)) {
                        darkOakBlocks++;
                        if (anchor == null) {
                            anchor = mutablePos.immutable();
                        }
                    } else if (state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE)) {
                        cobblestoneBlocks++;
                        if (anchor == null) {
                            anchor = mutablePos.immutable();
                        }
                    } else if (isWoodlandMansionCarpet(state)) {
                        carpets++;
                        if (anchor == null) {
                            anchor = mutablePos.immutable();
                        }
                    }

                    if (darkOakBlocks >= 80 && cobblestoneBlocks >= 24 && carpets >= 3 && anchor != null) {
                        return Optional.of(anchor);
                    }
                }
            }
        }

        return Optional.empty();
    }

    private static Optional<BlockPos> findNetherFortressSignature(ClientLevel level, int chunkX, int chunkZ) {
        if (!level.dimension().equals(Level.NETHER)) {
            return Optional.empty();
        }

        int startX = chunkX << 4;
        int startZ = chunkZ << 4;
        int minY = Math.max(level.getMinBuildHeight(), 16);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, 112);
        int netherBricks = 0;
        int fences = 0;
        int stairs = 0;
        int netherWart = 0;
        int soulSand = 0;
        BlockPos anchor = null;
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int y = minY; y <= maxY; y++) {
            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    mutablePos.set(startX + localX, y, startZ + localZ);
                    BlockState state = level.getBlockState(mutablePos);

                    if (isNetherFortressBrick(state)) {
                        netherBricks++;
                        if (anchor == null) {
                            anchor = mutablePos.immutable();
                        }
                    } else if (state.is(Blocks.NETHER_BRICK_FENCE)) {
                        fences++;
                        if (anchor == null) {
                            anchor = mutablePos.immutable();
                        }
                    } else if (state.is(Blocks.NETHER_BRICK_STAIRS)) {
                        stairs++;
                        if (anchor == null) {
                            anchor = mutablePos.immutable();
                        }
                    } else if (state.is(Blocks.NETHER_WART)) {
                        netherWart++;
                        if (anchor == null) {
                            anchor = mutablePos.immutable();
                        }
                    } else if (state.is(Blocks.SOUL_SAND)) {
                        soulSand++;
                        if (anchor == null) {
                            anchor = mutablePos.immutable();
                        }
                    }

                    boolean corridorSignature = netherBricks >= 80 && fences >= 6 && stairs >= 2;
                    boolean wartRoomSignature = netherBricks >= 36 && netherWart >= 8 && soulSand >= 8;
                    if ((corridorSignature || wartRoomSignature) && anchor != null) {
                        return Optional.of(anchor);
                    }
                }
            }
        }

        return Optional.empty();
    }

    private static Optional<BlockPos> findBastionSignature(ClientLevel level, int chunkX, int chunkZ) {
        if (!level.dimension().equals(Level.NETHER)) {
            return Optional.empty();
        }

        int startX = chunkX << 4;
        int startZ = chunkZ << 4;
        int minY = Math.max(level.getMinBuildHeight(), 18);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, 128);
        int blackstoneBlocks = 0;
        int gildedBlackstone = 0;
        int goldBlocks = 0;
        int chains = 0;
        int lavaBlocks = 0;
        BlockPos anchor = null;
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int y = minY; y <= maxY; y++) {
            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    mutablePos.set(startX + localX, y, startZ + localZ);
                    BlockState state = level.getBlockState(mutablePos);

                    if (isBastionBlackstone(state)) {
                        blackstoneBlocks++;
                        if (anchor == null) {
                            anchor = mutablePos.immutable();
                        }
                    } else if (state.is(Blocks.GILDED_BLACKSTONE)) {
                        gildedBlackstone++;
                        if (anchor == null) {
                            anchor = mutablePos.immutable();
                        }
                    } else if (state.is(Blocks.GOLD_BLOCK)) {
                        goldBlocks++;
                        if (anchor == null) {
                            anchor = mutablePos.immutable();
                        }
                    } else if (state.is(Blocks.CHAIN)) {
                        chains++;
                        if (anchor == null) {
                            anchor = mutablePos.immutable();
                        }
                    } else if (state.is(Blocks.LAVA)) {
                        lavaBlocks++;
                    }

                    boolean treasureSignature = blackstoneBlocks >= 80 && gildedBlackstone >= 2 && goldBlocks >= 1;
                    boolean generalSignature = blackstoneBlocks >= 120 && (gildedBlackstone >= 1 || goldBlocks >= 2) && chains >= 2 && lavaBlocks >= 12;
                    if ((treasureSignature || generalSignature) && anchor != null) {
                        return Optional.of(anchor);
                    }
                }
            }
        }

        return Optional.empty();
    }

    private static Optional<ResourceLocation> sampleChunkBiome(ClientLevel level, int chunkX, int chunkZ, int y) {
        int clampedY = Mth.clamp(y, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
        BlockPos biomeSample = new BlockPos((chunkX << 4) + 8, clampedY, (chunkZ << 4) + 8);
        return level.getBiome(biomeSample).unwrapKey().map(ResourceKey::location);
    }

    private static boolean isEndCityPurpur(BlockState state) {
        return state.is(Blocks.PURPUR_BLOCK)
            || state.is(Blocks.PURPUR_PILLAR)
            || state.is(Blocks.PURPUR_STAIRS)
            || state.is(Blocks.PURPUR_SLAB);
    }

    private static boolean isNetherFortressBrick(BlockState state) {
        return state.is(Blocks.NETHER_BRICKS)
            || state.is(Blocks.CRACKED_NETHER_BRICKS)
            || state.is(Blocks.RED_NETHER_BRICKS);
    }

    private static boolean isBastionBlackstone(BlockState state) {
        return state.is(Blocks.BLACKSTONE)
            || state.is(Blocks.POLISHED_BLACKSTONE)
            || state.is(Blocks.POLISHED_BLACKSTONE_BRICKS)
            || state.is(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS)
            || state.is(Blocks.CHISELED_POLISHED_BLACKSTONE)
            || state.is(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS)
            || state.is(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB)
            || state.is(Blocks.POLISHED_BLACKSTONE_STAIRS)
            || state.is(Blocks.POLISHED_BLACKSTONE_SLAB);
    }

    private static boolean isWoodlandMansionDarkOak(BlockState state) {
        return state.is(Blocks.DARK_OAK_PLANKS)
            || state.is(Blocks.DARK_OAK_LOG)
            || state.is(Blocks.DARK_OAK_WOOD)
            || state.is(Blocks.STRIPPED_DARK_OAK_LOG)
            || state.is(Blocks.STRIPPED_DARK_OAK_WOOD)
            || state.is(Blocks.DARK_OAK_STAIRS)
            || state.is(Blocks.DARK_OAK_SLAB);
    }

    private static boolean isWoodlandMansionCarpet(BlockState state) {
        return state.is(Blocks.WHITE_CARPET)
            || state.is(Blocks.RED_CARPET)
            || state.is(Blocks.GRAY_CARPET)
            || state.is(Blocks.LIGHT_GRAY_CARPET);
    }

    private static double horizontalDistance(BlockPos first, BlockPos second) {
        long deltaX = (long) second.getX() - first.getX();
        long deltaZ = (long) second.getZ() - first.getZ();
        return Math.sqrt((double) deltaX * deltaX + (double) deltaZ * deltaZ);
    }

    public record StructureMatch(ResourceLocation structureId, String displayName, BlockPos anchorPos, int sightings, double distanceBlocks) {
        public int roundedDistance() {
            return Mth.floor(distanceBlocks);
        }
    }

    public record TrackerSnapshot(Map<ResourceLocation, List<StructureSnapshot>> dimensions) {
    }

    public record StructureSnapshot(ResourceLocation structureId, List<BlockPos> samples) {
    }

    private static final class DimensionCache {
        private final Set<Long> scannedChunks = new HashSet<>();
        private final List<StructureSite> sites = new ArrayList<>();

        private boolean discover(ResourceLocation structureId, BlockPos sample) {
            StructureSite site = findSite(structureId, sample);
            if (site == null) {
                site = new StructureSite(structureId);
                sites.add(site);
            }
            return site.add(sample);
        }

        private void restore(ResourceLocation structureId, List<BlockPos> samples) {
            StructureSite site = new StructureSite(structureId);
            for (BlockPos sample : samples) {
                site.add(sample);
            }
            if (site.anchorPos() != null) {
                sites.add(site);
            }
        }

        private List<StructureSnapshot> snapshot() {
            List<StructureSnapshot> savedSites = new ArrayList<>();
            for (StructureSite site : sites) {
                savedSites.add(new StructureSnapshot(site.structureId, site.snapshot()));
            }
            return savedSites;
        }

        private StructureSite findSite(ResourceLocation structureId, BlockPos sample) {
            double bestDistance = Double.MAX_VALUE;
            StructureSite bestMatch = null;
            int mergeRadius = StructureType.mergeRadiusFor(structureId);

            for (StructureSite site : sites) {
                BlockPos anchorPos = site.anchorPos();
                if (!site.structureId.equals(structureId) || anchorPos == null) {
                    continue;
                }

                double distance = horizontalDistance(anchorPos, sample);
                if (distance <= mergeRadius && distance < bestDistance) {
                    bestDistance = distance;
                    bestMatch = site;
                }
            }

            return bestMatch;
        }
    }

    private static final class StructureSite {
        private final ResourceLocation structureId;
        private final Map<Long, BlockPos> sightings = new LinkedHashMap<>();

        private StructureSite(ResourceLocation structureId) {
            this.structureId = structureId;
        }

        private boolean add(BlockPos sample) {
            return sightings.putIfAbsent(sample.asLong(), sample.immutable()) == null;
        }

        private boolean matchesFilter(String normalizedFilter) {
            if (normalizedFilter.isEmpty()) {
                return true;
            }

            String idValue = structureId.toString().toLowerCase(Locale.ROOT);
            String displayValue = displayName().toLowerCase(Locale.ROOT);
            return idValue.contains(normalizedFilter) || displayValue.contains(normalizedFilter);
        }

        private BlockPos anchorPos() {
            return sightings.values().stream().findFirst().orElse(null);
        }

        private String displayName() {
            return StructureType.displayName(structureId);
        }

        private List<BlockPos> snapshot() {
            return new ArrayList<>(sightings.values());
        }
    }

    private enum StructureType {
        TRIAL_CHAMBERS(ResourceLocation.fromNamespaceAndPath(MINECRAFT_NAMESPACE, "trial_chambers"), "structure.biomfinder.trial_chambers", 96) {
            @Override
            protected Optional<BlockPos> detect(ClientLevel level, int chunkX, int chunkZ) {
                if (!level.dimension().equals(Level.OVERWORLD)) {
                    return Optional.empty();
                }
                return findFirstBlock(level, chunkX, chunkZ, level.getMinBuildHeight(), 32, Blocks.TRIAL_SPAWNER, Blocks.VAULT);
            }
        },
        ANCIENT_CITY(ResourceLocation.fromNamespaceAndPath(MINECRAFT_NAMESPACE, "ancient_city"), "structure.biomfinder.ancient_city", 192) {
            @Override
            protected Optional<BlockPos> detect(ClientLevel level, int chunkX, int chunkZ) {
                if (!level.dimension().equals(Level.OVERWORLD)) {
                    return Optional.empty();
                }
                return findFirstBlock(level, chunkX, chunkZ, level.getMinBuildHeight(), 16, Blocks.REINFORCED_DEEPSLATE);
            }
        },
        OCEAN_MONUMENT(ResourceLocation.fromNamespaceAndPath(MINECRAFT_NAMESPACE, "ocean_monument"), "structure.biomfinder.ocean_monument", 128) {
            @Override
            protected Optional<BlockPos> detect(ClientLevel level, int chunkX, int chunkZ) {
                return findOceanMonumentSignature(level, chunkX, chunkZ);
            }
        },
        END_CITY(ResourceLocation.fromNamespaceAndPath(MINECRAFT_NAMESPACE, "end_city"), "structure.biomfinder.end_city", 160) {
            @Override
            protected Optional<BlockPos> detect(ClientLevel level, int chunkX, int chunkZ) {
                return findEndCitySignature(level, chunkX, chunkZ);
            }
        },
        WOODLAND_MANSION(ResourceLocation.fromNamespaceAndPath(MINECRAFT_NAMESPACE, "woodland_mansion"), "structure.biomfinder.woodland_mansion", 224) {
            @Override
            protected Optional<BlockPos> detect(ClientLevel level, int chunkX, int chunkZ) {
                return findWoodlandMansionSignature(level, chunkX, chunkZ);
            }
        },
        NETHER_FORTRESS(ResourceLocation.fromNamespaceAndPath(MINECRAFT_NAMESPACE, "fortress"), "structure.biomfinder.nether_fortress", 192) {
            @Override
            protected Optional<BlockPos> detect(ClientLevel level, int chunkX, int chunkZ) {
                return findNetherFortressSignature(level, chunkX, chunkZ);
            }
        },
        BASTION_REMNANT(ResourceLocation.fromNamespaceAndPath(MINECRAFT_NAMESPACE, "bastion_remnant"), "structure.biomfinder.bastion_remnant", 224) {
            @Override
            protected Optional<BlockPos> detect(ClientLevel level, int chunkX, int chunkZ) {
                return findBastionSignature(level, chunkX, chunkZ);
            }
        };

        private static final Map<ResourceLocation, StructureType> BY_ID = new HashMap<>();

        static {
            for (StructureType structureType : values()) {
                BY_ID.put(structureType.id, structureType);
            }
        }

        private final ResourceLocation id;
        private final String translationKey;
        private final int mergeRadiusBlocks;

        StructureType(ResourceLocation id, String translationKey, int mergeRadiusBlocks) {
            this.id = id;
            this.translationKey = translationKey;
            this.mergeRadiusBlocks = mergeRadiusBlocks;
        }

        protected abstract Optional<BlockPos> detect(ClientLevel level, int chunkX, int chunkZ);

        private ResourceLocation id() {
            return id;
        }

        private String displayName() {
            return Component.translatable(translationKey).getString();
        }

        private static String displayName(ResourceLocation structureId) {
            StructureType structureType = BY_ID.get(structureId);
            return structureType == null ? structureId.toString() : structureType.displayName();
        }

        private static int mergeRadiusFor(ResourceLocation structureId) {
            StructureType structureType = BY_ID.get(structureId);
            return structureType == null ? 96 : structureType.mergeRadiusBlocks;
        }
    }
}