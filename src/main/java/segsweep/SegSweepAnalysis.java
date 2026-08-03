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
import ij.io.FileInfo;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.process.ImageProcessor;
import segsweep.sweep.CanonicalScale;
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
import segsweep.sweep.VariationResult;
import segsweep.sweep.analysis.IouStability;
import segsweep.sweep.analysis.KneeDetector;
import segsweep.sweep.analysis.KneeOutcome;
import segsweep.sweep.analysis.PickResult;
import segsweep.sweep.analysis.StabilityOutcome;
import segsweep.token.SettingsTokenWriter;
import segsweep.token.SegmentationMethod;
import segsweep.tree.ComponentTree;
import segsweep.tree.ComponentTreeQuery;
import segsweep.tree.ComponentTreeResult;
import segsweep.tree.LazyLabelMap;
import segsweep.tree.MorphologyAttribute;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Headless orchestration for one Object Segmentation Sweep run.
 */
public final class SegSweepAnalysis {
    private SegSweepAnalysis() {
    }

    public static SegSweepResult run(SegSweepParameters params) {
        return run(params, null, null);
    }

    public static SegSweepResult run(SegSweepParameters params,
                                     Consumer<SweepProgress> progress,
                                     BooleanSupplier cancelCheck) {
        validate(params);
        checkCancelled(cancelCheck);

        ParameterSweep displayWindow = buildDisplayWindow(params);
        ImagePlus channelImage = selectChannel(params.image(), params.channel());
        boolean ownsChannelImage = channelImage != params.image();
        ImagePlus cropped = null;
        boolean ownsCrop = false;
        try {
            SweepProvenance provenance = provenance(params, displayWindow);
            List<String> warnings = initialWarnings(params, provenance);
            cropped = params.crop().apply(channelImage);
            ownsCrop = cropped != channelImage;
            checkCancelled(cancelCheck);

            ResourceGuard.Feasibility feasibility =
                    ResourceGuard.assessFeasibility(croppedWindow(displayWindow), cropped);
            if (!feasibility.isOk()) {
                throw new SweepRefusedException(feasibility.getMessage());
            }

            final Consumer<SweepProgress> progressSink = progress;
            ComponentTree tree = ComponentTree.build(cropped, params.connectivity(), cancelCheck,
                    new BiConsumer<Integer, Integer>() {
                        @Override public void accept(Integer done, Integer total) {
                            emit(progressSink, done.intValue(), total.intValue(),
                                    "building", "Building component tree.");
                        }
                    });
            checkCancelled(cancelCheck);
            double[] fullThresholdValues = fullThresholdAxis(cropped, tree);
            List<VariationResult> displayedResults =
                    queryDisplayedResults(tree, displayWindow, provenance, progress, cancelCheck);
            PickAssembly pickAssembly = scoreAndPick(tree, displayWindow,
                    displayedResults, provenance, params, warnings, fullThresholdValues,
                    progress, cancelCheck);
            checkCancelled(cancelCheck);

            ResultsTable sweepTable = buildSweepTable(displayWindow,
                    pickAssembly.scoredResults, pickAssembly.stability);
            ResultsTable pickTable = params.pickCriterion() == SegSweepParameters.PickCriterion.NONE
                    ? new ResultsTable()
                    : buildPickTable(displayWindow, pickAssembly.pick,
                    pickAssembly.pickedCombo, pickAssembly.chosenCombinationIndex,
                    params.pickCriterion().name().toLowerCase(Locale.ROOT), provenance);
            String token = buildSettingsToken(params, provenance, pickAssembly.pick,
                    pickAssembly.pickedCombo);

            return new SegSweepResult(params, sweepTable, pickTable, pickAssembly.pick,
                    pickAssembly.pickedCombo, pickAssembly.pickedLabelMap,
                    pickAssembly.scoredResults, provenance, token, warnings);
        } finally {
            if (ownsCrop) {
                close(cropped);
            }
            if (ownsChannelImage) {
                close(channelImage);
            }
        }
    }

