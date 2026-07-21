# Wall Jump VS — Codebase Audit

**Date:** 2026-07-18
**Scope:** Full repository (`walljump-1.20.1/` — common, fabric, forge modules), focused on the Valkyrien Skies (VS) integration, cross-loader consistency, networking, and build/publishing configuration.

All file paths below are relative to `walljump-1.20.1/` (the inner project folder).

---

## Summary

| Severity | Count | Theme |
|----------|-------|-------|
| Critical | 4 | Forge crashes without VS installed; Fabric build is fundamentally broken (entrypoints, mixins); Fabric has no VS support at all |
| High | 4 | Publishing targets the upstream mod's project pages; wall cling doesn't follow moving ships; broken enchantment names; config-gated registry registration |
| Medium | 7 | VS wall-detection accuracy, oversized ship cling reach, client-authoritative packets, config sync sloppiness, floating dependency versions |
| Low | 6 | Branding/metadata leftovers, committed `.idea/`, code duplication, misc polish |

The Forge module is the only one with VS support, and it currently makes VS a **hard, undeclared** dependency. The Fabric module appears to have never been updated after the package rename and cannot launch.

---

## Critical

### C1. Forge build crashes if Valkyrien Skies is not installed
`forge/src/main/java/net/cama/walljumpvs/logic/WallJumpLogic.java:34-36` imports `org.valkyrienskies.core.api.ships.Ship`, `VSGameUtilsKt`, and `VectorConversionsMCKt` directly, and `updateWalls()` calls `checkShipWall()` every client tick. If VS (and its dependency Kotlin for Forge) is not present, the class fails to link with `NoClassDefFoundError` on the first player tick — a hard crash.

Meanwhile `forge/src/main/resources/META-INF/mods.toml` declares **no dependency** on `valkyrienskies` or `kotlinforforge`, so Forge won't even show a friendly "missing dependency" screen, and the VS jar is not bundled via jarJar.

**Fix (pick one):**
- **Make VS optional (recommended):** move all VS calls into a separate class (e.g. `VSCompat`) that is only touched when `ModList.get().isLoaded("valkyrienskies")` is true. This keeps the mod usable as a plain wall-jump mod.
- **Or make it mandatory:** add `[[dependencies.walljumpvs]]` entries for `valkyrienskies` (and note Kotlin for Forge comes with it) in `mods.toml` so users get a proper dependency error instead of a crash.

### C2. Fabric entrypoints point at classes that no longer exist
`fabric/src/main/resources/fabric.mod.json:21-28` declares entrypoints `net.jahirtrap.walljump.WallJumpMod` and `net.jahirtrap.walljump.WallJumpClient`, but the classes were repackaged to `net.cama.walljumpvs.*`. The Fabric jar will crash instantly at launch ("entrypoint not found").

### C3. Fabric mixin config is broken three ways
- `fabric.mod.json:29-31` references `${mod_id}.mixins.json` → `walljumpvs.mixins.json`, but the actual resource is named `walljump.mixins.json` → mixin config not found (and it's `required: true`).
- Inside `fabric/src/main/resources/walljump.mixins.json`, `package` is `com.jahirtrap.walljump.init.mixin` while the mixin entries are fully-qualified `net.jahirtrap.walljump.init.mixin.*` — mutually inconsistent.
- Neither matches the real package, `net.cama.walljumpvs.init.mixin`.

**Fix:** rename the file to `walljumpvs.mixins.json` and mirror the Forge config (`forge/src/main/resources/walljumpvs.mixins.json` is correct: package `net.cama.walljumpvs.init.mixin` with short class names). Fabric Loom also expects the refmap name set in `fabric/build.gradle` (`walljumpvs.refmap.json`) to be referenced by the config.

### C4. Fabric has no Valkyrien Skies support at all
`fabric/src/main/java/net/cama/walljumpvs/logic/WallJumpLogic.java` is still the vanilla upstream logic — no `checkShipWall`, no ship wall map. The headline feature of this fork exists only on Forge. VS 2 ships a Fabric 1.20.1 build, so this is portable.

**Fix:** either port the ship-wall logic to the Fabric module (add the VS Fabric dependency to `fabric/build.gradle`), or clearly mark the Fabric build as "no VS support yet" and stop publishing it alongside the Forge build with the same description ("with VS support!").

Related inconsistency: Fabric `WallJumpMod.MODID = "walljump"` (`fabric/.../WallJumpMod.java:11`) while Forge uses `"walljumpvs"` and `fabric.mod.json` declares id `walljumpvs`. This makes the Fabric build register enchantments under `walljump:*`, use `walljump:*` network channels, and write `walljump.json` config — all different from Forge. Unify on `walljumpvs`.

---

## High

### H1. Publishing config still targets the upstream mod's project pages
Both `forge/build.gradle:121-140` and `fabric/build.gradle:80-99` have `publishMods` blocks with CurseForge project `909143` and Modrinth project `oUoetxfR` — those are **Wall-Jump TXF's** (the upstream mod's) project IDs. Running `publishMods` with valid tokens would attempt to upload your fork to the original author's pages. Replace with your own project IDs (or delete the blocks until you have them).

