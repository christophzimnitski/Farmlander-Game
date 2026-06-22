package input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

import java.util.EnumMap;
import java.util.Map;

/**
 * The action→key binding layer. Gameplay polls semantic {@link InputAction}s and this resolves them to the
 * physical keys currently bound, so every key choice lives here (not scattered through the update loop) and
 * can be rebound at runtime via {@link #bind}. Continuous actions (movement) use {@link #isDown}; discrete
 * ones (select, use, harvest, quit) use {@link #isJustPressed}.
 *
 * <p>Pointer-targeted actions stay explicit: {@link #isPrimaryPointerJustPressed} (left-click to plant the
 * hovered tile) needs a cursor, not a key. Hotbar selection ({@link #hotbarSlotJustPressed}) is also explicit
 * — a fixed 1:1 number row, not a rebindable action. The binding table ({@link #keysFor}) is pure data, so
 * defaults and rebinding are unit-testable without a GL context; the poll methods thinly wrap libGDX
 * {@code Gdx.input}. A controller layer can later add alternate bindings behind this same API.
 */
public final class InputBindings {
    private final Map<InputAction, int[]> keys = new EnumMap<>(InputAction.class);

    /** Construct with the default keyboard layout (arrows + WASD to move; number row selects the hotbar;
     *  Tab inventory; Q/E/Esc). Hotbar selection isn't in this table — see {@link #hotbarSlotJustPressed}. */
    public InputBindings() {
        bind(InputAction.MOVE_UP,        Input.Keys.UP,    Input.Keys.W);
        bind(InputAction.MOVE_DOWN,      Input.Keys.DOWN,  Input.Keys.S);
        bind(InputAction.MOVE_LEFT,      Input.Keys.LEFT,  Input.Keys.A);
        bind(InputAction.MOVE_RIGHT,     Input.Keys.RIGHT, Input.Keys.D);
        bind(InputAction.OPEN_INVENTORY, Input.Keys.TAB);
        bind(InputAction.USE_TOOL,       Input.Keys.Q);
        bind(InputAction.HARVEST,        Input.Keys.E);
        bind(InputAction.QUIT,           Input.Keys.ESCAPE);
        bind(InputAction.TOGGLE_EDITOR,  Input.Keys.F1);
        bind(InputAction.TOGGLE_HITBOXES, Input.Keys.F2);
    }

    /** Rebind an action to one or more physical keys ({@code Input.Keys} codes), replacing its current keys. */
    public void bind(InputAction action, int... keyCodes) {
        keys.put(action, keyCodes.clone());
    }

    /** The keys currently bound to an action (defensive copy). Pure data — safe to read in headless tests. */
    public int[] keysFor(InputAction action) {
        return keys.get(action).clone();
    }

    /** True while any key bound to {@code action} is held — for continuous actions like movement. */
    public boolean isDown(InputAction action) {
        for (int code : keys.get(action)) if (Gdx.input.isKeyPressed(code)) return true;
        return false;
    }

    /** True on the frame any key bound to {@code action} goes down — for discrete (one-shot) actions. */
    public boolean isJustPressed(InputAction action) {
        for (int code : keys.get(action)) if (Gdx.input.isKeyJustPressed(code)) return true;
        return false;
    }

    /** Primary pointer action (left-click to plant the hovered tile): mouse-targeted, so not a key binding. */
    public boolean isPrimaryPointerJustPressed() {
        return Gdx.input.isButtonJustPressed(Input.Buttons.LEFT);
    }

    /** Is the primary (left) pointer button currently held? Drives inventory drag-and-drop (press/release
     *  edges are derived by the caller from this across frames). */
    public boolean isPrimaryPointerDown() {
        return Gdx.input.isButtonPressed(Input.Buttons.LEFT);
    }

    /** Is either Shift held? Modifies inventory moves: whole-stack vs single-item, and stack quick-transfer. */
    public boolean isShiftDown() {
        return Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
    }

    /** Hotbar number-row selection: the slot (0..9) whose number key was just pressed this frame, or -1 for
     *  none. Standard mapping — 1..9 select slots 0..8 and 0 selects the tenth slot (index 9). The ten number
     *  keys are consecutive in {@code Input.Keys} (NUM_0..NUM_9), so the row maps by offset. Kept explicit
     *  (like the pointer queries) rather than ten rebindable actions: it's a fixed 1:1 row. */
    public int hotbarSlotJustPressed() {
        for (int i = 0; i < 10; i++) {
            int key = (i == 9) ? Input.Keys.NUM_0 : Input.Keys.NUM_1 + i;
            if (Gdx.input.isKeyJustPressed(key)) return i;
        }
        return -1;
    }

    /** Pointer X in screen pixels (left origin) — matches the Y-down HUD camera, so it indexes HUD slots directly. */
    public int pointerX() { return Gdx.input.getX(); }

    /** Pointer Y in screen pixels (top origin) — matches the Y-down HUD camera. */
    public int pointerY() { return Gdx.input.getY(); }
}
