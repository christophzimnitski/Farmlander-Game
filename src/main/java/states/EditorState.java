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
import editor.PropCatalog;
import editor.SheetPicker;
import editor.TilePalette;
import input.InputAction;
import input.InputBindings;
import render.GameCamera;
import render.TextureSlicer;
import render.WorldRenderer;
import ui.PixelFont;
import world.BushAutotiler;
import world.ChunkedTileLayer;
import world.DualGridSoil;
import world.ObjectLayer;
import world.PlacedObject;
import world.layer.LayerStack;
import world.layer.MapLayer;

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

    /** What a left-click/drag on the map does. SOIL paints the dual-grid tilled flag (auto-orienting); BUSH
     *  paints an auto-orienting 9-slice clump into the collision layer; TILE stamps the picked decoration
     *  tile; LAYER paints the active editor stack layer (sand/sand-water/fence/props); PROP drops a free
     *  object (legacy); NONE until a brush is chosen. */
    private enum BrushMode { NONE, TILE, SOIL, BUSH, LAYER, PROP }

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
    private final LayerStack layerStack;

    private final float tile;                          // world units per cell (== Config.TILE)
    private final float propScale;                     // world units per source pixel (tile / 32) for props
    private final BushAutotiler bushAutotiler;         // 9-slice bush painting into the collision layer
    private BrushMode mode = BrushMode.NONE;
    private int bushBase;                              // selected bush type's collision band (0/18/36)
    private int brushTileId = TilePalette.EMPTY;     // the tile the picker last selected (TILE/BUSH preview)
    private PropCatalog.Prop brushProp;               // the prop selected (PROP mode)
    private TextureRegion brushPropRegion;            // its sliced region (swatch + ghost preview)
    private float camX, camY;                         // world-space camera centre
    private float zoom = 2f;                           // start zoomed out a little for an overview

    public EditorState(GameStateManager stateManager, WorldRenderer worldRenderer, Assets assets,
                       TilePalette palette, ChunkedTileLayer decoration, DualGridSoil soil,
                       ChunkedTileLayer collision, ObjectLayer objects, LayerStack layerStack,
                       float startX, float startY) {
        super(stateManager);
        this.worldRenderer = worldRenderer;
        this.assets = assets;
        this.palette = palette;
        this.decoration = decoration;
        this.soil = soil;
        this.collision = collision;
        this.objects = objects;
        this.layerStack = layerStack;
        this.tile = decoration.tileSize();
        this.propScale = tile / 32f; // source props are 32px tiles; scale them to world units like everything else
        this.bushAutotiler = new BushAutotiler(collision);
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

        // Mouse. UI clicks are edge-triggered (the picker is modal; otherwise the panel gets first refusal);
        // paint strokes are continuous (held) but only over the map, never over the panel/picker.
        int mx = Gdx.input.getX(), my = Gdx.input.getY();
        int w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        boolean overUi = picker.isOpen() || panel.contains(mx, my, h);

        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            if (picker.isOpen()) {
                int id = picker.handleClick(mx, my, w, h);
                if (id != TilePalette.EMPTY) { brushTileId = id; mode = BrushMode.TILE; }
            } else {
                EditorPanel.Hit hit = panel.handleClick(mx, my, h);
                if (hit == EditorPanel.Hit.SHEET) selectSheet(panel.selectedSheet());
                else if (hit == EditorPanel.Hit.PROP) selectProp(panel.selectedProp());
            }
        }
        if (!overUi) {
            // Props place one-per-click (edge); tiles/soil paint continuously while held. Right-click erases:
            // held to scrub tiles, but only the just-pressed frame removes a prop (so a held drag can't nuke a stack).
            if (mode == BrushMode.PROP) {
                if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) placePropAt(mx, my);
            } else if (Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
                paintAt(mx, my);
            }
            if (Gdx.input.isButtonPressed(Input.Buttons.RIGHT)) {
                eraseAt(mx, my, Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT));
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

    /** A tileset row was clicked: the plow sheet paints soil and the bush sheets paint auto-orienting clumps
     *  (no per-tile pick — connectivity decides the piece); every other sheet opens the picker. */
    private void selectSheet(int sheetIndex) {
        switch (TilePalette.sheet(sheetIndex).path) {
            case "tilesets/plowed_soil.png" -> mode = BrushMode.SOIL;
            case "tilesets/tallgrass.png"   -> selectBush(sheetIndex, 0);
            case "tilesets/wheat.png"       -> selectBush(sheetIndex, BushAutotiler.SPAN);
            case "tilesets/youngwheat.png"  -> selectBush(sheetIndex, 2 * BushAutotiler.SPAN);
            case "tilesets/sand.png"        -> activateLayer("Sand");
            case "tilesets/sandwater.png"   -> activateLayer("Sand water");
            default -> picker.open(sheetIndex);
        }
    }

    /** Make the named editor stack layer the active paint target (auto-orienting layers paint on click). */
    private void activateLayer(String name) {
        for (int i = 0; i < layerStack.size(); i++) {
            if (layerStack.get(i).name().equals(name)) {
                layerStack.setActive(i);
                mode = BrushMode.LAYER;
                return;
            }
        }
    }

    /** Switch to the bush brush for a sheet, previewing its centre tile (1-based index 11) in the swatch/ghost. */
    private void selectBush(int sheetIndex, int base) {
        mode = BrushMode.BUSH;
        bushBase = base;
        brushTileId = TilePalette.pack(sheetIndex, 10); // 0-based 10 == tile 11, the clump centre
    }

    /** A prop row was clicked: switch to the prop brush and slice its sprite once for the swatch/ghost. */
    private void selectProp(int propIndex) {
        mode = BrushMode.PROP;
        brushProp = PropCatalog.get(propIndex);
        brushPropRegion = TextureSlicer.subRegion(assets.get(PropCatalog.SHEET),
                brushProp.x, brushProp.y, brushProp.w, brushProp.h);
    }

    /** Drop the current prop with its base centred on the cursor (where it "stands"). */
    private void placePropAt(int screenX, int screenY) {
        if (brushProp == null) return;
        var wp = camera.unproject(screenX, screenY);
        float dw = brushProp.w * propScale, dh = brushProp.h * propScale;
        float wx = wp.x - dw / 2f, wy = wp.y - dh; // cursor at the prop's base centre
        PlacedObject o = new PlacedObject(PropCatalog.SHEET, brushProp.x, brushProp.y, brushProp.w, brushProp.h,
                wx, wy, dw, dh);
        o.setRegion(TextureSlicer.subRegion(assets.get(PropCatalog.SHEET),
                brushProp.x, brushProp.y, brushProp.w, brushProp.h));
        objects.add(o);
    }

    /** Paint the current brush at the cell under the cursor. */
    private void paintAt(int screenX, int screenY) {
        var wp = camera.unproject(screenX, screenY);
        int col = decoration.colAtWorldX(wp.x), row = decoration.rowAtWorldY(wp.y);
        if (!decoration.inBounds(col, row)) return;
        switch (mode) {
            case TILE -> { if (TilePalette.isValid(brushTileId)) decoration.set(col, row, brushTileId); }
            case SOIL -> soil.till(col, row);
            case BUSH -> bushAutotiler.paint(col, row, bushBase);
            case LAYER -> { MapLayer l = layerStack.active(); if (l != null) l.paint(col, row, 1); }
            case PROP, NONE -> { }
        }
    }

    /** Erase under the cursor. When {@code allowObject} (the right-button just went down) and a prop is under
     *  the cursor, remove that prop; otherwise clear the cell's decoration tile + soil (so a held right-drag
     *  scrubs tiles without nuking a stack of props). */
    private void eraseAt(int screenX, int screenY, boolean allowObject) {
        var wp = camera.unproject(screenX, screenY);
        if (allowObject) {
            PlacedObject hit = objects.topAt(wp.x, wp.y);
            if (hit != null) { objects.remove(hit); return; }
        }
        int col = decoration.colAtWorldX(wp.x), row = decoration.rowAtWorldY(wp.y);
        if (!decoration.inBounds(col, row)) return;
        decoration.set(col, row, TilePalette.EMPTY);
        soil.clear(col, row);
        bushAutotiler.erase(col, row); // clears a bush here + re-tiles its neighbours (no-op on non-bush cells)
        for (MapLayer l : layerStack.all()) l.erase(col, row); // clear any editor layer (sand/fence/props) here
    }

    @Override
    public void render(SpriteBatch batch) {
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        worldRenderer.renderForEditor(batch, camera);
        if (!picker.isOpen()) renderHover(batch, h);
        panel.render(batch, camera.hudCombined(), w, h);
        if (!picker.isOpen()) renderBrushSwatch(batch, w);
        picker.render(batch, camera.hudCombined(), w, h, brushTileId);
    }

    /** Preview the brush under the cursor (world space): a prop ghost at the cursor's base, or the hovered
     *  cell outlined with a tile preview. Skipped when the cursor is over the panel/tab. */
    private void renderHover(SpriteBatch batch, float screenH) {
        int mx = Gdx.input.getX(), my = Gdx.input.getY();
        if (panel.contains(mx, my, screenH)) return;
        var wp = camera.unproject(mx, my);
        batch.setProjectionMatrix(camera.worldCombined());
        batch.begin();
        if (mode == BrushMode.PROP && brushPropRegion != null) {
            float dw = brushProp.w * propScale, dh = brushProp.h * propScale;
            batch.setColor(1f, 1f, 1f, 0.55f);
            batch.draw(brushPropRegion, wp.x - dw / 2f, wp.y - dh, dw, dh);
            batch.setColor(Color.WHITE);
        } else {
            int col = decoration.colAtWorldX(wp.x), row = decoration.rowAtWorldY(wp.y);
            if (decoration.inBounds(col, row)) {
                float wx = col * tile, wy = row * tile;
                if (mode == BrushMode.TILE || mode == BrushMode.BUSH) {
                    TextureRegion r = palette.region(brushTileId);
                    if (r != null) {
                        batch.setColor(1f, 1f, 1f, 0.5f);
                        batch.draw(r, wx, wy, tile, tile);
                        batch.setColor(Color.WHITE);
                    }
                }
                float t = 2f * zoom; // keep the outline visible when zoomed out
                batch.setColor(1f, 1f, 0.3f, 0.9f);
                batch.draw(whiteRegion, wx, wy, tile, t);
                batch.draw(whiteRegion, wx, wy + tile - t, tile, t);
                batch.draw(whiteRegion, wx, wy, t, tile);
                batch.draw(whiteRegion, wx + tile - t, wy, t, tile);
                batch.setColor(Color.WHITE);
            }
        }
        batch.end();
    }

    /** A small swatch in the top-right showing the current paint brush (tile, prop or soil), so it's clear
     *  what a map click will place. */
    private void renderBrushSwatch(SpriteBatch batch, float screenW) {
        TextureRegion r;
        int srcW, srcH;
        String label;
        if (mode == BrushMode.TILE || mode == BrushMode.BUSH) {
            r = palette.region(brushTileId); srcW = srcH = 32; label = mode == BrushMode.BUSH ? "BUSH" : "TILE";
        }
        else if (mode == BrushMode.PROP) { r = brushPropRegion; srcW = brushProp.w; srcH = brushProp.h; label = "PROP"; }
        else if (mode == BrushMode.SOIL) { r = null; srcW = srcH = 1; label = "SOIL"; }
        else if (mode == BrushMode.LAYER) {
            r = null; srcW = srcH = 1;
            MapLayer l = layerStack.active();
            label = l != null ? l.name().toUpperCase() : "LAYER";
        }
        else return; // NONE
        float s = 64f, bx = screenW - s - 16f, by = 16f;
        batch.setProjectionMatrix(camera.hudCombined());
        batch.begin();
        batch.setColor(0.10f, 0.11f, 0.14f, 0.92f);
        batch.draw(whiteRegion, bx - 6f, by - 6f, s + 12f, s + 26f);
        batch.setColor(Color.WHITE);
        if (r != null) {
            float aspect = srcW / (float) srcH, dw = s, dh = s;
            if (aspect >= 1f) dh = s / aspect; else dw = s * aspect; // fit into the swatch, keep aspect
            batch.draw(r, bx + (s - dw) / 2f, by + (s - dh) / 2f, dw, dh);
        }
        font.draw(batch, label, bx + 2f, by + s + 4f, 12f, 12f, 11f);
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
