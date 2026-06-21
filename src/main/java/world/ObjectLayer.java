package world;

import java.util.ArrayList;
import java.util.List;

/**
 * The world's free-placed props ({@link PlacedObject}s), kept in placement order. A thin owner around a
 * list so callers don't pass a raw {@code List} around: the editor adds/erases, the renderer iterates for
 * its depth-sorted passes, and the map format reads/writes the whole set. Pure data — no libGDX — so the
 * add/erase/hit-test logic is headlessly testable (only the per-object {@code TextureRegion} is GL-bound,
 * and that lives on {@link PlacedObject}, set after load).
 */
public final class ObjectLayer {
    private final List<PlacedObject> objects = new ArrayList<>();

    public void add(PlacedObject object) { objects.add(object); }

    public boolean remove(PlacedObject object) { return objects.remove(object); }

    /** Read-only view for rendering/saving. */
    public List<PlacedObject> all() { return objects; }

    public int size() { return objects.size(); }

    /** The top-most (last-placed) object whose draw box contains the world point, or {@code null}. Last-first
     *  so the object drawn on top is the one picked — matches what the user sees. */
    public PlacedObject topAt(float worldX, float worldY) {
        for (int i = objects.size() - 1; i >= 0; i--) {
            if (objects.get(i).contains(worldX, worldY)) return objects.get(i);
        }
        return null;
    }
}
