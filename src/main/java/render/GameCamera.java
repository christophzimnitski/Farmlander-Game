package render;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

/**
 * The pair of cameras the play screen draws through: a <b>world</b> camera that follows the player and a
 * fixed <b>HUD</b> camera in screen space. Both are Y-down (top-left origin) to match the project's Swing
 * heritage and the Y-flipped texture regions. This wraps the raw libGDX {@link OrthographicCamera} plumbing
 * so callers ask for what they want ("follow this point", "the world matrix", "unproject this click")
 * instead of poking camera internals.
 */
public final class GameCamera {
    private final OrthographicCamera world = new OrthographicCamera();
    private final OrthographicCamera hud = new OrthographicCamera();
    private final Vector3 scratch = new Vector3(); // reused by unproject — no per-call allocation

    public GameCamera(int width, int height) {
        resize(width, height);
    }

    /** Centre the world camera on a world point (typically the player's centre) and recompute its matrix. */
    public void follow(float worldX, float worldY) {
        world.position.set(worldX, worldY, 0);
        world.update();
    }

    /** Re-establish both Y-down viewports after a window resize. */
    public void resize(int width, int height) {
        world.setToOrtho(true, width, height);
        hud.setToOrtho(true, width, height);
    }

    /** Projection matrix for drawing the world (set on the batch before world draws). */
    public Matrix4 worldCombined() { return world.combined; }

    /** Projection matrix for drawing the screen-space HUD. */
    public Matrix4 hudCombined() { return hud.combined; }

    /**
     * Convert a screen-pixel position (e.g. the mouse) to a world position through the world camera.
     * Returns a shared scratch vector — read {@code .x}/{@code .y} immediately; don't hold the reference.
     */
    public Vector3 unproject(float screenX, float screenY) {
        scratch.set(screenX, screenY, 0);
        world.unproject(scratch);
        return scratch;
    }
}
