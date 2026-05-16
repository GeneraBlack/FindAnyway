package de.gener.biomfinder.integration.journeymap;

import de.gener.biomfinder.BiomFinderMod;
import de.gener.biomfinder.client.BiomeFinderClientConfig;
import de.gener.biomfinder.client.NavigationTargetManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import journeymap.api.v2.client.display.DisplayType;
import journeymap.api.v2.client.display.MarkerOverlay;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.model.MapImage;
import journeymap.api.v2.client.model.ShapeProperties;
import journeymap.api.v2.client.model.TextProperties;
import journeymap.api.v2.client.util.PolygonHelper;
import journeymap.api.v2.common.JourneyMapPlugin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

@JourneyMapPlugin(apiVersion = "2.0.0")
@SuppressWarnings("null")
public final class BiomeFinderJourneyMapPlugin implements IClientPlugin, NavigationTargetManager.Listener {
    private static final String OVERLAY_GROUP = "FindAnyway";
    private static final Logger LOGGER = LogUtils.getLogger();

    private IClientAPI journeyMapApi;
    private MarkerOverlay currentMarkerOverlay;
    private PolygonOverlay currentAreaOverlay;

    @Override
    public void initialize(final IClientAPI journeyMapApi) {
        this.journeyMapApi = journeyMapApi;
        NavigationTargetManager.getInstance().addListener(this);
    }

    @Override
    public String getModId() {
        return BiomFinderMod.MOD_ID;
    }

    @Override
    public void onTargetChanged(NavigationTargetManager.Target target) {
        if (journeyMapApi == null) {
            return;
        }

        clearJourneyMapVisuals();

        if (target == null) {
            return;
        }

        BiomeFinderClientConfig.JourneyMapSettings settings = BiomeFinderClientConfig.get().journeyMap();
        ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, target.dimensionId());

        try {
            if (settings.markerEnabled() && journeyMapApi.playerAccepts(BiomFinderMod.MOD_ID, DisplayType.Marker)) {
                currentMarkerOverlay = createMarkerOverlay(target, dimensionKey, settings);
                journeyMapApi.show(currentMarkerOverlay);
            }

            if (settings.areaEnabled() && journeyMapApi.playerAccepts(BiomFinderMod.MOD_ID, DisplayType.Polygon)) {
                currentAreaOverlay = createAreaOverlay(target, dimensionKey, settings);
                journeyMapApi.show(currentAreaOverlay);
            }
        } catch (Exception exception) {
            LOGGER.warn("Could not update JourneyMap overlays for the current target.", exception);
            clearJourneyMapVisuals();
        }
    }

    private void clearJourneyMapVisuals() {
        if (currentMarkerOverlay != null) {
            journeyMapApi.remove(currentMarkerOverlay);
            currentMarkerOverlay = null;
        }
        if (currentAreaOverlay != null) {
            journeyMapApi.remove(currentAreaOverlay);
            currentAreaOverlay = null;
        }
    }

    private MarkerOverlay createMarkerOverlay(NavigationTargetManager.Target target, ResourceKey<Level> dimensionKey, BiomeFinderClientConfig.JourneyMapSettings settings) {
        MapImage icon = new MapImage(createMarkerIcon(24));
        icon.centerAnchors().setColor(settings.markerColor());

        MarkerOverlay overlay = new MarkerOverlay(BiomFinderMod.MOD_ID, target.targetPos(), icon);
        overlay.setDimension(dimensionKey)
            .setOverlayGroupName(OVERLAY_GROUP)
            .setLabel(shortTargetLabel(target))
            .setTitle("Target: " + target.displayName());
        overlay.setTextProperties(new TextProperties()
            .setColor(settings.markerColor())
            .setBackgroundColor(0x000000)
            .setBackgroundOpacity(0.45F)
            .setOffsetY(-14)
            .setMinZoom(1));
        return overlay;
    }

    private PolygonOverlay createAreaOverlay(NavigationTargetManager.Target target, ResourceKey<Level> dimensionKey, BiomeFinderClientConfig.JourneyMapSettings settings) {
        int radius = settings.areaRadiusBlocks();
        BlockPos center = target.targetPos();
        BlockPos firstCorner = new BlockPos(center.getX() - radius, center.getY(), center.getZ() - radius);
        BlockPos secondCorner = new BlockPos(center.getX() + radius, center.getY(), center.getZ() + radius);

        PolygonOverlay overlay = new PolygonOverlay(
            BiomFinderMod.MOD_ID,
            dimensionKey,
            new ShapeProperties()
                .setStrokeWidth(2.0F)
                .setStrokeColor(settings.areaStrokeColor())
                .setStrokeOpacity(0.9F)
                .setFillColor(settings.areaFillColor())
                .setFillOpacity(0.20F),
            PolygonHelper.createBlockRect(firstCorner, secondCorner)
        );
        overlay.setOverlayGroupName(OVERLAY_GROUP)
            .setLabel(shortTargetLabel(target))
            .setTitle("Known target area for " + target.displayName());
        overlay.setTextProperties(new TextProperties()
            .setColor(settings.areaStrokeColor())
            .setBackgroundColor(0x000000)
            .setBackgroundOpacity(0.35F)
            .setMinZoom(1)
            .setMaxZoom(5));
        return overlay;
    }

    private String shortTargetLabel(NavigationTargetManager.Target target) {
        String label = target.displayName();
        return label.length() <= 18 ? label : label.substring(0, 18);
    }

    private NativeImage createMarkerIcon(int size) {
        NativeImage image = new NativeImage(size, size, false);
        image.fillRect(0, 0, size, size, 0x00000000);

        int center = size / 2;
        image.fillRect(center - 2, 2, 4, 10, 0xFFFFFFFF);
        image.fillRect(center - 5, 5, 10, 4, 0xFFFFFFFF);
        image.fillRect(center - 4, 12, 8, 4, 0xFFFFFFFF);
        image.fillRect(center - 2, 16, 4, size - 16, 0xFFFFFFFF);

        return image;
    }
}