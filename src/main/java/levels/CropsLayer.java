package levels;

import entities.Player;
import main.Level;
import org.jetbrains.annotations.NotNull;
import utilz.Constants;
import utilz.LoadSave;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.Serializable;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.stream.IntStream;

import static utilz.Constants.Zoom.zoomMultiplier;

public class CropsLayer implements LayerInterface, Serializable {
    ///ERROR WHEN TILLING 4 blocks and another 4 from 2 away, they merge instead of being separate blobs

    private final Level level;
    int[][] layerArray = new int[40][22];
    Dictionary<Integer, BufferedImage> layerDictionary = new Hashtable<>();
    BufferedImage[][] crops = {};

    BufferedImage[][][] tileSets;
    int[] cropsType = IntStream.rangeClosed(1, 480).toArray();
    int[][] timesLeft = new int[40][22];

    private final Player player;

    public boolean isCropCollectable = false;
    public int closestCropRow = 0;
    public int closestCropColumn = 0;

    public CropsLayer(Level level) {
        this.level = level;
        this.player = level.getPlayer();
    }

    public static BufferedImage[] addValue(BufferedImage[] array, BufferedImage x) {
        BufferedImage[] newArray = new BufferedImage[array.length + 1];
        System.arraycopy(array, 0, newArray, 0, array.length);
        newArray[array.length] = x;
        return newArray;
    }

