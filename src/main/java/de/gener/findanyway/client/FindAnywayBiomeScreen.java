package de.gener.findanyway.client;

import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

@SuppressWarnings({"null"})
public final class FindAnywayBiomeScreen extends Screen {
    private static final int ROW_HEIGHT = 14;
    private static final int LIST_TOP = 60;
    private static final int PANEL_GAP = 6;
    private static final int DETAILS_PANEL_HEIGHT = 38;
    private static final int PANEL_BORDER_COLOR = 0xFF5A5A64;
    private static final int PANEL_FILL_COLOR = 0xF0141418;
    private static final int LIST_ROW_SELECTED_COLOR = 0xAA5A7FD8;
    private static final int LIST_ROW_HOVER_COLOR = 0x66404040;

    private final BiomeTracker tracker;
    private final StructureTracker structureTracker;
    private final NavigationTargetManager targetManager;

    private EditBox searchBox;
    private Button targetButton;
    private Button shareSelectedButton;
    private List<BiomeTracker.BiomeMatch> visibleBiomes = List.of();
    private BiomeTracker.BiomeMatch selectedBiome;
    private int scrollOffset;

    public FindAnywayBiomeScreen(BiomeTracker tracker, StructureTracker structureTracker, NavigationTargetManager targetManager) {
        super(Component.translatable("screen.findanyway.biome.title"));
        this.tracker = tracker;
        this.structureTracker = structureTracker;
        this.targetManager = targetManager;
    }

    @Override
    protected void init() {
        int contentWidth = Math.min(420, this.width - 40);
        int left = (this.width - contentWidth) / 2;
        int buttonGap = 5;
        int buttonWidth = (contentWidth - (buttonGap * 4)) / 5;
        int buttonY = this.height - 28;

        this.searchBox = new EditBox(this.font, left, 28, contentWidth, 20, Component.translatable("screen.findanyway.biome.search"));
        this.searchBox.setHint(Component.translatable("screen.findanyway.biome.search_hint"));
        this.searchBox.setResponder(value -> {
            this.scrollOffset = 0;
            refreshMatches();
        });
        this.addRenderableWidget(this.searchBox);

        this.targetButton = this.addRenderableWidget(Button.builder(Component.translatable("screen.findanyway.biome.target"), button -> toggleTarget())
            .bounds(left, buttonY, buttonWidth, 20)
            .build());
        this.shareSelectedButton = this.addRenderableWidget(Button.builder(Component.translatable("screen.findanyway.biome.send_selected"), button -> shareSelected())
            .bounds(left + buttonWidth + buttonGap, buttonY, buttonWidth, 20)
            .build());
        this.addRenderableWidget(Button.builder(Component.translatable("screen.findanyway.biome.send_current"), button -> shareCurrent())
            .bounds(left + (buttonWidth + buttonGap) * 2, buttonY, buttonWidth, 20)
            .build());
        this.addRenderableWidget(Button.builder(Component.translatable("screen.findanyway.biome.open_structures"), button -> openStructureScreen())
            .bounds(left + (buttonWidth + buttonGap) * 3, buttonY, buttonWidth, 20)
            .build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
            .bounds(left + (buttonWidth + buttonGap) * 4, buttonY, buttonWidth, 20)
            .build());

        setInitialFocus(this.searchBox);
        refreshMatches();
    }

