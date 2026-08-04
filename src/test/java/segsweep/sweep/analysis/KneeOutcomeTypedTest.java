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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertArrayEquals;

public class KneeOutcomeTypedTest {

    @Test
    public void typedRefusalsAreDistinctFromKneeAt() {
        assertKind(KneeOutcome.Kind.ALL_PLATEAU,
                KneeDetector.detect(
                        new double[] { 0, 1, 2, 3 },
                        new double[] { 9, 9, 9, 9 },
                        0, 3, 1));

        assertKind(KneeOutcome.Kind.TOO_FEW_POINTS,
                KneeDetector.detect(
                        new double[] { 0, 1, 2 },
                        new double[] { 9, 5, 1 },
                        0, 2, 1));

        assertKind(KneeOutcome.Kind.NO_BEND,
                KneeDetector.detect(
                        new double[] { 0, 1, 2, 3, 4, 5, 6 },
                        new double[] { 100, 85, 70, 55, 40, 25, 10 },
                        0, 6, 1));

        assertKind(KneeOutcome.Kind.DEGENERATE_RANGE,
                KneeDetector.detect(
                        new double[] { 1, 1, 1, 1 },
                        new double[] { 100, 50, 20, 10 },
                        1, 1, 0));
    }

    @Test
    public void clearElbowReturnsParameterUnits() {
        KneeOutcome outcome = KneeDetector.detect(
                new double[] { 0, 1, 2, 3, 4, 5, 6 },
                new double[] { 100, 95, 80, 30, 10, 8, 7 },
                1, 5, 1);

        assertEquals(KneeOutcome.Kind.KNEE_AT, outcome.kind());
        assertEquals(3, outcome.index());
        assertEquals(3.0d, outcome.parameterValue(), 0.000001d);
        assertEquals(1.0d, outcome.rangeMin(), 0.000001d);
        assertEquals(5.0d, outcome.rangeMax(), 0.000001d);
        assertArrayEquals(new double[] { 0, 1, 2, 3, 4, 5, 6 },
                outcome.sampledValues(), 0.0d);
    }

    @Test
    public void comparabilityIncludesExactIrregularSamples() {
        KneeOutcome first = KneeOutcome.of(KneeOutcome.Kind.ALL_PLATEAU,
                1, 50, Double.NaN, new double[] { 1, 2, 10, 50 }, "flat");
        KneeOutcome second = KneeOutcome.of(KneeOutcome.Kind.ALL_PLATEAU,
                1, 50, Double.NaN, new double[] { 1, 4, 20, 50 }, "flat");

        assertFalse(first.comparable(second));
    }

    private static void assertKind(KneeOutcome.Kind expected, KneeOutcome outcome) {
        assertEquals(expected, outcome.kind());
        assertFalse(outcome.hasKnee());
    }
}
