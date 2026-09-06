package de.gener.findanyway.client;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

public final class NavigationTargetManager {
    private static final NavigationTargetManager INSTANCE = new NavigationTargetManager();

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private Target target;

    private NavigationTargetManager() {
    }

    public static NavigationTargetManager getInstance() {
        return INSTANCE;
    }

    public Target getTarget() {
        return target;
    }

    public void clear() {
        updateTarget(null);
    }

    public void setBiomeTarget(Identifier dimensionId, BiomeTracker.BiomeMatch match) {
        updateTarget(new Target(dimensionId, TargetKind.BIOME, match.biomeId(), match.biomeId().toString(), match.nearestPos().immutable()));
    }

    public void setStructureTarget(Identifier dimensionId, StructureTracker.StructureMatch match) {
        updateTarget(new Target(dimensionId, TargetKind.STRUCTURE, match.structureId(), match.displayName(), match.anchorPos().immutable()));
    }

    public boolean isTrackingBiome(Identifier dimensionId, Identifier biomeId) {
        return target != null
            && target.kind() == TargetKind.BIOME
            && target.dimensionId().equals(dimensionId)
            && target.targetId().equals(biomeId);
    }

    public boolean isTrackingStructure(Identifier dimensionId, StructureTracker.StructureMatch match) {
        return target != null
            && target.kind() == TargetKind.STRUCTURE
            && target.dimensionId().equals(dimensionId)
            && target.targetId().equals(match.structureId())
            && target.targetPos().equals(match.anchorPos());
    }

    public void refreshBiomeTarget(ClientLevel level, BlockPos reference, BiomeTracker tracker) {
        Target current = this.target;
        if (current == null || current.kind() != TargetKind.BIOME || !current.dimensionId().equals(level.dimension().identifier())) {
            return;
        }

        tracker.getVisibleBiomes(level, reference, current.targetId().toString()).stream()
            .filter(match -> match.biomeId().equals(current.targetId()))
            .findFirst()
            .ifPresent(match -> updateTarget(new Target(
                current.dimensionId(),
                current.kind(),
                current.targetId(),
                current.displayName(),
                match.nearestPos().immutable()
            )));
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
        listener.onTargetChanged(target);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public void reapplyCurrentTarget() {
        for (Listener listener : listeners) {
            listener.onTargetChanged(this.target);
        }
    }

    private void updateTarget(Target nextTarget) {
        if (Objects.equals(this.target, nextTarget)) {
            return;
        }

        this.target = nextTarget;
        for (Listener listener : listeners) {
            listener.onTargetChanged(this.target);
        }
    }

    public interface Listener {
        void onTargetChanged(Target target);
    }

    public enum TargetKind {
        BIOME,
        STRUCTURE
    }

    public record Target(Identifier dimensionId, TargetKind kind, Identifier targetId, String displayName, BlockPos targetPos) {
    }
}