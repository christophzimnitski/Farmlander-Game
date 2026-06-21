package ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Matrix4;
import core.Assets;
import core.Log;
import entities.Inventory;
import items.ItemRegistry;
import items.ItemType;
import render.TextureSlicer;

import java.util.HashMap;
import java.util.Map;

/**
 * The screen-space inventory bar: the bar background, the selected-slot toggle, each slot's item icon, and
 * its stack amount drawn glyph-by-glyph from the bitmap font. Self-contained — give it the matrix and the
 * current inventory and it draws a complete HUD pass (its own {@code begin}/{@code end}).
 *
 * <p>Item icons are loaded on demand and cached (null cached too, so a missing icon isn't retried). Most
 * come from the shared {@link Assets} cache (so {@code PlayState.dispose} frees them); seeds have no file
 * art, so their packet icons are composited by {@link SeedIconFactory} (owned + freed here).
 */
public final class InventoryHud {
    private final ItemRegistry itemRegistry;
    private final Assets assets; // icon textures live here; disposed by PlayState with everything else
    private final SeedIconFactory seedIcons = new SeedIconFactory(); // generated packet icons (no file art)
    private final TextureRegion barRegion;
    private final TextureRegion toggleRegion;
    private final TextureRegion[] numberRegions;          // game_font.png row 0: 11 digit/sign glyphs, 8x8 each
    private final TextureRegion[] letterRegions;          // game_font.png A-Z (cell 41..66 in its 12-wide grid)
    private final Map<Integer, TextureRegion> iconCache = new HashMap<>();
    // Hover tooltip text metrics (HUD = screen pixels). game_font.png is an 8x8-per-character grid, so each
    // glyph is drawn at a square 1:1 ratio (W == H) — no stretching. The advance is tighter than the draw
    // width (the ~1px glyph padding overlaps), the same trick the digit row uses.
    private static final float TIP_GLYPH_W = 20f, TIP_GLYPH_H = 20f, TIP_ADV = 15f, TIP_PAD = 8f;
    private final Texture whiteTexture;                   // 1x1 white pixel for the dim-the-world quad
    private final TextureRegion whiteRegion;

    public InventoryHud(Texture barTexture, Texture toggleTexture, Texture fontTexture,
                        ItemRegistry itemRegistry, Assets assets) {
        this.itemRegistry = itemRegistry;
        this.assets = assets;
        this.barRegion = TextureSlicer.flippedFull(barTexture);
        this.toggleRegion = TextureSlicer.flippedFull(toggleTexture);
        this.numberRegions = new TextureRegion[11];
        for (int k = 0; k < 11; k++) {
            TextureRegion r = new TextureRegion(fontTexture, k * 8, 0, 8, 8);
            r.flip(false, true);
            numberRegions[k] = r;
        }
        // A-Z live at cells 41..66 of the 12-wide font grid (so 'A' = row 3, col 5). Same white-on-transparent
        // glyphs as the digits, used for hover-name tooltips.
        this.letterRegions = new TextureRegion[26];
        for (int i = 0; i < 26; i++) {
            int cell = 41 + i;
            TextureRegion r = new TextureRegion(fontTexture, (cell % 12) * 8, (cell / 12) * 8, 8, 8);
            r.flip(false, true);
            letterRegions[i] = r;
        }
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        this.whiteTexture = new Texture(pm);
        pm.dispose();
        this.whiteRegion = new TextureRegion(whiteTexture);
    }

