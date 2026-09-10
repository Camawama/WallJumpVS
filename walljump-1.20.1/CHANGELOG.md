# Changelog

## 1.20.1-1.2.0 (unreleased) — rotated-ship wall jumping

### 2026-09-10 pass: ship edge cases, ledge pose, config, block list, sounds, cling damage

#### Valkyrien Skies

- **Ship walls are found by the face touched, not by which probe found them.** The four directional probes each contained the player's whole box, so a hull the box was pressed into answered to all four at once and the cling direction (and the ledge pose's arm aim) was whichever came out of a `HashSet` first. One probe now collects every ship block the box touches, classifies each by the face it meets (the least-penetrated axis, turned into world space), throws out floors and ceilings, and keeps the deepest wall. The cardinal direction and the pose's bearing are derived from that face's real normal.
- **The "ground below" check no longer mistakes a leaning hull for a floor.** It used to shift the whole body box down 0.8 and ask whether any ship block overlapped it, so a hull face tilted a few degrees (which leans *out* below the feet) refused every cling on that face. Ground is now found by rays down from the footprint, and only a face within 60° of level counts. Most likely cause of "the bottom of a tipped pillar can't be clung to".
- **Sliding down a ship wall now ends on the ground.** A ship cling is a position hold, which never triggers `onGround`; the slide now looks for a floor within its descent and steps off onto it, handing the ship's motion back so a moving deck carries the player on.
- **The hold is pushed back out of the hull** along the contact normal each tick, so a tilted or rolling hull no longer swallows the player as they slide.
- The ledge probe reaches further into a ship hull (0.35 instead of 0.15 blocks), since a tilted hull leans away from a probe aimed at the cardinal.
- The pose's ledge-arm aim and grip-arm choice use the hull's true bearing; other players receive it (and whether the wall is a ship) in the cling sync packet. Forge network protocol bumped to 2.
- `debugShipCling` now logs *why* a cling was refused on the key press, and what the hull probe touched (block, face, depth).

#### Wall cling

- **Ledge arms reach forward onto a low ledge.** When a grab landed high, the ledge sat below the shoulders and the "cosine" arm model swung the arms straight down beside the body, holding air. The arms now always reach out past the wall face to where the ledge is, so a low ledge is held with the arms angled down and forward onto it.
- In an inside corner the cling keeps the wall it started on rather than swapping grips, and out of a cling the wall most squarely in front is preferred. Wall sets are `EnumSet`s now, so nothing depends on hash order.
- **Wall slide sound.** Sliding scrapes the wall's own block hit sound every 0.35 blocks of descent, quietly and with a little pitch variation (`playSlideSound`, client, default on).
- **Cling fall damage.** Catching a wall after a fall of `clingFallDamageMinDistance` blocks or more (default 6) deals `clingFallDamageFraction` (default 0.35) of the damage the ground would have done, as a new `walljumpunbound:wall_cling` damage type that bypasses armour and is reduced by Feather Falling like real fall damage. Computed on the server from its own fall record, on the first cling packet. `clingFallDamage` (server, default on).

#### Config

- Renamed the confusing pairs: `useWallJump` → `wallJumpEnabled`, `useDoubleJump` → `doubleJumpEnabled`, `enableWallJump` → `wallJumpEnchantment`, `enableDoubleJump` → `doubleJumpEnchantment`, `enableSpeedBoost` → `speedBoostEnchantment`. Old keys in an existing JSON are ignored (defaults apply).
- Options are grouped into Wall Jump / Other Movement / Block List / Valkyrien Skies / Enchantments tabs, every option has a tooltip (with a server/client note), and `CONFIG.md` at the repo root documents them all.
- **Defaults:** `enableEnchantments` is now off (double jump was already off).

#### Block list

- Entries can be block ids, `#block/tags`, `*wild?cards*` or `/regular expressions/`; see `CONFIG.md`. Invalid entries are logged and skipped.

### Valkyrien Skies

