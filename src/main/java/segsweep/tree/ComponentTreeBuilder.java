/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.tree;

import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import segsweep.SegSweepLabeller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

public final class ComponentTreeBuilder {
    private ComponentTreeBuilder() {}

    public static ComponentTree build(ImagePlus source, SegSweepLabeller.Connectivity connectivity) {
        return build(source, connectivity, null, null);
    }

    public static ComponentTree build(ImagePlus source,
                                      SegSweepLabeller.Connectivity connectivity,
                                      BooleanSupplier cancelCheck,
                                      BiConsumer<Integer, Integer> progress) {
        if (source == null || source.getStack() == null || source.getStackSize() == 0) {
            throw new IllegalArgumentException("source image must have a non-empty stack");
        }
        SegSweepLabeller.Connectivity safeConnectivity = connectivity == null
                ? SegSweepLabeller.DEFAULT_CONNECTIVITY : connectivity;
        BuilderState state = new BuilderState(source, safeConnectivity, cancelCheck, progress);
        state.build();
        Calibration calibration = source.getCalibration();
        return new ComponentTree(source.getWidth(),
                source.getHeight(),
                source.getStackSize(),
                calibration == null ? null : calibration.copy(),
                safeConnectivity,
                state.nodes);
    }

    private static final class BuilderState {
        private final ImagePlus source;
        private final SegSweepLabeller.Connectivity connectivity;
        private final int width;
        private final int height;
        private final int depth;
        private final int plane;
        private final int voxelCount;
        private final float[] intensities;
        private final Integer[] order;
        private final boolean[] active;
        private final int[] parent;
        private final byte[] rank;
        private final RootAttributes attrs;
        private final List<ComponentTree.NodeData> nodes = new ArrayList<ComponentTree.NodeData>();
        private final int[] currentNode;
        private final boolean[] touchedAtLevel;
        private final IntList[] pendingChildren;
        private final BooleanSupplier cancelCheck;
        private final BiConsumer<Integer, Integer> progress;

        BuilderState(ImagePlus source,
                     SegSweepLabeller.Connectivity connectivity,
                     BooleanSupplier cancelCheck,
                     BiConsumer<Integer, Integer> progress) {
            this.source = source;
            this.connectivity = connectivity;
            this.width = source.getWidth();
            this.height = source.getHeight();
            this.depth = source.getStackSize();
            this.plane = width * height;
            long total = (long) width * (long) height * (long) depth;
            if (total > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Image is too large for component-tree array indexing: "
                        + total + " voxels.");
            }
            this.voxelCount = (int) total;
            this.intensities = new float[voxelCount];
            this.order = new Integer[voxelCount];
            this.active = new boolean[voxelCount];
            this.parent = new int[voxelCount];
            this.rank = new byte[voxelCount];
            this.attrs = new RootAttributes(voxelCount);
            this.currentNode = new int[voxelCount];
            Arrays.fill(currentNode, -1);
            this.touchedAtLevel = new boolean[voxelCount];
            this.pendingChildren = new IntList[voxelCount];
            this.cancelCheck = cancelCheck;
            this.progress = progress;
            readIntensities();
        }

        void build() {
            checkCancelled();
            if (progress != null) {
                progress.accept(Integer.valueOf(0), Integer.valueOf(order.length));
            }
            Arrays.sort(order, new java.util.Comparator<Integer>() {
                private int comparisons;

                @Override public int compare(Integer a, Integer b) {
                    if ((comparisons++ & 16383) == 0) checkCancelled();
                    float left = intensities[a.intValue()];
                    float right = intensities[b.intValue()];
                    boolean leftFinite = Float.isFinite(left);
                    boolean rightFinite = Float.isFinite(right);
                    if (leftFinite != rightFinite) {
                        return leftFinite ? -1 : 1;
                    }
                    return -Float.compare(left, right);
                }
            });
            checkCancelled();

            int at = 0;
            int lastProgress = -1;
            while (at < order.length) {
                checkCancelled();
                int first = order[at].intValue();
                float level = intensities[first];
                if (!Float.isFinite(level)) {
                    break;
                }
                int end = at + 1;
                while (end < order.length
                        && Float.compare(intensities[order[end].intValue()], level) == 0) {
                    end++;
                }
                for (int i = at; i < end; i++) {
                    activate(order[i].intValue());
                }
                snapshotLevel(level, at, end);
                at = end;
                int percent = order.length == 0 ? 100 : (int) ((100L * at) / order.length);
                if (progress != null && percent != lastProgress) {
                    progress.accept(Integer.valueOf(at), Integer.valueOf(order.length));
                    lastProgress = percent;
                }
            }
        }

        private void checkCancelled() {
            if (cancelCheck != null && cancelCheck.getAsBoolean()) {
                throw new CancellationException("Component-tree construction was cancelled.");
            }
        }

