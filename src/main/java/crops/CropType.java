package crops;

import com.badlogic.gdx.graphics.g2d.TextureRegion;

import java.util.Arrays;

/**
 * A kind of crop: its growth-stage sprites plus how long each stage takes to advance.
 *
 * <p>{@code stageDurations[i]} is the milliseconds needed to grow from stage {@code i} to
 * {@code i+1}, so the array length is {@code stageCount - 1}. The durations are mutable so growth
 * speed can be tuned per crop (or per stage) later; the registry currently seeds them all to one
 * shared default. The inventory item id of a crop's seed/produce is {@code 100 + index}, matching
 * the existing {@code food/<id>.png} assets.
 */
public final class CropType {
    public final int index;
    public final String name;
    public final TextureRegion[] stages;   // stage 0 (sprout) .. last (mature)
    public final long[] stageDurations;    // length = stages.length - 1
    public final int itemId;

    public CropType(int index, String name, TextureRegion[] stages, long defaultStageMillis) {
        this.index = index;
        this.name = name;
        this.stages = stages;
        this.itemId = 100 + index;
        this.stageDurations = new long[Math.max(0, stages.length - 1)];
        Arrays.fill(this.stageDurations, defaultStageMillis);
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
