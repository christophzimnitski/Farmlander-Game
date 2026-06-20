# Farmlander

A hand-rolled 2D farming game in Java, built on [libGDX](https://libgdx.com/) (1.13.1,
LWJGL3 backend). Walk a bounded, hand-authored world; till, plant, water, and harvest
crops; manage a 40-slot inventory. The world is intentionally **finite** (Stardew-style,
not an infinite Minecraft world).

This is a learning project: most systems (the dual-grid soil renderer, the binary save
format, the chunked tile world, the input/asset layers) are written by hand rather than
pulled from an engine, on purpose.

## Requirements

- **JDK 21.** Nothing else to install — the committed Gradle wrapper provisions the
  right Gradle, and the build's Java toolchain pins the language level to 21.
- macOS, Linux, or Windows. On macOS the run adds `-XstartOnFirstThread` automatically
  (GLFW must run on the first thread).

## Run

```bash
./gradlew run --console=plain
```

On macOS you can also **double-click `Play Farmlander.command`** in the project root —
it does exactly the same thing.

Close the window to quit cleanly (this is what saves — see below).

## Controls

| Input | Action |
|-------|--------|
| `W` `A` `S` `D` / arrow keys | Move |
| `1`–`9`, `0` | Select hotbar slot (1–9 → slots 1–9, 0 → slot 10) |
| Left-click a hotbar slot | Select it |
| Left-click the world | Use the held item at the aim target — a tool swings; a held seed is planted |
| `Q` | Use the held tool (acts on the tile in front of you) |
| `E` | Harvest the mature crop under the cursor |
| `Tab` | Toggle the inventory popup (the 3×10 bag above the hotbar) |
| `Esc` | Quit |

**Aim flex:** every tool and planting target the tile in front of the player by default,
but if the mouse is within one tile of that cell you can aim at any of the surrounding 9
cells. The framed cell shows where the action will land.

**Inventory (while `Tab` is open):** drag items to move them (hold to grab more of a
stack); **Shift**-drag grabs the whole stack; **Shift**-click quick-transfers between
hotbar and bag; click a slot to select it, click the dimmed backdrop to close.

**Item names:** hover an item — in the hotbar or the open bag — to see its name on a
small plate by the cursor.

### Tools

Five tools, each held like an inventory item and selected from the hotbar:

| Tool | Action |
|------|--------|
| Hoe | Till bare ground into plantable soil |
| Watering Can | Water tilled soil — wet soil grows its crop ~30% faster, and dries after 30s |
| Pickaxe | Un-till a cell back to bare ground |
| Shovel | Clear the leftover "lump" a harvested root crop leaves behind |
| Axe | Remove a standing trellis/continuous crop (one that leaves no lump) |

### Crops & seeds

Seeds and produce are **distinct items**. You plant a **seed** (consumed); when the crop
matures, harvesting it (`E`) yields **produce** — a sellable good you can't replant. A new
farm starts with a starter seed packet (Potato, Carrot, Cauliflower, Pumpkin). Seeds have
no art yet, so each is shown as a generated paper packet with its crop on the label.

> Harvest is **produce-only** and there's no shop yet, so seeds are finite. As a stopgap,
> a seedless player is re-granted the starter packet on load — this becomes fresh-worlds-only
> once a shop (the real seed source) exists.

## Saves

The world saves to `~/.farmlander/objects.dat` (outside the repo). It saves on **autosave**
(every 120s, written on a background thread so it never hitches a frame) and on a **clean
window close**. Writes are **atomic** (temp file → fsync → rename), so an interrupted write
can't truncate a good save.

> **Note:** killing the process (`kill`/`pkill`/force-quit) does **not** save — only a
> clean close does. The save format is versioned (`FLND`, currently v2) and migrates older
> saves forward automatically on the next save.

## Tests

```bash
./gradlew test
```

A headless JUnit 5 suite (no GL context needed) covers the pure-logic systems — save
round-trip and atomic write, the dual-grid soil masks, the chunked tile layer, collision
math, the world loader, the aim-flex targeting, registries, and input bindings. CI runs
`./gradlew build test` on every push.

## Project layout

```
src/main/java/
  core/        Entry point (DesktopLauncher), game loop, shared Config, Log, Assets cache
  states/      PlayState — the per-frame coordinator (input → move/collide → tools → render)
  world/       ChunkedTileLayer (bounded sparse grid), DualGridSoil, GrassGenerator
  entities/    PlayerCharacter, Inventory, PlayerAnimations (sprite-sheet row map)
  physics/     CollisionResolver + Body (AABB feet hitbox)
  crops/       CropType/CropField/PlantedCrop, geometry, registry, JSON data source
  items/       ItemType/ItemRegistry (id↔crop↔tool), JSON data source
  tools/       ToolActionSystem + data-driven tool actions
  farm/        FarmInteraction — targeting, planting, harvesting, tool effects
  render/      WorldRenderer, GameCamera, TextureSlicer
  ui/          InventoryHud + InventoryInteraction (drag/drop)
  input/       InputAction (semantic verbs) + InputBindings (rebindable key map)
  save/        WorldSave/CropSave, SaveManager, BinarySaveFormat (v2), FarmWorldLoader, WorldPersistence
  data/        ProbeLoader (data-file load + fallback), AssetManifest (boot icon check)
src/main/resources/
  data/        crops.json, items.json, tool_actions.json (content, behind swappable data sources)
  tilesets/, player_animations/, food/, ui/, tools/   art assets
```

### A few conventions worth knowing

- **Coordinates are Y-down** (an `OrthographicCamera` flipped to match screen space); texture
  regions are Y-flipped to compensate, and the world is depth-sorted around the player's feet.
- **Content is data-driven** behind swappable `*DataSource` seams with built-in fallbacks: a
  missing or unparseable JSON file falls back to a hardcoded catalog and logs a warning, so the
  game still runs.
- **Tuning lives in `core.Config`** — world size, tile size, move speed, autosave interval,
  growth/watering timers. Class-intrinsic geometry (hitbox, sprite layout) stays with its owner.
