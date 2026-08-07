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
import ij.measure.Calibration;
import segsweep.SegSweepLabeller;
import segsweep.SweepRefusedException;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.concurrent.CancellationException;

/**
 * Component tree over one cropped image.
 *
 * <p>3D Objects Counter+ applies morphology predicates directly to the objects
 * produced by a thresholded connected-component run, ANDs every predicate, and
 * rejects non-finite feature values. This tree follows that direct Salembier
 * filtering rule for non-increasing attributes instead of min/max/viterbi
 * propagation: a node is tested using its own measured value at the requested
 * threshold cut. That is the closest ij-only analogue of 3D Objects Counter+'s
 * post-labelling filter behaviour.</p>
 */
public final class ComponentTree {
    private static final int MAX_16_BIT_LABEL = 65535;
    private static final int MAX_EXACT_FERET_VOXELS = 4096;

    private final int width;
    private final int height;
    private final int depth;
    private final Calibration calibration;
    private final SegSweepLabeller.Connectivity connectivity;
    private final List<NodeData> nodes;
    private int feretComputationCount;

    ComponentTree(int width,
                  int height,
                  int depth,
                  Calibration calibration,
                  SegSweepLabeller.Connectivity connectivity,
                  List<NodeData> nodes) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.calibration = calibration == null ? null : calibration.copy();
        this.connectivity = connectivity == null
                ? SegSweepLabeller.DEFAULT_CONNECTIVITY : connectivity;
        this.nodes = Collections.unmodifiableList(new ArrayList<NodeData>(nodes));
    }

    public static ComponentTree build(ImagePlus source, SegSweepLabeller.Connectivity connectivity) {
        return ComponentTreeBuilder.build(source, connectivity);
    }

    public static ComponentTree build(ImagePlus source,
                                      SegSweepLabeller.Connectivity connectivity,
                                      BooleanSupplier cancelCheck,
                                      BiConsumer<Integer, Integer> progress) {
        return ComponentTreeBuilder.build(source, connectivity, cancelCheck, progress);
    }

    public ComponentTreeResult query(ComponentTreeQuery query) {
        return query(query, null);
    }

    public ComponentTreeResult query(ComponentTreeQuery query,
                                     BooleanSupplier cancelCheck) {
        checkCancelled(cancelCheck, "Component-tree query was cancelled.");
        ComponentTreeQuery safeQuery = query == null
                ? ComponentTreeQuery.builder().build() : query;
        if (safeQuery.maxSize() < safeQuery.minSize()) {
            return result(ComponentTreeResult.Status.EMPTY,
                    "No nodes can satisfy minSize " + safeQuery.minSize()
                            + " with maxSize " + safeQuery.maxSize() + ".",
                    Collections.<NodeData>emptyList());
        }

        List<NodeData> candidates = nodesAtThreshold(
                safeQuery.threshold(), cancelCheck);
        List<NodeData> cheapSurvivors = new ArrayList<NodeData>();
        List<MorphologyPredicate> feretPredicates = new ArrayList<MorphologyPredicate>();
        List<MorphologyPredicate> cheapPredicates = new ArrayList<MorphologyPredicate>();
        for (MorphologyPredicate predicate : safeQuery.predicates()) {
            if (predicate.attribute() == MorphologyAttribute.FERET_DIAMETER_MAX) {
                feretPredicates.add(predicate);
            } else {
                cheapPredicates.add(predicate);
            }
        }

        for (int i = 0; i < candidates.size(); i++) {
            if ((i & 1023) == 0) {
                checkCancelled(cancelCheck, "Component-tree query was cancelled.");
            }
            NodeData node = candidates.get(i);
            int volume = node.voxelCount;
            if (volume < safeQuery.minSize() || volume > safeQuery.maxSize()) {
                continue;
            }
            if (matchesAll(node, cheapPredicates, cancelCheck)) {
                cheapSurvivors.add(node);
            }
        }

        List<NodeData> selected = new ArrayList<NodeData>();
        for (int i = 0; i < cheapSurvivors.size(); i++) {
            if ((i & 255) == 0) {
                checkCancelled(cancelCheck, "Component-tree query was cancelled.");
            }
            NodeData node = cheapSurvivors.get(i);
            if (matchesFeretPredicates(node, feretPredicates, cancelCheck)) {
                selected.add(node);
            }
        }

        if (selected.size() > MAX_16_BIT_LABEL) {
            return result(ComponentTreeResult.Status.TOO_MANY_LABELS,
                    "Selected node count " + selected.size()
                            + " exceeds the 16-bit label limit of 65535.",
                    Collections.<NodeData>emptyList(), selected.size());
        }
        if (selected.isEmpty()) {
            return result(ComponentTreeResult.Status.EMPTY,
                    "No component-tree nodes matched the query.", selected);
        }
        return result(ComponentTreeResult.Status.OK, "", selected);
    }

    /**
     * Counts matching threshold cuts in one tree sweep rather than rescanning
     * every node once per threshold. Thresholds must be finite and ascending.
     */
    public int[] objectCountsAtThresholds(double[] thresholds,
                                          ComponentTreeQuery queryTemplate) {
        return objectCountsAtThresholds(thresholds, queryTemplate, null);
    }

    public int[] objectCountsAtThresholds(double[] thresholds,
                                          ComponentTreeQuery queryTemplate,
                                          BooleanSupplier cancelCheck) {
        if (thresholds == null) {
            throw new IllegalArgumentException("thresholds must not be null");
        }
        for (int i = 0; i < thresholds.length; i++) {
            if (!Double.isFinite(thresholds[i])) {
                throw new IllegalArgumentException("thresholds must be finite");
            }
            if (i > 0 && thresholds[i] < thresholds[i - 1]) {
                throw new IllegalArgumentException("thresholds must be ascending");
            }
        }
        int[] counts = new int[thresholds.length];
        if (thresholds.length == 0) return counts;
        ComponentTreeQuery template = queryTemplate == null
                ? ComponentTreeQuery.builder().build() : queryTemplate;
        if (template.maxSize() < template.minSize()) return counts;

        List<MorphologyPredicate> feretPredicates = new ArrayList<MorphologyPredicate>();
        List<MorphologyPredicate> cheapPredicates = new ArrayList<MorphologyPredicate>();
        for (MorphologyPredicate predicate : template.predicates()) {
            if (predicate.attribute() == MorphologyAttribute.FERET_DIAMETER_MAX) {
                feretPredicates.add(predicate);
            } else {
                cheapPredicates.add(predicate);
            }
        }

        int[] changes = new int[thresholds.length + 1];
        for (int i = 0; i < nodes.size(); i++) {
            if ((i & 1023) == 0) {
                checkCancelled(cancelCheck, "Full-axis count scoring was cancelled.");
            }
            NodeData data = nodes.get(i);
            int right = lowerBound(thresholds, data.level);
            if (right <= 0) continue;
            int left = data.parentId < 0
                    ? 0 : lowerBound(thresholds, nodes.get(data.parentId).level);
            if (left >= right) continue;
            int volume = data.voxelCount;
            if (volume < template.minSize() || volume > template.maxSize()) continue;
            if (!matchesAll(data, cheapPredicates, cancelCheck)
                    || !matchesFeretPredicates(data, feretPredicates, cancelCheck)) continue;
            changes[left]++;
            changes[right]--;
        }
        int activeCount = 0;
        for (int i = 0; i < thresholds.length; i++) {
            activeCount += changes[i];
            counts[i] = labelCountForOutput(activeCount);
        }
        return counts;
    }

    static int labelCountForOutput(int count) {
        return count > MAX_16_BIT_LABEL ? -1 : count;
    }

    /** Sorted unique component-tree event levels for continuous threshold axes. */
    public double[] thresholdLevels() {
        double[] levels = new double[nodes.size()];
        int count = 0;
        for (int i = 0; i < nodes.size(); i++) {
            double level = nodes.get(i).level;
            if (Double.isFinite(level)) levels[count++] = level;
        }
        Arrays.sort(levels, 0, count);
        int unique = 0;
        for (int i = 0; i < count; i++) {
            if (unique == 0 || Double.compare(levels[i], levels[unique - 1]) != 0) {
                levels[unique++] = levels[i];
            }
        }
        return Arrays.copyOf(levels, unique);
    }

    public List<ComponentNode> nodes() {
        List<ComponentNode> views = new ArrayList<ComponentNode>(nodes.size());
        for (NodeData data : nodes) {
            views.add(new ComponentNode(this, data));
        }
        return Collections.unmodifiableList(views);
    }

    public SegSweepLabeller.Connectivity connectivity() {
        return connectivity;
    }

    ComponentNode nodeView(int nodeId) {
        if (nodeId < 0 || nodeId >= nodes.size()) {
            throw new IllegalArgumentException("nodeId is outside this component tree");
        }
        return new ComponentNode(this, nodes.get(nodeId));
    }

    int[] voxelsByNodeId(int nodeId, BooleanSupplier cancelCheck) {
        if (nodeId < 0 || nodeId >= nodes.size()) {
            throw new IllegalArgumentException("nodeId is outside this component tree");
        }
        return voxels(nodes.get(nodeId), cancelCheck);
    }

    Calibration calibrationCopy() {
        return calibration == null ? null : calibration.copy();
    }

    long totalVoxelCount() {
        return (long) width * (long) height * (long) depth;
    }

    public synchronized int feretComputationCount() {
        return feretComputationCount;
    }

    public synchronized void resetFeretComputationCount() {
        feretComputationCount = 0;
    }

    private ComponentTreeResult result(ComponentTreeResult.Status status,
                                       String reason,
                                       List<NodeData> selected) {
        return result(status, reason, selected, selected == null ? 0 : selected.size());
    }

    private ComponentTreeResult result(ComponentTreeResult.Status status,
                                       String reason,
                                       List<NodeData> selected,
                                       int reportedObjectCount) {
        ComponentSelection selection = new ComponentSelection(this, selected);
        return new ComponentTreeResult(status, reason, selection,
                new LazyLabelMap(width, height, depth, selection),
                reportedObjectCount);
    }

    private List<NodeData> nodesAtThreshold(double threshold,
                                            BooleanSupplier cancelCheck) {
        List<NodeData> views = new ArrayList<NodeData>();
        for (int i = 0; i < nodes.size(); i++) {
            if ((i & 1023) == 0) {
                checkCancelled(cancelCheck, "Component-tree query was cancelled.");
            }
            NodeData node = nodes.get(i);
            if (node.level <= threshold) continue;
            if (node.parentId >= 0 && nodes.get(node.parentId).level > threshold) continue;
            views.add(node);
        }
        return views;
    }

    private static int lowerBound(double[] values, double target) {
        int low = 0;
        int high = values.length;
        while (low < high) {
            int middle = low + (high - low) / 2;
            if (values[middle] < target) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private boolean matchesAll(NodeData node,
                               List<MorphologyPredicate> predicates,
                               BooleanSupplier cancelCheck) {
        for (int i = 0; i < predicates.size(); i++) {
            MorphologyPredicate predicate = predicates.get(i);
            if (!predicate.matches(attribute(node, predicate.attribute(), cancelCheck))) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesFeretPredicates(NodeData node,
                                            List<MorphologyPredicate> predicates,
                                            BooleanSupplier cancelCheck) {
        if (predicates.isEmpty()) return true;
        double upper = feretBoundingBoxUpper(node);
        boolean exactRequired = false;
        for (int i = 0; i < predicates.size(); i++) {
            MorphologyPredicate predicate = predicates.get(i);
            double target = predicate.value();
            MorphologyPredicate.Operator operator = predicate.operator();
            if ((operator == MorphologyPredicate.Operator.GE && target <= 0.0)
                    || (operator == MorphologyPredicate.Operator.GT && target < 0.0)) {
                continue;
            }
            if ((operator == MorphologyPredicate.Operator.LE && target < 0.0)
                    || (operator == MorphologyPredicate.Operator.LT && target <= 0.0)) {
                return false;
            }
            if ((operator == MorphologyPredicate.Operator.GE && upper < target)
                    || (operator == MorphologyPredicate.Operator.GT && upper <= target)) {
                return false;
            }
            if ((operator == MorphologyPredicate.Operator.LE && upper <= target)
                    || (operator == MorphologyPredicate.Operator.LT && upper < target)) {
                continue;
            }
            exactRequired = true;
        }
        return !exactRequired || matchesAll(node, predicates, cancelCheck);
    }

    private double feretBoundingBoxUpper(NodeData node) {
        double pixelWidth = calibration == null ? 1.0 : positiveOrOne(calibration.pixelWidth);
        double pixelHeight = calibration == null ? 1.0 : positiveOrOne(calibration.pixelHeight);
        double pixelDepth = calibration == null ? 1.0 : positiveOrOne(calibration.pixelDepth);
        double dx = (node.maxX - node.minX) * pixelWidth;
        double dy = (node.maxY - node.minY) * pixelHeight;
        double dz = (node.maxZ - node.minZ) * pixelDepth;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    double attribute(NodeData data,
                     MorphologyAttribute attribute,
                     BooleanSupplier cancelCheck) {
        if (attribute == MorphologyAttribute.VOLUME) return data.voxelCount;
        if (attribute == MorphologyAttribute.MEAN_INTENSITY) {
            return data.voxelCount == 0
                    ? Double.NaN : data.intensitySum / (double) data.voxelCount;
        }
        if (attribute == MorphologyAttribute.MAX_INTENSITY) return data.maxIntensity;
        if (attribute == MorphologyAttribute.ELONGATION) return elongation(data);
        if (attribute == MorphologyAttribute.SURFACE_AREA) return data.surfaceArea;
        if (attribute == MorphologyAttribute.SPHERICITY) {
            if (data.voxelCount <= 0 || data.surfaceArea <= 0.0d) return Double.NaN;
            return Math.pow(Math.PI, 1.0d / 3.0d)
                    * Math.pow(6.0d * data.voxelCount, 2.0d / 3.0d)
                    / data.surfaceArea;
        }
        if (attribute == MorphologyAttribute.COMPACTNESS) {
            if (data.voxelCount <= 0 || data.surfaceArea <= 0.0d) return Double.NaN;
            return (36.0d * Math.PI * data.voxelCount * data.voxelCount)
                    / (data.surfaceArea * data.surfaceArea * data.surfaceArea);
        }
        if (attribute == MorphologyAttribute.FERET_DIAMETER_MAX) {
            return feretDiameterMax(data, cancelCheck);
        }
        return Double.NaN;
    }

    double feretDiameterMax(NodeData data) {
        return feretDiameterMax(data, null);
    }

    synchronized double feretDiameterMax(NodeData data, BooleanSupplier cancelCheck) {
        if (Double.isNaN(data.feretDiameterMax)) {
            if (data.voxelCount > MAX_EXACT_FERET_VOXELS) {
                throw new SweepRefusedException("Exact Feret diameter for a "
                        + data.voxelCount + "-voxel object exceeds the bounded v0.2 limit of "
                        + MAX_EXACT_FERET_VOXELS
                        + ". Add a cheaper size/morphology filter or crop more tightly.");
            }
            data.feretDiameterMax = exactFeret(
                    voxels(data, cancelCheck), cancelCheck);
            feretComputationCount++;
        }
        return data.feretDiameterMax;
    }

    int[] voxels(NodeData data) {
        return voxels(data, null);
    }

    int[] voxels(NodeData data, BooleanSupplier cancelCheck) {
        if (data == null || data.voxelCount <= 0) {
            return new int[0];
        }
        int[] out = new int[data.voxelCount];
        int at = 0;
        ArrayDeque<Integer> pending = new ArrayDeque<Integer>();
        pending.push(Integer.valueOf(data.id));
        while (!pending.isEmpty()) {
            if ((at & 1023) == 0) {
                checkCancelled(cancelCheck, "Component-tree query was cancelled.");
            }
            NodeData current = nodes.get(pending.pop().intValue());
            if (at + current.voxels.length > out.length) {
                throw new IllegalStateException("Component-tree voxel accounting is inconsistent.");
            }
            System.arraycopy(current.voxels, 0, out, at, current.voxels.length);
            at += current.voxels.length;
            for (int i = 0; i < current.childIds.size(); i++) {
                pending.push(current.childIds.get(i));
            }
        }
        if (at != out.length) {
            throw new IllegalStateException("Component-tree voxel accounting expected "
                    + out.length + " voxels but found " + at + ".");
        }
        return out;
    }

    /** Number of voxel indexes retained by the tree; each source voxel is stored at most once. */
    public long storedVoxelMembershipCount() {
        long count = 0L;
        for (int i = 0; i < nodes.size(); i++) {
            count += nodes.get(i).voxels.length;
        }
        return count;
    }

    private double exactFeret(int[] voxels, BooleanSupplier cancelCheck) {
        if (voxels == null || voxels.length <= 1) {
            return 0.0;
        }
        double pixelWidth = calibration == null ? 1.0 : positiveOrOne(calibration.pixelWidth);
        double pixelHeight = calibration == null ? 1.0 : positiveOrOne(calibration.pixelHeight);
        double pixelDepth = calibration == null ? 1.0 : positiveOrOne(calibration.pixelDepth);
        int plane = width * height;
        double maxDistanceSquared = 0.0;
        for (int i = 0; i < voxels.length; i++) {
            if ((i & 15) == 0) {
                checkCancelled(cancelCheck, "Component-tree query was cancelled.");
            }
            int a = voxels[i];
            int az = a / plane;
            int ar = a - az * plane;
            int ay = ar / width;
            int ax = ar - ay * width;
            for (int j = i + 1; j < voxels.length; j++) {
                int b = voxels[j];
                int bz = b / plane;
                int br = b - bz * plane;
                int by = br / width;
                int bx = br - by * width;
                double dx = (ax - bx) * pixelWidth;
                double dy = (ay - by) * pixelHeight;
                double dz = (az - bz) * pixelDepth;
                double distanceSquared = dx * dx + dy * dy + dz * dz;
                if (distanceSquared > maxDistanceSquared) {
                    maxDistanceSquared = distanceSquared;
                }
            }
        }
        return Math.sqrt(maxDistanceSquared);
    }

    private static void checkCancelled(BooleanSupplier cancelCheck, String message) {
        if (Thread.currentThread().isInterrupted()
                || (cancelCheck != null && cancelCheck.getAsBoolean())) {
            throw new CancellationException(message);
        }
    }

    private static double positiveOrOne(double value) {
        return Double.isFinite(value) && value > 0.0 ? value : 1.0;
    }

    double elongation(NodeData data) {
        return data.elongation(depth <= 1);
    }

    static final class NodeData {
        final int id;
        int parentId = -1;
        final List<Integer> childIds = new ArrayList<Integer>();
        final float level;
        final int voxelCount;
        final double intensitySum;
        final double maxIntensity;
        final int minX;
        final int minY;
        final int minZ;
        final int maxX;
        final int maxY;
        final int maxZ;
        final double surfaceArea;
        final double xSum;
        final double ySum;
        final double zSum;
        final double xxSum;
        final double yySum;
        final double zzSum;
        final double xySum;
        final double xzSum;
        final double yzSum;
        final int[] voxels;
        double feretDiameterMax = Double.NaN;

        NodeData(int id,
                 float level,
                 int voxelCount,
                 double intensitySum,
                 double maxIntensity,
                 int minX,
                 int minY,
                 int minZ,
                 int maxX,
                 int maxY,
                 int maxZ,
                 double surfaceArea,
                 double xSum,
                 double ySum,
                 double zSum,
                 double xxSum,
                 double yySum,
                 double zzSum,
                 double xySum,
                 double xzSum,
                 double yzSum,
                 int[] voxels) {
            this.id = id;
            this.level = level;
            this.voxelCount = voxelCount;
            this.intensitySum = intensitySum;
            this.maxIntensity = maxIntensity;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.surfaceArea = surfaceArea;
            this.xSum = xSum;
            this.ySum = ySum;
            this.zSum = zSum;
            this.xxSum = xxSum;
            this.yySum = yySum;
            this.zzSum = zzSum;
            this.xySum = xySum;
            this.xzSum = xzSum;
            this.yzSum = yzSum;
            this.voxels = Arrays.copyOf(voxels, voxels.length);
        }

        double elongation(boolean twoDimensional) {
            if (voxelCount <= 1) return Double.NaN;
            double inv = 1.0 / (double) voxelCount;
            double cx = xSum * inv;
            double cy = ySum * inv;
            double cz = zSum * inv;
            double cxx = xxSum * inv - cx * cx;
            double cyy = yySum * inv - cy * cy;
            double czz = zzSum * inv - cz * cz;
            double cxy = xySum * inv - cx * cy;
            double cxz = xzSum * inv - cx * cz;
            double cyz = yzSum * inv - cy * cz;
            if (twoDimensional) {
                double trace = cxx + cyy;
                double discriminant = Math.sqrt(Math.max(0.0,
                        (cxx - cyy) * (cxx - cyy) + 4.0 * cxy * cxy));
                double smallest2d = zeroIfTiny((trace - discriminant) / 2.0);
                double largest2d = zeroIfTiny((trace + discriminant) / 2.0);
                if (largest2d <= 0.0 || smallest2d <= 0.0) return Double.NaN;
                return Math.sqrt(largest2d / smallest2d);
            }
            double[] eigenvalues = symmetricEigenvalues3x3(cxx, cxy, cxz, cyy, cyz, czz);
            Arrays.sort(eigenvalues);
            double smallest = zeroIfTiny(eigenvalues[0]);
            double largest = zeroIfTiny(eigenvalues[2]);
            if (largest <= 0.0 || smallest <= 0.0) return Double.NaN;
            return Math.sqrt(largest / smallest);
        }

        private static double[] symmetricEigenvalues3x3(double cxx,
                                                        double cxy,
                                                        double cxz,
                                                        double cyy,
                                                        double cyz,
                                                        double czz) {
            double p1 = cxy * cxy + cxz * cxz + cyz * cyz;
            if (p1 == 0.0) {
                return new double[] { cxx, cyy, czz };
            }
            double q = (cxx + cyy + czz) / 3.0;
            double axx = cxx - q;
            double ayy = cyy - q;
            double azz = czz - q;
            double p2 = axx * axx + ayy * ayy + azz * azz + 2.0 * p1;
            double p = Math.sqrt(p2 / 6.0);
            if (!Double.isFinite(p) || p <= 0.0) {
                return new double[] { cxx, cyy, czz };
            }
            double bxx = axx / p;
            double byy = ayy / p;
            double bzz = azz / p;
            double bxy = cxy / p;
            double bxz = cxz / p;
            double byz = cyz / p;
            double determinant = bxx * (byy * bzz - byz * byz)
                    - bxy * (bxy * bzz - byz * bxz)
                    + bxz * (bxy * byz - byy * bxz);
            double r = determinant / 2.0;
            double phi;
            if (r <= -1.0) {
                phi = Math.PI / 3.0;
            } else if (r >= 1.0) {
                phi = 0.0;
            } else {
                phi = Math.acos(r) / 3.0;
            }
            double largest = q + 2.0 * p * Math.cos(phi);
            double smallest = q + 2.0 * p * Math.cos(phi + (2.0 * Math.PI / 3.0));
            double middle = 3.0 * q - largest - smallest;
            return new double[] { largest, middle, smallest };
        }

        private static double zeroIfTiny(double value) {
            if (!Double.isFinite(value)) return Double.NaN;
            return Math.abs(value) <= 1.0e-12 ? 0.0 : value;
        }
    }
}
