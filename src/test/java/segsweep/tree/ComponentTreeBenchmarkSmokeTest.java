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
import org.junit.Test;
import segsweep.SegSweepLabeller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Cost-shape checks that need no timing: one tree serves many queries without
 * materialising label maps. Timings live in {@code segsweep.bench.SweepBenchmark}.
 */
public class ComponentTreeBenchmarkSmokeTest {
    @Test
    public void repeatedQueriesDoNotMaterialiseLabelMaps() {
        ImagePlus image = ComponentTreeOracleFixtures.equivalenceStack();
        ComponentTree tree = ComponentTree.build(image, SegSweepLabeller.Connectivity.TWENTY_SIX);

        ComponentTreeResult last = null;
        int queries = 0;
        for (int threshold = 0; threshold <= 80; threshold += 10) {
            last = tree.query(ComponentTreeQuery.builder()
                    .threshold(threshold)
                    .minSize(1)
                    .maxSize(Integer.MAX_VALUE)
                    .build());
            last.objectCount();
            assertEquals(0, last.labelMap().materializationCount());
            queries++;
        }
        assertEquals(9, queries);
        assertTrue(last != null);
    }

    @Test
    public void aSliceCountsAsOneMaterialisationAndLeavesTheTreeIntact() {
        ImagePlus image = ComponentTreeOracleFixtures.equivalenceStack();
        ComponentTree tree = ComponentTree.build(image, SegSweepLabeller.Connectivity.SIX);
        long storedBefore = tree.storedVoxelMembershipCount();
        ComponentTreeResult result = tree.query(ComponentTreeQuery.builder()
                .threshold(0).minSize(1).build());

        result.labelMap().getSlice(1);

        assertEquals(1, result.labelMap().materializationCount());
        assertEquals(storedBefore, tree.storedVoxelMembershipCount());
    }
}
