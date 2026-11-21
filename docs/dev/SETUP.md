# Setup

For a contributor who has just cloned the repo and wants a green build. It covers the JDK, the wrapper, the IDE
and the first run; the commands you use every day are in [WORKFLOW.md](WORKFLOW.md).

## What you need

| Thing | Version | Why |
|---|---|---|
| JDK | 25 | The compiler target and `.java-version`. |
| JDK | 17 | The GTNH convention plugin resolves a 17 toolchain for some helper tasks. Point `JAVA_HOME_17_X64` at it if Gradle cannot find one. |
| Git | any recent | The wrapper and the release engine use tags. |
| Python | 3.8+ | Only for `.github/scripts/release.py` and its tests. No packages. |
| Network | first build only | The GTNH maven, Forge and the dev-runtime mods are downloaded. |

Linux and macOS are the supported shells. Windows works through `gradlew.bat`, but the docs and the smoke job
assume a POSIX shell.

## First build

```sh
git clone https://github.com/koreaeatsrice/patchee.git
cd patchee
./gradlew build --no-daemon
```

Expect several minutes on the first run. A successful run ends with `BUILD SUCCESSFUL` and leaves:

- `build/libs/patchee-<version>.jar` — the jar you would ship;
- `build/libs/patchee-<version>-dev.jar` — the development jar, not a release artifact.

If Gradle reports a missing toolchain, set both JDKs explicitly:

```sh
export JAVA_HOME=/path/to/jdk-25
export JAVA_HOME_17_X64=/path/to/jdk-17
./gradlew build --no-daemon
```

`org.gradle.configuration-cache` and `org.gradle.parallel` are on in `gradle.properties`. If a task behaves oddly
after a dependency change, `./gradlew --stop` clears the daemons; `--no-daemon` avoids them entirely.

## IDE

The project is a normal Gradle project. Two workable setups:

- **IntelliJ IDEA** — *Open* the repo folder, let it import the Gradle project, set the project SDK to JDK 25 and
  the Gradle JVM to the same. The GTNH plugin generates the run configurations; do not hand-write one.
- **VS Code** — the Gradle extension imports the project; use JDK 25 for the language server. The generated
  sources appear under `build/`; add that to the Java source path if completion misses `Tags`.

Do not commit `.idea/`, `.vscode/`, `*.iml` or a run configuration. `.gitignore` covers the usual files; a forced
add is rejected in review.

## The dev runtime

`./gradlew runServer` boots a dedicated server in `run/server`. `./gradlew runClient` boots a client in `run/`. Both
write config, logs and worlds under `run/`, which is ignored by git.

Two facts about the pins:

- Twilight Forest is pinned to 2.7.41, the newest version published to a resolvable maven. The pack's newer build
  is not on that maven. The pin affects the dev runtime only and never reaches a published jar.
- Gendustry and MagicBees are not on the dev runtime: their jitpack artifacts drag in an unresolvable
  CodeChickenLib pin. The self-test skips them locally and checks them on a real pack.

If a dev-server feature check is skipped with "not installed", that is the pin, not a bug. Check it on a real pack
before you call it broken.

## First-run checklist

1. `./gradlew build --no-daemon` is green.
2. `./gradlew runServer` reaches `Done` and the log contains a `[patchee]` banner.
3. `run/server/config/patchee.cfg` exists and has one `B:enable...` key per feature.
4. With `runSelfTest=true`, the log ends with `[SelfTest] result: N passed, 0 failed` (some checks may SKIP for a
   mod the dev runtime does not carry).
5. `git status` is clean apart from `run/`, which is ignored.

If step 2 fails, read [../guide/TROUBLESHOOTING.md](../guide/TROUBLESHOOTING.md) before changing code.

## Where to go next

- [WORKFLOW.md](WORKFLOW.md) — branch, commit, review, merge.
- [STYLE.md](STYLE.md) — formatting and voice.
- [TESTING.md](TESTING.md) — the four test layers.
- [ADDING-A-FEATURE.md](ADDING-A-FEATURE.md) — the worked example.
