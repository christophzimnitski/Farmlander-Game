package crops;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * {@link CropRegistry} mostly slices a GL texture (verified visually), but its one GL-free invariant — the
 * column clamp that keeps the two vestigial out-of-sheet crops (Maize/Green Beans, column 32 of a 32-wide
 * sheet) from sampling past the texture — is worth pinning so the documented footgun stays closed.
 */
class CropRegistryTest {

    @Test
    void inSheetColumnsArePassedThrough() {
        assertEquals(0, CropRegistry.clampColumn(0, 32));
        assertEquals(31, CropRegistry.clampColumn(31, 32)); // last real column of a 32-wide sheet
    }

    @Test
    void outOfSheetColumnClampsToTheLastColumn() {
        assertEquals(31, CropRegistry.clampColumn(32, 32)); // Maize / Green Beans live here
        assertEquals(31, CropRegistry.clampColumn(99, 32));
    }
}
