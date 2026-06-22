package core;

/**
 * Dev-only runtime debug toggles, shared across game states. These are <b>developer tools</b> (off by default,
 * flipped at runtime with a key) — deliberately separate from {@link Config}, which holds genuine game-design
 * dials. Kept undocumented in the player-facing controls.
 */
public final class Debug {
    private Debug() {}

    /** F2: draw collision hitboxes over the world — the player's feet hitbox in red, every solid box (bushes,
     *  solid editor layers like fences, and crop footprints) in blue. Honoured by both play and editor. */
    public static boolean showHitboxes = false;
}
