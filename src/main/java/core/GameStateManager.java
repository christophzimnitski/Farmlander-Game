package core;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Hand-rolled stack of {@link GameState}s. The top of the stack is the active
 * state; pushing (e.g. a pause menu over play) keeps the one below alive.
 * This is the core of P1 and deliberately not libGDX's Game/Screen.
 */
public class GameStateManager {
    private final Deque<GameState> stack = new ArrayDeque<>();

    /** Put a new state on top (the one below stays in memory). */
    public void push(GameState state) {
        stack.push(state);
    }

    /** Remove and dispose the top state. */
    public void pop() {
        if (!stack.isEmpty()) {
            stack.pop().dispose();
        }
    }

    /** Replace the top state with a new one. */
    public void set(GameState state) {
        pop();
        push(state);
    }

    public GameState current() {
        return stack.peek();
    }

    public void update(float delta) {
        if (!stack.isEmpty()) stack.peek().update(delta);
    }

    public void render(SpriteBatch batch) {
        if (!stack.isEmpty()) stack.peek().render(batch);
    }

    public void resize(int width, int height) {
        for (GameState state : stack) state.resize(width, height);
    }

    public void dispose() {
        while (!stack.isEmpty()) pop();
    }
}
