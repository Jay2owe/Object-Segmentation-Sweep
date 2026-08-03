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

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ComponentTreeMorphologyEquivalenceTest {
    @Test
    public void everyImplementedPredicateMatchesDirectLabellerOracle() {
        ImagePlus image = ComponentTreeOracleFixtures.morphologyStack();
        int checked = 0;
        for (int i = 0; i < MorphologyAttribute.values().length; i++) {
            MorphologyAttribute attribute = MorphologyAttribute.values()[i];
            double[] values = ComponentTreeOracleFixtures.finiteOracleValues(image, 10,
                    attribute, SegSweepLabeller.Connectivity.SIX);
            assertTrue("Fixture must produce finite values for " + attribute, values.length > 0);

            double min = values[0];
            double max = values[values.length - 1];
            assertPredicateEquivalent(image, attribute, "<=", max);
            assertPredicateEquivalent(image, attribute, ">=", min);
            assertPredicateEquivalent(image, attribute, ">", max + Math.max(1.0, Math.abs(max) * 0.25));
            checked += 3;
        }
        assertEquals(MorphologyAttribute.values().length * 3, checked);
    }

    @Test
    public void nonIncreasingAttributesUseDirectCutObjectRule() {
        ImagePlus image = ComponentTreeOracleFixtures.nonIncreasingAttributeStack();
        ComponentTree tree = ComponentTree.build(image, SegSweepLabeller.Connectivity.SIX);
        ComponentTreeResult highCut = tree.query(ComponentTreeQuery.builder()
                .threshold(40)
                .minSize(1)
                .build());
        ComponentTreeResult lowCut = tree.query(ComponentTreeQuery.builder()
                .threshold(10)
                .minSize(1)
                .build());

        double highSphericity = highCut.selectedNodes().get(0).sphericity();
        double lowSphericity = lowCut.selectedNodes().get(0).sphericity();
        assertTrue("The fixture must make the lower-threshold object less spherical.",
                highSphericity > lowSphericity);

        MorphologyPredicate directPredicate = MorphologyPredicate.of(MorphologyAttribute.SPHERICITY,
                ">=", (highSphericity + lowSphericity) / 2.0);

        assertMorphologyEquivalent(image, 40, directPredicate);
        assertMorphologyEquivalent(image, 10, directPredicate);
        assertEquals(1, tree.query(ComponentTreeQuery.builder()
                .threshold(40)
                .minSize(1)
                .predicate(directPredicate)
                .build()).objectCount());
        assertEquals(0, tree.query(ComponentTreeQuery.builder()
                .threshold(10)
                .minSize(1)
                .predicate(directPredicate)
                .build()).objectCount());
    }

    private static void assertPredicateEquivalent(ImagePlus image,
                                                  MorphologyAttribute attribute,
                                                  String operator,
                                                  double value) {
        assertMorphologyEquivalent(image, 10, MorphologyPredicate.of(attribute, operator, value));
    }

    private static void assertMorphologyEquivalent(ImagePlus image,
                                                   int threshold,
                                                   MorphologyPredicate predicate) {
        ComponentTreeResult result = ComponentTree.build(image, SegSweepLabeller.Connectivity.SIX)
                .query(ComponentTreeQuery.builder()
                        .threshold(threshold)
                        .minSize(1)
                        .maxSize(Integer.MAX_VALUE)
                        .predicate(predicate)
                        .build());
        List<List<Integer>> expected = ComponentTreeOracleFixtures.oracleSurvivingVoxelSets(image,
                threshold, 1, Integer.MAX_VALUE, SegSweepLabeller.Connectivity.SIX, predicate);

        assertEquals("predicate " + predicate.format(),
                expected, ComponentTreeOracleFixtures.treeVoxelSets(result));
    }
}