    private static void validate(SegSweepParameters params) {
        if (params == null) {
            throw new SegSweepParameters.ValidationException(
                    SegSweepParameters.ValidationFailure.NO_IMAGE,
                    "SegSweep parameters must not be null.");
        }
        ImagePlus image = params.image();
        if (image == null || image.getStack() == null || image.getStackSize() <= 0
                || image.getWidth() <= 0 || image.getHeight() <= 0) {
            throw new SegSweepParameters.ValidationException(
                    SegSweepParameters.ValidationFailure.NO_IMAGE,
                    "SegSweep requires a source ImagePlus with at least one slice.");
        }
        int channels = Math.max(1, image.getNChannels());
        if (params.channel() < 1 || params.channel() > channels) {
            throw new SegSweepParameters.ValidationException(
                    SegSweepParameters.ValidationFailure.INVALID_CHANNEL,
                    "Channel " + params.channel() + " is outside the image channel range 1.."
                            + channels + ".");
        }
        if (params.engine() != SegmentationMethod.Engine.CLASSICAL) {
            throw new SegSweepParameters.ValidationException(
                    SegSweepParameters.ValidationFailure.UNSUPPORTED_ENGINE,
                    "Only the Classical engine is executable in v0.1.0.");
        }
        if (params.axes().isEmpty()) {
            throw new SegSweepParameters.ValidationException(
                    SegSweepParameters.ValidationFailure.EMPTY_AXIS,
                    "SegSweep requires one or two display axes.");
        }
        if (params.axes().size() > 2) {
            throw new SegSweepParameters.ValidationException(
                    SegSweepParameters.ValidationFailure.UNSUPPORTED_AXIS_COMBINATION,
                    "The v0.1.0 public API accepts at most two display axes.");
        }
        for (Map.Entry<ParameterId, ParameterValueList> entry : params.axes().entrySet()) {
            ParameterId id = entry.getKey();
            ParameterValueList values = entry.getValue();
            if (id == null || values == null || values.size() == 0) {
                throw new SegSweepParameters.ValidationException(
                        SegSweepParameters.ValidationFailure.EMPTY_AXIS,
                        "Every display axis must have a parameter id and at least one value.");
            }
            if (!isClassicalAxis(id)) {
                throw new SegSweepParameters.ValidationException(
                        SegSweepParameters.ValidationFailure.UNSUPPORTED_AXIS_COMBINATION,
                        "Axis " + id.stableKey()
                                + " is not supported by the v0.1.0 Classical engine.");
            }
        }
        if (params.crop().mode() == CropSpec.Mode.CUSTOM) {
            Rectangle requested = params.crop().bounds();
            if (requested == null || requested.x < 0 || requested.y < 0
                    || requested.x + requested.width > image.getWidth()
                    || requested.y + requested.height > image.getHeight()) {
                throw new SegSweepParameters.ValidationException(
                        SegSweepParameters.ValidationFailure.CROP_OUTSIDE_IMAGE_BOUNDS,
                        "Custom crop " + requested + " must be fully inside the source image.");
            }
        }
    }

    private static ParameterSweep buildDisplayWindow(SegSweepParameters params) {
        LinkedHashMap<ParameterId, ParameterValueList> axes =
                new LinkedHashMap<ParameterId, ParameterValueList>(params.axes());
        return new ParameterSweep(ParameterSweep.Method.CLASSICAL, axes,
                params.crop(), "C" + params.channel());
    }

    private static ParameterSweep croppedWindow(ParameterSweep displayWindow) {
        return new ParameterSweep(displayWindow.method(), displayWindow.valueLists(),
                CropSpec.full(), displayWindow.channelName());
    }

    private static SweepProvenance provenance(SegSweepParameters params,
                                              ParameterSweep displayWindow) {
        Calibration calibration = params.image().getCalibration();
        String unit = calibration == null ? "" : calibration.getUnit();
        double pixelWidth = 1.0d;
        double pixelHeight = 1.0d;
        double pixelDepth = 1.0d;
        if (calibration != null
                && Double.isFinite(calibration.pixelWidth) && calibration.pixelWidth > 0.0d
                && Double.isFinite(calibration.pixelHeight) && calibration.pixelHeight > 0.0d) {
            pixelWidth = calibration.pixelWidth;
            pixelHeight = calibration.pixelHeight;
            if (Double.isFinite(calibration.pixelDepth) && calibration.pixelDepth > 0.0d) {
                pixelDepth = calibration.pixelDepth;
            }
        }
        LinkedHashMap<ParameterId, ParameterValueList> ranges =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        for (Map.Entry<ParameterId, ParameterValueList> entry : params.axes().entrySet()) {
            ranges.put(entry.getKey(), entry.getValue());
        }
        return new SweepProvenance(params.crop(), params.image().getWidth(),
                params.image().getHeight(), Math.max(1, params.image().getNSlices()),
                ranges, unit, pixelWidth, pixelHeight, pixelDepth,
                params.connectivity().name());
    }

    private static List<String> initialWarnings(SegSweepParameters params,
                                                SweepProvenance provenance) {
        List<String> warnings = new ArrayList<String>();
        if (!provenance.hasMetricCalibration()) {
            warnings.add("Input image is uncalibrated or has an unrecognised physical unit; density is left blank.");
        }
        if (provenance.belowMinimumFraction(params.minimumCropFraction())) {
            warnings.add(String.format(Locale.ROOT,
                    "Crop fraction %.6f is below the configured minimum %.6f.",
                    Double.valueOf(provenance.cropFraction()),
                    Double.valueOf(params.minimumCropFraction())));
        }
        return warnings;
    }

