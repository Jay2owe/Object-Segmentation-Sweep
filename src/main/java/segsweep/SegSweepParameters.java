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
import segsweep.sweep.CropSpec;
import segsweep.sweep.ParameterId;
import segsweep.sweep.ParameterValueList;
import segsweep.token.SegmentationMethod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable input bundle for headless Object Segmentation Sweep analysis.
 */
public final class SegSweepParameters {
    public enum PickCriterion { KNEE, STABILITY, BOTH, NONE }

    private final ImagePlus image;
    private final int channel;
    private final SegmentationMethod.Engine engine;
    private final LinkedHashMap<ParameterId, ParameterValueList> axes;
    private final CropSpec crop;
    private final SegSweepLabeller.Connectivity connectivity;
    private final PickCriterion pickCriterion;
    private final double minimumCropFraction;
    private final long stabilityBudgetMs;
    private final int parallelism;

    private SegSweepParameters(Builder builder) {
        this.image = builder.image;
        this.channel = builder.channel;
        this.engine = builder.engine;
        this.axes = new LinkedHashMap<ParameterId, ParameterValueList>(builder.axes);
        this.crop = builder.crop;
        this.connectivity = builder.connectivity;
        this.pickCriterion = builder.pickCriterion;
        this.minimumCropFraction = builder.minimumCropFraction;
        this.stabilityBudgetMs = builder.stabilityBudgetMs;
        this.parallelism = builder.parallelism > 0
                ? builder.parallelism
                : Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    }

    public static Builder builder() {
        return new Builder();
    }

    public ImagePlus image() {
        return image;
    }

    public ImagePlus getImage() {
        return image;
    }

    public int channel() {
        return channel;
    }

    public int getChannel() {
        return channel;
    }

    public SegmentationMethod.Engine engine() {
        return engine;
    }

    public SegmentationMethod.Engine getEngine() {
        return engine;
    }

    public Map<ParameterId, ParameterValueList> axes() {
        return Collections.unmodifiableMap(axes);
    }

    public Map<ParameterId, ParameterValueList> getAxes() {
        return axes();
    }

    public List<ParameterId> axisIds() {
        return Collections.unmodifiableList(new ArrayList<ParameterId>(axes.keySet()));
    }

    public CropSpec crop() {
        return crop;
    }

    public CropSpec getCrop() {
        return crop;
    }

    public SegSweepLabeller.Connectivity connectivity() {
        return connectivity;
    }

    public SegSweepLabeller.Connectivity getConnectivity() {
        return connectivity;
    }

    public PickCriterion pickCriterion() {
        return pickCriterion;
    }

    public PickCriterion getPickCriterion() {
        return pickCriterion;
    }

    public double minimumCropFraction() {
        return minimumCropFraction;
    }

    public double getMinimumCropFraction() {
        return minimumCropFraction;
    }

    public long stabilityBudgetMs() {
        return stabilityBudgetMs;
    }

    public long getStabilityBudgetMs() {
        return stabilityBudgetMs;
    }

    public int parallelism() {
        return parallelism;
    }

    public int getParallelism() {
        return parallelism;
    }

    SegSweepParameters withoutImage() {
        Builder builder = new Builder();
        builder.image = null;
        builder.channel = channel;
        builder.engine = engine;
        builder.axes.putAll(axes);
        builder.crop = crop;
        builder.connectivity = connectivity;
        builder.pickCriterion = pickCriterion;
        builder.minimumCropFraction = minimumCropFraction;
        builder.stabilityBudgetMs = stabilityBudgetMs;
        builder.parallelism = parallelism;
        return new SegSweepParameters(builder);
    }

