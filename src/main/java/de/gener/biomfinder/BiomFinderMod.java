package de.gener.biomfinder;

import de.gener.biomfinder.client.BiomeDirectionOverlay;
import de.gener.biomfinder.client.BiomeFinderClientConfig;
import de.gener.biomfinder.client.BiomeFinderClientEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = BiomFinderMod.MOD_ID, dist = Dist.CLIENT)
public final class BiomFinderMod {
    public static final String MOD_ID = FindAnywayPaths.MOD_ID;

    public BiomFinderMod(IEventBus modEventBus, ModContainer modContainer) {
        BiomeFinderClientConfig.get();
        BiomeFinderClientEvents clientEvents = new BiomeFinderClientEvents();
        BiomeDirectionOverlay directionOverlay = new BiomeDirectionOverlay();

        modEventBus.addListener(clientEvents::onRegisterKeyMappings);
        modEventBus.addListener(directionOverlay::onRegisterGuiLayers);
        NeoForge.EVENT_BUS.addListener(clientEvents::onClientTick);
        NeoForge.EVENT_BUS.addListener(clientEvents::onClientLoggingIn);
        NeoForge.EVENT_BUS.addListener(clientEvents::onClientLoggingOut);
        NeoForge.EVENT_BUS.addListener(clientEvents::onRegisterClientCommands);
    }
}
