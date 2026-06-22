package editor;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import core.Assets;
import render.TextureSlicer;

import java.util.HashMap;
import java.util.Map;

/**
 * The global catalog of every paintable 32px tile across all tilesets — the addressing layer that lets the
 * map editor pick a tile from <em>any</em> sheet and store it in one grid. The world's per-cell {@code int}
 * model can only index a single sheet's region array; this lifts that limit the way Tiled does, by giving
 * each (sheet, tile) pair a stable global id.
 *
 * <p>A global id packs the sheet and the in-sheet tile: {@code ((sheetIndex+1) << 16) | localTileIndex}, so
 * id {@code 0} means "empty". The {@link #SHEETS} table is <b>append-only</b> — a sheet's array position is
 * baked into every id stored in a saved map, so new sheets may only be added at the end (same discipline as
 * the versioned save format); never reorder or remove. The id math is pure (no libGDX) and unit-tested;
 * {@link #region} is the only GL-touching method (it slices on demand through the shared {@link Assets}
 * cache and {@link TextureSlicer}, so every region keeps the same half-texel inset + Y-flip as the game).
 */
public final class TilePalette {
    /** Tile size in source-sheet pixels. Every catalogued sheet is a grid of this. */
    public static final int TILE = 32;
    /** The empty/no-tile id (a cleared cell). */
    public static final int EMPTY = 0;

    /** A paintable sprite sheet: a {@code cols}×{@code rows} grid of {@value #TILE}px tiles. */
    public static final class Sheet {
        public final String path;     // classpath path under resources/
        public final String name;     // human label for the editor panel
        public final int cols, rows;  // tile grid dimensions
        /** True for sheets meant to be auto-tiled (painted as a connectivity flag) rather than stamped raw:
         *  the plow corner dual-grid and the bush 9-slice clumps. Raw stamping of these is still possible. */
        public final boolean autotile;

        Sheet(String path, String name, int cols, int rows, boolean autotile) {
            this.path = path;
            this.name = name;
            this.cols = cols;
            this.rows = rows;
            this.autotile = autotile;
        }

        public int tileCount() { return cols * rows; }
    }

    // APPEND-ONLY. The index here is the stable sheet id packed into every tile id (and saved to disk), so
    // only ever add new sheets at the end — reordering or removing would silently corrupt existing maps.
    private static final Sheet[] SHEETS = {
            new Sheet("tilesets/grassTileset.png", "Grass detail", 3, 1, false),
            new Sheet("tilesets/ore.png",          "Rocks & ore",  16, 16, false),
            new Sheet("tilesets/sand.png",         "Sand",         3, 6, false),
            new Sheet("tilesets/sandwater.png",    "Sand + water", 3, 6, false),
            new Sheet("tilesets/reed.png",         "Reeds",        3, 10, false),
            new Sheet("tilesets/fence.png",        "Fence",        3, 6, false),
            new Sheet("tilesets/fence_alt.png",    "Fence (alt)",  3, 6, false),
            new Sheet("tilesets/plowed_soil.png",  "Tilled soil",  3, 6, true),
            new Sheet("tilesets/tallgrass.png",    "Tall grass",   3, 6, true),
            new Sheet("tilesets/wheat.png",        "Wheat",        3, 6, true),
            new Sheet("tilesets/youngwheat.png",   "Young wheat",  3, 6, true),
            new Sheet("tilesets/farming_fishing.png", "Props",     20, 20, false),
    };

    public static int sheetCount() { return SHEETS.length; }

    public static Sheet sheet(int sheetIndex) { return SHEETS[sheetIndex]; }

    /** Index of the sheet with the given classpath path, or -1 if not catalogued. */
    public static int indexOf(String path) {
        for (int i = 0; i < SHEETS.length; i++) if (SHEETS[i].path.equals(path)) return i;
        return -1;
    }

    /** Pack a (sheet, in-sheet tile) pair into a stable non-zero global id. */
    public static int pack(int sheetIndex, int tileIndex) {
        return ((sheetIndex + 1) << 16) | (tileIndex & 0xFFFF);
    }

    public static int sheetIndexOf(int id) { return (id >>> 16) - 1; }

    public static int tileIndexOf(int id) { return id & 0xFFFF; }

    /** Is this a real, in-range tile id (non-empty, known sheet, in-bounds tile)? */
    public static boolean isValid(int id) {
        if (id == EMPTY) return false;
        int s = sheetIndexOf(id);
        return s >= 0 && s < SHEETS.length && tileIndexOf(id) < SHEETS[s].tileCount();
    }

    /** In-sheet tile index for a (col,row), row-major to match {@link TextureSlicer#slice}'s order. */
    public static int tileIndex(Sheet sheet, int col, int row) { return row * sheet.cols + col; }

    // --- region lookup (GL-bound) ---
    private final Assets assets;
    private final Map<Integer, TextureRegion[]> sliced = new HashMap<>(); // sheetIndex -> 1-based region array

    public TilePalette(Assets assets) { this.assets = assets; }

    /** The drawable region for a global tile id, or {@code null} for empty/invalid ids. Slices each sheet
     *  once on first use (through the asset cache) and reuses it thereafter. */
    public TextureRegion region(int id) {
        if (!isValid(id)) return null;
        int s = sheetIndexOf(id);
        TextureRegion[] regions = sliced.get(s);
        if (regions == null) {
            Sheet sheet = SHEETS[s];
            Texture texture = assets.get(sheet.path);
            regions = TextureSlicer.slice(texture, sheet.rows, sheet.cols); // 1-based
            sliced.put(s, regions);
        }
        return regions[tileIndexOf(id) + 1];
    }
}
