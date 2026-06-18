package physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Locks the pure collision math — the overlap test and the minimum-translation push-out — so the
 * player-vs-world feel can't silently drift during the rest of the {@code PlayState} split. No GL, no world.
 */
class CollisionMathTest {
    private static final float ERR = 0.45f;
    private static final float EPS = 1e-4f;

    @Test
    void separatedBoxesDoNotOverlap() {
        assertFalse(CollisionResolver.overlaps(0, 0, 10, 10, 20, 0, 10, 10));
    }

    @Test
    void touchingEdgesDoNotOverlap() {
        // Half-open AABB: sharing an edge (hx+hw == bx) is not an overlap.
        assertFalse(CollisionResolver.overlaps(0, 0, 10, 10, 10, 0, 10, 10));
    }

    @Test
    void interpenetratingBoxesOverlap() {
        assertTrue(CollisionResolver.overlaps(0, 0, 10, 10, 8, 0, 10, 10));
    }

    @Test
    void noOverlapResolvesToZero() {
        float[] d = CollisionResolver.resolveBox(0, 0, 10, 10, 50, 50, 10, 10, ERR);
        assertEquals(0f, d[0], EPS);
        assertEquals(0f, d[1], EPS);
    }

    @Test
    void boxOnTheRightPushesPlayerLeft() {
        // hitbox [0,10]x[0,10], box overlaps its right edge by 2 → smaller axis is X → push -X by 2+err.
        float[] d = CollisionResolver.resolveBox(0, 0, 10, 10, 8, 0, 10, 10, ERR);
        assertEquals(-(2f + ERR), d[0], EPS);
        assertEquals(0f, d[1], EPS);
    }

    @Test
    void boxOnTheLeftPushesPlayerRight() {
        float[] d = CollisionResolver.resolveBox(8, 0, 10, 10, 0, 0, 10, 10, ERR);
        assertEquals(2f + ERR, d[0], EPS);
        assertEquals(0f, d[1], EPS);
    }

    @Test
    void boxBelowPushesPlayerUp() {
        // Vertical overlap (2) smaller than horizontal (10) → push along Y, away from the box below.
        float[] d = CollisionResolver.resolveBox(0, 0, 10, 10, 0, 8, 10, 10, ERR);
        assertEquals(0f, d[0], EPS);
        assertEquals(-(2f + ERR), d[1], EPS);
    }

    @Test
    void boxAbovePushesPlayerDown() {
        float[] d = CollisionResolver.resolveBox(0, 8, 10, 10, 0, 0, 10, 10, ERR);
        assertEquals(0f, d[0], EPS);
        assertEquals(2f + ERR, d[1], EPS);
    }
}
