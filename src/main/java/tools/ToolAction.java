package tools;

import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * A tool's Q-action: the per-direction animation frames, the frame at which it acts on the world
 * ({@code effectFrame}, -1 = none) and what that effect is ({@code effect}, e.g. {@code "till"}), plus how
 * big and where to draw it (oversized 128px swings draw at 2× centred on the normal footprint).
 *
 * <p>An immutable value object built once from a {@code ToolActionBlueprint}; the live animation state
 * (which frame is showing) lives in {@link ToolActionSystem}, not here.
 */
public final class ToolAction {
    public final TextureRegion[][] frames; // [4 dirs: up,left,down,right][frameCount]
    public final int frameCount;
    public final int effectFrame;
    public final String effect;            // world effect to fire at effectFrame ("till", or null/none)
    public final float drawSize, offX, offY;

    public ToolAction(TextureRegion[][] frames, int frameCount, int effectFrame, String effect,
                      float drawSize, float offX, float offY) {
        this.frames = frames;
        this.frameCount = frameCount;
        this.effectFrame = effectFrame;
        this.effect = effect;
        this.drawSize = drawSize;
        this.offX = offX;
        this.offY = offY;
    }
}
