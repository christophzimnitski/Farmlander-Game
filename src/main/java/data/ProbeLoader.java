package data;

import core.Log;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The project's "data file, or built-in fallback" loading pattern in one place. Each catalog (crops, items,
 * tool actions) is read from a JSON file behind a swappable data-source seam, but a missing or unparseable
 * file must fall back to the hardcoded built-in catalog <em>at load time</em> — never silently mid-game.
 *
 * <p>{@link #loadOrFallback} runs the loader against the primary (JSON) source; if constructing or parsing
 * it throws, it runs the same loader against the fallback source instead. Parsing happens once on success.
 */
public final class ProbeLoader {
    private ProbeLoader() {}

    /**
     * @param what     a short label for what's being loaded (e.g. "crops.json"), used only in the warning
     * @param primary  builds the primary source (e.g. the JSON-backed data source)
     * @param load     pulls the parsed blueprints from a source (forces the parse, so it trips the fallback)
     * @param fallback builds the built-in source used when the primary can't be constructed or parsed
     */
    public static <S, R> R loadOrFallback(String what, Supplier<? extends S> primary, Function<S, R> load,
                                          Supplier<? extends S> fallback) {
        try {
            return load.apply(primary.get());
        } catch (Exception e) {
            // Don't let a broken/missing data file fail silently — a fallback looks correct in-game.
            Log.warn("ProbeLoader", "could not load " + what + "; using built-in defaults", e);
            return load.apply(fallback.get());
        }
    }
}
