package input;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.Input;
import org.junit.jupiter.api.Test;

/**
 * The binding table is pure data (no GL): defaults are present for every action, movement binds to both
 * arrows and WASD, rebinding replaces an action's keys, and {@code keysFor} hands back a defensive copy.
 * The poll methods ({@code isDown}/{@code isJustPressed}) wrap {@code Gdx.input} and aren't exercised here.
 */
class InputBindingsTest {

    @Test
    void everyActionHasADefaultBinding() {
        InputBindings bindings = new InputBindings();
        for (InputAction action : InputAction.values()) {
            assertEquals(true, bindings.keysFor(action).length > 0, "no default for " + action);
        }
    }

    @Test
    void movementDefaultsToArrowsAndWasd() {
        InputBindings bindings = new InputBindings();
        assertArrayEquals(new int[]{Input.Keys.UP, Input.Keys.W}, bindings.keysFor(InputAction.MOVE_UP));
        assertArrayEquals(new int[]{Input.Keys.LEFT, Input.Keys.A}, bindings.keysFor(InputAction.MOVE_LEFT));
    }

    @Test
    void inventoryTogglesWithTabByDefault() {
        InputBindings bindings = new InputBindings();
        assertArrayEquals(new int[]{Input.Keys.TAB}, bindings.keysFor(InputAction.OPEN_INVENTORY));
    }

    @Test
    void rebindReplacesKeys() {
        InputBindings bindings = new InputBindings();
        bindings.bind(InputAction.USE_TOOL, Input.Keys.SPACE, Input.Keys.ENTER);
        assertArrayEquals(new int[]{Input.Keys.SPACE, Input.Keys.ENTER}, bindings.keysFor(InputAction.USE_TOOL));
    }

    @Test
    void keysForReturnsADefensiveCopy() {
        InputBindings bindings = new InputBindings();
        int[] keys = bindings.keysFor(InputAction.HARVEST);
        keys[0] = -999; // mutating the returned array must not corrupt the binding
        assertArrayEquals(new int[]{Input.Keys.E}, bindings.keysFor(InputAction.HARVEST));
    }
}
