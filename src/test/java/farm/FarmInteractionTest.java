package farm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import crops.CropField;
import crops.CropType;
import entities.PlayerCharacter;
import org.junit.jupiter.api.Test;
import world.ChunkedTileLayer;
import world.DualGridSoil;

/**
 * Pins the tool/seed aim-flex targeting math — the most fiddly part of the recent farming work, and until the
 * {@link FarmInteraction#setHoverCell} seam was added, untestable because the hover came straight off the
 * libGDX pointer. Directions are 0 up, 1 left, 2 down, 3 right. The player is placed so its standing
 * (hitbox-center) cell is exactly (5,5) at TILE=100: {@code hitboxCenterX = x+120 = 500 → col 5} and
 * {@code hitboxCenterY = y+219 = 500 → row 5}.
 */
class FarmInteractionTest {
    private static final int COLS = 16, ROWS = 16;
    private static final float TILE = 100f;

    private static FarmInteraction farmWithPlayerAt5x5() {
        DualGridSoil soil = new DualGridSoil(new ChunkedTileLayer(COLS, ROWS, TILE));
        PlayerCharacter player =
                new PlayerCharacter(380f, 281f, 34, new int[]{32, 33, 34, 35}, new TextureRegion[36][9]);
        // Only soil + player are touched by the targeting math; the rest of the deps stay null.
        return new FarmInteraction(soil, null, null, null, null, null, null, player, null, null);
    }

    @Test
    void withTheMouseAwayTheTargetIsTheCellInFrontOfThePlayer() {
        FarmInteraction f = farmWithPlayerAt5x5();
        f.setHoverCell(-1, -1); // mouse nowhere near the player → no flex, fall back to the facing cell

        assertEquals(5, f.targetCol(0)); assertEquals(4, f.targetRow(0)); // up    → (5,4)
        assertEquals(4, f.targetCol(1)); assertEquals(5, f.targetRow(1)); // left  → (4,5)
        assertEquals(5, f.targetCol(2)); assertEquals(5, f.targetRow(2)); // down  → feet edge lands on (5,5)
        assertEquals(6, f.targetCol(3)); assertEquals(5, f.targetRow(3)); // right → (6,5)
    }

    @Test
    void aMouseInsideTheOneTileFlexBlockAimsAtTheHoveredCell() {
        FarmInteraction f = farmWithPlayerAt5x5();
        // Facing down the expected cell is (5,5); a hover one cell off on each axis is inside the 3×3 flex.
        f.setHoverCell(6, 4);
        assertEquals(6, f.targetCol(2));
        assertEquals(4, f.targetRow(2));
    }

    @Test
    void aMouseOutsideTheFlexBlockFallsBackToTheFacingCell() {
        FarmInteraction f = farmWithPlayerAt5x5();
        f.setHoverCell(7, 5); // two cells right of the down-facing cell (5,5) → beyond the 1-tile flex
        assertEquals(5, f.targetCol(2));
        assertEquals(5, f.targetRow(2));
    }

    // --- chopAt: the axe reaps + clears an above-ground (non-lump) crop. Touches only the crop field and the
    //     inventory arrays, so the GL collaborators stay null. ---

    private static FarmInteraction farmWith(CropField field, int[] items, int[] amounts) {
        DualGridSoil soil = new DualGridSoil(new ChunkedTileLayer(COLS, ROWS, TILE));
        PlayerCharacter player =
                new PlayerCharacter(380f, 281f, 34, new int[]{32, 33, 34, 35}, new TextureRegion[36][9]);
        return new FarmInteraction(soil, field, null, null, null, null, null, player, items, amounts);
    }

    private static CropType aboveGroundCrop(int index, String name) {
        CropType c = new CropType(index, name, new TextureRegion[5], 1000L); // 5 frames → grown at stage 3
        c.leavesLump = false; // above-ground → the axe's job
        return c;
    }

    @Test
    void axeReapsAGrownAboveGroundCropThenClearsIt() {
        CropField field = new CropField(COLS, ROWS);
        int[] items = new int[10], amounts = new int[10];
        FarmInteraction f = farmWith(field, items, amounts);
        field.plant(3, 3, aboveGroundCrop(61, "Tomato"), System.currentTimeMillis() - 1_000_000L); // long mature

        f.chopAt(3, 3);

        assertEquals(161, items[0], "produce (100+index) banked when a grown plant is axed");
        assertEquals(1, amounts[0]);
        assertNull(field.at(3, 3), "the plant is removed");
    }

    @Test
    void axeDestroysAnUnripePlantWithoutProduce() {
        CropField field = new CropField(COLS, ROWS);
        int[] items = new int[10], amounts = new int[10];
        FarmInteraction f = farmWith(field, items, amounts);
        field.plant(3, 3, aboveGroundCrop(61, "Tomato"), System.currentTimeMillis()); // just planted → unripe

        f.chopAt(3, 3);

        assertEquals(0, items[0], "no produce from an unripe plant");
        assertNull(field.at(3, 3), "but it's still cleared");
    }
}
