# Security review

_Reviewed 2026-09-30. This review covered all three repositories (umbra,
sundial, patchee); the same document is kept in each._

Scope: source code, build toolchain, released artifacts, and GitHub security
settings.

## What the Security tab was showing

35 open Dependabot alerts, **every one of them in `settings.gradle.kts`** —
dependencies of the **GTNH Gradle convention plugin** (the build toolchain:
log4j, Netty, Plexus, commons-*, Guava, Gson, …), not of the mod itself.

## Is the mod affected? No.

The released jar ships **only this project's own classes** (plus `mcmod.info`
and `LICENSE` — check with `unzip -l`). No third-party library is bundled, so
none of the flagged code is present in the artifact that runs on a server.

## What was done (dependency side)

1. **Fixed for real (5 alerts):** the toolchain pin was bumped
   `com.gtnewhorizons.gtnhsettingsconvention` 2.0.20 → **2.0.33**, matching
   `GTNewHorizons/ExampleMod1.7.10` master. That updated several transitive
   pins and 5 alerts closed as _fixed_.
2. **Triaged (30 alerts):** the rest are pinned by the upstream toolchain and
   cannot be moved from this repository without breaking the GTNH build. Each
   was dismissed with reason `tolerable_risk` and a comment pointing here.
3. **CodeQL enabled** (default setup): first analysis completed with
   **0 results** — no code-scanning findings in this project's own code.
4. **Secret scanning**, **push protection** and **Dependabot security
   updates**: enabled.
5. **Private vulnerability reporting**: enabled (Security tab → _Report a
   vulnerability_).

## Code review (adversarial, same date)

An independent adversarial pass read every source file, workflow and script in
all three repositories, verified behaviour against the local Forge/FML
sources, and live-tested the injection it found. Verdict: **SHIP WITH NOTES**
— no critical or high findings. Every finding, and what happened to it:

| # | Severity | Where | Finding | Status |
|---|----------|-------|---------|--------|
| 1 | Medium | `release.yml` (all three) | Tag name interpolated into bash — a crafted tag could run commands on the release runner | **Fixed**: every value now travels via `env:` as quoted shell variables |
| 2 | Low | sundial `WorldHandler.java` | World load/unload hooks had no try/catch; FML's bus re-throws, so a bad world file could take the server down | **Fixed**: fail-soft wraps; first failure logged once |
| 3 | Low | patchee `DollyFeature.java`, `Config.java` | `extraDollyClasses` feeds `Class.forName` — a config editor can load any class on the classpath | **Accepted risk** (config access already implies server control) — documented here |
| 4 | Low | patchee `VeinConfigFeature.java` (a parallel work-in-progress branch) | VeinMiner's config is written with truncate-first `FileOutputStream`; a crash mid-write can leave a broken file | **Open — owner to apply**: write to a temp file, then atomic rename |
| 5 | Low | sundial `WorldHandler.java` | `worlds` map written without the lock its readers take → possible `ConcurrentModificationException` | **Fixed**: put/remove now under `synchronized (worlds)` |
| 6 | Info | umbra `Umbra.java` | `isHostile` could NPE in its class-alias-missing fallback | **Fixed**: null guard added |
| 7 | Info | `dependabot-automerge.yml` (all three) | `pull_request_target` gated on actor only | **Fixed**: also checks the PR author and the `dependabot/` branch prefix |
| 8 | Info | `gradle-wrapper.properties` (all three) | Wrapper zip had no checksum | **Fixed**: official `distributionSha256Sum` added |

Clean categories (checked, nothing found): no `Runtime.exec` /
`ProcessBuilder` / `URLClassLoader` / `ScriptEngine` / `ObjectInputStream` /
`XMLDecoder`; no secrets in any file; no untrusted file paths; no unsafe
deserialization; umbra's reflection always fails safe; network packets are
client-channel only; commands are op-gated; no format-string or log-injection
bugs.

## Notes for maintainers

- Re-check after toolchain bumps. If an alert is ever genuinely reachable from
  this project's own code, treat it as a real bug.
- **Dependabot malware alerts** have no REST API — enable per repository under
  _Settings → Advanced Security → Dependabot alerts → Malware alerts_ (UI-only
  as of 2026-09).

_Prepared with the help of an AI agent; see the disclaimer in the README._