package farm;

import com.badlogic.gdx.Gdx;
import core.Config;
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
 * The player's farming actions and the cell they target. Aim resolves to a <b>flex target</b>: the cell in
 * front of the player ({@link #toolTargetCol}/{@link #toolTargetRow}) by default, but any of the 9 cells in
 * the 3×3 block around it when the mouse hovers within that block ({@link #targetCol}/{@link #targetRow}) — a
 * one-tile aim flex shared by every tool and by planting. {@link PlayState} captures that cell when an action
 * starts and hands it to the tool effects here — {@link #tillAt}, {@link #waterAt}, {@link #untillAt},
 * {@link #digAt}, {@link #chopAt} — while {@link #plantAtTarget} sows directly. The renderer frames the same
 * target. Harvesting ({@link #harvestAtHover}) still acts on the raw mouse cell.
 *
 * <p>This is also where the planting↔inventory coupling lives, made explicit: planting consumes a seed from
 * the held inventory arrays and harvesting banks the produce back into them.
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

    private int hoverCol = -1, hoverRow = -1; // raw tile under the mouse this frame (drives aim flex + harvest)
    private float boostCarryMillis = 0f; // sub-ms growth-boost remainder, carried between frames (see applyWaterGrowthBoost)

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

    /** Resolve the raw cell under the live mouse pointer, then store it. Feeds the tool/seed aim flex
     *  ({@link #targetCol}) and harvest. The pure {@link #setHoverCell} entry point behind it lets the
     *  targeting math be exercised headlessly, without a libGDX pointer. */
    public void updateHover() {
        var mouseWorld = camera.unproject(Gdx.input.getX(), Gdx.input.getY());
        setHoverCell(soil.grid().colAtWorldX(mouseWorld.x), soil.grid().rowAtWorldY(mouseWorld.y));
    }

    /** Set the hovered cell directly (package-private: the test seam {@link #updateHover} drives in prod). */
    void setHoverCell(int col, int row) {
        hoverCol = col;
        hoverRow = row;
    }

    /** Plant the selected seed (an inventory crop item) at the action target — the facing cell, or a nearby
     *  cell when the mouse flexes the aim — consuming one seed. No-op unless that cell is plantable. */
    public void plantAtTarget(int dir, int selectedSlot) {
        CropType seed = cropRegistry.get(itemRegistry.cropIndexFor(inventoryItems[selectedSlot]));
        if (seed == null || inventoryAmounts[selectedSlot] <= 0 || !targetPlantable(dir)) return;
        cropField.plant(targetCol(dir), targetRow(dir), seed, System.currentTimeMillis());
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

    /** Column of the cell a facing tool action targets: the player's standing (hitbox-center) cell, offset
     *  one tile left/right when facing that way. Shared by tilling/digging/chopping and the tool highlight. */
    public int toolTargetCol(int dir) {
        int col = soil.grid().colAtWorldX(player.hitboxCenterX());
        switch (dir) { case 1 -> col -= 1; case 3 -> col += 1; } // 1 left, 3 right
        return col;
    }

    /** Row of the cell a facing tool action targets. Up offsets one row; <b>down</b> targets the cell at the
     *  feet (the hitbox <em>bottom</em> edge) rather than a full cell below the center: the hitbox is only
     *  half a tile tall and sits at the very bottom of the sprite, so a full cell below its center overshoots
     *  the feet by up to a tile. Using the bottom edge lands the target snug at the feet (it drops the target
     *  a row only when that gap would be large, matching up/left/right's tightness). */
    public int toolTargetRow(int dir) {
        int row = soil.grid().rowAtWorldY(player.hitboxCenterY());
        switch (dir) { // 0 up, 2 down (Y-down: up = -row)
            case 0 -> row -= 1;
            case 2 -> row = soil.grid().rowAtWorldY(player.hitboxY() + player.hitboxH());
        }
        return row;
    }

    /** Is the mouse cell within the 3×3 "flex" block around the facing (expected) cell? When it is, the
     *  player is aiming at a specific nearby tile; when it isn't, the aim falls back to the facing cell. */
    private boolean mouseInFlexRange(int dir) {
        return Math.abs(hoverCol - toolTargetCol(dir)) <= 1 && Math.abs(hoverRow - toolTargetRow(dir)) <= 1;
    }

    /** Column of the action target: the mouse cell when it's within one tile of the facing cell, else the
     *  facing cell itself. This gives every tool (and planting) a 1-tile aim flex — 9 reachable cells centred
     *  on the cell in front of the player. */
    public int targetCol(int dir) { return mouseInFlexRange(dir) ? hoverCol : toolTargetCol(dir); }

    /** Row of the action target — the flex counterpart to {@link #targetCol}. */
    public int targetRow(int dir) { return mouseInFlexRange(dir) ? hoverRow : toolTargetRow(dir); }

    /** Is the action target a plantable cell — tilled, truly empty, and clear of the player? A leftover
     *  harvest lump (a "dirt pile") blocks planting; it must be shoveled away first, so this checks the cell
     *  is empty rather than merely open-for-replant. */
    public boolean targetPlantable(int dir) {
        int col = targetCol(dir), row = targetRow(dir);
        return soil.isPlantable(col, row) && cropField.isEmpty(col, row) && !playerOverlapsCropCell(col, row);
    }

    // --- Tool world effects. Each acts on an explicit cell (captured at trigger by PlayState, so the effect,
    //     which fires mid-swing, is unaffected by the mouse moving during the swing).
    //     Bounds contract: every effect is a safe no-op on an out-of-bounds or ineligible cell. `tillAt`
    //     checks explicitly (it also needs to reject solid cells); the rest reach that contract by delegating
    //     to out-of-bounds-tolerant collaborators (ChunkedTileLayer.get / CropField.at return empty for OOB),
    //     so they need no guard of their own. ---

    /** Hoe: till the cell, which the dual grid renders as a clean 4-tile square. No-op out of bounds or on a
     *  solid (collidable) cell. */
    public void tillAt(int col, int row) {
        if (!soil.grid().inBounds(col, row) || collisionResolver.isSolid(col, row)) return;
        soil.till(col, row);
    }

    /** Shovel: clear a harvest lump — the post-harvest hole a <b>root (lump) crop</b> leaves — at the cell.
     *  Disturbing the lump also dries the soil, so any water there is removed with it. No-op unless the cell
     *  holds a harvested lump crop; an above-ground (non-lump) crop's remains are the axe's job, not the
     *  shovel's, so the two tools stay one-per-crop-type. */
    public void digAt(int col, int row) {
        PlantedCrop crop = cropField.at(col, row);
        if (crop != null && crop.harvested && crop.type.leavesLump) {
            cropField.remove(col, row);
            soil.dry(col, row); // shoveling the lump turns the soil over — its water goes with it
        }
    }

    /** Pickaxe: un-till the cell back to bare ground. Only acts on empty tilled soil — it won't disturb a
     *  cell that still holds a crop or a harvest lump (clear those first). */
    public void untillAt(int col, int row) {
        if (cropField.isEmpty(col, row) && soil.isTilled(col, row)) soil.clear(col, row);
    }

    /** Watering can: wet the cell's soil (under a crop or bare). No-op unless it's plantable (tilled) soil —
     *  you can't water untilled ground. The wet patch dries on its own after a while (see
     *  {@code Config.WATER_DRY_MILLIS}). */
    public void waterAt(int col, int row) {
        soil.water(col, row, System.currentTimeMillis());
    }

    /** Axe: remove an above-ground (non-lump) crop — a berry/trellis/leafy plant that never leaves a dirt
     *  lump — at the cell, at any growth stage. A <b>fully grown</b> plant is reaped as it's cut: its produce
     *  goes to the inventory, then the plant is removed (a one-swing harvest). An unripe plant is just
     *  destroyed (no produce), and a plant already reaped by hand (E) isn't paid out twice. No-op on an empty
     *  cell or a lump (root) crop. */
    public void chopAt(int col, int row) {
        PlantedCrop crop = cropField.at(col, row);
        if (crop == null || crop.type.leavesLump) return;
        if (!crop.harvested && crop.isMature(System.currentTimeMillis())) addInventory(crop.type.itemId, 1);
        cropField.remove(col, row);
    }

    /** Watered soil grows its crop faster: pull each watered crop's growth clock earlier by
     *  {@code WATER_GROWTH_BONUS} of this frame's elapsed time (so it ages at 1 + bonus speed while wet).
     *  Walks only the watered cells (sparse), so the cost is independent of field size. */
    public void applyWaterGrowthBoost(float delta) {
        // Accumulate the bonus as a float and only ever spend whole milliseconds, carrying the sub-ms
        // remainder to the next frame. A naive per-frame (long) cast truncates to 0 once the frame is short
        // enough (BONUS*delta*1000 < 1, i.e. > ~330fps at 0.30), silently killing the boost at high refresh
        // rates; carrying the remainder makes the total bonus frame-rate-independent.
        boostCarryMillis += Config.WATER_GROWTH_BONUS * delta * 1000f;
        long extra = (long) boostCarryMillis; // whole ms of bonus age to grant this frame
        if (extra <= 0) return;
        boostCarryMillis -= extra;
        long now = System.currentTimeMillis();
        soil.forEachWatered((col, row) -> {
            PlantedCrop crop = cropField.at(col, row);
            if (crop != null && !crop.harvested && !crop.isMature(now)) crop.boostGrowth(extra);
        });
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
