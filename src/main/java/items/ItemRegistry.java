package items;

import java.util.LinkedHashMap;
import java.util.Map;

import crops.CropType;
import items.data.ItemDef;

/**
 * The single source of truth for "what inventory items exist" and the one place the
 * {@code id <-> crop} mapping is centralized (it used to be scattered as {@code itemId - 100} /
 * {@code 100 + index} magic in {@code CropRegistry} and {@code CropType}).
 *
 * <p>Two inputs are merged:
 * <ol>
 *   <li>explicit {@link ItemDef}s from the data file (today: the tools) — see {@code items.data};</li>
 *   <li><em>crop</em> items derived from the crop catalog: each {@link CropType} contributes one item
 *       at {@code type.itemId} named after the crop, with the {@code food/<id>.png} icon convention.</li>
 * </ol>
 * Deriving crop items (rather than listing them) keeps crop names single-sourced in {@code crops.json}
 * and means the {@code 100+index} offset stays defined exactly once, in {@link CropType}. Explicit
 * defs win on id collision.
 */
public final class ItemRegistry {
    private final Map<Integer, ItemType> byId = new LinkedHashMap<>();

    public ItemRegistry(ItemDef[] explicit, CropType[] crops) {
        if (explicit != null) {
            for (ItemDef d : explicit) {
                if (d == null) continue;
                String icon = d.icon; // tools: null unless the data file gives one
                byId.put(d.id, new ItemType(d.id, d.name, d.type, icon, d.cropIndex));
            }
        }
        if (crops != null) {
            for (CropType c : crops) {
                if (c == null) continue;
                byId.putIfAbsent(c.itemId, new ItemType(
                        c.itemId, c.name, "crop", "food/" + c.itemId + ".png", c.index));
            }
        }
    }

    public ItemType get(int id) {
        return byId.get(id);
    }

    /** HUD sprite path for an item, or {@code null} if the item is unknown or has no icon. */
    public String iconPath(int id) {
        ItemType t = byId.get(id);
        return t == null ? null : t.iconPath;
    }

    /** The crop index a held item plants, or {@code -1} if it isn't a crop seed. */
    public int cropIndexFor(int id) {
        ItemType t = byId.get(id);
        return (t == null) ? -1 : t.cropIndex;
    }

    public int count() {
        return byId.size();
    }
}
