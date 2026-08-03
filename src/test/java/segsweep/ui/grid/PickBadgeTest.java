/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.ui.grid;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PickBadgeTest {
    @Test
    public void kindIsRetained() {
        assertEquals(PickBadge.Kind.BOTH,
                new PickBadge(PickBadge.Kind.BOTH).kind());
    }
}
