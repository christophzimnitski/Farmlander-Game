package ui;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * A tiny reusable renderer for the project's hand-sliced bitmap font ({@code ui/game_font.png}). The sheet
 * is a 12×14 grid of 8×8 glyphs: digits live in row 0 (1–9 at cells 1–9, {@code 0} at cell 10) and {@code A}–
 * {@code Z} at cells 41–66. Glyphs are Y-flipped to match the project's Y-down HUD camera.
 *
 * <p>Text is drawn upper-cased (the sheet's letters are caps); unknown characters (incl. space) advance
 * without drawing. Hand-rolled on purpose rather than libGDX's {@code BitmapFont} — text rendering was
 * already a decided-handmade part of this game. Extracted so the editor and the inventory HUD can share one
 * font instead of each re-slicing it.
 */
public final class PixelFont {
    private final TextureRegion[] digits = new TextureRegion[11]; // index = digit, except 0 at index 10
    private final TextureRegion[] letters = new TextureRegion[26]; // A..Z

    public PixelFont(Texture fontTexture) {
        for (int k = 0; k < 11; k++) {
            TextureRegion r = new TextureRegion(fontTexture, k * 8, 0, 8, 8);
            r.flip(false, true);
            digits[k] = r;
        }
        for (int i = 0; i < 26; i++) {
            int cell = 41 + i;
            TextureRegion r = new TextureRegion(fontTexture, (cell % 12) * 8, (cell / 12) * 8, 8, 8);
            r.flip(false, true);
            letters[i] = r;
        }
    }

    /** Region for a character (case-insensitive A–Z, 0–9), or {@code null} for anything else (incl. space). */
    public TextureRegion glyph(char c) {
        if (c >= 'a' && c <= 'z') c -= 32; // to upper
        if (c >= 'A' && c <= 'Z') return letters[c - 'A'];
        if (c >= '0' && c <= '9') return digits[c == '0' ? 10 : c - '0'];
        return null;
    }

    /** Width in pixels a string would occupy at the given advance. */
    public float width(String text, float advance) {
        return text.length() * advance;
    }

    /**
     * Draw {@code text} glyph-by-glyph starting at {@code (x,y)} (top-left in the Y-down HUD), each glyph
     * {@code glyphW}×{@code glyphH}, stepping {@code advance} per character. Uses the batch's current color
     * (set/reset it around the call to tint). Returns the x just past the last glyph.
     */
    public float draw(SpriteBatch batch, String text, float x, float y, float glyphW, float glyphH, float advance) {
        for (int i = 0; i < text.length(); i++) {
            TextureRegion g = glyph(text.charAt(i));
            if (g != null) batch.draw(g, x, y, glyphW, glyphH);
            x += advance;
        }
        return x;
    }
}
