package states;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import core.Assets;
import core.Config;
import core.GameState;
import core.GameStateManager;
import core.Log;
import crops.CropField;
import crops.CropGeometry;
import crops.CropRegistry;
import crops.CropType;
import crops.data.CropDataSource;
import crops.data.DefaultCropDataSource;
import crops.data.JsonCropDataSource;
import data.ProbeLoader;
import entities.Inventory;
import entities.PlayerCharacter;
import farm.FarmInteraction;
import input.InputAction;
import input.InputBindings;
import items.ItemRegistry;
import items.data.DefaultItemDataSource;
import items.data.ItemDataSource;
import items.data.JsonItemDataSource;
import physics.CollisionResolver;
import render.GameCamera;
import render.TextureSlicer;
import render.WorldRenderer;
import save.CropSave;
import save.SaveManager;
import save.WorldPersistence;
import save.WorldSave;
import tools.ToolAction;
import tools.ToolActionSystem;
import ui.InventoryHud;
import ui.InventoryInteraction;
import tools.data.DefaultToolActionDataSource;
import tools.data.JsonToolActionDataSource;
import tools.data.ToolActionDataSource;
import tools.data.ToolActionBlueprint;
import world.ChunkedTileLayer;
import world.DualGridSoil;
import world.GrassGenerator;

import static utilz.Constants.PlayerActionConstants.playerAnimationArrays;

/**
 * Renders your real saved world (grass + tilled soil + collidables, depth-sorted
 * around the player), the player with a hand-stepped walk animation and AABB
 * collision, and the screen-space inventory HUD. All from your save in
 * ~/.farmlander, faithful to the Swing layers.
 *
 * Deferred: crops (none planted), tool actions / planting interaction.
 */
public class PlayState extends GameState {
    // Game-tuning dials (world size, tile size, move speed, autosave/grow timers) live in core.Config.
    // These two are internal cadence/epsilon values, not design dials, so they stay here.
    private static final float ANIM_FRAME_TIME = 16f / 144f; // tool-action animation cadence
    private static final float COLLISION_ERR = 0.45f; // passed to CollisionResolver (push past the edge)
    // Crop sprite/footprint geometry on a cell lives in CropGeometry (shared by collision, planting, draw).
    private final CropGeometry cropGeometry = new CropGeometry(Config.TILE);

    private final GameCamera gameCamera; // world camera (follows the player) + screen-space HUD camera
    private final InputBindings input = new InputBindings(); // semantic action -> key layer (reboundable)

    private final Texture grassTexture, plowedTexture, tallGrassTexture, wheatTexture, youngWheatTexture,
            playerTexture, chopTexture;
    private final InventoryHud inventoryHud; // screen-space inventory bar (owns its regions + icon cache)
    private InventoryInteraction inventoryInteraction; // mouse drag/move/select while the popup is open
    private final WorldRenderer worldRenderer; // grass/soil/crops/bushes/player depth-sorted draws

    // Every classpath texture is owned by one lazy cache (load-on-first-use + dispose-all).
    private final Assets assets = new Assets();

    // World grid (single source of truth for all per-cell state). Soil is a dual grid: gameplay reads
    // the `tilled` flags on `soil`; the visuals are derived on a half-tile-offset grid at render time.
    private final ChunkedTileLayer grassLayer, collisionLayer;
    private final long worldSeed; // seed that generated this bounded world's grass (persisted in the save)
    private final DualGridSoil soil;
    private final CollisionResolver collisionResolver;
    private PlayerCharacter player; // position, facing, tool, walk animation; also the collision Body
    private final int[] inventoryItems, inventoryAmounts; // live refs into the Inventory built at load
    private int selectedSlot;

    // Crop subsystem.
    private final Texture cropsTexture;
    private final CropRegistry cropRegistry;
    private final ItemRegistry itemRegistry;
    private final CropField cropField;
    private FarmInteraction farmInteraction; // hover targeting + plant/harvest/till
    private WorldPersistence worldPersistence; // save snapshot + crop restore
    private float autosaveTimer = 0; // counts up to Config.AUTOSAVE_INTERVAL, then saves
    private boolean inventoryOpen = false; // TAB toggles the inventory popup; world is frozen while it's open

