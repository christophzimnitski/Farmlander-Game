package crops;

/**
 * Pure geometry for a crop sitting on a single world cell — the one place that knows how a crop's anchor,
 * its solid footprint, and its 2-tile-tall sprite relate to a {@code (col,row)} cell and the tile size.
 * Collision, planting, and rendering all read from here so they can never disagree (the duplication that
 * the {@code PlayState} split is removing). No libGDX: just arithmetic, so it's unit-testable on its own.
 *
 * <p>A crop's <em>root</em> is the cell centre. Its footprint (what blocks the player) is a small box
 * centred on the root. Its sprite is {@code tile} wide × {@code 2*tile} tall, drawn so the root sits a
 * quarter-tile above the sprite's bottom; the player draws in front of it once their feet pass the
 * ground-line ~2/3 of the way down the sprite (the old {@code CropsLayer} depth mask).
 */
public final class CropGeometry {
    /** Player passes in front of the crop once feetY ≤ groundLineY: ~2/3 down the 2-tile sprite. */
    private static final float GROUND_LINE_RATIO = 126f / 192f;

    public final float tile;
    public final float spriteWidth;     // CROP_W
    public final float spriteHeight;    // CROP_H
    public final float footprintWidth;  // CROP_COLL_W — narrower than a tile
    public final float footprintHeight; // CROP_COLL_H — ~quarter-tile tall

    public CropGeometry(float tile) {
        this.tile = tile;
        this.spriteWidth = tile;
        this.spriteHeight = tile * 2f;
        this.footprintWidth = 48f;
        this.footprintHeight = 24f;
    }

    /** Root anchor (cell centre) X. */
    public float rootX(int col) { return (col + 0.5f) * tile; }

    /** Root anchor (cell centre) Y. */
    public float rootY(int row) { return (row + 0.5f) * tile; }

    /** Left edge of the solid footprint box, centred on the root. */
    public float footprintX(int col) { return rootX(col) - footprintWidth / 2f; }

    /** Top edge of the solid footprint box, centred on the root. */
    public float footprintY(int row) { return rootY(row) - footprintHeight / 2f; }

    /** Left edge where the sprite is drawn (sprite centred on the root). */
    public float spriteX(int col) { return rootX(col) - spriteWidth / 2f; }

    /** Top edge of the 2-tile-tall sprite: the root sits a quarter-tile above the sprite's bottom. */
    public float spriteTopY(int row) { return rootY(row) - (spriteHeight - tile / 4f); }

    /** Depth ground-line: the player is drawn in front of the crop when their feetY ≤ this. */
    public float groundLineY(int row) { return spriteTopY(row) + spriteHeight * GROUND_LINE_RATIO; }
}
