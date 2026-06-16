package states;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import core.GameState;
import core.GameStateManager;
import levels.GrassLayer;
import levels.LevelManager;

import java.util.List;

/**
 * Renders your real saved world (grass + tilled soil + collidables) and the
 * player with a hand-stepped walk animation (Stage B). Frame stepping mirrors
 * your Player.updateAnimationTick: walk actions have 9 frames, frame 0 is idle,
 * walking cycles frames 1..8 at aniSpeed 16 (= 16/144 s per frame).
 *
 * Deferred: collision, crops (none planted), tool actions, inventory.
 */
public class PlayState extends GameState {
    private static final float TILE = 96f;          // 64 * zoom(1.5)
    private static final float PLAYER_SIZE = 240f;  // 32 * 5 * zoom(1.5)
    private static final float MOVE_SPEED = 270f;   // ~ Swing playerSpeed (1.25*1.5) * 144 ups
    private static final float ANIM_FRAME_TIME = 16f / 144f; // aniSpeed 16 ticks at 144 ups
    private static final int WALK_FRAMES = 9;

    // Player hitbox (feet) relative to player x/y, and collision push tuning —
    // mirrors Player.initPlayerHitbox and MovableEntity.checkCollision at zoom 1.5.
    private static final float HITBOX_OFF_X = 75f, HITBOX_OFF_Y = 195f; // 50*z, 130*z
    private static final float HITBOX_W = 90f, HITBOX_H = 48f;          // 60*z, 32*z
    private static final float COLLISION_ERR = 0.45f;                   // 0.3*z
    // Collision-layer tile indices that are walkable (edge/transition tiles).
    private static final int[] NON_COLLIDABLE = {7, 8, 9, 25, 26, 27, 43, 44, 45};
    // Depth-sort threshold (63*zoom): tiles above the player's feet draw under it,
    // tiles the player stands behind draw over it. Mirrors drawLayer/drawCollisionMask.
    private static final float DEPTH_OFFSET = 94.5f;

    private final OrthographicCamera camera;

    private final Texture grassTex, plowedTex, tallGrassTex, wheatTex, youngWheatTex, playerTex;
    private final TextureRegion[] grassRegions, plantingRegions, collisionRegions;
    private final TextureRegion[][] playerFrames; // [36 actions][9 frames], 64x64

    private final int[][] grass, planting, collision;
    private float playerX, playerY;

    // Animation state (hand-stepped, mirroring Player.updateAnimationTick).
    private int[] walkActions;   // [up, left, down, right] action rows for current tool
    private int currentAction;   // row into playerFrames
    private int frameIndex = 0;  // 0 = idle, 1..8 = walk cycle
    private float animTimer = 0;

