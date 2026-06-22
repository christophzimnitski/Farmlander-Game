package editor;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Matrix4;
import ui.PixelFont;

/**
 * The editor's retractable side panel (left edge): the **Layers** menu. It lists the map's layers (built-in
 * grass/soil/bushes plus the editor stack — sand, sand-water, fence, props, and any added), highlights the
 * active one you're painting into, shows a visibility eye for the layers that support hiding, and has an
 * "Add layer" row. A little tab on the outer edge hides/shows the whole panel.
 *
 * <p>Hand-rolled to match the rest of the game's UI (per the project's owned-tech preference): filled quads
 * from a 1×1 white texture and the shared {@link PixelFont}, no Scene2D. Pure presentation — the editor owns
 * the layer model and passes the rows in; the panel reports what a click hit. Y-down HUD space, so screen
 * pixels index it directly.
 */
public final class EditorPanel {
    private static final float PANEL_W = 250f;
    private static final float TAB_W = 26f, TAB_H = 74f;
    private static final float TITLE_H = 42f;
    private static final float ROW_H = 28f;
    private static final float PAD = 12f;
    private static final float GLYPH = 15f, ADVANCE = 13f;
    private static final float EYE = 18f;

    /** What a click hit. {@code index} is the layer row for LAYER/EYE. */
    public enum HitKind { NONE, PANEL, TAB, LAYER, EYE }

    public static final class Hit {
        public final HitKind kind;
        public final int index;
        Hit(HitKind kind, int index) { this.kind = kind; this.index = index; }
    }

    private final PixelFont font;
    private final TextureRegion whiteRegion;
    private boolean collapsed = false;

    public EditorPanel(PixelFont font, TextureRegion whiteRegion) {
        this.font = font;
        this.whiteRegion = whiteRegion;
    }

    public boolean collapsed() { return collapsed; }

    private float panelX() { return collapsed ? -PANEL_W : 0f; }

    private float rowY(int i) { return TITLE_H + i * ROW_H; }

    private void rect(SpriteBatch batch, float x, float y, float w, float h, float r, float g, float b, float a) {
        batch.setColor(r, g, b, a);
        batch.draw(whiteRegion, x, y, w, h);
        batch.setColor(Color.WHITE);
    }

    /** Draw the panel. Every row has a visibility eye; {@code visible[i]} is its on/off state. */
    public void render(SpriteBatch batch, Matrix4 hudCombined, float screenW, float screenH,
                       String[] names, int active, boolean[] visible) {
        batch.setProjectionMatrix(hudCombined);
        batch.begin();
        float px = panelX();
        if (!collapsed) {
            rect(batch, px, 0, PANEL_W, screenH, 0.12f, 0.13f, 0.17f, 0.96f);          // body
            rect(batch, px, 0, PANEL_W, TITLE_H, 0.20f, 0.30f, 0.42f, 1f);             // title bar
            text(batch, "LAYERS", px + PAD, PAD, 1f, 1f, 1f);
            for (int i = 0; i < names.length; i++) {
                float ry = rowY(i);
                if (i == active) rect(batch, px, ry, PANEL_W, ROW_H, 0.24f, 0.40f, 0.26f, 1f);
                text(batch, names[i], px + PAD, ry + 7f, 0.9f, 0.92f, 0.9f);
                float ex = px + PANEL_W - EYE - 8f, ey = ry + (ROW_H - EYE) / 2f;
                rect(batch, ex, ey, EYE, EYE, 0.05f, 0.05f, 0.07f, 1f);                // eye well
                if (visible[i]) rect(batch, ex + 4f, ey + 4f, EYE - 8f, EYE - 8f, 0.7f, 0.85f, 0.7f, 1f);
            }
            text(batch, "WASD PAN  WHEEL ZOOM", px + PAD, screenH - 50f, 0.6f, 0.62f, 0.66f);
            text(batch, "F1 EXIT  RMB ERASE", px + PAD, screenH - 30f, 0.6f, 0.62f, 0.66f);
        }
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

    /** Resolve a click. Every row has an eye hit-box at its right edge; the rest of the row selects the layer. */
    public Hit handleClick(float x, float y, float screenH, int rowCount) {
        float px = panelX();
        float tabX = px + PANEL_W, tabY = screenH / 2f - TAB_H / 2f;
        if (within(x, y, tabX, tabY, TAB_W, TAB_H)) {
            collapsed = !collapsed;
            return new Hit(HitKind.TAB, -1);
        }
        if (collapsed) return new Hit(HitKind.NONE, -1);
        if (!within(x, y, px, 0, PANEL_W, screenH)) return new Hit(HitKind.NONE, -1);
        for (int i = 0; i < rowCount; i++) {
            float ry = rowY(i);
            if (within(x, y, px, ry, PANEL_W, ROW_H)) {
                float ex = px + PANEL_W - EYE - 8f, ey = ry + (ROW_H - EYE) / 2f;
                if (within(x, y, ex, ey, EYE, EYE)) return new Hit(HitKind.EYE, i);
                return new Hit(HitKind.LAYER, i);
            }
        }
        return new Hit(HitKind.PANEL, -1);
    }

    /** Is a screen point over the panel chrome (body when open, plus the tab)? Lets the editor tell a UI click
     *  from a map paint stroke without mutating state (unlike {@link #handleClick}). */
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
