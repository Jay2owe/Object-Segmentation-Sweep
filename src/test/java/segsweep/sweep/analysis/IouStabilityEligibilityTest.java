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
import java.util.List;
import java.util.function.LongSupplier;

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
    public void numericAdjacencyIsIndependentOfExplicitValueOrder() {
        List<segsweep.sweep.ParameterCombo> ascending = TestCombos.oneAxis(
                Arrays.asList(Integer.valueOf(10), Integer.valueOf(20), Integer.valueOf(30)));
        List<segsweep.sweep.ParameterCombo> permuted = TestCombos.oneAxis(
                Arrays.asList(Integer.valueOf(10), Integer.valueOf(30), Integer.valueOf(20)));

        StabilityOutcome first = IouStability.score(ascending,
                TestCombos.sources(TestCombos.ids(1), TestCombos.ids(1), TestCombos.ids(1)));
        StabilityOutcome second = IouStability.score(permuted,
                TestCombos.sources(TestCombos.ids(1), TestCombos.ids(1), TestCombos.ids(1)));

        assertEquals(Integer.valueOf(20), ascending.get(first.index()).get(
                segsweep.sweep.ParameterId.THRESHOLD));
        assertEquals(Integer.valueOf(20), permuted.get(second.index()).get(
                segsweep.sweep.ParameterId.THRESHOLD));
    }

    @Test
    public void countRatioUsesFullEligibleNeighbours() {
        assertEquals(1.0d, IouStability.meanNeighbourCountRatio(
                TestCombos.oneAxis(Arrays.asList(
                        Integer.valueOf(0), Integer.valueOf(1), Integer.valueOf(2))),
                Arrays.asList(Integer.valueOf(7), Integer.valueOf(7), Integer.valueOf(7)),
                1), 0.000001d);
    }

    @Test
    public void countGateFailureIsNotReportedAsStabilityEligible() {
        StabilityOutcome outcome = IouStability.score(
                TestCombos.oneAxis(Arrays.asList(
                        Integer.valueOf(0), Integer.valueOf(1), Integer.valueOf(2))),
                TestCombos.sources(
                        TestCombos.ids(1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
                        TestCombos.ids(1),
                        TestCombos.ids(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)));

        assertEquals(StabilityOutcome.Kind.NO_ELIGIBLE_COMBINATIONS, outcome.kind());
        assertEquals(0, outcome.eligibleCount());
        assertFalse(outcome.isEligible(1));
        assertTrue(outcome.explanation().contains("object-count-ratio gates"));
    }

    @Test
    public void twoAxisStabilityUsesOnlyTheTwoNeighboursAlongEachAxis() {
        StabilityOutcome outcome = IouStability.score(
                TestCombos.twoAxis(3, 3),
                TestCombos.sources(
                        TestCombos.ids(2), TestCombos.ids(1), TestCombos.ids(2),
                        TestCombos.ids(1), TestCombos.ids(1), TestCombos.ids(1),
                        TestCombos.ids(2), TestCombos.ids(1), TestCombos.ids(2)));

        assertEquals(StabilityOutcome.Kind.STABLE_AT, outcome.kind());
        assertTrue(outcome.isEligible(4));
        assertEquals(1.0d, outcome.meanNeighbourIou(4), 0.000001d);
    }

    @Test
    public void elapsedBudgetReturnsTypedAbortedOutcome() {
        final long[] now = { -2000000L };
        StabilityOutcome outcome = IouStability.score(
                TestCombos.oneAxis(Arrays.asList(
                        Integer.valueOf(0), Integer.valueOf(1), Integer.valueOf(2))),
                TestCombos.sources(TestCombos.ids(1), TestCombos.ids(1), TestCombos.ids(1)),
                null, 1L, new LongSupplier() {
                    @Override public long getAsLong() {
                        now[0] += 2000000L;
                        return now[0];
                    }
                });

        assertEquals(StabilityOutcome.Kind.ABORTED, outcome.kind());
        assertTrue(outcome.explanation().contains("1 ms budget"));
    }
}
