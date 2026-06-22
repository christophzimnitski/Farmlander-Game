package world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The fence connection table is pure (no GL): the tile picked from the four neighbours (runs/corners/edges/
 * cross), that gates are preserved but still connect, and that paint/erase re-tile the right cells.
 */
class FenceAutotilerTest {

    private ChunkedTileLayer grid() { return new ChunkedTileLayer(8, 8, 96f); }

    @Test
    void tileForCoversRunsCornersEdgesCross() {
        assertEquals(4, BushAutotilerTileFor(false, false, false, false)); // isolated post
        assertEquals(2, BushAutotilerTileFor(false, true, false, true));   // horizontal middle (E+W)
        assertEquals(1, BushAutotilerTileFor(false, true, false, false));  // left-end (E only)
        assertEquals(3, BushAutotilerTileFor(false, false, false, true));  // right-end (W only)
        assertEquals(5, BushAutotilerTileFor(true, false, true, false));   // vertical middle (N+S)
        assertEquals(6, BushAutotilerTileFor(false, false, true, false));  // top-end (S only)
        assertEquals(4, BushAutotilerTileFor(true, false, false, false));  // bottom-end (N only)
        assertEquals(7, BushAutotilerTileFor(false, true, true, false));   // TL corner (S+E)
        assertEquals(9, BushAutotilerTileFor(false, false, true, true));   // TR corner (S+W)
        assertEquals(13, BushAutotilerTileFor(true, true, false, false));  // BL corner (N+E)
        assertEquals(15, BushAutotilerTileFor(true, false, false, true));  // BR corner (N+W)
        assertEquals(8, BushAutotilerTileFor(false, true, true, true));    // top edge (S+E+W)
        assertEquals(14, BushAutotilerTileFor(true, true, false, true));   // bottom edge (N+E+W)
        assertEquals(10, BushAutotilerTileFor(true, true, true, false));   // left edge (N+S+E)
        assertEquals(12, BushAutotilerTileFor(true, false, true, true));   // right edge (N+S+W)
        assertEquals(11, BushAutotilerTileFor(true, true, true, true));    // cross
    }

    private static int BushAutotilerTileFor(boolean n, boolean e, boolean s, boolean w) {
        return FenceAutotiler.tileFor(n, e, s, w);
    }

    @Test
    void horizontalRunGivesEndsAndMiddle() {
        ChunkedTileLayer g = grid();
        FenceAutotiler a = new FenceAutotiler(g);
        a.paint(2, 2);
        a.paint(3, 2);
        a.paint(4, 2);
        assertEquals(1, g.get(2, 2)); // left end
        assertEquals(2, g.get(3, 2)); // middle
        assertEquals(3, g.get(4, 2)); // right end
    }

    @Test
    void gateIsPreservedAndConnectsNeighbours() {
        ChunkedTileLayer g = grid();
        FenceAutotiler a = new FenceAutotiler(g);
        a.paint(2, 2);
        a.paintGate(3, 2, 17); // a closed gate to the right of the fence
        a.paint(4, 2);
        assertEquals(17, g.get(3, 2));            // gate tile kept as picked
        assertEquals(1, g.get(2, 2));             // left cell connects east to the gate -> left end
        assertEquals(3, g.get(4, 2));             // right cell connects west to the gate -> right end
    }

    @Test
    void eraseRetilesNeighbours() {
        ChunkedTileLayer g = grid();
        FenceAutotiler a = new FenceAutotiler(g);
        a.paint(2, 2);
        a.paint(3, 2);
        a.erase(3, 2);
        assertEquals(0, g.get(3, 2));
        assertEquals(4, g.get(2, 2)); // back to a lone post
    }
}
