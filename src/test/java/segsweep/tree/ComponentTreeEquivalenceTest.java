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
import segsweep.LabelResult;
import segsweep.SegSweepLabeller;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ComponentTreeEquivalenceTest {
    @Test
    public void treeCountsMatchPlainLabellerForSampledBaseCombinations() {
        ImagePlus image = ComponentTreeOracleFixtures.equivalenceStack();
        List<QueryCase> cases = sampledBaseCases();
        int checked = 0;

        for (int c = 0; c < cases.size(); c++) {
            QueryCase queryCase = cases.get(c);
            LabelResult oracle = SegSweepLabeller.label(image,
                    queryCase.threshold, queryCase.minSize, queryCase.maxSize, queryCase.connectivity);
            ComponentTreeResult fast = ComponentTree.build(image, queryCase.connectivity)
                    .query(queryCase.toTreeQuery());

            assertEquals("count mismatch for " + queryCase, oracle.objectCount(), fast.objectCount());
            checked++;
        }

        assertTrue("Stage 04 requires at least 30 sampled base combinations; checked " + checked,
                checked >= 30);
    }

    @Test
    public void lazyLabelMapsMatchOracleVoxelSetsForRepresentativeCombinations() {
        ImagePlus image = ComponentTreeOracleFixtures.equivalenceStack();
        QueryCase[] cases = new QueryCase[] {
                new QueryCase(10, 0, Integer.MAX_VALUE, SegSweepLabeller.Connectivity.SIX),
                new QueryCase(10, 0, Integer.MAX_VALUE, SegSweepLabeller.Connectivity.TWENTY_SIX),
                new QueryCase(24, 2, 3, SegSweepLabeller.Connectivity.SIX),
                new QueryCase(34, 1, 8, SegSweepLabeller.Connectivity.TWENTY_SIX),
                new QueryCase(49, 4, 8, SegSweepLabeller.Connectivity.SIX),
                new QueryCase(69, 1, 1, SegSweepLabeller.Connectivity.TWENTY_SIX)
        };

        for (int i = 0; i < cases.length; i++) {
            QueryCase queryCase = cases[i];
            LabelResult oracle = SegSweepLabeller.label(image,
                    queryCase.threshold, queryCase.minSize, queryCase.maxSize, queryCase.connectivity);
            ComponentTreeResult fast = ComponentTree.build(image, queryCase.connectivity)
                    .query(queryCase.toTreeQuery());

            ComponentTreeOracleFixtures.assertEquivalentToOracle(image, fast, oracle);
        }
    }

    private static List<QueryCase> sampledBaseCases() {
        List<QueryCase> cases = new ArrayList<QueryCase>();
        int[] thresholds = new int[] { 0, 10, 24, 34, 49, 59, 69, 79 };
        int[][] sizes = new int[][] {
                { 0, Integer.MAX_VALUE },
                { 1, 1 },
                { 2, 3 },
                { 3, 5 },
                { 6, 100 }
        };
        SegSweepLabeller.Connectivity[] connectivities = new SegSweepLabeller.Connectivity[] {
                SegSweepLabeller.Connectivity.SIX,
                SegSweepLabeller.Connectivity.TWENTY_SIX
        };
        for (int t = 0; t < thresholds.length; t++) {
            for (int s = 0; s < sizes.length; s++) {
                for (int c = 0; c < connectivities.length; c++) {
                    cases.add(new QueryCase(thresholds[t], sizes[s][0], sizes[s][1], connectivities[c]));
                }
            }
        }
        return cases;
    }

    private static final class QueryCase {
        final int threshold;
        final int minSize;
        final int maxSize;
        final SegSweepLabeller.Connectivity connectivity;

        QueryCase(int threshold,
                  int minSize,
                  int maxSize,
                  SegSweepLabeller.Connectivity connectivity) {
            this.threshold = threshold;
            this.minSize = minSize;
            this.maxSize = maxSize;
            this.connectivity = connectivity;
        }

        ComponentTreeQuery toTreeQuery() {
            return ComponentTreeQuery.builder()
                    .threshold(threshold)
                    .minSize(minSize)
                    .maxSize(maxSize)
                    .build();
        }

        @Override public String toString() {
            return "threshold=" + threshold + ", minSize=" + minSize
                    + ", maxSize=" + maxSize + ", connectivity=" + connectivity;
        }
    }
}
