# Lootr Prospector

A small Forge 1.20.1 addon for [Lootr](https://www.curseforge.com/minecraft/mc-mods/lootr):
a **Divining Rod** that finds Lootr containers within a configurable radius
(64 blocks by default) that **you** have not opened yet, and reports how many
Lootr containers **you** have opened in the structure you are standing in.

Lootr gives every player their own loot state. This mod reads Lootr's public
`IHasOpeners` data on the server and only reports containers whose opener set
does not contain the scanning player's UUID, so a chest another player has
already looted is still visible to you.

## Features

- Right-click to scan a sphere around the player (64 blocks by default,
  configurable from 8 to 256 blocks).
- Finds Lootr chests, trapped chests, barrels, shulkers, `lootr_inventory`
  blocks and (optionally) Lootr chest minecarts.
- Server-authoritative filtering, cooldown and scan-time budget.
- Action bar summary, optional nearest-first coordinate list in chat, and sound
  feedback.
- Client-side through-wall gold outlines for up to 128 results, for 15 seconds.
- If you are inside a standard Minecraft structure, every scan also reports
  `structure progress opened/total` for Lootr containers in that structure.
- Configurable radius, scan budget, cooldown, highlight duration, result cap,
  minecart scanning, structure progress, sounds and highlight toggle.
- Only scans loaded chunks; it never forces chunk loading. A scan that exceeds
  its time budget stops early and says that the results may be incomplete.

## Requirements

- Minecraft 1.20.1
- Forge 47.x (built against 47.4.28)
- [Lootr](https://www.curseforge.com/minecraft/mc-mods/lootr) 0.7.35.90
  (declared as a mandatory dependency)

## Building

1. Put `lootr-forge-1.20-0.7.35.90.jar` into `libs/` (see
   [`libs/README.md`](libs/README.md)).
2. Run:

   ```powershell
   .\gradlew.bat build --console=plain
   ```

3. The mod jar is written to `build/libs/lootrprospector-0.3.0.jar`.

For development, `.\gradlew.bat runClient` and `.\gradlew.bat runServer` use
the local Lootr jar through ForgeGradle.

## Usage

- Creative: the Divining Rod is in the **Tools & Utilities** tab.
- Survival recipe (shapeless): amethyst shard + compass + stick.
- Right-click air or a block to scan. While holding the rod, right-clicking a
  container scans instead of opening it.
- While standing inside a standard structure, the action bar also reports the
  structure's Lootr progress, for example `structure progress: 32/80`.
- Default cooldown: 5 seconds.
- Config file: `config/lootrprospector-common.toml`.
- On a dedicated server, edit the **server's** config file. The server's values
  control scanning and cooldown; a client's local file only affects local
  display such as the tooltip.

## Configuration

| Key | Default | Meaning |
|---|---:|---|
| `scanRadius` | 64 | Scan sphere radius in blocks (8–256) |
| `scanTimeBudgetMs` | 5 | Soft per-scan time budget in milliseconds; 0 = unlimited. Exceeding it stops the scan early and reports incomplete results |
| `enableStructureProgress` | true | Report opened/total Lootr containers for the structure you are in |
| `structureProgressBudgetMs` | 3 | Soft time budget for the structure progress lookup; 0 = unlimited |
| `cooldownTicks` | 100 | Cooldown between scans |
| `highlightTicks` | 300 | Client highlight duration (15 s) |
| `maxHighlights` | 128 | Maximum positions sent/highlighted |
| `chatListLimit` | 5 | Nearest coordinates listed in chat; 0 disables |
| `scanMinecarts` | true | Also scan Lootr chest minecarts |
| `enableHighlight` | true | Send positions for client outlines |
| `playSound` | true | Play scan feedback sounds |
| `showUnloadedHint` | true | Warn when some chunks in range are unloaded |
| `debugLogScan` | false | Log scan chunk/block-entity counts, budget status and duration |

Forge configs are normally reloaded when the file is edited. If a value does
not take effect, restart the world/server. Values outside the documented range
are corrected by Forge's config validator.

## Structure progress

When you scan while standing inside a structure, the mod looks up the
`StructureStart` for that position and counts every Lootr container inside the
structure's pieces:

- `total` is the number of Lootr containers in the structure, whether opened or
  not;
- `opened` is how many of those contain your UUID in Lootr's opener set.

The lookup supports vanilla structures, datapack structures, and modded
structures that use Minecraft's normal `Structure` / `StructureStart` /
`StructurePiece` system. It does not recognize feature-based content such as the
vanilla dungeon (monster room), or mods that generate dungeons as features
instead of structures.

Only loaded chunks are counted, and the structure's start chunk must already be
loaded. Large structures may therefore show a partial total and a warning. The
lookup never force-loads a chunk. If several structures overlap at your
position, the alphabetically first structure ID is reported.

## Performance and high scan radii

The scanner does **not** walk every block. It visits loaded chunk columns in
range and iterates their block-entity maps, so the cost is roughly:

```
(number of loaded chunk columns in range) × (block entities per chunk)
```

The chunk-column count grows quadratically with radius:

| `scanRadius` | Chunk columns in the square range |
|---:|---:|
| 64 | 81 |
| 128 | 289 |
| 192 | 625 |
| 256 | 1089 |
| 512 (not allowed) | 4225 |

Only loaded chunks are actually scanned; `getChunkNow` returns `null` for the
rest without loading them. If the radius is larger than the server view
distance, most of the extra area is simply unloaded and cannot be searched. At
a typical view distance the number of loaded chunks, not the configured radius,
is the real limit on work.

Other costs to keep in mind:

- `maxHighlights` caps both the network packet and the number of boxes drawn by
  the client every frame. Keep it at 128–512.
- `scanMinecarts` uses an entity AABB query; on servers with very large entity
  counts it can be more expensive than the block-entity scan. Disable it if you
  do not need minecart detection.
- `scanTimeBudgetMs` is the safety valve. A scan checks the budget between
  chunks (and every 256 block entities) and stops when it is exceeded, so one
  scan cannot block the server tick indefinitely. The default is 5 ms; setting
  it to `0` removes the limit and is only recommended for small radii or
  single-player worlds.
- `structureProgressBudgetMs` does the same for the additional structure lookup.
  It only runs when you are inside a structure, but that lookup may cover a
  larger area than the normal scan radius. The default is 3 ms.
- Cooldowns are per player. Several players scanning in the same tick each pay
  their own scan cost; there is no global rate limiter.

For a large radius, the practical result is usually **incomplete rather than
slow**: the scan stops at the time budget and the action bar says so. Raise
`scanTimeBudgetMs` if you prefer completeness and accept a longer tick, or
lower the radius. Do not set both a huge radius and an unlimited budget on a
public server.

## Known limitations

- Only **loaded** chunks are scanned. Chunks beyond the server view distance
  are missing; the mod warns instead of forcing chunk loads.
- Despite the 3D sphere check, chunk columns cover the full world height, so a
  tall world with many block entities increases the per-column cost.
- `scanTimeBudgetMs` can intentionally truncate a scan; this is reported in the
  action bar and in the debug log.
- Structure progress is based on Minecraft's `Structure` system. Vanilla
  dungeons (monster rooms) and modded feature-based dungeons are not structures
  and will not show progress.
- Structure progress only counts loaded chunks inside the structure's pieces.
  Large structures, or structures whose start chunk is not loaded, can show a
  partial total with a warning.
- Lootr's `openers` records "opened", not "loot still available". If you enable
  Lootr's refresh features, a refreshed container may still count as opened.
- `lootr:containers` includes `lootr:lootr_inventory`; all Lootr containers are
  treated as valid targets.
- No Lootr means the mod will not load (mandatory dependency).

## License

Mod code: All Rights Reserved. The Forge MDK files included in this repository
retain their original license; see [`LICENSE.txt`](LICENSE.txt). Lootr is a
separate project and is not bundled here.
