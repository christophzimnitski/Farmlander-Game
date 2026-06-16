---
name: run-farmlander
description: Launch and run the Farmlander game (this project's app). Use when asked to run, start, play, or launch the game or app.
---

# Run Farmlander

Verified launch path for this project. Do this verbatim.

## Launch

From the project root:

```bash
./gradlew run --console=plain
```

This is exactly what the double-clickable `Play Farmlander.command` in the
project root does — the two are equivalent.

## What to expect

- Native **Java Swing desktop GUI** (Gradle `application` plugin, `mainClass = main.Main`, Java 21 toolchain via the committed wrapper). A window opens on the primary display.
- The game loop runs until the window is closed. If you need to keep working while it runs, launch it in the **background**; on window close the JVM exits cleanly (`BUILD SUCCESSFUL`) and a shutdown hook prints `Objects saved to file.`
- The app prints the inventory array (e.g. `[100, 0, 0, ...]`) each update — that's expected debug output, not an error.

## Saves

Saves live at `~/.farmlander/objects.dat` (outside the repo), referenced via `LevelManager.SAVE_FILE`.

## Troubleshooting

- If a build fails with `NoSuchFileException` pointing at a `/tmp/...` Gradle path, a stale daemon is being reused. Run `./gradlew --stop`, then launch again. (Should not recur — the wrapper has its own Gradle distribution.)
