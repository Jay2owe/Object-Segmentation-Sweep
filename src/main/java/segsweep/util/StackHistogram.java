/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.util;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;

/**
 * Intensity histogram accumulated over a whole stack with binning that is
 * consistent across every slice.
 */
public final class StackHistogram {

    private static final int BINS = 256;

    private final int[] counts;
    private final double min;
    private final double max;
    private final boolean direct;

    private StackHistogram(int[] counts, double min, double max, boolean direct) {
        this.counts = counts;
        this.min = min;
        this.max = max;
        this.direct = direct;
    }

    public static StackHistogram of(ImagePlus imp) {
        if (imp == null) {
            return empty();
        }
        ImageStack stack = imp.getStack();
        if (stack == null || stack.getSize() < 1) {
            ImageProcessor processor = imp.getProcessor();
            return processor == null ? empty() : of(processor);
        }
        return build(stack, stack.getSize());
    }

    public static StackHistogram of(ImageProcessor processor) {
        if (processor == null) {
            return empty();
        }
        ImageStack single = new ImageStack(processor.getWidth(), processor.getHeight());
        single.addSlice(processor);
        return build(single, 1);
    }

    public static StackHistogram ofValues(double[] values) {
        if (values == null || values.length == 0) {
            return empty();
        }
        Range range = new Range();
        for (int i = 0; i < values.length; i++) {
            range.accept(values[i]);
        }
        if (range.seen == 0L) return empty();
        Layout layout = range.layout();
        int[] counts = new int[layout.bins];
        if (layout.degenerate) {
            fillDegenerate(counts, layout, range.seen);
            return new StackHistogram(counts, layout.min, layout.max, layout.direct);
        }
        for (int i = 0; i < values.length; i++) {
            if (Double.isFinite(values[i])) counts[layout.binFor(values[i])]++;
        }
        return new StackHistogram(counts, layout.min, layout.max, layout.direct);
    }

    public int[] counts() {
        return counts;
    }

    public double valueFor(int bin) {
        if (max <= min) {
            return min;
        }
        if (direct) {
            return bin;
        }
        return min + ((double) bin / (double) Math.max(1, counts.length - 1)) * (max - min);
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public boolean isDirect() {
        return direct;
    }

    private static StackHistogram build(ImageStack stack, int slices) {
        Range range = new Range();
        for (int z = 1; z <= slices; z++) {
            ImageProcessor processor = stack.getProcessor(z);
            if (processor == null) {
                continue;
            }
            int pixels = processor.getWidth() * processor.getHeight();
            for (int p = 0; p < pixels; p++) {
                range.accept(processor.getf(p));
            }
        }

        if (range.seen == 0L) return empty();
        Layout layout = range.layout();
        int[] counts = new int[layout.bins];
        if (layout.degenerate) {
            fillDegenerate(counts, layout, range.seen);
            return new StackHistogram(counts, layout.min, layout.max, layout.direct);
        }
        for (int z = 1; z <= slices; z++) {
            ImageProcessor processor = stack.getProcessor(z);
            if (processor == null) {
                continue;
            }
            int pixels = processor.getWidth() * processor.getHeight();
            for (int p = 0; p < pixels; p++) {
                double value = processor.getf(p);
                if (Double.isFinite(value)) counts[layout.binFor(value)]++;
            }
        }
        return new StackHistogram(counts, layout.min, layout.max, layout.direct);
    }

    private static void fillDegenerate(int[] counts, Layout layout, long total) {
        int bin = layout.direct ? (int) Math.round(layout.min) : 0;
        if (bin < 0) bin = 0;
        if (bin >= counts.length) bin = counts.length - 1;
        counts[bin] = total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    private static StackHistogram empty() {
        return new StackHistogram(new int[BINS], 0.0d, 0.0d, false);
    }

    private static final class Range {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        boolean integral = true;
        long seen;

        void accept(double value) {
            if (!Double.isFinite(value)) return;
            if (value < min) min = value;
            if (value > max) max = value;
            if (integral && Math.abs(value - Math.rint(value)) > 0.000001d) {
                integral = false;
            }
            seen++;
        }

        Layout layout() {
            double lo = min;
            double hi = max;
            if (!Double.isFinite(lo) || !Double.isFinite(hi)) {
                lo = 0.0d;
                hi = 0.0d;
            }
            boolean direct = integral && lo >= 0.0d && hi <= 255.0d;
            return new Layout(lo, hi, direct, BINS);
        }
    }

    private static final class Layout {
        final double min;
        final double max;
        final boolean direct;
        final int bins;
        final boolean degenerate;

        Layout(double min, double max, boolean direct, int bins) {
            this.min = min;
            this.max = max;
            this.direct = direct;
            this.bins = bins;
            this.degenerate = max <= min;
        }

        int binFor(double rawValue) {
            double value = Double.isNaN(rawValue) ? 0.0d : rawValue;
            int bin;
            if (direct) {
                bin = (int) Math.round(value);
            } else {
                bin = (int) Math.floor(((value - min) / (max - min)) * (bins - 1));
            }
            if (bin < 0) return 0;
            if (bin >= bins) return bins - 1;
            return bin;
        }
    }
}