- **Wall cling and wall jump now work on rotated ships.** Wall detection used four thin probes centered on the player, reaching ~0.36 blocks out along the cardinal axes; a rotated ship stops the player at a *corner* of their bounding box (~0.42–0.6 blocks from center), so the probes never reached the wall. Ship walls are now probed with the player's whole bounding box — which already touches the hull at any rotation — expanded slightly in each direction. World-grid walls keep the vanilla probes.
- **Ship block tests are now exact for any orientation.** The probe is treated as an oriented box in shipyard space and tested against block collision boxes with a separating-axis test, instead of the previous enclosing-AABB approximation. Rotated and pitched ships neither miss touching blocks nor report phantom walls, and mere surface contact no longer counts as a wall.
- **Moving and turning ships no longer break the cling.** Ship-relative velocity now includes the rotational component (`omega × r`), the slide/cling state machine runs on velocity relative to the ship, and the ship's vertical motion at the anchor point is added to the player's motion each tick — so the player rides an ascending, descending, or turning ship while clinging instead of being left behind.
- **The cling ground check now sees ship decks** (closes the M3 gap from 1.1.0). The "0.8 blocks below" check also tests ship blocks, so you can no longer cling while hovering just above a deck.
- **New "Valkyrien Skies" config category**:
  - *Enable Valkyrien Skies Compat* (default on) — master switch for all ship integration.
  - *Ship Wall Detection Range* (default 0.06, 0.01–0.5) — how far beyond the player's bounding box ship walls are detected; raise it if clinging to rotated hulls feels finicky.
  - *Cling Sticks To Moving Ships* (default on) — whether the cling anchor follows the ship's movement and rotation.

## 1.20.1-1.1.0 (2026-07-18) — audit fixes

Full audit pass over the codebase (see `AUDIT.md` at the repo root). Every change below references the audit finding it resolves.

### Crash / launch fixes

- **Forge no longer crashes when Valkyrien Skies is not installed** (C1). All VS calls moved into a new `compat/VSCompat` class that is only loaded when the `valkyrienskies` mod is present (checked via `ModList`/`FabricLoader`). `mods.toml` now declares VS as an *optional* dependency (`[2.4.0,)`, ordering AFTER) so the ship features simply switch off without it.
- **Fixed Fabric entrypoints** (C2). `fabric.mod.json` pointed at the old `net.jahirtrap.walljump.*` classes; it now points at `net.cama.walljumpvs.WallJumpMod` / `WallJumpClient`. The Fabric build previously crashed at launch.
- **Fixed the Fabric mixin config** (C3). Renamed `walljump.mixins.json` → `walljumpvs.mixins.json` (matching the `${mod_id}.mixins.json` reference in `fabric.mod.json`), corrected the package to `net.cama.walljumpvs.init.mixin`, switched entries to short class names, and added the `walljumpvs.refmap.json` refmap reference.

### Valkyrien Skies

- **Ported VS ship wall support to Fabric** (C4). The Fabric `WallJumpLogic` now has the same ship wall detection, cling, and slide behavior as Forge, via the same `VSCompat` class. Added optional VS dependencies to `fabric/build.gradle` and a `suggests` entry in `fabric.mod.json`.
- **Wall cling now follows moving ships** (H2). The cling anchor is stored in shipyard space when the wall belongs to a ship and transformed back to world space every tick, so the player sticks to the wall while the ship moves and rotates. Previously the anchor was a frozen world coordinate and the ship would sail away from (or into) the clinging player.
- **Cling velocity checks are now ship-relative** (H2). The "moving up too fast to cling" (`> 0.1`) and "falling too fast to cling" (`< -0.8`) thresholds now use the player's velocity relative to the ship (`Ship.getVelocity()`, converted to per-tick), so clinging works on ascending/descending ships.
- **Ship wall detection now tests real collision shapes** (M1). Instead of two `isSolid` point samples at feet/head height, the probe AABB is transformed into shipyard space and tested against each block's actual collision boxes. Slabs, fences, and other partial blocks are now detected correctly, and near-edge false negatives are gone. (The transformed box is the enclosing AABB of the rotated probe, so detection is slightly generous on strongly rotated ships.)
- **Ship cling reach matches vanilla reach** (M2). The ship probe previously used the player's full body width (~double the vanilla distance), letting players cling while visibly floating away from the hull. Both paths now use the same half-width + tolerance distance.
- **Auto-rotation is skipped for ship walls** (M3). Snapping the camera to a cardinal direction is wrong for rotated ships, so `autoRotation` only applies to world-grid walls.
- Ship code now uses `BlockPos.containing(...)` instead of manual `Math.floor` casts (L6).

