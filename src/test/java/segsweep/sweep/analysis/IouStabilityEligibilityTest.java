/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.sweep.analysis;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IouStabilityEligibilityTest {

    @Test
    public void twoValueSweepHasNoEligibleInteriorPoint() {
        StabilityOutcome outcome = IouStability.score(
                TestCombos.oneAxis(Arrays.asList(Integer.valueOf(0), Integer.valueOf(1))),
                TestCombos.sources(TestCombos.ids(1), TestCombos.ids(1)));

        assertEquals(StabilityOutcome.Kind.NO_ELIGIBLE_COMBINATIONS, outcome.kind());
        assertEquals(0, outcome.eligibleCount());
        assertFalse(outcome.isEligible(0));
        assertFalse(outcome.isEligible(1));
    }

    @Test
    public void threeValueSweepHasOneEligibleInteriorPoint() {
        StabilityOutcome outcome = IouStability.score(
                TestCombos.oneAxis(Arrays.asList(
                        Integer.valueOf(0), Integer.valueOf(1), Integer.valueOf(2))),
                TestCombos.sources(TestCombos.ids(1), TestCombos.ids(1), TestCombos.ids(1)));

        assertEquals(StabilityOutcome.Kind.STABLE_AT, outcome.kind());
        assertEquals(1, outcome.eligibleCount());
        assertFalse(outcome.isEligible(0));
        assertTrue(outcome.isEligible(1));
        assertFalse(outcome.isEligible(2));
    }

    @Test
    public void countRatioUsesFullEligibleNeighbours() {
        assertEquals(1.0d, IouStability.meanNeighbourCountRatio(
                TestCombos.oneAxis(Arrays.asList(
                        Integer.valueOf(0), Integer.valueOf(1), Integer.valueOf(2))),
                Arrays.asList(Integer.valueOf(7), Integer.valueOf(7), Integer.valueOf(7)),
                1), 0.000001d);
    }
}
