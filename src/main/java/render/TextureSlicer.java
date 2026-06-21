package render;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * Stateless helpers for carving {@link TextureRegion}s out of the project's sprite sheets. Every region is
 * Y-flipped (the world camera is Y-down) and inset by half a texel on each edge: with Nearest filtering at
 * a fractional camera position, sampling exactly on a tile boundary can pick up the neighbouring atlas
 * tile and paint a 1px seam, so insetting keeps every sample inside the intended tile.
 *
 * <p>Lifted verbatim from {@code PlayState} so the slicing rules live in one named place. Pure given a
 * loaded {@link Texture} — no asset cache, no game state.
 */
public final class TextureSlicer {
    private static final float PAD = 0.5f; // half-texel inset on each edge

    private TextureSlicer() {}

    /** A whole texture as one Y-flipped region. */
    public static TextureRegion flippedFull(Texture texture) {
        TextureRegion r = new TextureRegion(texture);
        r.flip(false, true);
        return r;
    }

    /**
     * An arbitrary source rect ({@code x,y,w,h} in sheet pixels) as one Y-flipped, half-texel-inset region.
     * Used for free-placed objects (boats, stalls, …) cut from a sheet at a non-tile-aligned rect.
     */
    public static TextureRegion subRegion(Texture texture, int x, int y, int w, int h) {
        float tw = texture.getWidth(), th = texture.getHeight();
        TextureRegion r = new TextureRegion(texture);
        r.setRegion((x + PAD) / tw, (y + PAD) / th, (x + w - PAD) / tw, (y + h - PAD) / th);
        r.flip(false, true);
        return r;
    }

    /**
     * Slice a {@code rows}×{@code cols} grid of 32px tiles into a 1-based array (index 0 unused, so tile
     * values from the save/autotiler map straight to {@code regions[value]}).
     */
    public static TextureRegion[] slice(Texture texture, int rows, int cols) {
        float tw = texture.getWidth(), th = texture.getHeight();
        TextureRegion[] regions = new TextureRegion[rows * cols + 1];
        int k = 1;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int sx = col * 32, sy = row * 32;
                TextureRegion r = new TextureRegion(texture);
                r.setRegion((sx + PAD) / tw, (sy + PAD) / th,
                            (sx + 32 - PAD) / tw, (sy + 32 - PAD) / th);
                r.flip(false, true);
                regions[k++] = r;
            }
        }
        return regions;
    }

    /** Slice 64px action frames from a sheet: {@code frames[dir][f]} for the given direction rows. */
    public static TextureRegion[][] sliceActionRows(Texture texture, int[] directionRows, int frameCount) {
        float tw = texture.getWidth(), th = texture.getHeight();
        TextureRegion[][] out = new TextureRegion[directionRows.length][frameCount];
        for (int dir = 0; dir < directionRows.length; dir++) {
            int row = directionRows[dir];
            for (int f = 0; f < frameCount; f++) {
                TextureRegion r = new TextureRegion(texture);
                r.setRegion((f * 64 + PAD) / tw, (row * 64 + PAD) / th,
                            ((f + 1) * 64 - PAD) / tw, ((row + 1) * 64 - PAD) / th);
                r.flip(false, true);
                out[dir][f] = r;
            }
        }
        return out;
    }

    /** Slice oversized 128px action frames: {@code frames[dir][f]} from each direction's start row. */
    public static TextureRegion[][] sliceActionRows128(Texture texture, int[] directionStartRows, int frameCount) {
        float tw = texture.getWidth(), th = texture.getHeight();
        TextureRegion[][] out = new TextureRegion[directionStartRows.length][frameCount];
        for (int dir = 0; dir < directionStartRows.length; dir++) {
            float y = directionStartRows[dir] * 64f;
            for (int f = 0; f < frameCount; f++) {
                float x = f * 128f;
                TextureRegion r = new TextureRegion(texture);
                r.setRegion((x + PAD) / tw, (y + PAD) / th, (x + 128 - PAD) / tw, (y + 128 - PAD) / th);
                r.flip(false, true);
                out[dir][f] = r;
            }
        }
        return out;
    }

    /** Re-map each direction's frames into a new order (e.g. reverse and/or drop bad frames). */
    public static TextureRegion[][] reorder(TextureRegion[][] frames, int[] frameOrder) {
        TextureRegion[][] out = new TextureRegion[frames.length][frameOrder.length];
        for (int dir = 0; dir < frames.length; dir++)
            for (int i = 0; i < frameOrder.length; i++)
                out[dir][i] = frames[dir][frameOrder[i]];
        return out;
    }
}
