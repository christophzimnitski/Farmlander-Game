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
import editor.EditorPanel;
import editor.SheetPicker;
import editor.TilePalette;
import input.InputAction;
import input.InputBindings;
import render.GameCamera;
import render.WorldRenderer;
import ui.PixelFont;
import world.ChunkedTileLayer;
import world.DualGridSoil;
import world.ObjectLayer;

/**
 * The map editor scene, pushed over {@link PlayState} by F1 (see {@code Config.EDITOR_ENABLED} +
 * {@code InputAction.TOGGLE_EDITOR}). A <b>free-camera</b> authoring view: no player, no collisions — fly
 * around with WASD/arrows, zoom with {@code -}/{@code =}, pick a tileset from the retractable side panel,
 * choose a tile in the picker popup, and (next slice) paint it onto the map.
 *
 * <p>It edits the <em>same</em> world layers {@link PlayState} owns (handed in here), through its own free
 * {@link GameCamera}, so edits show up immediately back in play. While the editor is on top of the state
 * stack, {@code PlayState.update} doesn't run, so the world is frozen underneath. Owns the shared editor
 * font + white pixel (passed to the panel and picker); freed in {@link #dispose}.
 */
public final class EditorState extends GameState {
    private static final float PAN_SPEED = 1800f;  // world units/sec at zoom 1 (scaled up when zoomed out)
    private static final float ZOOM_RATE = 1.8f;   // multiplicative zoom per second while held
    private static final float MIN_ZOOM = 0.5f, MAX_ZOOM = 8f;

    private final WorldRenderer worldRenderer;     // shared: draws PlayState's live world layers
    private final GameCamera camera;               // the editor's own free camera
    private final InputBindings input = new InputBindings();

    // Shared editor UI resources (owned here).
    private final Texture white;
    private final TextureRegion whiteRegion;
    private final PixelFont font;
    private final EditorPanel panel;
    private final SheetPicker picker;

    // Editable world references (shared with PlayState). Used by placement (next slice).
    private final Assets assets;
    private final TilePalette palette;
    private final ChunkedTileLayer decoration;
    private final DualGridSoil soil;
    private final ChunkedTileLayer collision;
    private final ObjectLayer objects;

    private int brushTileId = TilePalette.EMPTY;     // the tile the picker last selected
    private float camX, camY;                         // world-space camera centre
    private float zoom = 2f;                           // start zoomed out a little for an overview

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

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        white = new Texture(pm);
        pm.dispose();
        whiteRegion = new TextureRegion(white);
        font = new PixelFont(assets.get("ui/game_font.png"));
        panel = new EditorPanel(font, whiteRegion);
        picker = new SheetPicker(assets, font, whiteRegion);
        applyCamera();
    }

    @Override
    public void update(float delta) {
        // Esc closes the picker first if it's open, otherwise leaves the editor; F1 always leaves.
        if (input.isJustPressed(InputAction.QUIT)) {
            if (picker.isOpen()) { picker.close(); return; }
            stateManager.pop();
            return;
        }
        if (input.isJustPressed(InputAction.TOGGLE_EDITOR)) {
            stateManager.pop();
            return;
        }

        // Mouse. The picker is modal: while open it consumes every click. Otherwise the side panel gets first
        // refusal (toggle / pick a tileset); a click it doesn't claim falls through to the map (placement is
        // the next slice).
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            int mx = Gdx.input.getX(), my = Gdx.input.getY();
            int w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
            if (picker.isOpen()) {
                int id = picker.handleClick(mx, my, w, h);
                if (id != TilePalette.EMPTY) brushTileId = id;
            } else {
                EditorPanel.Hit hit = panel.handleClick(mx, my, h);
                if (hit == EditorPanel.Hit.SHEET) picker.open(panel.selectedSheet());
                // EditorPanel.Hit.NONE -> a map click; placement lands in the next slice.
            }
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
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        worldRenderer.renderForEditor(batch, camera);
        panel.render(batch, camera.hudCombined(), w, h);
        if (!picker.isOpen()) renderBrushSwatch(batch, w);
        picker.render(batch, camera.hudCombined(), w, h, brushTileId);
    }

    /** A small swatch in the top-right showing the current paint brush, so it's clear what a map click places. */
    private void renderBrushSwatch(SpriteBatch batch, float screenW) {
        if (brushTileId == TilePalette.EMPTY) return;
        TextureRegion r = palette.region(brushTileId);
        float s = 64f, bx = screenW - s - 16f, by = 16f;
        batch.setProjectionMatrix(camera.hudCombined());
        batch.begin();
        batch.setColor(0.10f, 0.11f, 0.14f, 0.92f);
        batch.draw(whiteRegion, bx - 6f, by - 6f, s + 12f, s + 26f);
        batch.setColor(Color.WHITE);
        if (r != null) batch.draw(r, bx, by, s, s);
        font.draw(batch, "BRUSH", bx + 2f, by + s + 4f, 12f, 12f, 11f);
        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        camera.resize(width, height);
        applyCamera();
    }

    @Override
    public void dispose() {
        white.dispose();
    }
}