    private static List<VariationResult> queryDisplayedResults(ComponentTree tree,
                                                               ParameterSweep displayWindow,
                                                               SweepProvenance provenance,
                                                               Consumer<SweepProgress> progress,
                                                               BooleanSupplier cancelCheck) {
        List<ParameterCombo> ordered = SweepDispatchOrder.order(displayWindow);
        List<VariationResult> results = new ArrayList<VariationResult>(ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            checkCancelled(cancelCheck);
            ParameterCombo combo = ordered.get(i);
            long started = System.currentTimeMillis();
            ComponentTreeResult treeResult = tree.query(toTreeQuery(combo));
            long durationMs = Math.max(0L, System.currentTimeMillis() - started);
            EnumSet<VariationResult.Flag> flags = EnumSet.noneOf(VariationResult.Flag.class);
            if (treeResult.status() == ComponentTreeResult.Status.TOO_MANY_LABELS) {
                flags.add(VariationResult.Flag.TOO_MANY_LABELS);
                results.add(VariationResult.failure(combo,
                        new IllegalStateException(treeResult.reason()), provenance,
                        flags, treeResult.objectCount()));
            } else {
                results.add(VariationResult.success(combo, treeResult.labelMap(),
                        treeResult.objectCount(), durationMs, null, provenance, flags,
                        IouStability.IouSource.fromTreeResult(treeResult)));
            }
            emit(progress, i + 1, ordered.size(), "querying",
                    "Querying component tree.");
        }
        return canonicalResultOrder(displayWindow, results);
    }

    private static PickAssembly scoreAndPick(ComponentTree tree,
                                             ParameterSweep displayWindow,
                                             List<VariationResult> displayedResults,
                                             SweepProvenance provenance,
                                              SegSweepParameters params,
                                              List<String> warnings,
                                              double[] fullThresholdValues,
                                              Consumer<SweepProgress> progress,
                                              BooleanSupplier cancelCheck) {
        if (params.pickCriterion() == SegSweepParameters.PickCriterion.NONE) {
            return new PickAssembly(null, null, null, displayedResults,
                    null, 0);
        }

        emit(progress, 0, 2, "scoring", "Scoring stability.");
        StabilityOutcome stability = scoreStability(displayedResults, cancelCheck,
                params.stabilityBudgetMs());
        checkCancelled(cancelCheck);
        emit(progress, 1, 2, "scoring", "Scoring knee.");
        List<VariationResult> scoredResults = withStability(displayedResults, stability);
        if (stability.kind() != StabilityOutcome.Kind.STABLE_AT) {
            warnings.add("Stability pick: " + stability.explanation());
        }

        KneeAssembly kneeAssembly = scoreKnee(tree, displayWindow, provenance,
                params, displayedResults, fullThresholdValues, cancelCheck);
        emit(progress, 2, 2, "scoring", "Pick scoring complete.");
        KneeOutcome knee = kneeAssembly.outcome;
        if (knee.kind() != KneeOutcome.Kind.KNEE_AT) {
            warnings.add("Knee pick: " + knee.explanation());
        }

        ParameterCombo stabilityCombo = stability.kind() == StabilityOutcome.Kind.STABLE_AT
                && stability.index() >= 0 && stability.index() < scoredResults.size()
                ? scoredResults.get(stability.index()).combo() : null;
        PickResult pick = new PickResult(knee, stability, provenance,
                kneeAssembly.combo, stabilityCombo);
        ChosenPick chosen = choosePicked(params.pickCriterion(), pick, displayWindow,
                scoredResults, kneeAssembly);
        return new PickAssembly(pick, stability, chosen.combo, scoredResults,
                chosen.labelMap, chosen.displayCombinationIndex);
    }

    private static StabilityOutcome scoreStability(List<VariationResult> results,
                                                    BooleanSupplier cancelCheck,
                                                    long budgetMs) {
        List<ParameterCombo> combos = new ArrayList<ParameterCombo>(results.size());
        List<IouStability.IouSource> sources =
                new ArrayList<IouStability.IouSource>(results.size());
        for (int i = 0; i < results.size(); i++) {
            VariationResult result = results.get(i);
            combos.add(result.combo());
            sources.add(result.iouSource());
        }
        return IouStability.score(combos, sources, cancelCheck, budgetMs);
    }

    private static List<VariationResult> withStability(List<VariationResult> results,
                                                       StabilityOutcome stability) {
        List<VariationResult> out = new ArrayList<VariationResult>(results.size());
        for (int i = 0; i < results.size(); i++) {
            out.add(results.get(i).withMeanNeighbourIou(stability.meanNeighbourIou(i)));
        }
        return out;
    }

