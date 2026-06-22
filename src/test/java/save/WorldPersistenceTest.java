package save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import crops.CropField;
import entities.PlayerCharacter;
import org.junit.jupiter.api.Test;
import world.ChunkedTileLayer;
import world.DualGridSoil;
import world.ObjectLayer;
import world.PlacedObject;
import world.layer.LayerStack;

/**
 * Pins the property that makes the background autosave safe: {@link WorldPersistence#snapshot} returns a
 * fully <b>detached</b> copy of the world. The inventory arrays are mutated in place during play, so if the
 * snapshot kept live references the writer thread could serialize a half-mutated inventory. Mutating the live
 * arrays after snapshotting must not change the snapshot.
 */
class WorldPersistenceTest {

    private static WorldPersistence persistence(int[] items, int[] amounts) {
        int cols = 8, rows = 8;
        float tile = 96f;
        ChunkedTileLayer collision = new ChunkedTileLayer(cols, rows, tile);
        ChunkedTileLayer grass = new ChunkedTileLayer(cols, rows, tile);
        ChunkedTileLayer decoration = new ChunkedTileLayer(cols, rows, tile);
        DualGridSoil soil = new DualGridSoil(new ChunkedTileLayer(cols, rows, tile));
        CropField field = new CropField(cols, rows);
        ObjectLayer objects = new ObjectLayer();
        objects.add(new PlacedObject("tilesets/farming_fishing.png", 160, 96, 96, 160, 200f, 100f, 288f, 480f));
        PlayerCharacter player =
                new PlayerCharacter(100f, 100f, 34, new int[]{32, 33, 34, 35}, new TextureRegion[36][9]);
        // cropRegistry is only used by restoreCrops, not by snapshot, so null is fine here.
        return new WorldPersistence(collision, grass, decoration, soil, field, objects, new LayerStack(), player,
                items, amounts, null, 42L);
    }

    @Test
    void snapshotIsDetachedFromTheLiveInventory() {
        int[] items = {100, 5, 0};
        int[] amounts = {9, 1, 0};
        WorldPersistence wp = persistence(items, amounts);

        WorldSave snap = wp.snapshot(3);

        // Mutate the live inventory AFTER snapshotting — exactly what a background write must be immune to.
        items[0] = 999;
        amounts[0] = -1;

        assertEquals(100, snap.invItems[0], "snapshot inventory is a detached copy, not a live reference");
        assertEquals(9, snap.invAmounts[0]);
        assertEquals(3, snap.invToggle, "the selected slot is captured at snapshot time");
        assertEquals(42L, snap.worldSeed);
        assertNotNull(snap.collision, "grids are materialised into the snapshot");
        assertNotNull(snap.decoration, "the decoration grid is materialised into the snapshot");
        assertNotNull(snap.walkActions);
        assertEquals(1, snap.objects.length, "placed props are captured into the snapshot");
        assertEquals(160, snap.objects[0].srcX);
    }

    @Test
    void snapshotWalkActionsAreCopiedNotShared() {
        WorldPersistence wp = persistence(new int[]{0}, new int[]{0});
        WorldSave snap = wp.snapshot(0);
        assertEquals(32, snap.walkActions[0], "walk set starts {32,33,34,35}");
        snap.walkActions[0] = -123; // mutating the snapshot must not reach back into the player's array
        assertEquals(32, wp.snapshot(0).walkActions[0], "each snapshot gets its own walk-action array");
    }
}