    // Data-driven tool Q-actions, indexed by toolIndex (null = no action for that tool). ToolActionSystem
    // owns the live animation/effect state; the array is filled by buildToolAction during construction.
    private final ToolAction[] toolActions = new ToolAction[playerAnimationArrays.length];
    private ToolActionSystem toolActionSystem;

    public PlayState(GameStateManager stateManager) {
        super(stateManager);

        gameCamera = new GameCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        grassTexture      = loadTexture("tilesets/grassTileset.png");
        plowedTexture     = loadTexture("tilesets/plowed_soil.png");
        tallGrassTexture  = loadTexture("tilesets/tallgrass.png");
        wheatTexture      = loadTexture("tilesets/wheat.png");
        youngWheatTexture = loadTexture("tilesets/youngwheat.png");
        playerTexture     = loadTexture("player_animations/player_mining.png");
        chopTexture       = loadTexture("player_animations/player_chopping.png");
        // HUD sheets — kept local; InventoryHud slices and owns their regions (textures stay in `assets`).
        Texture inventoryBarTexture = loadTexture("ui/Inventory.png");
        Texture toggleTexture       = loadTexture("ui/toggle.png");
        Texture fontTexture         = loadTexture("ui/game_font.png");

        // World tile regions — handed to WorldRenderer, which owns the drawing.
        TextureRegion[] grassRegions    = TextureSlicer.slice(grassTexture, 1, 3);
        TextureRegion[] soilRegions     = TextureSlicer.slice(plowedTexture, 6, 3); // plowed_soil 1..18
        TextureRegion[] collisionRegions = new TextureRegion[55];
        TextureRegion[] tg = TextureSlicer.slice(tallGrassTexture, 6, 3);
        TextureRegion[] wh = TextureSlicer.slice(wheatTexture, 6, 3);
        TextureRegion[] yw = TextureSlicer.slice(youngWheatTexture, 6, 3);
        for (int k = 1; k <= 18; k++) {
            collisionRegions[k]      = tg[k];
            collisionRegions[k + 18] = wh[k];
            collisionRegions[k + 36] = yw[k];
        }

        // Player walk sheet: 36 animation rows × 9 frames of 64px. Handed to PlayerCharacter below.
        TextureRegion[][] playerFrames = new TextureRegion[36][9];
        float tw = playerTexture.getWidth(), th = playerTexture.getHeight();
        float pad = 0.5f;
        for (int row = 0; row < 36; row++) {
            for (int col = 0; col < 9; col++) {
                TextureRegion r = new TextureRegion(playerTexture);
                r.setRegion((col * 64 + pad) / tw, (row * 64 + pad) / th,
                            ((col + 1) * 64 - pad) / tw, ((row + 1) * 64 - pad) / th);
                r.flip(false, true);
                playerFrames[row][col] = r;
            }
        }

        // Tool Q-actions are data-driven (data/tool_actions.json), behind a swappable ToolActionDataSource
        // seam; a missing/unparseable file falls back to the built-in catalog at load (see ProbeLoader).
        for (ToolActionBlueprint blueprint : ProbeLoader.loadOrFallback("tool_actions.json",
                () -> new JsonToolActionDataSource(Gdx.files.classpath("data/tool_actions.json")),
                ToolActionDataSource::loadToolActionBlueprints,
                DefaultToolActionDataSource::new)) buildToolAction(blueprint);
        // At the effect frame, the system calls back into applyActionEffect (today: till).
        toolActionSystem = new ToolActionSystem(toolActions, ANIM_FRAME_TIME, this::applyActionEffect);

        int[][] sCollision, sPlanting, sGrass;
        float px, py;
        int animation;
        int[] selected;
        int[] sInvItems, sInvAmounts;
        int sSelectedSlot;
        // Crops read from the save (null when there's no save → none to restore).
        CropSave[] sCrops;
        // One read through the swappable SaveFormat seam: SaveManager picks binary vs. the legacy
        // positional Java-serialization reader by sniffing the file's magic, so old saves migrate.
        WorldSave ws = SaveManager.load(SaveManager.SAVE_FILE);
        if (ws != null) {
            sCollision = ws.collision;
            sPlanting  = ws.planting;
            sGrass     = ws.grass;
            sInvItems  = ws.invItems;
            sInvAmounts = ws.invAmounts;
            sSelectedSlot = ws.invToggle;
            px = ws.playerX;
            py = ws.playerY;
            selected = ws.walkActions;
            animation = ws.currentAnimation;
            sCrops = ws.crops;
        } else {
            // Fresh world: a seeded grass field (deterministic) and empty collision/soil at world size.
            sGrass = null; // signals "generate" below
            sPlanting = null;
            sCollision = null;
            sInvItems = null;
            sInvAmounts = null;
            sSelectedSlot = 0;
            px = 2000;
            py = 800;
            animation = 34;
            selected = new int[]{32, 33, 34, 35};
            sCrops = null;
        }
        // The world's seed: keep the saved one, or mint a fresh seed for a new world (or a pre-seed save).
        worldSeed = (ws != null && ws.worldSeed != 0) ? ws.worldSeed : System.nanoTime();
        // All layers live on one bounded grid (WORLD_COLS x WORLD_ROWS). Saved grids are placed into it —
        // an old 40x22 save lands in the corner; a current full-size save fills it. The tilled layer is
        // normalized to 0/1 (any legacy autotile index becomes "tilled?"); the dual grid derives the tiles.
        collisionLayer = placeIntoWorld(sCollision, false);
        grassLayer = (sGrass != null) ? placeIntoWorld(sGrass, false)
                                      : GrassGenerator.randomGrass(Config.WORLD_COLS, Config.WORLD_ROWS, Config.TILE, worldSeed);
        soil = new DualGridSoil(placeIntoWorld(sPlanting, true));
        // Rebuild the live Inventory from saved primitives (it's no longer serialized directly). Fixed at
        // Inventory.SIZE (hotbar + bag); a shorter old save copies into the low slots, the tail stays empty.
        Inventory inv = new Inventory(Inventory.SIZE);
        inventoryItems = inv.getInventory();      // shared refs: mutating these mutates the live Inventory
        inventoryAmounts = inv.getInventoryAmounts();
        if (sInvItems != null) {
            for (int i = 0; i < inventoryItems.length && i < sInvItems.length; i++) {
                inventoryItems[i] = sInvItems[i];
                inventoryAmounts[i] = sInvAmounts[i];
            }
        }
        inv.moveSelection(sSelectedSlot - inv.getSelectedSlot());
        selectedSlot = inv.getSelectedSlot();
        // Player avatar: position + walk sheet, recovering tool/facing from the saved animation state.
        player = new PlayerCharacter(px, py, animation, selected, playerFrames);

        // Crop subsystem: catalog from crops.png, an empty field sized to the world, and a 1x1
        // white pixel for the hover highlight (kept on the main SpriteBatch — no ShapeRenderer).
        cropsTexture = loadTexture("tilesets/crops.png");
        // Crop blueprints come from data/crops.json behind a swappable CropDataSource seam; a missing or
        // unparseable file falls back to the built-in catalog at load (see ProbeLoader).
        cropRegistry = new CropRegistry(cropsTexture, ProbeLoader.loadOrFallback("crops.json",
                () -> new JsonCropDataSource(Gdx.files.classpath("data/crops.json")),
                CropDataSource::loadCropBlueprints,
                DefaultCropDataSource::new), Config.DEFAULT_STAGE_MILLIS);
        // Items: same probe-load + fallback. The data file owns the tools; crop seed/produce items are
        // derived from the crop catalog so their names aren't duplicated out of crops.json.
        CropType[] cropTypes = new CropType[cropRegistry.count()];
        for (int i = 0; i < cropTypes.length; i++) cropTypes[i] = cropRegistry.get(i);
        itemRegistry = new ItemRegistry(ProbeLoader.loadOrFallback("items.json",
                () -> new JsonItemDataSource(Gdx.files.classpath("data/items.json")),
                ItemDataSource::loadItemBlueprints,
                DefaultItemDataSource::new), cropTypes);
        grantMissingTools(); // ensure the player holds the tools as items (fresh worlds + pre-tool saves)
        inventoryHud = new InventoryHud(inventoryBarTexture, toggleTexture, fontTexture, itemRegistry, assets);
        inventoryInteraction = new InventoryInteraction(inventoryItems, inventoryAmounts, inventoryHud, input);
        cropField = new CropField(collisionLayer.cols(), collisionLayer.rows());
        collisionResolver = new CollisionResolver(collisionLayer, cropField, cropGeometry, Config.TILE, COLLISION_ERR);
        worldPersistence = new WorldPersistence(collisionLayer, grassLayer, soil, cropField, player,
                inventoryItems, inventoryAmounts, cropRegistry, worldSeed);
        worldPersistence.restoreCrops(sCrops);
        worldRenderer = new WorldRenderer(grassLayer, soil, collisionLayer, cropField, cropGeometry, Config.TILE,
                grassRegions, soilRegions, collisionRegions);
        farmInteraction = new FarmInteraction(soil, cropField, cropRegistry, itemRegistry, cropGeometry,
                collisionResolver, gameCamera, player, inventoryItems, inventoryAmounts);
        syncSelectedTool(); // initial held tool reflects the selected hotbar slot
    }

