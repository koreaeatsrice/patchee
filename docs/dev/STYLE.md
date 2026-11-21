# Style

For a contributor writing code, a log line, a config comment or a doc page. It covers the formatter, the linter,
the voice and the two things that are treated as API.

## Formatter

Java is formatted by Spotless, configured by the GTNH convention plugin (`gtnh.settings.blowdryerTag` in
`gradle.properties`). Do not hand-format; run:

```sh
./gradlew spotlessApply
./gradlew spotlessCheck    # what CI checks
```

Spotless may reformat generated or unrelated code if you touched it. Keep the diff to your change; revert files you
did not mean to touch.

## Linter

Checkstyle runs as part of `check`. The rule currently enforced is the wildcard import ban: no `import foo.*;`.
`./gradlew checkstyleMain` runs that pass alone.

## Whitespace

`.editorconfig` is authoritative:

- UTF-8, LF, final newline.
- 4 spaces for Java; 2 spaces for `*.json`, `*.md`, `*.yml`, `*.yaml`.
- Trailing whitespace is trimmed, except in `*.md` and `*.lang`.

## The voice

Dry and factual. No marketing, no adjectives, no emoji. A tradeoff or a limit gets one sentence, then move on.

Write the thing, not the feeling:

- Yes: "A stock client still draws GregTech's smog. Hiding it needs a client copy of Patchee."
- No: "Patchee beautifully eliminates unsightly pollution for everyone!"
- Yes: "The transformer leaves the class untouched when the byte shape does not match."
- No: "The transformer robustly handles all edge cases."

A comment explains why, not what. `// add one` is noise; `// the loader can call with either name` is the reason
the line exists.

## Log lines are API

Operators grep them; CI asserts some of them. Rules:

- One line, lowercase after the bracketed prefix: `[FeatureId] ...`.
- INFO for a decision the operator can act on (`armed`, `wrote`, `skipped because ...`).
- ERROR for a shape or a target that changed, with the observed shape where possible.
- DEBUG for per-step tracing; a production log must not grow because a step ran.
- No stack trace at INFO. `failSoft` logs the throwable at ERROR; that is the normal path.
- Do not change an existing line's text unless the PR is about that line. `docs/ops/CI.md` lists the ones the smoke
  job greps.

## Config keys are API

A key name, its default and its comment are user-facing. `docs/SETTINGS.md` is the authoritative list.

- Add a key for a new setting; never rename an existing one.
- Keep a removed key parseable and ignored, as `TeamChunkCapSettings` does with the old
  `teamChunkCap.maxClaimChunks` keys.
- Write the comment for a server owner, not a developer. Say what changes and what the safe range is.
- A new key needs a row in `docs/SETTINGS.md` in the same PR.

## Test names

A test name says the behaviour, not the method: `glassBreakDoesNotCutWhenFourPiecesWorn`, not `testGlass1`. A
failing test name should tell a reviewer what broke without opening the file.

## Docs

- One sentence at the top saying who the page is for and what it answers.
- Link instead of repeating. If two pages need the same fact, one owns it.
- Cite a file, and a line only when the line is stable. A stale line number is worse than no line number.
- No emoji, no badges, no "simply" or "just".

## Where to go next

- [TESTING.md](TESTING.md) — how to prove the change.
- [WORKFLOW.md](WORKFLOW.md) — the PR and review.
- [../ops/CI.md](../ops/CI.md) — the log lines CI asserts.
