# FindAnyway

FindAnyway ist ein client-only NeoForge-Mod fuer Minecraft 1.21.1. Er scannt nur bereits geladene Chunks und merkt sich dort entdeckte Biome und erkannte Strukturen.

## Was der Mod kann

- Bereits gesehene Biome in geladenen Chunks erfassen
- Erkannten Strukturen in geladenen Chunks erfassen
- Nach bekannten Biomen in der aktuellen Dimension suchen
- Nach bekannten Strukturen in der aktuellen Dimension suchen
- Die naechste bekannte Position eines Bioms anzeigen
- Die naechste bekannte Position einer erkannten Struktur anzeigen
- Einen einfachen Finder-Screen per Taste oeffnen
- Zwischen Biome- und Struktur-Finder im UI wechseln
- Gefundene Biome und Strukturen pro Welt oder Server zwischen Sessions speichern
- Ein aktives Ziel setzen und per HUD-Pfeil verfolgen
- Optional das aktive Ziel in JourneyMap als Marker plus Zielbereich-Overlay spiegeln, wenn JourneyMap installiert ist

Aktuell werden sieben clientseitig erkennbare Strukturtypen verfolgt:

- Trial Chambers ueber Trial Spawner oder Vault
- Ancient Cities ueber Reinforced Deepslate
- Ocean Monuments ueber eine Prismarine-/Sea-Lantern-Signatur in geladenen Ozean-Chunks
- End Cities ueber Purpur-, End-Rod- und End-Stone-Brick-Signaturen auf den aeusseren End-Inseln
- Woodland Mansions ueber eine Dark-Oak-/Cobblestone-/Carpet-Signatur in Dark-Forest-Chunks; diese Erkennung ist heuristischer und damit anfaelliger fuer False Positives als die anderen Typen
- Nether Fortresses ueber Nether-Brick-/Fence-/Stairs-Signaturen oder erkannte Nether-Wart-Raeume
- Bastions ueber Blackstone-/Gilded-Blackstone-/Gold-/Chain-Signaturen; wie bei allen client-only Heuristiken zaehlt nur, was der Client bereits geladen hat

## Wichtige Grenze

Ohne Seed und ohne Serverhilfe kann ein reiner Client-Mod keine ungeladenen oder nie gesehenen Gebiete exakt durchsuchen. Der Mod ist deshalb bewusst als Entdeckt-Finder gebaut.

Das gilt bei Strukturen noch strenger: Der Mod kann keine serverseitige locate-Funktion ersetzen, sondern nur bereits sichtbar gewordene oder aus geladenen Chunks clientseitig ableitbare Strukturen sammeln.

JourneyMap-Daten werden nicht ueber interne Dateien ausgelesen. Stattdessen nutzt der Mod bei vorhandener Installation die stabile JourneyMap-API und legt fuer das aktive Ziel eigene Marker- und Flaechen-Overlays an.

## Persistenz

Die entdeckten Biome werden pro Singleplayer-Welt oder Multiplayer-Server als JSON unter `config/findanyway/discoveries/` gespeichert. Strukturfunde liegen daneben als separate `*_structures.json`-Dateien.

Beim ersten Start migriert der Mod vorhandene lokale Daten aus `config/biomfinder/` automatisch nach `config/findanyway/`.

## Client-Config

Die HUD-Anzeige laesst sich ueber `config/findanyway/client.json` verschieben und skalieren.

- `overlay.anchor`: Position relativ zum Bildschirm, z. B. `TOP_CENTER`, `TOP_LEFT`, `BOTTOM_RIGHT`
- `overlay.offsetX` und `overlay.offsetY`: Pixel-Offset relativ zum Anchor
- `overlay.scale`: Skalierung der HUD-Box von `0.5` bis `4.0`
- `journeyMap.markerEnabled`: Marker in JourneyMap ein/aus
- `journeyMap.areaEnabled`: Zielbereich-Overlay in JourneyMap ein/aus
- `journeyMap.areaRadiusBlocks`: Radius des JourneyMap-Zielbereichs

Die Datei kann nach Aenderungen per ` /findanyway config reload` neu geladen werden.

## Entwicklung

- Java 21 wird benoetigt
- Starten: `gradlew.bat runClient`
- Standardtaste: `B`
- Befehle: ` /findanyway`, ` /findanyway nearest <query>`, ` /findanyway target <query>`, ` /findanyway target clear`, ` /findanyway structure`, ` /findanyway structure nearest <query>`, ` /findanyway structure target <query>`, ` /findanyway config reload`

## Release-Automation

- Der Workflow [.github/workflows/publish.yml](.github/workflows/publish.yml) veroeffentlicht neue Versionen automatisch auf CurseForge und als GitHub Release.
- Ausgeloest wird er bei einem Tag-Push im Format `v*`, zum Beispiel `v0.1.1`, oder manuell ueber `workflow_dispatch`.
- Erwartete GitHub-Konfiguration: Secrets `CURSEFORGE_TOKEN` und `CURSEFORGE_PROJECT_ID`.
- Der Workflow validiert, dass ein gepushtes Tag zur `mod_version` in [gradle.properties](gradle.properties) passt.

## Lizenz

MIT
