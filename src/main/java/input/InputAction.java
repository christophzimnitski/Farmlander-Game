package input;

/**
 * The player's <b>semantic</b> actions, decoupled from the physical keys that trigger them. Gameplay code
 * asks "is MOVE_UP down?" instead of "is W or UP pressed?", so the update loop reads as intent and the
 * actual key choices all live in one place ({@link InputBindings}), reboundable without touching gameplay.
 *
 * <p>Pointer-targeted actions (planting at the hovered tile) are intentionally <em>not</em> here: they need
 * a cursor position, not a key, so they stay as explicit mouse queries on {@link InputBindings}. Hotbar
 * selection is likewise explicit ({@link InputBindings#hotbarSlotJustPressed}) — a fixed 1:1 number row
 * (1..9, 0), not a rebindable action.
 */
public enum InputAction {
    MOVE_UP,
    MOVE_DOWN,
    MOVE_LEFT,
    MOVE_RIGHT,
    OPEN_INVENTORY,  // toggle the inventory popup (the 3x10 bag over the hotbar)
    USE_TOOL,        // trigger the held tool's action (e.g. till)
    HARVEST,         // harvest the crop under the cursor
    QUIT,            // exit the game
    TOGGLE_EDITOR,   // open/close the map editor (gated by Config.EDITOR_ENABLED)
    TOGGLE_HITBOXES  // dev-only: draw collision hitboxes over the world (see core.Debug)
}
