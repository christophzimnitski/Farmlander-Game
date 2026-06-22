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
}
