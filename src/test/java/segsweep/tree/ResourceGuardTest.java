/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.tree;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ResourceGuardTest {
    @Test
    public void refusesTreeMemoryAboveLimit() {
        ResourceGuard.Decision decision = ResourceGuard.checkTreeMemory(2048, 2048, 40,
                128L * 1024L * 1024L);

        assertEquals(ResourceGuard.Decision.Status.REFUSED, decision.status());
        assertTrue(decision.reason().contains("component-tree memory"));
        assertTrue(decision.estimate().oneLazyLabelMapBytes() > 0);
    }

    @Test
    public void permitsSmallCroppedStackAndIncludesLazyLabelMap() {
        ResourceGuard.Decision decision = ResourceGuard.checkTreeMemory(16, 16, 4,
                16L * 1024L * 1024L);

        assertEquals(ResourceGuard.Decision.Status.PERMITTED, decision.status());
        assertEquals(16L * 16L * 4L * 2L, decision.estimate().oneLazyLabelMapBytes());
        assertTrue(decision.estimate().totalBytes() > decision.estimate().oneLazyLabelMapBytes());
    }
}
