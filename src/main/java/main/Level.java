package main;

import entities.Camera;
import entities.Player;
import levels.LevelManager;

import java.awt.*;

public class Level implements Runnable {
    public final static int TILES_DEFAULT_SIZE = 64;
    public final static float SCALE = 2.0f;
    public final static int TILES_IN_WIDTH = 200;
    public final static int TILES_IN_HEIGHT = 110;

    public final static int GAME_WIDTH = Toolkit.getDefaultToolkit().getScreenSize().width;
    public final static int GAME_HEIGHT = Toolkit.getDefaultToolkit().getScreenSize().height;

    private final MainPanel mainPanel;
    private Player player;
    private LevelManager levelManager;

    public Level() {
        initClasses();

        mainPanel = new MainPanel(this);
        MainWindow mainWindow = new MainWindow(mainPanel);
        mainPanel.requestFocus();

        startGameLoop();

        player.setLevelManager(levelManager);

    }

    private void initClasses() {
        player = new Player(2000, 800);
        levelManager = new LevelManager(this);
        levelManager.getGrassLayer().createLayer();
        levelManager.getPlantingLayer().createLayer();
        levelManager.getCollisionLayer().createLayer();
        levelManager.getCropsLayer().createLayer();
    }

    private void startGameLoop() {
        Thread gameThread = new Thread(this);
        gameThread.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            levelManager.getObjects().add(levelManager.getCollisionLayer().getLayerArray());
            levelManager.getObjects().add(levelManager.getPlantingLayer().getLayerArray());
            levelManager.getObjects().add(levelManager.getGrassLayer().getLayerArray());
            levelManager.getObjects().add(levelManager.getCropsLayer().getLayerArray());
            levelManager.getObjects().add(levelManager.getCropsLayer().getTimesLeft());
            levelManager.getObjects().add(player.getInventory());
            levelManager.getObjects().add(player.getX());
            levelManager.getObjects().add(player.getY());
            levelManager.getObjects().add(player.getSelectedAnimationArray());
            levelManager.getObjects().add(player.getEntityAction());
            LevelManager.saveObjects(levelManager.getObjects(), LevelManager.SAVE_FILE);
            System.out.println("Objects saved to file.");
        }));
    }

    public void update() {
        player.update();
    }

    public void render(Graphics g) {
        Camera camera = new Camera(GAME_WIDTH, GAME_HEIGHT, player);
        levelManager.getGrassLayer().drawLayer(g, camera.getOffsetX(), camera.getOffsetY());
        levelManager.getPlantingLayer().drawLayer(g, camera.getOffsetX(), camera.getOffsetY());
        levelManager.getCollisionLayer().drawLayer(g, camera.getOffsetX(), camera.getOffsetY());
        levelManager.getCropsLayer().drawLayer(g, camera.getOffsetX(), camera.getOffsetY());
        player.render(g, camera.getOffsetX(), camera.getOffsetY());
        levelManager.getCollisionLayer().drawCollisionMask(g, camera.getOffsetX(), camera.getOffsetY());
        levelManager.getCropsLayer().drawCollisionMask(g, camera.getOffsetX(), camera.getOffsetY());
        levelManager.getCropsLayer().drawCollectionButton(g, camera.getOffsetX(), camera.getOffsetY()) ;

        player.getInventory().render(g);
    }


    @Override
    public void run() {

        int FPS_SET = 144;
        double timePerFrame = 1000000000.0 / FPS_SET;

        int UPS_SET = 144;
        double timePerUpdate = 1000000000.0 / UPS_SET;
        long previousTime = System.nanoTime();

        int frames = 0;
        int updates = 0;
        long lastCheck = System.currentTimeMillis();

        double deltaU = 0;
        double deltaF = 0;

        while (true) {
            long currentTime = System.nanoTime();

            deltaU += (currentTime - previousTime) / timePerUpdate;
            deltaF += (currentTime - previousTime) / timePerFrame;

            previousTime = currentTime;


            if (deltaU >= 1) {
                update();
                updates++;
                deltaU--;
            }

            if (deltaF >= 1) {
                mainPanel.repaint();
                frames++;
                deltaF--;
            }

            if (System.currentTimeMillis() - lastCheck >= 1000) {
                lastCheck = System.currentTimeMillis();
                frames = 0;
                updates = 0;
            }
        }

    }

    public void windowFocusLost() {
        player.resetDirBooleans();
    }

    public Player getPlayer() {
        return player;
    }

    public LevelManager getLevelManager() {
        return levelManager;
    }
}
