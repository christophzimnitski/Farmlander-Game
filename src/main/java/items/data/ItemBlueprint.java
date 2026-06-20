package items.data;

/**
 * Plain data blueprint for one inventory item, as it appears in the data file (e.g.
 * {@code data/items.json}).
 *
 * <p>A dumb value holder: public fields, no behavior, no engine types — same shape as
 * {@code crops.data.CropBlueprint} and {@code tools.data.ToolActionBlueprint}, which is what lets the
 * loader be swapped out behind {@link ItemDataSource}.
 *
 * <p>The data file only carries the items that aren't already implied by another dataset — today
 * that's the five tools. <em>Crop</em> items — produce (ids {@code 100+cropIndex}) and seeds (ids
 * {@code 200+cropIndex}) — are derived from the crop catalog at registry-build time, so their names
 * live once in {@code crops.json}.
 *
 * <ul>
 *   <li>{@link #id} — the inventory item id (tools 1-5, produce 100+, seeds 200+).</li>
 *   <li>{@link #name} — display name.</li>
 *   <li>{@link #category} — {@code "tool"}, {@code "seed"}, or {@code "produce"}; what role the item plays.</li>
 *   <li>{@link #iconPath} — optional HUD sprite path under {@code resources/}; {@code null} means
 *       "none" for tools / a generated icon for seeds, or the {@code food/<id>.png} convention for produce
 *       (filled in by the registry).</li>
 *   <li>{@link #cropIndex} — for seed items, the {@code CropType} index they plant; {@code -1} otherwise.</li>
 *   <li>{@link #toolIndex} — for tools, the player-animation set they activate (chop/mine/water/till/dig);
 *       {@code -1} otherwise. Selecting the tool in the hotbar makes that the player's active tool.</li>
 * </ul>
 */
public final class ItemBlueprint {
    public int id;
    public String name;
    public String category;   // "tool", "seed", or "produce"
    public String iconPath;   // optional HUD sprite path; null -> none / generated / convention
    public int cropIndex = -1; // seed items only; -1 for produce/tools
    public int toolIndex = -1; // tools only: the player-animation set they activate; -1 for non-tools

    /** Required no-arg constructor for reflective deserialization (libGDX Json). */
    public ItemBlueprint() {
    }

    public ItemBlueprint(int id, String name, String category, String iconPath, int cropIndex) {
        this(id, name, category, iconPath, cropIndex, -1);
    }

    public ItemBlueprint(int id, String name, String category, String iconPath, int cropIndex, int toolIndex) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.iconPath = iconPath;
        this.cropIndex = cropIndex;
        this.toolIndex = toolIndex;
    }
}
