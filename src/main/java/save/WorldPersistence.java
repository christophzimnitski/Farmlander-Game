package save;

import core.Log;
import crops.CropField;
import crops.CropRegistry;
import crops.CropType;
import crops.PlantedCrop;
import entities.PlayerCharacter;
import world.ChunkedTileLayer;
import world.DualGridSoil;
import world.ObjectLayer;
import world.PlacedObject;
import world.layer.LayerStack;
import world.layer.MapLayer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The named owner of "load/save the world" — the bridge between the live game objects and the binary
 * {@link WorldSave} snapshot, behind the {@link SaveManager}/{@link SaveFormat} seam.
 *
 * <p>The write is split in two so the blocking {@code fsync} never hitches a frame: {@link #snapshot} builds
 * a fully <b>detached</b> copy of the world on the caller's game thread (the tile grids come back fresh from
 * {@code toDense}; the in-place-mutated inventory + walk arrays are cloned), and the actual write happens on
 * a background daemon thread. {@link #saveAsync} is the periodic autosave path; {@link #saveOnExit} stays
 * synchronous so a clean quit always finishes writing before the JVM exits. {@link #restoreCrops} replays
 * saved crops back into the field on load.
 */
public final class WorldPersistence {
    private final ChunkedTileLayer collision, grass, decoration;
    private final DualGridSoil soil;
    private final CropField cropField;
    private final ObjectLayer objects;
    private final LayerStack layerStack;
    private final PlayerCharacter player;
    private final int[] inventoryItems, inventoryAmounts; // shared live refs — mutated in place during play
    private final CropRegistry cropRegistry;
    private final long worldSeed; // the seed that generated this world's grass — persisted for a stable identity

    // One daemon writer thread: keeps the blocking write+fsync off the game/render thread. Single-threaded, so
    // autosaves serialize (two writers never race the same temp file); daemon, so it never holds the JVM open.
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "farmlander-save");
        t.setDaemon(true);
        return t;
    });

    public WorldPersistence(ChunkedTileLayer collision, ChunkedTileLayer grass, ChunkedTileLayer decoration,
                            DualGridSoil soil, CropField cropField, ObjectLayer objects, LayerStack layerStack,
                            PlayerCharacter player, int[] inventoryItems, int[] inventoryAmounts,
                            CropRegistry cropRegistry, long worldSeed) {
        this.collision = collision;
        this.grass = grass;
        this.decoration = decoration;
        this.soil = soil;
        this.cropField = cropField;
        this.objects = objects;
        this.layerStack = layerStack;
        this.player = player;
        this.inventoryItems = inventoryItems;
        this.inventoryAmounts = inventoryAmounts;
        this.cropRegistry = cropRegistry;
        this.worldSeed = worldSeed;
    }

    /** Replay saved crops into the field, dropping any crop id that no longer exists in the catalog. */
    public void restoreCrops(CropSave[] crops) {
        if (crops == null) return;
        for (CropSave s : crops) {
            CropType type = cropRegistry.get(s.type);
            if (type == null) continue;
            cropField.plant(s.col, s.row, type, s.planted);
            PlantedCrop c = cropField.at(s.col, s.row);
            if (c != null) c.harvested = s.harvested;
        }
    }

    /**
     * Build a fully detached snapshot of the world as plain data (no engine objects). <b>Must be called on
     * the game thread</b> — it reads the live world — but the result owns no live references, so it is safe to
     * write from another thread: the grids come back fresh from {@code toDense}, and the inventory + walk
     * arrays (which the game mutates in place) are cloned here.
     */
    public WorldSave snapshot(int selectedSlot) {
        WorldSave ws = new WorldSave();
        ws.playerX = player.x();
        ws.playerY = player.y();
        ws.currentAnimation = player.currentAnimation();
        ws.walkActions = player.walkActions().clone();
        ws.invItems = inventoryItems.clone();      // detach: these arrays are mutated in place during play
        ws.invAmounts = inventoryAmounts.clone();
        ws.invToggle = selectedSlot;
        ws.collision = collision.toDense();         // toDense already materialises a fresh array
        ws.planting = soil.grid().toDense();        // tilled flags (0/1); watering is a separate, unsaved layer
        ws.grass = grass.toDense();
        ws.decoration = decoration.toDense();        // editor-placed tiles
        ws.worldSeed = worldSeed;

        // Editor-placed props -> plain ObjectSave records (the TextureRegion is rebuilt from the sheet on load).
        List<PlacedObject> objs = objects.all();
        ObjectSave[] objectSaves = new ObjectSave[objs.size()];
        for (int i = 0; i < objs.size(); i++) {
            PlacedObject o = objs.get(i);
            objectSaves[i] = new ObjectSave(o.sheetPath, o.srcX, o.srcY, o.srcW, o.srcH,
                    o.worldX, o.worldY, o.drawW, o.drawH);
        }
        ws.objects = objectSaves;

        // Editor layer stack -> plain LayerSave records (grids detached via toDense; regions rebuilt on load).
        List<MapLayer> ls = layerStack.all();
        LayerSave[] layerSaves = new LayerSave[ls.size()];
        for (int i = 0; i < ls.size(); i++) {
            MapLayer l = ls.get(i);
            layerSaves[i] = new LayerSave(l.name(), l.kind().ordinal(), l.sheetIndex(),
                    l.isSolid(), l.isUpright(), l.grid().toDense());
        }
        ws.layers = layerSaves;

        // Snapshot the crop grid as plain CropSave records (no PlantedCrop/CropType, which hold textures).
        List<CropSave> crops = new ArrayList<>();
        for (int j = 0; j < cropField.cols(); j++)
            for (int i = 0; i < cropField.rows(); i++) {
                PlantedCrop c = cropField.at(j, i);
                if (c == null) continue;
                crops.add(new CropSave(j, i, c.type.index, c.plantedAt, c.harvested));
            }
        ws.crops = crops.toArray(new CropSave[0]);
        return ws;
    }

    /**
     * Autosave: take a detached {@link #snapshot} now (on the caller's game thread), then write + fsync it on
     * the background writer so the periodic save never stalls the frame.
     */
    public void saveAsync(int selectedSlot) {
        WorldSave ws = snapshot(selectedSlot);
        writer.submit(() -> {
            try {
                SaveManager.save(ws, SaveManager.SAVE_FILE);
                Log.info("WorldPersistence", "autosaved");
            } catch (Throwable t) {
                // Never let a write failure kill the single writer thread (that would silently end all autosaves).
                Log.error("WorldPersistence", "async autosave failed", t);
            }
        });
    }

    /**
     * Save on a clean exit: snapshot, drain any in-flight async write, then write synchronously on this thread
     * so it completes before the JVM exits (the daemon writer could be killed mid-write otherwise). The final
     * synchronous write is authoritative — it holds the latest state and lands last.
     */
    public void saveOnExit(int selectedSlot) {
        WorldSave ws = snapshot(selectedSlot);
        writer.shutdown();
        try {
            writer.awaitTermination(5, TimeUnit.SECONDS); // let any queued autosave finish before the final write
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        SaveManager.save(ws, SaveManager.SAVE_FILE);
    }
}
