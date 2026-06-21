package states;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import core.Assets;
import core.GameState;
import core.GameStateManager;
import editor.EditorPanel;
import editor.TilePalette;
import input.InputAction;
import input.InputBindings;
import render.GameCamera;
import render.WorldRenderer;
import world.ChunkedTileLayer;
import world.DualGridSoil;
import world.ObjectLayer;

/**
 * The map editor scene, pushed over {@link PlayState} by F1 (see {@code Config.EDITOR_ENABLED} +
 * {@code InputAction.TOGGLE_EDITOR}). A <b>free-camera</b> authoring view: no player, no collisions — fly
 * around with WASD/arrows, zoom with {@code -}/{@code =}, pick a tileset from the retractable side panel,
 * and (in later slices) paint tiles and drop props.
 *
 * <p>It edits the <em>same</em> world layers {@link PlayState} owns (handed in here), through its own free
 * {@link GameCamera}, so edits show up immediately back in play. While the editor is on top of the state
 * stack, {@code PlayState.update} doesn't run, so the world is frozen underneath. The only GL resource it
 * owns is the panel's white pixel, freed in {@link #dispose}.
 */
public final class EditorState extends GameState {
    private static final float PAN_SPEED = 1800f;  // world units/sec at zoom 1 (scaled up when zoomed out)
    private static final float ZOOM_RATE = 1.8f;   // multiplicative zoom per second while held
    private static final float MIN_ZOOM = 0.5f, MAX_ZOOM = 8f;

    private final WorldRenderer worldRenderer;     // shared: draws PlayState's live world layers
    private final GameCamera camera;               // the editor's own free camera
    private final InputBindings input = new InputBindings();
    private final EditorPanel panel;

    // Editable world references (shared with PlayState). Stored for the placement/picker slices.
    private final Assets assets;
    private final TilePalette palette;
    private final ChunkedTileLayer decoration;
    private final DualGridSoil soil;
    private final ChunkedTileLayer collision;
    private final ObjectLayer objects;

    private float camX, camY;                        // world-space camera centre
    private float zoom = 2f;                         // start zoomed out a little for an overview

    public EditorState(GameStateManager stateManager, WorldRenderer worldRenderer, Assets assets,
                       TilePalette palette, ChunkedTileLayer decoration, DualGridSoil soil,
                       ChunkedTileLayer collision, ObjectLayer objects, float startX, float startY) {
        super(stateManager);
        this.worldRenderer = worldRenderer;
        this.assets = assets;
        this.palette = palette;
        this.decoration = decoration;
        this.soil = soil;
        this.collision = collision;
        this.objects = objects;
        this.camX = startX;
        this.camY = startY;
        camera = new GameCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        panel = new EditorPanel(assets.get("ui/game_font.png"));
        applyCamera();
    }

    @Override
    public void update(float delta) {
        // F1 again (or Esc) closes the editor and returns to play.
        if (input.isJustPressed(InputAction.TOGGLE_EDITOR) || input.isJustPressed(InputAction.QUIT)) {
            stateManager.pop();
            return;
        }

        // Mouse: panel first (toggle tab / pick a tileset). Clicks the panel consumes won't fall through to
        // the map. Picker + placement come in the next slices.
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            panel.handleClick(Gdx.input.getX(), Gdx.input.getY(), Gdx.graphics.getHeight());
        }

        // Free pan — fast, and faster still when zoomed out so crossing the map stays quick.
        float pan = PAN_SPEED * zoom * delta;
        if (input.isDown(InputAction.MOVE_LEFT))  camX -= pan;
        if (input.isDown(InputAction.MOVE_RIGHT)) camX += pan;
        if (input.isDown(InputAction.MOVE_UP))    camY -= pan; // Y-down
        if (input.isDown(InputAction.MOVE_DOWN))  camY += pan;

        // Zoom on -/= (and keypad), continuous while held.
        if (Gdx.input.isKeyPressed(Input.Keys.EQUALS) || Gdx.input.isKeyPressed(Input.Keys.PLUS)
                || Gdx.input.isKeyPressed(Input.Keys.NUMPAD_ADD)) {
            zoom = Math.max(MIN_ZOOM, zoom / (1f + ZOOM_RATE * delta));
        }
        if (Gdx.input.isKeyPressed(Input.Keys.MINUS) || Gdx.input.isKeyPressed(Input.Keys.NUMPAD_SUBTRACT)) {
            zoom = Math.min(MAX_ZOOM, zoom * (1f + ZOOM_RATE * delta));
        }
        applyCamera();
    }

    private void applyCamera() {
        camera.setZoom(zoom);
        camera.follow(camX, camY);
    }

    @Override
    public void render(SpriteBatch batch) {
        worldRenderer.renderForEditor(batch, camera);
        panel.render(batch, camera.hudCombined(), Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    @Override
    public void resize(int width, int height) {
        camera.resize(width, height);
        applyCamera();
    }

    @Override
    public void dispose() {
        panel.dispose();
    }
}
