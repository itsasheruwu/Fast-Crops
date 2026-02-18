# Fast Crops

Fast Crops is a Paper plugin for Minecraft that accelerates only crop and sapling growth (plus optional bamboo/sugar cane/cactus), using a tracked-block model designed for large servers.

## Release notes
- See `CHANGELOG.md` for the full release history and consolidated `v1.0.0` notes.

## Compatibility
- Paper `1.21.11`
- Java `21`
- Geyser-Spigot compatible (server-side only, no client mods, packets, or resource packs required)

## Install
1. Build the plugin:
   - `./gradlew build` (or `gradle build` if you do not use the wrapper)
2. Copy `build/libs/FastCrops-<version>.jar` into your server `plugins/` folder.
3. Start or restart the Paper server.
4. Edit `plugins/FastCrops/config.yml` as needed.
5. Use `/fastcrops reload` after config changes.

## Commands
- `/fastcrops status`
- `/fastcrops reload`
- `/fastcrops set <world> targetTickSpeed <number>`
- `/fastcrops toggle <world>`

Permission:
- `fastcrops.admin`

`status` is readable by any sender; `reload`, `set`, and `toggle` require `fastcrops.admin`.

## Configuration
Default `config.yml`:

```yaml
enabled: true
vanillaRandomTickSpeed: 3
defaultTargetTickSpeed: 100
intervalTicks: 2
maxBlocksPerTickPerWorld: 2000

worlds:
  world:
    enabled: true
    targetTickSpeed: 100
    maxBlocksPerTick: 2000

include:
  crops: true
  saplings: true
  bamboo: false
  sugarCane: false
  cactus: false

debug: false
```

### Key options
- `vanillaRandomTickSpeed`: baseline random tick speed for multiplier calculations.
- `defaultTargetTickSpeed`: equivalent target speed for worlds without explicit override.
- `intervalTicks`: engine run interval (lower = more frequent updates).
- `maxBlocksPerTickPerWorld`: hard cap of tracked blocks processed per world each run.
- `worlds.<name>`: per-world enable/disable and per-world overrides.
- `include.*`: include or exclude categories from tracking and acceleration.

## Performance notes
- Fast Crops never scans the whole world each tick.
- It tracks only relevant growables via events and chunk lifecycle:
  - `ChunkLoadEvent` one-time chunk scan
  - `ChunkUnloadEvent` chunk-local cleanup
  - Block change events keep tracked sets accurate
- Processing is budgeted per world using a rolling cursor over tracked blocks for fair distribution.
- Recommended tuning:
  - Large networks: raise `intervalTicks` (e.g., 3-5) and cap per-world `maxBlocksPerTick`.
  - Small servers: keep `intervalTicks` at 1-2 for snappier growth.

## Implementation behavior
- Target tick speed uses random-tick-accurate per-block math:
  - `extraTickSpeed = max(0, targetTickSpeed - vanillaRandomTickSpeed)`
  - `extraPerRun = (extraTickSpeed / 4096) * intervalTicks`
  - floor plus fractional chance for +1 attempt.
- Growth is probabilistic and step-based (no forced instant max age).
- Sapling acceleration uses bonemeal application API to preserve vanilla tree events/cancellations.
- Crop/manual state changes are guarded through `BlockGrowEvent` checks.

## Auto-update
- Fast Crops can check GitHub releases on startup and automatically download updates to the server `plugins/update` folder.
- Controlled by `autoUpdate` config:
  - `enabled`
  - `checkOnStartup`
  - `downloadOnUpdate`
  - `repositoryOwner`
  - `repositoryName`
  - `channel` (`latest` or a specific tag)

## Quick testing steps
1. Start Paper 1.21.11 with this plugin.
2. Plant wheat/carrots/potatoes and saplings in `world`.
3. Run `/fastcrops status` and verify tracked counts rise as chunks load.
4. Compare growth speed against vanilla baseline (`randomTickSpeed=3`).
5. Run `/fastcrops set world targetTickSpeed 50` and verify slower acceleration.
6. Run `/fastcrops toggle world` and confirm growth acceleration stops/resumes.

## License
This project is licensed under **GPL-3.0-only**. See `LICENSE` for the full text.
