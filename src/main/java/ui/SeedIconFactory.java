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
 * <p>{@code Seeds.png} is a vertical sheet of five identical 64×32 packet cells; we use the first. The returned
 * region is cropped to the packet's art bounds so it fills a HUD slot like a produce icon does.
 *
 * <p>Each packet is built the first time its seed is shown and cached by seed id. GL/native-bound (Pixmap +
 * Texture), so it isn't unit-tested — like {@code CropRegistry}'s slicing, it's verified visually. The base
 * packet pixmap and every generated texture are owned here and freed in {@link #dispose()}.
 */
public final class SeedIconFactory {
    private static final String PACKET_SHEET = "food_items/Seeds.png";
    private static final int FRAME_W = 64, FRAME_H = 32; // one packet cell (top of the sheet)

    // Packet art bounds within the frame — the returned icon is cropped to this so it fills a slot like produce.
    private static final int ART_X = 3, ART_Y = 2, ART_W = 27, ART_H = 28;
    // The label window where the crop sprite sits (frame pixel coords), measured from the art.
    private static final int WIN_X = 6, WIN_Y = 16, WIN_W = 15, WIN_H = 10;
    private static final int OVERLAY_PAD = 1; // inset of the crop sprite inside the window

    private final Map<Integer, TextureRegion> cache = new HashMap<>();
    private final List<Texture> owned = new ArrayList<>();
    private Pixmap basePacket; // frame 0 of the sheet, loaded once and copied per packet

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
        Pixmap pm = new Pixmap(FRAME_W, FRAME_H, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.drawPixmap(basePacket(), 0, 0); // straight copy of the packet art
        pm.setBlending(Pixmap.Blending.SourceOver);
        if (overlayPath != null) drawOverlay(pm, overlayPath);
        Texture tex = new Texture(pm);
        tex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pm.dispose();
        owned.add(tex);
        TextureRegion region = new TextureRegion(tex, ART_X, ART_Y, ART_W, ART_H); // crop to the packet art
        region.flip(false, true); // HUD camera is y-down, like the file-loaded icons
        return region;
    }

    /** Load the shared packet art (frame 0 of the sheet) once and keep it for copying. */
    private Pixmap basePacket() {
        if (basePacket == null) {
            Pixmap sheet = new Pixmap(Gdx.files.classpath(PACKET_SHEET));
            basePacket = new Pixmap(FRAME_W, FRAME_H, Pixmap.Format.RGBA8888);
            basePacket.setBlending(Pixmap.Blending.None);
            basePacket.drawPixmap(sheet, 0, 0, 0, 0, FRAME_W, FRAME_H); // top-left cell
            sheet.dispose();
        }
        return basePacket;
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
