package render;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import crops.CropField;
import crops.CropGeometry;
import crops.PlantedCrop;
import editor.TilePalette;
import entities.PlayerCharacter;
import tools.ToolAction;
import tools.ToolActionSystem;
import world.ChunkedTileLayer;
import world.DualGridSoil;
import world.ObjectLayer;
import world.PlacedObject;
import world.layer.LayerStack;
import world.layer.LayerVisibility;

import java.util.List;

/**
 * Draws the world for the play screen: the grass layer, the dual-grid soil, the watered-soil blobs, the
 * selection-frame highlight, then the crops, collidables (bushes) and the player depth-sorted around the
 * player's feet — anything whose ground line is below the player's feet draws in front, the rest behind.
 * All tile/blob/frame regions are sliced and owned by {@code PlayState} (textures live in its asset cache),
 * so this renderer holds no GL resources of its own.
 *
 * <p>Pure presentation: it reads world state and the player but never mutates them.
 */
public final class WorldRenderer {
    // Bush/crop depth line. Tracks the collision box, which is raised by tile/8, so the player's resting
    // position stays on the "draw in front" side and bushes don't paint over him.
    private static final float DEPTH_OFFSET = 82.5f; // 94.5 - TILE/8

    private final ChunkedTileLayer grass;
    private final DualGridSoil soil;
    private final ChunkedTileLayer collision;
    private final ChunkedTileLayer decoration; // editor-placed visual tiles (palette ids), flat above grass/soil
    private final CropField crops;
    private final ObjectLayer objects;         // free-placed props (boats, stalls, …), depth-sorted with the player
    private final CropGeometry cropGeometry;
    private final TilePalette palette;         // resolves decoration ids -> regions
    private final LayerStack layerStack;       // editor-authored layers (sand/sand-water ground, fence/props upright)
    private final LayerVisibility visibility;  // show/hide flags for the built-in soil/bushes/decoration layers
    private final float tile;

    private final TextureRegion[] grassRegions, soilRegions, collisionRegions;
    private final TextureRegion[] wateredRegions; // 1-based: the 4 wet-soil blob variants (index 0 unused)
    private final TextureRegion frameRegion;      // ui/frame.png — the tile-selection highlight

    public WorldRenderer(ChunkedTileLayer grass, DualGridSoil soil, ChunkedTileLayer collision, CropField crops,
                         CropGeometry cropGeometry, float tile,
                         TextureRegion[] grassRegions, TextureRegion[] soilRegions,
                         TextureRegion[] collisionRegions, TextureRegion[] wateredRegions,
                         TextureRegion frameRegion,
                         ChunkedTileLayer decoration, ObjectLayer objects, TilePalette palette,
                         LayerStack layerStack, LayerVisibility visibility) {
        this.grass = grass;
        this.soil = soil;
        this.collision = collision;
        this.crops = crops;
        this.cropGeometry = cropGeometry;
        this.tile = tile;
        this.grassRegions = grassRegions;
        this.soilRegions = soilRegions;
        this.collisionRegions = collisionRegions;
        this.wateredRegions = wateredRegions;
        this.frameRegion = frameRegion;
        this.decoration = decoration;
        this.objects = objects;
        this.palette = palette;
        this.layerStack = layerStack;
        this.visibility = visibility;
    }

    /** One world pass: grass → soil → watered blobs → highlight → crops/bushes/player depth-sorted, between
     *  begin/end. Every layer is frustum-culled to {@code camera}'s visible rectangle, so the cost is the
     *  on-screen cells, not the whole map (the grass grid alone is 400×220).
     *
     *  <p>{@code (highlightCol, highlightRow)} is the single cell to frame — the tile a held tool or seed
     *  would act on — or {@code (-1, -1)} for no highlight. The caller decides the cell (and whether to show
     *  one); the renderer just draws the frame there. */
    public void render(SpriteBatch batch, GameCamera camera, PlayerCharacter player,
                       ToolActionSystem toolActions, int highlightCol, int highlightRow) {
        Rectangle view = camera.worldViewBounds();
        float feetY = player.feetY();
        batch.setProjectionMatrix(camera.worldCombined());
        batch.begin();
        if (visibility.grass) drawArray(batch, grass, grassRegions, view);
        if (visibility.soil) { drawSoil(batch, view); drawWateredBlobs(batch, view); }
        drawDecoration(batch, view); // editor-placed tiles, flat above the ground (per-sheet visibility inside)
        layerStack.drawGround(batch, view, tile); // editor ground layers (sand / sand-water)
        drawHighlight(batch, highlightCol, highlightRow);
        drawCrops(batch, feetY, false, view);     // crops rooted above the player -> behind
        if (visibility.bushes) drawCollision(batch, feetY, false, view); // bushes behind
        drawObjects(batch, feetY, false, view);   // props behind
        layerStack.drawDepth(batch, view, tile, feetY, false); // editor upright layers behind (fence/props)
        drawPlayer(batch, player, toolActions);
        layerStack.drawDepth(batch, view, tile, feetY, true);  // editor upright layers in front
        drawObjects(batch, feetY, true, view);    // props in front
        if (visibility.bushes) drawCollision(batch, feetY, true, view); // bushes in front
        drawCrops(batch, feetY, true, view);      // crops rooted at/below the player -> in front
        batch.end();
    }

