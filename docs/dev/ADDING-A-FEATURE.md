# Adding a feature

For a contributor adding behaviour. It is one worked example, start to finish, using the real `DollyFix` feature as
the plain case, then the extra steps a transformer-backed feature needs. The theory is
[../internals/PIPELINE.md](../internals/PIPELINE.md); the tests are [TESTING.md](TESTING.md).

## Step 0: pick the shape

Ask two questions.

1. **Can the change be a field write, a registry change, a config write or an event listener?** If yes, it is a
   plain feature. This is the default and the cheapest to review.
2. **Is the thing to change a constant, a return value or a constructor argument inside another mod's compiled
   method, with no hook and no config key?** If yes, it is transformer-backed. Go to the last section after you read
   the plain recipe; the `Feature` half is the same.

Then pick the client shape: SERVER-ONLY (preferred), BOTH-SIDES-OPTIONAL, or CLIENT-ONLY (cosmetic and inert
without Patchee). Write the choice in the PR. [../internals/SIDES.md](../internals/SIDES.md) has the decision table.

## Step 1: look at the real target

`DollyFix` extends JABBA's barrel-mover whitelist. The target is a static list built at class-init:

```java
public class ItemBarrelMover {
    public static List<Class<? extends TileEntity>> classExtensions = new ArrayList<>();
}
```

Before writing code, confirm the field with `javap` on the jar the pack ships:

```sh
javap -p -classpath JABBA-*.jar mcp.mobius.betterbarrels.common.items.dolly.ItemBarrelMover | grep classExtensions
```

If the field is `final`, a reflection write may fail or may be rejected by the JVM; check before you design around
it. If it is built by a static initializer that only runs once, appending later is enough.

## Step 2: the feature class

One file: `features/DollyFeature.java` (about 170 lines including comments). It implements `core.Feature`. The
methods that matter:

| Method | The real value | Why |
|---|---|---|
| `id()` | `"DollyFix"` | The log prefix and the banner field. Stable; do not rename. |
| `configKey()` | `"enableDollyFix"` | The key in `config/patchee.cfg`. Stable. |
| `defaultEnabled()` | `true` | Used when the key is absent. |
| `description()` | plain sentence | Written into the config file as the comment. |
| `disabledMessage()` | one INFO line | What an operator sees when the switch is off. |
| `failureMessage()` | one ERROR line | What `failSoft` logs when a step throws. |
| `targetClass()` | the JABBA class name | The feature only runs when that class is present. |
| `missingTargetMessage()` | one INFO line | What an operator sees when JABBA is absent. |
| `steps()` | `Collections.singletonList(appendHousings())` | The work. |
| `early()` | not overridden | `false`; postInit is early enough. |

The work itself (`DollyFeature.java:121-168`):

1. resolve the static list through `Steps.withStaticField`;
2. reject the value if it is not a `List` (log and return `FAILED`);
3. build the wanted names from the static `HOUSINGS` list plus `ctx.toggles().extraDollyClasses()`, keeping only
   names that resolve with `ctx.reflect().find(...)` and are `TileEntity` subclasses;
4. append each name once, via `Reflective.appendIfAbsent`;
5. log how many were added and how many were already there;
6. return `APPLIED` unconditionally; the added and already-present counts are in the INFO line.

Two rules are visible in that code and apply to every feature:

- **No raw `try/catch` around the work.** The step throws; `failSoft` reports it through the policy.
- **No `Config` static reads.** Settings come from `ctx.toggles()`; the file system comes from
  `ctx.configDirectory()`.

## Step 3: register it

One line in `features/PatcheeFeatures.standard()`, in run order:

```java
registry.register(new DollyFeature())
```

`PatcheeFeatures.java:18-34` registers the 17 features in the order they run, then freezes the registry. You do
not edit `Config.java`, `CommonProxy.java` or `Patchee.java` for a plain feature: the config option, the boot
banner field and the pipeline wiring are derived from the registry.

