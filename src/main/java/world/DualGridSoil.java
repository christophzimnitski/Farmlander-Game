package world;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

/**
 * Tilled soil modelled as a <b>dual grid</b>.
 *
 * <p>Gameplay reads and writes a single per-cell {@code tilled} flag on the world grid — the one source
 * of truth. The <em>visuals</em> live on a second grid offset by half a tile: each display node sits on
 * a world-cell <em>corner</em> and picks its tile from the four cells touching that corner. Because a
 * node only ever sees those four corners, the four cells give a 4-bit mask and every configuration maps
 * to exactly one of 16 tiles ("marching squares" / Wang tiling). One tilled cell therefore shows as its
 * four surrounding nodes each drawing a corner piece that meet in a clean square, and adjacent tills
 * merge automatically — there are no special cases and no merge bug to get wrong.
 *
 * <p>Watering is a separate, <em>transient</em> layer: a cell stays wet for {@link #update a fixed time}
 * then dries on its own, and it isn't part of the saved world. So it lives in a runtime map of watered
 * cell → the time it was watered, kept off the persisted {@code tilled} grid (which stays a plain 0/1
 * flag). {@link #tileForMask(int)} is the entire connection logic and depends on nothing but the mask, so
 * it is unit-testable on its own (no libGDX, no rendering).
 */
public final class DualGridSoil {
    // Corner bits of a node's 4-bit mask — re-exported from CornerAutotile (the shared corner dual-grid rule,
    // now used by sand/sand-water too). Kept here so existing callers/tests still reference DualGridSoil.TL etc.
    public static final int TL = CornerAutotile.TL;
    public static final int TR = CornerAutotile.TR;
    public static final int BL = CornerAutotile.BL;
    public static final int BR = CornerAutotile.BR;

    /** The pure connection rule (delegates to {@link CornerAutotile#tileForMask}). */
    public static int tileForMask(int mask) {
        return CornerAutotile.tileForMask(mask);
    }

    // Persisted per-cell soil state: a plain tilled flag (the dual grid only cares whether a cell is soil).
    private static final int UNTILLED = 0;
    private static final int TILLED   = 1;

    private final ChunkedTileLayer tilled; // per-cell tilled flag (0/1); the save persists it
    // Transient wet soil: watered cell key -> when it was wetted + its random look. Off the persisted grid
    // because water dries on a timer (see update) and isn't part of the saved world. Sparse — only wet cells
    // appear. The look is rolled once per watering so a tile keeps the same blob+rotation until it dries.
    private final Map<Long, WetCell> wet = new HashMap<>();
    private final Random rng = new Random();

    /** A watered cell: when it was wetted (ms) and an opaque random {@code decor} the renderer decodes into a
     *  blob variant + rotation, so each watering looks a little different. */
    private record WetCell(long wateredAt, int decor) {}

    public DualGridSoil(ChunkedTileLayer tilled) {
        this.tilled = tilled;
    }

    private long cellKey(int col, int row) { return (long) row * tilled.cols() + col; }

    /** Is the cell soil at all (tilled)? This drives the dual-grid Wang rendering. */
    public boolean isTilled(int col, int row) { return tilled.get(col, row) != UNTILLED; }

    /** Is the cell plantable soil — tilled (watering doesn't change plantability)? */
    public boolean isPlantable(int col, int row) { return tilled.get(col, row) == TILLED; }

    /** Is the cell currently watered (a damp patch, drawn as a wet-soil blob until it dries)? */
    public boolean isWatered(int col, int row) { return wet.containsKey(cellKey(col, row)); }

    /** The random look-seed of a watered cell (0 when dry); the renderer decodes it into a blob + rotation. */
    public int wateredDecor(int col, int row) {
        WetCell c = wet.get(cellKey(col, row));
        return c == null ? 0 : c.decor();
    }

    /** Till the cell. Also dries it — a fresh till always starts dry. */
    public void till(int col, int row) { tilled.set(col, row, TILLED); wet.remove(cellKey(col, row)); }

    /** Water plantable soil at {@code now} (ms), rolling a fresh random look. No-op on untilled ground — you
     *  can only wet real farmland. The cell dries on its own after the timeout in {@link #update}. */
    public void water(int col, int row, long now) {
        if (isPlantable(col, row)) wet.put(cellKey(col, row), new WetCell(now, rng.nextInt()));
    }

    /** Dry a cell immediately (e.g. when it's disturbed by shoveling). */
    public void dry(int col, int row) { wet.remove(cellKey(col, row)); }

    /** Un-till the cell back to bare ground; also dries it. */
    public void clear(int col, int row) { tilled.set(col, row, UNTILLED); wet.remove(cellKey(col, row)); }

    /** Age the wet soil: any cell watered more than {@code dryMillis} ago dries out (its blob vanishes). */
    public void update(long now, long dryMillis) {
        if (wet.isEmpty()) return;
        for (Iterator<Map.Entry<Long, WetCell>> it = wet.entrySet().iterator(); it.hasNext(); ) {
            if (now - it.next().getValue().wateredAt() >= dryMillis) it.remove();
        }
    }

    /** Visit each currently-watered cell's {@code (col, row)} — lets a caller apply an effect to wet soil
     *  (e.g. the crop-growth boost) without exposing the internal map. The visitor must not water/dry cells. */
    public void forEachWatered(CellVisitor visitor) {
        if (wet.isEmpty()) return;
        int cols = tilled.cols();
        for (long key : wet.keySet()) visitor.visit((int) (key % cols), (int) (key / cols));
    }

    /** A cell callback for {@link #forEachWatered}. */
    public interface CellVisitor {
        void visit(int col, int row);
    }

    /** The world-grid layer of tilled flags (for sizing, save, and coordinate helpers). */
    public ChunkedTileLayer grid() { return tilled; }

    /** Corner mask for the dual-grid node at the corner shared by cells around (nodeCol,nodeRow). */
    public int maskAt(int nodeCol, int nodeRow) {
        return CornerAutotile.maskAt(tilled, nodeCol, nodeRow);
    }

    /**
     * Draw the soil. Walks every dual-grid node — the cell corners, so {@code [0..cols] x [0..rows]} —
     * and draws the tile for that node's corner mask, centred on the corner (the {@code -half} offset).
     *
     * @param soilRegions tiles sliced from {@code plowed_soil.png}, indexed 1..18 (index 0 unused)
     */
    public void render(SpriteBatch batch, TextureRegion[] soilRegions) {
        render(batch, soilRegions, 0, tilled.cols(), 0, tilled.rows());
    }

    /**
     * Draw only the nodes in the inclusive {@code [minNodeCol..maxNodeCol] × [minNodeRow..maxNodeRow]}
     * range — the frustum-culled path the renderer uses so off-screen soil isn't walked every frame. The
     * caller clamps the range to {@code [0..cols] × [0..rows]} (one extra node past the last cell).
     */
    public void render(SpriteBatch batch, TextureRegion[] soilRegions,
                       int minNodeCol, int maxNodeCol, int minNodeRow, int maxNodeRow) {
        CornerAutotile.render(batch, tilled, soilRegions, tilled.tileSize(),
                minNodeCol, maxNodeCol, minNodeRow, maxNodeRow);
    }
}