### H2. Wall cling/slide does not follow a moving ship
`forge/.../WallJumpLogic.java:90-92,116` captures `clingX`/`clingZ` in **world space** when the cling starts and re-asserts them every tick with `pl.setPos(clingX, pl.getY(), clingZ)`. On a moving or turning ship the wall travels away from that fixed point, so the player either detaches (walls empty → drop/jump state) or gets pushed through the hull by VS collision. Vertical ship motion also fights the slide logic: `canWallCling` rejects players with `deltaMovement.y > 0.1`, which is always true while standing near a wall on an ascending ship, and the slide's `motionY = -0.1` is relative to the world, not the deck.

**Fix:** when the cling wall came from a ship (you already know via `shipWallBlockPositions`), store the cling anchor in **ship space** (`ship.getWorldToShip().transformPosition(...)` at cling time) and transform it back with `ship.getShipToWorld()` each tick before `setPos`. Ideally also evaluate cling velocity relative to the ship (`player velocity − ship velocity at player position`) for the `> 0.1` and `< -0.8` thresholds so cling/slide feels identical on a moving ship.

### H3. Enchantment names are untranslated on Forge
Forge registers enchantments under `walljumpvs:*` (`forge/.../ModEnchantments.java:17-21`), so vanilla derives keys like `enchantment.walljumpvs.wall_jump`. The shared lang files (`common/src/main/resources/assets/walljumpvs/lang/*.json`) only define `enchantment.walljump.wall_jump` — Forge players see raw translation keys. (Fabric currently matches only by accident of the C4 modid mismatch.) Update the lang keys to `enchantment.walljumpvs.*` once the modid is unified.

### H4. Registry entries are conditionally registered based on config
`ModEnchantments.register(...)` (both loaders) returns `null` when the local config disables an enchantment, so:
- Toggling the config strips already-registered enchantments from existing worlds/items on next launch (missing-registry data loss).
- `ServerConfig` values are **synced from the server** at join, but registration happened at class-load from the **local** config. A client that disabled enchantments locally joining a server with them enabled hits `ModEnchantments.WALL_JUMP.get()` on a null `RegistryObject` → NPE in `canWallJump` (`forge/.../WallJumpLogic.java:147`), and `DoubleJumpLogic.getMultiJumps` likewise.

**Fix:** always register all three enchantments; gate their *effects* and enchanting-table availability (e.g. `isDiscoverable`/`isTradeable` overrides) on config instead.

---

## Medium

### M1. Ship wall detection uses two point samples instead of a collision test
`checkShipWall` (`forge/.../WallJumpLogic.java:195-220`) transforms two points (feet+head, offset along a **world-axis** direction) into ship space and checks `isSolid` at those block positions. Consequences:
- Partial blocks (slabs, trapdoors, fences, walls) and blocks whose collision doesn't fill the cell produce wrong results (`isSolid` is about material, not shape).
- Near block edges or on rotated ships, the sampled cell can miss the block the player is visually touching (false negatives) or hit an interior block (false positives).

A more robust approach: transform the player's probe AABB into ship space (or use VS's raycast/collision utilities) and test the ship's block collision shapes, mirroring what `level.noCollision` does for world blocks. Also note VS2 already injects ship collision into entity collision — verify whether `level.noCollision(axis)` on the expanded AABB already reports ship contact; if it does, the vanilla path plus a "which ship block is it" lookup may be all you need.

### M2. Ship cling reach is double the vanilla reach
`updateWalls` uses `dist = getBbWidth()/2 + 0.06/0.1` for world blocks but `shipDist = getBbWidth() + 0.06/0.1` (`forge/.../WallJumpLogic.java:165-166`) — a full body width (~0.7 blocks from center) for ships. Players can cling to ship walls while visibly floating ~0.3 blocks away. Unless this was deliberate slack for ship motion, use the same half-width formula (plus a small tolerance, e.g. +0.1, if moving ships need it).