    /**
     * Draw the world for the editor: every ground layer plus all crops/bushes/props, with no player and no
     * tool highlight. With no feet position to split depth around, entities draw in a single back-to-front
     * layer order (bushes, props, crops) — good enough for an authoring view. Manages its own begin/end so
     * the editor can layer its own HUD overlays afterwards.
     */
    public void renderForEditor(SpriteBatch batch, GameCamera camera) {
        Rectangle view = camera.worldViewBounds();
        float feetY = Float.MAX_VALUE; // no player: feetY >= threshold is always true, so one "behind" pass draws all
        batch.setProjectionMatrix(camera.worldCombined());
        batch.begin();
        if (visibility.grass) drawArray(batch, grass, grassRegions, view);
        if (visibility.soil) { drawSoil(batch, view); drawWateredBlobs(batch, view); }
        drawDecoration(batch, view); // per-sheet visibility handled inside
        layerStack.drawGround(batch, view, tile);
        if (visibility.bushes) drawCollision(batch, feetY, false, view);
        drawObjects(batch, feetY, false, view);
        layerStack.drawDepth(batch, view, tile, feetY, false);
        drawCrops(batch, feetY, false, view);
        batch.end();
    }

    private static int clamp(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }

    /** First cell-column whose tile could touch {@code worldX}, padded one cell each side and clamped. */
    private int minCol(float worldX, int cols) { return clamp((int) Math.floor(worldX / tile) - 1, 0, cols - 1); }

    private int maxCol(float worldX, int cols) { return clamp((int) Math.floor(worldX / tile) + 1, 0, cols - 1); }

    private void drawArray(SpriteBatch batch, ChunkedTileLayer layer, TextureRegion[] regions, Rectangle view) {
        int loCol = minCol(view.x, layer.cols()), hiCol = maxCol(view.x + view.width, layer.cols());
        int loRow = minCol(view.y, layer.rows()), hiRow = maxCol(view.y + view.height, layer.rows());
        for (int j = loCol; j <= hiCol; j++) {
            for (int i = loRow; i <= hiRow; i++) {
                int v = layer.get(j, i);
                if (v >= 1 && v < regions.length && regions[v] != null) {
                    batch.draw(regions[v], j * tile, i * tile, tile, tile);
                }
            }
        }
    }

    /** Editor-placed decoration tiles: cell-aligned, resolved through the {@link TilePalette} so any sheet's
     *  tiles can sit in one grid. Drawn flat (no depth split) above the ground and below crops/bushes/props.
     *  Each source sheet (rocks/ore, reeds, …) hides independently via {@link LayerVisibility}. */
    private void drawDecoration(SpriteBatch batch, Rectangle view) {
        int loCol = minCol(view.x, decoration.cols()), hiCol = maxCol(view.x + view.width, decoration.cols());
        int loRow = minCol(view.y, decoration.rows()), hiRow = maxCol(view.y + view.height, decoration.rows());
        for (int j = loCol; j <= hiCol; j++) {
            for (int i = loRow; i <= hiRow; i++) {
                int id = decoration.get(j, i);
                if (id == TilePalette.EMPTY) continue;
                if (!visibility.isDecorationSheetVisible(TilePalette.sheetIndexOf(id))) continue;
                TextureRegion r = palette.region(id);
                if (r != null) batch.draw(r, j * tile, i * tile, tile, tile);
            }
        }
    }

    /** Free-placed props, depth-split around the player by their base line (same rule as the bushes). Each
     *  object is view-culled by its draw box; props whose region hasn't been built yet are skipped. */
    private void drawObjects(SpriteBatch batch, float feetY, boolean inFront, Rectangle view) {
        List<PlacedObject> all = objects.all();
        for (int i = 0; i < all.size(); i++) {
            PlacedObject o = all.get(i);
            if (o.region() == null) continue;
            if (o.worldX + o.drawW < view.x || o.worldX > view.x + view.width
                    || o.worldY + o.drawH < view.y || o.worldY > view.y + view.height) continue;
            boolean drawIt = inFront ? (feetY <= o.baseY()) : (feetY >= o.baseY());
            if (drawIt) batch.draw(o.region(), o.worldX, o.worldY, o.drawW, o.drawH);
        }
    }

    /** Soil nodes sit on cell corners, so the node grid is one wider/taller than the cells (0..cols). */
    private void drawSoil(SpriteBatch batch, Rectangle view) {
        ChunkedTileLayer grid = soil.grid();
        int loCol = clamp((int) Math.floor(view.x / tile) - 1, 0, grid.cols());
        int hiCol = clamp((int) Math.floor((view.x + view.width) / tile) + 1, 0, grid.cols());
        int loRow = clamp((int) Math.floor(view.y / tile) - 1, 0, grid.rows());
        int hiRow = clamp((int) Math.floor((view.y + view.height) / tile) + 1, 0, grid.rows());
        soil.render(batch, soilRegions, loCol, hiCol, loRow, hiRow);
    }

