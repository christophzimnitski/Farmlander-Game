package world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Locks {@link TileLayer}'s bounds handling and the zoom-decoupled world↔grid coordinate math that all
 * gameplay (tilling, hover, collision) leans on. Pure arithmetic — no GL.
 */
class TileLayerTest {

    @Test
    void dimensionsMatchBackingArray() {
        TileLayer layer = new TileLayer(40, 22, 96f);
        assertEquals(40, layer.cols());
        assertEquals(22, layer.rows());
        assertEquals(96f, layer.tileSize());
    }

    @Test
    void getOutOfBoundsReturnsZero() {
        TileLayer layer = new TileLayer(4, 4, 96f);
        layer.set(2, 2, 7);
        assertEquals(7, layer.get(2, 2));
        assertEquals(0, layer.get(-1, 0), "off the left edge is empty");
        assertEquals(0, layer.get(0, -1), "off the top edge is empty");
        assertEquals(0, layer.get(4, 0), "off the right edge is empty");
        assertEquals(0, layer.get(0, 4), "off the bottom edge is empty");
    }

    @Test
    void setOutOfBoundsIsIgnored() {
        TileLayer layer = new TileLayer(2, 2, 96f);
        layer.set(5, 5, 9); // must not throw
        assertEquals(0, layer.get(5, 5));
    }

    @Test
    void inBoundsTracksTheGrid() {
        TileLayer layer = new TileLayer(3, 3, 96f);
        assertTrue(layer.inBounds(0, 0));
        assertTrue(layer.inBounds(2, 2));
        assertFalse(layer.inBounds(3, 0));
        assertFalse(layer.inBounds(0, -1));
    }

    @Test
    void worldToColRoundTripsAtCellOrigins() {
        TileLayer layer = new TileLayer(10, 10, 96f);
        for (int col = 0; col < 10; col++) {
            float x = layer.worldXOfCol(col);
            assertEquals(col, layer.colAtWorldX(x), "left edge of a cell maps back to that cell");
            assertEquals(col, layer.colAtWorldX(x + 95f), "anywhere inside the cell maps to that cell");
        }
    }

    @Test
    void worldToColFloorsTowardNegativeInfinity() {
        TileLayer layer = new TileLayer(10, 10, 96f);
        assertEquals(0, layer.colAtWorldX(0f));
        assertEquals(0, layer.colAtWorldX(95.9f));
        assertEquals(1, layer.colAtWorldX(96f));
        assertEquals(-1, layer.colAtWorldX(-1f), "just left of origin is cell -1, not 0");
        assertEquals(-1, layer.rowAtWorldY(-0.001f));
    }

    @Test
    void dataExposesTheBackingArrayForSaving() {
        int[][] backing = new int[3][3];
        TileLayer layer = new TileLayer(backing, 96f);
        layer.set(1, 1, 5);
        assertSame(backing, layer.data(), "the layer wraps the save array without copying");
        assertEquals(5, backing[1][1], "writes go straight through to the saved grid");
    }
}
