package world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The object layer's placement/hit-test logic is pure (no GL): containment, base-line depth, last-placed
 * wins on overlap, and removal. The {@code TextureRegion} on each object is GL-bound and not exercised here.
 */
class ObjectLayerTest {

    private static PlacedObject obj(float x, float y, float w, float h) {
        return new PlacedObject("tilesets/farming_fishing.png", 0, 0, 32, 32, x, y, w, h);
    }

    @Test
    void containsRespectsTheDrawBox() {
        PlacedObject o = obj(100, 100, 50, 40);
        assertTrue(o.contains(100, 100));   // top-left corner is inside
        assertTrue(o.contains(149, 139));   // just inside the far edge
        assertFalse(o.contains(150, 140));  // far edge is exclusive
        assertFalse(o.contains(99, 100));   // left of the box
    }

    @Test
    void baseYIsTheBottomEdge() {
        assertEquals(140f, obj(100, 100, 50, 40).baseY());
    }

    @Test
    void topAtReturnsTheLastPlacedOverlapping() {
        ObjectLayer layer = new ObjectLayer();
        PlacedObject under = obj(0, 0, 100, 100);
        PlacedObject over = obj(0, 0, 100, 100); // same box, placed later -> drawn on top
        layer.add(under);
        layer.add(over);
        assertEquals(2, layer.size());
        assertSame(over, layer.topAt(50, 50));
    }

    @Test
    void topAtReturnsNullWhenNothingUnderThePoint() {
        ObjectLayer layer = new ObjectLayer();
        layer.add(obj(0, 0, 10, 10));
        assertNull(layer.topAt(500, 500));
    }

    @Test
    void removeDropsTheObject() {
        ObjectLayer layer = new ObjectLayer();
        PlacedObject o = obj(0, 0, 10, 10);
        layer.add(o);
        assertTrue(layer.remove(o));
        assertEquals(0, layer.size());
        assertNull(layer.topAt(5, 5));
    }
}
