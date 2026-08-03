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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ComponentTreeDisplayWindowTest {
    @Test
    public void displayWindowChangesSubsetNotFullAxisCounts() {
        ImagePlus image = ComponentTreeOracleFixtures.equivalenceStack();
        int[] fullAxis = new int[] { 0, 10, 20, 30, 40, 50, 60, 70, 80 };

        ClassicalCountSpace narrow = ClassicalCountSpace.compute(image, fullAxis,
                SegSweepLabeller.Connectivity.TWENTY_SIX);
        ClassicalCountSpace wide = ClassicalCountSpace.compute(image, fullAxis,
                SegSweepLabeller.Connectivity.TWENTY_SIX);

        assertArrayEquals(narrow.fullAxisCounts(), wide.fullAxisCounts());
        assertFalse(Arrays.equals(narrow.displayedCounts(20, 60, 20),
                wide.displayedCounts(0, 80, 20)));
        assertTrue(narrow.buildCount() == 1);
        assertTrue(wide.buildCount() == 1);
        assertTrue(narrow.queryCount() == fullAxis.length);
        assertTrue(wide.queryCount() == fullAxis.length);
    }

    private static final class ClassicalCountSpace {
        private final int[] thresholds;
        private final int[] counts;
        private int buildCount;
        private int queryCount;

        private ClassicalCountSpace(int[] thresholds, int[] counts, int buildCount, int queryCount) {
            this.thresholds = Arrays.copyOf(thresholds, thresholds.length);
            this.counts = Arrays.copyOf(counts, counts.length);
            this.buildCount = buildCount;
            this.queryCount = queryCount;
        }

        static ClassicalCountSpace compute(ImagePlus image,
                                           int[] fullAxis,
                                           SegSweepLabeller.Connectivity connectivity) {
            ComponentTree tree = ComponentTree.build(image, connectivity);
            int buildCount = 1;
            int[] counts = new int[fullAxis.length];
            int queryCount = 0;
            for (int i = 0; i < fullAxis.length; i++) {
                counts[i] = tree.query(ComponentTreeQuery.builder()
                        .threshold(fullAxis[i])
                        .minSize(1)
                        .maxSize(Integer.MAX_VALUE)
                        .build()).objectCount();
                queryCount++;
            }
            return new ClassicalCountSpace(fullAxis, counts, buildCount, queryCount);
        }

        int[] fullAxisCounts() {
            return Arrays.copyOf(counts, counts.length);
        }

        int[] displayedCounts(int from, int to, int step) {
            List<Integer> displayed = new ArrayList<Integer>();
            for (int threshold = from; threshold <= to; threshold += step) {
                for (int i = 0; i < thresholds.length; i++) {
                    if (thresholds[i] == threshold) {
                        displayed.add(Integer.valueOf(counts[i]));
                    }
                }
            }
            int[] out = new int[displayed.size()];
            for (int i = 0; i < out.length; i++) {
                out[i] = displayed.get(i).intValue();
            }
            return out;
        }

        int buildCount() {
            return buildCount;
        }

        int queryCount() {
            return queryCount;
        }
    }
}
