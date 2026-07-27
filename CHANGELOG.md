# Changelog

All notable changes to EasyAFK are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

> **Release process:** the publish workflow extracts the section whose heading is
> `## [<version>]`, matching `version=` in `gradle.properties`, and uses it as the
> changelog on Modrinth, CurseForge and the GitHub Release. A tag with no matching
> section fails the release before anything is built or uploaded.

## [Unreleased]

## [2.5.0] - 2026-07-27

### Added

- Live AFK duration in the tab list, e.g. `[AFK] Steve (5m 12s)`, refreshed once per second. Controlled by `showAFKDurationInTab` and `afkDurationFormat`. Fabric had no tab list decoration at all before this, so the `[AFK]` prefix now shows there too.
- `invulnerableWhileAFK` (default `true`). Turn it off to keep AFK players vulnerable to mobs, lava and drowning while still shielding them from the fall damage that AFK freezing would otherwise cause.
- Kick messages are now configurable via `msgKickWarning` and `msgKicked`.

### Fixed

- `preventFallDamage` did nothing. Every loader read the flag and then cancelled the damage unconditionally on the next line, so AFK players were immune to all damage regardless of configuration.
- `colorAfkPlayerName` was hardcoded to `#FFFFFF` on all three loaders, so the setting could never take effect.
- Per-player state was keyed by `ServerPlayer`, whose instance is replaced on respawn and dimension change. Entries accumulated for the lifetime of the server and pinned the world graph; combat and damage timestamps were never cleared on disconnect either.
- NeoForge showed a fixed number in the kick warning instead of the actual time remaining.
- NeoForge and Forge now apply config edits on reload rather than only at startup.
- Fabric clamps out-of-range config values and logs invalid ones instead of accepting them silently.

### Changed

- AFK state, damage handling, config schema and colour parsing moved into the shared `common` module. The three loaders no longer carry near-duplicate copies that could drift apart. Existing config files keep loading unchanged; new settings are appended with their defaults.
- Idle time is derived from a single "last activity" timestamp instead of a counter incremented every tick, removing the per-tick bookkeeping.
- Removed four no-op mixins and their registrations.

### Internal

- Added JUnit to `common` with 46 tests covering damage policy, config schema and coercion, hex colour parsing and the AFK tracker.
- Added a Build workflow running the full build and test suite on push and pull request.

## [2.4.0] - 2026-07-23

### Added

- Update checker that reports when a newer version of the mod is available.
- The Fabric jar is now published as Quilt-compatible.

### Internal

- Extracted `Config` into the `common` module.
- Added a GitHub Actions workflow that publishes to Modrinth, CurseForge and GitHub Releases from a version tag.

## [2.3.2] - 2025-12-19

### Fixed

- Players can now use `/afk` while sitting on modded chairs, which previously failed with a "cannot go into AFK whilst jumping" error.
- Auto-AFK now works correctly when sitting on modded chairs.
- False jump events from modded furniture no longer pull players out of AFK.
- Players can no longer mount horses or other rideable entities while AFK.
- Sitting on a chair while AFK is now properly blocked.
- Dismounting from a chair while AFK correctly maintains AFK status.

### Changed

- Manual `/afk` and auto-AFK share a single `canEnterAFK()` validation path.
- Chairs (non-living entities) are treated as valid AFK locations, while rideable entities such as horses, boats and minecarts are blocked.
- Jump handlers filter out false positives from sitting and standing animations using pose checks.

## [2.3.1] - 2025-12-18

### Fixed

- Server crash on shutdown caused by the config system reading values during the unload event. Affected NeoForge and Forge only; Fabric was unaffected.

## [2.3.0] - 2025-12-14

### Added

- Initial release for Forge and Fabric/Quilt.

### Changed

- Migrated to a multiloader project layout.

## [2.2.0] - 2025-12-13

### Added

- NeoForge release.

[Unreleased]: https://github.com/gemsi-mc/EasyAFK/compare/2.5.0...HEAD
[2.5.0]: https://github.com/gemsi-mc/EasyAFK/compare/2.4.0...2.5.0
[2.4.0]: https://github.com/gemsi-mc/EasyAFK/compare/2.3.2...2.4.0
[2.3.2]: https://github.com/gemsi-mc/EasyAFK/compare/2.3.1-hotfix...2.3.2
[2.3.1]: https://github.com/gemsi-mc/EasyAFK/compare/2.3.0...2.3.1-hotfix
[2.3.0]: https://github.com/gemsi-mc/EasyAFK/compare/2.2.0-neoforge...2.3.0
[2.2.0]: https://github.com/gemsi-mc/EasyAFK/releases/tag/2.2.0-neoforge
