package crops;

import com.badlogic.gdx.graphics.g2d.TextureRegion;

import java.util.Arrays;

/**
 * A kind of crop: its growth-stage sprites plus how long each stage takes to advance.
 *
 * <p>{@code stageDurations[i]} is the milliseconds needed to grow from stage {@code i} to
 * {@code i+1}, so the array length is {@code stageCount - 1}. The durations are mutable so growth
 * speed can be tuned per crop (or per stage) later; the registry currently seeds them all to one
 * shared default.
 *
 * <p>A crop maps to <b>two</b> distinct inventory items, both keyed off its {@link #index}: the
 * <b>produce</b> ({@link #itemId} = {@code 100 + index}, matching the {@code food/<id>.png} assets — a
 * sellable good you can't replant) and the <b>seed</b> ({@link #seedItemId} = {@code 200 + index} — what
 * you sow). Keeping the two id formulas here is the single source of the crop↔item mapping (the registry
 * derives both from these fields rather than recomputing the offsets).
 */
public final class CropType {
    /** Produce item ids start here: {@code itemId = 100 + index} (the existing {@code food/<id>.png} space). */
    public static final int PRODUCE_ITEM_BASE = 100;
    /** Seed item ids start here: {@code seedItemId = 200 + index}, a band clear of the produce ids. */
    public static final int SEED_ITEM_BASE = 200;

    public final int index;
    public final String name;
    public final TextureRegion[] stages;   // stage 0 (sprout) .. last (mature)
    public final long[] stageDurations;    // length = stages.length - 1
    public final int itemId;               // produce item id = PRODUCE_ITEM_BASE + index
    public final int seedItemId;           // seed item id = SEED_ITEM_BASE + index
    // Harvest category. true (default) = a root crop that leaves a "lump" (the post-harvest hole frame),
    // which the shovel clears. false = a continuous/trellis crop that leaves no lump and is removed whole
    // by the axe. Mutable like stageDurations so the catalog can be re-categorized/tuned (set by the registry).
    public boolean leavesLump = true;

    /** Build a crop with every stage taking the same default time. */
    public CropType(int index, String name, TextureRegion[] stages, long defaultStageMillis) {
        this(index, name, stages, durationsFilled(stages.length, defaultStageMillis));
    }

    /**
     * Build a crop with explicit per-stage growth times (data-driven). {@code stageDurations} should
     * have length {@code stages.length - 1}; if it's null or a different length it's normalized so
     * any missing stage falls back to the first supplied value (or 0 when none are supplied).
     */
    public CropType(int index, String name, TextureRegion[] stages, long[] stageDurations) {
        this.index = index;
        this.name = name;
        this.stages = stages;
        this.itemId = PRODUCE_ITEM_BASE + index;
        this.seedItemId = SEED_ITEM_BASE + index;
        int need = Math.max(0, stages.length - 1);
        this.stageDurations = new long[need];
        long fallback = (stageDurations != null && stageDurations.length > 0) ? stageDurations[0] : 0L;
        for (int i = 0; i < need; i++) {
            this.stageDurations[i] = (stageDurations != null && i < stageDurations.length)
                    ? stageDurations[i] : fallback;
        }
    }

    private static long[] durationsFilled(int stageCount, long millis) {
        long[] d = new long[Math.max(0, stageCount - 1)];
        Arrays.fill(d, millis);
        return d;
    }

    public int stageCount() {
        return stages.length;
    }

    public TextureRegion stage(int s) {
        if (s < 0) s = 0;
        if (s >= stages.length) s = stages.length - 1;
        return stages[s];
    }

    /** Override how long a single stage takes (ms). Lets individual crops grow at their own pace. */
    public void setStageDuration(int stage, long millis) {
        if (stage >= 0 && stage < stageDurations.length) stageDurations[stage] = millis;
    }

    /** Total time from planting to fully mature. */
    public long timeToMature() {
        long t = 0;
        for (long d : stageDurations) t += d;
        return t;
    }
}
