package editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The id packing/unpacking math is pure (no GL): every (sheet, tile) round-trips, empty stays 0, ids from
 * different sheets never collide, and bounds are validated. {@link TilePalette#region} is GL-bound and not
 * exercised here.
 */
class TilePaletteTest {

    @Test
    void packUnpackRoundTrips() {
        for (int s = 0; s < TilePalette.sheetCount(); s++) {
            TilePalette.Sheet sheet = TilePalette.sheet(s);
            for (int t = 0; t < sheet.tileCount(); t++) {
                int id = TilePalette.pack(s, t);
                assertEquals(s, TilePalette.sheetIndexOf(id), "sheet of " + sheet.name + " tile " + t);
                assertEquals(t, TilePalette.tileIndexOf(id), "tile of " + sheet.name + " tile " + t);
                assertTrue(TilePalette.isValid(id), "valid " + sheet.name + " tile " + t);
            }
        }
    }

    @Test
    void emptyIsZeroAndInvalid() {
        assertEquals(0, TilePalette.EMPTY);
        assertFalse(TilePalette.isValid(TilePalette.EMPTY));
    }

    @Test
    void idsFromDifferentSheetsDoNotCollide() {
        int a = TilePalette.pack(0, 0);
        int b = TilePalette.pack(1, 0);
        assertNotEquals(a, b);
        assertEquals(0, TilePalette.sheetIndexOf(a));
        assertEquals(1, TilePalette.sheetIndexOf(b));
    }

    @Test
    void outOfRangeTileIsInvalid() {
        TilePalette.Sheet first = TilePalette.sheet(0);
        int pastEnd = TilePalette.pack(0, first.tileCount()); // one past the last tile
        assertFalse(TilePalette.isValid(pastEnd));
    }

    @Test
    void unknownSheetIsInvalid() {
        assertFalse(TilePalette.isValid(TilePalette.pack(TilePalette.sheetCount(), 0)));
    }

    @Test
    void tileIndexIsRowMajor() {
        TilePalette.Sheet ore = TilePalette.sheet(1); // 16x16
        assertEquals(0, TilePalette.tileIndex(ore, 0, 0));
        assertEquals(1, TilePalette.tileIndex(ore, 1, 0));
        assertEquals(ore.cols, TilePalette.tileIndex(ore, 0, 1)); // first tile of row 1
    }
}
