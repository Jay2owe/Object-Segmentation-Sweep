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

import java.util.concurrent.CancellationException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ComponentTreeConstructionTest {
    @Test
    public void emptyImageBuildsButQueryReturnsTypedEmpty() {
        ComponentTree tree = ComponentTree.build(SegSweepLabellerFixtures.emptyStack(3, 3, 2),
                SegSweepLabeller.Connectivity.SIX);

        ComponentTreeResult result = tree.query(query(0));

        assertEquals(ComponentTreeResult.Status.EMPTY, result.status());
        assertEquals(0, result.objectCount());
        assertTrue(result.reason().contains("No component-tree nodes"));
    }

    @Test
    public void singleObjectIsOneNodeAtThresholdCut() {
        ImagePlus image = SegSweepLabellerFixtures.points(4, 4, 1,
                new int[][] { { 1, 1, 0 }, { 2, 1, 0 }, { 2, 2, 0 } });
        ComponentTree tree = ComponentTree.build(image, SegSweepLabeller.Connectivity.SIX);

        ComponentTreeResult result = tree.query(query(SegSweepLabellerFixtures.THRESHOLD));

        assertEquals(ComponentTreeResult.Status.OK, result.status());
        assertEquals(1, result.objectCount());
        assertEquals(3, result.selectedNodes().get(0).voxelCount());
    }

    @Test
    public void diagonalTouchingFollowsRequestedConnectivity() {
        ImagePlus image = SegSweepLabellerFixtures.points(4, 4, 1,
                new int[][] { { 1, 1, 0 }, { 2, 2, 0 } });

        ComponentTreeResult six = ComponentTree.build(image, SegSweepLabeller.Connectivity.SIX)
                .query(query(SegSweepLabellerFixtures.THRESHOLD));
        ComponentTreeResult twentySix = ComponentTree.build(image, SegSweepLabeller.Connectivity.TWENTY_SIX)
                .query(query(SegSweepLabellerFixtures.THRESHOLD));

        assertEquals(2, six.objectCount());
        assertEquals(1, twentySix.objectCount());
    }

    @Test
    public void componentsBornAtDifferentThresholdsMergeThroughLowerBridge() {
        ImagePlus image = SegSweepLabellerFixtures.emptyStack(5, 1, 1);
        SegSweepLabellerFixtures.setVoxel(image, 1, 0, 0, 30);
        SegSweepLabellerFixtures.setVoxel(image, 2, 0, 0, 20);
        SegSweepLabellerFixtures.setVoxel(image, 3, 0, 0, 30);
        ComponentTree tree = ComponentTree.build(image, SegSweepLabeller.Connectivity.SIX);

        ComponentTreeResult highCut = tree.query(query(25));
        ComponentTreeResult lowCut = tree.query(query(10));

        assertEquals(2, highCut.objectCount());
        assertEquals(1, lowCut.objectCount());
        assertEquals(3, lowCut.selectedNodes().get(0).voxelCount());
        assertTrue(highCut.selectedNodes().get(0).parentId() >= 0);
    }

    @Test
    public void stackFaceObjectIsNotClipped() {
        ImagePlus image = SegSweepLabellerFixtures.emptyStack(3, 3, 3);
        for (int z = 0; z < 3; z++) {
            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 3; x++) {
                    SegSweepLabellerFixtures.setVoxel(image, x, y, z, SegSweepLabellerFixtures.FOREGROUND);
                }
            }
        }

        ComponentTreeResult result = ComponentTree.build(image, SegSweepLabeller.Connectivity.SIX)
                .query(query(SegSweepLabellerFixtures.THRESHOLD));

        assertEquals(1, result.objectCount());
        assertEquals(27, result.selectedNodes().get(0).voxelCount());
    }

    @Test
    public void nullConnectivityUsesOracleDefault() {
        ImagePlus image = SegSweepLabellerFixtures.points(4, 4, 1,
                new int[][] { { 1, 1, 0 }, { 2, 2, 0 } });
        ComponentTree tree = ComponentTree.build(image, null);

        assertEquals(SegSweepLabeller.DEFAULT_CONNECTIVITY, tree.connectivity());
        assertEquals(1, tree.query(query(SegSweepLabellerFixtures.THRESHOLD)).objectCount());
    }

    @Test(expected = CancellationException.class)
    public void queryHonoursCancellationSupplier() {
        ComponentTree tree = ComponentTree.build(
                SegSweepLabellerFixtures.points(4, 4, 1,
                        new int[][] { { 1, 1, 0 } }),
                SegSweepLabeller.Connectivity.SIX);

        tree.query(query(SegSweepLabellerFixtures.THRESHOLD), () -> true);
    }

    private static ComponentTreeQuery query(int threshold) {
        return ComponentTreeQuery.builder()
                .threshold(threshold)
                .minSize(0)
                .maxSize(Integer.MAX_VALUE)
                .build();
    }
}
