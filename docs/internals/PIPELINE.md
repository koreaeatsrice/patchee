# The pipeline

For an implementer changing `core/` or adding a feature. It describes what runs, in what order, and which decorator
owns which failure. The byte-level layer is [TRANSFORMERS.md](TRANSFORMERS.md); the side rules are
[SIDES.md](SIDES.md). Files below are under `src/main/java/com/jointspaceforce/patchee/`.

## The shape

```
FeatureRegistry  ──>  PatchFactory  ──>  FeaturePipeline
   (ordered list)        (decorates)         (runs once)
```

A `Feature` is a descriptor plus a list of `Step`s. The factory wraps each step in decorators. The pipeline runs
them in registration order. Nothing is dynamic: a step that does not run at this boot does not run later.

## `Feature`

`core/Feature.java:14`. The methods:

| Method | Used for |
|---|---|
| `id()` | Log prefix, banner field name, lookup key. |
| `configKey()` | The key in `config/patchee.cfg`. |
| `defaultEnabled()` | Value when the key is absent. |
| `description()` | The comment written into the config file. |
| `disabledMessage()` | INFO line when the switch is off. |
| `failureMessage()` | ERROR line when a step throws. |
| `targetClass()` | A class whose presence gates the feature, or `null`. |
| `missingTargetMessage()` | INFO line when that class is absent. |
| `steps()` | The work, in order. |
| `early()` | `false` by default; `true` means run at Patchee's `preInit`. |

Only `VeinConfigFeature` returns `early() == true` (`features/VeinConfigFeature.java:150`). It writes VeinMiner's
config files, and VeinMiner reads them in its own `preInit`; a postInit step would be too late.
`dependencies = "before:VeinMiner"` in `Patchee.java:121` makes Patchee's `preInit` run first.

## `Step` and `Outcome`

`core/Step.java` is `Outcome run(PatchContext ctx) throws Exception`. A step may throw; a decorator handles it. A
step holds no state of its own: everything comes from the context.

`core/Outcome.java`:

- `APPLIED` — it changed the target.
- `SKIPPED` — it did nothing on purpose (target absent, feature off, already patched).
- `FAILED` — it could not do its job and the failure was reported.

## Composition helpers

`core/Steps.java`:

| Helper | Behaviour |
|---|---|
| `sequence(Step...)` | Runs in order; stops on `FAILED`, continues on `SKIPPED` (`Steps.java:21-34`). |
| `note(String)` | Logs one line (`Steps.java:38`). |
| `withStaticField(owner, field, action)` | Resolves a declared static field through `ctx.reflect()`; an absent class is `SKIPPED` (`Steps.java:57-72`). |
| `present(String)` | A target probe: true when the class loads (`Steps.java:74`). |
| `noop()` | A do-nothing step, so a single-step feature is still a list. |

The `SKIPPED` asymmetry is deliberate: a later step usually needs an earlier one, but a skip often still leaves
something worth reporting.

## Decorators

`core/StepDecorators.java`, one concern each:

- `failSoft(stepId, failureMessage, step)` — catches `Throwable`, calls
  `ctx.failurePolicy().onFailure(...)`, returns `FAILED` (`StepDecorators.java:23-36`). This is the normal failure
  path; a step does not need its own catch around the work it reports.
- `logged(stepId, step)` — logs start and result at DEBUG only (`StepDecorators.java:39-55`), so a production log
  does not grow because a step ran.
- `requiresTarget(stepId, present, skipMessage, step)` — calls `ctx.missingTargetPolicy().onMissingTarget(...)`
  and returns `SKIPPED` when the probe is false (`StepDecorators.java:60-72`).

## `PatchFactory`

`core/PatchFactory.java` turns a registry plus a `Toggles` snapshot into a pipeline. Per feature, in registration
order:

**Enabled** (`PatchFactory.java:74`), innermost first:

```
Steps.sequence(feature.steps())
  -> failSoft
  -> requiresTarget   (only when targetClass() != null)
  -> logged
```

