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
    private GameStateManager gsm;

    @Override
    public void create() {
        batch = new SpriteBatch();
        gsm = new GameStateManager();
        gsm.push(new PlayState(gsm));
    }

    @Override
    public void render() {
        // libGDX hands us a correctly-timed frame — no more while(true) loop.
        float delta = Gdx.graphics.getDeltaTime();
        gsm.update(delta);

        // Grass green (Swing's Color(48,129,54)) — the base the detail tiles sit on.
        Gdx.gl.glClearColor(48f / 255f, 129f / 255f, 54f / 255f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        gsm.render(batch);
    }

    @Override
    public void resize(int width, int height) {
        gsm.resize(width, height);
    }

    @Override public void pause() {}
    @Override public void resume() {}

    @Override
    public void dispose() {
        gsm.dispose();
        batch.dispose();
    }
}
