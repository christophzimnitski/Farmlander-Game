package world;

import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * A free-placed prop in the world — a boat, market stall, sack, barrel, crate, etc. Unlike a tile, an object
 * isn't snapped to a single 32px cell: it's an arbitrary sprite cut from a source sheet and dropped at a
 * world position, depth-sorted against the player by its base (ground) line so the player can walk in front
 * of or behind it. This is the "object layer" that big multi-tile art needs (forcing a boat into the tile
 * grid would mean hand-placing a dozen fragments with broken depth).
 *
 * <p>Serialisable state is the source rect ({@code sheetPath} + {@code src*}), the world position and the
 * draw size — all plain data, so a saved map can rebuild the object. The {@link TextureRegion} is
 * <em>derived</em> from the sheet (Y-flipped, half-texel inset) and set after construction/load via
 * {@link #setRegion}; it isn't persisted.
 */
public final class PlacedObject {
    public final String sheetPath;          // classpath path of the source sheet
    public final int srcX, srcY, srcW, srcH; // source rect within the sheet, in sheet pixels
    public float worldX, worldY;            // top-left in world units
    public float drawW, drawH;              // draw size in world units

    private TextureRegion region;           // transient: built from the sheet, not serialised

    public PlacedObject(String sheetPath, int srcX, int srcY, int srcW, int srcH,
                        float worldX, float worldY, float drawW, float drawH) {
        this.sheetPath = sheetPath;
        this.srcX = srcX;
        this.srcY = srcY;
        this.srcW = srcW;
        this.srcH = srcH;
        this.worldX = worldX;
        this.worldY = worldY;
        this.drawW = drawW;
        this.drawH = drawH;
    }

    /** The depth ground line: the sprite's bottom edge. Objects whose base is below the player's feet draw
     *  in front of him, mirroring how bushes/crops depth-sort. */
    public float baseY() { return worldY + drawH; }

    public TextureRegion region() { return region; }

    public void setRegion(TextureRegion region) { this.region = region; }

    /** Is the world point inside this object's draw box? (Used to pick/erase an object under the cursor.) */
    public boolean contains(float wx, float wy) {
        return wx >= worldX && wx < worldX + drawW && wy >= worldY && wy < worldY + drawH;
    }
}
