package ui;

import entities.Inventory;
import input.InputBindings;

/**
 * Mouse handling for the open inventory popup: selecting, drag-and-drop, and quick-transfer, all expressed
 * as edits to the live {@code items}/{@code amounts} arrays (shared with the rest of the game).
 *
 * <p>Interaction model (left button), with the press slot and release slot resolved through
 * {@link InventoryHud#slotAt}:
 * <ul>
 *   <li><b>press slot → release same slot</b>: Shift = quick-transfer its whole stack to the other region
 *       (hotbar↔bag); otherwise <em>click-to-move</em> — a plain click highlights the slot, and a further click
 *       on another slot sends the highlighted slot's whole stack there if it can land (empty target or the same
 *       item to merge). Clicking a different item just re-highlights it (no swap on click — only a drag swaps);
 *       the destination stays highlighted, so successive clicks keep moving it on.</li>
 *   <li><b>press slot → release a different slot</b>: a drag — drop the carried units — fill an empty target,
 *       merge onto the same item, or (only when carrying the <em>entire</em> source stack) swap the two slots.
 *       A partial drop onto a different item is a no-op (the units stay put).</li>
 *   <li><b>press dim → release dim</b>: ask to close the popup.</li>
 *   <li>a drag released on the dim backdrop is cancelled (no move, popup stays open).</li>
 * </ul>
 * The dragged amount starts at one and, while the button is held, ramps up exponentially toward the whole
 * stack (Shift picks up the whole stack at once). While held, {@link #draggedItemId()} / {@link #draggedAmount()}
 * / {@link #cursorX()} let the HUD float that group (icon + count) at the cursor, and {@link #hoverSlot()} marks
 * the slot it would land on. The move operations are pure array edits (no GL) and unit-tested.
 */
public final class InventoryInteraction {
    // Hold-to-grab ramp: the pickup rate (items/sec) starts at BASE and grows exponentially with hold time,
    // capped at MAX — so a quick drag carries ~1 and holding scoops the stack ever faster. Tuned deliberately
    // gentle: a gentle start (~1 item per ~0.4s) ramping to the cap over roughly a second.
    private static final float RAMP_BASE_RATE = 1f;
    private static final float RAMP_ACCEL = 1.3f;
    private static final float RAMP_MAX_RATE = 16f;

    private final int[] items, amounts;
    private final InventoryHud hud;
    private final InputBindings input;

    private boolean wasDown = false;   // pointer state last frame, to derive press/release edges
    private boolean dragging = false;  // a non-empty slot is currently picked up
    private int pressSlot = -1;        // slot the press landed on (-1 = dim/outside)
    private boolean pressShift = false; // Shift state captured at press time
    private int dragAmount = 0;        // units in hand: ramps from 1 while held (whole stack on Shift)
    private float holdTime = 0f;       // seconds the plain drag has been held (drives the ramp)
    private float holdAccum = 0f;      // accumulated fractional items grabbed by the ramp
    private int hoverSlot = -1;        // slot under the cursor mid-drag (for the landing-spot highlight), else -1
    private float cursorX, cursorY;

    public InventoryInteraction(int[] items, int[] amounts, InventoryHud hud, InputBindings input) {
        this.items = items;
        this.amounts = amounts;
        this.hud = hud;
        this.input = input;
    }

    /**
     * Process one frame of inventory mouse input while the popup is open. Returns the (possibly new) selected
     * slot and whether a click on the dim backdrop asked to close the popup.
     */
    public Result update(float delta, int selectedSlot, boolean open) {
        boolean down = input.isPrimaryPointerDown();
        cursorX = input.pointerX();
        cursorY = input.pointerY();
        int newSelected = selectedSlot;
        boolean close = false;

        if (down && !wasDown) { // press edge
            pressSlot = hud.slotAt(cursorX, cursorY, items.length, open);
            pressShift = input.isShiftDown();
            dragging = pressSlot >= 0 && items[pressSlot] != 0;
            // What's "in hand": Shift picks up the whole stack at once; a plain drag starts at one and ramps.
            dragAmount = dragging ? (pressShift ? amounts[pressSlot] : 1) : 0;
            holdTime = 0f;
            holdAccum = 0f;
        } else if (!down && wasDown) { // release edge
            int releaseSlot = hud.slotAt(cursorX, cursorY, items.length, open);
            if (pressSlot < 0 && releaseSlot < 0) {
                close = true; // a click on the dim backdrop closes the popup
            } else if (pressSlot >= 0) {
                if (releaseSlot == pressSlot) {
                    if (pressShift) {
                        quickTransfer(items, amounts, pressSlot);
                    } else if (newSelected >= 0 && newSelected != pressSlot && items[newSelected] != 0
                               && (items[pressSlot] == 0 || items[pressSlot] == items[newSelected])) {
                        // Click-to-move: send the highlighted slot's whole stack here — into an empty slot or
                        // merged onto the same item. A different item is left untouched (only a drag swaps). The
                        // clicked slot stays highlighted, so it becomes the source for a further click (move on).
                        dropCarried(items, amounts, newSelected, pressSlot, amounts[newSelected]);
                    }
                    newSelected = pressSlot; // a plain click always highlights the clicked slot
                } else if (releaseSlot >= 0) {
                    dropCarried(items, amounts, pressSlot, releaseSlot, dragAmount);
                    newSelected = releaseSlot; // the dropped-on slot stays highlighted
                }
                // releaseSlot < 0 (dropped on the dim): cancel — no move.
            }
            dragging = false;
            pressSlot = -1;
        }

        // While a plain drag is held, ramp the carried amount up toward the whole source stack: the pickup
        // rate grows exponentially with hold time (capped), so the count climbs slowly then ever faster.
        if (dragging && !pressShift) {
            holdTime += delta;
            float rate = Math.min(RAMP_MAX_RATE, RAMP_BASE_RATE * (float) Math.exp(RAMP_ACCEL * holdTime));
            holdAccum += rate * delta;
            dragAmount = Math.min(amounts[pressSlot], 1 + (int) holdAccum);
        }

        // The slot under the cursor mid-drag, so the HUD can highlight where the carried item would land.
        hoverSlot = dragging ? hud.slotAt(cursorX, cursorY, items.length, open) : -1;

        wasDown = down;
        return new Result(newSelected, close);
    }

