# FindAnyway

FindAnyway is a client-only finder mod for Minecraft 1.21.1 on NeoForge. It does not use server-side commands, world seeds, or hidden map data. Instead, it remembers biomes and structures that your client has already discovered in loaded chunks and helps you navigate back to them.

## What It Does

- Tracks biomes you have already seen in loaded chunks
- Tracks supported structures your client can detect from loaded chunks
- Lets you search known biome and structure entries in-game
- Shows the nearest known match in the current dimension
- Lets you set an active target and follow it with a HUD direction overlay
- Optionally mirrors the active target into JourneyMap as a marker and area overlay
- Saves discoveries per singleplayer world or multiplayer server

## Supported Structures

FindAnyway currently detects these structure types from client-visible chunk data:

- Trial Chambers
- Ancient Cities
- Ocean Monuments
- End Cities
- Woodland Mansions
- Nether Fortresses
- Bastion Remnants

## Important Limitation

FindAnyway is intentionally discovery-based. It cannot locate chunks that were never loaded by your client, and it cannot replace server-side locate commands. If your client has not seen a biome or structure yet, the mod cannot know where it is.

## JourneyMap Integration

If JourneyMap is installed, FindAnyway can create its own marker and target-area overlay for the currently selected destination. This uses the JourneyMap API directly and remains optional.

## Why Use It

FindAnyway is useful when you want a lightweight, client-only way to remember interesting places you have already explored and quickly return to them later, without requiring server support.