    private static KneeAssembly scoreKnee(ComponentTree tree,
                                          ParameterSweep displayWindow,
                                          SweepProvenance provenance,
                                          SegSweepParameters params,
                                          List<VariationResult> displayedResults,
                                          double[] fullThresholdValues,
                                          BooleanSupplier cancelCheck) {
        ParameterId axis = params.axes().containsKey(ParameterId.THRESHOLD)
                ? ParameterId.THRESHOLD : firstAxis(params);
        ParameterValueList displayedValues = params.axes().get(axis);
        double[] displayStats = rangeStats(displayedValues);
        if (varyingAxisCount(params) > 1) {
            return new KneeAssembly(KneeOutcome.of(
                    KneeOutcome.Kind.MULTI_AXIS_UNSUPPORTED,
                    displayStats[0], displayStats[1], displayStats[2],
                    "Knee scoring is one-dimensional; it is not defined for two varying sweep axes."),
                    null, null);
        }
        if (axis == ParameterId.THRESHOLD) {
            double[] thresholdValues = fullThresholdValues == null
                    ? new double[0] : fullThresholdValues;
            if (thresholdValues.length > 0) {
                double[] xs = Arrays.copyOf(thresholdValues, thresholdValues.length);
                double[] counts = new double[thresholdValues.length];
                ParameterCombo base = firstCombo(displayWindow);
                int[] objectCounts = tree.objectCountsAtThresholds(
                        xs, toTreeQuery(base), cancelCheck);
                for (int i = 0; i < objectCounts.length; i++) {
                    if (objectCounts[i] < 0) {
                        return new KneeAssembly(KneeOutcome.of(
                                KneeOutcome.Kind.TOO_MANY_OBJECTS,
                                displayStats[0], displayStats[1], displayStats[2],
                                "At least one threshold exceeds the 16-bit object limit; knee scoring was refused."),
                                null, null);
                    }
                    counts[i] = objectCounts[i];
                }
                KneeOutcome outcome = KneeDetector.detect(xs, counts,
                        displayStats[0], displayStats[1], displayStats[2]);
                if (outcome.kind() == KneeOutcome.Kind.KNEE_AT) {
                    ParameterCombo pickedCombo = comboWith(base, ParameterId.THRESHOLD,
                            Double.valueOf(outcome.parameterValue()));
                    ComponentTreeResult picked = tree.query(toTreeQuery(pickedCombo));
                    return new KneeAssembly(outcome, pickedCombo, picked.labelMap());
                }
                return new KneeAssembly(outcome, null, null);
            }
        }

        double[] xs = new double[displayedResults.size()];
        double[] counts = new double[displayedResults.size()];
        for (int i = 0; i < displayedResults.size(); i++) {
            xs[i] = numericValue(displayedResults.get(i).combo().get(axis), axis);
            counts[i] = displayedResults.get(i).objectCount();
        }
        KneeOutcome outcome = KneeDetector.detect(xs, counts,
                displayStats[0], displayStats[1], displayStats[2]);
        if (outcome.kind() == KneeOutcome.Kind.KNEE_AT
                && outcome.index() >= 0 && outcome.index() < displayedResults.size()) {
            VariationResult result = displayedResults.get(outcome.index());
            return new KneeAssembly(outcome, result.combo(), result.labelMap());
        }
        return new KneeAssembly(outcome, null, null);
    }

    private static ChosenPick choosePicked(SegSweepParameters.PickCriterion criterion,
                                           PickResult pick,
                                           ParameterSweep displayWindow,
                                           List<VariationResult> results,
                                           KneeAssembly kneeAssembly) {
        if (criterion == SegSweepParameters.PickCriterion.NONE || pick == null) {
            return ChosenPick.none();
        }
        if (criterion == SegSweepParameters.PickCriterion.KNEE) {
            return kneeAssembly.combo == null ? ChosenPick.none()
                    : new ChosenPick(kneeAssembly.combo, kneeAssembly.labelMap,
                    displayIndex(displayWindow, kneeAssembly.combo));
        }
        if (criterion == SegSweepParameters.PickCriterion.STABILITY) {
            StabilityOutcome stability = pick.stability();
            if (stability.kind() == StabilityOutcome.Kind.STABLE_AT
                    && stability.index() >= 0 && stability.index() < results.size()) {
                VariationResult result = results.get(stability.index());
                return new ChosenPick(result.combo(), result.labelMap(),
                        displayIndex(displayWindow, result.combo()));
            }
            return ChosenPick.none();
        }
        if (pick.criteriaAgree() && kneeAssembly.combo != null
                && kneeAssembly.labelMap != null) {
            return new ChosenPick(kneeAssembly.combo, kneeAssembly.labelMap,
                    displayIndex(displayWindow, kneeAssembly.combo));
        }
        return ChosenPick.none();
    }

