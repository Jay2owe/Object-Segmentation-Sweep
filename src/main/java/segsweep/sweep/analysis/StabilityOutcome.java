/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.sweep.analysis;

/**
 * Typed result of neighbour-agreement stability scoring.
 *
 * <p>Eligibility is a property of the parameter lattice: only combinations with
 * a full neighbour complement across every varying axis can win. Scores are
 * heuristic local-agreement measurements, not segmentation validation against
 * annotations, and no randomisation null model is included before v0.2.0.</p>
 */
public final class StabilityOutcome {
    public enum Kind {
        STABLE_AT,
        NO_ELIGIBLE_COMBINATIONS,
        TOO_MANY_AXES,
        ABORTED
    }

    private final Kind kind;
    private final int index;
    private final double meanNeighbourIou;
    private final int eligibleCount;
    private final boolean[] eligible;
    private final double[] meanNeighbourIous;
    private final String explanation;

    private StabilityOutcome(Kind kind,
                             int index,
                             double meanNeighbourIou,
                             int eligibleCount,
                             boolean[] eligible,
                             double[] meanNeighbourIous,
                             String explanation) {
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        this.kind = kind;
        this.index = index;
        this.meanNeighbourIou = meanNeighbourIou;
        this.eligibleCount = Math.max(0, eligibleCount);
        this.eligible = eligible == null ? new boolean[0] : eligible.clone();
        this.meanNeighbourIous = meanNeighbourIous == null ? new double[0] : meanNeighbourIous.clone();
        this.explanation = explanation == null ? "" : explanation;
    }

    static StabilityOutcome stableAt(int index,
                                     double meanNeighbourIou,
                                     int eligibleCount,
                                     boolean[] eligible,
                                     double[] meanNeighbourIous,
                                     String explanation) {
        return new StabilityOutcome(Kind.STABLE_AT, index, meanNeighbourIou,
                eligibleCount, eligible, meanNeighbourIous, explanation);
    }

    static StabilityOutcome of(Kind kind,
                               int eligibleCount,
                               boolean[] eligible,
                               double[] meanNeighbourIous,
                               String explanation) {
        if (kind == Kind.STABLE_AT) {
            throw new IllegalArgumentException("use stableAt for STABLE_AT outcomes");
        }
        return new StabilityOutcome(kind, -1, Double.NaN, eligibleCount,
                eligible, meanNeighbourIous, explanation);
    }

    public Kind kind() {
        return kind;
    }

    public int index() {
        return index;
    }

    public double meanNeighbourIou() {
        return meanNeighbourIou;
    }

    public int eligibleCount() {
        return eligibleCount;
    }

    public boolean isEligible(int index) {
        return index >= 0 && index < eligible.length && eligible[index];
    }

    public boolean[] eligible() {
        return eligible.clone();
    }

    public double meanNeighbourIou(int index) {
        return index >= 0 && index < meanNeighbourIous.length
                ? meanNeighbourIous[index]
                : Double.NaN;
    }

    public double[] meanNeighbourIous() {
        return meanNeighbourIous.clone();
    }

    public String explanation() {
        return explanation;
    }
}
