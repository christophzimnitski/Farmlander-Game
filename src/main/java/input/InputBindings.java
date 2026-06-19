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
 * hovered tile) needs a cursor, not a key. The binding table ({@link #keysFor}) is pure data, so defaults
 * and rebinding are unit-testable without a GL context; the poll methods thinly wrap libGDX {@code Gdx.input}.
 * A controller layer can later add alternate bindings behind this same API.
 */
public final class InputBindings {
    private final Map<InputAction, int[]> keys = new EnumMap<>(InputAction.class);

    /** Construct with the default keyboard layout (arrows + WASD to move; 1/3 select; Tab inventory; Q/E/Esc). */
    public InputBindings() {
        bind(InputAction.MOVE_UP,        Input.Keys.UP,    Input.Keys.W);
        bind(InputAction.MOVE_DOWN,      Input.Keys.DOWN,  Input.Keys.S);
        bind(InputAction.MOVE_LEFT,      Input.Keys.LEFT,  Input.Keys.A);
        bind(InputAction.MOVE_RIGHT,     Input.Keys.RIGHT, Input.Keys.D);
        bind(InputAction.SELECT_PREV,    Input.Keys.NUM_1);
        bind(InputAction.SELECT_NEXT,    Input.Keys.NUM_3);
        bind(InputAction.OPEN_INVENTORY, Input.Keys.TAB);
        bind(InputAction.USE_TOOL,       Input.Keys.Q);
        bind(InputAction.HARVEST,        Input.Keys.E);
        bind(InputAction.QUIT,           Input.Keys.ESCAPE);
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
}
