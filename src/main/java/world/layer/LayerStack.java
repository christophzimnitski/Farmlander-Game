package world.layer;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;

import java.util.ArrayList;
import java.util.List;

/**
 * The ordered set of editor-authored {@link MapLayer}s on top of the game's built-in layers. Owns draw order
 * (ground layers drawn flat first, upright layers depth-split around the player), the union collision test,
 * and which layer the editor is currently painting into. Iterated by the renderer and collision; managed by
 * the editor's Layers menu.
 */
public final class LayerStack {
    private final List<MapLayer> layers = new ArrayList<>();
    private int active = -1;

    public List<MapLayer> all() { return layers; }

    public int size() { return layers.size(); }

    public MapLayer get(int i) { return layers.get(i); }

    public void add(MapLayer layer) { layers.add(layer); }

    public int activeIndex() { return active; }

    public void setActive(int i) { active = (i >= 0 && i < layers.size()) ? i : -1; }

    public MapLayer active() { return (active >= 0 && active < layers.size()) ? layers.get(active) : null; }

    /** Draw every visible ground (flat) layer, in stack order. */
    public void drawGround(SpriteBatch batch, Rectangle view, float tile) {
        for (MapLayer l : layers) if (!l.isUpright()) l.drawGround(batch, view, tile);
    }

    /** Draw the depth pass for every visible upright layer (behind or in front of the player). */
    public void drawDepth(SpriteBatch batch, Rectangle view, float tile, float feetY, boolean inFront) {
        for (MapLayer l : layers) if (l.isUpright()) l.drawDepth(batch, view, tile, feetY, inFront);
    }

    /** Does any solid layer block movement at this cell? */
    public boolean blocksAt(int col, int row) {
        for (MapLayer l : layers) if (l.isSolid() && l.blocksAt(col, row)) return true;
        return false;
    }
}
