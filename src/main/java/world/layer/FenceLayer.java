package world.layer;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import world.ChunkedTileLayer;
import world.FenceAutotiler;

/**
 * An upright, solid fence layer: painting auto-orients the connective tiles (1–15) via {@link FenceAutotiler},
 * while a picked gate tile (16–18) is placed as-is. Drawn depth-sorted around the player like the bushes, so
 * you can stand in front of or behind a fence; blocks movement.
 */
public final class FenceLayer extends AbstractMapLayer {
    private final FenceAutotiler autotiler;

    public FenceLayer(String name, ChunkedTileLayer grid, TextureRegion[] regions, int sheetIndex) {
        super(name, grid, regions, true, true, sheetIndex); // solid, upright
        this.autotiler = new FenceAutotiler(grid);
    }

    @Override public Kind kind() { return Kind.FENCE; }

    @Override public void paint(int col, int row, int brushTile) {
        if (FenceAutotiler.isGate(brushTile)) autotiler.paintGate(col, row, brushTile);
        else autotiler.paint(col, row);
    }

    @Override public void erase(int col, int row) { autotiler.erase(col, row); }

    @Override
    public void drawDepth(SpriteBatch batch, Rectangle view, float tile, float feetY, boolean inFront) {
        if (isVisible()) drawCellsDepth(batch, view, tile, feetY, inFront);
    }
}
