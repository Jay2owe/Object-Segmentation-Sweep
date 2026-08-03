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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

    private final int width;
    private final int height;
    private final int depth;
    private final Calibration calibration;
    private final SegSweepLabeller.Connectivity connectivity;
    private final List<NodeData> nodes;
    private final List<Float> levelsDescending;
    private final List<int[]> nodeIdsByLevel;
    private int feretComputationCount;

    ComponentTree(int width,
                  int height,
                  int depth,
                  Calibration calibration,
                  SegSweepLabeller.Connectivity connectivity,
                  List<NodeData> nodes,
                  List<Float> levelsDescending,
                  List<int[]> nodeIdsByLevel) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.calibration = calibration == null ? null : calibration.copy();
        this.connectivity = connectivity == null
                ? SegSweepLabeller.DEFAULT_CONNECTIVITY : connectivity;
        this.nodes = Collections.unmodifiableList(new ArrayList<NodeData>(nodes));
        this.levelsDescending = Collections.unmodifiableList(new ArrayList<Float>(levelsDescending));
        this.nodeIdsByLevel = Collections.unmodifiableList(new ArrayList<int[]>(nodeIdsByLevel));
    }

    public static ComponentTree build(ImagePlus source, SegSweepLabeller.Connectivity connectivity) {
        return ComponentTreeBuilder.build(source, connectivity);
    }

    public ComponentTreeResult query(ComponentTreeQuery query) {
        ComponentTreeQuery safeQuery = query == null
                ? ComponentTreeQuery.builder().build() : query;
        if (safeQuery.maxSize() < safeQuery.minSize()) {
            return result(ComponentTreeResult.Status.EMPTY,
                    "No nodes can satisfy minSize " + safeQuery.minSize()
                            + " with maxSize " + safeQuery.maxSize() + ".",
                    Collections.<ComponentNode>emptyList());
        }

        List<ComponentNode> candidates = nodesAtThreshold(safeQuery.threshold());
        List<ComponentNode> cheapSurvivors = new ArrayList<ComponentNode>();
        List<MorphologyPredicate> feretPredicates = new ArrayList<MorphologyPredicate>();
        List<MorphologyPredicate> cheapPredicates = new ArrayList<MorphologyPredicate>();
        for (MorphologyPredicate predicate : safeQuery.predicates()) {
            if (predicate.attribute() == MorphologyAttribute.FERET_DIAMETER_MAX) {
                feretPredicates.add(predicate);
            } else {
                cheapPredicates.add(predicate);
            }
        }

        for (ComponentNode node : candidates) {
            int volume = node.voxelCount();
            if (volume < safeQuery.minSize() || volume > safeQuery.maxSize()) {
                continue;
            }
            if (matchesAll(node, cheapPredicates)) {
                cheapSurvivors.add(node);
            }
        }

        List<ComponentNode> selected = new ArrayList<ComponentNode>();
        for (ComponentNode node : cheapSurvivors) {
            if (matchesAll(node, feretPredicates)) {
                selected.add(node);
            }
        }

        if (selected.size() > MAX_16_BIT_LABEL) {
            return result(ComponentTreeResult.Status.TOO_MANY_LABELS,
                    "Selected node count " + selected.size()
                            + " exceeds the 16-bit label limit of 65535.",
                    Collections.<ComponentNode>emptyList());
        }
        if (selected.isEmpty()) {
            return result(ComponentTreeResult.Status.EMPTY,
                    "No component-tree nodes matched the query.", selected);
        }
        return result(ComponentTreeResult.Status.OK, "", selected);
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

    public int feretComputationCount() {
        return feretComputationCount;
    }

    public void resetFeretComputationCount() {
        feretComputationCount = 0;
    }

    private ComponentTreeResult result(ComponentTreeResult.Status status,
                                       String reason,
                                       List<ComponentNode> selected) {
        return new ComponentTreeResult(status, reason, selected,
                new LazyLabelMap(width, height, depth, calibration, selected));
    }

    private List<ComponentNode> nodesAtThreshold(int threshold) {
        int levelIndex = -1;
        for (int i = 0; i < levelsDescending.size(); i++) {
            if (levelsDescending.get(i).floatValue() > threshold) {
                levelIndex = i;
            } else {
                break;
            }
        }
        if (levelIndex < 0) {
            return Collections.emptyList();
        }
        int[] ids = nodeIdsByLevel.get(levelIndex);
        List<ComponentNode> views = new ArrayList<ComponentNode>(ids.length);
        for (int i = 0; i < ids.length; i++) {
            views.add(new ComponentNode(this, nodes.get(ids[i])));
        }
        return views;
    }

    private static boolean matchesAll(ComponentNode node, List<MorphologyPredicate> predicates) {
        for (int i = 0; i < predicates.size(); i++) {
            MorphologyPredicate predicate = predicates.get(i);
            if (!predicate.matches(node.attribute(predicate.attribute()))) {
                return false;
            }
        }
        return true;
    }

    double feretDiameterMax(NodeData data) {
        if (Double.isNaN(data.feretDiameterMax)) {
            data.feretDiameterMax = exactFeret(data.voxels);
            feretComputationCount++;
        }
        return data.feretDiameterMax;
    }

    private double exactFeret(int[] voxels) {
        if (voxels == null || voxels.length <= 1) {
            return 0.0;
        }
        double pixelWidth = calibration == null ? 1.0 : positiveOrOne(calibration.pixelWidth);
        double pixelHeight = calibration == null ? 1.0 : positiveOrOne(calibration.pixelHeight);
        double pixelDepth = calibration == null ? 1.0 : positiveOrOne(calibration.pixelDepth);
        int plane = width * height;
        double maxDistanceSquared = 0.0;
        for (int i = 0; i < voxels.length; i++) {
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

    private static double positiveOrOne(double value) {
        return Double.isFinite(value) && value > 0.0 ? value : 1.0;
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

        double elongation() {
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
