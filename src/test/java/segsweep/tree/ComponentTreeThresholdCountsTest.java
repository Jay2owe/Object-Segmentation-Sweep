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
import ij.process.ShortProcessor;
import org.junit.Test;
import segsweep.SegSweepLabeller;

import static org.junit.Assert.assertEquals;

public class ComponentTreeThresholdCountsTest {
    @Test
    public void batchedCountsMatchIndividualQueries() {
        short[] pixels = new short[] { 0, 1, 3, 3, 2, 0, 4, 1 };
        ComponentTree tree = ComponentTree.build(
                new ImagePlus("small", new ShortProcessor(4, 2, pixels, null)),
                SegSweepLabeller.Connectivity.TWENTY_SIX);
        double[] thresholds = new double[] { 0, 1, 2, 3, 4 };
        ComponentTreeQuery template = ComponentTreeQuery.builder()
                .minSize(1).maxSize(20).build();

        int[] batched = tree.objectCountsAtThresholds(thresholds, template);

        for (int i = 0; i < thresholds.length; i++) {
            int expected = tree.query(ComponentTreeQuery.builder()
                    .threshold(thresholds[i]).minSize(1).maxSize(20).build()).objectCount();
            assertEquals(expected, batched[i]);
        }
    }

    @Test(timeout = 10000L)
    public void fullSixteenBitAxisIsHandledInOneTreeSweep() {
        int size = 65536;
        short[] pixels = new short[size];
        double[] thresholds = new double[size];
        for (int i = 0; i < size; i++) {
            pixels[i] = (short) i;
            thresholds[i] = i;
        }
        ComponentTree tree = ComponentTree.build(
                new ImagePlus("sixteen-bit", new ShortProcessor(size, 1, pixels, null)),
                SegSweepLabeller.Connectivity.TWENTY_SIX);

        int[] counts = tree.objectCountsAtThresholds(thresholds,
                ComponentTreeQuery.builder().build());

        assertEquals(1, counts[0]);
        assertEquals(1, counts[size - 2]);
        assertEquals(0, counts[size - 1]);
    }
}
