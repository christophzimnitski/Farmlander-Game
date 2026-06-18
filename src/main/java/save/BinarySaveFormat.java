package save;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import world.ChunkedTileLayer;

/**
 * The owned, primary save encoding: a compact hand-rolled binary stream — no library, no Java
 * serialization (so no {@code serialVersionUID} fragility), no positional {@code Object} list.
 *
 * <p>Layout: a 4-byte magic ({@code "FLND"}) and an {@code int} version up front, then the fields in a
 * fixed order. Being <em>versioned</em> is what replaces the old format's index-fragility: to add a
 * field later, append it after the current ones and bump {@link #VERSION}; the reader only consumes the
 * extra field when {@code fileVersion >=} the version that introduced it, so old files still load and
 * adding a field never shifts everything else.
 *
 * <p><b>Version 2</b> writes the tile grids <em>per-chunk</em>: each grid is split into
 * {@value world.ChunkedTileLayer#CHUNK_SIZE}-cell blocks and only the non-empty ones are stored (with
 * their chunk coords), so a mostly-empty layer (collision, tilled soil) costs almost nothing on disk. It
 * also appends a {@code worldSeed}. A version-1 file (dense grids, no seed) still reads — the grid
 * decoder branches on the file version, so old saves migrate to v2 on the next save.
 */
public final class BinarySaveFormat implements SaveFormat {
    /** 'F','L','N','D' — distinguishes our stream from a Java-serialization stream (which starts 0xACED). */
    public static final int MAGIC = 0x464C4E44;
    public static final int VERSION = 2;

    @Override
    public void write(WorldSave d, OutputStream rawOut) throws IOException {
        DataOutputStream out = new DataOutputStream(rawOut);
        out.writeInt(MAGIC);
        out.writeInt(VERSION);

        out.writeFloat(d.playerX);
        out.writeFloat(d.playerY);
        out.writeInt(d.currentAnimation);
        writeIntArray(out, d.walkActions);

        writeIntArray(out, d.invItems);
        writeIntArray(out, d.invAmounts);
        out.writeInt(d.invToggle);

        writeGridChunked(out, d.collision);
        writeGridChunked(out, d.planting);
        writeGridChunked(out, d.grass);

        CropSave[] crops = d.crops == null ? new CropSave[0] : d.crops;
        out.writeInt(crops.length);
        for (CropSave c : crops) {
            out.writeInt(c.col);
            out.writeInt(c.row);
            out.writeInt(c.type);
            out.writeLong(c.planted);
            out.writeBoolean(c.harvested);
        }
        out.writeLong(d.worldSeed); // appended in v2
        out.flush();
    }

    @Override
    public WorldSave read(InputStream rawIn) throws IOException {
        DataInputStream in = new DataInputStream(rawIn);
        int magic = in.readInt();
        if (magic != MAGIC) throw new IOException("not a Farmlander binary save (bad magic)");
        int version = in.readInt();
        if (version < 1) throw new IOException("unsupported save version " + version);

        WorldSave d = new WorldSave();
        d.playerX = in.readFloat();
        d.playerY = in.readFloat();
        d.currentAnimation = in.readInt();
        d.walkActions = readIntArray(in);

        d.invItems = readIntArray(in);
        d.invAmounts = readIntArray(in);
        d.invToggle = in.readInt();

        // v2 stores grids per-chunk; v1 stored them dense. Branch on the file's version.
        if (version >= 2) {
            d.collision = readGridChunked(in);
            d.planting = readGridChunked(in);
            d.grass = readGridChunked(in);
        } else {
            d.collision = readGridDense(in);
            d.planting = readGridDense(in);
            d.grass = readGridDense(in);
        }

        int cropCount = in.readInt();
        CropSave[] crops = new CropSave[cropCount];
        for (int i = 0; i < cropCount; i++) {
            crops[i] = new CropSave(in.readInt(), in.readInt(), in.readInt(),
                                    in.readLong(), in.readBoolean());
        }
        d.crops = crops;
        if (version >= 2) d.worldSeed = in.readLong(); // appended in v2; v1 worlds default to 0
        return d;
    }

    private static void writeIntArray(DataOutputStream out, int[] a) throws IOException {
        if (a == null) { out.writeInt(-1); return; }
        out.writeInt(a.length);
        for (int v : a) out.writeInt(v);
    }

    private static int[] readIntArray(DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len < 0) return null;
        int[] a = new int[len];
        for (int i = 0; i < len; i++) a[i] = in.readInt();
        return a;
    }

    // --- v1 (dense) grid reader, kept for migration only ---

    /** Read a v1 dense grid: outer length, inner length, then row-major ints. {@code -1} outer == null. */
    private static int[][] readGridDense(DataInputStream in) throws IOException {
        int outer = in.readInt();
        if (outer < 0) return null;
        int inner = in.readInt();
        int[][] g = new int[outer][inner];
        for (int i = 0; i < outer; i++)
            for (int j = 0; j < inner; j++) g[i][j] = in.readInt();
        return g;
    }

    // --- v2 (per-chunk) grid encoding ---

    /**
     * Write a grid as: {@code cols}, {@code rows}, {@code chunkSize}, then the count of non-empty chunks
     * and, for each, its {@code (chunkCol, chunkRow)} and {@code chunkSize²} ints (row-major within the
     * chunk, zero-padded past the grid edge). All-zero chunks are skipped, so a sparse layer is tiny.
     * A {@code null} grid writes {@code cols == -1}.
     */
    private static void writeGridChunked(DataOutputStream out, int[][] g) throws IOException {
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

    private static int[][] readGridChunked(DataInputStream in) throws IOException {
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
