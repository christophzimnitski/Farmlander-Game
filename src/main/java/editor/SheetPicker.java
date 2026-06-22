package editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Matrix4;
import core.Assets;
import render.TextureSlicer;
import ui.PixelFont;

import java.util.HashMap;
import java.util.Map;

/**
 * The modal tile-picker popup: opened from the side panel with a tileset, it shows that whole sheet enlarged
 * with a grid overlay so you can pick tiles to set the paint brush. A single click selects one tile; pressing
 * and dragging selects a whole rectangle of tiles that the caller can stamp as a block. Returns the chosen
 * cells as a {@link Selection} of {@link TilePalette} global ids.
 *
 * <p>While open it's modal — the whole press/drag/release is consumed by the popup (it closes on release; a
 * press that began outside the grid is a cancel). Drawn in the Y-down HUD space with the shared
 * {@link PixelFont} and white pixel (owned by {@code EditorState}); the only thing it caches is one full-sheet
 * {@link TextureRegion} per sheet.
 */
public final class SheetPicker {
    private static final float MAX_W_FRAC = 0.6f, MAX_H_FRAC = 0.66f, MAX_CELL = 64f;
    private static final float TITLE_H = 34f, MARGIN = 16f;

    private final Assets assets;
    private final PixelFont font;
    private final TextureRegion whiteRegion;
    private final Map<Integer, TextureRegion> fullSheets = new HashMap<>(); // sheetIndex -> flipped full region

    private boolean open = false;
    private int sheetIndex = -1;
    private boolean dragging = false;
    private int dragStartCell = -1, dragEndCell = -1; // cell indices spanning the in-progress drag rectangle

    /** A rectangular block of tiles chosen in the picker. {@code ids} are {@link TilePalette} global ids in
     *  row-major order ({@code cols}×{@code rows}); a single click yields a 1×1 selection. */
    public static final class Selection {
        public final int sheetIndex, cols, rows;
        public final int[] ids;
        Selection(int sheetIndex, int cols, int rows, int[] ids) {
            this.sheetIndex = sheetIndex; this.cols = cols; this.rows = rows; this.ids = ids;
        }
        public int topLeftId() { return ids[0]; }
        public boolean isSingle() { return cols == 1 && rows == 1; }
    }

    public SheetPicker(Assets assets, PixelFont font, TextureRegion whiteRegion) {
        this.assets = assets;
        this.font = font;
        this.whiteRegion = whiteRegion;
    }

    public boolean isOpen() { return open; }

    public void open(int sheetIndex) {
        this.sheetIndex = sheetIndex;
        this.open = true;
    }

    public void close() { open = false; }

    /** The popup's grid box (in screen pixels), recomputed from the window + sheet shape. Reused. */
    private static final class Box { float x, y, w, h, cell; int cols, rows; }
    private final Box box = new Box();

    private Box box(float screenW, float screenH) {
        TilePalette.Sheet sheet = TilePalette.sheet(sheetIndex);
        float maxW = screenW * MAX_W_FRAC, maxH = screenH * MAX_H_FRAC;
        float cell = Math.min(Math.min(maxW / sheet.cols, maxH / sheet.rows), MAX_CELL);
        box.cell = cell;
        box.cols = sheet.cols;
        box.rows = sheet.rows;
        box.w = cell * sheet.cols;
        box.h = cell * sheet.rows;
        box.x = (screenW - box.w) / 2f;
        box.y = (screenH - box.h) / 2f + TITLE_H / 2f; // nudge down to leave room for the title
        return box;
    }

    private TextureRegion fullSheet() {
        TextureRegion r = fullSheets.get(sheetIndex);
        if (r == null) {
            r = TextureSlicer.flippedFull(assets.get(TilePalette.sheet(sheetIndex).path));
            fullSheets.put(sheetIndex, r);
        }
        return r;
    }

    private void rect(SpriteBatch batch, float x, float y, float w, float h, float r, float g, float b, float a) {
        batch.setColor(r, g, b, a);
        batch.draw(whiteRegion, x, y, w, h);
        batch.setColor(Color.WHITE);
    }

    public void render(SpriteBatch batch, Matrix4 hudCombined, float screenW, float screenH, int currentBrushId) {
        if (!open) return;
        Box b = box(screenW, screenH);
        batch.setProjectionMatrix(hudCombined);
        batch.begin();
        rect(batch, 0, 0, screenW, screenH, 0f, 0f, 0f, 0.6f); // dim the editor behind
        // frame: title bar + a margin border around the sheet
        rect(batch, b.x - MARGIN, b.y - TITLE_H, b.w + 2 * MARGIN, TITLE_H, 0.20f, 0.30f, 0.42f, 1f);
        batch.setColor(1, 1, 1, 1);
        font.draw(batch, TilePalette.sheet(sheetIndex).name, b.x - MARGIN + 10f, b.y - TITLE_H + 9f, 15f, 15f, 13f);
        rect(batch, b.x - MARGIN, b.y, b.w + 2 * MARGIN, b.h + MARGIN, 0.10f, 0.11f, 0.14f, 1f);
        // the sheet itself
        batch.setColor(Color.WHITE);
        batch.draw(fullSheet(), b.x, b.y, b.w, b.h);
        // grid lines
        for (int c = 0; c <= b.cols; c++) rect(batch, b.x + c * b.cell, b.y, 1f, b.h, 1f, 1f, 1f, 0.25f);
        for (int r = 0; r <= b.rows; r++) rect(batch, b.x, b.y + r * b.cell, b.w, 1f, 1f, 1f, 1f, 0.25f);
        // current brush cell (if it belongs to this sheet) + hover cell
        if (TilePalette.isValid(currentBrushId) && TilePalette.sheetIndexOf(currentBrushId) == sheetIndex) {
            markCell(batch, b, TilePalette.tileIndexOf(currentBrushId), 0.3f, 0.9f, 0.4f, 0.5f);
        }
        if (dragging && dragStartCell >= 0) {
            markRect(batch, b, dragStartCell, dragEndCell, 0.3f, 0.9f, 0.4f, 0.45f); // the drag selection
        } else {
            int hover = cellAt(b, Gdx.input.getX(), Gdx.input.getY());
            if (hover >= 0) markCell(batch, b, hover, 1f, 1f, 1f, 0.35f);
        }
        batch.end();
    }

