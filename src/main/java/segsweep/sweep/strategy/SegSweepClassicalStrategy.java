/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.sweep.strategy;

import ij.ImagePlus;
import ij.measure.Calibration;
import segsweep.SegSweepLabeller;
import segsweep.sweep.CropSpec;
import segsweep.sweep.ParameterCombo;
import segsweep.sweep.ParameterId;
import segsweep.sweep.ParameterKey;
import segsweep.sweep.ParameterSweep;
import segsweep.sweep.ParameterValueList;
import segsweep.sweep.ResourceGuard;
import segsweep.sweep.SweepDispatchOrder;
import segsweep.sweep.SweepProgress;
import segsweep.sweep.SweepProvenance;
import segsweep.sweep.SweepRefusedException;
import segsweep.sweep.VariationResult;
import segsweep.sweep.VariationStrategy;
import segsweep.sweep.analysis.IouStability;
import segsweep.token.MorphPredicate;
import segsweep.tree.ComponentTree;
import segsweep.tree.ComponentTreeQuery;
import segsweep.tree.ComponentTreeResult;
import segsweep.tree.MorphologyAttribute;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class SegSweepClassicalStrategy implements VariationStrategy {

    private final ImagePlus source;
    private final CropSpec crop;
    private final SegSweepLabeller.Connectivity connectivity;
    private final SweepProvenance provenance;
    private final List<MorphPredicate> baseMorphPredicates;
    private final TreeMemoryGuard guard;
    private final TreeFactory treeFactory;
    private final ImageCloser imageCloser;

    public SegSweepClassicalStrategy(ImagePlus source,
                                     CropSpec crop,
                                     SegSweepLabeller.Connectivity connectivity,
                                     SweepProvenance provenance) {
        this(source, crop, connectivity, provenance,
                Collections.<MorphPredicate>emptyList());
    }

    public SegSweepClassicalStrategy(ImagePlus source,
                                     CropSpec crop,
                                     SegSweepLabeller.Connectivity connectivity,
                                     SweepProvenance provenance,
                                     List<MorphPredicate> baseMorphPredicates) {
        this(source, crop, connectivity, provenance, baseMorphPredicates,
                DefaultTreeMemoryGuard.INSTANCE,
                DefaultTreeFactory.INSTANCE,
                DefaultImageCloser.INSTANCE);
    }

    SegSweepClassicalStrategy(ImagePlus source,
                              CropSpec crop,
                              SegSweepLabeller.Connectivity connectivity,
                              SweepProvenance provenance,
                              List<MorphPredicate> baseMorphPredicates,
                              TreeMemoryGuard guard,
                              TreeFactory treeFactory,
                              ImageCloser imageCloser) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        this.source = source;
        this.crop = crop == null ? CropSpec.full() : crop;
        this.connectivity = connectivity == null
                ? SegSweepLabeller.DEFAULT_CONNECTIVITY : connectivity;
        this.provenance = provenance;
        this.baseMorphPredicates = Collections.unmodifiableList(
                new ArrayList<MorphPredicate>(baseMorphPredicates == null
                        ? Collections.<MorphPredicate>emptyList() : baseMorphPredicates));
        this.guard = guard == null ? DefaultTreeMemoryGuard.INSTANCE : guard;
        this.treeFactory = treeFactory == null ? DefaultTreeFactory.INSTANCE : treeFactory;
        this.imageCloser = imageCloser == null ? DefaultImageCloser.INSTANCE : imageCloser;
    }

    @Override
    public void dispatch(ParameterSweep displayWindow,
                         Consumer<VariationResult> publisher,
                         Consumer<SweepProgress> progress,
                         BooleanSupplier cancelCheck) throws Exception {
        if (displayWindow == null) {
            throw new IllegalArgumentException("displayWindow must not be null");
        }
        if (displayWindow.method() != ParameterSweep.Method.CLASSICAL) {
            throw new IllegalArgumentException("SegSweepClassicalStrategy only accepts Classical sweeps.");
        }
        if (publisher == null) {
            throw new IllegalArgumentException("publisher must not be null");
        }
        if (isCancelled(cancelCheck)) {
            return;
        }

        List<ParameterCombo> ordered = SweepDispatchOrder.order(displayWindow);
        ImagePlus cropped = null;
        boolean ownsCrop = false;
        try {
            emit(progress, new SweepProgress(0, ordered.size(), 0, null,
                    "cropping", "Applying sweep crop."));
            cropped = crop.apply(source);
            ownsCrop = cropped != source;
            if (isCancelled(cancelCheck)) {
                return;
            }

            emit(progress, new SweepProgress(0, ordered.size(), 0, null,
                    "guarding", "Checking component-tree memory."));
            GuardVerdict verdict = guard.assess(displayWindow, cropped);
            if (!verdict.permitted()) {
                throw new SweepRefusedException(verdict.reason());
            }
            if (isCancelled(cancelCheck)) {
                return;
            }

            emit(progress, new SweepProgress(0, ordered.size(), 0, null,
                    "building", "Building component tree."));
            TreeHandle tree = treeFactory.build(cropped, connectivity);
            if (isCancelled(cancelCheck)) {
                return;
            }

            SweepProvenance activeProvenance = provenance == null
                    ? provenanceFor(displayWindow) : provenance;
            for (int i = 0; i < ordered.size(); i++) {
                if (isCancelled(cancelCheck)) {
                    return;
                }
                ParameterCombo combo = ordered.get(i);
                emit(progress, new SweepProgress(i, ordered.size(), 0, combo,
                        "querying", "Querying component tree."));
                long started = System.currentTimeMillis();
                ComponentTreeResult treeResult = tree.query(toTreeQuery(combo));
                long durationMs = Math.max(0L, System.currentTimeMillis() - started);
                if (isCancelled(cancelCheck)) {
                    return;
                }
                publisher.accept(toVariationResult(combo, treeResult, durationMs, activeProvenance));
            }
        } finally {
            if (ownsCrop) {
                imageCloser.close(cropped);
            }
        }
    }

    private ComponentTreeQuery toTreeQuery(ParameterCombo combo) {
        ComponentTreeQuery.Builder builder = ComponentTreeQuery.builder()
                .threshold(thresholdParameter(combo))
                .minSize(intParameter(combo, ParameterId.MIN_SIZE, 0))
                .maxSize(intParameter(combo, ParameterId.MAX_SIZE, Integer.MAX_VALUE));
        for (int i = 0; i < baseMorphPredicates.size(); i++) {
            builder.predicate(baseMorphPredicates.get(i).toTreePredicate());
        }
        for (Map.Entry<ParameterKey, Object> entry : combo.values().entrySet()) {
            MorphologyAttribute attribute = morphologyAttribute(entry.getKey());
            if (attribute != null) {
                builder.predicate(new segsweep.tree.MorphologyPredicate(
                        attribute, segsweep.tree.MorphologyPredicate.Operator.GE,
                        doubleParameter(entry.getValue(), entry.getKey())));
            }
        }
        return builder.build();
    }

    private static VariationResult toVariationResult(ParameterCombo combo,
                                                     ComponentTreeResult treeResult,
                                                     long durationMs,
                                                     SweepProvenance provenance) {
        EnumSet<VariationResult.Flag> flags = EnumSet.noneOf(VariationResult.Flag.class);
        if (treeResult.status() == ComponentTreeResult.Status.TOO_MANY_LABELS) {
            flags.add(VariationResult.Flag.TOO_MANY_LABELS);
        }
        return VariationResult.success(combo, treeResult.labelMap(),
                treeResult.objectCount(), durationMs, null, provenance, flags,
                IouStability.IouSource.fromTreeResult(treeResult));
    }

    private SweepProvenance provenanceFor(ParameterSweep displayWindow) {
        Calibration calibration = source.getCalibration();
        String unit = calibration == null ? "" : calibration.getUnit();
        double voxelVolume = 1.0d;
        if (calibration != null
                && Double.isFinite(calibration.pixelWidth) && calibration.pixelWidth > 0.0d
                && Double.isFinite(calibration.pixelHeight) && calibration.pixelHeight > 0.0d
                && Double.isFinite(calibration.pixelDepth) && calibration.pixelDepth > 0.0d) {
            voxelVolume = calibration.pixelWidth * calibration.pixelHeight * calibration.pixelDepth;
        }
        return new SweepProvenance(crop, source.getWidth(), source.getHeight(),
                Math.max(1, source.getStackSize()), displayedRanges(displayWindow),
                unit, voxelVolume);
    }

    private static java.util.LinkedHashMap<ParameterId, ParameterValueList> displayedRanges(
            ParameterSweep displayWindow) {
        java.util.LinkedHashMap<ParameterId, ParameterValueList> ranges =
                new java.util.LinkedHashMap<ParameterId, ParameterValueList>();
        for (Map.Entry<ParameterKey, ParameterValueList> entry : displayWindow.valueLists().entrySet()) {
            if (entry.getKey() instanceof ParameterId) {
                ranges.put((ParameterId) entry.getKey(), entry.getValue());
            }
        }
        return ranges;
    }

    private static MorphologyAttribute morphologyAttribute(ParameterKey key) {
        if (!(key instanceof ParameterId)) {
            return null;
        }
        ParameterId id = (ParameterId) key;
        if (id == ParameterId.VOLUME) return MorphologyAttribute.VOLUME;
        if (id == ParameterId.MEAN_INTENSITY) return MorphologyAttribute.MEAN_INTENSITY;
        if (id == ParameterId.MAX_INTENSITY) return MorphologyAttribute.MAX_INTENSITY;
        if (id == ParameterId.ELONGATION) return MorphologyAttribute.ELONGATION;
        if (id == ParameterId.SURFACE_AREA) return MorphologyAttribute.SURFACE_AREA;
        if (id == ParameterId.SPHERICITY) return MorphologyAttribute.SPHERICITY;
        if (id == ParameterId.COMPACTNESS) return MorphologyAttribute.COMPACTNESS;
        if (id == ParameterId.FERET_DIAMETER_MAX) return MorphologyAttribute.FERET_DIAMETER_MAX;
        return null;
    }

    private static int intParameter(ParameterCombo combo, ParameterId id, int fallback) {
        Object value = combo == null ? null : combo.get(id);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number) {
            double parsed = ((Number) value).doubleValue();
            if (!Double.isFinite(parsed)) {
                return fallback;
            }
            if (parsed >= Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
            if (parsed <= Integer.MIN_VALUE) {
                return Integer.MIN_VALUE;
            }
            return Math.max(0, (int) Math.round(parsed));
        }
        try {
            return Math.max(0, (int) Math.round(Double.parseDouble(String.valueOf(value))));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double thresholdParameter(ParameterCombo combo) {
        Object value = combo == null ? null : combo.get(ParameterId.THRESHOLD);
        if (value == null) return 0.0d;
        if (value instanceof Number) {
            double parsed = ((Number) value).doubleValue();
            return Double.isFinite(parsed) ? parsed : 0.0d;
        }
        try {
            double parsed = Double.parseDouble(String.valueOf(value));
            return Double.isFinite(parsed) ? parsed : 0.0d;
        } catch (NumberFormatException e) {
            return 0.0d;
        }
    }

    private static double doubleParameter(Object value, ParameterKey key) {
        if (value instanceof Number) {
            double parsed = ((Number) value).doubleValue();
            if (Double.isFinite(parsed)) {
                return parsed;
            }
        }
        try {
            double parsed = Double.parseDouble(String.valueOf(value));
            if (Double.isFinite(parsed)) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // Fall through to typed error.
        }
        throw new IllegalArgumentException("Morphology axis " + key.stableKey()
                + " must be a finite numeric value.");
    }

    private static boolean isCancelled(BooleanSupplier cancelCheck) {
        return Thread.currentThread().isInterrupted()
                || (cancelCheck != null && cancelCheck.getAsBoolean());
    }

    private static void emit(Consumer<SweepProgress> progress, SweepProgress value) {
        if (progress != null) {
            progress.accept(value);
        }
    }

    interface TreeFactory {
        TreeHandle build(ImagePlus cropped, SegSweepLabeller.Connectivity connectivity);
    }

    interface TreeHandle {
        ComponentTreeResult query(ComponentTreeQuery query);
    }

    interface TreeMemoryGuard {
        GuardVerdict assess(ParameterSweep displayWindow, ImagePlus cropped);
    }

    interface ImageCloser {
        void close(ImagePlus image);
    }

    static final class GuardVerdict {
        private final boolean permitted;
        private final String reason;

        private GuardVerdict(boolean permitted, String reason) {
            this.permitted = permitted;
            this.reason = reason == null ? "" : reason;
        }

        static GuardVerdict allow() {
            return new GuardVerdict(true, "");
        }

        static GuardVerdict deny(String reason) {
            return new GuardVerdict(false, reason);
        }

        boolean permitted() {
            return permitted;
        }

        String reason() {
            return reason;
        }
    }

    private static final class DefaultTreeMemoryGuard implements TreeMemoryGuard {
        static final DefaultTreeMemoryGuard INSTANCE = new DefaultTreeMemoryGuard();

        @Override public GuardVerdict assess(ParameterSweep displayWindow, ImagePlus cropped) {
            ParameterSweep croppedWindow = new ParameterSweep(displayWindow.method(),
                    displayWindow.valueLists(), CropSpec.full(), displayWindow.channelName());
            ResourceGuard.Feasibility feasibility = ResourceGuard.assessFeasibility(croppedWindow, cropped);
            return feasibility.isOk()
                    ? GuardVerdict.allow()
                    : GuardVerdict.deny(feasibility.getMessage());
        }
    }

    private static final class DefaultTreeFactory implements TreeFactory {
        static final DefaultTreeFactory INSTANCE = new DefaultTreeFactory();

        @Override public TreeHandle build(ImagePlus cropped,
                                          SegSweepLabeller.Connectivity connectivity) {
            final ComponentTree tree = ComponentTree.build(cropped, connectivity);
            return new TreeHandle() {
                @Override public ComponentTreeResult query(ComponentTreeQuery query) {
                    return tree.query(query);
                }
            };
        }
    }

    private static final class DefaultImageCloser implements ImageCloser {
        static final DefaultImageCloser INSTANCE = new DefaultImageCloser();

        @Override public void close(ImagePlus image) {
            if (image == null) {
                return;
            }
            image.changes = false;
            image.close();
            image.flush();
        }
    }
}
