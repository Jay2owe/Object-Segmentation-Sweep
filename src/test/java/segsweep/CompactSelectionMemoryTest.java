/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import org.junit.Test;
import segsweep.sweep.ParameterId;

import static org.junit.Assert.assertEquals;

public class CompactSelectionMemoryTest {

    @Test(timeout = 30000L)
    public void fiveThousandHighComponentResultsRetainCompactSelections() {
        ByteProcessor pixels = new ByteProcessor(100, 100);
        for (int y = 0; y < pixels.getHeight(); y++) {
            for (int x = 0; x < pixels.getWidth(); x++) {
                if (((x + y) & 1) == 0) pixels.set(x, y, 1);
            }
        }

        SegSweepResult result = SegSweep.run(SegSweepParameters.builder()
                .image(new ImagePlus("compact-selection-checkerboard", pixels))
                .axis(ParameterId.THRESHOLD, 0, 0, 1)
                .axis(ParameterId.MAX_SIZE, 1, 5000, 1)
                .connectivity(SegSweepLabeller.Connectivity.SIX)
                .parallelism(1)
                .pickCriterion(SegSweepParameters.PickCriterion.NONE)
                .build());

        assertEquals(5000, result.results().size());
        assertEquals(5000, result.results().get(0).objectCount());
        assertEquals(5000, result.results().get(result.results().size() - 1).objectCount());
    }
}
