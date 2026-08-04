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

public class KneeWholeAxisTest {

    @Test
    public void displayWindowDoesNotChangeFullAxisKneeCurve() {
        double[] fullAxis = { 0, 1, 2, 3, 4, 5, 6 };
        double[] counts = { 100, 95, 80, 30, 10, 8, 7 };

        KneeOutcome narrowDisplay = KneeDetector.detect(fullAxis, counts, 0, 6, 1);
        KneeOutcome wideDisplay = KneeDetector.detect(fullAxis, counts, 0, 6, 1);

        assertEquals(KneeOutcome.Kind.KNEE_AT, narrowDisplay.kind());
        assertEquals(KneeOutcome.Kind.KNEE_AT, wideDisplay.kind());
        assertEquals(narrowDisplay.index(), wideDisplay.index());
        assertEquals(narrowDisplay.parameterValue(), wideDisplay.parameterValue(), 0.000001d);
        assertEquals(0.0d, narrowDisplay.rangeMin(), 0.000001d);
        assertEquals(0.0d, wideDisplay.rangeMin(), 0.000001d);
    }
}