    private void makeCropsArrays() {
        BufferedImage[] potato = {};
        BufferedImage[] goldPotato = {};
        BufferedImage[] purplePotato = {};
        BufferedImage[] sweetPotato = {};
        BufferedImage[] cassava = {};
        BufferedImage[] daikonRadish = {};
        BufferedImage[] carrot = {};
        BufferedImage[] parsnip = {};
        BufferedImage[] radish = {};
        BufferedImage[] beet = {};
        BufferedImage[] turnip = {};
        BufferedImage[] rutabaga = {};
        BufferedImage[] garlic = {};
        BufferedImage[] yellowOnion = {};
        BufferedImage[] redOnion = {};
        BufferedImage[] whiteOnion = {};
        BufferedImage[] scallion = {};
        BufferedImage[] hotPepper = {};
        BufferedImage[] greenBellPepper = {};
        BufferedImage[] redBellPepper = {};
        BufferedImage[] orangeBellPepper = {};
        BufferedImage[] yellowBellPepper = {};
        BufferedImage[] chiliPepper = {};
        BufferedImage[] watermelon = {};
        BufferedImage[] honeydew = {};
        BufferedImage[] cantaloupe = {};
        BufferedImage[] acornSquash = {};
        BufferedImage[] pumpkin = {};
        BufferedImage[] crookneckSquash = {};
        BufferedImage[] butternutSquash = {};
        BufferedImage[] corn = {};
        BufferedImage[] maize = {};
        BufferedImage[] asparagus = {};
        BufferedImage[] rhubarb = {};
        BufferedImage[] romaineLettuce = {};
        BufferedImage[] icebergLettuce = {};
        BufferedImage[] kale = {};
        BufferedImage[] redCabbage = {};
        BufferedImage[] cabbage = {};
        BufferedImage[] celery = {};
        BufferedImage[] bokChoy = {};
        BufferedImage[] fennelBulb = {};
        BufferedImage[] brusselsSprouts = {};
        BufferedImage[] cauliflower = {};
        BufferedImage[] broccoli = {};
        BufferedImage[] artichoke = {};
        BufferedImage[] leek = {};
        BufferedImage[] kohlrabi = {};
        BufferedImage[] eggplant = {};
        BufferedImage[] zucchini = {};
        BufferedImage[] yellowSquash = {};
        BufferedImage[] cucumber = {};
        BufferedImage[] strawberry = {};
        BufferedImage[] blackberry = {};
        BufferedImage[] raspberry = {};
        BufferedImage[] blueberry = {};
        BufferedImage[] grapes = {};
        BufferedImage[] greenGrapes = {};
        BufferedImage[] cherryTomatoes = {};
        BufferedImage[] tomato = {};
        BufferedImage[] largeTomato = {};
        BufferedImage[] peas = {};
        BufferedImage[] hops = {};
        BufferedImage[] greenBeans = {};
        BufferedImage[] coffee = {};
        BufferedImage[] pineapple = {};
        BufferedImage[] kiwi = {};


        for (int x = 0; x < 5; x++)
            for (int y = 0; y < LoadSave.cropsTileset[x].length; y++) {
                if (y == 0) potato = addValue(potato, LoadSave.cropsTileset[x][y]);
                if (y == 1) goldPotato = addValue(goldPotato, LoadSave.cropsTileset[x][y]);
                if (y == 2) purplePotato = addValue(purplePotato, LoadSave.cropsTileset[x][y]);
                if (y == 3) sweetPotato = addValue(sweetPotato, LoadSave.cropsTileset[x][y]);
                if (y == 4) cassava = addValue(cassava, LoadSave.cropsTileset[x][y]);
                if (y == 5) daikonRadish = addValue(daikonRadish, LoadSave.cropsTileset[x][y]);
                if (y == 6) carrot = addValue(carrot, LoadSave.cropsTileset[x][y]);
                if (y == 7) parsnip = addValue(parsnip, LoadSave.cropsTileset[x][y]);
                if (y == 8) radish = addValue(radish, LoadSave.cropsTileset[x][y]);
                if (y == 9) beet = addValue(beet, LoadSave.cropsTileset[x][y]);
                if (y == 10) turnip = addValue(turnip, LoadSave.cropsTileset[x][y]);
                if (y == 11) rutabaga = addValue(rutabaga, LoadSave.cropsTileset[x][y]);
                if (y == 12) garlic = addValue(garlic, LoadSave.cropsTileset[x][y]);
                if (y == 13) yellowOnion = addValue(yellowOnion, LoadSave.cropsTileset[x][y]);
                if (y == 14) redOnion = addValue(redOnion, LoadSave.cropsTileset[x][y]);
                if (y == 15) whiteOnion = addValue(whiteOnion, LoadSave.cropsTileset[x][y]);
                if (y == 16) scallion = addValue(scallion, LoadSave.cropsTileset[x][y]);
                if (y == 17) hotPepper = addValue(hotPepper, LoadSave.cropsTileset[x][y]);
                if (y == 18) greenBellPepper = addValue(greenBellPepper, LoadSave.cropsTileset[x][y]);
                if (y == 19) redBellPepper = addValue(redBellPepper, LoadSave.cropsTileset[x][y]);
                if (y == 20) orangeBellPepper = addValue(orangeBellPepper, LoadSave.cropsTileset[x][y]);
                if (y == 21) yellowBellPepper = addValue(yellowBellPepper, LoadSave.cropsTileset[x][y]);
                if (y == 22) yellowBellPepper = addValue(yellowBellPepper, LoadSave.cropsTileset[x][y]);
                if (y == 23) chiliPepper = addValue(chiliPepper, LoadSave.cropsTileset[x][y]);
                if (y == 24) watermelon = addValue(watermelon, LoadSave.cropsTileset[x][y]);
                if (y == 25) honeydew = addValue(honeydew, LoadSave.cropsTileset[x][y]);
                if (y == 26) cantaloupe = addValue(cantaloupe, LoadSave.cropsTileset[x][y]);
                if (y == 27) acornSquash = addValue(acornSquash, LoadSave.cropsTileset[x][y]);
                if (y == 28) pumpkin = addValue(pumpkin, LoadSave.cropsTileset[x][y]);
                if (y == 29) crookneckSquash = addValue(crookneckSquash, LoadSave.cropsTileset[x][y]);
                if (y == 30) butternutSquash = addValue(butternutSquash, LoadSave.cropsTileset[x][y]);
                if (y == 31) corn = addValue(corn, LoadSave.cropsTileset[x][y]);
                if (y == 32) maize = addValue(maize, LoadSave.cropsTileset[x][y]);
            }
        for (int x = 5; x < 10; x++)
            for (int y = 0; y < LoadSave.cropsTileset[x].length; y++) {
                if (y == 0) asparagus = addValue(asparagus, LoadSave.cropsTileset[x][y]);
                if (y == 1) rhubarb = addValue(rhubarb, LoadSave.cropsTileset[x][y]);
                if (y == 2) romaineLettuce = addValue(romaineLettuce, LoadSave.cropsTileset[x][y]);
                if (y == 3) icebergLettuce = addValue(icebergLettuce, LoadSave.cropsTileset[x][y]);
                if (y == 4) kale = addValue(kale, LoadSave.cropsTileset[x][y]);
                if (y == 5) redCabbage = addValue(redCabbage, LoadSave.cropsTileset[x][y]);
                if (y == 6) cabbage = addValue(cabbage, LoadSave.cropsTileset[x][y]);
                if (y == 7) celery = addValue(celery, LoadSave.cropsTileset[x][y]);
                if (y == 8) bokChoy = addValue(bokChoy, LoadSave.cropsTileset[x][y]);
                if (y == 9) fennelBulb = addValue(fennelBulb, LoadSave.cropsTileset[x][y]);
                if (y == 10) brusselsSprouts = addValue(brusselsSprouts, LoadSave.cropsTileset[x][y]);
                if (y == 11) cauliflower = addValue(cauliflower, LoadSave.cropsTileset[x][y]);
                if (y == 12) broccoli = addValue(broccoli, LoadSave.cropsTileset[x][y]);
                if (y == 13) artichoke = addValue(artichoke, LoadSave.cropsTileset[x][y]);
                if (y == 14) leek = addValue(leek, LoadSave.cropsTileset[x][y]);
                if (y == 15) kohlrabi = addValue(kohlrabi, LoadSave.cropsTileset[x][y]);
                if (y == 16) eggplant = addValue(eggplant, LoadSave.cropsTileset[x][y]);
                if (y == 17) zucchini = addValue(zucchini, LoadSave.cropsTileset[x][y]);
                if (y == 18) yellowSquash = addValue(yellowSquash, LoadSave.cropsTileset[x][y]);
                if (y == 19) cucumber = addValue(cucumber, LoadSave.cropsTileset[x][y]);
                if (y == 20) strawberry = addValue(strawberry, LoadSave.cropsTileset[x][y]);
                if (y == 21) blackberry = addValue(blackberry, LoadSave.cropsTileset[x][y]);
                if (y == 22) yellowBellPepper = addValue(yellowBellPepper, LoadSave.cropsTileset[x][y]);
                if (y == 23) raspberry = addValue(raspberry, LoadSave.cropsTileset[x][y]);
                if (y == 24) blueberry = addValue(blueberry, LoadSave.cropsTileset[x][y]);
                if (y == 25) grapes = addValue(grapes, LoadSave.cropsTileset[x][y]);
                if (y == 26) greenGrapes = addValue(greenGrapes, LoadSave.cropsTileset[x][y]);
                if (y == 27) cherryTomatoes = addValue(cherryTomatoes, LoadSave.cropsTileset[x][y]);
                if (y == 28) tomato = addValue(tomato, LoadSave.cropsTileset[x][y]);
                if (y == 29) largeTomato = addValue(largeTomato, LoadSave.cropsTileset[x][y]);
                if (y == 30) peas = addValue(peas, LoadSave.cropsTileset[x][y]);
                if (y == 31) hops = addValue(hops, LoadSave.cropsTileset[x][y]);
                if (y == 32) greenBeans = addValue(greenBeans, LoadSave.cropsTileset[x][y]);
            }
        for (int x = 10; x < 15; x++)
            for (int y = 29; y < LoadSave.cropsTileset[x].length; y++) {
                if (y == 29) coffee = addValue(coffee, LoadSave.cropsTileset[x][y]);
                if (y == 30) pineapple = addValue(pineapple, LoadSave.cropsTileset[x][y]);
                if (y == 31) kiwi = addValue(kiwi, LoadSave.cropsTileset[x][y]);
            }
        crops = new BufferedImage[][]{
                potato,
                goldPotato,
                purplePotato,
                sweetPotato,
                cassava,
                daikonRadish,
                carrot,
                parsnip,
                radish,
                beet,
                turnip,
                rutabaga,
                garlic,
                yellowOnion,
                redOnion,
                whiteOnion,
                scallion,
                hotPepper,
                greenBellPepper,
                redBellPepper,
                orangeBellPepper,
                yellowBellPepper,
                chiliPepper,
                watermelon,
                honeydew,
                cantaloupe,
                acornSquash,
                pumpkin,
                crookneckSquash,
                butternutSquash,
                corn,
                maize,
                asparagus,
                rhubarb,
                romaineLettuce,
                icebergLettuce,
                kale,
                redCabbage,
                cabbage,
                celery,
                bokChoy,
                fennelBulb,
                brusselsSprouts,
                cauliflower,
                broccoli,
                artichoke,
                leek,
                kohlrabi,
                eggplant,
                zucchini,
                yellowSquash,
                cucumber,
                strawberry,
                blackberry,
                raspberry,
                blueberry,
                grapes,
                greenGrapes,
                cherryTomatoes,
                tomato,
                largeTomato,
                peas,
                hops,
                greenBeans,
                coffee,
                pineapple,
                kiwi
        };
        tileSets = new BufferedImage[][][]{crops};
    }

