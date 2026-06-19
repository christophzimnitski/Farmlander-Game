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
import render.TextureSlicer;

import java.util.HashMap;
import java.util.Map;

/**
 * The screen-space inventory bar: the bar background, the selected-slot toggle, each slot's item icon, and
 * its stack amount drawn glyph-by-glyph from the bitmap font. Self-contained — give it the matrix and the
 * current inventory and it draws a complete HUD pass (its own {@code begin}/{@code end}).
 *
 * <p>Item icons are loaded on demand and cached (null cached too, so a missing icon isn't retried). Their
 * textures come from the shared {@link Assets} cache, so {@code PlayState.dispose} frees them with the rest.
 */
public final class InventoryHud {
    private final ItemRegistry itemRegistry;
    private final Assets assets; // icon textures live here; disposed by PlayState with everything else
    private final TextureRegion barRegion;
    private final TextureRegion toggleRegion;
    private final TextureRegion[] numberRegions;          // game_font.png: 1 row of 11 glyphs, 8x8 each
    private final Map<Integer, TextureRegion> iconCache = new HashMap<>();
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
                       boolean open) {
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        float invW = 1200, invH = invW / 10f; // 120
        float invX = w / 2f - invW / 2f, invY = h * 7f / 8f;
        float slot = invW / 10f;

        batch.setProjectionMatrix(hudCombined);
        batch.begin();
        // While open, dim the world first so the bars (drawn after) stay bright on top.
        if (open) {
            batch.setColor(0f, 0f, 0f, 0.55f);
            batch.draw(whiteRegion, 0, 0, w, h);
            batch.setColor(Color.WHITE);
        }
        // Hotbar: the bottom bar with the selection highlight.
        batch.draw(barRegion, invX, invY, invW, invH);
        batch.draw(toggleRegion, invX + selectedSlot * slot, invY, slot, invH);
        for (int i = 0; i < Inventory.HOTBAR && i < items.length; i++) {
            drawSlot(batch, items[i], amounts[i], invX + i * slot, invY, w, h, invW, invH, slot);
        }
        // Bag: the remaining slots as rows stacked above the hotbar (row 0 just above it, then upward).
        if (open) {
            int bagSlots = Math.max(0, items.length - Inventory.HOTBAR);
            int bagRows = (bagSlots + Inventory.HOTBAR - 1) / Inventory.HOTBAR; // ceil
            for (int row = 0; row < bagRows; row++) {
                float rowY = invY - (row + 1) * invH;
                batch.draw(barRegion, invX, rowY, invW, invH);
                for (int col = 0; col < Inventory.HOTBAR; col++) {
                    int idx = Inventory.HOTBAR + row * Inventory.HOTBAR + col;
                    if (idx >= items.length) break;
                    drawSlot(batch, items[idx], amounts[idx], invX + col * slot, rowY, w, h, invW, invH, slot);
                }
            }
        }
        batch.end();
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

    /** Free the procedurally-generated dim pixel (the bar/toggle/font/icon textures live in the Assets cache). */
    public void dispose() {
        whiteTexture.dispose();
    }

    /** HUD icon for an item, loaded on demand (cached, null cached too so we don't retry). The path comes
     *  from the ItemRegistry (which applies the food/<id>.png convention for crop items). */
    private TextureRegion icon(int id) {
        if (iconCache.containsKey(id)) return iconCache.get(id);
        TextureRegion region = null;
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
        iconCache.put(id, region);
        return region;
    }
}
