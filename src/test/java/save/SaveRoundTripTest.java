package save;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * Locks the primary save encoding: a {@link WorldSave} written by {@link BinarySaveFormat} and read back
 * must be byte-faithful. Runs fully headless — the format is pure {@code java.io}, so we drive it through
 * in-memory streams (no file, no GL). This is the safety net for any future save change.
 */
class SaveRoundTripTest {

    private static WorldSave roundTrip(WorldSave in) throws IOException {
        BinarySaveFormat format = new BinarySaveFormat();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        format.write(in, bytes);
        return format.read(new ByteArrayInputStream(bytes.toByteArray()));
    }

    @Test
    void fullWorldSurvivesRoundTrip() throws IOException {
        WorldSave in = new WorldSave();
        in.playerX = 2000.5f;
        in.playerY = 800.25f;
        in.currentAnimation = 34;
        in.walkActions = new int[]{32, 33, 34, 35};
        in.invItems = new int[]{100, 101, 0, 0};
        in.invAmounts = new int[]{5, 12, 0, 0};
        in.invToggle = 2;
        in.worldSeed = 0x0BADC0DE_CAFEF00DL;
        in.collision = new int[][]{{0, 1}, {2, 0}};
        in.planting = new int[][]{{0, 1}, {1, 0}};   // tilled flags
        in.grass = new int[][]{{0, 3}, {2, 1}};
        in.crops = new CropSave[]{
                new CropSave(3, 4, 0, 1_700_000_000_000L, false),
                new CropSave(5, 6, 12, 1_700_000_500_000L, true),
        };

        WorldSave out = roundTrip(in);

        assertEquals(in.playerX, out.playerX);
        assertEquals(in.playerY, out.playerY);
        assertEquals(in.currentAnimation, out.currentAnimation);
        assertArrayEquals(in.walkActions, out.walkActions);
        assertArrayEquals(in.invItems, out.invItems);
        assertArrayEquals(in.invAmounts, out.invAmounts);
        assertEquals(in.invToggle, out.invToggle);
        assertEquals(in.worldSeed, out.worldSeed);
        assertArrayEquals(in.collision, out.collision);
        assertArrayEquals(in.planting, out.planting);
        assertArrayEquals(in.grass, out.grass);

        assertEquals(2, out.crops.length);
        assertEquals(3, out.crops[0].col);
        assertEquals(4, out.crops[0].row);
        assertEquals(0, out.crops[0].type);
        assertEquals(1_700_000_000_000L, out.crops[0].planted);
        assertEquals(false, out.crops[0].harvested);
        assertEquals(12, out.crops[1].type);
        assertEquals(true, out.crops[1].harvested);
    }

    @Test
    void largeSparseGridSurvivesPerChunkEncoding() throws IOException {
        // A world-sized grid that is empty except for a few cells in distant chunks: the per-chunk encoding
        // must skip the all-zero chunks yet reproduce the populated cells exactly across chunk boundaries.
        int[][] grid = new int[400][220];
        grid[0][0] = 1;
        grid[33][35] = 2;     // a different chunk than (0,0)
        grid[399][219] = 3;   // the far corner chunk
        WorldSave in = new WorldSave();
        in.walkActions = new int[]{32, 33, 34, 35};
        in.collision = grid;
        in.planting = new int[400][220]; // wholly empty -> zero chunks written
        in.grass = grid;
        in.crops = null;

        WorldSave out = roundTrip(in);

        assertArrayEquals(in.collision, out.collision);
        assertArrayEquals(in.planting, out.planting);
        assertArrayEquals(in.grass, out.grass);
        assertEquals(1, out.collision[0][0]);
        assertEquals(2, out.collision[33][35]);
        assertEquals(3, out.collision[399][219]);
    }

