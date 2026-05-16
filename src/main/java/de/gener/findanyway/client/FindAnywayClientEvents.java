package de.gener.findanyway.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.blaze3d.platform.InputConstants;
import de.gener.findanyway.FindAnywayMod;
import java.util.Objects;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@SuppressWarnings({"unused", "null"})
public final class FindAnywayClientEvents {
    private static final String QUERY_ARGUMENT = "query";
    private static final String NO_WORLD_KEY = "commands.findanyway.no_world";

    private static final KeyMapping OPEN_FINDER = new KeyMapping(
        "key." + FindAnywayMod.MOD_ID + ".open",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_B,
        "key.categories." + FindAnywayMod.MOD_ID
    );

    private final BiomeTracker tracker = new BiomeTracker();
    private final StructureTracker structureTracker = new StructureTracker();
    private final BiomeTrackerPersistence persistence = new BiomeTrackerPersistence();
    private final StructureTrackerPersistence structurePersistence = new StructureTrackerPersistence();
    private final NavigationTargetManager targetManager = NavigationTargetManager.getInstance();
    private BiomeTrackerPersistence.SessionRef activeSession;
    private int scanCooldown;
    private int saveCooldown;

    public void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_FINDER);
    }

    public void onClientTick(ClientTickEvent.Post event) {
        Objects.requireNonNull(event);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        LocalPlayer player = Objects.requireNonNull(minecraft.player);
        ClientLevel level = Objects.requireNonNull(minecraft.level);

        while (OPEN_FINDER.consumeClick()) {
            minecraft.setScreen(new FindAnywayBiomeScreen(tracker, structureTracker, targetManager));
        }

        scanCooldown++;
        if (scanCooldown >= 20) {
            scanCooldown = 0;
            tracker.scan(level, player);
            structureTracker.scan(level, player);
            targetManager.refreshBiomeTarget(level, player.blockPosition(), tracker);
        }

        saveCooldown++;
        if (activeSession != null && saveCooldown >= 200 && (tracker.isDirty() || structureTracker.isDirty())) {
            saveCooldown = 0;
            persistence.save(activeSession, tracker);
            structurePersistence.save(activeSession, structureTracker);
        }
    }

    public void onClientLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        Objects.requireNonNull(event);
        scanCooldown = 0;
        saveCooldown = 0;
        targetManager.clear();

        Minecraft minecraft = Minecraft.getInstance();
        activeSession = persistence.resolveCurrentSession(minecraft).orElse(null);
        if (activeSession != null) {
            persistence.loadInto(activeSession, tracker);
            structurePersistence.loadInto(activeSession, structureTracker);
        } else {
            tracker.clear();
            structureTracker.clear();
        }
    }

    public void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        Objects.requireNonNull(event);
        if (activeSession != null && (tracker.isDirty() || structureTracker.isDirty())) {
            persistence.save(activeSession, tracker);
            structurePersistence.save(activeSession, structureTracker);
        }
        scanCooldown = 0;
        saveCooldown = 0;
        activeSession = null;
        targetManager.clear();
        tracker.clear();
        structureTracker.clear();
    }

    public void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(FindAnywayMod.MOD_ID)
            .executes(context -> openFinder())
            .then(Commands.literal("current")
                .executes(context -> shareCurrentBiome()))
            .then(Commands.literal("nearest")
                .then(Commands.argument(QUERY_ARGUMENT, StringArgumentType.greedyString())
                    .executes(context -> shareNearestBiome(StringArgumentType.getString(context, QUERY_ARGUMENT)))))
            .then(Commands.literal("structure")
                .executes(context -> openStructureFinder())
                .then(Commands.literal("nearest")
                    .then(Commands.argument(QUERY_ARGUMENT, StringArgumentType.greedyString())
                        .executes(context -> shareNearestStructure(StringArgumentType.getString(context, QUERY_ARGUMENT)))))
                .then(Commands.literal("target")
                    .then(Commands.argument(QUERY_ARGUMENT, StringArgumentType.greedyString())
                        .executes(context -> setStructureTargetFromQuery(StringArgumentType.getString(context, QUERY_ARGUMENT))))))
            .then(Commands.literal("config")
                .then(Commands.literal("reload")
                    .executes(context -> reloadClientConfig())))
            .then(Commands.literal("target")
                .then(Commands.literal("clear")
                    .executes(context -> clearTarget()))
                .then(Commands.argument(QUERY_ARGUMENT, StringArgumentType.greedyString())
                    .executes(context -> setTargetFromQuery(StringArgumentType.getString(context, QUERY_ARGUMENT))))));
    }

    private int openFinder() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new FindAnywayBiomeScreen(tracker, structureTracker, targetManager));
        return 1;
    }

    private int openStructureFinder() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new StructureFinderScreen(tracker, structureTracker, targetManager));
        return 1;
    }

    private int shareCurrentBiome() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            sendClientMessage(Component.translatable(NO_WORLD_KEY));
            return 0;
        }

        LocalPlayer player = Objects.requireNonNull(minecraft.player);
        ClientLevel level = Objects.requireNonNull(minecraft.level);

        return tracker.getCurrentSurfaceBiome(level, player.blockPosition())
            .map(biomeId -> {
                var pos = player.blockPosition();
                sendClientMessage(Component.translatable(
                    "commands.findanyway.current",
                    biomeId.toString(),
                    pos.getX(),
                    pos.getY(),
                    pos.getZ()
                ));
                return 1;
            })
            .orElseGet(() -> {
                sendClientMessage(Component.translatable(NO_WORLD_KEY));
                return 0;
            });
    }

    private int shareNearestBiome(String query) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            sendClientMessage(Component.translatable(NO_WORLD_KEY));
            return 0;
        }

        LocalPlayer player = Objects.requireNonNull(minecraft.player);
        ClientLevel level = Objects.requireNonNull(minecraft.level);

        return tracker.findNearest(level, player.blockPosition(), query)
            .map(match -> {
                var nearest = match.nearestPos();
                sendClientMessage(Component.translatable(
                    "commands.findanyway.nearest",
                    match.biomeId().toString(),
                    nearest.getX(),
                    nearest.getY(),
                    nearest.getZ(),
                    match.roundedDistance()
                ));
                return 1;
            })
            .orElseGet(() -> {
                sendClientMessage(Component.translatable("commands.findanyway.missing", query));
                return 0;
            });
    }

    private int shareNearestStructure(String query) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            sendClientMessage(Component.translatable(NO_WORLD_KEY));
            return 0;
        }

        LocalPlayer player = Objects.requireNonNull(minecraft.player);
        ClientLevel level = Objects.requireNonNull(minecraft.level);

        return structureTracker.findNearest(level, player.blockPosition(), query)
            .map(match -> {
                var anchor = match.anchorPos();
                sendClientMessage(Component.translatable(
                    "commands.findanyway.nearest",
                    match.displayName(),
                    anchor.getX(),
                    anchor.getY(),
                    anchor.getZ(),
                    match.roundedDistance()
                ));
                return 1;
            })
            .orElseGet(() -> {
                sendClientMessage(Component.translatable("commands.findanyway.structure_missing", query));
                return 0;
            });
    }

    private int setTargetFromQuery(String query) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            sendClientMessage(Component.translatable(NO_WORLD_KEY));
            return 0;
        }

        LocalPlayer player = Objects.requireNonNull(minecraft.player);
        ClientLevel level = Objects.requireNonNull(minecraft.level);

        return tracker.findNearest(level, player.blockPosition(), query)
            .map(match -> {
                setTarget(level.dimension().location(), match);
                var nearest = match.nearestPos();
                sendClientMessage(Component.translatable(
                    "commands.findanyway.target_set",
                    match.biomeId().toString(),
                    nearest.getX(),
                    nearest.getY(),
                    nearest.getZ()
                ));
                return 1;
            })
            .orElseGet(() -> {
                sendClientMessage(Component.translatable("commands.findanyway.missing", query));
                return 0;
            });
    }

    private int setStructureTargetFromQuery(String query) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            sendClientMessage(Component.translatable(NO_WORLD_KEY));
            return 0;
        }

        LocalPlayer player = Objects.requireNonNull(minecraft.player);
        ClientLevel level = Objects.requireNonNull(minecraft.level);

        return structureTracker.findNearest(level, player.blockPosition(), query)
            .map(match -> {
                targetManager.setStructureTarget(level.dimension().location(), match);
                var anchor = match.anchorPos();
                sendClientMessage(Component.translatable(
                    "commands.findanyway.target_set",
                    match.displayName(),
                    anchor.getX(),
                    anchor.getY(),
                    anchor.getZ()
                ));
                return 1;
            })
            .orElseGet(() -> {
                sendClientMessage(Component.translatable("commands.findanyway.structure_missing", query));
                return 0;
            });
    }

    private int clearTarget() {
        if (targetManager.getTarget() == null) {
            sendClientMessage(Component.translatable("commands.findanyway.target_cleared"));
            return 0;
        }

        targetManager.clear();
        sendClientMessage(Component.translatable("commands.findanyway.target_cleared"));
        return 1;
    }

    public void setTarget(ResourceLocation dimensionId, BiomeTracker.BiomeMatch match) {
        targetManager.setBiomeTarget(dimensionId, match);
    }

    private int reloadClientConfig() {
        FindAnywayClientConfig config = FindAnywayClientConfig.reload();
        targetManager.reapplyCurrentTarget();
        sendClientMessage(Component.translatable("commands.findanyway.config_reloaded", config.getPath().toString()));
        return 1;
    }

    private void sendClientMessage(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(message);
        }
    }
}
