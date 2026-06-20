package entities;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import physics.Body;

import java.util.Arrays;

import static entities.PlayerAnimations.playerAnimationArrays;

/**
 * The player avatar: world position, facing, the selected tool, and the hand-stepped walk animation. It is
 * also the collision {@link Body} (its feet hitbox is an AABB offset from the sprite's top-left), so the
 * {@code CollisionResolver} pushes the player directly. Drawing and input live elsewhere — this owns the
 * <em>state</em> and the small rules that mutate it (facing from motion, tool cycling, animation stepping).
 */
public final class PlayerCharacter implements Body {
    public static final float SIZE = 240f;            // 32 * 5 * zoom(1.5) — sprite draw size
    private static final int WALK_FRAMES = 9;
    private static final float ANIM_FRAME_TIME = 16f / 144f;

    // Feet hitbox: offset from the sprite top-left, plus its size. The player's footprint for collision.
    private static final float HITBOX_OFF_X = 75f, HITBOX_OFF_Y = 195f;
    private static final float HITBOX_W = 90f, HITBOX_H = 48f;

    private final TextureRegion[][] frames; // [36 animation rows][9 frames]

    private float x, y;
    private int facing = 2;       // 0 up, 1 left, 2 down, 3 right
    private int toolIndex = 0;    // index into playerAnimationArrays (current tool)
    private int[] walkActions;    // the 4 walk-animation rows for the current tool
    private int currentAnimation; // the WALK_* row currently shown
    private int frameIndex = 0;
    private float animTimer = 0;

    /**
     * Rebuild from saved primitives, recovering the tool and facing: the tool is the {@code playerAnimationArrays}
     * row matching the saved walk set, and the facing is the direction whose walk row equals the saved animation.
     */
    public PlayerCharacter(float x, float y, int animation, int[] walkActions, TextureRegion[][] frames) {
        this.x = x;
        this.y = y;
        this.frames = frames;
        this.walkActions = (walkActions != null && walkActions.length == 4)
                ? walkActions : new int[]{32, 33, 34, 35};
        this.currentAnimation = (animation >= 0 && animation < 36) ? animation : 34;
        for (int i = 0; i < playerAnimationArrays.length; i++)
            if (Arrays.equals(playerAnimationArrays[i], this.walkActions)) { toolIndex = i; break; }
        for (int i = 0; i < 4; i++)
            if (this.walkActions[i] == currentAnimation) { facing = i; break; }
    }

    // --- collision Body: the feet hitbox in world space ---
    @Override public float hitboxX() { return x + HITBOX_OFF_X; }
    @Override public float hitboxY() { return y + HITBOX_OFF_Y; }
    @Override public float hitboxW() { return HITBOX_W; }
    @Override public float hitboxH() { return HITBOX_H; }
    @Override public void move(float dx, float dy) { x += dx; y += dy; }

    // --- position / geometry ---
    public float x() { return x; }
    public float y() { return y; }
    public float centerX() { return x + SIZE / 2f; }   // for the camera to follow
    public float centerY() { return y + SIZE / 2f; }
    public float feetY() { return hitboxY(); }          // depth ground-line reference
    public float hitboxCenterX() { return hitboxX() + HITBOX_W / 2f; } // the cell the player stands on
    public float hitboxCenterY() { return hitboxY() + HITBOX_H / 2f; }

    // --- facing / tool ---
    public int facing() { return facing; }
    public int toolIndex() { return toolIndex; }
    public int[] walkActions() { return walkActions; }
    public int currentAnimation() { return currentAnimation; }

    /**
     * Set the active tool to a specific {@code playerAnimationArrays} row ({@code 0} = empty-handed walk).
     * Driven by the hotbar selection: the selected item's {@code toolIndex} becomes the held tool. Out-of-range
     * indices fall back to empty-handed. Refreshes the shown walk row so the idle frame matches immediately.
     */
    public void setTool(int index) {
        toolIndex = (index >= 0 && index < playerAnimationArrays.length) ? index : 0;
        walkActions = playerAnimationArrays[toolIndex];
        currentAnimation = walkActions[facing];
    }

    /**
     * Update facing from actual movement. Opposing keys (which cancel an axis) keep the current facing,
     * and horizontal wins over vertical on diagonals. Also refreshes the shown walk row.
     */
    public void setFacingFromMotion(int ix, int iy) {
        if (iy < 0) facing = 0; // up
        if (iy > 0) facing = 2; // down
        if (ix < 0) facing = 1; // left
        if (ix > 0) facing = 3; // right
        currentAnimation = walkActions[facing];
    }

    /** Advance the walk animation while moving; snap to the idle frame when stopped. */
    public void stepWalk(float delta, boolean moving) {
        if (moving) {
            if (frameIndex == 0) frameIndex = 1;
            animTimer += delta;
            if (animTimer >= ANIM_FRAME_TIME) {
                animTimer -= ANIM_FRAME_TIME;
                frameIndex++;
                if (frameIndex >= WALK_FRAMES) frameIndex = 1;
            }
        } else {
            frameIndex = 0;
            animTimer = 0;
        }
    }

    /** The current walk frame, to draw at (x, y, SIZE, SIZE). */
    public TextureRegion currentFrame() { return frames[currentAnimation][frameIndex]; }
}
