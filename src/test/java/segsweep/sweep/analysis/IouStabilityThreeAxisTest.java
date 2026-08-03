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

public class IouStabilityThreeAxisTest {

    @Test
    public void threeVaryingAxesReturnTypedRefusal() {
        StabilityOutcome outcome = IouStability.score(
                TestCombos.threeAxis(2, 2, 2),
                TestCombos.repeatedSources(8, TestCombos.ids(1)));

        assertEquals(StabilityOutcome.Kind.TOO_MANY_AXES, outcome.kind());
        assertEquals(-1, outcome.index());
    }
}
