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

public class ComponentTreeAttributeTest {
    @Test
    public void pinsCoreAttributesOnSmallCube() {
        ImagePlus image = SegSweepLabellerFixtures.emptyStack(3, 3, 3);
        for (int z = 0; z < 2; z++) {
            for (int y = 0; y < 2; y++) {
                for (int x = 0; x < 2; x++) {
                    SegSweepLabellerFixtures.setVoxel(image, x, y, z, 20);
                }
            }
        }
        SegSweepLabellerFixtures.setVoxel(image, 0, 0, 0, 30);

        ComponentNode node = ComponentTree.build(image, SegSweepLabeller.Connectivity.SIX)
                .query(ComponentTreeQuery.builder().threshold(10).build())
                .selectedNodes().get(0);

        assertEquals(8, node.voxelCount());
        assertEquals(170.0, node.intensitySum(), 0.0);
        assertEquals(21.25, node.meanIntensity(), 0.0);
        assertEquals(30.0, node.maxIntensity(), 0.0);
        assertEquals(0, node.minX());
        assertEquals(0, node.minY());
        assertEquals(0, node.minZ());
        assertEquals(1, node.maxX());
        assertEquals(1, node.maxY());
        assertEquals(1, node.maxZ());
        assertEquals(24.0, node.surfaceArea(), 0.0);
        assertEquals(expectedSphericity(8.0, 24.0), node.sphericity(), 1.0e-12);
        assertEquals(expectedCompactness(8.0, 24.0), node.compactness(), 1.0e-12);
        assertEquals(1.0, node.elongation(), 1.0e-12);
    }

    @Test
    public void pinsSurfaceForLineObject() {
        ImagePlus image = SegSweepLabellerFixtures.emptyStack(4, 1, 1);
        SegSweepLabellerFixtures.setVoxel(image, 0, 0, 0, 20);
        SegSweepLabellerFixtures.setVoxel(image, 1, 0, 0, 20);
        SegSweepLabellerFixtures.setVoxel(image, 2, 0, 0, 20);

        ComponentNode node = ComponentTree.build(image, SegSweepLabeller.Connectivity.SIX)
                .query(ComponentTreeQuery.builder().threshold(10).build())
                .selectedNodes().get(0);

        assertEquals(3, node.voxelCount());
        assertEquals(14.0, node.surfaceArea(), 0.0);
    }

    private static double expectedSphericity(double volume, double surface) {
        return Math.pow(Math.PI, 1.0 / 3.0) * Math.pow(6.0 * volume, 2.0 / 3.0) / surface;
    }

    private static double expectedCompactness(double volume, double surface) {
        return (36.0 * Math.PI * volume * volume) / (surface * surface * surface);
    }
}
