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
 * a null-filled array stands in). These pin the derivation that's easy to get subtly wrong: the
 * {@code 100+index} id offset, the {@code food/<id>.png} icon convention, the crop-index reverse lookup
 * used when planting, and explicit blueprints winning over derived crop items on id collision.
 */
class ItemRegistryTest {

    /** A crop with no real sprites — ItemRegistry only reads id/name/index, never the textures. */
    private static CropType crop(int index, String name) {
        return new CropType(index, name, new TextureRegion[5], 1000L);
    }

    @Test
    void derivesCropItemsWithIdOffsetAndIconConvention() {
        ItemRegistry reg = new ItemRegistry(null, new CropType[]{crop(0, "Potato"), crop(6, "Carrot")});
        ItemType potato = reg.get(100); // 100 + index 0
        assertEquals("Potato", potato.name);
        assertEquals("crop", potato.category);
        assertEquals("food/100.png", potato.iconPath);
        assertEquals(0, potato.cropIndex);
        assertEquals("food/106.png", reg.get(106).iconPath); // 100 + index 6
    }

    @Test
    void includesExplicitToolsAndSkipsNulls() {
        ItemBlueprint hoe = new ItemBlueprint(1, "Hoe", "tool", null, -1);
        ItemRegistry reg = new ItemRegistry(new ItemBlueprint[]{hoe, null}, new CropType[]{null, crop(0, "Potato")});
        assertEquals("Hoe", reg.get(1).name);
        assertNull(reg.iconPath(1)); // tool with no icon
        assertEquals(2, reg.count()); // the Hoe + the one non-null crop; nulls skipped
    }

    @Test
    void explicitBlueprintWinsOnIdCollision() {
        // An explicit blueprint at id 100 should not be overwritten by the derived Potato (also id 100).
        ItemBlueprint override = new ItemBlueprint(100, "Override", "tool", "icons/x.png", -1);
        ItemRegistry reg = new ItemRegistry(new ItemBlueprint[]{override}, new CropType[]{crop(0, "Potato")});
        assertEquals("Override", reg.get(100).name);
        assertEquals("icons/x.png", reg.get(100).iconPath);
    }

    @Test
    void cropIndexForReverseLooksUpSeeds() {
        ItemRegistry reg = new ItemRegistry(
                new ItemBlueprint[]{new ItemBlueprint(1, "Hoe", "tool", null, -1)},
                new CropType[]{crop(0, "Potato")});
        assertEquals(0, reg.cropIndexFor(100)); // crop item -> its CropType index
        assertEquals(-1, reg.cropIndexFor(1));  // a tool plants nothing
        assertEquals(-1, reg.cropIndexFor(999)); // unknown id
    }
}
