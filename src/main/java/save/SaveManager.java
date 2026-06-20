package save;

import core.Log;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Owns world-save file IO and which {@link SaveFormat} to use. Writes are always the owned binary
 * format ({@link BinarySaveFormat}); reads sniff the leading 4 bytes and route an old positional
 * Java-serialization file through {@link LegacySaveFormat} instead — so an existing save migrates to
 * binary on the next save, transparently.
 */
public final class SaveManager {
    /** Saves live in a per-user data dir, never inside the project/repo. */
    public static final String SAVE_FILE =
            System.getProperty("user.home") + File.separator + ".farmlander" + File.separator + "objects.dat";

    private static final SaveFormat BINARY = new BinarySaveFormat();
    private static final SaveFormat LEGACY = new LegacySaveFormat();

    private SaveManager() {
    }

    /** Load the world at {@code path}, or {@code null} if there's no save (or it can't be read). */
    public static WorldSave load(String path) {
        File f = new File(path);
        if (!f.isFile() || f.length() < 4) return null;
        try {
            byte[] bytes = Files.readAllBytes(f.toPath());
            boolean binary = magicOf(bytes) == BinarySaveFormat.MAGIC;
            SaveFormat fmt = binary ? BINARY : LEGACY;
            return fmt.read(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            // Corrupt/unreadable save: treat as "no save" so the caller starts fresh rather than crashing.
            Log.warn("SaveManager", "could not read save at " + path + "; starting fresh", e);
            return null;
        }
    }

    /**
     * Write {@code data} to {@code path} in the binary format, <b>atomically</b>: the bytes go to a sibling
     * {@code .tmp} file (flushed to disk), which is then renamed over the real save in one step. So an
     * interruption mid-write — a crash, power loss, or a force-quit during the exit-save or an autosave —
     * can only ever damage the temp file; the previous good save is left intact rather than truncated.
     * Creates the parent dir as needed.
     */
    public static void save(WorldSave data, String path) {
        File target = new File(path);
        File parent = target.getParentFile();
        if (parent != null) parent.mkdirs();
        File tmp = new File(path + ".tmp");
        try {
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                BINARY.write(data, out);
                out.getFD().sync(); // force bytes to disk before the swap, so the rename can't expose a partial file
            }
            try {
                Files.move(tmp.toPath(), target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException notAtomic) {
                // Rare filesystem without atomic rename: a plain replace is still far safer than writing in place.
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Log.error("SaveManager", "could not write save to " + path, e);
            tmp.delete(); // drop the half-written temp; the previous save (if any) is untouched
        }
    }

    private static int magicOf(byte[] b) {
        return ((b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16) | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
    }
}
