/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep;

import ij.measure.ResultsTable;
import segsweep.sweep.ParameterCombo;
import segsweep.sweep.SweepProvenance;
import segsweep.sweep.VariationResult;
import segsweep.sweep.analysis.PickResult;
import segsweep.tree.LazyLabelMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Output bundle returned by the public Object Segmentation Sweep Java API.
 */
public final class SegSweepResult {
    public static final String COL_COMBINATION = "Combination";
    public static final String COL_OBJECTS = "Objects";
    public static final String COL_OBJECTS_PER_MM3 = "Objects_Per_mm3";
    public static final String COL_OBJECTS_PER_MM2 = "Objects_Per_mm2";
    public static final String COL_MEAN_NEIGHBOUR_IOU = "Mean_Neighbour_IoU";
    public static final String COL_STABILITY_ELIGIBLE = "Stability_Eligible";
    public static final String COL_DURATION_MS = "Duration_ms";
    public static final String COL_CROP_FRACTION = "Crop_Fraction";
    public static final String COL_FLAGS = "Flags";

    public static final String PICK_CRITERION = "criterion";
    public static final String PICK_CHOSEN_COMBINATION = "chosen combination index";
    public static final String PICK_KNEE_OUTCOME = "knee outcome kind";
    public static final String PICK_KNEE_VALUE = "knee value";
    public static final String PICK_KNEE_RANGE_MIN = "knee computation range min";
    public static final String PICK_KNEE_RANGE_MAX = "knee computation range max";
    public static final String PICK_KNEE_RANGE_STEP = "knee computation range step";
    public static final String PICK_DISPLAY_RANGE_MIN = PICK_KNEE_RANGE_MIN;
    public static final String PICK_DISPLAY_RANGE_MAX = PICK_KNEE_RANGE_MAX;
    public static final String PICK_DISPLAY_RANGE_STEP = PICK_KNEE_RANGE_STEP;
    public static final String PICK_STABILITY_SCORE = "stability score";
    public static final String PICK_KNEE_RECOMMENDATION = "knee recommended settings";
    public static final String PICK_STABILITY_RECOMMENDATION = "stability recommended settings";
    public static final String PICK_ELIGIBLE_COUNT = "eligible count";
    public static final String PICK_CROP_X = "crop x";
    public static final String PICK_CROP_Y = "crop y";
    public static final String PICK_CROP_WIDTH = "crop width";
    public static final String PICK_CROP_HEIGHT = "crop height";
    public static final String PICK_CROP_FRACTION = "crop fraction";
    public static final String PICK_CRITERIA_AGREE = "criteria agreed";

    private final SegSweepParameters parameters;
    private final ResultsTable sweepTable;
    private final ResultsTable pickTable;
    private final PickResult pick;
    private final ParameterCombo pickedCombo;
    private final LazyLabelMap pickedLabelMap;
    private final List<VariationResult> results;
    private final SweepProvenance provenance;
    private final String pickedSettingsToken;
    private final List<String> warnings;

    SegSweepResult(SegSweepParameters parameters,
                   ResultsTable sweepTable,
                   ResultsTable pickTable,
                   PickResult pick,
                   ParameterCombo pickedCombo,
                   LazyLabelMap pickedLabelMap,
                   List<VariationResult> results,
                   SweepProvenance provenance,
                   String pickedSettingsToken,
                   List<String> warnings) {
        this.parameters = parameters;
        this.sweepTable = sweepTable;
        this.pickTable = pickTable;
        this.pick = pick;
        this.pickedCombo = pickedCombo;
        this.pickedLabelMap = pickedLabelMap;
        this.results = immutableCopy(results);
        this.provenance = provenance;
        this.pickedSettingsToken = pickedSettingsToken == null ? "" : pickedSettingsToken;
        this.warnings = immutableStringCopy(warnings);
    }

    public SegSweepParameters parameters() {
        return parameters;
    }

    public SegSweepParameters getParameters() {
        return parameters;
    }

    public ResultsTable sweepTable() {
        return sweepTable;
    }

    public ResultsTable getSweepTable() {
        return sweepTable;
    }

    public ResultsTable pickTable() {
        return pickTable;
    }

    public ResultsTable getPickTable() {
        return pickTable;
    }

    public PickResult pick() {
        return pick;
    }

    public PickResult getPick() {
        return pick;
    }

    public ParameterCombo pickedCombo() {
        return pickedCombo;
    }

    public ParameterCombo getPickedCombo() {
        return pickedCombo;
    }

    public LazyLabelMap pickedLabelMap() {
        return pickedLabelMap;
    }

    public LazyLabelMap getPickedLabelMap() {
        return pickedLabelMap;
    }

    public List<VariationResult> results() {
        return results;
    }

    public List<VariationResult> getResults() {
        return results;
    }

    public SweepProvenance provenance() {
        return provenance;
    }

    public SweepProvenance getProvenance() {
        return provenance;
    }

    public String pickedSettingsToken() {
        return pickedSettingsToken;
    }

    public String getPickedSettingsToken() {
        return pickedSettingsToken;
    }

    public List<String> warnings() {
        return warnings;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    /** Returns a result view whose picked combo/labels/settings reflect a manual grid choice. */
    public SegSweepResult withPickedSelection(ParameterCombo combo, String settingsToken) {
        LazyLabelMap selectedLabels = null;
        if (combo != null) {
            for (int i = 0; i < results.size(); i++) {
                VariationResult result = results.get(i);
                if (combo.equals(result.combo()) && result.hasLabelMap()) {
                    selectedLabels = result.labelMap();
                    break;
                }
            }
        }
        ResultsTable selectedPickTable = combo == null
                ? pickTable : SegSweepAnalysis.buildManualPickTable(this, combo);
        return new SegSweepResult(parameters, sweepTable, selectedPickTable, pick, combo,
                selectedLabels, results, provenance, settingsToken, warnings);
    }

    /**
     * Drops tree-backed variation and label providers after batch output has been written.
     * The returned summary retains scalar report tables and folder roll-up metadata.
     */
    SegSweepResult compactForBatch() {
        return new SegSweepResult(parameters == null ? null : parameters.withoutImage(),
                sweepTable, pickTable, pick, pickedCombo,
                null, Collections.<VariationResult>emptyList(), provenance,
                pickedSettingsToken, warnings);
    }

    private static List<VariationResult> immutableCopy(List<VariationResult> input) {
        if (input == null) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<VariationResult>(input));
    }

    private static List<String> immutableStringCopy(List<String> input) {
        if (input == null) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<String>(input));
    }
}
