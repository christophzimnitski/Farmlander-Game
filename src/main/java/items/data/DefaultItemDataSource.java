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
 * inventory items), hence {@code iconPath == null}.
 */
public final class DefaultItemDataSource implements ItemDataSource {
    @Override
    public ItemBlueprint[] loadItemBlueprints() {
        // toolIndex = the playerAnimationArrays row the tool activates (1 chop / 2 mine / 3 water /
        // 4 till / 5 dig). iconPath points at the placeholder swatches in resources/tools/.
        return new ItemBlueprint[] {
            new ItemBlueprint(1, "Axe",          "tool", "tools/1.png", -1, 1),
            new ItemBlueprint(2, "Pickaxe",      "tool", "tools/2.png", -1, 2),
            new ItemBlueprint(3, "Watering Can", "tool", "tools/3.png", -1, 3),
            new ItemBlueprint(4, "Hoe",          "tool", "tools/4.png", -1, 4),
            new ItemBlueprint(5, "Shovel",       "tool", "tools/5.png", -1, 5),
        };
    }
}
