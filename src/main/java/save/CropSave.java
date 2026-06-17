package save;

/**
 * One persisted crop: grid cell, crop-catalog index, plant timestamp, and whether it's been harvested.
 * A dumb value holder (no engine types, no live {@code PlantedCrop}/{@code CropType}, which carry
 * non-serializable textures) — just the facts the save format reads and writes.
 */
public final class CropSave {
    public int col;
    public int row;
    public int type;        // CropType index
    public long planted;    // epoch millis when planted (growth is derived from this)
    public boolean harvested;

    public CropSave() {
    }

    public CropSave(int col, int row, int type, long planted, boolean harvested) {
        this.col = col;
        this.row = row;
        this.type = type;
        this.planted = planted;
        this.harvested = harvested;
    }
}
