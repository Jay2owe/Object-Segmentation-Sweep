/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * A single bin could hold more than 2^31 - 1 voxels in a huge stack; the
 * normal counting path used a plain {@code int++} and wrapped negative.
 */
public class StackHistogramOverflowTest {
    @Test
    public void binCountSaturatesInsteadOfWrapping() {
        int[] counts = { Integer.MAX_VALUE - 1, 0 };
        StackHistogram.increment(counts, 0);
        assertEquals(Integer.MAX_VALUE, counts[0]);
        StackHistogram.increment(counts, 0);
        assertEquals(Integer.MAX_VALUE, counts[0]);
        StackHistogram.increment(counts, 1);
        assertEquals(1, counts[1]);
    }
}
