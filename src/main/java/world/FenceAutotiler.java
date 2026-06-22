package world;

/**
 * Auto-orients fence tiles by edge connection (which of the four orthogonal neighbours are also fence) — the
 * sibling of the corner {@link CornerAutotile} (areas) and the {@link BushAutotiler} (clumps). The fence sheet
 * lays out: a horizontal run (1 left-end, 2 middle, 3 right-end), a vertical run (4 top-end, 5 middle, 6
 * bottom-end), the 3×3 enclosure block (7–15: corners/edges/cross), and gates (16–18).
 *
 * <p>Painted cells (1–15) are recomputed from their neighbours; <b>gate</b> cells (16–18, placed from the
 * picker) keep whatever tile was chosen but still count as a connection so neighbours close up against them.
 * Pure logic over a {@link ChunkedTileLayer} — the connection table is unit-tested with no GL.
 */
public final class FenceAutotiler {
    public static final int GATE_MIN = 16, GATE_MAX = 18;

    private final ChunkedTileLayer grid;

    public FenceAutotiler(ChunkedTileLayer grid) {
        this.grid = grid;
    }

    public static boolean isGate(int v) { return v >= GATE_MIN && v <= GATE_MAX; }

    /** Any non-empty fence cell (a run/corner tile or a gate) counts as a connection. */
    public static boolean isFence(int v) { return v != 0; }

    /**
     * The fence tile (1–15) for a cell given which of its four orthogonal neighbours are fence. No neighbours
     * → a lone post (4). Only horizontal neighbours → the horizontal run (1/2/3); only vertical → the vertical
     * run (4/5/6); a mix → the enclosure corner/edge/cross (7–15).
     */
    public static int tileFor(boolean n, boolean e, boolean s, boolean w) {
        if (!n && !e && !s && !w) return 4;          // isolated -> a lone post
        if (!n && !s) return e ? (w ? 2 : 1) : 3;    // horizontal: E+W=2, E=1(left-end), W=3(right-end)
        if (!e && !w) return s ? (n ? 5 : 6) : 4;    // vertical:   N+S=5, S=6(top-end), N=4(bottom-end)
        if (n && e && s && w) return 11;             // cross
        if (s && e && w) return 8;                   // top edge   (down + left + right)
        if (n && e && w) return 14;                  // bottom edge
        if (n && s && e) return 10;                  // left edge
        if (n && s && w) return 12;                  // right edge
        if (s && e) return 7;                        // top-left corner
        if (s && w) return 9;                        // top-right corner
        if (n && e) return 13;                       // bottom-left corner
        return 15;                                   // n && w -> bottom-right corner
    }

    /** Paint an auto-orienting fence at the cell (unless it's a gate), then re-tile it + its four neighbours. */
    public void paint(int col, int row) {
        if (!grid.inBounds(col, row)) return;
        if (!isGate(grid.get(col, row))) grid.set(col, row, 11); // mark as fence; retile picks the exact piece
        retile(col, row);
        retileNeighbours(col, row);
    }

    /** Place a gate tile (16–18) directly, then re-tile its neighbours so the run meets the gate. */
    public void paintGate(int col, int row, int gateTile) {
        if (!grid.inBounds(col, row)) return;
        grid.set(col, row, gateTile);
        retileNeighbours(col, row);
    }

    /** Erase the fence/gate at the cell and re-tile its neighbours. */
    public void erase(int col, int row) {
        if (!grid.inBounds(col, row) || grid.get(col, row) == 0) return;
        grid.set(col, row, 0);
        retileNeighbours(col, row);
    }

    private void retileNeighbours(int col, int row) {
        retile(col - 1, row);
        retile(col + 1, row);
        retile(col, row - 1);
        retile(col, row + 1);
    }

    /** Recompute a run/corner cell (1–15) from its neighbours. Gates and empty cells are left alone. */
    private void retile(int col, int row) {
        int v = grid.get(col, row);
        if (v == 0 || isGate(v)) return;
        boolean n = isFence(grid.get(col, row - 1));
        boolean e = isFence(grid.get(col + 1, row));
        boolean s = isFence(grid.get(col, row + 1));
        boolean w = isFence(grid.get(col - 1, row));
        grid.set(col, row, tileFor(n, e, s, w));
    }
}
