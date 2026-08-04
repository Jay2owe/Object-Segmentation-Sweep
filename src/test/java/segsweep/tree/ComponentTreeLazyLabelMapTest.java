/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.tree;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import org.junit.Test;
import segsweep.SegSweepLabeller;
import segsweep.SegSweepLabellerFixtures;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ComponentTreeLazyLabelMapTest {
    @Test
    public void objectCountDoesNotMaterialiseLabels() {
        ImagePlus image = SegSweepLabellerFixtures.points(5, 2, 1,
                new int[][] { { 0, 0, 0 }, { 4, 0, 0 } });
        ComponentTreeResult result = ComponentTree.build(image, SegSweepLabeller.Connectivity.SIX)
                .query(ComponentTreeQuery.builder().threshold(10).build());

        assertEquals(2, result.objectCount());
        assertEquals(0, result.labelMap().materializationCount());
    }

    @Test
    public void materialisesCalibrated16BitContiguousLabelStack() {
        ImagePlus image = SegSweepLabellerFixtures.calibratedEmptyStack(5, 2, 1);
        SegSweepLabellerFixtures.setVoxel(image, 0, 0, 0, 20);
        SegSweepLabellerFixtures.setVoxel(image, 4, 0, 0, 20);
        ComponentTreeResult result = ComponentTree.build(image, SegSweepLabeller.Connectivity.SIX)
                .query(ComponentTreeQuery.builder().threshold(10).build());

        ImagePlus labels = result.labelMap().get();

        assertEquals(1, result.labelMap().materializationCount());
        assertEquals(16, labels.getBitDepth());
        assertEquals(image.getCalibration().pixelWidth, labels.getCalibration().pixelWidth, 0.0);
        assertEquals(image.getCalibration().pixelDepth, labels.getCalibration().pixelDepth, 0.0);
        assertEquals(2, distinctNonZero(labels));
        assertEquals(1, labels.getStack().getProcessor(1).get(0, 0));
        assertEquals(2, labels.getStack().getProcessor(1).get(4, 0));
    }

    @Test
    public void emptySelectionDetachesFromTreeButKeepsBlankLabelMetadata() {
        ImagePlus image = SegSweepLabellerFixtures.calibratedEmptyStack(5, 3, 2);
        ComponentTreeResult result = ComponentTree.build(
                image, SegSweepLabeller.Connectivity.SIX)
                .query(ComponentTreeQuery.builder().threshold(1000).build());

        assertEquals(ComponentTreeResult.Status.EMPTY, result.status());
        assertFalse(result.selection().retainsTree());
        ImagePlus labels = result.labelMap().get();
        assertEquals(5, labels.getWidth());
        assertEquals(3, labels.getHeight());
        assertEquals(2, labels.getStackSize());
        assertEquals(image.getCalibration().pixelWidth,
                labels.getCalibration().pixelWidth, 0.0d);
        assertEquals(0, distinctNonZero(labels));
    }

    private static int distinctNonZero(ImagePlus image) {
        Set<Integer> values = new HashSet<Integer>();
        ImageProcessor processor = image.getStack().getProcessor(1);
        for (int i = 0; i < processor.getPixelCount(); i++) {
            int value = processor.get(i);
            if (value > 0) {
                values.add(Integer.valueOf(value));
            }
        }
        return values.size();
    }
}
