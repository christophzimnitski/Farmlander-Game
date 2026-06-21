package world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The bush 9-slice connectivity is pure (no GL): the tile picked from the four neighbours, the band/base
 * mapping, and that painting/erasing re-tiles the right cells and never merges different bush types.
 */
class BushAutotilerTest {

    private ChunkedTileLayer grid() { return new ChunkedTileLayer(8, 8, 96f); }

    @Test
    void tileForCoversCornersEdgesCentreAndIsolated() {
        assertEquals(1, BushAutotiler.tileFor(false, false, false, false)); // isolated -> single bush
        assertEquals(11, BushAutotiler.tileFor(true, true, true, true));    // fully surrounded -> centre
        assertEquals(7, BushAutotiler.tileFor(false, true, false, true));   // top-left corner
        assertEquals(9, BushAutotiler.tileFor(false, true, true, false));   // top-right corner
        assertEquals(13, BushAutotiler.tileFor(true, false, false, true));  // bottom-left corner
        assertEquals(15, BushAutotiler.tileFor(true, false, true, false));  // bottom-right corner
        assertEquals(8, BushAutotiler.tileFor(false, true, true, true));    // top edge
        assertEquals(14, BushAutotiler.tileFor(true, false, true, true));   // bottom edge
        assertEquals(10, BushAutotiler.tileFor(true, true, false, true));   // left edge
        assertEquals(12, BushAutotiler.tileFor(true, true, true, false));   // right edge
    }

    @Test
    void baseOfMapsEachBand() {
        assertEquals(0, BushAutotiler.baseOf(1));
        assertEquals(0, BushAutotiler.baseOf(18));
        assertEquals(18, BushAutotiler.baseOf(19));
        assertEquals(36, BushAutotiler.baseOf(54));
        assertEquals(-1, BushAutotiler.baseOf(0));
        assertEquals(-1, BushAutotiler.baseOf(55));
    }

    @Test
    void singlePaintIsTheIsolatedTile() {
        ChunkedTileLayer g = grid();
        new BushAutotiler(g).paint(4, 4, 0);
        assertEquals(1, g.get(4, 4)); // tallgrass band, single-bush tile
    }

    @Test
    void twoByTwoBlockBecomesFourCorners() {
        ChunkedTileLayer g = grid();
        BushAutotiler a = new BushAutotiler(g);
        a.paint(2, 2, 0);
        a.paint(3, 2, 0);
        a.paint(2, 3, 0);
        a.paint(3, 3, 0);
        assertEquals(7, g.get(2, 2));  // TL
        assertEquals(9, g.get(3, 2));  // TR
        assertEquals(13, g.get(2, 3)); // BL
        assertEquals(15, g.get(3, 3)); // BR
    }

    @Test
    void centreOfThreeByThreeIsTheCentreTile() {
        ChunkedTileLayer g = grid();
        BushAutotiler a = new BushAutotiler(g);
        for (int c = 1; c <= 3; c++) for (int r = 1; r <= 3; r++) a.paint(c, r, 0);
        assertEquals(11, g.get(2, 2));
    }

    @Test
    void wheatUsesItsOwnBandAndDoesNotMergeWithTallgrass() {
        ChunkedTileLayer g = grid();
        BushAutotiler a = new BushAutotiler(g);
        a.paint(2, 2, 0);   // tallgrass
        a.paint(3, 2, 18);  // wheat, right of it — different type, must not connect
        assertEquals(1, g.get(2, 2));       // tallgrass still isolated (no same-type neighbour)
        assertEquals(18 + 1, g.get(3, 2));  // wheat isolated, in its own band
    }

    @Test
    void eraseRetilesNeighbours() {
        ChunkedTileLayer g = grid();
        BushAutotiler a = new BushAutotiler(g);
        a.paint(2, 2, 0);
        a.paint(3, 2, 0); // a horizontal pair: 2,2 has a right neighbour
        a.erase(3, 2);
        assertEquals(0, g.get(3, 2));   // erased
        assertEquals(1, g.get(2, 2));   // back to isolated single bush
    }
}
