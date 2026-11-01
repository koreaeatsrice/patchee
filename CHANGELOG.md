# Changelog

All notable changes to this project will be documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/) and this project adheres to [Semantic Versioning](https://semver.org/).

## [1.7.3] - 2026-11-01

### Bug Fixes
- **enrichedfertilizer**: drop GregTech's NEI recipe cache on the client (#16) ([`da2526b`](https://github.com/koreaeatsrice/patchee/commit/da2526b37f6bfade1c6ba2e5ddfdb6322306245a))

### Contributors

- @uriel-runner[bot]
- @koreaeatsrice

**Full changelog**: https://github.com/koreaeatsrice/patchee/compare/v1.7.2...v1.7.3

## [1.7.2] - 2026-11-01

### Features
- **tf**: match aurora break to the castle and add Weathered Deadrock (#15) ([`27ca7af`](https://github.com/koreaeatsrice/patchee/commit/27ca7af7437821fc38923d5d0c9bf422e77a4183))

### Contributors

- @uriel-runner[bot]
- @koreaeatsrice

**Full changelog**: https://github.com/koreaeatsrice/patchee/compare/v1.7.1...v1.7.2

## [1.7.1] - 2026-11-01

### Bug Fixes
- **enrichedfertilizer**: resolve the cropsnh fluids case-insensitively (#14) ([`689d77e`](https://github.com/koreaeatsrice/patchee/commit/689d77e50333a779f09452166bb3cea9d7f409ce))

### Contributors

- @uriel-runner[bot]
- @koreaeatsrice

**Full changelog**: https://github.com/koreaeatsrice/patchee/compare/v1.7.0...v1.7.1

## [1.7.0] - 2026-10-31

### Features
- **enrichedfertilizer**: keep the old enriched fertilizer recipe (#12) ([`ca6dc39`](https://github.com/koreaeatsrice/patchee/commit/ca6dc39cbf015289e23b5a86bf517bee795d025c))

### Bug Fixes
- **tf-breakspeed**: make the Twilight Forest break-speed floors actually apply (#13) ([`31769af`](https://github.com/koreaeatsrice/patchee/commit/31769af4233f060a039497bd0de332e02f32e59e))

### Documentation & Wiki
- **readme**: one short line per feature (#11) ([`f195b63`](https://github.com/koreaeatsrice/patchee/commit/f195b6350a76329fdb969d43533fc8b7b643cfbd))
- **readme**: drop the LLM disclaimer; one Twilight Forest entry (#10) ([`aabf131`](https://github.com/koreaeatsrice/patchee/commit/aabf1318d8ccefeefab09eabd3a7b1eb3ce3506f))

### Contributors

- @uriel-runner[bot]
- @koreaeatsrice

**Full changelog**: https://github.com/koreaeatsrice/patchee/compare/v1.6.1...v1.7.0

## [1.6.1] - 2026-10-19

### Bug Fixes
- **teamchunkcap**: read a team's limit from the best member's per-rank setting (#9) ([`f07c314`](https://github.com/koreaeatsrice/patchee/commit/f07c314f3014e1e459e9fad6480f47566952f89b))

### Contributors

- @uriel-runner[bot]
- @koreaeatsrice

**Full changelog**: https://github.com/koreaeatsrice/patchee/compare/v1.6.0...v1.6.1

## [1.6.0] - 2026-10-17

### Features
- **finalcastlebreak**: take the Final Castle apart once Glacier is earned (#8) ([`e087920`](https://github.com/koreaeatsrice/patchee/commit/e0879205c81f96c1d9f97773877ff80e304bed1e))

### Contributors

- @uriel-runner[bot]
- @koreaeatsrice

**Full changelog**: https://github.com/koreaeatsrice/patchee/compare/v1.5.1...v1.6.0

## [1.5.1] - 2026-10-15

### Bug Fixes
- **teamchunkcap**: write claim-clamp samples to logs/patchee-claims.log instead of the console (#7) ([`9605420`](https://github.com/koreaeatsrice/patchee/commit/96054204ab409da4378c02d5202976199b79957a))

### Other Changes
- Refactor README.md for clarity on features ([`81e8c6d`](https://github.com/koreaeatsrice/patchee/commit/81e8c6df4abcd6f8309af7d2ea09631741a86a56))

### Contributors

- @koreaeatsrice

**Full changelog**: https://github.com/koreaeatsrice/patchee/compare/v1.5.0...v1.5.1

## [1.5.0] - 2026-10-14

### Features
- **teamchunkcap**: cap a team's chunk claims at 1500 and chunk loads at 500 (#6) ([`3c4338d`](https://github.com/koreaeatsrice/patchee/commit/3c4338d0fa391f1c0d9993e07352c169b15dce1f))

### Contributors

- @uriel-runner[bot]
- @koreaeatsrice

**Full changelog**: https://github.com/koreaeatsrice/patchee/compare/v1.4.0...v1.5.0

## [1.4.0] - 2026-10-12

### Features
- backport modern Twilight Forest break speeds (aurora blocks and arctic fur) ([`8ebb561`](https://github.com/koreaeatsrice/patchee/commit/8ebb561a23c603ace5a413ba80d443d6f4d52b51))

### Documentation & Wiki
- rewrite the README in the project's own voice (#4) ([`2cc6cbb`](https://github.com/koreaeatsrice/patchee/commit/2cc6cbb8aa3c205b29c87ed32dbf494594255955))

### Contributors

- @uriel-runner[bot]
- @koreaeatsrice

**Full changelog**: https://github.com/koreaeatsrice/patchee/compare/v1.3.1...v1.4.0

## [1.3.1] - 2026-10-02

### Bug Fixes
- **lightoverlay**: apply NEI's light patch at class load instead of a Mixin (#3) ([`8954534`](https://github.com/koreaeatsrice/patchee/commit/895453440df09050ce5eaee43f3b1e01f300834f))
- **release**: @-mention contributors and always credit the owner ([`31859d4`](https://github.com/koreaeatsrice/patchee/commit/31859d4c9934afe68d83d661a469f807fdae7050))
- **changelog**: de-duplicate the v1.3.0 section (one section per version) ([`a1b5ad1`](https://github.com/koreaeatsrice/patchee/commit/a1b5ad1cb8cddf9466eb0e5a40fe0c004effc076))

### Maintenance & CI
- run the release-engine unit tests on every push and PR (#2) ([`eeb0b0b`](https://github.com/koreaeatsrice/patchee/commit/eeb0b0b68acb40c75292c572bf797d3ec57e1b22))

### Other Changes
- Update README for clarity on GTNH server add-on ([`9833676`](https://github.com/koreaeatsrice/patchee/commit/9833676ba87fadafc9e281bbdcb016aaa689bc89))

### Contributors

- @uriel-runner[bot]
- @koreaeatsrice

**Full changelog**: https://github.com/koreaeatsrice/patchee/compare/v1.3.0...v1.3.1

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