    /**
     * Draw the inventory HUD in screen space via {@code hudCombined}. The hotbar (the first
     * {@link Inventory#HOTBAR} slots) always draws at the bottom. When {@code open}, the world behind is
     * dimmed and the bag — the remaining slots as a 3×10 grid — is drawn stacked directly above the hotbar.
     */
    public void render(SpriteBatch batch, Matrix4 hudCombined, int[] items, int[] amounts, int selectedSlot,
                       boolean open, int dragItemId, int dragAmount, int dragSourceSlot, int hoverSlot,
                       float dragX, float dragY) {
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        float invW = 1200, invH = invW / 10f; // 120
        float invX = w / 2f - invW / 2f, invY = h * 7f / 8f;
        float slot = invW / 10f;
        float gap = invH; // one-tile gap between hotbar and bag (must match slotAt)
        int bagSlots = Math.max(0, items.length - Inventory.HOTBAR);
        int bagRows = (bagSlots + Inventory.HOTBAR - 1) / Inventory.HOTBAR; // ceil

        batch.setProjectionMatrix(hudCombined);
        batch.begin();
        // While open, dim the world first so the bars (drawn after) stay bright on top.
        if (open) {
            batch.setColor(0f, 0f, 0f, 0.55f);
            batch.draw(whiteRegion, 0, 0, w, h);
            batch.setColor(Color.WHITE);
        }
        // Bars: the hotbar always; the bag rows (stacked above, lifted by a one-tile gap so the grid reads as
        // a separate panel rather than a fourth hotbar row) only when open. Bag row 0 (slots 10..) is the TOP
        // row — the grid reads top-to-bottom, left-to-right — so the bottom-most row sits one gap above the bar.
        batch.draw(barRegion, invX, invY, invW, invH);
        if (open) {
            for (int row = 0; row < bagRows; row++) {
                batch.draw(barRegion, invX, bagRowY(invY, invH, gap, bagRows, row), invW, invH);
            }
        }
        // Highlights: while dragging, mark the picked-up source slot and the slot under the cursor (where it
        // would land); otherwise mark the persistent selection. The same toggle sprite is reused for all.
        if (dragItemId != 0) {
            drawHighlight(batch, dragSourceSlot, items.length, open, invX, invY, invH, slot, gap, bagRows);
            if (hoverSlot != dragSourceSlot) {
                drawHighlight(batch, hoverSlot, items.length, open, invX, invY, invH, slot, gap, bagRows);
            }
        } else {
            drawHighlight(batch, selectedSlot, items.length, open, invX, invY, invH, slot, gap, bagRows);
        }
        // Slot contents: hotbar always, bag when open. The drag source slot hides its picked-up units so the
        // dragged item shows only at the cursor (no duplicate sprite).
        for (int i = 0; i < Inventory.HOTBAR && i < items.length; i++) {
            drawHeldAware(batch, items, amounts, i, dragSourceSlot, dragAmount,
                          invX + i * slot, invY, w, h, invW, invH, slot);
        }
        if (open) {
            for (int row = 0; row < bagRows; row++) {
                float rowY = bagRowY(invY, invH, gap, bagRows, row);
                for (int col = 0; col < Inventory.HOTBAR; col++) {
                    int idx = Inventory.HOTBAR + row * Inventory.HOTBAR + col;
                    if (idx >= items.length) break;
                    drawHeldAware(batch, items, amounts, idx, dragSourceSlot, dragAmount,
                                  invX + col * slot, rowY, w, h, invW, invH, slot);
                }
            }
        }
        // Floating dragged item: the whole picked-up group (icon + count) trails the cursor, centred.
        if (open && dragItemId != 0) {
            drawSlot(batch, dragItemId, dragAmount, dragX - slot / 2f, dragY - invH / 2f, w, h, invW, invH, slot);
        }
        // Hover tooltip: the name of the item under the cursor — the hotbar always, the bag when open. Skipped
        // mid-drag (the floating item already shows what's in hand).
        if (dragItemId == 0) {
            float mx = Gdx.input.getX(), my = Gdx.input.getY();
            int hover = slotAt(mx, my, items.length, open);
            if (hover >= 0 && items[hover] != 0) {
                ItemType t = itemRegistry.get(items[hover]);
                if (t != null && t.name != null) drawTooltip(batch, t.name, mx, my, w, h);
            }
        }
        batch.end();
    }

    /** Draw a small dark name plate near the cursor with the item's name (rendered in caps from the bitmap
     *  font), clamped to stay on screen. */
    private void drawTooltip(SpriteBatch batch, String name, float mx, float my, float screenW, float screenH) {
        String text = name.toUpperCase();
        float boxW = text.length() * TIP_ADV + 2 * TIP_PAD, boxH = TIP_GLYPH_H + 2 * TIP_PAD;
        float bx = mx + 18, by = my - boxH - 10;          // above-right of the cursor by default
        if (bx + boxW > screenW) bx = screenW - boxW;
        if (bx < 0) bx = 0;
        if (by < 0) by = my + 24;                          // flip below the cursor if it would clip the top
        if (by + boxH > screenH) by = screenH - boxH;
        batch.setColor(0.55f, 0.42f, 0.22f, 1f);           // thin border
        batch.draw(whiteRegion, bx - 2, by - 2, boxW + 4, boxH + 4);
        batch.setColor(0.06f, 0.07f, 0.09f, 0.94f);        // dark plate
        batch.draw(whiteRegion, bx, by, boxW, boxH);
        batch.setColor(Color.WHITE);
        drawText(batch, text, bx + TIP_PAD, by + TIP_PAD);
    }

