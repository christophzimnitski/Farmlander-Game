package states;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import core.GameState;
import core.GameStateManager;
import input.InputAction;
import input.InputBindings;
import render.GameCamera;
import render.WorldRenderer;

/**
 * The map editor scene, pushed over {@link PlayState} by F1 (see {@code Config.EDITOR_ENABLED} +
 * {@code InputAction.TOGGLE_EDITOR}). It is a <b>free-camera</b> authoring view: no player, no collisions —
 * you fly around the world quickly with WASD/arrows and zoom with {@code -}/{@code =}, and (in later slices)
 * paint tiles and drop props from a side panel.
 *
 * <p>It draws the <em>same</em> world layers {@link PlayState} owns (handed in as a shared
 * {@link WorldRenderer}), through its own free {@link GameCamera}, so any edit shows up immediately when you
 * drop back into play. While the editor is on top of the state stack, {@code PlayState.update} doesn't run,
 * so the world is frozen underneath. Owns no GL resources (the renderer/textures belong to PlayState), so
 * {@link #dispose} is a no-op.
 */
public final class EditorState extends GameState {
    private static final float PAN_SPEED = 1800f;  // world units/sec at zoom 1 (scaled up when zoomed out)
    private static final float ZOOM_RATE = 1.8f;   // multiplicative zoom per second while held
    private static final float MIN_ZOOM = 0.5f, MAX_ZOOM = 8f;

    private final WorldRenderer worldRenderer;     // shared: draws PlayState's live world layers
    private final GameCamera camera;               // the editor's own free camera
    private final InputBindings input = new InputBindings();

    private float camX, camY;                       // world-space camera centre
    private float zoom = 2f;                         // start zoomed out a little for an overview

    public EditorState(GameStateManager stateManager, WorldRenderer worldRenderer, float startX, float startY) {
        super(stateManager);
        this.worldRenderer = worldRenderer;
        this.camX = startX;
        this.camY = startY;
        camera = new GameCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        applyCamera();
    }

    @Override
    public void update(float delta) {
        // F1 again (or Esc) closes the editor and returns to play.
        if (input.isJustPressed(InputAction.TOGGLE_EDITOR) || input.isJustPressed(InputAction.QUIT)) {
            stateManager.pop();
            return;
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
    }

    @Override
    public void resize(int width, int height) {
        camera.resize(width, height);
        applyCamera();
    }

    @Override
    public void dispose() {
        // Owns no GL resources — the world layers, renderer and textures belong to PlayState.
    }
}
