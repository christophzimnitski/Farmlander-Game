package tools.data;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;

/**
 * Loads tool Q-action blueprints from a JSON file via libGDX {@link Json} (a shortcut — see the
 * project's "flag every libGDX shortcut" rule). This is the ONLY class that imports {@code Json};
 * confining it here keeps a hand-rolled parser a one-class swap behind {@link ToolActionDataSource}.
 */
public final class JsonToolActionDataSource implements ToolActionDataSource {
    private final FileHandle file;

    public JsonToolActionDataSource(FileHandle file) {
        this.file = file;
    }

    @Override
    public ToolActionBlueprint[] loadToolActionBlueprints() {
        return new Json().fromJson(ToolActionBlueprint[].class, file);
    }
}
