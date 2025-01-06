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
import java.util.Random;

public class GrassLayer implements LayerInterface, Serializable {
    Random random = new Random();
    int[][] layerArray = new int[(int) (Level.TILES_IN_WIDTH*Level.SCALE)][(int) (Level.TILES_IN_HEIGHT*Level.SCALE)];
    Dictionary<Integer, BufferedImage> layerDictionary = new Hashtable<>();
    BufferedImage[][][] tileSets = {LoadSave.grassTileset};

    public void drawLayer(Graphics g, float offsetX, float offsetY) {
        LayerInterface.super.drawLayer(g, layerArray, layerDictionary, offsetX, offsetY);
    }

    @Override
    public void numberTiles(@NotNull BufferedImage @NotNull [][][] tileSets, Dictionary<Integer, BufferedImage> layerDictionary) {
        LayerInterface.super.numberTiles(tileSets, layerDictionary);
    }

    public void editLayer() {
        for (int j = 0; j < layerArray.length; j++)
            for (int i = 0; i < layerArray[j].length; i++) {
                double randomValue = random.nextDouble();

                int result;
                if (randomValue < 0.7) {
                    result = 0; // 30% chance
                } else if (randomValue < 0.75) {
                    result = 1; // 20% chance (0.5 - 0.3 = 0.2)
                } else if (randomValue < 0.85) {
                    result = 2;
                } else result = 3;
                layerArray[j][i] = result;
            }
    }

    @Override
    public void formatLayer(int[][] layerArray, int[] arrayType) {

    }

    @Override
    public void createLayer() {
        numberTiles(tileSets, layerDictionary);
    }

    public int[][] getLayerArray() {
        return layerArray;
    }

    public void setLayerArray(int[][] layerArray) {
        this.layerArray = layerArray;
    }
}
