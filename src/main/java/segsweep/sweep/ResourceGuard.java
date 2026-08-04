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

import java.awt.Rectangle;

public final class ResourceGuard {

    private static final double DEFAULT_AVAILABLE_FRACTION = 0.5d;
    private static final long UNION_FIND_BYTES_PER_VOXEL = 13L;
    private static final long TREE_NODE_BYTES_PER_VOXEL = 64L;
    private static final long TREE_CHILD_BYTES_PER_VOXEL = 8L;
    private static final long ATTRIBUTE_BYTES_PER_NODE = 160L;
    private static final long LABEL_MAP_BYTES_PER_VOXEL = 2L;
    private static final long RGB_PREVIEW_BYTES_PER_PIXEL = 4L;
    private static final long MONTAGE_CELL_BYTES = 220L * 210L * RGB_PREVIEW_BYTES_PER_PIXEL;
    private static final long MONTAGE_SCRATCH_BYTES_PER_PIXEL = 12L;
    private static final long SWING_BYTES_PER_CELL = 16L * 1024L;
    private static final long RESULT_STATE_BYTES_PER_CELL = 512L;
    private static final long QUERY_SCRATCH_BYTES_PER_VOXEL_PER_WORKER = 24L;
    static final long MAX_COMPUTE_CELLS = 10000L;
    static final long MAX_COMPUTE_VOXEL_QUERIES = 250000000L;
    static final long MAX_DISPLAY_CELLS = 100L;

    private ResourceGuard() {
    }

    /**
     * Assesses the tree and query working set only. Headless/API callers do not
     * retain Swing cells or preview montages and must not inherit UI limits.
     */
    public static Feasibility assessComputeFeasibility(ParameterSweep sweep, ImagePlus source) {
        return assessFeasibility(sweep, source, OutputMode.COMPUTE_ONLY,
                availableBytes(), defaultQueryWorkers(sweep));
    }

    /** Assesses compute using the same bounded worker count as the analysis API. */
    public static Feasibility assessComputeFeasibility(ParameterSweep sweep,
                                                        ImagePlus source,
                                                        int parallelism) {
        return assessFeasibility(sweep, source, OutputMode.COMPUTE_ONLY,
                availableBytes(), boundedQueryWorkers(sweep, parallelism));
    }

    static Feasibility assessComputeFeasibilityForBudget(ParameterSweep sweep,
                                                          ImagePlus source,
                                                          int parallelism,
                                                          long available) {
        return assessFeasibility(sweep, source, OutputMode.COMPUTE_ONLY,
                Math.max(0L, available), boundedQueryWorkers(sweep, parallelism));
    }

    /** Assesses compute plus the deterministic PNG montage written by autosave. */
    public static Feasibility assessMontageFeasibility(ParameterSweep sweep, ImagePlus source) {
        return assessFeasibility(sweep, source, OutputMode.MONTAGE,
                availableBytes(), defaultQueryWorkers(sweep));
    }

    /**
     * Assesses only the synthetic PNG allocation performed after analysis.
     * The already-retained tree and result state are reflected in available
     * heap and must not be charged a second time.
     */
    public static Feasibility assessMontageOutputFeasibility(ParameterSweep sweep,
                                                              ImagePlus source) {
        return assessMontageOutputFeasibility(sweep, source, availableBytes());
    }

    static Feasibility assessMontageOutputFeasibilityForBudget(ParameterSweep sweep,
                                                                ImagePlus source,
                                                                long available) {
        return assessMontageOutputFeasibility(sweep, source, Math.max(0L, available));
    }

    /** Assesses compute plus the retained preview/Swing grid working set. */
    public static Feasibility assessFeasibility(ParameterSweep sweep, ImagePlus source) {
        return assessFeasibility(sweep, source, OutputMode.DISPLAY,
                availableBytes(), defaultQueryWorkers(sweep));
    }

