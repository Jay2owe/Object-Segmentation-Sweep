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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Parser for ImageJ macro options passed to the Object Segmentation Sweep command.
 */
public final class SegSweepMacroOptionsParser {

    private SegSweepMacroOptionsParser() {
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
                String value = decodeValue(token.substring(eq + 1).trim());
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
        if ("hide_display".equals(flag) || "no_display".equals(flag)) {
            options.setHideDisplay(true);
        } else if ("show_display".equals(flag)) {
            options.setHideDisplay(false);
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
            throw new IllegalArgumentException(optionName + " must be an integer.");
        }
    }

    private static long parseLong(String value, String optionName) {
        try {
            return Long.parseLong(value.trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(optionName + " must be an integer.");
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
        throw new IllegalArgumentException(optionName + " must be a finite number.");
    }

    private static String decodeValue(String raw) {
        if (raw.length() >= 2 && raw.charAt(0) == '[' && raw.charAt(raw.length() - 1) == ']') {
            String inner = raw.substring(1, raw.length() - 1);
            if (inner.indexOf('[') >= 0 || inner.indexOf(']') >= 0
                    || inner.indexOf('"') >= 0 || inner.indexOf('\\') >= 0
                    || inner.indexOf('\n') >= 0 || inner.indexOf('\r') >= 0) {
                throw new IllegalArgumentException("Bracketed macro values must not contain brackets, quotes, backslashes, or line breaks.");
            }
            return inner;
        }
        if (raw.indexOf('"') >= 0 || raw.indexOf('\\') >= 0
                || raw.indexOf('\n') >= 0 || raw.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Macro values must not contain quotes, backslashes, or line breaks.");
        }
        return raw;
    }

    private static final class BuilderState {
        final SegSweepMacroOptions options = new SegSweepMacroOptions();
        final AxisBuilder primary = new AxisBuilder();
        final AxisBuilder secondary = new AxisBuilder();
        boolean hasSecondary;

        void finishAxes() {
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