    public void drawLayer(Graphics g, float offsetX, float offsetY) {
        //int y = -1; //DRAW ALL CROPS
        for (int row = 0; row < layerArray.length; row++)
            for (int column = 0; column < layerArray[row].length; column++) {
                //y+=5;
                //g.drawImage(layerDictionary.get(1), 100, 100, 320, 640, null); //DRAW TEST PLANT
                g.drawImage(layerDictionary.get(layerArray[row][column]), (int) (row * (64 * zoomMultiplier) + (32 * zoomMultiplier)) - (int) offsetX, (int) (column * (64 * zoomMultiplier) + (16 * zoomMultiplier)) - (int) offsetY, (int) ((64 * zoomMultiplier)), (int) ((128 * zoomMultiplier)), null);
                //g.drawImage(layerDictionary.get(y), (int)(column * (64 * zoomMultiplier) + (32 * zoomMultiplier)), (int)(row * (64 * zoomMultiplier) + (16 * zoomMultiplier)), (int)((64 * zoomMultiplier)), (int)((128 * zoomMultiplier)), null); //DRAW ALL PLANTS
            }

    }

    public void drawCollectionButton(Graphics g, float offsetX, float offsetY) {
        closestCropRow = 0;
        closestCropColumn = 0;
        int closestDistance = 1000;

        for (int row = 0; row < layerArray.length; row++)
            for (int column = 0; column < layerArray[row].length; column++) {
                if (layerArray[row][column] != 0 && timesLeft[row][column] == 0) {
                    if (playerDistance(row , column, offsetX, offsetY, g) < closestDistance) {
                        closestDistance = playerDistance(row , column, offsetX, offsetY, g);
                        closestCropRow = row;
                        closestCropColumn = column;
                    }
                }
            }

        isCropCollectable = closestDistance <= 196;
        if (isCropCollectable) g.drawImage(LoadSave.GetSpriteAtlas(LoadSave.collectionIcon), (int) (closestCropRow * (64 * zoomMultiplier) + (48 * zoomMultiplier)) - (int) offsetX, (int) (closestCropColumn * (64 * zoomMultiplier) + (100 * zoomMultiplier)) - (int) offsetY, (int) ((32 * zoomMultiplier)), (int) ((32 * zoomMultiplier)), null);

    }

