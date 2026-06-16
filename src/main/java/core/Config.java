package core;

/**
 * Central tunables for the game/engine. Replaces the scattered static fields
 * that used to live on Level (SCALE, TILES_DEFAULT_SIZE, FPS, ...).
 */
public final class Config {
    public static final String TITLE = "Farmlander";
    public static final int WINDOW_WIDTH = 1280;
    public static final int WINDOW_HEIGHT = 720;

    /** World units per tile. Matches the old Level.TILES_DEFAULT_SIZE. */
    public static final int TILE_SIZE = 64;

    public static final boolean VSYNC = true;

    private Config() {}
}
