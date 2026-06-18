package farm;

import com.badlogic.gdx.Gdx;
import crops.CropField;
import crops.CropGeometry;
import crops.CropRegistry;
import crops.CropType;
import crops.PlantedCrop;
import entities.PlayerCharacter;
import items.ItemRegistry;
import physics.CollisionResolver;
import render.GameCamera;
import world.DualGridSoil;

/**
 * The player's farming actions and the cell they target: hovering a tile, planting the selected seed,
 * harvesting a mature crop, and tilling the cell in front of the player. Owns the hover state that the
 * renderer's highlight reads. This is where the planting↔inventory coupling lives, made explicit: planting
 * consumes a seed from the held inventory arrays and harvesting banks the produce back into them.
 */
public final class FarmInteraction {
    private final DualGridSoil soil;
    private final CropField cropField;
    private final CropRegistry cropRegistry;
    private final ItemRegistry itemRegistry;
    private final CropGeometry cropGeometry;
    private final CollisionResolver collisionResolver;
    private final GameCamera camera;
    private final PlayerCharacter player;
    private final int[] inventoryItems, inventoryAmounts; // shared live refs with the Inventory/PlayState

    private int hoverCol = -1, hoverRow = -1; // tile under the mouse this frame
    private boolean hoverPlantable = false;   // hovered tile is tilled soil, empty, and clear of the player

    public FarmInteraction(DualGridSoil soil, CropField cropField, CropRegistry cropRegistry,
                           ItemRegistry itemRegistry, CropGeometry cropGeometry,
                           CollisionResolver collisionResolver, GameCamera camera, PlayerCharacter player,
                           int[] inventoryItems, int[] inventoryAmounts) {
        this.soil = soil;
        this.cropField = cropField;
        this.cropRegistry = cropRegistry;
        this.itemRegistry = itemRegistry;
        this.cropGeometry = cropGeometry;
        this.collisionResolver = collisionResolver;
        this.camera = camera;
        this.player = player;
        this.inventoryItems = inventoryItems;
        this.inventoryAmounts = inventoryAmounts;
    }

    public int hoverCol() { return hoverCol; }
    public int hoverRow() { return hoverRow; }
    public boolean hoverPlantable() { return hoverPlantable; }

    /** Resolve the cell under the mouse. A cell is plantable when it's tilled soil, empty, and planting
     *  there wouldn't immediately overlap the player. */
    public void updateHover() {
        var mouseWorld = camera.unproject(Gdx.input.getX(), Gdx.input.getY());
        hoverCol = soil.grid().colAtWorldX(mouseWorld.x);
        hoverRow = soil.grid().rowAtWorldY(mouseWorld.y);
        hoverPlantable = soil.isTilled(hoverCol, hoverRow) && cropField.isOpen(hoverCol, hoverRow)
                     && !playerOverlapsCropCell(hoverCol, hoverRow);
    }

    /** Plant the selected seed (an inventory crop item) at the hovered tile, consuming one seed. */
    public void plantAtHover(int selectedSlot) {
        CropType seed = cropRegistry.get(itemRegistry.cropIndexFor(inventoryItems[selectedSlot]));
        if (seed == null || inventoryAmounts[selectedSlot] <= 0) return; // no seed of the selected crop in hand
        cropField.plant(hoverCol, hoverRow, seed, System.currentTimeMillis());
        if (--inventoryAmounts[selectedSlot] <= 0) {
            inventoryAmounts[selectedSlot] = 0;
            inventoryItems[selectedSlot] = 0;
        }
    }

    /** Harvest the hovered crop if it's mature: bank the produce and leave a hole that can be replanted. */
    public void harvestAtHover() {
        PlantedCrop crop = cropField.at(hoverCol, hoverRow);
        if (crop == null || !crop.isMature(System.currentTimeMillis())) return;
        addInventory(crop.type.itemId, 1);
        crop.harvested = true; // now renders the hole frame; cell is open for replanting
    }

    /** Till the cell one step in front of the player, which the dual grid renders as a clean 4-tile square.
     *  "In front" = the player's standing (hitbox-center) cell, offset one tile in the facing direction —
     *  except <b>down</b>, which targets the cell at the feet (the hitbox <em>bottom</em> edge): the hitbox is
     *  only half a tile tall and sits at the very bottom of the sprite, so a full cell below its center
     *  overshoots the feet by up to a tile. Using the bottom edge lands the soil snug at the feet (it drops
     *  the target a row only when that gap would be large, matching up/left/right's tightness). */
    public void performTill(int dir) {
        int col = soil.grid().colAtWorldX(player.hitboxCenterX());
        int row = soil.grid().rowAtWorldY(player.hitboxCenterY());
        switch (dir) { // 0 up, 1 left, 2 down, 3 right (Y-down: up = -row)
            case 0 -> row -= 1;
            case 1 -> col -= 1;
            case 2 -> row = soil.grid().rowAtWorldY(player.hitboxY() + player.hitboxH());
            case 3 -> col += 1;
        }
        if (!soil.grid().inBounds(col, row) || collisionResolver.isSolid(col, row)) return;
        soil.till(col, row);
    }

    /** Would a crop planted in cell (col,row) immediately overlap the player where they stand now? */
    private boolean playerOverlapsCropCell(int col, int row) {
        return CollisionResolver.overlaps(
                player.hitboxX(), player.hitboxY(), player.hitboxW(), player.hitboxH(),
                cropGeometry.footprintX(col), cropGeometry.footprintY(row),
                cropGeometry.footprintWidth, cropGeometry.footprintHeight);
    }

    /** Add an item to the inventory: stack onto a matching slot, else fill the first empty slot. */
    private void addInventory(int itemId, int amount) {
        for (int i = 0; i < inventoryItems.length; i++) {
            if (inventoryItems[i] == itemId) { inventoryAmounts[i] += amount; return; }
        }
        for (int i = 0; i < inventoryItems.length; i++) {
            if (inventoryItems[i] == 0) { inventoryItems[i] = itemId; inventoryAmounts[i] = amount; return; }
        }
    }
}
