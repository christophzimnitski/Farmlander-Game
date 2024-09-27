package levels;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Dictionary;

import static utilz.Constants.Zoom.zoomMultiplier;

public interface LayerInterface {
    default void drawLayer(Graphics g, int[][] layerArray, Dictionary<Integer, BufferedImage> layerDictionary, float offsetX, float offsetY) {
        for (int j = 0; j < layerArray.length; j++)
            for (int i = 0; i < layerArray[j].length; i++)
                g.drawImage(layerDictionary.get(layerArray[j][i]), j * (int) (64 * zoomMultiplier) - (int) offsetX, i * (int) (64 * zoomMultiplier) - (int) offsetY, (int) (64 * zoomMultiplier), (int) (64 * zoomMultiplier), null);
    }

    default void numberTiles(BufferedImage[][][] tileSets, Dictionary<Integer, BufferedImage> layerDictionary) {
        int count = 0;
        for (BufferedImage[][] tileSet : tileSets)
            for (BufferedImage[] bufferedImages : tileSet)
                for (BufferedImage bufferedImage : bufferedImages) {
                    count++;
                    layerDictionary.put(count, bufferedImage);
                }
    }

    void formatLayer(int[][] layerArray, int[] arrayType);

    void createLayer();

    int[][] getLayerArray();
}
