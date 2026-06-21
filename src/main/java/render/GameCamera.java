package render;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
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
    private final Rectangle viewBounds = new Rectangle(); // reused by worldViewBounds — no per-call allocation

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

    /** Set the world camera's zoom (1 = 1:1 pixels; &gt;1 zooms out to see more world). Used by the editor's
     *  free camera; the play camera leaves it at 1. {@link #worldViewBounds} and {@link #unproject} already
     *  account for it. Re-{@link #follow} or call this to apply. */
    public void setZoom(float zoom) {
        world.zoom = zoom;
        world.update();
    }

    public float zoom() { return world.zoom; }

    /** Projection matrix for drawing the world (set on the batch before world draws). */
    public Matrix4 worldCombined() { return world.combined; }

    /** Projection matrix for drawing the screen-space HUD. */
    public Matrix4 hudCombined() { return hud.combined; }

    /**
     * The world-space rectangle the world camera currently sees (centre = camera position, extent =
     * viewport × zoom). Used to frustum-cull world draws and collision scans to what's on screen. Returns
     * a shared scratch rectangle — read it immediately, don't hold the reference. Y-down like everything
     * else, so {@code y} is the top edge; for culling only the min/max span matters.
     */
    public Rectangle worldViewBounds() {
        float halfW = world.viewportWidth * 0.5f * world.zoom;
        float halfH = world.viewportHeight * 0.5f * world.zoom;
        viewBounds.set(world.position.x - halfW, world.position.y - halfH, halfW * 2f, halfH * 2f);
        return viewBounds;
    }

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
