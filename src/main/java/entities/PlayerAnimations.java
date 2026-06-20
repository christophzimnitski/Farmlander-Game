package entities;

/**
 * The row map of the player sprite sheet (a {@code 36}-row × {@code 9}-frame atlas) plus the per-tool walk
 * sets. Each constant names one animation row; {@link #playerAnimationArrays} groups the four facing rows of
 * each tool's <em>walk</em> animation, indexed by tool ({@code 0} = empty-handed). {@link PlayerCharacter}
 * resolves its held tool and facing against this table, and {@code PlayState} sizes the tool-action array by
 * it. Pure constants — no libGDX — so it's safe to read from anywhere, including headless tests.
 *
 * <p>(Formerly {@code utilz.Constants.PlayerActionConstants}; moved here, next to {@link PlayerCharacter},
 * when the {@code utilz} package was retired.)
 */
public final class PlayerAnimations {
    private PlayerAnimations() {
    }

    public static final int TILLING_UP = 0;
    public static final int TILLING_LEFT = 1;
    public static final int TILLING_DOWN = 2;
    public static final int TILLING_RIGHT = 3;
    public static final int DIGGING_UP = 4;
    public static final int DIGGING_LEFT = 5;
    public static final int DIGGING_DOWN = 6;
    public static final int DIGGING_RIGHT = 7;
    public static final int WALK_MINING_UP = 8;
    public static final int WALK_MINING_LEFT = 9;
    public static final int WALK_MINING_DOWN = 10;
    public static final int WALK_MINING_RIGHT = 11;
    public static final int WALK_TILLING_UP = 12;
    public static final int WALK_TILLING_LEFT = 13;
    public static final int WALK_TILLING_DOWN = 14;
    public static final int WALK_TILLING_RIGHT = 15;
    public static final int WALK_DIGGING_UP = 16;
    public static final int WALK_DIGGING_LEFT = 17;
    public static final int WALK_DIGGING_DOWN = 18;
    public static final int WALK_DIGGING_RIGHT = 19;
    public static final int WATERING_UP = 20;
    public static final int WATERING_LEFT = 21;
    public static final int WATERING_DOWN = 22;
    public static final int WATERING_RIGHT = 23;
    public static final int WALK_WATERING_UP = 24;
    public static final int WALK_WATERING_LEFT = 25;
    public static final int WALK_WATERING_DOWN = 26;
    public static final int WALK_WATERING_RIGHT = 27;
    public static final int WALK_CHOPPING_UP = 28;
    public static final int WALK_CHOPPING_LEFT = 29;
    public static final int WALK_CHOPPING_DOWN = 30;
    public static final int WALK_CHOPPING_RIGHT = 31;
    public static final int WALK_UP = 32;
    public static final int WALK_LEFT = 33;
    public static final int WALK_DOWN = 34;
    public static final int WALK_RIGHT = 35;
    public static final int MINE_UP = 36;
    public static final int MINE_LEFT = 37;
    public static final int MINE_DOWN = 38;
    public static final int MINE_RIGHT = 39;

    /** The four facing walk rows for each tool, indexed by tool: 0 empty-handed, then chop/mine/water/till/dig. */
    public static final int[][] playerAnimationArrays = {
            {WALK_UP, WALK_LEFT, WALK_DOWN, WALK_RIGHT},
            {WALK_CHOPPING_UP, WALK_CHOPPING_LEFT, WALK_CHOPPING_DOWN, WALK_CHOPPING_RIGHT},
            {WALK_MINING_UP, WALK_MINING_LEFT, WALK_MINING_DOWN, WALK_MINING_RIGHT},
            {WALK_WATERING_UP, WALK_WATERING_LEFT, WALK_WATERING_DOWN, WALK_WATERING_RIGHT},
            {WALK_TILLING_UP, WALK_TILLING_LEFT, WALK_TILLING_DOWN, WALK_TILLING_RIGHT},
            {WALK_DIGGING_UP, WALK_DIGGING_LEFT, WALK_DIGGING_DOWN, WALK_DIGGING_RIGHT}};
}
