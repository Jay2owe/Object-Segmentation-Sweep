/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.token;

import segsweep.sweep.CanonicalScale;
import segsweep.sweep.ParameterId;
import segsweep.sweep.ParameterValueList;
import segsweep.sweep.SweepProvenance;

import java.awt.Rectangle;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class SettingsTokenWriter {
    public static final String VERSION = "0.1.0";

    private SettingsTokenWriter() {
    }

    public static String write(SegmentationMethod method, SweepProvenance provenance) {
        return write(method, provenance, PickSummary.empty(), Instant.now());
    }

    public static String write(SegmentationMethod method,
                               SweepProvenance provenance,
                               PickSummary pickSummary,
                               Instant writtenAt) {
        return write(method, provenance, pickSummary, writtenAt, "", 0);
    }

    public static String write(SegmentationMethod method,
                               SweepProvenance provenance,
                               PickSummary pickSummary,
                               Instant writtenAt,
                               String imageIdentity,
                               int channel) {
        if (provenance == null) {
            throw new IllegalArgumentException("provenance must not be null");
        }
        SegmentationMethod safeMethod = method == null
                ? SegmentationMethod.classical("classical")
                : method;
        PickSummary safePick = pickSummary == null ? PickSummary.empty() : pickSummary;
        Instant safeInstant = writtenAt == null ? Instant.now() : writtenAt;

        StringBuilder out = new StringBuilder();
        out.append("# Object Segmentation Sweep ").append(VERSION).append('\n');
        out.append("# Written ").append(safeInstant.toString()).append('\n');
        out.append('\n');
        if (imageIdentity != null && !imageIdentity.trim().isEmpty()) {
            out.append("image\t").append(lineValue(imageIdentity)).append('\n');
        }
        if (channel > 0) out.append("channel\t").append(channel).append('\n');
        out.append("settings\t").append(SegmentationTokenParser.format(safeMethod)).append('\n');
        out.append("engine\t").append(safeMethod.engineName()).append('\n');
        if (!safePick.criterion.isEmpty()) out.append("criterion\t").append(safePick.criterion).append('\n');
        if (!safePick.knee.isEmpty()) out.append("knee\t").append(safePick.knee).append('\n');
        if (!safePick.stability.isEmpty()) out.append("stability\t").append(safePick.stability).append('\n');
        if (!safePick.agreement.isEmpty()) out.append("agreement\t").append(safePick.agreement).append('\n');
        out.append('\n');
        appendDisplayedRanges(out, provenance);
        out.append("region\t").append(regionLine(provenance)).append('\n');
        out.append("calibration\tunit=").append(emptyAsUncalibrated(provenance.calibrationUnit()))
                .append("; pixel_width=").append(CanonicalScale.formatNumber(Double.valueOf(provenance.pixelWidth())))
                .append("; pixel_height=").append(CanonicalScale.formatNumber(Double.valueOf(provenance.pixelHeight())))
                .append("; pixel_depth=").append(CanonicalScale.formatNumber(Double.valueOf(provenance.pixelDepth())))
                .append("; pixel_area=").append(CanonicalScale.formatNumber(Double.valueOf(provenance.pixelArea())))
                .append("; voxel_volume=").append(CanonicalScale.formatNumber(Double.valueOf(provenance.voxelVolume())))
                .append('\n');
        if (!provenance.connectivity().isEmpty()) {
            out.append("connectivity\t").append(provenance.connectivity()).append('\n');
        }
        out.append("provenance\t").append(provenance.toCanonicalJson()).append('\n');
        return out.toString();
    }

    private static String lineValue(String value) {
        return value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static void appendDisplayedRanges(StringBuilder out, SweepProvenance provenance) {
        List<ParameterId> keys = new ArrayList<ParameterId>(provenance.displayedRanges().keySet());
        Collections.sort(keys);
        for (int i = 0; i < keys.size(); i++) {
            ParameterId id = keys.get(i);
            ParameterValueList values = provenance.displayedRanges().get(id);
            out.append("displayed_range\t").append(id.stableKey()).append('=')
                    .append(formatValues(values)).append('\n');
        }
    }

    private static String formatValues(ParameterValueList values) {
        StringBuilder sb = new StringBuilder();
        List<Object> raw = values.values();
        for (int i = 0; i < raw.size(); i++) {
            if (i > 0) sb.append(',');
            Object value = raw.get(i);
            if (value instanceof Number) {
                sb.append(CanonicalScale.formatNumber((Number) value));
            } else {
                sb.append(String.valueOf(value));
            }
        }
        return sb.toString();
    }

    private static String regionLine(SweepProvenance provenance) {
        Rectangle bounds = provenance.crop().boundsFor(provenance.fullWidth(), provenance.fullHeight());
        return "x=" + bounds.x
                + " y=" + bounds.y
                + " w=" + bounds.width
                + " h=" + bounds.height
                + " (" + String.format(java.util.Locale.ROOT, "%.1f",
                Double.valueOf(provenance.cropFraction() * 100.0d)) + "% of image)";
    }

    private static String emptyAsUncalibrated(String unit) {
        return unit == null || unit.trim().isEmpty() ? "uncalibrated" : unit.trim();
    }

    public static final class PickSummary {
        private final String criterion;
        private final String knee;
        private final String stability;
        private final String agreement;

        public PickSummary(String criterion, String knee, String stability, String agreement) {
            this.criterion = clean(criterion);
            this.knee = clean(knee);
            this.stability = clean(stability);
            this.agreement = clean(agreement);
        }

        public static PickSummary empty() {
            return new PickSummary("", "", "", "");
        }

        public static PickSummary of(String criterion, String knee, String stability, String agreement) {
            return new PickSummary(criterion, knee, stability, agreement);
        }

        private static String clean(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
