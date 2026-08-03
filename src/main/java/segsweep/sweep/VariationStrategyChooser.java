/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.sweep;

public final class VariationStrategyChooser {
    private VariationStrategyChooser() {
    }

    public static VariationStrategy choose(ParameterSweep sweep,
                                           VariationStrategy classicalStrategy) {
        if (sweep == null) {
            throw new IllegalArgumentException("sweep must not be null");
        }
        if (sweep.method() != ParameterSweep.Method.CLASSICAL) {
            throw new UnsupportedOperationException(
                    sweep.method().label() + " variations are not implemented in v0.1.0.");
        }
        if (classicalStrategy == null) {
            throw new IllegalStateException(
                    "Classical variations require a classical sweep strategy.");
        }
        return classicalStrategy;
    }
}
