package items;

/**
 * A resolved inventory item: the same facts as {@code items.data.ItemBlueprint}, but with the icon
 * path already filled in (the {@code food/<id>.png} convention applied for crop items that didn't
 * override it). Deliberately engine-free — it holds an icon <em>path</em>, not a live texture, so the
 * registry stays out of texture lifecycle (the renderer in {@code PlayState.itemIcon} loads/disposes
 * those).
 */
public final class ItemType {
    public final int id;
    public final String name;
    public final String category;  // "tool" | "crop"
    public final String iconPath;  // HUD sprite path under resources/, or null for none
    public final int cropIndex;    // crop items: the CropType index they plant; -1 otherwise

    public ItemType(int id, String name, String category, String iconPath, int cropIndex) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.iconPath = iconPath;
        this.cropIndex = cropIndex;
    }

    public boolean isCrop() {
        return cropIndex >= 0;
    }
}
