/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.sweep;

import ij.measure.ResultsTable;
import segsweep.tree.LazyLabelMap;

import java.awt.Rectangle;
import java.util.EnumSet;

public final class VariationResult {
    public enum Flag {
        EMPTY,
        SATURATED,
        TIMED_OUT,
        FAILED,
        TOO_MANY_LABELS,
        UNCALIBRATED
    }

    private final ParameterCombo combo;
    private final LazyLabelMap labelMap;
    private final int objectCount;
    private final long durationMs;
    private final ResultsTable stats;
    private final Throwable error;
    private final SweepProvenance provenance;
    private final EnumSet<Flag> flags;
    private final double meanNeighbourIou;
    private boolean transferred;
    private boolean disposed;

    private VariationResult(ParameterCombo combo,
                            LazyLabelMap labelMap,
                            int objectCount,
                            long durationMs,
                            ResultsTable stats,
                            Throwable error,
                            SweepProvenance provenance,
                            EnumSet<Flag> flags,
                            double meanNeighbourIou) {
        if (combo == null) {
            throw new IllegalArgumentException("combo must not be null");
        }
        if (provenance == null) {
            throw new IllegalArgumentException("provenance must not be null");
        }
        if (objectCount < 0) {
            throw new IllegalArgumentException("objectCount must not be negative");
        }
        this.combo = combo;
        this.labelMap = labelMap;
        this.objectCount = objectCount;
        this.durationMs = Math.max(0L, durationMs);
        this.stats = stats;
        this.error = error;
        this.provenance = provenance;
        this.flags = normaliseFlags(objectCount, error, provenance, flags);
        this.meanNeighbourIou = meanNeighbourIou;
    }

    public static VariationResult success(ParameterCombo combo,
                                          LazyLabelMap labelMap,
                                          int objectCount,
                                          long durationMs,
                                          ResultsTable stats,
                                          SweepProvenance provenance) {
        if (labelMap == null) {
            throw new IllegalArgumentException("labelMap must not be null");
        }
        return new VariationResult(combo, labelMap, objectCount, durationMs, stats,
                null, provenance, EnumSet.noneOf(Flag.class), Double.NaN);
    }

    public static VariationResult success(ParameterCombo combo,
                                          LazyLabelMap labelMap,
                                          int objectCount,
                                          long durationMs,
                                          ResultsTable stats,
                                          SweepProvenance provenance,
                                          EnumSet<Flag> flags) {
        if (labelMap == null) {
            throw new IllegalArgumentException("labelMap must not be null");
        }
        return new VariationResult(combo, labelMap, objectCount, durationMs, stats,
                null, provenance, flags, Double.NaN);
    }

    public static VariationResult failure(ParameterCombo combo,
                                          Throwable error,
                                          SweepProvenance provenance) {
        return failure(combo, error, provenance, EnumSet.noneOf(Flag.class));
    }

    public static VariationResult failure(ParameterCombo combo,
                                          Throwable error,
                                          SweepProvenance provenance,
                                          EnumSet<Flag> flags) {
        return new VariationResult(combo, null, 0, 0L, null, error, provenance,
                flags, Double.NaN);
    }

    public ParameterCombo combo() {
        return combo;
    }

    public ParameterCombo getCombo() {
        return combo;
    }

    public boolean hasLabelMap() {
        return labelMap != null;
    }

    public LazyLabelMap labelMap() {
        if (labelMap == null) {
            throw new IllegalStateException("No lazy label map is available for this result.");
        }
        return labelMap;
    }

    public LazyLabelMap getLabelMap() {
        return labelMap();
    }

    public int objectCount() {
        return objectCount;
    }

    public int getObjectCount() {
        return objectCount;
    }

    public int nObjects() {
        return objectCount;
    }

    public int getNObjects() {
        return objectCount;
    }

