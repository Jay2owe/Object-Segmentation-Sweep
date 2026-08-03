/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.sweep;

import java.util.Locale;

public enum ParameterId implements ParameterKey {
    THRESHOLD("threshold", "threshold"),
    MIN_SIZE("min_size", "minimum size"),
    MAX_SIZE("max_size", "maximum size"),

    VOLUME("volume", "volume"),
    MEAN_INTENSITY("mean_intensity", "mean intensity"),
    MAX_INTENSITY("max_intensity", "maximum intensity"),
    ELONGATION("elongation", "elongation"),
    SURFACE_AREA("surface_area", "surface area"),
    SPHERICITY("sphericity", "sphericity"),
    COMPACTNESS("compactness", "compactness"),
    FERET_DIAMETER_MAX("feret_diameter_max", "maximum Feret diameter"),

    PROB_THRESH("probability_threshold", "probability threshold"),
    NMS_THRESH("nms_threshold", "nms threshold"),
    LINKING_MAX("linking_max_distance", "linking max distance"),
    GAP_CLOSING_MAX("gap_closing_max_distance", "gap closing max distance"),
    FRAME_GAP("frame_gap", "frame gap"),
    AREA_MIN("area_min", "area minimum"),
    AREA_MAX("area_max", "area maximum"),
    QUALITY_MIN("quality_min", "quality minimum"),
    INTENSITY_MIN("intensity_min", "intensity minimum"),

    DIAMETER("diameter", "diameter"),
    FLOW_THRESHOLD("flow_threshold", "flow threshold"),
    CELLPROB_THRESHOLD("cellprob_threshold", "cellprob threshold"),
    MODEL("model", "model") {
        @Override
        public boolean orderable() {
            return false;
        }

        @Override
        public ValueKind valueKind() {
            return ValueKind.STRING;
        }
    };

    private final String stableKey;
    private final String displayLabel;

    ParameterId(String stableKey, String displayLabel) {
        this.stableKey = stableKey;
        this.displayLabel = displayLabel;
    }

    @Override
    public String stableKey() {
        return stableKey;
    }

    @Override
    public String displayLabel() {
        return displayLabel;
    }

    @Override
    public ValueKind valueKind() {
        return ValueKind.NUMBER;
    }

    public boolean orderable() {
        return true;
    }

    public static ParameterId fromStableKey(String key) {
        if (key == null) {
            return null;
        }
        String trimmed = key.trim();
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        for (ParameterId id : values()) {
            if (id.name().equals(trimmed)
                    || id.name().toLowerCase(Locale.ROOT).equals(normalized)
                    || id.stableKey().equals(normalized)) {
                return id;
            }
        }
        if ("prob_thresh".equals(normalized)) return PROB_THRESH;
        if ("nms_thresh".equals(normalized)) return NMS_THRESH;
        if ("linking_max".equals(normalized)) return LINKING_MAX;
        if ("gap_closing_max".equals(normalized)) return GAP_CLOSING_MAX;
        return null;
    }
}
