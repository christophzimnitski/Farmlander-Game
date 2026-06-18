package save;

import crops.CropField;
import crops.CropRegistry;
import crops.CropType;
import crops.PlantedCrop;
import entities.PlayerCharacter;
import world.ChunkedTileLayer;
import world.DualGridSoil;

import java.util.ArrayList;
import java.util.List;

/**
 * The named owner of "load/save the world" — the bridge between the live game objects and the binary
 * {@link WorldSave} snapshot, behind the {@link SaveManager}/{@link SaveFormat} seam. Extracting this is
 * what lets {@code PlayState.dispose} call {@link #save} explicitly instead of hiding a save inside the
 * lifecycle method.
 *
 * <p>{@link #save} builds a plain snapshot (no engine objects, no Java serialization) from the world it was
 * given; {@link #restoreCrops} replays saved crops back into the field.
 */
public final class WorldPersistence {
    private final ChunkedTileLayer collision, grass;
    private final DualGridSoil soil;
    private final CropField cropField;
    private final PlayerCharacter player;
    private final int[] inventoryItems, inventoryAmounts; // shared live refs — already current at save time
    private final CropRegistry cropRegistry;

    public WorldPersistence(ChunkedTileLayer collision, ChunkedTileLayer grass, DualGridSoil soil, CropField cropField,
                            PlayerCharacter player, int[] inventoryItems, int[] inventoryAmounts,
                            CropRegistry cropRegistry) {
        this.collision = collision;
        this.grass = grass;
        this.soil = soil;
        this.cropField = cropField;
        this.player = player;
        this.inventoryItems = inventoryItems;
        this.inventoryAmounts = inventoryAmounts;
        this.cropRegistry = cropRegistry;
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
     * Snapshot the world to the binary save file. Called on a clean exit; a SIGKILL (e.g. the dev
     * pkill-relaunch) skips it, which is fine — only deliberate quits should save.
     */
    public void save(int selectedSlot) {
        WorldSave ws = new WorldSave();
        ws.playerX = player.x();
        ws.playerY = player.y();
        ws.currentAnimation = player.currentAnimation();
        ws.walkActions = player.walkActions();
        ws.invItems = inventoryItems;
        ws.invAmounts = inventoryAmounts;
        ws.invToggle = selectedSlot;
        ws.collision = collision.toDense();
        ws.planting = soil.grid().toDense(); // tilled flags (0/1)
        ws.grass = grass.toDense();

        // Snapshot the crop grid as plain CropSave records (no PlantedCrop/CropType, which hold textures).
        List<CropSave> crops = new ArrayList<>();
        for (int j = 0; j < cropField.cols(); j++)
            for (int i = 0; i < cropField.rows(); i++) {
                PlantedCrop c = cropField.at(j, i);
                if (c == null) continue;
                crops.add(new CropSave(j, i, c.type.index, c.plantedAt, c.harvested));
            }
        ws.crops = crops.toArray(new CropSave[0]);

        SaveManager.save(ws, SaveManager.SAVE_FILE);
    }
}
