package items;

import java.util.LinkedHashMap;
import java.util.Map;

import crops.CropType;
import items.data.ItemBlueprint;

/**
 * The single source of truth for "what inventory items exist" and the one place the
 * {@code id <-> crop} mapping is centralized (it used to be scattered as {@code itemId - 100} /
 * {@code 100 + index} magic in {@code CropRegistry} and {@code CropType}).
 *
 * <p>Two inputs are merged:
 * <ol>
 *   <li>explicit {@link ItemBlueprint}s from the data file (today: the tools) — see {@code items.data};</li>
 *   <li><em>crop</em> items derived from the crop catalog: each {@link CropType} contributes <b>two</b>
 *       items — a <b>produce</b> item at {@code type.itemId} ({@code 100+index}, the sellable harvest with
 *       the {@code food/<id>.png} icon, <em>not</em> plantable) and a <b>seed</b> item at
 *       {@code type.seedItemId} ({@code 200+index}, what you sow; it carries the crop index so it plants,
 *       and its packet icon is generated at render time rather than loaded from a file).</li>
 * </ol>
 * Deriving crop items (rather than listing them) keeps crop names single-sourced in {@code crops.json}
 * and means the {@code 100+index} / {@code 200+index} offsets stay defined exactly once, in
 * {@link CropType}. Explicit blueprints win on id collision.
 */
public final class ItemRegistry {
    private final Map<Integer, ItemType> byId = new LinkedHashMap<>();

    public ItemRegistry(ItemBlueprint[] explicit, CropType[] crops) {
        if (explicit != null) {
            for (ItemBlueprint b : explicit) {
                if (b == null) continue;
                String iconPath = b.iconPath; // tools: null unless the data file gives one
                byId.put(b.id, new ItemType(b.id, b.name, b.category, iconPath, b.cropIndex, b.toolIndex));
            }
        }
        if (crops != null) {
            for (CropType c : crops) {
                if (c == null) continue;
                // Produce: the harvested good. Sellable, NOT plantable (cropIndex -1), keeps the food/<id>.png icon.
                byId.putIfAbsent(c.itemId, new ItemType(
                        c.itemId, c.name, "produce", "food/" + c.itemId + ".png", -1, -1));
                // Seed: what you sow. Plantable (carries the crop index); icon is generated (packet + crop), no file.
                byId.putIfAbsent(c.seedItemId, new ItemType(
                        c.seedItemId, c.name + " Seeds", "seed", null, c.index, -1));
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

    /** Every item that declares a HUD icon, as {@code id -> path} (registry order). Lets a boot-time check
     *  verify the art actually exists, so a missing {@code food/<id>.png} surfaces as a startup WARN instead
     *  of a silently blank slot. */
    public Map<Integer, String> iconPaths() {
        Map<Integer, String> paths = new LinkedHashMap<>();
        for (ItemType t : byId.values()) if (t.iconPath != null) paths.put(t.id, t.iconPath);
        return paths;
    }

    /** The crop index a held item plants, or {@code -1} if it isn't a crop seed. */
    public int cropIndexFor(int id) {
        ItemType t = byId.get(id);
        return (t == null) ? -1 : t.cropIndex;
    }

    /** For a seed item, the {@code food/<id>.png} path of the produce it grows — the mini crop overlaid on
     *  the generated seed packet. {@code null} if {@code seedId} isn't a seed or that produce has no icon. */
    public String seedOverlayPath(int seedId) {
        ItemType seed = byId.get(seedId);
        if (seed == null || !seed.isSeed()) return null;
        ItemType produce = byId.get(CropType.PRODUCE_ITEM_BASE + seed.cropIndex);
        return produce == null ? null : produce.iconPath;
    }

    /** The player-animation/tool index a held item activates, or {@code -1} if it isn't a tool. */
    public int toolIndexFor(int id) {
        ItemType t = byId.get(id);
        return (t == null) ? -1 : t.toolIndex;
    }

    /** The item ids that are tools (have a {@code toolIndex}), in registry order. Used to grant the player
     *  their tools as inventory items. */
    public int[] toolItemIds() {
        int n = 0;
        for (ItemType t : byId.values()) if (t.isTool()) n++;
        int[] ids = new int[n];
        int i = 0;
        for (ItemType t : byId.values()) if (t.isTool()) ids[i++] = t.id;
        return ids;
    }

    public int count() {
        return byId.size();
    }
}
