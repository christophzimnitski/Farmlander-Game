package crops.data;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;

/**
 * Loads crop blueprints from a JSON file using libGDX's {@link Json}.
 *
 * <p><strong>This is the ONLY class allowed to touch the JSON library.</strong> It is a deliberate
 * libGDX shortcut (per the project's "flag every shortcut" rule): {@code Json.fromJson} does the
 * parsing + reflective field mapping for us instead of hand-writing a reader. Because all of that is
 * confined here behind {@link CropDataSource}, replacing it with a hand-rolled format later means
 * writing one sibling class and swapping the call site — nothing else changes.
 *
 * <p>The file is a JSON array of objects whose keys match {@link CropBlueprint}'s fields, e.g.:
 * <pre>[{ "index": 0, "name": "Potato", "spriteColumn": 0, "spriteTopRow": 0 }]</pre>
 */
public final class JsonCropDataSource implements CropDataSource {
    private final FileHandle file;

    public JsonCropDataSource(FileHandle file) {
        this.file = file;
    }

    @Override
    public CropBlueprint[] loadCropBlueprints() {
        return new Json().fromJson(CropBlueprint[].class, file);
    }
}
