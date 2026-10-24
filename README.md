# Altered Aether

A NeoForge addon for [The Aether](https://www.curseforge.com/minecraft/mc-mods/aether).

- Minecraft 1.21.1
- NeoForge 21.1.264 or later
- The Aether 1.5.10 or later
- Aeroblender 1.0.0 or later (and TerraBlender)
- Required on both client and server

## Development

The Aether and the mods it needs are not committed. Copy these from the CurseForge instance's `mods` folder into `libs/`:

- `aether-1.21.1-1.5.10-neoforge.jar`
- `aeroblender-1.21.1-1.0.0-neoforge.jar`
- `TerraBlender-neoforge-1.21.1-4.1.0.8.jar`
- `accessories-neoforge-1.1.0-beta.54+1.21.1.jar`
- `owo-lib-neoforge-0.12.15.6-beta.1+1.21.jar`

They are compiled against, and copied into `run/mods` (and `run-server/mods`) before each launch. Any other mods to play-test with can be dropped into `run/mods` by hand.

- `.\run-client.ps1` or `.\gradlew runClient` launches the dev client.
- `.\gradlew runServer` launches a dev server in `run-server/`.
- `.\gradlew build` builds the jar into `build/libs/`.
