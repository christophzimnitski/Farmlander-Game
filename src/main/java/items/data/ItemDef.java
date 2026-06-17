package items.data;

/**
 * Plain data definition of one inventory item, as it appears in the data file (e.g.
 * {@code data/items.json}).
 *
 * <p>A dumb value holder: public fields, no behavior, no engine types — same shape as
 * {@code crops.data.CropDef} and {@code tools.data.ToolActionDef}, which is what lets the loader be
 * swapped out behind {@link ItemDataSource}.
 *
 * <p>The data file only carries the items that aren't already implied by another dataset — today
 * that's the five tools. <em>Crop</em> items (seeds/produce, ids {@code 100+cropIndex}) are derived
 * from the crop catalog at registry-build time, so their names live once in {@code crops.json}.
 *
 * <ul>
 *   <li>{@link #id} — the inventory item id (matches the old {@code utilz.Constants.InventoryConstants}
 *       integers: tools 1-5, crop items 100+).</li>
 *   <li>{@link #name} — display name.</li>
 *   <li>{@link #type} — {@code "tool"} or {@code "crop"}; what role the item plays.</li>
 *   <li>{@link #icon} — optional HUD sprite path under {@code resources/}; {@code null} means "none"
 *       for tools, or the {@code food/<id>.png} convention for crop items (filled in by the registry).</li>
 *   <li>{@link #cropIndex} — for crop items, the {@code CropType} index they plant; {@code -1} otherwise.</li>
 * </ul>
 */
public final class ItemDef {
    public int id;
    public String name;
    public String type;
    public String icon;       // optional HUD sprite path; null -> none / convention
    public int cropIndex = -1; // crop items only; -1 for tools

    /** Required no-arg constructor for reflective deserialization (libGDX Json). */
    public ItemDef() {
    }

    public ItemDef(int id, String name, String type, String icon, int cropIndex) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.icon = icon;
        this.cropIndex = cropIndex;
    }
}
