# Workflow

For a contributor who is ready to change the repo. It covers the branch, the commit, the PR, the review and the
merge. The feature recipe itself is [ADDING-A-FEATURE.md](ADDING-A-FEATURE.md); the tests are
[TESTING.md](TESTING.md).

## Branch from main

One topic per branch:

```
feat/<slug>      new behaviour
fix/<slug>       a correction
docs/<slug>      documentation only
test/<slug>      tests only
chore/<slug>     build, CI, release plumbing
```

```sh
git switch main && git pull
git switch -c feat/my-thing
```

Do not work directly on `main`. Do not reuse a branch for a second topic; a mixed diff is hard to review and hard
to revert.

## Commits

The release engine parses commit subjects, so the format is load-bearing:

```
<type>(<scope>): <description>
```

| Type | Release section |
|---|---|
| `feat` | Features; forces a minor bump in `auto` mode |
| `fix` | Bug Fixes |
| `perf` | Performance Improvements |
| `refactor` | Refactoring & Code Quality |
| `docs` | Documentation & Wiki |
| `style` | Code Style & Formatting |
| `test` | Tests & Verification |
| `ci`, `build`, `chore` | Maintenance & CI |

A `!` after the type or scope, or a `BREAKING CHANGE:` line in the body, forces a major bump in `auto` mode. An
unconventional subject still appears under Other Changes, but it cannot trigger a minor or major bump.

Rules:

- Subject in the imperative, lower case after the colon, no trailing period.
- One logical change per commit.
- Do not put a bare issue number in the subject; link the issue in the PR body.
- Do not force-push a branch under review unless a reviewer asks. A new commit is easier to follow.

## The pull request

Push, then open the PR against `main`. The body starts with the banner this repo uses:

```md
> [!NOTE]
> AI-GENERATED CONTENT

### 1. What Was Done
### 2. Why & Context
### 3. Overall Workflow & Project Status
```

Then the substance:

- **What Was Done** — the files and the behaviour, in plain English. No raw diff.
- **Why & Context** — the problem, the rule it satisfies, the alternatives rejected.
- **Overall Workflow & Project Status** — the stage, what was verified, what is left.
- **Verification** — the exact command and the output you saw. A test name, a matcher `RESULT`, a log line.
- **Limits** — one sentence each. What a stock client sees, which mod version is not covered.

Do not write `#<number>` for anything except a real issue or PR reference. Write `Step 2`, `commit 3`,
`Topic 4`.

## Review

This repo uses adversarial review: two independent reviewers, a fresh pair every round, both hostile and read-only.

- A reviewer checks the technical claims against the source. A wrong signature, an invented class, a stale line
  number or a command that does not exist is a rejection.
- A reviewer checks that no artifact-style document crept in beyond the requested set.
- A finding is fixed, not argued. The loop repeats with a new pair until both approve the live head commit.
- An approval for an older commit does not count. The approval must be for the current head SHA.

Write the fix as a normal commit. Do not rewrite history to make a finding disappear.

## Merge

`main` is unprotected, but a local guard blocks `gh pr merge`. Merge through the REST API with the reviewed head
SHA pinned:

```sh
gh api -X PUT repos/koreaeatsrice/patchee/pulls/<n>/merge \
  -f merge_method=squash -f sha=<head-sha>
```

Squash only. Read the response back and confirm `merged: true` and a `merge_commit_sha`. Delete the branch
afterwards. A feature PR does not cut a release; that is [../ops/RELEASING.md](../ops/RELEASING.md).

## After the merge

- Confirm the `ci.yml` run on `main` is green.
- If the change is user-visible, confirm `docs/FEATURES.md`, `docs/SETTINGS.md` and the README bullet are in the
  same PR. A follow-up docs PR is allowed but the release should not ship without them.
- Do not tag, publish or touch the game server from a feature branch.

## Common mistakes

| Mistake | Why it fails |
|---|---|
| Mixing a refactor with a feature | The reviewer cannot tell which line changed the behaviour. |
| A subject like `update stuff` | It lands in Other Changes and cannot bump the version. |
| Writing `#12` in prose | It reads as a reference to issue 12 when it may not be. |
| Merging on one approval | The process requires two, for the live head. |
| Force-pushing after a review | The approval no longer matches the diff. |
| Adding a report Markdown | Forbidden. Put it in the PR conversation. |
