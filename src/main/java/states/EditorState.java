package states;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import core.Assets;
import core.Debug;
import core.GameState;
import core.GameStateManager;
import editor.EditorPanel;
import editor.SheetPicker;
import editor.TilePalette;
import input.InputAction;
import input.InputBindings;
import physics.CollisionResolver;
import render.DebugOverlay;
import render.GameCamera;
import render.WorldRenderer;
import ui.PixelFont;
import world.BushAutotiler;
import world.ChunkedTileLayer;
import world.FenceAutotiler;
import world.DualGridSoil;
import world.layer.LayerStack;
import world.layer.LayerVisibility;
import world.layer.MapLayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

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
    private static final float SCROLL_STEP = 1.07f; // zoom factor per mouse-wheel notch (~half the prior rate)
    private static final float MIN_ZOOM = 0.5f, MAX_ZOOM = 8f;
    // The contextual GATE button, sized/placed to sit just under the top-right brush swatch box.
    private static final float GATE_BTN_W = 76f, GATE_BTN_H = 28f, GATE_BTN_Y = 108f;
    // The grass-detail sheet (palette sheet 0); the Grass layer picks one of its tiles to paint.
    private static final int GRASS_SHEET = TilePalette.indexOf("tilesets/grassTileset.png");

    /** What a left-click/drag on the map does. GRASS stamps a picked grass-detail tile into the base grass
     *  background; SOIL paints the dual-grid tilled flag (auto-orienting); BUSH paints an auto-orienting
     *  9-slice clump into the collision layer; TILE stamps the picked decoration tile; LAYER paints the active
     *  editor stack layer (sand/sand-water/fence/props); NONE until chosen. */
    private enum BrushMode { NONE, GRASS, TILE, SOIL, BUSH, LAYER }

    /** One row in the Layers panel: a name, what activating it does, and the visibility get/toggle for its eye
     *  (every layer — stack or built-in — has one). */
    private static final class MenuEntry {
        final String name;
        final Runnable activate;
        final BooleanSupplier visible;
        final Runnable toggleVisible;
        MenuEntry(String name, Runnable activate, BooleanSupplier visible, Runnable toggleVisible) {
            this.name = name; this.activate = activate; this.visible = visible; this.toggleVisible = toggleVisible;
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
    private final ChunkedTileLayer grass;       // base grass background; the Grass layer stamps detail tiles into it
    private final ChunkedTileLayer decoration;
    private final DualGridSoil soil;
    private final ChunkedTileLayer collision;
    private final LayerStack layerStack;
    private final LayerVisibility visibility;
    private final CollisionResolver collisionResolver; // shared with PlayState; only for the F2 hitbox overlay
    private final DebugOverlay debugOverlay = new DebugOverlay(); // F2 hitbox overlay (dev-only)

    private final List<MenuEntry> menu = new ArrayList<>();
    private int activeMenuRow = -1;
    private MapLayer pendingLayer;                     // stack layer awaiting a tile from the picker
    private boolean pendingGrass;                       // the Grass layer is awaiting a detail tile from the picker

    private final float tile;                          // world units per cell (== Config.TILE)
    private final BushAutotiler bushAutotiler;         // 9-slice bush painting into the collision layer
    private BrushMode mode = BrushMode.NONE;
    private int bushBase;                              // selected bush type's collision band (0/18/36)
    private int layerBrush = 0;                        // brush for the active stack layer: fence gate id / props tile / 0 = auto
    private int grassBrush = 1;                         // grass-detail tile value (1..3) the Grass layer paints
    private int brushTileId = TilePalette.EMPTY;     // the tile the picker last selected (GRASS/TILE/BUSH preview)
    private int[] stampIds;                            // non-null = a multi-tile rectangular stamp (packed ids, row-major)
    private int stampCols, stampRows;                  // its dimensions in cells
    private boolean consumeClick;                      // a UI click (picker/panel) holds the LEFT button down for
                                                       // several frames; suppress map painting until it's released
    private float camX, camY;                         // world-space camera centre
    private float zoom = 2f;                           // start zoomed out a little for an overview

    public EditorState(GameStateManager stateManager, WorldRenderer worldRenderer, Assets assets,
                       TilePalette palette, ChunkedTileLayer grass, ChunkedTileLayer decoration, DualGridSoil soil,
                       ChunkedTileLayer collision, LayerStack layerStack, LayerVisibility visibility,
                       CollisionResolver collisionResolver, float startX, float startY) {
        super(stateManager);
        this.worldRenderer = worldRenderer;
        this.assets = assets;
        this.palette = palette;
        this.grass = grass;
        this.decoration = decoration;
        this.soil = soil;
        this.collision = collision;
        this.layerStack = layerStack;
        this.visibility = visibility;
        this.collisionResolver = collisionResolver;
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
        // Mouse-wheel zoom needs the scroll callback (polling can't read it): scroll up zooms in, down zooms out.
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override public boolean scrolled(float amountX, float amountY) {
                zoom = clampZoom(zoom * (amountY > 0 ? SCROLL_STEP : 1f / SCROLL_STEP));
                return true;
            }
        });
    }

    private static float clampZoom(float z) { return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z)); }

    /** Build the Layers panel: the built-in soil + bush layers, every editor stack layer (sand/sand-water/
     *  fence/props + any added), and the plain decoration sheets. Rebuilt when a layer is added. */
    private void buildMenu() {
        menu.clear();
        // The base grass background: selecting it opens the picker to choose one of the grass-detail tiles,
        // which then paints into the grass layer. Its eye hides/shows the whole grass background.
        menu.add(new MenuEntry("Grass", this::selectGrass,
                () -> visibility.grass, () -> visibility.grass = !visibility.grass));
        menu.add(new MenuEntry("Soil", () -> { mode = BrushMode.SOIL; clearStamp(); },
                () -> visibility.soil, () -> visibility.soil = !visibility.soil));
        // The fence layers (different styles) form one network: they connect across layers and share a single
        // eye, so toggling either hides/shows them all together.
        List<MapLayer> fences = new ArrayList<>();
        for (MapLayer layer : layerStack.all()) if (layer.kind() == MapLayer.Kind.FENCE) fences.add(layer);
        BooleanSupplier fencesVisible = () -> !fences.isEmpty() && fences.get(0).isVisible();
        Runnable toggleFences = () -> { boolean v = !fences.get(0).isVisible(); for (MapLayer f : fences) f.setVisible(v); };
        for (MapLayer layer : layerStack.all()) {
            boolean isFence = layer.kind() == MapLayer.Kind.FENCE;
            BooleanSupplier vis = isFence ? fencesVisible : layer::isVisible;
            Runnable toggle = isFence ? toggleFences : () -> layer.setVisible(!layer.isVisible());
            menu.add(new MenuEntry(layer.name(), () -> activateStackLayer(layer), vis, toggle));
        }
        BooleanSupplier bushesVisible = () -> visibility.bushes;
        Runnable toggleBushes = () -> visibility.bushes = !visibility.bushes;
        menu.add(new MenuEntry("Tall grass", () -> selectBush(bushSheet("tallgrass"), 0), bushesVisible, toggleBushes));
        menu.add(new MenuEntry("Wheat", () -> selectBush(bushSheet("wheat"), BushAutotiler.SPAN), bushesVisible, toggleBushes));
        menu.add(new MenuEntry("Young wheat", () -> selectBush(bushSheet("youngwheat"), 2 * BushAutotiler.SPAN), bushesVisible, toggleBushes));
        // Rocks/ore and reeds share one decoration grid but hide independently — each gets its own eye keyed by
        // its TilePalette sheet, so toggling one no longer blanks the other.
        int oreSheet = TilePalette.indexOf("tilesets/ore.png");
        int reedSheet = TilePalette.indexOf("tilesets/reed.png");
        menu.add(new MenuEntry("Rocks & ore", () -> picker.open(oreSheet),
                () -> visibility.isDecorationSheetVisible(oreSheet), () -> visibility.toggleDecorationSheet(oreSheet)));
        menu.add(new MenuEntry("Reeds", () -> picker.open(reedSheet),
                () -> visibility.isDecorationSheetVisible(reedSheet), () -> visibility.toggleDecorationSheet(reedSheet)));
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
        if (input.isJustPressed(InputAction.TOGGLE_HITBOXES)) Debug.showHitboxes = !Debug.showHitboxes;

        int mx = Gdx.input.getX(), my = Gdx.input.getY();
        int w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        boolean overUi = picker.isOpen() || panel.contains(mx, my, h);

        if (picker.isOpen()) {
            // Drag-select in the modal picker: press starts a rectangle, drag grows it, release stamps it. The
            // popup stays open for the whole press (closing on release), so nothing falls through to the map.
            if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) picker.onPress(mx, my, w, h);
            else if (Gdx.input.isButtonPressed(Input.Buttons.LEFT)) picker.onDrag(mx, my, w, h);
            else if (picker.isDragging()) {
                SheetPicker.Selection sel = picker.onRelease();
                if (sel != null) applySelection(sel);
            }
        } else if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            if (isFenceActive() && gateButtonContains(mx, my, w)) {
                openGatePicker();
                consumeClick = true; // a button press, not a map paint stroke
            } else {
                EditorPanel.Hit hit = panel.handleClick(mx, my, h, menu.size());
                if (hit.kind != EditorPanel.HitKind.NONE) consumeClick = true; // hit a row/eye/tab, not the map
                switch (hit.kind) {
                    case LAYER -> { activeMenuRow = hit.index; pendingLayer = null; pendingGrass = false; menu.get(hit.index).activate.run(); }
                    case EYE -> menu.get(hit.index).toggleVisible.run();
                    default -> { } // TAB / PANEL / NONE
                }
            }
        }
        // A UI click keeps LEFT held for several frames; don't start painting until it's released (the picker may
        // have closed / the panel collapsed, so the cursor is no longer "over UI" but the same press is ongoing).
        if (!Gdx.input.isButtonPressed(Input.Buttons.LEFT)) consumeClick = false;
        if (!overUi) {
            // Left paints the active brush continuously while held; right-click erases the active layer only.
            if (Gdx.input.isButtonPressed(Input.Buttons.LEFT) && !consumeClick) paintAt(mx, my);
            if (Gdx.input.isButtonPressed(Input.Buttons.RIGHT)) eraseAt(mx, my);
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

    /** Activate a stack layer: auto-orienting layers (corner soil/sand, fence connections) paint immediately
     *  on click — a fence starts in auto mode, with a separate GATE button to place gate pieces. Plain (props)
     *  layers open the picker to choose a tile. */
    private void activateStackLayer(MapLayer layer) {
        if (layer.kind() == MapLayer.Kind.CORNER || layer.kind() == MapLayer.Kind.FENCE) {
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
        clearStamp();
    }

    /** Route a rectangle picked from the popup to whatever opened it: the Grass layer (a single detail tile for
     *  the grass background), a fence layer via the GATE button (a gate 16–18 placed as-is, else back to
     *  auto-orient), a plain props layer (its picked tile + optional multi-tile stamp), or the generic
     *  decoration layer. A 1×1 pick stays a single-tile brush; a larger rectangle becomes a {@link #stampIds
     *  stamp}. */
    private void applySelection(SheetPicker.Selection sel) {
        int firstId = sel.topLeftId();
        int tileNum = TilePalette.tileIndexOf(firstId) + 1; // 1-based tile within the sheet
        if (pendingGrass) {                  // a grass-detail tile for the base grass background
            pendingGrass = false;
            mode = BrushMode.GRASS;
            grassBrush = tileNum;            // grass value 1..3 == grass-sheet local tile + 1
            brushTileId = firstId;           // drives the swatch/ghost preview
            clearStamp();
        } else if (pendingLayer != null) {
            MapLayer l = pendingLayer;
            pendingLayer = null;
            setActiveLayer(l); // clears any stamp; mode = LAYER
            if (l.kind() == MapLayer.Kind.FENCE) {
                // From the GATE button: a gate piece (16–18) is placed as-is, anything else returns to auto-orient.
                layerBrush = FenceAutotiler.isGate(tileNum) ? tileNum : 0;
            } else { // PLAIN (props): supports a rectangular stamp
                layerBrush = tileNum;
                brushTileId = firstId; // top-left tile drives the swatch/preview
                if (!sel.isSingle()) setStamp(sel);
            }
        } else { // generic decoration tile layer
            brushTileId = firstId;
            mode = BrushMode.TILE;
            clearStamp();
            if (!sel.isSingle()) setStamp(sel);
        }
    }

    private void setStamp(SheetPicker.Selection sel) {
        stampIds = sel.ids; stampCols = sel.cols; stampRows = sel.rows;
    }

    private void clearStamp() { stampIds = null; stampCols = 0; stampRows = 0; }

    /** Stamp the captured rectangle with its top-left anchored at cell (col,row), into the decoration layer
     *  ({@code layer == null}) or a plain stack layer. Cells off the world edge are skipped. */
    private void stampInto(int col, int row, MapLayer layer) {
        for (int dr = 0; dr < stampRows; dr++) {
            for (int dc = 0; dc < stampCols; dc++) {
                int c = col + dc, r = row + dr;
                if (!decoration.inBounds(c, r)) continue;
                int id = stampIds[dr * stampCols + dc];
                if (layer == null) decoration.set(c, r, id);
                else layer.paint(c, r, TilePalette.tileIndexOf(id) + 1);
            }
        }
    }

    /** Choose a grass-detail tile for the base grass background: open the picker on the grass sheet. The picked
     *  tile becomes the {@link #grassBrush} that {@link #paintAt} stamps into the grass layer. */
    private void selectGrass() {
        pendingGrass = true;
        pendingLayer = null;
        picker.open(GRASS_SHEET);
    }

    /** Switch to the bush brush for a sheet, previewing its centre tile (1-based index 11) in the swatch/ghost. */
    private void selectBush(int sheetIndex, int base) {
        mode = BrushMode.BUSH;
        bushBase = base;
        brushTileId = TilePalette.pack(sheetIndex, 10); // 0-based 10 == tile 11, the clump centre
        clearStamp();
    }

    /** Paint the current brush at the cell under the cursor. */
    private void paintAt(int screenX, int screenY) {
        var wp = camera.unproject(screenX, screenY);
        int col = decoration.colAtWorldX(wp.x), row = decoration.rowAtWorldY(wp.y);
        if (!decoration.inBounds(col, row)) return;
        switch (mode) {
            case GRASS -> grass.set(col, row, grassBrush); // stamp a detail tile (1..3) into the grass background
            case TILE -> {
                if (stampIds != null) stampInto(col, row, null);
                else if (TilePalette.isValid(brushTileId)) decoration.set(col, row, brushTileId);
            }
            case SOIL -> soil.till(col, row);
            case BUSH -> bushAutotiler.paint(col, row, bushBase);
            case LAYER -> {
                MapLayer l = layerStack.active();
                if (l != null) {
                    if (stampIds != null) stampInto(col, row, l);
                    else l.paint(col, row, layerBrush);
                }
            }
            case NONE -> { }
        }
    }

    /** Erase the cell under the cursor on the <b>active layer only</b> — each layer is independent, so erasing
     *  a sand tile doesn't disturb the fence or decoration in the same cell. */
    private void eraseAt(int screenX, int screenY) {
        var wp = camera.unproject(screenX, screenY);
        int col = decoration.colAtWorldX(wp.x), row = decoration.rowAtWorldY(wp.y);
        if (!decoration.inBounds(col, row)) return;
        switch (mode) {
            case GRASS -> grass.set(col, row, 0); // clear the grass-detail tile back to plain base
            case TILE -> decoration.set(col, row, TilePalette.EMPTY);
            case SOIL -> soil.clear(col, row);
            case BUSH -> bushAutotiler.erase(col, row);
            case LAYER -> { MapLayer l = layerStack.active(); if (l != null) l.erase(col, row); }
            case NONE -> { }
        }
    }

    @Override
    public void render(SpriteBatch batch) {
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        worldRenderer.renderForEditor(batch, camera);
        // Dev hitbox overlay (F2): same shared resolver as play (no player here); thickness tracks zoom.
        if (Debug.showHitboxes) debugOverlay.render(batch, camera, collisionResolver, null, 2f * zoom);
        if (!picker.isOpen()) renderHover(batch, h);
        renderPanel(batch, w, h);
        if (!picker.isOpen()) renderBrushSwatch(batch, w);
        if (!picker.isOpen() && isFenceActive()) renderGateButton(batch, w);
        picker.render(batch, camera.hudCombined(), w, h, brushTileId);
    }

    private void renderPanel(SpriteBatch batch, float w, float h) {
        String[] names = new String[menu.size()];
        boolean[] visible = new boolean[menu.size()];
        for (int i = 0; i < menu.size(); i++) {
            names[i] = menu.get(i).name;
            visible[i] = menu.get(i).visible.getAsBoolean();
        }
        panel.render(batch, camera.hudCombined(), w, h, names, activeMenuRow, visible);
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
        if (stampIds != null) {
            // ghost the whole rectangular stamp footprint, anchored top-left at the hovered cell
            batch.setColor(1f, 1f, 1f, 0.5f);
            for (int dr = 0; dr < stampRows; dr++) {
                for (int dc = 0; dc < stampCols; dc++) {
                    TextureRegion reg = palette.region(stampIds[dr * stampCols + dc]);
                    if (reg != null) batch.draw(reg, (col + dc) * tile, (row + dr) * tile, tile, tile);
                }
            }
            batch.setColor(Color.WHITE);
            outline(batch, wx, wy, stampCols * tile, stampRows * tile);
        } else {
            TextureRegion preview = previewRegion();
            if (preview != null) {
                batch.setColor(1f, 1f, 1f, 0.5f);
                batch.draw(preview, wx, wy, tile, tile);
                batch.setColor(Color.WHITE);
            }
            outline(batch, wx, wy, tile, tile);
        }
        batch.end();
    }

    /** Draw a yellow rectangle outline (in the active batch) thick enough to stay visible when zoomed out. */
    private void outline(SpriteBatch batch, float x, float y, float w, float h) {
        float t = 2f * zoom;
        batch.setColor(1f, 1f, 0.3f, 0.9f);
        batch.draw(whiteRegion, x, y, w, t);
        batch.draw(whiteRegion, x, y + h - t, w, t);
        batch.draw(whiteRegion, x, y, t, h);
        batch.draw(whiteRegion, x + w - t, y, t, h);
        batch.setColor(Color.WHITE);
    }

    /** The brush's tile region for the swatch + hover ghost, or null for auto layers (sand) that have no
     *  single tile. */
    private TextureRegion previewRegion() {
        if (mode == BrushMode.TILE || mode == BrushMode.BUSH || mode == BrushMode.GRASS) return palette.region(brushTileId);
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
        if (mode == BrushMode.GRASS) label = "GRASS";
        else if (mode == BrushMode.TILE) label = "TILE";
        else if (mode == BrushMode.BUSH) label = "BUSH";
        else if (mode == BrushMode.SOIL) label = "SOIL";
        else if (mode == BrushMode.LAYER) {
            MapLayer l = layerStack.active();
            label = l != null ? l.name().toUpperCase() : "LAYER";
        }
        else return; // NONE
        if (stampIds != null) label += " " + stampCols + "X" + stampRows; // e.g. "TILE 3X2"
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

    /** Is the active brush an editor fence layer? Fences are the only layer with gate pieces, so the GATE
     *  button is shown (and hit-tested) only for them. */
    private boolean isFenceActive() {
        MapLayer l = layerStack.active();
        return mode == BrushMode.LAYER && l != null && l.kind() == MapLayer.Kind.FENCE;
    }

    private static float gateBtnX(float screenW) { return screenW - 86f; } // aligns with the swatch box's left

    private boolean gateButtonContains(int x, int y, float screenW) {
        float bx = gateBtnX(screenW);
        return x >= bx && x < bx + GATE_BTN_W && y >= GATE_BTN_Y && y < GATE_BTN_Y + GATE_BTN_H;
    }

    /** Open the tile picker on the active fence layer's sheet so a gate piece (16–18) can be chosen. Picking a
     *  connective tile (1–15) instead returns the brush to auto-orient. */
    private void openGatePicker() {
        MapLayer l = layerStack.active();
        if (l == null) return;
        pendingLayer = l;
        pendingGrass = false;
        picker.open(l.sheetIndex());
    }

    /** A prominent button under the brush swatch (fence layers only) that opens the picker to choose a gate.
     *  Blue ("+ GATE") while painting auto-orienting fence; green ("GATE n") while a gate is the active brush
     *  (the swatch above previews the actual tile a click will place). */
    private void renderGateButton(SpriteBatch batch, float screenW) {
        boolean gateOn = FenceAutotiler.isGate(layerBrush);
        float bx = gateBtnX(screenW);
        batch.setProjectionMatrix(camera.hudCombined());
        batch.begin();
        if (gateOn) batch.setColor(0.26f, 0.55f, 0.30f, 0.98f); // green: a gate is selected
        else batch.setColor(0.22f, 0.45f, 0.72f, 0.98f);        // blue: click to pick a gate
        batch.draw(whiteRegion, bx, GATE_BTN_Y, GATE_BTN_W, GATE_BTN_H);
        batch.setColor(Color.WHITE);
        String label = gateOn ? ("GATE " + layerBrush) : "+ GATE";
        font.draw(batch, label, bx + 8f, GATE_BTN_Y + 10f, 12f, 12f, 11f);
        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        camera.resize(width, height);
        applyCamera();
    }

    @Override
    public void dispose() {
        Gdx.input.setInputProcessor(null); // PlayState polls input; don't leave the editor's scroll handler set
        white.dispose();
        debugOverlay.dispose();
    }
}
