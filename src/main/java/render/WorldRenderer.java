package render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import crops.CropField;
import crops.CropGeometry;
import crops.PlantedCrop;
import entities.PlayerCharacter;
import tools.ToolAction;
import tools.ToolActionSystem;
import world.DualGridSoil;
import world.TileLayer;

/**
 * Draws the world for the play screen: the grass layer, the dual-grid soil, the hover highlight, then the
 * crops, collidables (bushes) and the player depth-sorted around the player's feet — anything whose ground
 * line is below the player's feet draws in front, the rest behind. Owns the tile regions and the 1×1 white
 * pixel used for the highlight (created here, freed in {@link #dispose()}).
 *
 * <p>Pure presentation: it reads world state and the player but never mutates them.
 */
public final class WorldRenderer {
    // Bush/crop depth line. Tracks the collision box, which is raised by tile/8, so the player's resting
    // position stays on the "draw in front" side and bushes don't paint over him.
    private static final float DEPTH_OFFSET = 82.5f; // 94.5 - TILE/8

    private final TileLayer grass;
    private final DualGridSoil soil;
    private final TileLayer collision;
    private final CropField crops;
    private final CropGeometry cropGeometry;
    private final float tile;

    private final TextureRegion[] grassRegions, soilRegions, collisionRegions;
    private final Texture whiteTexture;
    private final TextureRegion whiteRegion;

    public WorldRenderer(TileLayer grass, DualGridSoil soil, TileLayer collision, CropField crops,
                         CropGeometry cropGeometry, float tile,
                         TextureRegion[] grassRegions, TextureRegion[] soilRegions,
                         TextureRegion[] collisionRegions) {
        this.grass = grass;
        this.soil = soil;
        this.collision = collision;
        this.crops = crops;
        this.cropGeometry = cropGeometry;
        this.tile = tile;
        this.grassRegions = grassRegions;
        this.soilRegions = soilRegions;
        this.collisionRegions = collisionRegions;

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        this.whiteTexture = new Texture(pm);
        pm.dispose();
        this.whiteRegion = new TextureRegion(whiteTexture);
    }

    /** One world pass: grass → soil → highlight → crops/bushes/player depth-sorted, between begin/end.
     *  Every layer is frustum-culled to {@code camera}'s visible rectangle, so the cost is the on-screen
     *  cells, not the whole map (the grass grid alone is 400×220). */
    public void render(SpriteBatch batch, GameCamera camera, PlayerCharacter player,
                       ToolActionSystem toolActions, int hoverCol, int hoverRow, boolean hoverPlantable) {
        Rectangle view = camera.worldViewBounds();
        float feetY = player.feetY();
        batch.setProjectionMatrix(camera.worldCombined());
        batch.begin();
        drawArray(batch, grass, grassRegions, view);
        drawSoil(batch, view);
        drawHighlight(batch, hoverCol, hoverRow, hoverPlantable);
        drawCrops(batch, feetY, false, view);     // crops rooted above the player -> behind
        drawCollision(batch, feetY, false, view); // bushes behind
        drawPlayer(batch, player, toolActions);
        drawCollision(batch, feetY, true, view);  // bushes in front
        drawCrops(batch, feetY, true, view);      // crops rooted at/below the player -> in front
        batch.end();
    }

    public void dispose() {
        whiteTexture.dispose();
    }

    private static int clamp(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }

    /** First cell-column whose tile could touch {@code worldX}, padded one cell each side and clamped. */
    private int minCol(float worldX, int cols) { return clamp((int) Math.floor(worldX / tile) - 1, 0, cols - 1); }

    private int maxCol(float worldX, int cols) { return clamp((int) Math.floor(worldX / tile) + 1, 0, cols - 1); }

    private void drawArray(SpriteBatch batch, TileLayer layer, TextureRegion[] regions, Rectangle view) {
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

    /** Soil nodes sit on cell corners, so the node grid is one wider/taller than the cells (0..cols). */
    private void drawSoil(SpriteBatch batch, Rectangle view) {
        TileLayer grid = soil.grid();
        int loCol = clamp((int) Math.floor(view.x / tile) - 1, 0, grid.cols());
        int hiCol = clamp((int) Math.floor((view.x + view.width) / tile) + 1, 0, grid.cols());
        int loRow = clamp((int) Math.floor(view.y / tile) - 1, 0, grid.rows());
        int hiRow = clamp((int) Math.floor((view.y + view.height) / tile) + 1, 0, grid.rows());
        soil.render(batch, soilRegions, loCol, hiCol, loRow, hiRow);
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

    /** Highlight the hovered tile: green when plantable, yellow when it holds a mature (harvestable) crop. */
    private void drawHighlight(SpriteBatch batch, int hoverCol, int hoverRow, boolean hoverPlantable) {
        if (hoverCol < 0 || hoverRow < 0) return;
        if (hoverPlantable) {
            batch.setColor(0.3f, 1f, 0.3f, 0.35f);
        } else {
            PlantedCrop crop = crops.at(hoverCol, hoverRow);
            if (crop == null || !crop.isMature(System.currentTimeMillis())) return;
            batch.setColor(1f, 0.95f, 0.3f, 0.35f);
        }
        batch.draw(whiteRegion, hoverCol * tile, hoverRow * tile, tile, tile);
        batch.setColor(Color.WHITE);
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