    private static ResultsTable buildSweepTable(ParameterSweep displayWindow,
                                                List<VariationResult> results,
                                                StabilityOutcome stability) {
        ResultsTable table = new ResultsTable();
        List<ParameterId> axes = displayWindow.parameterIds();
        for (int i = 0; i < results.size(); i++) {
            VariationResult result = results.get(i);
            int row = table.getCounter();
            table.incrementCounter();
            table.setValue(SegSweepResult.COL_COMBINATION, row, i + 1);
            for (int a = 0; a < axes.size(); a++) {
                ParameterId axis = axes.get(a);
                Object value = result.combo().get(axis);
                if (value instanceof Number) {
                    table.setValue(axis.displayLabel(), row, ((Number) value).doubleValue());
                } else {
                    table.setValue(axis.displayLabel(), row, String.valueOf(value));
                }
            }
            table.setValue(SegSweepResult.COL_OBJECTS, row, result.objectCount());
            if (result.provenance().fullDepth() > 1) {
                if (Double.isFinite(result.objectsPerCalibratedVolume())) {
                    table.setValue(SegSweepResult.COL_OBJECTS_PER_MM3, row,
                            result.objectsPerCalibratedVolume());
                } else {
                    table.setValue(SegSweepResult.COL_OBJECTS_PER_MM3, row, "");
                }
            } else {
                if (Double.isFinite(result.objectsPerCalibratedArea())) {
                    table.setValue(SegSweepResult.COL_OBJECTS_PER_MM2, row,
                            result.objectsPerCalibratedArea());
                } else {
                    table.setValue(SegSweepResult.COL_OBJECTS_PER_MM2, row, "");
                }
            }
            if (Double.isFinite(result.meanNeighbourIou())) {
                table.setValue(SegSweepResult.COL_MEAN_NEIGHBOUR_IOU, row,
                        result.meanNeighbourIou());
            } else {
                table.setValue(SegSweepResult.COL_MEAN_NEIGHBOUR_IOU, row, "");
            }
            table.setValue(SegSweepResult.COL_STABILITY_ELIGIBLE, row,
                    String.valueOf(stability != null && stability.isEligible(i)));
            table.setValue(SegSweepResult.COL_DURATION_MS, row, result.durationMs());
            table.setValue(SegSweepResult.COL_CROP_FRACTION, row,
                    result.provenance().cropFraction());
            table.setValue(SegSweepResult.COL_FLAGS, row, flags(result.flags()));
        }
        return table;
    }

    private static ResultsTable buildPickTable(ParameterSweep displayWindow,
                                               PickResult pick,
                                               ParameterCombo pickedCombo,
                                               int displayCombinationIndex,
                                               String criterion,
                                               SweepProvenance provenance) {
        ResultsTable table = new ResultsTable();
        int row = table.getCounter();
        table.incrementCounter();
        table.setValue(SegSweepResult.PICK_CRITERION, row,
                criterion == null ? "" : criterion);
        table.setValue(SegSweepResult.PICK_CHOSEN_COMBINATION, row,
                displayCombinationIndex > 0 ? displayCombinationIndex : 0);
        List<ParameterId> axes = displayWindow.parameterIds();
        for (int i = 0; i < axes.size(); i++) {
            ParameterId axis = axes.get(i);
            Object value = pickedCombo == null ? null : pickedCombo.get(axis);
            if (value instanceof Number) {
                table.setValue(axis.displayLabel(), row, ((Number) value).doubleValue());
            } else {
                table.setValue(axis.displayLabel(), row, value == null ? "" : String.valueOf(value));
            }
        }
        KneeOutcome knee = pick == null ? null : pick.knee();
        StabilityOutcome stability = pick == null ? null : pick.stability();
        table.setValue(SegSweepResult.PICK_KNEE_OUTCOME, row,
                knee == null ? "" : knee.kind().name());
        if (knee != null && Double.isFinite(knee.parameterValue())) {
            table.setValue(SegSweepResult.PICK_KNEE_VALUE, row,
                    knee.parameterValue());
        } else {
            table.setValue(SegSweepResult.PICK_KNEE_VALUE, row, "");
        }
        if (knee != null) {
            table.setValue(SegSweepResult.PICK_DISPLAY_RANGE_MIN, row, knee.rangeMin());
            table.setValue(SegSweepResult.PICK_DISPLAY_RANGE_MAX, row, knee.rangeMax());
            table.setValue(SegSweepResult.PICK_DISPLAY_RANGE_STEP, row, knee.step());
        } else {
            table.setValue(SegSweepResult.PICK_DISPLAY_RANGE_MIN, row, "");
            table.setValue(SegSweepResult.PICK_DISPLAY_RANGE_MAX, row, "");
            table.setValue(SegSweepResult.PICK_DISPLAY_RANGE_STEP, row, "");
        }
        if (stability != null && Double.isFinite(stability.meanNeighbourIou())) {
            table.setValue(SegSweepResult.PICK_STABILITY_SCORE, row,
                    stability.meanNeighbourIou());
        } else {
            table.setValue(SegSweepResult.PICK_STABILITY_SCORE, row, "");
        }
        table.setValue(SegSweepResult.PICK_KNEE_RECOMMENDATION, row,
                pick == null || pick.kneeCombo() == null
                        ? "" : pick.kneeCombo().toCanonicalJson());
        table.setValue(SegSweepResult.PICK_STABILITY_RECOMMENDATION, row,
                pick == null || pick.stabilityCombo() == null
                        ? "" : pick.stabilityCombo().toCanonicalJson());
        table.setValue(SegSweepResult.PICK_ELIGIBLE_COUNT, row,
                stability == null ? 0 : stability.eligibleCount());
        SweepProvenance reportProvenance = provenance != null
                ? provenance : pick == null ? null : pick.provenance();
        Rectangle crop = reportProvenance.crop().boundsFor(
                reportProvenance.fullWidth(), reportProvenance.fullHeight());
        table.setValue(SegSweepResult.PICK_CROP_X, row, crop.x);
        table.setValue(SegSweepResult.PICK_CROP_Y, row, crop.y);
        table.setValue(SegSweepResult.PICK_CROP_WIDTH, row, crop.width);
        table.setValue(SegSweepResult.PICK_CROP_HEIGHT, row, crop.height);
        table.setValue(SegSweepResult.PICK_CROP_FRACTION, row,
                reportProvenance.cropFraction());
        table.setValue(SegSweepResult.PICK_CRITERIA_AGREE, row,
                pick == null ? "" : String.valueOf(pick.criteriaAgree()));
        return table;
    }

