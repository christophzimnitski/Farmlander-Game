package crops.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The built-in crop catalog is pure data (no GL), so its band→sheet layout is fully unit-testable. These
 * pin the layout the renderer relies on: three vertical bands of 5 stacked rows, one column per crop, the
 * two duplicate-column gaps (no crop / item at index 22 and 55), and the short last band offset to columns
 * 29..31. A regression here would silently mis-slice crop sprites, so it's worth nailing down.
 */
class DefaultCropDataSourceTest {

    private Map<Integer, CropBlueprint> byIndex() {
        Map<Integer, CropBlueprint> map = new HashMap<>();
        for (CropBlueprint b : new DefaultCropDataSource().loadCropBlueprints()) map.put(b.index, b);
        return map;
    }

    @Test
    void emitsEveryRealCropAndSkipsTheTwoGaps() {
        Map<Integer, CropBlueprint> crops = byIndex();
        // 69 catalog slots minus the 2 duplicate-column gaps (22, 55) = 67 real crops.
        assertEquals(67, crops.size());
        assertNull(crops.get(22), "index 22 is a duplicate column — no crop / no item 122");
        assertNull(crops.get(55), "index 55 is a duplicate column — no crop / no item 155");
    }

    @Test
    void band0PlacesCropsInTheirOwnColumnAtTopRowZero() {
        Map<Integer, CropBlueprint> crops = byIndex();
        CropBlueprint potato = crops.get(0);
        assertEquals("Potato", potato.name);
        assertEquals(0, potato.spriteColumn);
        assertEquals(0, potato.spriteTopRow);
        // Maize sits at column 32 — past the 32-wide sheet; the source keeps it as an item only.
        assertEquals(32, crops.get(32).spriteColumn);
        assertEquals(0, crops.get(32).spriteTopRow);
    }

    @Test
    void band1StartsAtRow5WithColumnRelativeToTheBand() {
        Map<Integer, CropBlueprint> crops = byIndex();
        CropBlueprint asparagus = crops.get(33); // first crop of band 1
        assertEquals("Asparagus", asparagus.name);
        assertEquals(0, asparagus.spriteColumn); // column resets at each band's left edge
        assertEquals(5, asparagus.spriteTopRow);
    }

    @Test
    void band2IsTheShortBandOffsetToColumns29To31() {
        Map<Integer, CropBlueprint> crops = byIndex();
        assertEquals("Coffee", crops.get(66).name);
        assertEquals(29, crops.get(66).spriteColumn);
        assertEquals(10, crops.get(66).spriteTopRow);
        assertEquals("Kiwi", crops.get(68).name);
        assertEquals(31, crops.get(68).spriteColumn);
        assertEquals(10, crops.get(68).spriteTopRow);
    }

    @Test
    void everyEmittedColumnAndRowIsNonNegative() {
        for (CropBlueprint b : new DefaultCropDataSource().loadCropBlueprints()) {
            assertTrue(b.spriteColumn >= 0 && b.spriteTopRow >= 0, "negative slot for " + b.name);
        }
    }

    @Test
    void onlyRootCropsLeaveALumpEverythingElseIsAxeRemovable() {
        Map<Integer, CropBlueprint> crops = byIndex();
        // Lumps are the special case: exactly the 16 root/tuber/bulb crops (indices 0..15) — shovel clears them.
        for (int index = 0; index <= 15; index++) {
            assertTrue(crops.get(index).leavesLump, crops.get(index).name + " (root) should leave a lump");
        }
        // Everything else grows above ground — axe-removed, no lump. Spot-check the leafy crops the user hit
        // (broccoli, cabbage, lettuce, kale, cauliflower), plus berries/trellis and a fruit/squash.
        for (int index : new int[]{16, 17, 28, 35, 37, 39, 44, 45, 53, 61, 65, 68}) {
            assertFalse(crops.get(index).leavesLump, crops.get(index).name + " should be axe-removable (no lump)");
        }
        assertEquals("Cauliflower", crops.get(44).name); // guard the index→name mapping the fix relies on
    }
}
