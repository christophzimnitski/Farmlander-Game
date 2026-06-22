package editor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The picker's drag-select rectangle math ({@link SheetPicker#selection}) is pure id math (no GL): a single
 * cell yields a 1×1 selection, a span builds the right row-major block of ids, and the result is independent
 * of the drag direction. The modal drawing/hit-testing is GL-bound and not exercised here.
 */
class SheetPickerTest {

    private static final int ORE = 1; // "Rocks & ore", 16x16 — big enough for a multi-cell rectangle

    @Test
    void singleCellIsOneByOne() {
        int cols = TilePalette.sheet(ORE).cols;
        int cell = 2 * cols + 3; // row 2, col 3
        SheetPicker.Selection sel = SheetPicker.selection(ORE, cell, cell);
        assertEquals(1, sel.cols);
        assertEquals(1, sel.rows);
        assertTrue(sel.isSingle());
        assertEquals(TilePalette.pack(ORE, cell), sel.topLeftId());
        assertEquals(1, sel.ids.length);
    }

    @Test
    void spanBuildsRowMajorBlock() {
        int cols = TilePalette.sheet(ORE).cols;
        int start = 1 * cols + 1; // row 1, col 1
        int end = 3 * cols + 2;   // row 3, col 2  -> 2 wide, 3 tall
        SheetPicker.Selection sel = SheetPicker.selection(ORE, start, end);
        assertEquals(2, sel.cols);
        assertEquals(3, sel.rows);
        assertFalse(sel.isSingle());
        assertEquals(6, sel.ids.length);
        // row-major from the top-left corner: (r1c1, r1c2, r2c1, r2c2, r3c1, r3c2)
        int[] expected = {
                TilePalette.pack(ORE, 1 * cols + 1), TilePalette.pack(ORE, 1 * cols + 2),
                TilePalette.pack(ORE, 2 * cols + 1), TilePalette.pack(ORE, 2 * cols + 2),
                TilePalette.pack(ORE, 3 * cols + 1), TilePalette.pack(ORE, 3 * cols + 2),
        };
        assertArrayEquals(expected, sel.ids);
    }

    @Test
    void selectionIsDirectionAgnostic() {
        int cols = TilePalette.sheet(ORE).cols;
        int a = 1 * cols + 1; // top-left
        int b = 3 * cols + 4; // bottom-right
        SheetPicker.Selection forward = SheetPicker.selection(ORE, a, b);
        SheetPicker.Selection reverse = SheetPicker.selection(ORE, b, a);
        assertEquals(forward.cols, reverse.cols);
        assertEquals(forward.rows, reverse.rows);
        assertArrayEquals(forward.ids, reverse.ids); // dragging either corner first gives the same block
        assertEquals(TilePalette.pack(ORE, a), forward.topLeftId()); // always anchored at the top-left cell
    }
}