    static void validateAxisValues(ParameterId id, ParameterValueList values) {
        if (id == null || values == null || values.size() == 0) {
            throw new ValidationException(ValidationFailure.EMPTY_AXIS,
                    "Sweep axes must have a parameter id and at least one value.");
        }
        double[] seen = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            if (!(value instanceof Number)) {
                throw new ValidationException(ValidationFailure.INVALID_AXIS_VALUE,
                        "Axis " + id.stableKey() + " value " + (i + 1)
                                + " must be numeric, not " + String.valueOf(value) + ".");
            }
            double numeric = ((Number) value).doubleValue();
            if (!Double.isFinite(numeric)) {
                throw new ValidationException(ValidationFailure.INVALID_AXIS_VALUE,
                        "Axis " + id.stableKey() + " value " + (i + 1)
                                + " must be finite.");
            }
            if (id == ParameterId.MIN_SIZE || id == ParameterId.MAX_SIZE) {
                if (numeric < 0.0d || numeric > Integer.MAX_VALUE
                        || numeric != Math.rint(numeric)) {
                    throw new ValidationException(ValidationFailure.INVALID_AXIS_VALUE,
                            "Axis " + id.stableKey() + " value " + (i + 1)
                                    + " must be a non-negative integer no greater than "
                                    + Integer.MAX_VALUE + ".");
                }
            }
            for (int previous = 0; previous < i; previous++) {
                if (numeric == seen[previous]) {
                    throw new ValidationException(ValidationFailure.INVALID_AXIS_VALUE,
                            "Axis " + id.stableKey() + " contains duplicate numeric coordinate "
                                    + numeric + " at values " + (previous + 1) + " and " + (i + 1)
                                    + ". Every grid coordinate must be unique.");
                }
            }
            seen[i] = numeric;
        }
    }

    public static final class Builder {
        private ImagePlus image;
        private int channel = 1;
        private SegmentationMethod.Engine engine = SegmentationMethod.Engine.CLASSICAL;
        private final LinkedHashMap<ParameterId, ParameterValueList> axes =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        private CropSpec crop = CropSpec.full();
        private SegSweepLabeller.Connectivity connectivity = SegSweepLabeller.DEFAULT_CONNECTIVITY;
        private PickCriterion pickCriterion = PickCriterion.BOTH;
        private double minimumCropFraction = 0.05d;
        private long stabilityBudgetMs;
        private int parallelism = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);

        private Builder() {
        }

        public Builder image(ImagePlus image) {
            this.image = image;
            return this;
        }

        public Builder channel(int channel) {
            this.channel = channel;
            return this;
        }

        public Builder engine(SegmentationMethod.Engine engine) {
            this.engine = engine == null ? null : engine;
            return this;
        }

        public Builder axis(ParameterId id, double from, double to, double step) {
            if (id == null) {
                throw new ValidationException(ValidationFailure.EMPTY_AXIS,
                        "Sweep axis id must not be null.");
            }
            if (!Double.isFinite(from) || !Double.isFinite(to) || !Double.isFinite(step)) {
                throw new ValidationException(ValidationFailure.EMPTY_AXIS,
                        "Axis " + id.stableKey() + " range values must be finite.");
            }
            if (from > to) {
                throw new ValidationException(ValidationFailure.FROM_GREATER_THAN_TO,
                        "Axis " + id.stableKey() + " has from > to (" + from + " > " + to + ").");
            }
            if (step == 0.0d) {
                throw new ValidationException(ValidationFailure.ZERO_STEP,
                        "Axis " + id.stableKey() + " step must not be zero.");
            }
            if (step < 0.0d) {
                throw new ValidationException(ValidationFailure.ZERO_STEP,
                        "Axis " + id.stableKey() + " step must be positive for an ascending range.");
            }
            return axis(id, ParameterValueList.fromRange(from, to, step));
        }

        public Builder axis(ParameterId id, ParameterValueList values) {
            if (id == null || values == null || values.size() == 0) {
                throw new ValidationException(ValidationFailure.EMPTY_AXIS,
                        "Sweep axes must have a parameter id and at least one value.");
            }
            if (axes.containsKey(id)) {
                throw new ValidationException(ValidationFailure.UNSUPPORTED_AXIS_COMBINATION,
                        "Sweep parameter " + id.stableKey() + " was provided more than once.");
            }
            validateAxisValues(id, values);
            axes.put(id, values);
            return this;
        }

        public Builder axis(ParameterId id, List<?> values) {
            if (values == null || values.isEmpty()) {
                throw new ValidationException(ValidationFailure.EMPTY_AXIS,
                        "Axis " + (id == null ? "<null>" : id.stableKey()) + " must not be empty.");
            }
            return axis(id, ParameterValueList.of(values));
        }

        public Builder crop(CropSpec crop) {
            this.crop = crop == null ? CropSpec.full() : crop;
            return this;
        }

        public Builder connectivity(SegSweepLabeller.Connectivity connectivity) {
            this.connectivity = connectivity == null
                    ? SegSweepLabeller.DEFAULT_CONNECTIVITY : connectivity;
            return this;
        }

        public Builder pickCriterion(PickCriterion pickCriterion) {
            this.pickCriterion = pickCriterion == null ? PickCriterion.BOTH : pickCriterion;
            return this;
        }

        public Builder minimumCropFraction(double minimumCropFraction) {
            if (!Double.isFinite(minimumCropFraction) || minimumCropFraction < 0.0d) {
                throw new ValidationException(ValidationFailure.INVALID_CROP_FRACTION,
                        "Minimum crop fraction must be a non-negative finite number.");
            }
            this.minimumCropFraction = minimumCropFraction;
            return this;
        }

        public Builder stabilityBudgetMs(long stabilityBudgetMs) {
            if (stabilityBudgetMs < 0L) {
                throw new ValidationException(ValidationFailure.INVALID_STABILITY_BUDGET,
                        "Stability budget must be >= 0 ms (0 means unlimited).");
            }
            this.stabilityBudgetMs = stabilityBudgetMs;
            return this;
        }

        public Builder parallelism(int parallelism) {
            this.parallelism = parallelism;
            return this;
        }

        public SegSweepParameters build() {
            return new SegSweepParameters(this);
        }
    }

    public enum ValidationFailure {
        NO_IMAGE,
        EMPTY_AXIS,
        FROM_GREATER_THAN_TO,
        ZERO_STEP,
        CROP_OUTSIDE_IMAGE_BOUNDS,
        UNSUPPORTED_ENGINE,
        UNSUPPORTED_AXIS_COMBINATION,
        INVALID_CHANNEL,
        UNSUPPORTED_BIT_DEPTH,
        UNSUPPORTED_TIME_SERIES,
        INVALID_AXIS_VALUE,
        INVALID_CROP_FRACTION,
        INVALID_STABILITY_BUDGET
    }

    public static final class ValidationException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        private final ValidationFailure failure;

        public ValidationException(ValidationFailure failure, String message) {
            super(message);
            this.failure = failure;
        }

        public ValidationFailure failure() {
            return failure;
        }
    }
}
