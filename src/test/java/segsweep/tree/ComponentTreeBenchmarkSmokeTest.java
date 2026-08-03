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

public class ComponentTreeBenchmarkSmokeTest {
    @Test
    public void recordsBuildCostOnceAndQueryCostSeparately() {
        ImagePlus image = ComponentTreeOracleFixtures.equivalenceStack();
        long buildStarted = System.nanoTime();
        ComponentTree tree = ComponentTree.build(image, SegSweepLabeller.Connectivity.SIX);
        long buildNanos = System.nanoTime() - buildStarted;

        long queryStarted = System.nanoTime();
        int queryCount = 0;
        for (int threshold = 0; threshold <= 80; threshold += 5) {
            tree.query(ComponentTreeQuery.builder()
                    .threshold(threshold)
                    .minSize(1)
                    .maxSize(Integer.MAX_VALUE)
                    .build()).objectCount();
            queryCount++;
        }
        long queryNanos = System.nanoTime() - queryStarted;

        assertTrue(buildNanos >= 0L);
        assertTrue(queryNanos >= 0L);
        assertEquals(17, queryCount);
    }

    @Test
    public void repeatedQueriesDoNotMaterialiseLabelMaps() {
        ImagePlus image = ComponentTreeOracleFixtures.equivalenceStack();
        ComponentTree tree = ComponentTree.build(image, SegSweepLabeller.Connectivity.TWENTY_SIX);

        ComponentTreeResult last = null;
        for (int threshold = 0; threshold <= 80; threshold += 10) {
            last = tree.query(ComponentTreeQuery.builder()
                    .threshold(threshold)
                    .minSize(1)
                    .maxSize(Integer.MAX_VALUE)
                    .build());
            last.objectCount();
            assertEquals(0, last.labelMap().materializationCount());
        }
        assertTrue(last != null);
    }
}
