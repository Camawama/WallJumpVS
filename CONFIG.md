# WallJump Unbound configuration

The config lives in `config/walljumpunbound.json` (created on first launch) and can be edited in game through the mod's config screen (Mods list on Forge, Mod Menu on Fabric). Every option has a tooltip there; this file is the same information in one place, since the JSON file itself cannot hold comments.

**Server** options are the rules of play: on a dedicated server the server's values are sent to every client on join and override that client's own file. **Client** options only affect your own game.

If you are upgrading from an earlier build: `useWallJump`, `useDoubleJump`, `enableWallJump`, `enableDoubleJump` and `enableSpeedBoost` were renamed (see below). Old keys are ignored and the new ones start at their defaults.

## Wall Jump

| Option | Default | Scope | What it does |
| --- | --- | --- | --- |
| `wallJumpEnabled` (was `useWallJump`) | `true` | Server | Lets every player cling to walls and wall jump. When off, only players wearing boots with the Wall Jump **enchantment** can. |
| `wallJumpHeight` | `0.55` | Server | Upward speed of a wall jump. A normal jump is 0.42. |
| `maxWallJumps` | `72000` | Server | Wall jumps allowed in a row before touching the ground again. 72000 is as good as unlimited. |
| `exhaustionWallJump` | `0.8` | Server | Hunger spent per wall jump, in exhaustion points (sprinting costs about 0.1 per block). |
| `allowReClinging` | `true` | Server | Cling again to the wall you just jumped off. When off you must first fall a block below your jump, or reach a different wall. |
| `onFallWallCling` | `true` | Server | Catch a wall while falling faster than 0.8 blocks a tick. When off, a fast fall cannot be stopped by clinging. |
| `autoRotation` | `false` | Client | Snap your view to face straight away from the wall when you cling. Skipped on Valkyrien Skies ships. |
| `wallSlideDelay` | `15` | Server | Ticks a cling holds before you start sliding (20 ticks = 1 second). Hanging from a ledge never slides. |
| `stopWallSlideDelay` | `72000` | Server | Ticks of sliding before your grip gives out and you drop. |
| `ledgeGrab` | `true` | Server | Grab the top of a wall that is within arm's reach. A ledge hold never slides; turning more than 90° away lets go. |
| `minFallDistance` | `3.0` | Server | Falls up to this many blocks do no damage (vanilla: 3). Catching a wall never hurts below it either. |
| `clingFallDamage` | `true` | Server | Catching a wall after a long fall hurts: a share of the damage the ground would have done. Armour does not reduce it; Feather Falling and Protection do, like real fall damage. |
| `clingFallDamageMinDistance` | `6.0` | Server | The fall, in blocks, from which catching a wall starts to hurt. Never lower than `minFallDistance`. |
| `clingFallDamageFraction` | `0.35` | Server | Share of the ground's fall damage dealt by catching a wall instead. With the default, a 10-block fall caught on a wall costs 3 health (1.5 hearts) instead of the 7 the ground would take. |
| `wallClingPose` | `true` | Client | Draw the climbing pose on clinging players: one arm on the wall, or both hands over a ledge. |
| `playFallingSound` | `true` | Client | Rushing wind while falling fast. |
| `playSlideSound` | `true` | Client | Scrape the wall's own block sound while sliding down it. |

## Other Movement

| Option | Default | Scope | What it does |
| --- | --- | --- | --- |
| `doubleJumpEnabled` (was `useDoubleJump`) | `false` | Server | Lets every player jump once more in mid-air. When off, only the Double Jump **enchantment** grants extra jumps. |
| `onFallDoubleJump` | `true` | Server | Allow the mid-air jump while falling faster than 0.8 blocks a tick. |
| `sprintSpeedBoost` | `0.0` | Server | Extra sprint speed for every player, on top of the enchantment. 0 is off. |
| `elytraSpeedBoost` | `0.0` | Server | Extra speed while holding sprint on an elytra, on top of the enchantment. 0 is off. |
| `stepAssist` | `true` | Server | Walk up single-block steps without jumping, even mid-air, and hop over fences. |

## Block List

| Option | Default | Scope | What it does |
| --- | --- | --- | --- |
| `blockListMode` | `BLACKLIST` | Server | `DISABLED`: any solid block can be clung to. `BLACKLIST`: any block except those listed. `WHITELIST`: only the blocks listed. |
| `blockList` | `[]` | Server | The blocks, one entry per line. An empty list means every block is allowed whatever the mode. |

Each `blockList` entry is one of:

| Form | Example | Matches |
| --- | --- | --- |
| Block id | `minecraft:ice` | That block. A bare name (`ice`) means `minecraft:ice`. |
| Block tag | `#minecraft:logs` | Every block in the tag, including tags from other mods and datapacks. |
| Wildcard | `minecraft:*_glass`, `*:*_ore` | `*` stands for any run of characters, `?` for a single one. |
| Regular expression | `/minecraft:(oak\|spruce)_.*/` | A Java regular expression between slashes, matched against the whole id. |

Entries that do not parse are logged at startup and skipped. A wall is checked at the two blocks the player faces: if either is a solid block the list allows, the wall can be clung to.

## Valkyrien Skies

These only do anything when Valkyrien Skies is installed.

| Option | Default | Scope | What it does |
| --- | --- | --- | --- |
| `enableVSCompat` | `true` | Client | Cling to and jump off ship hulls, moving and rotated ones included. |
| `shipWallDetectionRange` | `0.06` | Client | How far past your hitbox ship walls are felt for, in blocks. Raise it if clinging to tilted hulls feels finicky. |
| `stickToMovingShips` | `true` | Client | Carry you along with the ship's movement and rotation while clinging. |
| `debugShipCling` | `false` | Client | Write ship cling and wall detection details to the game log, including why a cling was refused and what the hull probe touched. Turn it on before reporting a ship bug. |

## Enchantments

| Option | Default | Scope | What it does |
| --- | --- | --- | --- |
| `enableEnchantments` | `false` | Server | Master switch. When off, none of the mod's enchantments turn up in enchanting tables or trades. Items already enchanted keep working. |
| `wallJumpEnchantment` (was `enableWallJump`) | `true` | Server | Boots enchantment granting wall clinging and jumping to its wearer. Only matters when `wallJumpEnabled` is off. |
| `doubleJumpEnchantment` (was `enableDoubleJump`) | `true` | Server | Boots enchantment granting one extra mid-air jump per level, up to II. |
| `speedBoostEnchantment` (was `enableSpeedBoost`) | `true` | Server | Boots and chestplate enchantment that speeds up sprinting and elytra flight. |
| `speedBoostMultiplier` | `0.5` | Server | How much speed each level of Speed Boost adds. |

The three per-enchantment switches only matter while `enableEnchantments` is on.