    private int playerDistance(int row, int column, float offsetX, float offsetY, Graphics g) {
        double x = player.getHitbox().getX() - offsetX;
        double y = player.getHitbox().getY() - offsetY;

        //g.drawRect((int) x, (int) y, (int) player.getHitbox().getWidth(), (int) player.getHitbox().getHeight());
        //g.drawRect((int) (row * (64 * zoomMultiplier) + (32 * zoomMultiplier)) - (int) offsetX, (int) (column * (64 * zoomMultiplier) + (80 * zoomMultiplier)) - (int) offsetY, (int) ((64 * zoomMultiplier)), (int) ((128 * zoomMultiplier)));

        row = (int) (row * (64 * zoomMultiplier) + (32 * zoomMultiplier)) - (int) offsetX;
        column = (int) (column * (64 * zoomMultiplier) + (128 * zoomMultiplier)) - (int) offsetY;

        int distanceDifference = (int) Math.sqrt(((row - x) * (row-x)) + ((column - y) * (column - y)));

        return (int) (distanceDifference * zoomMultiplier);
    }

    public void editLayer(int x, int y, int amount) {

        layerArray[x / (int) (64 * zoomMultiplier)][y / (int) (64 * zoomMultiplier)] += amount;
        if (layerArray[x / (int) (64 * zoomMultiplier)][y / (int) (64 * zoomMultiplier)] >= 66 * 5)
            layerArray[x / (int) (64 * zoomMultiplier)][y / (int) (64 * zoomMultiplier)] = 0;

        growPlant(x, y, 5000);

    }

