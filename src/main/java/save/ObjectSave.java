package save;

/**
 * The persisted form of a {@link world.PlacedObject}: just its source rect (sheet + {@code src*}), world
 * position and draw size as plain data. The drawable {@code TextureRegion} is rebuilt from the sheet on load
 * (it's GL state), so it isn't stored here — keeping the save layer engine-free and headlessly testable.
 */
public final class ObjectSave {
    public String sheetPath;
    public int srcX, srcY, srcW, srcH;
    public float worldX, worldY, drawW, drawH;

    public ObjectSave() {}

    public ObjectSave(String sheetPath, int srcX, int srcY, int srcW, int srcH,
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
}
