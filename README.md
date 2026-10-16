# Farmlander

A 2D farming game written in Java with libGDX. Walk around a small world, till
the soil, plant seeds, water them, and harvest the crops. Most of the game
systems, like the tile world, soil rendering, saving, and inventory, are written
by hand as a learning project.

## Requirements

- Java 21 (JDK)
- macOS, Linux, or Windows

Gradle is downloaded automatically by the included wrapper.

## Run

```bash
./gradlew run
```

On macOS you can also double-click `Play Farmlander.command`.

## Controls

| Key                  | Action                          |
|----------------------|---------------------------------|
| W A S D / arrow keys | Move                            |
| 1-9, 0               | Select a hotbar slot            |
| Left click           | Use the held tool or plant seed |
| Q                    | Use the held tool               |
| E                    | Harvest a grown crop            |
| Tab                  | Open or close the inventory     |
| F1                   | Toggle the map editor           |
| F2                   | Show hitboxes                   |
| Esc                  | Quit                            |

## Gameplay

- **Hoe:** till ground into soil
- **Watering can:** watered crops grow faster
- **Pickaxe:** turn soil back into ground
- **Shovel:** clear what is left after harvesting a root crop
- **Axe:** remove a crop above ground

Plant a seed, wait for it to grow, then harvest it for produce. A new game
starts with potato, carrot, cauliflower, and pumpkin seeds.

The game saves to `~/.farmlander/objects.dat` every two minutes and when the
window is closed.

## Tests

```bash
./gradlew test
```

The tests cover saving and loading, the tile world, soil, collision, aiming,
and input. They run automatically on every push.

## Project layout

| Folder                    | Contents                                  |
|---------------------------|-------------------------------------------|
| `src/main/java/core`      | Launcher, game loop, and settings         |
| `src/main/java/world`     | Tile world, soil, and grass               |
| `src/main/java/entities`  | Player, inventory, and animations         |
| `src/main/java/crops`     | Crop types and growth                     |
| `src/main/java/farm`      | Planting, harvesting, and tool effects    |
| `src/main/java/save`      | Save file format and loading              |
| `src/main/java/ui`        | Inventory and hotbar                      |
| `src/main/java/editor`    | In-game map editor                        |
| `src/main/resources`      | Art, and crop and item data (JSON)        |

## Art credits

All art comes from the Liberated Pixel Cup (LPC) collection on
[OpenGameArt](https://opengameart.org) and is used under its original
licenses (CC-BY-SA 3.0, GPL 3.0, OGA-BY 3.0, CC-BY 3.0, and CC0). This includes
the character sprites, food icons, tilesets, UI elements, and tool icons.

- Character sprites: many LPC artists. Every file, author, and license is listed
  in [`all-credits.csv`](src/main/resources/credits/all-credits.csv).
- Food icons: "[LPC] Foods" by bluecarrot16, Daniel Eddeland, Joshua Taylor,
  Richard Kettering, thekingphoenix, RedVoxel, and Molly "Cougarmint" Willits.
  See [`CREDITS-food.txt`](src/main/resources/credits/CREDITS-food.txt).
- Farming tilesets and UI: based on "[LPC] Farming tilesets, magic animations
  and UI elements" by Daniel Eddeland and other LPC tilesets.

These art files are not covered by the MIT license below.

## License

The code is released under the MIT License. See [LICENSE](LICENSE).
