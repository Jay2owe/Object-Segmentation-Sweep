/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.sweep;

public final class ParameterLabels {

    private ParameterLabels() {
    }

    public static String labelFor(ParameterKey key) {
        return editorLabel(key);
    }

    public static String labelFor(ParameterId id) {
        return editorLabel(id);
    }

    public static String shortKey(ParameterKey key) {
        if (key instanceof ParameterId) {
            return shortKey((ParameterId) key);
        }
        if (key == null) {
            return "";
        }
        String stable = key.stableKey();
        return stable == null || stable.trim().isEmpty()
                ? key.displayLabel()
                : stable;
    }

    public static String shortKey(ParameterId id) {
        if (id == null) return "";
        if (id == ParameterId.THRESHOLD) return "threshold";
        if (id == ParameterId.MIN_SIZE) return "minSize";
        if (id == ParameterId.MAX_SIZE) return "maxSize";
        if (id == ParameterId.MEAN_INTENSITY) return "meanIntensity";
        if (id == ParameterId.MAX_INTENSITY) return "maxIntensity";
        if (id == ParameterId.SURFACE_AREA) return "surfaceArea";
        if (id == ParameterId.FERET_DIAMETER_MAX) return "feretMax";
        if (id == ParameterId.PROB_THRESH) return "probThresh";
        if (id == ParameterId.NMS_THRESH) return "nms";
        if (id == ParameterId.LINKING_MAX) return "linkingMax";
        if (id == ParameterId.GAP_CLOSING_MAX) return "gapClosingMax";
        if (id == ParameterId.FRAME_GAP) return "frameGap";
        if (id == ParameterId.AREA_MIN) return "areaMin";
        if (id == ParameterId.AREA_MAX) return "areaMax";
        if (id == ParameterId.QUALITY_MIN) return "qualityMin";
        if (id == ParameterId.INTENSITY_MIN) return "intensityMin";
        if (id == ParameterId.FLOW_THRESHOLD) return "flow";
        if (id == ParameterId.CELLPROB_THRESHOLD) return "cellprob";
        return id.stableKey();
    }

    public static String editorLabel(ParameterKey key) {
        if (key instanceof ParameterId) {
            return editorLabel((ParameterId) key);
        }
        return key == null ? "" : key.displayLabel();
    }

    public static String editorLabel(ParameterId id) {
        return id == null ? "" : id.displayLabel();
    }
}
