package world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Locks the dual-grid connection logic — the pure {@link DualGridSoil#tileForMask(int)} table that was
 * derived by measuring {@code plowed_soil.png}. This is the standalone, no-GL unit the design promised:
 * if a future edit perturbs the Wang table, these assertions fail before anything draws a pixel.
 */
class DualGridSoilTest {

    /** The full mask→tile expectation, mirroring the table in {@link DualGridSoil}. */
    private static final int[] EXPECTED = {
            0, 15, 13, 14, 9, 12, 1, 2, 7, 4, 10, 3, 8, 5, 6, 11,
    };

    @Test
    void everyMaskMapsToItsTile() {
        for (int mask = 0; mask < 16; mask++) {
            assertEquals(EXPECTED[mask], DualGridSoil.tileForMask(mask),
                    "mask " + mask + " should map to tile " + EXPECTED[mask]);
        }
    }

    @Test
    void maskIsMaskedToLowFourBits() {
        // Callers OR in TL/TR/BL/BR; higher bits must be ignored, never index out of range.
        assertEquals(DualGridSoil.tileForMask(0), DualGridSoil.tileForMask(0x10));
        assertEquals(DualGridSoil.tileForMask(15), DualGridSoil.tileForMask(0xFF));
    }

    @Test
    void emptyCornerDrawsNothing() {
        assertEquals(0, DualGridSoil.tileForMask(0), "no tilled corner → draw nothing (grass shows)");
    }

    @Test
    void singleTilledCellFormsAFourTileSquare() {
        // One tilled cell is seen by its four surrounding corner nodes. Each node sees exactly one
        // filled corner, so the four nodes draw the four distinct outer-corner tiles that meet in a square.
        DualGridSoil soil = new DualGridSoil(new ChunkedTileLayer(3, 3, 1f));
        soil.till(1, 1);

        int br = soil.maskAt(1, 1); // node whose bottom-right cell is (1,1)
        int bl = soil.maskAt(2, 1); // bottom-left cell is (1,1)
        int tr = soil.maskAt(1, 2); // top-right cell is (1,1)
        int tl = soil.maskAt(2, 2); // top-left cell is (1,1)

        assertEquals(DualGridSoil.BR, br);
        assertEquals(DualGridSoil.BL, bl);
        assertEquals(DualGridSoil.TR, tr);
        assertEquals(DualGridSoil.TL, tl);

        // Four different, non-empty corner tiles — a clean square, no duplicates, nothing blank.
        int[] tiles = {
                DualGridSoil.tileForMask(br), DualGridSoil.tileForMask(bl),
                DualGridSoil.tileForMask(tr), DualGridSoil.tileForMask(tl),
        };
        for (int t : tiles) assertTrue(t > 0, "each corner of a single till must draw a tile");
        assertEquals(4, distinctCount(tiles), "the four corners must be four distinct tiles");
    }

    @Test
    void interiorOfAFilledBlockIsTheCenterTile() {
        // A node fully surrounded by tilled cells → the nine-slice center (tile 11).
        DualGridSoil soil = new DualGridSoil(new ChunkedTileLayer(3, 3, 1f));
        for (int c = 0; c < 3; c++)
            for (int r = 0; r < 3; r++) soil.till(c, r);
        assertEquals(11, DualGridSoil.tileForMask(soil.maskAt(1, 1)));
    }

    @Test
    void wateringWetsTilledSoilThenItDriesOnATimer() {
        DualGridSoil soil = new DualGridSoil(new ChunkedTileLayer(3, 3, 1f));
        long t0 = 1_000_000L;

        // Can't water ground that was never tilled — nothing there to wet.
        soil.water(0, 0, t0);
        assertFalse(soil.isWatered(0, 0), "untilled ground can't be watered");

        soil.till(1, 1);
        soil.water(1, 1, t0);
        assertTrue(soil.isWatered(1, 1), "watering tilled soil wets it");
        assertTrue(soil.isPlantable(1, 1), "watered soil is still plantable");

        // Still wet just before the timeout; dries once it elapses.
        soil.update(t0 + 29_000L, 30_000L);
        assertTrue(soil.isWatered(1, 1), "still wet before the dry timeout");
        soil.update(t0 + 30_000L, 30_000L);
        assertFalse(soil.isWatered(1, 1), "wet soil dries once the timeout elapses");
        assertTrue(soil.isPlantable(1, 1), "drying leaves the soil tilled and plantable");
    }

    @Test
    void tillingShovellingAndUntillingAllRemoveWater() {
        DualGridSoil soil = new DualGridSoil(new ChunkedTileLayer(3, 3, 1f));
        long t0 = 5_000L;

        soil.till(0, 0); soil.water(0, 0, t0);
        soil.till(0, 0); // re-tilling
        assertFalse(soil.isWatered(0, 0), "re-tilling dries the soil");

        soil.till(1, 0); soil.water(1, 0, t0);
        soil.dry(1, 0); // shovel disturbance
        assertFalse(soil.isWatered(1, 0), "drying (shoveling) removes the water");

        soil.till(2, 0); soil.water(2, 0, t0);
        soil.clear(2, 0); // pickaxe un-till
        assertFalse(soil.isWatered(2, 0), "un-tilling removes the water");
        assertFalse(soil.isTilled(2, 0), "un-tilling reverts the cell to bare ground");
    }

    @Test
    void wateringDoesNotChangeTheDualGridMask() {
        // Water is a separate runtime layer, so a wet cell masks exactly like the dry tilled cell underneath.
        DualGridSoil dry = new DualGridSoil(new ChunkedTileLayer(3, 3, 1f));
        dry.till(1, 1);
        DualGridSoil wet = new DualGridSoil(new ChunkedTileLayer(3, 3, 1f));
        wet.till(1, 1);
        wet.water(1, 1, 0L);
        assertEquals(dry.maskAt(1, 1), wet.maskAt(1, 1), "watering must not change the soil's dual-grid shape");
    }

    private static int distinctCount(int[] values) {
        int distinct = 0;
        for (int i = 0; i < values.length; i++) {
            boolean seen = false;
            for (int j = 0; j < i; j++) if (values[j] == values[i]) seen = true;
            if (!seen) distinct++;
        }
        return distinct;
    }
}
