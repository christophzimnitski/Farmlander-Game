package world;

/**
 * Auto-orients the bush/clump sheets (tall grass, wheat, young wheat) as you paint them, the sibling of the
 * plow {@link DualGridSoil}. Those sheets aren't a corner dual-grid — they're a <b>9-slice</b> clump (a 3×3
 * block of edge/corner pieces at tile indices 7–15, plus a single-bush tile at index 1) — so connectivity is
 * computed from the four orthogonal neighbours rather than the four corners, and the result is one of nine
 * edge tiles (or the single-bush tile when isolated).
 *
 * <p>Bushes live in the existing collision layer (so they're solid). Each sheet occupies a {@value #SPAN}-wide
 * band there — tall grass {@code 1..18}, wheat {@code 19..36}, young wheat {@code 37..54} — so a cell's
 * <em>base</em> offset (0 / 18 / 36) identifies its bush type and different types never merge. Pure logic over
 * a {@link ChunkedTileLayer}, no libGDX, so the connectivity is unit-tested directly.
 */
public final class BushAutotiler {
    /** Collision-layer indices per bush sheet (each sheet is sliced 1..18). */
    public static final int SPAN = 18;

    private final ChunkedTileLayer collision;

    public BushAutotiler(ChunkedTileLayer collision) {
        this.collision = collision;
    }

    /** The bush type's base offset (0 / 18 / 36) for a collision value, or -1 if it isn't a bush. */
    public static int baseOf(int value) {
        if (value >= 1 && value <= SPAN) return 0;
        if (value > SPAN && value <= 2 * SPAN) return SPAN;
        if (value > 2 * SPAN && value <= 3 * SPAN) return 2 * SPAN;
        return -1;
    }

    /**
     * The 9-slice tile (1..18) for a cell given which of its four orthogonal neighbours are the same bush.
     * No neighbours at all → the single-bush tile (1); otherwise one of the 3×3 block (7..15): the row is
     * chosen by the up/down neighbours (top edge when nothing above, bottom edge when nothing below, middle
     * when both), the column likewise by left/right.
     */
    public static int tileFor(boolean up, boolean down, boolean left, boolean right) {
        if (!up && !down && !left && !right) return 1;
        int r = (!up && down) ? 0 : (up && !down) ? 2 : 1;
        int c = (!left && right) ? 0 : (left && !right) ? 2 : 1;
        return 7 + r * 3 + c;
    }

    /** Paint a bush of the given type ({@code base} = 0/18/36) at the cell, then re-tile it and its four
     *  neighbours so edges meet cleanly. */
    public void paint(int col, int row, int base) {
        if (!collision.inBounds(col, row)) return;
        collision.set(col, row, base + 11); // mark as this type (centre tile); retile fixes the exact piece
        retile(col, row);
        retile(col - 1, row);
        retile(col + 1, row);
        retile(col, row - 1);
        retile(col, row + 1);
    }

    /** Erase a bush at the cell and re-tile its four neighbours so their edges close up. Leaves non-bush
     *  collision untouched. */
    public void erase(int col, int row) {
        if (!collision.inBounds(col, row)) return;
        if (baseOf(collision.get(col, row)) < 0) return;
        collision.set(col, row, 0);
        retile(col - 1, row);
        retile(col + 1, row);
        retile(col, row - 1);
        retile(col, row + 1);
    }

    /** Recompute one cell's 9-slice piece from its same-type neighbours (no-op on a non-bush cell). */
    private void retile(int col, int row) {
        int base = baseOf(collision.get(col, row));
        if (base < 0) return;
        boolean up = sameBush(col, row - 1, base);
        boolean down = sameBush(col, row + 1, base);
        boolean left = sameBush(col - 1, row, base);
        boolean right = sameBush(col + 1, row, base);
        collision.set(col, row, base + tileFor(up, down, left, right));
    }

    private boolean sameBush(int col, int row, int base) {
        return baseOf(collision.get(col, row)) == base;
    }
}
