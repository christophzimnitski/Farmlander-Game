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
        drawInventory(g);
        drawItems(g);
        drawItemAmounts(g);
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
            System.out.println("fail");
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

    private void drawInventory(Graphics g) {
        int inventoryWidth = 1200;
        int inventoryHeight = inventoryWidth / 10;
        int inventoryX = (GAME_WIDTH / 2) - (inventoryWidth / 2);
        int inventoryY = (GAME_HEIGHT * 3 / 4) + (GAME_HEIGHT / 8);

        g.drawImage(LoadSave.INVENTORY, inventoryX, inventoryY, inventoryWidth, inventoryHeight, null);
        g.drawImage(LoadSave.INVENTORY_TOGGLE, inventoryX + (toggle * inventoryWidth/10), inventoryY, inventoryWidth/10, inventoryHeight, null);
    }

    private void drawItems(Graphics g) {
        int index = 0;
        int inventoryWidth = 1200;
        int inventoryHeight = inventoryWidth / 10;
        int inventoryX = (GAME_WIDTH / 2) - (inventoryWidth / 2);
        int inventoryY = (GAME_HEIGHT * 3 / 4) + (GAME_HEIGHT / 8);

        for (int i = 0; i < inventory.length; i++) {
            //inventory[i] = 0; inventoryAmounts[i] = 0; //RESET INVENTORY
            if (inventory[i] != Nothing) {
                g.drawImage(LoadSave.GetSpriteAtlas("food/" + inventory[i] + ".png"), inventoryX + (index * inventoryWidth / 10) + GAME_WIDTH/120, inventoryY+GAME_HEIGHT/80, inventoryWidth / 15, (int) (inventoryHeight/1.5), null);
            }
            index++;
        }
    }

    private void drawItemAmounts(Graphics g) {
        int index = 0;
        int inventoryWidth = 1200;
        int inventoryHeight = inventoryWidth / 10;
        int inventoryX = (GAME_WIDTH / 2) - (inventoryWidth / 2);
        int inventoryY = (GAME_HEIGHT * 3 / 4) + (GAME_HEIGHT / 8);

        for (int i = 0; i < inventory.length; i++) {
            if (inventory[i] != Nothing) {
                g.drawImage(LoadSave.numbers[0][inventoryAmounts[i]], inventoryX + (index * inventoryWidth / 10) + GAME_WIDTH/32, inventoryY+GAME_HEIGHT/18, inventoryWidth / 50, (int) (inventoryHeight/7), null);
            }
            index++;
        }
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
