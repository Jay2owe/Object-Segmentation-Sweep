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
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ShortProcessor;
import org.junit.Test;
import segsweep.sweep.ParameterCombo;
import segsweep.sweep.ParameterId;
import segsweep.ui.SegSweepDialog;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SegSweepDialogTest {

    @Test
    public void headlessSafeConstructionAndTwoSentenceAnalysisExplanation() {
        assertNotNull(new SegSweepDialog(null));
        String[] sentences = SegSweepDialog.analysisExplanationSentences();
        assertEquals(2, sentences.length);
        assertTrue(SegSweepDialog.killCriterionRecord().contains("one compact screenshot"));
    }

    @Test
    public void liveCostEstimateUsesDisplayWindowWording() {
        String text = SegSweepDialog.costEstimateText(
                SegSweepAnalysisTest.designedKneeStack(true),
                SegSweepMacroOptions.defaults());
        assertTrue(text.contains("combinations displayed"));
        assertTrue(text.contains("computes the crop once"));
    }

    @Test
    public void resourceGuardRefusalReasonIsAvailableBeforeRun() {
        ByteProcessor processor = new ByteProcessor(7000, 7000);
        ImagePlus huge = new ImagePlus("huge", processor);
        String text = SegSweepDialog.costEstimateText(huge, SegSweepMacroOptions.defaults());
        String lower = text.toLowerCase(Locale.ROOT);
        assertTrue(lower.contains("above") && lower.contains("limit"));
        assertTrue(lower.contains("crop"));
    }

    @Test
    public void disabledGridUsesMontageRatherThanSwingFeasibility() {
        SegSweepMacroOptions options = SegSweepMacroOptions.defaults();
        options.setPrimaryAxis(SegSweepMacroOptions.AxisSpec.range(
                ParameterId.THRESHOLD, 0, 99, 1));
        options.setShowGrid(false);

        assertTrue(SegSweepDialog.feasibility(
                new ImagePlus("tiny-headless", new ByteProcessor(2, 2)), options).isOk());
        assertTrue(SegSweepDialog.costEstimateText(
                new ImagePlus("tiny-headless", new ByteProcessor(2, 2)), options)
                .contains("computed without a grid"));
    }

    @Test
    public void rgbInputRefusalIsVisibleBeforeRun() {
        String text = SegSweepDialog.costEstimateText(
                new ImagePlus("rgb", new ColorProcessor(8, 8)),
                SegSweepMacroOptions.defaults());

        assertTrue(text.contains("8-bit, 16-bit, or 32-bit grayscale"));
        assertTrue(text.contains("24-bit input"));
    }

    @Test
    public void suggestRangePopulatesDisplayWindowThatRuns() {
        SegSweepMacroOptions suggested = SegSweepDialog.applySuggestedRange(
                SegSweepAnalysisTest.designedKneeStack(true),
                SegSweepMacroOptions.defaults(),
                ParameterId.THRESHOLD);
        assertTrue(suggested.primaryAxis().valueList().size() > 0);
        SegSweepResult result = SegSweep.run(suggested.toParameters(
                SegSweepAnalysisTest.designedKneeStack(true)));
        assertEquals(suggested.primaryAxis().valueList().size(), result.sweepTable().size());
    }

    @Test
    public void unsupportedMorphologyAxisDoesNotReceiveSizeSuggestions() {
        try {
            SegSweepDialog.applySuggestedRange(
                    SegSweepAnalysisTest.designedKneeStack(true),
                    SegSweepMacroOptions.defaults(), ParameterId.ELONGATION);
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("not available"));
            assertTrue(expected.getMessage().contains("Enter From, To, and Step"));
            return;
        }
        throw new AssertionError("Expected an unsupported range-suggestion failure.");
    }

    @Test
    public void suggestedRangeUsesSelectedChannel() {
        ImageStack stack = new ImageStack(8, 8);
        ShortProcessor channelOne = new ShortProcessor(8, 8);
        ShortProcessor channelTwo = new ShortProcessor(8, 8);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                channelOne.set(x, y, x < 4 ? 10 : 20);
                channelTwo.set(x, y, x < 4 ? 1000 : 2000);
            }
        }
        stack.addSlice("c1", channelOne);
        stack.addSlice("c2", channelTwo);
        ImagePlus image = new ImagePlus("two-channel", stack);
        image.setDimensions(2, 1, 1);
        SegSweepMacroOptions options = SegSweepMacroOptions.defaults();
        options.setChannel(2);

        SegSweepMacroOptions suggested = SegSweepDialog.applySuggestedRange(
                image, options, ParameterId.THRESHOLD);

        for (Object value : suggested.primaryAxis().valueList().values()) {
            assertTrue(((Number) value).doubleValue() > 500.0d);
        }
    }

    @Test
    public void warningsStatusTextSurfacesResultWarnings() {
        SegSweepResult result = SegSweep.run(SegSweepParameters.builder()
                .image(SegSweepAnalysisTest.designedKneeStack(false))
                .axis(ParameterId.THRESHOLD, 10, 60, 10)
                .build());
        String text = SegSweepDialog.warningsStatusText(result);
        assertTrue(text.contains("Warnings:"));
        assertTrue(text.toLowerCase(Locale.ROOT).contains("uncalibrated"));
    }

    @Test
    public void pickSelectedTokenReflectsChosenCombination() {
        SegSweepResult result = SegSweep.run(SegSweepParameters.builder()
                .image(SegSweepAnalysisTest.designedKneeStack(true))
                .axis(ParameterId.THRESHOLD, 10, 60, 10)
                .build());
        ParameterCombo selected = ParameterCombo.builder()
                .put(ParameterId.THRESHOLD, Integer.valueOf(50))
                .build();
        String token = SegSweep_.settingsTokenForSelected(result, selected);
        assertTrue(token.contains("criterion\tmanual"));
        assertTrue(token.contains("thresh=50"));
        assertTrue(token.contains("image\tstage-12-knee"));
        assertTrue(token.contains("channel\t1"));
        assertTrue(!token.contains("# Written 1970-01-01T00:00:00Z"));
    }

    @Test
    public void warningLoggingPathHasNoDialogDependency() {
        SegSweepMacroOptions options = SegSweepMacroOptionsParser.parse(
                "sweep=threshold from=10 to=20 step=10 hide_display");
        SegSweepResult result = SegSweep.run(options.toParameters(oneSliceImage()));
        assertTrue(result.warnings().size() > 0);
        assertTrue(SegSweepDialog.warningsStatusText(result).contains("Warnings:"));
    }

    private static ImagePlus oneSliceImage() {
        ByteProcessor processor = new ByteProcessor(8, 8);
        processor.set(2, 2, 100);
        return new ImagePlus("one-slice", processor);
    }
}
