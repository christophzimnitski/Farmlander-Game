package utilz;

import core.Log;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

public class LoadSave {
    public static final String PLAYER_ATLAS = "player_animations/player_mining.png";
    public static final String TALL_GRASS = "tilesets/tallgrass.png";
    public static final String WHEAT = "tilesets/wheat.png";
    public static final String YOUNG_WHEAT = "tilesets/youngwheat.png";
    public static final String FENCE = "tilesets/fence.png";
    public static final String FENCE_ALT = "tilesets/fence_alt.png";
    public static final String PLOWED_SOIL = "tilesets/plowed_soil.png";
    public static final String REED = "tilesets/reed.png";
    public static final String SAND = "tilesets/sand.png";
    public static final String SAND_WATER = "tilesets/sandwater.png";
    public static final String PLANTS = "tilesets/plants.png";
    public static final String COLLECTION_ICON = "ui/collect_button.png";
    public static final BufferedImage INVENTORY = getSpriteAtlas("ui/Inventory.png");
    public static final BufferedImage INVENTORY_TOGGLE = getSpriteAtlas("ui/toggle.png");
    public static final BufferedImage[][] grassTileset = loadAnimations(getSpriteAtlas("tilesets/grassTileset.png"), 1, 3);

    public static final BufferedImage[][] tallGrassTileset = loadAnimations(getSpriteAtlas("tilesets/tallgrass.png"), 6, 3);
    public static final BufferedImage[][] wheatTileset = loadAnimations(getSpriteAtlas("tilesets/wheat.png"), 6, 3);
    public static final BufferedImage[][] youngWheatTileset = loadAnimations(getSpriteAtlas("tilesets/youngwheat.png"), 6, 3);
    public static final BufferedImage[][] fenceTileset = loadAnimations(getSpriteAtlas("tilesets/fence.png"), 6, 3);
    public static final BufferedImage[][] fenceAltTileset = loadAnimations(getSpriteAtlas("tilesets/fence_alt.png"), 6, 3);
    public static final BufferedImage[][] reedTileset = loadAnimations(getSpriteAtlas("tilesets/reed.png"), 6, 3);
    public static final BufferedImage[][] plowedSoilTileset = loadAnimations(getSpriteAtlas("tilesets/plowed_soil.png"), 6, 3);
    public static final BufferedImage[][] cropsTileset = loadAnimations(getSpriteAtlas("tilesets/crops.png"), 15, 32, 64, 32);
    public static final BufferedImage[][] numbers = loadAnimations(getSpriteAtlas("ui/game_font.png"), 1, 11, 8, 8);

    public static BufferedImage getSpriteAtlas(String fileName) {
        BufferedImage img;

        InputStream is = LoadSave.class.getResourceAsStream("/" + fileName);

        try {
            assert is != null;
            img = ImageIO.read(is);

        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                assert is != null;
                is.close();
            } catch (IOException e) {
                Log.warn("LoadSave", "failed to close stream for " + fileName, e);
            }
        }
        return img;
    }

    private static BufferedImage[][] loadAnimations(BufferedImage tileset, int height, int width) {

        BufferedImage[][] tiles = new BufferedImage[height][width];

        for (int j = 0; j < tiles.length; j++)
            for (int i = 0; i < tiles[j].length; i++)
                tiles[j][i] = tileset.getSubimage(i * 32, j * 32, 32, 32);

        return tiles;
    }

    private static BufferedImage[][] loadAnimations(BufferedImage tileset, int height, int width, int tileHeight, int tileWidth) {

        BufferedImage[][] tiles = new BufferedImage[height][width];

        for (int j = 0; j < tiles.length; j++)
            for (int i = 0; i < tiles[j].length; i++) {
                tiles[j][i] = tileset.getSubimage(i * tileWidth, j * tileHeight, tileWidth, tileHeight);
            }
        return tiles;

    }
}
