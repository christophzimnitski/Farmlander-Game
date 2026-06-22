package editor;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Matrix4;
import ui.PixelFont;

/**
 * The editor's retractable side panel (left edge), drawn in screen space. It lists every paintable tileset
 * (from {@link TilePalette}); clicking a row picks that sheet, which the editor opens in a tile-picker popup.
 * A little tab on the panel's outer edge hides/shows the whole panel so it's out of the way while you place.
 *
 * <p>Hand-rolled to match the rest of the game's UI (per the project's owned-tech preference): filled quads
 * from a 1×1 white texture and the shared {@link PixelFont}, no Scene2D. Coordinates are the Y-down HUD
 * space (origin top-left), the same convention the inventory HUD uses, so screen-pixel mouse positions index
 * it directly. The font and white pixel are owned by {@code EditorState} and shared with the picker.
 */
public final class EditorPanel {
    private static final float PANEL_W = 250f;
    private static final float TAB_W = 26f, TAB_H = 74f;
    private static final float TITLE_H = 42f;     // top title bar
    private static final float HEADER_H = 30f;    // "TILESETS" section header
    private static final float ROW_H = 28f;
    private static final float PAD = 12f;
    private static final float GLYPH = 15f, ADVANCE = 13f;

    /** What a click landed on, so the editor knows whether to consume it and what to do (open the picker for
     *  a tileset, toggle the tab, or nothing). */
    public enum Hit { NONE, PANEL, TAB, SHEET }

    private final PixelFont font;
    private final TextureRegion whiteRegion;

    private boolean collapsed = false;
    private int selectedSheet = -1; // last-clicked tileset row (index into TilePalette), -1 = none

    public EditorPanel(PixelFont font, TextureRegion whiteRegion) {
        this.font = font;
        this.whiteRegion = whiteRegion;
    }

    public boolean collapsed() { return collapsed; }

    public int selectedSheet() { return selectedSheet; }

    private float panelX() { return collapsed ? -PANEL_W : 0f; }

    private float rowsTop() { return TITLE_H + HEADER_H; }

    private void rect(SpriteBatch batch, float x, float y, float w, float h, float r, float g, float b, float a) {
        batch.setColor(r, g, b, a);
        batch.draw(whiteRegion, x, y, w, h);
        batch.setColor(Color.WHITE);
    }

    /** Draw the panel + its tab in HUD space. */
    public void render(SpriteBatch batch, Matrix4 hudCombined, float screenW, float screenH) {
        batch.setProjectionMatrix(hudCombined);
        batch.begin();
        float px = panelX();
        if (!collapsed) {
            rect(batch, px, 0, PANEL_W, screenH, 0.12f, 0.13f, 0.17f, 0.96f);          // panel body
            rect(batch, px, 0, PANEL_W, TITLE_H, 0.20f, 0.30f, 0.42f, 1f);             // title bar
            text(batch, "MAP EDITOR", px + PAD, PAD, 1f, 1f, 1f);
            rect(batch, px, TITLE_H, PANEL_W, HEADER_H, 0.17f, 0.18f, 0.23f, 1f);      // section header
            text(batch, "TILESETS", px + PAD, TITLE_H + 8f, 0.7f, 0.8f, 0.95f);
            for (int i = 0; i < TilePalette.sheetCount(); i++) {
                float ry = rowsTop() + i * ROW_H;
                if (i == selectedSheet) rect(batch, px, ry, PANEL_W, ROW_H, 0.24f, 0.40f, 0.26f, 1f);
                text(batch, TilePalette.sheet(i).name, px + PAD, ry + 7f, 0.9f, 0.92f, 0.9f);
            }
            // footer hint
            text(batch, "WASD PAN  - = ZOOM", px + PAD, screenH - 50f, 0.6f, 0.62f, 0.66f);
            text(batch, "F1 EXIT  RMB ERASE", px + PAD, screenH - 30f, 0.6f, 0.62f, 0.66f);
        }
        // Tab (always visible) on the panel's outer edge, with a small "grip" of bars.
        float tabX = px + PANEL_W, tabY = screenH / 2f - TAB_H / 2f;
        rect(batch, tabX, tabY, TAB_W, TAB_H, 0.20f, 0.30f, 0.42f, 1f);
        for (int k = 0; k < 3; k++) {
            rect(batch, tabX + 7f, tabY + 22f + k * 10f, TAB_W - 14f, 3f, 0.85f, 0.88f, 0.92f, 1f);
        }
        batch.end();
    }

    private void text(SpriteBatch batch, String s, float x, float y, float r, float g, float b) {
        batch.setColor(r, g, b, 1f);
        font.draw(batch, s, x, y, GLYPH, GLYPH, ADVANCE);
        batch.setColor(Color.WHITE);
    }

    /**
     * Resolve a screen-pixel click. Toggles the tab itself, records a clicked tileset row, and reports what
     * was hit so the editor can both react (open the picker on {@link Hit#SHEET}) and know whether the click
     * was consumed (anything but {@link Hit#NONE}) so it doesn't also paint the map.
     */
    public Hit handleClick(float x, float y, float screenH) {
        float px = panelX();
        float tabX = px + PANEL_W, tabY = screenH / 2f - TAB_H / 2f;
        if (within(x, y, tabX, tabY, TAB_W, TAB_H)) {
            collapsed = !collapsed;
            return Hit.TAB;
        }
        if (collapsed) return Hit.NONE; // body is off-screen; only the tab is live
        if (!within(x, y, px, 0, PANEL_W, screenH)) return Hit.NONE;
        for (int i = 0; i < TilePalette.sheetCount(); i++) {
            float ry = rowsTop() + i * ROW_H;
            if (within(x, y, px, ry, PANEL_W, ROW_H)) {
                selectedSheet = i;
                return Hit.SHEET;
            }
        }
        return Hit.PANEL; // clicked the panel chrome (title/header/footer) — consume, no-op
    }

    /** Is a screen point over the panel chrome (body when open, plus the tab)? Lets the editor tell a UI
     *  click from a map paint stroke without mutating any state (unlike {@link #handleClick}). */
    public boolean contains(float x, float y, float screenH) {
        float px = panelX();
        float tabX = px + PANEL_W, tabY = screenH / 2f - TAB_H / 2f;
        if (within(x, y, tabX, tabY, TAB_W, TAB_H)) return true;
        if (collapsed) return false;
        return within(x, y, px, 0, PANEL_W, screenH);
    }

    private static boolean within(float px, float py, float x, float y, float w, float h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }
}