    private static Feasibility assessFeasibility(ParameterSweep sweep,
                                                  ImagePlus source,
                                                  OutputMode outputMode,
                                                  long available,
                                                  int queryWorkers) {
        if (sweep == null) {
            return Feasibility.refused(null, available,
                    "No parameter sweep was provided.");
        }
        if (source == null) {
            return Feasibility.refused(null, available,
                    "No source image was provided.");
        }
        int bitDepth = source.getBitDepth();
        if (bitDepth != 8 && bitDepth != 16 && bitDepth != 32) {
            return Feasibility.refused(null, available,
                    "Only 8-bit, 16-bit, or 32-bit grayscale images are supported; received "
                            + bitDepth + "-bit input.");
        }
        Rectangle crop = sweep.cropSpec().boundsFor(source);
        Estimate estimate = estimateTreeMemory(source, crop);
        long cells = sweep.cellCount();
        long selectionBytesPerCell = saturatingAdd(RESULT_STATE_BYTES_PER_CELL,
                divideRoundUp(estimate.cropVoxels(), 8L));
        estimate = estimate.withCombinationBytes(multiply(cells, selectionBytesPerCell));
        long scratchPerWorker = multiply(estimate.cropVoxels(),
                QUERY_SCRATCH_BYTES_PER_VOXEL_PER_WORKER);
        estimate = estimate.withQueryScratchBytes(multiply(
                Math.min(cells, Math.max(1L, queryWorkers)), scratchPerWorker));
        if (cells > MAX_COMPUTE_CELLS) {
            return Feasibility.refused(estimate, available,
                    "The sweep contains " + cells
                            + " combinations, above the compute limit of "
                            + MAX_COMPUTE_CELLS
                            + ". Narrow the ranges or increase the step sizes before dispatch.");
        }
        long queryWork = multiply(cells, estimate.cropVoxels());
        if (queryWork > MAX_COMPUTE_VOXEL_QUERIES) {
            return Feasibility.refused(estimate, available,
                    "The sweep requires up to " + queryWork
                            + " combination-voxel queries, above the practical compute limit of "
                            + MAX_COMPUTE_VOXEL_QUERIES
                            + ". Crop tighter or narrow the parameter ranges before dispatch.");
        }
        if (outputMode != OutputMode.COMPUTE_ONLY) {
            long cropPreviewBytes = multiply(multiply(crop.width, crop.height),
                    RGB_PREVIEW_BYTES_PER_PIXEL);
            long retainedBytesPerCell = outputMode == OutputMode.DISPLAY
                    ? saturatingAdd(Math.max(cropPreviewBytes, MONTAGE_CELL_BYTES),
                    SWING_BYTES_PER_CELL)
                    : MONTAGE_CELL_BYTES;
            long previewBytes = multiply(sweep.cellCount(), retainedBytesPerCell);
            if (outputMode == OutputMode.MONTAGE) {
                previewBytes = saturatingAdd(previewBytes,
                        multiply(multiply(crop.width, crop.height),
                                MONTAGE_SCRATCH_BYTES_PER_PIXEL));
            }
            estimate = estimate.withPreviewBytes(previewBytes);
            if (sweep.cellCount() > MAX_DISPLAY_CELLS) {
                return Feasibility.refused(estimate, available,
                        "The " + (outputMode == OutputMode.DISPLAY
                                ? "display grid" : "autosave montage")
                                + " contains " + sweep.cellCount()
                                + " cells, above the practical limit of "
                                + MAX_DISPLAY_CELLS
                                + ". Narrow the ranges or step sizes before creating this output.");
            }
        }
        return decide(estimate, available, (long) Math.floor(available * DEFAULT_AVAILABLE_FRACTION));
    }

    private enum OutputMode {
        COMPUTE_ONLY,
        MONTAGE,
        DISPLAY
    }