    @Override
    public void tick() {
        super.tick();
        refreshMatches();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int contentWidth = Math.min(420, this.width - 40);
        int left = (this.width - contentWidth) / 2;
        int right = left + contentWidth;
        int listTop = getListTop();
        int listBottom = getListBottom();
        int detailsTop = getDetailsTop();
        int detailsBottom = getDetailsBottom();

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
        guiGraphics.drawString(this.font, getStatusLine(), left, 52, 0xD0D0D0);
        drawPanel(guiGraphics, left, listTop, right, listBottom);
        drawPanel(guiGraphics, left, detailsTop, right, detailsBottom);

        renderList(guiGraphics, mouseX, mouseY, left, right, listTop);
        renderSelectionDetails(guiGraphics, left + 6, detailsTop + 5);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int clickedIndex = getEntryIndexAt(mouseX, mouseY);
            if (clickedIndex >= 0 && clickedIndex < this.visibleBiomes.size()) {
                this.selectedBiome = this.visibleBiomes.get(clickedIndex);
                updateButtons();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseInsideList(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        int maxOffset = Math.max(0, this.visibleBiomes.size() - getVisibleRows());
        if (scrollY > 0.0D) {
            this.scrollOffset = Math.max(0, this.scrollOffset - 1);
        } else if (scrollY < 0.0D) {
            this.scrollOffset = Math.min(maxOffset, this.scrollOffset + 1);
        }

        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            shareSelected();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void refreshMatches() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            this.visibleBiomes = List.of();
            this.selectedBiome = null;
            updateButtons();
            return;
        }

        this.visibleBiomes = tracker.getVisibleBiomes(
            Objects.requireNonNull(minecraft.level),
            Objects.requireNonNull(minecraft.player).blockPosition(),
            this.searchBox == null ? "" : this.searchBox.getValue()
        );
        if (this.selectedBiome != null) {
            this.selectedBiome = this.visibleBiomes.stream()
                .filter(match -> match.biomeId().equals(this.selectedBiome.biomeId()))
                .findFirst()
                .orElse(null);
        }
        if (this.selectedBiome == null && !this.visibleBiomes.isEmpty()) {
            this.selectedBiome = this.visibleBiomes.get(0);
        }

        int maxOffset = Math.max(0, this.visibleBiomes.size() - getVisibleRows());
        this.scrollOffset = Math.min(this.scrollOffset, maxOffset);
        updateButtons();
    }

    private void updateButtons() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean hasSelection = this.selectedBiome != null;

        if (this.targetButton != null) {
            this.targetButton.active = hasSelection;
            boolean selectedIsTarget = hasSelection
                && minecraft.level != null
                && this.targetManager.isTrackingBiome(minecraft.level.dimension().location(), this.selectedBiome.biomeId());
            this.targetButton.setMessage(Component.translatable(selectedIsTarget ? "screen.findanyway.biome.target_clear" : "screen.findanyway.biome.target"));
        }

        if (this.shareSelectedButton != null) {
            this.shareSelectedButton.active = hasSelection;
        }
    }

    private void renderList(GuiGraphics guiGraphics, int mouseX, int mouseY, int left, int right, int listTop) {
        if (this.visibleBiomes.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("screen.findanyway.biome.none"), this.width / 2, listTop + 10, 0xAAAAAA);
            return;
        }

        int rows = getVisibleRows();
        int lineWidth = right - left - 8;