    /** Draw an (already upper-cased) string glyph-by-glyph from the bitmap font; unknown chars (incl. space)
     *  advance without drawing. */
    private void drawText(SpriteBatch batch, String upper, float x, float y) {
        for (int i = 0; i < upper.length(); i++) {
            TextureRegion g = glyph(upper.charAt(i));
            if (g != null) batch.draw(g, x, y, TIP_GLYPH_W, TIP_GLYPH_H);
            x += TIP_ADV;
        }
    }

    /** Bitmap-font region for a character: A-Z and 0-9 (0 lives at digit index 10); null for anything else. */
    private TextureRegion glyph(char c) {
        if (c >= 'A' && c <= 'Z') return letterRegions[c - 'A'];
        if (c >= '0' && c <= '9') return numberRegions[c == '0' ? 10 : c - '0'];
        return null;
    }

    /** Screen Y (lower-left) of bag row {@code row}, where row 0 is the visual top and the last row sits one
     *  gap above the hotbar. Shared by render() and slotAt() so the grid and its hit-testing agree. */
    private static float bagRowY(float invY, float invH, float gap, int bagRows, int row) {
        return invY - (bagRows - row) * invH - gap;
    }

    /** Draw the selection toggle over a slot's cell — a hotbar slot always, a bag slot only when {@code open}.
     *  A negative / out-of-range index draws nothing. Shared so the persistent selection, the drag source, and
     *  the landing-spot all highlight identically. */
    private void drawHighlight(SpriteBatch batch, int slotIndex, int itemsLength, boolean open,
                               float invX, float invY, float invH, float slot, float gap, int bagRows) {
        if (slotIndex < 0 || slotIndex >= itemsLength) return;
        if (slotIndex < Inventory.HOTBAR) {
            batch.draw(toggleRegion, invX + slotIndex * slot, invY, slot, invH);
        } else if (open) {
            int bagIdx = slotIndex - Inventory.HOTBAR;
            int r = bagIdx / Inventory.HOTBAR, col = bagIdx % Inventory.HOTBAR;
            batch.draw(toggleRegion, invX + col * slot, bagRowY(invY, invH, gap, bagRows, r), slot, invH);
        }
    }

    /** Draw a slot, hiding the units currently picked up from the drag source so they don't double up with
     *  the floating cursor icon. */
    private void drawHeldAware(SpriteBatch batch, int[] items, int[] amounts, int idx, int dragSourceSlot,
                               int dragAmount, float slotLeft, float rowTop, float w, float h,
                               float invW, float invH, float slot) {
        int it = items[idx], am = amounts[idx];
        if (idx == dragSourceSlot) {
            am -= dragAmount;
            if (am <= 0) it = 0; // the whole slot is in hand — draw nothing here
        }
        drawSlot(batch, it, am, slotLeft, rowTop, w, h, invW, invH, slot);
    }

    /** Draw one slot's icon and stack amount. {@code slotLeft}/{@code rowTop} are the slot's lower-left in
     *  the bar; the inner offsets place the icon and the right-aligned digits within the cell. Empty slots
     *  (item 0) draw nothing. Shared by the hotbar and every bag row so they look identical. */
    private void drawSlot(SpriteBatch batch, int item, int amount, float slotLeft, float rowTop,
                          float w, float h, float invW, float invH, float slot) {
        if (item == 0) return;
        TextureRegion icon = icon(item);
        if (icon != null) {
            batch.draw(icon, slotLeft + w / 120f, rowTop + h / 80f, invW / 15f, invH / 1.5f);
        }
        // Draw the amount digit by digit (the font row is "-123456789 0 -", so digit d is at glyph index d,
        // except 0 which lives at index 10). This shows 10+ instead of a lone "0". Each glyph cell is drawn
        // glyphW wide but the digit only fills its middle ~50%, so advance by ~0.6*glyphW: cells overlap only
        // in their transparent padding, giving tight digits.
        String digits = Integer.toString(Math.max(0, amount));
        float glyphW = invW / 50f, adv = glyphW * 0.6f;
        float numX = slotLeft + w / 32f, numY = rowTop + h / 18f;
        // Right-align: the ones digit stays anchored at numX, extra digits grow to the left, so the ones
        // place lines up whether the amount is 1 or 2+ digits (9 -> 10 doesn't shift).
        for (int d = 0; d < digits.length(); d++) {
            int digit = digits.charAt(d) - '0';
            float x = numX - (digits.length() - 1 - d) * adv;
            batch.draw(numberRegions[digit == 0 ? 10 : digit], x, numY, glyphW, invH / 7f);
        }
    }

