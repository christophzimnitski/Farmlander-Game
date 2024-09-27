package utilz;

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
    public static final BufferedImage INVENTORY = GetSpriteAtlas("ui/Inventory.png");
    public static final BufferedImage INVENTORY_TOGGLE = GetSpriteAtlas("ui/toggle.png");
    public static final BufferedImage[][] grassTileset = loadAnimations(GetSpriteAtlas("tilesets/grassTileset.png"), 1, 3);

    public static final BufferedImage[][] tallGrassTileset = loadAnimations(GetSpriteAtlas("tilesets/tallgrass.png"), 6, 3);
    public static final BufferedImage[][] wheatTileset = loadAnimations(GetSpriteAtlas("tilesets/wheat.png"), 6, 3);
    public static final BufferedImage[][] youngWheatTileset = loadAnimations(GetSpriteAtlas("tilesets/youngwheat.png"), 6, 3);
    public static final BufferedImage[][] fenceTileset = loadAnimations(GetSpriteAtlas("tilesets/fence.png"), 6, 3);
    public static final BufferedImage[][] fenceAltTileset = loadAnimations(GetSpriteAtlas("tilesets/fence_alt.png"), 6, 3);
    public static final BufferedImage[][] reedTileset = loadAnimations(GetSpriteAtlas("tilesets/reed.png"), 6, 3);
    public static final BufferedImage[][] plowedSoilTileset = loadAnimations(GetSpriteAtlas("tilesets/plowed_soil.png"), 6, 3);
    public static final BufferedImage[][] cropsTileset = loadAnimations(GetSpriteAtlas("tilesets/crops.png"), 15, 32, 64, 32);

    public static BufferedImage GetSpriteAtlas(String fileName) {
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
                e.printStackTrace();
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
