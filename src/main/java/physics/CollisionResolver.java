package physics;

import com.badlogic.gdx.math.Rectangle;
import crops.CropField;
import crops.CropGeometry;
import crops.PlantedCrop;
import world.ChunkedTileLayer;
import world.layer.LayerStack;

/**
 * AABB collision against the world: solid tiles on the collision layer and the footprints of planted
 * crops. Two services — a yes/no {@link #collidesAt} query (for pre-move checks) and {@link #resolve},
 * which pushes a {@link Body} back out of anything it overlaps. The push-out core, {@link #resolveBox},
 * is a pure static function so it's unit-testable with no world and no GL.
 *
 * <p>Lifted verbatim from {@code PlayState}: solid collidable tiles are raised by {@code tile/8} so the
 * player rests at their visual base, and a separated body is nudged {@code err} past the box edge so it
 * doesn't immediately re-collide and stick.
 */
public final class CollisionResolver {
    // Tile values that sit on the collision layer but are walkable (decorative variants of the bushes).
    private static final int[] NON_COLLIDABLE = {7, 8, 9, 25, 26, 27, 43, 44, 45};

    private final ChunkedTileLayer collision;
    private final CropField crops;
    private final CropGeometry cropGeometry;
    private final float tile;
    private final float collidableRaise; // tile/8: collidables are raised so the player rests at their base
    private final float err;             // nudge past the box edge so re-collision doesn't stick
    private final LayerStack layers;     // editor layers also contribute solidity (e.g. fences)

    public CollisionResolver(ChunkedTileLayer collision, CropField crops, CropGeometry cropGeometry,
                             float tile, float err, LayerStack layers) {
        this.collision = collision;
        this.crops = crops;
        this.cropGeometry = cropGeometry;
        this.tile = tile;
        this.collidableRaise = tile / 8f;
        this.err = err;
        this.layers = layers;
    }

    /** A solid (blocking) collidable occupies this cell — a bush variant, or a solid editor layer (fence). */
    public boolean isSolid(int col, int row) {
        return blocks(collision.get(col, row)) || layers.blocksAt(col, row);
    }

    /** Would the given world-space hitbox overlap any solid tile or planted-crop footprint? */
    public boolean collidesAt(float hx, float hy, float hw, float hh) {
        int loCol = clampCol((int) Math.floor(hx / tile) - 1);
        int hiCol = clampCol((int) Math.floor((hx + hw) / tile) + 1);
        int loRow = clampRow((int) Math.floor(hy / tile) - 1);
        int hiRow = clampRow((int) Math.floor((hy + hh) / tile) + 1);
        for (int j = loCol; j <= hiCol; j++)
            for (int i = loRow; i <= hiRow; i++) {
                if (blocks(collision.get(j, i))
                        && overlaps(hx, hy, hw, hh, j * tile, i * tile - collidableRaise, tile, tile)) return true;
                if (layers.blocksAt(j, i)
                        && overlaps(hx, hy, hw, hh, j * tile, i * tile, tile, tile)) return true;
                PlantedCrop crop = crops.at(j, i);
                if (crop != null && !crop.harvested
                        && overlaps(hx, hy, hw, hh, cropGeometry.footprintX(j), cropGeometry.footprintY(i),
                                    cropGeometry.footprintWidth, cropGeometry.footprintHeight)) return true;
            }
        return false;
    }

    /** Push the body out of every solid tile and crop footprint it currently overlaps (in scan order).
     *  Solid tiles first, then crops — same order as a full-grid scan, just limited to the cells the
     *  hitbox can reach (push-out is order-dependent, so the order is preserved exactly). */
    public void resolve(Body body) {
        int loCol = clampCol((int) Math.floor(body.hitboxX() / tile) - 1);
        int hiCol = clampCol((int) Math.floor((body.hitboxX() + body.hitboxW()) / tile) + 1);
        int loRow = clampRow((int) Math.floor(body.hitboxY() / tile) - 1);
        int hiRow = clampRow((int) Math.floor((body.hitboxY() + body.hitboxH()) / tile) + 1);
        for (int j = loCol; j <= hiCol; j++)
            for (int i = loRow; i <= hiRow; i++)
                if (blocks(collision.get(j, i))) push(body, j * tile, i * tile - collidableRaise, tile, tile);
        for (int j = loCol; j <= hiCol; j++)
            for (int i = loRow; i <= hiRow; i++)
                if (layers.blocksAt(j, i)) push(body, j * tile, i * tile, tile, tile);
        for (int j = loCol; j <= hiCol; j++)
            for (int i = loRow; i <= hiRow; i++) {
                PlantedCrop crop = crops.at(j, i);
                if (crop != null && !crop.harvested)
                    push(body, cropGeometry.footprintX(j), cropGeometry.footprintY(i),
                         cropGeometry.footprintWidth, cropGeometry.footprintHeight);
            }
    }

