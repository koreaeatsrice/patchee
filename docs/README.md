# Documentation index

Start here. Each page added by this split says at the top who it is for and what it answers. Every page is short on
purpose; where two pages touch the same subject, one owns it and the other links.

## For server owners

| Page | For | Answers |
|---|---|---|
| [guide/GETTING-STARTED.md](guide/GETTING-STARTED.md) | Owner, first install | Which jar, where it goes, what the first boot should say. |
| [guide/CONFIGURING.md](guide/CONFIGURING.md) | Owner | How `config/patchee.cfg` is generated, what the master switch does, when a restart is needed. |
| [SETTINGS.md](SETTINGS.md) | Owner | Every key, its default, and when to change it. |
| [FEATURES.md](FEATURES.md) | Owner, player | What each feature does, its switch, and its limits. |
| [guide/CLIENT.md](guide/CLIENT.md) | Owner, player | What a client copy of Patchee adds, and what it never adds. |
| [guide/TROUBLESHOOTING.md](guide/TROUBLESHOOTING.md) | Owner | Symptom, cause, fix. |
| [ops/SERVER-STAGING.md](ops/SERVER-STAGING.md) | Owner, operator | Installing an update, backups, rollback. |

## For contributors

| Page | For | Answers |
|---|---|---|
| [dev/SETUP.md](dev/SETUP.md) | New contributor | JDK, Gradle, IDE, first build. |
| [dev/WORKFLOW.md](dev/WORKFLOW.md) | Contributor | Branch, commit, PR, review, merge. |
| [dev/ADDING-A-FEATURE.md](dev/ADDING-A-FEATURE.md) | Contributor | A worked feature, start to finish. |
| [dev/TESTING.md](dev/TESTING.md) | Contributor | Unit, real-jar, matcher and smoke tests; how to test a transformer. |
| [dev/STYLE.md](dev/STYLE.md) | Contributor | Spotless, Checkstyle, the voice, log and config key rules. |

## For the implementer

| Page | For | Answers |
|---|---|---|
| [internals/PIPELINE.md](internals/PIPELINE.md) | Implementer | `Feature`, `Step`, `Outcome`, decorators, factory, registry, context, policies, reflection. |
| [internals/TRANSFORMERS.md](internals/TRANSFORMERS.md) | Implementer | The coremod, the five transformers, class-load order, the patch contract. |
| [internals/SIDES.md](internals/SIDES.md) | Implementer | The client-optional mechanics, the decision table, how each shape is proven. |
| [internals/SELFTEST.md](internals/SELFTEST.md) | Implementer | What the self-test runs and what a PASS means. |
| [internals/CONFIG-AND-TOGGLES.md](internals/CONFIG-AND-TOGGLES.md) | Implementer | The registry-driven config, the `Toggles` snapshot, early readers. |
| [ops/GTNH-INTERNALS.md](ops/GTNH-INTERNALS.md) | Implementer | 1.7.10 names, load order, reflection versus ASM, jar inspection, licences. |

## Operations

| Page | For | Answers |
|---|---|---|
| [ops/RELEASING.md](ops/RELEASING.md) | Maintainer | Version derivation, bumps, notes, tags, drafts. |
| [ops/CI.md](ops/CI.md) | Maintainer | The four workflows, the jobs, and the log lines CI asserts. |

## The rules

[AGENTS.md](../AGENTS.md) is the short, non-negotiable rule set for an AI agent or a human changing the repo. Read
it before either of the two tables above matters.