    private static Feasibility assessMontageOutputFeasibility(ParameterSweep sweep,
                                                               ImagePlus source,
                                                               long available) {
        if (sweep == null) {
            return Feasibility.refused(null, available,
                    "No parameter sweep was provided.");
        }
        if (source == null) {
            return Feasibility.refused(null, available,
                    "No source image was provided.");
        }
        long cells = sweep.cellCount();
        Rectangle crop = sweep.cropSpec().boundsFor(source);
        long previewBytes = saturatingAdd(multiply(cells, MONTAGE_CELL_BYTES),
                multiply(multiply(crop.width, crop.height),
                        MONTAGE_SCRATCH_BYTES_PER_PIXEL));
        long cropVoxels = multiply(multiply(crop.width, crop.height), stackDepth(source));
        Estimate estimate = new Estimate(cropVoxels, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L, previewBytes, previewBytes);
        if (cells > MAX_DISPLAY_CELLS) {
            return Feasibility.refused(estimate, available,
                    "The autosave montage contains " + cells
                            + " cells, above the practical limit of "
                            + MAX_DISPLAY_CELLS
                            + ". Narrow the ranges or step sizes before creating this output.");
        }
        long budget = (long) Math.floor(available * DEFAULT_AVAILABLE_FRACTION);
        if (estimate.totalBytes() > budget) {
            return Feasibility.refused(estimate, available,
                    "Estimated autosave montage memory is ~" + formatGb(previewBytes)
                            + " GB (" + previewBytes + " bytes), above the remaining output budget of ~"
                            + formatGb(budget) + " GB (" + budget + " bytes).");
        }
        return new Feasibility(true, estimate, available,
                "The autosave montage fits the current memory budget.");
    }

    public static Decision checkTreeMemory(ImagePlus source, CropSpec cropSpec, long maxBytes) {
        if (source == null) {
            return Decision.refused(null, "No source image was provided.");
        }
        CropSpec safeCrop = cropSpec == null ? CropSpec.full() : cropSpec;
        Estimate estimate = estimateTreeMemory(source, safeCrop.boundsFor(source));
        if (estimate.totalBytes() > maxBytes) {
            return Decision.refused(estimate, refusalMessage(estimate, maxBytes));
        }
        return Decision.permitted(estimate);
    }

