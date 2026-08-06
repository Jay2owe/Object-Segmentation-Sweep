/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import org.junit.Test;
import segsweep.sweep.CropSpec;
import segsweep.sweep.ParameterId;

import java.awt.Rectangle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The store is exercised through {@code serialise}/{@code deserialise} rather
 * than through {@code save}/{@code restore}, so the rules are tested without
 * writing to the user's real IJ_Prefs.txt.
 */
public class SweepStateStoreTest {

    private static ImagePlus image(int width, int height) {
        return new ImagePlus("state", new ByteProcessor(width, height));
    }

    private static SegSweepMacroOptions narrowedRange() {
        SegSweepMacroOptions options = SegSweepMacroOptions.defaults();
        options.setPrimaryAxis(SegSweepMacroOptions.AxisSpec.range(
                ParameterId.THRESHOLD, 22.0d, 31.0d, 1.0d));
        return options;
    }

    @Test
    public void rangesSurviveARoundTrip() {
        String stored = SweepStateStore.serialise(narrowedRange());
        assertNotNull(stored);

        SegSweepMacroOptions restored = SweepStateStore.deserialise(stored, image(64, 64));
        assertEquals(ParameterId.THRESHOLD, restored.primaryAxis().id());
        assertEquals(22.0d, restored.primaryAxis().from(), 1e-9d);
        assertEquals(31.0d, restored.primaryAxis().to(), 1e-9d);
        assertEquals(1.0d, restored.primaryAxis().step(), 1e-9d);
    }

    @Test
    public void secondAxisAndOutputTogglesSurvive() {
        SegSweepMacroOptions options = narrowedRange();
        options.setSecondaryAxis(SegSweepMacroOptions.AxisSpec.range(
                ParameterId.MIN_SIZE, 10.0d, 40.0d, 10.0d));
        options.setShowTables(false);

        SegSweepMacroOptions restored = SweepStateStore.deserialise(
                SweepStateStore.serialise(options), image(64, 64));

        assertNotNull(restored.secondaryAxis());
        assertEquals(ParameterId.MIN_SIZE, restored.secondaryAxis().id());
        assertEquals(40.0d, restored.secondaryAxis().to(), 1e-9d);
        assertFalse(restored.showTables());
    }

    @Test
    public void theImageIsNeverCarriedBetweenSessions() {
        SegSweepMacroOptions options = narrowedRange();
        options.setImage("a-stack-from-last-week.tif");

        String stored = SweepStateStore.serialise(options);
        assertFalse("a stored session must not name an image: " + stored,
                stored.contains("a-stack-from-last-week"));

        // The options normalise a blank title to null, so accept either form —
        // what matters is that no image name survives.
        String restoredImage = SweepStateStore.deserialise(stored, image(64, 64)).image();
        assertTrue("expected no remembered image but got: " + restoredImage,
                restoredImage == null || restoredImage.trim().isEmpty());
    }

    @Test
    public void aCropThatDoesNotFitTheNewImageFallsBackToTheWholeImage() {
        SegSweepMacroOptions options = narrowedRange();
        options.setCrop(CropSpec.custom(new Rectangle(0, 0, 900, 900)));

        SegSweepMacroOptions restored = SweepStateStore.deserialise(
                SweepStateStore.serialise(options), image(512, 512));

        assertEquals(CropSpec.Mode.FULL, restored.crop().mode());
    }

    @Test
    public void aCropThatStillFitsIsKept() {
        SegSweepMacroOptions options = narrowedRange();
        options.setCrop(CropSpec.custom(new Rectangle(10, 10, 100, 100)));

        SegSweepMacroOptions restored = SweepStateStore.deserialise(
                SweepStateStore.serialise(options), image(512, 512));

        assertEquals(CropSpec.Mode.CUSTOM, restored.crop().mode());
        assertEquals(new Rectangle(10, 10, 100, 100), restored.crop().bounds());
    }

    @Test
    public void aCustomCropIsDroppedWhenThereIsNoImageToCheckItAgainst() {
        assertFalse(SweepStateStore.cropFits(
                CropSpec.custom(new Rectangle(0, 0, 10, 10)), null));
        assertTrue(SweepStateStore.cropFits(CropSpec.full(), null));
        assertTrue(SweepStateStore.cropFits(CropSpec.centre256(), null));
    }

    @Test
    public void corruptOrEmptyStateYieldsDefaultsRatherThanAnError() {
        assertEquals(SegSweepMacroOptions.defaults().primaryAxis().from(),
                SweepStateStore.deserialise("not a macro option string {{{",
                        image(64, 64)).primaryAxis().from(), 1e-9d);
        assertEquals(SegSweepMacroOptions.defaults().primaryAxis().to(),
                SweepStateStore.deserialise("", image(64, 64))
                        .primaryAxis().to(), 1e-9d);
        assertEquals(SegSweepMacroOptions.defaults().primaryAxis().to(),
                SweepStateStore.deserialise(null, image(64, 64))
                        .primaryAxis().to(), 1e-9d);
    }

    @Test
    public void optionsThatDoNotValidateAreNotStored() {
        SegSweepMacroOptions broken = new SegSweepMacroOptions();
        // No primary axis: validate() refuses, so there is nothing worth keeping.
        assertEquals(null, SweepStateStore.serialise(broken));
        assertEquals(null, SweepStateStore.serialise(null));
    }

    @Test
    public void theOversizedOverrideRoundTripsSoAMacroReproducesTheRun() {
        SegSweepMacroOptions options = narrowedRange();
        options.setAllowOversizedSweep(true);

        String stored = SweepStateStore.serialise(options);
        assertTrue(stored, stored.contains("allow_oversized"));
        assertTrue(SweepStateStore.deserialise(stored, image(64, 64))
                .allowOversizedSweep());
    }
}
