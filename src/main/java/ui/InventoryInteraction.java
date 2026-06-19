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
 *   <li><b>press slot → release same slot</b>: plain = select/highlight it; Shift = quick-transfer its whole
 *       stack to the other region (hotbar↔bag).</li>
 *   <li><b>press slot → release a different slot</b>: plain = move one item; Shift = move the whole stack
 *       (swapping if the target holds a different item).</li>
 *   <li><b>press dim → release dim</b>: ask to close the popup.</li>
 *   <li>a drag released on the dim backdrop is cancelled (no move, popup stays open).</li>
 * </ul>
 * While the button is held over a picked-up slot, {@link #draggedItemId()} / {@link #cursorX()} let the HUD
 * float that item's icon at the cursor. The move operations are pure array edits (no GL) and unit-tested.
 */
public final class InventoryInteraction {
    private final int[] items, amounts;
    private final InventoryHud hud;
    private final InputBindings input;

    private boolean wasDown = false;   // pointer state last frame, to derive press/release edges
    private boolean dragging = false;  // a non-empty slot is currently picked up
    private int pressSlot = -1;        // slot the press landed on (-1 = dim/outside)
    private boolean pressShift = false; // Shift state captured at press time
    private int dragAmount = 0;        // units picked up: 1 for a plain drag, the whole stack for Shift
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
    public Result update(int selectedSlot, boolean open) {
        boolean down = input.isPrimaryPointerDown();
        cursorX = input.pointerX();
        cursorY = input.pointerY();
        int newSelected = selectedSlot;
        boolean close = false;

        if (down && !wasDown) { // press edge
            pressSlot = hud.slotAt(cursorX, cursorY, items.length, open);
            pressShift = input.isShiftDown();
            dragging = pressSlot >= 0 && items[pressSlot] != 0;
            // What's "in hand": a plain drag carries one item, a Shift-drag the whole stack. Used to mask the
            // source slot so the picked-up units don't also show there (no duplicate sprite).
            dragAmount = dragging ? (pressShift ? amounts[pressSlot] : 1) : 0;
        } else if (!down && wasDown) { // release edge
            int releaseSlot = hud.slotAt(cursorX, cursorY, items.length, open);
            if (pressSlot < 0 && releaseSlot < 0) {
                close = true; // a click on the dim backdrop closes the popup
            } else if (pressSlot >= 0) {
                if (releaseSlot == pressSlot) {
                    if (pressShift) quickTransfer(items, amounts, pressSlot);
                    else newSelected = pressSlot; // a plain click selects/highlights the slot
                } else if (releaseSlot >= 0) {
                    if (pressShift) moveStack(items, amounts, pressSlot, releaseSlot);
                    else moveOne(items, amounts, pressSlot, releaseSlot);
                }
                // releaseSlot < 0 (dropped on the dim): cancel — no move.
            }
            dragging = false;
            pressSlot = -1;
        }
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

    public float cursorX() { return cursorX; }

    public float cursorY() { return cursorY; }

    /** Drop any in-progress drag/press state — e.g. when the popup closes. */
    public void cancel() {
        dragging = false;
        pressSlot = -1;
        dragAmount = 0;
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

    /** Move one item from {@code from} to {@code to}: fills an empty target or stacks onto the same item.
     *  A different, occupied target is left alone (use a Shift-drag to swap whole stacks). */
    static void moveOne(int[] items, int[] amounts, int from, int to) {
        if (from == to || items[from] == 0 || amounts[from] <= 0) return;
        if (items[to] == 0) {
            items[to] = items[from];
            amounts[to] = 1;
            decrement(items, amounts, from);
        } else if (items[to] == items[from]) {
            amounts[to]++;
            decrement(items, amounts, from);
        }
    }

    /** Move the whole stack from {@code from} to {@code to}: fills an empty target, merges onto the same item,
     *  or swaps the two slots when the target holds a different item. */
    static void moveStack(int[] items, int[] amounts, int from, int to) {
        if (from == to || items[from] == 0) return;
        if (items[to] == 0) {
            items[to] = items[from];
            amounts[to] = amounts[from];
            clear(items, amounts, from);
        } else if (items[to] == items[from]) {
            amounts[to] += amounts[from];
            clear(items, amounts, from);
        } else { // swap
            int ti = items[to], ta = amounts[to];
            items[to] = items[from];
            amounts[to] = amounts[from];
            items[from] = ti;
            amounts[from] = ta;
        }
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

    private static void decrement(int[] items, int[] amounts, int slot) {
        if (--amounts[slot] <= 0) clear(items, amounts, slot);
    }

    private static void clear(int[] items, int[] amounts, int slot) {
        items[slot] = 0;
        amounts[slot] = 0;
    }
}
