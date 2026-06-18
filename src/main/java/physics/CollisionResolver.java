package physics;

import crops.CropField;
import crops.CropGeometry;
import crops.PlantedCrop;
import world.TileLayer;

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

    private final TileLayer collision;
    private final CropField crops;
    private final CropGeometry cropGeometry;
    private final float tile;
    private final float collidableRaise; // tile/8: collidables are raised so the player rests at their base
    private final float err;             // nudge past the box edge so re-collision doesn't stick

    public CollisionResolver(TileLayer collision, CropField crops, CropGeometry cropGeometry,
                             float tile, float err) {
        this.collision = collision;
        this.crops = crops;
        this.cropGeometry = cropGeometry;
        this.tile = tile;
        this.collidableRaise = tile / 8f;
        this.err = err;
    }

    /** A solid (blocking) collidable occupies this cell — present and not one of the walkable variants. */
    public boolean isSolid(int col, int row) {
        return blocks(collision.get(col, row));
    }

    /** Would the given world-space hitbox overlap any solid tile or planted-crop footprint? */
    public boolean collidesAt(float hx, float hy, float hw, float hh) {
        for (int j = 0; j < collision.cols(); j++)
            for (int i = 0; i < collision.rows(); i++) {
                if (!blocks(collision.get(j, i))) continue;
                if (overlaps(hx, hy, hw, hh, j * tile, i * tile - collidableRaise, tile, tile)) return true;
            }
        for (int j = 0; j < crops.cols(); j++)
            for (int i = 0; i < crops.rows(); i++) {
                PlantedCrop crop = crops.at(j, i);
                if (crop == null || crop.harvested) continue;
                if (overlaps(hx, hy, hw, hh, cropGeometry.footprintX(j), cropGeometry.footprintY(i),
                             cropGeometry.footprintWidth, cropGeometry.footprintHeight)) return true;
            }
        return false;
    }

    /** Push the body out of every solid tile and crop footprint it currently overlaps (in scan order). */
    public void resolve(Body body) {
        for (int j = 0; j < collision.cols(); j++)
            for (int i = 0; i < collision.rows(); i++) {
                if (!blocks(collision.get(j, i))) continue;
                push(body, j * tile, i * tile - collidableRaise, tile, tile);
            }
        for (int j = 0; j < crops.cols(); j++)
            for (int i = 0; i < crops.rows(); i++) {
                PlantedCrop crop = crops.at(j, i);
                if (crop == null || crop.harvested) continue;
                push(body, cropGeometry.footprintX(j), cropGeometry.footprintY(i),
                     cropGeometry.footprintWidth, cropGeometry.footprintHeight);
            }
    }

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
