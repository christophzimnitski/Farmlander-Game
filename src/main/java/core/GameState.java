package core;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/**
 * Base class for every screen/scene (menu, play, pause, ...).
 * Hand-rolled on purpose instead of using libGDX's Game/Screen, so we own the
 * lifecycle and the state stack (see {@link GameStateManager}).
 */
public abstract class GameState {
    protected final GameStateManager stateManager;

    protected GameState(GameStateManager stateManager) {
        this.stateManager = stateManager;
    }

    /** Advance simulation by {@code delta} seconds. Input is polled here. */
    public abstract void update(float delta);

    /** Draw this state using the shared batch. */
    public abstract void render(SpriteBatch batch);

    /** Called when the window size changes. */
    public void resize(int width, int height) {}

    /** Release any GPU/native resources this state owns. */
    public abstract void dispose();
}
