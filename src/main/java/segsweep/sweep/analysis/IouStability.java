/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.sweep.analysis;

import segsweep.sweep.ParameterCombo;
import segsweep.sweep.ParameterKey;
import segsweep.tree.ComponentNode;
import segsweep.tree.ComponentTreeResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;

/**
 * Scores parameter combinations by mean agreement with their lattice neighbours.
 *
 * <p>The production path accepts object-membership sources, including component
 * tree query results, so classical stability does not need a retained label
 * stack for every displayed combination. Membership IoU compares selected
 * object identities, not voxel footprint labels; when an engine can provide
 * only labels, use {@link LabelIou} separately and document that fallback. This
 * heuristic has no randomisation null model until v0.2.0.</p>
 */
public final class IouStability {

    static final double COUNT_RATIO_FLOOR = 0.8d;
    private static final int UI_AXIS_CAP = 2;

    private IouStability() {
    }

    public static StabilityOutcome score(List<ParameterCombo> combos,
                                         List<IouSource> sources) {
        return score(combos, sources, null);
    }

    public static StabilityOutcome score(List<ParameterCombo> combos,
                                         List<IouSource> sources,
                                         BooleanSupplier cancelCheck) {
        if (combos == null || sources == null || combos.size() != sources.size()
                || combos.size() < 3) {
            return StabilityOutcome.of(StabilityOutcome.Kind.NO_ELIGIBLE_COMBINATIONS,
                    0, new boolean[sizeOf(combos)], emptyMeans(sizeOf(combos)),
                    "Stability scoring needs at least three aligned combinations.");
        }
        TopologyBuild topologyBuild = Topology.from(combos);
        if (topologyBuild.kind == StabilityOutcome.Kind.TOO_MANY_AXES) {
            return StabilityOutcome.of(StabilityOutcome.Kind.TOO_MANY_AXES,
                    0, new boolean[combos.size()], emptyMeans(combos.size()),
                    topologyBuild.explanation);
        }
        if (topologyBuild.topology == null) {
            return StabilityOutcome.of(StabilityOutcome.Kind.NO_ELIGIBLE_COMBINATIONS,
                    0, new boolean[combos.size()], emptyMeans(combos.size()),
                    topologyBuild.explanation);
        }

        Topology topology = topologyBuild.topology;
        boolean[] eligible = new boolean[combos.size()];
        double[] means = emptyMeans(combos.size());
        Map<Long, Double> pairCache = new HashMap<Long, Double>();
        int eligibleCount = 0;
        int bestIndex = -1;
        double bestMean = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < combos.size(); i++) {
            if (cancelCheck != null && cancelCheck.getAsBoolean()) {
                return StabilityOutcome.of(StabilityOutcome.Kind.ABORTED,
                        eligibleCount, eligible, means,
                        "Stability scoring was cancelled.");
            }
            List<Integer> neighbours = topology.fullNeighboursOf(i);
            if (neighbours.isEmpty()) {
                continue;
            }
            eligible[i] = true;
            eligibleCount++;
            IouSource source = sources.get(i);
            if (source == null || source.objectCount() <= 0) {
                continue;
            }
            double total = 0.0d;
            int compared = 0;
            boolean countGateFailed = false;
            for (int n = 0; n < neighbours.size(); n++) {
                int neighbourIndex = neighbours.get(n).intValue();
                IouSource neighbour = sources.get(neighbourIndex);
                if (neighbour == null || neighbour.objectCount() <= 0) {
                    countGateFailed = true;
                    break;
                }
                double ratio = countRatio(source.objectCount(), neighbour.objectCount());
                if (Double.isNaN(ratio) || ratio < COUNT_RATIO_FLOOR) {
                    countGateFailed = true;
                    break;
                }
                Double cached = pairCache.get(pairKey(i, neighbourIndex));
                double value = cached == null
                        ? objectMembershipIou(source, neighbour)
                        : cached.doubleValue();
                if (cached == null) {
                    pairCache.put(pairKey(i, neighbourIndex), Double.valueOf(value));
                }
                total += value;
                compared++;
            }
            if (countGateFailed || compared != neighbours.size()) {
                continue;
            }
            means[i] = total / compared;
            if (means[i] > bestMean) {
                bestMean = means[i];
                bestIndex = i;
            }
        }

