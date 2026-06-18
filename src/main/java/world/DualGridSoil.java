package world;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

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
 * <p>{@link #tileForMask(int)} is the entire connection logic and depends on nothing but the mask, so it
 * is unit-testable on its own (no libGDX, no rendering).
 */
public final class DualGridSoil {
    // Corner bits of a node's 4-bit mask, one per surrounding cell (bit set = that cell is tilled).
    public static final int TL = 1; // cell up-and-left of the node   (col-1, row-1)
    public static final int TR = 2; // cell up-and-right               (col,   row-1)
    public static final int BL = 4; // cell down-and-left              (col-1, row)
    public static final int BR = 8; // cell down-and-right             (col,   row)

    /**
     * Corner-mask ({@link #TL}|{@link #TR}|{@link #BL}|{@link #BR}) → {@code plowed_soil} tile index
     * (1-based; {@code 0} = draw nothing, so the grass below shows). This table was derived directly
     * from the sprite sheet by measuring which quadrant of each tile is soil, so it is a <b>complete</b>
     * dual-grid set: all 16 corner configurations resolve to a real tile.
     */
    private static final int[] TILE_FOR_MASK = {
            0,   //  0  ........  (no tilled corner — nothing to draw)
            15,  //  1  TL
            13,  //  2  TR
            14,  //  3  TL TR
            9,   //  4  BL
            12,  //  5  TL BL
            1,   //  6  TR BL            (opposite diagonal)
            2,   //  7  TL TR BL
            7,   //  8  BR
            4,   //  9  TL BR            (opposite diagonal)
            10,  // 10  TR BR
            3,   // 11  TL TR BR
            8,   // 12  BL BR
            5,   // 13  TL BL BR
            6,   // 14  TR BL BR
            11,  // 15  TL TR BL BR      (fully surrounded)
    };

    /** The pure connection rule: which {@code plowed_soil} tile a node with this corner mask draws. */
    public static int tileForMask(int mask) {
        return TILE_FOR_MASK[mask & 0xF];
    }

    private final ChunkedTileLayer tilled; // 1 = tilled, 0 = not; the per-cell flags the save persists

    public DualGridSoil(ChunkedTileLayer tilled) {
        this.tilled = tilled;
    }

    public boolean isTilled(int col, int row) { return tilled.get(col, row) != 0; }

    public void till(int col, int row) { tilled.set(col, row, 1); }

    public void clear(int col, int row) { tilled.set(col, row, 0); }

    /** The world-grid layer of tilled flags (for sizing, save, and coordinate helpers). */
    public ChunkedTileLayer grid() { return tilled; }

    /** Corner mask for the dual-grid node at the corner shared by cells around (nodeCol,nodeRow). */
    public int maskAt(int nodeCol, int nodeRow) {
        int mask = 0;
        if (isTilled(nodeCol - 1, nodeRow - 1)) mask |= TL;
        if (isTilled(nodeCol,     nodeRow - 1)) mask |= TR;
        if (isTilled(nodeCol - 1, nodeRow))     mask |= BL;
        if (isTilled(nodeCol,     nodeRow))     mask |= BR;
        return mask;
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
        float tileSize = tilled.tileSize();
        float half = tileSize / 2f;
        for (int nodeCol = minNodeCol; nodeCol <= maxNodeCol; nodeCol++) {
            for (int nodeRow = minNodeRow; nodeRow <= maxNodeRow; nodeRow++) {
                int tile = tileForMask(maskAt(nodeCol, nodeRow));
                if (tile <= 0 || tile >= soilRegions.length || soilRegions[tile] == null) continue;
                batch.draw(soilRegions[tile],
                        nodeCol * tileSize - half, nodeRow * tileSize - half, tileSize, tileSize);
            }
        }
    }
}
