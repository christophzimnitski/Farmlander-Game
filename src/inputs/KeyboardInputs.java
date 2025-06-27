package inputs;

import main.MainPanel;
import utilz.Constants;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class KeyboardInputs implements KeyListener {
    private final MainPanel mainPanel;

    public KeyboardInputs(MainPanel mainPanel) {
        this.mainPanel = mainPanel;
    }

    @Override
    public void keyTyped(KeyEvent e) {

    }

    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {

            case KeyEvent.VK_W, KeyEvent.VK_UP:
                mainPanel.getLevel().getPlayer().setUp(true);
                break;
            case KeyEvent.VK_A, KeyEvent.VK_LEFT:
                mainPanel.getLevel().getPlayer().setLeft(true);
                break;
            case KeyEvent.VK_S, KeyEvent.VK_DOWN:
                mainPanel.getLevel().getPlayer().setDown(true);
                break;
            case KeyEvent.VK_D, KeyEvent.VK_RIGHT:
                mainPanel.getLevel().getPlayer().setRight(true);
                break;


            case KeyEvent.VK_1:
                mainPanel.getLevel().getPlayer().getInventory().toggle(-1);
                //mainPanel.getLevel().getLevelManager().getPlantingLayer().editLayer(MouseInfo.getPointerInfo().getLocation().x, MouseInfo.getPointerInfo().getLocation().y-56, -18);
                break;
            case KeyEvent.VK_2:
                //mainPanel.getLevel().getLevelManager().getPlantingLayer().editLayer(MouseInfo.getPointerInfo().getLocation().x, MouseInfo.getPointerInfo().getLocation().y - 56, -1);
                break;
            case KeyEvent.VK_3:
                mainPanel.getLevel().getPlayer().getInventory().toggle(1);
                //mainPanel.getLevel().getLevelManager().getPlantingLayer().editLayer(MouseInfo.getPointerInfo().getLocation().x, MouseInfo.getPointerInfo().getLocation().y-56, 1);
                break;
            case KeyEvent.VK_4:
                //mainPanel.getLevel().getLevelManager().getPlantingLayer().editLayer(MouseInfo.getPointerInfo().getLocation().x, MouseInfo.getPointerInfo().getLocation().y - 56, 18);
                break;
            case KeyEvent.VK_5:
                mainPanel.getLevel().getLevelManager().getCropsLayer().editLayer(MouseInfo.getPointerInfo().getLocation().x, MouseInfo.getPointerInfo().getLocation().y - 36 - 64, 1);
                break;
            case KeyEvent.VK_9:
                //mainPanel.getLevel().getLevelManager().getCollisionLayer().editLayer(MouseInfo.getPointerInfo().getLocation().x, MouseInfo.getPointerInfo().getLocation().y - 56, -80);
                break;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {

        switch (e.getKeyCode()) {

            case KeyEvent.VK_W, KeyEvent.VK_UP:
                mainPanel.getLevel().getPlayer().setUp(false);
                break;
            case KeyEvent.VK_A, KeyEvent.VK_LEFT:
                mainPanel.getLevel().getPlayer().setLeft(false);
                break;
            case KeyEvent.VK_S, KeyEvent.VK_DOWN:
                mainPanel.getLevel().getPlayer().setDown(false);
                break;
            case KeyEvent.VK_D, KeyEvent.VK_RIGHT:
                mainPanel.getLevel().getPlayer().setRight(false);
                break;

            case KeyEvent.VK_Q:
                mainPanel.getLevel().getPlayer().setAttacking(true);
                break;
            case KeyEvent.VK_2:
                mainPanel.getLevel().getPlayer().changeAnimationArray(1);
                break;
            case KeyEvent.VK_E:
                if (mainPanel.getLevel().getLevelManager().getCropsLayer().isCropCollectable) {
                    mainPanel.getLevel().getLevelManager().getCropsLayer().collectPlant(mainPanel.getLevel().getLevelManager().getCropsLayer().closestCropRow, mainPanel.getLevel().getLevelManager().getCropsLayer().closestCropColumn);
                }
                break;


        }

    }
}