    static ResultsTable buildManualPickTable(SegSweepResult result,
                                             ParameterCombo selected) {
        if (result == null || result.parameters() == null || selected == null) {
            return new ResultsTable();
        }
        ParameterSweep displayWindow = buildDisplayWindow(result.parameters());
        return buildPickTable(displayWindow, result.pick(), selected,
                displayIndex(displayWindow, selected), "manual", result.provenance());
    }

    private static String buildSettingsToken(SegSweepParameters params,
                                             SweepProvenance provenance,
                                             PickResult pick,
                                             ParameterCombo pickedCombo) {
        SegmentationMethod method = methodFor(params, pickedCombo);
        SettingsTokenWriter.PickSummary summary = pick == null
                ? SettingsTokenWriter.PickSummary.empty()
                : SettingsTokenWriter.PickSummary.of(
                params.pickCriterion().name().toLowerCase(Locale.ROOT),
                pick.knee().kind().name() + valueSuffix(pick.knee().parameterValue())
                        + comboSuffix(pick.kneeCombo()),
                pick.stability().kind().name() + valueSuffix(pick.stability().meanNeighbourIou())
                        + comboSuffix(pick.stabilityCombo()),
                String.valueOf(pick.criteriaAgree()));
        return SettingsTokenWriter.write(method, provenance, summary, java.time.Instant.now(),
                imageIdentity(params == null ? null : params.image()),
                params == null ? 0 : params.channel());
    }

    static SegmentationMethod methodFor(SegSweepParameters params, ParameterCombo combo) {
        if (combo == null) {
            return SegmentationMethod.classical("classical");
        }
        LinkedHashMap<String, String> values = new LinkedHashMap<String, String>();
        values.put("thresh", CanonicalScale.formatNumber(Double.valueOf(
                doubleParameter(combo, ParameterId.THRESHOLD, 0.0d))));
        values.put("minSize", Integer.toString(intParameter(combo, ParameterId.MIN_SIZE, 0)));
        values.put("maxSize", Integer.toString(
                intParameter(combo, ParameterId.MAX_SIZE, Integer.MAX_VALUE)));
        if (params != null) {
            values.put("channel", Integer.toString(params.channel()));
            values.put("connectivity", params.connectivity().name().toLowerCase(Locale.ROOT));
        }
        List<String> morphology = new ArrayList<String>();
        if (combo != null) {
            for (Map.Entry<ParameterKey, Object> entry : combo.values().entrySet()) {
                MorphologyAttribute attribute = morphologyAttribute(entry.getKey());
                if (attribute != null) {
                    morphology.add(attribute.token() + ">=" + CanonicalScale.formatNumber(
                            Double.valueOf(numericValue(entry.getValue(), entry.getKey()))));
                }
            }
        }
        if (!morphology.isEmpty()) {
            Collections.sort(morphology);
            StringBuilder encoded = new StringBuilder();
            for (int i = 0; i < morphology.size(); i++) {
                if (i > 0) encoded.append(',');
                encoded.append(morphology.get(i));
            }
            values.put("morph", encoded.toString());
        }
        return new SegmentationMethod(SegmentationMethod.Engine.CLASSICAL, values, "");
    }

    private static String imageIdentity(ImagePlus image) {
        if (image == null) return "";
        FileInfo info = image.getOriginalFileInfo();
        if (info != null && info.fileName != null && !info.fileName.trim().isEmpty()) {
            return info.fileName.trim();
        }
        return image.getTitle() == null ? "" : image.getTitle().trim();
    }

