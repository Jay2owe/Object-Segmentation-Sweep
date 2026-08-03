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

import static org.junit.Assert.assertEquals;

public class IouStabilityBoundaryTest {

    @Test
    public void extendingDisplayedAxisDoesNotMoveUnderlyingStableValue() {
        List<Integer> sevenValues = Arrays.asList(
                Integer.valueOf(0), Integer.valueOf(1), Integer.valueOf(2),
                Integer.valueOf(3), Integer.valueOf(4), Integer.valueOf(5),
                Integer.valueOf(6));
        List<IouStability.IouSource> sevenSources = sources(
                ids(10),
                ids(1, 2, 3),
                ids(1, 2, 3, 4),
                ids(1, 2, 3, 4, 5),
                ids(1, 2, 3, 4),
                ids(1, 2, 3),
                ids(11));

        List<Integer> nineValues = Arrays.asList(
                Integer.valueOf(-1), Integer.valueOf(0), Integer.valueOf(1),
                Integer.valueOf(2), Integer.valueOf(3), Integer.valueOf(4),
                Integer.valueOf(5), Integer.valueOf(6), Integer.valueOf(7));
        List<IouStability.IouSource> nineSources = sources(
                ids(100),
                ids(10),
                ids(1, 2, 3),
                ids(1, 2, 3, 4),
                ids(1, 2, 3, 4, 5),
                ids(1, 2, 3, 4),
                ids(1, 2, 3),
                ids(11),
                ids(101));

        StabilityOutcome seven = IouStability.score(TestCombos.oneAxis(sevenValues), sevenSources);
        StabilityOutcome nine = IouStability.score(TestCombos.oneAxis(nineValues), nineSources);

        assertEquals(StabilityOutcome.Kind.STABLE_AT, seven.kind());
        assertEquals(StabilityOutcome.Kind.STABLE_AT, nine.kind());
        assertEquals(Integer.valueOf(3), sevenValues.get(seven.index()));
        assertEquals(Integer.valueOf(3), nineValues.get(nine.index()));
    }

    private static List<Integer> ids(int... ids) {
        return TestCombos.ids(ids);
    }

    @SafeVarargs
    private static List<IouStability.IouSource> sources(List<Integer>... ids) {
        return TestCombos.sources(ids);
    }
}