    public long durationMs() {
        return durationMs;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public ResultsTable stats() {
        return stats;
    }

    public ResultsTable getStats() {
        return stats;
    }

    public Throwable error() {
        return error;
    }

    public Throwable getError() {
        return error;
    }

    public boolean hasError() {
        return error != null;
    }

    public SweepProvenance provenance() {
        return provenance;
    }

    public SweepProvenance getProvenance() {
        return provenance;
    }

    public EnumSet<Flag> flags() {
        return flags.clone();
    }

    public EnumSet<Flag> getFlags() {
        return flags();
    }

    public boolean hasFlag(Flag flag) {
        return flags.contains(flag);
    }

    public boolean calibrated() {
        return !flags.contains(Flag.UNCALIBRATED);
    }

    public boolean isCalibrated() {
        return calibrated();
    }

    public double objectsPerCalibratedVolume() {
        if (!calibrated()) {
            return Double.NaN;
        }
        Rectangle crop = provenance.crop().boundsFor(
                provenance.fullWidth(), provenance.fullHeight());
        double croppedVoxelCount = (double) crop.width
                * (double) crop.height
                * (double) provenance.fullDepth();
        double volume = provenance.voxelVolume() * croppedVoxelCount;
        return volume > 0.0d ? objectCount / volume : Double.NaN;
    }

    public double getObjectsPerCalibratedVolume() {
        return objectsPerCalibratedVolume();
    }

    public double meanNeighbourIou() {
        return meanNeighbourIou;
    }

    public double getMeanNeighbourIou() {
        return meanNeighbourIou;
    }

    public VariationResult withMeanNeighbourIou(double value) {
        return new VariationResult(combo, labelMap, objectCount, durationMs, stats,
                error, provenance, flags, value);
    }

    public synchronized void transferOwnership() {
        transferred = true;
    }

    public synchronized void dispose() {
        if (!transferred) {
            disposed = true;
        }
    }

    public synchronized boolean disposedForTest() {
        return disposed;
    }

    synchronized boolean hasDirectOwnership() {
        return !transferred && !disposed;
    }

    synchronized boolean ownsImagesForTest() {
        return hasDirectOwnership();
    }

    public synchronized void releaseTransferredImages() {
        disposed = true;
    }

    synchronized Object[] pendingTransferredImages() {
        return transferred && !disposed ? new Object[] { this } : new Object[0];
    }

    static boolean containsInterruptedFailure(Throwable failure) {
        return containsInterruptedFailure(failure,
                java.util.Collections.newSetFromMap(
                        new java.util.IdentityHashMap<Throwable, Boolean>()));
    }

    private static boolean containsInterruptedFailure(
            Throwable failure,
            java.util.Set<Throwable> visited) {
        if (failure == null || visited.contains(failure)) {
            return false;
        }
        if (failure instanceof InterruptedException) {
            return true;
        }
        visited.add(failure);
        if (containsInterruptedFailure(failure.getCause(), visited)) {
            return true;
        }
        Throwable[] suppressed = failure.getSuppressed();
        for (int i = 0; i < suppressed.length; i++) {
            if (containsInterruptedFailure(suppressed[i], visited)) {
                return true;
            }
        }
        return false;
    }

    private static EnumSet<Flag> normaliseFlags(int objectCount,
                                                Throwable error,
                                                SweepProvenance provenance,
                                                EnumSet<Flag> flags) {
        EnumSet<Flag> out = flags == null
                ? EnumSet.noneOf(Flag.class)
                : flags.clone();
        if (objectCount == 0) {
            out.add(Flag.EMPTY);
        }
        if (error != null) {
            out.add(Flag.FAILED);
        }
        if (!hasPhysicalCalibration(provenance.calibrationUnit())) {
            out.add(Flag.UNCALIBRATED);
        }
        return out;
    }

    private static boolean hasPhysicalCalibration(String unit) {
        if (unit == null) {
            return false;
        }
        String trimmed = unit.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        return !"pixel".equalsIgnoreCase(trimmed)
                && !"pixels".equalsIgnoreCase(trimmed);
    }
}
