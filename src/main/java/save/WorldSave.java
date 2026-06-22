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
    public int currentAnimation;  // the player animation row to restore (a WALK_* id)
    public int[] walkActions;     // the 4 selected walk-animation indices

    // Inventory (primitives, not the Inventory object).
    public int[] invItems;
    public int[] invAmounts;
    public int invToggle;

    // Tile layers (rectangular grids, [outer][inner]). Held dense here; the binary format persists them
    // per-chunk on disk (skipping all-zero 32×32 blocks), so this stays decoupled from the wire encoding.
    public int[][] collision;
    public int[][] planting;
    public int[][] grass;

    // The seed that generated this bounded world's grass — stored so the world has a stable identity.
    public long worldSeed;

    // Crops.
    public CropSave[] crops;

    // Editor-authored content (save v3+): the decoration tile layer (global palette ids, [col][row]) and the
    // free-placed props. Both may be null on an older save / a partially-built instance.
    public int[][] decoration;
    public ObjectSave[] objects;

    // Editor layer stack (save v4+): sand / sand-water / fence / props / user-added layers. Null on older saves.
    public LayerSave[] layers;

    public WorldSave() {
    }
}