    public static Estimate estimateTreeMemory(ImagePlus source, Rectangle cropBounds) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        if (cropBounds == null) {
            throw new IllegalArgumentException("cropBounds must not be null");
        }
        int depth = stackDepth(source);
        return estimateTreeMemory(cropBounds.width, cropBounds.height, depth, source.getBitDepth());
    }

    public static Estimate estimateTreeMemory(int width, int height, int depth, int bitDepth) {
        long cropVoxels = multiply(multiply(Math.max(0, width), Math.max(0, height)), Math.max(0, depth));
        long sourceBytes = multiply(cropVoxels, bytesPerSourceVoxel(bitDepth));
        long unionFindBytes = multiply(cropVoxels, UNION_FIND_BYTES_PER_VOXEL);
        long nodeArrayBytes = multiply(cropVoxels, TREE_NODE_BYTES_PER_VOXEL);
        long childArrayBytes = multiply(cropVoxels, TREE_CHILD_BYTES_PER_VOXEL);
        long attributeBytes = multiply(cropVoxels, ATTRIBUTE_BYTES_PER_NODE);
        long oneLazyLabelMapBytes = multiply(cropVoxels, LABEL_MAP_BYTES_PER_VOXEL);
        long treeBytes = saturatingAdd(saturatingAdd(unionFindBytes, nodeArrayBytes), childArrayBytes);
        long totalBytes = saturatingAdd(sourceBytes,
                saturatingAdd(treeBytes, saturatingAdd(attributeBytes, oneLazyLabelMapBytes)));
        return new Estimate(cropVoxels, sourceBytes, unionFindBytes, nodeArrayBytes,
                childArrayBytes, attributeBytes, oneLazyLabelMapBytes,
                0L, 0L, 0L, totalBytes);
    }

    private static Feasibility decide(Estimate estimate, long available, long budget) {
        if (estimate.totalBytes() > budget) {
            return new Feasibility(false, estimate, available, refusalMessage(estimate, budget));
        }
        return new Feasibility(true, estimate, available,
                "This component-tree sweep fits the current memory budget.");
    }

    private static String refusalMessage(Estimate estimate, long limit) {
        long estimatedBytes = estimate == null ? 0L : estimate.totalBytes();
        return "Estimated component-tree memory plus retained sweep state is ~"
                + formatGb(estimatedBytes)
                + " GB (" + estimatedBytes + " bytes), above the limit of ~"
                + formatGb(limit) + " GB (" + limit
                + " bytes). Crop tighter before building the tree.";
    }

    private static long availableBytes() {
        long maxMem = Runtime.getRuntime().maxMemory();
        long usedMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        return Math.max(0L, maxMem - usedMem);
    }

    private static int defaultQueryWorkers(ParameterSweep sweep) {
        return boundedQueryWorkers(sweep,
                Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
    }

    private static int boundedQueryWorkers(ParameterSweep sweep, int requested) {
        long cells = sweep == null ? 1L : Math.max(1L, sweep.cellCount());
        int coreBound = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        long bounded = Math.min(cells, Math.min(Math.max(1, requested), coreBound));
        return (int) Math.max(1L, bounded);
    }

    private static int stackDepth(ImagePlus source) {
        int slices = Math.max(1, source.getNSlices());
        int frames = Math.max(1, source.getNFrames());
        int channels = Math.max(1, source.getNChannels());
        int stackSize = Math.max(1, source.getStackSize());
        int channelAware = Math.max(1, slices * frames);
        if (channels > 1 && channelAware <= stackSize) {
            return channelAware;
        }
        return stackSize;
    }

    private static long bytesPerSourceVoxel(int bitDepth) {
        if (bitDepth <= 8) return 1L;
        if (bitDepth <= 16) return 2L;
        return 4L;
    }

    private static long multiply(long a, long b) {
        if (a == 0L || b == 0L) return 0L;
        if (a > Long.MAX_VALUE / b) return Long.MAX_VALUE;
        return a * b;
    }

    private static long divideRoundUp(long value, long divisor) {
        if (value <= 0L) return 0L;
        return 1L + (value - 1L) / divisor;
    }

    private static long saturatingAdd(long a, long b) {
        if (Long.MAX_VALUE - a < b) return Long.MAX_VALUE;
        return a + b;
    }

    private static String formatGb(long bytes) {
        double gb = bytes / (1024.0d * 1024.0d * 1024.0d);
        return String.format(java.util.Locale.ROOT, "%.1f", Double.valueOf(gb));
    }

    public static final class Estimate {
        private final long cropVoxels;
        private final long sourceBytes;
        private final long unionFindBytes;
        private final long nodeArrayBytes;
        private final long childArrayBytes;
        private final long attributeBytes;
        private final long oneLazyLabelMapBytes;
        private final long queryScratchBytes;
        private final long combinationBytes;
        private final long previewBytes;
        private final long totalBytes;

        private Estimate(long cropVoxels,
                         long sourceBytes,
                         long unionFindBytes,
                         long nodeArrayBytes,
                         long childArrayBytes,
                         long attributeBytes,
                         long oneLazyLabelMapBytes,
                         long queryScratchBytes,
                         long combinationBytes,
                         long previewBytes,
                         long totalBytes) {
            this.cropVoxels = cropVoxels;
            this.sourceBytes = sourceBytes;
            this.unionFindBytes = unionFindBytes;
            this.nodeArrayBytes = nodeArrayBytes;
            this.childArrayBytes = childArrayBytes;
            this.attributeBytes = attributeBytes;
            this.oneLazyLabelMapBytes = oneLazyLabelMapBytes;
            this.queryScratchBytes = queryScratchBytes;
            this.combinationBytes = combinationBytes;
            this.previewBytes = previewBytes;
            this.totalBytes = totalBytes;
        }

        private Estimate withPreviewBytes(long bytes) {
            long safe = Math.max(0L, bytes);
            return new Estimate(cropVoxels, sourceBytes, unionFindBytes,
                    nodeArrayBytes, childArrayBytes, attributeBytes,
                    oneLazyLabelMapBytes, queryScratchBytes, combinationBytes, safe,
                    saturatingAdd(totalBytes, safe));
        }

        private Estimate withCombinationBytes(long bytes) {
            long safe = Math.max(0L, bytes);
            return new Estimate(cropVoxels, sourceBytes, unionFindBytes,
                    nodeArrayBytes, childArrayBytes, attributeBytes,
                    oneLazyLabelMapBytes, queryScratchBytes, safe, previewBytes,
                    saturatingAdd(totalBytes, safe));
        }

        private Estimate withQueryScratchBytes(long bytes) {
            long safe = Math.max(0L, bytes);
            return new Estimate(cropVoxels, sourceBytes, unionFindBytes,
                    nodeArrayBytes, childArrayBytes, attributeBytes,
                    oneLazyLabelMapBytes, safe, combinationBytes, previewBytes,
                    saturatingAdd(totalBytes, safe));
        }

        public long cropVoxels() {
            return cropVoxels;
        }

        public long sourceBytes() {
            return sourceBytes;
        }

        public long unionFindBytes() {
            return unionFindBytes;
        }

        public long nodeArrayBytes() {
            return nodeArrayBytes;
        }

        public long childArrayBytes() {
            return childArrayBytes;
        }

        public long treeBytes() {
            return saturatingAdd(saturatingAdd(unionFindBytes, nodeArrayBytes), childArrayBytes);
        }

        public long attributeBytes() {
            return attributeBytes;
        }

        public long oneLazyLabelMapBytes() {
            return oneLazyLabelMapBytes;
        }

        public long queryScratchBytes() {
            return queryScratchBytes;
        }

        public long combinationBytes() {
            return combinationBytes;
        }

        public long previewBytes() {
            return previewBytes;
        }

        public long totalBytes() {
            return totalBytes;
        }
    }

    public static final class Feasibility {
        public final boolean ok;
        public final long estimatedBytes;
        public final long availableBytes;
        public final String message;
        private final Estimate estimate;

        private Feasibility(boolean ok, Estimate estimate, long availableBytes, String message) {
            this.ok = ok;
            this.estimate = estimate;
            this.estimatedBytes = estimate == null ? 0L : estimate.totalBytes();
            this.availableBytes = availableBytes;
            this.message = message == null ? "" : message;
        }

        private static Feasibility refused(Estimate estimate, long availableBytes, String message) {
            return new Feasibility(false, estimate, availableBytes, message);
        }

        public boolean isOk() {
            return ok;
        }

        public long getEstimatedBytes() {
            return estimatedBytes;
        }

        public long getAvailableBytes() {
            return availableBytes;
        }

        public String getMessage() {
            return message;
        }

        public Estimate estimate() {
            return estimate;
        }
    }

    public static final class Decision {
        public enum Status { PERMITTED, REFUSED }

        private final Status status;
        private final String reason;
        private final Estimate estimate;

        private Decision(Status status, String reason, Estimate estimate) {
            this.status = status;
            this.reason = reason == null ? "" : reason;
            this.estimate = estimate;
        }

        static Decision permitted(Estimate estimate) {
            return new Decision(Status.PERMITTED, "", estimate);
        }

        static Decision refused(Estimate estimate, String reason) {
            return new Decision(Status.REFUSED, reason, estimate);
        }

        public Status status() {
            return status;
        }

        public boolean permitted() {
            return status == Status.PERMITTED;
        }

        public boolean refused() {
            return status == Status.REFUSED;
        }

        public String reason() {
            return reason;
        }

        public Estimate estimate() {
            return estimate;
        }
    }
}
