package world;

import java.util.Random;

/**
 * Builds the random background grass layer — the weighted tile pick lifted from the old
 * {@code GrassLayer.editLayer}: mostly tile 0 (plain ground shows through) with occasional detail tiles.
 * Used to generate a fresh world when there's no save to load grass from.
 *
 * <p>Generation is <b>seeded</b> so a given {@code seed} always produces the same bounded world — the world
 * is finite (Stardew-style), so the seed fixes one reproducible map rather than driving infinite terrain.
 */
public final class GrassGenerator {
    private GrassGenerator() {}

    public static ChunkedTileLayer randomGrass(int cols, int rows, float tileSize, long seed) {
        ChunkedTileLayer layer = new ChunkedTileLayer(cols, rows, tileSize);
        Random random = new Random(seed);
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
