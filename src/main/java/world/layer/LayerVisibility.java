package world.layer;

import java.util.HashSet;
import java.util.Set;

/**
 * Show/hide flags for the game's built-in layers that aren't part of the {@link LayerStack} (the base grass
 * background, the farmable soil, the bushes on the collision grid, and the decoration tiles). The editor's
 * Layers menu toggles these and {@code WorldRenderer} reads them, so every layer — stack or built-in — can be
 * hidden individually while editing. Default visible.
 *
 * <p>Decoration is one shared grid that holds tiles from several palette sheets (rocks/ore, reeds, …). Each
 * sheet hides independently — the editor gives every decoration sheet its own eye — so we track which sheets
 * are currently hidden rather than a single decoration flag.
 */
public final class LayerVisibility {
    public boolean grass = true;
    public boolean soil = true;
    public boolean bushes = true;

    private final Set<Integer> hiddenDecorationSheets = new HashSet<>();

    /** Should decoration tiles from this {@code TilePalette} sheet be drawn? (Drives both rendering and the
     *  per-sheet eye in the Layers menu.) */
    public boolean isDecorationSheetVisible(int sheetIndex) {
        return !hiddenDecorationSheets.contains(sheetIndex);
    }

    /** Flip a single decoration sheet's visibility. */
    public void toggleDecorationSheet(int sheetIndex) {
        if (!hiddenDecorationSheets.remove(sheetIndex)) hiddenDecorationSheets.add(sheetIndex);
    }
}
