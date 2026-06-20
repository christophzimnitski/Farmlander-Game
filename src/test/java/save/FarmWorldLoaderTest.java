package save;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import entities.Inventory;
import org.junit.jupiter.api.Test;

/**
 * Pins the save→world decode now that it's extracted out of {@code PlayState}'s constructor and into a pure
 * {@link FarmWorldLoader}: fresh-world defaults, top-left placement of saved grids into the bounded world,
 * the tilled-flag normalization, inventory rebuild + selected-slot clamp, seed handling, and crop passthrough.
 */
class FarmWorldLoaderTest {
    private static final int COLS = 16, ROWS = 12;
    private static final float TILE = 96f;

    private static FarmWorldLoader loader() {
        return new FarmWorldLoader(COLS, ROWS, TILE);
    }

    @Test
    void noSaveMintsAFreshWorldWithSpawnDefaults() {
        FarmWorldLoader.LoadedWorld w = loader().load(null);

        assertFalse(w.soil().isTilled(0, 0), "fresh soil is untilled");
        assertEquals(0, w.collision().get(3, 3), "fresh collision is empty");
        assertEquals(COLS, w.grass().cols(), "grass is generated at world size");
        assertEquals(ROWS, w.grass().rows());

        assertEquals(2000f, w.playerX());
        assertEquals(800f, w.playerY());
        assertEquals(34, w.animation());
        assertArrayEquals(new int[]{32, 33, 34, 35}, w.walkActions());

        assertEquals(0, w.selectedSlot());
        assertEquals(Inventory.SIZE, w.inventoryItems().length, "inventory is sized to the bag, not the save");
        assertEquals(0, w.inventoryItems()[0], "fresh inventory is empty");
        assertNull(w.crops(), "no save → no crops to restore");
        assertNotEquals(0L, w.worldSeed(), "a fresh world mints a real seed");
    }

    @Test
    void savedGridsLandTopLeftAndTheTilledFlagIsNormalized() {
        WorldSave ws = new WorldSave();
        ws.worldSeed = 123456789L;
        ws.invToggle = 0;
        // 2x2 corner grids placed into the 16x12 world (col-major: grid[col][row]).
        ws.collision = new int[][]{{0, 1}, {0, 0}};
        ws.planting  = new int[][]{{7, 0}, {0, 0}}; // legacy autotile index 7 must normalize to a tilled flag
        ws.grass     = new int[][]{{2, 0}, {0, 0}};
        ws.invItems   = new int[]{100, 5, 0};
        ws.invAmounts = new int[]{9, 1, 0};
        ws.playerX = 1561f;
        ws.playerY = 625f;
        ws.currentAnimation = 35;
        ws.walkActions = new int[]{32, 33, 34, 35};
        ws.crops = new CropSave[]{new CropSave(3, 4, 0, 1_700_000_000_000L, false)};

        FarmWorldLoader.LoadedWorld w = loader().load(ws);

        assertEquals(1, w.collision().get(0, 1), "collision placed at its corner cell");
        assertTrue(w.soil().isTilled(0, 0), "a nonzero legacy planting index becomes tilled");
        assertFalse(w.soil().isTilled(1, 1), "a zero planting cell stays untilled");
        assertTrue(w.soil().isPlantable(0, 0), "normalized tilled soil is plantable");
        assertEquals(2, w.grass().get(0, 0), "grass copied verbatim");

        assertEquals(Inventory.SIZE, w.inventoryItems().length);
        assertEquals(100, w.inventoryItems()[0]);
        assertEquals(9, w.inventoryAmounts()[0]);
        assertEquals(0, w.inventoryItems()[Inventory.SIZE - 1], "the tail past a short save stays empty");

        assertEquals(1561f, w.playerX(), "spawn comes from the save");
        assertEquals(123456789L, w.worldSeed(), "a real saved seed is preserved");
        assertEquals(1, w.crops().length);
        assertEquals(3, w.crops()[0].col, "crops pass straight through for replay");
    }

    @Test
    void selectedSlotComesFromInvToggleClampedToTheHotbar() {
        WorldSave ws = new WorldSave();
        ws.invToggle = 4;
        assertEquals(4, loader().load(ws).selectedSlot());

        ws.invToggle = 99; // past the hotbar → clamped to the last hotbar slot
        assertEquals(Inventory.HOTBAR - 1, loader().load(ws).selectedSlot());
    }

    @Test
    void aZeroSeedFromAPreSeedSaveIsMintedFresh() {
        WorldSave ws = new WorldSave();
        ws.worldSeed = 0L; // a v1 save predates worldSeed
        assertNotEquals(0L, loader().load(ws).worldSeed());
    }
}
