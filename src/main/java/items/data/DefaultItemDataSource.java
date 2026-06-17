package items.data;

/**
 * The built-in item catalog: the five tools that used to live as integers in
 * {@code utilz.Constants.InventoryConstants}. Serves two roles — the fallback when
 * {@code data/items.json} is missing/unparseable, and the source of truth the JSON file was generated
 * from (keep the two in sync).
 *
 * <p>Only tools are listed here. Crop seed/produce items (ids {@code 100+cropIndex}) are derived from
 * the crop catalog by {@code items.ItemRegistry}, so their names aren't duplicated out of
 * {@code crops.json}. Tools currently have no HUD icon (they're chosen with the tool key, not held as
 * inventory items), hence {@code icon == null}.
 */
public final class DefaultItemDataSource implements ItemDataSource {
    @Override
    public ItemDef[] loadItems() {
        return new ItemDef[] {
            new ItemDef(1, "Axe",         "tool", null, -1),
            new ItemDef(2, "Pickaxe",     "tool", null, -1),
            new ItemDef(3, "Watering Can", "tool", null, -1),
            new ItemDef(4, "Hoe",         "tool", null, -1),
            new ItemDef(5, "Shovel",      "tool", null, -1),
        };
    }
}
