package world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@link ChunkedTileLayer} is the sparse-chunk drop-in for the old {@code TileLayer}: it must behave like a
 * dense bounded grid through {@code get/set}, stay sparse (only touched chunks allocate), and round-trip
 * through its chunk API so the save can persist just the non-empty chunks. Pure logic, no GL.
 */
class ChunkedTileLayerTest {
    private static final float TILE = 96f;

    @Test
    void emptyLayerReadsZeroEverywhere() {
        ChunkedTileLayer layer = new ChunkedTileLayer(100, 80, TILE);
        assertEquals(100, layer.cols());
        assertEquals(80, layer.rows());
        assertEquals(0, layer.get(0, 0));
        assertEquals(0, layer.get(50, 40));
        assertEquals(0, layer.get(99, 79));
    }

    @Test
    void setAndGetAcrossChunkBoundaries() {
        // CHUNK_SIZE is 32, so these points land in four different chunks.
        ChunkedTileLayer layer = new ChunkedTileLayer(100, 80, TILE);
        layer.set(0, 0, 7);
        layer.set(31, 31, 8);   // last cell of chunk (0,0)
        layer.set(32, 32, 9);   // first cell of chunk (1,1)
        layer.set(99, 79, 5);   // far corner
        assertEquals(7, layer.get(0, 0));
        assertEquals(8, layer.get(31, 31));
        assertEquals(9, layer.get(32, 32));
        assertEquals(5, layer.get(99, 79));
        assertEquals(0, layer.get(33, 33)); // untouched neighbour stays empty
    }

    @Test
    void outOfBoundsReadsZeroAndIgnoresWrites() {
        ChunkedTileLayer layer = new ChunkedTileLayer(40, 22, TILE);
        assertEquals(0, layer.get(-1, 0));
        assertEquals(0, layer.get(0, -1));
        assertEquals(0, layer.get(40, 0));
        assertEquals(0, layer.get(0, 22));
        layer.set(-5, -5, 3);
        layer.set(100, 100, 3);
        assertEquals(0, layer.get(-5, -5));
        assertEquals(0, layer.get(100, 100));
    }

    @Test
    void staysSparseUntilWritten() {
        ChunkedTileLayer layer = new ChunkedTileLayer(100, 80, TILE);
        assertEquals(0, countChunks(layer));
        layer.set(10, 10, 0); // a zero write must not allocate a chunk
        assertEquals(0, countChunks(layer));
        layer.set(10, 10, 1);
        assertEquals(1, countChunks(layer)); // one populated chunk
        layer.set(11, 11, 1); // same chunk, no new allocation
        assertEquals(1, countChunks(layer));
        layer.set(40, 40, 1); // different chunk
        assertEquals(2, countChunks(layer));
    }

    @Test
    void roundTripsThroughChunkApi() {
        ChunkedTileLayer src = new ChunkedTileLayer(100, 80, TILE);
        src.set(3, 4, 11);
        src.set(50, 60, 22);
        src.set(99, 79, 33);

        // Copy every populated chunk into a fresh layer via the save-facing API, then compare.
        ChunkedTileLayer copy = new ChunkedTileLayer(100, 80, TILE);
        src.forEachChunk((chunkCol, chunkRow, blob) -> copy.setChunk(chunkCol, chunkRow, blob.clone()));
        assertEquals(11, copy.get(3, 4));
        assertEquals(22, copy.get(50, 60));
        assertEquals(33, copy.get(99, 79));
        assertEquals(0, copy.get(0, 0));
    }

    @Test
    void fromDenseMatchesSource() {
        int[][] dense = new int[40][22];
        dense[5][6] = 2;
        dense[39][21] = 4;
        ChunkedTileLayer layer = ChunkedTileLayer.fromDense(dense, TILE);
        assertEquals(40, layer.cols());
        assertEquals(22, layer.rows());
        assertEquals(2, layer.get(5, 6));
        assertEquals(4, layer.get(39, 21));
        assertEquals(0, layer.get(0, 0));
    }

    @Test
    void setChunkRejectsWrongSizedBlob() {
        ChunkedTileLayer layer = new ChunkedTileLayer(40, 22, TILE);
        assertThrows(IllegalArgumentException.class, () -> layer.setChunk(0, 0, new int[10]));
    }

    @Test
    void worldGridHelpersUseTileSize() {
        ChunkedTileLayer layer = new ChunkedTileLayer(40, 22, TILE);
        assertEquals(2, layer.colAtWorldX(2 * TILE + 5));
        assertEquals(3, layer.rowAtWorldY(3 * TILE + 5));
        assertTrue(layer.worldXOfCol(4) == 4 * TILE);
        assertTrue(layer.worldYOfRow(7) == 7 * TILE);
    }

    private static int countChunks(ChunkedTileLayer layer) {
        int[] count = {0};
        layer.forEachChunk((c, r, blob) -> count[0]++);
        return count[0];
    }
}
