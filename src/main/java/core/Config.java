package core;

/**
 * Central game-tuning knobs — the dials you'd turn to balance or reshape the game, gathered out of the
 * classes that used to hard-code them. This holds <b>only</b> genuine design tuning: a class's intrinsic
 * geometry (the player sprite's hitbox box and draw size, render depth offsets, sprite-sheet layout) stays
 * with the class that owns it, not here, so this never grows into a god-object every class depends on.
 * Pure constants, no libGDX — safe to read from anywhere, including headless tests.
 */
public final class Config {
    private Config() {}

    // --- Display / window ---
    public static final String TITLE = "Farmlander";
    public static final boolean VSYNC = true;

    // --- Tooling ---
    /**
     * Master switch for the in-game map editor. When true, the editor toggle key (see InputBindings'
     * TOGGLE_EDITOR, F1) opens a free-camera editing scene over the world; when false the key does nothing
     * and the editor is never entered (the shipping-build state). Flip this one flag to disable the tool.
     */
    public static final boolean EDITOR_ENABLED = true;

    // --- World ---
    /** World units per tile cell. (Historically 64px art at 1.5 zoom; the zoom is now baked in here.) */
    public static final float TILE = 96f;
    /** The bounded world's size in cells — a finite, hand-authored map (Stardew-style, not infinite). */
    public static final int WORLD_COLS = 400;
    public static final int WORLD_ROWS = 220;

    // --- Player ---
    /** Walk speed, world units per second. */
    public static final float MOVE_SPEED = 270f;

    // --- Persistence / gameplay timing ---
    /** Seconds between periodic autosaves (a crash loses at most this much progress). */
    public static final float AUTOSAVE_INTERVAL = 120f;
    /** Default growth time per crop stage, ms (5 stages -> ~12s to mature). Overridable per crop in crops.json. */
    public static final long DEFAULT_STAGE_MILLIS = 3000L;
    /** How long watered soil stays wet before it dries out on its own, ms. */
    public static final long WATER_DRY_MILLIS = 30_000L;
    /** Extra growth rate a crop gets while its soil is watered (0.30 = grows 30% faster). */
    public static final float WATER_GROWTH_BONUS = 0.30f;
}
