package entities;

import utilz.LoadSave;

import java.awt.*;
import java.io.Serializable;

import static main.Level.GAME_HEIGHT;
import static main.Level.GAME_WIDTH;
import static utilz.Constants.InventoryConstants.Nothing;

public class Inventory implements Serializable {
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

    public void render(Graphics g) {
        int width = 1200;
        int height = width / 10;
        g.drawImage(LoadSave.INVENTORY, (GAME_WIDTH / 2) - (width / 2), (GAME_HEIGHT * 3 / 4) + (GAME_HEIGHT / 8), width, height, null);
        g.drawImage(LoadSave.INVENTORY_TOGGLE, ((GAME_WIDTH / 2) - (width / 2)) + (toggle * width / 10), (GAME_HEIGHT * 3 / 4) + (GAME_HEIGHT / 8), width / 10, height, null);
    }

    public void addInventoryItem(int spot, int amount, int item) {
        if (inventory[spot] == Nothing) {
            inventory[spot] = item;
            inventoryAmounts[spot] = amount;
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
}
