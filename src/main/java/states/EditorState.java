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
import render.TextureSlicer;
import render.WorldRenderer;
import ui.PixelFont;
import world.BushAutotiler;
import world.ChunkedTileLayer;
import world.DualGridSoil;
import world.ObjectLayer;
import world.PlacedObject;
import world.layer.CornerAutotileLayer;
import world.layer.FenceLayer;
import world.layer.LayerStack;
import world.layer.MapLayer;
import world.layer.PlainTileLayer;

import java.util.ArrayList;
import java.util.List;

/**
 * The map editor scene, pushed over {@link PlayState} by F1 (see {@code Config.EDITOR_ENABLED} +
 * {@code InputAction.TOGGLE_EDITOR}). A <b>free-camera</b> authoring view: no player, no collisions — fly
 * around with WASD/arrows, zoom with {@code -}/{@code =}, pick a layer from the retractable <b>Layers</b>
 * panel, and paint it onto the map (auto-orienting layers paint on click; tile layers open a picker).
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
     *  tile; LAYER paints the active editor stack layer (sand/sand-water/fence/props); NONE until chosen. */
    private enum BrushMode { NONE, TILE, SOIL, BUSH, LAYER }

    /** One row in the Layers panel: a name, what activating it does, and (for stack layers) the layer whose
     *  visibility the eye toggles. {@code visLayer == null} for the built-in rows (no eye). */
    private final class MenuEntry {
        final String name;
        final Runnable activate;
        final MapLayer visLayer;
        MenuEntry(String name, Runnable activate, MapLayer visLayer) {
            this.name = name; this.activate = activate; this.visLayer = visLayer;
        }
    }

    private final WorldRenderer worldRenderer;     // shared: draws PlayState's live world layers
    private final GameCamera camera;               // the editor's own free camera
    private final InputBindings input = new InputBindings();

    // Shared editor UI resources (owned here).
    private final Texture white;
    private final TextureRegion whiteRegion;
    private final PixelFont font;
    private final EditorPanel panel;
    private final SheetPicker picker;

    // Editable world references (shared with PlayState).
    private final Assets assets;
    private final TilePalette palette;
    private final ChunkedTileLayer decoration;
    private final DualGridSoil soil;
    private final ChunkedTileLayer collision;
    private final ObjectLayer objects;
    private final LayerStack layerStack;

    private final List<MenuEntry> menu = new ArrayList<>();
    private int activeMenuRow = -1;
    private MapLayer pendingLayer;                     // stack layer awaiting a tile from the picker

    private final float tile;                          // world units per cell (== Config.TILE)
    private final BushAutotiler bushAutotiler;         // 9-slice bush painting into the collision layer
    private BrushMode mode = BrushMode.NONE;
    private int bushBase;                              // selected bush type's collision band (0/18/36)
    private int layerBrush = 0;                        // brush for the active stack layer: fence gate id / props tile / 0 = auto
    private int brushTileId = TilePalette.EMPTY;     // the tile the picker last selected (TILE/BUSH preview)
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
        buildMenu();
        applyCamera();
    }

    /** Build the Layers panel: the built-in soil + bush layers, every editor stack layer (sand/sand-water/
     *  fence/props + any added), and the plain decoration sheets. Rebuilt when a layer is added. */
    private void buildMenu() {
        menu.clear();
        menu.add(new MenuEntry("Soil", () -> mode = BrushMode.SOIL, null));
        for (MapLayer layer : layerStack.all()) {
            menu.add(new MenuEntry(layer.name(), () -> activateStackLayer(layer), layer));
        }
        menu.add(new MenuEntry("Tall grass", () -> selectBush(bushSheet("tallgrass"), 0), null));
        menu.add(new MenuEntry("Wheat", () -> selectBush(bushSheet("wheat"), BushAutotiler.SPAN), null));
        menu.add(new MenuEntry("Young wheat", () -> selectBush(bushSheet("youngwheat"), 2 * BushAutotiler.SPAN), null));
        menu.add(new MenuEntry("Grass detail", () -> picker.open(TilePalette.indexOf("tilesets/grassTileset.png")), null));
        menu.add(new MenuEntry("Rocks & ore", () -> picker.open(TilePalette.indexOf("tilesets/ore.png")), null));
        menu.add(new MenuEntry("Reeds", () -> picker.open(TilePalette.indexOf("tilesets/reed.png")), null));
    }

    private static int bushSheet(String name) { return TilePalette.indexOf("tilesets/" + name + ".png"); }

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

        int mx = Gdx.input.getX(), my = Gdx.input.getY();
        int w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        boolean overUi = picker.isOpen() || panel.contains(mx, my, h);

        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            if (picker.isOpen()) {
                int id = picker.handleClick(mx, my, w, h);
                if (id != TilePalette.EMPTY) applyPickedTile(id);
            } else {
                EditorPanel.Hit hit = panel.handleClick(mx, my, h, menu.size(), eyeFlags());
                switch (hit.kind) {
                    case LAYER -> { activeMenuRow = hit.index; pendingLayer = null; menu.get(hit.index).activate.run(); }
                    case EYE -> { MapLayer l = menu.get(hit.index).visLayer; if (l != null) l.setVisible(!l.isVisible()); }
                    case ADD -> addLayer();
                    default -> { } // TAB / PANEL / NONE
                }
            }
        }
        if (!overUi) {
            // Left paints the brush continuously while held; right-click erases (held to scrub tiles, the
            // just-pressed frame also removing any legacy free-object under the cursor).
            if (Gdx.input.isButtonPressed(Input.Buttons.LEFT)) paintAt(mx, my);
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

    /** Activate a stack layer: auto-orienting (corner) layers paint immediately; tile/fence layers open the
     *  picker to choose a tile (a fence piece auto-orients; a gate is placed as-is). */
    private void activateStackLayer(MapLayer layer) {
        if (layer.kind() == MapLayer.Kind.CORNER) {
            setActiveLayer(layer);
        } else {
            pendingLayer = layer;
            picker.open(layer.sheetIndex());
        }
    }

    private void setActiveLayer(MapLayer layer) {
        for (int i = 0; i < layerStack.size(); i++) {
            if (layerStack.get(i) == layer) { layerStack.setActive(i); break; }
        }
        mode = BrushMode.LAYER;
        layerBrush = 0;
    }

    /** Route a tile picked from the popup. If a stack layer was awaiting one, paint that layer (a fence gate
     *  16–18 is placed as-is, other fence tiles auto-orient, a props tile stamps). Otherwise stamp the generic
     *  decoration layer. */
    private void applyPickedTile(int id) {
        int tileNum = TilePalette.tileIndexOf(id) + 1; // 1-based tile within the sheet
        if (pendingLayer != null) {
            MapLayer l = pendingLayer;
            pendingLayer = null;
            setActiveLayer(l);
            layerBrush = (l.kind() == MapLayer.Kind.FENCE)
                    ? ((tileNum >= 16 && tileNum <= 18) ? tileNum : 0) : tileNum;
        } else {
            brushTileId = id;
            mode = BrushMode.TILE;
        }
    }

    /** Switch to the bush brush for a sheet, previewing its centre tile (1-based index 11) in the swatch/ghost. */
    private void selectBush(int sheetIndex, int base) {
        mode = BrushMode.BUSH;
        bushBase = base;
        brushTileId = TilePalette.pack(sheetIndex, 10); // 0-based 10 == tile 11, the clump centre
    }

    /** Add a new layer by duplicating the active stack layer's type (so you can group/organise content). The
     *  new empty layer is reslice-backed by the same sheet, persisted, and made active. */
    private void addLayer() {
        MapLayer base = (activeMenuRow >= 0 && activeMenuRow < menu.size()) ? menu.get(activeMenuRow).visLayer : null;
        if (base == null || base.sheetIndex() < 0) return; // can only duplicate a sheet-backed stack layer
        TilePalette.Sheet sheet = TilePalette.sheet(base.sheetIndex());
        TextureRegion[] regions = TextureSlicer.slice(assets.get(sheet.path), sheet.rows, sheet.cols);
        ChunkedTileLayer grid = new ChunkedTileLayer(base.grid().cols(), base.grid().rows(), tile);
        String name = uniqueLayerName(base.name());
        MapLayer copy = switch (base.kind()) {
            case PLAIN -> new PlainTileLayer(name, grid, regions, base.isSolid(), base.isUpright(), base.sheetIndex());
            case CORNER -> new CornerAutotileLayer(name, grid, regions, base.sheetIndex());
            case FENCE -> new FenceLayer(name, grid, regions, base.sheetIndex());
        };
        layerStack.add(copy);
        buildMenu();
        for (int i = 0; i < menu.size(); i++) {
            if (menu.get(i).visLayer == copy) { activeMenuRow = i; activateStackLayer(copy); break; }
        }
    }

    private String uniqueLayerName(String base) {
        String stem = base.replaceAll(" \\d+$", "");
        for (int n = 2; ; n++) {
            String name = stem + " " + n;
            boolean taken = false;
            for (int i = 0; i < layerStack.size(); i++) if (layerStack.get(i).name().equals(name)) taken = true;
            if (!taken) return name;
        }
    }

    private boolean[] eyeFlags() {
        boolean[] eye = new boolean[menu.size()];
        for (int i = 0; i < menu.size(); i++) eye[i] = menu.get(i).visLayer != null;
        return eye;
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
            case LAYER -> { MapLayer l = layerStack.active(); if (l != null) l.paint(col, row, layerBrush); }
            case NONE -> { }
        }
    }

    /** Erase under the cursor. When {@code allowObject} (the right-button just went down) and a legacy free
     *  object is under the cursor, remove it; otherwise clear the cell across every layer. */
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
        renderPanel(batch, w, h);
        if (!picker.isOpen()) renderBrushSwatch(batch, w);
        picker.render(batch, camera.hudCombined(), w, h, brushTileId);
    }

    private void renderPanel(SpriteBatch batch, float w, float h) {
        String[] names = new String[menu.size()];
        boolean[] eye = new boolean[menu.size()];
        boolean[] visible = new boolean[menu.size()];
        for (int i = 0; i < menu.size(); i++) {
            names[i] = menu.get(i).name;
            MapLayer vl = menu.get(i).visLayer;
            eye[i] = vl != null;
            visible[i] = vl != null && vl.isVisible();
        }
        panel.render(batch, camera.hudCombined(), w, h, names, activeMenuRow, eye, visible);
    }

    /** Preview the brush in the hovered cell (world space): the brush tile ghosted in the cell plus a yellow
     *  outline. Skipped when the cursor is over the panel/tab. */
    private void renderHover(SpriteBatch batch, float screenH) {
        int mx = Gdx.input.getX(), my = Gdx.input.getY();
        if (panel.contains(mx, my, screenH)) return;
        var wp = camera.unproject(mx, my);
        int col = decoration.colAtWorldX(wp.x), row = decoration.rowAtWorldY(wp.y);
        if (!decoration.inBounds(col, row)) return;
        float wx = col * tile, wy = row * tile;
        batch.setProjectionMatrix(camera.worldCombined());
        batch.begin();
        TextureRegion preview = previewRegion();
        if (preview != null) {
            batch.setColor(1f, 1f, 1f, 0.5f);
            batch.draw(preview, wx, wy, tile, tile);
            batch.setColor(Color.WHITE);
        }
        float t = 2f * zoom; // keep the outline visible when zoomed out
        batch.setColor(1f, 1f, 0.3f, 0.9f);
        batch.draw(whiteRegion, wx, wy, tile, t);
        batch.draw(whiteRegion, wx, wy + tile - t, tile, t);
        batch.draw(whiteRegion, wx, wy, t, tile);
        batch.draw(whiteRegion, wx + tile - t, wy, t, tile);
        batch.setColor(Color.WHITE);
        batch.end();
    }

    /** The brush's tile region for the swatch + hover ghost, or null for auto layers (sand) that have no
     *  single tile. */
    private TextureRegion previewRegion() {
        if (mode == BrushMode.TILE || mode == BrushMode.BUSH) return palette.region(brushTileId);
        if (mode == BrushMode.LAYER) {
            MapLayer l = layerStack.active();
            if (l != null && l.sheetIndex() >= 0 && layerBrush > 0)
                return palette.region(TilePalette.pack(l.sheetIndex(), layerBrush - 1));
        }
        return null;
    }

    /** A small swatch in the top-right showing the current paint brush, so it's clear what a click places. */
    private void renderBrushSwatch(SpriteBatch batch, float screenW) {
        String label;
        if (mode == BrushMode.TILE) label = "TILE";
        else if (mode == BrushMode.BUSH) label = "BUSH";
        else if (mode == BrushMode.SOIL) label = "SOIL";
        else if (mode == BrushMode.LAYER) {
            MapLayer l = layerStack.active();
            label = l != null ? l.name().toUpperCase() : "LAYER";
        }
        else return; // NONE
        TextureRegion r = previewRegion(); // the brush tile (null for soil / sand auto layers)
        float s = 64f, bx = screenW - s - 16f, by = 16f;
        batch.setProjectionMatrix(camera.hudCombined());
        batch.begin();
        batch.setColor(0.10f, 0.11f, 0.14f, 0.92f);
        batch.draw(whiteRegion, bx - 6f, by - 6f, s + 12f, s + 26f);
        batch.setColor(Color.WHITE);
        if (r != null) batch.draw(r, bx, by, s, s);
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
