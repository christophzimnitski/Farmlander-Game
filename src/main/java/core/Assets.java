package core;

import java.util.LinkedHashMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;

/**
 * A tiny hand-rolled lazy texture cache — the single owner of every classpath-loaded {@link Texture}.
 *
 * <p>{@link #get} loads a texture the first time a path is asked for (Nearest filtering, to match the
 * rest of the pixel-art renderer), caches it by path, and hands back the same instance on every later
 * call. {@link #dispose} frees them all. This replaces the old scatter of individual {@code Texture}
 * fields + two separate dispose loops in {@code PlayState} with one place that owns load and free.
 *
 * <p>Deliberately NOT libGDX's {@code AssetManager}: no async queue, no reference counting, no
 * loaders — none of which this game needs yet. Because callers only touch {@link #get}/{@link #dispose},
 * a real {@code AssetManager} (or streaming for P5's chunked world) can later hide behind this same
 * seam without changing call sites.
 */
public final class Assets {
    private final Map<String, Texture> cache = new LinkedHashMap<>();

    /** The texture at {@code path} (under resources/, via classpath), loaded once and cached. */
    public Texture get(String path) {
        Texture t = cache.get(path);
        if (t == null) {
            t = new Texture(Gdx.files.classpath(path));
            t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            cache.put(path, t);
        }
        return t;
    }

    /** Free every loaded texture. Call once on shutdown. */
    public void dispose() {
        for (Texture t : cache.values()) t.dispose();
        cache.clear();
    }
}