        private void readIntensities() {
            ImageStack stack = source.getStack();
            for (int z = 0; z < depth; z++) {
                ImageProcessor processor = stack.getProcessor(z + 1);
                if (processor == null || processor.getPixelCount() < plane) {
                    throw new IllegalArgumentException("source stack has an invalid slice at z=" + z);
                }
                for (int i = 0; i < plane; i++) {
                    int index = z * plane + i;
                    if ((index & 65535) == 0) checkCancelled();
                    intensities[index] = processor.getf(i);
                    order[index] = Integer.valueOf(index);
                }
            }
        }

        private void activate(int index) {
            active[index] = true;
            parent[index] = index;
            currentNode[index] = -1;
            touchedAtLevel[index] = true;
            pendingChildren[index] = new IntList();
            int z = index / plane;
            int rem = index - z * plane;
            int y = rem / width;
            int x = rem - y * width;
            int faceNeighbours = activeFaceNeighbourCount(x, y, z);
            attrs.init(index, intensities[index], x, y, z, 6.0 - 2.0 * faceNeighbours);
            if (connectivity == SegSweepLabeller.Connectivity.SIX) {
                unionNeighbour(index, x - 1, y, z);
                unionNeighbour(index, x + 1, y, z);
                unionNeighbour(index, x, y - 1, z);
                unionNeighbour(index, x, y + 1, z);
                unionNeighbour(index, x, y, z - 1);
                unionNeighbour(index, x, y, z + 1);
                return;
            }
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        unionNeighbour(index, x + dx, y + dy, z + dz);
                    }
                }
            }
        }

        private int activeFaceNeighbourCount(int x, int y, int z) {
            int count = 0;
            if (isActive(x - 1, y, z)) count++;
            if (isActive(x + 1, y, z)) count++;
            if (isActive(x, y - 1, z)) count++;
            if (isActive(x, y + 1, z)) count++;
            if (isActive(x, y, z - 1)) count++;
            if (isActive(x, y, z + 1)) count++;
            return count;
        }

        private void unionNeighbour(int index, int x, int y, int z) {
            int neighbour = indexOf(x, y, z);
            if (neighbour >= 0 && active[neighbour]) {
                union(index, neighbour);
            }
        }

        private boolean isActive(int x, int y, int z) {
            int index = indexOf(x, y, z);
            return index >= 0 && active[index];
        }

        private int indexOf(int x, int y, int z) {
            if (x < 0 || y < 0 || z < 0 || x >= width || y >= height || z >= depth) {
                return -1;
            }
            return z * plane + y * width + x;
        }

        private int find(int index) {
            int root = index;
            while (parent[root] != root) {
                root = parent[root];
            }
            while (parent[index] != index) {
                int next = parent[index];
                parent[index] = root;
                index = next;
            }
            return root;
        }

        private void union(int a, int b) {
            int rootA = find(a);
            int rootB = find(b);
            if (rootA == rootB) return;
            IntList childrenA = lineageAtCurrentLevel(rootA);
            IntList childrenB = lineageAtCurrentLevel(rootB);
            IntList mergedChildren = IntList.merge(childrenA, childrenB);
            if (rank[rootA] < rank[rootB]) {
                parent[rootA] = rootB;
                attrs.merge(rootB, rootA);
                adoptUnionState(rootB, rootA, mergedChildren);
            } else if (rank[rootA] > rank[rootB]) {
                parent[rootB] = rootA;
                attrs.merge(rootA, rootB);
                adoptUnionState(rootA, rootB, mergedChildren);
            } else {
                parent[rootB] = rootA;
                rank[rootA]++;
                attrs.merge(rootA, rootB);
                adoptUnionState(rootA, rootB, mergedChildren);
            }
        }

        private IntList lineageAtCurrentLevel(int root) {
            if (touchedAtLevel[root]) {
                IntList children = pendingChildren[root];
                return children == null ? new IntList() : children;
            }
            IntList children = new IntList();
            if (currentNode[root] >= 0) {
                children.add(currentNode[root]);
            }
            return children;
        }

        private void adoptUnionState(int into, int from, IntList children) {
            touchedAtLevel[into] = true;
            pendingChildren[into] = children;
            currentNode[into] = -1;
            touchedAtLevel[from] = false;
            pendingChildren[from] = null;
            currentNode[from] = -1;
        }

        private void snapshotLevel(float level, int start, int end) {
            Map<Integer, IntList> voxelsByRoot = new HashMap<Integer, IntList>();
            for (int at = start; at < end; at++) {
                int voxel = order[at].intValue();
                int root = find(voxel);
                IntList voxels = voxelsByRoot.get(Integer.valueOf(root));
                if (voxels == null) {
                    voxels = new IntList();
                    voxelsByRoot.put(Integer.valueOf(root), voxels);
                }
                voxels.add(voxel);
            }

            for (Map.Entry<Integer, IntList> entry : voxelsByRoot.entrySet()) {
                int root = entry.getKey().intValue();
                int[] voxels = entry.getValue().toArray();
                ComponentTree.NodeData node = new ComponentTree.NodeData(nodes.size(),
                        level,
                        attrs.voxelCount[root],
                        attrs.intensitySum[root],
                        attrs.maxIntensity[root],
                        attrs.minX[root],
                        attrs.minY[root],
                        attrs.minZ[root],
                        attrs.maxX[root],
                        attrs.maxY[root],
                        attrs.maxZ[root],
                        attrs.surfaceArea[root],
                        attrs.xSum[root],
                        attrs.ySum[root],
                        attrs.zSum[root],
                        attrs.xxSum[root],
                        attrs.yySum[root],
                        attrs.zzSum[root],
                        attrs.xySum[root],
                        attrs.xzSum[root],
                        attrs.yzSum[root],
                        voxels);
                nodes.add(node);
                IntList children = pendingChildren[root];
                if (children != null) {
                    int[] childIds = children.toArray();
                    Arrays.sort(childIds);
                    for (int i = 0; i < childIds.length; i++) {
                        int childId = childIds[i];
                        if (childId < 0 || childId >= node.id) continue;
                        ComponentTree.NodeData child = nodes.get(childId);
                        child.parentId = node.id;
                        node.childIds.add(Integer.valueOf(childId));
                    }
                }
                currentNode[root] = node.id;
                touchedAtLevel[root] = false;
                pendingChildren[root] = null;
            }
        }
    }

    private static final class RootAttributes {
        final int[] voxelCount;
        final double[] intensitySum;
        final double[] maxIntensity;
        final int[] minX;
        final int[] minY;
        final int[] minZ;
        final int[] maxX;
        final int[] maxY;
        final int[] maxZ;
        final double[] surfaceArea;
        final double[] xSum;
        final double[] ySum;
        final double[] zSum;
        final double[] xxSum;
        final double[] yySum;
        final double[] zzSum;
        final double[] xySum;
        final double[] xzSum;
        final double[] yzSum;

        RootAttributes(int size) {
            voxelCount = new int[size];
            intensitySum = new double[size];
            maxIntensity = new double[size];
            minX = new int[size];
            minY = new int[size];
            minZ = new int[size];
            maxX = new int[size];
            maxY = new int[size];
            maxZ = new int[size];
            surfaceArea = new double[size];
            xSum = new double[size];
            ySum = new double[size];
            zSum = new double[size];
            xxSum = new double[size];
            yySum = new double[size];
            zzSum = new double[size];
            xySum = new double[size];
            xzSum = new double[size];
            yzSum = new double[size];
        }

        void init(int index, float intensity, int x, int y, int z, double surface) {
            voxelCount[index] = 1;
            intensitySum[index] = intensity;
            maxIntensity[index] = intensity;
            minX[index] = x;
            minY[index] = y;
            minZ[index] = z;
            maxX[index] = x;
            maxY[index] = y;
            maxZ[index] = z;
            surfaceArea[index] = surface;
            xSum[index] = x;
            ySum[index] = y;
            zSum[index] = z;
            xxSum[index] = x * x;
            yySum[index] = y * y;
            zzSum[index] = z * z;
            xySum[index] = x * y;
            xzSum[index] = x * z;
            yzSum[index] = y * z;
        }

        void merge(int into, int from) {
            voxelCount[into] += voxelCount[from];
            intensitySum[into] += intensitySum[from];
            if (maxIntensity[from] > maxIntensity[into]) maxIntensity[into] = maxIntensity[from];
            if (minX[from] < minX[into]) minX[into] = minX[from];
            if (minY[from] < minY[into]) minY[into] = minY[from];
            if (minZ[from] < minZ[into]) minZ[into] = minZ[from];
            if (maxX[from] > maxX[into]) maxX[into] = maxX[from];
            if (maxY[from] > maxY[into]) maxY[into] = maxY[from];
            if (maxZ[from] > maxZ[into]) maxZ[into] = maxZ[from];
            surfaceArea[into] += surfaceArea[from];
            xSum[into] += xSum[from];
            ySum[into] += ySum[from];
            zSum[into] += zSum[from];
            xxSum[into] += xxSum[from];
            yySum[into] += yySum[from];
            zzSum[into] += zzSum[from];
            xySum[into] += xySum[from];
            xzSum[into] += xzSum[from];
            yzSum[into] += yzSum[from];
        }
    }

    private static final class IntList {
        private int[] values = new int[16];
        private int size;

        void add(int value) {
            if (size == values.length) {
                values = Arrays.copyOf(values, values.length * 2);
            }
            values[size++] = value;
        }

        int[] toArray() {
            return Arrays.copyOf(values, size);
        }

        static IntList merge(IntList a, IntList b) {
            IntList left = a == null ? new IntList() : a;
            IntList right = b == null ? new IntList() : b;
            if (left.size < right.size) {
                IntList swap = left;
                left = right;
                right = swap;
            }
            for (int i = 0; i < right.size; i++) {
                left.add(right.values[i]);
            }
            return left;
        }
    }
}
