package crops;

/**
 * The grid of planted crops over the world, indexed {@code [col][row]} to match the world's
 * {@code collision[j][i]} (j = column = x, i = row = y) convention. {@code null} means an empty tile.
 */
public final class CropField {
    private final PlantedCrop[][] grid;

    public CropField(int cols, int rows) {
        grid = new PlantedCrop[cols][rows];
    }

    public boolean inBounds(int col, int row) {
        return col >= 0 && col < grid.length && row >= 0 && row < grid[0].length;
    }

    public PlantedCrop at(int col, int row) {
        return inBounds(col, row) ? grid[col][row] : null;
    }

    public boolean isEmpty(int col, int row) {
        return inBounds(col, row) && grid[col][row] == null;
    }

    public void plant(int col, int row, CropType type, long now) {
        if (inBounds(col, row)) grid[col][row] = new PlantedCrop(type, now);
    }

    /** Removes and returns the crop at the tile (e.g. on harvest), or null if empty/out of bounds. */
    public PlantedCrop remove(int col, int row) {
        if (!inBounds(col, row)) return null;
        PlantedCrop p = grid[col][row];
        grid[col][row] = null;
        return p;
    }

    public int cols() {
        return grid.length;
    }

    public int rows() {
        return grid[0].length;
    }
}
