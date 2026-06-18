package tools;

/**
 * Runs the player's tool Q-actions: which one is playing, how far through its animation it is, and firing
 * its world effect once at the effect frame. It owns the transient animation state (active action, frame,
 * timer) that used to be loose fields on {@code PlayState}. It does <b>not</b> touch the world itself — at
 * the effect frame it calls a {@link ToolEffectHandler}, so the system stays independent of farming logic.
 */
public final class ToolActionSystem {

    /** Receives a tool action's world effect (e.g. {@code "till"}) at its effect frame, facing {@code dir}. */
    public interface ToolEffectHandler {
        void apply(String effect, int dir);
    }

    private final ToolAction[] actions; // indexed by tool index; null = that tool has no Q-action
    private final float frameTime;      // seconds per animation frame
    private final ToolEffectHandler effectHandler;

    private ToolAction active;          // non-null while an action plays
    private int dir = 2;                // facing captured when the action started
    private int frame = 0;
    private float timer = 0;
    private boolean effectDone = false; // the effect fires once per action

    public ToolActionSystem(ToolAction[] actions, float frameTime, ToolEffectHandler effectHandler) {
        this.actions = actions;
        this.frameTime = frameTime;
        this.effectHandler = effectHandler;
    }

    public boolean isActive() { return active != null; }

    public boolean hasActionFor(int toolIndex) {
        return toolIndex >= 0 && toolIndex < actions.length && actions[toolIndex] != null;
    }

    /** Start {@code toolIndex}'s action facing {@code dir}. No-op if it has no action or one already plays. */
    public void trigger(int toolIndex, int dir) {
        if (active != null || !hasActionFor(toolIndex)) return;
        active = actions[toolIndex];
        this.dir = dir;
        frame = 0;
        timer = 0;
        effectDone = false;
    }

    /** Advance the active action: fire its effect once at the effect frame, end after the last frame. */
    public void update(float delta) {
        if (active == null) return;
        timer += delta;
        if (timer >= frameTime) {
            timer -= frameTime;
            frame++;
            if (frame == active.effectFrame && !effectDone) {
                effectHandler.apply(active.effect, dir);
                effectDone = true;
            }
            if (frame >= active.frameCount) active = null;
        }
    }

    /** The action currently playing, or {@code null} when idle (draw it at the player's position). */
    public ToolAction current() { return active; }

    /** Facing the active action was started with (which frame row to draw). */
    public int currentDir() { return dir; }

    /** Clamped current frame index of the active action (0 when idle). */
    public int currentFrame() { return active == null ? 0 : Math.min(frame, active.frameCount - 1); }
}
