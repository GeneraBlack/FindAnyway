package de.gener.biomfinder.client;

import de.gener.biomfinder.BiomFinderMod;
import java.util.Objects;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

@SuppressWarnings("null")
public final class BiomeDirectionOverlay {
    private static final int BACKGROUND_COLOR = 0x90000000;
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int DETAIL_COLOR = 0xFFD37A;
    private static final int DIMENSION_COLOR = 0xFFC8C8C8;
    private static final ResourceLocation LAYER_ID = ResourceLocation.fromNamespaceAndPath(BiomFinderMod.MOD_ID, "target_overlay");

    private final NavigationTargetManager targetManager = NavigationTargetManager.getInstance();

    public void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER_ID, this::render);
    }

    private void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null || minecraft.level == null) {
            return;
        }

        LocalPlayer player = Objects.requireNonNull(minecraft.player);
        ClientLevel level = Objects.requireNonNull(minecraft.level);

        BiomeFinderClientConfig.OverlaySettings settings = BiomeFinderClientConfig.get().overlay();
        if (!settings.enabled()) {
            return;
        }

        NavigationTargetManager.Target target = targetManager.getTarget();
        if (target == null) {
            return;
        }

        Font font = minecraft.font;
        boolean sameDimension = target.dimensionId().equals(level.dimension().location());
        Component title = Component.translatable("overlay.biomfinder.target", target.displayName());
        Component detail = sameDimension
            ? Component.translatable(
                "overlay.biomfinder.target_detail",
                directionArrow(player.getYRot(), player.getX(), player.getZ(), target.targetPos()),
                horizontalDistance(player.getX(), player.getZ(), target.targetPos()),
                target.targetPos().getX(),
                target.targetPos().getZ()
            )
            : Component.translatable("overlay.biomfinder.target_other_dimension", target.dimensionId().toString());

        int boxWidth = Math.max(font.width(title), font.width(detail)) + 12;
        int boxHeight = 28;
        float scale = settings.scale();
        int scaledWidth = Mth.ceil(boxWidth * scale);
        int scaledHeight = Mth.ceil(boxHeight * scale);
        int guiWidth = minecraft.getWindow().getGuiScaledWidth();
        int guiHeight = minecraft.getWindow().getGuiScaledHeight();
        BiomeFinderClientConfig.OverlayAnchor anchor = settings.anchor();

        int left = (guiWidth - scaledWidth) / 2;
        if (anchor.isLeft()) {
            left = 0;
        } else if (anchor.isRight()) {
            left = guiWidth - scaledWidth;
        }

        int top = (guiHeight - scaledHeight) / 2;
        if (anchor.isTop()) {
            top = 0;
        } else if (anchor.isBottom()) {
            top = guiHeight - scaledHeight;
        }

        left += settings.offsetX();
        top += settings.offsetY();

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(left, top, 0.0F);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        if (settings.showBackground()) {
            guiGraphics.fill(0, 0, boxWidth, boxHeight, BACKGROUND_COLOR);
        }
        guiGraphics.drawString(font, title, 6, 5, TEXT_COLOR);
        guiGraphics.drawString(font, detail, 6, 16, sameDimension ? DETAIL_COLOR : DIMENSION_COLOR);
        guiGraphics.pose().popPose();
    }

    private int horizontalDistance(double playerX, double playerZ, net.minecraft.core.BlockPos targetPos) {
        double deltaX = targetPos.getX() + 0.5D - playerX;
        double deltaZ = targetPos.getZ() + 0.5D - playerZ;
        return Mth.floor(Math.hypot(deltaX, deltaZ));
    }

    private String directionArrow(float playerYaw, double playerX, double playerZ, net.minecraft.core.BlockPos targetPos) {
        int distance = horizontalDistance(playerX, playerZ, targetPos);
        if (distance <= 8) {
            return "◎";
        }

        double deltaX = targetPos.getX() + 0.5D - playerX;
        double deltaZ = targetPos.getZ() + 0.5D - playerZ;
        double bearing = Math.toDegrees(Math.atan2(-deltaX, deltaZ));
        float relative = Mth.wrapDegrees((float) (bearing - playerYaw));
        int sector = Mth.floor((relative + 180.0F + 22.5F) / 45.0F) & 7;

        return switch (sector) {
            case 0 -> "↓";
            case 1 -> "↙";
            case 2 -> "←";
            case 3 -> "↖";
            case 4 -> "↑";
            case 5 -> "↗";
            case 6 -> "→";
            default -> "↘";
        };
    }
}