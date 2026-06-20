package ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import core.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates seed-packet HUD icons on demand. The project has no seed art, so a seed item's icon is
 * composited here: a small procedural paper packet with the crop's produce sprite ({@code food/<id>.png})
 * drawn into a label window, so a seed reads as "<crop> in a packet" and is visually distinct from the bare
 * produce icon. A crop with no produce art gets a blank packet (logged once).
 *
 * <p>Each packet is built the first time its seed is shown and cached by seed id. GL/native-bound (Pixmap +
 * Texture), so it isn't unit-tested — like {@code CropRegistry}'s slicing, it's verified visually. The
 * generated textures are owned here and freed in {@link #dispose()}.
 *
 * <p>This is deliberately a quick, hand-tunable first cut: the packet is plain procedural geometry, meant to
 * be swapped for real art later.
 */
public final class SeedIconFactory {
    private static final int SIZE = 64; // square canvas; upscaled crisply (Nearest) into the HUD slot

    // Packet body and the label window the crop sprite sits in (pixels on the SIZE×SIZE canvas).
    private static final int BODY_X = 11, BODY_Y = 4, BODY_W = 42, BODY_H = 56;
    private static final int FLAP_H = 12;                 // glued top flap band
    private static final int WIN_X = 15, WIN_Y = 23, WIN_W = 34, WIN_H = 32; // crop "label" window
    private static final int OVERLAY_PAD = 2;             // inset of the crop sprite inside the window border

    // Kraft-paper packet palette (RGBA8888).
    private static final Color PAPER    = new Color(0xC9A66BFF);
    private static final Color PAPER_HI = new Color(0xDCC089FF);
    private static final Color BORDER   = new Color(0x6E4B27FF);
    private static final Color SEAM     = new Color(0x8A6231FF);
    private static final Color WINDOW   = new Color(0xF3EAD2FF);

    private final Map<Integer, TextureRegion> cache = new HashMap<>();
    private final List<Texture> owned = new ArrayList<>();

    /**
     * The packet icon for a seed, keyed by {@code seedId}. {@code overlayPath} is the crop's produce icon
     * ({@code food/<id>.png}) to draw on the packet, or {@code null}/missing for a blank packet. Built on
     * first request, cached thereafter.
     */
    public TextureRegion get(int seedId, String overlayPath) {
        if (cache.containsKey(seedId)) return cache.get(seedId);
        TextureRegion region = build(overlayPath);
        cache.put(seedId, region);
        return region;
    }

    private TextureRegion build(String overlayPath) {
        Pixmap pm = new Pixmap(SIZE, SIZE, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.SourceOver);
        drawPacket(pm);
        if (overlayPath != null) drawOverlay(pm, overlayPath);
        Texture tex = new Texture(pm);
        tex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pm.dispose();
        owned.add(tex);
        TextureRegion region = new TextureRegion(tex);
        region.flip(false, true); // HUD camera is y-down, like the file-loaded icons
        return region;
    }

    /** Paint the paper packet: body, a highlighted top flap with a serrated seam, a double border, and a
     *  light label window where the crop shows. */
    private void drawPacket(Pixmap pm) {
        pm.setColor(PAPER);
        pm.fillRectangle(BODY_X, BODY_Y, BODY_W, BODY_H);
        pm.setColor(PAPER_HI);
        pm.fillRectangle(BODY_X, BODY_Y, BODY_W, FLAP_H);
        pm.setColor(SEAM); // serrated seam (dashes) under the flap
        for (int x = BODY_X; x < BODY_X + BODY_W; x += 4) pm.fillRectangle(x, BODY_Y + FLAP_H, 2, 2);
        pm.setColor(BORDER);
        pm.drawRectangle(BODY_X, BODY_Y, BODY_W, BODY_H);
        pm.drawRectangle(BODY_X + 1, BODY_Y + 1, BODY_W - 2, BODY_H - 2);
        pm.setColor(WINDOW);
        pm.fillRectangle(WIN_X, WIN_Y, WIN_W, WIN_H);
        pm.setColor(BORDER);
        pm.drawRectangle(WIN_X, WIN_Y, WIN_W, WIN_H);
    }

    /** Draw the produce sprite into the label window, scaled to fit while preserving aspect ratio (bilinear
     *  for a clean downscale), centered. A missing/unreadable file leaves the window blank. */
    private void drawOverlay(Pixmap dst, String overlayPath) {
        Pixmap crop = null;
        try {
            crop = new Pixmap(Gdx.files.classpath(overlayPath));
            crop.setFilter(Pixmap.Filter.BiLinear);
            int areaX = WIN_X + OVERLAY_PAD, areaY = WIN_Y + OVERLAY_PAD;
            int areaW = WIN_W - 2 * OVERLAY_PAD, areaH = WIN_H - 2 * OVERLAY_PAD;
            float scale = Math.min(areaW / (float) crop.getWidth(), areaH / (float) crop.getHeight());
            int dw = Math.max(1, Math.round(crop.getWidth() * scale));
            int dh = Math.max(1, Math.round(crop.getHeight() * scale));
            int dx = areaX + (areaW - dw) / 2, dy = areaY + (areaH - dh) / 2;
            dst.drawPixmap(crop, 0, 0, crop.getWidth(), crop.getHeight(), dx, dy, dw, dh);
        } catch (Exception e) {
            Log.warn("SeedIconFactory", "no overlay for seed packet (" + overlayPath + ")", e);
        } finally {
            if (crop != null) crop.dispose();
        }
    }

    /** Free every generated packet texture. */
    public void dispose() {
        for (Texture t : owned) t.dispose();
        owned.clear();
        cache.clear();
    }
}
