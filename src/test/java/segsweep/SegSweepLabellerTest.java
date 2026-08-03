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
import ij.process.ImageProcessor;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class SegSweepLabellerTest {
    @Test
    public void diagonalVoxelsTouchUnder26ButNot6Connectivity() {
        ImagePlus image = SegSweepLabellerFixtures.points(4, 4, 1,
                new int[][] { { 1, 1, 0 }, { 2, 2, 0 } });

        LabelResult twentySix = label(image, SegSweepLabeller.Connectivity.TWENTY_SIX);
        LabelResult six = label(image, SegSweepLabeller.Connectivity.SIX);

        assertEquals(LabelResult.Status.OK, twentySix.status());
        assertEquals(1, twentySix.objectCount());
        assertObjectCountMatchesDistinctLabels(twentySix);
        assertEquals(LabelResult.Status.OK, six.status());
        assertEquals(2, six.objectCount());
        assertObjectCountMatchesDistinctLabels(six);
    }

    @Test
    public void faceTouchingAcrossSliceBoundaryIsOne3DObject() {
        ImagePlus image = SegSweepLabellerFixtures.points(4, 4, 2,
                new int[][] { { 1, 1, 0 }, { 1, 1, 1 } });

        LabelResult result = label(image, SegSweepLabeller.Connectivity.SIX);

        assertEquals(LabelResult.Status.OK, result.status());
        assertEquals(1, result.objectCount());
        assertArrayEquals(new int[] { 0, 2 }, result.objectSizes());
        assertObjectCountMatchesDistinctLabels(result);
    }

    @Test
    public void objectFlushAgainstEveryStackFaceIsFound() {
        ImagePlus image = SegSweepLabellerFixtures.emptyStack(3, 3, 3);
        for (int z = 0; z < 3; z++) {
            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 3; x++) {
                    SegSweepLabellerFixtures.setVoxel(image, x, y, z, SegSweepLabellerFixtures.FOREGROUND);
                }
            }
        }

        LabelResult result = label(image, SegSweepLabeller.Connectivity.SIX);

        assertEquals(LabelResult.Status.OK, result.status());
        assertEquals(1, result.objectCount());
        assertArrayEquals(new int[] { 0, 27 }, result.objectSizes());
        assertObjectCountMatchesDistinctLabels(result);
    }

    @Test
    public void minSizeExcludesBelowAndIncludesBoundary() {
        LabelResult result = SegSweepLabeller.label(SegSweepLabellerFixtures.sizeRangeStack(),
                SegSweepLabellerFixtures.THRESHOLD, 2, 10, SegSweepLabeller.Connectivity.SIX);

        assertEquals(LabelResult.Status.OK, result.status());
        assertEquals(2, result.objectCount());
        assertArrayEquals(new int[] { 0, 2, 3 }, result.objectSizes());
        assertObjectCountMatchesDistinctLabels(result);
    }

    @Test
    public void maxSizeExcludesAboveAndIncludesBoundary() {
        LabelResult result = SegSweepLabeller.label(SegSweepLabellerFixtures.sizeRangeStack(),
                SegSweepLabellerFixtures.THRESHOLD, 0, 2, SegSweepLabeller.Connectivity.SIX);

        assertEquals(LabelResult.Status.OK, result.status());
        assertEquals(2, result.objectCount());
        assertArrayEquals(new int[] { 0, 1, 2 }, result.objectSizes());
        assertObjectCountMatchesDistinctLabels(result);
    }

    @Test
    public void allVoxelsBelowThresholdReturnsCalibratedEmptyStack() {
        ImagePlus image = SegSweepLabellerFixtures.calibratedEmptyStack(5, 4, 3);
        SegSweepLabellerFixtures.setVoxel(image, 1, 1, 1, SegSweepLabellerFixtures.THRESHOLD);

        LabelResult result = label(image, SegSweepLabeller.Connectivity.TWENTY_SIX);

        assertEquals(LabelResult.Status.EMPTY, result.status());
        assertEquals(0, result.objectCount());
        assertEquals(5, result.labels().getWidth());
        assertEquals(4, result.labels().getHeight());
        assertEquals(3, result.labels().getStackSize());
        assertEquals(16, result.labels().getBitDepth());
        Calibration inputCalibration = image.getCalibration();
        Calibration outputCalibration = result.labels().getCalibration();
        assertEquals(inputCalibration.pixelWidth, outputCalibration.pixelWidth, 0.0);
        assertEquals(inputCalibration.pixelHeight, outputCalibration.pixelHeight, 0.0);
        assertEquals(inputCalibration.pixelDepth, outputCalibration.pixelDepth, 0.0);
        assertEquals(inputCalibration.getUnit(), outputCalibration.getUnit());
        assertObjectCountMatchesDistinctLabels(result);
    }

    @Test
    public void singleVoxelObjectSurvivesWhenMinimumAllowsIt() {
        ImagePlus image = SegSweepLabellerFixtures.points(3, 3, 1,
                new int[][] { { 1, 1, 0 } });

        LabelResult result = SegSweepLabeller.label(image, SegSweepLabellerFixtures.THRESHOLD,
                1, 1, SegSweepLabeller.Connectivity.TWENTY_SIX);

        assertEquals(LabelResult.Status.OK, result.status());
        assertEquals(1, result.objectCount());
        assertArrayEquals(new int[] { 0, 1 }, result.objectSizes());
        assertObjectCountMatchesDistinctLabels(result);
    }

    @Test
    public void finalLabelsAreContiguousAfterFilteringRemovesAGap() {
        LabelResult result = SegSweepLabeller.label(SegSweepLabellerFixtures.sizeRangeStack(),
                SegSweepLabellerFixtures.THRESHOLD, 2, 3, SegSweepLabeller.Connectivity.SIX);

        assertEquals(LabelResult.Status.OK, result.status());
        assertEquals(2, result.objectCount());
        assertEquals(1, result.labels().getStack().getProcessor(1).get(2, 1));
        assertEquals(1, result.labels().getStack().getProcessor(1).get(3, 1));
        assertEquals(2, result.labels().getStack().getProcessor(1).get(5, 1));
        assertEquals(2, result.labels().getStack().getProcessor(1).get(6, 1));
        assertEquals(2, result.labels().getStack().getProcessor(1).get(7, 1));
        assertObjectCountMatchesDistinctLabels(result);
    }

    @Test
    public void tooManyFinalLabelsReturnsTypedStatusWithoutThrowing() {
        LabelResult result = SegSweepLabeller.label(SegSweepLabellerFixtures.overLimitCheckerboard(),
                SegSweepLabellerFixtures.THRESHOLD, 1, 1, SegSweepLabeller.Connectivity.SIX);

        assertEquals(LabelResult.Status.TOO_MANY_LABELS, result.status());
        assertTrue(result.reason().contains("65535"));
        assertTrue(result.objectCount() > 65535);
        assertEquals(0, distinctNonZeroLabels(result.labels()));
        assertEquals(16, result.labels().getBitDepth());
    }

    @Test
    public void outputStackIs16BitAndCarriesCalibration() {
        ImagePlus image = SegSweepLabellerFixtures.calibratedEmptyStack(4, 4, 2);
        SegSweepLabellerFixtures.setVoxel(image, 1, 1, 0, SegSweepLabellerFixtures.FOREGROUND);

        LabelResult result = label(image, SegSweepLabeller.Connectivity.TWENTY_SIX);

        assertEquals(LabelResult.Status.OK, result.status());
        assertEquals(16, result.labels().getBitDepth());
        assertEquals(image.getCalibration().pixelWidth, result.labels().getCalibration().pixelWidth, 0.0);
        assertEquals(image.getCalibration().pixelDepth, result.labels().getCalibration().pixelDepth, 0.0);
    }

    @Test
    public void labelsLargeStackWithAboutTwoThousandObjectsUnderOneSecond() {
        ImagePlus image = SegSweepLabellerFixtures.largePerformanceStack();
        long started = System.nanoTime();

        LabelResult result = label(image, SegSweepLabeller.Connectivity.TWENTY_SIX);

        long elapsedMs = (System.nanoTime() - started) / 1000000L;
        assertEquals(LabelResult.Status.OK, result.status());
        assertEquals(2000, result.objectCount());
        assertObjectCountMatchesDistinctLabels(result);
        assertTrue("512x512x20 stack with 2000 objects took " + elapsedMs + " ms",
                elapsedMs < 1000L);
    }

    @Test
    public void nullConnectivityUsesDocumentedDefault() {
        assertSame(SegSweepLabeller.Connectivity.TWENTY_SIX, SegSweepLabeller.DEFAULT_CONNECTIVITY);
        ImagePlus image = SegSweepLabellerFixtures.points(4, 4, 1,
                new int[][] { { 1, 1, 0 }, { 2, 2, 0 } });

        LabelResult result = SegSweepLabeller.label(image, SegSweepLabellerFixtures.THRESHOLD,
                0, Integer.MAX_VALUE, null);

        assertEquals(1, result.objectCount());
    }

    private static LabelResult label(ImagePlus image, SegSweepLabeller.Connectivity connectivity) {
        return SegSweepLabeller.label(image, SegSweepLabellerFixtures.THRESHOLD,
                0, Integer.MAX_VALUE, connectivity);
    }

    private static void assertObjectCountMatchesDistinctLabels(LabelResult result) {
        assertEquals(result.objectCount(), distinctNonZeroLabels(result.labels()));
    }

    private static int distinctNonZeroLabels(ImagePlus image) {
        Set<Integer> labels = new HashSet<Integer>();
        ImageStack stack = image.getStack();
        for (int slice = 1; slice <= stack.getSize(); slice++) {
            ImageProcessor processor = stack.getProcessor(slice);
            for (int i = 0; i < processor.getPixelCount(); i++) {
                int value = processor.get(i);
                if (value > 0) {
                    labels.add(Integer.valueOf(value));
                }
            }
        }
        return labels.size();
    }
}
