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
import segsweep.sweep.CanonicalScale;
import segsweep.sweep.CropSpec;
import segsweep.sweep.ParameterId;
import segsweep.sweep.ParameterValueList;
import segsweep.token.SegmentationMethod;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Macro-facing Object Segmentation Sweep options.
 */
public final class SegSweepMacroOptions {
    public static final String AUTOSAVE_ALONGSIDE_INPUT = "alongside input";

    private String image;
    private int channel = 1;
    private SegmentationMethod.Engine engine = SegmentationMethod.Engine.CLASSICAL;
    private AxisSpec primaryAxis;
    private AxisSpec secondaryAxis;
    private CropSpec crop = CropSpec.full();
    private SegSweepParameters.PickCriterion pickCriterion = SegSweepParameters.PickCriterion.BOTH;
    private double minimumCropFraction = 0.05d;
    private long stabilityBudgetMs = 0L;
    private String autosave;
    private boolean hideDisplay;
    private boolean showGrid = true;
    private boolean showTables = true;

    public String image() {
        return image;
    }

    public void setImage(String image) {
        this.image = clean(image);
    }

    public int channel() {
        return channel;
    }

    public void setChannel(int channel) {
        if (channel < 1) {
            throw new IllegalArgumentException("channel must be >= 1.");
        }
        this.channel = channel;
    }

    public SegmentationMethod.Engine engine() {
        return engine;
    }

    public void setEngine(SegmentationMethod.Engine engine) {
        this.engine = engine == null ? SegmentationMethod.Engine.CLASSICAL : engine;
    }

    public AxisSpec primaryAxis() {
        return primaryAxis;
    }

    public void setPrimaryAxis(AxisSpec primaryAxis) {
        this.primaryAxis = primaryAxis;
    }

    public AxisSpec secondaryAxis() {
        return secondaryAxis;
    }

    public void setSecondaryAxis(AxisSpec secondaryAxis) {
        this.secondaryAxis = secondaryAxis;
    }

    public CropSpec crop() {
        return crop;
    }

    public void setCrop(CropSpec crop) {
        this.crop = crop == null ? CropSpec.full() : crop;
    }

    public SegSweepParameters.PickCriterion pickCriterion() {
        return pickCriterion;
    }

    public void setPickCriterion(SegSweepParameters.PickCriterion pickCriterion) {
        this.pickCriterion = pickCriterion == null
                ? SegSweepParameters.PickCriterion.BOTH
                : pickCriterion;
    }

    public double minimumCropFraction() {
        return minimumCropFraction;
    }

    public void setMinimumCropFraction(double minimumCropFraction) {
        if (!Double.isFinite(minimumCropFraction) || minimumCropFraction < 0.0d) {
            throw new IllegalArgumentException("min_crop_fraction must be a non-negative finite number.");
        }
        this.minimumCropFraction = minimumCropFraction;
    }

    public long stabilityBudgetMs() {
        return stabilityBudgetMs;
    }

    public void setStabilityBudgetMs(long stabilityBudgetMs) {
        if (stabilityBudgetMs < 0L) {
            throw new IllegalArgumentException("stability_budget_ms must be >= 0.");
        }
        this.stabilityBudgetMs = stabilityBudgetMs;
    }

    public String autosave() {
        return autosave;
    }

    public void setAutosave(String autosave) {
        String value = clean(autosave);
        this.autosave = AUTOSAVE_ALONGSIDE_INPUT.equalsIgnoreCase(value == null ? "" : value)
                ? null : value;
    }

    public boolean hideDisplay() {
        return hideDisplay;
    }

    public void setHideDisplay(boolean hideDisplay) {
        this.hideDisplay = hideDisplay;
    }

    public boolean showGrid() {
        return showGrid;
    }

    public void setShowGrid(boolean showGrid) {
        this.showGrid = showGrid;
    }

    public boolean showTables() {
        return showTables;
    }

    public void setShowTables(boolean showTables) {
        this.showTables = showTables;
    }

    public SegSweepParameters toParameters(ImagePlus source) {
        validate();
        SegSweepParameters.Builder builder = SegSweepParameters.builder()
                .image(source)
                .channel(channel)
                .engine(engine)
                .crop(crop)
                .pickCriterion(pickCriterion)
                .minimumCropFraction(minimumCropFraction)
                .stabilityBudgetMs(stabilityBudgetMs);
        addAxis(builder, primaryAxis);
        if (secondaryAxis != null) {
            addAxis(builder, secondaryAxis);
        }
        return builder.build();
    }

    public void validate() {
        if (primaryAxis == null) {
            throw new IllegalArgumentException("Macro option sweep is required.");
        }
        primaryAxis.validate("values", "from/to/step");
        SegSweepParameters.validateAxisValues(primaryAxis.id(), primaryAxis.valueList());
        if (secondaryAxis != null) {
            secondaryAxis.validate("values2", "from2/to2/step2");
            SegSweepParameters.validateAxisValues(
                    secondaryAxis.id(), secondaryAxis.valueList());
            if (primaryAxis.id() == secondaryAxis.id()) {
                throw new IllegalArgumentException("sweep and sweep2 must name different parameters.");
            }
        }
        if (engine != SegmentationMethod.Engine.CLASSICAL) {
            throw new IllegalArgumentException("engine must be classical in v0.1.0.");
        }
    }