    @Test
    void nullCropsReadBackAsEmpty() throws IOException {
        WorldSave in = new WorldSave();
        in.walkActions = new int[]{32, 33, 34, 35};
        in.collision = new int[][]{{0}};
        in.planting = new int[][]{{0}};
        in.grass = new int[][]{{0}};
        in.crops = null;

        WorldSave out = roundTrip(in);
        assertEquals(0, out.crops.length, "null crops normalize to an empty array");
    }

    @Test
    void nullIntArraysSurviveAsNull() throws IOException {
        WorldSave in = new WorldSave();
        in.walkActions = null;
        in.invItems = null;
        in.invAmounts = null;
        in.collision = null;
        in.planting = null;
        in.grass = null;

        WorldSave out = roundTrip(in);
        assertNull(out.walkActions);
        assertNull(out.invItems);
        assertNull(out.collision);
    }

    @Test
    void decorationAndObjectsSurviveRoundTrip() throws IOException {
        WorldSave in = new WorldSave();
        in.walkActions = new int[]{32, 33, 34, 35};
        in.collision = new int[][]{{0}};
        in.planting = new int[][]{{0}};
        in.grass = new int[][]{{0}};
        in.decoration = new int[][]{{0, 65537}, {131073, 0}}; // packed palette ids
        in.objects = new ObjectSave[]{
                new ObjectSave("tilesets/farming_fishing.png", 160, 96, 96, 160, 2000f, 800f, 288f, 480f),
                new ObjectSave("tilesets/farming_fishing.png", 452, 448, 196, 96, 3000.5f, 1200.25f, 588f, 288f),
        };

        WorldSave out = roundTrip(in);

        assertArrayEquals(in.decoration, out.decoration);
        assertEquals(2, out.objects.length);
        ObjectSave o = out.objects[1];
        assertEquals("tilesets/farming_fishing.png", o.sheetPath);
        assertEquals(452, o.srcX);
        assertEquals(448, o.srcY);
        assertEquals(196, o.srcW);
        assertEquals(96, o.srcH);
        assertEquals(3000.5f, o.worldX);
        assertEquals(1200.25f, o.worldY);
        assertEquals(588f, o.drawW);
        assertEquals(288f, o.drawH);
    }

    @Test
    void editorLayersSurviveRoundTrip() throws IOException {
        WorldSave in = new WorldSave();
        in.walkActions = new int[]{32, 33, 34, 35};
        in.collision = new int[][]{{0}};
        in.planting = new int[][]{{0}};
        in.grass = new int[][]{{0}};
        in.layers = new LayerSave[]{
                new LayerSave("Sand", 1, -1, false, false, new int[][]{{0, 1}, {1, 0}}),
                new LayerSave("Fence", 2, 5, true, true, new int[][]{{3, 0}, {0, 7}}),
        };

        WorldSave out = roundTrip(in);

        assertEquals(2, out.layers.length);
        LayerSave fence = out.layers[1];
        assertEquals("Fence", fence.name);
        assertEquals(2, fence.kind);
        assertEquals(5, fence.sheetIndex);
        assertEquals(true, fence.solid);
        assertEquals(true, fence.upright);
        assertArrayEquals(new int[][]{{3, 0}, {0, 7}}, fence.grid);
    }

    @Test
    void nullDecorationAndObjectsReadBack() throws IOException {
        WorldSave in = new WorldSave();
        in.walkActions = new int[]{32, 33, 34, 35};
        in.collision = new int[][]{{0}};
        in.planting = new int[][]{{0}};
        in.grass = new int[][]{{0}};
        // decoration + objects left null

        WorldSave out = roundTrip(in);
        assertNull(out.decoration, "a null decoration grid stays null");
        assertEquals(0, out.objects.length, "null objects normalize to an empty array");
    }

    @Test
    void badMagicIsRejected() {
        BinarySaveFormat format = new BinarySaveFormat();
        byte[] garbage = {0, 0, 0, 0, 0, 0, 0, 1};
        assertThrows(IOException.class,
                () -> format.read(new ByteArrayInputStream(garbage)),
                "a stream without the FLND magic must not be read as a save");
    }
}
