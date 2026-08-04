/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep;

import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

/**
 * Plain threshold and 3D connected-components labeller used as the oracle for
 * the component-tree engine.
 *
 * <p>The default connectivity is {@link Connectivity#TWENTY_SIX}, matching the
 * 3D Objects Counter+ native path: it creates {@code mcib3d.image3d.ImageLabeller}
 * and calls {@code getLabels(ImageHandler)}, whose default boolean argument is
 * {@code false}; in mcib3d-core 4.1.7b that routes to {@code labelSpots26}.</p>
 */
public final class SegSweepLabeller {
    public static final Connectivity DEFAULT_CONNECTIVITY = Connectivity.TWENTY_SIX;
    private static final int MAX_16_BIT_LABEL = 65535;

    public enum Connectivity { SIX, TWENTY_SIX }

    private SegSweepLabeller() {}

    /** Threshold, label in 3D, size-filter. Never returns null; never opens a window. */
    public static LabelResult label(ImagePlus source,
                                    int threshold,
                                    int minSize,
                                    int maxSize,
                                    Connectivity connectivity) {
        return label(source, (double) threshold, minSize, maxSize, connectivity);
    }

    /** Double-threshold overload used for calibrated and 32-bit image data. */
    public static LabelResult label(ImagePlus source,
                                    double threshold,
                                    int minSize,
                                    int maxSize,
                                    Connectivity connectivity) {
        Connectivity safeConnectivity = connectivity == null ? DEFAULT_CONNECTIVITY : connectivity;
        ImagePlus empty = emptyLabelMapLike(source);
        if (source == null || source.getStack() == null || source.getStackSize() == 0) {
            return LabelResult.empty(empty, "Source image has no stack to label.");
        }

        int width = source.getWidth();
        int height = source.getHeight();
        int depth = source.getStackSize();
        long voxelCount = (long) width * (long) height * (long) depth;
        if (voxelCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Image is too large for the plain labeller: "
                    + voxelCount + " voxels exceed Java array indexing.");
        }

        int safeMin = Math.max(0, minSize);
        int safeMax = Math.max(0, maxSize);
        if (safeMax < safeMin) {
            return LabelResult.empty(empty,
                    "No components can satisfy minSize " + safeMin + " with maxSize " + safeMax + ".");
        }

        int[] provisional = new int[(int) voxelCount];
        UnionFind uf = new UnionFind();
        ImageStack stack = source.getStack();
        int plane = width * height;

        for (int z = 0; z < depth; z++) {
            ImageProcessor processor = stack.getProcessor(z + 1);
            for (int y = 0; y < height; y++) {
                int row = y * width;
                for (int x = 0; x < width; x++) {
                    int index = z * plane + row + x;
                    float value = processor.getf(x, y);
                    if (!Float.isFinite(value) || value <= threshold) {
                        continue;
                    }
                    int label = smallestVisitedNeighbourLabel(provisional, width, height, depth,
                            x, y, z, safeConnectivity);
                    if (label == 0) {
                        provisional[index] = uf.add();
                    } else {
                        provisional[index] = label;
                        unionVisitedNeighbours(uf, provisional, width, height, depth,
                                x, y, z, label, safeConnectivity);
                    }
                }
            }
        }

        if (uf.size() == 1) {
            return LabelResult.empty(empty, "No voxel value was greater than threshold " + threshold + ".");
        }

        int[] rootSizes = new int[uf.size()];
        for (int i = 0; i < provisional.length; i++) {
            int label = provisional[i];
            if (label > 0) {
                int root = uf.find(label);
                provisional[i] = root;
                rootSizes[root]++;
            }
        }

        int[] finalByRoot = new int[uf.size()];
        int finalCount = 0;
        for (int label = 1; label < rootSizes.length; label++) {
            int size = rootSizes[label];
            if (size > 0 && size >= safeMin && size <= safeMax) {
                finalByRoot[label] = ++finalCount;
            }
        }

        if (finalCount == 0) {
            return LabelResult.empty(empty, "All components were outside the size range "
                    + safeMin + ".." + safeMax + " voxels.");
        }
        if (finalCount > MAX_16_BIT_LABEL) {
            return LabelResult.tooManyLabels(empty, finalCount);
        }

        int[] finalSizes = new int[finalCount + 1];
        for (int root = 1; root < rootSizes.length; root++) {
            int finalLabel = finalByRoot[root];
            if (finalLabel > 0) {
                finalSizes[finalLabel] = rootSizes[root];
            }
        }

        ImagePlus labels = emptyLabelMapLike(source);
        ImageStack labelStack = labels.getStack();
        for (int z = 0; z < depth; z++) {
            ShortProcessor output = (ShortProcessor) labelStack.getProcessor(z + 1);
            int zOffset = z * plane;
            for (int i = 0; i < plane; i++) {
                int root = provisional[zOffset + i];
                if (root > 0) {
                    output.set(i, finalByRoot[root]);
                }
            }
        }

