# Lootr Prospector

A small Forge 1.20.1 addon for [Lootr](https://www.curseforge.com/minecraft/mc-mods/lootr):
a **Divining Rod** that finds Lootr containers within 64 blocks that **you**
have not opened yet.

Lootr gives every player their own loot state. This mod reads Lootr's public
`IHasOpeners` data on the server and only reports containers whose opener set
does not contain the scanning player's UUID, so a chest another player has
already looted is still visible to you.

## Features

- Right-click to scan a 64-block sphere around the player.
- Finds Lootr chests, trapped chests, barrels, shulkers, `lootr_inventory`
  blocks and (optionally) Lootr chest minecarts.
- Server-authoritative filtering and cooldown.
- Action bar summary, optional nearest-first coordinate list in chat, and sound
  feedback.
- Client-side through-wall gold outlines for up to 128 results, for 15 seconds.
- Configurable radius, cooldown, highlight duration, result cap, minecart
  scanning, sounds and highlight toggle.
- Only scans loaded chunks; it never forces chunk loading.

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

3. The mod jar is written to `build/libs/lootrprospector-0.1.0.jar`.

For development, `.\gradlew.bat runClient` and `.\gradlew.bat runServer` use
the local Lootr jar through ForgeGradle.

## Usage

- Creative: the Divining Rod is in the **Tools & Utilities** tab.
- Survival recipe (shapeless): amethyst shard + compass + stick.
- Right-click air or a block to scan. While holding the rod, right-clicking a
  container scans instead of opening it.
- Default cooldown: 5 seconds.
- Config file: `config/lootrprospector-common.toml`.

## Configuration

| Key | Default | Meaning |
|---|---:|---|
| `scanRadius` | 64 | Scan sphere radius in blocks (8–128) |
| `cooldownTicks` | 100 | Cooldown between scans |
| `highlightTicks` | 300 | Client highlight duration (15 s) |
| `maxHighlights` | 128 | Maximum positions sent/highlighted |
| `chatListLimit` | 5 | Nearest coordinates listed in chat; 0 disables |
| `scanMinecarts` | true | Also scan Lootr chest minecarts |
| `enableHighlight` | true | Send positions for client outlines |
| `playSound` | true | Play scan feedback sounds |
| `showUnloadedHint` | true | Warn when some chunks in range are unloaded |
| `debugLogScan` | false | Log scan counts and duration |

## Known limitations

- Only **loaded** chunks are scanned. Chunks at the edge of a very low view
  distance may be missing; the mod warns instead of forcing chunk loads.
- Lootr's `openers` records "opened", not "loot still available". If you enable
  Lootr's refresh features, a refreshed container may still count as opened.
- `lootr:containers` includes `lootr:lootr_inventory`; all Lootr containers are
  treated as valid targets.
- No Lootr means the mod will not load (mandatory dependency).

## License

Mod code: All Rights Reserved. The Forge MDK files included in this repository
retain their original license; see [`LICENSE.txt`](LICENSE.txt). Lootr is a
separate project and is not bundled here.
