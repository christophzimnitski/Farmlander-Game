package save;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import world.ChunkedTileLayer;

/**
 * Shared per-chunk encoding for a rectangular {@code int[][]} tile grid: the grid is split into
 * {@value world.ChunkedTileLayer#CHUNK_SIZE}-cell blocks and only the non-empty ones are written (with their
 * chunk coords), so a mostly-empty layer (collision, tilled soil, editor decoration) costs almost nothing on
 * disk. Lifted out of {@code BinarySaveFormat} so every grid-bearing format (the world save, and later the
 * standalone map file) encodes grids the same way. Pure IO over streams — no libGDX.
 */
public final class GridCodec {
    private GridCodec() {}

    /**
     * Write a grid as: {@code cols}, {@code rows}, {@code chunkSize}, then the count of non-empty chunks and,
     * for each, its {@code (chunkCol, chunkRow)} and {@code chunkSize²} ints (row-major within the chunk,
     * zero-padded past the grid edge). A {@code null} grid writes {@code cols == -1}.
     */
    public static void writeChunked(DataOutputStream out, int[][] g) throws IOException {
        if (g == null) { out.writeInt(-1); return; }
        int cols = g.length;
        int rows = cols == 0 ? 0 : g[0].length;
        int cs = ChunkedTileLayer.CHUNK_SIZE;
        out.writeInt(cols);
        out.writeInt(rows);
        out.writeInt(cs);

        int chunkCols = (cols + cs - 1) / cs;
        int chunkRows = (rows + cs - 1) / cs;
        List<int[]> nonEmpty = new ArrayList<>();
        for (int cc = 0; cc < chunkCols; cc++)
            for (int cr = 0; cr < chunkRows; cr++)
                if (!chunkIsEmpty(g, cc, cr, cs, cols, rows)) nonEmpty.add(new int[]{cc, cr});

        out.writeInt(nonEmpty.size());
        for (int[] chunk : nonEmpty) {
            int cc = chunk[0], cr = chunk[1];
            out.writeInt(cc);
            out.writeInt(cr);
            for (int local = 0; local < cs * cs; local++) {
                int col = cc * cs + (local % cs);
                int row = cr * cs + (local / cs);
                out.writeInt((col < cols && row < rows) ? g[col][row] : 0);
            }
        }
    }

    /** Read back a grid written by {@link #writeChunked}. {@code cols == -1} yields {@code null}. */
    public static int[][] readChunked(DataInputStream in) throws IOException {
        int cols = in.readInt();
        if (cols < 0) return null;
        int rows = in.readInt();
        int cs = in.readInt();
        int[][] g = new int[cols][rows];
        int count = in.readInt();
        for (int n = 0; n < count; n++) {
            int cc = in.readInt();
            int cr = in.readInt();
            for (int local = 0; local < cs * cs; local++) {
                int v = in.readInt();
                int col = cc * cs + (local % cs);
                int row = cr * cs + (local / cs);
                if (col < cols && row < rows) g[col][row] = v;
            }
        }
        return g;
    }

    private static boolean chunkIsEmpty(int[][] g, int cc, int cr, int cs, int cols, int rows) {
        for (int col = cc * cs; col < Math.min((cc + 1) * cs, cols); col++)
            for (int row = cr * cs; row < Math.min((cr + 1) * cs, rows); row++)
                if (g[col][row] != 0) return false;
        return true;
    }
}