        return LabelResult.ok(labels, finalCount, finalSizes);
    }

    private static int smallestVisitedNeighbourLabel(int[] labels,
                                                     int width,
                                                     int height,
                                                     int depth,
                                                     int x,
                                                     int y,
                                                     int z,
                                                     Connectivity connectivity) {
        int smallest = 0;
        if (connectivity == Connectivity.SIX) {
            smallest = minNonZero(smallest, labelAt(labels, width, height, depth, x - 1, y, z));
            smallest = minNonZero(smallest, labelAt(labels, width, height, depth, x, y - 1, z));
            return minNonZero(smallest, labelAt(labels, width, height, depth, x, y, z - 1));
        }

        for (int dz = -1; dz <= 0; dz++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (!isVisitedOffset(dx, dy, dz)) {
                        continue;
                    }
                    smallest = minNonZero(smallest, labelAt(labels, width, height, depth,
                            x + dx, y + dy, z + dz));
                }
            }
        }
        return smallest;
    }

    private static void unionVisitedNeighbours(UnionFind uf,
                                               int[] labels,
                                               int width,
                                               int height,
                                               int depth,
                                               int x,
                                               int y,
                                               int z,
                                               int label,
                                               Connectivity connectivity) {
        if (connectivity == Connectivity.SIX) {
            unionIfPresent(uf, label, labelAt(labels, width, height, depth, x - 1, y, z));
            unionIfPresent(uf, label, labelAt(labels, width, height, depth, x, y - 1, z));
            unionIfPresent(uf, label, labelAt(labels, width, height, depth, x, y, z - 1));
            return;
        }

        for (int dz = -1; dz <= 0; dz++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (!isVisitedOffset(dx, dy, dz)) {
                        continue;
                    }
                    unionIfPresent(uf, label, labelAt(labels, width, height, depth,
                            x + dx, y + dy, z + dz));
                }
            }
        }
    }

    private static boolean isVisitedOffset(int dx, int dy, int dz) {
        if (dx == 0 && dy == 0 && dz == 0) {
            return false;
        }
        if (dz < 0) {
            return true;
        }
        if (dy < 0) {
            return true;
        }
        return dy == 0 && dx < 0;
    }

    private static int labelAt(int[] labels,
                               int width,
                               int height,
                               int depth,
                               int x,
                               int y,
                               int z) {
        if (x < 0 || y < 0 || z < 0 || x >= width || y >= height || z >= depth) {
            return 0;
        }
        return labels[z * width * height + y * width + x];
    }

    private static int minNonZero(int current, int candidate) {
        if (candidate <= 0) {
            return current;
        }
        if (current == 0) {
            return candidate;
        }
        return Math.min(current, candidate);
    }

    private static void unionIfPresent(UnionFind uf, int a, int b) {
        if (b > 0 && a != b) {
            uf.union(a, b);
        }
    }

    private static ImagePlus emptyLabelMapLike(ImagePlus reference) {
        int width = reference == null ? 1 : Math.max(1, reference.getWidth());
        int height = reference == null ? 1 : Math.max(1, reference.getHeight());
        int stackSize = reference == null ? 1 : Math.max(1, reference.getStackSize());
        ImageStack stack = new ImageStack(width, height);
        for (int i = 0; i < stackSize; i++) {
            stack.addSlice("z" + (i + 1), new ShortProcessor(width, height));
        }
        ImagePlus label = new ImagePlus("Object Segmentation Sweep labels", stack);
        if (reference != null) {
            Calibration calibration = reference.getCalibration();
            if (calibration != null) {
                label.setCalibration(calibration.copy());
            }
            int channels = Math.max(1, reference.getNChannels());
            int slices = Math.max(1, reference.getNSlices());
            int frames = Math.max(1, reference.getNFrames());
            if (channels * slices * frames == stackSize) {
                label.setDimensions(channels, slices, frames);
                label.setOpenAsHyperStack(reference.isHyperStack());
            }
        }
        return label;
    }

    private static final class UnionFind {
        private int[] parent = new int[1024];
        private byte[] rank = new byte[1024];
        private int next = 1;

        int add() {
            ensureCapacity(next + 1);
            parent[next] = next;
            return next++;
        }

        int size() {
            return next;
        }

        int find(int label) {
            int root = label;
            while (parent[root] != root) {
                root = parent[root];
            }
            while (parent[label] != label) {
                int nextLabel = parent[label];
                parent[label] = root;
                label = nextLabel;
            }
            return root;
        }

        void union(int a, int b) {
            int rootA = find(a);
            int rootB = find(b);
            if (rootA == rootB) {
                return;
            }
            if (rank[rootA] < rank[rootB]) {
                parent[rootA] = rootB;
            } else if (rank[rootA] > rank[rootB]) {
                parent[rootB] = rootA;
            } else {
                parent[rootB] = rootA;
                rank[rootA]++;
            }
        }

        private void ensureCapacity(int required) {
            if (required <= parent.length) {
                return;
            }
            int newLength = parent.length;
            while (newLength < required) {
                newLength *= 2;
            }
            int[] newParent = new int[newLength];
            byte[] newRank = new byte[newLength];
            System.arraycopy(parent, 0, newParent, 0, parent.length);
            System.arraycopy(rank, 0, newRank, 0, rank.length);
            parent = newParent;
            rank = newRank;
        }
    }
}
