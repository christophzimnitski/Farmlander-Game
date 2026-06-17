package save;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;

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
            System.err.println("SaveManager: could not read " + path + " (" + e.getMessage() + ")");
            return null;
        }
    }

    /** Write {@code data} to {@code path} in the binary format, creating the parent dir as needed. */
    public static void save(WorldSave data, String path) {
        File parent = new File(path).getParentFile();
        if (parent != null) parent.mkdirs();
        try (OutputStream out = new FileOutputStream(path)) {
            BINARY.write(data, out);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static int magicOf(byte[] b) {
        return ((b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16) | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
    }
}