    public void collectPlant(int row, int column) {
        System.out.println(layerArray[row][column]);
        if ((1+layerArray[row][column])%5 == 0) {
            player.getInventory().addInventoryItem(player.getInventory().getToggle(), 1, 100 + layerArray[row][column]/5);
        }
        layerArray[row][column] = 0;
    }

    public void drawCollisionMask(Graphics g, float offsetX, float offsetY) {

        int y = (int) level.getPlayer().getHitbox().getY();

        for (int row = 0; row < layerArray.length; row++)
            for (int column = 0; column < layerArray[row].length; column++) {
                if (y <= column * (64 * zoomMultiplier) + (100 * zoomMultiplier))
                    g.drawImage(layerDictionary.get(layerArray[row][column]), (int) (row * (64 * zoomMultiplier) + (32 * zoomMultiplier)) - (int) offsetX, (int) (column * (64 * zoomMultiplier) + (16 * zoomMultiplier)) - (int) offsetY, (int) ((64 * zoomMultiplier)), (int) ((128 * zoomMultiplier)), null);
            }

    }

    public void growPlant(int x, int y, int timeCount) {
        Timer timer = getTimer(x, y, timeCount);
        long startTime = System.currentTimeMillis();
        timer.start();
        timer.setDelay(5000);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            long currentTime = System.currentTimeMillis();
            long elapsedTime = currentTime - startTime;
            long remainingTime = timeCount - (elapsedTime % timeCount);
            timesLeft[x / (int) (64 * zoomMultiplier)][y / (int) (64 * zoomMultiplier)] = (int) remainingTime;
        }));
    }

    @NotNull
    private Timer getTimer(int x, int y, int timeCount) {
        ActionListener taskPerformer = evt -> {
            //System.out.println("Timer Working");
            if ((layerArray[x / (int) (64 * zoomMultiplier)][y / (int) (64 * zoomMultiplier)] + 1) % 5 != 0 && layerArray[x / (int) (64 * zoomMultiplier)][y / (int) (64 * zoomMultiplier)] % 5 != 0 && layerArray[x / (int) (64 * zoomMultiplier)][y / (int) (64 * zoomMultiplier)] != 0) {
                layerArray[x / (int) (64 * zoomMultiplier)][y / (int) (64 * zoomMultiplier)] += 1;
            } else {
                ((Timer) evt.getSource()).stop();
            }

        };
        Timer timer = new Timer(timeCount, taskPerformer);
        timer.setRepeats(true);
        return timer;
    }

    @Override
    public void numberTiles(@NotNull BufferedImage[][][] tileSets, Dictionary<Integer, BufferedImage> layerDictionary) {
        LayerInterface.super.numberTiles(this.tileSets, this.layerDictionary);
    }


    @Override
    public void formatLayer(int[][] layerArray, int[] arrayType) {
        for (int row = 0; row < layerArray.length; row++)
            for (int column = 0; column < layerArray[row].length; column++) {
                //layerArray[row][column] = 0; //Clear Array
                if ((layerArray[row][column] + 1) % 5 != 0 && layerArray[row][column] % 5 != 0 && layerArray[row][column] != 0)
                    growPlant(row * (int) (64 * zoomMultiplier), column * (int) (64 * zoomMultiplier), timesLeft[row][column]);
                else timesLeft[row][column] = 0;
            }
    }

    @Override
    public void createLayer() {
        makeCropsArrays();
        numberTiles(tileSets, layerDictionary);
        formatLayer(layerArray, cropsType);
    }

    @Override
    public int[][] getLayerArray() {
        return layerArray;
    }

    public void setLayerArray(int[][] layerArray) {
        this.layerArray = layerArray;
    }

    public int[][] getTimesLeft() {
        return timesLeft;
    }

    public void setTimesLeft(int[][] timesLeft) {
        this.timesLeft = timesLeft;
    }
}