    public PlayState(GameStateManager gsm) {
        super(gsm);

        camera = new OrthographicCamera();
        camera.setToOrtho(true, Gdx.graphics.getWidth(), Gdx.graphics.getHeight()); // Y-down to match Swing

        grassTex      = new Texture(Gdx.files.classpath("tilesets/grassTileset.png"));
        plowedTex     = new Texture(Gdx.files.classpath("tilesets/plowed_soil.png"));
        tallGrassTex  = new Texture(Gdx.files.classpath("tilesets/tallgrass.png"));
        wheatTex      = new Texture(Gdx.files.classpath("tilesets/wheat.png"));
        youngWheatTex = new Texture(Gdx.files.classpath("tilesets/youngwheat.png"));
        playerTex     = new Texture(Gdx.files.classpath("player_animations/player_mining.png"));

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

        // Inset each frame's edges by half a texel so upscaling (64->240, a
        // non-integer 3.75x) can't sample a sliver of the neighbouring frame
        // stacked above/below it in the atlas (the "line above the player").
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

        int[][] sCollision, sPlanting, sGrass;
        float px = 2000, py = 800;
        int action = 34; // WALK_DOWN default
        int[] selected = {32, 33, 34, 35}; // WALK_UP/LEFT/DOWN/RIGHT default
        try {
            List<Object> save = LevelManager.loadObjects(LevelManager.SAVE_FILE);
            sCollision = (int[][]) save.get(0);
            sPlanting  = (int[][]) save.get(1);
            sGrass     = (int[][]) save.get(2);
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
        }
        collision = sCollision;
        planting = sPlanting;
        grass = sGrass;
        playerX = px;
        playerY = py;
        walkActions = (selected != null && selected.length == 4) ? selected : new int[]{32, 33, 34, 35};
        currentAction = (action >= 0 && action < 36) ? action : 34;
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

    private static boolean isNonCollidable(int v) {
        for (int n : NON_COLLIDABLE) if (n == v) return true;
        return false;
    }

    /** True if the player's feet hitbox at (px,py) overlaps any solid collision tile. */
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

    /**
     * Push the player out of solid collision tiles. Faithful to
     * MovableEntity.checkCollision: resolve along the smaller overlap axis,
     * recomputing the (live) hitbox per tile so sequential pushes compose.
     */
    private void resolveCollisions() {
        for (int j = 0; j < collision.length; j++) {
            for (int i = 0; i < collision[j].length; i++) {
                int v = collision[j][i];
                if (v < 1 || isNonCollidable(v)) continue;

                float tx = j * TILE, ty = i * TILE;
                float hx = playerX + HITBOX_OFF_X, hy = playerY + HITBOX_OFF_Y;

                float ix = Math.max(hx, tx), iy = Math.max(hy, ty);
                float ix2 = Math.min(hx + HITBOX_W, tx + TILE), iy2 = Math.min(hy + HITBOX_H, ty + TILE);
                if (ix >= ix2 || iy >= iy2) continue; // no overlap

                float overlapW = ix2 - ix, overlapH = iy2 - iy;
                float pushH = overlapH + COLLISION_ERR, pushW = overlapW + COLLISION_ERR;

                if (overlapW > pushH) {              // colliding top/bottom
                    if (iy > hy) playerY -= pushH;    // tile below -> move up
                    else playerY += pushH;            // tile above -> move down
                }
                if (overlapH > pushW) {              // colliding left/right
                    if (ix > hx) playerX -= pushW;    // tile right -> move left
                    else playerX += pushW;            // tile left -> move right
                }
            }
        }
    }

    /**
     * Draw collidables split by depth: {@code inFront=false} draws tiles above the
     * player's feet (under the player); {@code inFront=true} draws tiles the player
     * stands behind (over the player), so the player is occluded by bush tops.
     */
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

    @Override
    public void update(float delta) {
        boolean up    = Gdx.input.isKeyPressed(Input.Keys.UP)    || Gdx.input.isKeyPressed(Input.Keys.W);
        boolean down  = Gdx.input.isKeyPressed(Input.Keys.DOWN)  || Gdx.input.isKeyPressed(Input.Keys.S);
        boolean left  = Gdx.input.isKeyPressed(Input.Keys.LEFT)  || Gdx.input.isKeyPressed(Input.Keys.A);
        boolean right = Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D);
        if (Gdx.input.isKeyPressed(Input.Keys.ESCAPE)) Gdx.app.exit();

        float d = MOVE_SPEED * delta;
        int ix = (right ? 1 : 0) - (left ? 1 : 0);
        int iy = (down ? 1 : 0) - (up ? 1 : 0); // Y-down
        if (ix != 0 && iy != 0) {
            // Diagonal: if exactly one axis is blocked, you can't actually move
            // diagonally, so slide the free axis at FULL speed. Only normalize
            // (0.707 each) when both axes are genuinely free.
            boolean xBlocked = collidesAt(playerX + ix * d, playerY);
            boolean yBlocked = collidesAt(playerX, playerY + iy * d);
            if (xBlocked ^ yBlocked) {
                if (xBlocked) playerY += iy * d;
                else playerX += ix * d;
            } else {
                float inv = 0.70710678f; // 1/sqrt(2)
                playerX += ix * d * inv;
                playerY += iy * d * inv;
            }
        } else {
            playerX += ix * d;
            playerY += iy * d;
        }

        // Facing: later assignments win, matching Player.setAnimation (right > left > down > up).
        if (up)    currentAction = walkActions[0];
        if (down)  currentAction = walkActions[2];
        if (left)  currentAction = walkActions[1];
        if (right) currentAction = walkActions[3];

        resolveCollisions();

        boolean moving = up || down || left || right;
        if (moving) {
            if (frameIndex == 0) frameIndex = 1; // skip idle frame once moving
            animTimer += delta;
            if (animTimer >= ANIM_FRAME_TIME) {
                animTimer -= ANIM_FRAME_TIME;
                frameIndex++;
                if (frameIndex >= WALK_FRAMES) frameIndex = 1; // wrap to second frame (idle stays 0)
            }
        } else {
            frameIndex = 0;
            animTimer = 0;
        }

        camera.position.set(playerX + PLAYER_SIZE / 2f, playerY + PLAYER_SIZE / 2f, 0);
        camera.update();
    }

    @Override
    public void render(SpriteBatch batch) {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        drawArray(batch, grass, grassRegions);
        drawArray(batch, planting, plantingRegions);
        drawCollision(batch, false); // collidables behind the player (under it)
        batch.draw(playerFrames[currentAction][frameIndex], playerX, playerY, PLAYER_SIZE, PLAYER_SIZE);
        drawCollision(batch, true);  // collidables the player stands behind (over it)
        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        camera.setToOrtho(true, width, height);
    }

    @Override
    public void dispose() {
        grassTex.dispose();
        plowedTex.dispose();
        tallGrassTex.dispose();
        wheatTex.dispose();
        youngWheatTex.dispose();
        playerTex.dispose();
    }
}