    public String toMacroOptions() {
        validate();
        List<String> tokens = new ArrayList<String>();
        append(tokens, "image", image);
        tokens.add("channel=" + channel);
        tokens.add("engine=" + engine.name().toLowerCase(Locale.ROOT));
        appendAxis(tokens, primaryAxis, "", false);
        if (secondaryAxis != null) {
            appendAxis(tokens, secondaryAxis, "2", true);
        }
        appendCrop(tokens, crop);
        tokens.add("pick=" + pickCriterion.name().toLowerCase(Locale.ROOT));
        tokens.add("min_crop_fraction=" + CanonicalScale.formatNumber(Double.valueOf(minimumCropFraction)));
        tokens.add("stability_budget_ms=" + stabilityBudgetMs);
        append(tokens, "autosave", autosave);
        if (hideDisplay) {
            tokens.add("hide_display");
        } else {
            if (!showGrid) tokens.add("hide_grid");
            if (!showTables) tokens.add("hide_tables");
        }
        return join(tokens);
    }

    public static SegSweepMacroOptions defaults() {
        SegSweepMacroOptions options = new SegSweepMacroOptions();
        options.setPrimaryAxis(AxisSpec.range(ParameterId.THRESHOLD, 10.0d, 60.0d, 5.0d));
        return options;
    }

    private static void addAxis(SegSweepParameters.Builder builder, AxisSpec axis) {
        if (axis.hasExplicitValues()) {
            builder.axis(axis.id(), axis.values());
        } else {
            builder.axis(axis.id(), axis.from(), axis.to(), axis.step());
        }
    }

    private static void appendAxis(List<String> tokens, AxisSpec axis,
                                   String suffix, boolean secondary) {
        String sweepKey = secondary ? "sweep" + suffix : "sweep";
        tokens.add(sweepKey + "=" + axis.id().stableKey());
        if (axis.hasExplicitValues()) {
            tokens.add("values" + suffix + "=" + encodeValue(axis.valuesCsv()));
        } else {
            tokens.add("from" + suffix + "=" + format(axis.from()));
            tokens.add("to" + suffix + "=" + format(axis.to()));
            tokens.add("step" + suffix + "=" + format(axis.step()));
        }
    }

    private static void appendCrop(List<String> tokens, CropSpec crop) {
        if (crop == null || crop.mode() == CropSpec.Mode.FULL) {
            return;
        }
        Rectangle bounds = crop.bounds();
        if (bounds != null) {
            tokens.add("crop=" + encodeValue(bounds.x + "," + bounds.y + ","
                    + bounds.width + "," + bounds.height));
        }
    }

    private static void append(List<String> tokens, String key, String value) {
        if (hasText(value)) {
            tokens.add(key + "=" + encodeValue(value));
        }
    }

    static String encodeValue(String value) {
        String normalized = value == null ? "" : value.trim().replace('\\', '/');
        if (normalized.indexOf('[') >= 0 || normalized.indexOf(']') >= 0
                || normalized.indexOf('"') >= 0 || normalized.indexOf('\n') >= 0
                || normalized.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Macro option values must not contain brackets, quotes, or line breaks.");
        }
        return "[" + normalized + "]";
    }

    private static String format(double value) {
        return CanonicalScale.formatNumber(Double.valueOf(value));
    }

    private static String join(List<String> tokens) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(tokens.get(i));
        }
        return sb.toString();
    }

    static String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.length() == 0 ? null : trimmed;
    }

    static boolean hasText(String value) {
        return value != null && value.trim().length() > 0;
    }

    public static final class AxisSpec {
        private final ParameterId id;
        private final Double from;
        private final Double to;
        private final Double step;
        private final ParameterValueList values;

        private AxisSpec(ParameterId id,
                         Double from,
                         Double to,
                         Double step,
                         ParameterValueList values) {
            if (id == null) {
                throw new IllegalArgumentException("sweep parameter must be recognised.");
            }
            this.id = id;
            this.from = from;
            this.to = to;
            this.step = step;
            this.values = values;
        }

        public static AxisSpec range(ParameterId id, double from, double to, double step) {
            return new AxisSpec(id, Double.valueOf(from), Double.valueOf(to),
                    Double.valueOf(step), null);
        }

        public static AxisSpec values(ParameterId id, ParameterValueList values) {
            return new AxisSpec(id, null, null, null, values);
        }

        public ParameterId id() {
            return id;
        }

        public boolean hasExplicitValues() {
            return values != null;
        }

        public double from() {
            return from == null ? Double.NaN : from.doubleValue();
        }

        public double to() {
            return to == null ? Double.NaN : to.doubleValue();
        }

        public double step() {
            return step == null ? Double.NaN : step.doubleValue();
        }

        public ParameterValueList values() {
            return values;
        }

        public ParameterValueList valueList() {
            return hasExplicitValues()
                    ? values
                    : ParameterValueList.fromRange(from(), to(), step());
        }

        public String valuesCsv() {
            ParameterValueList list = valueList();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(',');
                Object value = list.get(i);
                if (value instanceof Number) {
                    sb.append(CanonicalScale.formatNumber((Number) value));
                } else {
                    sb.append(String.valueOf(value));
                }
            }
            return sb.toString();
        }

        private void validate(String valuesName, String rangeName) {
            if (hasExplicitValues()) {
                if (values == null || values.size() == 0) {
                    throw new IllegalArgumentException(valuesName + " must name at least one value.");
                }
                return;
            }
            if (from == null || to == null || step == null) {
                throw new IllegalArgumentException("Provide either " + valuesName
                        + " or " + rangeName + ".");
            }
            ParameterValueList.fromRange(from.doubleValue(), to.doubleValue(), step.doubleValue());
        }

        @Override public String toString() {
            return id.stableKey() + "=" + Arrays.asList(valueList().values());
        }
    }
}
