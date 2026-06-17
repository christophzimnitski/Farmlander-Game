package tools.data;

/**
 * A dumb, serialization-friendly description of one tool's Q-action animation — everything needed to
 * slice it out of a spritesheet and play it, with no engine types. {@code PlayState} turns these into
 * live {@code ToolAction}s with the same slicers it already uses.
 *
 * <p>Fields are public and there's a no-arg constructor so libGDX {@code Json} can map them by name.
 * Where the blueprints come from (JSON, built-in defaults) is the caller's concern — see
 * {@link ToolActionDataSource}.
 */
public final class ToolActionBlueprint {
    /** Slot in PlayState's tool array (== the tool index): 1 chop, 2 mine, 3 water, 4 till, 5 dig. */
    public int toolIndex;
    /** Human label, for readability only. */
    public String name;
    /** Which player sheet to slice from: {@code "mining"} (player_mining.png) or {@code "chopping"}. */
    public String sheetName;
    /** Start row of each facing's frames, in [up, left, down, right] order. */
    public int[] directionRows;
    /** How many frames to slice per direction. */
    public int rawFrameCount;
    /** Optional reorder/cull of the sliced frames (e.g. [5,4,3,1,0] to reverse and drop a bad frame).
     *  When set, the played frame count is {@code frameOrder.length}; when null, frames play
     *  0..rawFrameCount-1. */
    public int[] frameOrder;
    /** Frame (in final, post-reorder indexing) at which the world effect fires; -1 for none. */
    public int effectFrame = -1;
    /** World effect to apply at {@link #effectFrame}: {@code "till"} or null/none. */
    public String effect;
    /** True for the 128px oversized swings (drawn at 2x, centered on the normal footprint). */
    public boolean oversized;

    public ToolActionBlueprint() {
    }

    public ToolActionBlueprint(int toolIndex, String name, String sheetName, int[] directionRows,
                               int rawFrameCount, int[] frameOrder, int effectFrame, String effect,
                               boolean oversized) {
        this.toolIndex = toolIndex;
        this.name = name;
        this.sheetName = sheetName;
        this.directionRows = directionRows;
        this.rawFrameCount = rawFrameCount;
        this.frameOrder = frameOrder;
        this.effectFrame = effectFrame;
        this.effect = effect;
        this.oversized = oversized;
    }
}
