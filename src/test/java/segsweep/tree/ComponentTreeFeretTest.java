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
import segsweep.SegSweepLabellerFixtures;
import segsweep.SweepRefusedException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ComponentTreeFeretTest {
    @Test
    public void exactFeretRunsOnlyAfterCheaperPredicatesPruneCandidates() {
        ImagePlus image = SegSweepLabellerFixtures.emptyStack(8, 1, 1);
        SegSweepLabellerFixtures.setVoxel(image, 0, 0, 0, 20);
        SegSweepLabellerFixtures.setVoxel(image, 1, 0, 0, 20);
        SegSweepLabellerFixtures.setVoxel(image, 5, 0, 0, 20);
        SegSweepLabellerFixtures.setVoxel(image, 6, 0, 0, 20);
        SegSweepLabellerFixtures.setVoxel(image, 7, 0, 0, 20);
        ComponentTree tree = ComponentTree.build(image, SegSweepLabeller.Connectivity.SIX);

        ComponentTreeResult result = tree.query(ComponentTreeQuery.builder()
                .threshold(10)
                .minSize(3)
                .predicate(MorphologyAttribute.FERET_DIAMETER_MAX, ">=", 2.0)
                .build());

        assertEquals(1, result.objectCount());
        assertEquals(1, tree.feretComputationCount());
    }

    @Test
    public void exactFeretDoesNotRunWhenCheapPredicatesRejectEverything() {
        ImagePlus image = SegSweepLabellerFixtures.points(6, 1, 1,
                new int[][] { { 0, 0, 0 }, { 5, 0, 0 } });
        ComponentTree tree = ComponentTree.build(image, SegSweepLabeller.Connectivity.SIX);

        ComponentTreeResult result = tree.query(ComponentTreeQuery.builder()
                .threshold(10)
                .minSize(2)
                .predicate(MorphologyAttribute.FERET_DIAMETER_MAX, ">=", 1.0)
                .build());

        assertEquals(ComponentTreeResult.Status.EMPTY, result.status());
        assertEquals(0, tree.feretComputationCount());
    }

    @Test
    public void boundingBoxRejectsImpossibleFeretMinimumWithoutExactWork() {
        ImagePlus image = SegSweepLabellerFixtures.emptyStack(3, 1, 1);
        for (int x = 0; x < 3; x++) {
            SegSweepLabellerFixtures.setVoxel(image, x, 0, 0, 20);
        }
        ComponentTree tree = ComponentTree.build(image, SegSweepLabeller.Connectivity.SIX);

        ComponentTreeResult result = tree.query(ComponentTreeQuery.builder()
                .threshold(10)
                .predicate(MorphologyAttribute.FERET_DIAMETER_MAX, ">=", 3.0)
                .build());

        assertEquals(ComponentTreeResult.Status.EMPTY, result.status());
        assertEquals(0, tree.feretComputationCount());
    }

    @Test
    public void concurrentWorkersAgreeOnOneCachedFeretValue() throws Exception {
        ImagePlus image = SegSweepLabellerFixtures.emptyStack(40, 40, 1);
        for (int i = 0; i < 40; i++) {
            SegSweepLabellerFixtures.setVoxel(image, i, i, 0, 20);
            SegSweepLabellerFixtures.setVoxel(image, i, 39 - i, 0, 20);
        }
        final ComponentTree tree = ComponentTree.build(image,
                SegSweepLabeller.Connectivity.TWENTY_SIX);
        final ComponentTree.NodeData node = tree.nodeData(tree.nodes().size() - 1);
        final double[] seen = new double[8];
        Thread[] workers = new Thread[seen.length];
        final java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        for (int t = 0; t < workers.length; t++) {
            final int slot = t;
            workers[t] = new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        return;
                    }
                    seen[slot] = tree.feretDiameterMax(node);
                }
            });
            workers[t].start();
        }
        start.countDown();
        for (Thread worker : workers) worker.join(10000L);

        double expected = Math.sqrt(2.0 * 39.0 * 39.0);
        for (double value : seen) assertEquals(expected, value, 0.0);
        assertEquals(1, tree.feretComputationCount());
    }

    @Test
    public void exactFeretRefusesObjectsAboveTheBoundedLimit() {
        ImagePlus image = SegSweepLabellerFixtures.emptyStack(65, 65, 1);
        for (int y = 0; y < 65; y++) {
            for (int x = 0; x < 65; x++) {
                SegSweepLabellerFixtures.setVoxel(image, x, y, 0, 20);
            }
        }
        ComponentTree tree = ComponentTree.build(image, SegSweepLabeller.Connectivity.SIX);

        try {
            tree.query(ComponentTreeQuery.builder()
                    .threshold(10)
                    .predicate(MorphologyAttribute.FERET_DIAMETER_MAX, ">=", 1.0)
                    .build());
        } catch (SweepRefusedException e) {
            assertTrue(e.getMessage().contains("4096"));
            assertTrue(e.getMessage().contains("crop"));
            return;
        }
        throw new AssertionError("Expected bounded exact-Feret refusal.");
    }
}
