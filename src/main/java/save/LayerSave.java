package save;

/**
 * The persisted form of an editor-authored {@link world.layer.MapLayer}: its identity + properties + the
 * tile grid, as plain data. The drawable regions are GL state rebuilt from the layer's sheet on load, so
 * they aren't stored here — keeping the save layer engine-free and headlessly testable. Layers are matched
 * back to the live stack by {@link #name} on load.
 */
public final class LayerSave {
    public String name;
    public int kind;        // MapLayer.Kind ordinal
    public int sheetIndex;  // TilePalette sheet index, or -1
    public boolean solid;
    public boolean upright;
    public int[][] grid;

    public LayerSave() {}

    public LayerSave(String name, int kind, int sheetIndex, boolean solid, boolean upright, int[][] grid) {
        this.name = name;
        this.kind = kind;
        this.sheetIndex = sheetIndex;
        this.solid = solid;
        this.upright = upright;
        this.grid = grid;
    }
}
