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
        /**
         * Finite voxels in processing order: value descending, index ascending
         * within a value. Non-finite voxels are left out and never activated.
         */
        private int[] order;
        private int finiteCount;
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
        /** Per-level scratch: group number of a root while its level is snapshotted, else -1. */
        private final int[] groupOfRoot;
        private int[] levelRoots = new int[16];
        private int[] groupRoots = new int[16];
        private int[] groupCounts = new int[16];
        private int[] groupVoxels = new int[16];
        /** Neighbour index offsets in the same order as the bounds-checked loops. */
        private final int[] faceOffsets;
        private final int[] unionOffsets;
        private final int[] planarFaceOffsets;
        private final int[] planarUnionOffsets;
        private int work;

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
            this.groupOfRoot = new int[voxelCount];
            Arrays.fill(groupOfRoot, -1);
            this.faceOffsets = new int[] { -1, 1, -width, width, -plane, plane };
            this.planarFaceOffsets = new int[] { -1, 1, -width, width };
            if (connectivity == SegSweepLabeller.Connectivity.SIX) {
                this.unionOffsets = faceOffsets;
                this.planarUnionOffsets = planarFaceOffsets;
            } else {
                this.unionOffsets = new int[26];
                this.planarUnionOffsets = new int[8];
                int all = 0;
                int planar = 0;
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            int offset = dz * plane + dy * width + dx;
                            unionOffsets[all++] = offset;
                            if (dz == 0) planarUnionOffsets[planar++] = offset;
                        }
                    }
                }
            }
            readIntensities();
        }

        void build() {
            checkCancelled();
            if (progress != null) {
                progress.accept(Integer.valueOf(0), Integer.valueOf(voxelCount));
            }
            order = sortedOrder();
            checkCancelled();

            int at = 0;
            int lastProgress = -1;
            while (at < finiteCount) {
                checkCancelled();
                float level = intensities[order[at]];
                int end = at + 1;
                while (end < finiteCount
                        && Float.compare(intensities[order[end]], level) == 0) {
                    end++;
                }
                for (int i = at; i < end; i++) {
                    // One flat level (a mostly-zero stack, a constant image) can hold
                    // millions of voxels; check for cancel inside it, not only between levels.
                    pollCancel();
                    activate(order[i]);
                }
                snapshotLevel(level, at, end);
                at = end;
                int percent = voxelCount == 0 ? 100 : (int) ((100L * at) / voxelCount);
                if (progress != null && percent != lastProgress) {
                    progress.accept(Integer.valueOf(at), Integer.valueOf(voxelCount));
                    lastProgress = percent;
                }
            }
        }

        /**
         * Finite voxel indices ordered by value descending, then index ascending.
         * This is the order the earlier stable comparator sort produced, so node
         * IDs and label numbers are unchanged. {@code Float.compare} order is kept:
         * 0.0 and -0.0 are different levels, 0.0 first.
         */
        private int[] sortedOrder() {
            boolean smallIntegers = true;
            int finite = 0;
            for (int i = 0; i < voxelCount; i++) {
                float value = intensities[i];
                if (!Float.isFinite(value)) continue;
                finite++;
                if (smallIntegers && !isSmallInteger(value)) smallIntegers = false;
            }
            finiteCount = finite;
            checkCancelled();
            return smallIntegers ? countingOrder() : packedOrder();
        }

        private static boolean isSmallInteger(float value) {
            return value >= 0.0f && value <= 65535.0f && value == (float) (int) value
                    && Float.floatToRawIntBits(value) != 0x80000000;
        }

        /** Counting sort over 0..65535 (every 8- and 16-bit image). */
        private int[] countingOrder() {
            int[] start = new int[65537];
            for (int i = 0; i < voxelCount; i++) {
                float value = intensities[i];
                if (Float.isFinite(value)) start[65535 - (int) value + 1]++;
            }
            for (int b = 0; b < 65536; b++) start[b + 1] += start[b];
            int[] sorted = new int[finiteCount];
            for (int i = 0; i < voxelCount; i++) {
                if ((i & 0xFFFF) == 0) checkCancelled();
                float value = intensities[i];
                if (Float.isFinite(value)) sorted[start[65535 - (int) value]++] = i;
            }
            return sorted;
        }

        /**
         * Any other data: one long per finite voxel holding the inverted sortable
         * float bits above the index, so an ascending sort gives value descending
         * and index ascending.
         */
        private int[] packedOrder() {
            long[] keys = new long[finiteCount];
            int at = 0;
            for (int i = 0; i < voxelCount; i++) {
                float value = intensities[i];
                if (!Float.isFinite(value)) continue;
                int bits = Float.floatToRawIntBits(value);
                bits ^= (bits >> 31) & 0x7FFFFFFF;
                keys[at++] = ((long) ~bits << 32) | (long) i;
            }
            checkCancelled();
            Arrays.sort(keys);
            checkCancelled();
            int[] sorted = new int[finiteCount];
            for (int i = 0; i < finiteCount; i++) {
                sorted[i] = (int) keys[i];
            }
            return sorted;
        }

        private void checkCancelled() {
            if (Thread.currentThread().isInterrupted()
                    || (cancelCheck != null && cancelCheck.getAsBoolean())) {
                throw new CancellationException("Component-tree construction was cancelled.");
            }
        }

        /** Cheap per-voxel cancel poll: one real check every 65536 calls. */
        private void pollCancel() {
            if ((++work & 0xFFFF) == 0) {
                checkCancelled();
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
                }
            }
        }

        private void activate(int index) {
            active[index] = true;
            parent[index] = index;
            currentNode[index] = -1;
            touchedAtLevel[index] = true;
            pendingChildren[index] = null;
            int z = index / plane;
            int rem = index - z * plane;
            int y = rem / width;
            int x = rem - y * width;
            if (x > 0 && y > 0 && x < width - 1 && y < height - 1
                    && (depth == 1 || (z > 0 && z < depth - 1))) {
                // Every neighbour exists: same neighbours, same order, no bounds checks.
                int[] faces = depth == 1 ? planarFaceOffsets : faceOffsets;
                int[] unions = depth == 1 ? planarUnionOffsets : unionOffsets;
                int faceNeighbours = 0;
                for (int i = 0; i < faces.length; i++) {
                    if (active[index + faces[i]]) faceNeighbours++;
                }
                attrs.init(index, intensities[index], x, y, z, 6.0 - 2.0 * faceNeighbours);
                int root = index;
                for (int i = 0; i < unions.length; i++) {
                    int neighbour = index + unions[i];
                    if (active[neighbour]) root = unionWithRoot(root, neighbour);
                }
                return;
            }
            int faceNeighbours = activeFaceNeighbourCount(x, y, z);
            attrs.init(index, intensities[index], x, y, z, 6.0 - 2.0 * faceNeighbours);
            int root = index;
            if (connectivity == SegSweepLabeller.Connectivity.SIX) {
                root = unionNeighbour(root, x - 1, y, z);
                root = unionNeighbour(root, x + 1, y, z);
                root = unionNeighbour(root, x, y - 1, z);
                root = unionNeighbour(root, x, y + 1, z);
                root = unionNeighbour(root, x, y, z - 1);
                unionNeighbour(root, x, y, z + 1);
                return;
            }
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        root = unionNeighbour(root, x + dx, y + dy, z + dz);
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

        /** Unions the new voxel's component (root {@code root}) with an active neighbour; returns the root after. */
        private int unionNeighbour(int root, int x, int y, int z) {
            int neighbour = indexOf(x, y, z);
            if (neighbour >= 0 && active[neighbour]) {
                return unionWithRoot(root, neighbour);
            }
            return root;
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

        /**
         * Union by rank of the component rooted at {@code rootA} (the voxel being
         * activated; its root is tracked by the caller rather than found again)
         * with the component holding {@code b}. Returns the surviving root.
         */
        private int unionWithRoot(int rootA, int b) {
            int rootB = parent[b] == b ? b : find(b);
            if (rootA == rootB) return rootA;
            IntList mergedChildren = mergedLineage(rootA, rootB);
            if (rank[rootA] < rank[rootB]) {
                parent[rootA] = rootB;
                attrs.merge(rootB, rootA);
                adoptUnionState(rootB, rootA, mergedChildren);
                return rootB;
            } else if (rank[rootA] > rank[rootB]) {
                parent[rootB] = rootA;
                attrs.merge(rootA, rootB);
                adoptUnionState(rootA, rootB, mergedChildren);
                return rootA;
            } else {
                parent[rootB] = rootA;
                rank[rootA]++;
                attrs.merge(rootA, rootB);
                adoptUnionState(rootA, rootB, mergedChildren);
                return rootA;
            }
        }

        /**
         * The nodes two roots carry into the current level, as one list (null when
         * there are none): a root touched at this level brings its pending
         * children, an untouched root brings its current node.
         */
        private IntList mergedLineage(int rootA, int rootB) {
            IntList merged = IntList.merge(
                    touchedAtLevel[rootA] ? pendingChildren[rootA] : null,
                    touchedAtLevel[rootB] ? pendingChildren[rootB] : null);
            int singleA = touchedAtLevel[rootA] ? -1 : currentNode[rootA];
            int singleB = touchedAtLevel[rootB] ? -1 : currentNode[rootB];
            if (singleA >= 0 || singleB >= 0) {
                if (merged == null) merged = new IntList();
                if (singleA >= 0) merged.add(singleA);
                if (singleB >= 0) merged.add(singleB);
            }
            return merged;
        }

        private void adoptUnionState(int into, int from, IntList children) {
            touchedAtLevel[into] = true;
            pendingChildren[into] = children;
            currentNode[into] = -1;
            touchedAtLevel[from] = false;
            pendingChildren[from] = null;
            currentNode[from] = -1;
        }

        /**
         * Emits one node per root touched at this level. Nodes are numbered in the
         * iteration order of a {@code HashMap} keyed by root and filled in
         * first-seen order, as before, so node IDs are unchanged; a level with a
         * single root skips the map. Each node's voxels keep the level order
         * (ascending index).
         */
        private void snapshotLevel(float level, int start, int end) {
            int size = end - start;
            if (levelRoots.length < size) {
                levelRoots = new int[Math.max(size, levelRoots.length * 2)];
            }
            int groups = 0;
            for (int at = start; at < end; at++) {
                pollCancel();
                int root = find(order[at]);
                levelRoots[at - start] = root;
                int group = groupOfRoot[root];
                if (group < 0) {
                    if (groupRoots.length == groups) {
                        groupRoots = Arrays.copyOf(groupRoots, groups * 2);
                        groupCounts = Arrays.copyOf(groupCounts, groups * 2);
                    }
                    group = groups++;
                    groupOfRoot[root] = group;
                    groupRoots[group] = root;
                    groupCounts[group] = 0;
                }
                groupCounts[group]++;
            }
            if (groups == 1) {
                emitNode(level, groupRoots[0], Arrays.copyOfRange(order, start, end));
                groupOfRoot[groupRoots[0]] = -1;
                return;
            }

            // Group the level's voxels by root, keeping level order inside a group.
            if (groupVoxels.length < size) {
                groupVoxels = new int[Math.max(size, groupVoxels.length * 2)];
            }
            int offset = 0;
            for (int g = 0; g < groups; g++) {
                int count = groupCounts[g];
                groupCounts[g] = offset;
                offset += count;
            }
            for (int i = 0; i < size; i++) {
                int group = groupOfRoot[levelRoots[i]];
                groupVoxels[groupCounts[group]++] = order[start + i];
            }
            // groupCounts[g] is now the end of group g; its start is the previous end.
            Map<Integer, Integer> numbering = new HashMap<Integer, Integer>();
            for (int g = 0; g < groups; g++) {
                numbering.put(Integer.valueOf(groupRoots[g]), Integer.valueOf(g));
            }
            for (Map.Entry<Integer, Integer> entry : numbering.entrySet()) {
                pollCancel();
                int g = entry.getValue().intValue();
                int from = g == 0 ? 0 : groupCounts[g - 1];
                emitNode(level, entry.getKey().intValue(),
                        Arrays.copyOfRange(groupVoxels, from, groupCounts[g]));
            }
            for (int g = 0; g < groups; g++) {
                groupOfRoot[groupRoots[g]] = -1;
            }
        }

        private void emitNode(float level, int root, int[] voxels) {
            ComponentTree.NodeData node = new ComponentTree.NodeData(nodes.size(),
                    level,
                    attrs.i(root, RootAttributes.VOXEL_COUNT),
                    attrs.d(root, RootAttributes.INTENSITY_SUM),
                    attrs.d(root, RootAttributes.MAX_INTENSITY),
                    attrs.i(root, RootAttributes.MIN_X),
                    attrs.i(root, RootAttributes.MIN_Y),
                    attrs.i(root, RootAttributes.MIN_Z),
                    attrs.i(root, RootAttributes.MAX_X),
                    attrs.i(root, RootAttributes.MAX_Y),
                    attrs.i(root, RootAttributes.MAX_Z),
                    attrs.d(root, RootAttributes.SURFACE_AREA),
                    attrs.d(root, RootAttributes.X_SUM),
                    attrs.d(root, RootAttributes.Y_SUM),
                    attrs.d(root, RootAttributes.Z_SUM),
                    attrs.d(root, RootAttributes.XX_SUM),
                    attrs.d(root, RootAttributes.YY_SUM),
                    attrs.d(root, RootAttributes.ZZ_SUM),
                    attrs.d(root, RootAttributes.XY_SUM),
                    attrs.d(root, RootAttributes.XZ_SUM),
                    attrs.d(root, RootAttributes.YZ_SUM),
                    voxels);
            nodes.add(node);
            IntList children = pendingChildren[root];
            if (children != null) {
                int[] childIds = children.toArray();
                sortSmall(childIds);
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

    /** Ascending sort; insertion sort for the short child lists that dominate. */
    static void sortSmall(int[] values) {
        if (values.length > 24) {
            Arrays.sort(values);
            return;
        }
        for (int i = 1; i < values.length; i++) {
            int value = values[i];
            int j = i - 1;
            while (j >= 0 && values[j] > value) {
                values[j + 1] = values[j];
                j--;
            }
            values[j + 1] = value;
        }
    }

    /**
     * Running attributes of every union-find root. Each voxel's 12 doubles and 7
     * ints sit next to each other (in pages of 65536 voxels, so any image size
     * that fits an int index still fits) so a merge touches a few cache lines
     * instead of 19 separate arrays. The arithmetic is unchanged.
     */
    private static final class RootAttributes {
        static final int VOXEL_COUNT = 0;
        static final int MIN_X = 1;
        static final int MIN_Y = 2;
        static final int MIN_Z = 3;
        static final int MAX_X = 4;
        static final int MAX_Y = 5;
        static final int MAX_Z = 6;
        private static final int INTS = 7;

        static final int INTENSITY_SUM = 0;
        static final int MAX_INTENSITY = 1;
        static final int SURFACE_AREA = 2;
        static final int X_SUM = 3;
        static final int Y_SUM = 4;
        static final int Z_SUM = 5;
        static final int XX_SUM = 6;
        static final int YY_SUM = 7;
        static final int ZZ_SUM = 8;
        static final int XY_SUM = 9;
        static final int XZ_SUM = 10;
        static final int YZ_SUM = 11;
        private static final int DOUBLES = 12;

        private static final int PAGE_BITS = 16;
        private static final int PAGE_MASK = (1 << PAGE_BITS) - 1;

        private final int[][] ints;
        private final double[][] doubles;

        RootAttributes(int size) {
            int pages = (int) (((long) size + PAGE_MASK) >>> PAGE_BITS);
            ints = new int[pages][];
            doubles = new double[pages][];
            for (int page = 0; page < pages; page++) {
                int length = Math.min(1 << PAGE_BITS, size - (page << PAGE_BITS));
                ints[page] = new int[length * INTS];
                doubles[page] = new double[length * DOUBLES];
            }
        }

        int i(int index, int field) {
            return ints[index >>> PAGE_BITS][(index & PAGE_MASK) * INTS + field];
        }

        double d(int index, int field) {
            return doubles[index >>> PAGE_BITS][(index & PAGE_MASK) * DOUBLES + field];
        }

        void init(int index, float intensity, int x, int y, int z, double surface) {
            int[] n = ints[index >>> PAGE_BITS];
            int ni = (index & PAGE_MASK) * INTS;
            n[ni + VOXEL_COUNT] = 1;
            n[ni + MIN_X] = x;
            n[ni + MIN_Y] = y;
            n[ni + MIN_Z] = z;
            n[ni + MAX_X] = x;
            n[ni + MAX_Y] = y;
            n[ni + MAX_Z] = z;
            double[] v = doubles[index >>> PAGE_BITS];
            int vi = (index & PAGE_MASK) * DOUBLES;
            v[vi + INTENSITY_SUM] = intensity;
            v[vi + MAX_INTENSITY] = intensity;
            v[vi + SURFACE_AREA] = surface;
            v[vi + X_SUM] = x;
            v[vi + Y_SUM] = y;
            v[vi + Z_SUM] = z;
            v[vi + XX_SUM] = (double) x * (double) x;
            v[vi + YY_SUM] = (double) y * (double) y;
            v[vi + ZZ_SUM] = (double) z * (double) z;
            v[vi + XY_SUM] = (double) x * (double) y;
            v[vi + XZ_SUM] = (double) x * (double) z;
            v[vi + YZ_SUM] = (double) y * (double) z;
        }

        void merge(int into, int from) {
            int[] a = ints[into >>> PAGE_BITS];
            int ai = (into & PAGE_MASK) * INTS;
            int[] b = ints[from >>> PAGE_BITS];
            int bi = (from & PAGE_MASK) * INTS;
            a[ai + VOXEL_COUNT] += b[bi + VOXEL_COUNT];
            if (b[bi + MIN_X] < a[ai + MIN_X]) a[ai + MIN_X] = b[bi + MIN_X];
            if (b[bi + MIN_Y] < a[ai + MIN_Y]) a[ai + MIN_Y] = b[bi + MIN_Y];
            if (b[bi + MIN_Z] < a[ai + MIN_Z]) a[ai + MIN_Z] = b[bi + MIN_Z];
            if (b[bi + MAX_X] > a[ai + MAX_X]) a[ai + MAX_X] = b[bi + MAX_X];
            if (b[bi + MAX_Y] > a[ai + MAX_Y]) a[ai + MAX_Y] = b[bi + MAX_Y];
            if (b[bi + MAX_Z] > a[ai + MAX_Z]) a[ai + MAX_Z] = b[bi + MAX_Z];
            double[] u = doubles[into >>> PAGE_BITS];
            int ui = (into & PAGE_MASK) * DOUBLES;
            double[] w = doubles[from >>> PAGE_BITS];
            int wi = (from & PAGE_MASK) * DOUBLES;
            u[ui + INTENSITY_SUM] += w[wi + INTENSITY_SUM];
            if (w[wi + MAX_INTENSITY] > u[ui + MAX_INTENSITY]) {
                u[ui + MAX_INTENSITY] = w[wi + MAX_INTENSITY];
            }
            u[ui + SURFACE_AREA] += w[wi + SURFACE_AREA];
            u[ui + X_SUM] += w[wi + X_SUM];
            u[ui + Y_SUM] += w[wi + Y_SUM];
            u[ui + Z_SUM] += w[wi + Z_SUM];
            u[ui + XX_SUM] += w[wi + XX_SUM];
            u[ui + YY_SUM] += w[wi + YY_SUM];
            u[ui + ZZ_SUM] += w[wi + ZZ_SUM];
            u[ui + XY_SUM] += w[wi + XY_SUM];
            u[ui + XZ_SUM] += w[wi + XZ_SUM];
            u[ui + YZ_SUM] += w[wi + YZ_SUM];
        }
    }

    private static final class IntList {
        private int[] values = new int[4];
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

        /** Multiset union of two child lists (order is irrelevant; callers sort). Null is empty. */
        static IntList merge(IntList a, IntList b) {
            if (a == null) return b;
            if (b == null) return a;
            IntList left = a;
            IntList right = b;
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