    /** Free the procedurally-generated textures owned here — the dim pixel and the seed packets (the
     *  bar/toggle/font/produce-icon textures live in the Assets cache, freed by PlayState). */
    public void dispose() {
        whiteTexture.dispose();
        seedIcons.dispose();
    }

    // --- layout / hit-testing (shared by render() and the mouse interaction) ---

    /** The HUD layout in screen pixels, recomputed each frame from the window size. Reused (single-threaded,
     *  read immediately) to avoid a per-frame allocation. {@code render()} and {@link #slotAt} must agree. */
    private static final class Geo { float invX, invY, invW, invH, slot, gap; }
    private final Geo geo = new Geo();

    private Geo geom() {
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        geo.invW = 1200f;
        geo.invH = geo.invW / 10f;
        geo.invX = w / 2f - geo.invW / 2f;
        geo.invY = h * 7f / 8f;
        geo.slot = geo.invW / 10f;
        geo.gap = geo.invH; // one-tile gap between hotbar and bag
        return geo;
    }

    private static boolean within(float px, float py, float x, float y, float w, float h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    /**
     * The inventory slot index under a screen-pixel point, or {@code -1} for the dim backdrop / outside. The
     * hotbar (first {@link Inventory#HOTBAR} slots) is always hit-testable; the bag rows only when {@code open}.
     */
    public int slotAt(float mx, float my, int itemsLength, boolean open) {
        Geo g = geom();
        if (within(mx, my, g.invX, g.invY, g.invW, g.invH)) {
            int col = (int) ((mx - g.invX) / g.slot);
            if (col >= 0 && col < Inventory.HOTBAR) return col;
        }
        if (open) {
            int bagSlots = Math.max(0, itemsLength - Inventory.HOTBAR);
            int bagRows = (bagSlots + Inventory.HOTBAR - 1) / Inventory.HOTBAR;
            for (int row = 0; row < bagRows; row++) {
                float rowY = bagRowY(g.invY, g.invH, g.gap, bagRows, row);
                if (within(mx, my, g.invX, rowY, g.invW, g.invH)) {
                    int col = (int) ((mx - g.invX) / g.slot);
                    if (col >= 0 && col < Inventory.HOTBAR) {
                        int idx = Inventory.HOTBAR + row * Inventory.HOTBAR + col;
                        if (idx < itemsLength) return idx;
                    }
                }
            }
        }
        return -1;
    }

    /** HUD icon for an item, resolved on demand (cached, null cached too so we don't retry). Seeds get a
     *  generated packet icon (the crop overlaid on a paper packet); everything else loads from the
     *  ItemRegistry's icon path (the food/<id>.png convention for produce, a swatch for tools). */
    private TextureRegion icon(int id) {
        if (iconCache.containsKey(id)) return iconCache.get(id);
        TextureRegion region;
        if (itemRegistry.cropIndexFor(id) >= 0) { // a seed: composite a packet (caches in the factory too)
            region = seedIcons.get(id, itemRegistry.seedOverlayPath(id));
        } else {
            region = null;
            String path = itemRegistry.iconPath(id);
            try {
                if (path == null) { iconCache.put(id, null); return null; }
                Texture t = assets.get(path); // owned + deduped by the Assets cache
                region = new TextureRegion(t);
                region.flip(false, true);
            } catch (Exception e) {
                // Missing/unreadable icon art — cache the null so we don't retry, but surface which one.
                Log.warn("InventoryHud", "no icon for item " + id + " (" + path + ")", e);
            }
        }
        iconCache.put(id, region);
        return region;
    }
}
