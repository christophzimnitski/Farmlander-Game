package states;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import core.Assets;
import core.Config;
import core.Debug;
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
import data.AssetManifest;
import data.ProbeLoader;
import editor.TilePalette;
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
import render.DebugOverlay;
import render.GameCamera;
import render.TextureSlicer;
import render.WorldRenderer;
import save.FarmWorldLoader;
import save.LayerSave;
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
import world.ObjectLayer;
import world.PlacedObject;
import world.layer.CornerAutotileLayer;
import world.layer.FenceLayer;
import world.layer.LayerStack;
import world.layer.LayerVisibility;
import world.layer.MapLayer;
import world.layer.PlainTileLayer;

import static entities.PlayerAnimations.playerAnimationArrays;

/**
 * The single active {@link GameState}: a coordinator that wires the world subsystems together and drives
 * them each frame. {@code update} reads input, moves/collides the player, runs tool actions and
 * planting/harvesting against the flex target, ages watered soil + applies its growth boost, handles the
 * inventory popup, and autosaves; {@code render} draws the world (grass + dual-grid soil + crops +
 * collidables, depth-sorted around the player) and the screen-space inventory HUD. World state loads from
 * the binary save in {@code ~/.farmlander} on construction and saves on autosave + clean exit.
 *
 * <p>The constructor still owns the whole load path inline — texture slicing, save parse, grid placement,
 * registry + subsystem wiring — which keeps it long and untestable; extracting a {@code FarmWorldLoader}
 * is tracked tech debt (see the code-health backlog).
 */
public class PlayState extends GameState {
    // Game-tuning dials (world size, tile size, move speed, autosave/grow timers) live in core.Config.
    // These two are internal cadence/epsilon values, not design dials, so they stay here.
    private static final float ANIM_FRAME_TIME = 16f / 144f; // tool-action animation cadence
    private static final float COLLISION_ERR = 0.45f; // passed to CollisionResolver (push past the edge)
    // Starter seed packet, as {cropIndex, count} pairs (Potato 0, Carrot 6, Cauliflower 44, Pumpkin 28 — all
    // have field + food art). Granted when the player holds no seeds. Until a shop exists (the next slice) and
    // with produce-only harvest, this is the whole seed supply, so it can run out — see grantStarterSeeds.
    private static final int[][] STARTER_SEEDS = { {0, 10}, {6, 10}, {44, 10}, {28, 10} };
    // Crop sprite/footprint geometry on a cell lives in CropGeometry (shared by collision, planting, draw).
    private final CropGeometry cropGeometry = new CropGeometry(Config.TILE);

    private final GameCamera gameCamera; // world camera (follows the player) + screen-space HUD camera
    private final InputBindings input = new InputBindings(); // semantic action -> key layer (reboundable)

    private final Texture grassTexture, plowedTexture, tallGrassTexture, wheatTexture, youngWheatTexture,
            playerTexture, chopTexture;
    private final InventoryHud inventoryHud; // screen-space inventory bar (owns its regions + icon cache)
    private final InventoryInteraction inventoryInteraction; // mouse drag/move/select while the popup is open
    private final WorldRenderer worldRenderer; // grass/soil/crops/bushes/player depth-sorted draws
    private final DebugOverlay debugOverlay = new DebugOverlay(); // F2 hitbox overlay (dev-only)

    // Every classpath texture is owned by one lazy cache (load-on-first-use + dispose-all).
    private final Assets assets = new Assets();

    // World grid (single source of truth for all per-cell state). Soil is a dual grid: gameplay reads
    // the `tilled` flags on `soil`; the visuals are derived on a half-tile-offset grid at render time.
    private final ChunkedTileLayer grassLayer, collisionLayer;
    private final long worldSeed; // seed that generated this bounded world's grass (persisted in the save)
    private final DualGridSoil soil;
    // Editor-authored world content (empty in play until a map is loaded / placements are made): a generic
    // decoration tile layer (any sheet, via the palette) and free-placed props. Shared with EditorState.
    private final TilePalette palette = new TilePalette(assets);
    private final ChunkedTileLayer decorationLayer;
    private final ObjectLayer objectLayer;
    private final LayerStack layerStack; // editor-authored stackable layers (sand, sand-water, fence, props, ...)
    private final LayerVisibility layerVisibility = new LayerVisibility(); // editor show/hide for built-in layers
    private final CollisionResolver collisionResolver;
    private final PlayerCharacter player; // position, facing, tool, walk animation; also the collision Body
    private final int[] inventoryItems, inventoryAmounts; // live refs into the Inventory built at load
    private int selectedSlot;
    private int lastHotbarSlot; // the hotbar slot to restore on inventory close — the hand never holds a bag slot