    /** True while a non-empty slot is picked up (button held after pressing it). */
    public boolean isDragging() { return dragging; }

    /** The item id currently being dragged (for the floating cursor icon), or {@code 0} when not dragging. */
    public int draggedItemId() { return (dragging && pressSlot >= 0) ? items[pressSlot] : 0; }

    /** How many units are in hand (1 for a plain drag, the whole stack for a Shift-drag), or {@code 0}. */
    public int draggedAmount() { return dragging ? dragAmount : 0; }

    /** The slot the drag started from (so the HUD can hide the picked-up units there), or {@code -1}. */
    public int draggedSourceSlot() { return dragging ? pressSlot : -1; }

    /** The slot under the cursor during a drag (for the HUD's landing-spot highlight), or {@code -1}. */
    public int hoverSlot() { return hoverSlot; }

    public float cursorX() { return cursorX; }

    public float cursorY() { return cursorY; }

    /** Drop any in-progress drag/press state — e.g. when the popup closes. */
    public void cancel() {
        dragging = false;
        pressSlot = -1;
        dragAmount = 0;
        holdTime = 0f;
        holdAccum = 0f;
        hoverSlot = -1;
        wasDown = false;
    }

    /** Outcome of a frame: the selection (changed only by a plain click) and a close request (dim click). */
    public static final class Result {
        public final int selectedSlot;
        public final boolean close;

        Result(int selectedSlot, boolean close) {
            this.selectedSlot = selectedSlot;
            this.close = close;
        }
    }

    // --- pure move operations (no GL; unit-tested) ---

    /** Drop {@code carried} units from {@code from} onto {@code to}: fill an empty target, merge onto the same
     *  item, or — only when {@code carried} is the <em>entire</em> source stack — swap the two slots. A partial
     *  drop onto a different item is a no-op (the carried units stay in the source). {@code carried} is clamped
     *  to what the source actually holds. */
    static void dropCarried(int[] items, int[] amounts, int from, int to, int carried) {
        if (from == to || items[from] == 0 || carried <= 0) return;
        carried = Math.min(carried, amounts[from]);
        boolean wholeStack = carried >= amounts[from];
        if (items[to] == 0) {
            items[to] = items[from];
            amounts[to] = carried;
            decrementBy(items, amounts, from, carried);
        } else if (items[to] == items[from]) {
            amounts[to] += carried;
            decrementBy(items, amounts, from, carried);
        } else if (wholeStack) { // different item — swap only when the whole stack is in hand
            int ti = items[to], ta = amounts[to];
            items[to] = items[from];
            amounts[to] = amounts[from];
            items[from] = ti;
            amounts[from] = ta;
        }
        // else: a partial drop onto a different item — no-op, the units return to the source.
    }

    /** Quick-transfer a whole stack to the other region: a hotbar slot ({@code < HOTBAR}) goes to the bag and
     *  a bag slot to the hotbar, merging onto an existing same-item stack if there is one, else the first empty
     *  slot. No-op if the target region is full. */
    static void quickTransfer(int[] items, int[] amounts, int slot) {
        if (items[slot] == 0) return;
        int start, end; // target region [start, end)
        if (slot < Inventory.HOTBAR) { start = Inventory.HOTBAR; end = items.length; } // hotbar -> bag
        else { start = 0; end = Inventory.HOTBAR; }                                     // bag -> hotbar
        for (int i = start; i < end; i++) {
            if (items[i] == items[slot]) { // merge onto an existing stack of the same item
                amounts[i] += amounts[slot];
                clear(items, amounts, slot);
                return;
            }
        }
        for (int i = start; i < end; i++) {
            if (items[i] == 0) { // else the first empty slot
                items[i] = items[slot];
                amounts[i] = amounts[slot];
                clear(items, amounts, slot);
                return;
            }
        }
    }

    private static void decrementBy(int[] items, int[] amounts, int slot, int n) {
        amounts[slot] -= n;
        if (amounts[slot] <= 0) clear(items, amounts, slot);
    }

    private static void clear(int[] items, int[] amounts, int slot) {
        items[slot] = 0;
        amounts[slot] = 0;
    }
}
