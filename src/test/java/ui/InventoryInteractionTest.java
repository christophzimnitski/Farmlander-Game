package ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import entities.Inventory;
import org.junit.jupiter.api.Test;

/**
 * The inventory move operations are pure array edits (no GL), so the drag/transfer rules are fully
 * unit-testable: single-item moves fill or stack but never clobber a different item, whole-stack moves merge
 * or swap, and quick-transfer hops a stack to the other region (bag↔hotbar) merging or taking the first empty.
 */
class InventoryInteractionTest {

    private static int[] slots() { return new int[Inventory.SIZE]; }

    private static void set(int[] items, int[] amounts, int slot, int id, int amount) {
        items[slot] = id;
        amounts[slot] = amount;
    }

    @Test
    void moveOneFillsAnEmptyTargetAndDecrementsSource() {
        int[] items = slots(), amounts = slots();
        set(items, amounts, 0, 5, 3);
        InventoryInteraction.moveOne(items, amounts, 0, 1);
        assertEquals(5, items[1]); assertEquals(1, amounts[1]);
        assertEquals(5, items[0]); assertEquals(2, amounts[0]);
    }

    @Test
    void moveOneStacksOntoTheSameItem() {
        int[] items = slots(), amounts = slots();
        set(items, amounts, 0, 5, 3);
        set(items, amounts, 1, 5, 4);
        InventoryInteraction.moveOne(items, amounts, 0, 1);
        assertEquals(5, amounts[1]); // 4 + 1
        assertEquals(2, amounts[0]); // 3 - 1
    }

    @Test
    void moveOneEmptiesSourceOnItsLastUnit() {
        int[] items = slots(), amounts = slots();
        set(items, amounts, 0, 5, 1);
        InventoryInteraction.moveOne(items, amounts, 0, 1);
        assertEquals(5, items[1]); assertEquals(1, amounts[1]);
        assertEquals(0, items[0]); assertEquals(0, amounts[0]); // source cleared
    }

    @Test
    void moveOneLeavesADifferentOccupiedTargetUntouched() {
        int[] items = slots(), amounts = slots();
        set(items, amounts, 0, 5, 3);
        set(items, amounts, 1, 7, 2);
        InventoryInteraction.moveOne(items, amounts, 0, 1);
        assertEquals(7, items[1]); assertEquals(2, amounts[1]); // unchanged
        assertEquals(5, items[0]); assertEquals(3, amounts[0]); // unchanged
    }

    @Test
    void moveStackFillsAnEmptyTargetAndClearsSource() {
        int[] items = slots(), amounts = slots();
        set(items, amounts, 0, 5, 3);
        InventoryInteraction.moveStack(items, amounts, 0, 1);
        assertEquals(5, items[1]); assertEquals(3, amounts[1]);
        assertEquals(0, items[0]); assertEquals(0, amounts[0]);
    }

    @Test
    void moveStackMergesOntoTheSameItem() {
        int[] items = slots(), amounts = slots();
        set(items, amounts, 0, 5, 3);
        set(items, amounts, 1, 5, 4);
        InventoryInteraction.moveStack(items, amounts, 0, 1);
        assertEquals(5, items[1]); assertEquals(7, amounts[1]);
        assertEquals(0, items[0]); assertEquals(0, amounts[0]);
    }

    @Test
    void moveStackSwapsWhenTheTargetHoldsADifferentItem() {
        int[] items = slots(), amounts = slots();
        set(items, amounts, 0, 5, 3);
        set(items, amounts, 1, 7, 2);
        InventoryInteraction.moveStack(items, amounts, 0, 1);
        assertEquals(5, items[1]); assertEquals(3, amounts[1]);
        assertEquals(7, items[0]); assertEquals(2, amounts[0]);
    }

    @Test
    void quickTransferSendsAHotbarStackToTheFirstEmptyBagSlot() {
        int[] items = slots(), amounts = slots();
        set(items, amounts, 0, 5, 3);
        InventoryInteraction.quickTransfer(items, amounts, 0);
        assertEquals(5, items[Inventory.HOTBAR]); assertEquals(3, amounts[Inventory.HOTBAR]);
        assertEquals(0, items[0]); assertEquals(0, amounts[0]);
    }

    @Test
    void quickTransferMergesOntoAnExistingStackInTheOtherRegion() {
        int[] items = slots(), amounts = slots();
        set(items, amounts, 0, 5, 3);
        set(items, amounts, Inventory.HOTBAR + 5, 5, 2); // a same-item stack waiting in the bag
        InventoryInteraction.quickTransfer(items, amounts, 0);
        assertEquals(5, amounts[Inventory.HOTBAR + 5]); // 2 + 3
        assertEquals(0, items[0]); assertEquals(0, amounts[0]);
    }

    @Test
    void quickTransferSendsABagStackBackToTheHotbar() {
        int[] items = slots(), amounts = slots();
        set(items, amounts, Inventory.HOTBAR + 2, 5, 3);
        InventoryInteraction.quickTransfer(items, amounts, Inventory.HOTBAR + 2);
        assertEquals(5, items[0]); assertEquals(3, amounts[0]); // first empty hotbar slot
        assertEquals(0, items[Inventory.HOTBAR + 2]);
    }
}
