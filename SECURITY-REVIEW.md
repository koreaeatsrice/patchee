# Security review

_Reviewed 2026-09-30._

Scope: this repository — source code, build toolchain, released artifacts, and
GitHub security settings.

## What the Security tab was showing

35 open Dependabot alerts, **every one of them in `settings.gradle.kts`** —
dependencies of the **GTNH Gradle convention plugin** (the build toolchain:
log4j, Netty, Plexus, commons-*, Guava, Gson, …), not of the mod itself.

## Is the mod affected? No.

The released jar ships **only this project's own classes** (plus `mcmod.info`
and `LICENSE` — check with `unzip -l`). No third-party library is bundled, so
none of the flagged code is present in the artifact that runs on a server.

## What was done

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

## Notes for maintainers

- Re-check after toolchain bumps. If an alert is ever genuinely reachable from
  this project's own code, treat it as a real bug.
- **Dependabot malware alerts** have no REST API — enable per repository under
  _Settings → Advanced Security → Dependabot alerts → Malware alerts_ (UI-only
  as of 2026-09).

_Prepared with the help of an AI agent; see the disclaimer in the README._