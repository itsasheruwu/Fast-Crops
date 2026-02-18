# Changelog

All notable user-facing changes are documented in this file.

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
