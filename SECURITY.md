# Security Policy

## Supported Versions

Security updates are provided for the latest minor release line:

| Version | Status |
| ------- | ------ |
| 1.0.x   | Maintained |
| < 1.0.0 | Unsupported |

---

## Reporting a Vulnerability

If you identify a security vulnerability in `patchee` (for example anything
that could abuse the reflection hooks into JABBA or Tinkers' Construct, the
config parsing, or a crafted payload smuggled through a movable TileEntity's
NBT), please report it responsibly.

### How to Report
- Open a private advisory report through GitHub Security Advisories at
  https://github.com/koreaeatsrice/patchee/security/advisories/new
- Or email `koreaeatsrice@gmail.com` with the subject line
  `[SECURITY] patchee Vulnerability Report`.

### What to Include
1. Clear description of the vulnerability and potential impact.
2. Steps to reproduce or proof-of-concept (a small test pack is ideal).
3. Mod version, GTNH pack version, and the relevant `logs/fml-server-latest.log` excerpt.
4. Any suggested remediations or mitigations.

### Response Expectations
Reports are handled on a best-effort basis as time permits. There are no formal
response timelines or resolution SLAs. Confirmed security fixes will be tagged
and released via standard repository releases.

---

## Security Model & Threat Boundaries

Patchee is a **server-side behaviour addon** that patches two other mods at
runtime. It is deliberately small, and its security-relevant boundaries are:

1. **Fixed reflection targets:** only two class/field names are touched —
   `mcp.mobius.betterbarrels.common.items.dolly.ItemBarrelMover#classExtensions`
   and `tconstruct.items.tools.Mattock#shovelMaterials`. No user-supplied class
   name is ever loaded except explicit opt-in entries from the server's own
   `config/patchee.cfg` (`extraDollyClasses`), which are validated as
   `TileEntity` subclasses before use.
2. **No network, no code loading:** the mod opens no sockets, downloads
   nothing, and executes no code from data. Client machines need nothing
   installed.
3. **Fail-soft by design:** if a target mod is absent or has changed shape,
   the affected fix logs and leaves that mod untouched instead of crashing.
   The config-gated self-test only *reads* the patched decisions.
4. **Server operator's control:** both fixes are config-gated
   (`enableDollyFix`, `enableMattockFix`) and can be turned off per server.