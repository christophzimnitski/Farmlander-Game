package save;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers the file-level save IO (the in-memory encoding is in {@link SaveRoundTripTest}). Pins the atomic
 * write: a save round-trips through a real file, leaves no {@code .tmp} behind, and replacing an existing
 * save swaps cleanly — so a partial write can never truncate a good save. Also pins the graceful recovery
 * that turns a corrupt file into "no save" instead of a crash.
 */
class SaveManagerTest {

    @Test
    void saveThenLoadRoundTripsThroughAFile(@TempDir Path dir) {
        String path = dir.resolve("objects.dat").toString();
        WorldSave in = sample();

        SaveManager.save(in, path);
        WorldSave out = SaveManager.load(path);

        assertEquals(in.playerX, out.playerX);
        assertArrayEquals(in.planting, out.planting);
        assertEquals(1, out.crops.length);
        assertEquals(3, out.crops[0].col);
        assertFalse(new File(path + ".tmp").exists(), "the temp file must be gone after an atomic save");
    }

    @Test
    void rewritingReplacesTheSaveCleanlyAndLeavesNoTemp(@TempDir Path dir) {
        String path = dir.resolve("objects.dat").toString();
        SaveManager.save(sample(), path);
        assertTrue(new File(path).length() > 0);

        WorldSave second = sample();
        second.playerX = 4242f;
        second.planting = new int[][]{{1, 1}, {1, 1}};
        SaveManager.save(second, path);

        WorldSave out = SaveManager.load(path);
        assertEquals(4242f, out.playerX, "the second save fully replaced the first");
        assertArrayEquals(second.planting, out.planting);
        assertFalse(new File(path + ".tmp").exists());
    }

    @Test
    void corruptSaveLoadsAsNullRatherThanThrowing(@TempDir Path dir) throws Exception {
        Path p = dir.resolve("objects.dat");
        // valid FLND magic + version, then a truncated body — exactly the shape an interrupted write left.
        Files.write(p, new byte[]{'F', 'L', 'N', 'D', 0, 0, 0, 2, 1, 2, 3});
        assertNull(SaveManager.load(p.toString()), "a truncated save reads as no-save, never crashes the loader");
    }

    private static WorldSave sample() {
        WorldSave w = new WorldSave();
        w.playerX = 2000.5f;
        w.playerY = 800.25f;
        w.currentAnimation = 34;
        w.walkActions = new int[]{32, 33, 34, 35};
        w.invItems = new int[]{100, 0};
        w.invAmounts = new int[]{5, 0};
        w.invToggle = 1;
        w.worldSeed = 123456789L;
        w.collision = new int[][]{{0, 1}, {2, 0}};
        w.planting = new int[][]{{0, 1}, {1, 0}};
        w.grass = new int[][]{{1, 2}, {3, 0}};
        w.crops = new CropSave[]{new CropSave(3, 4, 0, 1_700_000_000_000L, true)};
        return w;
    }
}
