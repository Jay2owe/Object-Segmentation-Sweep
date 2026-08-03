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
import ij.measure.ResultsTable;
import org.junit.Test;
import segsweep.sweep.CropSpec;
import segsweep.sweep.ParameterId;
import segsweep.sweep.ParameterValueList;
import segsweep.token.SegmentationMethod;

import java.awt.Rectangle;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class SegSweepMacroOptionsTest {

    @Test
    public void oneAxisRecordedMacroReplaysToSameSweepCsv() {
        SegSweepMacroOptions options = SegSweepMacroOptions.defaults();
        assertRoundTripsToSameSweepCsv(options);
    }

    @Test
    public void twoAxisRecordedMacroReplaysToSameSweepCsv() {
        SegSweepMacroOptions options = SegSweepMacroOptions.defaults();
        options.setSecondaryAxis(SegSweepMacroOptions.AxisSpec.range(
                ParameterId.MIN_SIZE, 0, 2, 1));
        assertRoundTripsToSameSweepCsv(options);
    }

    @Test
    public void explicitValuesRecordedMacroReplaysToSameSweepCsv() {
        SegSweepMacroOptions options = new SegSweepMacroOptions();
        options.setPrimaryAxis(SegSweepMacroOptions.AxisSpec.values(
                ParameterId.THRESHOLD, ParameterValueList.ofDoubles(10, 32, 60)));
        assertRoundTripsToSameSweepCsv(options);
    }

    @Test
    public void croppedRecordedMacroReplaysToSameSweepCsv() {
        SegSweepMacroOptions options = SegSweepMacroOptions.defaults();
        options.setCrop(CropSpec.custom(new Rectangle(0, 0, 20, 8)));
        assertRoundTripsToSameSweepCsv(options);
    }

    @Test
    public void fullContractOptionTableParses() {
        SegSweepMacroOptions options = SegSweepMacroOptionsParser.parse(
                "image=[source.tif] channel=2 engine=classical "
                        + "sweep=threshold values=[10,20,30] "
                        + "sweep2=min_size from2=1 to2=3 step2=1 "
                        + "crop=[1,2,30,40] pick=stability "
                        + "min_crop_fraction=0.1 stability_budget_ms=250 "
                        + "autosave=[C:/tmp/segsweep] hide_display");

        assertEquals("source.tif", options.image());
        assertEquals(2, options.channel());
        assertEquals(SegmentationMethod.Engine.CLASSICAL, options.engine());
        assertEquals(ParameterId.THRESHOLD, options.primaryAxis().id());
        assertEquals(3, options.primaryAxis().valueList().size());
        assertEquals(ParameterId.MIN_SIZE, options.secondaryAxis().id());
        assertEquals(SegSweepParameters.PickCriterion.STABILITY, options.pickCriterion());
        assertEquals(0.1d, options.minimumCropFraction(), 0.0d);
        assertEquals(250L, options.stabilityBudgetMs());
        assertEquals("C:/tmp/segsweep", options.autosave());
        assertTrue(options.hideDisplay());
        assertEquals(new Rectangle(1, 2, 30, 40), options.crop().bounds());
    }

    @Test
    public void unknownOptionIsReadableError() {
        try {
            SegSweepMacroOptionsParser.parse(
                    "sweep=threshold from=1 to=3 step=1 surprise=true");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Unknown"));
            assertTrue(e.getMessage().contains("surprise"));
            return;
        }
        throw new AssertionError("Expected unknown option failure.");
    }

    @Test
    public void valuesAndRangeTogetherAreRejectedNamingBoth() {
        try {
            SegSweepMacroOptionsParser.parse(
                    "sweep=threshold from=1 to=3 step=1 values=[1,2,3]");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("values"));
            assertTrue(e.getMessage().contains("from/to/step"));
            return;
        }
        throw new AssertionError("Expected values/range conflict.");
    }

    @Test
    public void hideDisplayParametersRunWithoutOpeningWindows() {
        SegSweepMacroOptions options = SegSweepMacroOptionsParser.parse(
                "sweep=threshold from=10 to=60 step=10 hide_display");
        ImagePlus image = SegSweepAnalysisTest.designedKneeStack(true);
        SegSweepResult result = SegSweep.run(options.toParameters(image));
        assertNotNull(result);
        assertEquals(6, result.sweepTable().size());
    }

    @Test
    public void gridAndTableVisibilityRoundTripIndependently() {
        boolean[] values = { false, true };
        for (boolean grid : values) {
            for (boolean tables : values) {
                SegSweepMacroOptions options = SegSweepMacroOptions.defaults();
                options.setShowGrid(grid);
                options.setShowTables(tables);
                SegSweepMacroOptions replayed = SegSweepMacroOptionsParser.parse(
                        options.toMacroOptions());
                assertEquals(grid, replayed.showGrid());
                assertEquals(tables, replayed.showTables());
            }
        }
    }

    @Test
    public void visibleGridDefersAutosaveUntilManualReview() {
        SegSweepMacroOptions options = SegSweepMacroOptions.defaults();
        options.setAutosave("output");
        assertFalse(SegSweep_.shouldAutoSaveImmediately(options, false));
        options.setShowGrid(false);
        assertTrue(SegSweep_.shouldAutoSaveImmediately(options, false));
        options.setShowGrid(true);
        assertTrue(SegSweep_.shouldAutoSaveImmediately(options, true));
    }

    private static void assertRoundTripsToSameSweepCsv(SegSweepMacroOptions options) {
        String recorded = options.toMacroOptions();
        SegSweepMacroOptions replayed = SegSweepMacroOptionsParser.parse(recorded);
        assertEquals(recorded, replayed.toMacroOptions());

        ImagePlus a = SegSweepAnalysisTest.designedKneeStack(true);
        ImagePlus b = SegSweepAnalysisTest.designedKneeStack(true);
        SegSweepResult original = SegSweep.run(options.toParameters(a));
        SegSweepResult replay = SegSweep.run(replayed.toParameters(b));
        assertEquals(sweepCsv(original.sweepTable()), sweepCsv(replay.sweepTable()));
    }

    private static String sweepCsv(ResultsTable table) {
        String[] columns = {
                SegSweepResult.COL_COMBINATION,
                ParameterId.THRESHOLD.displayLabel(),
                ParameterId.MIN_SIZE.displayLabel(),
                SegSweepResult.COL_OBJECTS,
                SegSweepResult.COL_OBJECTS_PER_MM3,
                SegSweepResult.COL_OBJECTS_PER_MM2,
                SegSweepResult.COL_MEAN_NEIGHBOUR_IOU,
                SegSweepResult.COL_STABILITY_ELIGIBLE,
                SegSweepResult.COL_DURATION_MS,
                SegSweepResult.COL_CROP_FRACTION,
                SegSweepResult.COL_FLAGS
        };
        StringBuilder out = new StringBuilder();
        out.append(String.join(",", Arrays.asList(columns))).append('\n');
        for (int row = 0; row < table.size(); row++) {
            for (int i = 0; i < columns.length; i++) {
                if (i > 0) out.append(',');
                out.append(value(table, columns[i], row));
            }
            out.append('\n');
        }
        return out.toString();
    }

    private static String value(ResultsTable table, String column, int row) {
        if (table.getColumnIndex(column) < 0) {
            return "";
        }
        String text = table.getStringValue(column, row);
        if (SegSweepResult.COL_DURATION_MS.equals(column)) {
            return "0";
        }
        return text == null ? "" : text;
    }
}
