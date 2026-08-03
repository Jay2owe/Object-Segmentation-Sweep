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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PickResultTest {

    @Test
    public void disagreementKeepsBothOutcomesPopulated() {
        KneeOutcome knee = KneeOutcome.kneeAt(2, 20.0d, 0.0d, 40.0d, 10.0d, "knee");
        StabilityOutcome stability = StabilityOutcome.stableAt(
                3, 0.91d, 2,
                new boolean[] { false, true, true, true },
                new double[] { Double.NaN, 0.7d, 0.8d, 0.91d },
                "stable");

        PickResult result = new PickResult(knee, stability, TestCombos.provenance());

        assertFalse(result.criteriaAgree());
        assertSame(knee, result.knee());
        assertSame(stability, result.stability());
    }

    @Test
    public void matchingIndexesAgree() {
        KneeOutcome knee = KneeOutcome.kneeAt(2, 20.0d, 0.0d, 40.0d, 10.0d, "knee");
        StabilityOutcome stability = StabilityOutcome.stableAt(
                2, 0.91d, 1,
                new boolean[] { false, false, true, false },
                new double[] { Double.NaN, Double.NaN, 0.91d, Double.NaN },
                "stable");

        assertTrue(new PickResult(knee, stability, TestCombos.provenance()).criteriaAgree());
    }
}
