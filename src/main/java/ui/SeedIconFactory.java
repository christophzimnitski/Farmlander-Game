package ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import core.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates seed-packet HUD icons on demand. A seed item has no art of its own, so its icon is composited
 * here from the shared packet sprite ({@code food_items/Seeds.png}) with a small crop sprite drawn into the
 * packet's label window — so a seed reads as "&lt;crop&gt; in a packet" and is visually distinct from the bare
 * produce icon.
 *
 * <p>The crop on the label comes from one of two sources, by crop index:
 * <ul>
 *   <li>For crops listed in {@link #FV_CELLS}, a single-specimen sprite cut from {@code food_items/fruits-veggies.png}
 *       (the top "single" row of that crop's band) — a clean lone crop rather than the multi-item produce pile.</li>
 *   <li>Otherwise the crop's produce icon ({@code food/<id>.png}); a missing file leaves a blank window (logged).</li>
 * </ul>
 * Only a handful of crops are mapped today (the starter seeds); the rest fall back. Extend {@link #FV_CELLS} to
 * add more.
 *
 * <p>{@code Seeds.png} is a vertical sheet of five identical 64×32 packet cells; we use the first. The packet is
 * centered on a square canvas with a transparent margin so it fills a HUD slot ~75%, like a produce icon does.
 *
 * <p>Each packet is built the first time its seed is shown and cached by seed id. GL/native-bound (Pixmap +
 * Texture), so it isn't unit-tested — like {@code CropRegistry}'s slicing, it's verified visually. The cached
 * source pixmaps and every generated texture are owned here and freed in {@link #dispose()}.
 */
public final class SeedIconFactory {
    private static final String PACKET_SHEET = "food_items/Seeds.png";
    private static final String CROP_SHEET = "food_items/fruits-veggies.png";
    // Packet art bounds within the sheet's first 64×32 cell (the rest of the cell is empty).
    private static final int ART_X = 3, ART_Y = 2, ART_W = 27, ART_H = 28;
    // Square icon canvas with a transparent margin so the packet fills a slot ~75% like a produce icon does,
    // instead of edge-to-edge (which read as oversized next to the produce icons).
    private static final int CANVAS = 36;
    private static final int OX = (CANVAS - ART_W) / 2, OY = (CANVAS - ART_H) / 2; // packet offset on the canvas
    // The label window where the crop sprite sits, in packet-art coords (frame x6–20,y16–25 minus the art origin).
    private static final int WIN_X = 3, WIN_Y = 14, WIN_W = 15, WIN_H = 10;
    private static final int WIN_PAD = 1; // inset so the crop fills the window ~80%, not edge-to-edge

    // Crop index → single-specimen cell {x,y,w,h} in fruits-veggies.png. The sheet is 8 bands of 160px, each
    // band's top row holding the lone-crop variant in 32px columns; a cell is that 32px column × ~32px tall.
    // Measured by eye (the sheet's ordering differs from the crop catalog) — only confident matches belong here.
    private static final Map<Integer, int[]> FV_CELLS = new HashMap<>();
    static {
        FV_CELLS.put(0,  new int[]{0,   0,   32, 32}); // Potato      (band 0, col 0)
        FV_CELLS.put(28, new int[]{896, 0,   32, 32}); // Pumpkin     (band 0, col 28)
        FV_CELLS.put(44, new int[]{448, 160, 32, 32}); // Cauliflower (band 1, col 14)
        FV_CELLS.put(6,  new int[]{768, 640, 32, 32}); // Carrot      (band 4, col 24)
    }

    private final Map<Integer, TextureRegion> cache = new HashMap<>();
    private final List<Texture> owned = new ArrayList<>();
    private Pixmap basePacket; // cropped packet art (ART_W×ART_H), loaded once and copied per packet
    private Pixmap cropSheet;  // fruits-veggies.png, loaded once for single-specimen overlays

    /**
     * The packet icon for a seed, keyed by {@code seedId}. The crop on the label is taken from
     * {@link #FV_CELLS} if {@code cropIndex} is mapped, else from {@code fallbackPath} (the produce icon, or
     * {@code null}/missing for a blank window). Built on first request, cached thereafter.
     */
    public TextureRegion get(int seedId, int cropIndex, String fallbackPath) {
        TextureRegion cached = cache.get(seedId);
        if (cached != null) return cached;
        TextureRegion region = build(cropIndex, fallbackPath);
        cache.put(seedId, region);
        return region;
    }

    private TextureRegion build(int cropIndex, String fallbackPath) {
        Pixmap pm = new Pixmap(CANVAS, CANVAS, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.drawPixmap(basePacket(), OX, OY); // packet centered on the canvas, leaving a margin
        pm.setBlending(Pixmap.Blending.SourceOver);
        int[] cell = FV_CELLS.get(cropIndex);
        if (cell != null) drawOverlay(pm, cropSheet(), cell[0], cell[1], cell[2], cell[3]);
        else if (fallbackPath != null) drawOverlayFromFile(pm, fallbackPath);
        Texture tex = new Texture(pm);
        tex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pm.dispose();
        owned.add(tex);
        TextureRegion region = new TextureRegion(tex);
        region.flip(false, true); // HUD camera is y-down, like the file-loaded icons
        return region;
    }

    /** Load the shared packet art (cropped from the sheet's first cell) once and keep it for copying. */
    private Pixmap basePacket() {
        if (basePacket == null) {
            Pixmap sheet = new Pixmap(Gdx.files.classpath(PACKET_SHEET));
            basePacket = new Pixmap(ART_W, ART_H, Pixmap.Format.RGBA8888);
            basePacket.setBlending(Pixmap.Blending.None);
            basePacket.drawPixmap(sheet, 0, 0, ART_X, ART_Y, ART_W, ART_H); // crop to the packet
            sheet.dispose();
        }
        return basePacket;
    }

    /** Load the single-specimen crop sheet once (only when a mapped crop is first requested). */
    private Pixmap cropSheet() {
        if (cropSheet == null) cropSheet = new Pixmap(Gdx.files.classpath(CROP_SHEET));
        return cropSheet;
    }

    /** Draw a produce file ({@code food/<id>.png}) into the label window; a missing/unreadable file leaves it
     *  blank (logged). */
    private void drawOverlayFromFile(Pixmap dst, String path) {
        Pixmap crop = null;
        try {
            crop = new Pixmap(Gdx.files.classpath(path));
            drawOverlay(dst, crop, 0, 0, crop.getWidth(), crop.getHeight());
        } catch (Exception e) {
            Log.warn("SeedIconFactory", "no overlay for seed packet (" + path + ")", e);
        } finally {
            if (crop != null) crop.dispose();
        }
    }

    /** Draw the {@code [sx,sy,sw,sh]} region of {@code src} into the label window, scaled by its *visible*
     *  bounds (so the crop fills the window rather than the source's transparent padding), aspect preserved,
     *  centered. Nearest-neighbour keeps hard pixel edges — at ~10px a bilinear downscale mushes low-contrast
     *  crops (potato/pumpkin) into a blur, whereas nearest stays crisp and readable. */
    private void drawOverlay(Pixmap dst, Pixmap src, int sx, int sy, int sw, int sh) {
        src.setFilter(Pixmap.Filter.NearestNeighbour);
        int[] bb = opaqueBounds(src, sx, sy, sw, sh); // {x,y,w,h} of the drawn art, in src coords
        int areaX = OX + WIN_X + WIN_PAD, areaY = OY + WIN_Y + WIN_PAD;
        int areaW = WIN_W - 2 * WIN_PAD, areaH = WIN_H - 2 * WIN_PAD;
        float scale = Math.min(areaW / (float) bb[2], areaH / (float) bb[3]);
        int dw = Math.max(1, Math.round(bb[2] * scale));
        int dh = Math.max(1, Math.round(bb[3] * scale));
        int dx = areaX + (areaW - dw) / 2, dy = areaY + (areaH - dh) / 2;
        dst.drawPixmap(src, bb[0], bb[1], bb[2], bb[3], dx, dy, dw, dh);
    }

    /** Opaque bounding box {@code {x,y,w,h}} of the {@code [x0,y0,w,h]} region of a pixmap (RGBA8888 — alpha is
     *  the low byte), so the visible art is what gets scaled into the window. Falls back to the whole region
     *  when fully transparent. */
    private static int[] opaqueBounds(Pixmap p, int x0, int y0, int w, int h) {
        int minX = x0 + w, minY = y0 + h, maxX = x0 - 1, maxY = y0 - 1;
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                if ((p.getPixel(x, y) & 0xff) != 0) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < x0) return new int[]{x0, y0, w, h};
        return new int[]{minX, minY, maxX - minX + 1, maxY - minY + 1};
    }

    /** Free the cached source pixmaps and every generated packet texture. */
    public void dispose() {
        for (Texture t : owned) t.dispose();
        owned.clear();
        cache.clear();
        if (basePacket != null) { basePacket.dispose(); basePacket = null; }
        if (cropSheet != null) { cropSheet.dispose(); cropSheet = null; }
    }
}