    /** Load a classpath texture with Nearest filtering (crisp pixel art, no color softening). */
    private Texture loadTexture(String path) {
        return assets.get(path); // lazy load + cache + dispose all handled by Assets
    }

    /** Build a world-sized layer and copy a saved grid into it (top-left aligned). A null source yields an
     *  empty layer; a smaller source (an old 40x22 save) occupies the corner. When {@code asTilledFlag},
     *  any nonzero source cell normalizes to 1 (legacy autotile indices → "tilled?"). */
    private static ChunkedTileLayer placeIntoWorld(int[][] src, boolean asTilledFlag) {
        ChunkedTileLayer layer = new ChunkedTileLayer(Config.WORLD_COLS, Config.WORLD_ROWS, Config.TILE);
        if (src == null) return layer;
        int cols = Math.min(src.length, Config.WORLD_COLS);
        for (int c = 0; c < cols; c++) {
            int rows = Math.min(src[c].length, Config.WORLD_ROWS);
            for (int r = 0; r < rows; r++) {
                int v = asTilledFlag ? (src[c][r] != 0 ? 1 : 0) : src[c][r];
                layer.set(c, r, v);
            }
        }
        return layer;
    }

    /** Turn a data {@link ToolActionBlueprint} into a live {@link ToolAction}, slicing from the named sheet
     *  with the same helpers the hardcoded version used. */
    private void buildToolAction(ToolActionBlueprint blueprint) {
        if (blueprint == null || blueprint.toolIndex < 0 || blueprint.toolIndex >= toolActions.length) return;
        Texture sheet = "chopping".equals(blueprint.sheetName) ? chopTexture : playerTexture;
        TextureRegion[][] frames = blueprint.oversized
                ? TextureSlicer.sliceActionRows128(sheet, blueprint.directionRows, blueprint.rawFrameCount)
                : TextureSlicer.sliceActionRows(sheet, blueprint.directionRows, blueprint.rawFrameCount);
        if (blueprint.frameOrder != null) frames = TextureSlicer.reorder(frames, blueprint.frameOrder);
        int frameCount = (blueprint.frameOrder != null) ? blueprint.frameOrder.length : blueprint.rawFrameCount;
        // Oversized swings draw at 2x, centered on the normal footprint; standard actions draw 1:1.
        float drawSize = blueprint.oversized ? PlayerCharacter.SIZE * 2f : PlayerCharacter.SIZE;
        float off = -(drawSize - PlayerCharacter.SIZE) / 2f;
        toolActions[blueprint.toolIndex] =
                new ToolAction(frames, frameCount, blueprint.effectFrame, blueprint.effect, drawSize, off, off);
    }

