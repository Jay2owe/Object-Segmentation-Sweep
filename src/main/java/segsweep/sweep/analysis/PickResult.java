/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.sweep.analysis;

import segsweep.sweep.ParameterCombo;
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
    private final ParameterCombo kneeCombo;
    private final ParameterCombo stabilityCombo;

    public PickResult(KneeOutcome knee,
                      StabilityOutcome stability,
                      SweepProvenance provenance) {
        this(knee, stability, provenance, null, null);
    }

    public PickResult(KneeOutcome knee,
                      StabilityOutcome stability,
                      SweepProvenance provenance,
                      ParameterCombo kneeCombo,
                      ParameterCombo stabilityCombo) {
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
        this.kneeCombo = kneeCombo;
        this.stabilityCombo = stabilityCombo;
    }

    public KneeOutcome knee() {
        return knee;
    }

    public StabilityOutcome stability() {
        return stability;
    }

    public boolean criteriaAgree() {
        if (kneeCombo != null || stabilityCombo != null) {
            return knee.kind() == KneeOutcome.Kind.KNEE_AT
                    && stability.kind() == StabilityOutcome.Kind.STABLE_AT
                    && kneeCombo != null
                    && kneeCombo.hasSameCoordinates(stabilityCombo);
        }
        return knee.kind() == KneeOutcome.Kind.KNEE_AT
                && stability.kind() == StabilityOutcome.Kind.STABLE_AT
                && knee.index() == stability.index();
    }

    public SweepProvenance provenance() {
        return provenance;
    }

    public ParameterCombo kneeCombo() {
        return kneeCombo;
    }

    public ParameterCombo stabilityCombo() {
        return stabilityCombo;
    }
}
