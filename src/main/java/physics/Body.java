package physics;

/**
 * Something the {@link CollisionResolver} can push out of solid geometry: it exposes its current hitbox
 * (world-space AABB) and can be shifted. Keeps the resolver independent of who owns the position — today
 * {@code PlayState} adapts the player; after the split {@code PlayerCharacter} implements this directly.
 */
public interface Body {
    float hitboxX();
    float hitboxY();
    float hitboxW();
    float hitboxH();

    /** Shift the body by (dx,dy) in world units. */
    void move(float dx, float dy);
}
