package data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Pins the boot-time asset check: {@link AssetManifest#missing} reports exactly the {@code id→path} entries
 * whose resource is absent (and never the null-icon items, e.g. tools), with the existence test injected so
 * it runs headlessly without libGDX file access.
 */
class AssetManifestTest {

    @Test
    void reportsOnlyTheEntriesWhosePathIsAbsent() {
        Map<Integer, String> icons = new LinkedHashMap<>();
        icons.put(100, "food/100.png"); // present
        icons.put(104, "food/104.png"); // missing
        icons.put(5, null);             // a tool: no icon declared, must never be reported

        Set<String> present = Set.of("food/100.png");
        Map<Integer, String> missing = AssetManifest.missing(icons, present::contains);

        assertEquals(1, missing.size());
        assertEquals("food/104.png", missing.get(104));
        assertFalse(missing.containsKey(100), "a present icon isn't flagged");
        assertFalse(missing.containsKey(5), "a null icon path isn't flagged");
    }

    @Test
    void emptyWhenEverythingExists() {
        Map<Integer, String> icons = Map.of(1, "a.png", 2, "b.png");
        assertTrue(AssetManifest.missing(icons, p -> true).isEmpty());
    }
}
