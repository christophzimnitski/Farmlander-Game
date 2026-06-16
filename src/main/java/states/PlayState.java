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
        if (left)  playerX -= d;
        if (right) playerX += d;
        if (up)    playerY -= d; // Y-down
        if (down)  playerY += d;

        // Facing: later assignments win, matching Player.setAnimation (right > left > down > up).
        if (up)    currentAction = walkActions[0];
        if (down)  currentAction = walkActions[2];
        if (left)  currentAction = walkActions[1];
        if (right) currentAction = walkActions[3];

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
        drawArray(batch, collision, collisionRegions);
        batch.draw(playerFrames[currentAction][frameIndex], playerX, playerY, PLAYER_SIZE, PLAYER_SIZE);
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