    private void markCell(SpriteBatch batch, Box b, int tileIndex, float r, float g, float bl, float a) {
        int col = tileIndex % b.cols, row = tileIndex / b.cols;
        rect(batch, b.x + col * b.cell, b.y + row * b.cell, b.cell, b.cell, r, g, bl, a);
    }

    /** Highlight the rectangle spanning two cell indices (the live drag selection). */
    private void markRect(SpriteBatch batch, Box b, int startCell, int endCell, float r, float g, float bl, float a) {
        int c0 = startCell % b.cols, r0 = startCell / b.cols;
        int c1 = endCell % b.cols, r1 = endCell / b.cols;
        int lc = Math.min(c0, c1), rc = Math.max(c0, c1), tr = Math.min(r0, r1), br = Math.max(r0, r1);
        rect(batch, b.x + lc * b.cell, b.y + tr * b.cell, (rc - lc + 1) * b.cell, (br - tr + 1) * b.cell, r, g, bl, a);
    }

    private int cellAt(Box b, float x, float y) {
        if (x < b.x || x >= b.x + b.w || y < b.y || y >= b.y + b.h) return -1;
        int col = (int) ((x - b.x) / b.cell), row = (int) ((y - b.y) / b.cell);
        if (col < 0 || col >= b.cols || row < 0 || row >= b.rows) return -1;
        return row * b.cols + col;
    }

    /** Like {@link #cellAt} but clamps the point into the grid, so dragging past an edge still extends the
     *  selection to the nearest cell instead of dropping out. */
    private int clampedCell(Box b, float x, float y) {
        int col = (int) Math.floor((x - b.x) / b.cell), row = (int) Math.floor((y - b.y) / b.cell);
        col = Math.max(0, Math.min(b.cols - 1, col));
        row = Math.max(0, Math.min(b.rows - 1, row));
        return row * b.cols + col;
    }

    public boolean isDragging() { return dragging; }

    /** Begin a drag-select on left-press while open: pressing on a cell starts a selection rectangle; pressing
     *  outside the grid arms a cancel (release then closes with no selection). */
    public void onPress(float x, float y, float screenW, float screenH) {
        if (!open) return;
        Box b = box(screenW, screenH);
        dragStartCell = cellAt(b, x, y); // -1 when the press is outside the grid
        dragEndCell = dragStartCell;
        dragging = true;
    }

    /** Extend the in-progress selection to the cell under the cursor (clamped into the grid). No-op until a
     *  drag that began on a real cell is in progress. */
    public void onDrag(float x, float y, float screenW, float screenH) {
        if (!open || !dragging || dragStartCell < 0) return;
        dragEndCell = clampedCell(box(screenW, screenH), x, y);
    }

    /** Finish the drag: close the popup and return the selected rectangle, or null when the press began outside
     *  the grid (a cancel) or no drag was in progress. The whole press/drag/release is modal — the caller
     *  should route input here for every frame the picker {@link #isOpen()}. */
    public Selection onRelease() {
        boolean was = dragging;
        int start = dragStartCell, end = dragEndCell;
        dragging = false;
        dragStartCell = dragEndCell = -1;
        open = false;
        if (!was || start < 0) return null;
        return selection(sheetIndex, start, end);
    }

    /** Build the rectangular selection spanning two cell indices within a sheet (inclusive, direction-agnostic).
     *  Pure id math — no GL — so it's unit-testable. */
    public static Selection selection(int sheetIndex, int startCell, int endCell) {
        int cols = TilePalette.sheet(sheetIndex).cols;
        int c0 = startCell % cols, r0 = startCell / cols, c1 = endCell % cols, r1 = endCell / cols;
        int lc = Math.min(c0, c1), rc = Math.max(c0, c1), tr = Math.min(r0, r1), br = Math.max(r0, r1);
        int w = rc - lc + 1, h = br - tr + 1;
        int[] ids = new int[w * h];
        for (int dr = 0; dr < h; dr++)
            for (int dc = 0; dc < w; dc++)
                ids[dr * w + dc] = TilePalette.pack(sheetIndex, (tr + dr) * cols + (lc + dc));
        return new Selection(sheetIndex, w, h, ids);
    }
}
