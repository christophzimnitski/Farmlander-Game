package states;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector3;
import core.GameState;
import core.GameStateManager;
import crops.CropField;
import crops.CropRegistry;
import crops.CropType;
import crops.PlantedCrop;
import crops.data.CropDataSource;
import crops.data.DefaultCropDataSource;
import crops.data.JsonCropDataSource;
import entities.Inventory;
import levels.GrassLayer;
import levels.LevelManager;
import levels.PlantingLayer;
import tools.data.DefaultToolActionDataSource;
import tools.data.JsonToolActionDataSource;
import tools.data.ToolActionDataSource;
import tools.data.ToolActionDef;

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
    private static final float COLLISION_ERR = 0.45f;
    private static final int[] NON_COLLIDABLE = {7, 8, 9, 25, 26, 27, 43, 44, 45};
    // Bush depth line. Tracks the collision box, which was raised by TILE/8 (see collidesAt/resolveBox),
    // so the player's resting position stays on the "draw in front" side and bushes don't paint over him.
    private static final float DEPTH_OFFSET = 82.5f; // 94.5 - TILE/8
    // Crops draw TILE wide x 2*TILE tall, centered on a plantable node — the shared corner of a 2x2
    // block of tilled tiles. The plant's base sits at the node; foliage rises into the tiles above.
    private static final float CROP_W = TILE, CROP_H = TILE * 2f;
    private static final long DEFAULT_STAGE_MILLIS = 3000L; // 5 stages -> ~12s to mature (tunable per crop)
    // A planted crop blocks the player with a small solid footprint centered on its base (the node corner).
    private static final float CROP_COLL_W = 48f, CROP_COLL_H = 24f; // narrower L/R, ~1/4 tile shorter T/B

    private final OrthographicCamera camera;
    private final OrthographicCamera hudCam; // screen-space HUD, Y-down like the world

    private final Texture grassTex, plowedTex, tallGrassTex, wheatTex, youngWheatTex, playerTex, chopTex;
    private final Texture invBarTex, toggleTex, fontTex;
    private final TextureRegion[] grassRegions, plantingRegions, collisionRegions;
    private final TextureRegion[][] playerFrames;
    private final TextureRegion invBarRegion, toggleRegion;
    private final TextureRegion[] numberRegions; // 0..10, sliced from game_font (8x8)

    // Food sprites are individual PNGs loaded on demand and cached.
    private final Map<Integer, TextureRegion> foodRegions = new HashMap<>();
    private final List<Texture> foodTextures = new ArrayList<>();

    private final int[][] grass, planting, collision;
    private final PlantingLayer plantingLayer; // reused for its editLayer/autotile logic
    private final int[] invItems, invAmounts;
    private int invToggle;
    private final Inventory inventory; // the live save object; its item/amount arrays are invItems/invAmounts

    // Crop subsystem.
    private final Texture cropsTex;
    private final CropRegistry cropRegistry;
    private final CropField cropField;
    private final Texture whiteTex;          // 1x1 white pixel for the tile highlight (handmade, no ShapeRenderer)
    private final TextureRegion whiteRegion;
    private int hovCol = -1, hovRow = -1;    // tile under the mouse this frame
    private boolean hovPlantable = false;    // hovered tile is tilled soil and empty
    private final Vector3 mouseWorld = new Vector3();

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
    private int currentAction;
    private int frameIndex = 0;
    private float animTimer = 0;

    public PlayState(GameStateManager gsm) {
        super(gsm);

        int w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        camera = new OrthographicCamera();
        camera.setToOrtho(true, w, h); // Y-down to match Swing
        hudCam = new OrthographicCamera();
        hudCam.setToOrtho(true, w, h);

        grassTex      = loadTex("tilesets/grassTileset.png");
        plowedTex     = loadTex("tilesets/plowed_soil.png");
        tallGrassTex  = loadTex("tilesets/tallgrass.png");
        wheatTex      = loadTex("tilesets/wheat.png");
        youngWheatTex = loadTex("tilesets/youngwheat.png");
        playerTex     = loadTex("player_animations/player_mining.png");
        chopTex       = loadTex("player_animations/player_chopping.png");
        invBarTex     = loadTex("ui/Inventory.png");
        toggleTex     = loadTex("ui/toggle.png");
        fontTex       = loadTex("ui/game_font.png");

        grassRegions    = slice(grassTex, 1, 3);
        plantingRegions = slice(plowedTex, 6, 3);
        collisionRegions = new TextureRegion[55];
        TextureRegion[] tg = slice(tallGrassTex, 6, 3);
        TextureRegion[] wh = slice(wheatTex, 6, 3);
        TextureRegion[] yw = slice(youngWheatTex, 6, 3);
        for (int k = 1; k <= 18; k++) {
            collisionRegions[k]      = tg[k];
            collisionRegions[k + 18] = wh[k];
            collisionRegions[k + 36] = yw[k];
        }

        playerFrames = new TextureRegion[36][9];
        float tw = playerTex.getWidth(), th = playerTex.getHeight();
        float pad = 0.5f;
        for (int row = 0; row < 36; row++) {
            for (int col = 0; col < 9; col++) {
                TextureRegion r = new TextureRegion(playerTex);
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
            json.loadToolActions(); // probe
            toolData = json;
        } catch (Exception e) {
            toolData = new DefaultToolActionDataSource();
        }
        for (ToolActionDef def : toolData.loadToolActions()) buildToolAction(def);

        invBarRegion = flippedFull(invBarTex);
        toggleRegion = flippedFull(toggleTex);
        // game_font.png: 1 row of 11 number glyphs, 8x8 each.
        numberRegions = new TextureRegion[11];
        for (int k = 0; k < 11; k++) {
            TextureRegion r = new TextureRegion(fontTex, k * 8, 0, 8, 8);
            r.flip(false, true);
            numberRegions[k] = r;
        }

        int[][] sCollision, sPlanting, sGrass;
        float px = 2000, py = 800;
        int action = 34;
        int[] selected = {32, 33, 34, 35};
        Inventory inv;
        // New crop field is persisted at indices [10..14] as parallel primitive arrays (null when the
        // save predates crop persistence — old saves stop at index 9).
        int[] sCropCols = null, sCropRows = null, sCropTypes = null;
        long[] sCropPlanted = null;
        boolean[] sCropHarvested = null;
        try {
            List<Object> save = LevelManager.loadObjects(LevelManager.SAVE_FILE);
            sCollision = (int[][]) save.get(0);
            sPlanting  = (int[][]) save.get(1);
            sGrass     = (int[][]) save.get(2);
            inv = (Inventory) save.get(5);
            px = (Float) save.get(6);
            py = (Float) save.get(7);
            selected = (int[]) save.get(8);
            action = (Integer) save.get(9);
            if (save.size() >= 15) {
                sCropCols      = (int[]) save.get(10);
                sCropRows      = (int[]) save.get(11);
                sCropTypes     = (int[]) save.get(12);
                sCropPlanted   = (long[]) save.get(13);
                sCropHarvested = (boolean[]) save.get(14);
            }
        } catch (Exception e) {
            GrassLayer fallback = new GrassLayer();
            fallback.editLayer();
            sGrass = fallback.getLayerArray();
            sPlanting = new int[40][22];
            sCollision = new int[40][22];
            inv = new Inventory(10);
        }
        collision = sCollision;
        plantingLayer = new PlantingLayer(); // editLayer/formatLayer drive the tilling autotile logic
        plantingLayer.setLayerArray(sPlanting);
        planting = plantingLayer.getLayerArray();
        grass = sGrass;
        inventory = inv;
        invItems = inv.getInventory();      // shared refs: mutating these mutates the saved Inventory
        invAmounts = inv.getInventoryAmounts();
        invToggle = inv.getToggle();
        playerX = px;
        playerY = py;
        walkActions = (selected != null && selected.length == 4) ? selected : new int[]{32, 33, 34, 35};
        currentAction = (action >= 0 && action < 36) ? action : 34;
        // Recover the tool index and facing from the saved state.
        for (int i = 0; i < playerAnimationArrays.length; i++) {
            if (Arrays.equals(playerAnimationArrays[i], walkActions)) { toolIndex = i; break; }
        }
        for (int i = 0; i < 4; i++) if (walkActions[i] == currentAction) { facing = i; break; }

        // Crop subsystem: catalog from crops.png, an empty field sized to the world, and a 1x1
        // white pixel for the hover highlight (kept on the main SpriteBatch — no ShapeRenderer).
        cropsTex = loadTex("tilesets/crops.png");
        // Crop definitions come from data (data/crops.json via libGDX Json), behind a swappable
        // CropDataSource seam. If the file is missing/unparseable, fall back to the built-in catalog
        // so crops never break.
        CropDataSource cropData;
        try {
            CropDataSource json = new JsonCropDataSource(Gdx.files.classpath("data/crops.json"));
            json.loadCrops(); // probe: force a parse now so a bad file trips the fallback here
            cropData = json;
        } catch (Exception e) {
            cropData = new DefaultCropDataSource();
        }
        cropRegistry = new CropRegistry(cropsTex, cropData.loadCrops(), DEFAULT_STAGE_MILLIS);
        cropField = new CropField(collision.length, collision[0].length);
        if (sCropCols != null) restoreCrops(sCropCols, sCropRows, sCropTypes, sCropPlanted, sCropHarvested);
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        whiteTex = new Texture(pm);
        pm.dispose();
        whiteRegion = new TextureRegion(whiteTex);

    }

    /** Repopulate the crop field from the parallel arrays read out of the save. */
    private void restoreCrops(int[] cols, int[] rows, int[] types, long[] planted, boolean[] harvested) {
        int n = Math.min(Math.min(cols.length, rows.length),
                         Math.min(types.length, Math.min(planted.length, harvested.length)));
        for (int k = 0; k < n; k++) {
            CropType type = cropRegistry.get(types[k]);
            if (type == null) continue; // a crop id that no longer exists in the catalog — drop it
            cropField.plant(cols[k], rows[k], type, planted[k]);
            PlantedCrop c = cropField.at(cols[k], rows[k]);
            if (c != null) c.harvested = harvested[k];
        }
    }

    /**
     * Persist the world back to the save file in the same positional format the loader reads.
     * Indices [0..9] match the Swing layout (so a save still round-trips); [10..14] are the new crop
     * field as parallel primitive arrays. Called on a clean exit (see {@link #dispose}); a SIGKILL
     * (e.g. the dev pkill-relaunch) skips it, which is fine — only deliberate quits should save.
     */
    private void saveGame() {
        // Fold the live toggle selection back into the Inventory object (item/amount arrays are shared
        // refs, already current). toggle() applies a delta and clamps, landing exactly on invToggle.
        inventory.toggle(invToggle - inventory.getToggle());

        // Flatten the crop grid into parallel primitive arrays (serialization-safe — no PlantedCrop or
        // CropType, which hold non-serializable TextureRegions).
        int count = 0;
        for (int j = 0; j < cropField.cols(); j++)
            for (int i = 0; i < cropField.rows(); i++)
                if (cropField.at(j, i) != null) count++;
        int[] cCols = new int[count], cRows = new int[count], cTypes = new int[count];
        long[] cPlanted = new long[count];
        boolean[] cHarvested = new boolean[count];
        int k = 0;
        for (int j = 0; j < cropField.cols(); j++)
            for (int i = 0; i < cropField.rows(); i++) {
                PlantedCrop c = cropField.at(j, i);
                if (c == null) continue;
                cCols[k] = j; cRows[k] = i; cTypes[k] = c.type.index;
                cPlanted[k] = c.plantedAt; cHarvested[k] = c.harvested; k++;
            }

        int rows = collision.length == 0 ? 0 : collision[0].length;
        List<Object> objs = new ArrayList<>();
        objs.add(collision);                          // [0]
        objs.add(planting);                           // [1]
        objs.add(grass);                              // [2]
        objs.add(new int[collision.length][rows]);    // [3] old crop format (dead) — zeros to hold the slot
        objs.add(new int[collision.length][rows]);    // [4] old timesLeft (dead)
        objs.add(inventory);                          // [5]
        objs.add(playerX);                            // [6] Float
        objs.add(playerY);                            // [7] Float
        objs.add(walkActions);                        // [8] selected animation array
        objs.add(currentAction);                      // [9] Integer
        objs.add(cCols);                              // [10] crop columns
        objs.add(cRows);                              // [11] crop rows
        objs.add(cTypes);                             // [12] crop type indices
        objs.add(cPlanted);                           // [13] crop plant timestamps
        objs.add(cHarvested);                         // [14] crop harvested flags
        LevelManager.saveObjects(objs, LevelManager.SAVE_FILE);
    }

    /** Set a slot to at least {@code amount} of an item (existing slot, else first empty). DEV/testing aid. */
    private void giveItem(int itemId, int amount) {
        for (int i = 0; i < invItems.length; i++) {
            if (invItems[i] == itemId) { invAmounts[i] = Math.max(invAmounts[i], amount); return; }
        }
        for (int i = 0; i < invItems.length; i++) {
            if (invItems[i] == 0) { invItems[i] = itemId; invAmounts[i] = amount; return; }
        }
    }

    /** Load a classpath texture with Nearest filtering (crisp pixel art, no color softening). */
    private Texture loadTex(String path) {
        Texture t = new Texture(Gdx.files.classpath(path));
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return t;
    }

    private TextureRegion flippedFull(Texture tex) {
        TextureRegion r = new TextureRegion(tex);
        r.flip(false, true);
        return r;
    }

    private TextureRegion[] slice(Texture tex, int rows, int cols) {
        TextureRegion[] regions = new TextureRegion[rows * cols + 1];
        int k = 1;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                TextureRegion r = new TextureRegion(tex, col * 32, row * 32, 32, 32);
                r.flip(false, true);
                regions[k++] = r;
            }
        }
        return regions;
    }

    /** Slice 64px action frames from a sheet: frames[dir][f] for the given direction rows (Y-flipped, inset). */
    private TextureRegion[][] sliceActionRows(Texture tex, int[] dirRows, int frameCount) {
        float tw = tex.getWidth(), th = tex.getHeight(), pad = 0.5f;
        TextureRegion[][] out = new TextureRegion[dirRows.length][frameCount];
        for (int dir = 0; dir < dirRows.length; dir++) {
            int row = dirRows[dir];
            for (int f = 0; f < frameCount; f++) {
                TextureRegion r = new TextureRegion(tex);
                r.setRegion((f * 64 + pad) / tw, (row * 64 + pad) / th,
                            ((f + 1) * 64 - pad) / tw, ((row + 1) * 64 - pad) / th);
                r.flip(false, true);
                out[dir][f] = r;
            }
        }
        return out;
    }

    /** Re-map each direction's frames into a new order (e.g. reverse and/or drop bad frames). */
    private TextureRegion[][] reorder(TextureRegion[][] frames, int[] order) {
        TextureRegion[][] out = new TextureRegion[frames.length][order.length];
        for (int dir = 0; dir < frames.length; dir++)
            for (int i = 0; i < order.length; i++)
                out[dir][i] = frames[dir][order[i]];
        return out;
    }

    /** Slice oversized 128px action frames: frames[dir][f] from each direction's start row (Y-flipped). */
    private TextureRegion[][] sliceActionRows128(Texture tex, int[] dirStartRows, int frameCount) {
        float tw = tex.getWidth(), th = tex.getHeight(), pad = 0.5f;
        TextureRegion[][] out = new TextureRegion[dirStartRows.length][frameCount];
        for (int dir = 0; dir < dirStartRows.length; dir++) {
            float y = dirStartRows[dir] * 64f;
            for (int f = 0; f < frameCount; f++) {
                float x = f * 128f;
                TextureRegion r = new TextureRegion(tex);
                r.setRegion((x + pad) / tw, (y + pad) / th, (x + 128 - pad) / tw, (y + 128 - pad) / th);
                r.flip(false, true);
                out[dir][f] = r;
            }
        }
        return out;
    }

    /** Turn a data {@link ToolActionDef} into a live {@link ToolAction}, slicing from the named sheet
     *  with the same helpers the hardcoded version used. */
    private void buildToolAction(ToolActionDef def) {
        if (def == null || def.toolIndex < 0 || def.toolIndex >= toolActions.length) return;
        Texture sheet = "chopping".equals(def.sheet) ? chopTex : playerTex;
        TextureRegion[][] frames = def.oversized
                ? sliceActionRows128(sheet, def.dirRows, def.rawFrames)
                : sliceActionRows(sheet, def.dirRows, def.rawFrames);
        if (def.order != null) frames = reorder(frames, def.order);
        int frameCount = (def.order != null) ? def.order.length : def.rawFrames;
        // Oversized swings draw at 2x, centered on the normal footprint; standard actions draw 1:1.
        float drawSize = def.oversized ? PLAYER_SIZE * 2f : PLAYER_SIZE;
        float off = -(drawSize - PLAYER_SIZE) / 2f;
        toolActions[def.toolIndex] = new ToolAction(frames, frameCount, def.effectFrame, def.effect, drawSize, off, off);
    }

    /** food/<id>.png loaded on demand (cached, null cached too so we don't retry). */
    private TextureRegion foodRegion(int id) {
        if (foodRegions.containsKey(id)) return foodRegions.get(id);
        TextureRegion region = null;
        try {
            Texture t = loadTex("food/" + id + ".png");
            foodTextures.add(t);
            region = new TextureRegion(t);
            region.flip(false, true);
        } catch (Exception ignored) {
        }
        foodRegions.put(id, region);
        return region;
    }

    private static boolean isNonCollidable(int v) {
        for (int n : NON_COLLIDABLE) if (n == v) return true;
        return false;
    }

    private boolean collidesAt(float px, float py) {
        float hx = px + HITBOX_OFF_X, hy = py + HITBOX_OFF_Y;
        for (int j = 0; j < collision.length; j++) {
            for (int i = 0; i < collision[j].length; i++) {
                int v = collision[j][i];
                if (v < 1 || isNonCollidable(v)) continue;
                if (hitboxOverlaps(hx, hy, j * TILE, i * TILE - TILE / 8f, TILE, TILE)) return true;
            }
        }
        // Planted crops (not yet harvested) are solid too.
        for (int j = 0; j < cropField.cols(); j++) {
            for (int i = 0; i < cropField.rows(); i++) {
                PlantedCrop crop = cropField.at(j, i);
                if (crop == null || crop.harvested) continue;
                if (hitboxOverlaps(hx, hy, cropBoxX(j), cropBoxY(i), CROP_COLL_W, CROP_COLL_H)) return true;
            }
        }
        return false;
    }

    private boolean hitboxOverlaps(float hx, float hy, float bx, float by, float bw, float bh) {
        return hx < bx + bw && hx + HITBOX_W > bx && hy < by + bh && hy + HITBOX_H > by;
    }

    // A crop in cell (col,row) has a solid footprint box centered on its node corner. Single source
    // for that geometry so collision and the pre-plant guard can never drift apart.
    private float cropBoxX(int col) { return (col + 1) * TILE - CROP_COLL_W / 2f; }
    private float cropBoxY(int row) { return (row + 1) * TILE - CROP_COLL_H / 2f; }

    /** Would a crop planted in cell (col,row) immediately overlap the player where they stand now? */
    private boolean playerOverlapsCropCell(int col, int row) {
        return hitboxOverlaps(playerX + HITBOX_OFF_X, playerY + HITBOX_OFF_Y,
                              cropBoxX(col), cropBoxY(row), CROP_COLL_W, CROP_COLL_H);
    }

    private void resolveCollisions() {
        for (int j = 0; j < collision.length; j++) {
            for (int i = 0; i < collision[j].length; i++) {
                int v = collision[j][i];
                if (v < 1 || isNonCollidable(v)) continue;
                resolveBox(j * TILE, i * TILE - TILE / 8f, TILE, TILE);
            }
        }
        for (int j = 0; j < cropField.cols(); j++) {
            for (int i = 0; i < cropField.rows(); i++) {
                PlantedCrop crop = cropField.at(j, i);
                if (crop == null || crop.harvested) continue;
                resolveBox(cropBoxX(j), cropBoxY(i), CROP_COLL_W, CROP_COLL_H);
            }
        }
    }

    /** Push the player out of a solid box (bx,by,bw,bh) along the smaller overlap axis. */
    private void resolveBox(float bx, float by, float bw, float bh) {
        float hx = playerX + HITBOX_OFF_X, hy = playerY + HITBOX_OFF_Y;
        float ix = Math.max(hx, bx), iy = Math.max(hy, by);
        float ix2 = Math.min(hx + HITBOX_W, bx + bw), iy2 = Math.min(hy + HITBOX_H, by + bh);
        if (ix >= ix2 || iy >= iy2) return;
        float overlapW = ix2 - ix, overlapH = iy2 - iy;
        float pushH = overlapH + COLLISION_ERR, pushW = overlapW + COLLISION_ERR;
        if (overlapW > pushH) {
            if (iy > hy) playerY -= pushH; else playerY += pushH;
        }
        if (overlapH > pushW) {
            if (ix > hx) playerX -= pushW; else playerX += pushW;
        }
    }

    private void drawCollision(SpriteBatch batch, boolean inFront) {
        float feetY = playerY + HITBOX_OFF_Y;
        for (int j = 0; j < collision.length; j++) {
            for (int i = 0; i < collision[j].length; i++) {
                int v = collision[j][i];
                if (v < 1 || v >= collisionRegions.length || collisionRegions[v] == null) continue;
                float threshold = i * TILE + DEPTH_OFFSET;
                boolean drawIt = inFront ? (feetY <= threshold) : (feetY >= threshold);
                if (drawIt) batch.draw(collisionRegions[v], j * TILE, i * TILE, TILE, TILE);
            }
        }
    }

    private void drawArray(SpriteBatch batch, int[][] arr, TextureRegion[] regions) {
        for (int j = 0; j < arr.length; j++) {
            for (int i = 0; i < arr[j].length; i++) {
                int v = arr[j][i];
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

        batch.setProjectionMatrix(hudCam.combined);
        batch.begin();
        batch.draw(invBarRegion, invX, invY, invW, invH);
        batch.draw(toggleRegion, invX + invToggle * slot, invY, slot, invH);
        for (int i = 0; i < invItems.length; i++) {
            if (invItems[i] != 0) {
                TextureRegion food = foodRegion(invItems[i]);
                if (food != null) {
                    batch.draw(food, invX + i * slot + w / 120f, invY + h / 80f, invW / 15f, invH / 1.5f);
                }
                // Draw the amount digit by digit (the font row is "-123456789 0 -", so digit d is at
                // glyph index d, except 0 which lives at index 10). This shows 10+ instead of a lone "0".
                // Each glyph cell is drawn glyphW wide but the digit only fills its middle ~50%, so advance
                // by ~0.6*glyphW: the cells overlap only in their transparent padding, giving tight digits.
                String digits = Integer.toString(Math.max(0, invAmounts[i]));
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

        resolveCollisions();

        // Inventory selector (1/3) and tool cycling (2) — discrete presses.
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) invToggle = Math.max(0, invToggle - 1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) invToggle = Math.min(9, invToggle + 1);
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
        currentAction = walkActions[facing];

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
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT) && hovPlantable) plantAtHover();
        if (Gdx.input.isKeyJustPressed(Input.Keys.E)) harvestAtHover();

        updateCamera();
    }

    /** Resolve the plantable node under the mouse. A node sits at the shared corner of a 2x2 block of
     *  tiles (index = the block's top-left tile) and is plantable when all four tiles are tilled soil
     *  and the node is empty. */
    private void updateHover() {
        mouseWorld.set(Gdx.input.getX(), Gdx.input.getY(), 0);
        camera.unproject(mouseWorld);
        // The node whose center ((j+1)*TILE,(i+1)*TILE) the mouse falls nearest.
        hovCol = (int) Math.floor((mouseWorld.x + TILE / 2f) / TILE) - 1;
        hovRow = (int) Math.floor((mouseWorld.y + TILE / 2f) / TILE) - 1;
        hovPlantable = isPlantableNode(hovCol, hovRow) && cropField.isOpen(hovCol, hovRow)
                     && !playerOverlapsCropCell(hovCol, hovRow);
    }

    /** True when the 2x2 block of tiles with top-left (col,row) is entirely tilled soil. */
    private boolean isPlantableNode(int col, int row) {
        return tilledAt(col, row) && tilledAt(col + 1, row)
                && tilledAt(col, row + 1) && tilledAt(col + 1, row + 1);
    }

    private boolean tilledAt(int col, int row) {
        return col >= 0 && col < planting.length && row >= 0 && row < planting[0].length
                && planting[col][row] >= 1;
    }

    /** Plant the selected seed (an inventory crop item) at the hovered tile, consuming one seed. */
    private void plantAtHover() {
        CropType seed = cropRegistry.byItemId(invItems[invToggle]);
        if (seed == null || invAmounts[invToggle] <= 0) return; // no seed of the selected crop in hand
        cropField.plant(hovCol, hovRow, seed, System.currentTimeMillis());
        if (--invAmounts[invToggle] <= 0) { invAmounts[invToggle] = 0; invItems[invToggle] = 0; }
    }

    /** Harvest the hovered crop if it's mature: bank the produce and leave a hole that can be replanted. */
    private void harvestAtHover() {
        PlantedCrop crop = cropField.at(hovCol, hovRow);
        if (crop == null || !crop.isMature(System.currentTimeMillis())) return;
        addInventory(crop.type.itemId, 1);
        crop.harvested = true; // now renders the hole frame; node is open for replanting
    }

    /** Add an item to the inventory: stack onto a matching slot, else fill the first empty slot. */
    private void addInventory(int itemId, int amount) {
        for (int i = 0; i < invItems.length; i++) {
            if (invItems[i] == itemId) { invAmounts[i] += amount; return; }
        }
        for (int i = 0; i < invItems.length; i++) {
            if (invItems[i] == 0) { invItems[i] = itemId; invAmounts[i] = amount; return; }
        }
    }

    private void updateCamera() {
        camera.position.set(playerX + PLAYER_SIZE / 2f, playerY + PLAYER_SIZE / 2f, 0);
        camera.update();
    }

    /** Apply a tool action's world effect at its effect frame. Only tilling has one today; new
     *  effects (watering, digging, ...) slot in here keyed by the def's {@code effect} string. */
    private void applyActionEffect(String effect, int dir) {
        if ("till".equals(effect)) performTill(dir);
    }

    /** Till the soil the player faces, reusing PlantingLayer's editLayer (amount 400 = a 2x2 plot). */
    private void performTill(int dir) {
        int px = (int) playerX, py = (int) playerY;
        try {
            switch (dir) { // 0 up, 1 left, 2 down, 3 right (offsets = Swing useTool * zoom 1.5)
                case 0 -> plantingLayer.editLayer(px + 70, py + 48, 400);
                case 1 -> plantingLayer.editLayer(px - 42, py + 156, 400);
                case 2 -> plantingLayer.editLayer(px + 70, py + 192, 400);
                case 3 -> plantingLayer.editLayer(px + 183, py + 156, 400);
            }
        } catch (Exception ignored) { /* tilling past the map edge — ignore OOB */ }
    }

    private void drawPlayer(SpriteBatch batch) {
        if (activeAction != null) {
            TextureRegion frame = activeAction.frames[actionDir][Math.min(actionFrame, activeAction.frameCount - 1)];
            batch.draw(frame, playerX + activeAction.offX, playerY + activeAction.offY,
                       activeAction.drawSize, activeAction.drawSize);
        } else {
            batch.draw(playerFrames[currentAction][frameIndex], playerX, playerY, PLAYER_SIZE, PLAYER_SIZE);
        }
    }

    @Override
    public void render(SpriteBatch batch) {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        drawArray(batch, grass, grassRegions);
        drawArray(batch, planting, plantingRegions);
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
        if (hovCol < 0 || hovRow < 0) return;
        if (hovPlantable) {
            batch.setColor(0.3f, 1f, 0.3f, 0.35f);
        } else {
            PlantedCrop crop = cropField.at(hovCol, hovRow);
            if (crop == null || !crop.isMature(System.currentTimeMillis())) return;
            batch.setColor(1f, 0.95f, 0.3f, 0.35f);
        }
        // A tile-sized highlight centered on the node (the middle of the 2x2 tilled block).
        batch.draw(whiteRegion, hovCol * TILE + TILE / 2f, hovRow * TILE + TILE / 2f, TILE, TILE);
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
                // Centered on the node ((j+1)*TILE,(i+1)*TILE), nudged 1/4 tile lower so the base sits right.
                // The post-harvest hole frame sits an extra 1/8 tile lower to line up with the others.
                float cropTop = i * TILE - TILE + TILE / 4f;
                float y = cropTop + (crop.harvested ? TILE / 8f : 0f);
                // Depth ground-line: the player goes in front of the crop once their feet pass ~2/3 down
                // the 2-tile-tall sprite. This is the old CropsLayer mask (feetY <= column*TILE + 100*zoom).
                float threshold = cropTop + CROP_H * (126f / 192f);
                boolean drawIt = inFront ? (feetY <= threshold) : (feetY >= threshold);
                if (!drawIt) continue;
                TextureRegion r = crop.type.stage(crop.stage(now));
                batch.draw(r, j * TILE + TILE / 2f, y, CROP_W, CROP_H);
            }
        }
    }

    @Override
    public void resize(int width, int height) {
        camera.setToOrtho(true, width, height);
        hudCam.setToOrtho(true, width, height);
    }

    @Override
    public void dispose() {
        // Persist first, but never let a save error block resource cleanup on shutdown.
        try { saveGame(); } catch (Exception e) { e.printStackTrace(); }
        grassTex.dispose();
        plowedTex.dispose();
        tallGrassTex.dispose();
        wheatTex.dispose();
        youngWheatTex.dispose();
        playerTex.dispose();
        chopTex.dispose();
        invBarTex.dispose();
        toggleTex.dispose();
        fontTex.dispose();
        for (Texture t : foodTextures) t.dispose();
    }
}