        if (eligibleCount == 0 || bestIndex < 0 || bestMean <= 0.0d) {
            return StabilityOutcome.of(StabilityOutcome.Kind.NO_ELIGIBLE_COMBINATIONS,
                    eligibleCount, eligible, means,
                    eligibleCount == 0
                            ? "No combination has a full neighbour complement."
                            : "No eligible combination had positive neighbour agreement.");
        }
        return StabilityOutcome.stableAt(bestIndex, bestMean, eligibleCount,
                eligible, means,
                "Highest mean object-membership IoU among eligible combinations.");
    }

    public static double meanNeighbourCountRatio(List<ParameterCombo> combos,
                                                 List<Integer> objectCounts,
                                                 int index) {
        if (combos == null || objectCounts == null
                || combos.size() != objectCounts.size()
                || index < 0 || index >= combos.size()) {
            return Double.NaN;
        }
        TopologyBuild build = Topology.from(combos);
        if (build.topology == null) {
            return Double.NaN;
        }
        List<Integer> neighbours = build.topology.fullNeighboursOf(index);
        if (neighbours.isEmpty()) {
            return Double.NaN;
        }
        Integer own = objectCounts.get(index);
        if (own == null || own.intValue() <= 0) {
            return Double.NaN;
        }
        double total = 0.0d;
        for (int i = 0; i < neighbours.size(); i++) {
            Integer other = objectCounts.get(neighbours.get(i).intValue());
            if (other == null || other.intValue() <= 0) {
                return Double.NaN;
            }
            total += countRatio(own.intValue(), other.intValue());
        }
        return total / neighbours.size();
    }

    static double objectMembershipIou(IouSource left, IouSource right) {
        if (left == null || right == null) {
            return 0.0d;
        }
        List<Integer> a = left.objectIds();
        List<Integer> b = right.objectIds();
        if (a.isEmpty() && b.isEmpty()) {
            return 0.0d;
        }
        TreeSet<Integer> union = new TreeSet<Integer>(a);
        union.addAll(b);
        if (union.isEmpty()) {
            return 0.0d;
        }
        TreeSet<Integer> intersection = new TreeSet<Integer>(a);
        intersection.retainAll(b);
        return (double) intersection.size() / (double) union.size();
    }

    private static double countRatio(int left, int right) {
        if (left <= 0 || right <= 0) {
            return Double.NaN;
        }
        int lo = Math.min(left, right);
        int hi = Math.max(left, right);
        return (double) lo / (double) hi;
    }

    private static int sizeOf(List<?> values) {
        return values == null ? 0 : values.size();
    }

    private static double[] emptyMeans(int size) {
        double[] means = new double[Math.max(0, size)];
        Arrays.fill(means, Double.NaN);
        return means;
    }

    private static long pairKey(int a, int b) {
        int low = Math.min(a, b);
        int high = Math.max(a, b);
        return (((long) low) << 32) ^ (high & 0xffffffffL);
    }

    /**
     * Object-identity source for stability scoring.
     *
     * <p>For component-tree results these identities are selected node IDs. The
     * comparison is intentionally cheap and deterministic, but it is not a
     * voxel-wise label-map IoU and it has no v0.2.0 randomisation null model.</p>
     */
    public static final class IouSource {
        private final List<Integer> objectIds;
        private final int objectCount;

        private IouSource(Collection<Integer> objectIds, int objectCount) {
            TreeSet<Integer> sorted = new TreeSet<Integer>();
            if (objectIds != null) {
                for (Integer id : objectIds) {
                    if (id != null) {
                        sorted.add(id);
                    }
                }
            }
            this.objectIds = Collections.unmodifiableList(new ArrayList<Integer>(sorted));
            this.objectCount = Math.max(0, objectCount);
        }

        public static IouSource fromObjectIds(Collection<Integer> objectIds) {
            int count = objectIds == null ? 0 : objectIds.size();
            return new IouSource(objectIds, count);
        }

        public static IouSource fromTreeResult(ComponentTreeResult result) {
            if (result == null) {
                return new IouSource(Collections.<Integer>emptyList(), 0);
            }
            List<Integer> ids = new ArrayList<Integer>();
            List<ComponentNode> nodes = result.selectedNodes();
            for (int i = 0; i < nodes.size(); i++) {
                ids.add(Integer.valueOf(nodes.get(i).id()));
            }
            return new IouSource(ids, result.objectCount());
        }

        public List<Integer> objectIds() {
            return objectIds;
        }

        public int objectCount() {
            return objectCount;
        }
    }

    private static final class TopologyBuild {
        final Topology topology;
        final StabilityOutcome.Kind kind;
        final String explanation;

        TopologyBuild(Topology topology,
                      StabilityOutcome.Kind kind,
                      String explanation) {
            this.topology = topology;
            this.kind = kind;
            this.explanation = explanation;
        }
    }

    private static final class Topology {
        private final List<Axis> axes;
        private final int[][] coordinates;
        private final Map<Coordinate, Integer> indexesByCoordinate;

        private Topology(List<Axis> axes,
                         int[][] coordinates,
                         Map<Coordinate, Integer> indexesByCoordinate) {
            this.axes = axes;
            this.coordinates = coordinates;
            this.indexesByCoordinate = indexesByCoordinate;
        }

        static TopologyBuild from(List<ParameterCombo> combos) {
            LinkedHashMap<ParameterKey, List<Object>> valuesById =
                    new LinkedHashMap<ParameterKey, List<Object>>();
            for (int i = 0; i < combos.size(); i++) {
                ParameterCombo combo = combos.get(i);
                if (combo == null) {
                    return new TopologyBuild(null,
                            StabilityOutcome.Kind.NO_ELIGIBLE_COMBINATIONS,
                            "A parameter combination was null.");
                }
                for (Map.Entry<ParameterKey, Object> entry : combo.values().entrySet()) {
                    List<Object> values = valuesById.get(entry.getKey());
                    if (values == null) {
                        values = new ArrayList<Object>();
                        valuesById.put(entry.getKey(), values);
                    }
                    if (!values.contains(entry.getValue())) {
                        values.add(entry.getValue());
                    }
                }
            }

            List<Axis> axes = new ArrayList<Axis>();
            for (Map.Entry<ParameterKey, List<Object>> entry : valuesById.entrySet()) {
                if (entry.getValue().size() > 1) {
                    axes.add(new Axis(entry.getKey(), entry.getValue()));
                }
            }
            if (axes.isEmpty()) {
                return new TopologyBuild(null,
                        StabilityOutcome.Kind.NO_ELIGIBLE_COMBINATIONS,
                        "No parameter axis varies.");
            }
            if (axes.size() > UI_AXIS_CAP) {
                return new TopologyBuild(null,
                        StabilityOutcome.Kind.TOO_MANY_AXES,
                        "The v0.1 UI reports stability for at most two varying axes.");
            }

            int[][] coordinates = new int[combos.size()][axes.size()];
            Map<Coordinate, Integer> indexesByCoordinate =
                    new HashMap<Coordinate, Integer>();
            for (int i = 0; i < combos.size(); i++) {
                ParameterCombo combo = combos.get(i);
                for (int axisIndex = 0; axisIndex < axes.size(); axisIndex++) {
                    Axis axis = axes.get(axisIndex);
                    int valueIndex = axis.indexOf(combo.get(axis.id));
                    if (valueIndex < 0) {
                        return new TopologyBuild(null,
                                StabilityOutcome.Kind.NO_ELIGIBLE_COMBINATIONS,
                                "A combination is missing a varying axis value.");
                    }
                    coordinates[i][axisIndex] = valueIndex;
                }
                Coordinate coordinate = new Coordinate(coordinates[i]);
                if (indexesByCoordinate.put(coordinate, Integer.valueOf(i)) != null) {
                    return new TopologyBuild(null,
                            StabilityOutcome.Kind.NO_ELIGIBLE_COMBINATIONS,
                            "Duplicate parameter coordinates were found.");
                }
            }
            return new TopologyBuild(new Topology(axes, coordinates, indexesByCoordinate),
                    StabilityOutcome.Kind.STABLE_AT, "");
        }

        List<Integer> fullNeighboursOf(int index) {
            if (index < 0 || index >= coordinates.length) {
                return Collections.emptyList();
            }
            int expected = expectedNeighbourCount();
            List<Integer> out = new ArrayList<Integer>(expected);
            collectNeighbours(coordinates[index], new int[coordinates[index].length], 0, out);
            return out.size() == expected ? out : Collections.<Integer>emptyList();
        }

        private int expectedNeighbourCount() {
            int count = 1;
            for (int i = 0; i < axes.size(); i++) {
                count *= 3;
            }
            return count - 1;
        }

        private void collectNeighbours(int[] origin,
                                       int[] offsets,
                                       int dimension,
                                       List<Integer> out) {
            if (dimension >= offsets.length) {
                boolean allZero = true;
                int[] coordinate = new int[origin.length];
                for (int i = 0; i < offsets.length; i++) {
                    if (offsets[i] != 0) {
                        allZero = false;
                    }
                    int value = origin[i] + offsets[i];
                    if (value < 0 || value >= axes.get(i).values.size()) {
                        return;
                    }
                    coordinate[i] = value;
                }
                if (allZero) {
                    return;
                }
                Integer neighbour = indexesByCoordinate.get(new Coordinate(coordinate));
                if (neighbour != null) {
                    out.add(neighbour);
                }
                return;
            }
            for (int offset = -1; offset <= 1; offset++) {
                offsets[dimension] = offset;
                collectNeighbours(origin, offsets, dimension + 1, out);
            }
        }
    }

    private static final class Axis {
        final ParameterKey id;
        final List<Object> values;

        Axis(ParameterKey id, List<Object> values) {
            this.id = id;
            this.values = values;
        }

        int indexOf(Object value) {
            return values.indexOf(value);
        }
    }

    private static final class Coordinate {
        final int[] values;

        Coordinate(int[] values) {
            this.values = values.clone();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Coordinate)) return false;
            Coordinate other = (Coordinate) obj;
            return Arrays.equals(values, other.values);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(values);
        }
    }
}
