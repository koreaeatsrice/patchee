# Changelog

All notable changes to this project will be documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/) and this project adheres to [Semantic Versioning](https://semver.org/).

## [1.3.0] - 2026-10-02

### Features
- **lightoverlay**: per-dimension F7 overlay light threshold (client mixin) ([`58adbdf`](https://github.com/koreaeatsrice/patchee/commit/58adbdf4e89fc915259806b31302c3fc2a611c83))

### Bug Fixes
- **build**: normalize gradlew.bat line endings so checkouts stop reporting dirty ([`1b1e2f1`](https://github.com/koreaeatsrice/patchee/commit/1b1e2f1c4692ada7eeebf5862b2a22870fefeca1))
- **security**: remaining review findings (automerge gate, wrapper checksum) + SECURITY-REVIEW update ([`31f473c`](https://github.com/koreaeatsrice/patchee/commit/31f473cc35fa6c55dfb410ebcc4c143a0a9bc6e1))
- **security**: release-workflow command injection + fail-soft event handlers (review findings) ([`ff3e38d`](https://github.com/koreaeatsrice/patchee/commit/ff3e38d609b0c37f70cd922ce9e509f43a650921))
- **changelog**: restore the v1.2.0 section (the previous publish snapshot reverted it) ([`1146f33`](https://github.com/koreaeatsrice/patchee/commit/1146f337a7289966550f2cbbcaceb7ee13409e16))

### Documentation & Wiki
- finish the SECURITY-REVIEW.md sync ([`4309e55`](https://github.com/koreaeatsrice/patchee/commit/4309e5590750f117977b5a3916a8962bcfcaface))
- add SECURITY-REVIEW.md (dependabot triage + CodeQL result) ([`25cd2fe`](https://github.com/koreaeatsrice/patchee/commit/25cd2fec6504e31025cdcb08d8d974690b021209))
- correct the client-side claims — join-optional, out of step without it ([`dc33158`](https://github.com/koreaeatsrice/patchee/commit/dc331581ec139fe62b60eef09025415a09665a2b))
- **readme**: neutral rewrite — Warning/Note disclaimer split, plain wording throughout ([`87b21f9`](https://github.com/koreaeatsrice/patchee/commit/87b21f90378885ce30c77afd0dbdb40927c00adb))

### Maintenance & CI
- **release**: v1.3.0 ([`78bd693`](https://github.com/koreaeatsrice/patchee/commit/78bd6930ccfc4932c5e07fef34192c4ae004d990))
- bump the GTNH convention toolchain 2.0.20 -> 2.0.33 (adopt upstream) ([`ea0ab7e`](https://github.com/koreaeatsrice/patchee/commit/ea0ab7e0c2c0c0dc245d5c449ad253c02be132ea))

### Other Changes
- Update README with project details and emphasis ([`d6396a9`](https://github.com/koreaeatsrice/patchee/commit/d6396a9f313f337b596d46b8dd11897497a9d766))

### Contributors

- uriel-runner[bot]
- koreaeatsrice

**Full changelog**: https://github.com/koreaeatsrice/patchee/compare/v1.2.0...v1.3.0

## [1.3.0] - 2026-10-01

### Features
- **lightoverlay**: per-dimension F7 overlay light threshold (client mixin) ([`58adbdf`](https://github.com/koreaeatsrice/patchee/commit/58adbdf4e89fc915259806b31302c3fc2a611c83))

### Bug Fixes
- **security**: remaining review findings (automerge gate, wrapper checksum) + SECURITY-REVIEW update ([`31f473c`](https://github.com/koreaeatsrice/patchee/commit/31f473cc35fa6c55dfb410ebcc4c143a0a9bc6e1))
- **security**: release-workflow command injection + fail-soft event handlers (review findings) ([`ff3e38d`](https://github.com/koreaeatsrice/patchee/commit/ff3e38d609b0c37f70cd922ce9e509f43a650921))
- **changelog**: restore the v1.2.0 section (the previous publish snapshot reverted it) ([`1146f33`](https://github.com/koreaeatsrice/patchee/commit/1146f337a7289966550f2cbbcaceb7ee13409e16))

### Documentation & Wiki
- finish the SECURITY-REVIEW.md sync ([`4309e55`](https://github.com/koreaeatsrice/patchee/commit/4309e5590750f117977b5a3916a8962bcfcaface))
- add SECURITY-REVIEW.md (dependabot triage + CodeQL result) ([`25cd2fe`](https://github.com/koreaeatsrice/patchee/commit/25cd2fec6504e31025cdcb08d8d974690b021209))
- correct the client-side claims — join-optional, out of step without it ([`dc33158`](https://github.com/koreaeatsrice/patchee/commit/dc331581ec139fe62b60eef09025415a09665a2b))
- **readme**: neutral rewrite — Warning/Note disclaimer split, plain wording throughout ([`87b21f9`](https://github.com/koreaeatsrice/patchee/commit/87b21f90378885ce30c77afd0dbdb40927c00adb))

### Maintenance & CI
- bump the GTNH convention toolchain 2.0.20 -> 2.0.33 (adopt upstream) ([`ea0ab7e`](https://github.com/koreaeatsrice/patchee/commit/ea0ab7e0c2c0c0dc245d5c449ad253c02be132ea))

### Other Changes
- Update README with project details and emphasis ([`d6396a9`](https://github.com/koreaeatsrice/patchee/commit/d6396a9f313f337b596d46b8dd11897497a9d766))

### Contributors

- uriel-runner[bot]
- koreaeatsrice

**Full changelog**: https://github.com/koreaeatsrice/patchee/compare/v1.2.0...v1.3.0

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