    // Crop subsystem.
    private final Texture cropsTexture;
    private final CropRegistry cropRegistry;
    private final ItemRegistry itemRegistry;
    private final CropField cropField;
    private final FarmInteraction farmInteraction; // hover targeting + plant/harvest/till
    private final WorldPersistence worldPersistence; // save snapshot + crop restore
    private float autosaveTimer = 0; // counts up to Config.AUTOSAVE_INTERVAL, then saves
    private boolean inventoryOpen = false; // TAB toggles the inventory popup; world is frozen while it's open

    // Data-driven tool Q-actions, indexed by toolIndex (null = no action for that tool). ToolActionSystem
    // owns the live animation/effect state; the array is filled by buildToolAction during construction.
    private final ToolAction[] toolActions = new ToolAction[playerAnimationArrays.length];
    private final ToolActionSystem toolActionSystem;
    // The cell the in-flight tool action acts on, captured at trigger so the mid-swing effect isn't shifted by
    // the mouse moving during the swing. Plus one optional action queued by clicking during a swing: it fires
    // the instant the swing ends, and only one may be queued at a time (a later click replaces it, no stacking).
    private int actionCol = -1, actionRow = -1;
    private boolean hasQueuedAction = false;
    private int queuedToolIndex, queuedDir, queuedCol, queuedRow;

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
        // Watered-soil blobs: 4 organic wet patches (one sheet, picked per cell for variety) drawn over
        // watered tiles. The cell-selection frame (ui/frame.png) highlights the tile a tool/seed will act on.
        TextureRegion[] wateredRegions = TextureSlicer.slice(loadTexture("tilesets/watered_soil.png"), 1, 4);
        TextureRegion frameRegion = TextureSlicer.flippedFull(loadTexture("ui/frame.png"));

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

