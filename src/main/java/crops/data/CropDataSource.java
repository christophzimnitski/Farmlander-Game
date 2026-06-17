package crops.data;

/**
 * Where crop blueprints come from. The single seam that isolates <em>how</em> crop data is loaded
 * (JSON today, possibly a hand-rolled format later) from everything that <em>uses</em> it.
 *
 * <p>{@link crops.CropRegistry} depends only on this interface and on {@link CropBlueprint}, so
 * switching formats is a one-class change: write a new implementation, swap it in at the call site.
 * No engine (libGDX) types appear here on purpose.
 */
public interface CropDataSource {
    /** @return every crop blueprint, in no particular order (the registry indexes by {@code index}). */
    CropBlueprint[] loadCropBlueprints();
}
