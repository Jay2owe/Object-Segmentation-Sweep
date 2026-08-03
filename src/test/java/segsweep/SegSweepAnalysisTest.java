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
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.process.ImageProcessor;
import ij.process.FloatProcessor;
import ij.process.ShortProcessor;
import org.junit.Test;
import segsweep.sweep.CropSpec;
import segsweep.sweep.ParameterCombo;
import segsweep.sweep.ParameterId;
import segsweep.sweep.ParameterKey;
import segsweep.sweep.ParameterValueList;
import segsweep.sweep.VariationResult;
import segsweep.token.MorphPredicate;
import segsweep.token.SegmentationMethod;
import segsweep.token.SegmentationTokenParser;
import segsweep.tree.LazyLabelMap;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.LinkedHashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SegSweepAnalysisTest {

    @Test
    public void syntheticStackProducesSweepRowsAndFullAxisKneeAtThirtyTwo() {
        SegSweepResult result = SegSweep.run(SegSweepParameters.builder()
                .image(designedKneeStack(true))
                .engine(SegmentationMethod.Engine.CLASSICAL)
                .axis(ParameterId.THRESHOLD, 10, 60, 5)
                .connectivity(SegSweepLabeller.Connectivity.SIX)
                .pickCriterion(SegSweepParameters.PickCriterion.KNEE)
                .build());

        ResultsTable sweep = result.sweepTable();
        assertEquals(11, sweep.size());
        assertSweepColumns(sweep);
        assertEquals(1.0d, sweep.getValue(SegSweepResult.COL_COMBINATION, 0), 0.0d);
        assertContainsThreshold(sweep, 10.0d);
        assertContainsThreshold(sweep, 60.0d);

        ResultsTable pick = result.pickTable();
        assertEquals(1, pick.size());
        assertEquals("knee", pick.getStringValue(SegSweepResult.PICK_CRITERION, 0));
        assertEquals("KNEE_AT", pick.getStringValue(SegSweepResult.PICK_KNEE_OUTCOME, 0));
        assertEquals(32.0d, pick.getValue(SegSweepResult.PICK_KNEE_VALUE, 0), 0.000001d);
        assertEquals(32.0d, pick.getValue(ParameterId.THRESHOLD.displayLabel(), 0), 0.000001d);

        ParameterCombo picked = result.pickedCombo();
        assertNotNull(picked);
        assertEquals(32.0d, ((Number) picked.get(ParameterId.THRESHOLD)).doubleValue(), 0.000001d);
        assertTrue(result.pickedSettingsToken().contains("thresh=32"));
    }

    @Test
    public void uncalibratedInputLeavesDensityBlankAndWarns() {
        SegSweepResult result = SegSweep.run(SegSweepParameters.builder()
                .image(designedKneeStack(false))
                .axis(ParameterId.THRESHOLD, 10, 60, 10)
                .pickCriterion(SegSweepParameters.PickCriterion.KNEE)
                .build());

        assertEquals("", result.sweepTable().getStringValue(SegSweepResult.COL_OBJECTS_PER_MM3, 0));
        assertTrue(result.sweepTable().getStringValue(SegSweepResult.COL_FLAGS, 0)
                .contains("UNCALIBRATED"));
        assertContains(result.warnings(), "uncalibrated");
    }

    @Test
    public void smallCropCompletesAndReportsFractionWarning() {
        SegSweepResult result = SegSweep.run(SegSweepParameters.builder()
                .image(designedKneeStack(true))
                .axis(ParameterId.THRESHOLD, 10, 20, 10)
                .crop(CropSpec.custom(new Rectangle(0, 0, 1, 1)))
                .minimumCropFraction(0.25d)
                .pickCriterion(SegSweepParameters.PickCriterion.NONE)
                .build());

        assertEquals(2, result.sweepTable().size());
        assertContains(result.warnings(), "Crop fraction");
    }

    @Test
    public void validationFailuresAreTypedAndReadable() {
        expectFailure(SegSweepParameters.ValidationFailure.NO_IMAGE,
                new Runnable() {
                    @Override public void run() {
                        SegSweep.run(SegSweepParameters.builder()
                                .axis(ParameterId.THRESHOLD, 1, 3, 1)
                                .build());
                    }
                });
        expectFailure(SegSweepParameters.ValidationFailure.EMPTY_AXIS,
                new Runnable() {
                    @Override public void run() {
                        SegSweep.run(SegSweepParameters.builder()
                                .image(designedKneeStack(true))
                                .build());
                    }
                });
        expectFailure(SegSweepParameters.ValidationFailure.FROM_GREATER_THAN_TO,
                new Runnable() {
                    @Override public void run() {
                        SegSweepParameters.builder()
                                .image(designedKneeStack(true))
                                .axis(ParameterId.THRESHOLD, 5, 1, 1)
                                .build();
                    }
                });
        expectFailure(SegSweepParameters.ValidationFailure.ZERO_STEP,
                new Runnable() {
                    @Override public void run() {
                        SegSweepParameters.builder()
                                .image(designedKneeStack(true))
                                .axis(ParameterId.THRESHOLD, 1, 5, 0)
                                .build();
                    }
                });
        expectFailure(SegSweepParameters.ValidationFailure.CROP_OUTSIDE_IMAGE_BOUNDS,
                new Runnable() {
                    @Override public void run() {
                        SegSweep.run(SegSweepParameters.builder()
                                .image(designedKneeStack(true))
                                .axis(ParameterId.THRESHOLD, 1, 5, 1)
                                .crop(CropSpec.custom(new Rectangle(100, 100, 5, 5)))
                                .build());
                    }
                });
        expectFailure(SegSweepParameters.ValidationFailure.UNSUPPORTED_ENGINE,
                new Runnable() {
                    @Override public void run() {
                        SegSweep.run(SegSweepParameters.builder()
                                .image(designedKneeStack(true))
                                .engine(SegmentationMethod.Engine.STARDIST)
                                .axis(ParameterId.THRESHOLD, 1, 5, 1)
                                .build());
                    }
                });
        expectFailure(SegSweepParameters.ValidationFailure.UNSUPPORTED_AXIS_COMBINATION,
                new Runnable() {
                    @Override public void run() {
                        SegSweep.run(SegSweepParameters.builder()
                                .image(designedKneeStack(true))
                                .axis(ParameterId.PROB_THRESH, 0.1, 0.5, 0.1)
                                .build());
                    }
                });
    }

    @Test
    public void pickNoneReturnsSweepWithoutPickedComboOrPickRows() {
        SegSweepResult result = SegSweep.run(SegSweepParameters.builder()
                .image(designedKneeStack(true))
                .axis(ParameterId.THRESHOLD, 10, 60, 10)
                .pickCriterion(SegSweepParameters.PickCriterion.NONE)
                .build());

        assertEquals(6, result.sweepTable().size());
        assertEquals(0, result.pickTable().size());
        assertNull(result.pick());
        assertNull(result.pickedCombo());
        assertNull(result.pickedLabelMap());
    }

    @Test
    public void pickedLabelMapIsLazyUntilCallerRequestsIt() {
        SegSweepResult result = SegSweep.run(SegSweepParameters.builder()
                .image(designedKneeStack(true))
                .axis(ParameterId.THRESHOLD, 10, 60, 5)
                .connectivity(SegSweepLabeller.Connectivity.SIX)
                .pickCriterion(SegSweepParameters.PickCriterion.KNEE)
                .build());

        LazyLabelMap labels = result.pickedLabelMap();
        assertNotNull(labels);
        assertEquals(0, labels.materializationCount());
        ImagePlus materialized = labels.get();
        assertEquals(1, labels.materializationCount());
        materialized.close();
    }

    @Test
    public void fractionalThresholdsRemainDistinctOnFloatImages() {
        ImagePlus image = new ImagePlus("fractional",
                new FloatProcessor(3, 1, new float[] { 0.2f, 0.0f, 0.8f }));
        SegSweepResult result = SegSweep.run(SegSweepParameters.builder()
                .image(image)
                .axis(ParameterId.THRESHOLD, ParameterValueList.ofDoubles(0.4d, 0.6d))
                .pickCriterion(SegSweepParameters.PickCriterion.NONE)
                .build());

        assertEquals(2, result.sweepTable().size());
        assertEquals(1.0d, result.sweepTable().getValue(SegSweepResult.COL_OBJECTS, 0), 0.0d);
        assertEquals(1.0d, result.sweepTable().getValue(SegSweepResult.COL_OBJECTS, 1), 0.0d);
    }

    @Test
    public void pickedMethodRecordsChannelFractionalThresholdAndMorphology() {
        SegSweepParameters params = SegSweepParameters.builder()
                .image(designedKneeStack(true))
                .channel(2)
                .axis(ParameterId.THRESHOLD, ParameterValueList.ofDoubles(0.4d))
                .build();
        LinkedHashMap<ParameterKey, Object> values = new LinkedHashMap<ParameterKey, Object>();
        values.put(ParameterId.THRESHOLD, Double.valueOf(0.4d));
        values.put(ParameterId.SPHERICITY, Double.valueOf(0.7d));
        ParameterCombo combo = new ParameterCombo(values);

        SegmentationMethod parsed = SegmentationTokenParser.parse(
                SegmentationTokenParser.format(SegSweepAnalysis.methodFor(params, combo)));

        assertEquals(0.4d, SegmentationMethod.threshold(parsed), 0.0d);
        assertEquals("2", parsed.params.get("channel"));
        assertEquals("twenty_six", parsed.params.get("connectivity"));
        java.util.List<MorphPredicate> predicates = SegmentationMethod.morphPredicates(parsed);
        assertEquals(1, predicates.size());
        assertEquals("sphericity", predicates.get(0).featureName());
        assertEquals(0.7d, predicates.get(0).value(), 0.0d);
    }

    @Test
    public void manualSelectionReturnsItsLazyLabelsAndSettings() {
        SegSweepResult result = SegSweep.run(SegSweepParameters.builder()
                .image(designedKneeStack(true))
                .axis(ParameterId.THRESHOLD, 10, 30, 10)
                .pickCriterion(SegSweepParameters.PickCriterion.NONE)
                .build());
        ParameterCombo selected = null;
        for (VariationResult variation : result.results()) {
            Number threshold = (Number) variation.combo().get(ParameterId.THRESHOLD);
            if (threshold != null && threshold.doubleValue() == 20.0d) {
                selected = variation.combo();
                break;
            }
        }
        assertNotNull(selected);
        String token = SegSweep_.settingsTokenForSelected(result, selected);

        SegSweepResult manual = result.withPickedSelection(selected, token);

        assertEquals(selected, manual.pickedCombo());
        assertNotNull(manual.pickedLabelMap());
        assertEquals(0, manual.pickedLabelMap().materializationCount());
        assertTrue(manual.pickedSettingsToken().contains("thresh=20"));
        assertTrue(manual.pickedSettingsToken().contains("channel=1"));
    }

    private static void assertSweepColumns(ResultsTable table) {
        for (String column : Arrays.asList(
                SegSweepResult.COL_COMBINATION,
                ParameterId.THRESHOLD.displayLabel(),
                SegSweepResult.COL_OBJECTS,
                SegSweepResult.COL_OBJECTS_PER_MM3,
                SegSweepResult.COL_MEAN_NEIGHBOUR_IOU,
                SegSweepResult.COL_STABILITY_ELIGIBLE,
                SegSweepResult.COL_DURATION_MS,
                SegSweepResult.COL_CROP_FRACTION,
                SegSweepResult.COL_FLAGS)) {
            assertTrue("Missing column " + column, table.getColumnIndex(column) >= 0);
        }
    }

    private static void assertContainsThreshold(ResultsTable table, double threshold) {
        for (int row = 0; row < table.size(); row++) {
            if (Math.abs(table.getValue(ParameterId.THRESHOLD.displayLabel(), row) - threshold)
                    < 0.000001d) {
                return;
            }
        }
        throw new AssertionError("Expected displayed threshold " + threshold);
    }

    private static void expectFailure(SegSweepParameters.ValidationFailure expected,
                                      Runnable action) {
        try {
            action.run();
        } catch (SegSweepParameters.ValidationException e) {
            assertEquals(expected, e.failure());
            assertTrue(e.getMessage() != null && e.getMessage().length() > 10);
            return;
        }
        throw new AssertionError("Expected validation failure " + expected);
    }

    private static void assertContains(Iterable<String> messages, String needle) {
        for (String message : messages) {
            if (message != null
                    && message.toLowerCase(java.util.Locale.ROOT)
                    .contains(needle.toLowerCase(java.util.Locale.ROOT))) {
                return;
            }
        }
        throw new AssertionError("Expected warning containing " + needle);
    }

    static ImagePlus designedKneeStack(boolean calibrated) {
        ImageStack stack = new ImageStack(40, 8);
        stack.addSlice("z1", new ShortProcessor(40, 8));
        ImagePlus image = new ImagePlus("stage-12-knee", stack);
        for (int i = 0; i < 4; i++) {
            setBlock(image, 1 + i * 4, 1, 2, 2, 32);
        }
        for (int i = 0; i < 3; i++) {
            setBlock(image, 1 + i * 4, 5, 2, 2, 60);
        }
        if (calibrated) {
            Calibration calibration = new Calibration();
            calibration.pixelWidth = 1.0d;
            calibration.pixelHeight = 1.0d;
            calibration.pixelDepth = 1.0d;
            calibration.setUnit("micron");
            image.setCalibration(calibration);
        }
        return image;
    }

    private static void setBlock(ImagePlus image, int x0, int y0,
                                 int width, int height, int value) {
        ImageProcessor processor = image.getStack().getProcessor(1);
        for (int y = y0; y < y0 + height; y++) {
            for (int x = x0; x < x0 + width; x++) {
                processor.set(x, y, value);
            }
        }
    }
}
