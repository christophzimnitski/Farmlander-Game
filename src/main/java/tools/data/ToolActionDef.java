package tools.data;

/**
 * A dumb, serialization-friendly description of one tool's Q-action animation — everything needed to
 * slice it out of a spritesheet and play it, with no engine types. {@code PlayState} turns these into
 * live {@code ToolAction}s with the same slicers it already uses.
 *
 * <p>Fields are public and there's a no-arg constructor so libGDX {@code Json} can map them by name.
 * Where the defs come from (JSON, built-in defaults) is the caller's concern — see
 * {@link ToolActionDataSource}.
 */
public final class ToolActionDef {
    /** Slot in PlayState's tool array (== the tool index): 1 chop, 2 mine, 3 water, 4 till, 5 dig. */
    public int toolIndex;
    /** Human label, for readability only. */
    public String name;
    /** Which player sheet to slice from: {@code "mining"} (player_mining.png) or {@code "chopping"}. */
    public String sheet;
    /** Start row of each facing's frames, in [up, left, down, right] order. */
    public int[] dirRows;
    /** How many frames to slice per direction. */
    public int rawFrames;
    /** Optional reorder/cull of the sliced frames (e.g. [5,4,3,1,0] to reverse and drop a bad frame).
     *  When set, the played frame count is {@code order.length}; when null, frames play 0..rawFrames-1. */
    public int[] order;
    /** Frame (in final, post-reorder indexing) at which the world effect fires; -1 for none. */
    public int effectFrame = -1;
    /** World effect to apply at {@link #effectFrame}: {@code "till"} or null/none. */
    public String effect;
    /** True for the 128px oversized swings (drawn at 2x, centered on the normal footprint). */
    public boolean oversized;

    public ToolActionDef() {
    }

    public ToolActionDef(int toolIndex, String name, String sheet, int[] dirRows, int rawFrames,
                         int[] order, int effectFrame, String effect, boolean oversized) {
        this.toolIndex = toolIndex;
        this.name = name;
        this.sheet = sheet;
        this.dirRows = dirRows;
        this.rawFrames = rawFrames;
        this.order = order;
        this.effectFrame = effectFrame;
        this.effect = effect;
        this.oversized = oversized;
    }
}
