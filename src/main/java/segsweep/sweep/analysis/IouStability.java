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
import segsweep.tree.ComponentSelection;
import segsweep.tree.ComponentTreeResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * Scores parameter combinations by mean agreement with their lattice neighbours.
 *
 * <p>The production path accepts object-membership sources, including component
 * tree query results, so classical stability does not need a retained label
 * stack for every displayed combination. Component-tree sources compare the
 * selected foreground voxel footprints directly. Synthetic sources can still
 * compare supplied object identities, while engines that provide only labels
 * can use {@link LabelIou}. This heuristic has no randomisation null model
 * until v0.2.0.</p>
 */
public final class IouStability {

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
        return score(combos, sources, cancelCheck, 0L);
    }

    public static StabilityOutcome score(List<ParameterCombo> combos,
                                         List<IouSource> sources,
                                         BooleanSupplier cancelCheck,
                                         long budgetMs) {
        return score(combos, sources, cancelCheck, budgetMs, new LongSupplier() {
            @Override public long getAsLong() {
                return System.nanoTime();
            }
        });
    }

    static StabilityOutcome score(List<ParameterCombo> combos,
                                  List<IouSource> sources,
                                  BooleanSupplier cancelCheck,
                                  long budgetMs,
                                  LongSupplier clock) {
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
        LongSupplier safeClock = clock == null ? new LongSupplier() {
            @Override public long getAsLong() {
                return System.nanoTime();
            }
        } : clock;
        ScoringGuard guard = new ScoringGuard(cancelCheck, budgetMs, safeClock);
        for (int i = 0; i < combos.size(); i++) {
            String abortReason = guard.abortReason();
            if (abortReason != null) {
                return StabilityOutcome.of(StabilityOutcome.Kind.ABORTED,
                        eligibleCount, eligible, means,
                        abortReason);
            }
            List<Integer> neighbours = topology.fullNeighboursOf(i);
            if (neighbours.isEmpty()) {
                continue;
            }
            IouSource source = sources.get(i);
            if (source == null) {
                continue;
            }
            double total = 0.0d;
            int compared = 0;
            for (int n = 0; n < neighbours.size(); n++) {
                abortReason = guard.abortReason();
                if (abortReason != null) {
                    return StabilityOutcome.of(StabilityOutcome.Kind.ABORTED,
                            eligibleCount, eligible, means,
                            abortReason);
                }
                int neighbourIndex = neighbours.get(n).intValue();
                IouSource neighbour = sources.get(neighbourIndex);
                if (neighbour == null) {
                    break;
                }
                Double cached = pairCache.get(pairKey(i, neighbourIndex));
                double value;
                try {
                    value = cached == null
                            ? objectMembershipIou(source, neighbour, guard)
                            : cached.doubleValue();
                } catch (ScoringAbortedException ex) {
                    return StabilityOutcome.of(StabilityOutcome.Kind.ABORTED,
                            eligibleCount, eligible, means, ex.getMessage());
                }
                if (cached == null) {
                    pairCache.put(pairKey(i, neighbourIndex), Double.valueOf(value));
                }
                total += value;
                compared++;
            }
            if (compared != neighbours.size()) {
                continue;
            }
            eligible[i] = true;
            eligibleCount++;
            means[i] = total / compared;
            if (means[i] > bestMean
                    || (Double.compare(means[i], bestMean) == 0
                    && (bestIndex < 0 || topology.compareCoordinates(i, bestIndex) < 0))) {
                bestMean = means[i];
                bestIndex = i;
            }
        }

        if (eligibleCount == 0 || bestIndex < 0) {
            return StabilityOutcome.of(StabilityOutcome.Kind.NO_ELIGIBLE_COMBINATIONS,
                    eligibleCount, eligible, means,
                    "No combination has a full neighbour complement with successful neighbour results.");
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
        return objectMembershipIou(left, right, null);
    }

    private static double objectMembershipIou(IouSource left,
                                              IouSource right,
                                              ScoringGuard guard) {
        check(guard);
        if (left == null || right == null) {
            return 0.0d;
        }
        if (left.hasTreeMembership() && right.hasTreeMembership()) {
            BitSet a = left.foregroundVoxels(guard);
            BitSet b = right.foregroundVoxels(guard);
            int intersection = 0;
            int union = 0;
            int visited = 0;
            for (int voxel = a.nextSetBit(0); voxel >= 0;
                 voxel = a.nextSetBit(voxel + 1)) {
                if ((visited++ & 1023) == 0) check(guard);
                union++;
                if (b.get(voxel)) intersection++;
                if (voxel == Integer.MAX_VALUE) break;
            }
            for (int voxel = b.nextSetBit(0); voxel >= 0;
                 voxel = b.nextSetBit(voxel + 1)) {
                if ((visited++ & 1023) == 0) check(guard);
                if (!a.get(voxel)) union++;
                if (voxel == Integer.MAX_VALUE) break;
            }
            return union == 0 ? 0.0d : (double) intersection / (double) union;
        }
        List<Integer> a = left.objectIds();
        List<Integer> b = right.objectIds();
        if (a.isEmpty() && b.isEmpty()) {
            return 0.0d;
        }
        int ai = 0;
        int bi = 0;
        int intersection = 0;
        int union = 0;
        while (ai < a.size() || bi < b.size()) {
            if ((union & 1023) == 0) check(guard);
            if (ai >= a.size()) {
                bi++;
            } else if (bi >= b.size()) {
                ai++;
            } else {
                int comparison = a.get(ai).compareTo(b.get(bi));
                if (comparison == 0) {
                    intersection++;
                    ai++;
                    bi++;
                } else if (comparison < 0) {
                    ai++;
                } else {
                    bi++;
                }
            }
            union++;
        }
        return union == 0 ? 0.0d : (double) intersection / (double) union;
    }

    private static void check(ScoringGuard guard) {
        if (guard != null) guard.check();
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
     * Lightweight source for stability scoring.
     *
     * <p>Component-tree results retain selected nodes and reconstruct their
     * foreground voxel footprint on demand without materialising label maps.
     * Explicit object-ID sources retain the deterministic identity-set
     * comparison used by synthetic callers. Neither path has a v0.2.0
     * randomisation null model.</p>
     */
    public static final class IouSource {
        private final List<Integer> objectIds;
        private final ComponentSelection treeSelection;
        private final int objectCount;
        private final boolean treeBacked;

        private IouSource(Collection<Integer> objectIds,
                          ComponentSelection treeSelection,
                          int objectCount) {
            TreeSet<Integer> sorted = new TreeSet<Integer>();
            if (objectIds != null) {
                for (Integer id : objectIds) {
                    if (id != null) {
                        sorted.add(id);
                    }
                }
            }
            this.objectIds = Collections.unmodifiableList(new ArrayList<Integer>(sorted));
            this.treeSelection = treeSelection;
            this.objectCount = Math.max(0, objectCount);
            this.treeBacked = treeSelection != null;
        }

        public static IouSource fromObjectIds(Collection<Integer> objectIds) {
            int count = objectIds == null ? 0 : objectIds.size();
            return new IouSource(objectIds, null, count);
        }

        public static IouSource fromTreeResult(ComponentTreeResult result) {
            if (result == null) {
                return new IouSource(Collections.<Integer>emptyList(), null, 0);
            }
            return new IouSource(Collections.<Integer>emptyList(),
                    result.selection(), result.objectCount());
        }

        public List<Integer> objectIds() {
            return objectIds;
        }

        public int objectCount() {
            return objectCount;
        }

        private boolean hasTreeMembership() {
            return treeBacked;
        }

        private BitSet foregroundVoxels() {
            return foregroundVoxels(null);
        }

        private BitSet foregroundVoxels(ScoringGuard guard) {
            check(guard);
            try {
                return treeSelection.foregroundVoxels(guard);
            } catch (CancellationException ex) {
                if (guard != null) guard.check();
                throw ex;
            }
        }

        /** Returns the tree-backed foreground voxel indexes without creating an ImageJ label map. */
        public int[] foregroundVoxelIndices() {
            if (!treeBacked) {
                return new int[0];
            }
            BitSet foreground = foregroundVoxels();
            int[] indexes = new int[foreground.cardinality()];
            int at = 0;
            for (int voxel = foreground.nextSetBit(0); voxel >= 0;
                 voxel = foreground.nextSetBit(voxel + 1)) {
                indexes[at++] = voxel;
                if (voxel == Integer.MAX_VALUE) break;
            }
            return indexes;
        }

        /** Returns one voxel-index array per selected tree object. */
        public int[][] objectVoxelIndices() {
            if (!treeBacked) return new int[0][];
            return treeSelection.objectVoxelIndices();
        }
    }

    private static final class ScoringGuard implements BooleanSupplier {
        private final BooleanSupplier cancelCheck;
        private final long budgetMs;
        private final long budgetNanos;
        private final LongSupplier clock;
        private final long started;
        private String lastReason;

        ScoringGuard(BooleanSupplier cancelCheck, long budgetMs, LongSupplier clock) {
            this.cancelCheck = cancelCheck;
            this.budgetMs = budgetMs;
            this.budgetNanos = budgetMs <= 0L ? 0L
                    : budgetMs > Long.MAX_VALUE / 1000000L
                    ? Long.MAX_VALUE : budgetMs * 1000000L;
            this.clock = clock;
            this.started = clock.getAsLong();
        }

        @Override public boolean getAsBoolean() {
            if (Thread.currentThread().isInterrupted()
                    || (cancelCheck != null && cancelCheck.getAsBoolean())) {
                lastReason = "Stability scoring was cancelled.";
                return true;
            }
            if (budgetNanos > 0L && clock.getAsLong() - started >= budgetNanos) {
                lastReason = "Stability scoring exceeded its " + budgetMs + " ms budget.";
                return true;
            }
            return false;
        }

        String abortReason() {
            return getAsBoolean() ? lastReason : null;
        }

        void check() {
            if (getAsBoolean()) {
                throw new ScoringAbortedException(lastReason);
            }
        }
    }

    private static final class ScoringAbortedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ScoringAbortedException(String message) {
            super(message);
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
                    if (!containsAxisValue(values, entry.getValue())) {
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
                        "The v0.2 UI reports stability for at most two varying axes.");
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

        private static boolean containsAxisValue(List<Object> values, Object candidate) {
            for (int i = 0; i < values.size(); i++) {
                if (sameAxisValue(values.get(i), candidate)) return true;
            }
            return false;
        }

        List<Integer> fullNeighboursOf(int index) {
            if (index < 0 || index >= coordinates.length) {
                return Collections.emptyList();
            }
            int expected = 2 * axes.size();
            List<Integer> out = new ArrayList<Integer>(expected);
            int[] origin = coordinates[index];
            for (int axis = 0; axis < axes.size(); axis++) {
                for (int offset = -1; offset <= 1; offset += 2) {
                    int[] coordinate = origin.clone();
                    coordinate[axis] += offset;
                    if (coordinate[axis] < 0
                            || coordinate[axis] >= axes.get(axis).values.size()) {
                        return Collections.emptyList();
                    }
                    Integer neighbour = indexesByCoordinate.get(new Coordinate(coordinate));
                    if (neighbour == null) return Collections.emptyList();
                    out.add(neighbour);
                }
            }
            return out.size() == expected ? out : Collections.<Integer>emptyList();
        }

        int compareCoordinates(int leftIndex, int rightIndex) {
            int[] left = coordinates[leftIndex];
            int[] right = coordinates[rightIndex];
            for (int i = 0; i < Math.min(left.length, right.length); i++) {
                int compared = Integer.compare(left[i], right[i]);
                if (compared != 0) return compared;
            }
            return Integer.compare(left.length, right.length);
        }
    }

    private static final class Axis {
        final ParameterKey id;
        final List<Object> values;

        Axis(ParameterKey id, List<Object> values) {
            this.id = id;
            this.values = new ArrayList<Object>(values);
            Collections.sort(this.values, new java.util.Comparator<Object>() {
                @Override public int compare(Object left, Object right) {
                    if (left instanceof Number && right instanceof Number) {
                        return Double.compare(((Number) left).doubleValue(),
                                ((Number) right).doubleValue());
                    }
                    return String.valueOf(left).compareTo(String.valueOf(right));
                }
            });
        }

        int indexOf(Object value) {
            for (int i = 0; i < values.size(); i++) {
                if (sameAxisValue(values.get(i), value)) return i;
            }
            return -1;
        }
    }

    private static boolean sameAxisValue(Object left, Object right) {
        if (left instanceof Number && right instanceof Number) {
            return ((Number) left).doubleValue() == ((Number) right).doubleValue();
        }
        return left == null ? right == null : left.equals(right);
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
