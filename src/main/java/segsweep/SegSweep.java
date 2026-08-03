/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep;

/**
 * Public Java facade for running Object Segmentation Sweep headlessly.
 */
public final class SegSweep {
    private SegSweep() {
    }

    public static SegSweepResult run(SegSweepParameters parameters) {
        return SegSweepAnalysis.run(parameters);
    }
}
