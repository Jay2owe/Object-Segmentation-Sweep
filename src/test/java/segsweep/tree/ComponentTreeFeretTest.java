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

import static org.junit.Assert.assertEquals;

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
}
