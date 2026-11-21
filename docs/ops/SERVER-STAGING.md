# Server staging and rollback

For the owner or operator installing a Patchee update on a GTNH server. It covers the artifact, the install, the
post-install check and the rollback. The release itself is [RELEASING.md](RELEASING.md); first install is
[../guide/GETTING-STARTED.md](../guide/GETTING-STARTED.md).

## The artifact

Take the plain `patchee-<version>.jar` from the published GitHub release. Do not use the `-dev` jar and do not build
on the server. The file name includes the version, and the release page is the only source.

## Install

1. Download the jar.
2. Stop the server. Patchee loads at boot; a reload does not pick up a new jar.
3. Keep the jar being replaced. The convention used with this project is to move the old `mods/patchee-*.jar` into
   `mods/backup/`.
4. Put exactly one `patchee-<version>.jar` in `mods/`. If two Patchee jars are present, FML sees the same modid
   twice and aborts the boot. Delete the older one.
5. Start the server.

## After the boot

Read `logs/latest.log`:

- the banner names the version: `[patchee] Patchee <version> — enabled=...`;
- the arm lines for the mods you run;
- `[SelfTest] result: N passed, 0 failed` if you ran the self-test.

For a first install after a pack update, run the self-test once:

1. set `B:runSelfTest=true` in `config/patchee.cfg`;
2. restart;
3. read the result line and any `FAIL`;
4. set it back to `false` and restart, or leave it off until the next check.

A `SKIP` is a missing mod, not a failure. A `FAIL` is a real problem; the line names the check.

## A pack update removes Patchee

A GTNH pack update replaces the `mods` folder, so Patchee disappears. Reinstall the newest jar after every pack
update. `config/patchee.cfg` survives in `config/`, but the pack can replace GregTech's config, so re-check the
pollution lines after an update.

## Rollback

The jar is the only thing that has to change.

1. Stop the server.
2. Move the new `mods/patchee-<version>.jar` out of `mods/` (or into `mods/backup/`).
3. Move the previous jar from `mods/backup/` back into `mods/`.
4. Start the server. Confirm the banner names the restored version, or that no `[patchee]` banner appears if you
   rolled back to "not installed".

With no backup jar, delete Patchee from `mods/` and restart. The server returns to stock behaviour. No world data is
migrated and no block or item is removed, because Patchee registers none. `config/patchee.cfg` can stay; it is
ignored when the jar is absent.

A rollback does **not** undo:

- `config/GregTech/Pollution.cfg` and `config/GregTech/Client.cfg`, which Patchee rewrote with a timestamped
  `.bak` next to each. Restore the `.bak` and restart if you want the previous values.
- `config/veinminer/`, rewritten by VeinConfig with no `.bak`. VeinMiner will accept its own settings once Patchee
  stops rewriting them.
- `config/patchee.cfg`, which keeps the last values. Harmless.

## What not to do

- Do not drop a second Patchee jar in `mods/` "just in case". The boot aborts.
- Do not edit a released jar, and do not patch a third-party jar on disk.
- Do not restart the server as part of a docs or feature PR. Staging is a separate operation.
- Do not copy a `-dev` jar to a production server.

## See also

- [RELEASING.md](RELEASING.md) — how the artifact is made.
- [../guide/GETTING-STARTED.md](../guide/GETTING-STARTED.md) — first install.
- [../guide/TROUBLESHOOTING.md](../guide/TROUBLESHOOTING.md) — a boot that fails.
