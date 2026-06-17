package save;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import entities.Inventory;

/**
 * Read-only reader for the <em>old</em> save format: a positional list of Java-serialized objects
 * (collision/planting/grass at [0..2], a dead crop pair at [3..4], the {@code Inventory} at [5],
 * player x/y/anim/action at [6..9], and the crop arrays appended at [10..14]). Exists solely so an
 * existing {@code ~/.farmlander/objects.dat} migrates cleanly to {@link BinarySaveFormat} on the next
 * save — {@link SaveManager} sniffs the stream and routes here when it isn't our binary magic.
 *
 * <p>{@link #write} is unsupported: we never write this format again.
 */
public final class LegacySaveFormat implements SaveFormat {
    @Override
    public void write(WorldSave data, OutputStream out) {
        throw new UnsupportedOperationException("legacy format is read-only (migration only)");
    }

    @Override
    public WorldSave read(InputStream in) throws IOException {
        List<Object> save = new ArrayList<>();
        try (ObjectInputStream ois = new ObjectInputStream(in)) {
            while (true) {
                try {
                    save.add(ois.readObject());
                } catch (IOException eof) {
                    break; // end of stream
                }
            }
        } catch (ClassNotFoundException e) {
            throw new IOException("legacy save references an unknown class", e);
        }
        if (save.size() < 10) throw new IOException("legacy save too short (" + save.size() + " objects)");

        WorldSave d = new WorldSave();
        d.collision = (int[][]) save.get(0);
        d.planting  = (int[][]) save.get(1);
        d.grass     = (int[][]) save.get(2);
        // [3],[4] are the dead Swing crop format — ignored.
        Inventory inv = (Inventory) save.get(5);
        d.invItems   = inv.getInventory();
        d.invAmounts = inv.getInventoryAmounts();
        d.invToggle  = inv.getSelectedSlot();
        d.playerX    = (Float) save.get(6);
        d.playerY    = (Float) save.get(7);
        d.walkActions = (int[]) save.get(8);
        d.currentAnimation = (Integer) save.get(9);

        if (save.size() >= 15) {
            int[] cols = (int[]) save.get(10);
            int[] rows = (int[]) save.get(11);
            int[] types = (int[]) save.get(12);
            long[] planted = (long[]) save.get(13);
            boolean[] harvested = (boolean[]) save.get(14);
            int n = Math.min(cols.length, Math.min(rows.length,
                    Math.min(types.length, Math.min(planted.length, harvested.length))));
            CropSave[] crops = new CropSave[n];
            for (int i = 0; i < n; i++)
                crops[i] = new CropSave(cols[i], rows[i], types[i], planted[i], harvested[i]);
            d.crops = crops;
        } else {
            d.crops = new CropSave[0];
        }
        return d;
    }
}
