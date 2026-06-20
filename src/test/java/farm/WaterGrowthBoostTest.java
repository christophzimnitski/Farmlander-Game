package farm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import crops.CropField;
import crops.CropType;
import crops.PlantedCrop;
import org.junit.jupiter.api.Test;
import world.ChunkedTileLayer;
import world.DualGridSoil;

/**
 * Pins the watered-soil growth boost ({@link FarmInteraction#applyWaterGrowthBoost}) — specifically that
 * it stays correct at high frame rates. A naive per-frame {@code (long)} cast of {@code BONUS*delta*1000}
 * truncates to 0 once the frame is short enough (> ~330fps at a 0.30 bonus), silently killing the boost;
 * the carry-the-remainder implementation must not. Only soil + cropField are exercised here, so the rest of
 * {@link FarmInteraction}'s collaborators are passed as null (this path never touches them).
 */
class WaterGrowthBoostTest {

    /** 5 sprite frames → 4 grow stages of 1000 ms each. */
    private static CropType fiveStage() {
        return new CropType(0, "Test", new TextureRegion[5], 1000L);
    }

    private static FarmInteraction soilAndCrops(DualGridSoil soil, CropField field) {
        return new FarmInteraction(soil, field, null, null, null, null, null, null, null, null);
    }

    @Test
    void boostAccumulatesAcrossManyTinyFramesInsteadOfTruncatingToZero() {
        ChunkedTileLayer layer = new ChunkedTileLayer(8, 8, 96f);
        DualGridSoil soil = new DualGridSoil(layer);
        CropField field = new CropField(8, 8);
        FarmInteraction farm = soilAndCrops(soil, field);

        long now = System.currentTimeMillis();
        // A crop 900 ms into its first (1000 ms) stage: still stage 0, just shy of the stage-1 boundary.
        field.plant(2, 2, fiveStage(), now - 900L);
        soil.till(2, 2);
        soil.water(2, 2, now);
        PlantedCrop crop = field.at(2, 2);
        assertEquals(0, crop.stage(now), "precondition: still in the first stage");

        // Simulate ~600 frames at 500fps (delta = 1/500 s). Per frame the bonus is 0.30 * 0.002 * 1000 =
        // 0.6 ms — which a truncating cast would floor to 0 every single frame. Total bonus age here is
        // 0.30 * (600/500) s ≈ 360 ms, enough to push the crop past the 1000 ms stage-1 boundary.
        float delta = 1f / 500f;
        for (int i = 0; i < 600; i++) farm.applyWaterGrowthBoost(delta);

        assertTrue(crop.stage(now) >= 1,
                "the boost must accumulate across short frames, not truncate away (got stage " + crop.stage(now) + ")");
    }

    @Test
    void dryUnwateredSoilGetsNoBoost() {
        ChunkedTileLayer layer = new ChunkedTileLayer(8, 8, 96f);
        DualGridSoil soil = new DualGridSoil(layer);
        CropField field = new CropField(8, 8);
        FarmInteraction farm = soilAndCrops(soil, field);

        long now = System.currentTimeMillis();
        field.plant(2, 2, fiveStage(), now - 900L);
        soil.till(2, 2); // tilled but never watered

        for (int i = 0; i < 600; i++) farm.applyWaterGrowthBoost(1f / 500f);

        assertEquals(0, field.at(2, 2).stage(now), "a crop on dry soil must not age faster");
    }
}
