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
 * Typed result of object-count knee detection.
 *
 * <p>The outcome records the parameter units and the displayed range reviewed
 * by the user, but the calculation is expected to be run over the full
 * available axis for the classical component-tree engine. This is a heuristic
 * curve summary, not a ground-truth optimiser; no randomisation null model is
 * included before v0.2.0.</p>
 */
public final class KneeOutcome {
    public enum Kind {
        KNEE_AT,
        ALL_PLATEAU,
        TOO_FEW_POINTS,
        NO_BEND,
        DEGENERATE_RANGE
    }

    private final Kind kind;
    private final int index;
    private final double parameterValue;
    private final double rangeMin;
    private final double rangeMax;
    private final double step;
    private final String explanation;

    private KneeOutcome(Kind kind,
                        int index,
                        double parameterValue,
                        double rangeMin,
                        double rangeMax,
                        double step,
                        String explanation) {
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        this.kind = kind;
        this.index = index;
        this.parameterValue = parameterValue;
        this.rangeMin = rangeMin;
        this.rangeMax = rangeMax;
        this.step = step;
        this.explanation = explanation == null ? "" : explanation;
    }

    public static KneeOutcome kneeAt(int index,
                                     double parameterValue,
                                     double rangeMin,
                                     double rangeMax,
                                     double step,
                                     String explanation) {
        if (index < 0) {
            throw new IllegalArgumentException("index must be non-negative");
        }
        return new KneeOutcome(Kind.KNEE_AT, index, parameterValue,
                rangeMin, rangeMax, step, explanation);
    }

    public static KneeOutcome of(Kind kind,
                                 double rangeMin,
                                 double rangeMax,
                                 double step,
                                 String explanation) {
        if (kind == Kind.KNEE_AT) {
            throw new IllegalArgumentException("use kneeAt for KNEE_AT outcomes");
        }
        return new KneeOutcome(kind, -1, Double.NaN, rangeMin, rangeMax,
                step, explanation);
    }

    public Kind kind() {
        return kind;
    }

    public int index() {
        return index;
    }

    public double parameterValue() {
        return parameterValue;
    }

    public double rangeMin() {
        return rangeMin;
    }

    public double rangeMax() {
        return rangeMax;
    }

    public double step() {
        return step;
    }

    public String explanation() {
        return explanation;
    }

    public boolean hasKnee() {
        return kind == Kind.KNEE_AT;
    }
}
