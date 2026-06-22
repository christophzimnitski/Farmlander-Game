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
        if (isVisible() && !isUpright()) drawCellsFlat(batch, view, tile);
    }

    @Override
    public void drawDepth(SpriteBatch batch, Rectangle view, float tile, float feetY, boolean inFront) {
        if (isVisible() && isUpright()) drawCellsDepth(batch, view, tile, feetY, inFront);
    }
}
