package states;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import core.Assets;
import core.GameState;
import core.GameStateManager;
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
import tools.data.DefaultToolActionDataSource;
import tools.data.JsonToolActionDataSource;
import tools.data.ToolActionDataSource;
import tools.data.ToolActionBlueprint;
import world.DualGridSoil;
import world.GrassGenerator;
import world.TileLayer;

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
    private static final float TILE = 96f;          // 64 * zoom(1.5)
    private static final float MOVE_SPEED = 270f;
    private static final float ANIM_FRAME_TIME = 16f / 144f; // tool-action animation cadence
    private static final float COLLISION_ERR = 0.45f; // passed to CollisionResolver (push past the edge)
    private static final long DEFAULT_STAGE_MILLIS = 3000L; // 5 stages -> ~12s to mature (tunable per crop)
    // Crop sprite/footprint geometry on a cell lives in CropGeometry (shared by collision, planting, draw).
    private final CropGeometry cropGeometry = new CropGeometry(TILE);

    private final GameCamera gameCamera; // world camera (follows the player) + screen-space HUD camera

    private final Texture grassTexture, plowedTexture, tallGrassTexture, wheatTexture, youngWheatTexture,
            playerTexture, chopTexture;
    private final InventoryHud inventoryHud; // screen-space inventory bar (owns its regions + icon cache)
    private final WorldRenderer worldRenderer; // grass/soil/crops/bushes/player depth-sorted draws

    // Every classpath texture is owned by one lazy cache (load-on-first-use + dispose-all).
    private final Assets assets = new Assets();

    // World grid (single source of truth for all per-cell state). Soil is a dual grid: gameplay reads
    // the `tilled` flags on `soil`; the visuals are derived on a half-tile-offset grid at render time.
    private final TileLayer grassLayer, collisionLayer;
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
        for (ToolActionBlueprint blueprint : ProbeLoader.loadOrFallback(
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
            sGrass = GrassGenerator.randomGrass(400, 220, TILE).data();
            sPlanting = new int[40][22];
            sCollision = new int[40][22];
            sInvItems = null;
            sInvAmounts = null;
            sSelectedSlot = 0;
            px = 2000;
            py = 800;
            animation = 34;
            selected = new int[]{32, 33, 34, 35};
            sCrops = null;
        }
        collisionLayer = new TileLayer(sCollision, TILE);
        grassLayer = new TileLayer(sGrass, TILE);
        // Soil is a per-cell tilled flag. Normalize any legacy save (old autotile tile-indices) to 0/1
        // so the array now means "tilled?" — the dual grid derives the actual tiles at render time.
        for (int[] column : sPlanting)
            for (int r = 0; r < column.length; r++) column[r] = (column[r] != 0) ? 1 : 0;
        soil = new DualGridSoil(new TileLayer(sPlanting, TILE));
        // Rebuild the live Inventory from saved primitives (it's no longer serialized directly).
        Inventory inv = new Inventory(sInvItems != null ? sInvItems.length : 10);
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
        cropRegistry = new CropRegistry(cropsTexture, ProbeLoader.loadOrFallback(
                () -> new JsonCropDataSource(Gdx.files.classpath("data/crops.json")),
                CropDataSource::loadCropBlueprints,
                DefaultCropDataSource::new), DEFAULT_STAGE_MILLIS);
        // Items: same probe-load + fallback. The data file owns the tools; crop seed/produce items are
        // derived from the crop catalog so their names aren't duplicated out of crops.json.
        CropType[] cropTypes = new CropType[cropRegistry.count()];
        for (int i = 0; i < cropTypes.length; i++) cropTypes[i] = cropRegistry.get(i);
        itemRegistry = new ItemRegistry(ProbeLoader.loadOrFallback(
                () -> new JsonItemDataSource(Gdx.files.classpath("data/items.json")),
                ItemDataSource::loadItemBlueprints,
                DefaultItemDataSource::new), cropTypes);
        inventoryHud = new InventoryHud(inventoryBarTexture, toggleTexture, fontTexture, itemRegistry, assets);
        cropField = new CropField(collisionLayer.cols(), collisionLayer.rows());
        collisionResolver = new CollisionResolver(collisionLayer, cropField, cropGeometry, TILE, COLLISION_ERR);
        worldPersistence = new WorldPersistence(collisionLayer, grassLayer, soil, cropField, player,
                inventoryItems, inventoryAmounts, cropRegistry);
        worldPersistence.restoreCrops(sCrops);
        worldRenderer = new WorldRenderer(grassLayer, soil, collisionLayer, cropField, cropGeometry, TILE,
                grassRegions, soilRegions, collisionRegions);
        farmInteraction = new FarmInteraction(soil, cropField, cropRegistry, itemRegistry, cropGeometry,
                collisionResolver, gameCamera, player, inventoryItems, inventoryAmounts);
    }

    /** Load a classpath texture with Nearest filtering (crisp pixel art, no color softening). */
    private Texture loadTexture(String path) {
        return assets.get(path); // lazy load + cache + dispose all handled by Assets
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

    /** Would the player's feet hitbox collide if shifted by (dx,dy) from where it is now? */
    private boolean wouldCollide(float dx, float dy) {
        return collisionResolver.collidesAt(player.hitboxX() + dx, player.hitboxY() + dy,
                                            player.hitboxW(), player.hitboxH());
    }

    @Override
    public void update(float delta) {
        if (Gdx.input.isKeyPressed(Input.Keys.ESCAPE)) Gdx.app.exit();

        // A tool Q-action is playing out: animate it (firing its effect at the effect frame), no movement.
        if (toolActionSystem.isActive()) {
            toolActionSystem.update(delta);
            updateCamera();
            return;
        }

        boolean up    = Gdx.input.isKeyPressed(Input.Keys.UP)    || Gdx.input.isKeyPressed(Input.Keys.W);
        boolean down  = Gdx.input.isKeyPressed(Input.Keys.DOWN)  || Gdx.input.isKeyPressed(Input.Keys.S);
        boolean left  = Gdx.input.isKeyPressed(Input.Keys.LEFT)  || Gdx.input.isKeyPressed(Input.Keys.A);
        boolean right = Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D);

        float d = MOVE_SPEED * delta;
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

        // Inventory selector (1/3) and tool cycling (2) — discrete presses.
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) selectedSlot = Math.max(0, selectedSlot - 1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) selectedSlot = Math.min(9, selectedSlot + 1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) player.cycleTool();

        // Facing follows ACTUAL movement: opposing keys keep the current facing; horizontal wins diagonals.
        player.setFacingFromMotion(ix, iy);

        // Q: trigger the current tool's Q-action animation (and its world effect, if any).
        if (Gdx.input.isKeyJustPressed(Input.Keys.Q)) {
            toolActionSystem.trigger(player.toolIndex(), player.facing());
            if (toolActionSystem.isActive()) {
                updateCamera();
                return;
            }
        }

        player.stepWalk(delta, ix != 0 || iy != 0);

        farmInteraction.updateHover();
        // Left-click a plantable tile to sow the selected seed; E harvests a mature crop under the mouse.
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT) && farmInteraction.hoverPlantable())
            farmInteraction.plantAtHover(selectedSlot);
        if (Gdx.input.isKeyJustPressed(Input.Keys.E)) farmInteraction.harvestAtHover();

        updateCamera();
    }

    private void updateCamera() {
        gameCamera.follow(player.centerX(), player.centerY());
    }

    /** Apply a tool action's world effect at its effect frame. Only tilling has one today; new
     *  effects (watering, digging, ...) slot in here keyed by the blueprint's {@code effect} string. */
    private void applyActionEffect(String effect, int dir) {
        if ("till".equals(effect)) farmInteraction.performTill(dir);
    }

    @Override
    public void render(SpriteBatch batch) {
        worldRenderer.render(batch, gameCamera.worldCombined(), player, toolActionSystem,
                             farmInteraction.hoverCol(), farmInteraction.hoverRow(),
                             farmInteraction.hoverPlantable());
        inventoryHud.render(batch, gameCamera.hudCombined(), inventoryItems, inventoryAmounts, selectedSlot);
    }

    @Override
    public void resize(int width, int height) {
        gameCamera.resize(width, height);
    }

    @Override
    public void dispose() {
        // Save on a clean exit (explicit now — no longer hidden inside a lifecycle method). Never let a
        // save error block resource cleanup on shutdown.
        try { worldPersistence.save(selectedSlot); } catch (Exception e) { e.printStackTrace(); }
        assets.dispose();        // frees every classpath texture (tilesets, player sheets, UI, crops, icons)
        worldRenderer.dispose(); // frees the procedurally-generated highlight texture (not in the cache)
    }
}
