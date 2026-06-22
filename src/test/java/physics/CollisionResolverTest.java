package physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import crops.CropField;
import crops.CropGeometry;
import crops.CropType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import world.ChunkedTileLayer;
import world.layer.LayerStack;

/**
 * Exercises the spatial-query windowing in {@link CollisionResolver#collidesAt} and {@link
 * CollisionResolver#resolve}: only cells near the hitbox are scanned, but nothing the hitbox can
 * actually touch may be culled. The boundary cases (a solid in the next cell, just-touching edges)
 * are the ones a naive window would drop. No GL, no world — pure logic over a small grid.
 */
class CollisionResolverTest {
    private static final float TILE = 96f;
    private static final float ERR = 0.45f;

    private CollisionResolver resolver(ChunkedTileLayer collision, CropField crops) {
        return new CollisionResolver(collision, crops, new CropGeometry(TILE), TILE, ERR, new LayerStack());
    }

    private ChunkedTileLayer emptyGrid() { return new ChunkedTileLayer(5, 5, TILE); }

    @Test
    void hitboxInsideSolidCellCollides() {
        ChunkedTileLayer c = emptyGrid();
        c.set(2, 2, 1); // solid
        CollisionResolver r = resolver(c, new CropField(5, 5));
        assertTrue(r.collidesAt(200, 200, 20, 20));
    }

    @Test
    void hitboxFarFromAnySolidDoesNotCollide() {
        ChunkedTileLayer c = emptyGrid();
        c.set(2, 2, 1);
        CollisionResolver r = resolver(c, new CropField(5, 5));
        assertFalse(r.collidesAt(10, 10, 20, 20)); // cell (0,0), nowhere near
    }

    @Test
    void hitboxReachesIntoSolidInTheNextCell() {
        // Hitbox sits in cell (1,2) but its right edge pokes past x=192 into cell (2,2)'s solid box.
        // A window that only scanned the hitbox's own cell would miss it — the +1 padding must catch it.
        ChunkedTileLayer c = emptyGrid();
        c.set(2, 2, 1);
        CollisionResolver r = resolver(c, new CropField(5, 5));
        assertTrue(r.collidesAt(180, 200, 20, 20)); // x-span [180,200] overlaps solid x[192,288]
        assertFalse(r.collidesAt(160, 200, 20, 20)); // x-span [160,180] stops short of x=192
    }

    @Test
    void cropFootprintInWindowCollides() {
        // Crop at cell (2,2): footprint is the 48x24 box centred on the cell centre (240,240).
        CropField crops = new CropField(5, 5);
        crops.plant(2, 2, new CropType(0, "Test", new com.badlogic.gdx.graphics.g2d.TextureRegion[1], 1000L), 0L);
        CollisionResolver r = resolver(emptyGrid(), crops);
        assertTrue(r.collidesAt(238, 238, 8, 8));   // inside the footprint box
        assertFalse(r.collidesAt(200, 200, 8, 8));  // in the cell but left of the narrow footprint
    }

    @Test
    void resolvePushesBodyClearOfSolid() {
        ChunkedTileLayer c = emptyGrid();
        c.set(2, 2, 1); // solid box x[192,288], y[180,276]
        CollisionResolver r = resolver(c, new CropField(5, 5));
        // Body clipped shallowly into the solid's left edge (the realistic walk-into-a-tile case): its
        // right edge pokes ~8px past x=192. resolve() should push it back out along X.
        MutableBody body = new MutableBody(160, 200, 40, 40);
        assertTrue(r.collidesAt(body.hitboxX(), body.hitboxY(), body.hitboxW(), body.hitboxH()));
        r.resolve(body);
        assertFalse(r.collidesAt(body.hitboxX(), body.hitboxY(), body.hitboxW(), body.hitboxH()));
    }

    @Test
    void forEachSolidBoxEmitsRaisedSolidAndCropFootprint() {
        ChunkedTileLayer c = emptyGrid();
        c.set(2, 2, 1); // solid bush-style tile
        c.set(0, 0, 7); // a NON_COLLIDABLE (walkable) variant — must be skipped
        CropField crops = new CropField(5, 5);
        crops.plant(3, 3, new CropType(0, "Test", new TextureRegion[1], 1000L), 0L);
        CollisionResolver r = resolver(c, crops);

        List<float[]> boxes = collectBoxes(r, new Rectangle(0, 0, 5 * TILE, 5 * TILE));

        assertEquals(2, boxes.size()); // the solid tile + the crop footprint; the walkable tile-7 is skipped
        // collision tiles are raised by TILE/8 so the drawn box matches the real collide box
        assertTrue(hasBox(boxes, 2 * TILE, 2 * TILE - TILE / 8f, TILE, TILE), "raised solid tile box");
        // crop footprint: 48x24 centred on the root (3.5*TILE, 3.5*TILE) = (336,336)
        assertTrue(hasBox(boxes, 312f, 324f, 48f, 24f), "crop footprint box");
    }

    @Test
    void forEachSolidBoxSkipsHarvestedCropsAndOffscreenCells() {
        ChunkedTileLayer c = emptyGrid();
        c.set(4, 4, 1); // solid, but outside the 1-cell view below
        CropField crops = new CropField(5, 5);
        crops.plant(0, 0, new CropType(0, "Test", new TextureRegion[1], 1000L), 0L);
        crops.at(0, 0).harvested = true; // a harvested hole no longer blocks → not emitted

        CollisionResolver r = resolver(c, crops);
        assertTrue(collectBoxes(r, new Rectangle(0, 0, TILE, TILE)).isEmpty()); // crop harvested + solid off-view
    }

    private List<float[]> collectBoxes(CollisionResolver r, Rectangle view) {
        List<float[]> boxes = new ArrayList<>();
        r.forEachSolidBox(view, (x, y, w, h) -> boxes.add(new float[]{x, y, w, h}));
        return boxes;
    }

    private static boolean hasBox(List<float[]> boxes, float x, float y, float w, float h) {
        for (float[] b : boxes)
            if (b[0] == x && b[1] == y && b[2] == w && b[3] == h) return true;
        return false;
    }

    /** Minimal mutable {@link Body} for the resolve test. */
    private static final class MutableBody implements Body {
        private float x, y;
        private final float w, h;
        MutableBody(float x, float y, float w, float h) { this.x = x; this.y = y; this.w = w; this.h = h; }
        public float hitboxX() { return x; }
        public float hitboxY() { return y; }
        public float hitboxW() { return w; }
        public float hitboxH() { return h; }
        public void move(float dx, float dy) { x += dx; y += dy; }
    }
}
