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
import ij.ImageStack;
import ij.process.ByteProcessor;
import org.junit.Test;

import java.awt.Rectangle;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ResourceGuardTest {

    @Test
    public void estimateIncludesTreeAttributesSourceAndOneLazyLabelMap() {
        ResourceGuard.Estimate estimate = ResourceGuard.estimateTreeMemory(10, 20, 3, 16);

        assertEquals(600L, estimate.cropVoxels());
        assertEquals(1200L, estimate.sourceBytes());
        assertEquals(1200L, estimate.oneLazyLabelMapBytes());
        assertTrue(estimate.unionFindBytes() > 0L);
        assertTrue(estimate.nodeArrayBytes() > 0L);
        assertTrue(estimate.childArrayBytes() > 0L);
        assertTrue(estimate.attributeBytes() > estimate.nodeArrayBytes());
        assertEquals(estimate.sourceBytes() + estimate.treeBytes()
                        + estimate.attributeBytes() + estimate.oneLazyLabelMapBytes(),
                estimate.totalBytes());
    }

    @Test
    public void refusesSyntheticFullStackTreeThatExceedsLimit() {
        ImagePlus source = stack("large", 1024, 1024, 30);

        ResourceGuard.Decision decision = ResourceGuard.checkTreeMemory(
                source, CropSpec.full(), 128L * 1024L * 1024L);

        assertTrue(decision.refused());
        assertTrue(decision.reason().contains("component-tree memory"));
    }

    @Test
    public void permitsSmallCroppedTree() {
        ImagePlus source = stack("small", 512, 512, 3);

        ResourceGuard.Decision decision = ResourceGuard.checkTreeMemory(
                source, CropSpec.custom(new Rectangle(0, 0, 32, 32)),
                128L * 1024L * 1024L);

        assertTrue(decision.permitted());
        assertEquals(32L * 32L * 3L, decision.estimate().cropVoxels());
    }

    @Test
    public void sweepFeasibilityUsesCropSpecNotCombinationCountCacheBudget() {
        Map<ParameterId, ParameterValueList> values =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        values.put(ParameterId.THRESHOLD, ParameterValueList.ofInts(1, 2, 3, 4, 5));
        ParameterSweep sweep = new ParameterSweep(ParameterSweep.Method.CLASSICAL,
                values, CropSpec.custom(new Rectangle(0, 0, 16, 16)), "DAPI");

        ResourceGuard.Feasibility feasibility = ResourceGuard.assessFeasibility(
                sweep, stack("small", 64, 64, 1));

        assertTrue(feasibility.isOk());
        assertEquals((220L * 210L * 4L + 16L * 1024L) * 5L,
                feasibility.estimate().previewBytes());
        assertFalse(feasibility.getMessage().contains("cell count"));
    }

    @Test
    public void refusesMoreThanOneHundredDisplayCellsEvenForTinyCrop() {
        Map<ParameterId, ParameterValueList> values =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        values.put(ParameterId.THRESHOLD, ParameterValueList.fromRange(0, 100, 1));
        ParameterSweep sweep = new ParameterSweep(ParameterSweep.Method.CLASSICAL,
                values, CropSpec.custom(new Rectangle(0, 0, 1, 1)), "DAPI");

        ResourceGuard.Feasibility feasibility = ResourceGuard.assessFeasibility(
                sweep, stack("tiny", 2, 2, 1));

        assertFalse(feasibility.isOk());
        assertTrue(feasibility.getMessage().contains("101 cells"));
        assertTrue(feasibility.getMessage().contains("practical limit of 100"));
    }

    @Test
    public void computeFeasibilityDoesNotChargeSwingCellsOrApplyGridLimit() {
        Map<ParameterId, ParameterValueList> values =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        values.put(ParameterId.THRESHOLD, ParameterValueList.fromRange(0, 100, 1));
        ParameterSweep sweep = new ParameterSweep(ParameterSweep.Method.CLASSICAL,
                values, CropSpec.full(), "DAPI");

        ResourceGuard.Feasibility feasibility = ResourceGuard.assessComputeFeasibility(
                sweep, stack("tiny-headless", 2, 2, 1));

        assertTrue(feasibility.isOk());
        assertEquals(0L, feasibility.estimate().previewBytes());
        assertEquals(101L * 513L, feasibility.estimate().combinationBytes());
        assertTrue(feasibility.estimate().queryScratchBytes() > 0L);
    }

    @Test
    public void computeFeasibilityRefusesPathologicalCellCountBeforeEnumeration() {
        Map<ParameterId, ParameterValueList> values =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        values.put(ParameterId.THRESHOLD, ParameterValueList.fromRange(
                0, ResourceGuard.MAX_COMPUTE_CELLS, 1));
        ParameterSweep sweep = new ParameterSweep(ParameterSweep.Method.CLASSICAL,
                values, CropSpec.full(), "DAPI");

        ResourceGuard.Feasibility feasibility = ResourceGuard.assessComputeFeasibility(
                sweep, stack("tiny-pathological", 2, 2, 1));

        assertFalse(feasibility.isOk());
        assertTrue(feasibility.getMessage().contains(
                Long.toString(ResourceGuard.MAX_COMPUTE_CELLS + 1L)));
        assertTrue(feasibility.getMessage().contains("compute limit"));
    }

    @Test
    public void computeFeasibilityAccountsForCombinationVoxelQueryCost() {
        Map<ParameterId, ParameterValueList> values =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        values.put(ParameterId.THRESHOLD, ParameterValueList.fromRange(0, 999, 1));
        ParameterSweep sweep = new ParameterSweep(ParameterSweep.Method.CLASSICAL,
                values, CropSpec.full(), "DAPI");

        ResourceGuard.Feasibility feasibility = ResourceGuard.assessComputeFeasibility(
                sweep, stack("large-query-product", 512, 512, 1));

        assertFalse(feasibility.isOk());
        assertTrue(feasibility.getMessage().contains("combination-voxel queries"));
    }

    @Test
    public void compactSelectionStateIsChargedAgainstInjectableHeapBudget() {
        Map<ParameterId, ParameterValueList> values =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        values.put(ParameterId.MAX_SIZE, ParameterValueList.fromRange(1, 5000, 1));
        ParameterSweep sweep = new ParameterSweep(ParameterSweep.Method.CLASSICAL,
                values, CropSpec.full(), "DAPI");
        ImagePlus checkerboard = checkerboard("selection-heavy", 100, 100);

        ResourceGuard.Feasibility feasibility =
                ResourceGuard.assessComputeFeasibilityForBudget(
                        sweep, checkerboard, 1, 20L * 1024L * 1024L);

        assertFalse(feasibility.isOk());
        assertTrue(feasibility.estimate().combinationBytes() > 8L * 1024L * 1024L);
        assertTrue(feasibility.estimate().queryScratchBytes() > 0L);
        assertTrue(feasibility.getMessage().contains("retained sweep state"));
    }

    @Test
    public void montageFeasibilityRejectsOversizedHeadlessOutput() {
        Map<ParameterId, ParameterValueList> values =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        values.put(ParameterId.THRESHOLD, ParameterValueList.fromRange(0, 100, 1));
        ParameterSweep sweep = new ParameterSweep(ParameterSweep.Method.CLASSICAL,
                values, CropSpec.full(), "DAPI");

        ResourceGuard.Feasibility feasibility = ResourceGuard.assessMontageFeasibility(
                sweep, stack("tiny-montage", 2, 2, 1));

        assertFalse(feasibility.isOk());
        assertTrue(feasibility.getMessage().contains("autosave montage"));
        assertTrue(feasibility.getMessage().contains("101 cells"));
    }

    @Test
    public void postAnalysisMontageGuardChargesOnlyTheOutputAllocation() {
        Map<ParameterId, ParameterValueList> values =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        values.put(ParameterId.THRESHOLD, ParameterValueList.fromRange(0, 9, 1));
        ParameterSweep sweep = new ParameterSweep(ParameterSweep.Method.CLASSICAL,
                values, CropSpec.full(), "DAPI");
        ImagePlus source = stack("output-only", 256, 256, 1);
        long available = 10L * 1024L * 1024L;

        ResourceGuard.Feasibility combined =
                ResourceGuard.assessComputeFeasibilityForBudget(
                        sweep, source, 1, available);
        ResourceGuard.Feasibility outputOnly =
                ResourceGuard.assessMontageOutputFeasibilityForBudget(
                        sweep, source, available);

        assertFalse(combined.isOk());
        assertTrue(outputOnly.isOk());
        assertEquals(0L, outputOnly.estimate().combinationBytes());
        assertEquals(10L * 220L * 210L * 4L + 256L * 256L * 12L,
                outputOnly.estimate().previewBytes());
        assertEquals(outputOnly.estimate().previewBytes(),
                outputOnly.estimate().totalBytes());
    }

    private static ImagePlus stack(String title, int width, int height, int slices) {
        ImageStack stack = new ImageStack(width, height);
        for (int i = 0; i < slices; i++) {
            stack.addSlice(new ByteProcessor(width, height));
        }
        ImagePlus image = new ImagePlus(title, stack);
        image.setDimensions(1, slices, 1);
        return image;
    }

    private static ImagePlus checkerboard(String title, int width, int height) {
        ByteProcessor pixels = new ByteProcessor(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (((x + y) & 1) == 0) pixels.set(x, y, 1);
            }
        }
        return new ImagePlus(title, pixels);
    }
}