    /** Make sure the player actually holds the tools as items: grant any tool id not already in the bag,
     *  into the first empty slot. Slots are scanned low-first, so tools prefer the hotbar (0..HOTBAR-1)
     *  where they're selectable. Runs for fresh worlds and for older saves from before tools were items. */
    private void grantMissingTools() {
        for (int toolId : itemRegistry.toolItemIds()) {
            boolean present = false;
            for (int v : inventoryItems) if (v == toolId) { present = true; break; }
            if (present) continue;
            for (int i = 0; i < inventoryItems.length; i++) {
                if (inventoryItems[i] == 0) { // 0 == InventoryConstants.Nothing (empty slot)
                    inventoryItems[i] = toolId;
                    inventoryAmounts[i] = 1;
                    break;
                }
            }
        }
    }

    /** Sync the player's active tool to the selected hotbar item: a tool item activates its animation set,
     *  anything else (a seed or an empty slot) leaves the player empty-handed. This is how tools are chosen
     *  now — by selecting them in the hotbar. */
    private void syncSelectedTool() {
        player.setTool(itemRegistry.toolIndexFor(inventoryItems[selectedSlot]));
    }

    /** Would the player's feet hitbox collide if shifted by (dx,dy) from where it is now? */
    private boolean wouldCollide(float dx, float dy) {
        return collisionResolver.collidesAt(player.hitboxX() + dx, player.hitboxY() + dy,
                                            player.hitboxW(), player.hitboxH());
    }

