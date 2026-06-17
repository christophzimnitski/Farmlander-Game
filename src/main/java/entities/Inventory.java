package entities;

import java.io.Serializable;

import static utilz.Constants.InventoryConstants.Nothing;

/**
 * Inventory data model: items and their amounts per slot, plus the selected-slot toggle. Rendering now
 * lives in the libGDX HUD ({@code PlayState.drawHud}); the old Swing {@code render(Graphics)} methods
 * were removed in the cutover.
 */
public class Inventory implements Serializable {
    // Pinned to the original (Swing-era) auto-computed value so existing saved worlds still
    // deserialize after the class was trimmed in the cutover. The fields below are unchanged.
    private static final long serialVersionUID = 1752945795411627435L;

    private final int[] inventory;
    private final int[] inventoryAmounts;
    private int toggle = 0;

    public Inventory(int size) {
        inventory = new int[size];
        inventoryAmounts = new int[size];
        for (int i = 0; i < size; i++) {
            inventory[i] = Nothing;
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
            if (inventory[i] == Nothing && !isProcessComplete) {
                inventory[i] = item;
                inventoryAmounts[i] = amount;
                break;
            }
        }
        if (item == Nothing) {
            inventory[spot] = Nothing;
            inventoryAmounts[spot] = 0;
        }
    }

    public void removeInventoryItem(int spot, int amount) {
        if (inventory[spot] != Nothing) {
            inventoryAmounts[spot] -= amount;
            if (inventoryAmounts[spot] <= 0) {
                inventory[spot] = Nothing;
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

    public void toggle(int toggle) {
        this.toggle += toggle;
        if (this.toggle > 9) this.toggle = 9;
        if (this.toggle < 0) this.toggle = 0;
    }

    public int getToggle() {
        return toggle;
    }
}
