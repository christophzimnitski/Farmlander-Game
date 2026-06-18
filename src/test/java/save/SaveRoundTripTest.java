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
    void badMagicIsRejected() {
        BinarySaveFormat format = new BinarySaveFormat();
        byte[] garbage = {0, 0, 0, 0, 0, 0, 0, 1};
        assertThrows(IOException.class,
                () -> format.read(new ByteArrayInputStream(garbage)),
                "a stream without the FLND magic must not be read as a save");
    }
}