    @Override
    public void update(float delta) {
        if (input.isDown(InputAction.QUIT)) Gdx.app.exit();
        autosave(delta); // periodic save so a crash/SIGKILL doesn't lose progress (exit-only save isn't enough)

        // TAB toggles the inventory popup. While it's open the world is a frozen, dimmed backdrop (a modal):
        // no movement, no tool use, no planting — only browsing/rearranging the bag, or closing it again.
        if (input.isJustPressed(InputAction.OPEN_INVENTORY)) {
            inventoryOpen = !inventoryOpen;
            if (!inventoryOpen) inventoryInteraction.cancel();
        }
        if (inventoryOpen) {
            // Mouse: drag/move items, shift to act on the whole stack, click a slot to select it (re-syncing
            // the held tool), click the dim backdrop to close.
            InventoryInteraction.Result r = inventoryInteraction.update(selectedSlot, true);
            selectedSlot = r.selectedSlot;
            if (r.close) { inventoryOpen = false; inventoryInteraction.cancel(); }
            syncSelectedTool(); // a move may have changed the selected slot's item
            updateCamera();
            return;
        }

        // A tool Q-action is playing out: animate it (firing its effect at the effect frame), no movement.
        if (toolActionSystem.isActive()) {
            toolActionSystem.update(delta);
            updateCamera();
            return;
        }

        boolean up    = input.isDown(InputAction.MOVE_UP);
        boolean down  = input.isDown(InputAction.MOVE_DOWN);
        boolean left  = input.isDown(InputAction.MOVE_LEFT);
        boolean right = input.isDown(InputAction.MOVE_RIGHT);

        float d = Config.MOVE_SPEED * delta;
        int ix = (right ? 1 : 0) - (left ? 1 : 0);
        int iy = (down ? 1 : 0) - (up ? 1 : 0); // Y-down
        if (ix != 0 && iy != 0) {
            boolean xBlocked = wouldCollide(ix * d, 0);
            boolean yBlocked = wouldCollide(0, iy * d);
            if (xBlocked ^ yBlocked) {
                if (xBlocked) player.move(0, iy * d);
                else player.move(ix * d, 0);
            } else {
                float inv = 0.70710678f;
                player.move(ix * d * inv, iy * d * inv);
            }
        } else {
            player.move(ix * d, iy * d);
        }

        collisionResolver.resolve(player);

        // Hotbar selection (1/3). Changing the selected slot re-syncs the held tool: tool switching now
        // happens through inventory selection, not a dedicated cycle key. These keys stay within the hotbar,
        // snapping the selection back in if a click had parked it on a bag slot.
        int hotbarSel = Math.min(selectedSlot, Inventory.HOTBAR - 1);
        if (input.isJustPressed(InputAction.SELECT_PREV)) { selectedSlot = Math.max(0, hotbarSel - 1); syncSelectedTool(); }
        if (input.isJustPressed(InputAction.SELECT_NEXT)) { selectedSlot = Math.min(Inventory.HOTBAR - 1, hotbarSel + 1); syncSelectedTool(); }

        // Facing follows ACTUAL movement: opposing keys keep the current facing; horizontal wins diagonals.
        player.setFacingFromMotion(ix, iy);

        // USE_TOOL (Q): trigger the current tool's action animation (and its world effect, if any).
        if (input.isJustPressed(InputAction.USE_TOOL)) {
            toolActionSystem.trigger(player.toolIndex(), player.facing());
            if (toolActionSystem.isActive()) {
                updateCamera();
                return;
            }
        }

        player.stepWalk(delta, ix != 0 || iy != 0);

        farmInteraction.updateHover();
        // Left-click a plantable tile to sow the selected seed; HARVEST (E) reaps a mature crop under the mouse.
        if (input.isPrimaryPointerJustPressed() && farmInteraction.hoverPlantable())
            farmInteraction.plantAtHover(selectedSlot);
        if (input.isJustPressed(InputAction.HARVEST)) farmInteraction.harvestAtHover();

        updateCamera();
    }

