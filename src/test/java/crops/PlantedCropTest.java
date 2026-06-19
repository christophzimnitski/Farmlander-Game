package crops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import org.junit.jupiter.api.Test;

/**
 * Growth is derived from the crop's growth-clock origin, so it's testable with no GL — the stage sprites
 * are never drawn here, only counted. Pins the stage progression and the watered-soil growth boost
 * ({@link PlantedCrop#boostGrowth}), which speeds a crop by pulling that origin earlier.
 */
class PlantedCropTest {

    /** 5 sprite frames → 4 grow stages of 1000 ms each; the grown plant is stage 3, stage 4 is the hole. */
    private static CropType fiveStage() {
        return new CropType(0, "Test", new TextureRegion[5], 1000L);
    }

    @Test
    void stageAdvancesWithElapsedTimeAndCapsAtTheGrownPlant() {
        PlantedCrop crop = new PlantedCrop(fiveStage(), 0L);
        assertEquals(0, crop.stage(500L), "before the first stage completes");
        assertEquals(2, crop.stage(2500L), "two full stages elapsed");
        assertEquals(3, crop.stage(9000L), "growth caps at the grown plant (stage 3), never the hole");
        assertTrue(crop.isMature(9000L));
    }

    @Test
    void boostGrowthPullsTheClockEarlierSoTheCropAgesFaster() {
        PlantedCrop crop = new PlantedCrop(fiveStage(), 0L);
        assertEquals(2, crop.stage(2500L));
        assertFalse(crop.isMature(2500L));

        crop.boostGrowth(1000L); // a watered tick: 1000 ms of bonus age
        assertEquals(3, crop.stage(2500L), "the bonus age advances it a stage at the same wall-clock time");
        assertTrue(crop.isMature(2500L), "the boost can bring maturity forward");
    }
}
