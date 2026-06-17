package items.data;

/**
 * The swap seam for where inventory item blueprints come from. Today it's a JSON file
 * ({@link JsonItemDataSource}) with a built-in fallback ({@link DefaultItemDataSource}); a hand-rolled
 * loader later is a one-class change. Deliberately free of engine types.
 *
 * <p>Note this only supplies the items the data file owns (the tools). Crop seed/produce items are
 * derived from the crop catalog by {@code items.ItemRegistry}, not listed here.
 */
public interface ItemDataSource {
    ItemBlueprint[] loadItemBlueprints();
}
