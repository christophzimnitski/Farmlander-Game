package ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Matrix4;
import core.Assets;
import core.Log;
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
    }

    /** Draw the inventory bar for the given slots and selection, in screen space via {@code hudCombined}. */
    public void render(SpriteBatch batch, Matrix4 hudCombined, int[] items, int[] amounts, int selectedSlot) {
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        float invW = 1200, invH = invW / 10f; // 120
        float invX = w / 2f - invW / 2f, invY = h * 7f / 8f;
        float slot = invW / 10f;

        batch.setProjectionMatrix(hudCombined);
        batch.begin();
        batch.draw(barRegion, invX, invY, invW, invH);
        batch.draw(toggleRegion, invX + selectedSlot * slot, invY, slot, invH);
        for (int i = 0; i < items.length; i++) {
            if (items[i] != 0) {
                TextureRegion icon = icon(items[i]);
                if (icon != null) {
                    batch.draw(icon, invX + i * slot + w / 120f, invY + h / 80f, invW / 15f, invH / 1.5f);
                }
                // Draw the amount digit by digit (the font row is "-123456789 0 -", so digit d is at
                // glyph index d, except 0 which lives at index 10). This shows 10+ instead of a lone "0".
                // Each glyph cell is drawn glyphW wide but the digit only fills its middle ~50%, so advance
                // by ~0.6*glyphW: the cells overlap only in their transparent padding, giving tight digits.
                String digits = Integer.toString(Math.max(0, amounts[i]));
                float glyphW = invW / 50f, adv = glyphW * 0.6f;
                float numX = invX + i * slot + w / 32f, numY = invY + h / 18f;
                // Right-align: the ones digit stays anchored at numX, extra digits grow to the left, so
                // the ones place lines up whether the amount is 1 or 2+ digits (9 -> 10 doesn't shift).
                for (int d = 0; d < digits.length(); d++) {
                    int digit = digits.charAt(d) - '0';
                    float x = numX - (digits.length() - 1 - d) * adv;
                    batch.draw(numberRegions[digit == 0 ? 10 : digit], x, numY, glyphW, invH / 7f);
                }
            }
        }
        batch.end();
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