    /** Receives one blocking box (world coords) from {@link #forEachSolidBox}. */
    public interface SolidBoxConsumer { void box(float x, float y, float w, float h); }

    /** Visit every blocking box whose cell lies within {@code view} (world-space rectangle): collision-layer
     *  solids (raised by {@code tile/8} to their visual base), solid editor-stack cells (fences), and
     *  non-harvested crop footprints. These are the exact boxes {@link #collidesAt}/{@link #resolve} test, so
     *  a debug overlay can draw precisely what blocks the player. */
    public void forEachSolidBox(Rectangle view, SolidBoxConsumer sink) {
        int loCol = clampCol((int) Math.floor(view.x / tile));
        int hiCol = clampCol((int) Math.ceil((view.x + view.width) / tile));
        int loRow = clampRow((int) Math.floor(view.y / tile));
        int hiRow = clampRow((int) Math.ceil((view.y + view.height) / tile));
        for (int j = loCol; j <= hiCol; j++)
            for (int i = loRow; i <= hiRow; i++) {
                if (blocks(collision.get(j, i))) sink.box(j * tile, i * tile - collidableRaise, tile, tile);
                if (layers.blocksAt(j, i)) sink.box(j * tile, i * tile, tile, tile);
                PlantedCrop crop = crops.at(j, i);
                if (crop != null && !crop.harvested)
                    sink.box(cropGeometry.footprintX(j), cropGeometry.footprintY(i),
                             cropGeometry.footprintWidth, cropGeometry.footprintHeight);
            }
    }

    private int clampCol(int col) { return Math.max(0, Math.min(collision.cols() - 1, col)); }

    private int clampRow(int row) { return Math.max(0, Math.min(collision.rows() - 1, row)); }

    private void push(Body body, float bx, float by, float bw, float bh) {
        float[] d = resolveBox(body.hitboxX(), body.hitboxY(), body.hitboxW(), body.hitboxH(),
                               bx, by, bw, bh, err);
        if (d[0] != 0f || d[1] != 0f) body.move(d[0], d[1]);
    }

    /** AABB overlap test (hitbox vs box). */
    public static boolean overlaps(float hx, float hy, float hw, float hh,
                                   float bx, float by, float bw, float bh) {
        return hx < bx + bw && hx + hw > bx && hy < by + bh && hy + hh > by;
    }

    /**
     * Pure minimum-translation push-out along the smaller-overlap axis. Returns the {@code {dx,dy}} to add
     * to the hitbox so it clears the box (plus {@code err}); {@code {0,0}} when they don't overlap. The
     * displaced axis is the one whose overlap is smaller, and the sign moves away from the box.
     */
    public static float[] resolveBox(float hx, float hy, float hw, float hh,
                                     float bx, float by, float bw, float bh, float err) {
        float ix = Math.max(hx, bx), iy = Math.max(hy, by);
        float ix2 = Math.min(hx + hw, bx + bw), iy2 = Math.min(hy + hh, by + bh);
        if (ix >= ix2 || iy >= iy2) return new float[]{0f, 0f};
        float overlapW = ix2 - ix, overlapH = iy2 - iy;
        float pushH = overlapH + err, pushW = overlapW + err;
        float dx = 0f, dy = 0f;
        if (overlapW > pushH) dy = (iy > hy) ? -pushH : pushH;
        if (overlapH > pushW) dx = (ix > hx) ? -pushW : pushW;
        return new float[]{dx, dy};
    }

    /** A tile value blocks movement when it's present and not one of the walkable variants. */
    private static boolean blocks(int v) {
        if (v < 1) return false;
        for (int n : NON_COLLIDABLE) if (n == v) return false;
        return true;
    }
}
