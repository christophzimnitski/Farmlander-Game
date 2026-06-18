package world;

/**
 * A bounded grid of {@code int} tile values stored sparsely as fixed-size chunks. Presents a plain grid
 * surface — {@code get/set/cols/rows/tileSize} plus the zoom-decoupled world↔grid
 * helpers — so it is a <b>drop-in</b> for callers (the renderer's and collision's cell windows clamp to
 * {@code cols()/rows()} unchanged). The chunking is an internal storage detail: each {@value #CHUNK_SIZE}×
 * {@value #CHUNK_SIZE} block is allocated lazily on its first non-zero write, so a mostly-empty layer
 * (collision, tilled soil) costs only the chunks that hold something. The save mirrors this on disk by
 * persisting just the non-empty blocks (see {@code BinarySaveFormat}).
 *
 * <p>The world is intentionally <b>finite</b> (Stardew-style, not an infinite Minecraft world): cells
 * outside {@code [0,cols) × [0,rows)} read as {@code 0} and ignore writes, exactly like the old array.
 * Pure Java — no libGDX, so it's headlessly testable.
 */
public final class ChunkedTileLayer {
    /** Cells per side of a chunk. 32 → a 400×220 world is 13×7 = 91 chunks. */
    public static final int CHUNK_SIZE = 32;

    private final int cols, rows;     // world bounds in cells
    private final float tileSize;
    private final int chunkCols, chunkRows;
    private final int[][] chunks;     // [chunkRow*chunkCols + chunkCol] -> CHUNK_SIZE² blob, or null when all-zero

    public ChunkedTileLayer(int cols, int rows, float tileSize) {
        this.cols = cols;
        this.rows = rows;
        this.tileSize = tileSize;
        this.chunkCols = (cols + CHUNK_SIZE - 1) / CHUNK_SIZE;
        this.chunkRows = (rows + CHUNK_SIZE - 1) / CHUNK_SIZE;
        this.chunks = new int[chunkCols * chunkRows][];
    }

    /** Build from a dense {@code [col][row]} grid (e.g. migrating an old monolithic save). */
    public static ChunkedTileLayer fromDense(int[][] cells, float tileSize) {
        int cols = cells.length;
        int rows = cols == 0 ? 0 : cells[0].length;
        ChunkedTileLayer layer = new ChunkedTileLayer(cols, rows, tileSize);
        for (int col = 0; col < cols; col++)
            for (int row = 0; row < rows; row++)
                if (cells[col][row] != 0) layer.set(col, row, cells[col][row]);
        return layer;
    }

    /** Materialise the whole bounded grid as a dense {@code [col][row]} array (used at the save boundary). */
    public int[][] toDense() {
        int[][] cells = new int[cols][rows];
        for (int col = 0; col < cols; col++)
            for (int row = 0; row < rows; row++)
                cells[col][row] = get(col, row);
        return cells;
    }

    public int cols() { return cols; }

    public int rows() { return rows; }

    public float tileSize() { return tileSize; }

    public boolean inBounds(int col, int row) {
        return col >= 0 && col < cols && row >= 0 && row < rows;
    }

    /** Tile value at (col,row), or 0 when out of bounds or in an unallocated (all-zero) chunk. */
    public int get(int col, int row) {
        if (!inBounds(col, row)) return 0;
        int[] blob = chunks[chunkIndex(col, row)];
        return blob == null ? 0 : blob[localIndex(col, row)];
    }

    public void set(int col, int row, int value) {
        if (!inBounds(col, row)) return;
        int ci = chunkIndex(col, row);
        int[] blob = chunks[ci];
        if (blob == null) {
            if (value == 0) return; // stay sparse: never allocate a chunk just to store a zero
            blob = chunks[ci] = new int[CHUNK_SIZE * CHUNK_SIZE];
        }
        blob[localIndex(col, row)] = value;
    }

    // World <-> grid. Decoupled from camera zoom: tileSize is fixed world units per cell.
    public int colAtWorldX(float worldX) { return (int) Math.floor(worldX / tileSize); }

    public int rowAtWorldY(float worldY) { return (int) Math.floor(worldY / tileSize); }

    public float worldXOfCol(int col) { return col * tileSize; }

    public float worldYOfRow(int row) { return row * tileSize; }

    private int chunkIndex(int col, int row) {
        return (row / CHUNK_SIZE) * chunkCols + (col / CHUNK_SIZE);
    }

    private int localIndex(int col, int row) {
        return (row % CHUNK_SIZE) * CHUNK_SIZE + (col % CHUNK_SIZE);
    }
}
