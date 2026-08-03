/*
 * Copyright (c) 2026 Jamie Malcolm
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.tree;

import ij.ImagePlus;
import ij.process.FloatProcessor;
import org.junit.Test;
import segsweep.SegSweepLabeller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ComponentTreeLinearStorageTest {
    @Test
    public void distinctFloatGradientStoresEachVoxelOnlyOnce() {
        int size = 2000;
        float[] pixels = new float[size];
        for (int i = 0; i < size; i++) pixels[i] = i + 1;
        ImagePlus image = new ImagePlus("gradient", new FloatProcessor(size, 1, pixels));

        ComponentTree tree = ComponentTree.build(image, SegSweepLabeller.Connectivity.SIX);

        assertEquals(size, tree.storedVoxelMembershipCount());
        assertTrue(tree.nodes().size() <= size);
        assertEquals(1, tree.query(ComponentTreeQuery.builder().threshold(0.5d).build()).objectCount());
    }
}
