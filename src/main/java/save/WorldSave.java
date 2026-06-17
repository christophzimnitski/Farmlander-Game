package save;

/**
 * The complete persisted state of a world, as plain data — decoupled from <em>how</em> it's stored on
 * disk ({@link SaveFormat}) and from the live engine objects in {@code PlayState}. This separation is
 * what lets the byte encoding be swapped (binary today, see {@link BinarySaveFormat}) and is the seam a
 * future P5 chunked world would split per-chunk.
 *
 * <p>Notably the inventory is held as primitive {@code int[]}/{@code int} rather than an
 * {@code entities.Inventory} — so persistence no longer round-trips a {@link java.io.Serializable}
 * object, which kills the {@code serialVersionUID} landmine the old positional format had.
 *
 * <p>All fields are public and may be {@code null} on a partially-built instance; the format and the
 * caller agree on what's required.
 */
public final class WorldSave {
    // Player.
    public float playerX;
    public float playerY;
    public int currentAction;
    public int[] walkActions;     // the 4 selected walk-animation indices

    // Inventory (primitives, not the Inventory object).
    public int[] invItems;
    public int[] invAmounts;
    public int invToggle;

    // Tile layers (rectangular grids, [outer][inner]).
    public int[][] collision;
    public int[][] planting;
    public int[][] grass;

    // Crops.
    public CropSave[] crops;

    public WorldSave() {
    }
}