### Gameplay / correctness

- **Enchantments are always registered; config now gates availability instead** (H4). Previously the registry entries were skipped entirely when disabled in the local config, which (a) destroyed existing enchantments on items when the config changed and (b) could NPE (`RegistryObject.get()` on null) when a client with enchantments disabled joined a server with them enabled. Each enchantment now overrides `isDiscoverable()`/`isTradeable()` based on the config, so disabled enchantments stop appearing in the enchanting table and trades but existing items keep working.
- **Fixed untranslated enchantment names on Forge** (H3). Lang keys updated from `enchantment.walljump.*` to `enchantment.walljumpvs.*` in `en_us`, `es_es`, and `es_mx` to match the actual registry namespace.
- **Unified the Fabric mod id to `walljumpvs`** (C4). Fabric code previously used `walljump` internally, giving it a different config file name, network channel namespace, and enchantment registry namespace than the Forge build and than its own declared mod id.

### Networking

- **Hardened the client→server packets** (M4). `MessageFallDistance` now ignores non-finite values, clamps to `[0, 512]`, and is dropped entirely when neither wall jump nor double jump is enabled on the server. `MessageWallJump` is ignored when wall jump is disabled on the server. (These packets are inherently client-authoritative by design; this bounds the abuse.)
- **Config sync no longer sends buffer padding** (M5, Forge). `PlayerListMixin` sent the netty buffer's entire backing array (256 bytes with garbage tail); it now sends exactly the written bytes.
- **Config sync failures are logged** (M7). `ServerConfig.reset()` no longer swallows reflection exceptions silently; failures are logged per-field via SLF4J.

### Build / publishing

- **Removed the upstream project IDs from the publish config** (H1). Both `publishMods` blocks pointed at Wall-Jump TXF's CurseForge (909143) and Modrinth (oUoetxfR) projects — running a publish would have tried to upload this fork to the original mod's pages. Replaced with `REPLACE_WITH_YOUR_*` placeholders and an explanatory comment.
- **Pinned Valkyrien Skies versions** (M6). Replaced the floating `org.valkyrienskies.core:*:+` dependencies with the exact core version VS 2.4.5 was built against (`1.1.0+3d898dfd55`), declared once in `gradle.properties` (`vs_version`, `vs_core_version`).
- Fabric release type aligned with Forge (`BETA` → `STABLE`) (L5).
- Version bumped to `1.20.1-1.1.0`.

### Repository housekeeping

- Repo-root `README.md` rewritten for the fork (credits upstream Wall-Jump TXF / Wall-Jump!); the original upstream README inside `walljump-1.20.1/` is intentionally preserved (L1). The upstream one-line changelog entry is preserved below.
- Added a repo-root `.gitignore` and untracked the committed `.idea/` directory (L2).
- `AUDIT.md` added at the repo root documenting all findings.

### Known gaps / not applied

- The repo-layout flattening (L3) and moving shared logic into the `common` module (L4) were deliberately **not** done — both are large restructures better done as their own change.
- The cling ground check (`0.8` blocks below the player) still uses world collision only; whether it detects ship decks depends on VS's collision injection (M3, partial).

---

## 1.20.1-1.0.x and earlier (upstream Wall-Jump TXF)

- onFallWallCling config option ([Issue #27](https://github.com/jahirxtrap/walljump/issues/27))
