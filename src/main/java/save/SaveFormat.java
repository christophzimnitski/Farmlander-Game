package save;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * The swap seam for how a {@link WorldSave} becomes bytes and back. Today there's one writer/reader
 * ({@link BinarySaveFormat}) plus a read-only migration reader for the old format
 * ({@link LegacySaveFormat}); swapping in a different encoding (JSON, compressed, networked) is a
 * one-class change that leaves {@code PlayState} and {@link SaveManager} untouched.
 *
 * <p>Implementations work on raw streams (not files) so the same format can write to disk, a chunk
 * blob, an in-memory buffer for tests, etc.
 */
public interface SaveFormat {
    /** Serialize {@code data} to {@code out}. */
    void write(WorldSave data, OutputStream out) throws IOException;

    /** Deserialize a world from {@code in}, or throw if the stream isn't this format. */
    WorldSave read(InputStream in) throws IOException;
}
