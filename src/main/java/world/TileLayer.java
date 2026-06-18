package world;

/**
 * A rectangular grid of {@code int} tile values, indexed {@code [col][row]} (col = x, row = y) to match
 * the world's {@code collision}/{@code CropField} convention. It wraps a backing {@code int[][]} — the
 * same shape the save stores — and adds bounds-safe access plus <b>zoom-decoupled</b> world↔grid
 * coordinate helpers driven by a fixed {@code tileSize} in world units (the old layers multiplied by the
 * live {@code zoomMultiplier}; here the tile size is a constant, so the grid never moves when the camera
 * zooms). Pure Java: no AWT, no {@code Serializable}, no {@code Dictionary}.
 */
public final class TileLayer {
    private final int[][] cells; // [col][row]
    private final float tileSize;

    public TileLayer(int cols, int rows, float tileSize) {
        this.cells = new int[cols][rows];
        this.tileSize = tileSize;
    }

    /** Wrap an existing array (e.g. straight from the save) without copying. */
    public TileLayer(int[][] cells, float tileSize) {
        this.cells = cells;
        this.tileSize = tileSize;
    }

    public int cols() { return cells.length; }

    public int rows() { return cells[0].length; }

    public boolean inBounds(int col, int row) {
        return col >= 0 && col < cells.length && row >= 0 && row < cells[0].length;
    }

    /** Tile value at (col,row), or 0 when out of bounds (treats off-map as empty). */
    public int get(int col, int row) {
        return inBounds(col, row) ? cells[col][row] : 0;
    }

    public void set(int col, int row, int value) {
        if (inBounds(col, row)) cells[col][row] = value;
    }

    /** The backing array, so the layer round-trips through the save as a plain {@code int[][]}. */
    public int[][] data() { return cells; }

    public float tileSize() { return tileSize; }

    // World <-> grid. Decoupled from camera zoom: tileSize is fixed world units per cell.
    public int colAtWorldX(float worldX) { return (int) Math.floor(worldX / tileSize); }

    public int rowAtWorldY(float worldY) { return (int) Math.floor(worldY / tileSize); }

    public float worldXOfCol(int col) { return col * tileSize; }

    public float worldYOfRow(int row) { return row * tileSize; }
}