The target gate sits **outside** fail-soft, so an absent class is a quiet skip, not a caught exception. An empty
step list becomes `Steps.noop()`.

**Disabled** (`PatchFactory.java:92`) contributes one step that logs `disabledMessage()` and returns `SKIPPED`.

`build(toggles)` and `buildEarly(toggles)` are disjoint: a feature goes to exactly one, by `early()`
(`PatchFactory.java:51-64`). The registry is not filtered by side; a side decision belongs to the step.

## `FeaturePipeline`

`core/FeaturePipeline.java:23` runs the decorated steps once. Its last-resort `catch (Throwable)` logs at DEBUG
(`FeaturePipeline.java:27`). The server boot continues whatever happens.

## `FeatureRegistry`

`core/FeatureRegistry.java` is an ordered list with a freeze. `register` throws
`IllegalStateException("feature registry is frozen")` after `freeze()` (`FeatureRegistry.java:19-30`). The only
registration point is `features/PatcheeFeatures.standard()`, which registers 17 features and freezes
(`PatcheeFeatures.java:18-35`). Order is run order.

## `PatchContext`

`core/PatchContext.java:25` carries what a step may use: the logger, the `Toggles` snapshot, the failure policy,
the missing-target policy, the `Reflective` helper, and the game's `config/` directory (`PatchContext.java:60`). It
is built once in `CommonProxy.preInit`. A feature that writes another mod's config builds the path from
`ctx.configDirectory()`.

## Policies

`core/policy/` has two interfaces and the standard instances in `Policies.java`:

- `FAIL_LOG_ERROR_CONTINUE` — `log.error(message, failure)`, then carry on.
- `QUIET_SKIP_INFO` — `log.info(message)`, then skip.

The composition root picks one of each (`CommonProxy.java:51-52`). A feature never sees a policy directly.

## `Reflective`

`core/reflect/Reflective.java`:

| Method | Behaviour |
|---|---|
| `find(name)` | The class or `null`; catches `ClassNotFoundException` and `LinkageError` (`Reflective.java:22-30`). |
| `staticField(owner, name)` | `getDeclaredField` + `setAccessible(true)` (`Reflective.java:36`). A missing field throws, and fail-soft reports it. |
| `staticClassList(field)` | The value as `List<Class<?>>`, or `null`. |
| `appendIfAbsent(list, value)` | The idempotence primitive (`Reflective.java:63`). |
| `extendMaterials(current, extras)` | The same array when nothing is missing (`Reflective.java:76-84`). |

## Startup, in order

1. `FMLPlugin` is constructed; `injectData` runs `PollutionPreload` (`asm/FMLPlugin.java:65-82`).
2. Classes load through the five transformers, in registration order.
3. `preInit`: load config, snapshot `Toggles`, build context, build both pipelines, log the banner, run the early
   pipeline when the master switch is on and the side is `SERVER` (`CommonProxy.java:43-70`).
4. `postInit`: master switch off means one INFO line (`CommonProxy.java:72-77`); on means the normal pipeline runs.
5. `serverStarted`: VeinConfig re-reads VeinMiner's live state; `SelfTest.schedule` if asked
   (`CommonProxy.java:80-93`).
6. First server tick: the self-test runs once ([SELFTEST.md](SELFTEST.md)).

## Invariants

- One registration point.
- Config keys, banner fields and order come from the registry.
- Features read settings through the context, never through `Config` statics.
- A missing target is a skip; a changed shape is an error and the target is left untouched.
- A step may throw; the decorator reports it and the boot continues.

## See also

- [TRANSFORMERS.md](TRANSFORMERS.md) — the byte layer.
- [CONFIG-AND-TOGGLES.md](CONFIG-AND-TOGGLES.md) — where the settings come from.
- [../dev/ADDING-A-FEATURE.md](../dev/ADDING-A-FEATURE.md) — the worked example.
