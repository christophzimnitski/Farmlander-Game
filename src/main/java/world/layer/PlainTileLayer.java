package world.layer;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import world.ChunkedTileLayer;

/**
 * A layer that simply stamps the picked tile (no auto-orienting): the grid stores the 1-based tile index for
 * this layer's sheet, drawn straight from {@link #regions}. Used for plain content like the props sheet.
 * Can be a flat ground layer or an <b>upright</b> layer that depth-sorts per cell around the player (so you
 * can walk in front of/behind it, like the bushes) — the props layer uses the upright form.
 */
public final class PlainTileLayer extends AbstractMapLayer {

    public PlainTileLayer(String name, ChunkedTileLayer grid, TextureRegion[] regions,
                          boolean solid, boolean upright, int sheetIndex) {
        super(name, grid, regions, solid, upright, sheetIndex);
    }

    @Override public Kind kind() { return Kind.PLAIN; }

    @Override public void paint(int col, int row, int brushTile) {
        if (brushTile >= 1 && brushTile < regions.length) grid.set(col, row, brushTile);
    }

    @Override
    public void drawGround(SpriteBatch batch, Rectangle view, float tile) {
        if (!isVisible() || isUpright()) return;
        forEachCellInView(view, tile, (col, row, v) -> batch.draw(regions[v], col * tile, row * tile, tile, tile));
    }

    @Override
    public void drawDepth(SpriteBatch batch, Rectangle view, float tile, float feetY, boolean inFront) {
        if (!isVisible() || !isUpright()) return;
        forEachCellInView(view, tile, (col, row, v) -> {
            float threshold = row * tile + DEPTH_OFFSET;
            boolean drawIt = inFront ? (feetY <= threshold) : (feetY >= threshold);
            if (drawIt) batch.draw(regions[v], col * tile, row * tile, tile, tile);
        });
    }

    private interface CellOp { void at(int col, int row, int value); }

    /** Visit each non-empty, in-range cell whose tile could touch the view (one-cell padding), clamped. */
    private void forEachCellInView(Rectangle view, float tile, CellOp op) {
        int loCol = clamp((int) Math.floor(view.x / tile) - 1, 0, grid.cols() - 1);
        int hiCol = clamp((int) Math.floor((view.x + view.width) / tile) + 1, 0, grid.cols() - 1);
        int loRow = clamp((int) Math.floor(view.y / tile) - 1, 0, grid.rows() - 1);
        int hiRow = clamp((int) Math.floor((view.y + view.height) / tile) + 1, 0, grid.rows() - 1);
        for (int col = loCol; col <= hiCol; col++) {
            for (int row = loRow; row <= hiRow; row++) {
                int v = grid.get(col, row);
                if (v >= 1 && v < regions.length && regions[v] != null) op.at(col, row, v);
            }
        }
    }
}
