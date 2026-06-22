package world.layer;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import world.ChunkedTileLayer;

/**
 * Shared state + defaults for {@link MapLayer} implementations: the name/visibility/solid/upright/sheet
 * properties, the backing grid and the sheet's sliced tile regions, and no-op draw methods (each concrete
 * layer overrides only the pass that applies — ground or depth). Keeps the concrete layer classes focused on
 * their paint rule and how they pick a region.
 */
abstract class AbstractMapLayer implements MapLayer {
    /** Depth ground-line offset for upright layers — matches WorldRenderer's bush/crop line. */
    protected static final float DEPTH_OFFSET = 82.5f;

    private final String name;
    private final boolean solid;
    private final boolean upright;
    private final int sheetIndex;
    private boolean visible = true;
    protected final ChunkedTileLayer grid;
    protected final TextureRegion[] regions; // 1-based tiles sliced from this layer's sheet

    protected AbstractMapLayer(String name, ChunkedTileLayer grid, TextureRegion[] regions,
                               boolean solid, boolean upright, int sheetIndex) {
        this.name = name;
        this.grid = grid;
        this.regions = regions;
        this.solid = solid;
        this.upright = upright;
        this.sheetIndex = sheetIndex;
    }

    @Override public String name() { return name; }

    @Override public boolean isVisible() { return visible; }

    @Override public void setVisible(boolean visible) { this.visible = visible; }

    @Override public boolean isSolid() { return solid; }

    @Override public boolean isUpright() { return upright; }

    @Override public int sheetIndex() { return sheetIndex; }

    @Override public ChunkedTileLayer grid() { return grid; }

    @Override public boolean blocksAt(int col, int row) { return solid && grid.get(col, row) != 0; }

    @Override public void erase(int col, int row) { grid.set(col, row, 0); }

    @Override public void drawGround(com.badlogic.gdx.graphics.g2d.SpriteBatch batch,
                                     com.badlogic.gdx.math.Rectangle view, float tile) { }

    @Override public void drawDepth(com.badlogic.gdx.graphics.g2d.SpriteBatch batch,
                                    com.badlogic.gdx.math.Rectangle view, float tile, float feetY, boolean inFront) { }

    protected static int clamp(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }

    // --- shared per-cell drawing (used by the plain + fence layers) ---

    protected interface CellOp { void at(int col, int row, int value); }

    /** Visit each non-empty, in-range cell whose tile could touch the view (one-cell padding), clamped. */
    protected void forEachCell(com.badlogic.gdx.math.Rectangle view, float tile, CellOp op) {
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

    /** Draw every cell flat (ground layers). */
    protected void drawCellsFlat(com.badlogic.gdx.graphics.g2d.SpriteBatch batch,
                                 com.badlogic.gdx.math.Rectangle view, float tile) {
        forEachCell(view, tile, (col, row, v) -> batch.draw(regions[v], col * tile, row * tile, tile, tile));
    }

    /** Draw cells depth-split by their ground line vs the player's feet (upright layers). */
    protected void drawCellsDepth(com.badlogic.gdx.graphics.g2d.SpriteBatch batch,
                                  com.badlogic.gdx.math.Rectangle view, float tile, float feetY, boolean inFront) {
        forEachCell(view, tile, (col, row, v) -> {
            float threshold = row * tile + DEPTH_OFFSET;
            boolean drawIt = inFront ? (feetY <= threshold) : (feetY >= threshold);
            if (drawIt) batch.draw(regions[v], col * tile, row * tile, tile, tile);
        });
    }
}
