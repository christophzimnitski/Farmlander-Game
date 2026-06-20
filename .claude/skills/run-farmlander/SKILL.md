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

- Native **libGDX desktop app** (LWJGL3 backend; Gradle `application` plugin, `mainClass = core.DesktopLauncher`, Java 21 toolchain via the committed wrapper). On macOS the run adds `-XstartOnFirstThread` (GLFW must run on the first thread). A window opens on the primary display.
- The game loop runs until the window is closed. If you need to keep working while it runs, launch it in the **background**; on window close the JVM exits cleanly (`BUILD SUCCESSFUL`) and `PlayState.dispose()` saves the world (look for a `[INFO] PlayState: autosaved` line during play, too).
- Logging goes through `core.Log` (`[INFO]/[WARN]/[ERROR]` lines). A `[WARN] SaveManager: could not read save ...; starting fresh` line is the loader gracefully handling a missing/corrupt save, **not** a crash.

## Saves

Saves live at `~/.farmlander/objects.dat` (outside the repo), referenced via `save.SaveManager.SAVE_FILE`. Writes are atomic (temp file + fsync + rename), so an interrupted write can't truncate a good save.

## Important: do NOT `pkill` a running game that holds a precious save

`pkill`/SIGTERM does **not** trigger a clean save — closing the window does. Ask the user to close via the window's X when a recovered or hard-won save is live. Use `pgrep -f DesktopLauncher` to check whether the game is running.

## Troubleshooting

- If a build fails with `NoSuchFileException` pointing at a `/tmp/...` Gradle path, a stale daemon is being reused. Run `./gradlew --stop`, then launch again. (Should not recur — the wrapper has its own Gradle distribution.)
