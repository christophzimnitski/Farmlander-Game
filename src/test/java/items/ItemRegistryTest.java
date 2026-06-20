package items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import crops.CropType;
import items.data.ItemBlueprint;
import org.junit.jupiter.api.Test;

/**
 * {@link ItemRegistry} merges explicit item blueprints (the tools) with crop items derived from the crop
 * catalog — all pure data, no GL (a {@code CropType} only needs the <em>length</em> of its stage array, so
 * a null-filled array stands in). These pin the derivation that's easy to get subtly wrong: each crop yields
 * a <em>produce</em> item ({@code 100+index}, the sellable harvest with the {@code food/<id>.png} icon, not
 * plantable) and a <em>seed</em> item ({@code 200+index}, what you sow — it carries the crop index and has
 * no file icon), the crop-index reverse lookup used when planting, and explicit blueprints winning on id
 * collision.
 */
class ItemRegistryTest {

    /** A crop with no real sprites — ItemRegistry only reads id/name/index, never the textures. */
    private static CropType crop(int index, String name) {
        return new CropType(index, name, new TextureRegion[5], 1000L);
    }

    @Test
    void derivesProduceItemsWithIdOffsetAndIconConvention() {
        ItemRegistry reg = new ItemRegistry(null, new CropType[]{crop(0, "Potato"), crop(6, "Carrot")});
        ItemType potato = reg.get(100); // produce = 100 + index 0
        assertEquals("Potato", potato.name);
        assertEquals("produce", potato.category);
        assertEquals("food/100.png", potato.iconPath);
        assertEquals(-1, potato.cropIndex, "produce is a sellable good, not plantable");
        assertEquals("food/106.png", reg.get(106).iconPath); // 100 + index 6
    }

    @Test
    void derivesSeedItemsDistinctFromProduce() {
        ItemRegistry reg = new ItemRegistry(null, new CropType[]{crop(0, "Potato"), crop(6, "Carrot")});
        ItemType seed = reg.get(200); // seed = 200 + index 0
        assertEquals("Potato Seeds", seed.name);
        assertEquals("seed", seed.category);
        assertNull(seed.iconPath, "seed icon is generated (packet + crop), not a file");
        assertEquals(0, seed.cropIndex, "the seed is what plants the crop");
        assertEquals(6, reg.get(206).cropIndex); // 200 + index 6
        assertEquals(4, reg.count()); // 2 produce + 2 seeds
    }

    @Test
    void seedOverlayPathPointsAtTheProduceIcon() {
        ItemRegistry reg = new ItemRegistry(null, new CropType[]{crop(6, "Carrot")});
        assertEquals("food/106.png", reg.seedOverlayPath(206)); // the mini crop drawn on the packet
        assertNull(reg.seedOverlayPath(106)); // produce isn't a seed
        assertNull(reg.seedOverlayPath(999)); // unknown id
    }

    @Test
    void includesExplicitToolsAndSkipsNulls() {
        ItemBlueprint hoe = new ItemBlueprint(1, "Hoe", "tool", null, -1);
        ItemRegistry reg = new ItemRegistry(new ItemBlueprint[]{hoe, null}, new CropType[]{null, crop(0, "Potato")});
        assertEquals("Hoe", reg.get(1).name);
        assertNull(reg.iconPath(1)); // tool with no icon
        assertEquals(3, reg.count()); // the Hoe + the one non-null crop's produce & seed; nulls skipped
    }

    @Test
    void explicitBlueprintWinsOnIdCollision() {
        // An explicit blueprint at id 100 should not be overwritten by the derived Potato produce (also id 100).
        ItemBlueprint override = new ItemBlueprint(100, "Override", "tool", "icons/x.png", -1);
        ItemRegistry reg = new ItemRegistry(new ItemBlueprint[]{override}, new CropType[]{crop(0, "Potato")});
        assertEquals("Override", reg.get(100).name);
        assertEquals("icons/x.png", reg.get(100).iconPath);
    }

    @Test
    void cropIndexForResolvesSeedsButNotProduce() {
        ItemRegistry reg = new ItemRegistry(
                new ItemBlueprint[]{new ItemBlueprint(1, "Hoe", "tool", null, -1)},
                new CropType[]{crop(0, "Potato")});
        assertEquals(0, reg.cropIndexFor(200));  // seed -> its CropType index (this is what plants)
        assertEquals(-1, reg.cropIndexFor(100)); // produce no longer plants
        assertEquals(-1, reg.cropIndexFor(1));   // a tool plants nothing
        assertEquals(-1, reg.cropIndexFor(999)); // unknown id
    }

    @Test
    void toolIndexForLooksUpTheActivatedAnimationSet() {
        ItemBlueprint hoe = new ItemBlueprint(4, "Hoe", "tool", "tools/4.png", -1, 4);
        ItemRegistry reg = new ItemRegistry(new ItemBlueprint[]{hoe}, new CropType[]{crop(0, "Potato")});
        assertEquals(4, reg.toolIndexFor(4));    // the Hoe activates animation set 4 (tilling)
        assertEquals(-1, reg.toolIndexFor(100)); // a crop item is not a tool
        assertEquals(-1, reg.toolIndexFor(999)); // unknown id
    }
}
