/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.sweep;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.AutoThresholder;
import ij.process.ImageProcessor;
import segsweep.util.StackHistogram;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

public final class RangeSuggester {

    private static final AutoThresholder.Method[] CLASSICAL_METHODS = {
            AutoThresholder.Method.Otsu,
            AutoThresholder.Method.Li,
            AutoThresholder.Method.Triangle,
            AutoThresholder.Method.Yen,
            AutoThresholder.Method.Huang
    };
    private static final double[] SIZE_PERCENTILES = { 10.0d, 25.0d, 50.0d, 75.0d, 90.0d };
    private static final int VOXEL_VISIT_BUDGET = 40 * 1000 * 1000;
    static final long MAX_VOXELS_FOR_SUGGESTION = 64L * 1024L * 1024L;
    static final String SAMPLED_NOTE = "sampled; large stack";

    private RangeSuggester() {
    }

    public static ParameterValueList suggestThresholdDisplayWindow(ImagePlus source, CropSpec crop) {
        ImagePlus cropped = applyCrop(source, crop);
        try {
            return thresholdSuggestions(StackHistogram.of(cropped));
        } finally {
            closeIfOwned(cropped, source);
        }
    }

    public static ParameterValueList suggestSizeDisplayWindow(ImagePlus source, CropSpec crop) {
        ImagePlus cropped = applyCrop(source, crop);
        try {
            StackHistogram histogram = StackHistogram.of(cropped);
            VoxelComponents components = componentVoxelCounts(cropped,
                    otsuThreshold(histogram), VOXEL_VISIT_BUDGET);
            if (components.sizes.isEmpty()) {
                ParameterValueList fallback = ParameterValueList.ofInts(1);
                return components.truncated
                        ? new ParameterValueList(fallback.values(), SAMPLED_NOTE)
                        : fallback;
            }
            Collections.sort(components.sizes);
            return new ParameterValueList(percentileInts(components.sizes),
                    components.truncated ? SAMPLED_NOTE : null);
        } finally {
            closeIfOwned(cropped, source);
        }
    }

