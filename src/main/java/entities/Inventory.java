package entities;

import java.io.Serializable;

/**
 * Inventory data model: items and their amounts per slot, plus the index of the selected slot.
 * Rendering now lives in the libGDX HUD ({@code PlayState.drawHud}); the old Swing
 * {@code render(Graphics)} methods were removed in the cutover.
 */
public class Inventory implements Serializable {
    // Pinned to the original (Swing-era) auto-computed value so existing saved worlds still
    // deserialize after the class was trimmed in the cutover. The fields below are unchanged.
    private static final long serialVersionUID = 1752945795411627435L;

    /** Total slots. The first {@link #HOTBAR} are the selectable bottom bar; the rest are the bag
     *  (the 3x10 grid revealed by the inventory popup). Saved inventories migrate by length (a short
     *  old save copies into the low slots; the new tail stays empty). */
    public static final int SIZE = 40;
    /** Empty-slot sentinel: an empty inventory slot holds this item id. (Formerly
     *  {@code utilz.Constants.InventoryConstants.Nothing}.) */
    public static final int EMPTY = 0;
    /** The selectable bottom bar: selection is clamped here, and the selected slot is the held item. */
    public static final int HOTBAR = 10;

    private final int[] inventory;
    private final int[] inventoryAmounts;
    private int selectedSlot = 0;

    public Inventory(int size) {
        inventory = new int[size];
        inventoryAmounts = new int[size];
        for (int i = 0; i < size; i++) {
            inventory[i] = EMPTY;
            inventoryAmounts[i] = 0;
        }
    }

    public void addInventoryItem(int spot, int amount, int item) {
        boolean isProcessComplete = false;
        for (int i = 0; i < inventory.length; i++) {
            if (inventory[i] == item) {
                inventoryAmounts[i] += amount;
                isProcessComplete = true;
            }
        }
        for (int i = 0; i < inventory.length; i++) {
            if (inventory[i] == EMPTY && !isProcessComplete) {
                inventory[i] = item;
                inventoryAmounts[i] = amount;
                break;
            }
        }
        if (item == EMPTY) {
            inventory[spot] = EMPTY;
            inventoryAmounts[spot] = 0;
        }
    }

    public void removeInventoryItem(int spot, int amount) {
        if (inventory[spot] != EMPTY) {
            inventoryAmounts[spot] -= amount;
            if (inventoryAmounts[spot] <= 0) {
                inventory[spot] = EMPTY;
                inventoryAmounts[spot] = 0;
            }
        }
    }

    public int[] getInventory() {
        return inventory;
    }

    public int[] getInventoryAmounts() {
        return inventoryAmounts;
    }

    /** Move the selected slot by {@code delta}, clamped to the hotbar (0..HOTBAR-1). */
    public void moveSelection(int delta) {
        this.selectedSlot += delta;
        if (this.selectedSlot > HOTBAR - 1) this.selectedSlot = HOTBAR - 1;
        if (this.selectedSlot < 0) this.selectedSlot = 0;
    }

    public int getSelectedSlot() {
        return selectedSlot;
    }
}
