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
 * here from the shared packet sprite ({@code food_items/Seeds.png}) with the crop's produce sprite
 * ({@code food/<id>.png}) drawn into the packet's label window — so a seed reads as "&lt;crop&gt; in a packet"
 * and is visually distinct from the bare produce icon. A crop with no produce art gets a blank packet (logged).
 *
 * <p>{@code Seeds.png} is a vertical sheet of five identical 64×32 packet cells; we use the first. The packet is
 * centered on a square canvas with a transparent margin so it fills a HUD slot ~75%, like a produce icon does.
 *
 * <p>Each packet is built the first time its seed is shown and cached by seed id. GL/native-bound (Pixmap +
 * Texture), so it isn't unit-tested — like {@code CropRegistry}'s slicing, it's verified visually. The base
 * packet pixmap and every generated texture are owned here and freed in {@link #dispose()}.
 */
public final class SeedIconFactory {
    private static final String PACKET_SHEET = "food_items/Seeds.png";
    // Packet art bounds within the sheet's first 64×32 cell (the rest of the cell is empty).
    private static final int ART_X = 3, ART_Y = 2, ART_W = 27, ART_H = 28;
    // Square icon canvas with a transparent margin so the packet fills a slot ~75% like a produce icon does,
    // instead of edge-to-edge (which read as oversized next to the produce icons).
    private static final int CANVAS = 36;
    private static final int OX = (CANVAS - ART_W) / 2, OY = (CANVAS - ART_H) / 2; // packet offset on the canvas
    // The label window where the crop sprite sits, in packet-art coords (frame x6–20,y16–25 minus the art origin).
    private static final int WIN_X = 3, WIN_Y = 14, WIN_W = 15, WIN_H = 10;
    private static final int WIN_PAD = 1; // inset so the crop fills the window ~80%, not edge-to-edge

    private final Map<Integer, TextureRegion> cache = new HashMap<>();
    private final List<Texture> owned = new ArrayList<>();
    private Pixmap basePacket; // cropped packet art (ART_W×ART_H), loaded once and copied per packet

    /**
     * The packet icon for a seed, keyed by {@code seedId}. {@code overlayPath} is the crop's produce icon
     * ({@code food/<id>.png}) to draw on the packet, or {@code null}/missing for a blank packet. Built on
     * first request, cached thereafter.
     */
    public TextureRegion get(int seedId, String overlayPath) {
        TextureRegion cached = cache.get(seedId);
        if (cached != null) return cached;
        TextureRegion region = build(overlayPath);
        cache.put(seedId, region);
        return region;
    }

    private TextureRegion build(String overlayPath) {
        Pixmap pm = new Pixmap(CANVAS, CANVAS, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.drawPixmap(basePacket(), OX, OY); // packet centered on the canvas, leaving a margin
        pm.setBlending(Pixmap.Blending.SourceOver);
        if (overlayPath != null) drawOverlay(pm, overlayPath);
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

    /** Draw the produce sprite into the label window, scaled by its *visible* bounds (so the crop fills the
     *  window rather than the source PNG's transparent padding), aspect preserved, centered. Bilinear for a
     *  clean downscale. A missing/unreadable file leaves the window blank. */
    private void drawOverlay(Pixmap dst, String overlayPath) {
        Pixmap crop = null;
        try {
            crop = new Pixmap(Gdx.files.classpath(overlayPath));
            crop.setFilter(Pixmap.Filter.BiLinear);
            int[] bb = opaqueBounds(crop); // {x,y,w,h} of the drawn art
            int areaX = OX + WIN_X + WIN_PAD, areaY = OY + WIN_Y + WIN_PAD;
            int areaW = WIN_W - 2 * WIN_PAD, areaH = WIN_H - 2 * WIN_PAD;
            float scale = Math.min(areaW / (float) bb[2], areaH / (float) bb[3]);
            int dw = Math.max(1, Math.round(bb[2] * scale));
            int dh = Math.max(1, Math.round(bb[3] * scale));
            int dx = areaX + (areaW - dw) / 2, dy = areaY + (areaH - dh) / 2;
            dst.drawPixmap(crop, bb[0], bb[1], bb[2], bb[3], dx, dy, dw, dh);
        } catch (Exception e) {
            Log.warn("SeedIconFactory", "no overlay for seed packet (" + overlayPath + ")", e);
        } finally {
            if (crop != null) crop.dispose();
        }
    }

    /** Opaque bounding box {@code {x,y,w,h}} of a pixmap (RGBA8888 — alpha is the low byte), so the visible
     *  art is what gets scaled into the window. Falls back to the full image when fully transparent. */
    private static int[] opaqueBounds(Pixmap p) {
        int w = p.getWidth(), h = p.getHeight();
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((p.getPixel(x, y) & 0xff) != 0) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < 0) return new int[]{0, 0, w, h};
        return new int[]{minX, minY, maxX - minX + 1, maxY - minY + 1};
    }

    /** Free the base packet pixmap and every generated packet texture. */
    public void dispose() {
        for (Texture t : owned) t.dispose();
        owned.clear();
        cache.clear();
        if (basePacket != null) {
            basePacket.dispose();
            basePacket = null;
        }
    }
}
