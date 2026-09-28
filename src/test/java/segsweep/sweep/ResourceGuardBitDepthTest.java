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
import ij.process.FloatProcessor;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The guard assumed ~249 bytes per voxel for every bit depth, but a 32-bit
 * build with unique values measured 465 bytes per voxel, so the guard let
 * through sweeps that then ran out of memory mid-build.
 */
public class ResourceGuardBitDepthTest {
    /** Measured minimum heap for a 32-bit unique-value build (stage 03 report). */
    private static final long MEASURED_32_BIT_BUILD_BYTES = 465L;

    @Test
    public void estimateCoversTheMeasuredBuildHeapForEveryBitDepth() {
        assertTrue(ResourceGuard.buildBytesPerVoxel(32) >= MEASURED_32_BIT_BUILD_BYTES);
        assertTrue(ResourceGuard.buildBytesPerVoxel(16) >= 261L);
        assertTrue(ResourceGuard.buildBytesPerVoxel(8) >= 191L);
        ResourceGuard.Estimate e32 = ResourceGuard.estimateTreeMemory(64, 64, 16, 32);
        long buildPart = e32.unionFindBytes() + e32.nodeArrayBytes()
                + e32.childArrayBytes() + e32.attributeBytes();
        assertEquals(64L * 64L * 16L * ResourceGuard.buildBytesPerVoxel(32), buildPart);
    }

    @Test
    public void thirtyTwoBitUniqueVolumeAboveTheBudgetIsRefusedAsMemory() {
        ImagePlus unique = unique32(64, 64, 16);
        long available = 60L * 1024L * 1024L; // budget = half = 30 MiB
        ResourceGuard.Feasibility feasibility = ResourceGuard.assessComputeFeasibilityForBudget(
                sweep(), unique, 1, available);

        assertFalse("65536 voxels x ~590 B exceeds a 30 MiB budget", feasibility.isOk());
        assertEquals(ResourceGuard.RefusalKind.MEMORY_BUDGET, feasibility.refusalKind());
        assertFalse(feasibility.overridable());
        assertTrue(feasibility.getMessage().contains("component-tree memory"));
        // The old flat estimate (~251 B/voxel incl. source and labels) fitted this budget.
        assertTrue(64L * 64L * 16L * 251L < available / 2L);
    }

    @Test
    public void eightBitVolumeOfTheSameSizeStillFits() {
        ImageStack stack = new ImageStack(64, 64);
        for (int z = 0; z < 16; z++) stack.addSlice("z" + z, new ByteProcessor(64, 64));
        ResourceGuard.Feasibility feasibility = ResourceGuard.assessComputeFeasibilityForBudget(
                sweep(), new ImagePlus("eight", stack), 1, 60L * 1024L * 1024L);
        assertTrue(feasibility.getMessage(), feasibility.isOk());
    }

    private static ParameterSweep sweep() {
        Map<ParameterId, ParameterValueList> values =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        values.put(ParameterId.THRESHOLD, ParameterValueList.ofInts(1, 2, 3));
        return new ParameterSweep(ParameterSweep.Method.CLASSICAL, values, CropSpec.full(), "C1");
    }

    private static ImagePlus unique32(int w, int h, int d) {
        ImageStack stack = new ImageStack(w, h);
        int value = 0;
        for (int z = 0; z < d; z++) {
            float[] pixels = new float[w * h];
            for (int i = 0; i < pixels.length; i++) pixels[i] = value++;
            stack.addSlice("z" + z, new FloatProcessor(w, h, pixels, null));
        }
        return new ImagePlus("unique32", stack);
    }
}