    private void updateCamera() {
        gameCamera.follow(player.centerX(), player.centerY());
    }

    /** Save every {@link Config#AUTOSAVE_INTERVAL} seconds so a crash doesn't lose progress between clean exits. */
    private void autosave(float delta) {
        autosaveTimer += delta;
        if (autosaveTimer < Config.AUTOSAVE_INTERVAL) return;
        autosaveTimer = 0;
        try {
            worldPersistence.save(selectedSlot);
            Log.info("PlayState", "autosaved");
        } catch (Exception e) {
            Log.error("PlayState", "autosave failed", e);
        }
    }

    /** Apply a tool action's world effect at its effect frame. Only tilling has one today; new
     *  effects (watering, digging, ...) slot in here keyed by the blueprint's {@code effect} string. */
    private void applyActionEffect(String effect, int dir) {
        if ("till".equals(effect)) farmInteraction.performTill(dir);
    }

    @Override
    public void render(SpriteBatch batch) {
        worldRenderer.render(batch, gameCamera, player, toolActionSystem,
                             farmInteraction.hoverCol(), farmInteraction.hoverRow(),
                             farmInteraction.hoverPlantable());
        inventoryHud.render(batch, gameCamera.hudCombined(), inventoryItems, inventoryAmounts, selectedSlot,
                            inventoryOpen, inventoryInteraction.draggedItemId(),
                            inventoryInteraction.cursorX(), inventoryInteraction.cursorY());
    }

    @Override
    public void resize(int width, int height) {
        gameCamera.resize(width, height);
    }

    @Override
    public void dispose() {
        // Save on a clean exit (explicit now — no longer hidden inside a lifecycle method). Never let a
        // save error block resource cleanup on shutdown.
        try { worldPersistence.save(selectedSlot); }
        catch (Exception e) { Log.error("PlayState", "save on exit failed", e); }
        assets.dispose();        // frees every classpath texture (tilesets, player sheets, UI, crops, icons)
        worldRenderer.dispose(); // frees the procedurally-generated highlight texture (not in the cache)
        inventoryHud.dispose();  // frees the procedurally-generated dim pixel
    }
}
