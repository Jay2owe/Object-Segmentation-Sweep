/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.sweep.analysis;

import java.util.Arrays;

/**
 * Typed result of object-count knee detection.
 *
 * <p>The outcome records the parameter units and the actual range used for the
 * calculation, which may be broader than the displayed review window for the
 * classical component-tree engine. This is a heuristic
 * curve summary, not a ground-truth optimiser; no randomisation null model is
 * included before v0.2.0.</p>
 */
public final class KneeOutcome {
    public enum Kind {
        KNEE_AT,
        ALL_PLATEAU,
        TOO_FEW_POINTS,
        NO_BEND,
        DEGENERATE_RANGE,
        TOO_MANY_OBJECTS,
        FAILED_COMBINATIONS,
        MULTI_AXIS_UNSUPPORTED
    }

    private final Kind kind;
    private final int index;
    private final double parameterValue;
    private final double rangeMin;
    private final double rangeMax;
    private final double step;
    private final double[] sampledValues;
    private final String explanation;

    private KneeOutcome(Kind kind,
                        int index,
                        double parameterValue,
                        double rangeMin,
                        double rangeMax,
                        double step,
                        double[] sampledValues,
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
        this.sampledValues = sampledValues == null
                ? new double[0] : Arrays.copyOf(sampledValues, sampledValues.length);
        this.explanation = explanation == null ? "" : explanation;
    }

    public static KneeOutcome kneeAt(int index,
                                     double parameterValue,
                                     double rangeMin,
                                     double rangeMax,
                                     double step,
                                     String explanation) {
        return kneeAt(index, parameterValue, rangeMin, rangeMax, step,
                new double[0], explanation);
    }

    public static KneeOutcome kneeAt(int index,
                                     double parameterValue,
                                     double rangeMin,
                                     double rangeMax,
                                     double step,
                                     double[] sampledValues,
                                     String explanation) {
        if (index < 0) {
            throw new IllegalArgumentException("index must be non-negative");
        }
        return new KneeOutcome(Kind.KNEE_AT, index, parameterValue,
                rangeMin, rangeMax, step, sampledValues, explanation);
    }

    public static KneeOutcome of(Kind kind,
                                 double rangeMin,
                                 double rangeMax,
                                 double step,
                                 String explanation) {
        return of(kind, rangeMin, rangeMax, step, new double[0], explanation);
    }

    public static KneeOutcome of(Kind kind,
                                 double rangeMin,
                                 double rangeMax,
                                 double step,
                                 double[] sampledValues,
                                 String explanation) {
        if (kind == Kind.KNEE_AT) {
            throw new IllegalArgumentException("use kneeAt for KNEE_AT outcomes");
        }
        return new KneeOutcome(kind, -1, Double.NaN, rangeMin, rangeMax,
                step, sampledValues, explanation);
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

    /** Exact, sorted finite parameter coordinates used by the knee calculation. */
    public double[] sampledValues() {
        return Arrays.copyOf(sampledValues, sampledValues.length);
    }

    public String explanation() {
        return explanation;
    }

    public boolean hasKnee() {
        return kind == Kind.KNEE_AT;
    }

    public boolean comparable(KneeOutcome other) {
        if (other == null) {
            return false;
        }
        return kind == other.kind
                && same(rangeMin, other.rangeMin)
                && same(rangeMax, other.rangeMax)
                && same(step, other.step)
                && sameSamples(sampledValues, other.sampledValues);
    }

    private static boolean same(double a, double b) {
        if (Double.isNaN(a) && Double.isNaN(b)) {
            return true;
        }
        return Double.compare(a, b) == 0;
    }

    private static boolean sameSamples(double[] a, double[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (!same(a[i], b[i])) return false;
        }
        return true;
    }
}
