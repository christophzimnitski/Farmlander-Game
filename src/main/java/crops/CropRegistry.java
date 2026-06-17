package crops;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * Builds the catalog of crop types by slicing {@code tilesets/crops.png}.
 *
 * <p>The sheet is a grid of 32px-wide x 64px-tall tiles: 32 columns (one per crop) and 15 rows
 * grouped into three 5-row bands. A crop's five growth stages are stacked vertically within its
 * band. The 67 crops map to the sheet as:
 * <ul>
 *   <li>crops 0..31  -> band rows 0..4,   column = index</li>
 *   <li>crops 32..63 -> band rows 5..9,   column = index-32</li>
 *   <li>crops 64..66 -> band rows 10..14, column = 29 + (index-64)</li>
 * </ul>
 * (This is the clean form of the old {@code makeCropsArrays}, dropping its duplicate/out-of-range
 * frame bugs.) Frames carry the same half-texel inset + y-flip as the rest of the renderer.
 */
public final class CropRegistry {
    public static final int CROP_COUNT = 67;
    public static final int STAGES = 5;
    private static final int TILE_W = 32, TILE_H = 64;

    private final CropType[] types = new CropType[CROP_COUNT];

    public CropRegistry(Texture sheet, long defaultStageMillis) {
        float tw = sheet.getWidth(), th = sheet.getHeight(), pad = 0.5f;
        for (int c = 0; c < CROP_COUNT; c++) {
            int group = c < 32 ? 0 : c < 64 ? 1 : 2;
            int bandRow = group * STAGES;                                   // 0, 5, 10
            int col = group == 0 ? c : group == 1 ? c - 32 : 29 + (c - 64); // sheet column

            TextureRegion[] stages = new TextureRegion[STAGES];
            for (int s = 0; s < STAGES; s++) {
                int sx = col * TILE_W, sy = (bandRow + s) * TILE_H;
                TextureRegion r = new TextureRegion(sheet);
                r.setRegion((sx + pad) / tw, (sy + pad) / th,
                            (sx + TILE_W - pad) / tw, (sy + TILE_H - pad) / th);
                r.flip(false, true);
                stages[s] = r;
            }
            types[c] = new CropType(c, "crop" + c, stages, defaultStageMillis);
        }
    }

    public int count() {
        return types.length;
    }

    public CropType get(int index) {
        return (index >= 0 && index < types.length) ? types[index] : null;
    }

    /** The crop whose seed/produce is the given inventory item id (100 + index), or null. */
    public CropType byItemId(int itemId) {
        return get(itemId - 100);
    }
}
