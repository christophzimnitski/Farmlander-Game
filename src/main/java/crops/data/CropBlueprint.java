package crops.data;

/**
 * Plain data blueprint for one crop, as it appears in the data file (e.g. {@code data/crops.json}).
 *
 * <p>This is a dumb value holder: public fields, no behavior, no engine types. Keeping it free of
 * libGDX (and of {@code CropType}, which owns the live {@code TextureRegion}s) is what lets the
 * loader be swapped out later — only the {@link CropDataSource} implementation knows how the bytes
 * on disk become a {@code CropBlueprint[]}.
 *
 * <ul>
 *   <li>{@link #index} — the crop's id; its seed/produce inventory item is {@code 100 + index}.</li>
 *   <li>{@link #name} — display name.</li>
 *   <li>{@link #spriteColumn} / {@link #spriteTopRow} — column and TOP row of the crop's vertical
 *       stack of growth frames on {@code tilesets/crops.png} (32x64 tiles).</li>
 *   <li>{@link #stageDurations} — optional ms-per-stage (length {@code stages-1}); {@code null}
 *       means "use the registry default for every stage".</li>
 * </ul>
 */
public final class CropBlueprint {
    public int index;
    public String name;
    public int spriteColumn;
    public int spriteTopRow;
    public long[] stageDurations; // optional; null -> default for all stages
    public boolean leavesLump = true; // true = root crop (shovel clears its lump); false = continuous/trellis (axe removes it)

    /** Required no-arg constructor for reflective deserialization (libGDX Json). */
    public CropBlueprint() {
    }

    public CropBlueprint(int index, String name, int spriteColumn, int spriteTopRow, long[] stageDurations) {
        this.index = index;
        this.name = name;
        this.spriteColumn = spriteColumn;
        this.spriteTopRow = spriteTopRow;
        this.stageDurations = stageDurations;
    }
}
