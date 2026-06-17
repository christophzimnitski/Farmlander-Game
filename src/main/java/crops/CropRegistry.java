package crops;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import crops.data.CropBlueprint;

/**
 * Builds the catalog of crop types from data blueprints ({@link CropBlueprint}) by slicing the growth
 * frames out of {@code tilesets/crops.png}.
 *
 * <p>The sheet is a grid of 32px-wide x 64px-tall tiles; each crop's {@link #STAGES} growth stages
 * are stacked vertically starting at {@code (blueprint.spriteColumn, blueprint.spriteTopRow)}. The
 * slice math (half-texel inset + y-flip) matches the rest of the renderer. <em>Where the blueprints
 * come from</em> — JSON file, built-in defaults, etc. — is the caller's concern (see
 * {@code crops.data.CropDataSource}); this class only turns blueprints into live {@link CropType}s.
 */
public final class CropRegistry {
    public static final int STAGES = 5;
    private static final int TILE_W = 32, TILE_H = 64;

    private final CropType[] types;

    /**
     * @param sheet              the crops spritesheet to slice frames from
     * @param blueprints         crop blueprints; each is placed at {@code types[blueprint.index]}
     * @param defaultStageMillis growth time per stage for any blueprint that doesn't specify its own
     */
    public CropRegistry(Texture sheet, CropBlueprint[] blueprints, long defaultStageMillis) {
        int size = 0;
        for (CropBlueprint b : blueprints) size = Math.max(size, b.index + 1);
        types = new CropType[size];

        float tw = sheet.getWidth(), th = sheet.getHeight(), pad = 0.5f;
        for (CropBlueprint blueprint : blueprints) {
            TextureRegion[] stages = new TextureRegion[STAGES];
            for (int s = 0; s < STAGES; s++) {
                int sx = blueprint.spriteColumn * TILE_W, sy = (blueprint.spriteTopRow + s) * TILE_H;
                TextureRegion r = new TextureRegion(sheet);
                r.setRegion((sx + pad) / tw, (sy + pad) / th,
                            (sx + TILE_W - pad) / tw, (sy + TILE_H - pad) / th);
                r.flip(false, true);
                stages[s] = r;
            }
            // null stageDurations -> CropType fills every stage with defaultStageMillis.
            long[] durations = blueprint.stageDurations;
            types[blueprint.index] = (durations != null)
                    ? new CropType(blueprint.index, blueprint.name, stages, durations)
                    : new CropType(blueprint.index, blueprint.name, stages, defaultStageMillis);
        }
    }

    public int count() {
        return types.length;
    }

    public CropType get(int index) {
        return (index >= 0 && index < types.length && types[index] != null) ? types[index] : null;
    }
}