        // One read through the swappable SaveFormat seam (SaveManager sniffs binary vs. the legacy
        // positional reader, so old saves migrate), then FarmWorldLoader decodes the result — or mints a
        // fresh world when there's no save — into ready-to-use layers, soil, seed, inventory, spawn, crops.
        WorldSave ws = SaveManager.load(SaveManager.SAVE_FILE);
        FarmWorldLoader.LoadedWorld loaded =
                new FarmWorldLoader(Config.WORLD_COLS, Config.WORLD_ROWS, Config.TILE).load(ws);
        collisionLayer = loaded.collision();
        grassLayer = loaded.grass();
        soil = loaded.soil();
        worldSeed = loaded.worldSeed();
        decorationLayer = loaded.decoration();
        objectLayer = loaded.objects();
        // Props load with null regions (the save layer is GL-free); rebuild each from its sheet now.
        for (PlacedObject o : objectLayer.all())
            o.setRegion(TextureSlicer.subRegion(assets.get(o.sheetPath), o.srcX, o.srcY, o.srcW, o.srcH));
        layerStack = buildLayerStack(collisionLayer.cols(), collisionLayer.rows());
        applySavedLayers(loaded.layers()); // restore editor layer grids from the save (matched by name)
        inventoryItems = loaded.inventoryItems();     // shared refs: mutating these mutates the loaded inventory
        inventoryAmounts = loaded.inventoryAmounts();
        selectedSlot = loaded.selectedSlot();
        lastHotbarSlot = selectedSlot; // selectedSlot is always a hotbar slot — a safe starting hand
        // Player avatar: position + walk sheet, recovering tool/facing from the saved animation state.
        player = new PlayerCharacter(loaded.playerX(), loaded.playerY(), loaded.animation(),
                loaded.walkActions(), playerFrames);

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
        // Boot-time asset check: warn for any item whose icon art is missing, so it surfaces here at startup
        // rather than as a silently-blank slot when the item is first shown (InventoryHud caches the miss).
        AssetManifest.missing(itemRegistry.iconPaths(), p -> Gdx.files.classpath(p).exists())
                .forEach((id, path) -> Log.warn("Assets", "item " + id + " icon missing: " + path));
        grantMissingTools();  // ensure the player holds the tools as items (fresh worlds + pre-tool saves)
        grantStarterSeeds();  // give a starter seed packet so a fresh farm (or a pre-seed-economy save) is plantable
        inventoryHud = new InventoryHud(inventoryBarTexture, toggleTexture, fontTexture, itemRegistry, assets);
        inventoryInteraction = new InventoryInteraction(inventoryItems, inventoryAmounts, inventoryHud, input);
        cropField = new CropField(collisionLayer.cols(), collisionLayer.rows());
        collisionResolver = new CollisionResolver(collisionLayer, cropField, cropGeometry, Config.TILE, COLLISION_ERR, layerStack);
        worldPersistence = new WorldPersistence(collisionLayer, grassLayer, decorationLayer, soil, cropField,
                objectLayer, layerStack, player, inventoryItems, inventoryAmounts, cropRegistry, worldSeed);
        worldPersistence.restoreCrops(loaded.crops());
        worldRenderer = new WorldRenderer(grassLayer, soil, collisionLayer, cropField, cropGeometry, Config.TILE,
                grassRegions, soilRegions, collisionRegions, wateredRegions, frameRegion,
                decorationLayer, objectLayer, palette, layerStack, layerVisibility);
        farmInteraction = new FarmInteraction(soil, cropField, cropRegistry, itemRegistry, cropGeometry,
                collisionResolver, gameCamera, player, inventoryItems, inventoryAmounts);
        syncSelectedTool(); // initial held tool reflects the selected hotbar slot
    }

    /** Load a classpath texture with Nearest filtering (crisp pixel art, no color softening). */
    private Texture loadTexture(String path) {
        return assets.get(path); // lazy load + cache + dispose all handled by Assets
    }

    /** Build the editor-authored layer stack. These sit on top of the built-in grass/soil/bush layers and are
     *  painted via the editor's Layers menu. Sand and sand-water share the soil corner dual-grid (their sheets
     *  use the same layout); fence + props are added in their own slices. */
    private LayerStack buildLayerStack(int cols, int rows) {
        LayerStack stack = new LayerStack();
        TextureRegion[] sand = TextureSlicer.slice(loadTexture("tilesets/sand.png"), 6, 3);
        TextureRegion[] sandWater = TextureSlicer.slice(loadTexture("tilesets/sandwater.png"), 6, 3);
        TextureRegion[] fence = TextureSlicer.slice(loadTexture("tilesets/fence.png"), 6, 3);
        TextureRegion[] fenceAlt = TextureSlicer.slice(loadTexture("tilesets/fence_alt.png"), 6, 3);
        TextureRegion[] props = TextureSlicer.slice(loadTexture("tilesets/farming_fishing.png"), 20, 20);
        stack.add(new CornerAutotileLayer("Sand", new ChunkedTileLayer(cols, rows, Config.TILE), sand,
                TilePalette.indexOf("tilesets/sand.png")));
        stack.add(new CornerAutotileLayer("Sand water", new ChunkedTileLayer(cols, rows, Config.TILE), sandWater,
                TilePalette.indexOf("tilesets/sandwater.png")));
        stack.add(new FenceLayer("Fence", new ChunkedTileLayer(cols, rows, Config.TILE), fence,
                TilePalette.indexOf("tilesets/fence.png")));
        stack.add(new FenceLayer("Fence alt", new ChunkedTileLayer(cols, rows, Config.TILE), fenceAlt,
                TilePalette.indexOf("tilesets/fence_alt.png")));
        stack.add(new PlainTileLayer("Props", new ChunkedTileLayer(cols, rows, Config.TILE), props,
                false, true, TilePalette.indexOf("tilesets/farming_fishing.png"))); // upright, non-solid
        return stack;
    }

    /** Restore saved editor-layer grids into the stack, matched by layer name. Saved layers that aren't one of
     *  the fixed built-ins are ignored (so any leftover one-off layer simply drops away on the next load/save). */
    private void applySavedLayers(LayerSave[] saved) {
        if (saved == null) return;
        for (LayerSave s : saved) {
            if (s.grid == null) continue;
            for (int i = 0; i < layerStack.size(); i++) {
                MapLayer layer = layerStack.get(i);
                if (!layer.name().equals(s.name)) continue;
                ChunkedTileLayer g = layer.grid();
                for (int c = 0; c < s.grid.length && c < g.cols(); c++)
                    for (int r = 0; r < s.grid[c].length && r < g.rows(); r++)
                        if (s.grid[c][r] != 0) g.set(c, r, s.grid[c][r]);
                break;
            }
        }
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
                if (inventoryItems[i] == Inventory.EMPTY) { // empty slot
                    inventoryItems[i] = toolId;
                    inventoryAmounts[i] = 1;
                    break;
                }
            }
        }
    }

    /** Set each {@link #STARTER_SEEDS} crop to its target count on load — so a fresh farm (or a save from before
     *  the seed economy, whose old crop items are now <em>produce</em> and can't be replanted) always has seeds
     *  to plant. A missing starter is added to the first empty slot, after tools.
     *
     *  <p>Stopgap: with produce-only harvest and no shop yet, seeds have no other source, so this keeps a fixed
     *  baseline (currently 10 of each) plantable every load. Once the shop slice lands (the real seed source),
     *  this should be restricted to fresh worlds (no reload-refill). */
    private void grantStarterSeeds() {
        for (int[] pack : STARTER_SEEDS) {
            CropType crop = cropRegistry.get(pack[0]);
            if (crop == null) continue;
            int seedId = crop.seedItemId, target = pack[1];
            int slot = -1, firstEmpty = -1;
            for (int i = 0; i < inventoryItems.length; i++) {
                if (inventoryItems[i] == seedId) { slot = i; break; }
                if (firstEmpty < 0 && inventoryItems[i] == Inventory.EMPTY) firstEmpty = i;
            }
            if (slot < 0) slot = firstEmpty;
            if (slot >= 0) {
                inventoryItems[slot] = seedId;
                inventoryAmounts[slot] = target;
            }
        }
    }

    /** Sync the player's active tool to the selected hotbar item: a tool item activates its animation set,
     *  anything else (a seed or an empty slot) leaves the player empty-handed. This is how tools are chosen
     *  now — by selecting them in the hotbar. */
    private void syncSelectedTool() {
        player.setTool(itemRegistry.toolIndexFor(inventoryItems[selectedSlot]));
    }

    /** Close the inventory popup, snapping the selection back to the last highlighted hotbar slot. The held
     *  item always comes from the hotbar, never a bag slot, so moving a tool into the bag empties the hand. */
    private void closeInventory() {
        inventoryOpen = false;
        inventoryInteraction.cancel();
        selectedSlot = lastHotbarSlot;
        syncSelectedTool();
    }

    /** Would the player's feet hitbox collide if shifted by (dx,dy) from where it is now? */
    private boolean wouldCollide(float dx, float dy) {
        return collisionResolver.collidesAt(player.hitboxX() + dx, player.hitboxY() + dy,
                                            player.hitboxW(), player.hitboxH());
    }

    @Override
    public void update(float delta) {
        if (input.isJustPressed(InputAction.QUIT)) Gdx.app.exit();
        // F2 toggles the dev hitbox overlay. Polled before any early-return so it works while a tool swings or
        // the inventory is open.
        if (input.isJustPressed(InputAction.TOGGLE_HITBOXES)) Debug.showHitboxes = !Debug.showHitboxes;
        // F1 opens the map editor over this state (when enabled). Push and return so the rest of the play
        // update is skipped this frame; while the editor is on top, PlayState.update won't run at all.
        if (Config.EDITOR_ENABLED && input.isJustPressed(InputAction.TOGGLE_EDITOR)) {
            stateManager.push(new EditorState(stateManager, worldRenderer, assets, palette,
                    grassLayer, decorationLayer, soil, collisionLayer, layerStack, layerVisibility, collisionResolver,
                    player.centerX(), player.centerY()));
            return;
        }
        autosave(delta); // periodic save so a crash/SIGKILL doesn't lose progress (exit-only save isn't enough)
        soil.update(System.currentTimeMillis(), Config.WATER_DRY_MILLIS); // expire wet soil (ticks every frame)
        farmInteraction.applyWaterGrowthBoost(delta); // watered crops grow faster (ticks every frame, before any early-out)

        // TAB toggles the inventory popup. While it's open the world is a frozen, dimmed backdrop (a modal):
        // no movement, no tool use, no planting — only browsing/rearranging the bag, or closing it again.
        if (input.isJustPressed(InputAction.OPEN_INVENTORY)) {
            if (inventoryOpen) closeInventory();
            else { inventoryOpen = true; lastHotbarSlot = Math.min(selectedSlot, Inventory.HOTBAR - 1); }
        }
        if (inventoryOpen) {
            // Mouse: drag/move items, shift to act on the whole stack, click a slot to highlight/move it, click
            // the dim backdrop to close. The highlight may roam into the bag, but the hand tracks the hotbar.
            InventoryInteraction.Result r = inventoryInteraction.update(delta, selectedSlot, true);
            selectedSlot = r.selectedSlot;
            if (selectedSlot < Inventory.HOTBAR) lastHotbarSlot = selectedSlot; // remember the latest hotbar highlight
            if (r.close) closeInventory();
            else syncSelectedTool(); // keep the (frozen) held tool roughly in step while browsing
            updateCamera();
            return;
        }

        // A tool Q-action is playing out: animate it (firing its effect at the effect frame), no movement.
        // You can pre-fire one more action by clicking/Q'ing another tile mid-swing — it's queued and starts
        // the instant this swing ends. The hover stays live so the highlight shows where the queued tile is.
        if (toolActionSystem.isActive()) {
            toolActionSystem.update(delta);
            farmInteraction.updateHover();
            queueActionIfRequested();
            if (!toolActionSystem.isActive() && hasQueuedAction) fireQueuedAction();
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

        // Hotbar selection: the number row picks a slot directly (1..9 -> slots 0..8, 0 -> slot 9), replacing
        // the old prev/next cycling. Selecting re-syncs the held tool — tool switching happens through hotbar
        // selection — and snaps the highlight back into the hotbar if a click had parked it on a bag slot.
        int hotbarKey = input.hotbarSlotJustPressed();
        if (hotbarKey >= 0 && hotbarKey < Inventory.HOTBAR) { selectedSlot = hotbarKey; syncSelectedTool(); }

        // Facing follows ACTUAL movement: opposing keys keep the current facing; horizontal wins diagonals.
        player.setFacingFromMotion(ix, iy);
        farmInteraction.updateHover(); // mouse cell — resolve it before Q/click so both aim at the same tile

        // USE_TOOL (Q): trigger the current tool's action animation (and its world effect, if any).
        if (input.isJustPressed(InputAction.USE_TOOL)) {
            triggerHeldTool();
            if (toolActionSystem.isActive()) {
                updateCamera();
                return;
            }
        }

        player.stepWalk(delta, ix != 0 || iy != 0);

        // Left-click: clicking a hotbar slot (the on-screen bar) selects it for use, re-syncing the held
        // tool. Otherwise a click in the world acts with what's in hand at the flex target (the cell in front
        // of the player, or a nearby cell the mouse is aiming at): a tool swings (toolIndex() >= 1; 0 is the
        // empty hand), a held seed sows on a plantable tile. HARVEST (E) still reaps a mature crop under the mouse.
        if (input.isPrimaryPointerJustPressed()) {
            int clickedSlot = inventoryHud.slotAt(input.pointerX(), input.pointerY(), inventoryItems.length, false);
            if (clickedSlot >= 0) { selectedSlot = clickedSlot; syncSelectedTool(); }
            else if (player.toolIndex() >= 1) triggerHeldTool();
            else farmInteraction.plantAtTarget(player.facing(), selectedSlot);
        }
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
            worldPersistence.saveAsync(selectedSlot); // snapshot on this thread; write+fsync on a background thread
        } catch (Exception e) {
            Log.error("PlayState", "autosave failed", e);
        }
    }

    /** Start the held tool's action at the current flex target, remembering that cell for the effect. */
    private void triggerHeldTool() {
        int dir = player.facing();
        triggerToolAt(player.toolIndex(), dir, farmInteraction.targetCol(dir), farmInteraction.targetRow(dir));
    }

    /** Trigger a tool action aimed at {@code (col,row)}, capturing the target so the mid-swing effect uses it
     *  (not the live mouse, which may move during the swing). */
    private void triggerToolAt(int toolIndex, int dir, int col, int row) {
        actionCol = col;
        actionRow = row;
        toolActionSystem.trigger(toolIndex, dir);
    }

    /** During a swing, a click or Q on another tile queues one tool action (replacing any already queued —
     *  only one pending at a time). It targets the live flex cell, captured now so it's locked when it fires. */
    private void queueActionIfRequested() {
        if (player.toolIndex() < 1) return; // only tools have a swing to queue behind
        boolean q = input.isJustPressed(InputAction.USE_TOOL);
        boolean worldClick = input.isPrimaryPointerJustPressed()
                && inventoryHud.slotAt(input.pointerX(), input.pointerY(), inventoryItems.length, false) < 0;
        if (!q && !worldClick) return;
        int dir = player.facing();
        queuedToolIndex = player.toolIndex();
        queuedDir = dir;
        queuedCol = farmInteraction.targetCol(dir);
        queuedRow = farmInteraction.targetRow(dir);
        hasQueuedAction = true;
    }

    /** Promote the queued action to the active swing (called the instant the previous swing ends). */
    private void fireQueuedAction() {
        hasQueuedAction = false;
        triggerToolAt(queuedToolIndex, queuedDir, queuedCol, queuedRow);
    }

    /** Apply a tool action's world effect at its effect frame, keyed by the blueprint's {@code effect} string,
     *  on the cell captured when the action started ({@code actionCol,actionRow}). The hoe tills, the watering
     *  can wets the soil, the pickaxe un-tills, the shovel clears a harvest lump, the axe removes a non-lump
     *  (continuous/trellis) crop. The {@code dir} the system passes back isn't needed — the cell is captured. */
    private void applyActionEffect(String effect, int dir) {
        if (effect == null) return;
        switch (effect) {
            case "till"   -> farmInteraction.tillAt(actionCol, actionRow);
            case "water"  -> farmInteraction.waterAt(actionCol, actionRow);
            case "untill" -> farmInteraction.untillAt(actionCol, actionRow);
            case "dig"    -> farmInteraction.digAt(actionCol, actionRow);
            case "chop"   -> farmInteraction.chopAt(actionCol, actionRow);
            default -> { } // unknown effect string: no-op
        }
    }

    @Override
    public void render(SpriteBatch batch) {
        // What's in hand decides whether to frame the flex target: a tool always frames it; a held seed
        // frames it only when it's plantable; an empty hand shows no frame.
        int dir = player.facing();
        int highlightCol = -1, highlightRow = -1;
        boolean seedInHand = itemRegistry.cropIndexFor(inventoryItems[selectedSlot]) >= 0;
        if (player.toolIndex() >= 1 || (seedInHand && farmInteraction.targetPlantable(dir))) {
            highlightCol = farmInteraction.targetCol(dir);
            highlightRow = farmInteraction.targetRow(dir);
        }
        worldRenderer.render(batch, gameCamera, player, toolActionSystem, highlightCol, highlightRow);
        // Dev hitbox overlay (F2): drawn over the world, under the HUD. 3px world units at zoom 1.
        if (Debug.showHitboxes) debugOverlay.render(batch, gameCamera, collisionResolver, player, 3f);
        inventoryHud.render(batch, gameCamera.hudCombined(), inventoryItems, inventoryAmounts, selectedSlot,
                            inventoryOpen, inventoryInteraction.draggedItemId(), inventoryInteraction.draggedAmount(),
                            inventoryInteraction.draggedSourceSlot(), inventoryInteraction.hoverSlot(),
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
        try { worldPersistence.saveOnExit(selectedSlot); } // synchronous: must finish before the JVM exits
        catch (Exception e) { Log.error("PlayState", "save on exit failed", e); }
        assets.dispose();        // frees every classpath texture (tilesets, player sheets, UI, crops, icons)
        inventoryHud.dispose();  // frees the procedurally-generated dim pixel
        debugOverlay.dispose();  // frees the overlay's 1x1 white pixel
    }
}
