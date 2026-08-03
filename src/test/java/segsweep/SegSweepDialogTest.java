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
        assertTrue(text.toLowerCase(Locale.ROOT).contains("above the limit"));
        assertTrue(text.toLowerCase(Locale.ROOT).contains("crop"));
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
