package save;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * The owned, primary save encoding: a compact hand-rolled binary stream — no library, no Java
 * serialization (so no {@code serialVersionUID} fragility), no positional {@code Object} list.
 *
 * <p>Layout: a 4-byte magic ({@code "FLND"}) and an {@code int} version up front, then the fields in a
 * fixed order. Being <em>versioned</em> is what replaces the old format's index-fragility: to add a
 * field later, append it after the current ones and bump {@link #VERSION}; the reader only consumes the
 * extra field when {@code fileVersion >=} the version that introduced it, so old files still load and
 * adding a field never shifts everything else. Big tile grids stay tiny here (the 400x220 grass array
 * is ~88k ints written as raw {@code int}s, not text).
 */
public final class BinarySaveFormat implements SaveFormat {
    /** 'F','L','N','D' — distinguishes our stream from a Java-serialization stream (which starts 0xACED). */
    public static final int MAGIC = 0x464C4E44;
    public static final int VERSION = 1;

    @Override
    public void write(WorldSave d, OutputStream rawOut) throws IOException {
        DataOutputStream out = new DataOutputStream(rawOut);
        out.writeInt(MAGIC);
        out.writeInt(VERSION);

        out.writeFloat(d.playerX);
        out.writeFloat(d.playerY);
        out.writeInt(d.currentAction);
        writeIntArray(out, d.walkActions);

        writeIntArray(out, d.invItems);
        writeIntArray(out, d.invAmounts);
        out.writeInt(d.invToggle);

        writeGrid(out, d.collision);
        writeGrid(out, d.planting);
        writeGrid(out, d.grass);

        CropSave[] crops = d.crops == null ? new CropSave[0] : d.crops;
        out.writeInt(crops.length);
        for (CropSave c : crops) {
            out.writeInt(c.col);
            out.writeInt(c.row);
            out.writeInt(c.type);
            out.writeLong(c.planted);
            out.writeBoolean(c.harvested);
        }
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
        d.currentAction = in.readInt();
        d.walkActions = readIntArray(in);

        d.invItems = readIntArray(in);
        d.invAmounts = readIntArray(in);
        d.invToggle = in.readInt();

        d.collision = readGrid(in);
        d.planting = readGrid(in);
        d.grass = readGrid(in);

        int cropCount = in.readInt();
        CropSave[] crops = new CropSave[cropCount];
        for (int i = 0; i < cropCount; i++) {
            crops[i] = new CropSave(in.readInt(), in.readInt(), in.readInt(),
                                    in.readLong(), in.readBoolean());
        }
        d.crops = crops;
        // (When VERSION > 1, read appended fields here, guarded by `if (version >= N)`.)
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

    /** Rectangular grid as outer length, inner length, then row-major ints. {@code -1} outer == null. */
    private static void writeGrid(DataOutputStream out, int[][] g) throws IOException {
        if (g == null) { out.writeInt(-1); return; }
        int outer = g.length;
        int inner = outer == 0 ? 0 : g[0].length;
        out.writeInt(outer);
        out.writeInt(inner);
        for (int[] row : g)
            for (int v : row) out.writeInt(v);
    }

    private static int[][] readGrid(DataInputStream in) throws IOException {
        int outer = in.readInt();
        if (outer < 0) return null;
        int inner = in.readInt();
        int[][] g = new int[outer][inner];
        for (int i = 0; i < outer; i++)
            for (int j = 0; j < inner; j++) g[i][j] = in.readInt();
        return g;
    }
}
