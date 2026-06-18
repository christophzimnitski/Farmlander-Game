package world;

import java.util.Random;

/**
 * Builds the random background grass layer — the weighted tile pick lifted from the old
 * {@code GrassLayer.editLayer}: mostly tile 0 (plain ground shows through) with occasional detail tiles.
 * Used only as the fallback when there's no save to load grass from.
 */
public final class GrassGenerator {
    private GrassGenerator() {}

    public static TileLayer randomGrass(int cols, int rows, float tileSize) {
        TileLayer layer = new TileLayer(cols, rows, tileSize);
        Random random = new Random();
        for (int col = 0; col < cols; col++) {
            for (int row = 0; row < rows; row++) {
                double roll = random.nextDouble();
                int tile = roll < 0.70 ? 0   // 70% plain
                         : roll < 0.75 ? 1   //  5%
                         : roll < 0.85 ? 2   // 10%
                         : 3;                // 15%
                layer.set(col, row, tile);
            }
        }
        return layer;
    }
}