### M3. Cling anchor/ground checks ignore ship context
- `canWallCling`'s ground check (`collidesWithBlock(level, bb.move(0, -0.8, 0))`, line 155) only works for ship decks if VS injects ship collision into `noCollision`; if it doesn't, players can cling while hovering just above a ship deck.
- `autoRotation` (line 85-88) snaps the player to a cardinal direction; on a rotated ship the wall's true normal is diagonal, so the snap looks wrong. Consider computing the wall normal from the ship transform when the wall is a ship wall, or disabling auto-rotation for ship walls.

### M4. Client-authoritative fall distance and wall-jump packets
`MessageFallDistance.handle` sets `player.fallDistance` to any client-supplied float with no validation (`forge/.../MessageFallDistance.java:18-24`), and `MessageWallJump.handle` resets fall distance unconditionally. Any modified client can spam these to negate all fall damage regardless of config. Inherited from upstream, but worth hardening: ignore the packets when the corresponding feature is disabled in server config, clamp values (`>= 0`, sane max), and optionally sanity-check that the player is actually near a wall.

### M5. `PlayerListMixin` config sync duplicates serialization and sends buffer garbage
`PlayerListMixin.placeNewPlayer` (both loaders) hand-writes every config field into an `Unpooled.buffer()` and sends `buffer.array()` — the full 256-byte backing array, not just the written bytes, and the field order must be kept in lockstep with `MessageServerConfig.handle` by hand. It works, but it's fragile and wasteful. Give `MessageServerConfig` proper encode/decode of the fields themselves (or at least send `ByteBufUtil.getBytes(buffer, 0, buffer.writerIndex())`).

### M6. Floating dependency versions in the Forge build
`forge/build.gradle:92-94` uses `org.valkyrienskies.core:api:+` (and `impl`, `util`) — builds are not reproducible and can silently break when VS publishes a new core snapshot. Pin the versions matching VS 2.4.5. Also `implementation` for VS/KfF is correct for dev, but consider `compileOnly` + runtime detection if you adopt the optional-dependency route (C1).

### M7. `ServerConfig.reset()` swallows all exceptions
`init/ServerConfig.java:31-49` catches `Exception ignored` around reflection. If a field is ever renamed in one class but not the other, the desync will be silent. Log at minimum.

---

## Low / polish

- **L1. README/branding still upstream.** Both `README.md` files are the Wall-Jump TXF readme with TXF download badges; `CHANGELOG.md` (used verbatim as the publish changelog) contains only an upstream entry; `mod_url`/`mod_issues` in `gradle.properties` are empty. Update for the fork (and keep upstream credit).
- **L2. `.idea/` is committed** (compiler.xml, machine-specific gradle.xml, module .iml files). Add `.idea/` to the root `.gitignore` and `git rm -r --cached` it; the nested `walljump-1.20.1/.gitignore` doesn't cover the repo root.
- **L3. Repo layout.** The project lives in `walljump-1.20.1/` inside a repo whose root only holds a copied README — consider moving the project to the repo root to simplify paths and CI.
- **L4. Fabric/Forge logic duplication.** All gameplay logic is copy-pasted per loader and has already drifted (Fabric missing VS support, minor API differences). The `common` module holds only resources. Longer term, move shared logic into `common` (Architectury-style) so fixes land once.
- **L5. Release channel mismatch.** Fabric publishes as `BETA`, Forge as `STABLE`, both to the same project with the same version string — intentional?
- **L6. Minor code nits.** `BlockPos.containing(x, y, z)` instead of `new BlockPos((int)Math.floor(...))` in `checkShipWall`; `getShipsIntersecting` is called up to 4× per tick with fresh AABBs (cheap, but one call on an inflated box then per-direction checks would be tidier); `Map`/`Set` statics in `WallJumpLogic` are fine client-side but worth a comment that this is single-player-instance state.

---

## Suggested priority order

1. **Fix the Fabric launch breakage** (C2, C3) or temporarily stop shipping the Fabric jar.
2. **Decide VS optional vs. required on Forge and implement it** (C1) — right now every non-VS user crashes.
3. **Unify the modid** (`walljumpvs`) across loaders and fix enchantment lang keys (C4, H3).
4. **Fix publishing project IDs** before ever running a publish task (H1).
5. **Ship-space cling anchoring** (H2) — this is the change that makes "cling and slide on a moving ship" actually work as advertised.
6. Registry registration decoupled from config (H4), then the medium items.