    private static ImagePlus applyCrop(ImagePlus source, CropSpec crop) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        CropSpec safeCrop = crop == null ? CropSpec.full() : crop;
        return safeCrop.apply(source);
    }

    private static ParameterValueList thresholdSuggestions(StackHistogram histogram) {
        TreeSet<Double> values = new TreeSet<Double>();
        AutoThresholder thresholder = new AutoThresholder();
        for (int i = 0; i < CLASSICAL_METHODS.length; i++) {
            int bin = thresholder.getThreshold(CLASSICAL_METHODS[i], histogram.counts());
            values.add(Double.valueOf(thresholdValue(histogram, bin)));
        }
        return ParameterValueList.of(centeredWindow(new ArrayList<Double>(values), 5));
    }

    private static double otsuThreshold(StackHistogram histogram) {
        int bin = new AutoThresholder().getThreshold(AutoThresholder.Method.Otsu,
                histogram.counts());
        return thresholdValue(histogram, bin);
    }

    private static double thresholdValue(StackHistogram histogram, int thresholdBin) {
        int[] counts = histogram.counts();
        int bin = Math.min(Math.max(0, thresholdBin), counts.length - 1);
        return histogram.valueFor(bin);
    }

    static VoxelComponents componentVoxelCounts(ImagePlus source, double threshold, int budget) {
        List<Integer> sizes = new ArrayList<Integer>();
        if (source == null) {
            return new VoxelComponents(sizes, false);
        }
        ImageStack stack = source.getStack();
        if (stack == null || stack.getSize() < 1) {
            return new VoxelComponents(sizes, false);
        }

        int width = source.getWidth();
        int height = source.getHeight();
        int depth = stack.getSize();
        long voxelCount = (long) width * (long) height * (long) depth;
        if (voxelCount <= 0 || voxelCount > MAX_VOXELS_FOR_SUGGESTION) {
            return new VoxelComponents(sizes, true);
        }

        ImageProcessor[] planes = new ImageProcessor[depth];
        for (int z = 0; z < depth; z++) {
            planes[z] = stack.getProcessor(z + 1);
        }

        int sliceSize = width * height;
        byte[] state = new byte[(int) voxelCount];
        ArrayDeque<Integer> queue = new ArrayDeque<Integer>();
        int visits = 0;

        for (int seed = 0; seed < state.length; seed++) {
            if (state[seed] != 0) {
                continue;
            }
            if (visits >= budget) {
                return new VoxelComponents(sizes, true);
            }
            visits++;
            if (!aboveThreshold(planes, sliceSize, width, seed, threshold)) {
                state[seed] = 1;
                continue;
            }
            state[seed] = 2;
            queue.add(Integer.valueOf(seed));
            int count = 0;
            while (!queue.isEmpty()) {
                int index = queue.removeFirst().intValue();
                count++;
                int z = index / sliceSize;
                int rem = index - (z * sliceSize);
                int y = rem / width;
                int x = rem - (y * width);
                for (int dz = -1; dz <= 1; dz++) {
                    int nz = z + dz;
                    if (nz < 0 || nz >= depth) continue;
                    for (int dy = -1; dy <= 1; dy++) {
                        int ny = y + dy;
                        if (ny < 0 || ny >= height) continue;
                        for (int dx = -1; dx <= 1; dx++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            int nx = x + dx;
                            if (nx < 0 || nx >= width) continue;
                            int neighbour = (nz * sliceSize) + (ny * width) + nx;
                            if (state[neighbour] == 0) {
                                if (visits >= budget) {
                                    queue.clear();
                                    sizes.add(Integer.valueOf(count));
                                    return new VoxelComponents(sizes, true);
                                }
                                visits++;
                                if (aboveThreshold(planes, sliceSize, width, neighbour, threshold)) {
                                    state[neighbour] = 2;
                                    queue.add(Integer.valueOf(neighbour));
                                } else {
                                    state[neighbour] = 1;
                                }
                            }
                        }
                    }
                }
            }
            sizes.add(Integer.valueOf(count));
        }
        return new VoxelComponents(sizes, false);
    }

    private static boolean aboveThreshold(ImageProcessor[] planes, int sliceSize, int width,
                                          int index, double threshold) {
        int z = index / sliceSize;
        ImageProcessor plane = planes[z];
        if (plane == null) {
            return false;
        }
        int rem = index - (z * sliceSize);
        int y = rem / width;
        int x = rem - (y * width);
        return plane.getf(x, y) > threshold;
    }

    private static List<Integer> percentileInts(List<Integer> sortedSizes) {
        TreeSet<Integer> unique = new TreeSet<Integer>();
        for (int i = 0; i < SIZE_PERCENTILES.length; i++) {
            unique.add(Integer.valueOf(Math.max(1,
                    (int) Math.round(percentile(sortedSizes, SIZE_PERCENTILES[i])))));
        }
        return new ArrayList<Integer>(unique);
    }

    private static double percentile(List<Integer> sorted, double percentile) {
        if (sorted.isEmpty()) {
            return 0.0d;
        }
        if (sorted.size() == 1) {
            return sorted.get(0).doubleValue();
        }
        double rank = (percentile / 100.0d) * (sorted.size() - 1);
        int low = (int) Math.floor(rank);
        int high = (int) Math.ceil(rank);
        if (low == high) {
            return sorted.get(low).doubleValue();
        }
        double fraction = rank - low;
        return sorted.get(low).doubleValue() * (1.0d - fraction)
                + sorted.get(high).doubleValue() * fraction;
    }

    private static <T> List<T> centeredWindow(List<T> sorted, int maxCount) {
        if (sorted.size() <= maxCount) {
            return sorted;
        }
        int start = Math.max(0, (sorted.size() - maxCount) / 2);
        return new ArrayList<T>(sorted.subList(start, start + maxCount));
    }

    private static void closeIfOwned(ImagePlus image, ImagePlus source) {
        if (image == null || image == source) {
            return;
        }
        image.changes = false;
        image.close();
        image.flush();
    }

    static final class VoxelComponents {
        final List<Integer> sizes;
        final boolean truncated;

        VoxelComponents(List<Integer> sizes, boolean truncated) {
            this.sizes = sizes;
            this.truncated = truncated;
        }
    }
}
