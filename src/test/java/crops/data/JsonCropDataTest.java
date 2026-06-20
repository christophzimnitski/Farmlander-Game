package crops.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.files.FileHandle;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Guards the <em>shipped</em> {@code data/crops.json}, not just {@link DefaultCropDataSource} (the fallback).
 * The game loads the JSON file via {@link JsonCropDataSource}; the default source is only used when the file
 * is missing/unparseable. A bug where cauliflower stayed a shovel-lump in-game came exactly from fixing the
 * default while the JSON still said otherwise — so these assertions pin the real data the player runs against.
 *
 * <p>Pure Java: libGDX's {@code Json} + a {@code File}-backed {@code FileHandle} need no GL/backend, so the
 * actual load path is exercised headlessly.
 */
class JsonCropDataTest {

    private static Map<Integer, CropBlueprint> shippedCrops() throws Exception {
        File file = new File(JsonCropDataTest.class.getResource("/data/crops.json").toURI());
        CropBlueprint[] blueprints = new JsonCropDataSource(new FileHandle(file)).loadCropBlueprints();
        Map<Integer, CropBlueprint> byIndex = new HashMap<>();
        for (CropBlueprint b : blueprints) byIndex.put(b.index, b);
        return byIndex;
    }

    @Test
    void cauliflowerIsAxeRemovableInTheShippedData() throws Exception {
        CropBlueprint cauliflower = shippedCrops().get(44);
        assertNotNull(cauliflower, "crops.json must define index 44");
        assertEquals("Cauliflower", cauliflower.name);
        assertFalse(cauliflower.leavesLump, "Cauliflower must be non-lump (axe-cleared) in crops.json, not just the fallback");
    }

    @Test
    void onlyRootCropsAreLumpsInTheShippedData() throws Exception {
        Map<Integer, CropBlueprint> crops = shippedCrops();
        // The 16 root crops (0..15) are the only lumps in the shipped file.
        for (int index = 0; index <= 15; index++) {
            assertTrue(crops.get(index).leavesLump, crops.get(index).name + " (root) must be a lump in crops.json");
        }
        // Above-ground crops are non-lump in the shipped file — the leafy ones the player hits, plus
        // berries/trellis: scallion, peppers, squash, lettuces, kale, cabbage, broccoli, tomato, beans, kiwi.
        for (int index : new int[]{16, 17, 28, 35, 37, 39, 45, 53, 61, 65, 68}) {
            assertFalse(crops.get(index).leavesLump, crops.get(index).name + " must be non-lump in crops.json");
        }
    }
}