    /** Watered soil reads as a darker, damp patch: draw a translucent wet-soil blob over each watered cell
     *  (the soil shows through). The cell's random {@code decor} chooses one of the blob variants and a
     *  quarter-turn rotation — 4 patterns × 4 rotations = 16 looks — so a watered plot never looks tiled.
     *  Cell-aligned, same one-cell-padded window as the other layers. */
    private void drawWateredBlobs(SpriteBatch batch, Rectangle view) {
        int loCol = minCol(view.x, soil.grid().cols()), hiCol = maxCol(view.x + view.width, soil.grid().cols());
        int loRow = minCol(view.y, soil.grid().rows()), hiRow = maxCol(view.y + view.height, soil.grid().rows());
        int variants = wateredRegions.length - 1; // index 0 is unused (1-based slice)
        if (variants <= 0) return;
        for (int col = loCol; col <= hiCol; col++)
            for (int row = loRow; row <= hiRow; row++)
                if (soil.isWatered(col, row)) {
                    int decor = soil.wateredDecor(col, row);
                    TextureRegion blob = wateredRegions[1 + Math.floorMod(decor, variants)];
                    float rotation = ((decor >>> 8) & 3) * 90f; // a different bit-slice than the variant, so they're independent
                    batch.draw(blob, col * tile, row * tile, tile / 2f, tile / 2f, tile, tile, 1f, 1f, rotation);
                }
    }

    private void drawCollision(SpriteBatch batch, float feetY, boolean inFront, Rectangle view) {
        int loCol = minCol(view.x, collision.cols()), hiCol = maxCol(view.x + view.width, collision.cols());
        int loRow = minCol(view.y, collision.rows()), hiRow = maxCol(view.y + view.height, collision.rows());
        for (int j = loCol; j <= hiCol; j++) {
            for (int i = loRow; i <= hiRow; i++) {
                int v = collision.get(j, i);
                if (v < 1 || v >= collisionRegions.length || collisionRegions[v] == null) continue;
                float threshold = i * tile + DEPTH_OFFSET;
                boolean drawIt = inFront ? (feetY <= threshold) : (feetY >= threshold);
                if (drawIt) batch.draw(collisionRegions[v], j * tile, i * tile, tile, tile);
            }
        }
    }

    /** Frame the targeted cell with the {@code ui/frame.png} selection frame. {@code (-1,-1)} or any
     *  out-of-bounds cell draws nothing. */
    private void drawHighlight(SpriteBatch batch, int col, int row) {
        if (col < 0 || row < 0 || col >= crops.cols() || row >= crops.rows()) return;
        batch.draw(frameRegion, col * tile, row * tile, tile, tile);
    }

    /** Draw planted crops at their current growth stage, depth-split around the player like the bushes.
     *  Culled to the view, but with extra rows below (larger Y) since a crop's 2-tile sprite reaches up
     *  into the view from a root that may sit just past the bottom edge. */
    private void drawCrops(SpriteBatch batch, float feetY, boolean inFront, Rectangle view) {
        long now = System.currentTimeMillis();
        int loCol = minCol(view.x, crops.cols()), hiCol = maxCol(view.x + view.width, crops.cols());
        int loRow = clamp((int) Math.floor(view.y / tile) - 1, 0, crops.rows() - 1);
        int hiRow = clamp((int) Math.floor((view.y + view.height) / tile) + 2, 0, crops.rows() - 1);
        for (int j = loCol; j <= hiCol; j++) {
            for (int i = loRow; i <= hiRow; i++) {
                PlantedCrop crop = crops.at(j, i);
                if (crop == null) continue;
                // Centered on the cell, with the root anchor (cell center) sitting 1/4 tile above the
                // sprite's bottom. The post-harvest hole frame sits an extra 1/8 tile lower to line up.
                float y = cropGeometry.spriteTopY(i) + (crop.harvested ? tile / 8f : 0f);
                // Depth ground-line: the player goes in front of the crop once their feet pass ~2/3 down
                // the 2-tile-tall sprite.
                float threshold = cropGeometry.groundLineY(i);
                boolean drawIt = inFront ? (feetY <= threshold) : (feetY >= threshold);
                if (!drawIt) continue;
                TextureRegion r = crop.type.stage(crop.stage(now));
                batch.draw(r, cropGeometry.spriteX(j), y, cropGeometry.spriteWidth, cropGeometry.spriteHeight);
            }
        }
    }

    private void drawPlayer(SpriteBatch batch, PlayerCharacter player, ToolActionSystem toolActions) {
        ToolAction action = toolActions.current();
        if (action != null) {
            TextureRegion frame = action.frames[toolActions.currentDir()][toolActions.currentFrame()];
            batch.draw(frame, player.x() + action.offX, player.y() + action.offY,
                       action.drawSize, action.drawSize);
        } else {
            batch.draw(player.currentFrame(), player.x(), player.y(),
                       PlayerCharacter.SIZE, PlayerCharacter.SIZE);
        }
    }
}
