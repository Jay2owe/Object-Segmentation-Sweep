/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.sweep.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Finds the knee of an object-count curve with explicit refusal outcomes.
 *
 * <p>The detector assumes the x values are comparable parameter units and the y
 * values are object counts or count densities from the same crop and engine. It
 * normalises only to find the bend within the supplied full axis, then reports
 * the chosen parameter value unchanged. The method is a deterministic heuristic,
 * not a proof of correctness, and no randomisation null model is included before
 * v0.2.0.</p>
 */
public final class KneeDetector {

    private static final double FLAT_DIFFERENCE_RANGE = 0.1d;
    private static final double SMALL_SWEEP_CLOSE_TO_MAX = 0.05d;

    private KneeDetector() {
    }

    public static KneeOutcome detect(double[] parameterValues,
                                     double[] counts,
                                     double displayRangeMin,
                                     double displayRangeMax,
                                     double displayStep) {
        int n = parameterValues == null || counts == null
                ? 0
                : Math.min(parameterValues.length, counts.length);
        List<Point> points = new ArrayList<Point>(n);
        for (int i = 0; i < n; i++) {
            if (isFinite(parameterValues[i]) && isFinite(counts[i])) {
                points.add(new Point(i, parameterValues[i], counts[i]));
            }
        }
        if (points.size() < 4) {
            return KneeOutcome.of(KneeOutcome.Kind.TOO_FEW_POINTS,
                    displayRangeMin, displayRangeMax, displayStep,
                    "Knee detection needs at least four finite points.");
        }
        Collections.sort(points, new Comparator<Point>() {
            @Override
            public int compare(Point a, Point b) {
                return Double.compare(a.x, b.x);
            }
        });

        Range xRange = range(points, true);
        if (xRange.span() <= 0.0d) {
            return KneeOutcome.of(KneeOutcome.Kind.DEGENERATE_RANGE,
                    displayRangeMin, displayRangeMax, displayStep,
                    "All finite parameter values collapse to one value.");
        }
        Range yRange = range(points, false);
        if (yRange.span() <= 0.0d) {
            return KneeOutcome.of(KneeOutcome.Kind.ALL_PLATEAU,
                    displayRangeMin, displayRangeMax, displayStep,
                    "The finite object-count curve is flat.");
        }

        boolean increasing = points.get(points.size() - 1).y >= points.get(0).y;
        double[] differences = new double[points.size()];
        double minDifference = Double.POSITIVE_INFINITY;
        double maxDifference = Double.NEGATIVE_INFINITY;
        int maxIndex = -1;
        for (int i = 0; i < points.size(); i++) {
            Point point = points.get(i);
            double xNorm = (point.x - xRange.min) / xRange.span();
            double yNorm = increasing
                    ? (point.y - yRange.min) / yRange.span()
                    : (yRange.max - point.y) / yRange.span();
            double difference = yNorm - xNorm;
            differences[i] = difference;
            if (difference < minDifference) {
                minDifference = difference;
            }
            if (difference > maxDifference) {
                maxDifference = difference;
                maxIndex = i;
            }
        }
        if (maxIndex < 0 || maxDifference - minDifference < FLAT_DIFFERENCE_RANGE) {
            return KneeOutcome.of(KneeOutcome.Kind.NO_BEND,
                    displayRangeMin, displayRangeMax, displayStep,
                    "No bend exceeded the flat-curve tolerance.");
        }

        int kneeIndex = maxIndex;
        int steepestTransitionIndex = steepestTransitionKnee(points, yRange, increasing);
        if (Math.abs(steepestTransitionIndex - maxIndex) <= 1) {
            kneeIndex = steepestTransitionIndex;
        } else {
            for (int i = 1; i < maxIndex; i++) {
                if (maxDifference - differences[i] <= SMALL_SWEEP_CLOSE_TO_MAX) {
                    kneeIndex = i;
                    break;
                }
            }
        }
        Point knee = points.get(kneeIndex);
        return KneeOutcome.kneeAt(knee.originalIndex, knee.x,
                displayRangeMin, displayRangeMax, displayStep,
                "Maximum bend in the finite object-count curve.");
    }

    static int[] findPlateauRange(double[] xs, double[] ys) {
        if (xs == null || ys == null) {
            return null;
        }
        int n = Math.min(xs.length, ys.length);
        List<Point> points = new ArrayList<Point>(n);
        double maxAbsY = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            if (isFinite(xs[i]) && isFinite(ys[i])) {
                points.add(new Point(i, xs[i], ys[i]));
                double absY = Math.abs(ys[i]);
                if (absY > maxAbsY) {
                    maxAbsY = absY;
                }
            }
        }
        if (points.size() < 3 || !isFinite(maxAbsY)) {
            return null;
        }
        Collections.sort(points, new Comparator<Point>() {
            @Override
            public int compare(Point a, Point b) {
                return Double.compare(a.x, b.x);
            }
        });

        double tolerance = Math.max(1.0d, 0.05d * maxAbsY);
        int end = points.size() - 1;
        int start = end;
        while (start > 0) {
            double delta = Math.abs(points.get(start).y - points.get(start - 1).y);
            if (delta > tolerance) {
                break;
            }
            start--;
        }
        if (start == end || start == 0) {
            return null;
        }
        return new int[] { points.get(start).originalIndex, points.get(end).originalIndex };
    }

    private static Range range(List<Point> points, boolean useX) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < points.size(); i++) {
            double value = useX ? points.get(i).x : points.get(i).y;
            if (value < min) {
                min = value;
            }
            if (value > max) {
                max = value;
            }
        }
        return new Range(min, max);
    }

    private static int steepestTransitionKnee(List<Point> points,
                                              Range yRange,
                                              boolean increasing) {
        int bestSegment = 0;
        double bestDelta = Double.NEGATIVE_INFINITY;
        for (int i = 1; i < points.size(); i++) {
            double previous = normalisedY(points.get(i - 1).y, yRange, increasing);
            double current = normalisedY(points.get(i).y, yRange, increasing);
            double delta = Math.abs(current - previous);
            if (delta > bestDelta) {
                bestDelta = delta;
                bestSegment = i - 1;
            }
        }
        return increasing ? bestSegment : bestSegment + 1;
    }

    private static double normalisedY(double y, Range yRange, boolean increasing) {
        return increasing
                ? (y - yRange.min) / yRange.span()
                : (yRange.max - y) / yRange.span();
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static final class Point {
        final int originalIndex;
        final double x;
        final double y;

        Point(int originalIndex, double x, double y) {
            this.originalIndex = originalIndex;
            this.x = x;
            this.y = y;
        }
    }

    private static final class Range {
        final double min;
        final double max;

        Range(double min, double max) {
            this.min = min;
            this.max = max;
        }

        double span() {
            return max - min;
        }
    }
}
