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
 * adding a field never shifts everything else.
 *
 * <p><b>Version 2</b> writes the tile grids <em>per-chunk</em> (see {@link GridCodec}): each grid is split
 * into {@value world.ChunkedTileLayer#CHUNK_SIZE}-cell blocks and only the non-empty ones are stored, so a
 * mostly-empty layer (collision, tilled soil) costs almost nothing on disk. It also appends a
 * {@code worldSeed}. <b>Version 3</b> appends the editor's decoration tile layer and the placed-object list.
 * Older files still read — each appended field is consumed only when {@code fileVersion} is high enough — so
 * a v1/v2 save migrates forward on the next save.
 */
public final class BinarySaveFormat implements SaveFormat {
    /** 'F','L','N','D' — distinguishes our stream from a Java-serialization stream (which starts 0xACED). */
    public static final int MAGIC = 0x464C4E44;
    public static final int VERSION = 4;

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

        GridCodec.writeChunked(out, d.collision);
        GridCodec.writeChunked(out, d.planting);
        GridCodec.writeChunked(out, d.grass);

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

        // v3: editor-authored content — the decoration tile layer and the placed-object list.
        GridCodec.writeChunked(out, d.decoration);
        ObjectSave[] objects = d.objects == null ? new ObjectSave[0] : d.objects;
        out.writeInt(objects.length);
        for (ObjectSave o : objects) {
            out.writeUTF(o.sheetPath);
            out.writeInt(o.srcX);
            out.writeInt(o.srcY);
            out.writeInt(o.srcW);
            out.writeInt(o.srcH);
            out.writeFloat(o.worldX);
            out.writeFloat(o.worldY);
            out.writeFloat(o.drawW);
            out.writeFloat(o.drawH);
        }

        // v4: the editor layer stack (sand/sand-water/fence/props/user layers).
        LayerSave[] layers = d.layers == null ? new LayerSave[0] : d.layers;
        out.writeInt(layers.length);
        for (LayerSave l : layers) {
            out.writeUTF(l.name);
            out.writeInt(l.kind);
            out.writeInt(l.sheetIndex);
            out.writeBoolean(l.solid);
            out.writeBoolean(l.upright);
            GridCodec.writeChunked(out, l.grid);
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
        d.currentAnimation = in.readInt();
        d.walkActions = readIntArray(in);

        d.invItems = readIntArray(in);
        d.invAmounts = readIntArray(in);
        d.invToggle = in.readInt();

        // v2 stores grids per-chunk; v1 stored them dense. Branch on the file's version.
        if (version >= 2) {
            d.collision = GridCodec.readChunked(in);
            d.planting = GridCodec.readChunked(in);
            d.grass = GridCodec.readChunked(in);
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

        if (version >= 3) { // editor decoration + objects
            d.decoration = GridCodec.readChunked(in);
            int objectCount = in.readInt();
            ObjectSave[] objects = new ObjectSave[objectCount];
            for (int i = 0; i < objectCount; i++) {
                objects[i] = new ObjectSave(in.readUTF(), in.readInt(), in.readInt(), in.readInt(), in.readInt(),
                        in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat());
            }
            d.objects = objects;
        }
        if (version >= 4) { // editor layer stack
            int layerCount = in.readInt();
            LayerSave[] layers = new LayerSave[layerCount];
            for (int i = 0; i < layerCount; i++) {
                LayerSave l = new LayerSave();
                l.name = in.readUTF();
                l.kind = in.readInt();
                l.sheetIndex = in.readInt();
                l.solid = in.readBoolean();
                l.upright = in.readBoolean();
                l.grid = GridCodec.readChunked(in);
                layers[i] = l;
            }
            d.layers = layers;
        }
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

}
