/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.sweep;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public interface VariationStrategy {
    void dispatch(ParameterSweep displayWindow,
                  Consumer<VariationResult> publisher,
                  Consumer<SweepProgress> progress,
                  BooleanSupplier cancelCheck) throws Exception;
}