        for (int row = 0; row < rows; row++) {
            int entryIndex = this.scrollOffset + row;
            if (entryIndex >= this.visibleBiomes.size()) {
                break;
            }

            int rowY = listTop + 4 + row * ROW_HEIGHT;
            BiomeTracker.BiomeMatch match = this.visibleBiomes.get(entryIndex);
            boolean hovered = mouseX >= left && mouseX <= right && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            boolean selected = match.equals(this.selectedBiome);

            if (selected) {
                guiGraphics.fill(left + 2, rowY - 1, right - 2, rowY + ROW_HEIGHT - 1, LIST_ROW_SELECTED_COLOR);
            } else if (hovered) {
                guiGraphics.fill(left + 2, rowY - 1, right - 2, rowY + ROW_HEIGHT - 1, LIST_ROW_HOVER_COLOR);
            }

            String line = formatEntry(match);
            guiGraphics.drawString(this.font, this.font.plainSubstrByWidth(line, lineWidth), left + 4, rowY + 2, selected ? 0xFFFFFF : 0xE0E0E0);
        }
    }

    private void renderSelectionDetails(GuiGraphics guiGraphics, int left, int y) {
        guiGraphics.drawString(this.font, Component.translatable("screen.findanyway.biome.limit"), left, y, 0xB0B0B0);
        if (this.selectedBiome == null) {
            guiGraphics.drawString(this.font, Component.translatable("screen.findanyway.biome.selected_none"), left, y + 12, 0x909090);
        } else {
            var pos = this.selectedBiome.nearestPos();
            guiGraphics.drawString(
                this.font,
                Component.translatable(
                    "screen.findanyway.biome.selected",
                    this.selectedBiome.biomeId().toString(),
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    this.selectedBiome.sightings(),
                    this.selectedBiome.roundedDistance()
                ),
                left,
                y + 12,
                0xFFFFFF
            );
        }

        renderActiveTarget(guiGraphics, left, y + 24);
    }

    private String getStatusLine() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return Component.translatable("commands.findanyway.no_world").getString();
        }

        String currentBiome = tracker.getCurrentSurfaceBiome(Objects.requireNonNull(minecraft.level), Objects.requireNonNull(minecraft.player).blockPosition())
            .map(Object::toString)
            .orElse("-");

        return Component.translatable(
            "screen.findanyway.biome.status",
            currentBiome,
            tracker.getKnownBiomeCount(minecraft.level),
            this.visibleBiomes.size()
        ).getString();
    }

    private String formatEntry(BiomeTracker.BiomeMatch match) {
        var pos = match.nearestPos();
        return "%s  @ %d %d %d  (%dm)".formatted(
            match.biomeId(),
            pos.getX(),
            pos.getY(),
            pos.getZ(),
            match.roundedDistance()
        );
    }

    private int getVisibleRows() {
        return Math.max(1, (getListBottom() - getListTop() - 8) / ROW_HEIGHT);
    }

    private boolean isMouseInsideList(double mouseX, double mouseY) {
        int contentWidth = Math.min(420, this.width - 40);
        int left = (this.width - contentWidth) / 2;
        int right = left + contentWidth;
        int listTop = getListTop();
        int listBottom = getListBottom();
        return mouseX >= left && mouseX <= right && mouseY >= listTop && mouseY <= listBottom;
    }

    private int getEntryIndexAt(double mouseX, double mouseY) {
        if (!isMouseInsideList(mouseX, mouseY)) {
            return -1;
        }

        int relativeRow = ((int) mouseY - 64) / ROW_HEIGHT;
        if (relativeRow < 0) {
            return -1;
        }

        return this.scrollOffset + relativeRow;
    }

    private int getListTop() {
        return LIST_TOP;
    }

    private int getButtonY() {
        return this.height - 28;
    }

    private int getDetailsTop() {
        return getButtonY() - PANEL_GAP - DETAILS_PANEL_HEIGHT;
    }

    private int getDetailsBottom() {
        return getButtonY() - PANEL_GAP;
    }

    private int getListBottom() {
        return getDetailsTop() - PANEL_GAP;
    }

    private void drawPanel(GuiGraphics guiGraphics, int left, int top, int right, int bottom) {
        guiGraphics.fill(left, top, right, bottom, PANEL_BORDER_COLOR);
        guiGraphics.fill(left + 1, top + 1, right - 1, bottom - 1, PANEL_FILL_COLOR);
    }

    private void shareSelected() {
        if (this.selectedBiome == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        var pos = this.selectedBiome.nearestPos();
        Objects.requireNonNull(minecraft.player).sendSystemMessage(Component.translatable(
            "commands.findanyway.nearest",
            this.selectedBiome.biomeId().toString(),
            pos.getX(),
            pos.getY(),
            pos.getZ(),
            this.selectedBiome.roundedDistance()
        ));
    }

    private void shareCurrent() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        tracker.getCurrentSurfaceBiome(Objects.requireNonNull(minecraft.level), Objects.requireNonNull(minecraft.player).blockPosition()).ifPresent(biomeId -> {
            var pos = Objects.requireNonNull(minecraft.player).blockPosition();
            Objects.requireNonNull(minecraft.player).sendSystemMessage(Component.translatable(
                "commands.findanyway.current",
                biomeId.toString(),
                pos.getX(),
                pos.getY(),
                pos.getZ()
            ));
        });
    }

    private void openStructureScreen() {
        Minecraft.getInstance().setScreen(new StructureFinderScreen(this.tracker, this.structureTracker, this.targetManager));
    }

    private void toggleTarget() {
        if (this.selectedBiome == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        if (this.targetManager.isTrackingBiome(Objects.requireNonNull(minecraft.level).dimension().location(), this.selectedBiome.biomeId())) {
            this.targetManager.clear();
            Objects.requireNonNull(minecraft.player).sendSystemMessage(Component.translatable("commands.findanyway.target_cleared"));
        } else {
            this.targetManager.setBiomeTarget(Objects.requireNonNull(minecraft.level).dimension().location(), this.selectedBiome);
            var pos = this.selectedBiome.nearestPos();
            Objects.requireNonNull(minecraft.player).sendSystemMessage(Component.translatable(
                "commands.findanyway.target_set",
                this.selectedBiome.biomeId().toString(),
                pos.getX(),
                pos.getY(),
                pos.getZ()
            ));
        }

        updateButtons();
    }

    private void renderActiveTarget(GuiGraphics guiGraphics, int left, int y) {
        NavigationTargetManager.Target target = this.targetManager.getTarget();
        if (target == null) {
            guiGraphics.drawString(this.font, Component.translatable("screen.findanyway.target.none"), left, y, 0x909090);
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        boolean sameDimension = minecraft.level != null && target.dimensionId().equals(minecraft.level.dimension().location());
        Component line;
        if (sameDimension && minecraft.player != null) {
            int distance = Mth.floor(Math.hypot(
                target.targetPos().getX() + 0.5D - minecraft.player.getX(),
                target.targetPos().getZ() + 0.5D - minecraft.player.getZ()
            ));
            line = Component.translatable(
                "screen.findanyway.target.active",
                target.displayName(),
                target.targetPos().getX(),
                target.targetPos().getY(),
                target.targetPos().getZ(),
                distance
            );
        } else {
            line = Component.translatable(
                "screen.findanyway.target.other_dimension",
                target.displayName(),
                target.dimensionId().toString(),
                target.targetPos().getX(),
                target.targetPos().getY(),
                target.targetPos().getZ()
            );
        }

        guiGraphics.drawString(this.font, line, left, y, 0xD9C07A);
    }
}
