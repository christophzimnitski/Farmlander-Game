package core;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import states.PlayState;

/**
 * The engine entry point libGDX drives. It owns the one shared SpriteBatch and
 * the {@link GameStateManager}, and forwards the lifecycle to the active state.
 * Replaces the old Level class (which was window + loop + renderer all at once).
 */
public class FarmlanderGame implements ApplicationListener {
    private SpriteBatch batch;
    private GameStateManager stateManager;

    @Override
    public void create() {
        batch = new SpriteBatch();
        stateManager = new GameStateManager();
        stateManager.push(new PlayState(stateManager));
    }

    @Override
    public void render() {
        // libGDX hands us a correctly-timed frame — no more while(true) loop.
        float delta = Gdx.graphics.getDeltaTime();
        stateManager.update(delta);

        // Grass green (Swing's Color(48,129,54)) — the base the detail tiles sit on.
        Gdx.gl.glClearColor(48f / 255f, 129f / 255f, 54f / 255f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        stateManager.render(batch);
    }

    @Override
    public void resize(int width, int height) {
        stateManager.resize(width, height);
    }

    @Override public void pause() {}
    @Override public void resume() {}

    @Override
    public void dispose() {
        stateManager.dispose();
        batch.dispose();
    }
}
