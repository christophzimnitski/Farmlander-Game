package world.layer;

/**
 * Show/hide flags for the game's built-in layers that aren't part of the {@link LayerStack} (the farmable
 * soil, the bushes on the collision grid, and the generic decoration layer). The editor's Layers menu
 * toggles these and {@code WorldRenderer} reads them, so every layer — stack or built-in — can be hidden
 * individually while editing. Default visible.
 */
public final class LayerVisibility {
    public boolean soil = true;
    public boolean bushes = true;
    public boolean decoration = true;
}
