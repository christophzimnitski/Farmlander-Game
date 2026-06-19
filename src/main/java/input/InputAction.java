package input;

/**
 * The player's <b>semantic</b> actions, decoupled from the physical keys that trigger them. Gameplay code
 * asks "is MOVE_UP down?" instead of "is W or UP pressed?", so the update loop reads as intent and the
 * actual key choices all live in one place ({@link InputBindings}), reboundable without touching gameplay.
 *
 * <p>Pointer-targeted actions (planting at the hovered tile) are intentionally <em>not</em> here: they need
 * a cursor position, not a key, so they stay as explicit mouse queries on {@link InputBindings}.
 */
public enum InputAction {
    MOVE_UP,
    MOVE_DOWN,
    MOVE_LEFT,
    MOVE_RIGHT,
    SELECT_PREV,     // previous hotbar slot
    SELECT_NEXT,     // next hotbar slot
    OPEN_INVENTORY,  // toggle the inventory popup (the 3x10 bag over the hotbar)
    USE_TOOL,        // trigger the held tool's action (e.g. till)
    HARVEST,         // harvest the crop under the cursor
    QUIT             // exit the game
}
