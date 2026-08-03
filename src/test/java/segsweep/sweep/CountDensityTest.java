/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.sweep;

import ij.ImagePlus;
import org.junit.Test;
import segsweep.SegSweepLabeller;
import segsweep.SegSweepLabellerFixtures;
import segsweep.tree.ComponentTree;
import segsweep.tree.ComponentTreeQuery;
import segsweep.tree.LazyLabelMap;

import java.awt.Rectangle;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CountDensityTest {
    @Test
    public void calibratedDensityUsesCropVoxelVolume() {
        SweepProvenance provenance = provenance(
                CropSpec.custom(new Rectangle(0, 0, 10, 10)),
                10, 10, 2, "micron", 0.024d);
        VariationResult result = VariationResult.success(combo(), labelMap(),
                12, 3L, null, provenance);

        assertTrue(result.calibrated());
        assertFalse(result.flags().contains(VariationResult.Flag.UNCALIBRATED));
        assertEquals(12.0d / (10.0d * 10.0d * 2.0d * 0.024d),
                result.objectsPerCalibratedVolume() / 1.0e9d, 1.0e-12);
        assertTrue(Double.isNaN(result.objectsPerCalibratedArea()));
    }

    @Test
    public void twoDimensionalDensityUsesSquareMillimetresAndIsNotVolume() {
        SweepProvenance provenance = provenance(
                CropSpec.custom(new Rectangle(0, 0, 10, 10)),
                10, 10, 1, "micron", 0.04d, 0.08d);
        VariationResult result = VariationResult.success(combo(), labelMap(),
                8, 3L, null, provenance);

        assertEquals(8.0d / (10.0d * 10.0d * 0.04d),
                result.objectsPerCalibratedArea() / 1.0e6d, 1.0e-12);
        assertTrue(Double.isNaN(result.objectsPerCalibratedVolume()));
    }

    @Test
    public void uncalibratedInputReturnsNanAndFlag() {
        SweepProvenance provenance = provenance(CropSpec.full(),
                10, 10, 1, "", 1.0d);
        VariationResult result = VariationResult.success(combo(), labelMap(),
                4, 3L, null, provenance);

        assertFalse(result.calibrated());
        assertTrue(Double.isNaN(result.objectsPerCalibratedVolume()));
        assertTrue(result.flags().contains(VariationResult.Flag.UNCALIBRATED));
    }

    @Test
    public void pixelUnitCountsAsUncalibrated() {
        SweepProvenance provenance = provenance(CropSpec.full(),
                10, 10, 1, "pixel", 1.0d);
        VariationResult result = VariationResult.success(combo(), labelMap(),
                4, 3L, null, provenance);

        assertFalse(result.calibrated());
        assertTrue(result.flags().contains(VariationResult.Flag.UNCALIBRATED));
    }

    @Test
    public void statusFlagsAreTyped() {
        VariationResult empty = VariationResult.success(combo(), labelMap(),
                0, 1L, null, provenance(CropSpec.full(), 10, 10, 1, "micron", 1.0d));
        VariationResult failed = VariationResult.failure(combo(),
                new RuntimeException("boom"),
                provenance(CropSpec.full(), 10, 10, 1, "micron", 1.0d),
                EnumSet.of(VariationResult.Flag.TIMED_OUT));

        assertTrue(empty.flags().contains(VariationResult.Flag.EMPTY));
        assertTrue(failed.flags().contains(VariationResult.Flag.FAILED));
        assertTrue(failed.flags().contains(VariationResult.Flag.TIMED_OUT));
    }

    private static ParameterCombo combo() {
        return ParameterCombo.builder()
                .put(ParameterId.THRESHOLD, Integer.valueOf(10))
                .build();
    }

    private static LazyLabelMap labelMap() {
        ImagePlus image = SegSweepLabellerFixtures.points(5, 2, 1,
                new int[][] { { 0, 0, 0 } });
        return ComponentTree.build(image, SegSweepLabeller.Connectivity.SIX)
                .query(ComponentTreeQuery.builder().threshold(10).build())
                .labelMap();
    }

    private static SweepProvenance provenance(CropSpec crop,
                                              int width,
                                              int height,
                                              int depth,
                                              String unit,
                                              double voxelVolume) {
        Map<ParameterId, ParameterValueList> ranges =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        ranges.put(ParameterId.THRESHOLD, ParameterValueList.ofInts(10));
        return new SweepProvenance(crop, width, height, depth, ranges, unit, voxelVolume);
    }

    private static SweepProvenance provenance(CropSpec crop,
                                              int width,
                                              int height,
                                              int depth,
                                              String unit,
                                              double pixelArea,
                                              double voxelVolume) {
        Map<ParameterId, ParameterValueList> ranges =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        ranges.put(ParameterId.THRESHOLD, ParameterValueList.ofInts(10));
        return new SweepProvenance(crop, width, height, depth, ranges,
                unit, pixelArea, voxelVolume);
    }
}
