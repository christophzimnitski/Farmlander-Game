package levels;

import main.Level;
import org.jetbrains.annotations.NotNull;
import utilz.LoadSave;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.stream.IntStream;

import static utilz.Constants.Zoom.zoomMultiplier;

public class CollisionLayer implements LayerInterface, Serializable {
    private final Level level;
    int[][] layerArray = new int[40][22];
    Dictionary<Integer, BufferedImage> layerDictionary = new Hashtable<>();
    BufferedImage[][][] tileSets = {LoadSave.tallGrassTileset, LoadSave.wheatTileset, LoadSave.youngWheatTileset};
    int[] tallGrassType = IntStream.rangeClosed(1, 18).toArray();
    int[] wheatType = IntStream.rangeClosed(19, 36).toArray();
    int[] youngWheatType = IntStream.rangeClosed(37, 54).toArray();
    //int[] fenceType = IntStream.rangeClosed(55, 72).toArray();
    //int[] fenceAltType = IntStream.rangeClosed(73, 90).toArray();
    //int[] reedType = IntStream.rangeClosed(91, 108).toArray();

    public CollisionLayer(Level level) {
        this.level = level;
    }

    public void drawLayer(Graphics g, float offsetX, float offsetY) {
        int y = (int) level.getPlayer().getHitbox().getY();

        for (int j = 0; j < layerArray.length; j++)
            for (int i = 0; i < layerArray[j].length; i++) {
                if (y >= i * (64 * zoomMultiplier) + (63 * zoomMultiplier))
                    g.drawImage(layerDictionary.get(layerArray[j][i]), j * (int) (64 * zoomMultiplier) - (int) offsetX, i * (int) (64 * zoomMultiplier) - (int) offsetY, (int) (64 * zoomMultiplier), (int) (64 * zoomMultiplier), null);
            }

    }

    public void drawCollisionMask(Graphics g, float offsetX, float offsetY) {

        int y = (int) level.getPlayer().getHitbox().getY();

        for (int j = 0; j < layerArray.length; j++)
            for (int i = 0; i < layerArray[j].length; i++) {
                if (y <= i * (64 * zoomMultiplier) + (63 * zoomMultiplier))
                    g.drawImage(layerDictionary.get(layerArray[j][i]), j * (int) (64 * zoomMultiplier) - (int) offsetX, i * (int) (64 * zoomMultiplier) - (int) offsetY, (int) (64 * zoomMultiplier), (int) (64 * zoomMultiplier), null);
            }

    }

    @Override
    public void numberTiles(@NotNull BufferedImage @NotNull [][][] tileSets, Dictionary<Integer, BufferedImage> layerDictionary) {
        LayerInterface.super.numberTiles(tileSets, layerDictionary);
    }

    public void editLayer(int x, int y, int amount) {
        layerArray[x / (int) (64 * zoomMultiplier)][y / (int) (64 * zoomMultiplier)] += amount;
        if (layerArray[x / (int) (64 * zoomMultiplier)][y / (int) (64 * zoomMultiplier)] >= tileSets.length * 18)
            layerArray[x / (int) (64 * zoomMultiplier)][y / (int) (64 * zoomMultiplier)] = 0;

        formatAllLayers();
    }

