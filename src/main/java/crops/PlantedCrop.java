package crops;

/**
 * One crop growing on a tile. Growth is <em>derived</em> from the growth-clock origin on demand — there
 * are no per-plant timers or threads (the old Swing version leaked a Swing Timer + JVM shutdown hook per
 * plant). Persisting/restoring growth is just a matter of saving {@link #plantedAt}.
 */
public final class PlantedCrop {
    public final CropType type;
    // The growth clock's origin (epoch millis): initially the plant time, but pulled earlier by growth
    // boosts (e.g. watered soil) so the crop ages faster. stage() measures from here, so saving it persists
    // the boosted progress for free.
    public long plantedAt;
    public boolean harvested = false; // once harvested, the tile shows the "hole" frame

    public PlantedCrop(CropType type, long plantedAt) {
        this.type = type;
        this.plantedAt = plantedAt;
    }

    /** Speed growth by pulling the growth-clock origin {@code millis} earlier — e.g. watered soil ages the
     *  crop faster. {@link #stage} then advances as if that much extra time had passed. */
    public void boostGrowth(long millis) {
        plantedAt -= millis;
    }

    /** The last growth frame — the fully grown, ready-to-harvest plant. The final sprite frame is the
     *  post-harvest hole and is never reached by growth alone. */
    public int grownStage() {
        return Math.max(0, type.stageCount() - 2);
    }

    /** Sprite frame to draw at {@code now}: grows up to {@link #grownStage()}, or the hole once harvested. */
    public int stage(long now) {
        if (harvested) return type.stageCount() - 1; // hole frame
        long elapsed = now - plantedAt;
        long[] d = type.stageDurations;
        int s = 0;
        while (s < d.length && elapsed >= d[s]) {
            elapsed -= d[s];
            s++;
        }
        return Math.min(s, grownStage()); // stop at the grown plant; don't roll into the hole
    }

    public boolean isMature(long now) {
        return !harvested && stage(now) >= grownStage();
    }
}
