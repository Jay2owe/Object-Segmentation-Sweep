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
import ij.process.FloatProcessor;
import org.junit.Test;
import segsweep.LabelResult;
import segsweep.SegSweepLabeller;

import static org.junit.Assert.assertEquals;

public class ComponentTreeNonFiniteTest {
    @Test
    public void mixedNonFinitePixelsDoNotSuppressFiniteComponents() {
        float[] pixels = new float[] {
                Float.NaN, 5.0f, Float.POSITIVE_INFINITY,
                0.0f, 5.0f, Float.NEGATIVE_INFINITY
        };
        ImagePlus image = new ImagePlus("mixed", new FloatProcessor(3, 2, pixels));

        LabelResult oracle = SegSweepLabeller.label(image, 1.0d, 0,
                Integer.MAX_VALUE, SegSweepLabeller.Connectivity.TWENTY_SIX);
        ComponentTreeResult tree = ComponentTree.build(image,
                SegSweepLabeller.Connectivity.TWENTY_SIX).query(
                ComponentTreeQuery.builder().threshold(1.0d).build());

        assertEquals(1, oracle.objectCount());
        assertEquals(oracle.objectCount(), tree.objectCount());
        assertEquals(2, tree.selectedNodes().get(0).voxelCount());
    }
}
