/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep;

import segsweep.sweep.CropSpec;
import segsweep.sweep.ParameterId;
import segsweep.sweep.ParameterValueList;
import segsweep.token.SegmentationMethod;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Parser for ImageJ macro options passed to the Object Segmentation Sweep command.
 */
public final class SegSweepMacroOptionsParser {

    /** Every {@code key=value} option the single-image command accepts, in README order. */
    public static final Set<String> VALUE_KEYS = Collections.unmodifiableSet(
            new LinkedHashSet<String>(Arrays.asList(
                    "image", "channel", "engine", "sweep", "from", "to", "step", "values",
                    "sweep2", "from2", "to2", "step2", "values2", "crop", "pick",
                    "min_crop_fraction", "stability_budget_ms", "autosave")));

    /** Every bare flag the single-image command accepts, in README order. */
    public static final Set<String> FLAGS = Collections.unmodifiableSet(
            new LinkedHashSet<String>(Arrays.asList(
                    "hide_display", "no_display", "show_display", "hide_grid", "show_grid",
                    "hide_tables", "show_tables", "allow_oversized")));

    /** Keys whose values are file paths; backslashes in them become forward slashes. */
    static final Set<String> PATH_KEYS = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList("image", "autosave", "folder", "output")));

    /** README defaults for the primary axis when the macro leaves them out. */
    static final double DEFAULT_FROM = 10.0d;
    static final double DEFAULT_TO = 60.0d;
    static final double DEFAULT_STEP = 5.0d;

    private SegSweepMacroOptionsParser() {
    }

    /**
     * The option keys and flags named in {@code optionsText}, lower-cased, without
     * validating them. Lets a caller refuse options that make no sense in its
     * context (for example {@code image=} inside a batch run).
     */
    public static Set<String> namesIn(String optionsText) {
        Set<String> names = new LinkedHashSet<String>();
        List<String> tokens = tokenize(optionsText == null ? "" : optionsText);
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            int eq = token.indexOf('=');
            names.add((eq >= 0 ? token.substring(0, eq) : token).trim().toLowerCase(Locale.ROOT));
        }
        return names;
    }

    public static SegSweepMacroOptions parse(String optionsText) {
        BuilderState state = new BuilderState();
        Set<String> seenKeys = new HashSet<String>();
        List<String> tokens = tokenize(optionsText == null ? "" : optionsText);
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            int eq = token.indexOf('=');
            if (eq >= 0) {
                String key = token.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                String value = decodeValue(key, token.substring(eq + 1).trim());
                if (!seenKeys.add(key)) {
                    throw new IllegalArgumentException("Duplicate macro option: " + key);
                }
                applyKeyValue(state, key, value);
            } else {
                applyFlag(state.options, token.toLowerCase(Locale.ROOT));
            }
        }
        state.finishAxes();
        state.options.validate();
        return state.options;
    }

    static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<String>();
        StringBuilder token = new StringBuilder();
        boolean inBracket = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inBracket) {
                if (c == '[') {
                    throw new IllegalArgumentException("Nested brackets are not allowed in macro options.");
                }
                if (c == '\n' || c == '\r') {
                    throw new IllegalArgumentException("Line breaks are not allowed in macro option values.");
                }
                token.append(c);
                if (c == ']') inBracket = false;
                continue;
            }
            if (Character.isWhitespace(c)) {
                if (token.length() > 0) {
                    tokens.add(token.toString());
                    token.setLength(0);
                }
                continue;
            }
            if (c == '[') {
                inBracket = true;
            } else if (c == ']') {
                throw new IllegalArgumentException("Unexpected closing bracket in macro options.");
            }
            token.append(c);
        }
        if (inBracket) {
            throw new IllegalArgumentException("Unclosed bracketed macro option value.");
        }
        if (token.length() > 0) tokens.add(token.toString());
        return tokens;
    }

    private static void applyKeyValue(BuilderState state, String key, String value) {
        if (!VALUE_KEYS.contains(key)) {
            throw new IllegalArgumentException("Unknown Object Segmentation Sweep macro option: " + key);
        }
        SegSweepMacroOptions options = state.options;
        if ("image".equals(key)) {
            options.setImage(value);
        } else if ("channel".equals(key)) {
            options.setChannel(parseInt(value, "channel"));
        } else if ("engine".equals(key)) {
            options.setEngine(parseEngine(value));
        } else if ("sweep".equals(key)) {
            state.primary.id = parseParameterId(value, "sweep");
        } else if ("from".equals(key)) {
            state.primary.from = Double.valueOf(parseDouble(value, "from"));
        } else if ("to".equals(key)) {
            state.primary.to = Double.valueOf(parseDouble(value, "to"));
        } else if ("step".equals(key)) {
            state.primary.step = Double.valueOf(parseDouble(value, "step"));
        } else if ("values".equals(key)) {
            state.primary.values = parseValues(value, "values");
        } else if ("sweep2".equals(key)) {
            state.secondary.id = parseParameterId(value, "sweep2");
            state.hasSecondary = true;
        } else if ("from2".equals(key)) {
            state.secondary.from = Double.valueOf(parseDouble(value, "from2"));
            state.hasSecondary = true;
        } else if ("to2".equals(key)) {
            state.secondary.to = Double.valueOf(parseDouble(value, "to2"));
            state.hasSecondary = true;
        } else if ("step2".equals(key)) {
            state.secondary.step = Double.valueOf(parseDouble(value, "step2"));
            state.hasSecondary = true;
        } else if ("values2".equals(key)) {
            state.secondary.values = parseValues(value, "values2");
            state.hasSecondary = true;
        } else if ("crop".equals(key)) {
            options.setCrop(parseCrop(value));
        } else if ("pick".equals(key)) {
            options.setPickCriterion(parsePick(value));
        } else if ("min_crop_fraction".equals(key)) {
            options.setMinimumCropFraction(parseDouble(value, key));
        } else if ("stability_budget_ms".equals(key)) {
            options.setStabilityBudgetMs(parseLong(value, key));
        } else if ("autosave".equals(key)) {
            options.setAutosave(value);
        } else {
            throw new IllegalArgumentException("Unknown Object Segmentation Sweep macro option: " + key);
        }
    }

    private static void applyFlag(SegSweepMacroOptions options, String flag) {
        if (!FLAGS.contains(flag)) {
            throw new IllegalArgumentException("Unknown Object Segmentation Sweep macro flag: " + flag);
        }
        if ("hide_display".equals(flag) || "no_display".equals(flag)) {
            options.setHideDisplay(true);
        } else if ("show_display".equals(flag)) {
            options.setHideDisplay(false);
        } else if ("hide_grid".equals(flag)) {
            options.setShowGrid(false);
        } else if ("show_grid".equals(flag)) {
            options.setShowGrid(true);
        } else if ("hide_tables".equals(flag)) {
            options.setShowTables(false);
        } else if ("show_tables".equals(flag)) {
            options.setShowTables(true);
        } else if ("allow_oversized".equals(flag)) {
            options.setAllowOversizedSweep(true);
        } else {
            throw new IllegalArgumentException("Unknown Object Segmentation Sweep macro flag: " + flag);
        }
    }

    private static SegmentationMethod.Engine parseEngine(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if ("classical".equals(normalized)) {
            return SegmentationMethod.Engine.CLASSICAL;
        }
        if ("stardist".equals(normalized)) {
            return SegmentationMethod.Engine.STARDIST;
        }
        if ("cellpose".equals(normalized)) {
            return SegmentationMethod.Engine.CELLPOSE;
        }
        throw new IllegalArgumentException("engine must be classical.");
    }

    private static ParameterId parseParameterId(String value, String optionName) {
        ParameterId id = ParameterId.fromStableKey(value);
        if (id == null) {
            throw new IllegalArgumentException(optionName + " names an unsupported parameter: " + value);
        }
        return id;
    }

    private static SegSweepParameters.PickCriterion parsePick(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if ("knee".equals(normalized)) return SegSweepParameters.PickCriterion.KNEE;
        if ("stability".equals(normalized)) return SegSweepParameters.PickCriterion.STABILITY;
        if ("both".equals(normalized)) return SegSweepParameters.PickCriterion.BOTH;
        if ("none".equals(normalized)) return SegSweepParameters.PickCriterion.NONE;
        throw new IllegalArgumentException("pick must be knee, stability, both, or none.");
    }

    private static ParameterValueList parseValues(String value, String optionName) {
        if (!SegSweepMacroOptions.hasText(value)) {
            throw new IllegalArgumentException(optionName + " must not be empty.");
        }
        String[] parts = value.split(",");
        List<Object> values = new ArrayList<Object>();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.length() == 0) {
                throw new IllegalArgumentException(optionName + " contains an empty value.");
            }
            values.add(Double.valueOf(parseDouble(part, optionName)));
        }
        return ParameterValueList.of(values);
    }

    private static CropSpec parseCrop(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() == 0 || "full".equalsIgnoreCase(normalized)) {
            return CropSpec.full();
        }
        String[] parts = normalized.split(",");
        if (parts.length != 4) {
            throw new IllegalArgumentException("crop must be full or x,y,w,h.");
        }
        int x = parseInt(parts[0], "crop x");
        int y = parseInt(parts[1], "crop y");
        int w = parseInt(parts[2], "crop width");
        int h = parseInt(parts[3], "crop height");
        return CropSpec.custom(new Rectangle(x, y, w, h));
    }

    private static int parseInt(String value, String optionName) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(optionName + " must be an integer" + got(value) + ".");
        }
    }

    private static long parseLong(String value, String optionName) {
        try {
            return Long.parseLong(value.trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(optionName + " must be an integer" + got(value) + ".");
        }
    }

    private static double parseDouble(String value, String optionName) {
        try {
            double parsed = Double.parseDouble(value.trim());
            if (Double.isFinite(parsed)) {
                return parsed;
            }
        } catch (RuntimeException ignored) {
            // Typed message below.
        }
        throw new IllegalArgumentException(optionName + " must be a finite number" + got(value) + ".");
    }

    /** Names the rejected value so a macro error says what to fix. */
    private static String got(String value) {
        return value == null ? "" : " (got \"" + value.trim() + "\")";
    }

    /**
     * Strips the brackets from a value. Path values from {@code getDirectory()}
     * on Windows carry backslashes; they are normalised to forward slashes, which
     * Java accepts on every platform. Other values keep their backslashes (a
     * batch filename regex needs them); numeric values then fail their own parse.
     */
    static String decodeValue(String key, String raw) {
        String value = raw;
        if (raw.length() >= 2 && raw.charAt(0) == '[' && raw.charAt(raw.length() - 1) == ']') {
            value = raw.substring(1, raw.length() - 1);
            if (value.indexOf('[') >= 0 || value.indexOf(']') >= 0) {
                throw new IllegalArgumentException(
                        "Bracketed macro values must not contain brackets.");
            }
        }
        if (value.indexOf('"') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(
                    "Macro values must not contain quotes or line breaks.");
        }
        if (key != null && PATH_KEYS.contains(key)) {
            value = value.replace('\\', '/');
        }
        return value;
    }

    private static final class BuilderState {
        final SegSweepMacroOptions options = new SegSweepMacroOptions();
        final AxisBuilder primary = new AxisBuilder();
        final AxisBuilder secondary = new AxisBuilder();
        boolean hasSecondary;

        void finishAxes() {
            primary.applyDefaults();
            options.setPrimaryAxis(primary.build("sweep", "values", "from/to/step"));
            if (hasSecondary) {
                options.setSecondaryAxis(secondary.build("sweep2", "values2", "from2/to2/step2"));
            }
        }
    }

    private static final class AxisBuilder {
        ParameterId id;
        Double from;
        Double to;
        Double step;
        ParameterValueList values;

        /**
         * README contract: {@code sweep}, {@code from}, {@code to} and {@code step}
         * default to threshold, 10, 60 and 5. Explicit {@code values} replace the
         * range, so no range default is applied alongside them.
         */
        void applyDefaults() {
            if (id == null) id = ParameterId.THRESHOLD;
            if (values != null) return;
            if (from == null) from = Double.valueOf(DEFAULT_FROM);
            if (to == null) to = Double.valueOf(DEFAULT_TO);
            if (step == null) step = Double.valueOf(DEFAULT_STEP);
        }

        SegSweepMacroOptions.AxisSpec build(String sweepName,
                                            String valuesName,
                                            String rangeName) {
            if (id == null) {
                throw new IllegalArgumentException(sweepName + " is required.");
            }
            boolean hasRange = from != null || to != null || step != null;
            if (values != null && hasRange) {
                throw new IllegalArgumentException(valuesName + " is mutually exclusive with " + rangeName + ".");
            }
            if (values != null) {
                return SegSweepMacroOptions.AxisSpec.values(id, values);
            }
            if (from == null || to == null || step == null) {
                throw new IllegalArgumentException("Provide either " + valuesName
                        + " or all of " + rangeName + ".");
            }
            return SegSweepMacroOptions.AxisSpec.range(id,
                    from.doubleValue(), to.doubleValue(), step.doubleValue());
        }
    }
}