    private static ComponentTreeQuery toTreeQuery(ParameterCombo combo) {
        ComponentTreeQuery.Builder builder = ComponentTreeQuery.builder()
                .threshold(doubleParameter(combo, ParameterId.THRESHOLD, 0.0d))
                .minSize(intParameter(combo, ParameterId.MIN_SIZE, 0))
                .maxSize(intParameter(combo, ParameterId.MAX_SIZE, Integer.MAX_VALUE));
        for (Map.Entry<ParameterKey, Object> entry : combo.values().entrySet()) {
            MorphologyAttribute attribute = morphologyAttribute(entry.getKey());
            if (attribute != null) {
                builder.predicate(new segsweep.tree.MorphologyPredicate(
                        attribute, segsweep.tree.MorphologyPredicate.Operator.GE,
                        numericValue(entry.getValue(), entry.getKey())));
            }
        }
        return builder.build();
    }

    private static boolean isClassicalAxis(ParameterId id) {
        return id == ParameterId.THRESHOLD
                || id == ParameterId.MIN_SIZE
                || id == ParameterId.MAX_SIZE
                || morphologyAttribute(id) != null;
    }

    private static MorphologyAttribute morphologyAttribute(ParameterKey key) {
        if (!(key instanceof ParameterId)) return null;
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

    private static ParameterId firstAxis(SegSweepParameters params) {
        return params.axes().keySet().iterator().next();
    }

    private static ParameterCombo firstCombo(ParameterSweep displayWindow) {
        List<ParameterCombo> combos = displayWindow.combos();
        if (combos.isEmpty()) {
            throw new SegSweepParameters.ValidationException(
                    SegSweepParameters.ValidationFailure.EMPTY_AXIS,
                    "Display window produced no parameter combinations.");
        }
        return combos.get(0);
    }

    private static ParameterCombo comboWith(ParameterCombo base,
                                            ParameterId id,
                                            Object value) {
        LinkedHashMap<ParameterKey, Object> values =
                new LinkedHashMap<ParameterKey, Object>(base.values());
        values.put(id, value);
        return new ParameterCombo(values);
    }

    private static int displayIndex(ParameterSweep displayWindow, ParameterCombo picked) {
        if (picked == null) return 0;
        List<ParameterCombo> combos = displayWindow.combos();
        for (int i = 0; i < combos.size(); i++) {
            if (combos.get(i).hasSameCoordinates(picked)) {
                return i + 1;
            }
        }
        return 0;
    }

    private static int varyingAxisCount(SegSweepParameters params) {
        int count = 0;
        for (ParameterValueList values : params.axes().values()) {
            if (values != null && values.size() > 1) count++;
        }
        return count;
    }

    private static List<VariationResult> canonicalResultOrder(
            ParameterSweep displayWindow,
            List<VariationResult> dispatched) {
        List<VariationResult> canonical = new ArrayList<VariationResult>(dispatched.size());
        List<ParameterCombo> expected = displayWindow.combos();
        for (int i = 0; i < expected.size(); i++) {
            VariationResult match = null;
            for (int j = 0; j < dispatched.size(); j++) {
                if (expected.get(i).hasSameCoordinates(dispatched.get(j).combo())) {
                    match = dispatched.get(j);
                    break;
                }
            }
            if (match == null) {
                throw new IllegalStateException("A dispatched result is missing from the display grid.");
            }
            canonical.add(match);
        }
        return canonical;
    }

    private static double[] rangeStats(ParameterValueList values) {
        if (values == null || values.size() == 0) {
            return new double[] { Double.NaN, Double.NaN, Double.NaN };
        }
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < values.size(); i++) {
            double value = numericValue(values.get(i), ParameterId.THRESHOLD);
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        double step = values.size() >= 2
                ? Math.abs(numericValue(values.get(1), ParameterId.THRESHOLD)
                - numericValue(values.get(0), ParameterId.THRESHOLD))
                : Double.NaN;
        return new double[] { min, max, step };
    }

    private static double[] fullThresholdAxis(ImagePlus image, ComponentTree tree) {
        if (image == null || image.getStack() == null) {
            return new double[0];
        }
        if (image.getBitDepth() == 32) {
            return tree == null ? new double[0] : tree.thresholdLevels();
        }
        int max = 0;
        ImageStack stack = image.getStack();
        for (int slice = 1; slice <= stack.getSize(); slice++) {
            ImageProcessor processor = stack.getProcessor(slice);
            for (int i = 0; i < processor.getPixelCount(); i++) {
                float value = processor.getf(i);
                if (Float.isFinite(value)) {
                    max = Math.max(max, (int) Math.ceil(value));
                }
            }
        }
        double[] values = new double[max + 1];
        for (int i = 0; i <= max; i++) {
            values[i] = i;
        }
        return values;
    }

    private static void emit(Consumer<SweepProgress> progress,
                             int completed,
                             int total,
                             String phase,
                             String message) {
        if (progress != null) {
            progress.accept(new SweepProgress(Math.max(0, completed), Math.max(0, total),
                    0, null, phase, message));
        }
    }

    private static void checkCancelled(BooleanSupplier cancelCheck) {
        if (cancelCheck != null && cancelCheck.getAsBoolean()) {
            throw new CancellationException("Object Segmentation Sweep was cancelled.");
        }
    }

    private static int intParameter(ParameterCombo combo, ParameterId id, int fallback) {
        Object value = combo == null ? null : combo.get(id);
        if (value == null) return fallback;
        if (value instanceof Number) {
            double parsed = ((Number) value).doubleValue();
            if (!Double.isFinite(parsed)) return fallback;
            if (parsed >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
            if (parsed <= Integer.MIN_VALUE) return Integer.MIN_VALUE;
            return Math.max(0, (int) Math.round(parsed));
        }
        try {
            return Math.max(0, (int) Math.round(Double.parseDouble(String.valueOf(value))));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double doubleParameter(ParameterCombo combo, ParameterId id, double fallback) {
        Object value = combo == null ? null : combo.get(id);
        if (value == null) return fallback;
        if (value instanceof Number) {
            double parsed = ((Number) value).doubleValue();
            return Double.isFinite(parsed) ? parsed : fallback;
        }
        try {
            double parsed = Double.parseDouble(String.valueOf(value));
            return Double.isFinite(parsed) ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double numericValue(Object value, ParameterKey key) {
        if (value instanceof Number) {
            double parsed = ((Number) value).doubleValue();
            if (Double.isFinite(parsed)) return parsed;
        }
        try {
            double parsed = Double.parseDouble(String.valueOf(value));
            if (Double.isFinite(parsed)) return parsed;
        } catch (NumberFormatException ignored) {
            // Typed message below.
        }
        throw new IllegalArgumentException("Parameter " + key.stableKey()
                + " must be a finite numeric value.");
    }

    private static String flags(EnumSet<VariationResult.Flag> flags) {
        if (flags == null || flags.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        for (VariationResult.Flag flag : flags) {
            if (out.length() > 0) out.append(';');
            out.append(flag.name());
        }
        return out.toString();
    }

    private static String valueSuffix(double value) {
        return Double.isFinite(value)
                ? "=" + CanonicalScale.formatNumber(Double.valueOf(value))
                : "";
    }

    private static String comboSuffix(ParameterCombo combo) {
        return combo == null ? "" : "; combo=" + combo.toCanonicalJson();
    }

    private static ImagePlus selectChannel(ImagePlus source, int channel) {
        int channels = Math.max(1, source.getNChannels());
        if (channels <= 1) {
            return source;
        }
        int slices = Math.max(1, source.getNSlices());
        ImageStack input = source.getStack();
        ImageStack output = new ImageStack(source.getWidth(), source.getHeight());
        for (int z = 1; z <= slices; z++) {
            int index = source.getStackIndex(channel, z, 1);
            ImageProcessor processor = input.getProcessor(index).duplicate();
            output.addSlice(input.getSliceLabel(index), processor);
        }
        ImagePlus selected = new ImagePlus(source.getTitle() + " C" + channel, output);
        Calibration calibration = source.getCalibration();
        if (calibration != null) {
            selected.setCalibration(calibration.copy());
        }
        selected.setDimensions(1, slices, 1);
        selected.setOpenAsHyperStack(slices > 1);
        return selected;
    }

    private static void close(ImagePlus image) {
        if (image == null) return;
        image.changes = false;
        image.close();
        image.flush();
    }

    private static final class PickAssembly {
        final PickResult pick;
        final StabilityOutcome stability;
        final ParameterCombo pickedCombo;
        final List<VariationResult> scoredResults;
        final LazyLabelMap pickedLabelMap;
        final int chosenCombinationIndex;

        PickAssembly(PickResult pick,
                     StabilityOutcome stability,
                     ParameterCombo pickedCombo,
                     List<VariationResult> scoredResults,
                     LazyLabelMap pickedLabelMap,
                     int chosenCombinationIndex) {
            this.pick = pick;
            this.stability = stability;
            this.pickedCombo = pickedCombo;
            this.scoredResults = scoredResults;
            this.pickedLabelMap = pickedLabelMap;
            this.chosenCombinationIndex = chosenCombinationIndex;
        }
    }

    private static final class KneeAssembly {
        final KneeOutcome outcome;
        final ParameterCombo combo;
        final LazyLabelMap labelMap;

        KneeAssembly(KneeOutcome outcome, ParameterCombo combo, LazyLabelMap labelMap) {
            this.outcome = outcome;
            this.combo = combo;
            this.labelMap = labelMap;
        }
    }

    private static final class ChosenPick {
        final ParameterCombo combo;
        final LazyLabelMap labelMap;
        final int displayCombinationIndex;

        ChosenPick(ParameterCombo combo, LazyLabelMap labelMap, int displayCombinationIndex) {
            this.combo = combo;
            this.labelMap = labelMap;
            this.displayCombinationIndex = displayCombinationIndex;
        }

        static ChosenPick none() {
            return new ChosenPick(null, null, 0);
        }
    }
}
