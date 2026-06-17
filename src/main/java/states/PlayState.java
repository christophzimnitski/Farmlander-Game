package states;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import core.GameState;
import core.GameStateManager;
import entities.Inventory;
import levels.GrassLayer;
import levels.LevelManager;
import levels.PlantingLayer;

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
    private static final float DEPTH_OFFSET = 94.5f;

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

    // Data-driven tool Q-actions, indexed by toolIndex (null = no action for that tool).
    private final ToolAction[] toolActions = new ToolAction[playerAnimationArrays.length];
    private ToolAction activeAction = null; // non-null while an action animation plays
    private int actionDir = 2;              // captured facing for the active action
    private int actionFrame = 0;
    private float actionTimer = 0;
    private boolean effectDone = false;

    /** A tool's Q-action: per-direction frames, frame count, the frame it acts on the world
     *  (-1 = none), and how big / where to draw (for oversized 128px swings). */
    private static final class ToolAction {
        final TextureRegion[][] frames; // [4 dirs: up,left,down,right][frameCount]
        final int frameCount, effectFrame;
        final float drawSize, offX, offY;
        ToolAction(TextureRegion[][] frames, int frameCount, int effectFrame) {
            this(frames, frameCount, effectFrame, PLAYER_SIZE, 0f, 0f);
        }
        ToolAction(TextureRegion[][] frames, int frameCount, int effectFrame, float drawSize, float offX, float offY) {
            this.frames = frames; this.frameCount = frameCount; this.effectFrame = effectFrame;
            this.drawSize = drawSize; this.offX = offX; this.offY = offY;
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

        // Tool Q-actions (data-driven). Tilling reuses the mining sheet's TILLING rows
        // (0..3 = up,left,down,right; 8 frames) and tills at frame 5. Watering plays the
        // watering sheet's use rows (0..3, 7 frames) with no world effect yet.
        TextureRegion[][] tillFrames = new TextureRegion[4][8];
        for (int dir = 0; dir < 4; dir++)
            System.arraycopy(playerFrames[dir], 0, tillFrames[dir], 0, 8);
        toolActions[4] = new ToolAction(tillFrames, 8, 5);
        // Watering = the mining sheet's WATERING rows (20..23 = up,left,down,right). The art
        // has 8 frames (Swing's GetSpritesInAction said 4, which is why it looked abrupt).
        TextureRegion[][] waterFrames = new TextureRegion[4][8];
        int[] waterRows = {20, 21, 22, 23};
        for (int dir = 0; dir < 4; dir++)
            System.arraycopy(playerFrames[waterRows[dir]], 0, waterFrames[dir], 0, 8);
        toolActions[3] = new ToolAction(waterFrames, 8, -1);
        // Digging = the DIGGING rows (4..7 = up,left,down,right), 8 frames (shovel).
        TextureRegion[][] digFrames = new TextureRegion[4][8];
        int[] digRows = {4, 5, 6, 7};
        for (int dir = 0; dir < 4; dir++)
            System.arraycopy(playerFrames[digRows[dir]], 0, digFrames[dir], 0, 8);
        toolActions[5] = new ToolAction(digFrames, 8, -1);
        // Chopping = the oversized 128x128 axe swing at the bottom of player_chopping.png:
        // up=46, left=48, down=50, right=52 (each 128 tall). The sheet's frames run in reverse,
        // and frame index 2 has no axe (a sheet glitch) — so play [5,4,3,1,0]: axe overhead ->
        // swing down -> impact low. Drawn at 480 (128*3.75), centered on the player.
        float chopSize = PLAYER_SIZE * 2f;
        float chopOff = -(chopSize - PLAYER_SIZE) / 2f;     // -120, centers the normal 240 footprint
        int[] chopOrder = {5, 4, 3, 1, 0};
        TextureRegion[][] chopFrames = reorder(sliceActionRows128(chopTex, new int[]{46, 48, 50, 52}, 6), chopOrder);
        toolActions[1] = new ToolAction(chopFrames, chopOrder.length, -1, chopSize, chopOff, chopOff);
        // Mining = the same oversized swing layout in player_mining.png (pickaxe): same reversed
        // order and the same axe-less frame 2 glitch, so reuse the chop order/size/offset.
        TextureRegion[][] mineFrames = reorder(sliceActionRows128(playerTex, new int[]{46, 48, 50, 52}, 6), chopOrder);
        toolActions[2] = new ToolAction(mineFrames, chopOrder.length, -1, chopSize, chopOff, chopOff);

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
        int[] items, amounts;
        int tog;
        try {
            List<Object> save = LevelManager.loadObjects(LevelManager.SAVE_FILE);
            sCollision = (int[][]) save.get(0);
            sPlanting  = (int[][]) save.get(1);
            sGrass     = (int[][]) save.get(2);
            Inventory inv = (Inventory) save.get(5);
            items = inv.getInventory();
            amounts = inv.getInventoryAmounts();
            tog = inv.getToggle();
            px = (Float) save.get(6);
            py = (Float) save.get(7);
            selected = (int[]) save.get(8);
            action = (Integer) save.get(9);
        } catch (Exception e) {
            GrassLayer fallback = new GrassLayer();
            fallback.editLayer();
            sGrass = fallback.getLayerArray();
            sPlanting = new int[40][22];
            sCollision = new int[40][22];
            items = new int[10];
            amounts = new int[10];
            tog = 0;
        }
        collision = sCollision;
        plantingLayer = new PlantingLayer(null); // editLayer/formatLayer don't use Level
        plantingLayer.setLayerArray(sPlanting);
        planting = plantingLayer.getLayerArray();
        grass = sGrass;
        invItems = items;
        invAmounts = amounts;
        invToggle = tog;
        playerX = px;
        playerY = py;
        walkActions = (selected != null && selected.length == 4) ? selected : new int[]{32, 33, 34, 35};
        currentAction = (action >= 0 && action < 36) ? action : 34;
        // Recover the tool index and facing from the saved state.
        for (int i = 0; i < playerAnimationArrays.length; i++) {
            if (Arrays.equals(playerAnimationArrays[i], walkActions)) { toolIndex = i; break; }
        }
        for (int i = 0; i < 4; i++) if (walkActions[i] == currentAction) { facing = i; break; }
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
                float tx = j * TILE, ty = i * TILE;
                if (hx < tx + TILE && hx + HITBOX_W > tx && hy < ty + TILE && hy + HITBOX_H > ty) {
                    return true;
                }
            }
        }
        return false;
    }

    private void resolveCollisions() {
        for (int j = 0; j < collision.length; j++) {
            for (int i = 0; i < collision[j].length; i++) {
                int v = collision[j][i];
                if (v < 1 || isNonCollidable(v)) continue;
                float tx = j * TILE, ty = i * TILE;
                float hx = playerX + HITBOX_OFF_X, hy = playerY + HITBOX_OFF_Y;
                float ix = Math.max(hx, tx), iy = Math.max(hy, ty);
                float ix2 = Math.min(hx + HITBOX_W, tx + TILE), iy2 = Math.min(hy + HITBOX_H, ty + TILE);
                if (ix >= ix2 || iy >= iy2) continue;
                float overlapW = ix2 - ix, overlapH = iy2 - iy;
                float pushH = overlapH + COLLISION_ERR, pushW = overlapW + COLLISION_ERR;
                if (overlapW > pushH) {
                    if (iy > hy) playerY -= pushH; else playerY += pushH;
                }
                if (overlapH > pushW) {
                    if (ix > hx) playerX -= pushW; else playerX += pushW;
                }
            }
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
                int amt = Math.max(0, Math.min(10, invAmounts[i]));
                batch.draw(numberRegions[amt], invX + i * slot + w / 32f, invY + h / 18f, invW / 50f, invH / 7f);
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
                    performTill(actionDir);
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

        updateCamera();
    }

    private void updateCamera() {
        camera.position.set(playerX + PLAYER_SIZE / 2f, playerY + PLAYER_SIZE / 2f, 0);
        camera.update();
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
        drawCollision(batch, false);
        drawPlayer(batch);
        drawCollision(batch, true);
        batch.end();

        drawHud(batch);
    }

    @Override
    public void resize(int width, int height) {
        camera.setToOrtho(true, width, height);
        hudCam.setToOrtho(true, width, height);
    }

    @Override
    public void dispose() {
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
