package items.data;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;

/**
 * Loads inventory item blueprints from a JSON file via libGDX {@link Json} (a shortcut — see the
 * project's "flag every libGDX shortcut" rule). This is the ONLY class that imports {@code Json};
 * confining it here keeps a hand-rolled parser a one-class swap behind {@link ItemDataSource}.
 */
public final class JsonItemDataSource implements ItemDataSource {
    private final FileHandle file;

    public JsonItemDataSource(FileHandle file) {
        this.file = file;
    }

    @Override
    public ItemBlueprint[] loadItemBlueprints() {
        return new Json().fromJson(ItemBlueprint[].class, file);
    }
}
