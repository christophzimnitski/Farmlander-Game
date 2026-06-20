package data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Boot-time resource validation. Given a map of {@code id -> classpath resource path}, returns the entries
 * whose resource is absent — so a startup check can WARN up front instead of letting a missing asset fail
 * silently at first use (e.g. a crop whose {@code food/<id>.png} icon doesn't exist renders as a blank slot
 * with no error). The existence test is injected, so this is pure and headlessly testable; the caller wires
 * in {@code Gdx.files.classpath(path)::exists}.
 */
public final class AssetManifest {
    private AssetManifest() {
    }

    /** The {@code id -> path} entries (insertion order preserved) whose path is non-null but doesn't exist. */
    public static Map<Integer, String> missing(Map<Integer, String> idToPath, Predicate<String> exists) {
        Map<Integer, String> gone = new LinkedHashMap<>();
        idToPath.forEach((id, path) -> {
            if (path != null && !exists.test(path)) gone.put(id, path);
        });
        return gone;
    }
}
