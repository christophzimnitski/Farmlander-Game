package world.layer;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import world.ChunkedTileLayer;
import world.CornerAutotile;

/**
 * A ground layer that auto-orients via the shared corner dual-grid ({@link CornerAutotile}) — the same rule
 * the farmable soil uses. The grid stores a plain on/off flag per cell; painting a cell just sets the flag,
 * and the visuals (edges/corners merging cleanly) derive at draw time. Used for sand and sand-water, whose
 * sheets share the plowed-soil tile layout, so they reuse the exact same table. Always a flat ground layer,
 * non-solid.
 */
public final class CornerAutotileLayer extends AbstractMapLayer {

    public CornerAutotileLayer(String name, ChunkedTileLayer grid, TextureRegion[] regions, int sheetIndex) {
        super(name, grid, regions, false, false, sheetIndex);
    }

    @Override public Kind kind() { return Kind.CORNER; }

    @Override public void paint(int col, int row, int brushTile) { grid.set(col, row, 1); }

    @Override
    public void drawGround(SpriteBatch batch, Rectangle view, float tile) {
        if (!isVisible()) return;
        // Nodes sit on cell corners, so the node grid is one wider/taller than the cells (0..cols).
        int loCol = clamp((int) Math.floor(view.x / tile) - 1, 0, grid.cols());
        int hiCol = clamp((int) Math.floor((view.x + view.width) / tile) + 1, 0, grid.cols());
        int loRow = clamp((int) Math.floor(view.y / tile) - 1, 0, grid.rows());
        int hiRow = clamp((int) Math.floor((view.y + view.height) / tile) + 1, 0, grid.rows());
        CornerAutotile.render(batch, grid, regions, tile, loCol, hiCol, loRow, hiRow);
    }
}
