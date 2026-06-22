package render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import entities.PlayerCharacter;
import physics.CollisionResolver;

/**
 * The dev-only (F2) hitbox overlay: draws every collision box over the world so you can see exactly what
 * blocks movement. Solid boxes — collision-layer bushes, solid editor layers (fences), and crop footprints —
 * are outlined in <b>blue</b>; the player's feet hitbox in <b>red</b>. Boxes come straight from
 * {@link CollisionResolver#forEachSolidBox}, so they match the real collide geometry exactly (including the
 * {@code tile/8} collidable raise). Drawn with a 1×1 white pixel it owns (no ShapeRenderer); the owning state
 * calls {@link #dispose}. Toggled via {@link core.Debug#showHitboxes}; honoured by play and editor.
 */
public final class DebugOverlay {
    private static final Color SOLID = new Color(0.25f, 0.55f, 1f, 0.9f);  // blue: anything that blocks
    private static final Color PLAYER = new Color(1f, 0.2f, 0.2f, 0.95f);  // red: the player's feet hitbox
    private final Texture white;
    private final TextureRegion whiteRegion;

    public DebugOverlay() {
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        white = new Texture(pm);
        pm.dispose();
        whiteRegion = new TextureRegion(white);
    }

    /**
     * Draw the overlay through the world camera. {@code player} may be null (the editor has no player), in
     * which case only the blue solid boxes are drawn. {@code thickness} is in world units — scale it by the
     * camera zoom so the outline stays visible when zoomed out.
     */
    public void render(SpriteBatch batch, GameCamera camera, CollisionResolver resolver,
                       PlayerCharacter player, float thickness) {
        batch.setProjectionMatrix(camera.worldCombined());
        batch.begin();
        batch.setColor(SOLID);
        resolver.forEachSolidBox(camera.worldViewBounds(), (x, y, w, h) -> outline(batch, x, y, w, h, thickness));
        if (player != null) {
            batch.setColor(PLAYER);
            outline(batch, player.hitboxX(), player.hitboxY(), player.hitboxW(), player.hitboxH(), thickness);
        }
        batch.setColor(Color.WHITE);
        batch.end();
    }

    private void outline(SpriteBatch batch, float x, float y, float w, float h, float t) {
        batch.draw(whiteRegion, x, y, w, t);          // top
        batch.draw(whiteRegion, x, y + h - t, w, t);  // bottom
        batch.draw(whiteRegion, x, y, t, h);          // left
        batch.draw(whiteRegion, x + w - t, y, t, h);  // right
    }

    public void dispose() { white.dispose(); }
}