## Step 4: settings, if any

An on/off switch is automatic. A value needs four edits:

1. a field in `core/Toggles` and in `Config.Snapshot`;
2. a read in `Config.synchronizeConfiguration`, with a plain comment constant;
3. a static default in `Config`;
4. the accessor on `Toggles`.

If a transformer needs the same value before Forge config exists, add a small `*Settings` class that parses
`config/patchee.cfg` itself and reuses the key constant. `LightOverlaySettings` is the model; it also tolerates a
missing file, comments and unknown keys.

## Step 5: the self-test

Add a check to `SelfTest` when the behaviour can be exercised on a server. Follow `checkDollyGate`
(`SelfTest.java:157-230`):

- run the patched decision on a real object;
- run a **control** that must stay unchanged (a natural hive and a furnace stay refused);
- report with `check("what was observed", condition)`;
- add the call to `runChecks`.

If a check cannot run on a dedicated server, log a `SKIP` with the reason. A skipped check is honest; a fake PASS
is not.

## Step 6: tests

At minimum, a JUnit test for the pure decision (here: the class-name filter and the idempotent append). If the
feature touches a real mod's field names, add a real-jar test gated on an environment variable, following
`PollutionVisualsRealJarTest`. If it is transformer-backed, add the synthetic-bytes test and a matcher tool run.
See [TESTING.md](TESTING.md).

## Step 7: docs rows

In the same PR:

- `docs/FEATURES.md` — a row: what it does, the switch, the honest limits.
- `docs/SETTINGS.md` — a row per key: default and when to change it.
- `README.md` — one bullet if the feature is user-visible. The bullet is one sentence of at most 18 words after the
  dash; `ReadmeFeatureSentencesTest` enforces that and fails the build otherwise.
- `docs/guide/CLIENT.md` — if it has a client half.
- `docs/guide/GETTING-STARTED.md` / `TROUBLESHOOTING.md` — if the boot log gained a line an operator must read.

## Step 8: the client check

- Does a stock client still join with no error?
- Does the server jar alone do the whole job? If not, the docs must say what a stock client sees instead.
- Did you add a packet, a channel, a client mod requirement, a registration change or an unguarded one-sided class
  patch? If yes, redesign.
- Run the no-packet grep from `AGENTS.md`.

## Step 9: build and PR

```sh
./gradlew spotlessApply
./gradlew build --no-daemon
./gradlew test --rerun-tasks
python3 .github/scripts/test_release.py   # only if the release engine changed
```

Then [WORKFLOW.md](WORKFLOW.md): the Conventional Commit, the PR body, two hostile reviews, a squash merge.

## The transformer variant

A transformer-backed feature keeps the `Feature` for the switch, banner and self-test, and adds four things:

1. `asm/YourTransformer.java` — name gate, `try/catch Throwable`, settings read, patch call, site-count log,
   original bytes on any doubt. `LightOverlayTransformer.java:37-70` is the smallest example.
2. One line in `FMLPlugin.getASMTransformerClass()`.
3. `features/YourPatch.java` — the byte matching and editing. Report a state (`PATCHED`, `ALREADY_PATCHED`,
   `UNEXPECTED_SHAPE`, and `PARTIAL` where a multi-edit patch supports it), a site count, and never throw.
4. `features/YourSettings.java` — the early config reader, if the transformer needs a value.

The feature's step usually only logs that the transformer is armed, gated on the transformer's "patch applied" flag.
`TeamChunkCapFeature.java:143-163` shows why: a class that is merely present does not prove the patch matched.
`TeamChunkCapFeature` also publishes the authoritative `Toggles` values into the holder the injected hook reads,
because the transformer ran before Forge's config existed.

Prove it in all four layers: synthetic bytes, the real jar, a dev server, and a second pass. The full contract is
[../internals/TRANSFORMERS.md](../internals/TRANSFORMERS.md).