    @Override
    public void formatLayer(int[][] layerArray, int[] arrayType) {
        for (int x = 0; x < layerArray.length; x++)
            for (int y = 0; y < layerArray[x].length; y++) {
                int leftTile;
                int rightTile;
                int upTile;
                int downTile;
                int upLeft;
                int upRight;
                int downLeft;
                int downRight;

                if (x >= 1) leftTile = layerArray[x - 1][y];
                else {
                    leftTile = -1;
                }
                if (x + 1 < layerArray.length) rightTile = layerArray[x + 1][y];
                else {
                    rightTile = -1;
                }
                if (y >= 1) upTile = layerArray[x][y - 1];
                else {
                    upTile = -1;
                }
                if (y + 1 < layerArray[x].length) downTile = layerArray[x][y + 1];
                else {
                    downTile = -1;
                }
                if (x >= 1 && y >= 1) upLeft = layerArray[x - 1][y - 1];
                else {
                    upLeft = -1;
                }
                if (x + 1 < layerArray.length && y >= 1) upRight = layerArray[x + 1][y - 1];
                else {
                    upRight = -1;
                }
                if (y + 1 < layerArray[x].length && x >= 1) downLeft = layerArray[x - 1][y + 1];
                else {
                    downLeft = -1;
                }
                if (y + 1 < layerArray[x].length && x + 1 < layerArray.length) downRight = layerArray[x + 1][y + 1];
                else {
                    downRight = -1;
                }

                boolean left = Arrays.stream(arrayType).anyMatch(q -> q == leftTile);
                boolean up = Arrays.stream(arrayType).anyMatch(q -> q == upTile);
                boolean right = Arrays.stream(arrayType).anyMatch(q -> q == rightTile);
                boolean down = Arrays.stream(arrayType).anyMatch(q -> q == downTile);
                boolean upLeftBool = Arrays.stream(arrayType).anyMatch(q -> q == upLeft);
                boolean upRightBool = Arrays.stream(arrayType).anyMatch(q -> q == upRight);
                boolean downLeftBool = Arrays.stream(arrayType).anyMatch(q -> q == downLeft);
                boolean downRightBool = Arrays.stream(arrayType).anyMatch(q -> q == downRight);
                int finalX = x;
                int finalY = y;
                boolean tileMatch = Arrays.stream(arrayType).anyMatch(q -> q == layerArray[finalX][finalY]);


                if (down && tileMatch) layerArray[x][y] = arrayType[0] - 1 + 1;
                if (up && tileMatch) layerArray[x][y] = arrayType[0] - 1 + 4;

                if (left && right && !up && tileMatch && downLeftBool && downRightBool)
                    layerArray[x][y] = arrayType[0] - 1 + 8;
                if (left && right && !down && tileMatch && upLeftBool && upRightBool)
                    layerArray[x][y] = arrayType[0] - 1 + 14;
                if (up && down && !left && tileMatch) layerArray[x][y] = arrayType[0] - 1 + 10;
                if (up && down && !right && tileMatch) layerArray[x][y] = arrayType[0] - 1 + 12;
                if (left && right && !up && tileMatch && !downLeftBool) layerArray[x][y] = arrayType[0] - 1 + 7;
                if (left && right && !up && tileMatch && !downRightBool) layerArray[x][y] = arrayType[0] - 1 + 9;
                if (left && right && !down && tileMatch && !upLeftBool) layerArray[x][y] = arrayType[0] - 1 + 13;
                if (left && right && !down && tileMatch && !upRightBool) layerArray[x][y] = arrayType[0] - 1 + 15;


                if (up && down && right && left && tileMatch) layerArray[x][y] = arrayType[0] - 1 + 11;
                if (up && down && right && left && !upRightBool && tileMatch) layerArray[x][y] = arrayType[0] - 1 + 5;
                if (up && down && right && left && !downRightBool && tileMatch) layerArray[x][y] = arrayType[0] - 1 + 2;
                if (up && down && right && left && !upLeftBool && tileMatch) layerArray[x][y] = arrayType[0] - 1 + 6;
                if (up && down && right && left && !downLeftBool && tileMatch) layerArray[x][y] = arrayType[0] - 1 + 3;

                if (right && up && !left && !down && upRightBool && tileMatch) layerArray[x][y] = arrayType[0] - 1 + 13;
                if (right && down && !left && !up && downRightBool && tileMatch)
                    layerArray[x][y] = arrayType[0] - 1 + 7;
                if (left && up && !down && !right && upLeftBool && tileMatch) layerArray[x][y] = arrayType[0] - 1 + 15;
                if (left && down && !up && !right && downLeftBool && tileMatch) layerArray[x][y] = arrayType[0] - 1 + 9;

                //layerArray[x][y] = 0; //Clear layer
            }
    }

    @Override
    public void createLayer() {
        numberTiles(tileSets, layerDictionary);
        formatAllLayers();

    }

    public void formatAllLayers() {
        formatLayer(layerArray, tallGrassType);
        formatLayer(layerArray, wheatType);
        formatLayer(layerArray, youngWheatType);
    }

    public int[][] getLayerArray() {
        return layerArray;
    }

    public void setLayerArray(int[][] layerArray) {
        this.layerArray = layerArray;
    }
}
