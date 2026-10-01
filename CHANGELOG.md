# Changelog

All notable changes to this project will be documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/) and this project adheres to [Semantic Versioning](https://semver.org/).

## [1.3.0] - 2026-10-01

### Features
- **lightoverlay**: client-side override of NEI's F7 mob-spawn overlay light threshold, per dimension. Outside the Nether the overlay marks blocks at light level `lightOverlay.maxLightNormal` (default `0`); in the Nether at `lightOverlay.maxLightNether` (default `7`, NEI's own value). Implemented as a client-only, non-required Mixin on `codechicken.nei.WorldOverlayRenderer#getSpawnMode`, computed at runtime from the client's dimension plus the config snapshot; a client without NEI skips it instead of crashing. Turned off (`enableLightOverlayTweak=false`) the overlay is byte-identical to NEI. Only clients that install Patchee see the change; the server is not involved.
- **tests**: JUnit 5 coverage for the pure threshold maths (`LightOverlayThresholdTest`).

## [1.2.0] - 2026-09-30

### Features
- **veinconfig**: server-enforced VeinMiner limits (sand/clay/gravel, cap 64, radius 20) + docs + CI smoke coverage ([`c59ba94`](https://github.com/koreaeatsrice/patchee/commit/c59ba944745683c0edbb5f42dd14eb157e946614))

### Bug Fixes
- **build**: store gradlew.bat normalised (clean checkouts; no more '-dirty' version strings) ([`4579846`](https://github.com/koreaeatsrice/patchee/commit/457984676d2bef76022adfe4fafb71ab69c668c4))

### Maintenance & CI
- **changelog**: remove the stale v1.2.0 section ahead of the clean re-cut ([`3d40556`](https://github.com/koreaeatsrice/patchee/commit/3d405567072ba5f8a2ac9fef6cf297a37522df83))
- **release**: v1.2.0 ([`f1d4228`](https://github.com/koreaeatsrice/patchee/commit/f1d4228a2b5976cd7b406c07ab673127be085eec))

### Contributors

- uriel-runner[bot]

**Full changelog**: https://github.com/koreaeatsrice/patchee/compare/v1.1.0...v1.2.0
