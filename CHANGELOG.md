# Changelog

All notable user-facing changes are documented in this file.

## v1.1.1 - 2026-03-06

### Added
- New config option: `include.hoppers` (default `false`) to enable hopper acceleration.
- Hopper acceleration listener that scales hopper transfer cooldown from world `targetTickSpeed` and `vanillaRandomTickSpeed` (vanilla `8` ticks, minimum `1` tick).

### Changed
- Rebranded plugin/project naming from Fast Crops to Fast Things for runtime metadata and docs.
- Primary admin command is now `/fastthings` with `/fthings` alias.
- Legacy command aliases `/fastcrops` and `/fcrops` remain supported for compatibility.
- Primary permission is now `fastthings.admin`, with legacy `fastcrops.admin` still accepted.
- `/fastthings status` now reports `include.hoppers`.
- Default auto-update repository name now targets `Fast-Things`.

## v1.1.0 - 2026-02-24

### Added
- Extended tripwire support for hook-to-hook lines beyond vanilla distance when configured.
- New global config option: `tripwire.maxLength` (default `69`, clamped to `40..256`).
- Tripwire extension logic for common trigger sources (player/entity interaction and projectile hits on tripwire).
- Extended tripwire state management with short power windows and retrigger extension handling.

### Changed
- `/fastcrops status` now reports `tripwire.maxLength`.
- Updated default config and README docs to include extended tripwire behavior and limits.

## v1.0.0 - 2026-02-16

### Added
- Initial Fast Crops release for Paper `1.21.11` on Java `21`.
- Targeted growth acceleration for crops and saplings, with optional bamboo, sugar cane, and cactus support.
- Per-world controls and tuning via config (`enabled`, target tick speed, and max blocks per tick).
- Admin command set: `/fastcrops status`, `/fastcrops reload`, `/fastcrops set`, and `/fastcrops toggle`.
- Auto-update system that can check GitHub releases on startup and download updates to `plugins/update`.

### Changed
- Reworked growth math to be random-tick accurate instead of a coarse approximation.
- Compensated growth attempts for per-tick processing budgets so configured speed is maintained more consistently.
- Expanded plugin metadata and command registration to reduce command conflicts and improve compatibility.
- Standardized command aliasing (`/fcrops`) and permission behavior for operational commands.

### Fixed
- Fixed command accessibility so status remains readable while admin operations enforce `fastcrops.admin`.
- Fixed startup instability by replacing thread-local random usage with `java.util.Random` in growth processing.
- Removed and reverted a temporary `fastcropsadmin` command path after conflict-safe command cleanup.
