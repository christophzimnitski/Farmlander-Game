package world;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * The shared <b>corner dual-grid</b> auto-tiling rule, used by every layer whose sheet has the plowed-soil
 * layout — tilled soil, sand and sand-water (all verified to share it). A per-cell on/off flag grid is the
 * source of truth; the visuals derive on a half-tile-offset grid where each display node sits on a cell
 * <em>corner</em> and picks one of 16 tiles from the four cells touching that corner ("marching squares" /
 * Wang tiling), so adjacent painted cells merge with no special cases.
 *
 * <p>Extracted from {@link DualGridSoil} so soil (which adds watering on top) and the plain sand layers share
 * one implementation. {@link #tileForMask} is pure (no libGDX) and unit-tested; {@link #render} is the only
 * GL-touching method. The 16-entry table was measured directly from {@code plowed_soil.png}, and the sand
 * sheets reuse it because their tile layout is identical.
 */
public final class CornerAutotile {
    private CornerAutotile() {}

    // Corner bits of a node's 4-bit mask, one per surrounding cell.
    public static final int TL = 1; // cell up-and-left of the node   (col-1, row-1)
    public static final int TR = 2; // cell up-and-right               (col,   row-1)
    public static final int BL = 4; // cell down-and-left              (col-1, row)
    public static final int BR = 8; // cell down-and-right             (col,   row)

    /** Corner-mask (TL|TR|BL|BR) → sheet tile index (1-based; 0 = draw nothing). Complete dual-grid set. */
    private static final int[] TILE_FOR_MASK = {
            0,   //  0  ........
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

    /** The pure connection rule: which sheet tile a node with this corner mask draws. */
    public static int tileForMask(int mask) {
        return TILE_FOR_MASK[mask & 0xF];
    }

    /** Corner mask for the dual-grid node at the corner shared by the four cells around (nodeCol,nodeRow). */
    public static int maskAt(ChunkedTileLayer flags, int nodeCol, int nodeRow) {
        int mask = 0;
        if (flags.get(nodeCol - 1, nodeRow - 1) != 0) mask |= TL;
        if (flags.get(nodeCol,     nodeRow - 1) != 0) mask |= TR;
        if (flags.get(nodeCol - 1, nodeRow)     != 0) mask |= BL;
        if (flags.get(nodeCol,     nodeRow)     != 0) mask |= BR;
        return mask;
    }

    /**
     * Draw the nodes in the inclusive {@code [minNodeCol..maxNodeCol] × [minNodeRow..maxNodeRow]} range (cell
     * corners, so one past the last cell), each tile centred on its corner.
     *
     * @param regions tiles sliced from the layer's sheet, indexed 1.. (index 0 unused)
     */
    public static void render(SpriteBatch batch, ChunkedTileLayer flags, TextureRegion[] regions, float tile,
                              int minNodeCol, int maxNodeCol, int minNodeRow, int maxNodeRow) {
        float half = tile / 2f;
        for (int nodeCol = minNodeCol; nodeCol <= maxNodeCol; nodeCol++) {
            for (int nodeRow = minNodeRow; nodeRow <= maxNodeRow; nodeRow++) {
                int t = tileForMask(maskAt(flags, nodeCol, nodeRow));
                if (t <= 0 || t >= regions.length || regions[t] == null) continue;
                batch.draw(regions[t], nodeCol * tile - half, nodeRow * tile - half, tile, tile);
            }
        }
    }
}
