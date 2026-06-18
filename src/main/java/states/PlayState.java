package states;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
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
import crops.PlantedCrop;
import crops.data.CropDataSource;
import crops.data.DefaultCropDataSource;
import crops.data.JsonCropDataSource;
import entities.Inventory;
import items.ItemRegistry;
import items.data.DefaultItemDataSource;
import items.data.ItemDataSource;
import items.data.JsonItemDataSource;
import physics.Body;
import physics.CollisionResolver;
import render.GameCamera;
import render.TextureSlicer;
import save.CropSave;
import save.SaveManager;
import save.WorldSave;
import tools.data.DefaultToolActionDataSource;
import tools.data.JsonToolActionDataSource;
import tools.data.ToolActionDataSource;
import tools.data.ToolActionBlueprint;
import world.DualGridSoil;
import world.GrassGenerator;
import world.TileLayer;

import static utilz.Constants.PlayerActionConstants.playerAnimationArrays;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private static final float PLAYER_SIZE = 240f;  // 32 * 5 * zoom(1.5)
    private static final float MOVE_SPEED = 270f;
    private static final float ANIM_FRAME_TIME = 16f / 144f;
    private static final int WALK_FRAMES = 9;

    private static final float HITBOX_OFF_X = 75f, HITBOX_OFF_Y = 195f;
    private static final float HITBOX_W = 90f, HITBOX_H = 48f;
    private static final float COLLISION_ERR = 0.45f; // passed to CollisionResolver (push past the edge)
    // Bush depth line. Tracks the collision box, which was raised by TILE/8 (see CollisionResolver),
    // so the player's resting position stays on the "draw in front" side and bushes don't paint over him.
    private static final float DEPTH_OFFSET = 82.5f; // 94.5 - TILE/8
    private static final long DEFAULT_STAGE_MILLIS = 3000L; // 5 stages -> ~12s to mature (tunable per crop)
    // Crop sprite/footprint geometry on a cell lives in CropGeometry (shared by collision, planting, draw).
    private final CropGeometry cropGeometry = new CropGeometry(TILE);

    private final GameCamera gameCamera; // world camera (follows the player) + screen-space HUD camera

    private final Texture grassTexture, plowedTexture, tallGrassTexture, wheatTexture, youngWheatTexture,
            playerTexture, chopTexture;
    private final Texture inventoryBarTexture, toggleTexture, fontTexture;
    private final TextureRegion[] grassRegions, soilRegions, collisionRegions;
    private final TextureRegion[][] playerFrames;
    private final TextureRegion inventoryBarRegion, toggleRegion;
    private final TextureRegion[] numberRegions; // 0..10, sliced from game_font (8x8)

    // Every classpath texture is owned by one lazy cache (load-on-first-use + dispose-all).
    private final Assets assets = new Assets();
    // Item HUD icons: their sliced regions are cached here; the underlying textures live in `assets`.
    private final Map<Integer, TextureRegion> itemIconRegions = new HashMap<>();

    // World grid (single source of truth for all per-cell state). Soil is a dual grid: gameplay reads
    // the `tilled` flags on `soil`; the visuals are derived on a half-tile-offset grid at render time.
    private final TileLayer grassLayer, collisionLayer;
    private final DualGridSoil soil;
    private final CollisionResolver collisionResolver;
    // Adapts the player to the collision Body interface: hitbox = player position + the fixed feet offset.
    private final Body playerBody = new Body() {
        public float hitboxX() { return playerX + HITBOX_OFF_X; }
        public float hitboxY() { return playerY + HITBOX_OFF_Y; }
        public float hitboxW() { return HITBOX_W; }
        public float hitboxH() { return HITBOX_H; }
        public void move(float dx, float dy) { playerX += dx; playerY += dy; }
    };
    private final int[] inventoryItems, inventoryAmounts;
    private int selectedSlot;
    private final Inventory inventory; // the live save object; its arrays are inventoryItems/inventoryAmounts

    // Crop subsystem.
    private final Texture cropsTexture;
    private final CropRegistry cropRegistry;
    private final ItemRegistry itemRegistry;
    private final CropField cropField;
    private final Texture whiteTexture;      // 1x1 white pixel for the tile highlight (handmade, no ShapeRenderer)
    private final TextureRegion whiteRegion;
    private int hoverCol = -1, hoverRow = -1; // tile under the mouse this frame
    private boolean hoverPlantable = false;   // hovered tile is tilled soil and empty

    // Data-driven tool Q-actions, indexed by toolIndex (null = no action for that tool).
    private final ToolAction[] toolActions = new ToolAction[playerAnimationArrays.length];
    private ToolAction activeAction = null; // non-null while an action animation plays
    private int actionDir = 2;              // captured facing for the active action
    private int actionFrame = 0;
    private float actionTimer = 0;
    private boolean effectDone = false;

    /** A tool's Q-action: per-direction frames, frame count, the frame it acts on the world
     *  (-1 = none) and what that effect is, and how big / where to draw (for oversized 128px swings). */
    private static final class ToolAction {
        final TextureRegion[][] frames; // [4 dirs: up,left,down,right][frameCount]
        final int frameCount, effectFrame;
        final String effect;            // world effect to fire at effectFrame ("till", or null/none)
        final float drawSize, offX, offY;
        ToolAction(TextureRegion[][] frames, int frameCount, int effectFrame, String effect,
                   float drawSize, float offX, float offY) {
            this.frames = frames; this.frameCount = frameCount; this.effectFrame = effectFrame;
            this.effect = effect; this.drawSize = drawSize; this.offX = offX; this.offY = offY;
        }
    }

    private float playerX, playerY;
    private int[] walkActions;
    private int toolIndex = 0;   // index into playerAnimationArrays (current tool)
    private int facing = 2;      // 0 up, 1 left, 2 down, 3 right
    private int currentAnimation; // the player animation row being shown (a WALK_* id into playerFrames)
    private int frameIndex = 0;
    private float animTimer = 0;

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
        inventoryBarTexture = loadTexture("ui/Inventory.png");
        toggleTexture       = loadTexture("ui/toggle.png");
        fontTexture         = loadTexture("ui/game_font.png");

        grassRegions    = TextureSlicer.slice(grassTexture, 1, 3);
        soilRegions     = TextureSlicer.slice(plowedTexture, 6, 3); // plowed_soil tiles 1..18, indexed by DualGridSoil
        collisionRegions = new TextureRegion[55];
        TextureRegion[] tg = TextureSlicer.slice(tallGrassTexture, 6, 3);
        TextureRegion[] wh = TextureSlicer.slice(wheatTexture, 6, 3);
        TextureRegion[] yw = TextureSlicer.slice(youngWheatTexture, 6, 3);
        for (int k = 1; k <= 18; k++) {
            collisionRegions[k]      = tg[k];
            collisionRegions[k + 18] = wh[k];
            collisionRegions[k + 36] = yw[k];
        }

        playerFrames = new TextureRegion[36][9];
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

        // Tool Q-actions are data-driven (data/tool_actions.json via libGDX Json), behind the same
        // swappable ToolActionDataSource seam as crops. The probe forces a parse now so a
        // missing/unparseable file falls back to the built-in catalog here, not mid-game.
        ToolActionDataSource toolData;
        try {
            ToolActionDataSource json = new JsonToolActionDataSource(Gdx.files.classpath("data/tool_actions.json"));
            json.loadToolActionBlueprints(); // probe
            toolData = json;
        } catch (Exception e) {
            toolData = new DefaultToolActionDataSource();
        }
        for (ToolActionBlueprint blueprint : toolData.loadToolActionBlueprints()) buildToolAction(blueprint);

        inventoryBarRegion = TextureSlicer.flippedFull(inventoryBarTexture);
        toggleRegion = TextureSlicer.flippedFull(toggleTexture);
        // game_font.png: 1 row of 11 number glyphs, 8x8 each.
        numberRegions = new TextureRegion[11];
        for (int k = 0; k < 11; k++) {
            TextureRegion r = new TextureRegion(fontTexture, k * 8, 0, 8, 8);
            r.flip(false, true);
            numberRegions[k] = r;
        }

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
        inventory = inv;
        selectedSlot = inv.getSelectedSlot();
        playerX = px;
        playerY = py;
        walkActions = (selected != null && selected.length == 4) ? selected : new int[]{32, 33, 34, 35};
        currentAnimation = (animation >= 0 && animation < 36) ? animation : 34;
        // Recover the tool index and facing from the saved state.
        for (int i = 0; i < playerAnimationArrays.length; i++) {
            if (Arrays.equals(playerAnimationArrays[i], walkActions)) { toolIndex = i; break; }
        }
        for (int i = 0; i < 4; i++) if (walkActions[i] == currentAnimation) { facing = i; break; }

        // Crop subsystem: catalog from crops.png, an empty field sized to the world, and a 1x1
        // white pixel for the hover highlight (kept on the main SpriteBatch — no ShapeRenderer).
        cropsTexture = loadTexture("tilesets/crops.png");
        // Crop blueprints come from data (data/crops.json via libGDX Json), behind a swappable
        // CropDataSource seam. If the file is missing/unparseable, fall back to the built-in catalog
        // so crops never break.
        CropDataSource cropData;
        try {
            CropDataSource json = new JsonCropDataSource(Gdx.files.classpath("data/crops.json"));
            json.loadCropBlueprints(); // probe: force a parse now so a bad file trips the fallback here
            cropData = json;
        } catch (Exception e) {
            cropData = new DefaultCropDataSource();
        }
        cropRegistry = new CropRegistry(cropsTexture, cropData.loadCropBlueprints(), DEFAULT_STAGE_MILLIS);
        // ItemDataSource seam (same probe-load + fallback pattern as crops/tools). The data file owns
        // the tools; crop seed/produce items are derived from the crop catalog so their names aren't
        // duplicated out of crops.json.
        ItemDataSource itemData;
        try {
            ItemDataSource json = new JsonItemDataSource(Gdx.files.classpath("data/items.json"));
            json.loadItemBlueprints(); // probe: force a parse now so a bad file trips the fallback here
            itemData = json;
        } catch (Exception e) {
            itemData = new DefaultItemDataSource();
        }
        CropType[] cropTypes = new CropType[cropRegistry.count()];
        for (int i = 0; i < cropTypes.length; i++) cropTypes[i] = cropRegistry.get(i);
        itemRegistry = new ItemRegistry(itemData.loadItemBlueprints(), cropTypes);
        cropField = new CropField(collisionLayer.cols(), collisionLayer.rows());
        collisionResolver = new CollisionResolver(collisionLayer, cropField, cropGeometry, TILE, COLLISION_ERR);
        if (sCrops != null) restoreCrops(sCrops);
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        whiteTexture = new Texture(pm);
        pm.dispose();
        whiteRegion = new TextureRegion(whiteTexture);

    }

    /** Repopulate the crop field from the crops read out of the save. */
    private void restoreCrops(CropSave[] crops) {
        for (CropSave s : crops) {
            CropType type = cropRegistry.get(s.type);
            if (type == null) continue; // a crop id that no longer exists in the catalog — drop it
            cropField.plant(s.col, s.row, type, s.planted);
            PlantedCrop c = cropField.at(s.col, s.row);
            if (c != null) c.harvested = s.harvested;
        }
    }

    /**
     * Persist the world to the save file via the {@link SaveManager}/{@link save.SaveFormat} seam (binary).
     * Builds a plain {@link WorldSave} snapshot — no engine objects, no Java serialization. Called on a
     * clean exit (see {@link #dispose}); a SIGKILL (e.g. the dev pkill-relaunch) skips it, which is fine
     * — only deliberate quits should save.
     */
    private void saveGame() {
        WorldSave ws = new WorldSave();
        ws.playerX = playerX;
        ws.playerY = playerY;
        ws.currentAnimation = currentAnimation;
        ws.walkActions = walkActions;
        ws.invItems = inventoryItems;       // shared live refs — already current
        ws.invAmounts = inventoryAmounts;
        ws.invToggle = selectedSlot;
        ws.collision = collisionLayer.data();
        ws.planting = soil.grid().data(); // tilled flags (0/1)
        ws.grass = grassLayer.data();

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
        float drawSize = blueprint.oversized ? PLAYER_SIZE * 2f : PLAYER_SIZE;
        float off = -(drawSize - PLAYER_SIZE) / 2f;
        toolActions[blueprint.toolIndex] =
                new ToolAction(frames, frameCount, blueprint.effectFrame, blueprint.effect, drawSize, off, off);
    }

    /** HUD icon for an item, loaded on demand (cached, null cached too so we don't retry). The path
     *  comes from the ItemRegistry (which applies the food/<id>.png convention for crop items). */
    private TextureRegion itemIcon(int id) {
        if (itemIconRegions.containsKey(id)) return itemIconRegions.get(id);
        TextureRegion region = null;
        String path = itemRegistry.iconPath(id);
        try {
            if (path == null) { itemIconRegions.put(id, null); return null; }
            Texture t = loadTexture(path); // owned + deduped by the Assets cache
            region = new TextureRegion(t);
            region.flip(false, true);
        } catch (Exception ignored) {
        }
        itemIconRegions.put(id, region);
        return region;
    }

    /** Would the player's feet hitbox collide if its top-left were at (px,py)? Maps to the world resolver. */
    private boolean collidesAt(float px, float py) {
        return collisionResolver.collidesAt(px + HITBOX_OFF_X, py + HITBOX_OFF_Y, HITBOX_W, HITBOX_H);
    }

    /** Would a crop planted in cell (col,row) immediately overlap the player where they stand now? */
    private boolean playerOverlapsCropCell(int col, int row) {
        return CollisionResolver.overlaps(
                playerX + HITBOX_OFF_X, playerY + HITBOX_OFF_Y, HITBOX_W, HITBOX_H,
                cropGeometry.footprintX(col), cropGeometry.footprintY(row),
                cropGeometry.footprintWidth, cropGeometry.footprintHeight);
    }

    private void drawCollision(SpriteBatch batch, boolean inFront) {
        float feetY = playerY + HITBOX_OFF_Y;
        for (int j = 0; j < collisionLayer.cols(); j++) {
            for (int i = 0; i < collisionLayer.rows(); i++) {
                int v = collisionLayer.get(j, i);
                if (v < 1 || v >= collisionRegions.length || collisionRegions[v] == null) continue;
                float threshold = i * TILE + DEPTH_OFFSET;
                boolean drawIt = inFront ? (feetY <= threshold) : (feetY >= threshold);
                if (drawIt) batch.draw(collisionRegions[v], j * TILE, i * TILE, TILE, TILE);
            }
        }
    }

    private void drawArray(SpriteBatch batch, TileLayer layer, TextureRegion[] regions) {
        for (int j = 0; j < layer.cols(); j++) {
            for (int i = 0; i < layer.rows(); i++) {
                int v = layer.get(j, i);
                if (v >= 1 && v < regions.length && regions[v] != null) {
                    batch.draw(regions[v], j * TILE, i * TILE, TILE, TILE);
                }
            }
        }
    }

    private void drawHud(SpriteBatch batch) {
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        float invW = 1200, invH = invW / 10f; // 120
        float invX = w / 2f - invW / 2f, invY = h * 7f / 8f;
        float slot = invW / 10f;

        batch.setProjectionMatrix(gameCamera.hudCombined());
        batch.begin();
        batch.draw(inventoryBarRegion, invX, invY, invW, invH);
        batch.draw(toggleRegion, invX + selectedSlot * slot, invY, slot, invH);
        for (int i = 0; i < inventoryItems.length; i++) {
            if (inventoryItems[i] != 0) {
                TextureRegion icon = itemIcon(inventoryItems[i]);
                if (icon != null) {
                    batch.draw(icon, invX + i * slot + w / 120f, invY + h / 80f, invW / 15f, invH / 1.5f);
                }
                // Draw the amount digit by digit (the font row is "-123456789 0 -", so digit d is at
                // glyph index d, except 0 which lives at index 10). This shows 10+ instead of a lone "0".
                // Each glyph cell is drawn glyphW wide but the digit only fills its middle ~50%, so advance
                // by ~0.6*glyphW: the cells overlap only in their transparent padding, giving tight digits.
                String digits = Integer.toString(Math.max(0, inventoryAmounts[i]));
                float glyphW = invW / 50f, adv = glyphW * 0.6f;
                float numX = invX + i * slot + w / 32f, numY = invY + h / 18f;
                // Right-align: the ones digit stays anchored at numX, extra digits grow to the left, so
                // the ones place lines up whether the amount is 1 or 2+ digits (9 -> 10 doesn't shift).
                for (int d = 0; d < digits.length(); d++) {
                    int digit = digits.charAt(d) - '0';
                    float x = numX - (digits.length() - 1 - d) * adv;
                    batch.draw(numberRegions[digit == 0 ? 10 : digit], x, numY, glyphW, invH / 7f);
                }
            }
        }
        batch.end();
    }

    @Override
    public void update(float delta) {
        if (Gdx.input.isKeyPressed(Input.Keys.ESCAPE)) Gdx.app.exit();

        // A tool Q-action is playing out: animate it, no movement.
        if (activeAction != null) {
            actionTimer += delta;
            if (actionTimer >= ANIM_FRAME_TIME) {
                actionTimer -= ANIM_FRAME_TIME;
                actionFrame++;
                if (actionFrame == activeAction.effectFrame && !effectDone) {
                    applyActionEffect(activeAction.effect, actionDir);
                    effectDone = true;
                }
                if (actionFrame >= activeAction.frameCount) activeAction = null;
            }
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
            boolean xBlocked = collidesAt(playerX + ix * d, playerY);
            boolean yBlocked = collidesAt(playerX, playerY + iy * d);
            if (xBlocked ^ yBlocked) {
                if (xBlocked) playerY += iy * d;
                else playerX += ix * d;
            } else {
                float inv = 0.70710678f;
                playerX += ix * d * inv;
                playerY += iy * d * inv;
            }
        } else {
            playerX += ix * d;
            playerY += iy * d;
        }

        collisionResolver.resolve(playerBody);

        // Inventory selector (1/3) and tool cycling (2) — discrete presses.
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) selectedSlot = Math.max(0, selectedSlot - 1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) selectedSlot = Math.min(9, selectedSlot + 1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) {
            toolIndex = (toolIndex + 1) % playerAnimationArrays.length;
            walkActions = playerAnimationArrays[toolIndex];
        }

        // Facing follows ACTUAL movement, so opposing keys (which cancel an axis) keep the
        // current facing instead of flipping. Horizontal wins over vertical on diagonals.
        if (iy < 0) facing = 0; // up
        if (iy > 0) facing = 2; // down
        if (ix < 0) facing = 1; // left
        if (ix > 0) facing = 3; // right
        currentAnimation = walkActions[facing];

        // Q: trigger the current tool's Q-action animation (and its world effect, if any).
        if (Gdx.input.isKeyJustPressed(Input.Keys.Q) && toolActions[toolIndex] != null) {
            activeAction = toolActions[toolIndex];
            actionDir = facing;
            actionFrame = 0;
            actionTimer = 0;
            effectDone = false;
            updateCamera();
            return;
        }

        boolean moving = ix != 0 || iy != 0;
        if (moving) {
            if (frameIndex == 0) frameIndex = 1;
            animTimer += delta;
            if (animTimer >= ANIM_FRAME_TIME) {
                animTimer -= ANIM_FRAME_TIME;
                frameIndex++;
                if (frameIndex >= WALK_FRAMES) frameIndex = 1;
            }
        } else {
            frameIndex = 0;
            animTimer = 0;
        }

        updateHover();
        // Left-click a plantable tile to sow the selected seed; E harvests a mature crop under the mouse.
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT) && hoverPlantable) plantAtHover();
        if (Gdx.input.isKeyJustPressed(Input.Keys.E)) harvestAtHover();

        updateCamera();
    }

    /** Resolve the cell under the mouse. A cell is plantable when it's tilled soil, empty, and planting
     *  there wouldn't immediately overlap the player. */
    private void updateHover() {
        var mouseWorld = gameCamera.unproject(Gdx.input.getX(), Gdx.input.getY());
        hoverCol = soil.grid().colAtWorldX(mouseWorld.x);
        hoverRow = soil.grid().rowAtWorldY(mouseWorld.y);
        hoverPlantable = soil.isTilled(hoverCol, hoverRow) && cropField.isOpen(hoverCol, hoverRow)
                     && !playerOverlapsCropCell(hoverCol, hoverRow);
    }

    /** Plant the selected seed (an inventory crop item) at the hovered tile, consuming one seed. */
    private void plantAtHover() {
        CropType seed = cropRegistry.get(itemRegistry.cropIndexFor(inventoryItems[selectedSlot]));
        if (seed == null || inventoryAmounts[selectedSlot] <= 0) return; // no seed of the selected crop in hand
        cropField.plant(hoverCol, hoverRow, seed, System.currentTimeMillis());
        if (--inventoryAmounts[selectedSlot] <= 0) {
            inventoryAmounts[selectedSlot] = 0;
            inventoryItems[selectedSlot] = 0;
        }
    }

    /** Harvest the hovered crop if it's mature: bank the produce and leave a hole that can be replanted. */
    private void harvestAtHover() {
        PlantedCrop crop = cropField.at(hoverCol, hoverRow);
        if (crop == null || !crop.isMature(System.currentTimeMillis())) return;
        addInventory(crop.type.itemId, 1);
        crop.harvested = true; // now renders the hole frame; node is open for replanting
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

    private void updateCamera() {
        gameCamera.follow(playerX + PLAYER_SIZE / 2f, playerY + PLAYER_SIZE / 2f);
    }

    /** Apply a tool action's world effect at its effect frame. Only tilling has one today; new
     *  effects (watering, digging, ...) slot in here keyed by the blueprint's {@code effect} string. */
    private void applyActionEffect(String effect, int dir) {
        if ("till".equals(effect)) performTill(dir);
    }

    /** Till the cell one step in front of the player, which the dual grid renders as a clean 4-tile
     *  square. "In front" = the player's standing (hitbox) cell, offset one tile in the facing direction. */
    private void performTill(int dir) {
        int col = soil.grid().colAtWorldX(playerX + HITBOX_OFF_X + HITBOX_W / 2f);
        int row = soil.grid().rowAtWorldY(playerY + HITBOX_OFF_Y + HITBOX_H / 2f);
        switch (dir) { // 0 up, 1 left, 2 down, 3 right (Y-down: up = -row)
            case 0 -> row -= 1;
            case 1 -> col -= 1;
            case 2 -> row += 1;
            case 3 -> col += 1;
        }
        if (!soil.grid().inBounds(col, row) || collisionResolver.isSolid(col, row)) return;
        soil.till(col, row);
    }

    private void drawPlayer(SpriteBatch batch) {
        if (activeAction != null) {
            TextureRegion frame = activeAction.frames[actionDir][Math.min(actionFrame, activeAction.frameCount - 1)];
            batch.draw(frame, playerX + activeAction.offX, playerY + activeAction.offY,
                       activeAction.drawSize, activeAction.drawSize);
        } else {
            batch.draw(playerFrames[currentAnimation][frameIndex], playerX, playerY, PLAYER_SIZE, PLAYER_SIZE);
        }
    }

    @Override
    public void render(SpriteBatch batch) {
        batch.setProjectionMatrix(gameCamera.worldCombined());
        batch.begin();
        drawArray(batch, grassLayer, grassRegions);
        soil.render(batch, soilRegions);
        drawHighlight(batch);
        drawCrops(batch, false);     // crops rooted above the player -> behind
        drawCollision(batch, false); // bushes behind
        drawPlayer(batch);
        drawCollision(batch, true);  // bushes in front
        drawCrops(batch, true);      // crops rooted at/below the player -> in front
        batch.end();

        drawHud(batch);
    }

    /** Highlight the hovered tile: green when plantable, yellow when it holds a mature (harvestable) crop. */
    private void drawHighlight(SpriteBatch batch) {
        if (hoverCol < 0 || hoverRow < 0) return;
        if (hoverPlantable) {
            batch.setColor(0.3f, 1f, 0.3f, 0.35f);
        } else {
            PlantedCrop crop = cropField.at(hoverCol, hoverRow);
            if (crop == null || !crop.isMature(System.currentTimeMillis())) return;
            batch.setColor(1f, 0.95f, 0.3f, 0.35f);
        }
        // A tile-sized highlight over the hovered cell.
        batch.draw(whiteRegion, hoverCol * TILE, hoverRow * TILE, TILE, TILE);
        batch.setColor(Color.WHITE);
    }

    /** Draw planted crops at their current growth stage, depth-split around the player like the bushes. */
    private void drawCrops(SpriteBatch batch, boolean inFront) {
        long now = System.currentTimeMillis();
        float feetY = playerY + HITBOX_OFF_Y;
        for (int j = 0; j < cropField.cols(); j++) {
            for (int i = 0; i < cropField.rows(); i++) {
                PlantedCrop crop = cropField.at(j, i);
                if (crop == null) continue;
                // Centered on the cell, with the root anchor (cell center) sitting 1/4 tile above the
                // sprite's bottom. The post-harvest hole frame sits an extra 1/8 tile lower to line up.
                float y = cropGeometry.spriteTopY(i) + (crop.harvested ? TILE / 8f : 0f);
                // Depth ground-line: the player goes in front of the crop once their feet pass ~2/3 down
                // the 2-tile-tall sprite. This is the old CropsLayer mask (feetY <= column*TILE + 100*zoom).
                float threshold = cropGeometry.groundLineY(i);
                boolean drawIt = inFront ? (feetY <= threshold) : (feetY >= threshold);
                if (!drawIt) continue;
                TextureRegion r = crop.type.stage(crop.stage(now));
                batch.draw(r, cropGeometry.spriteX(j), y, cropGeometry.spriteWidth, cropGeometry.spriteHeight);
            }
        }
    }

    @Override
    public void resize(int width, int height) {
        gameCamera.resize(width, height);
    }

    @Override
    public void dispose() {
        // Persist first, but never let a save error block resource cleanup on shutdown.
        try { saveGame(); } catch (Exception e) { e.printStackTrace(); }
        assets.dispose();        // frees every classpath texture (tilesets, player sheets, UI, crops, icons)
        whiteTexture.dispose();  // the one procedurally-generated texture, not owned by the cache
    }
}
