/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.sweep.analysis;

import segsweep.sweep.SweepProvenance;

/**
 * Combined picker report for one sweep.
 *
 * <p>The knee and stability criteria are reported independently; neither
 * overrides the other and disagreement is preserved for the UI and saved
 * methods record. The provenance states the crop and displayed range under
 * which the heuristics were reviewed. These are objective computations over the
 * supplied data, not claims of optimality, and no randomisation null model is
 * included before v0.2.0.</p>
 */
public final class PickResult {
    private final KneeOutcome knee;
    private final StabilityOutcome stability;
    private final SweepProvenance provenance;

    public PickResult(KneeOutcome knee,
                      StabilityOutcome stability,
                      SweepProvenance provenance) {
        if (knee == null) {
            throw new IllegalArgumentException("knee must not be null");
        }
        if (stability == null) {
            throw new IllegalArgumentException("stability must not be null");
        }
        if (provenance == null) {
            throw new IllegalArgumentException("provenance must not be null");
        }
        this.knee = knee;
        this.stability = stability;
        this.provenance = provenance;
    }

    public KneeOutcome knee() {
        return knee;
    }

    public StabilityOutcome stability() {
        return stability;
    }

    public boolean criteriaAgree() {
        return knee.kind() == KneeOutcome.Kind.KNEE_AT
                && stability.kind() == StabilityOutcome.Kind.STABLE_AT
                && knee.index() == stability.index();
    }

    public SweepProvenance provenance() {
        return provenance;
    }
}
