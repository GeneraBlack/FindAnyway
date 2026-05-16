package de.gener.findanyway;

import de.gener.findanyway.client.BiomeDirectionOverlay;
import de.gener.findanyway.client.FindAnywayClientConfig;
import de.gener.findanyway.client.FindAnywayClientEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = FindAnywayMod.MOD_ID, dist = Dist.CLIENT)
public final class FindAnywayMod {
    public static final String MOD_ID = FindAnywayPaths.MOD_ID;

    public FindAnywayMod(IEventBus modEventBus, ModContainer modContainer) {
        FindAnywayClientConfig.get();
        FindAnywayClientEvents clientEvents = new FindAnywayClientEvents();
        BiomeDirectionOverlay directionOverlay = new BiomeDirectionOverlay();

        modEventBus.addListener(clientEvents::onRegisterKeyMappings);
        modEventBus.addListener(directionOverlay::onRegisterGuiLayers);
        NeoForge.EVENT_BUS.addListener(clientEvents::onClientTick);
        NeoForge.EVENT_BUS.addListener(clientEvents::onClientLoggingIn);
        NeoForge.EVENT_BUS.addListener(clientEvents::onClientLoggingOut);
        NeoForge.EVENT_BUS.addListener(clientEvents::onRegisterClientCommands);
    }
}
