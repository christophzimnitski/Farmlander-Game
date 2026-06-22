package world.layer;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import world.ChunkedTileLayer;

/**
 * One editor-authored map layer: a named tile grid with a paint rule, a draw rule, and collision/depth
 * properties. This is the {@code LayerInterface} the project's original Swing version had, reintroduced for
 * the new map editor — each tileset type gets a layer class abiding by it, with shared behaviour pulled into
 * the concrete classes ({@link CornerAutotileLayer} for the soil/sand snapping family, {@link FenceLayer}
 * for fence connection, {@link PlainTileLayer} for stamped tiles).
 *
 * <p>The game's deeply-integrated layers (grass, the farmable {@code DualGridSoil}, the bush collision grid)
 * stay where they are — they're tangled into farming/collision/crops — so this interface covers the
 * <em>generic, stackable</em> layers the editor adds on top (sand, sand-water, fence, props, and any
 * user-added ones). The renderer draws them in stack order (ground layers flat, upright layers depth-sorted
 * around the player); collision unions the solid ones. Backed by a {@link ChunkedTileLayer} so it persists
 * through the same per-chunk save codec.
 */
public interface MapLayer {
    /** How the editor paints this layer (which also implies whether a tile picker is needed). */
    enum Kind { PLAIN, CORNER, FENCE }

    String name();

    boolean isVisible();

    void setVisible(boolean visible);

    /** Contributes to collision (solid layers block movement on their occupied cells). */
    boolean isSolid();

    /** Drawn depth-sorted around the player (so he can walk in front/behind), vs flat ground. */
    boolean isUpright();

    Kind kind();

    /** The {@link editor.TilePalette} sheet index this layer paints from (for the picker / save), or -1. */
    int sheetIndex();

    /** The backing grid (for bounds + the save codec). */
    ChunkedTileLayer grid();

    /** Paint at a cell. {@code brushTile} is the picked tile for plain/gate painting; autotile layers derive
     *  their own tile and may ignore it. */
    void paint(int col, int row, int brushTile);

    /** Clear a cell (autotile layers also re-tile neighbours). */
    void erase(int col, int row);

    /** Does this layer block movement at the cell (only meaningful when {@link #isSolid()})? */
    boolean blocksAt(int col, int row);

    /** Draw flat (called for ground layers). No-op for upright layers. */
    void drawGround(SpriteBatch batch, Rectangle view, float tile);

    /** Draw the depth-split pass (called for upright layers): {@code inFront} selects tiles whose ground line
     *  is at/below the player's feet. No-op for ground layers. */
    void drawDepth(SpriteBatch batch, Rectangle view, float tile, float feetY, boolean inFront);
}
