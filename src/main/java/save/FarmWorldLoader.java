package save;

import entities.Inventory;
import world.ChunkedTileLayer;
import world.DualGridSoil;
import world.GrassGenerator;
import world.ObjectLayer;
import world.PlacedObject;

/**
 * The load counterpart to {@link WorldPersistence}: decodes a loaded {@link WorldSave} (or {@code null} for
 * "no save") into ready-to-use world state — the bounded tile layers, the dual-grid soil, the world seed,
 * the rebuilt inventory + selected slot, the player spawn, and the crops to replay. Pure logic (no file IO,
 * no libGDX/GL), so {@link PlayState}'s constructor can hand it a {@code WorldSave} and the decode is unit-
 * testable on its own. {@code SaveManager} still owns the file read; this owns turning the result into world.
 *
 * <p>All saved grids are placed top-left into a fresh {@code cols×rows} bounded grid: an old, smaller save
 * lands in the corner; a full-size one fills it. The tilled layer is normalized to a plain 0/1 flag (any
 * legacy autotile index becomes "tilled?"), since the dual grid derives the actual tiles at render time.
 */
public final class FarmWorldLoader {
    // Fresh-world spawn defaults (no save). 34 = WALK_DOWN; {32,33,34,35} = the WALK_UP/LEFT/DOWN/RIGHT set.
    private static final float DEFAULT_SPAWN_X = 2000f, DEFAULT_SPAWN_Y = 800f;
    private static final int DEFAULT_ANIMATION = 34;

    private final int cols, rows;
    private final float tile;

    public FarmWorldLoader(int cols, int rows, float tile) {
        this.cols = cols;
        this.rows = rows;
        this.tile = tile;
    }

    /** Decode {@code ws} (or {@code null} = fresh world) into the world state {@link PlayState} wires up. */
    public LoadedWorld load(WorldSave ws) {
        // Keep the saved seed; mint a fresh one for a new world (or a pre-seed save where it's 0).
        long seed = (ws != null && ws.worldSeed != 0) ? ws.worldSeed : System.nanoTime();

        ChunkedTileLayer collision = placeIntoWorld(ws == null ? null : ws.collision, false);
        ChunkedTileLayer grass = (ws != null && ws.grass != null)
                ? placeIntoWorld(ws.grass, false)
                : GrassGenerator.randomGrass(cols, rows, tile, seed); // deterministic fresh grass
        DualGridSoil soil = new DualGridSoil(placeIntoWorld(ws == null ? null : ws.planting, true));
        ChunkedTileLayer decoration = placeIntoWorld(ws == null ? null : ws.decoration, false);
        ObjectLayer objects = buildObjects(ws == null ? null : ws.objects);

        // Rebuild the live Inventory from saved primitives (no longer serialized directly). Fixed at
        // Inventory.SIZE (hotbar + bag); a shorter old save copies into the low slots, the tail stays empty.
        Inventory inv = new Inventory(Inventory.SIZE);
        int[] items = inv.getInventory();        // shared refs: mutating these mutates the live Inventory
        int[] amounts = inv.getInventoryAmounts();
        if (ws != null && ws.invItems != null) {
            for (int i = 0; i < items.length && i < ws.invItems.length; i++) {
                items[i] = ws.invItems[i];
                amounts[i] = ws.invAmounts[i];
            }
        }
        int savedSlot = ws != null ? ws.invToggle : 0;
        inv.moveSelection(savedSlot - inv.getSelectedSlot());
        int selectedSlot = inv.getSelectedSlot();

        LayerSave[] layers = ws == null ? null : ws.layers;
        if (ws != null) {
            return new LoadedWorld(collision, grass, decoration, soil, objects, layers, seed, items, amounts,
                    selectedSlot, ws.playerX, ws.playerY, ws.currentAnimation, ws.walkActions, ws.crops);
        }
        return new LoadedWorld(collision, grass, decoration, soil, objects, layers, seed, items, amounts,
                selectedSlot, DEFAULT_SPAWN_X, DEFAULT_SPAWN_Y, DEFAULT_ANIMATION, new int[]{32, 33, 34, 35}, null);
    }

    /** Rebuild the object layer from saved records. The drawable {@code TextureRegion} is GL state, so it's
     *  left null here and filled in by the caller (which has the asset cache) — keeping this loader GL-free. */
    private ObjectLayer buildObjects(ObjectSave[] saved) {
        ObjectLayer layer = new ObjectLayer();
        if (saved == null) return layer;
        for (ObjectSave o : saved) {
            layer.add(new PlacedObject(o.sheetPath, o.srcX, o.srcY, o.srcW, o.srcH,
                    o.worldX, o.worldY, o.drawW, o.drawH));
        }
        return layer;
    }

    /** Build a world-sized layer and copy a saved grid into it (top-left aligned). A {@code null} source
     *  yields an empty layer; a smaller source occupies the corner. When {@code asTilledFlag}, any nonzero
     *  source cell normalizes to 1 (legacy autotile indices → "tilled?"). */
    private ChunkedTileLayer placeIntoWorld(int[][] src, boolean asTilledFlag) {
        ChunkedTileLayer layer = new ChunkedTileLayer(cols, rows, tile);
        if (src == null) return layer;
        int c = Math.min(src.length, cols);
        for (int col = 0; col < c; col++) {
            int r = Math.min(src[col].length, rows);
            for (int row = 0; row < r; row++) {
                int v = asTilledFlag ? (src[col][row] != 0 ? 1 : 0) : src[col][row];
                layer.set(col, row, v);
            }
        }
        return layer;
    }

    /**
     * The decoded world state, ready to wire into {@link PlayState}. The inventory arrays are the live
     * backing arrays (shared refs — they're what the game mutates and re-saves); {@code crops} is the raw
     * {@link CropSave} list to replay once the crop registry exists ({@code null} when there's no save).
     */
    public record LoadedWorld(
            ChunkedTileLayer collision,
            ChunkedTileLayer grass,
            ChunkedTileLayer decoration,
            DualGridSoil soil,
            ObjectLayer objects,
            LayerSave[] layers,
            long worldSeed,
            int[] inventoryItems,
            int[] inventoryAmounts,
            int selectedSlot,
            float playerX,
            float playerY,
            int animation,
            int[] walkActions,
            CropSave[] crops) {
    }
}
